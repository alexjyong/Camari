package dev.alexjyong.camari.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Monitors network status and provides connectivity information.
 *
 * Supports three modes:
 * - WIFI: phone is connected to a WiFi access point as a client
 * - HOTSPOT: phone is the access point (or USB/Bluetooth tethering); a PC on the same
 *            local network can reach the HTTP server
 * - NONE: cellular only or no network — OBS cannot connect
 *
 * Hotspot detection does NOT use hidden Android APIs (WifiManager.isWifiApEnabled was
 * restricted in Android 9+). Instead it scans NetworkInterface to find any UP,
 * non-loopback, non-cellular interface with an IPv4 address, which indicates an active
 * hotspot, USB tethering, or Bluetooth tethering session.
 */
class NetworkStatus(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val TAG = "NetworkStatus"

        /**
         * Returns true if the interface name belongs to a cellular/mobile-data interface
         * that should never be treated as a local network for streaming.
         *
         * Extracted as a companion function so it can be unit-tested without Android stubs.
         *
         * Prefixes covered:
         * - rmnet*    Qualcomm cellular (AOSP, most Android devices)
         * - ccmni*    MediaTek cellular
         * - pdp*      Legacy cellular
         * - dummy*    Kernel dummy interface
         * - sit*      IPv6-in-IPv4 tunnel
         * - tun*      VPN tunnel
         * - ppp*      PPP (legacy cellular / VPN)
         */
        internal fun isCellularInterfaceName(name: String): Boolean {
            val n = name.lowercase()
            return n == "lo" ||
                   n.startsWith("rmnet") ||
                   n.startsWith("ccmni") ||
                   n.startsWith("pdp") ||
                   n.startsWith("dummy") ||
                   n.startsWith("sit") ||
                   n.startsWith("tun") ||
                   n.startsWith("ppp")
        }
    }

    init {
        registerNetworkCallback()
    }

    // -------------------------------------------------------------------------
    // Connection type
    // -------------------------------------------------------------------------

    fun getConnectionType(): ConnectionType {
        // Check WiFi client mode first — most common happy path.
        if (isWifiClient()) return ConnectionType.WIFI

        // Scan network interfaces for any active hotspot/tethering interface.
        if (isHotspotActive()) return ConnectionType.HOTSPOT

        // IP-address safety net: if the phone has a routable IPv4 address even though
        // neither check above fired (e.g. interface name not in our known sets, or
        // Android restricted the API), the phone IS reachable on a local network.
        if (getIpAddress() != null) return ConnectionType.HOTSPOT

        return ConnectionType.NONE
    }

    /** True when the phone is connected to WiFi as a client OR is running a hotspot. */
    fun isConnected(): Boolean = getConnectionType() != ConnectionType.NONE

    // -------------------------------------------------------------------------
    // Network info
    // -------------------------------------------------------------------------

    /**
     * SSID of the WiFi network the phone is connected to, or null if on hotspot / no WiFi.
     * Returns null in hotspot mode — use getConnectionType() to distinguish.
     */
    fun getNetworkSsid(): String? {
        if (!isWifiClient()) return null
        val ssid = getWifiInfo()?.ssid ?: return null
        return ssid.removeSurrounding("\"").takeIf { it != "<unknown ssid>" }
    }

    /**
     * The phone's IP address on the local network.
     * Uses NetworkInterface enumeration — works for WiFi client, hotspot, and USB tethering.
     */
    fun getIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<Inet4Address>()
                ?.filterNot { it.isLoopbackAddress }
                ?.map { it.hostAddress }
                ?.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "getIpAddress failed: ${e.message}")
            null
        }
    }

    fun getSignalStrength(): Int = getWifiInfo()?.rssi ?: -100

    fun getConnectionQuality(): ConnectionQuality {
        val rssi = getSignalStrength()
        return when {
            rssi >= -50 -> ConnectionQuality.EXCELLENT
            rssi >= -65 -> ConnectionQuality.GOOD
            rssi >= -75 -> ConnectionQuality.FAIR
            else        -> ConnectionQuality.POOR
        }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    /**
     * Detects active hotspot or tethering by scanning network interfaces.
     *
     * Only called when isWifiClient() is false, so the WiFi client interface (wlan0 in
     * client mode) won't have an active IP and won't cause false positives.
     *
     * Covers WiFi hotspot (ap0, softap0, wlan0 in AP mode, swlan0 on Samsung),
     * USB tethering (rndis0, usb0), and Bluetooth tethering (bt-pan).
     */
    private fun isHotspotActive(): Boolean {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { iface ->
                    iface.isUp &&
                    !iface.isLoopback &&
                    !isCellularInterfaceName(iface.name) &&
                    iface.inetAddresses.asSequence()
                        .filterIsInstance<Inet4Address>()
                        .any { !it.isLoopbackAddress }
                }
                ?.any() ?: false
        } catch (e: Exception) {
            Log.w(TAG, "isHotspotActive scan failed: ${e.message}")
            false
        }
    }

    /**
     * Returns the current WifiInfo.
     * API 31+: retrieved from NetworkCapabilities.transportInfo (non-deprecated path).
     * API 29-30: retrieved from WifiManager.connectionInfo (deprecated in 31, still works).
     */
    private fun getWifiInfo(): WifiInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val network = connectivityManager.activeNetwork ?: return null
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return null
            caps.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        }
    }

    private fun isWifiClient(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "WiFi available")
            }
            override fun onLost(network: Network) {
                Log.w(TAG, "WiFi lost")
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    fun unregister() {
        try {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister network callback", e)
        }
    }
}

enum class ConnectionType { WIFI, HOTSPOT, NONE }

enum class ConnectionQuality {
    EXCELLENT,  // >= -50 dBm
    GOOD,       // >= -65 dBm
    FAIR,       // >= -75 dBm
    POOR        // < -75 dBm
}
