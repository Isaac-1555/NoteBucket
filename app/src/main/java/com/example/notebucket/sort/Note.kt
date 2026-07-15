package com.example.notebucket.sort

import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val embedding: FloatArray,
    val folderId: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Note) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
