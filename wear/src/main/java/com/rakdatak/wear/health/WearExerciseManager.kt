package com.rakdatak.wear.health

import android.content.Context
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.pauseExercise
import androidx.health.services.client.resumeExercise
import androidx.health.services.client.startExercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WearExerciseMetrics(
    val heartRateBpm: Double? = null,
    val distanceMeters: Double? = null,
    val heartRateAvailable: Boolean = false,
    val distanceAvailable: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Small wrapper around Wear OS Health Services.
 *
 * Heart rate is requested independently of GPS. Distance is only requested when GPS is enabled,
 * because some Wear OS devices reject a RUNNING exercise that asks for DISTANCE_TOTAL while the
 * exercise configuration explicitly has GPS disabled. The timed workout can still continue when
 * Health Services is unavailable.
 */
class WearExerciseManager(context: Context) {
    private val exerciseClient = HealthServices.getClient(context.applicationContext).exerciseClient

    private val _metrics = MutableStateFlow(WearExerciseMetrics())
    val metrics: StateFlow<WearExerciseMetrics> = _metrics.asStateFlow()

    private var callbackRegistered = false

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val latest = update.latestMetrics
            val heartRate = latest.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value
            val distance = latest.getData(DataType.DISTANCE_TOTAL)?.total

            _metrics.value = _metrics.value.copy(
                heartRateBpm = heartRate ?: _metrics.value.heartRateBpm,
                distanceMeters = distance ?: _metrics.value.distanceMeters,
                errorMessage = null,
            )
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            _metrics.value = _metrics.value.copy(errorMessage = throwable.message)
        }

        override fun onAvailabilityChanged(
            dataType: DataType<*, *>,
            availability: Availability,
        ) = Unit
    }

    suspend fun start(gpsEnabled: Boolean): Boolean {
        return runCatching {
            val capabilities = exerciseClient.getCapabilities()
            if (ExerciseType.RUNNING !in capabilities.supportedExerciseTypes) {
                _metrics.value = _metrics.value.copy(
                    errorMessage = "هذه الساعة لا تدعم تتبع الركض عبر Health Services.",
                )
                return false
            }

            val runningCapabilities = capabilities.getExerciseTypeCapabilities(ExerciseType.RUNNING)
            val supported = runningCapabilities.supportedDataTypes

            val requested = buildSet {
                if (DataType.HEART_RATE_BPM in supported) {
                    add(DataType.HEART_RATE_BPM)
                }
                if (gpsEnabled && DataType.DISTANCE_TOTAL in supported) {
                    add(DataType.DISTANCE_TOTAL)
                }
            }

            _metrics.value = WearExerciseMetrics(
                heartRateAvailable = DataType.HEART_RATE_BPM in requested,
                distanceAvailable = DataType.DISTANCE_TOTAL in requested,
            )

            if (!callbackRegistered) {
                exerciseClient.setUpdateCallback(callback)
                callbackRegistered = true
            }

            exerciseClient.startExercise(
                ExerciseConfig(
                    exerciseType = ExerciseType.RUNNING,
                    dataTypes = requested,
                    isAutoPauseAndResumeEnabled = false,
                    isGpsEnabled = gpsEnabled,
                    exerciseGoals = emptyList(),
                )
            )
            true
        }.getOrElse { throwable ->
            _metrics.value = _metrics.value.copy(errorMessage = throwable.message)
            false
        }
    }

    suspend fun pause() {
        runCatching { exerciseClient.pauseExercise() }
            .onFailure { _metrics.value = _metrics.value.copy(errorMessage = it.message) }
    }

    suspend fun resume() {
        runCatching { exerciseClient.resumeExercise() }
            .onFailure { _metrics.value = _metrics.value.copy(errorMessage = it.message) }
    }

    suspend fun end() {
        runCatching { exerciseClient.endExercise() }
            .onFailure { _metrics.value = _metrics.value.copy(errorMessage = it.message) }

        if (callbackRegistered) {
            exerciseClient.clearUpdateCallbackAsync(callback)
            callbackRegistered = false
        }
    }
}
