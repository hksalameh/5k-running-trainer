package com.rakdatak.app.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.rakdatak.core.training.WearSyncProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WearLiveMetrics(
    val status: String? = null,
    val phase: String? = null,
    val phaseRemainingSeconds: Int = 0,
    val totalElapsedSeconds: Int = 0,
    val completionRatio: Double = 0.0,
    val heartRateBpm: Double? = null,
    val distanceMeters: Double? = null,
    val updatedAtMillis: Long = 0L,
) {
    fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
        updatedAtMillis > 0L && nowMillis - updatedAtMillis <= 15_000L
}

/**
 * Keeps the phone UI subscribed to the latest Wear OS workout state.
 * The DataItem is persistent, so opening the phone app after the watch workout started still loads
 * the most recent heart-rate/distance snapshot.
 */
class WearLiveMetricsRepository(context: Context) : DataClient.OnDataChangedListener {
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val _state = MutableStateFlow(WearLiveMetrics())
    val state: StateFlow<WearLiveMetrics> = _state.asStateFlow()

    fun start() {
        dataClient.addListener(this)
        dataClient.dataItems.addOnSuccessListener { items ->
            try {
                for (item in items) {
                    if (item.uri.path == WearSyncProtocol.LIVE_DATA_PATH) {
                        updateFrom(item)
                    }
                }
            } finally {
                items.release()
            }
        }
    }

    fun stop() {
        dataClient.removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearSyncProtocol.LIVE_DATA_PATH
            ) {
                updateFrom(event.dataItem)
            }
        }
    }

    private fun updateFrom(item: DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        val heartRate = map.getDouble(
            WearSyncProtocol.KEY_HEART_RATE_BPM,
            WearSyncProtocol.UNKNOWN_DOUBLE,
        ).takeIf { it >= 0.0 }
        val distance = map.getDouble(
            WearSyncProtocol.KEY_DISTANCE_METERS,
            WearSyncProtocol.UNKNOWN_DOUBLE,
        ).takeIf { it >= 0.0 }

        _state.value = WearLiveMetrics(
            status = map.getString(WearSyncProtocol.KEY_STATUS),
            phase = map.getString(WearSyncProtocol.KEY_PHASE),
            phaseRemainingSeconds = map.getInt(WearSyncProtocol.KEY_PHASE_REMAINING_SECONDS),
            totalElapsedSeconds = map.getInt(WearSyncProtocol.KEY_TOTAL_ELAPSED_SECONDS),
            completionRatio = map.getDouble(WearSyncProtocol.KEY_COMPLETION_RATIO),
            heartRateBpm = heartRate,
            distanceMeters = distance,
            updatedAtMillis = map.getLong(WearSyncProtocol.KEY_UPDATED_AT_MILLIS),
        )
    }
}
