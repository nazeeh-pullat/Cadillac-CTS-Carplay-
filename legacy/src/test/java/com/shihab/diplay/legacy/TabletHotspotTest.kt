package com.shihab.diplay.legacy

import android.net.wifi.WifiConfiguration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22])
class TabletHotspotTest {
    @Test fun existingHotspotIsDisabledAndWaitedForBeforeRestart() {
        val events = mutableListOf<String>()
        var state = 13
        restartLegacyHotspot(
            disable = { events += "disable"; state = 10 }, state = { state },
            enable = { events += "enable"; assertEquals(11, state) }, cancelled = { false },
            pause = { events += "wait"; state = 11 },
        )
        assertEquals(listOf("disable", "wait", "enable"), events)
    }

    @Test fun stopDuringShutdownNeverTurnsTheHotspotBackOn() {
        var stopped = false
        var enabled = false
        try {
            restartLegacyHotspot(disable = {}, state = { 10 }, enable = { enabled = true },
                cancelled = { stopped }, pause = { stopped = true })
            fail("Cancelled startup must stop")
        } catch (_: IllegalStateException) { assertFalse(enabled) }
    }

    @Test fun shutdownTimeoutDoesNotAttemptToEnableAnAlreadyRunningHotspot() {
        var enabled = false
        try {
            restartLegacyHotspot(disable = {}, state = { 13 }, enable = { enabled = true },
                cancelled = { false }, pause = {}, attempts = 3)
            fail("Unfinished shutdown must time out")
        } catch (_: IllegalStateException) { assertFalse(enabled) }
    }
    @Test fun accessPointUsesRawCredentialsAndWpa2InsteadOfStationKeyManagement() {
        val config = TabletHotspot.configuration("Cadillac CTS", "example-pass-123")
        assertEquals("Cadillac CTS", config.SSID)
        assertEquals("example-pass-123", config.preSharedKey)
        assertEquals(1, config.allowedKeyManagement.cardinality())
        assertTrue(config.allowedKeyManagement[4])
        assertFalse(config.allowedKeyManagement[WifiConfiguration.KeyMgmt.NONE])
        assertTrue(config.allowedProtocols[WifiConfiguration.Protocol.RSN])
    }
}
