package com.ohmybattery.invoicing.ui.backup

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmybattery.invoicing.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    vm: BackupViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val restoreDone by vm.restoreDone.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }

    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
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
            item { FolderCard(settings, { picker.launch(null) }, vm::clearFolder) }
            item { AutoToggle(settings, vm::setAutoEnabled) }
            item { LastBackupCard(settings) }
            item {
                Button(
                    onClick = vm::runNow,
                    enabled = !running && settings.folderUri != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.backup_now))
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { RestoreCard(running) { restorePicker.launch(arrayOf("*/*")) } }
        }
    }

    pendingRestoreUri?.let { uri ->
        RestoreConfirmDialog(
            isRunning = running,
            onDismiss = { pendingRestoreUri = null },
            onConfirm = {
                vm.restore(uri)
                pendingRestoreUri = null
            },
        )
    }

    if (restoreDone) {
        RestoreSuccessDialog(onRelaunch = vm::relaunchApp)
    }
}

@Composable
private fun RestoreCard(disabled: Boolean, onPick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.restore_section_title), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.restore_section_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPick,
                enabled = !disabled,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.restore_button))
            }
        }
    }
}

@Composable
private fun RestoreConfirmDialog(
    isRunning: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        title = { Text(stringResource(R.string.restore_dialog_title)) },
        text = { Text(stringResource(R.string.restore_dialog_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp,
                    )
                } else Text(stringResource(R.string.restore_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRunning) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun RestoreSuccessDialog(onRelaunch: () -> Unit) {
    AlertDialog(
        onDismissRequest = onRelaunch,
        title = { Text(stringResource(R.string.restore_done_title)) },
        text = { Text(stringResource(R.string.restore_done_body)) },
        confirmButton = {
            Button(onClick = onRelaunch) { Text(stringResource(R.string.restore_done_relaunch)) }
        },
    )
}

@Composable
private fun Intro() {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.backup_intro_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.backup_intro_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FolderCard(
    settings: com.ohmybattery.invoicing.data.preferences.BackupSettings,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.backup_folder_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    settings.folderLabel
                        ?: settings.folderUri
                        ?: stringResource(R.string.backup_folder_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (settings.folderUri != null) {
                OutlinedButton(onClick = onClear) { Text(stringResource(R.string.action_remove)) }
            }
        }
    }
}

@Composable
private fun AutoToggle(
    settings: com.ohmybattery.invoicing.data.preferences.BackupSettings,
    onChange: (Boolean) -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.backup_auto_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (settings.folderUri == null) stringResource(R.string.backup_auto_disabled_subtitle)
                    else stringResource(R.string.backup_auto_enabled_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.autoEnabled,
                onCheckedChange = onChange,
                enabled = settings.folderUri != null,
            )
        }
    }
}

@Composable
private fun LastBackupCard(settings: com.ohmybattery.invoicing.data.preferences.BackupSettings) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.backup_last),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                settings.lastBackupAt?.let {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))
                } ?: stringResource(R.string.backup_last_never),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
