package com.example.notebucket.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index("folderId"), Index("timestamp")]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val text: String,
    val embedding: ByteArray,
    val folderId: String,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoteEntity) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}
