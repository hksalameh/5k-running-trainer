package com.rakdatak.core.training

/** Shared keys for live workout state exchanged between the phone and Wear OS app. */
object WearSyncProtocol {
    const val LIVE_DATA_PATH = "/rakdatak/live-workout"

    const val KEY_STATUS = "status"
    const val KEY_PHASE = "phase"
    const val KEY_PHASE_REMAINING_SECONDS = "phase_remaining_seconds"
    const val KEY_TOTAL_ELAPSED_SECONDS = "total_elapsed_seconds"
    const val KEY_COMPLETION_RATIO = "completion_ratio"
    const val KEY_HEART_RATE_BPM = "heart_rate_bpm"
    const val KEY_DISTANCE_METERS = "distance_meters"
    const val KEY_UPDATED_AT_MILLIS = "updated_at_millis"

    const val UNKNOWN_DOUBLE = -1.0
}