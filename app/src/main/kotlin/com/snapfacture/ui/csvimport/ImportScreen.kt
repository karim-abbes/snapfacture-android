package com.snapfacture.ui.csvimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfacture.R
import com.snapfacture.core.csv.ImportField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    vm: ImportViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.load(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
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
            item { Intro() }

            when (val phase = state.phase) {
                ImportPhase.Idle -> item {
                    Button(
                        onClick = {
                            picker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.import_choose_file))
                    }
                }
                is ImportPhase.Mapping -> {
                    item {
                        Text(
                            stringResource(R.string.import_mapping_title, phase.rowCount),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    val required = ImportField.entries.filter { it.required }
                    val optional = ImportField.entries.filterNot { it.required }
                    items(required.size) { i ->
                        FieldMappingRow(required[i], phase, vm::setMapping)
                    }
                    item {
                        Text(
                            stringResource(R.string.import_mapping_optional),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(optional.size) { i ->
                        FieldMappingRow(optional[i], phase, vm::setMapping)
                    }
                    item {
                        Button(
                            onClick = vm::import,
                            enabled = phase.canImport,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text(stringResource(R.string.import_start, phase.rowCount)) }
                    }
                    item {
                        OutlinedButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
                ImportPhase.Running -> item { RunningCard() }
                is ImportPhase.Done -> item { DoneCard(phase, onPickAgain = { vm.reset() }, onBack = onBack) }
                is ImportPhase.Error -> item { ErrorCard(phase, onRetry = { vm.reset() }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldMappingRow(
    field: ImportField,
    phase: ImportPhase.Mapping,
    onSet: (ImportField, Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = phase.mapping[field]
    val fieldLabel = stringResource(fieldLabelRes(field)) +
        if (field.required) " *" else ""
    val preview = selected?.let { phase.firstDataRow.getOrNull(it) }?.takeIf { it.isNotBlank() }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { phase.headers.getOrNull(it) } ?: stringResource(R.string.import_column_none),
            onValueChange = {},
            readOnly = true,
            label = { Text(fieldLabel) },
            supportingText = preview?.let { { Text(stringResource(R.string.import_preview_value, it), maxLines = 1) } },
            isError = field.required && selected == null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.import_column_none)) },
                onClick = { onSet(field, null); expanded = false },
            )
            phase.headers.forEachIndexed { index, header ->
                DropdownMenuItem(
                    text = { Text(header.ifBlank { stringResource(R.string.import_column_unnamed, index + 1) }) },
                    onClick = { onSet(field, index); expanded = false },
                )
            }
        }
    }
}

private fun fieldLabelRes(field: ImportField): Int = when (field) {
    ImportField.NUMBER -> R.string.import_field_number
    ImportField.CLIENT -> R.string.import_field_client
    ImportField.ISSUE_DATE -> R.string.import_field_issue_date
    ImportField.TOTAL -> R.string.import_field_total
    ImportField.HT -> R.string.import_field_ht
    ImportField.VAT -> R.string.import_field_vat
    ImportField.TYPE -> R.string.import_field_type
    ImportField.DUE_DATE -> R.string.import_field_due_date
    ImportField.DELIVERY_DATE -> R.string.import_field_delivery_date
    ImportField.PAYMENT_METHOD -> R.string.import_field_payment_method
    ImportField.PAYMENT_DATE -> R.string.import_field_payment_date
    ImportField.ISSUER -> R.string.import_field_issuer
    ImportField.STREET -> R.string.import_field_street
    ImportField.POSTAL -> R.string.import_field_postal
    ImportField.CITY -> R.string.import_field_city
}

@Composable
private fun Intro() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.import_intro_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.import_intro_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RunningCard() {
    Card {
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.import_running))
        }
    }
}

@Composable
private fun DoneCard(phase: ImportPhase.Done, onPickAgain: () -> Unit, onBack: () -> Unit) {
    val r = phase.report
    Card {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.import_done_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            StatRow(stringResource(R.string.import_done_imported), r.imported.toString())
            StatRow(stringResource(R.string.import_done_skipped), r.skipped.toString())
            r.maxImportedNumber?.let { StatRow(stringResource(R.string.import_done_max_number), it.toString()) }
            if (r.errors.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.import_done_details),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                r.errors.take(20).forEach { err ->
                    Text("• $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (r.errors.size > 20) {
                    Text(
                        stringResource(R.string.import_done_more_errors, r.errors.size - 20),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_back)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPickAgain, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_done_pick_again))
            }
        }
    }
}

@Composable
private fun ErrorCard(phase: ImportPhase.Error, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.import_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(phase.message, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
