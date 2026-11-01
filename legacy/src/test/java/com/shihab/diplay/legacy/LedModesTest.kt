package com.shihab.diplay.legacy

import android.content.Context
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22])
class LedModesTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("led-test", Context.MODE_PRIVATE)
    private fun uuid(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    @Test fun elkColorCommandHasTheSevenEToEfFrame() {
        val bytes = LedModes.color(LedProtocol.ELK, 255, 128, 0)
        assertArrayEquals(byteArrayOf(0x7e, 0x07, 0x05, 0x03, 0xff.toByte(), 0x80.toByte(), 0x00, 0x10, 0xef.toByte()), bytes)
    }

    @Test fun trionesColorCommandHasTheFiftySixToAaFrame() {
        val bytes = LedModes.color(LedProtocol.TRIONES, 1, 2, 3)
        assertArrayEquals(byteArrayOf(0x56, 1, 2, 3, 0x00, 0xf0.toByte(), 0xaa.toByte()), bytes)
    }

    @Test fun colourValuesAreClampedToAByte() {
        val bytes = LedModes.color(LedProtocol.TRIONES, 999, -5, 255)
        assertEquals(255, bytes[1].toInt() and 0xff); assertEquals(0, bytes[2].toInt()); assertEquals(255, bytes[3].toInt() and 0xff)
    }

    @Test fun detectionPicksTheFamilyFromTheWritableCharacteristics() {
        assertEquals(LedProtocol.ELK, LedModes.detect(listOf(uuid("fff3"), uuid("2a00"))))
        assertEquals(LedProtocol.TRIONES, LedModes.detect(listOf(uuid("ffd9"))))
        assertEquals(LedProtocol.TRIONES, LedModes.detect(listOf(uuid("ffe9"))))
        assertNull(LedModes.detect(listOf(uuid("2a00"))))
        assertEquals(uuid("fff3"), LedModes.preferredCharacteristic(LedProtocol.ELK, listOf(uuid("fff3"))))
    }

    @Test fun savedStripAndColourPersistAndForgetClearsThem() {
        prefs.edit().clear().commit()
        assertNull(LedModes.address(prefs))
        LedModes.saveStrip(prefs, "AA:BB:CC:DD:EE:FF", "ELK-BLEDOM"); LedModes.saveColor(prefs, 3); LedModes.saveProtocol(prefs, LedProtocol.ELK)
        assertEquals("AA:BB:CC:DD:EE:FF", LedModes.address(prefs)); assertEquals(3, LedModes.colorIndex(prefs)); assertEquals(LedProtocol.ELK, LedModes.protocol(prefs))
        LedModes.forget(prefs)
        assertNull(LedModes.address(prefs)); assertEquals(-1, LedModes.colorIndex(prefs))
    }

    @Test fun scanRangeDefaultsToAnyAndSurvivesForget() {
        prefs.edit().clear().commit()
        assertEquals(LedScanRange.ANY, LedModes.scanRange(prefs))
        LedModes.saveScanRange(prefs, LedScanRange.CLOSE)
        LedModes.saveStrip(prefs, "AA:BB:CC:DD:EE:FF", "x"); LedModes.forget(prefs)
        assertEquals(LedScanRange.CLOSE, LedModes.scanRange(prefs))
        assertTrue(LedScanRange.values().map { it.minRssi }.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun paletteEndsWithOffAndHasNoText() {
        assertEquals("off", LedModes.palette.last().name)
        assertTrue(LedModes.palette.size >= 8)
    }
}
