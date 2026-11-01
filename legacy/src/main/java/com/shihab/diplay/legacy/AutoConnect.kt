package com.shihab.diplay.legacy

import android.content.SharedPreferences

/** When the dashboard connects to the saved iPhone by itself, and how it backs off when a connection attempt fails. */
internal object AutoConnect {
    private const val KEY = "auto_connect"
    const val MAX_ATTEMPTS = 8

    fun enabled(prefs: SharedPreferences) = prefs.getBoolean(KEY, true)
    fun save(prefs: SharedPreferences, value: Boolean) { prefs.edit().putBoolean(KEY, value).apply() }

    /** 5 s, 10 s, 20 s, then 40 s between attempts, so a phone that is not there yet is not hammered. */
    fun retryDelayMs(attempt: Int): Long = 5_000L shl attempt.coerceIn(0, 3)
}

/** What the small indicator on Home says about the link to the iPhone. */
internal enum class LinkState(val label: String, val dot: Int) {
    READY("Ready", 0xff8b8f96.toInt()),
    CONNECTING("Connecting", 0xffe8b04a.toInt()),
    CONNECTED("Connected", 0xff3ddc84.toInt()),
    RETRYING("Reconnecting", 0xffe8b04a.toInt()),
    PAUSED("Not connected", 0xffe5566b.toInt());

    companion object {
        fun of(connected: Boolean, connecting: Boolean, retryPending: Boolean, gaveUp: Boolean) = when {
            connected -> CONNECTED
            connecting -> CONNECTING
            retryPending -> RETRYING
            gaveUp -> PAUSED
            else -> READY
        }
    }
}
