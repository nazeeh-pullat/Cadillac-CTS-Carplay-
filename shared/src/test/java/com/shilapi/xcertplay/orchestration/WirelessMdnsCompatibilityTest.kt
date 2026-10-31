package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.network.WirelessHotspotBackend
import org.junit.Assert.*
import org.junit.Test

class WirelessMdnsCompatibilityTest {
    @Test fun lollipopTabletHotspotUsesSharedSystemResponder() {
        assertFalse(useInterfaceMdnsForWireless(22, WirelessHotspotBackend.MANUAL_HOTSPOT))
        assertTrue(useInterfaceMdnsForWireless(24, WirelessHotspotBackend.MANUAL_HOTSPOT))
    }
    @Test fun lollipopExistingLanUsesSharedSystemResponder() {
        assertFalse(useInterfaceMdnsForWireless(22, WirelessHotspotBackend.EXISTING_WIFI))
    }
    @Test fun newerExistingLanKeepsInterfaceDiscovery() {
        assertTrue(useInterfaceMdnsForWireless(24, WirelessHotspotBackend.EXISTING_WIFI))
    }
    @Test fun otherLollipopBackendsKeepInterfaceDiscovery() {
        assertTrue(useInterfaceMdnsForWireless(22, WirelessHotspotBackend.WIFI_P2P))
    }
}
