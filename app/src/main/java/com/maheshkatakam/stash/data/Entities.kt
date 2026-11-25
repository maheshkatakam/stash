package com.maheshkatakam.stash.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter

/** What an item mainly is. Filters also look at its contents (a note with photos shows under Images too). */
enum class ItemKind { NOTE, LIST, LINK, MEDIA, VOICE, FILE }

enum class AttachmentKind { IMAGE, VIDEO, AUDIO, FILE }

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = StashCollection::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("collectionId")],
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: ItemKind = ItemKind.NOTE,
    val title: String = "",
    val body: String = "",
    val url: String? = null,
    /** "Instagram", "X", "YouTube", or the site's domain. */
    val source: String? = null,
    val linkTitle: String? = null,
    val linkDescription: String? = null,
    val linkImageUrl: String? = null,
    /** Local copy of the link's picture (file name in the media folder), so it survives expiring CDN links. */
    val linkThumb: String? = null,
    val collectionId: Long? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val reminderAt: Long? = null,
    val trashedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val kind: AttachmentKind,
    /** File name inside the app's media folder. */
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "checklist_entries",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class ChecklistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val text: String = "",
    val checked: Boolean = false,
    val position: Int = 0,
)

@Entity(tableName = "collections")
data class StashCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "",
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "item_tags",
    primaryKeys = ["itemId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class ItemTag(
    val itemId: Long,
    val tagId: Long,
)

data class ItemFull(
    @Embedded val item: Item,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val attachments: List<Attachment>,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val entries: List<ChecklistEntry>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(value = ItemTag::class, parentColumn = "itemId", entityColumn = "tagId"),
    )
    val tags: List<Tag>,
)

class Converters {
    @TypeConverter
    fun itemKindToString(kind: ItemKind): String = kind.name

    @TypeConverter
    fun stringToItemKind(value: String): ItemKind =
        ItemKind.entries.firstOrNull { it.name == value } ?: ItemKind.NOTE

    @TypeConverter
    fun attachmentKindToString(kind: AttachmentKind): String = kind.name

    @TypeConverter
    fun stringToAttachmentKind(value: String): AttachmentKind =
        AttachmentKind.entries.firstOrNull { it.name == value } ?: AttachmentKind.FILE
}
