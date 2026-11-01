package com.shihab.diplay.legacy

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID

/** The command families used by the cheap Bluetooth LE LED controllers behind apps such as Lotus Lantern. */
internal enum class LedProtocol(val label: String) {
    AUTO("Auto-detect"),
    ELK("ELK-BLEDOM family (7E … EF)"),
    TRIONES("Triones / LEDBLE family (56 … AA)"),
}

/** How strong a Bluetooth signal a device needs to be listed while scanning; hold the tablet near the strip to find it. */
internal enum class LedScanRange(val label: String, val note: String, val minRssi: Int) {
    ANY("Any signal", "Every device in range, including weak ones", -127),
    NEAR("Nearby", "A few metres, about one room", -80),
    CLOSE("Close", "Within about a metre or two", -65),
    TOUCHING("Very close", "Hold the tablet right next to the strip", -50),
}

internal data class LedColor(val name: String, val red: Int, val green: Int, val blue: Int) {
    val argb get() = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
}

internal object LedModes {
    private fun uuid(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
    val ELK_WRITE = uuid("fff3")
    val TRIONES_WRITE = listOf(uuid("ffd9"), uuid("ffe9"))

    /** The swatches shown on Home; the last one switches the lights off. */
    val palette = listOf(
        LedColor("red", 255, 0, 0), LedColor("orange", 255, 90, 0), LedColor("amber", 255, 170, 0),
        LedColor("green", 0, 255, 40), LedColor("cyan", 0, 210, 255), LedColor("blue", 0, 70, 255),
        LedColor("purple", 150, 0, 255), LedColor("pink", 255, 0, 140), LedColor("white", 255, 255, 255),
        LedColor("off", 0, 0, 0),
    )

    /** Picks the command family from the writable characteristics a controller offers. */
    fun detect(writable: Collection<UUID>): LedProtocol? = when {
        ELK_WRITE in writable -> LedProtocol.ELK
        TRIONES_WRITE.any { it in writable } -> LedProtocol.TRIONES
        else -> null
    }

    fun preferredCharacteristic(protocol: LedProtocol, writable: Collection<UUID>): UUID? = when (protocol) {
        LedProtocol.ELK -> ELK_WRITE.takeIf { it in writable }
        LedProtocol.TRIONES -> TRIONES_WRITE.firstOrNull { it in writable }
        LedProtocol.AUTO -> null
    }

    fun color(protocol: LedProtocol, red: Int, green: Int, blue: Int): ByteArray {
        fun v(x: Int) = x.coerceIn(0, 255).toByte()
        return when (protocol) {
            LedProtocol.ELK -> byteArrayOf(0x7e, 0x07, 0x05, 0x03, v(red), v(green), v(blue), 0x10, 0xef.toByte())
            LedProtocol.TRIONES -> byteArrayOf(0x56, v(red), v(green), v(blue), 0x00, 0xf0.toByte(), 0xaa.toByte())
            LedProtocol.AUTO -> throw IllegalArgumentException("Resolve the protocol before building a command")
        }
    }

    private const val KEY_ADDRESS = "led_address"
    private const val KEY_NAME = "led_name"
    private const val KEY_PROTOCOL = "led_protocol"
    private const val KEY_COLOR = "led_color"

    fun address(prefs: SharedPreferences): String? = prefs.getString(KEY_ADDRESS, null)
    fun name(prefs: SharedPreferences): String? = prefs.getString(KEY_NAME, null)
    fun protocol(prefs: SharedPreferences): LedProtocol =
        LedProtocol.values().firstOrNull { it.name == prefs.getString(KEY_PROTOCOL, null) } ?: LedProtocol.AUTO
    fun colorIndex(prefs: SharedPreferences): Int = prefs.getInt(KEY_COLOR, -1).takeIf { it in palette.indices } ?: -1

    fun scanRange(prefs: SharedPreferences): LedScanRange =
        LedScanRange.values().firstOrNull { it.name == prefs.getString("led_scan_range", null) } ?: LedScanRange.ANY
    fun saveScanRange(prefs: SharedPreferences, value: LedScanRange) { prefs.edit().putString("led_scan_range", value.name).apply() }

    fun saveStrip(prefs: SharedPreferences, address: String, name: String?) {
        prefs.edit().putString(KEY_ADDRESS, address).putString(KEY_NAME, name ?: "LED strip").apply()
    }
    fun saveProtocol(prefs: SharedPreferences, value: LedProtocol) { prefs.edit().putString(KEY_PROTOCOL, value.name).apply() }
    fun saveColor(prefs: SharedPreferences, index: Int) { prefs.edit().putInt(KEY_COLOR, index).apply() }
    fun forget(prefs: SharedPreferences) {
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_NAME).remove(KEY_COLOR).apply()
    }
}

