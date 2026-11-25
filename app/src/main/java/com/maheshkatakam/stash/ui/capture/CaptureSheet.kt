@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.maheshkatakam.stash.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.maheshkatakam.stash.CaptureMode
import com.maheshkatakam.stash.CaptureViewModel
import com.maheshkatakam.stash.StashApp
import com.maheshkatakam.stash.data.AttachmentKind
import com.maheshkatakam.stash.link.Links
import com.maheshkatakam.stash.ui.components.CollectionNameDialog
import com.maheshkatakam.stash.ui.components.Format
import com.maheshkatakam.stash.ui.components.SourceBadge
import com.maheshkatakam.stash.ui.components.TagDialog
import kotlinx.coroutines.delay

@Composable
fun CaptureSheet(vm: CaptureViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val collections by vm.collections.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    var collectionMenu by remember { mutableStateOf(false) }
    var newCollection by remember { mutableStateOf(false) }
    var showTag by remember { mutableStateOf(false) }
    val textFocus = remember { FocusRequester() }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (!vm.startRecording()) Toast.makeText(context, "Couldn't start the microphone", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Stash needs the microphone to record voice notes", Toast.LENGTH_LONG).show()
        }
    }
    fun record() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (!vm.startRecording()) Toast.makeText(context, "Couldn't start the microphone", Toast.LENGTH_SHORT).show()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        when (vm.mode) {
            CaptureMode.VOICE -> if (!vm.autoStartedVoice) {
                vm.autoStartedVoice = true
                record()
            }
            CaptureMode.NOTE, CaptureMode.LIST -> {
                delay(200)
                runCatching { textFocus.requestFocus() }
                keyboard?.show()
            }
            CaptureMode.SHARE -> Unit
        }
    }

    fun save() {
        vm.save { ok ->
            if (ok) Toast.makeText(context, "Saved to Stash", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!vm.saving) onClose()
            },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                // Taps inside the sheet must not close it.
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (vm.mode) {
                            CaptureMode.SHARE -> "Save to Stash"
                            CaptureMode.NOTE -> "Quick note"
                            CaptureMode.LIST -> "Quick checklist"
                            CaptureMode.VOICE -> "Voice note"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                }

                vm.url?.let { url -> LinkPreviewCard(vm, url) }

                if (vm.files.isNotEmpty() || vm.copying > 0) FilesStrip(vm)
                if (vm.failedCopies > 0) {
                    Text(
                        "${vm.failedCopies} file(s) couldn't be read and were skipped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (vm.recording) RecordingBar(vm)

                OutlinedTextField(
                    value = vm.title,
                    onValueChange = { vm.title = it },
                    label = { Text("Title (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.text,
                    onValueChange = { vm.text = it },
                    label = {
                        Text(
                            when {
                                vm.checklist -> "One item per line"
                                vm.mode == CaptureMode.SHARE -> "Note (optional)"
                                else -> "Write something…"
                            },
                        )
                    },
                    minLines = 3,
                    maxLines = 10,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(textFocus),
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = vm.checklist,
                        onClick = { vm.checklist = !vm.checklist },
                        label = { Text("Checklist") },
                        leadingIcon = { Icon(Icons.Outlined.Checklist, null, Modifier.size(18.dp)) },
                    )
                    Box {
                        val current = collections.firstOrNull { it.id == vm.collectionId }
                        AssistChip(
                            onClick = { collectionMenu = true },
                            label = {
                                Text(current?.let { listOf(it.emoji, it.name).filter { s -> s.isNotBlank() }.joinToString(" ") } ?: "Collection")
                            },
                            leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)) },
                        )
                        DropdownMenu(expanded = collectionMenu, onDismissRequest = { collectionMenu = false }) {
                            DropdownMenuItem(text = { Text("No collection") }, onClick = {
                                vm.collectionId = null
                                collectionMenu = false
                            })
                            collections.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(listOf(c.emoji, c.name).filter { it.isNotBlank() }.joinToString(" ")) },
                                    onClick = {
                                        vm.collectionId = c.id
                                        collectionMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("New collection…") },
                                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                                onClick = {
                                    collectionMenu = false
                                    newCollection = true
                                },
                            )
                        }
                    }
                    AssistChip(
                        onClick = { showTag = true },
                        label = { Text("Tag") },
                        leadingIcon = { Icon(Icons.Outlined.Tag, null, Modifier.size(18.dp)) },
                    )
                    if (!vm.recording) {
                        AssistChip(
                            onClick = { record() },
                            label = { Text("Voice") },
                            leadingIcon = { Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp)) },
                        )
                    }
                    vm.tags.forEach { t ->
                        InputChip(
                            selected = false,
                            onClick = { vm.tags.remove(t) },
                            label = { Text("#$t") },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove tag", Modifier.size(16.dp)) },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    val ready = vm.copying == 0 && !vm.saving && (vm.hasContent || vm.recording)
                    Button(onClick = { save() }, enabled = ready) {
                        if (vm.saving || vm.copying > 0) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (vm.copying > 0) "Copying…" else "Save")
                    }
                }
            }
        }
    }

    if (newCollection) {
        CollectionNameDialog(
            title = "New collection",
            confirmLabel = "Create",
            onDismiss = { newCollection = false },
            onSave = { name, emoji ->
                vm.createCollection(name, emoji)
                newCollection = false
            },
        )
    }
    if (showTag) {
        TagDialog(
            allTags = allTags,
            currentNames = vm.tags.toList(),
            onDismiss = { showTag = false },
            onAdd = {
                vm.addTag(it)
                showTag = false
            },
        )
    }
}

@Composable
private fun LinkPreviewCard(vm: CaptureViewModel, url: String) {
    val p = vm.preview
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p?.imageUrl != null) {
                AsyncImage(
                    model = p.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
            } else if (vm.previewLoading) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SourceBadge(Links.sourceFor(url))
                val heading = p?.title
                if (heading != null) {
                    Text(heading, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val description = p?.description
                if (description != null && description != heading) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { vm.removeLink() }) {
                Icon(Icons.Filled.Close, contentDescription = "Don't save the link", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FilesStrip(vm: CaptureViewModel) {
    val app = LocalContext.current.applicationContext as StashApp
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        vm.files.forEach { f ->
            Box {
                when (f.kind) {
                    AttachmentKind.IMAGE, AttachmentKind.VIDEO -> AsyncImage(
                        model = app.repository.files.file(f.fileName),
                        contentDescription = f.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    else -> Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(width = 140.dp, height = 88.dp),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(
                                if (f.kind == AttachmentKind.AUDIO) Icons.Outlined.Mic else Icons.Outlined.Description,
                                contentDescription = null,
                            )
                            Text(
                                if (f.kind == AttachmentKind.AUDIO && f.durationMs > 0) {
                                    "${f.displayName} · ${Format.duration(f.durationMs)}"
                                } else {
                                    f.displayName
                                },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { vm.removeFile(f) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (vm.copying > 0) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun RecordingBar(vm: CaptureViewModel) {
    var elapsed by remember { mutableLongStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(vm.recordingSince) {
        while (true) {
            elapsed = SystemClock.elapsedRealtime() - vm.recordingSince
            level = vm.level()
            delay(100)
        }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Mic, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Recording  ${Format.duration(elapsed)}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { vm.stopRecording() }) {
                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Stop")
            }
        }
    }
}
