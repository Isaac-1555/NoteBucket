package com.example.notebucket.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    indices = [Index("noteId")]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val fileName: String,
    val mimeType: String,
    val filePath: String,
    val createdAt: Long
)
