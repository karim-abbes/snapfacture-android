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
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text("Sauvegarde automatique") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
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
                        Text("Sauvegarder maintenant")
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro() {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Pourquoi sauvegarder ?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Choisissez un dossier (Google Drive, OneDrive, ou la carte SD). Après chaque facture ou avoir, " +
                    "une copie complète de la base est automatiquement enregistrée dans ce dossier. " +
                    "Si votre téléphone casse, vous récupérez l'app sur un nouvel appareil en restaurant ce fichier.",
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
                Text("Dossier de sauvegarde", style = MaterialTheme.typography.titleMedium)
                Text(
                    settings.folderLabel ?: settings.folderUri ?: "Aucun dossier choisi — toucher pour sélectionner",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (settings.folderUri != null) {
                OutlinedButton(onClick = onClear) { Text("Retirer") }
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
                Text("Sauvegarder après chaque facture", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (settings.folderUri == null) "Choisissez un dossier pour activer"
                    else "Une copie est créée à chaque nouvelle facture ou avoir",
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
                "Dernière sauvegarde",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                settings.lastBackupAt?.let {
                    SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(it))
                } ?: "Jamais",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
