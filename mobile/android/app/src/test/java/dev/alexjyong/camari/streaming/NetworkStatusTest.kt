package dev.alexjyong.camari.streaming

import dev.alexjyong.camari.network.NetworkStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for NetworkStatus interface classification logic.
 *
 * Tests the pure companion function `isCellularInterfaceName()` — no Android
 * framework stubs needed since it operates only on strings.
 */
class NetworkStatusTest {

    // -------------------------------------------------------------------------
    // isCellularInterfaceName — interfaces that MUST be excluded (cellular / tunnel)
    // -------------------------------------------------------------------------

    @Test
    fun `rmnet0 is cellular`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("rmnet0"))
    }

    @Test
    fun `rmnet_data0 is cellular`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("rmnet_data0"))
    }

    @Test
    fun `rmnet1 is cellular`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("rmnet1"))
    }

    @Test
    fun `ccmni0 is cellular (MediaTek)`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("ccmni0"))
    }

    @Test
    fun `ccmni1 is cellular (MediaTek)`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("ccmni1"))
    }

    @Test
    fun `pdp0 is cellular (legacy)`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("pdp0"))
    }

    @Test
    fun `pdp_ip0 is cellular (legacy)`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("pdp_ip0"))
    }

    @Test
    fun `dummy0 is dummy interface`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("dummy0"))
    }

    @Test
    fun `sit0 is tunnel interface`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("sit0"))
    }

    @Test
    fun `tun0 is VPN tunnel`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("tun0"))
    }

    @Test
    fun `ppp0 is PPP interface`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("ppp0"))
    }

    @Test
    fun `lo is loopback`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("lo"))
    }

    // -------------------------------------------------------------------------
    // isCellularInterfaceName — interfaces that MUST NOT be excluded (hotspot / LAN)
    // -------------------------------------------------------------------------

    @Test
    fun `wlan0 is NOT cellular (WiFi or hotspot AP interface)`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("wlan0"))
    }

    @Test
    fun `wlan1 is NOT cellular`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("wlan1"))
    }

    @Test
    fun `ap0 is NOT cellular (common hotspot interface)`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("ap0"))
    }

    @Test
    fun `softap0 is NOT cellular`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("softap0"))
    }

    @Test
    fun `swlan0 is NOT cellular (Samsung hotspot)`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("swlan0"))
    }

    @Test
    fun `rndis0 is NOT cellular (USB tethering)`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("rndis0"))
    }

    @Test
    fun `usb0 is NOT cellular (USB tethering on some devices)`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("usb0"))
    }

    @Test
    fun `bt-pan is NOT cellular (Bluetooth tethering)`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("bt-pan"))
    }

    @Test
    fun `bridge0 is NOT cellular`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("bridge0"))
    }

    @Test
    fun `eth0 is NOT cellular (Ethernet)`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("eth0"))
    }

    // -------------------------------------------------------------------------
    // Case insensitivity
    // -------------------------------------------------------------------------

    @Test
    fun `RMNET0 uppercase is treated as cellular`() {
        assertTrue(NetworkStatus.isCellularInterfaceName("RMNET0"))
    }

    @Test
    fun `Wlan0 mixed case is NOT cellular`() {
        assertFalse(NetworkStatus.isCellularInterfaceName("Wlan0"))
    }
}
