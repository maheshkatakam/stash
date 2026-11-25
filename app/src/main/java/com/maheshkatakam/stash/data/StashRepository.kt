package com.maheshkatakam.stash.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.maheshkatakam.stash.link.LinkPreviewWorker
import com.maheshkatakam.stash.link.Links
import com.maheshkatakam.stash.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/** Everything a share or quick capture can contain, saved as one item. */
data class CaptureDraft(
    val title: String = "",
    val body: String = "",
    val url: String? = null,
    val files: List<ImportedFile> = emptyList(),
    val checklistLines: List<String>? = null,
    val collectionId: Long? = null,
    val tags: List<String> = emptyList(),
    val linkTitle: String? = null,
    val linkDescription: String? = null,
    val linkImageUrl: String? = null,
)

class StashRepository(
    private val context: Context,
    val db: StashDatabase,
    val files: FileStore,
    val appScope: CoroutineScope,
) {
    private val dao = db.dao()

    val items: Flow<List<ItemFull>> = dao.observeAll()
    val collections: Flow<List<StashCollection>> = dao.observeCollections()
    val tags: Flow<List<Tag>> = dao.observeTags()

    fun observeItem(id: Long): Flow<ItemFull?> = dao.observeItem(id)
    suspend fun getItem(id: Long): ItemFull? = dao.getItem(id)
    suspend fun getItemRow(id: Long): Item? = dao.getItemRow(id)

    private fun now() = System.currentTimeMillis()

    // ---------- Creating ----------

    suspend fun createItem(
        kind: ItemKind,
        collectionId: Long? = null,
        withEmptyEntry: Boolean = false,
    ): Long = db.withTransaction {
        val id = dao.insertItem(Item(kind = kind, collectionId = collectionId))
        if (withEmptyEntry) dao.insertEntry(ChecklistEntry(itemId = id, position = 0))
        id
    }

    suspend fun saveLink(text: String, collectionId: Long?, fetchPreview: Boolean): Long? {
        val raw = Links.fromTyped(text) ?: return null
        val url = Links.clean(raw)
        val rest = text.replace(raw, "").trim()
        return saveCapture(CaptureDraft(body = rest, url = url, collectionId = collectionId), fetchPreview)
    }

    suspend fun saveCapture(draft: CaptureDraft, fetchPreview: Boolean): Long {
        val kind = when {
            draft.checklistLines != null -> ItemKind.LIST
            draft.url != null -> ItemKind.LINK
            draft.files.any { it.kind == AttachmentKind.IMAGE || it.kind == AttachmentKind.VIDEO } -> ItemKind.MEDIA
            draft.files.isNotEmpty() && draft.files.all { it.kind == AttachmentKind.AUDIO } -> ItemKind.VOICE
            draft.files.isNotEmpty() -> ItemKind.FILE
            else -> ItemKind.NOTE
        }
        val id = db.withTransaction {
            val id = dao.insertItem(
                Item(
                    kind = kind,
                    title = draft.title.trim(),
                    body = draft.body.trim(),
                    url = draft.url,
                    source = draft.url?.let { Links.sourceFor(it) },
                    linkTitle = draft.linkTitle?.takeIf { it.isNotBlank() },
                    linkDescription = draft.linkDescription?.takeIf { it.isNotBlank() },
                    linkImageUrl = draft.linkImageUrl?.takeIf { it.isNotBlank() },
                    collectionId = draft.collectionId,
                ),
            )
            draft.files.forEachIndexed { i, f -> dao.insertAttachment(f.toAttachment(id, i)) }
            draft.checklistLines?.forEachIndexed { i, line ->
                dao.insertEntry(ChecklistEntry(itemId = id, text = line, position = i))
            }
            for (t in draft.tags) addTagInternal(id, t)
            id
        }
        if (draft.url != null && fetchPreview) requestPreview(id)
        return id
    }

    private fun ImportedFile.toAttachment(itemId: Long, position: Int) = Attachment(
        itemId = itemId,
        kind = kind,
        fileName = fileName,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        position = position,
    )

    // ---------- Editing ----------

    /** Saves title/body only when they actually changed, so opening an item doesn't reorder the feed. */
    suspend fun updateText(id: Long, title: String, body: String) {
        val current = dao.getItemRow(id) ?: return
        if (current.title == title && current.body == body) return
        dao.updateText(id, title, body, now())
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)
    suspend fun setCollection(id: Long, collectionId: Long?) = dao.setCollection(id, collectionId)

    suspend fun addAttachments(itemId: Long, imported: List<ImportedFile>) {
        if (imported.isEmpty()) return
        db.withTransaction {
            var pos = dao.maxAttachmentPosition(itemId) + 1
            for (f in imported) dao.insertAttachment(f.toAttachment(itemId, pos++))
            dao.touch(itemId, now())
        }
    }

    suspend fun removeAttachment(attachment: Attachment) {
        dao.deleteAttachment(attachment.id)
        files.delete(attachment.fileName)
        dao.touch(attachment.itemId, now())
    }

    suspend fun removeLink(id: Long) {
        val item = dao.getItemRow(id) ?: return
        db.withTransaction {
            dao.clearLink(id)
            if (item.kind == ItemKind.LINK) dao.setKind(id, ItemKind.NOTE)
        }
        files.delete(item.linkThumb)
    }

    // ---------- Checklists ----------

    /** Inserts new entries after [afterPosition] (or at the end) and returns their ids. */
    suspend fun addEntries(itemId: Long, afterPosition: Int?, texts: List<String>): List<Long> =
        db.withTransaction {
            val start = if (afterPosition == null) {
                dao.maxEntryPosition(itemId) + 1
            } else {
                dao.shiftEntries(itemId, afterPosition, texts.size)
                afterPosition + 1
            }
            val ids = texts.mapIndexed { i, t -> dao.insertEntry(ChecklistEntry(itemId = itemId, text = t, position = start + i)) }
            dao.touch(itemId, now())
            ids
        }

    suspend fun setEntryText(id: Long, text: String) = dao.setEntryText(id, text)

    suspend fun setEntryChecked(itemId: Long, entryId: Long, checked: Boolean) {
        dao.setEntryChecked(entryId, checked)
        dao.touch(itemId, now())
    }

    suspend fun deleteEntry(itemId: Long, entryId: Long) {
        dao.deleteEntry(entryId)
        dao.touch(itemId, now())
    }

    suspend fun deleteCheckedEntries(itemId: Long) = dao.deleteCheckedEntries(itemId)
    suspend fun uncheckAll(itemId: Long) = dao.uncheckAll(itemId)

    /** "Show checkboxes": every line of the note becomes a checklist entry. */
    suspend fun convertToChecklist(itemId: Long, title: String, body: String) = db.withTransaction {
        val lines = body.lines().map { it.trim().removePrefix("- ").removePrefix("• ").trim() }.filter { it.isNotEmpty() }
        var pos = dao.maxEntryPosition(itemId) + 1
        if (lines.isEmpty()) {
            dao.insertEntry(ChecklistEntry(itemId = itemId, position = pos))
        } else {
            for (line in lines) dao.insertEntry(ChecklistEntry(itemId = itemId, text = line, position = pos++))
        }
        dao.updateText(itemId, title, "", now())
        dao.setKind(itemId, ItemKind.LIST)
    }

    /** "Hide checkboxes": entries become plain lines of text. */
    suspend fun convertToNote(itemId: Long, title: String, body: String) = db.withTransaction {
        val entries = dao.entriesFor(itemId).sortedWith(compareBy<ChecklistEntry>({ it.checked }, { it.position }))
        val text = entries.filter { it.text.isNotBlank() }.joinToString("\n") { it.text.trim() }
        val newBody = listOf(body.trim(), text).filter { it.isNotEmpty() }.joinToString("\n")
        dao.deleteAllEntries(itemId)
        dao.updateText(itemId, title, newBody, now())
        dao.setKind(itemId, ItemKind.NOTE)
    }

    // ---------- Tags ----------

    suspend fun addTag(itemId: Long, raw: String) {
        db.withTransaction { addTagInternal(itemId, raw) }
    }

    private suspend fun addTagInternal(itemId: Long, raw: String) {
        val name = normalizeTag(raw) ?: return
        val tagId = dao.findTag(name)?.id
            ?: dao.insertTag(Tag(name = name)).takeIf { it > 0 }
            ?: dao.findTag(name)?.id
            ?: return
        dao.insertItemTag(ItemTag(itemId, tagId))
    }

    suspend fun removeTag(itemId: Long, tagId: Long) {
        dao.removeItemTag(itemId, tagId)
        dao.deleteUnusedTags()
    }

    // ---------- Collections ----------

    suspend fun createCollection(name: String, emoji: String): Long =
        dao.insertCollection(StashCollection(name = name.trim(), emoji = emoji.trim()))

    suspend fun updateCollection(collection: StashCollection) = dao.updateCollection(collection)

    /** Items in a deleted collection are kept; they just leave the collection. */
    suspend fun deleteCollection(id: Long) = dao.deleteCollection(id)

    // ---------- Trash ----------

    suspend fun moveToTrash(id: Long) {
        dao.setTrashed(id, now())
        ReminderScheduler.cancel(context, id)
    }

    suspend fun restore(id: Long) {
        dao.setTrashed(id, null)
        val at = dao.getItemRow(id)?.reminderAt
        if (at != null) ReminderScheduler.schedule(context, id, at)
    }

    suspend fun deleteForever(id: Long) {
        val full = dao.getItem(id) ?: return
        ReminderScheduler.cancel(context, id)
        dao.deleteItem(id)
        full.attachments.forEach { files.delete(it.fileName) }
        files.delete(full.item.linkThumb)
        dao.deleteUnusedTags()
    }

    suspend fun emptyTrash() {
        for (id in dao.trashedIds()) deleteForever(id)
    }

    suspend fun purgeOldTrash() {
        val cutoff = now() - TRASH_DAYS * 24L * 60 * 60 * 1000
        for (id in dao.trashedBefore(cutoff)) deleteForever(id)
    }

    /** Called when leaving the editor: an untouched new note shouldn't linger. */
    suspend fun deleteIfEmpty(id: Long) {
        val full = dao.getItem(id) ?: return
        if (full.isEmptyContent()) deleteForever(id)
    }

    // ---------- Reminders ----------

    suspend fun setReminder(id: Long, at: Long?) {
        dao.setReminder(id, at)
        if (at != null) ReminderScheduler.schedule(context, id, at) else ReminderScheduler.cancel(context, id)
    }

    /** After a reminder has fired, it's cleared so the card no longer shows it. */
    suspend fun clearFiredReminder(id: Long) = dao.setReminder(id, null)

    suspend fun rescheduleAllReminders() {
        for (item in dao.itemsWithReminders()) {
            item.reminderAt?.let { ReminderScheduler.schedule(context, item.id, it) }
        }
    }

    // ---------- Link previews ----------

    fun requestPreview(id: Long, force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<LinkPreviewWorker>()
            .setInputData(workDataOf(LinkPreviewWorker.KEY_ID to id, LinkPreviewWorker.KEY_FORCE to force))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("preview-$id", ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun applyPreview(id: Long, title: String?, description: String?, imageUrl: String?, thumb: String?) =
        dao.applyPreview(id, title, description, imageUrl, thumb)

    companion object {
        const val TRASH_DAYS = 30
    }
}
