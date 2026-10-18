package com.eisax.callmirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MirrorService : Service() {
    private var relay: CloudRelay? = null

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
                .setContentText("Secure sync is active")
                .setSilent(true)
                .setOngoing(true)
                .build()
        )
        refreshRelay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshRelay()
        return START_STICKY
    }

    override fun onDestroy() {
        relay?.close()
        relay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshRelay() {
        relay?.close()
        relay = null
        if (MirrorPrefs.role(this) != MirrorPrefs.ROLE_RECEIVER) return
        val secret = MirrorPrefs.secret(this)
        if (secret.length < 16) return
        relay = CloudRelay(secret, onPayload = { AlertPresenter.show(this, it) }).also { it.connect() }
    }
}
