package com.maheshkatakam.stash.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Item::class,
        Attachment::class,
        ChecklistEntry::class,
        StashCollection::class,
        Tag::class,
        ItemTag::class,
    ],
    version = StashDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StashDatabase : RoomDatabase() {
    abstract fun dao(): StashDao

    companion object {
        const val VERSION = 1
        const val NAME = "stash.db"

        fun build(context: Context): StashDatabase =
            Room.databaseBuilder(context.applicationContext, StashDatabase::class.java, NAME).build()
    }
}
