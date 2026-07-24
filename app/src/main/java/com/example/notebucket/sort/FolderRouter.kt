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
    val embedding: FloatArray,
    val unsortedFolderId: String? = null
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

        val ranked = rankFolders(embedding)
        val best = ranked.firstOrNull()
        if (best != null && best.second >= threshold) {
            val gap = ranked.getOrNull(1)?.let { best.second - it.second } ?: Float.MAX_VALUE
            if (gap > AMBIGUOUS_MARGIN) {
                learn(embedding, confirmedFolderId = best.first.id)
            } else {
                Log.d(TAG, "  ambiguous (gap=${"%.4f".format(gap)}), skipping learning")
            }
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
            val routingEmb = folder.routingEmbedding ?: return@mapNotNull null
            RoutingCandidate(folder, cosine(embedding, routingEmb))
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

        // Decision rules:
        //   gap >= CLEAR_MARGIN (5%)      -> clear winner, auto-sort (overrides floor)
        //   gap <= AMBIGUOUS_MARGIN (2%)  -> suggestions
        //   between                       -> auto-sort only if best >= threshold
        val aboveThreshold = best.similarity >= threshold
        val gap = if (second != null) best.similarity - second.similarity else Float.MAX_VALUE
        val clearWinner = gap >= CLEAR_MARGIN
        val ambiguous = gap <= AMBIGUOUS_MARGIN

        val needsDisambiguation = !clearWinner && (ambiguous || !aboveThreshold)

        Log.d(TAG, "  best='${best.folder.name}' sim=${"%.4f".format(best.similarity)}" +
            (second?.let { " second='${it.folder.name}' sim=${"%.4f".format(it.similarity)}" } ?: "") +
            " gap=${"%.4f".format(gap)} threshold=$threshold clearWinner=$clearWinner" +
            " ambiguous=$ambiguous needsDisambiguation=$needsDisambiguation")

        val unsortedFolder = repo.getFolderByName("Unsorted") ?: createFolder("Unsorted")

        val candidates = if (needsDisambiguation) {
            val hidden = scored.filter { it.folder.isHidden }.take(2)
            val bestVisible = scored.firstOrNull { !it.folder.isHidden && it.folder.name != "Unsorted" }
            val merged = hidden.toMutableList()
            if (bestVisible != null && bestVisible !in hidden) {
                merged.add(bestVisible)
            }
            merged.take(3)
        } else {
            scored.take(2)
        }

        return RoutingResult(
            candidates = candidates,
            needsDisambiguation = needsDisambiguation,
            embedding = embedding,
            unsortedFolderId = if (needsDisambiguation) unsortedFolder.id else null
        )
    }

    suspend fun commitToFolder(
        text: String,
        folderId: String,
        embedding: FloatArray
    ): AssignmentResult {
        val folder = repo.getFolder(folderId)
            ?: repo.getFolderByName("Unsorted") ?: createFolder("Unsorted")
        learn(embedding, confirmedFolderId = folder.id)
        return assignToFolder(folder, text, embedding)
    }

    /**
     * Small RL: folder routing centroid drifts toward embeddings of notes the
     * user files (or confirms) there; explicit correction away from a folder
     * pushes its centroid slightly away from that note. "Unsorted" never learns.
     */
    private suspend fun learn(
        embedding: FloatArray,
        confirmedFolderId: String? = null,
        rejectedFolderId: String? = null
    ) {
        confirmedFolderId?.let { id ->
            val folder = repo.getFolder(id)
            if (folder != null && !folder.name.equals("Unsorted", ignoreCase = true)) {
                val base = folder.routingEmbedding
                if (base != null && base.size == embedding.size) {
                    val n = folder.learnedCount
                    val updated = FloatArray(base.size) { i -> (base[i] * n + embedding[i]) / (n + 1) }
                    repo.updateFolderLearning(id, normalize(updated), n + 1)
                    Log.d(TAG, "  LEARN confirm '${folder.name}' n=${n + 1}")
                }
            }
        }
        rejectedFolderId?.let { id ->
            if (id == confirmedFolderId) return@let
            val folder = repo.getFolder(id)
            if (folder != null && !folder.name.equals("Unsorted", ignoreCase = true)) {
                val base = folder.routingEmbedding
                if (base != null && base.size == embedding.size) {
                    val updated = FloatArray(base.size) { i -> base[i] - REJECTION_RATE * embedding[i] }
                    repo.updateFolderLearning(id, normalize(updated), maxOf(folder.learnedCount, 1))
                    Log.d(TAG, "  LEARN reject '${folder.name}'")
                }
            }
        }
    }

    private suspend fun rankFolders(embedding: FloatArray): List<Pair<Folder, Float>> {
        val folders = repo.getAllFolders()
        return folders.mapNotNull { folder ->
            val routingEmb = folder.routingEmbedding ?: return@mapNotNull null
            folder to cosine(embedding, routingEmb)
        }.sortedByDescending { it.second }
    }

    suspend fun createFolder(name: String, color: String = "teal", isHidden: Boolean = false): Folder {
        embedder.awaitLoaded()
        val nameEmb = embedder.embedNote(name)
        val folder = Folder(
            id = newFolderId(),
            name = name,
            nameEmbedding = nameEmb,
            noteCount = 0,
            isUserRenamed = false,
            color = color,
            isHidden = isHidden
        )
        repo.insertFolder(folder)
        Log.d(TAG, "NEW FOLDER '$name' hidden=$isHidden")
        return folder
    }

    suspend fun seedDefaultFolders() {
        val existing = repo.getAllFolders().associateBy { it.name.lowercase() }

        if (!existing.containsKey("unsorted")) {
            createFolder("Unsorted", "teal", isHidden = false)
        }

        val hiddenFolders = listOf(
            "Ideas" to "slate",
            "Tasks" to "slate",
            "Reference" to "slate",
            "Personal" to "slate",
            "Work" to "slate",
            "Recipes & Cooking" to "slate",
            "Code Snippets" to "slate",
            "Bookmarks & Links" to "slate",
            "Shopping Lists" to "slate",
            "Meeting Notes" to "slate",
            "Health & Fitness" to "slate",
            "Finance & Budget" to "slate",
            "Reading List" to "slate",
            "DIY & Projects" to "slate",
            "Travel Plans" to "slate"
        )
        for ((name, color) in hiddenFolders) {
            if (!existing.containsKey(name.lowercase())) {
                createFolder(name, color, isHidden = true)
            }
        }
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
        learn(note.embedding, confirmedFolderId = newFolderId, rejectedFolderId = note.folderId)
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

    private fun normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm == 0f) return v
        return FloatArray(v.size) { i -> v[i] / norm }
    }

    companion object {
        private const val TAG = "FolderRouter"
        const val DEFAULT_THRESHOLD = 0.55f

        /** Gap between top-2 similarities at or below this -> ask the user. */
        const val AMBIGUOUS_MARGIN = 0.02f

        /** Gap at or above this -> clear winner, auto-sort regardless of threshold. */
        const val CLEAR_MARGIN = 0.05f

        /** How far a rejected folder's centroid is pushed away from the note. */
        private const val REJECTION_RATE = 0.1f

        const val SEARCH_MIN_SIM = 0.35f
        const val SEARCH_RELATIVE_MARGIN = 0.25f
        private val URL_PATTERN = Regex("https?://\\S+|www\\.\\S+|[\\w-]+\\.[a-z]{2,}")
    }
}
