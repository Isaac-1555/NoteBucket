package com.example.notebucket.data.mapper

import com.example.notebucket.data.entity.FolderEntity
import com.example.notebucket.data.entity.NoteEntity
import com.example.notebucket.sort.Folder
import com.example.notebucket.sort.Note
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

fun FloatArray.toBytes(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val out = FloatArray(size / 4)
    buffer.asFloatBuffer().get(out)
    return out
}

fun FolderEntity.toDomain(): Folder = Folder(
    id = id,
    name = name,
    nameEmbedding = nameEmbedding?.toFloatArray(),
    noteCount = noteCount,
    isUserRenamed = isUserRenamed,
    color = color,
    isHidden = isHidden,
    centroidEmbedding = centroidEmbedding?.toFloatArray(),
    learnedCount = learnedCount
)

fun Folder.toEntity(createdAt: Long = System.currentTimeMillis()): FolderEntity = FolderEntity(
    id = id,
    name = name,
    nameEmbedding = nameEmbedding?.toBytes(),
    noteCount = noteCount,
    isUserRenamed = isUserRenamed,
    createdAt = createdAt,
    color = color,
    isHidden = isHidden,
    centroidEmbedding = centroidEmbedding?.toBytes(),
    learnedCount = learnedCount
)

fun NoteEntity.toDomain(): Note = Note(
    id = id,
    text = text,
    embedding = embedding.toFloatArray(),
    folderId = folderId,
    timestamp = timestamp
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    text = text,
    embedding = embedding.toBytes(),
    folderId = folderId,
    timestamp = timestamp
)

fun newFolderId(): String = UUID.randomUUID().toString()
fun newNoteId(): String = UUID.randomUUID().toString()
