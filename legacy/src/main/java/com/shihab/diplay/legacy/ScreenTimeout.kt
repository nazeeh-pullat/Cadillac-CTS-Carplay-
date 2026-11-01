package com.shihab.diplay.legacy

import android.content.SharedPreferences

/** How long Home waits without a touch before it dims down to just the Cadillac logo. */
internal enum class ScreenTimeout(val label: String, val seconds: Int) {
    OFF("Never", 0),
    S10("10 seconds", 10),
    S15("15 seconds", 15),
    S30("30 seconds", 30),
    M1("1 minute", 60),
    M2("2 minutes", 120),
    M5("5 minutes", 300),
}

internal object ScreenTimeouts {
    private const val KEY = "screen_timeout"
    val DEFAULT = ScreenTimeout.S15

    fun get(prefs: SharedPreferences): ScreenTimeout =
        ScreenTimeout.values().firstOrNull { it.name == prefs.getString(KEY, null) } ?: DEFAULT

    fun save(prefs: SharedPreferences, value: ScreenTimeout) {
        prefs.edit().putString(KEY, value.name).apply()
    }
}