// BLUETOOTH and BLUETOOTH_ADMIN are install-time permissions on Android 5.1, the only version this module runs on;
// BLUETOOTH_SCAN/CONNECT exist only on Android 12+.
@android.annotation.SuppressLint("MissingPermission")
/**
 * One Bluetooth LE connection to the saved LED controller. Writes are sent one at a time and only the
 * newest pending colour is kept, so dragging across swatches never floods the controller.
 */
internal class LedStrip(private val context: Context, private val prefs: SharedPreferences) {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var gatt: BluetoothGatt? = null
    private var writer: BluetoothGattCharacteristic? = null
    private var resolved: LedProtocol? = null
    private var pending: ByteArray? = null
    private var writing = false
    private var writeStartedAt = 0L
    @Volatile var summary: String = "Not connected"
        private set
    @Volatile var onChange: (() -> Unit)? = null

    val connected get() = synchronized(lock) { writer != null }

    fun connect() {
        val address = LedModes.address(prefs) ?: return
        synchronized(lock) { if (gatt != null) return }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return summarise("Bluetooth is not available")
        if (!adapter.isEnabled) return summarise("Bluetooth is off")
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return summarise("Saved strip address is invalid")
        summarise("Connecting…")
        val opened = device.connectGatt(context, false, callback)
        synchronized(lock) { gatt = opened }
    }

    fun setColor(red: Int, green: Int, blue: Int) {
        val command = synchronized(lock) {
            val protocol = resolved
            if (writer == null || protocol == null) null else LedModes.color(protocol, red, green, blue)
        }
        if (command == null) {
            // Not ready yet: remember the request as an RGB triple and connect.
            synchronized(lock) { queuedRgb = intArrayOf(red, green, blue) }
            connect()
            return
        }
        synchronized(lock) { pending = command }
        pump()
    }

    @Volatile private var queuedRgb: IntArray? = null

    private fun pump() {
        val (g, c, bytes) = synchronized(lock) {
            if (writing && SystemClock.uptimeMillis() - writeStartedAt > 600) writing = false
            val next = pending
            if (writing || next == null || gatt == null || writer == null) return
            pending = null
            writing = true; writeStartedAt = SystemClock.uptimeMillis()
            Triple(gatt!!, writer!!, next)
        }
        c.value = bytes
        c.writeType = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (!g.writeCharacteristic(c)) synchronized(lock) { writing = false }
    }

    fun close() {
        val g = synchronized(lock) { val old = gatt; gatt = null; writer = null; resolved = null; pending = null; writing = false; old }
        runCatching { g?.disconnect(); g?.close() }
        summarise("Not connected")
    }

    private fun summarise(text: String) { summary = text; main.post { onChange?.invoke() } }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                summarise("Connected, reading services…")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                synchronized(lock) { if (gatt === g) { gatt = null; writer = null; resolved = null; writing = false } }
                runCatching { g.close() }
                summarise("Disconnected")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val writable = LinkedHashMap<UUID, BluetoothGattCharacteristic>()
            for (service in g.services) for (c in service.characteristics) {
                val p = c.properties
                if (p and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) writable[c.uuid] = c
            }
            val chosen = LedModes.protocol(prefs).let { saved -> if (saved == LedProtocol.AUTO) LedModes.detect(writable.keys) else saved }
            val characteristic = chosen?.let { LedModes.preferredCharacteristic(it, writable.keys) }?.let { writable[it] }
            if (chosen == null || characteristic == null) {
                summarise("Connected, but no known LED controller found (${writable.size} writable characteristics)")
                return
            }
            synchronized(lock) { writer = characteristic; resolved = chosen }
            summarise("Ready · ${chosen.label}")
            queuedRgb?.let { rgb -> queuedRgb = null; setColor(rgb[0], rgb[1], rgb[2]) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            synchronized(lock) { writing = false }
            pump()
        }
    }
}
