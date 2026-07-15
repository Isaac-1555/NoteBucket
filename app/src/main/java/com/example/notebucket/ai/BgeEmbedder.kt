package com.example.notebucket.ai

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BgeEmbedder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val mutex = Mutex()

    @Volatile private var loaded = false
    @Volatile private var nativeLibLoaded = false

    private val _loadState = MutableStateFlow(LoadState.Idle)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    enum class LoadState { Idle, Loading, Loaded, Failed }

    fun modelAssetName(): String = DEFAULT_ASSET

    suspend fun awaitLoaded(): Boolean = loadModel()

    suspend fun loadModel(assetName: String = DEFAULT_ASSET): Boolean = mutex.withLock {
        if (loaded) return@withLock true
        _loadState.value = LoadState.Loading

        if (!nativeLibLoaded) {
            try {
                NativeBridge.loadNativeLib()
                nativeLibLoaded = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load libbge.so", t)
                _loadState.value = LoadState.Failed
                return@withLock false
            }
        }

        val outFile = File(context.filesDir, assetName)
        if (!outFile.exists()) {
            try {
                context.assets.open(assetName).use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy $assetName from assets", e)
                _loadState.value = LoadState.Failed
                return@withLock false
            }
        }

        val ok = withContext(Dispatchers.IO) {
            try {
                NativeBridge.loadModel(outFile.absolutePath)
            } catch (t: Throwable) {
                Log.e(TAG, "NativeBridge.loadModel threw", t)
                false
            }
        }
        loaded = ok
        _loadState.value = if (ok) LoadState.Loaded else LoadState.Failed
        ok
    }

    suspend fun embedNote(text: String): FloatArray = withContext(Dispatchers.IO) {
        NativeBridge.embed(text)
    }

    suspend fun embedQuery(query: String): FloatArray = withContext(Dispatchers.IO) {
        NativeBridge.embed("Represent this sentence for searching relevant passages: $query")
    }

    fun isLoaded(): Boolean = loaded && runCatching { NativeBridge.isLoaded() }.getOrDefault(false)

    fun unload() {
        if (loaded) {
            try { NativeBridge.unloadModel() } catch (_: Throwable) {}
            loaded = false
            _loadState.value = LoadState.Idle
        }
    }

    companion object {
        private const val TAG = "BgeEmbedder"
        const val DEFAULT_ASSET = "bge-small-en-v1.5.Q8_0.gguf"
    }
}
