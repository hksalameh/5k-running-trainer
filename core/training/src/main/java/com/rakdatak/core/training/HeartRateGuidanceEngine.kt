package com.rakdatak.core.training

import kotlin.math.roundToInt

data class HeartRateProfile(
    val ageYears: Int? = null,
    val knownMaxHeartRateBpm: Int? = null,
) {
    init {
        require(ageYears == null || ageYears in 14..100)
        require(knownMaxHeartRateBpm == null || knownMaxHeartRateBpm in 100..240)
    }

    /**
     * Uses a known measured/personal max when available. Otherwise uses the Tanaka age estimate
     * (208 - 0.7 * age) as a training estimate, not as a medical limit.
     */
    fun maxHeartRateBpm(): Int? =
        knownMaxHeartRateBpm ?: ageYears?.let { age -> (208.0 - (0.7 * age)).roundToInt() }
}

enum class HeartRateGuidance {
    NO_GUIDANCE,
    COMFORTABLE,
    MODERATE,
    HARD,
    SLOW_DOWN,
    WALK_AND_RECOVER,
}

data class HeartRateGuidanceResult(
    val guidance: HeartRateGuidance,
    val percentOfEstimatedMax: Double? = null,
)

/**
 * Conservative beginner-oriented heart-rate guidance.
 *
 * Age-predicted maximum heart rate has meaningful individual error, so brief spikes never trigger
 * a pace change. Advice is only escalated after a sustained reading and should be combined with
 * perceived exertion / symptoms in the UI.
 */
class HeartRateGuidanceEngine(
    private val profile: HeartRateProfile,
    private val slowDownThresholdFraction: Double = 0.85,
    private val recoverThresholdFraction: Double = 0.90,
    private val slowDownSustainSeconds: Int = 30,
    private val recoverSustainSeconds: Int = 15,
) {
    private var secondsAboveSlowDown = 0
    private var secondsAboveRecover = 0

    fun reset() {
        secondsAboveSlowDown = 0
        secondsAboveRecover = 0
    }

    fun update(heartRateBpm: Double?, sampleDurationSeconds: Int = 1): HeartRateGuidanceResult {
        require(sampleDurationSeconds > 0)

        val maxHeartRate = profile.maxHeartRateBpm()
        if (heartRateBpm == null || heartRateBpm <= 0.0 || maxHeartRate == null) {
            reset()
            return HeartRateGuidanceResult(HeartRateGuidance.NO_GUIDANCE)
        }

        val fraction = heartRateBpm / maxHeartRate.toDouble()

        secondsAboveSlowDown = if (fraction >= slowDownThresholdFraction) {
            secondsAboveSlowDown + sampleDurationSeconds
        } else {
            0
        }

        secondsAboveRecover = if (fraction >= recoverThresholdFraction) {
            secondsAboveRecover + sampleDurationSeconds
        } else {
            0
        }

        val guidance = when {
            secondsAboveRecover >= recoverSustainSeconds -> HeartRateGuidance.WALK_AND_RECOVER
            secondsAboveSlowDown >= slowDownSustainSeconds -> HeartRateGuidance.SLOW_DOWN
            fraction < 0.50 -> HeartRateGuidance.COMFORTABLE
            fraction < 0.70 -> HeartRateGuidance.MODERATE
            else -> HeartRateGuidance.HARD
        }

        return HeartRateGuidanceResult(
            guidance = guidance,
            percentOfEstimatedMax = fraction,
        )
    }
}
