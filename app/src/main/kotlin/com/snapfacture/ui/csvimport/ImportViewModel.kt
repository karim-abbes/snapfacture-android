package com.snapfacture.ui.csvimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.R
import com.snapfacture.core.csv.CsvParser
import com.snapfacture.core.csv.ImportField
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
import java.io.StringReader
import javax.inject.Inject

sealed interface ImportPhase {
    data object Idle : ImportPhase
    data class Mapping(
        val headers: List<String>,
        val firstDataRow: List<String>,
        val mapping: Map<ImportField, Int>,
        val rowCount: Int,
    ) : ImportPhase {
        val canImport: Boolean
            get() = ImportField.entries.filter { it.required }.all { it in mapping }
    }
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

    // Kept in memory between the mapping phase and the import: invoice CSVs
    // are small (a few thousand rows at most), no need to re-read the Uri.
    private var rows: List<List<String>> = emptyList()

    fun load(uri: Uri) {
        _state.update { it.copy(phase = ImportPhase.Running) }
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error(context.getString(R.string.import_err_open_file))
                    val text = input.use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                    }
                    val separator = CsvParser.detectSeparator(text.lineSequence().firstOrNull().orEmpty())
                    CsvParser.parse(StringReader(text), separator)
                }
                if (parsed.size < 2) {
                    _state.update { it.copy(phase = ImportPhase.Error(context.getString(R.string.import_err_empty))) }
                    return@launch
                }
                rows = parsed
                _state.update {
                    it.copy(
                        phase = ImportPhase.Mapping(
                            headers = parsed.first(),
                            firstDataRow = parsed[1],
                            mapping = ImportField.suggestMapping(parsed.first()),
                            rowCount = parsed.size - 1,
                        )
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(phase = ImportPhase.Error(t.message ?: context.getString(R.string.common_unknown_error))) }
            }
        }
    }

    fun setMapping(field: ImportField, columnIndex: Int?) {
        _state.update { st ->
            val phase = st.phase as? ImportPhase.Mapping ?: return@update st
            val newMapping = phase.mapping.toMutableMap()
            if (columnIndex == null) newMapping.remove(field)
            else {
                // A column feeds one field: mapping it here unmaps it elsewhere.
                newMapping.entries.removeAll { it.value == columnIndex }
                newMapping[field] = columnIndex
            }
            st.copy(phase = phase.copy(mapping = newMapping))
        }
    }

    fun import() {
        val phase = _state.value.phase as? ImportPhase.Mapping ?: return
        if (!phase.canImport) return
        _state.update { it.copy(phase = ImportPhase.Running) }
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) { importer.runImport(rows, phase.mapping) }
                _state.update { it.copy(phase = ImportPhase.Done(report)) }
            } catch (t: Throwable) {
                _state.update { it.copy(phase = ImportPhase.Error(t.message ?: context.getString(R.string.common_unknown_error))) }
            }
        }
    }

    fun reset() {
        rows = emptyList()
        _state.update { ImportUiState() }
    }
}
