package com.rakdatak.wear.health

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.rakdatak.core.training.*

class WearControlListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearSyncProtocol.CONTROL_MESSAGE_PATH) return
        val raw = messageEvent.data.decodeToString()
        val command = WorkoutSync.parse(raw) ?: return
        if (!WorkoutSync.fresh(command.sentAt, System.currentTimeMillis())) return
        val intent = Intent(this, WearWorkoutService::class.java).putExtra("remote_command", raw)
        runCatching {
            if (command.action == "START") ContextCompat.startForegroundService(this, intent)
            else if (WearWorkoutRepository.state.value.snapshot?.status in
                setOf(WorkoutSessionStatus.RUNNING, WorkoutSessionStatus.PAUSED)) startService(intent)
        }
    }
}
