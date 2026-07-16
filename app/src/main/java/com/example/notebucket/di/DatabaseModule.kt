package com.example.notebucket.di

import android.content.Context
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NoteBucketDatabase =
        Room.databaseBuilder(context, NoteBucketDatabase::class.java, NoteBucketDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideFolderDao(db: NoteBucketDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideNoteDao(db: NoteBucketDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideDraftDao(db: NoteBucketDatabase): DraftDao = db.draftDao()
}
