package com.rakdatak.app.profile

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.runnerProfileDataStore by preferencesDataStore(name = "runner_profile")

enum class TrainingEnvironment {
    OUTDOOR,
    TREADMILL,
    BOTH,
}

data class RunnerProfile(
    val onboardingComplete: Boolean = false,
    val ageYears: Int? = null,
    val trainingEnvironment: TrainingEnvironment = TrainingEnvironment.BOTH,
    val safetyReviewNeeded: Boolean = false,
)

class RunnerProfileRepository(private val context: Context) {
    val profile: Flow<RunnerProfile> = context.runnerProfileDataStore.data.map { preferences ->
        RunnerProfile(
            onboardingComplete = preferences[ONBOARDING_COMPLETE] ?: false,
            ageYears = preferences[AGE_YEARS],
            trainingEnvironment = preferences[TRAINING_ENVIRONMENT]
                ?.let { stored -> runCatching { TrainingEnvironment.valueOf(stored) }.getOrNull() }
                ?: TrainingEnvironment.BOTH,
            safetyReviewNeeded = preferences[SAFETY_REVIEW_NEEDED] ?: false,
        )
    }

    suspend fun completeOnboarding(
        ageYears: Int,
        trainingEnvironment: TrainingEnvironment,
        safetyReviewNeeded: Boolean,
    ) {
        require(ageYears in 14..100)

        context.runnerProfileDataStore.edit { preferences ->
            preferences[AGE_YEARS] = ageYears
            preferences[TRAINING_ENVIRONMENT] = trainingEnvironment.name
            preferences[SAFETY_REVIEW_NEEDED] = safetyReviewNeeded
            preferences[ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun clearForTesting() {
        context.runnerProfileDataStore.edit { it.clear() }
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val AGE_YEARS = intPreferencesKey("age_years")
        val TRAINING_ENVIRONMENT = stringPreferencesKey("training_environment")
        val SAFETY_REVIEW_NEEDED = booleanPreferencesKey("safety_review_needed")
    }
}
