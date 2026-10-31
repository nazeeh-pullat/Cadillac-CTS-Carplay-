package com.shihab.diplay.legacy

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22])
class StartupModesTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("startup-modes-test", Context.MODE_PRIVATE)

    @Test fun defaultsToTheFullAnimation() {
        prefs.edit().clear().commit()
        assertEquals(StartupAnimation.FULL, StartupModes.get(prefs))
    }

    @Test fun savedModePersistsAndUnknownValuesFallBack() {
        StartupModes.save(prefs, StartupAnimation.SHORT)
        assertEquals(StartupAnimation.SHORT, StartupModes.get(prefs))
        prefs.edit().putString("startup_animation", "BOGUS").commit()
        assertEquals(StartupAnimation.FULL, StartupModes.get(prefs))
    }

    @Test fun onlyOffSkipsTheAnimationAndShortIsFaster() {
        assertTrue(StartupAnimation.FULL.playsAnimation)
        assertTrue(StartupAnimation.SHORT.speed > StartupAnimation.FULL.speed)
        assertFalse(StartupAnimation.OFF.playsAnimation)
    }
}
