package com.shihab.diplay.legacy

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22])
class VideoModesTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("video-modes-test", Context.MODE_PRIVATE)

    @Test fun defaultsMatchTheTestedTabletMode() {
        prefs.edit().clear().commit()
        assertEquals(800, VideoModes.resolution(prefs).width)
        assertEquals(480, VideoModes.resolution(prefs).height)
        assertEquals(30, VideoModes.fps(prefs))
    }

    @Test fun savedChoicesPersist() {
        val native = VideoModes.resolutions.first { it.width == 1024 }
        VideoModes.saveResolution(prefs, native)
        VideoModes.saveFps(prefs, 24)
        assertEquals(native, VideoModes.resolution(prefs))
        assertEquals(24, VideoModes.fps(prefs))
    }

    @Test fun unknownValuesFallBackToDefaults() {
        prefs.edit().putString("video_resolution", "9999x1").putInt("video_fps", 144).commit()
        assertEquals(VideoModes.DEFAULT_RESOLUTION, VideoModes.resolution(prefs))
        assertEquals(VideoModes.DEFAULT_FPS, VideoModes.fps(prefs))
        VideoModes.saveFps(prefs, 144)
        assertEquals(VideoModes.DEFAULT_FPS, VideoModes.fps(prefs))
    }

    @Test fun everyPresetKeepsTheTabletsLandscapeShape() {
        VideoModes.resolutions.forEach { assertEquals(1.7, it.width.toDouble() / it.height, 0.1) }
    }
}
