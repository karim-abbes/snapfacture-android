package com.snapfacture.ui.csvimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.core.csv.ImportReport
import com.snapfacture.core.csv.InvoiceCsvImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

sealed interface ImportPhase {
    data object Idle : ImportPhase
    data object Running : ImportPhase
    data class Done(val report: ImportReport) : ImportPhase
    data class Error(val message: String) : ImportPhase
}

data class ImportUiState(val phase: ImportPhase = ImportPhase.Idle)

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: InvoiceCsvImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun run(uri: Uri) {
        _state.update { it.copy(phase = ImportPhase.Running) }
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("Impossible d'ouvrir le fichier")
                    input.use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                            importer.runImport(reader)
                        }
                    }
                }
                _state.update { it.copy(phase = ImportPhase.Done(report)) }
            } catch (t: Throwable) {
                _state.update { it.copy(phase = ImportPhase.Error(t.message ?: "Erreur inconnue")) }
            }
        }
    }

    fun reset() {
        _state.update { ImportUiState() }
    }
}
