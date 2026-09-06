package com.rakdatak.app.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.rakdatak.core.training.WearSyncProtocol

/** Sends best-effort workout controls from the phone to every connected Wear OS node. */
class PhoneWearController(context: Context) {
    private val appContext = context.applicationContext
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)

    fun start(planIndex: Int, elapsedSeconds: Int = 0) {
        send(WearSyncProtocol.startCommand(planIndex, elapsedSeconds))
    }

    fun pause() = send(WearSyncProtocol.COMMAND_PAUSE)

    fun resume() = send(WearSyncProtocol.COMMAND_RESUME)

    fun stop() = send(WearSyncProtocol.COMMAND_STOP)

    private fun send(command: String) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            val payload = command.encodeToByteArray()
            nodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    WearSyncProtocol.CONTROL_MESSAGE_PATH,
                    payload,
                )
            }
        }
    }
}
