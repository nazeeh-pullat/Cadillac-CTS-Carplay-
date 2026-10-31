package com.shilapi.xcertplay.compat

import android.content.Context
import android.os.Build

/** Class-based service lookup was added in API 23; the engine also runs on API 22. */
fun <T> Context.getSystemServiceCompat(type: Class<T>): T? {
    if (Build.VERSION.SDK_INT >= 23) return getSystemService(type)
    val serviceName = when (type.name) {
        "android.hardware.usb.UsbManager" -> Context.USB_SERVICE
        "android.bluetooth.BluetoothManager" -> Context.BLUETOOTH_SERVICE
        "android.location.LocationManager" -> Context.LOCATION_SERVICE
        "android.net.wifi.WifiManager" -> Context.WIFI_SERVICE
        "android.net.wifi.p2p.WifiP2pManager" -> Context.WIFI_P2P_SERVICE
        "android.net.ConnectivityManager" -> Context.CONNECTIVITY_SERVICE
        "android.media.AudioManager" -> Context.AUDIO_SERVICE
        "android.hardware.display.DisplayManager" -> Context.DISPLAY_SERVICE
        "android.telecom.TelecomManager" -> Context.TELECOM_SERVICE
        "android.app.AppOpsManager" -> Context.APP_OPS_SERVICE
        else -> return null
    }
    return getSystemService(serviceName)?.let { type.cast(it) }
}
