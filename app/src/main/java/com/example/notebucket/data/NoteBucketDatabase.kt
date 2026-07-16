package com.example.notebucket.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.notebucket.data.dao.DraftDao
import com.example.notebucket.data.dao.FolderDao
import com.example.notebucket.data.dao.NoteDao
import com.example.notebucket.data.entity.DraftEntity
import com.example.notebucket.data.entity.FolderEntity
import com.example.notebucket.data.entity.NoteEntity

@Database(
    entities = [FolderEntity::class, NoteEntity::class, DraftEntity::class],
    version = 2,
    exportSchema = true
)
abstract class NoteBucketDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun noteDao(): NoteDao
    abstract fun draftDao(): DraftDao

    companion object {
        const val NAME = "notebucket.db"
    }
}
