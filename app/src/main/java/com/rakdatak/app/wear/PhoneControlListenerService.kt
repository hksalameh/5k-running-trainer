package com.rakdatak.app.wear

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.rakdatak.core.training.*

/** Durable inbox: background reception never starts a timer or resumes a recovered workout. */
class PhoneControlListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearSyncProtocol.REVERSE_MESSAGE_PATH) return
        val raw = event.data.decodeToString()
        val c = WorkoutSync.parse(raw) ?: return
        val prefs = getSharedPreferences("phone_workout_sync", Context.MODE_PRIVATE)
        if (!WorkoutSync.accepts(c, prefs.getString("session_id", null), WorkoutOwner.PHONE,
                WorkoutOwner.PHONE, System.currentTimeMillis())) return
        synchronized(PhoneCommandInbox) {
            val inbox = prefs.getStringSet("inbox", emptySet()).orEmpty().toMutableSet()
            inbox.add(raw)
            prefs.edit().putStringSet("inbox", inbox.sortedByDescending {
                WorkoutSync.parse(it)?.sentAt ?: 0
            }.take(64).toSet()).commit()
        }
    }
}

object PhoneCommandInbox {
    private const val LAST_PROCESSED_COMMAND = "last_processed_watch_command_at"

    fun pending(context: Context): List<WorkoutCommand> = synchronized(this) {
        val prefs = context.getSharedPreferences("phone_workout_sync", Context.MODE_PRIVATE)
        val lastProcessed = prefs.getLong(LAST_PROCESSED_COMMAND, 0L)
        prefs.getStringSet("inbox", emptySet()).orEmpty()
            .mapNotNull(WorkoutSync::parse)
            .filter { it.sentAt > lastProcessed }
            .sortedBy { it.sentAt }
    }

    fun acknowledge(context: Context, command: WorkoutCommand, applied: Boolean) = synchronized(this) {
        val prefs = context.getSharedPreferences("phone_workout_sync", Context.MODE_PRIVATE)
        val editor = prefs.edit().putStringSet(
            "inbox",
            prefs.getStringSet("inbox", emptySet()).orEmpty() - command.encode(),
        )
        if (applied) {
            editor.putLong(
                LAST_PROCESSED_COMMAND,
                maxOf(prefs.getLong(LAST_PROCESSED_COMMAND, 0L), command.sentAt),
            )
        }
        editor.commit()
    }
}
