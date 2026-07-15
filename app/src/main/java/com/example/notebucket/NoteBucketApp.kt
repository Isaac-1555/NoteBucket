package com.example.notebucket

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.example.notebucket.ai.BgeEmbedder
import com.example.notebucket.data.SettingsRepository
import com.example.notebucket.work.DraftLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NoteBucketApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var draftObserver: DraftLifecycleObserver
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var embedder: BgeEmbedder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(draftObserver)
        appScope.launch { settings.applyStoredThresholdToRouter() }
        appScope.launch { embedder.loadModel() }
    }
}
