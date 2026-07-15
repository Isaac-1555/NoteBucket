package com.example.notebucket.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.notebucket.data.NoteBucketRepository
import com.example.notebucket.sort.FolderRouter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DraftCommitWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: NoteBucketRepository,
    private val router: FolderRouter
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val draft = repo.getDraft() ?: return Result.success()
        return try {
            val text = draft.text.trim()
            if (text.isBlank()) {
                repo.deleteDraft()
                return Result.success()
            }
            val ageMs = System.currentTimeMillis() - draft.updatedAt
            Log.d(TAG, "doWork: draft age=${ageMs}ms")
            if (ageMs < MIN_DRAFT_AGE_MS) {
                Result.retry()
            } else {
                router.commit(text)
                repo.deleteDraft()
                Result.success()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to commit draft", t)
            Result.failure()
        }
    }

    companion object {
        const val TAG = "DraftCommitWorker"
        const val UNIQUE_NAME = "notebucket_draft_commit"
        const val MIN_DRAFT_AGE_MS = 60_000L
    }
}
