package com.maheshkatakam.stash.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maheshkatakam.stash.CaptureActivity
import com.maheshkatakam.stash.data.Attachment
import com.maheshkatakam.stash.data.FileStore
import com.maheshkatakam.stash.data.ItemFull
import com.maheshkatakam.stash.data.plainText

object Format {
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    fun size(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024L -> "%d KB".format(bytes / 1024)
        else -> "$bytes B"
    }

    fun reminder(context: Context, at: Long): String {
        val time = DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_TIME)
        return when {
            DateUtils.isToday(at) -> "Today, $time"
            DateUtils.isToday(at - DateUtils.DAY_IN_MILLIS) -> "Tomorrow, $time"
            DateUtils.isToday(at + DateUtils.DAY_IN_MILLIS) -> "Yesterday, $time"
            else -> DateUtils.formatDateTime(
                context,
                at,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_TIME,
            )
        }
    }

    fun edited(at: Long): String {
        val now = System.currentTimeMillis()
        if (now - at < 60_000) return "Edited just now"
        return "Edited " + DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS)
    }
}

fun sourceColor(source: String): Color = when (source) {
    "Instagram" -> Color(0xFFE1306C)
    "X" -> Color(0xFF71767B)
    "YouTube" -> Color(0xFFFF0033)
    "TikTok" -> Color(0xFF25F4EE)
    "Reddit" -> Color(0xFFFF4500)
    "LinkedIn" -> Color(0xFF0A66C2)
    "Facebook" -> Color(0xFF1877F2)
    "Threads" -> Color(0xFF8E8E93)
    "GitHub" -> Color(0xFF8B5CF6)
    "Spotify" -> Color(0xFF1DB954)
    "Maps" -> Color(0xFF34A853)
    "Amazon" -> Color(0xFFFF9900)
    "Pinterest" -> Color(0xFFE60023)
    "Medium" -> Color(0xFF9CA3AF)
    else -> Color(0xFF6366F1)
}

@Composable
fun SourceBadge(source: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(sourceColor(source)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            source,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MiniChip(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

object Actions {
    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
    }

    fun openFile(context: Context, files: FileStore, attachment: Attachment) {
        val uri = files.uriFor(attachment.fileName)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, attachment.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    /** Shares an item's text plus its photos, voice notes and files to another app. */
    fun shareItem(context: Context, files: FileStore, full: ItemFull) {
        val text = full.plainText()
        val uris = full.attachments.sortedBy { it.position }.map { files.uriFor(it.fileName) }
        val intent = when {
            uris.isEmpty() -> Intent(Intent.ACTION_SEND).setType("text/plain")
            uris.size == 1 -> Intent(Intent.ACTION_SEND)
                .setType(full.attachments.first().mimeType)
                .putExtra(Intent.EXTRA_STREAM, uris.first())
            else -> Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(commonMime(full.attachments.map { it.mimeType }))
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        if (text.isNotBlank()) intent.putExtra(Intent.EXTRA_TEXT, text)
        if (uris.isNotEmpty()) {
            val clip = ClipData.newRawUri("", uris.first())
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            intent.clipData = clip
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share")
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, CaptureActivity::class.java)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun commonMime(types: List<String>): String {
        val majors = types.map { it.substringBefore('/') }.distinct()
        return if (majors.size == 1) "${majors.first()}/*" else "*/*"
    }
}
