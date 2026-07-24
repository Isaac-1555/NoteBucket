package com.example.notebucket.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.notebucket.data.NoteBucketDatabase
import com.example.notebucket.data.dao.AttachmentDao
import com.example.notebucket.data.dao.DraftDao
import com.example.notebucket.data.dao.FolderDao
import com.example.notebucket.data.dao.NoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE folders ADD COLUMN centroidEmbedding BLOB")
            db.execSQL("ALTER TABLE folders ADD COLUMN learnedCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NoteBucketDatabase =
        Room.databaseBuilder(context, NoteBucketDatabase::class.java, NoteBucketDatabase.NAME)
            .addMigrations(MIGRATION_5_6)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideFolderDao(db: NoteBucketDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideNoteDao(db: NoteBucketDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideDraftDao(db: NoteBucketDatabase): DraftDao = db.draftDao()

    @Provides
    fun provideAttachmentDao(db: NoteBucketDatabase): AttachmentDao = db.attachmentDao()
}
