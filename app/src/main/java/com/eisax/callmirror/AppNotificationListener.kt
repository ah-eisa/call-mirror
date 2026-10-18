package com.eisax.callmirror

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class AppNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (!MirrorPrefs.mirrorNotifications(this)) return
        if (MirrorPrefs.role(this) != MirrorPrefs.ROLE_SENDER) return
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        var body = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if (body.isBlank()) {
            body = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        }
        if (body.isBlank()) {
            val lines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
            if (!lines.isNullOrEmpty()) body = lines.joinToString("\n")
        }
        if (body.isBlank()) {
            val messages = extras.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)
            val last = messages?.lastOrNull() as? android.os.Bundle
            body = last?.getCharSequence("text")?.toString().orEmpty()
        }

        if (title.isBlank() && body.isBlank()) return

        val appName = try {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        MirrorSender.send(
            this,
            MirrorPayload(
                type = MirrorPayload.TYPE_NOTIFICATION,
                appName = appName,
                title = title.takeIf { it.isNotBlank() },
                text = body.takeIf { it.isNotBlank() }
            )
        )
    }
}
