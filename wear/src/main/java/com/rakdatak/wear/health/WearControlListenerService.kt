package com.rakdatak.wear.health

import android.Manifest
import android.content.pm.PackageManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.rakdatak.core.training.WearSyncProtocol

/** Receives best-effort phone workout controls and mirrors them on the watch. */
class WearControlListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearSyncProtocol.CONTROL_MESSAGE_PATH) return

        val command = messageEvent.data.decodeToString()
        val parts = command.split('|')

        when (parts.firstOrNull()) {
            WearSyncProtocol.COMMAND_START -> {
                val planIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val elapsedSeconds = parts.getOrNull(2)?.toIntOrNull() ?: 0
                val gpsGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

                WearWorkoutService.start(
                    context = this,
                    gpsEnabled = gpsGranted,
                    planIndex = planIndex,
                    elapsedSeconds = elapsedSeconds,
                )
            }

            WearSyncProtocol.COMMAND_PAUSE -> WearWorkoutService.pause(this)
            WearSyncProtocol.COMMAND_RESUME -> WearWorkoutService.resume(this)
            WearSyncProtocol.COMMAND_STOP -> WearWorkoutService.stop(this)
        }
    }
}
