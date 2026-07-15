package com.example.notebucket.di

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.notebucket.data.NoteBucketDatabase
import com.example.notebucket.data.dao.DraftDao
import com.example.notebucket.data.dao.FolderDao
import com.example.notebucket.data.dao.NoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NoteBucketDatabase {
        purgeIncompatibleLegacyDb(context)
        return Room.databaseBuilder(context, NoteBucketDatabase::class.java, NoteBucketDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideFolderDao(db: NoteBucketDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideNoteDao(db: NoteBucketDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideDraftDao(db: NoteBucketDatabase): DraftDao = db.draftDao()

    /**
     * Removes any pre-1.0 database that used the sqlite-vec `vec0` virtual table
     * module. Room's destructive migration issues `DROP TABLE IF EXISTS` against
     * every table in sqlite_master, and the vec0 virtual table cannot be dropped
     * without its extension loaded, causing a SQLiteException on open. We try a
     * targeted drop; if that fails (module missing), we delete the file outright
     * so Room recreates a clean schema.
     */
    private fun purgeIncompatibleLegacyDb(context: Context) {
        val dbFile = context.getDatabasePath(NoteBucketDatabase.NAME)
        if (!dbFile.exists()) return
        try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            )
            db.use {
                it.execSQL("DROP TABLE IF EXISTS vec_notes")
                it.execSQL("DROP TABLE IF EXISTS vec_folders")
            }
        } catch (_: Throwable) {
            listOf("", "-journal", "-wal", "-shm").forEach { suffix ->
                File(dbFile.absolutePath + suffix).delete()
            }
        }
    }
}
