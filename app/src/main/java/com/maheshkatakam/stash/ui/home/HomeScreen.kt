@file:OptIn(ExperimentalMaterial3Api::class)

package com.maheshkatakam.stash.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maheshkatakam.stash.StashApp
import com.maheshkatakam.stash.data.ItemFull
import com.maheshkatakam.stash.data.ItemKind
import com.maheshkatakam.stash.data.StashCollection
import com.maheshkatakam.stash.data.Tag
import com.maheshkatakam.stash.link.Links
import com.maheshkatakam.stash.ui.components.Actions
import com.maheshkatakam.stash.ui.components.AddLinkDialog
import com.maheshkatakam.stash.ui.components.CollectionNameDialog
import com.maheshkatakam.stash.ui.components.CollectionPickerDialog
import com.maheshkatakam.stash.ui.components.ConfirmDialog
import com.maheshkatakam.stash.ui.components.ItemCard
import kotlinx.coroutines.launch

private enum class CreateOption(val label: String, val hint: String, val icon: ImageVector) {
    NOTE("Note", "Write anything", Icons.Outlined.EditNote),
    LIST("Checklist", "To-dos, shopping, packing…", Icons.Outlined.Checklist),
    VOICE("Voice note", "Record your thoughts", Icons.Outlined.Mic),
    PHOTOS("Photos & videos", "Pick from your gallery", Icons.Outlined.AddPhotoAlternate),
    CAMERA("Take a photo", "Use the camera", Icons.Outlined.PhotoCamera),
    FILE("File", "PDFs, documents, anything", Icons.Outlined.AttachFile),
    LINK("Link", "Paste or type a web link", Icons.Outlined.Link),
}

