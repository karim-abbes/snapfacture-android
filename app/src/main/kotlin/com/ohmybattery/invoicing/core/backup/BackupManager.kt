package com.ohmybattery.invoicing.core.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ohmybattery.invoicing.core.di.ApplicationScope
import com.ohmybattery.invoicing.data.local.AppDatabase
import com.ohmybattery.invoicing.data.preferences.BackupPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupResult {
    data class Success(val fileName: String, val at: Long) : BackupResult
    data class Failure(val message: String) : BackupResult
}

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: BackupPreferences,
    private val database: AppDatabase,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val stampFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE)

    fun triggerIfEnabled() {
        scope.launch {
            val s = prefs.flow.first()
            if (s.autoEnabled && s.folderUri != null) {
                runBackup(Uri.parse(s.folderUri))
            }
        }
    }

    suspend fun runBackup(folderUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            runCatching {
                database.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(FULL)")
                    .use { /* drained */ }
            }

            val folder = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext BackupResult.Failure("Dossier introuvable")
            if (!folder.canWrite()) {
                return@withContext BackupResult.Failure("Permission d'écriture refusée sur le dossier choisi")
            }

            val fileName = "ohmybattery_${stampFmt.format(Date())}.db"
            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            if (!dbFile.exists()) {
                return@withContext BackupResult.Failure("Base de données introuvable")
            }

            val backup = folder.createFile("application/octet-stream", fileName)
                ?: return@withContext BackupResult.Failure("Impossible de créer le fichier de sauvegarde")

            context.contentResolver.openOutputStream(backup.uri)?.use { out ->
                dbFile.inputStream().use { it.copyTo(out) }
            } ?: return@withContext BackupResult.Failure("Impossible d'écrire dans le dossier")

            val now = System.currentTimeMillis()
            prefs.markBackedUp(now)
            BackupResult.Success(fileName, now)
        } catch (t: Throwable) {
            BackupResult.Failure(t.message ?: "Erreur inconnue")
        }
    }
}
