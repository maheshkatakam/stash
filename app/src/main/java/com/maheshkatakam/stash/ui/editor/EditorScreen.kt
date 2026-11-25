@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.maheshkatakam.stash.ui.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.maheshkatakam.stash.StashApp
import com.maheshkatakam.stash.data.AttachmentKind
import com.maheshkatakam.stash.data.ChecklistEntry
import com.maheshkatakam.stash.data.FileStore
import com.maheshkatakam.stash.data.Item
import com.maheshkatakam.stash.data.ItemFull
import com.maheshkatakam.stash.data.audios
import com.maheshkatakam.stash.data.docs
import com.maheshkatakam.stash.data.isList
import com.maheshkatakam.stash.data.visuals
import com.maheshkatakam.stash.link.Links
import com.maheshkatakam.stash.ui.components.Actions
import com.maheshkatakam.stash.ui.components.CollectionPickerDialog
import com.maheshkatakam.stash.ui.components.ConfirmDialog
import com.maheshkatakam.stash.ui.components.Format
import com.maheshkatakam.stash.ui.components.SourceBadge
import com.maheshkatakam.stash.ui.components.TagDialog

@Composable
fun EditorScreen(app: StashApp, itemId: Long, startRecording: Boolean, onBack: () -> Unit) {
    val vm: EditorViewModel = viewModel { EditorViewModel(app.repository, itemId) }
    val fullState by vm.item.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val files = app.repository.files
    val context = LocalContext.current

    var showReminder by remember { mutableStateOf(false) }
    var showCollection by remember { mutableStateOf(false) }
    var showTag by remember { mutableStateOf(false) }
    var showRecorder by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmDeleteChecked by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var showChecked by rememberSaveable { mutableStateOf(true) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var autoRecordDone by rememberSaveable { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showRecorder = true
        } else {
            Toast.makeText(context, "Stash needs the microphone to record voice notes", Toast.LENGTH_LONG).show()
        }
    }
    fun startRecorder() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            showRecorder = true
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(Unit) {
        if (startRecording && !autoRecordDone) {
            autoRecordDone = true
            startRecorder()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Turn on notifications for Stash to see reminders", Toast.LENGTH_LONG).show()
        showReminder = true
    }
    fun openReminder() {
        val needs = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else showReminder = true
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { vm.addUris(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addUris(it) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingPhoto
        pendingPhoto = null
        if (name != null) {
            if (ok) vm.addCameraPhoto(name) else files.delete(name)
        }
    }
    fun launchCamera() {
        val file = files.newFile("jpg")
        pendingPhoto = file.name
        try {
            takePhoto.launch(files.uriFor(file.name))
        } catch (e: Exception) {
            pendingPhoto = null
            Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    if (vm.missing || vm.gone) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val full = fullState ?: return
    val item = full.item
    val isList = full.isList
    val bodyFocus = remember { FocusRequester() }
    LaunchedEffect(vm.autoFocusBody) {
        if (vm.autoFocusBody) {
            runCatching { bodyFocus.requestFocus() }
            vm.autoFocusBody = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.togglePin() }) {
                        Icon(
                            if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (item.pinned) "Unpin" else "Pin",
                        )
                    }
                    IconButton(onClick = { openReminder() }) {
                        Icon(
                            if (item.reminderAt != null) Icons.Filled.Alarm else Icons.Outlined.Alarm,
                            contentDescription = "Reminder",
                        )
                    }
                    IconButton(onClick = { showCollection = true }) {
                        Icon(Icons.Outlined.Folder, contentDescription = "Collection")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        EditorMenu(
                            expanded = showMenu,
                            full = full,
                            onDismiss = { showMenu = false },
                            onShare = { Actions.shareItem(context, files, full) },
                            onOpenLink = { item.url?.let { Actions.openUrl(context, it) } },
                            onCopyLink = { item.url?.let { Actions.copy(context, "Link", it) } },
                            onRefreshPreview = {
                                vm.refreshPreview()
                                Toast.makeText(context, "Refreshing preview…", Toast.LENGTH_SHORT).show()
                            },
                            onRemoveLink = { vm.removeLink() },
                            onShowCheckboxes = { vm.showCheckboxes() },
                            onHideCheckboxes = { vm.hideCheckboxes() },
                            onUncheckAll = { vm.uncheckAll() },
                            onDeleteChecked = { confirmDeleteChecked = true },
                            onTag = { showTag = true },
                            onArchive = { vm.toggleArchive() },
                            onTrash = {
                                vm.moveToTrash()
                                Toast.makeText(context, "Moved to trash", Toast.LENGTH_SHORT).show()
                                onBack()
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        IconButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, contentDescription = "Add") }
                        DropdownMenu(expanded = showAdd, onDismissRequest = { showAdd = false }) {
                            DropdownMenuItem(
                                text = { Text("Photos & videos") },
                                leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null) },
                                onClick = {
                                    showAdd = false
                                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Take photo") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                                onClick = {
                                    showAdd = false
                                    launchCamera()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Voice note") },
                                leadingIcon = { Icon(Icons.Outlined.Mic, null) },
                                onClick = {
                                    showAdd = false
                                    startRecorder()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("File") },
                                leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                                onClick = {
                                    showAdd = false
                                    pickFiles.launch(arrayOf("*/*"))
                                },
                            )
                            if (!isList) {
                                DropdownMenuItem(
                                    text = { Text("Checkboxes") },
                                    leadingIcon = { Icon(Icons.Outlined.CheckBox, null) },
                                    onClick = {
                                        showAdd = false
                                        vm.showCheckboxes()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Tag") },
                                leadingIcon = { Icon(Icons.Outlined.Tag, null) },
                                onClick = {
                                    showAdd = false
                                    showTag = true
                                },
                            )
                        }
                    }
                    IconButton(onClick = { startRecorder() }) { Icon(Icons.Outlined.Mic, contentDescription = "Record voice note") }
                    IconButton(onClick = { launchCamera() }) { Icon(Icons.Outlined.PhotoCamera, contentDescription = "Take photo") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        Format.edited(item.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (item.trashedAt != null) {
                Banner("This item is in the trash.", "Restore") { vm.restore() }
            } else if (item.archived) {
                Banner("This item is archived.", "Unarchive") { vm.toggleArchive() }
            }

            val visuals = full.visuals
            if (visuals.isNotEmpty()) {
                VisualStrip(
                    full = full,
                    files = files,
                    onOpenImage = { attachment ->
                        val images = visuals.filter { it.kind == AttachmentKind.IMAGE }
                        viewerIndex = images.indexOfFirst { it.id == attachment.id }.coerceAtLeast(0)
                    },
                    onOpenVideo = { Actions.openFile(context, files, it) },
                )
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                if (item.url != null) {
                    LinkBlock(item = item, files = files, onOpen = { Actions.openUrl(context, item.url) })
                }

                PlainField(
                    value = vm.title,
                    onValueChange = vm::onTitle,
                    placeholder = "Title",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )

                if (!isList || vm.body.isNotEmpty()) {
                    PlainField(
                        value = vm.body,
                        onValueChange = vm::onBody,
                        placeholder = if (item.url != null || visuals.isNotEmpty()) "Add a note…" else "Note",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp).heightIn(min = 48.dp).focusRequester(bodyFocus),
                    )
                }

                if (isList) {
                    ChecklistBlock(
                        entries = full.entries,
                        focusEntryId = vm.focusEntryId,
                        onFocusHandled = { vm.focusEntryId = null },
                        showChecked = showChecked,
                        onToggleShowChecked = { showChecked = !showChecked },
                        onText = vm::onEntryText,
                        onChecked = { e, c -> vm.setChecked(e, c) },
                        onSplit = { e, rest -> vm.addEntriesAfter(e, rest) },
                        onDelete = { e, focusAfter -> vm.deleteEntry(e, focusAfter) },
                        onAdd = { vm.addEntryAtEnd() },
                    )
                }

                full.audios.forEach { a ->
                    key(a.id) {
                        AudioRow(
                            file = files.file(a.fileName),
                            durationMs = a.durationMs,
                            label = a.displayName,
                            onDelete = { vm.removeAttachment(a) },
                        )
                    }
                }
                full.docs.forEach { d ->
                    key(d.id) {
                        FileRow(d, onOpen = { Actions.openFile(context, files, d) }, onDelete = { vm.removeAttachment(d) })
                    }
                }

                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.reminderAt?.let { at ->
                        AssistChip(
                            onClick = { openReminder() },
                            label = { Text(Format.reminder(context, at)) },
                            leadingIcon = { Icon(Icons.Outlined.Alarm, null, Modifier.size(18.dp)) },
                        )
                    }
                    item.collectionId?.let { cid ->
                        collections.firstOrNull { it.id == cid }?.let { c ->
                            AssistChip(
                                onClick = { showCollection = true },
                                label = { Text(listOf(c.emoji, c.name).filter { it.isNotBlank() }.joinToString(" ")) },
                                leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                    full.tags.forEach { t ->
                        InputChip(
                            selected = false,
                            onClick = { vm.removeTag(t) },
                            label = { Text("#${t.name}") },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove tag", Modifier.size(16.dp)) },
                        )
                    }
                    AssistChip(
                        onClick = { showTag = true },
                        label = { Text("Tag") },
                        leadingIcon = { Icon(Icons.Outlined.Tag, null, Modifier.size(18.dp)) },
                    )
                }
                Spacer(Modifier.height(120.dp))
            }
        }
    }

    if (showRecorder) {
        RecorderDialog(
            files = files,
            onSaved = { name, duration ->
                vm.addVoice(name, duration)
                showRecorder = false
            },
            onCancel = { showRecorder = false },
        )
    }

    if (showReminder) {
        ReminderDialog(
            current = item.reminderAt,
            onSet = {
                vm.setReminder(it)
                showReminder = false
            },
            onRemove = {
                vm.setReminder(null)
                showReminder = false
            },
            onDismiss = { showReminder = false },
        )
    }

    if (showCollection) {
        CollectionPickerDialog(
            collections = collections,
            currentId = item.collectionId,
            onDismiss = { showCollection = false },
            onPick = {
                vm.setCollection(it)
                showCollection = false
            },
            onCreate = { name, emoji ->
                vm.createCollection(name, emoji)
                showCollection = false
            },
        )
    }

    if (showTag) {
        TagDialog(
            allTags = allTags,
            currentNames = full.tags.map { it.name },
            onDismiss = { showTag = false },
            onAdd = {
                vm.addTag(it)
                showTag = false
            },
        )
    }

    if (confirmDeleteChecked) {
        ConfirmDialog(
            title = "Delete checked items?",
            message = "All ticked items in this list will be removed.",
            confirmLabel = "Delete",
            onDismiss = { confirmDeleteChecked = false },
            onConfirm = {
                vm.deleteChecked()
                confirmDeleteChecked = false
            },
        )
    }

    viewerIndex?.let { index ->
        val images = full.visuals.filter { it.kind == AttachmentKind.IMAGE }
        if (images.isNotEmpty()) {
            ImageViewer(
                images = images,
                files = files,
                startIndex = index,
                onDismiss = { viewerIndex = null },
                onShare = { a ->
                    val uri = files.uriFor(a.fileName)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType(a.mimeType)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, "Share photo"))
                },
                onDelete = { a ->
                    vm.removeAttachment(a)
                    if (images.size <= 1) viewerIndex = null
                },
            )
        }
    }
}

@Composable
private fun EditorMenu(
    expanded: Boolean,
    full: ItemFull,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpenLink: () -> Unit,
    onCopyLink: () -> Unit,
    onRefreshPreview: () -> Unit,
    onRemoveLink: () -> Unit,
    onShowCheckboxes: () -> Unit,
    onHideCheckboxes: () -> Unit,
    onUncheckAll: () -> Unit,
    onDeleteChecked: () -> Unit,
    onTag: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
) {
    val item = full.item
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuEntry(onDismiss, "Share", Icons.Outlined.Share, onShare)
        if (item.url != null) {
            MenuEntry(onDismiss, "Open link", Icons.AutoMirrored.Outlined.OpenInNew, onOpenLink)
            MenuEntry(onDismiss, "Copy link", Icons.Outlined.ContentCopy, onCopyLink)
            MenuEntry(onDismiss, "Refresh preview", Icons.Outlined.Refresh, onRefreshPreview)
            MenuEntry(onDismiss, "Remove link", Icons.Outlined.LinkOff, onRemoveLink)
        }
        HorizontalDivider()
        if (full.isList) {
            MenuEntry(onDismiss, "Hide checkboxes", Icons.AutoMirrored.Outlined.Notes, onHideCheckboxes)
            if (full.entries.any { it.checked }) {
                MenuEntry(onDismiss, "Uncheck all", Icons.Outlined.RemoveDone, onUncheckAll)
                MenuEntry(onDismiss, "Delete checked items", Icons.Outlined.DeleteSweep, onDeleteChecked)
            }
        } else {
            MenuEntry(onDismiss, "Show checkboxes", Icons.Outlined.CheckBox, onShowCheckboxes)
        }
        MenuEntry(onDismiss, "Add tag", Icons.Outlined.Tag, onTag)
        HorizontalDivider()
        MenuEntry(
            onDismiss,
            if (item.archived) "Unarchive" else "Archive",
            if (item.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
            onArchive,
        )
        if (item.trashedAt == null) MenuEntry(onDismiss, "Move to trash", Icons.Outlined.Delete, onTrash)
    }
}

@Composable
private fun MenuEntry(onDismiss: () -> Unit, label: String, icon: ImageVector, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = {
            onDismiss()
            action()
        },
    )
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                inner()
            }
        },
    )
}

@Composable
private fun VisualStrip(
    full: ItemFull,
    files: FileStore,
    onOpenImage: (com.maheshkatakam.stash.data.Attachment) -> Unit,
    onOpenVideo: (com.maheshkatakam.stash.data.Attachment) -> Unit,
) {
    val visuals = full.visuals
    if (visuals.size == 1) {
        val a = visuals.first()
        VisualTile(
            a = a,
            files = files,
            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
            onClick = { if (a.kind == AttachmentKind.VIDEO) onOpenVideo(a) else onOpenImage(a) },
        )
    } else {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visuals.forEach { a ->
                VisualTile(
                    a = a,
                    files = files,
                    modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)),
                    onClick = { if (a.kind == AttachmentKind.VIDEO) onOpenVideo(a) else onOpenImage(a) },
                )
            }
        }
    }
}

