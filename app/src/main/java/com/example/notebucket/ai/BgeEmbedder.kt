package com.example.notebucket.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BgeEmbedder {

    private var loaded = false

    suspend fun loadModel(context: Context, assetName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (loaded) return@withContext true

            val outFile = File(context.filesDir, assetName)
            if (!outFile.exists()) {
                try {
                    context.assets.open(assetName).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    Log.e("BgeEmbedder", "Failed to copy $assetName from assets", e)
                    return@withContext false
                }
            }

            val ok = NativeBridge.loadModel(outFile.absolutePath)
            loaded = ok
            ok
        }

    suspend fun embedNote(text: String): FloatArray = withContext(Dispatchers.IO) {
        NativeBridge.embed(text)
    }

    suspend fun embedQuery(query: String): FloatArray = withContext(Dispatchers.IO) {
        NativeBridge.embed("Represent this sentence for searching relevant passages: $query")
    }

    fun isLoaded(): Boolean = loaded && NativeBridge.isLoaded()

    fun unload() {
        if (loaded) {
            NativeBridge.unloadModel()
            loaded = false
        }
    }
}
