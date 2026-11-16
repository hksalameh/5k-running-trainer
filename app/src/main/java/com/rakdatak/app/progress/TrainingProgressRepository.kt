package com.rakdatak.app.progress

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rakdatak.core.training.PlanProgressionEngine
import com.rakdatak.core.training.model.TrainingAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToLong

private val Context.trainingProgressDataStore by preferencesDataStore(name = "training_progress")

data class TrainingProgress(
    val savedWorkouts: Int = 0,
    val completedWorkouts: Int = 0,
    val totalSeconds: Long = 0L,
    val totalDistanceMeters: Long = 0L,
    val longestWorkoutSeconds: Long = 0L,
    val longestDistanceMeters: Long = 0L,
    /** Distance covered during RUN phases only, excluding warm-up and walking. */
    val longestRunDistanceMeters: Long = 0L,
    val longestContinuousRunSeconds: Int = 0,
    val currentPlanIndex: Int = 0,
)

/**
 * Small offline-first aggregate store for the home screen and current adaptive-plan position.
 * Detailed workout samples/routes will live in a dedicated local database later.
 */
class TrainingProgressRepository(private val context: Context) {
    private val progressionEngine = PlanProgressionEngine()

    val progress: Flow<TrainingProgress> = context.trainingProgressDataStore.data.map { preferences ->
        TrainingProgress(
            savedWorkouts = preferences[SAVED_WORKOUTS] ?: 0,
            completedWorkouts = preferences[COMPLETED_WORKOUTS] ?: 0,
            totalSeconds = preferences[TOTAL_SECONDS] ?: 0L,
            totalDistanceMeters = preferences[TOTAL_DISTANCE_METERS] ?: 0L,
            longestWorkoutSeconds = preferences[LONGEST_WORKOUT_SECONDS] ?: 0L,
            longestDistanceMeters = preferences[LONGEST_DISTANCE_METERS] ?: 0L,
            longestRunDistanceMeters = preferences[LONGEST_RUN_DISTANCE_METERS] ?: 0L,
            longestContinuousRunSeconds = preferences[LONGEST_CONTINUOUS_RUN] ?: 0,
            currentPlanIndex = preferences[CURRENT_PLAN_INDEX] ?: 0,
        )
    }

    suspend fun recordWorkout(
        elapsedSeconds: Int,
        completionRatio: Double,
        distanceMeters: Double? = null,
        continuousRunSeconds: Int = 0,
        runDistanceMeters: Double = 0.0,
    ) {
        require(elapsedSeconds >= 0)
        require(completionRatio in 0.0..1.0)
        require(continuousRunSeconds >= 0)

        val safeDistanceMeters = (distanceMeters ?: 0.0).coerceAtLeast(0.0).roundToLong()
        val safeRunDistanceMeters = runDistanceMeters.coerceIn(0.0, (distanceMeters ?: 0.0).coerceAtLeast(0.0)).roundToLong()
        context.trainingProgressDataStore.edit { preferences ->
            val previousSaved = preferences[SAVED_WORKOUTS] ?: 0
            val previousCompleted = preferences[COMPLETED_WORKOUTS] ?: 0
            val previousSeconds = preferences[TOTAL_SECONDS] ?: 0L
            val previousDistance = preferences[TOTAL_DISTANCE_METERS] ?: 0L
            val previousLongestSeconds = preferences[LONGEST_WORKOUT_SECONDS] ?: 0L
            val previousLongestDistance = preferences[LONGEST_DISTANCE_METERS] ?: 0L

            preferences[SAVED_WORKOUTS] = previousSaved + 1
            if (completionRatio >= 0.999) {
                preferences[COMPLETED_WORKOUTS] = previousCompleted + 1
            }
            preferences[TOTAL_SECONDS] = previousSeconds + elapsedSeconds
            preferences[TOTAL_DISTANCE_METERS] = previousDistance + safeDistanceMeters
            preferences[LONGEST_WORKOUT_SECONDS] = maxOf(previousLongestSeconds, elapsedSeconds.toLong())
            preferences[LONGEST_DISTANCE_METERS] = maxOf(previousLongestDistance, safeDistanceMeters)
            preferences[LONGEST_RUN_DISTANCE_METERS] = maxOf(
                preferences[LONGEST_RUN_DISTANCE_METERS] ?: 0L,
                safeRunDistanceMeters,
            )
            preferences[LONGEST_CONTINUOUS_RUN] = maxOf(
                preferences[LONGEST_CONTINUOUS_RUN] ?: 0,
                continuousRunSeconds,
            )
        }
    }

    /** Adds a treadmill distance that was entered after the workout itself had been recorded. */
    suspend fun addDistanceToLatestWorkout(distanceMeters: Double) {
        val safeDistanceMeters = distanceMeters.coerceAtLeast(0.0).roundToLong()
        if (safeDistanceMeters <= 0L) return

        context.trainingProgressDataStore.edit { preferences ->
            val previousDistance = preferences[TOTAL_DISTANCE_METERS] ?: 0L
            val previousLongestDistance = preferences[LONGEST_DISTANCE_METERS] ?: 0L
            preferences[TOTAL_DISTANCE_METERS] = previousDistance + safeDistanceMeters
            preferences[LONGEST_DISTANCE_METERS] = maxOf(previousLongestDistance, safeDistanceMeters)
        }
    }

    suspend fun applyTrainingAction(
        action: TrainingAction,
        planCount: Int,
    ) {
        context.trainingProgressDataStore.edit { preferences ->
            val current = (preferences[CURRENT_PLAN_INDEX] ?: 0).coerceIn(0, planCount - 1)
            preferences[CURRENT_PLAN_INDEX] = progressionEngine.nextIndex(
                currentIndex = current,
                planCount = planCount,
                action = action,
            )
        }
    }

    /** For the separate preview APK only: jump directly to a week without touching production. */
    suspend fun selectWeekForPreview(week: Int) {
        require(context.packageName.endsWith(".preview"))
        require(week in 1..9)
        context.trainingProgressDataStore.edit { preferences ->
            preferences[CURRENT_PLAN_INDEX] = (week - 1) * 3
        }
    }

    private companion object {
        val SAVED_WORKOUTS = intPreferencesKey("saved_workouts")
        val COMPLETED_WORKOUTS = intPreferencesKey("completed_workouts")
        val TOTAL_SECONDS = longPreferencesKey("total_seconds")
        val TOTAL_DISTANCE_METERS = longPreferencesKey("total_distance_meters")
        val LONGEST_WORKOUT_SECONDS = longPreferencesKey("longest_workout_seconds")
        val LONGEST_DISTANCE_METERS = longPreferencesKey("longest_distance_meters")
        val LONGEST_RUN_DISTANCE_METERS = longPreferencesKey("longest_run_distance_meters")
        val CURRENT_PLAN_INDEX = intPreferencesKey("current_plan_index")
        val LONGEST_CONTINUOUS_RUN = intPreferencesKey("longest_continuous_run")
    }
}
