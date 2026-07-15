package com.example.notebucket.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.example.notebucket.sort.FolderRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val router: FolderRouter
) {

    private val keyOnboardingDone = booleanPreferencesKey("onboarding_done")
    private val keyThreshold = floatPreferencesKey("threshold")

    fun observeOnboardingDone(): Flow<Boolean> =
        store.data.map { it[keyOnboardingDone] ?: false }

    suspend fun isOnboardingDone(): Boolean =
        store.data.first()[keyOnboardingDone] ?: false

    suspend fun setOnboardingDone(done: Boolean) {
        store.edit { it[keyOnboardingDone] = done }
    }

    fun observeThreshold(): Flow<Float> =
        store.data.map { it[keyThreshold] ?: FolderRouter.DEFAULT_THRESHOLD }

    suspend fun getThreshold(): Float =
        store.data.first()[keyThreshold] ?: FolderRouter.DEFAULT_THRESHOLD

    suspend fun setThreshold(value: Float) {
        val coerced = value.coerceIn(0f, 1f)
        store.edit { it[keyThreshold] = coerced }
        router.setThreshold(coerced)
    }

    suspend fun applyStoredThresholdToRouter() {
        router.setThreshold(getThreshold())
    }
}
