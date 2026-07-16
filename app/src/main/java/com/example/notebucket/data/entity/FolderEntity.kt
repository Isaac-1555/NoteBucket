package com.example.notebucket.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [Index("createdAt")]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameEmbedding: ByteArray?,
    val noteCount: Int,
    val isUserRenamed: Boolean = false,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FolderEntity) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}
