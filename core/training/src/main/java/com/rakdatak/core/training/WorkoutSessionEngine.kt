package com.rakdatak.core.training

import com.rakdatak.core.training.model.WorkoutPhase
import com.rakdatak.core.training.model.WorkoutPlan

enum class WorkoutSessionStatus {
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
    STOPPED,
}

data class WorkoutSessionSnapshot(
    val status: WorkoutSessionStatus,
    val phaseIndex: Int,
    val currentPhase: WorkoutPhase,
    val phaseRemainingSeconds: Int,
    val totalElapsedSeconds: Int,
    val completionRatio: Double,
)

class WorkoutSessionEngine(
    private val plan: WorkoutPlan,
) {
    private var status = WorkoutSessionStatus.READY
    private var phaseIndex = 0
    private var phaseElapsedSeconds = 0
    private var totalElapsedSeconds = 0

    fun start(): WorkoutSessionSnapshot {
        if (status == WorkoutSessionStatus.READY) {
            status = WorkoutSessionStatus.RUNNING
        }
        return snapshot()
    }

    fun pause(): WorkoutSessionSnapshot {
        if (status == WorkoutSessionStatus.RUNNING) {
            status = WorkoutSessionStatus.PAUSED
        }
        return snapshot()
    }

    fun resume(): WorkoutSessionSnapshot {
        if (status == WorkoutSessionStatus.PAUSED) {
            status = WorkoutSessionStatus.RUNNING
        }
        return snapshot()
    }

    fun stop(): WorkoutSessionSnapshot {
        if (status != WorkoutSessionStatus.COMPLETED) {
            status = WorkoutSessionStatus.STOPPED
        }
        return snapshot()
    }

    /**
     * Advances the workout clock. Large ticks are handled correctly and may cross more than one
     * phase, which is useful if the app was briefly suspended and then catches up.
     */
    fun tick(seconds: Int = 1): WorkoutSessionSnapshot {
        require(seconds >= 0) { "seconds must be >= 0" }
        if (status != WorkoutSessionStatus.RUNNING || seconds == 0) return snapshot()

        var remainingTick = seconds
        while (remainingTick > 0 && status == WorkoutSessionStatus.RUNNING) {
            val phase = plan.phases[phaseIndex]
            val phaseRemaining = phase.durationSeconds - phaseElapsedSeconds
            val consumed = minOf(remainingTick, phaseRemaining)

            phaseElapsedSeconds += consumed
            totalElapsedSeconds += consumed
            remainingTick -= consumed

            if (phaseElapsedSeconds >= phase.durationSeconds) {
                moveToNextPhaseOrComplete()
            }
        }

        return snapshot()
    }

    fun snapshot(): WorkoutSessionSnapshot {
        val safeIndex = phaseIndex.coerceIn(0, plan.phases.lastIndex)
        val phase = plan.phases[safeIndex]
        val remaining = if (status == WorkoutSessionStatus.COMPLETED) {
            0
        } else {
            (phase.durationSeconds - phaseElapsedSeconds).coerceAtLeast(0)
        }

        return WorkoutSessionSnapshot(
            status = status,
            phaseIndex = safeIndex,
            currentPhase = phase,
            phaseRemainingSeconds = remaining,
            totalElapsedSeconds = totalElapsedSeconds,
            completionRatio = if (plan.totalDurationSeconds == 0) {
                0.0
            } else {
                (totalElapsedSeconds.toDouble() / plan.totalDurationSeconds).coerceIn(0.0, 1.0)
            },
        )
    }

    private fun moveToNextPhaseOrComplete() {
        if (phaseIndex >= plan.phases.lastIndex) {
            phaseElapsedSeconds = plan.phases.last().durationSeconds
            status = WorkoutSessionStatus.COMPLETED
            return
        }

        phaseIndex += 1
        phaseElapsedSeconds = 0
    }
}
