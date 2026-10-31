package com.shihab.diplay.legacy

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Owns only the AP it starts; restores the tablet's Wi-Fi when stopped. */
@Suppress("DEPRECATION")
@SuppressLint("PrivateApi")
internal class TabletHotspot(context: Context) : Closeable {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val lock = Any()
    private val cancelled = AtomicBoolean(false)
    @Volatile private var owned = false
    private var restoreWifi = false
    private var previous: WifiConfiguration? = null

    fun isReady(): Boolean = owned && runCatching {
        wifi.javaClass.getMethod("isWifiApEnabled").invoke(wifi) == true
    }.getOrDefault(false)

    fun start(ssid: String, password: String) {
        check(Build.VERSION.SDK_INT <= 25) { "Automatic tablet hotspot requires Android 5–7.1 on this build" }
        require(ssid.toByteArray(Charsets.UTF_8).size in 1..32 && '\u0000' !in ssid)
        require(password.length in 8..63 && password.all { it.code in 32..126 })
        val config = configuration(ssid, password)
        try {
            synchronized(lock) {
                check(!cancelled.get()) { "Hotspot startup cancelled" }
                previous = wifi.javaClass.getMethod("getWifiApConfiguration").invoke(wifi) as? WifiConfiguration
                restoreWifi = wifi.isWifiEnabled
                owned = true
                if (restoreWifi) check(wifi.setWifiEnabled(false)) { "Could not leave the current Wi-Fi" }
            }
            restartLegacyHotspot(
                disable = { synchronized(lock) {
                    check(!cancelled.get()) { "Hotspot startup cancelled" }
                    if (apState() != 11) check(setAp(null, false)) { "Could not turn off the existing hotspot" }
                } },
                state = ::apState,
                enable = { synchronized(lock) {
                    check(!cancelled.get()) { "Hotspot startup cancelled" }
                    check(setAp(config, true)) { "The tablet firmware refused to start its hotspot" }
                } },
                cancelled = cancelled::get,
            )
        } catch (error: Exception) { close(); throw error }
    }

    private fun apState() = wifi.javaClass.getMethod("getWifiApState").invoke(wifi) as Int

    private fun setAp(config: WifiConfiguration?, enabled: Boolean): Boolean =
        wifi.javaClass.getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
            .invoke(wifi, config, enabled) == true

    override fun close() = synchronized(lock) {
        cancelled.set(true)
        if (!owned) return
        owned = false
        runCatching { wifi.javaClass.getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
            .invoke(wifi, null, false) }
        previous?.let { config -> runCatching {
            wifi.javaClass.getMethod("setWifiApConfiguration", WifiConfiguration::class.java).invoke(wifi, config)
        } }
        if (restoreWifi) runCatching { wifi.setWifiEnabled(true) }
    }

    companion object {
        internal fun configuration(ssid: String, password: String) = WifiConfiguration().apply {
            SSID = ssid
            preSharedKey = password
            allowedKeyManagement.clear()
            // Android 5's hidden WPA2_PSK AP constant is 4 (station WPA_PSK is 1).
            allowedKeyManagement.set(4)
            allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
            allowedProtocols.set(WifiConfiguration.Protocol.RSN)
            allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
            // MTK Lollipop has no apBand/apChannel fields; the firmware selects 2.4 GHz.
            runCatching { javaClass.getField("apBand").setInt(this, 0) }
            runCatching { javaClass.getField("apChannel").setInt(this, 6) }
        }
    }
}

/** Wait for Android's asynchronous AP shutdown before requesting a fresh connection. */
internal fun restartLegacyHotspot(
    disable: () -> Unit,
    state: () -> Int,
    enable: () -> Unit,
    cancelled: () -> Boolean,
    pause: () -> Unit = { Thread.sleep(100) },
    attempts: Int = 100,
) {
    check(!cancelled()) { "Hotspot startup cancelled" }
    disable()
    repeat(attempts) {
        check(!cancelled()) { "Hotspot startup cancelled" }
        if (state() == 11) { enable(); return }
        pause()
    }
    error("The existing hotspot did not turn off in time")
}
