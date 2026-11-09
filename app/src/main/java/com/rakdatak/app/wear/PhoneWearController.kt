package com.rakdatak.app.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.rakdatak.core.training.*
import java.util.UUID

class PhoneWearController(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("phone_workout_sync", Context.MODE_PRIVATE)
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    var sessionId: String = prefs.getString("session_id", null)
        ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString("session_id", generated).commit()
        }
        private set
    var planIndex: Int = prefs.getInt("plan_index", 0)
        private set
    var elapsed: Int = 0
    private var lastSent = 0L

    fun newSession(index: Int) {
        sessionId = UUID.randomUUID().toString()
        planIndex = index
        prefs.edit().putString("session_id", sessionId).putInt("plan_index", index).commit()
    }
    fun start(planIndex: Int, elapsedSeconds: Int = 0, gpsEnabled: Boolean = true, paused: Boolean = false) {
        this.planIndex = planIndex
        prefs.edit().putInt("plan_index", planIndex).apply()
        elapsed = elapsedSeconds
        send("START", gps = gpsEnabled, paused = paused)
    }
    fun pause() = send("PAUSE")
    fun resume() = send("RESUME")
    fun stop() = send("STOP")
    fun controlWatch(action: String, id: String, index: Int, seconds: Int) =
        send(action, id, WorkoutOwner.WATCH, index, seconds)

    private fun send(action: String, id: String = sessionId, owner: WorkoutOwner = WorkoutOwner.PHONE,
        index: Int = planIndex, seconds: Int = elapsed, gps: Boolean = false, paused: Boolean = false) {
        lastSent = maxOf(System.currentTimeMillis(), lastSent + 1)
        val command = WorkoutCommand(action, id, owner, WorkoutOwner.PHONE, lastSent, index, seconds, gps, paused)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (WorkoutSync.fresh(command.sentAt, System.currentTimeMillis())) nodes.forEach {
                messageClient.sendMessage(it.id, WearSyncProtocol.CONTROL_MESSAGE_PATH, command.encode().encodeToByteArray())
            }
        }
    }
}
