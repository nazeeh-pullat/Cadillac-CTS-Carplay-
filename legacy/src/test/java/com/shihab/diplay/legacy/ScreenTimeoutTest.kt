package com.shihab.diplay.legacy

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22])
class ScreenTimeoutTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("screen-timeout-test", Context.MODE_PRIVATE)

    @Test fun defaultsToFifteenSeconds() {
        prefs.edit().clear().commit()
        assertEquals(15, ScreenTimeouts.get(prefs).seconds)
    }

    @Test fun savedValuePersistsAndUnknownValuesFallBack() {
        ScreenTimeouts.save(prefs, ScreenTimeout.M2)
        assertEquals(120, ScreenTimeouts.get(prefs).seconds)
        prefs.edit().putString("screen_timeout", "BOGUS").commit()
        assertEquals(ScreenTimeouts.DEFAULT, ScreenTimeouts.get(prefs))
    }

    @Test fun onlyNeverIsZeroAndTheRestAscend() {
        val seconds = ScreenTimeout.values().map { it.seconds }
        assertEquals(0, seconds.first())
        assertTrue(seconds.drop(1).zipWithNext().all { (a, b) -> a < b })
    }
}
