package com.eisax.callmirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MirrorService : Service() {
    private var relay: CloudRelay? = null
    private var localServer: LocalServer? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("mirror_service", "Call Mirror service", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(
            1,
            NotificationCompat.Builder(this, "mirror_service")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Call Mirror")
                .setContentText("Secure receiver is active")
                .setSilent(true)
                .setOngoing(true)
                .build()
        )
        refreshReceivers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshReceivers()
        return START_STICKY
    }

    override fun onDestroy() {
        relay?.close()
        relay = null
        localServer?.close()
        localServer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshReceivers() {
        relay?.close()
        relay = null
        localServer?.close()
        localServer = null

        if (MirrorPrefs.role(this) != MirrorPrefs.ROLE_RECEIVER) return
        val secret = MirrorPrefs.secret(this)
        if (secret.length < 16) return

        val deliver: (MirrorPayload) -> Unit = { AlertPresenter.show(this, it) }

        // One guard across both transports, so a payload that was already
        // delivered locally is not re-alerted when the cloud copy arrives (or
        // vice versa) — including when a local send falls back to the cloud.
        val guard = MessageGuard(
            MessageGuard.DEFAULT_MAX_MESSAGE_AGE_MS,
            REPLAY_CAPACITY,
            MessageGuard.DEFAULT_DRIFT_TOLERANCE_MS
        )
        relay = CloudRelay(secret, onPayload = deliver, replayGuard = guard).also { it.connect() }

        if (MirrorPrefs.localEnabled(this)) {
            localServer = LocalServer(MirrorPrefs.localPort(this), secret, deliver, guard).also { it.start() }
        }
    }

    private companion object {
        const val REPLAY_CAPACITY = 512
    }
}
