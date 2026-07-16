package com.example.notebucket.data

import com.example.notebucket.data.dao.AttachmentDao
import com.example.notebucket.data.dao.DraftDao
import com.example.notebucket.data.dao.FolderCount
import com.example.notebucket.data.dao.FolderDao
import com.example.notebucket.data.dao.NoteDao
import com.example.notebucket.data.entity.AttachmentEntity
import com.example.notebucket.data.entity.DraftEntity
import com.example.notebucket.data.mapper.toBytes
import com.example.notebucket.data.mapper.toDomain
import com.example.notebucket.data.mapper.toEntity
import com.example.notebucket.sort.Folder
import com.example.notebucket.sort.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteBucketRepository @Inject constructor(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val draftDao: DraftDao,
    private val attachmentDao: AttachmentDao
) {

    fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeFolder(id: String): Flow<Folder?> =
        folderDao.observeById(id).map { it?.toDomain() }

    fun observeNotesByFolder(folderId: String): Flow<List<Note>> =
        noteDao.observeByFolder(folderId).map { rows -> rows.map { it.toDomain() } }

    fun observeNote(id: String): Flow<Note?> =
        noteDao.observeById(id).map { it?.toDomain() }

    fun observeAllNotes(): Flow<List<Note>> =
        noteDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeNoteCountsByFolder(): Flow<List<FolderCount>> =
        noteDao.observeCountsByFolder()

    fun observeDraft(): Flow<DraftEntity?> = draftDao.observe()

    suspend fun getDraft(): DraftEntity? = draftDao.get()

    suspend fun saveDraft(text: String) {
        draftDao.upsert(DraftEntity(text = text, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDraft() = draftDao.delete()

    suspend fun getAllFolders(): List<Folder> = folderDao.getAll().map { it.toDomain() }

    suspend fun getFolder(id: String): Folder? = folderDao.getById(id)?.toDomain()

    suspend fun getFolderByName(name: String): Folder? = folderDao.getByName(name)?.toDomain()

    suspend fun getNote(id: String): Note? = noteDao.getById(id)?.toDomain()

    suspend fun getAllNotes(): List<Note> = noteDao.getAll().map { it.toDomain() }

    suspend fun getNotesByFolder(folderId: String): List<Note> =
        noteDao.getByFolder(folderId).map { it.toDomain() }

    suspend fun insertFolder(folder: Folder, createdAt: Long = System.currentTimeMillis()) {
        folderDao.upsert(folder.toEntity(createdAt))
    }

    suspend fun insertNote(note: Note) {
        noteDao.upsert(note.toEntity())
    }

    suspend fun updateFolderEmbedding(folderId: String, embedding: FloatArray) {
        folderDao.updateEmbedding(folderId, embedding.toBytes())
    }

    suspend fun updateNoteCount(folderId: String, noteCount: Int) {
        folderDao.updateNoteCount(folderId, noteCount)
    }

    suspend fun updateFolderColor(folderId: String, color: String) {
        folderDao.updateColor(folderId, color)
    }

    suspend fun renameFolder(folderId: String, newName: String) {
        folderDao.rename(folderId, newName)
    }

    suspend fun deleteFolder(folderId: String) {
        folderDao.deleteById(folderId)
    }

    suspend fun updateNote(note: Note) {
        noteDao.updateTextAndEmbedding(note.id, note.text, note.embedding.toBytes(), note.timestamp)
    }

    suspend fun deleteNote(noteId: String) {
        noteDao.deleteById(noteId)
    }

    suspend fun deleteNotes(noteIds: List<String>) {
        noteDao.deleteBatch(noteIds)
    }

    suspend fun moveNote(noteId: String, newFolderId: String) {
        noteDao.move(noteId, newFolderId)
    }

    suspend fun moveNotes(noteIds: List<String>, newFolderId: String) {
        noteDao.moveBatch(noteIds, newFolderId)
    }

    suspend fun noteCount(): Int = noteDao.count()
    suspend fun folderCount(): Int = folderDao.count()

    suspend fun clearAll() {
        noteDao.clear()
        folderDao.clear()
        draftDao.delete()
        attachmentDao.clear()
    }

    fun observeAttachments(noteId: String): Flow<List<AttachmentEntity>> =
        attachmentDao.observeByNote(noteId)

    suspend fun getAttachments(noteId: String): List<AttachmentEntity> =
        attachmentDao.getByNote(noteId)

    suspend fun insertAttachment(attachment: AttachmentEntity) {
        attachmentDao.upsert(attachment)
    }

    suspend fun deleteAttachment(id: String) {
        attachmentDao.deleteById(id)
    }

    suspend fun deleteAttachmentsForNote(noteId: String) {
        attachmentDao.deleteByNote(noteId)
    }
}
