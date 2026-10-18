package com.eisax.callmirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object AlertPresenter {
    private const val CHANNEL = "mirrored_alerts"

    fun show(context: Context, payload: MirrorPayload) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Mirrored alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
        )

        val heading = if (payload.type == MirrorPayload.TYPE_CALL) {
            "Incoming call alert"
        } else {
            listOfNotNull(payload.appName, payload.title).joinToString(" • ").ifBlank { "New alert" }
        }
        val detail = if (payload.type == MirrorPayload.TYPE_CALL) payload.caller.orEmpty() else payload.text.orEmpty()

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(heading)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setLocalOnly(true)
            .build()

        manager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
    }
}
