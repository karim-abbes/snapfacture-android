package com.snapfacture.ui.fec

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snapfacture.R
import com.snapfacture.core.fec.FecExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import javax.inject.Inject

sealed interface FecPhase {
    data object Idle : FecPhase
    data object Running : FecPhase
    data class Done(val entryCount: Int, val file: File) : FecPhase
    data class Failed(val message: String) : FecPhase
}

@HiltViewModel
class FecViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: FecExporter,
) : ViewModel() {

    private val _phase = MutableStateFlow<FecPhase>(FecPhase.Idle)
    val phase: StateFlow<FecPhase> = _phase.asStateFlow()

    fun export() {
        if (_phase.value == FecPhase.Running) return
        _phase.update { FecPhase.Running }
        viewModelScope.launch {
            _phase.update {
                try {
                    val (count, file) = withContext(Dispatchers.IO) {
                        val dir = File(context.filesDir, "exports").apply { mkdirs() }
                        val file = File(dir, exporter.suggestedFileName(System.currentTimeMillis()))
                        val count = BufferedWriter(
                            OutputStreamWriter(file.outputStream(), Charsets.UTF_8)
                        ).use { exporter.exportAll(it) }
                        count to file
                    }
                    FecPhase.Done(count, file)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Exception) {
                    FecPhase.Failed(t.message ?: context.getString(R.string.common_unknown_error))
                }
            }
        }
    }

    fun shareUri(file: File): android.net.Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun reset() = _phase.update { FecPhase.Idle }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FecScreen(
    onBack: () -> Unit,
    vm: FecViewModel = hiltViewModel(),
) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fec_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.fec_intro_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.fec_intro_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when (val p = phase) {
                FecPhase.Idle -> item {
                    Button(
                        onClick = vm::export,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.fec_generate)) }
                }
                FecPhase.Running -> item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.export_running))
                    }
                }
                is FecPhase.Done -> item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.export_done_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.fec_done_entries, p.entryCount))
                            Text(
                                p.file.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            val shareTitle = stringResource(R.string.export_share)
                            val subject = stringResource(R.string.fec_email_subject)
                            Button(
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, vm.shareUri(p.file))
                                        putExtra(Intent.EXTRA_SUBJECT, subject)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(send, shareTitle)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(shareTitle)
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.export_new))
                            }
                        }
                    }
                }
                is FecPhase.Failed -> item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.export_error_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(p.message, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = vm::export, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}
