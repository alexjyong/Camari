package com.camari.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/**
 * Monitors WiFi network status and provides connectivity information.
 */
class NetworkStatus(private val context: Context) {
    
    private val connectivityManager: ConnectivityManager
    private val wifiManager: WifiManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    private var isConnectedState = false
    private var isReconnectingState = false
    private var reconnectTimeoutAt: Long? = null
    
    companion object {
        private const val TAG = "NetworkStatus"
        private const val RECONNECT_TIMEOUT_MS = 60_000L // 60 seconds
    }

    init {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        registerNetworkCallback()
    }

    /**
     * Check if device is connected to WiFi.
     */
    fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Get the current WiFi network SSID.
     */
    fun getNetworkSsid(): String? {
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid
        
        // Remove quotes if present
        return ssid.removeSurrounding("\"").takeIf { it != "<unknown ssid>" }
    }

    /**
     * Get the device's IP address on the local network.
     */
    fun getIpAddress(): String? {
        val wifiInfo = wifiManager.connectionInfo
        val ip = wifiInfo.ipAddress
        
        if (ip == 0) return null
        
        // Convert integer IP to string format
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xFF,
            (ip shr 8) and 0xFF,
            (ip shr 16) and 0xFF,
            (ip shr 24) and 0xFF
        )
    }

    /**
     * Get WiFi signal strength in dBm.
     */
    fun getSignalStrength(): Int {
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo.rssi
    }

    /**
     * Get connection quality based on signal strength.
     */
    fun getConnectionQuality(): ConnectionQuality {
        val rssi = getSignalStrength()
        return when {
            rssi >= -50 -> ConnectionQuality.EXCELLENT
            rssi >= -65 -> ConnectionQuality.GOOD
            rssi >= -75 -> ConnectionQuality.FAIR
            else -> ConnectionQuality.POOR
        }
    }

    /**
     * Check if currently attempting reconnection.
     */
    fun isReconnecting(): Boolean {
        if (!isReconnectingState) return false
        
        // Check if timeout has expired
        val timeout = reconnectTimeoutAt
        if (timeout != null && System.currentTimeMillis() > timeout) {
            isReconnectingState = false
            reconnectTimeoutAt = null
            return false
        }
        
        return true
    }

    /**
     * Start reconnection attempt with timeout.
     */
    fun startReconnection() {
        isReconnectingState = true
        reconnectTimeoutAt = System.currentTimeMillis() + RECONNECT_TIMEOUT_MS
        Log.i(TAG, "Reconnection started, timeout: ${RECONNECT_TIMEOUT_MS / 1000}s")
    }

    /**
     * Stop reconnection attempt.
     */
    fun stopReconnection() {
        isReconnectingState = false
        reconnectTimeoutAt = null
        Log.i(TAG, "Reconnection stopped")
    }

    /**
     * Get reconnection timeout timestamp.
     */
    fun getReconnectTimeoutAt(): Long? {
        return reconnectTimeoutAt
    }

    /**
     * Register for network state changes.
     */
    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "WiFi available")
                isConnectedState = true
                stopReconnection()
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "WiFi lost")
                isConnectedState = false
                startReconnection()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val wasConnected = isConnectedState
                isConnectedState = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                
                if (wasConnected && !isConnectedState) {
                    startReconnection()
                } else if (!wasConnected && isConnectedState) {
                    stopReconnection()
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Unregister network callback.
     */
    fun unregister() {
        try {
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister network callback", e)
        }
    }
}

/**
 * WiFi connection quality levels.
 */
enum class ConnectionQuality {
    EXCELLENT,  // >= -50 dBm
    GOOD,       // >= -65 dBm
    FAIR,       // >= -75 dBm
    POOR        // < -75 dBm
}
