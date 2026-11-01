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
class AutoConnectTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("auto-connect-test", Context.MODE_PRIVATE)

    @Test fun onByDefaultAndRemembersTheChoice() {
        prefs.edit().clear().commit()
        assertTrue(AutoConnect.enabled(prefs))
        AutoConnect.save(prefs, false)
        assertFalse(AutoConnect.enabled(prefs))
    }

    @Test fun retryDelaysGrowThenLevelOff() {
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 40_000L), (0..4).map { AutoConnect.retryDelayMs(it) })
        assertEquals(5_000L, AutoConnect.retryDelayMs(-3))
    }

    @Test fun indicatorPrefersWhatIsHappeningNow() {
        assertEquals(LinkState.CONNECTED, LinkState.of(connected = true, connecting = true, retryPending = true, gaveUp = true))
        assertEquals(LinkState.CONNECTING, LinkState.of(false, true, true, false))
        assertEquals(LinkState.RETRYING, LinkState.of(false, false, true, true))
        assertEquals(LinkState.PAUSED, LinkState.of(false, false, false, true))
        assertEquals(LinkState.READY, LinkState.of(false, false, false, false))
    }
}
