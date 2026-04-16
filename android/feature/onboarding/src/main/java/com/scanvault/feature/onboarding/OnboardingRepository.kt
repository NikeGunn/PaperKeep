package com.scanvault.feature.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

/** Contract for onboarding state persistence. */
interface OnboardingRepository {
    val isCompleted: Flow<Boolean>
    suspend fun markCompleted()
}

/**
 * DataStore-backed onboarding repository (3B.12).
 */
@Singleton
class DataStoreOnboardingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnboardingRepository {

    private val completedKey = booleanPreferencesKey("onboarding_completed")

    /** Emits `true` if the user has completed onboarding. */
    override val isCompleted: Flow<Boolean> = context.onboardingDataStore.data
        .map { prefs -> prefs[completedKey] ?: false }

    /** Mark onboarding as completed (never show again). */
    override suspend fun markCompleted() {
        context.onboardingDataStore.edit { prefs ->
            prefs[completedKey] = true
        }
    }
}
