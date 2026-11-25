package com.maheshkatakam.stash.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import com.maheshkatakam.stash.BuildConfig
import com.maheshkatakam.stash.MainActivity
import com.maheshkatakam.stash.data.FileStore
import com.maheshkatakam.stash.data.StashDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ExportResult(val items: Int, val files: Int, val bytes: Long)
data class RestoreInfo(val items: Int, val files: Int, val createdAt: Long)

/**
 * One-file backup for moving to a new phone: a .zip with the database and every photo,
 * voice note and file. Restoring replaces everything in Stash with the backup's contents.
 */
object BackupManager {
    private const val PENDING_DIR = "restore_pending"
    private const val READY_MARKER = "READY"

    suspend fun export(context: Context, db: StashDatabase, uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        // Fold the write-ahead log into the main database file so one file holds everything.
        runCatching { db.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).use { it.moveToFirst() } }
        val itemCount = db.dao().countItems()
        val dbFile = context.getDatabasePath(StashDatabase.NAME)
        val wal = File(dbFile.path + "-wal")
        val media = File(context.filesDir, FileStore.MEDIA_DIR).listFiles()?.filter { it.isFile }.orEmpty()

        val resolver = context.contentResolver
        val stream: OutputStream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri)
            ?: throw IllegalStateException("Couldn't write to that location.")

        var bytes = 0L
        ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
            val manifest = JSONObject()
                .put("app", "stash")
                .put("dbVersion", StashDatabase.VERSION)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("items", itemCount)
                .put("files", media.size)
                .put("createdAt", System.currentTimeMillis())
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray())
            zip.closeEntry()

            fun add(name: String, file: File) {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                bytes += file.length()
            }
            add("db/${StashDatabase.NAME}", dbFile)
            if (wal.exists() && wal.length() > 0) add("db/${StashDatabase.NAME}-wal", wal)
            media.forEach { add("media/${it.name}", it) }
        }
        ExportResult(itemCount, media.size, bytes)
    }

    /**
     * Unpacks and checks a backup into a holding folder. The swap happens at the next app start
     * (before the database opens), which [restartApp] triggers right away.
     */
    suspend fun stageRestore(context: Context, uri: Uri): RestoreInfo = withContext(Dispatchers.IO) {
        val pending = File(context.filesDir, PENDING_DIR)
        pending.deleteRecursively()
        pending.mkdirs()
        try {
            var manifest: JSONObject? = null
            var hasDb = false
            var fileCount = 0
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Couldn't open that file.")
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    val target: File? = when {
                        name == "manifest.json" -> {
                            manifest = JSONObject(zip.readBytes().decodeToString())
                            null
                        }
                        name == "db/${StashDatabase.NAME}" -> {
                            hasDb = true
                            File(pending, name)
                        }
                        name == "db/${StashDatabase.NAME}-wal" -> File(pending, name)
                        name.startsWith("media/") && isSafeName(name.removePrefix("media/")) -> {
                            fileCount++
                            File(pending, name)
                        }
                        else -> null
                    }
                    if (target != null) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val m = manifest ?: throw IllegalArgumentException("This isn't a Stash backup file.")
            if (m.optString("app") != "stash" || !hasDb) throw IllegalArgumentException("This isn't a Stash backup file.")
            if (m.optInt("dbVersion", 1) > StashDatabase.VERSION) {
                throw IllegalArgumentException("This backup was made by a newer version of Stash. Update the app first, then restore.")
            }
            File(pending, READY_MARKER).writeText("1")
            RestoreInfo(m.optInt("items"), fileCount, m.optLong("createdAt"))
        } catch (e: Exception) {
            pending.deleteRecursively()
            throw e
        }
    }

    private fun isSafeName(name: String) =
        name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != ".."

    /** Runs at app start, before the database is opened. */
    fun applyPendingRestore(context: Context) {
        val pending = File(context.filesDir, PENDING_DIR)
        if (!pending.exists()) return
        if (!File(pending, READY_MARKER).exists()) {
            pending.deleteRecursively()
            return
        }
        try {
            val dbFile = context.getDatabasePath(StashDatabase.NAME)
            dbFile.parentFile?.mkdirs()
            listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            File(pending, "db/${StashDatabase.NAME}").copyTo(dbFile, overwrite = true)
            val stagedWal = File(pending, "db/${StashDatabase.NAME}-wal")
            if (stagedWal.exists()) stagedWal.copyTo(File(dbFile.path + "-wal"), overwrite = true)

            val media = File(context.filesDir, FileStore.MEDIA_DIR)
            media.deleteRecursively()
            media.mkdirs()
            File(pending, "media").listFiles()?.forEach { f ->
                val dest = File(media, f.name)
                if (!f.renameTo(dest)) f.copyTo(dest, overwrite = true)
            }
        } finally {
            pending.deleteRecursively()
        }
    }

    fun restartApp(activity: Activity) {
        val intent = Intent(activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
