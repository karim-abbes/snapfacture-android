package com.snapfacture.core.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.snapfacture.core.di.ApplicationScope
import com.snapfacture.data.local.AppDatabase
import com.snapfacture.data.preferences.BackupPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupResult {
    data class Success(val fileName: String, val at: Long) : BackupResult
    data class Failure(val message: String) : BackupResult
}

sealed interface RestoreResult {
    data object Success : RestoreResult
    data class Failure(val message: String) : RestoreResult
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

    suspend fun restore(fileUri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val candidate = File(context.cacheDir, "restore-candidate.db")
        try {
            context.contentResolver.openInputStream(fileUri)?.use { src ->
                candidate.outputStream().use { dst -> src.copyTo(dst) }
            } ?: return@withContext RestoreResult.Failure("Impossible de lire le fichier.")

            validateCandidate(candidate)?.let { return@withContext RestoreResult.Failure(it) }

            runCatching { database.close() }

            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            // Keep the current database as a rollback point: restoring a bad
            // file must never be able to destroy the existing invoices.
            val rollback = File(dbFile.parent, "${dbFile.name}.bak")
            if (dbFile.exists()) dbFile.copyTo(rollback, overwrite = true)
            File(dbFile.parent, "${dbFile.name}-wal").delete()
            File(dbFile.parent, "${dbFile.name}-shm").delete()
            File(dbFile.parent, "${dbFile.name}-journal").delete()

            try {
                candidate.copyTo(dbFile, overwrite = true)
            } catch (t: Throwable) {
                if (rollback.exists()) rollback.copyTo(dbFile, overwrite = true)
                throw t
            }

            RestoreResult.Success
        } catch (t: Exception) {
            RestoreResult.Failure(t.message ?: "Erreur inconnue lors de la restauration")
        } finally {
            candidate.delete()
        }
    }

    /** Returns an error message, or null when the file is a sound Snapfacture database. */
    private fun validateCandidate(file: File): String? {
        file.inputStream().use {
            val header = ByteArray(16)
            val read = it.read(header)
            if (read < 16 || !String(header, 0, 15, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                return "Le fichier sélectionné n'est pas une sauvegarde Snapfacture valide."
            }
        }
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val integrity = db.rawQuery("PRAGMA integrity_check", null).use { c ->
                    if (c.moveToFirst()) c.getString(0) else "corrompu"
                }
                if (!integrity.equals("ok", ignoreCase = true)) {
                    return@runCatching "Fichier corrompu (integrity_check : $integrity)."
                }
                val expectedTables = db.rawQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN ('invoices','company')",
                    null,
                ).use { c -> c.moveToFirst() && c.getInt(0) == 2 }
                if (!expectedTables) {
                    return@runCatching "Ce fichier SQLite n'est pas une base Snapfacture."
                }
                val version = db.rawQuery("PRAGMA user_version", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
                if (version > AppDatabase.SCHEMA_VERSION) {
                    return@runCatching "Cette sauvegarde vient d'une version plus récente de Snapfacture — mettez d'abord l'app à jour."
                }
                null
            }
        }.getOrElse { "Fichier illisible : ${it.message}" }
    }

    suspend fun runBackup(folderUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            if (!checkpointWal()) {
                // Copying the .db while frames sit in the WAL would silently
                // drop the most recent invoices from the backup.
                return@withContext BackupResult.Failure("Base occupée, sauvegarde reportée — réessayez dans un instant.")
            }

            val folder = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext BackupResult.Failure("Dossier introuvable")
            if (!folder.canWrite()) {
                return@withContext BackupResult.Failure("Permission d'écriture refusée sur le dossier choisi")
            }

            val fileName = "snapfacture_${stampFmt.format(Date())}.db"
            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            if (!dbFile.exists()) {
                return@withContext BackupResult.Failure("Base de données introuvable")
            }

            val backup = folder.createFile("application/octet-stream", fileName)
                ?: return@withContext BackupResult.Failure("Impossible de créer le fichier de sauvegarde")

            context.contentResolver.openOutputStream(backup.uri)?.use { out ->
                dbFile.inputStream().use { it.copyTo(out) }
            } ?: return@withContext BackupResult.Failure("Impossible d'écrire dans le dossier")

            rotateOldBackups(folder)

            val now = System.currentTimeMillis()
            prefs.markBackedUp(now)
            BackupResult.Success(fileName, now)
        } catch (t: Exception) {
            BackupResult.Failure(t.message ?: "Erreur inconnue")
        }
    }

    // Force every WAL frame back into the main .db file and clear the WAL.
    // The PRAGMA returns (busy, log, checkpointed): busy != 0 means a reader
    // blocked the checkpoint and the copy would be incomplete — retry, and
    // report failure instead of shipping a truncated backup.
    private suspend fun checkpointWal(): Boolean {
        repeat(5) { attempt ->
            val busy = runCatching {
                database.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(TRUNCATE)")
                    .use { c -> if (c.moveToFirst()) c.getInt(0) else 1 }
            }.getOrDefault(1)
            if (busy == 0) return true
            delay(100L * (attempt + 1))
        }
        return false
    }

    // The timestamped name makes lexicographic order chronological.
    private fun rotateOldBackups(folder: DocumentFile) {
        runCatching {
            folder.listFiles()
                .filter { it.name?.startsWith("snapfacture_") == true && it.name?.endsWith(".db") == true }
                .sortedByDescending { it.name }
                .drop(MAX_KEPT_BACKUPS)
                .forEach { runCatching { it.delete() } }
        }
    }

    private companion object {
        const val MAX_KEPT_BACKUPS = 30
    }
}
