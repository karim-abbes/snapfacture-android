package com.snapfacture.spike

import android.content.Context
import org.json.JSONObject

data class ExpectedLine(
    val descriptionContains: String,
    val quantity: String,
    val unitPrice: String?,
)

data class EvalCase(
    val id: String,
    val lang: String,
    val category: String, // read | propose | guard | trap
    val query: String,
    val expectTools: List<String>,
    val expectArgs: Map<String, String>,
    val expectLines: List<ExpectedLine>,
    val constrained: Boolean,
    val allowNoTool: Boolean,
)

object EvalCases {

    fun load(context: Context): List<EvalCase> {
        val raw = context.assets.open("eval_cases.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw).getJSONArray("cases")
        val cases = ArrayList<EvalCase>(root.length())
        for (i in 0 until root.length()) {
            val o = root.getJSONObject(i)
            val expectTools = ArrayList<String>()
            o.optJSONArray("expect_tools")?.let { arr ->
                for (j in 0 until arr.length()) expectTools.add(arr.getString(j))
            }
            val expectArgs = LinkedHashMap<String, String>()
            o.optJSONObject("expect_args")?.let { args ->
                args.keys().forEach { k -> expectArgs[k] = args.getString(k) }
            }
            val expectLines = ArrayList<ExpectedLine>()
            o.optJSONArray("expect_lines")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val l = arr.getJSONObject(j)
                    expectLines.add(
                        ExpectedLine(
                            descriptionContains = l.getString("description_contains"),
                            quantity = l.optString("quantity", "1"),
                            unitPrice = if (l.has("unit_price")) l.getString("unit_price") else null,
                        )
                    )
                }
            }
            cases.add(
                EvalCase(
                    id = o.getString("id"),
                    lang = o.getString("lang"),
                    category = o.getString("category"),
                    query = o.getString("query"),
                    expectTools = expectTools,
                    expectArgs = expectArgs,
                    expectLines = expectLines,
                    constrained = o.optBoolean("constrained", true),
                    allowNoTool = o.optBoolean("allow_no_tool", false),
                )
            )
        }
        return cases
    }
}
