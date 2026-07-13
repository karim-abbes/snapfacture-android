package com.snapfacture.spike

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SpikeScreen()
            }
        }
    }
}

@Composable
private fun SpikeScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Spike assistant IA — évaluation phase 0", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = vm.modelPath,
                onValueChange = { vm.modelPath = it },
                label = { Text("Chemin du modèle (.task / .litertlm)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !vm.busy,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !vm.busy) {
                    Text("Importer…")
                }
                Button(onClick = { vm.loadModel() }, enabled = !vm.busy) {
                    Text("Charger")
                }
                Text("GPU")
                Switch(checked = vm.useGpu, onCheckedChange = { vm.useGpu = it }, enabled = !vm.busy)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.runEval("fr") }, enabled = vm.loaded && !vm.busy) { Text("Éval FR") }
                Button(onClick = { vm.runEval("en") }, enabled = vm.loaded && !vm.busy) { Text("Éval EN") }
                Button(onClick = { vm.runEval(null) }, enabled = vm.loaded && !vm.busy) { Text("Tout") }
            }

            if (vm.busy && vm.progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { vm.progressDone.toFloat() / vm.progressTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(vm.status, style = MaterialTheme.typography.bodyMedium)

            vm.summary?.let { summary ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(summary, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("eval-report", vm.reportJson ?: summary)
                            )
                        }) {
                            Text("Copier le rapport JSON")
                        }
                    }
                }
            }

            HorizontalDivider()

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.results.asReversed()) { result ->
                    Column {
                        Text(
                            "${if (result.passed) "✅" else "❌"} ${result.case_.id} — ${result.latencyMs / 1000.0} s",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(result.case_.query, style = MaterialTheme.typography.bodySmall)
                        val detail = result.failReason
                            ?: result.toolCalled?.let { "→ $it ${result.argsJson.orEmpty()}" }
                            ?: "→ réponse texte"
                        Text(detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
