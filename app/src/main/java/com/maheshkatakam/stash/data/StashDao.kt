package com.maheshkatakam.stash.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StashDao {

    // ---- Items ----

    @Transaction
    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ItemFull>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun observeItem(id: Long): Flow<ItemFull?>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItem(id: Long): ItemFull?

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemRow(id: Long): Item?

    @Insert
    suspend fun insertItem(item: Item): Long

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT COUNT(*) FROM items WHERE trashedAt IS NULL")
    suspend fun countItems(): Int

    @Query("UPDATE items SET title = :title, body = :body, updatedAt = :now WHERE id = :id")
    suspend fun updateText(id: Long, title: String, body: String, now: Long)

    @Query("UPDATE items SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE items SET archived = :archived, pinned = CASE WHEN :archived THEN 0 ELSE pinned END WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE items SET collectionId = :collectionId WHERE id = :id")
    suspend fun setCollection(id: Long, collectionId: Long?)

    @Query("UPDATE items SET reminderAt = :at WHERE id = :id")
    suspend fun setReminder(id: Long, at: Long?)

    @Query("UPDATE items SET trashedAt = :at WHERE id = :id")
    suspend fun setTrashed(id: Long, at: Long?)

    @Query("UPDATE items SET kind = :kind WHERE id = :id")
    suspend fun setKind(id: Long, kind: ItemKind)

    @Query("UPDATE items SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query(
        """UPDATE items SET
            linkTitle = COALESCE(:title, linkTitle),
            linkDescription = COALESCE(:description, linkDescription),
            linkImageUrl = COALESCE(:imageUrl, linkImageUrl),
            linkThumb = COALESCE(:thumb, linkThumb)
        WHERE id = :id""",
    )
    suspend fun applyPreview(id: Long, title: String?, description: String?, imageUrl: String?, thumb: String?)

    @Query(
        "UPDATE items SET url = NULL, source = NULL, linkTitle = NULL, linkDescription = NULL, " +
            "linkImageUrl = NULL, linkThumb = NULL WHERE id = :id",
    )
    suspend fun clearLink(id: Long)

    @Query("UPDATE items SET linkThumb = :thumb WHERE id = :id")
    suspend fun setLinkThumb(id: Long, thumb: String?)

    @Query("SELECT * FROM items WHERE reminderAt IS NOT NULL AND trashedAt IS NULL")
    suspend fun itemsWithReminders(): List<Item>

    @Query("SELECT id FROM items WHERE trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun trashedBefore(before: Long): List<Long>

    @Query("SELECT id FROM items WHERE trashedAt IS NOT NULL")
    suspend fun trashedIds(): List<Long>

    // ---- Attachments ----

    @Insert
    suspend fun insertAttachment(attachment: Attachment): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM attachments WHERE itemId = :itemId")
    suspend fun maxAttachmentPosition(itemId: Long): Int

    // ---- Checklist ----

    @Insert
    suspend fun insertEntry(entry: ChecklistEntry): Long

    @Query("SELECT * FROM checklist_entries WHERE itemId = :itemId ORDER BY position")
    suspend fun entriesFor(itemId: Long): List<ChecklistEntry>

    @Query("UPDATE checklist_entries SET text = :text WHERE id = :id")
    suspend fun setEntryText(id: Long, text: String)

    @Query("UPDATE checklist_entries SET checked = :checked WHERE id = :id")
    suspend fun setEntryChecked(id: Long, checked: Boolean)

    @Query("UPDATE checklist_entries SET checked = 0 WHERE itemId = :itemId")
    suspend fun uncheckAll(itemId: Long)

    @Query("DELETE FROM checklist_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM checklist_entries WHERE itemId = :itemId AND checked = 1")
    suspend fun deleteCheckedEntries(itemId: Long)

    @Query("DELETE FROM checklist_entries WHERE itemId = :itemId")
    suspend fun deleteAllEntries(itemId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM checklist_entries WHERE itemId = :itemId")
    suspend fun maxEntryPosition(itemId: Long): Int

    @Query("UPDATE checklist_entries SET position = position + :by WHERE itemId = :itemId AND position > :after")
    suspend fun shiftEntries(itemId: Long, after: Int, by: Int)

    // ---- Collections ----

    @Query("SELECT * FROM collections ORDER BY position, name COLLATE NOCASE")
    fun observeCollections(): Flow<List<StashCollection>>

    @Insert
    suspend fun insertCollection(collection: StashCollection): Long

    @Update
    suspend fun updateCollection(collection: StashCollection)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    // ---- Tags ----

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTag(name: String): Tag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemTag(itemTag: ItemTag)

    @Query("DELETE FROM item_tags WHERE itemId = :itemId AND tagId = :tagId")
    suspend fun removeItemTag(itemId: Long, tagId: Long)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM item_tags)")
    suspend fun deleteUnusedTags()
}
