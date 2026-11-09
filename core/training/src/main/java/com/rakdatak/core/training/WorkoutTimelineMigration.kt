package com.rakdatak.core.training

import com.rakdatak.core.training.model.WorkoutPlan

/**
 * Preserve the exact phase and seconds within that phase when a workout
 * started under an older plan is resumed after an app upgrade.
 *
 * Example: old warm-up 180s -> new warm-up 120s. Someone 40s into
 * the first run is still 40s into that run, instead of being sent
 * back into warm-up by restoring the old total elapsed seconds.
 *
 * Completed history and workout plan identifiers are not modified.
 */
object WorkoutTimelineMigration {
    fun migrateElapsed(
        oldPlan: WorkoutPlan,
        newPlan: WorkoutPlan,
        oldElapsedSeconds: Int,
    ): Int {
        require(oldPlan.id == newPlan.id) { "A workout must remain in the same plan" }
        require(oldPlan.phases.size == newPlan.phases.size) {
            "Phase-count mismatch: cannot safely migrate this workout"
        }
        require(oldPlan.phases.indices.all { i ->
            oldPlan.phases[i].type == newPlan.phases[i].type
        }) { "Phase-order mismatch: cannot safely migrate this workout" }

        var oldRemaining = oldElapsedSeconds.coerceIn(0, oldPlan.totalDurationSeconds)
        var newElapsed = 0
        oldPlan.phases.zip(newPlan.phases).forEach { (oldPhase, newPhase) ->
            if (oldRemaining < oldPhase.durationSeconds) {
                // An unfinished old phase must not become a completed new phase.
                return newElapsed + oldRemaining.coerceAtMost(newPhase.durationSeconds - 1)
            }
            oldRemaining -= oldPhase.durationSeconds
            newElapsed += newPhase.durationSeconds
        }
        return newPlan.totalDurationSeconds
    }
}
