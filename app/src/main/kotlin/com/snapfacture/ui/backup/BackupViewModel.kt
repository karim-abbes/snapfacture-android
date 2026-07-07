package com.snapfacture.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfacture.R
import com.snapfacture.core.backup.BackupManager
import com.snapfacture.core.backup.BackupResult
import com.snapfacture.core.backup.RestoreResult
import com.snapfacture.data.preferences.BackupPreferences
import com.snapfacture.data.preferences.BackupSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val settings: BackupSettings = BackupSettings(),
    val isRunning: Boolean = false,
    val lastMessage: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: BackupPreferences,
    private val manager: BackupManager,
) : ViewModel() {

    val settings: StateFlow<BackupSettings> =
        prefs.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupSettings())

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val label = DocumentFile.fromTreeUri(context, uri)?.name
            prefs.setFolder(uri.toString(), label)
            prefs.setAutoEnabled(true)
            runNow()
        }
    }

    fun clearFolder() {
        viewModelScope.launch { prefs.clearFolder() }
    }

    fun setAutoEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoEnabled(enabled) }
    }

    fun runNow() {
        val uri = settings.value.folderUri?.let(Uri::parse) ?: return
        _running.update { true }
        _message.update { null }
        viewModelScope.launch {
            val result = manager.runBackup(uri)
            _running.update { false }
            _message.update {
                when (result) {
                    is BackupResult.Success -> context.getString(R.string.backup_msg_success, result.fileName)
                    is BackupResult.Failure -> context.getString(R.string.backup_msg_failure, result.message)
                }
            }
        }
    }

    fun dismissMessage() {
        _message.update { null }
    }

    private val _restoreDone = MutableStateFlow(false)
    val restoreDone: StateFlow<Boolean> = _restoreDone.asStateFlow()

    fun restore(uri: Uri) {
        _running.update { true }
        _message.update { null }
        viewModelScope.launch {
            val result = manager.restore(uri)
            _running.update { false }
            when (result) {
                RestoreResult.Success -> _restoreDone.update { true }
                is RestoreResult.Failure -> _message.update { result.message }
            }
        }
    }

    fun relaunchApp() {
        val pm = context.packageManager
        val launch = pm.getLaunchIntentForPackage(context.packageName)
        launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) context.startActivity(launch)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
