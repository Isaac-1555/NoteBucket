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

data class RoutingCandidate(
    val folder: Folder,
    val similarity: Float
)

data class RoutingResult(
    val candidates: List<RoutingCandidate>,
    val needsDisambiguation: Boolean,
    val embedding: FloatArray
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

        val best = scoreFolders(embedding)
        if (best != null && best.second >= threshold) {
            return assignToFolder(best.first, text, embedding, best.second)
        }

        val unsorted = repo.getFolderByName("Unsorted") ?: createFolder("Unsorted")
        Log.d(TAG, "  no match above threshold, assigning to Unsorted")
        return assignToFolder(unsorted, text, embedding, best?.second ?: 0f)
    }

    suspend fun routeForDisplay(text: String): RoutingResult {
        embedder.awaitLoaded()
        val embedding = embedder.embedNote(text)

        Log.d(TAG, "routeForDisplay: text='${text.take(60)}'")

        val folders = repo.getAllFolders()
        if (folders.isEmpty()) {
            val unsorted = createFolder("Unsorted")
            return RoutingResult(
                candidates = listOf(RoutingCandidate(unsorted, 0f)),
                needsDisambiguation = false,
                embedding = embedding
            )
        }

        val scored = folders.mapNotNull { folder ->
            val nameEmb = folder.nameEmbedding ?: return@mapNotNull null
            RoutingCandidate(folder, cosine(embedding, nameEmb))
        }.sortedByDescending { it.similarity }

        if (scored.isEmpty()) {
            val unsorted = repo.getFolderByName("Unsorted") ?: createFolder("Unsorted")
            return RoutingResult(
                candidates = listOf(RoutingCandidate(unsorted, 0f)),
                needsDisambiguation = false,
                embedding = embedding
            )
        }

        val best = scored[0]
        val second = scored.getOrNull(1)

        val aboveThreshold = best.similarity >= threshold
        val ambiguous = second != null &&
            aboveThreshold &&
            (best.similarity - second.similarity) <= DISAMBIGUATION_MARGIN

        Log.d(TAG, "  best='${best.folder.name}' sim=${"%.4f".format(best.similarity)}" +
            (second?.let { " second='${it.folder.name}' sim=${"%.4f".format(it.similarity)}" } ?: "") +
            " ambiguous=$ambiguous")

        return RoutingResult(
            candidates = scored.take(2),
            needsDisambiguation = ambiguous,
            embedding = embedding
        )
    }

    suspend fun commitToFolder(
        text: String,
        folderId: String,
        embedding: FloatArray
    ): AssignmentResult {
        val folder = repo.getFolder(folderId)
            ?: repo.getFolderByName("Unsorted") ?: createFolder("Unsorted")
        return assignToFolder(folder, text, embedding)
    }

    private suspend fun scoreFolders(embedding: FloatArray): Pair<Folder, Float>? {
        val folders = repo.getAllFolders()
        if (folders.isEmpty()) return null

        val scored = folders.mapNotNull { folder ->
            val nameEmb = folder.nameEmbedding ?: return@mapNotNull null
            folder to cosine(embedding, nameEmb)
        }
        if (scored.isEmpty()) return null

        val best = scored.maxByOrNull { it.second }!!
        Log.d(TAG, "  best match: '${best.first.name}' sim=${"%.4f".format(best.second)}")
        return best
    }

    suspend fun createFolder(name: String, color: String = "teal"): Folder {
        embedder.awaitLoaded()
        val nameEmb = embedder.embedNote(name)
        val folder = Folder(
            id = newFolderId(),
            name = name,
            nameEmbedding = nameEmb,
            noteCount = 0,
            isUserRenamed = false,
            color = color
        )
        repo.insertFolder(folder)
        Log.d(TAG, "NEW FOLDER '$name'")
        return folder
    }

    suspend fun renameFolder(folderId: String, newName: String) {
        embedder.awaitLoaded()
        val nameEmb = embedder.embedNote(newName)
        repo.renameFolder(folderId, newName)
        repo.updateFolderEmbedding(folderId, nameEmb)
    }

    private suspend fun assignToFolder(
        folder: Folder,
        text: String,
        embedding: FloatArray,
        sim: Float = 0f
    ): AssignmentResult {
        val note = Note(
            id = newNoteId(),
            text = text,
            embedding = embedding,
            folderId = folder.id,
            timestamp = System.currentTimeMillis()
        )
        repo.insertNote(note)
        val newCount = folder.noteCount + 1
        repo.updateNoteCount(folder.id, newCount)
        val updatedFolder = folder.copy(noteCount = newCount)
        Log.d(TAG, "  ASSIGN to '${folder.name}' sim=${"%.4f".format(sim)}")
        return AssignmentResult(note, updatedFolder, sim, isNewFolder = false)
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
        val ranked = notes
            .map { SearchResult(it, cosine(qEmb, it.embedding)) }
            .sortedByDescending { it.similarity }
        val topSim = ranked.first().similarity
        val floor = maxOf(SEARCH_MIN_SIM, topSim - SEARCH_RELATIVE_MARGIN)
        return ranked.filter { it.similarity >= floor }.take(topK)
    }

    suspend fun recategorize(noteId: String, newFolderId: String) {
        val note = repo.getNote(noteId) ?: return
        if (note.folderId == newFolderId) return

        val oldFolder = repo.getFolder(note.folderId)
        if (oldFolder != null) {
            val remaining = repo.getNotesByFolder(oldFolder.id).filter { it.id != noteId }
            repo.updateNoteCount(oldFolder.id, remaining.size)
        }

        val newFolder = repo.getFolder(newFolderId)
        if (newFolder != null) {
            repo.updateNoteCount(newFolderId, newFolder.noteCount + 1)
        }
        repo.moveNote(noteId, newFolderId)
    }

    suspend fun rewriteNote(noteId: String, newText: String, updateTimestamp: Boolean) {
        val note = repo.getNote(noteId) ?: return
        embedder.awaitLoaded()
        val newEmbedding = embedder.embedNote(newText)
        val newTimestamp = if (updateTimestamp) System.currentTimeMillis() else note.timestamp
        val updated = note.copy(text = newText, embedding = newEmbedding, timestamp = newTimestamp)
        repo.updateNote(updated)
    }

    suspend fun deleteNote(noteId: String) {
        val note = repo.getNote(noteId) ?: return
        repo.deleteNote(noteId)
        val remaining = repo.getNotesByFolder(note.folderId)
        repo.updateNoteCount(note.folderId, remaining.size)
    }

    suspend fun bulkMove(noteIds: List<String>, toFolderId: String) {
        if (noteIds.isEmpty()) return
        val notes = noteIds.mapNotNull { repo.getNote(it) }
        if (notes.isEmpty()) return
        val idSet = noteIds.toSet()
        val bySource = notes.groupBy { it.folderId }
        for ((fromFolderId, _) in bySource) {
            if (fromFolderId == toFolderId) continue
            val remaining = repo.getNotesByFolder(fromFolderId).filter { it.id !in idSet }
            repo.updateNoteCount(fromFolderId, remaining.size)
        }
        val targetNotes = repo.getNotesByFolder(toFolderId)
        val incoming = notes.filter { it.folderId != toFolderId }
        repo.updateNoteCount(toFolderId, targetNotes.size + incoming.size)
        repo.moveNotes(noteIds, toFolderId)
    }

    suspend fun bulkDelete(noteIds: List<String>) {
        if (noteIds.isEmpty()) return
        val notes = noteIds.mapNotNull { repo.getNote(it) }
        if (notes.isEmpty()) return
        val idSet = noteIds.toSet()
        val byFolder = notes.groupBy { it.folderId }
        for ((folderId, _) in byFolder) {
            val remaining = repo.getNotesByFolder(folderId).filter { it.id !in idSet }
            repo.updateNoteCount(folderId, remaining.size)
        }
        repo.deleteNotes(noteIds)
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    companion object {
        private const val TAG = "FolderRouter"
        const val DEFAULT_THRESHOLD = 0.55f
        const val DISAMBIGUATION_MARGIN = 0.03f
        const val SEARCH_MIN_SIM = 0.35f
        const val SEARCH_RELATIVE_MARGIN = 0.25f
        private val URL_PATTERN = Regex("https?://\\S+|www\\.\\S+|[\\w-]+\\.[a-z]{2,}")
    }
}
