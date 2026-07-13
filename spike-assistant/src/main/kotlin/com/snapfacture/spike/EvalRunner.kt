package com.snapfacture.spike

import android.os.Build
import android.os.Debug
import com.google.ai.edge.localagents.core.proto.GenerateContentResponse
import com.google.ai.edge.localagents.fc.GemmaFormatter
import com.google.ai.edge.localagents.fc.GenerativeModel
import com.google.ai.edge.localagents.fc.LlmInferenceBackend
import com.google.ai.edge.localagents.fc.proto.ConstraintOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.protobuf.Struct
import com.google.protobuf.Value
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate

data class CaseResult(
    val case_: EvalCase,
    val passed: Boolean,
    val toolCalled: String?,
    val argsJson: String?,
    val failReason: String?,
    val latencyMs: Long,
)

data class EvalReport(
    val results: List<CaseResult>,
    val summaryText: String,
    val json: JSONObject,
)

class EvalRunner(
    private val llm: LlmInference,
    private val backendLabel: String,
    private val modelLabel: String,
) {

    fun run(cases: List<EvalCase>, onProgress: (Int, Int, CaseResult) -> Unit): EvalReport {
        val results = ArrayList<CaseResult>(cases.size)
        val byLang = cases.groupBy { it.lang }
        var done = 0
        val today = LocalDate.now().toString()
        for ((lang, langCases) in byLang) {
            val model = GenerativeModel(
                LlmInferenceBackend(llm, GemmaFormatter()),
                SnapfactureTools.systemInstruction(lang, today),
                listOf(SnapfactureTools.tool),
            )
            for (case in langCases) {
                val result = runCase(model, case)
                results.add(result)
                done++
                onProgress(done, cases.size, result)
            }
        }
        return buildReport(results)
    }

    private fun runCase(model: GenerativeModel, case: EvalCase): CaseResult {
        val chat = model.startChat()
        if (case.constrained) {
            chat.enableConstraint(
                ConstraintOptions.newBuilder()
                    .setToolCallOnly(
                        ConstraintOptions.ToolCallOnly.newBuilder()
                            .setConstraintPrefix("```tool_code\n")
                            .setConstraintSuffix("\n```")
                    )
                    .build()
            )
        }
        val start = System.nanoTime()
        val response: GenerateContentResponse
        try {
            response = chat.sendMessage(case.query)
        } catch (e: Exception) {
            val ms = (System.nanoTime() - start) / 1_000_000
            runCatching { chat.close() }
            return CaseResult(case, false, null, null, "exception: ${e.javaClass.simpleName}: ${e.message}", ms)
        }
        val latencyMs = (System.nanoTime() - start) / 1_000_000
        runCatching { chat.close() }

        val parts = if (response.candidatesCount > 0) response.getCandidates(0).content.partsList else emptyList()
        val callPart = parts.firstOrNull { it.hasFunctionCall() }
        val toolName = callPart?.functionCall?.name
        val args: JSONObject? = callPart?.functionCall?.args?.let { structToJson(it) }
        val argsStr = args?.toString()

        val (passed, reason) = evaluate(case, toolName, args)
        return CaseResult(case, passed, toolName, argsStr, reason, latencyMs)
    }

    private fun evaluate(case: EvalCase, toolName: String?, args: JSONObject?): Pair<Boolean, String?> {
        if (toolName == null) {
            return if (case.expectTools.isEmpty() || case.allowNoTool) true to null
            else false to "aucun tool appelé, attendu: ${case.expectTools}"
        }
        if (case.expectTools.isEmpty()) {
            return false to "tool $toolName appelé alors qu'aucun n'était attendu"
        }
        if (toolName !in case.expectTools) {
            return false to "tool $toolName, attendu: ${case.expectTools}"
        }
        for ((key, expected) in case.expectArgs) {
            val actual = args?.opt(key)?.let { anyToComparableString(it) }
                ?: return false to "argument \"$key\" absent (attendu: $expected)"
            if (!valuesMatch(expected, actual)) {
                return false to "argument \"$key\" = \"$actual\", attendu: \"$expected\""
            }
        }
        if (case.expectLines.isNotEmpty()) {
            val actualLines = args?.optJSONArray("lines")
                ?: return false to "argument \"lines\" absent"
            if (actualLines.length() != case.expectLines.size) {
                return false to "${actualLines.length()} ligne(s), attendu: ${case.expectLines.size}"
            }
            val used = BooleanArray(actualLines.length())
            for (expected in case.expectLines) {
                var found = false
                for (i in 0 until actualLines.length()) {
                    if (used[i]) continue
                    val line = actualLines.optJSONObject(i) ?: continue
                    if (lineMatches(expected, line)) {
                        used[i] = true
                        found = true
                        break
                    }
                }
                if (!found) return false to "ligne attendue non trouvée: \"${expected.descriptionContains}\" x${expected.quantity} à ${expected.unitPrice}"
            }
        }
        return true to null
    }

    private fun lineMatches(expected: ExpectedLine, actual: JSONObject): Boolean {
        val desc = actual.opt("description")?.let { anyToComparableString(it) } ?: return false
        if (!norm(desc).contains(norm(expected.descriptionContains))) return false
        val qty = actual.opt("quantity")?.let { anyToComparableString(it) } ?: "1"
        if (!valuesMatch(expected.quantity, qty)) return false
        if (expected.unitPrice != null) {
            val price = actual.opt("unit_price")?.let { anyToComparableString(it) } ?: return false
            if (!valuesMatch(expected.unitPrice, price)) return false
        }
        return true
    }

    private fun buildReport(results: List<CaseResult>): EvalReport {
        fun accuracy(category: String): Pair<Int, Int> {
            val subset = results.filter { it.case_.category == category }
            return subset.count { it.passed } to subset.size
        }

        val read = accuracy("read")
        val propose = accuracy("propose")
        val guard = accuracy("guard")
        val trap = accuracy("trap")
        val latencies = results.map { it.latencyMs }.sorted()
        val median = if (latencies.isEmpty()) 0 else latencies[latencies.size / 2]
        val p90 = if (latencies.isEmpty()) 0 else latencies[((latencies.size * 9) / 10).coerceAtMost(latencies.size - 1)]
        val pssMb = memoryPssMb()

        fun pct(p: Pair<Int, Int>) = if (p.second == 0) "n/a" else "${p.first}/${p.second} (${p.first * 100 / p.second} %)"

        val summary = buildString {
            appendLine("Modèle: $modelLabel — backend: $backendLabel")
            appendLine("Appareil: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            appendLine("Lecture:      ${pct(read)}  [go/no-go: ≥ 85 %]")
            appendLine("Proposition:  ${pct(propose)}  [go/no-go: ≥ 75 %]")
            appendLine("Ambiguïté:    ${pct(guard)}  (informatif)")
            appendLine("Pièges:       ${pct(trap)}  (informatif)")
            appendLine("Latence: médiane ${median / 1000.0} s — p90 ${p90 / 1000.0} s")
            appendLine("Mémoire process (PSS): $pssMb Mo")
        }

        val json = JSONObject().apply {
            put("model", modelLabel)
            put("backend", backendLabel)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("api_level", Build.VERSION.SDK_INT)
            put("read_passed", read.first); put("read_total", read.second)
            put("propose_passed", propose.first); put("propose_total", propose.second)
            put("guard_passed", guard.first); put("guard_total", guard.second)
            put("trap_passed", trap.first); put("trap_total", trap.second)
            put("latency_median_ms", median)
            put("latency_p90_ms", p90)
            put("memory_pss_mb", pssMb)
            put("results", JSONArray().apply {
                results.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.case_.id)
                        put("query", r.case_.query)
                        put("passed", r.passed)
                        put("tool_called", r.toolCalled ?: JSONObject.NULL)
                        put("args", r.argsJson ?: JSONObject.NULL)
                        put("fail_reason", r.failReason ?: JSONObject.NULL)
                        put("latency_ms", r.latencyMs)
                    })
                }
            })
        }
        return EvalReport(results, summary, json)
    }

    private fun memoryPssMb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss / 1024L
    }

    companion object {

        fun structToJson(struct: Struct): JSONObject {
            val obj = JSONObject()
            for ((k, v) in struct.fieldsMap) obj.put(k, valueToAny(v))
            return obj
        }

        private fun valueToAny(v: Value): Any = when (v.kindCase) {
            Value.KindCase.NULL_VALUE -> JSONObject.NULL
            Value.KindCase.NUMBER_VALUE -> BigDecimal(v.numberValue).stripTrailingZeros().toPlainString()
            Value.KindCase.STRING_VALUE -> v.stringValue
            Value.KindCase.BOOL_VALUE -> v.boolValue
            Value.KindCase.STRUCT_VALUE -> structToJson(v.structValue)
            Value.KindCase.LIST_VALUE -> JSONArray().apply { v.listValue.valuesList.forEach { put(valueToAny(it)) } }
            else -> JSONObject.NULL
        }

        fun anyToComparableString(value: Any): String = when (value) {
            is String -> value
            is Number -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
            else -> value.toString()
        }

        fun norm(s: String): String {
            val lowered = s.lowercase().trim()
                .replace("€", "").replace("$", "")
                .replace('\u00A0', ' ')
                .replace(',', '.')
            val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{Mn}+"), "").replace(Regex("\\s+"), " ").trim()
        }

        fun valuesMatch(expected: String, actual: String): Boolean {
            if (expected.startsWith("~")) return norm(actual).contains(norm(expected.substring(1)))
            val ne = norm(expected)
            val na = norm(actual)
            if (ne == na) return true
            val de = ne.toBigDecimalOrNull()
            val da = na.toBigDecimalOrNull()
            return de != null && da != null && de.compareTo(da) == 0
        }

        private fun String.toBigDecimalOrNull(): BigDecimal? = try {
            BigDecimal(this)
        } catch (_: NumberFormatException) {
            null
        }
    }
}
