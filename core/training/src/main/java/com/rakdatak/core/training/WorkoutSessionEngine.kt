package com.rakdatak.core.training

import com.rakdatak.core.training.model.WorkoutPhase
import com.rakdatak.core.training.model.WorkoutPlan
import com.rakdatak.core.training.model.WorkoutPhaseType

enum class WorkoutSessionStatus {
    READY,
    ANNOUNCING,
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
    val nextPhase: WorkoutPhase? = null,
    val longestContinuousRunSeconds: Int = 0,
    val planId: String = "",
)

class WorkoutSessionEngine(
    private val plan: WorkoutPlan,
) {
    private var status = WorkoutSessionStatus.READY
    private var phaseIndex = 0
    private var phaseElapsedSeconds = 0
    private var totalElapsedSeconds = 0
    private var currentContinuousRunSeconds = 0
    private var longestContinuousRunSeconds = 0
    private var pausedDuringAnnouncement = false

    fun start(): WorkoutSessionSnapshot {
        if (status == WorkoutSessionStatus.READY) {
            status = WorkoutSessionStatus.ANNOUNCING
        }
        return snapshot()
    }

    /** The phase clock starts only when its entire spoken instruction has finished. */
    fun instructionFinished(): WorkoutSessionSnapshot {
        if (status == WorkoutSessionStatus.ANNOUNCING) {
            status = WorkoutSessionStatus.RUNNING
        }
        return snapshot()
    }

    /**
     * Restores a previously active workout from persisted elapsed time.
     * This keeps the recovery logic inside the engine so phone and watch clients can safely
     * reconstruct phase position without duplicating phase calculations.
     */
    fun restore(
        elapsedSeconds: Int,
        paused: Boolean = false,
        recordedLongestContinuousRunSeconds: Int = 0,
    ): WorkoutSessionSnapshot {
        require(elapsedSeconds >= 0) { "elapsedSeconds must be >= 0" }
        require(recordedLongestContinuousRunSeconds >= 0)

        status = WorkoutSessionStatus.ANNOUNCING
        phaseIndex = 0
        phaseElapsedSeconds = 0
        totalElapsedSeconds = 0
        currentContinuousRunSeconds = 0
        longestContinuousRunSeconds = 0
        pausedDuringAnnouncement = false

        val boundedElapsed = elapsedSeconds.coerceAtMost(plan.totalDurationSeconds)
        totalElapsedSeconds = boundedElapsed
        if (boundedElapsed == plan.totalDurationSeconds) {
            phaseIndex = plan.phases.lastIndex
            phaseElapsedSeconds = plan.phases.last().durationSeconds
            status = WorkoutSessionStatus.COMPLETED
        } else {
            var secondsToLocate = boundedElapsed
            plan.phases.forEachIndexed { index, phase ->
                if (secondsToLocate >= 0 && secondsToLocate < phase.durationSeconds) {
                    phaseIndex = index
                    phaseElapsedSeconds = secondsToLocate
                    status = if (secondsToLocate == 0) {
                        WorkoutSessionStatus.ANNOUNCING
                    } else {
                        WorkoutSessionStatus.RUNNING
                    }
                    secondsToLocate = -1
                } else if (secondsToLocate >= 0) {
                    secondsToLocate -= phase.durationSeconds
                }
            }
        }
        // We cannot infer whether old RUN phases had pauses from elapsedSeconds alone.
        // Restore the actual recorded best instead of falsely treating them as continuous.
        longestContinuousRunSeconds =
            recordedLongestContinuousRunSeconds.coerceAtMost(boundedElapsed)
        currentContinuousRunSeconds = 0

        pausedDuringAnnouncement = status == WorkoutSessionStatus.ANNOUNCING
        if (status != WorkoutSessionStatus.COMPLETED && paused) {
            status = WorkoutSessionStatus.PAUSED
        }

        return snapshot()
    }

    fun pause(): WorkoutSessionSnapshot {
        if (status == WorkoutSessionStatus.RUNNING || status == WorkoutSessionStatus.ANNOUNCING) {
            currentContinuousRunSeconds = 0
            pausedDuringAnnouncement = status == WorkoutSessionStatus.ANNOUNCING
            status = WorkoutSessionStatus.PAUSED
        }
        return snapshot()
    }

    fun resume(): WorkoutSessionSnapshot {
        if (status == WorkoutSessionStatus.PAUSED) {
            status = if (pausedDuringAnnouncement) {
                WorkoutSessionStatus.ANNOUNCING
            } else {
                WorkoutSessionStatus.RUNNING
            }
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
            if (phase.type == WorkoutPhaseType.RUN) {
                currentContinuousRunSeconds += consumed
                longestContinuousRunSeconds = maxOf(
                    longestContinuousRunSeconds, currentContinuousRunSeconds,
                )
            }

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
        val nextPhase = if (
            status == WorkoutSessionStatus.COMPLETED || status == WorkoutSessionStatus.STOPPED
        ) {
            null
        } else {
            plan.phases.getOrNull(safeIndex + 1)
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
            nextPhase = nextPhase,
            longestContinuousRunSeconds = longestContinuousRunSeconds,
            planId = plan.id,
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
        currentContinuousRunSeconds = 0
        status = WorkoutSessionStatus.ANNOUNCING
    }
}
