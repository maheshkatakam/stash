package com.maheshkatakam.stash.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshkatakam.stash.data.ChecklistEntry
import com.maheshkatakam.stash.data.ItemFull
import com.maheshkatakam.stash.data.StashCollection
import com.maheshkatakam.stash.data.StashRepository
import com.maheshkatakam.stash.data.Tag
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditorViewModel(private val repo: StashRepository, val itemId: Long) : ViewModel() {

    val item: StateFlow<ItemFull?> = repo.observeItem(itemId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val collections: StateFlow<List<StashCollection>> =
        repo.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allTags: StateFlow<List<Tag>> =
        repo.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Title and note text are edited locally and saved shortly after you stop typing.
    var title by mutableStateOf("")
        private set
    var body by mutableStateOf("")
        private set
    var loaded by mutableStateOf(false)
        private set
    var missing by mutableStateOf(false)
        private set

    /** The checklist entry that should take keyboard focus next (after Enter or "Add item"). */
    var focusEntryId by mutableStateOf<Long?>(null)

    private var textJob: Job? = null
    private val pendingEntryText = mutableMapOf<Long, String>()
    private val entryJobs = mutableMapOf<Long, Job>()

    init {
        viewModelScope.launch {
            val full = repo.getItem(itemId)
            if (full == null) {
                missing = true
            } else {
                title = full.item.title
                body = full.item.body
                loaded = true
            }
        }
    }

    fun onTitle(value: String) {
        title = value
        scheduleTextSave()
    }

    fun onBody(value: String) {
        body = value
        scheduleTextSave()
    }

    private fun scheduleTextSave() {
        if (!loaded) return
        textJob?.cancel()
        textJob = viewModelScope.launch {
            delay(400)
            repo.updateText(itemId, title, body)
        }
    }

    private suspend fun flushText() {
        textJob?.cancel()
        if (loaded) repo.updateText(itemId, title, body)
    }

    fun onEntryText(entryId: Long, text: String) {
        pendingEntryText[entryId] = text
        entryJobs[entryId]?.cancel()
        entryJobs[entryId] = viewModelScope.launch {
            delay(300)
            flushEntry(entryId)
        }
    }

    private suspend fun flushEntry(entryId: Long) {
        val text = pendingEntryText.remove(entryId) ?: return
        repo.setEntryText(entryId, text)
    }

    private suspend fun flushAllEntries() {
        entryJobs.values.forEach { it.cancel() }
        entryJobs.clear()
        for (id in pendingEntryText.keys.toList()) flushEntry(id)
    }

    // ---- Item actions ----

    fun togglePin() = launchWithItem { repo.setPinned(itemId, !it.item.pinned) }
    fun toggleArchive() = launchWithItem { repo.setArchived(itemId, !it.item.archived) }
    fun setCollection(id: Long?) = viewModelScope.launch { repo.setCollection(itemId, id) }
    fun createCollection(name: String, emoji: String) = viewModelScope.launch {
        val id = repo.createCollection(name, emoji)
        repo.setCollection(itemId, id)
    }

    fun setReminder(at: Long?) = viewModelScope.launch { repo.setReminder(itemId, at) }
    fun addTag(name: String) = viewModelScope.launch { repo.addTag(itemId, name) }
    fun removeTag(tag: Tag) = viewModelScope.launch { repo.removeTag(itemId, tag.id) }
    fun moveToTrash() = viewModelScope.launch {
        flushText()
        repo.moveToTrash(itemId)
    }

    fun restore() = viewModelScope.launch { repo.restore(itemId) }
    fun refreshPreview() = repo.requestPreview(itemId, force = true)
    fun removeLink() = viewModelScope.launch { repo.removeLink(itemId) }

    fun addUris(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        val imported = uris.mapNotNull { repo.files.importUri(it) }
        repo.addAttachments(itemId, imported)
    }

    fun addCameraPhoto(fileName: String) = viewModelScope.launch {
        val file = repo.files.file(fileName)
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return@launch
        }
        repo.addAttachments(itemId, listOf(repo.files.importedFromLocal(file, "Photo", "image/jpeg")))
    }

    fun addVoice(fileName: String, durationMs: Long) = viewModelScope.launch {
        val file = repo.files.file(fileName)
        if (!file.exists()) return@launch
        repo.addAttachments(itemId, listOf(repo.files.importedFromLocal(file, "Voice note", "audio/mp4", durationMs)))
    }

    fun removeAttachment(attachment: com.maheshkatakam.stash.data.Attachment) =
        viewModelScope.launch { repo.removeAttachment(attachment) }

    // ---- Checklist actions ----

    fun setChecked(entry: ChecklistEntry, checked: Boolean) = viewModelScope.launch {
        flushEntry(entry.id)
        repo.setEntryChecked(itemId, entry.id, checked)
    }

    /** Enter pressed in an entry: the text after the cursor (or a blank line) becomes new entries below it. */
    fun addEntriesAfter(entry: ChecklistEntry, texts: List<String>) = viewModelScope.launch {
        flushEntry(entry.id)
        val ids = repo.addEntries(itemId, entry.position, texts.ifEmpty { listOf("") })
        focusEntryId = ids.lastOrNull()
    }

    fun addEntryAtEnd() = viewModelScope.launch {
        val ids = repo.addEntries(itemId, null, listOf(""))
        focusEntryId = ids.lastOrNull()
    }

    fun deleteEntry(entry: ChecklistEntry, focusAfter: Long?) = viewModelScope.launch {
        pendingEntryText.remove(entry.id)
        entryJobs.remove(entry.id)?.cancel()
        repo.deleteEntry(itemId, entry.id)
        focusEntryId = focusAfter
    }

    fun deleteChecked() = viewModelScope.launch { repo.deleteCheckedEntries(itemId) }
    fun uncheckAll() = viewModelScope.launch { repo.uncheckAll(itemId) }

    fun showCheckboxes() = viewModelScope.launch {
        textJob?.cancel()
        val currentBody = body
        repo.convertToChecklist(itemId, title, currentBody)
        body = ""
        focusEntryId = repo.getItem(itemId)?.entries?.filter { !it.checked }?.maxByOrNull { it.position }?.id
    }

    fun hideCheckboxes() = viewModelScope.launch {
        textJob?.cancel()
        flushAllEntries()
        repo.convertToNote(itemId, title, body)
        body = repo.getItemRow(itemId)?.body ?: body
    }

    private fun launchWithItem(block: suspend (ItemFull) -> Unit) = viewModelScope.launch {
        val full = item.value ?: repo.getItem(itemId) ?: return@launch
        block(full)
    }

    /** Leaving the editor: save everything, and drop the item if it was never filled in. */
    override fun onCleared() {
        val t = title
        val b = body
        val wasLoaded = loaded
        val entries = pendingEntryText.toMap()
        textJob?.cancel()
        entryJobs.values.forEach { it.cancel() }
        repo.appScope.launch {
            if (wasLoaded) repo.updateText(itemId, t, b)
            entries.forEach { (id, text) -> repo.setEntryText(id, text) }
            repo.deleteIfEmpty(itemId)
        }
    }
}
