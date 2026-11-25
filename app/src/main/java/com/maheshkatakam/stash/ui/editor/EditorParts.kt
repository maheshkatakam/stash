@file:OptIn(ExperimentalMaterial3Api::class)

package com.maheshkatakam.stash.ui.editor

import android.media.MediaPlayer
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.maheshkatakam.stash.audio.VoiceRecorder
import com.maheshkatakam.stash.data.Attachment
import com.maheshkatakam.stash.data.FileStore
import com.maheshkatakam.stash.ui.components.Format
import kotlinx.coroutines.delay
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Inline player for a voice note or audio file. */
@Composable
fun AudioRow(file: File, durationMs: Long, label: String, onDelete: (() -> Unit)?) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(durationMs) }

    DisposableEffect(file) {
        onDispose {
            player?.release()
            player = null
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
            delay(200)
        }
    }

    fun ensurePlayer(): MediaPlayer? {
        player?.let { return it }
        return try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    playing = false
                    position = 0L
                }
                if (total <= 0) total = duration.toLong()
            }.also { player = it }
        } catch (e: Exception) {
            null
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = {
                val p = ensurePlayer() ?: return@FilledIconButton
                if (playing) {
                    p.pause()
                    playing = false
                } else {
                    p.start()
                    playing = true
                }
            }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play")
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Slider(
                    value = if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    onValueChange = { f ->
                        val p = ensurePlayer() ?: return@Slider
                        val target = (f * total).toLong()
                        p.seekTo(target.toInt())
                        position = target
                    },
                    modifier = Modifier.height(24.dp),
                )
            }
            Text(
                "${Format.duration(position)} / ${Format.duration(total)}",
                style = MaterialTheme.typography.labelSmall,
            )
            if (onDelete != null) {
                IconButton(onClick = {
                    player?.release()
                    player = null
                    playing = false
                    onDelete()
                }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete voice note") }
            }
        }
    }
}

@Composable
fun FileRow(attachment: Attachment, onOpen: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(attachment.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(Format.size(attachment.sizeBytes)) },
        leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Remove file") }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

/** Full-screen photo viewer: swipe between photos, pinch or double-tap to zoom. */
@Composable
fun ImageViewer(
    images: List<Attachment>,
    files: FileStore,
    startIndex: Int,
    onDismiss: () -> Unit,
    onShare: (Attachment) -> Unit,
    onDelete: (Attachment) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))) { images.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(files.file(images[page].fileName))
            }
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
                Text(
                    "${pager.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { images.getOrNull(pager.currentPage)?.let(onShare) }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share photo", tint = Color.White)
                }
                IconButton(onClick = { images.getOrNull(pager.currentPage)?.let(onDelete) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete photo", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .pointerInput(Unit) {
                // Only takes over the gesture while zooming, so swiping between photos keeps working.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zooming = event.changes.size > 1
                        if (zooming || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/** Records a voice note. Save keeps it, Discard throws it away. */
@Composable
fun RecorderDialog(files: FileStore, onSaved: (fileName: String, durationMs: Long) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    val file = remember { files.newFile("m4a") }
    var elapsed by remember { mutableLongStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        if (!recorder.start(file)) failed = true
        onDispose { if (recorder.isRecording) recorder.cancel() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            elapsed = recorder.elapsedMs()
            level = recorder.level()
            delay(100)
        }
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = { Text(if (failed) "Microphone unavailable" else "Recording…") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (failed) {
                    Text("Stash couldn't start the microphone. Another app may be using it.")
                } else {
                    Text(Format.duration(elapsed), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { level },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (!failed) {
                Button(onClick = {
                    val duration = recorder.stop()
                    if (duration != null && file.exists()) onSaved(file.name, duration) else onCancel()
                }) {
                    Icon(Icons.Outlined.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                recorder.cancel()
                file.delete()
                onCancel()
            }) { Text(if (failed) "Close" else "Discard") }
        },
    )
}

/** Quick picks (later today, tomorrow, weekend, next week) or a custom date and time. */
@Composable
fun ReminderDialog(current: Long?, onSet: (Long) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val zone = ZoneId.systemDefault()

    when (step) {
        0 -> {
            val options = remember { quickReminderOptions(zone) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Remind me") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (current != null) {
                            Text(
                                "Currently: ${Format.reminder(context, current)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        options.forEach { (label, at) ->
                            ReminderOption(label, Format.reminder(context, at)) { onSet(at) }
                        }
                        ReminderOption("Pick a date & time", null) { step = 1 }
                    }
                },
                confirmButton = {
                    if (current != null) TextButton(onClick = onRemove) { Text("Remove reminder") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        1 -> {
            val todayUtc = LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val dateState = rememberDatePickerState(
                initialSelectedDateMillis = todayUtc,
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
                },
            )
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = {
                        dateState.selectedDateMillis?.let {
                            pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            step = 2
                        }
                    }) { Text("Next") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            ) {
                DatePicker(state = dateState)
            }
        }
        else -> {
            val timeState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = DateFormat.is24HourFormat(context))
            val date = pickedDate ?: LocalDate.now(zone)
            val at = date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant().toEpochMilli()
            val inFuture = at > System.currentTimeMillis()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Pick a time") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TimePicker(state = timeState)
                        if (!inFuture) {
                            Text(
                                "That time has already passed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = { TextButton(enabled = inFuture, onClick = { onSet(at) }) { Text("Set reminder") } },
                dismissButton = { TextButton(onClick = { step = 1 }) { Text("Back") } },
            )
        }
    }
}

@Composable
private fun ReminderOption(label: String, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun quickReminderOptions(zone: ZoneId): List<Pair<String, Long>> {
    val now = ZonedDateTime.now(zone)
    fun at(day: ZonedDateTime, hour: Int) = day.withHour(hour).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
    return buildList {
        if (now.hour < 17) add("Later today" to at(now, 18))
        add("Tomorrow morning" to at(now.plusDays(1), 9))
        if (now.dayOfWeek != DayOfWeek.SATURDAY && now.dayOfWeek != DayOfWeek.SUNDAY) {
            add("This weekend" to at(now.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)), 9))
        }
        add("Next week" to at(now.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), 9))
    }
}
