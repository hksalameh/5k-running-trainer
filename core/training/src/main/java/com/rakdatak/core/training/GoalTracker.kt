package com.rakdatak.core.training

enum class GoalFocus {
    BOTH,
    TIME,
    DISTANCE,
    COMPLETE,
}

data class GoalProgress(
    val bestDistanceMeters: Double,
    val bestContinuousRunSeconds: Int,
) {
    val reachedFiveKilometers: Boolean = bestDistanceMeters >= 5_000.0
    val reachedThirtyMinutes: Boolean = bestContinuousRunSeconds >= 1_800

    val focus: GoalFocus = when {
        reachedFiveKilometers && reachedThirtyMinutes -> GoalFocus.COMPLETE
        reachedFiveKilometers -> GoalFocus.TIME
        reachedThirtyMinutes -> GoalFocus.DISTANCE
        else -> GoalFocus.BOTH
    }

    val distanceProgress: Double = (bestDistanceMeters / 5_000.0).coerceIn(0.0, 1.0)
    val timeProgress: Double = (bestContinuousRunSeconds / 1_800.0).coerceIn(0.0, 1.0)
}
