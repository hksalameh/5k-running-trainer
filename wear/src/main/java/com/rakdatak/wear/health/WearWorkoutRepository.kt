package com.rakdatak.wear.health

import com.rakdatak.core.training.WorkoutSessionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WearWorkoutState(
    val snapshot: WorkoutSessionSnapshot? = null,
    val metrics: WearExerciseMetrics = WearExerciseMetrics(),
    val gpsEnabled: Boolean = false,
    val serviceError: String? = null,
)

/**
 * Process-local state shared by the foreground workout service and the Wear UI.
 * Active workout ownership lives in the service so the Activity can disappear without stopping it.
 */
object WearWorkoutRepository {
    private val _state = MutableStateFlow(WearWorkoutState())
    val state: StateFlow<WearWorkoutState> = _state.asStateFlow()

    internal fun start(snapshot: WorkoutSessionSnapshot, gpsEnabled: Boolean) {
        _state.value = WearWorkoutState(
            snapshot = snapshot,
            gpsEnabled = gpsEnabled,
        )
    }

    internal fun updateSnapshot(snapshot: WorkoutSessionSnapshot) {
        _state.update { it.copy(snapshot = snapshot) }
    }

    internal fun updateMetrics(metrics: WearExerciseMetrics) {
        _state.update { it.copy(metrics = metrics) }
    }

    internal fun setServiceError(message: String?) {
        _state.update { it.copy(serviceError = message) }
    }

    fun resetAfterSummary() {
        _state.value = WearWorkoutState()
    }
}