@Composable
fun HomeScreen(app: StashApp, onOpenItem: (Long, Boolean) -> Unit, onOpenSettings: () -> Unit) {
    val vm: HomeViewModel = viewModel { HomeViewModel(app.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val place by vm.place.collectAsStateWithLifecycle()
    val type by vm.type.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val settings by app.settingsState.collectAsStateWithLifecycle()
    val grid = settings?.gridView ?: true
    val previewsOn = settings?.previews ?: true
    val files = app.repository.files

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }

    var showCreate by remember { mutableStateOf(false) }
    var linkDialogText by remember { mutableStateOf<String?>(null) }
    var newCollection by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<StashCollection?>(null) }
    var deletingCollection by remember { mutableStateOf<StashCollection?>(null) }
    var moving by remember { mutableStateOf<ItemFull?>(null) }
    var deletingForever by remember { mutableStateOf<ItemFull?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        vm.createFromUris(uris) { onOpenItem(it, false) }
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.createFromUris(uris) { onOpenItem(it, false) }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingPhoto
        pendingPhoto = null
        if (name != null) {
            if (ok) vm.createFromCameraFile(name) { onOpenItem(it, false) } else files.delete(name)
        }
    }

    fun trashWithUndo(id: Long) {
        vm.trash(id)
        scope.launch {
            val result = snackbar.showSnackbar("Moved to trash", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.restore(id)
        }
    }

    fun onCreateOption(option: CreateOption) {
        when (option) {
            CreateOption.NOTE -> vm.create(ItemKind.NOTE) { onOpenItem(it, false) }
            CreateOption.LIST -> vm.create(ItemKind.LIST) { onOpenItem(it, false) }
            CreateOption.VOICE -> vm.create(ItemKind.VOICE) { onOpenItem(it, true) }
            CreateOption.PHOTOS -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            CreateOption.CAMERA -> {
                val file = files.newFile("jpg")
                pendingPhoto = file.name
                try {
                    takePhoto.launch(files.uriFor(file.name))
                } catch (e: Exception) {
                    pendingPhoto = null
                    Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
                }
            }
            CreateOption.FILE -> pickFiles.launch(arrayOf("*/*"))
            CreateOption.LINK -> {
                val clip = clipboard.getText()?.text.orEmpty()
                linkDialogText = if (Links.firstUrl(clip) != null) clip else ""
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && (query.isNotEmpty() || place != Place.All)) {
        if (query.isNotEmpty()) vm.query.value = "" else vm.place.value = Place.All
    }

    val collectionsById = collections.associateBy { it.id }
    val placeLabel = when (val p = place) {
        Place.All -> "your stash"
        Place.Reminders -> "reminders"
        Place.Archive -> "archive"
        Place.Trash -> "trash"
        is Place.InCollection -> collectionsById[p.id]?.name ?: "collection"
        is Place.WithTag -> "#" + (tags.firstOrNull { it.id == p.id }?.name ?: "tag")
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            StashDrawer(
                place = place,
                state = state,
                collections = collections,
                tags = tags,
                onPlace = {
                    vm.place.value = it
                    vm.type.value = TypeFilter.ALL
                    scope.launch { drawerState.close() }
                },
                onNewCollection = { newCollection = true },
                onRename = { renaming = it },
                onDelete = { deletingCollection = it },
                onSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                SearchTopBar(
                    query = query,
                    onQuery = { vm.query.value = it },
                    placeholder = "Search $placeLabel",
                    grid = grid,
                    onMenu = { scope.launch { drawerState.open() } },
                    onToggleGrid = { scope.launch { app.settings.setGridView(!grid) } },
                )
            },
            floatingActionButton = {
                if (place != Place.Trash) {
                    ExtendedFloatingActionButton(
                        onClick = { showCreate = true },
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text("New") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                PlaceHeader(
                    place = place,
                    label = when (val p = place) {
                        is Place.InCollection -> collectionsById[p.id]?.let { listOf(it.emoji, it.name).filter { s -> s.isNotBlank() }.joinToString(" ") } ?: "Collection"
                        is Place.WithTag -> placeLabel
                        Place.Reminders -> "Reminders"
                        Place.Archive -> "Archive"
                        Place.Trash -> "Trash"
                        Place.All -> ""
                    },
                    trashCount = state.trashCount,
                    onEmptyTrash = { confirmEmptyTrash = true },
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(TypeFilter.entries) { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { vm.type.value = t },
                            label = { Text(t.label) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                if (!state.loading && state.pinned.isEmpty() && state.others.isEmpty()) {
                    EmptyState(place, query.isNotBlank() || type != TypeFilter.ALL)
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(if (grid) 2 else 1),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 104.dp),
                        verticalItemSpacing = 10.dp,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.pinned.isNotEmpty()) {
                            item(key = "h_pinned", span = StaggeredGridItemSpan.FullLine) { SectionLabel("Pinned") }
                            items(state.pinned, key = { it.item.id }) { full ->
                                CardWithMenu(
                                    full = full,
                                    place = place,
                                    app = app,
                                    collectionName = if (place == Place.All) full.item.collectionId?.let { collectionsById[it]?.name } else null,
                                    onOpen = { onOpenItem(full.item.id, false) },
                                    onPin = { vm.togglePin(full) },
                                    onArchive = { vm.toggleArchive(full) },
                                    onMove = { moving = full },
                                    onTrash = { trashWithUndo(full.item.id) },
                                    onRestore = { vm.restore(full.item.id) },
                                    onDeleteForever = { deletingForever = full },
                                )
                            }
                            if (state.others.isNotEmpty()) {
                                item(key = "h_others", span = StaggeredGridItemSpan.FullLine) { SectionLabel("Others") }
                            }
                        }
                        items(state.others, key = { it.item.id }) { full ->
                            CardWithMenu(
                                full = full,
                                place = place,
                                app = app,
                                collectionName = if (place == Place.All) full.item.collectionId?.let { collectionsById[it]?.name } else null,
                                onOpen = { onOpenItem(full.item.id, false) },
                                onPin = { vm.togglePin(full) },
                                onArchive = { vm.toggleArchive(full) },
                                onMove = { moving = full },
                                onTrash = { trashWithUndo(full.item.id) },
                                onRestore = { vm.restore(full.item.id) },
                                onDeleteForever = { deletingForever = full },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showCreate = false }, sheetState = sheetState) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Text(
                    "Add to Stash",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                CreateOption.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(option.label) },
                        supportingContent = { Text(option.hint) },
                        leadingContent = { Icon(option.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            showCreate = false
                            onCreateOption(option)
                        },
                    )
                }
            }
        }
    }

    linkDialogText?.let { initial ->
        AddLinkDialog(
            initial = initial,
            onDismiss = { linkDialogText = null },
            onSave = {
                vm.saveLink(it, previewsOn)
                linkDialogText = null
                scope.launch { snackbar.showSnackbar("Link saved") }
            },
        )
    }

    if (newCollection) {
        CollectionNameDialog(
            title = "New collection",
            confirmLabel = "Create",
            onDismiss = { newCollection = false },
            onSave = { name, emoji ->
                vm.createCollection(name, emoji)
                newCollection = false
                scope.launch { drawerState.close() }
            },
        )
    }

    renaming?.let { c ->
        CollectionNameDialog(
            title = "Edit collection",
            initialName = c.name,
            initialEmoji = c.emoji,
            onDismiss = { renaming = null },
            onSave = { name, emoji ->
                vm.updateCollection(c.copy(name = name.trim(), emoji = emoji.trim()))
                renaming = null
            },
        )
    }

    deletingCollection?.let { c ->
        ConfirmDialog(
            title = "Delete \"${c.name}\"?",
            message = "The collection is removed. Everything inside it stays in Stash.",
            confirmLabel = "Delete",
            onDismiss = { deletingCollection = null },
            onConfirm = {
                vm.deleteCollection(c)
                deletingCollection = null
            },
        )
    }

    moving?.let { full ->
        CollectionPickerDialog(
            collections = collections,
            currentId = full.item.collectionId,
            onDismiss = { moving = null },
            onPick = {
                vm.moveToCollection(full.item.id, it)
                moving = null
            },
            onCreate = { name, emoji ->
                vm.createCollection(name, emoji, moveItemId = full.item.id)
                moving = null
            },
        )
    }

    deletingForever?.let { full ->
        ConfirmDialog(
            title = "Delete forever?",
            message = "This item and its photos, voice notes and files will be permanently deleted. This can't be undone.",
            confirmLabel = "Delete forever",
            onDismiss = { deletingForever = null },
            onConfirm = {
                vm.deleteForever(full.item.id)
                deletingForever = null
            },
        )
    }

    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = "Empty trash?",
            message = "All ${state.trashCount} items in the trash will be permanently deleted. This can't be undone.",
            confirmLabel = "Empty trash",
            onDismiss = { confirmEmptyTrash = false },
            onConfirm = {
                vm.emptyTrash()
                confirmEmptyTrash = false
            },
        )
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    grid: Boolean,
    onMenu: () -> Unit,
    onToggleGrid: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                    }
                    IconButton(onClick = onToggleGrid) {
                        Icon(
                            if (grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                            contentDescription = if (grid) "List view" else "Grid view",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceHeader(place: Place, label: String, trashCount: Int, onEmptyTrash: () -> Unit) {
    if (place == Place.All) return
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (place == Place.Trash && trashCount > 0) {
                TextButton(onClick = onEmptyTrash) { Text("Empty trash") }
            }
        }
        if (place == Place.Trash) {
            Text(
                "Items in the trash are deleted for good after 30 days. Long-press one to restore it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyState(place: Place, filtering: Boolean) {
    val (title, message) = when {
        filtering -> "Nothing found" to "Try a different word or filter."
        place == Place.All -> "Your stash is empty" to
            "Tap New to write a note or a checklist.\n\nTo save a post, reel, link or photo from any app, tap Share and pick Stash."
        place == Place.Reminders -> "No reminders" to "Open any item and tap the alarm icon to get reminded about it."
        place == Place.Archive -> "Archive is empty" to "Archived items are hidden from your main list but kept here."
        place == Place.Trash -> "Trash is empty" to "Deleted items stay here for 30 days."
        else -> "Nothing here yet" to "Tap New to add something, or move items here with a long-press."
    }
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CardWithMenu(
    full: ItemFull,
    place: Place,
    app: StashApp,
    collectionName: String?,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        ItemCard(
            full = full,
            files = app.repository.files,
            collectionName = collectionName,
            onClick = onOpen,
            onLongClick = { menu = true },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (place == Place.Trash) {
                DropdownMenuItem(
                    text = { Text("Restore") },
                    leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                    onClick = { menu = false; onRestore() },
                )
                DropdownMenuItem(
                    text = { Text("Delete forever") },
                    leadingIcon = { Icon(Icons.Outlined.DeleteForever, null) },
                    onClick = { menu = false; onDeleteForever() },
                )
            } else {
                if (!full.item.archived) {
                    DropdownMenuItem(
                        text = { Text(if (full.item.pinned) "Unpin" else "Pin to top") },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                        onClick = { menu = false; onPin() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (full.item.archived) "Unarchive" else "Archive") },
                    leadingIcon = { Icon(if (full.item.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) },
                    onClick = { menu = false; onArchive() },
                )
                DropdownMenuItem(
                    text = { Text("Move to collection") },
                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                    onClick = { menu = false; onMove() },
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                    onClick = {
                        menu = false
                        Actions.shareItem(context, app.repository.files, full)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Move to trash") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menu = false; onTrash() },
                )
            }
        }
    }
}

@Composable
private fun StashDrawer(
    place: Place,
    state: HomeState,
    collections: List<StashCollection>,
    tags: List<Tag>,
    onPlace: (Place) -> Unit,
    onNewCollection: () -> Unit,
    onRename: (StashCollection) -> Unit,
    onDelete: (StashCollection) -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet(Modifier.width(304.dp)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                "Stash",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
            DrawerEntry("All items", Icons.Outlined.Inbox, place == Place.All, state.allCount) { onPlace(Place.All) }
            DrawerEntry("Reminders", Icons.Outlined.Alarm, place == Place.Reminders, state.reminderCount) { onPlace(Place.Reminders) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Collections",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNewCollection) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New collection")
                }
            }
            if (collections.isEmpty()) {
                Text(
                    "Group things like Recipes, Travel or AI ideas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            collections.forEach { c ->
                CollectionEntry(
                    collection = c,
                    selected = place == Place.InCollection(c.id),
                    count = state.collectionCounts[c.id] ?: 0,
                    onClick = { onPlace(Place.InCollection(c.id)) },
                    onRename = { onRename(c) },
                    onDelete = { onDelete(c) },
                )
            }

            if (tags.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Tags",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                tags.forEach { t ->
                    DrawerEntry("#${t.name}", Icons.Outlined.Tag, place == Place.WithTag(t.id), null) { onPlace(Place.WithTag(t.id)) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DrawerEntry("Archive", Icons.Outlined.Archive, place == Place.Archive, state.archiveCount) { onPlace(Place.Archive) }
            DrawerEntry("Trash", Icons.Outlined.Delete, place == Place.Trash, state.trashCount) { onPlace(Place.Trash) }
            DrawerEntry("Settings & backup", Icons.Outlined.Settings, false, null, onSettings)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DrawerEntry(label: String, icon: ImageVector, selected: Boolean, count: Int?, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        badge = { if (count != null && count > 0) Text("$count") },
    )
}

@Composable
private fun CollectionEntry(
    collection: StashCollection,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    NavigationDrawerItem(
        label = { Text(collection.name, maxLines = 1) },
        selected = selected,
        onClick = onClick,
        icon = {
            if (collection.emoji.isNotBlank()) {
                Text(collection.emoji, style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(Icons.Outlined.Folder, contentDescription = null)
            }
        },
        badge = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (count > 0) Text("$count")
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Collection options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        },
    )
}
