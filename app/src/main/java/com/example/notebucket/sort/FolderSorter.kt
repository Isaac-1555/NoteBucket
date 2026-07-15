package com.example.notebucket.sort

import com.example.notebucket.ai.BgeEmbedder
import kotlin.math.sqrt

data class AssignmentResult(
    val note: Note,
    val folder: Folder,
    val similarity: Float,
    val isNewFolder: Boolean
)

data class SearchResult(
    val note: Note,
    val similarity: Float
)

class FolderSorter(
    private val embedder: BgeEmbedder,
    private var threshold: Float = 0.55f
) {

    private val _folders = mutableListOf<Folder>()
    private val _notes = mutableListOf<Note>()

    val folders: List<Folder> get() = _folders.toList()
    val notes: List<Note> get() = _notes.toList()

    fun setThreshold(t: Float) { threshold = t.coerceIn(0f, 1f) }
    fun getThreshold(): Float = threshold

    suspend fun commit(text: String): AssignmentResult {
        val embedding = embedder.embedNote(text)

        var bestFolder: Folder? = null
        var bestSim = -1f

        for (folder in _folders) {
            val centroid = folder.centroid
            if (centroid != null) {
                val sim = cosine(embedding, centroid)
                if (sim > bestSim) {
                    bestSim = sim
                    bestFolder = folder
                }
            }
        }

        val folder: Folder
        val isNew: Boolean
        val simForResult: Float

        if (bestFolder != null && bestSim >= threshold) {
            folder = bestFolder
            isNew = false
            simForResult = bestSim
            updateCentroid(folder, embedding)
        } else {
            val crudeName = keyBertName(text, embedding)
            folder = Folder(
                name = crudeName,
                centroid = embedding.copyOf(),
                noteCount = 0
            )
            _folders.add(folder)
            isNew = true
            simForResult = 1f
        }

        val note = Note(text = text, embedding = embedding, folderId = folder.id)
        _notes.add(note)
        folder.noteCount++

        return AssignmentResult(note, folder, simForResult, isNew)
    }

    suspend fun search(query: String, topK: Int = 5): List<SearchResult> {
        if (_notes.isEmpty()) return emptyList()
        val qEmb = embedder.embedQuery(query)
        return _notes.map { note ->
            SearchResult(note, cosine(qEmb, note.embedding))
        }.sortedByDescending { it.similarity }.take(topK)
    }

    fun renameFolder(folderId: String, newName: String) {
        val folder = _folders.find { it.id == folderId }
        if (folder != null && newName.isNotBlank()) {
            folder.name = newName.trim()
            folder.isUserRenamed = true
        }
    }

    private fun updateCentroid(folder: Folder, newEmb: FloatArray) {
        val old = folder.centroid
        if (old == null) {
            folder.centroid = newEmb.copyOf()
        } else {
            val n = folder.noteCount
            val updated = FloatArray(old.size)
            for (i in old.indices) {
                updated[i] = (old[i] * n + newEmb[i]) / (n + 1)
            }
            val norm = sqrt(updated.fold(0f) { acc, v -> acc + v * v })
            if (norm > 0f) {
                for (i in updated.indices) updated[i] /= norm
            }
            folder.centroid = updated
        }
    }

    private suspend fun keyBertName(text: String, noteEmb: FloatArray): String {
        val candidates = extractCandidatePhrases(text)
        if (candidates.isEmpty()) {
            val words = text.trim().split(Regex("\\s+")).take(3)
            return words.joinToString(" ").ifBlank { "New Folder" }
        }

        var bestPhrase = candidates.first()
        var bestSim = -1f
        for (phrase in candidates.take(5)) {
            val phraseEmb = embedder.embedNote(phrase)
            val sim = cosine(noteEmb, phraseEmb)
            if (sim > bestSim) {
                bestSim = sim
                bestPhrase = phrase
            }
        }
        return bestPhrase.replaceFirstChar { it.titlecase() }
    }

    private fun extractCandidatePhrases(text: String): List<String> {
        val words = text.trim()
            .split(Regex("[^\\w]+"))
            .filter { it.isNotBlank() && it.length > 2 }

        val phrases = mutableListOf<String>()
        for (i in 0 until words.size - 1) {
            phrases.add("${words[i]} ${words[i + 1]}")
        }
        phrases.addAll(words.distinct())
        return phrases.distinct()
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}
