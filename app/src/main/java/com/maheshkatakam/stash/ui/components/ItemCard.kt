@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.maheshkatakam.stash.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.maheshkatakam.stash.data.AttachmentKind
import com.maheshkatakam.stash.data.FileStore
import com.maheshkatakam.stash.data.ItemFull
import com.maheshkatakam.stash.data.audios
import com.maheshkatakam.stash.data.displayTitle
import com.maheshkatakam.stash.data.docs
import com.maheshkatakam.stash.data.visuals
import com.maheshkatakam.stash.link.Links

@Composable
fun ItemCard(
    full: ItemFull,
    files: FileStore,
    collectionName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = full.item
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        val visuals = full.visuals
        val firstVisual = visuals.firstOrNull()
        val thumb: Any? = when {
            firstVisual != null -> files.file(firstVisual.fileName)
            item.linkThumb != null -> files.file(item.linkThumb)
            item.linkImageUrl != null -> item.linkImageUrl
            else -> null
        }
        if (thumb != null) {
            Box {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 240.dp),
                )
                val isVideo = firstVisual?.kind == AttachmentKind.VIDEO || (firstVisual == null && Links.looksLikeVideo(item.url))
                if (isVideo) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
                if (visuals.size > 1) {
                    Text(
                        "+${visuals.size - 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (item.url != null || item.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.url != null) {
                        SourceBadge(item.source ?: Links.sourceFor(item.url), Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (item.pinned) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val title = full.displayTitle
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val description = item.linkDescription?.takeIf { item.url != null && it != title }
            when {
                item.body.isNotBlank() -> Text(
                    item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                description != null -> Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (full.entries.isNotEmpty()) ChecklistPreview(full)

            full.audios.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Voice note" + if (a.durationMs > 0) " · ${Format.duration(a.durationMs)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            full.docs.take(3).forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(d.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (title.isBlank() && item.body.isBlank() && description == null && item.url != null) {
                Text(
                    item.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val reminderAt = item.reminderAt
            if (reminderAt != null || collectionName != null || full.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (reminderAt != null) MiniChip(Format.reminder(context, reminderAt), Icons.Outlined.Alarm)
                    if (collectionName != null) MiniChip(collectionName, Icons.Outlined.Folder)
                    full.tags.take(4).forEach { MiniChip("#${it.name}") }
                }
            }
        }
    }
}

@Composable
private fun ChecklistPreview(full: ItemFull) {
    val unchecked = full.entries.filter { !it.checked }.sortedBy { it.position }
    val checkedCount = full.entries.count { it.checked }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        unchecked.take(6).forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(e.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (unchecked.size > 6) {
            Text(
                "+${unchecked.size - 6} more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (checkedCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CheckBox,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "$checkedCount done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }
    }
}
