package com.maheshkatakam.stash.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshkatakam.stash.data.AttachmentKind
import com.maheshkatakam.stash.data.CaptureDraft
import com.maheshkatakam.stash.data.ItemFull
import com.maheshkatakam.stash.data.ItemKind
import com.maheshkatakam.stash.data.StashCollection
import com.maheshkatakam.stash.data.StashRepository
import com.maheshkatakam.stash.data.Tag
import com.maheshkatakam.stash.data.isList
import com.maheshkatakam.stash.data.matchesQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which part of Stash the home screen shows. */
sealed interface Place {
    data object All : Place
    data object Reminders : Place
    data object Archive : Place
    data object Trash : Place
    data class InCollection(val id: Long) : Place
    data class WithTag(val id: Long) : Place
}

enum class TypeFilter(val label: String) {
    ALL("All"),
    NOTES("Notes"),
    LISTS("Lists"),
    LINKS("Links"),
    IMAGES("Photos"),
    VOICE("Voice"),
    FILES("Files"),
}

data class HomeState(
    val loading: Boolean = true,
    val pinned: List<ItemFull> = emptyList(),
    val others: List<ItemFull> = emptyList(),
    val allCount: Int = 0,
    val reminderCount: Int = 0,
    val archiveCount: Int = 0,
    val trashCount: Int = 0,
    val collectionCounts: Map<Long, Int> = emptyMap(),
)

class HomeViewModel(private val repo: StashRepository) : ViewModel() {
    val place = MutableStateFlow<Place>(Place.All)
    val type = MutableStateFlow(TypeFilter.ALL)
    val query = MutableStateFlow("")

    val collections: StateFlow<List<StashCollection>> =
        repo.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags: StateFlow<List<Tag>> =
        repo.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<HomeState> = combine(repo.items, place, type, query) { items, p, t, q -> build(items, p, t, q) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun build(items: List<ItemFull>, place: Place, type: TypeFilter, query: String): HomeState {
        val live = items.filter { it.item.trashedAt == null }
        val active = live.filter { !it.item.archived }
        val base = when (place) {
            Place.All -> active
            Place.Reminders -> live.filter { it.item.reminderAt != null }.sortedBy { it.item.reminderAt }
            Place.Archive -> live.filter { it.item.archived }
            Place.Trash -> items.filter { it.item.trashedAt != null }.sortedByDescending { it.item.trashedAt }
            is Place.InCollection -> active.filter { it.item.collectionId == place.id }
            is Place.WithTag -> active.filter { full -> full.tags.any { it.id == place.id } }
        }
        val filtered = base.filter { matchesType(it, type) && it.matchesQuery(query) }
        val splitPinned = place is Place.All || place is Place.InCollection || place is Place.WithTag
        val pinned = if (splitPinned) filtered.filter { it.item.pinned } else emptyList()
        val others = if (splitPinned) filtered.filter { !it.item.pinned } else filtered
        return HomeState(
            loading = false,
            pinned = pinned,
            others = others,
            allCount = active.size,
            reminderCount = live.count { it.item.reminderAt != null },
            archiveCount = live.count { it.item.archived },
            trashCount = items.size - live.size,
            collectionCounts = active.mapNotNull { it.item.collectionId }.groupingBy { it }.eachCount(),
        )
    }

    private fun matchesType(full: ItemFull, type: TypeFilter): Boolean = when (type) {
        TypeFilter.ALL -> true
        TypeFilter.NOTES -> !full.isList && full.item.url == null &&
            (full.item.kind == ItemKind.NOTE || full.item.body.isNotBlank())
        TypeFilter.LISTS -> full.isList
        TypeFilter.LINKS -> full.item.url != null
        TypeFilter.IMAGES -> full.attachments.any { it.kind == AttachmentKind.IMAGE || it.kind == AttachmentKind.VIDEO }
        TypeFilter.VOICE -> full.attachments.any { it.kind == AttachmentKind.AUDIO }
        TypeFilter.FILES -> full.attachments.any { it.kind == AttachmentKind.FILE }
    }

    private fun currentCollectionId(): Long? = (place.value as? Place.InCollection)?.id

    fun create(kind: ItemKind, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val id = repo.createItem(kind, collectionId = currentCollectionId(), withEmptyEntry = kind == ItemKind.LIST)
        onCreated(id)
    }

    fun createFromUris(uris: List<Uri>, onCreated: (Long) -> Unit) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        val imported = uris.mapNotNull { repo.files.importUri(it) }
        if (imported.isEmpty()) return@launch
        val id = repo.saveCapture(CaptureDraft(files = imported, collectionId = currentCollectionId()), fetchPreview = false)
        onCreated(id)
    }

    fun createFromCameraFile(fileName: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val file = repo.files.file(fileName)
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return@launch
        }
        val imported = repo.files.importedFromLocal(file, "Photo", "image/jpeg")
        val id = repo.saveCapture(CaptureDraft(files = listOf(imported), collectionId = currentCollectionId()), fetchPreview = false)
        onCreated(id)
    }

    fun saveLink(text: String, fetchPreview: Boolean) = viewModelScope.launch {
        repo.saveLink(text, currentCollectionId(), fetchPreview)
    }

    fun togglePin(full: ItemFull) = viewModelScope.launch { repo.setPinned(full.item.id, !full.item.pinned) }
    fun toggleArchive(full: ItemFull) = viewModelScope.launch { repo.setArchived(full.item.id, !full.item.archived) }
    fun moveToCollection(id: Long, collectionId: Long?) = viewModelScope.launch { repo.setCollection(id, collectionId) }
    fun trash(id: Long) = viewModelScope.launch { repo.moveToTrash(id) }
    fun restore(id: Long) = viewModelScope.launch { repo.restore(id) }
    fun deleteForever(id: Long) = viewModelScope.launch { repo.deleteForever(id) }
    fun emptyTrash() = viewModelScope.launch { repo.emptyTrash() }

    fun createCollection(name: String, emoji: String, moveItemId: Long? = null) = viewModelScope.launch {
        val id = repo.createCollection(name, emoji)
        if (moveItemId != null) repo.setCollection(moveItemId, id) else place.value = Place.InCollection(id)
    }

    fun updateCollection(collection: StashCollection) = viewModelScope.launch { repo.updateCollection(collection) }

    fun deleteCollection(collection: StashCollection) = viewModelScope.launch {
        if (place.value == Place.InCollection(collection.id)) place.value = Place.All
        repo.deleteCollection(collection.id)
    }
}
