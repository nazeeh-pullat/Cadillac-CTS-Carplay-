package com.shihab.diplay.legacy

import android.content.SharedPreferences

/** How much of the Cadillac animation to play when the app opens. */
internal enum class StartupAnimation(val label: String, val note: String, val speed: Float) {
    FULL("Full", "The whole animation, about 5 seconds", 1f),
    SHORT("Short", "Plays about three times faster, about 2 seconds", 3f),
    OFF("Off", "Go straight to the Connect button", 0f);

    val playsAnimation get() = speed > 0f
}

internal object StartupModes {
    private const val KEY = "startup_animation"
    val DEFAULT = StartupAnimation.FULL

    fun get(prefs: SharedPreferences): StartupAnimation =
        StartupAnimation.values().firstOrNull { it.name == prefs.getString(KEY, null) } ?: DEFAULT

    fun save(prefs: SharedPreferences, value: StartupAnimation) {
        prefs.edit().putString(KEY, value.name).apply()
    }
}
