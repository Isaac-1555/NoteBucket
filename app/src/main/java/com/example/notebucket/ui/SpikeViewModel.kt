package com.example.notebucket.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notebucket.ai.BgeEmbedder
import com.example.notebucket.ai.NativeBridge
import com.example.notebucket.sort.AssignmentResult
import com.example.notebucket.sort.Folder
import com.example.notebucket.sort.FolderSorter
import com.example.notebucket.sort.Note
import com.example.notebucket.sort.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SpikeState(
    val isModelLoaded: Boolean = false,
    val isLoadingModel: Boolean = false,
    val status: String = "Idle — press Load Model",
    val lastLatencyMs: Long = 0,
    val peakNativeHeapKb: Long = 0,
    val folders: List<Folder> = emptyList(),
    val notes: List<Note> = emptyList(),
    val lastAssignment: AssignmentResult? = null,
    val searchResults: List<SearchResult> = emptyList(),
    val threshold: Float = 0.55f,
    val error: String? = null
)

class SpikeViewModel : ViewModel() {

    private val embedder = BgeEmbedder()
    private var sorter: FolderSorter? = null

    private val _state = MutableStateFlow(SpikeState())
    val state: StateFlow<SpikeState> = _state.asStateFlow()

    fun loadModel(context: Context) {
        if (_state.value.isLoadingModel || _state.value.isModelLoaded) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoadingModel = true,
                status = "Loading model...",
                error = null
            )
            try {
                NativeBridge.loadNativeLib()
                val ok = embedder.loadModel(context, "bge-small-en-v1.5.Q8_0.gguf")
                if (ok) {
                    sorter = FolderSorter(embedder, _state.value.threshold)
                    _state.value = _state.value.copy(
                        isModelLoaded = true,
                        isLoadingModel = false,
                        status = "Model loaded — type a note and press Done"
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoadingModel = false,
                        status = "Failed to load model",
                        error = "Ensure bge-small-en-v1.5.Q8_0.gguf is in app/src/main/assets/"
                    )
                }
            } catch (e: Throwable) {
                Log.e("SpikeViewModel", "loadModel failed", e)
                _state.value = _state.value.copy(
                    isLoadingModel = false,
                    status = "Error: ${e.message}",
                    error = e.stackTraceToString()
                )
            }
        }
    }

    fun commitNote(text: String) {
        if (text.isBlank()) return
        val s = sorter ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "Embedding + sorting...")
            val start = System.currentTimeMillis()
            try {
                val result = s.commit(text)
                val latency = System.currentTimeMillis() - start
                val heapKb = android.os.Debug.getNativeHeapAllocatedSize() / 1024
                _state.value = _state.value.copy(
                    status = if (result.isNewFolder)
                        "Created folder: \"${result.folder.name}\""
                    else
                        "Assigned to \"${result.folder.name}\" (sim=${"%.3f".format(result.similarity)})",
                    lastLatencyMs = latency,
                    peakNativeHeapKb = maxOf(_state.value.peakNativeHeapKb, heapKb),
                    folders = s.folders,
                    notes = s.notes,
                    lastAssignment = result
                )
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    status = "Error: ${e.message}",
                    error = e.stackTraceToString()
                )
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        val s = sorter ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "Searching...")
            try {
                val results = s.search(query)
                _state.value = _state.value.copy(
                    status = "Found ${results.size} results",
                    searchResults = results
                )
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    status = "Error: ${e.message}",
                    error = e.stackTraceToString()
                )
            }
        }
    }

    fun setThreshold(t: Float) {
        sorter?.setThreshold(t)
        _state.value = _state.value.copy(threshold = t)
    }

    fun renameFolder(folderId: String, newName: String) {
        sorter?.renameFolder(folderId, newName)
        _state.value = _state.value.copy(folders = sorter?.folders ?: emptyList())
    }

    override fun onCleared() {
        embedder.unload()
    }
}
