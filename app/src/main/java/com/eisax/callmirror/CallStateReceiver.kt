package com.eisax.callmirror

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (MirrorPrefs.role(context) != MirrorPrefs.ROLE_SENDER) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        @Suppress("DEPRECATION")
        val incoming = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        MirrorSender.send(
            context,
            MirrorPayload(
                type = MirrorPayload.TYPE_CALL,
                caller = incoming?.takeIf { it.isNotBlank() } ?: "Unknown caller"
            )
        )
    }
}
