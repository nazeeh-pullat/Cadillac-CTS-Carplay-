package com.shihab.diplay.legacy

import android.content.SharedPreferences

/** CarPlay video size the tablet asks the iPhone for; touch is normalized, so any size maps correctly. */
internal data class VideoResolution(val width: Int, val height: Int, val name: String, val note: String) {
    val label get() = "$width × $height"
}

internal object VideoModes {
    val resolutions = listOf(
        VideoResolution(640, 384, "Light", "Smoothest on this tablet, softer picture"),
        VideoResolution(800, 480, "Balanced", "Default; tested on this tablet"),
        VideoResolution(1024, 600, "Native", "Matches the tablet's own screen, sharper text"),
        VideoResolution(1280, 720, "Sharp", "Experimental; may stutter on this chip"),
    )
    val frameRates = listOf(20, 24, 30)

    val DEFAULT_RESOLUTION get() = resolutions[1]
    const val DEFAULT_FPS = 30

    private const val KEY_RESOLUTION = "video_resolution"
    private const val KEY_FPS = "video_fps"

    fun resolution(prefs: SharedPreferences): VideoResolution {
        val saved = prefs.getString(KEY_RESOLUTION, null)
        return resolutions.firstOrNull { "${it.width}x${it.height}" == saved } ?: DEFAULT_RESOLUTION
    }

    fun fps(prefs: SharedPreferences): Int = prefs.getInt(KEY_FPS, DEFAULT_FPS).takeIf { it in frameRates } ?: DEFAULT_FPS

    fun saveResolution(prefs: SharedPreferences, value: VideoResolution) {
        prefs.edit().putString(KEY_RESOLUTION, "${value.width}x${value.height}").apply()
    }

    fun saveFps(prefs: SharedPreferences, value: Int) {
        if (value in frameRates) prefs.edit().putInt(KEY_FPS, value).apply()
    }
}
