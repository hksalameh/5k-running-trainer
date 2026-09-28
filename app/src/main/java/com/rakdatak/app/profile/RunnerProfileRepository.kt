package com.rakdatak.app.profile

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.runnerProfileDataStore by preferencesDataStore(name = "runner_profile")

enum class TrainingEnvironment {
    OUTDOOR,
    TREADMILL,
    BOTH,
}

enum class RunnerSex {
    MALE,
    FEMALE,
    PREFER_NOT_TO_SAY,
}

data class RunnerProfile(
    val onboardingComplete: Boolean = false,
    val ageYears: Int? = null,
    val sex: RunnerSex? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val restingHeartRateBpm: Int? = null,
    val trainingEnvironment: TrainingEnvironment = TrainingEnvironment.BOTH,
    val safetyReviewNeeded: Boolean = false,
)

class RunnerProfileRepository(private val context: Context) {
    val profile: Flow<RunnerProfile> = context.runnerProfileDataStore.data.map { preferences ->
        RunnerProfile(
            onboardingComplete = preferences[ONBOARDING_COMPLETE] ?: false,
            ageYears = preferences[AGE_YEARS],
            sex = preferences[SEX]
                ?.let { stored -> runCatching { RunnerSex.valueOf(stored) }.getOrNull() },
            heightCm = preferences[HEIGHT_CM],
            weightKg = preferences[WEIGHT_TENTHS_KG]?.let { it / 10.0 },
            restingHeartRateBpm = preferences[RESTING_HEART_RATE_BPM],
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

    suspend fun updateProfile(profile: RunnerProfile) {
        val ageYears = requireNotNull(profile.ageYears)
        require(ageYears in 14..100)
        require(profile.heightCm == null || profile.heightCm in 120..230)
        require(profile.weightKg == null || profile.weightKg in 30.0..250.0)
        require(profile.restingHeartRateBpm == null || profile.restingHeartRateBpm in 35..120)

        context.runnerProfileDataStore.edit { preferences ->
            preferences[AGE_YEARS] = ageYears
            setOrRemove(preferences, SEX, profile.sex?.name)
            setOrRemove(preferences, HEIGHT_CM, profile.heightCm)
            setOrRemove(
                preferences,
                WEIGHT_TENTHS_KG,
                profile.weightKg?.let { (it * 10.0).roundToInt() },
            )
            setOrRemove(preferences, RESTING_HEART_RATE_BPM, profile.restingHeartRateBpm)
            preferences[TRAINING_ENVIRONMENT] = profile.trainingEnvironment.name
            preferences[SAFETY_REVIEW_NEEDED] = profile.safetyReviewNeeded
            preferences[ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun clearForTesting() {
        context.runnerProfileDataStore.edit { it.clear() }
    }

    private fun <T> setOrRemove(
        preferences: MutablePreferences,
        key: Preferences.Key<T>,
        value: T?,
    ) {
        if (value == null) {
            preferences.remove(key)
        } else {
            preferences[key] = value
        }
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val AGE_YEARS = intPreferencesKey("age_years")
        val SEX = stringPreferencesKey("sex")
        val HEIGHT_CM = intPreferencesKey("height_cm")
        val WEIGHT_TENTHS_KG = intPreferencesKey("weight_tenths_kg")
        val RESTING_HEART_RATE_BPM = intPreferencesKey("resting_heart_rate_bpm")
        val TRAINING_ENVIRONMENT = stringPreferencesKey("training_environment")
        val SAFETY_REVIEW_NEEDED = booleanPreferencesKey("safety_review_needed")
    }
}
