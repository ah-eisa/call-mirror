package com.eisax.callmirror

import android.content.Context

object MirrorPrefs {
    private const val FILE = "call_mirror_prefs"
    private const val KEY_ROLE = "role"
    private const val KEY_MIRROR_NOTIFICATIONS = "mirror_notifications"

    const val ROLE_SENDER = "sender"
    const val ROLE_RECEIVER = "receiver"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun role(context: Context): String = prefs(context).getString(KEY_ROLE, ROLE_SENDER) ?: ROLE_SENDER

    fun setRole(context: Context, role: String) {
        prefs(context).edit().putString(KEY_ROLE, role).apply()
    }

    fun secret(context: Context): String = SecretStore.load(context)

    fun setSecret(context: Context, secret: String) {
        SecretStore.save(context, secret.trim())
    }

    fun mirrorNotifications(context: Context): Boolean = prefs(context).getBoolean(KEY_MIRROR_NOTIFICATIONS, true)

    fun setMirrorNotifications(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MIRROR_NOTIFICATIONS, enabled).apply()
    }
}