@Composable
private fun VisualTile(
    a: com.maheshkatakam.stash.data.Attachment,
    files: FileStore,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = files.file(a.fileName),
            contentDescription = a.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(),
        )
        if (a.kind == AttachmentKind.VIDEO) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play video", tint = Color.White)
            }
        }
    }
}

@Composable
private fun LinkBlock(item: Item, files: FileStore, onOpen: () -> Unit) {
    val url = item.url ?: return
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val thumb: Any? = item.linkThumb?.let { files.file(it) } ?: item.linkImageUrl
        if (thumb != null) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                )
                if (Links.looksLikeVideo(url)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SourceBadge(item.source ?: Links.sourceFor(url))
            item.linkTitle?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            item.linkDescription?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ChecklistBlock(
    entries: List<ChecklistEntry>,
    focusEntryId: Long?,
    onFocusHandled: () -> Unit,
    showChecked: Boolean,
    onToggleShowChecked: () -> Unit,
    onText: (Long, String) -> Unit,
    onChecked: (ChecklistEntry, Boolean) -> Unit,
    onSplit: (ChecklistEntry, List<String>) -> Unit,
    onDelete: (ChecklistEntry, Long?) -> Unit,
    onAdd: () -> Unit,
) {
    val unchecked = entries.filter { !it.checked }.sortedBy { it.position }
    val checked = entries.filter { it.checked }.sortedBy { it.position }
    Column(Modifier.padding(vertical = 4.dp)) {
        unchecked.forEachIndexed { i, e ->
            key(e.id) {
                EntryRow(
                    entry = e,
                    requestFocus = focusEntryId == e.id,
                    onFocusHandled = onFocusHandled,
                    onText = { onText(e.id, it) },
                    onChecked = { onChecked(e, it) },
                    onSplit = { onSplit(e, it) },
                    onDelete = { onDelete(e, unchecked.getOrNull(i - 1)?.id) },
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAdd)
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(18.dp))
            Text("Add item", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (checked.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleShowChecked)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (showChecked) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(18.dp))
                Text(
                    "${checked.size} checked item${if (checked.size == 1) "" else "s"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showChecked) {
                checked.forEach { e ->
                    key(e.id) {
                        EntryRow(
                            entry = e,
                            requestFocus = false,
                            onFocusHandled = {},
                            onText = { onText(e.id, it) },
                            onChecked = { onChecked(e, it) },
                            onSplit = {},
                            onDelete = { onDelete(e, null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: ChecklistEntry,
    requestFocus: Boolean,
    onFocusHandled: () -> Unit,
    onText: (String) -> Unit,
    onChecked: (Boolean) -> Unit,
    onSplit: (List<String>) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(entry.id) { mutableStateOf(entry.text) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focus.requestFocus() }
            onFocusHandled()
        }
    }
    val baseStyle = MaterialTheme.typography.bodyLarge
    val style = if (entry.checked) {
        baseStyle.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
        )
    } else {
        baseStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = entry.checked, onCheckedChange = onChecked)
        BasicTextField(
            value = text,
            onValueChange = { v ->
                if ('\n' in v) {
                    val parts = v.split('\n')
                    text = parts.first()
                    onText(parts.first())
                    if (!entry.checked) onSplit(parts.drop(1).map { it.trim() })
                } else {
                    text = v
                    onText(v)
                }
            },
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .onPreviewKeyEvent { ev ->
                    if (ev.key == Key.Backspace && ev.type == KeyEventType.KeyDown && text.isEmpty()) {
                        onDelete()
                        true
                    } else {
                        false
                    }
                },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("List item", style = baseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    inner()
                }
            },
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Delete item",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
