package com.example.notebucket.sort

import java.util.UUID

data class Folder(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var nameEmbedding: FloatArray? = null,
    var noteCount: Int = 0,
    var isUserRenamed: Boolean = false,
    var color: String = "teal"
)
