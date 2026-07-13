package com.snapfacture.spike

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var modelPath by mutableStateOf("/data/local/tmp/llm/model.task")
    var useGpu by mutableStateOf(true)
    var status by mutableStateOf("Modèle non chargé.")
    var loaded by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var progressDone by mutableStateOf(0)
    var progressTotal by mutableStateOf(0)
    var summary by mutableStateOf<String?>(null)
    var reportPath by mutableStateOf<String?>(null)
    var reportJson by mutableStateOf<String?>(null)
    val results = mutableStateListOf<CaseResult>()

    private var llm: LlmInference? = null

    fun importModel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            status = "Copie du modèle en cours…"
            try {
                val app = getApplication<Application>()
                val dir = File(app.filesDir, "models").apply { mkdirs() }
                val target = File(dir, "imported.task")
                app.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "flux illisible" }
                    target.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 20)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            status = "Copie du modèle… ${copied / (1 shl 20)} Mo"
                        }
                    }
                }
                modelPath = target.absolutePath
                status = "Modèle copié (${target.length() / (1 shl 20)} Mo). Appuyez sur Charger."
            } catch (e: Exception) {
                status = "Échec de la copie : ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            loaded = false
            status = "Chargement du modèle (peut prendre une minute)…"
            try {
                llm?.close()
                llm = null
                val file = File(modelPath)
                require(file.exists()) { "fichier introuvable : $modelPath" }
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxTokens(2048)
                    .setPreferredBackend(if (useGpu) LlmInference.Backend.GPU else LlmInference.Backend.CPU)
                    .build()
                llm = LlmInference.createFromOptions(getApplication(), options)
                loaded = true
                status = "Modèle chargé (${file.name}, backend ${if (useGpu) "GPU" else "CPU"})."
            } catch (e: Exception) {
                status = "Échec du chargement : ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun runEval(langFilter: String?) {
        val inference = llm ?: run {
            status = "Chargez d'abord le modèle."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            summary = null
            reportPath = null
            withContext(Dispatchers.Main) { results.clear() }
            try {
                val app = getApplication<Application>()
                val cases = EvalCases.load(app).filter { langFilter == null || it.lang == langFilter }
                progressDone = 0
                progressTotal = cases.size
                status = "Évaluation en cours (0/${cases.size})…"
                val runner = EvalRunner(
                    llm = inference,
                    backendLabel = if (useGpu) "GPU" else "CPU",
                    modelLabel = File(modelPath).name,
                )
                val report = runner.run(cases) { done, total, result ->
                    progressDone = done
                    status = "Évaluation en cours ($done/$total)…"
                    viewModelScope.launch(Dispatchers.Main) { results.add(result) }
                }
                val out = File(
                    app.getExternalFilesDir(null),
                    "eval-report-${System.currentTimeMillis()}.json"
                )
                out.writeText(report.json.toString(2))
                summary = report.summaryText
                reportPath = out.absolutePath
                reportJson = report.json.toString(2)
                status = "Terminé. Rapport : ${out.absolutePath}"
            } catch (e: Exception) {
                status = "Échec de l'évaluation : ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        runCatching { llm?.close() }
    }
}
