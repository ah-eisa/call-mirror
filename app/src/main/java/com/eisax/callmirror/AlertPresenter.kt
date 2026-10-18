package com.eisax.callmirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.content.Context
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

object AlertPresenter {
    fun show(context: Context, payload: MirrorPayload) {
        val ringtone = MirrorPrefs.ringtone(context)
        val vibrate = MirrorPrefs.vibrate(context)
        val channelId = "mirrored_alerts_${if (ringtone) 1 else 0}${if (vibrate) 1 else 0}"
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(channelId, "Mirrored alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                enableVibration(vibrate)
                if (vibrate) vibrationPattern = longArrayOf(0, 250, 150, 350)
                if (!ringtone) setSound(null, null)
            }
        )

        val heading = if (payload.type == MirrorPayload.TYPE_CALL) {
            "Incoming call alert"
        } else {
            listOfNotNull(payload.appName, payload.title).joinToString(" • ").ifBlank { "New alert" }
        }
        val detail = if (payload.type == MirrorPayload.TYPE_CALL) payload.caller.orEmpty() else payload.text.orEmpty()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(heading)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setLocalOnly(true)

        if (ringtone) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            builder.setSilent(true)
        }
        if (vibrate) builder.setVibrate(longArrayOf(0, 250, 150, 350))

        manager.notify(payload.messageId.hashCode(), builder.build())
    }
}
