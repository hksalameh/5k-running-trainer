package com.rakdatak.core.training

enum class GoalFocus {
    BOTH,
    TIME,
    DISTANCE,
    COMPLETE,
}

data class GoalProgress(
    val bestRunDistanceMeters: Double,
    val bestContinuousRunSeconds: Int,
) {
    /** Warm-up and walking breaks never count toward the 5 km RUNNING goal. */
    val reachedFiveKilometers: Boolean = bestRunDistanceMeters >= 5_000.0
    val reachedThirtyMinutes: Boolean = bestContinuousRunSeconds >= 1_800
    /** Each is a valid independent success. A second milestone is optional. */
    val reachedAnyGoal: Boolean = reachedFiveKilometers || reachedThirtyMinutes

    val focus: GoalFocus = when {
        reachedAnyGoal -> GoalFocus.COMPLETE
        else -> GoalFocus.BOTH
    }

    val distanceProgress: Double = (bestRunDistanceMeters / 5_000.0).coerceIn(0.0, 1.0)
    val timeProgress: Double = (bestContinuousRunSeconds / 1_800.0).coerceIn(0.0, 1.0)
}
