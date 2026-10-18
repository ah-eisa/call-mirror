package com.eisax.callmirror

import android.content.Context

object MirrorSender {
    fun send(context: Context, payload: MirrorPayload) {
        if (MirrorPrefs.role(context) != MirrorPrefs.ROLE_SENDER) return
        val secret = MirrorPrefs.secret(context)
        if (secret.length < 16) return
        CloudRelay(secret, onPayload = {}).send(payload)
    }
}
