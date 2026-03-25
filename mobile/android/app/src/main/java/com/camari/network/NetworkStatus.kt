package com.camari.network

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
 * - HOTSPOT: phone is the access point; a PC connecting to it can reach the HTTP server
 * - NONE: cellular only or no network ;  OBS cannot connect
 */
class NetworkStatus(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val TAG = "NetworkStatus"
        private const val RECONNECT_TIMEOUT_MS = 60_000L
    }

    init {
        registerNetworkCallback()
    }

    // -------------------------------------------------------------------------
    // Connection type
    // -------------------------------------------------------------------------

    fun getConnectionType(): ConnectionType {
        if (isWifiClient()) return ConnectionType.WIFI
        if (isHotspotEnabled()) return ConnectionType.HOTSPOT
        return ConnectionType.NONE
    }

    /** True when the phone is connected to WiFi as a client OR is running a hotspot. */
    fun isConnected(): Boolean = getConnectionType() != ConnectionType.NONE

    // -------------------------------------------------------------------------
    // Network info
    // -------------------------------------------------------------------------

    /**
     * SSID of the WiFi network the phone is connected to, or null if on hotspot / no WiFi.
     * Returns null in hotspot mode ;  use getConnectionType() to distinguish.
     */
    fun getNetworkSsid(): String? {
        if (!isWifiClient()) return null
        val ssid = getWifiInfo()?.ssid ?: return null
        return ssid.removeSurrounding("\"").takeIf { it != "<unknown ssid>" }
    }

    /**
     * The phone's IP address on the local network.
     * Uses NetworkInterface enumeration ;  works for WiFi client, hotspot, and USB tethering.
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
     * `WifiManager.isWifiApEnabled()` is a hidden API so we call it via reflection.
     * Returns false if the method is unavailable (future Android versions).
     */
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

    private fun isHotspotEnabled(): Boolean {
        return try {
            wifiManager.javaClass.getMethod("isWifiApEnabled").invoke(wifiManager) as Boolean
        } catch (e: Exception) {
            Log.w(TAG, "isWifiApEnabled unavailable: ${e.message}")
            false
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
