package com.example.notebucket.sort

import android.util.Log
import com.example.notebucket.ai.BgeEmbedder
import com.example.notebucket.data.NoteBucketRepository
import com.example.notebucket.data.mapper.newFolderId
import com.example.notebucket.data.mapper.newNoteId
import javax.inject.Inject
import javax.inject.Singleton
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

@Singleton
class FolderRouter @Inject constructor(
    private val embedder: BgeEmbedder,
    private val repo: NoteBucketRepository
) {

    @Volatile private var threshold: Float = DEFAULT_THRESHOLD

    fun setThreshold(t: Float) { threshold = t.coerceIn(0f, 1f) }
    fun getThreshold(): Float = threshold

    suspend fun commit(text: String): AssignmentResult {
        embedder.awaitLoaded()
        val embedding = embedder.embedNote(text)

        Log.d(TAG, "commit: text='${text.take(60)}' norm=${sqrt(embedding.fold(0f) { a, v -> a + v * v })} first5=[${embedding.take(5).joinToString(",") { "%.4f".format(it) }}]")

        val folders = repo.getAllFolders()
        var bestFolder: Folder? = null
        var bestSim = -1f

        for (folder in folders) {
            val centroid = folder.centroid
            if (centroid == null) {
                Log.d(TAG, "  vs folder '${folder.name}' (n=${folder.noteCount}) CENTROID NULL — skipped")
                continue
            }
            val sim = cosine(embedding, centroid)
            Log.d(TAG, "  vs folder '${folder.name}' (n=${folder.noteCount}) sim=${"%.4f".format(sim)} centroidFirst5=[${centroid.take(5).joinToString(",") { "%.4f".format(it) }}]")
            if (sim > bestSim) {
                bestSim = sim
                bestFolder = folder
            }
        }

        Log.d(TAG, "  bestSim=${"%.4f".format(bestSim)} threshold=${"%.4f".format(threshold)} -> ${if (bestFolder != null && bestSim >= threshold) "ASSIGN to '${bestFolder.name}'" else "NEW FOLDER"}")

        return if (bestFolder != null && bestSim >= threshold) {
            val updated = updateCentroid(bestFolder.centroid!!, embedding, bestFolder.noteCount)
            repo.updateFolderCentroid(bestFolder.id, updated, bestFolder.noteCount + 1)
            val note = Note(
                id = newNoteId(),
                text = text,
                embedding = embedding,
                folderId = bestFolder.id,
                timestamp = System.currentTimeMillis()
            )
            repo.insertNote(note)
            val updatedFolder = bestFolder.copy(centroid = updated, noteCount = bestFolder.noteCount + 1)
            Log.d(TAG, "  ASSIGN to '${bestFolder.name}' sim=${"%.4f".format(bestSim)}")
            AssignmentResult(note, updatedFolder, bestSim, isNewFolder = false)
        } else {
            val crudeName = keyBertName(text, embedding)
            val newFolder = Folder(
                id = newFolderId(),
                name = crudeName,
                centroid = embedding.copyOf(),
                noteCount = 0,
                isUserRenamed = false
            )
            repo.insertFolder(newFolder)
            val note = Note(
                id = newNoteId(),
                text = text,
                embedding = embedding,
                folderId = newFolder.id,
                timestamp = System.currentTimeMillis()
            )
            repo.insertNote(note)
            repo.updateFolderCentroid(newFolder.id, embedding.copyOf(), 1)
            val finalFolder = newFolder.copy(noteCount = 1)
            Log.d(TAG, "  NEW FOLDER '$crudeName'")
            AssignmentResult(note, finalFolder, 1f, isNewFolder = true)
        }
    }

    suspend fun search(
        query: String,
        topK: Int = 5,
        folderFilter: String? = null,
        dateFrom: Long? = null,
        dateTo: Long? = null
    ): List<SearchResult> {
        embedder.awaitLoaded()
        val qEmb = embedder.embedQuery(query)
        val notes = repo.getAllNotes().filter { n ->
            (folderFilter == null || n.folderId == folderFilter) &&
                (dateFrom == null || n.timestamp >= dateFrom) &&
                (dateTo == null || n.timestamp <= dateTo)
        }
        if (notes.isEmpty()) return emptyList()
        return notes
            .map { SearchResult(it, cosine(qEmb, it.embedding)) }
            .sortedByDescending { it.similarity }
            .take(topK)
    }

    suspend fun recategorize(noteId: String, newFolderId: String) {
        val note = repo.getNote(noteId) ?: return
        if (note.folderId == newFolderId) return

        val oldFolder = repo.getFolder(note.folderId)
        if (oldFolder != null) {
            val remaining = repo.getNotesByFolder(oldFolder.id).filter { it.id != noteId }
            val dim = note.embedding.size
            val newOldCentroid = if (remaining.isEmpty()) FloatArray(dim) else average(remaining.map { it.embedding })
            repo.updateFolderCentroid(oldFolder.id, newOldCentroid, remaining.size)
        }

        val newFolder = repo.getFolder(newFolderId)
        if (newFolder != null) {
            val dim = note.embedding.size
            val existingCentroid = newFolder.centroid
            val newCentroid = if (existingCentroid == null || newFolder.noteCount == 0) {
                note.embedding.copyOf()
            } else {
                val n = newFolder.noteCount
                val updated = FloatArray(dim)
                for (i in updated.indices) updated[i] = (existingCentroid[i] * n + note.embedding[i]) / (n + 1)
                normalize(updated)
                updated
            }
            repo.updateFolderCentroid(newFolderId, newCentroid, newFolder.noteCount + 1)
        }
        repo.moveNote(noteId, newFolderId)
    }

    private fun updateCentroid(old: FloatArray, newEmb: FloatArray, n: Int): FloatArray {
        val updated = FloatArray(old.size)
        for (i in old.indices) updated[i] = (old[i] * n + newEmb[i]) / (n + 1)
        return normalize(updated)
    }

    private fun average(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(0)
        val dim = embeddings.first().size
        val out = FloatArray(dim)
        for (e in embeddings) {
            for (i in 0 until dim) out[i] += e[i]
        }
        for (i in out.indices) out[i] /= embeddings.size
        return normalize(out)
    }

    private fun normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.fold(0f) { acc, x -> acc + x * x })
        if (norm > 0f) for (i in v.indices) v[i] /= norm
        return v
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

    companion object {
        private const val TAG = "FolderRouter"
        const val DEFAULT_THRESHOLD = 0.72f
    }
}
