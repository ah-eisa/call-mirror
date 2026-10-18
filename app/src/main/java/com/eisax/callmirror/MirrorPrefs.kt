package com.eisax.callmirror

import android.content.Context

object MirrorPrefs {
    private const val FILE = "call_mirror_prefs"
    private const val KEY_ROLE = "role"
    private const val KEY_MIRROR_NOTIFICATIONS = "mirror_notifications"
    private const val KEY_LOCAL_ENABLED = "local_enabled"
    private const val KEY_LOCAL_HOST = "local_host"
    private const val KEY_LOCAL_PORT = "local_port"
    private const val KEY_RINGTONE = "ringtone"
    private const val KEY_VIBRATE = "vibrate"

    const val ROLE_SENDER = "sender"
    const val ROLE_RECEIVER = "receiver"
    const val DEFAULT_PORT = 8080

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun role(context: Context): String = prefs(context).getString(KEY_ROLE, ROLE_SENDER) ?: ROLE_SENDER
    fun setRole(context: Context, role: String) { prefs(context).edit().putString(KEY_ROLE, role).apply() }

    fun secret(context: Context): String = SecretStore.load(context)
    fun setSecret(context: Context, secret: String) { SecretStore.save(context, secret.trim()) }

    fun mirrorNotifications(context: Context): Boolean = prefs(context).getBoolean(KEY_MIRROR_NOTIFICATIONS, true)
    fun setMirrorNotifications(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_MIRROR_NOTIFICATIONS, enabled).apply() }

    fun localEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCAL_ENABLED, false)
    fun setLocalEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_LOCAL_ENABLED, enabled).apply() }

    fun localHost(context: Context): String = prefs(context).getString(KEY_LOCAL_HOST, "") ?: ""
    fun setLocalHost(context: Context, host: String) { prefs(context).edit().putString(KEY_LOCAL_HOST, host.trim()).apply() }

    fun localPort(context: Context): Int = prefs(context).getInt(KEY_LOCAL_PORT, DEFAULT_PORT)
    fun setLocalPort(context: Context, port: Int) { prefs(context).edit().putInt(KEY_LOCAL_PORT, port.coerceIn(1024, 65535)).apply() }

    fun ringtone(context: Context): Boolean = prefs(context).getBoolean(KEY_RINGTONE, true)
    fun setRingtone(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_RINGTONE, enabled).apply() }

    fun vibrate(context: Context): Boolean = prefs(context).getBoolean(KEY_VIBRATE, true)
    fun setVibrate(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_VIBRATE, enabled).apply() }
}
