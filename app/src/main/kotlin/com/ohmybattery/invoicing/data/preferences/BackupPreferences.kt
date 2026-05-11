package com.ohmybattery.invoicing.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore by preferencesDataStore(name = "backup_prefs")

data class BackupSettings(
    val folderUri: String? = null,
    val folderLabel: String? = null,
    val autoEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
)

@Singleton
class BackupPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyFolderUri = stringPreferencesKey("folder_uri")
    private val keyFolderLabel = stringPreferencesKey("folder_label")
    private val keyAutoEnabled = booleanPreferencesKey("auto_enabled")
    private val keyLastBackupAt = longPreferencesKey("last_backup_at")

    val flow: Flow<BackupSettings> = context.backupDataStore.data.map { prefs ->
        BackupSettings(
            folderUri = prefs[keyFolderUri],
            folderLabel = prefs[keyFolderLabel],
            autoEnabled = prefs[keyAutoEnabled] ?: false,
            lastBackupAt = prefs[keyLastBackupAt],
        )
    }

    suspend fun setFolder(uri: String, label: String?) {
        context.backupDataStore.edit {
            it[keyFolderUri] = uri
            if (label != null) it[keyFolderLabel] = label else it.remove(keyFolderLabel)
        }
    }

    suspend fun clearFolder() {
        context.backupDataStore.edit {
            it.remove(keyFolderUri)
            it.remove(keyFolderLabel)
            it[keyAutoEnabled] = false
        }
    }

    suspend fun setAutoEnabled(enabled: Boolean) {
        context.backupDataStore.edit { it[keyAutoEnabled] = enabled }
    }

    suspend fun markBackedUp(at: Long) {
        context.backupDataStore.edit { it[keyLastBackupAt] = at }
    }
}
