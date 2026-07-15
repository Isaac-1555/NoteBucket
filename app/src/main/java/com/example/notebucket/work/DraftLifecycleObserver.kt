package com.example.notebucket.work

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DraftLifecycleObserver @Inject constructor(
    private val workManager: WorkManager
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        scheduleDraftCommit()
    }

    override fun onStart(owner: LifecycleOwner) {
        cancelDraftCommit()
    }

    private fun scheduleDraftCommit() {
        val request = OneTimeWorkRequestBuilder<DraftCommitWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(
            DraftCommitWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancelDraftCommit() {
        workManager.cancelUniqueWork(DraftCommitWorker.UNIQUE_NAME)
    }
}
