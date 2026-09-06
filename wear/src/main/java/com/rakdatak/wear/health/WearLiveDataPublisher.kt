package com.rakdatak.wear.health

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.rakdatak.core.training.WearSyncProtocol
import com.rakdatak.core.training.WorkoutSessionSnapshot

/** Publishes the latest watch workout state to the paired phone through the Wear data layer. */
class WearLiveDataPublisher(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    fun publish(snapshot: WorkoutSessionSnapshot?, metrics: WearExerciseMetrics) {
        val request = PutDataMapRequest.create(WearSyncProtocol.LIVE_DATA_PATH).apply {
            dataMap.putString(WearSyncProtocol.KEY_STATUS, snapshot?.status?.name ?: "IDLE")
            dataMap.putString(WearSyncProtocol.KEY_PHASE, snapshot?.currentPhase?.type?.name ?: "")
            dataMap.putInt(
                WearSyncProtocol.KEY_PHASE_REMAINING_SECONDS,
                snapshot?.phaseRemainingSeconds ?: 0,
            )
            dataMap.putInt(
                WearSyncProtocol.KEY_TOTAL_ELAPSED_SECONDS,
                snapshot?.totalElapsedSeconds ?: 0,
            )
            dataMap.putDouble(
                WearSyncProtocol.KEY_COMPLETION_RATIO,
                snapshot?.completionRatio ?: 0.0,
            )
            dataMap.putDouble(
                WearSyncProtocol.KEY_HEART_RATE_BPM,
                metrics.heartRateBpm ?: WearSyncProtocol.UNKNOWN_DOUBLE,
            )
            dataMap.putDouble(
                WearSyncProtocol.KEY_DISTANCE_METERS,
                metrics.distanceMeters ?: WearSyncProtocol.UNKNOWN_DOUBLE,
            )
            dataMap.putLong(WearSyncProtocol.KEY_UPDATED_AT_MILLIS, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
    }
}
