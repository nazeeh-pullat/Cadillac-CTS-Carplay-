package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.network.WirelessHotspotBackend

/** Lollipop LAN and tethered AP modes use the system's shared mDNS responder. */
internal fun useInterfaceMdnsForWireless(sdk: Int, backend: WirelessHotspotBackend): Boolean =
    sdk >= 23 || backend !in setOf(WirelessHotspotBackend.EXISTING_WIFI, WirelessHotspotBackend.MANUAL_HOTSPOT)
