package com.rakdatak.core.training

import com.rakdatak.core.training.model.TrainingAction

/** Maps an adaptive decision to the next baseline-plan position. */
class PlanProgressionEngine {
    fun nextIndex(
        currentIndex: Int,
        planCount: Int,
        action: TrainingAction,
    ): Int {
        require(planCount > 0)
        require(currentIndex in 0 until planCount)

        val target = when (action) {
            TrainingAction.ADVANCE -> currentIndex + 1
            TrainingAction.ADVANCE_FASTER -> currentIndex + 2
            TrainingAction.REPEAT -> currentIndex
            TrainingAction.STEP_BACK_ONE -> currentIndex - 1
            TrainingAction.RECOVERY -> currentIndex
        }

        return target.coerceIn(0, planCount - 1)
    }
}
