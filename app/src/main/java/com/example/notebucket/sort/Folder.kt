package com.example.notebucket.sort

import java.util.UUID

data class Folder(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var centroid: FloatArray? = null,
    var noteCount: Int = 0,
    var isUserRenamed: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Folder) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
