package com.snapfacture.ui.csvexport

import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.core.csv.InvoiceCsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface ExportPhase {
    data object Idle : ExportPhase
    data object Running : ExportPhase
    data class Done(val count: Int, val file: File) : ExportPhase
    data class Failed(val message: String) : ExportPhase
}

data class ExportUiState(val phase: ExportPhase = ExportPhase.Idle)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: InvoiceCsvExporter,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private val stampFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE)

    fun export() {
        _state.update { it.copy(phase = ExportPhase.Running) }
        viewModelScope.launch {
            try {
                val (count, file) = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "exports").apply { mkdirs() }
                    val file = File(dir, "snapfacture_${stampFmt.format(Date())}.csv")
                    val written = BufferedWriter(OutputStreamWriter(file.outputStream(), Charsets.UTF_8)).use {
                        exporter.exportAll(it)
                    }
                    written to file
                }
                _state.update { it.copy(phase = ExportPhase.Done(count, file)) }
            } catch (t: Throwable) {
                _state.update { it.copy(phase = ExportPhase.Failed(t.message ?: "Erreur inconnue")) }
            }
        }
    }

    fun shareUri(file: File): android.net.Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun reset() {
        _state.update { ExportUiState() }
    }
}
