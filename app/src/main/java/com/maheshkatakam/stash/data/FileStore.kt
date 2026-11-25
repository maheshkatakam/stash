package com.maheshkatakam.stash.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.maheshkatakam.stash.link.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** A file copied into Stash's own storage, ready to be attached to an item. */
data class ImportedFile(
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: AttachmentKind,
    val durationMs: Long = 0,
)

/** Everything Stash keeps (photos, videos, voice notes, files, link pictures) lives in filesDir/media. */
class FileStore(private val context: Context) {

    val mediaDir: File
        get() = File(context.filesDir, MEDIA_DIR).apply { mkdirs() }

    fun file(name: String): File = File(mediaDir, name)

    fun newFile(extension: String): File {
        val ext = extension.trim().trimStart('.').ifBlank { "bin" }
        return File(mediaDir, "${UUID.randomUUID()}.$ext")
    }

    fun uriFor(name: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file(name))

    fun delete(name: String?) {
        if (name.isNullOrBlank()) return
        runCatching { file(name).delete() }
    }

    /** Copies a shared or picked file (content:// or file://) into Stash. Returns null if it can't be read. */
    suspend fun importUri(uri: Uri): ImportedFile? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName: String? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) displayName = c.getString(0)
            }
        }
        if (displayName == null) displayName = uri.lastPathSegment?.substringAfterLast('/')
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
            ?: mimeFromName(displayName)
            ?: "application/octet-stream"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: displayName?.substringAfterLast('.', "")?.takeIf { it.length in 1..6 }
            ?: "bin"
        val out = newFile(ext)
        val copied = try {
            resolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
        if (!copied) {
            out.delete()
            return@withContext null
        }
        val kind = kindForMime(mime)
        ImportedFile(
            fileName = out.name,
            displayName = displayName ?: out.name,
            mimeType = mime,
            sizeBytes = out.length(),
            kind = kind,
            durationMs = if (kind == AttachmentKind.AUDIO || kind == AttachmentKind.VIDEO) durationOf(out) else 0L,
        )
    }

    fun importedFromLocal(file: File, displayName: String, mime: String, durationMs: Long = 0): ImportedFile =
        ImportedFile(
            fileName = file.name,
            displayName = displayName,
            mimeType = mime,
            sizeBytes = file.length(),
            kind = kindForMime(mime),
            durationMs = durationMs,
        )

    fun durationOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Saves a link's preview picture locally (Instagram/X picture links expire after a while). */
    suspend fun downloadImage(url: String): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        var out: File? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", Http.BROWSER_UA)
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val type = conn.contentType.orEmpty()
            if (type.isNotEmpty() && !type.startsWith("image")) return@withContext null
            val target = newFile("img")
            out = target
            var total = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_THUMB_BYTES) throw IllegalStateException("too big")
                        output.write(buffer, 0, n)
                    }
                }
            }
            if (total == 0L) {
                target.delete()
                null
            } else {
                target.name
            }
        } catch (e: Exception) {
            out?.delete()
            null
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        const val MEDIA_DIR = "media"
        private const val MAX_THUMB_BYTES = 10L * 1024 * 1024

        fun kindForMime(mime: String): AttachmentKind = when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            mime.startsWith("video/") -> AttachmentKind.VIDEO
            mime.startsWith("audio/") -> AttachmentKind.AUDIO
            else -> AttachmentKind.FILE
        }

        fun mimeFromName(name: String?): String? {
            val ext = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
    }
}
