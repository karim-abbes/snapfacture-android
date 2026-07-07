package com.snapfacture.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfacture.R
import com.snapfacture.core.country.LocalCountryProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenFec: () -> Unit,
    onOpenTaxReport: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCompany: () -> Unit,
    onOpenSecurity: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val isFr = LocalCountryProfile.current.code == "FR"
    val hasAuditChain = LocalCountryProfile.current.antiFraudHashChain
    val verification by vm.verification.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SettingsCard(
                    icon = Icons.Default.Inventory2,
                    title = stringResource(R.string.settings_catalog_title),
                    subtitle = stringResource(R.string.settings_catalog_subtitle),
                    onClick = onOpenCatalog,
                )
            }
            // Import is gated on the FR profile (legacy fixed CSV format);
            // export is universal — it's the only way to get data out for an accountant.
            if (isFr) {
                item {
                    SettingsCard(
                        icon = Icons.Default.UploadFile,
                        title = stringResource(R.string.settings_import_title),
                        subtitle = stringResource(R.string.settings_import_subtitle),
                        onClick = onOpenImport,
                    )
                }
            }
            item {
                SettingsCard(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.settings_export_title),
                    subtitle = stringResource(R.string.settings_export_subtitle),
                    onClick = onOpenExport,
                )
            }
            item {
                SettingsCard(
                    icon = Icons.Default.RequestQuote,
                    title = stringResource(R.string.settings_tax_report_title, LocalCountryProfile.current.taxLabel),
                    subtitle = stringResource(R.string.settings_tax_report_subtitle),
                    onClick = onOpenTaxReport,
                )
            }
            // The FEC is a French statutory accounting file — FR profile only.
            if (isFr) {
                item {
                    SettingsCard(
                        icon = Icons.Default.AccountBalance,
                        title = stringResource(R.string.settings_fec_title),
                        subtitle = stringResource(R.string.settings_fec_subtitle),
                        onClick = onOpenFec,
                    )
                }
            }
            item {
                SettingsCard(
                    icon = Icons.Default.Backup,
                    title = stringResource(R.string.settings_backup_title),
                    subtitle = stringResource(R.string.settings_backup_subtitle),
                    onClick = onOpenBackup,
                )
            }
            item {
                SettingsCard(
                    icon = Icons.Default.Business,
                    title = stringResource(R.string.settings_company_title),
                    subtitle = stringResource(R.string.settings_company_subtitle),
                    onClick = onOpenCompany,
                )
            }
            item {
                SettingsCard(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.settings_security_title),
                    subtitle = stringResource(R.string.settings_security_subtitle),
                    onClick = onOpenSecurity,
                )
            }
            if (hasAuditChain) {
                item {
                    SettingsCard(
                        icon = Icons.Default.VerifiedUser,
                        title = stringResource(R.string.settings_verify_title),
                        subtitle = stringResource(R.string.settings_verify_subtitle),
                        onClick = vm::verify,
                    )
                }
            }
        }
    }

    if (verification != VerificationUi.Idle) {
        VerificationDialog(state = verification, onDismiss = vm::dismissVerification)
    }
}

@Composable
private fun VerificationDialog(state: VerificationUi, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (state != VerificationUi.Running) onDismiss() },
        title = { Text(stringResource(R.string.verify_dialog_title)) },
        text = {
            when (state) {
                VerificationUi.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.verify_running))
                }
                VerificationUi.Failed -> Text(
                    stringResource(R.string.verify_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                is VerificationUi.Done -> {
                    val r = state.result
                    when {
                        !r.ok && r.tamperedInvoiceNumber != null -> Text(
                            stringResource(R.string.verify_tampered, r.tamperedInvoiceNumber),
                            color = MaterialTheme.colorScheme.error,
                        )
                        !r.ok -> Text(
                            stringResource(R.string.verify_broken, r.brokenAtEntryId ?: 0L),
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Column {
                            Text(stringResource(R.string.verify_ok, r.verifiedEntries))
                            if (r.legacyEntries > 0) {
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    stringResource(R.string.verify_legacy_note, r.legacyEntries),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                VerificationUi.Idle -> {}
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = state != VerificationUi.Running) {
                Text(stringResource(R.string.verify_close))
            }
        },
    )
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
