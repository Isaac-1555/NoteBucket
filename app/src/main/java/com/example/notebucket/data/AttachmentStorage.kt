package com.example.notebucket.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.util.UUID

object AttachmentStorage {

    fun getAttachmentsDir(context: Context, noteId: String): File {
        val dir = File(context.filesDir, "attachments/$noteId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun copyToStorage(context: Context, uri: Uri, noteId: String): Pair<String, String> {
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        val id = UUID.randomUUID().toString()
        val fileName = "${id}.$extension"
        val dir = getAttachmentsDir(context, noteId)
        val destFile = File(dir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open URI: $uri")

        val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: fileName

        return Pair(destFile.absolutePath, displayName)
    }

    fun deleteFile(path: String) {
        File(path).delete()
    }

    fun deleteAllForNote(context: Context, noteId: String) {
        val dir = File(context.filesDir, "attachments/$noteId")
        if (dir.exists()) dir.deleteRecursively()
    }
}
