package com.rakdatak.core.training

/**
 * Coordinates a prerecorded "next phase ... بلّش" prompt with the phase
 * boundary while the runner stays in motion. No phase-clock pauses or horns.
 *
 * Inputs are phase seconds remaining and exact measured audio clip length.
 * Compose may deliver a new snapshot a few milliseconds after a second ticks;
 * the initial extra second gives the player enough lead-in without losing
 * the aligned end-of-clip boundary.
 */
object WorkoutCueTiming {
    private const val PLAYER_MARGIN_MILLIS = 90L
    private const val MAX_LOOKAHEAD_EXTRA_SECONDS = 1L

    fun shouldQueue(remainingSeconds: Int, clipDurationMillis: Long): Boolean {
        if (remainingSeconds <= 0 || clipDurationMillis <= 0L) return false
        // Never play a cue so late that its closing "بلّش" falls AFTER the transition.
        if (remainingSeconds.toLong() * 1_000L < clipDurationMillis + PLAYER_MARGIN_MILLIS) {
            return false
        }
        val cueSeconds = (clipDurationMillis + PLAYER_MARGIN_MILLIS + 999L) / 1_000L
        return remainingSeconds.toLong() <= cueSeconds + MAX_LOOKAHEAD_EXTRA_SECONDS
    }

    fun delayUntilCue(remainingSeconds: Int, clipDurationMillis: Long): Long {
        return (remainingSeconds.toLong() * 1_000L -
            clipDurationMillis - PLAYER_MARGIN_MILLIS).coerceAtLeast(0L)
    }
}
