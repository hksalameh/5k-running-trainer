package com.rakdatak.app.workout

import android.content.Context
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.WorkoutSessionStatus
import kotlin.math.roundToLong

data class PersistedWorkoutState(
    val elapsedSeconds: Int,
    val paused: Boolean,
    val distanceMeters: Double,
    val lastUpdatedEpochMillis: Long,
)

/**
 * Small crash/process-death recovery store for the currently active workout.
 * Aggregated completed history continues to live in TrainingProgressRepository.
 */
class ActiveWorkoutRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): PersistedWorkoutState? {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null

        return PersistedWorkoutState(
            elapsedSeconds = prefs.getInt(KEY_ELAPSED_SECONDS, 0).coerceAtLeast(0),
            paused = prefs.getBoolean(KEY_PAUSED, false),
            distanceMeters = prefs.getLong(KEY_DISTANCE_CENTIMETERS, 0L)
                .coerceAtLeast(0L) / 100.0,
            lastUpdatedEpochMillis = prefs.getLong(
                KEY_LAST_UPDATED_EPOCH_MILLIS,
                System.currentTimeMillis(),
            ),
        )
    }

    fun save(
        snapshot: WorkoutSessionSnapshot,
        distanceMeters: Double,
    ) {
        if (snapshot.status !in ACTIVE_STATUSES) {
            clear()
            return
        }

        prefs.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putInt(KEY_ELAPSED_SECONDS, snapshot.totalElapsedSeconds.coerceAtLeast(0))
            .putBoolean(KEY_PAUSED, snapshot.status == WorkoutSessionStatus.PAUSED)
            .putLong(
                KEY_DISTANCE_CENTIMETERS,
                (distanceMeters.coerceAtLeast(0.0) * 100.0).roundToLong(),
            )
            .putLong(KEY_LAST_UPDATED_EPOCH_MILLIS, System.currentTimeMillis())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "active_workout"
        private const val KEY_ACTIVE = "active"
        private const val KEY_ELAPSED_SECONDS = "elapsed_seconds"
        private const val KEY_PAUSED = "paused"
        private const val KEY_DISTANCE_CENTIMETERS = "distance_centimeters"
        private const val KEY_LAST_UPDATED_EPOCH_MILLIS = "last_updated_epoch_millis"

        private val ACTIVE_STATUSES = setOf(
            WorkoutSessionStatus.RUNNING,
            WorkoutSessionStatus.PAUSED,
        )
    }
}
