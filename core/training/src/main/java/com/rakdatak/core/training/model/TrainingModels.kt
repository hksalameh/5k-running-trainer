package com.rakdatak.core.training.model

enum class WorkoutPhaseType {
    WARM_UP,
    WALK,
    RUN,
    COOL_DOWN,
}

data class WorkoutPhase(
    val type: WorkoutPhaseType,
    val durationSeconds: Int,
)

data class WorkoutPlan(
    val id: String,
    val week: Int,
    val session: Int,
    val titleAr: String,
    val phases: List<WorkoutPhase>,
) {
    val totalDurationSeconds: Int = phases.sumOf { it.durationSeconds }
}

enum class PerceivedDifficulty {
    EASY,
    RIGHT,
    VERY_HARD,
}

enum class PainLevel {
    NONE,
    MILD,
    MODERATE,
    STRONG,
}

data class WorkoutResult(
    val planId: String,
    val completionRatio: Double,
    val distanceMeters: Double?,
    val continuousRunSeconds: Int,
    val averageHeartRateBpm: Int?,
    val excessiveHeartRateFraction: Double?,
    val unplannedWalkBreaks: Int,
    val difficulty: PerceivedDifficulty,
    val pain: PainLevel,
)

enum class TrainingAction {
    ADVANCE,
    ADVANCE_FASTER,
    REPEAT,
    STEP_BACK_ONE,
    RECOVERY,
}

data class TrainingDecision(
    val action: TrainingAction,
    val reasonAr: String,
)
