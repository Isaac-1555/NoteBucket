package com.example.notebucket.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: Int = 1,
    val text: String,
    val updatedAt: Long
)
