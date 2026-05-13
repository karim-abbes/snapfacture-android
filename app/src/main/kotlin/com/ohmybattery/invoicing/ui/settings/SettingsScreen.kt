package com.ohmybattery.invoicing.ui.settings

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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ohmybattery.invoicing.R
import com.ohmybattery.invoicing.core.country.LocalCountryProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCompany: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    val isFr = LocalCountryProfile.current.code == "FR"
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
            if (isFr) {
                item {
                    SettingsCard(
                        icon = Icons.Default.UploadFile,
                        title = stringResource(R.string.settings_import_title),
                        subtitle = stringResource(R.string.settings_import_subtitle),
                        onClick = onOpenImport,
                    )
                }
                item {
                    SettingsCard(
                        icon = Icons.Default.Download,
                        title = stringResource(R.string.settings_export_title),
                        subtitle = stringResource(R.string.settings_export_subtitle),
                        onClick = onOpenExport,
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
        }
    }
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
