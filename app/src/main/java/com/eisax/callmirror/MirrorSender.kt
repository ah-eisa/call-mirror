package com.eisax.callmirror

import android.content.Context
import kotlin.concurrent.thread

object MirrorSender {
    fun send(context: Context, payload: MirrorPayload) {
        if (MirrorPrefs.role(context) != MirrorPrefs.ROLE_SENDER) return
        val secret = MirrorPrefs.secret(context)
        if (secret.length < 16) return

        val appContext = context.applicationContext
        thread(name = "CallMirrorSend", isDaemon = true) {
            val sentLocal = if (MirrorPrefs.localEnabled(appContext)) {
                LocalTransport.send(
                    MirrorPrefs.localHost(appContext),
                    MirrorPrefs.localPort(appContext),
                    secret,
                    payload
                )
            } else {
                false
            }

            if (!sentLocal) {
                CloudRelay.publish(secret, payload)
            }
        }
    }
}
