package com.camari.streaming

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.camari.network.NetworkStatus
import com.camari.network.BatteryMonitor

/**
 * Capacitor plugin for camera streaming to OBS via HTTP server.
 */
@CapacitorPlugin(
    name = "CameraStream",
    permissions = [
        Permission(
            strings = [Manifest.permission.CAMERA],
            alias = "camera"
        )
    ]
)
class CameraStreamPlugin : Plugin() {

    private var httpServer: HttpServer? = null
    private var cameraManager: CameraManager? = null
    private var networkStatus: NetworkStatus? = null
    private var batteryMonitor: BatteryMonitor? = null
    
    private var isStreaming = false
    private var currentCameraType = "front"
    
    companion object {
        private const val DEFAULT_PORT = 8080
        private const val TAG = "CameraStreamPlugin"
    }

    override fun load() {
        super.load()
        try {
            networkStatus = NetworkStatus(activity)
            batteryMonitor = BatteryMonitor(activity)
            android.util.Log.i(TAG, "Plugin loaded successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error loading plugin: ${e.message}")
        }
    }

    @PluginMethod
    fun startStreaming(call: PluginCall) {
        android.util.Log.i(TAG, "startStreaming called")
        
        if (!hasCameraPermission()) {
            android.util.Log.w(TAG, "Camera permission not granted, requesting...")
            requestPermissionForAlias("camera", call, "cameraPermissionCallback")
            return
        }

        try {
            // Clean up any existing resources first
            stopInternal()
            
            // Initialize camera manager
            cameraManager = CameraManager(activity)
            cameraManager?.setCameraType(currentCameraType)
            cameraManager?.startCamera()
            
            // Initialize and start HTTP server, trying multiple ports if needed
            val portsToTry = listOf(DEFAULT_PORT, 8081, 8082, 8083)
            var serverStarted = false
            var actualPort = DEFAULT_PORT
            
            for (port in portsToTry) {
                try {
                    httpServer = HttpServer(port, cameraManager!!)
                    httpServer?.start()
                    actualPort = port
                    serverStarted = true
                    android.util.Log.i(TAG, "Server started on port $port")
                    break
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to start on port $port: ${e.message}")
                    httpServer = null
                    // Try next port
                }
            }
            
            if (!serverStarted) {
                throw RuntimeException("Could not start server on any port")
            }
            
            // Get network info
            val ipAddress = networkStatus?.getIpAddress() ?: "192.168.1.100"
            val ssid = networkStatus?.getNetworkSsid() ?: "WiFi"
            val streamUrl = "http://$ipAddress:$actualPort/stream"
            
            android.util.Log.i(TAG, "Streaming started: $streamUrl")
            
            // Build result
            val result = JSObject()
            result.put("streamUrl", streamUrl)
            result.put("ipAddress", ipAddress)
            result.put("port", actualPort)
            result.put("networkSsid", ssid)
            result.put("cameraType", currentCameraType)
            
            isStreaming = true
            call.resolve(result)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error starting streaming: ${e.message}", e)
            call.reject("Failed to start streaming: ${e.message}")
        }
    }

    @PluginMethod
    fun stopStreaming(call: PluginCall) {
        android.util.Log.i(TAG, "stopStreaming called")
        
        try {
            stopInternal()
            call.resolve()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping streaming: ${e.message}", e)
            call.reject("Failed to stop streaming: ${e.message}")
        }
    }
    
    /**
     * Internal stop method that cleans up resources.
     */
    private fun stopInternal() {
        try {
            httpServer?.stop()
            httpServer = null
            
            cameraManager?.stopCamera()
            cameraManager = null
            
            isStreaming = false
            android.util.Log.i(TAG, "Resources cleaned up")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in stopInternal: ${e.message}", e)
        }
    }

    @PluginMethod
    fun switchCamera(call: PluginCall) {
        android.util.Log.i(TAG, "switchCamera called")
        
        if (!isStreaming) {
            android.util.Log.w(TAG, "Not streaming, cannot switch camera")
            call.reject("Not currently streaming")
            return
        }

        try {
            currentCameraType = if (currentCameraType == "front") "back" else "front"
            cameraManager?.setCameraType(currentCameraType)
            
            android.util.Log.i(TAG, "Switched to $currentCameraType camera")
            
            val result = JSObject()
            result.put("cameraType", currentCameraType)
            result.put("success", true)
            
            call.resolve(result)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error switching camera: ${e.message}", e)
            val result = JSObject()
            result.put("cameraType", currentCameraType)
            result.put("success", false)
            call.resolve(result)
        }
    }

    @PluginMethod
    fun getStatus(call: PluginCall) {
        try {
            val result = JSObject()
            
            val status = when {
                !isStreaming -> "idle"
                networkStatus?.isConnected() == false -> "reconnecting"
                else -> "streaming"
            }
            
            result.put("status", status)
            result.put("cameraType", if (isStreaming) currentCameraType else null)
            result.put("batteryLevel", batteryMonitor?.getBatteryLevel() ?: 0)
            result.put("isCharging", batteryMonitor?.isCharging() ?: false)
            result.put("isLowBattery", batteryMonitor?.isLowBattery() ?: false)
            result.put("networkSsid", networkStatus?.getNetworkSsid())
            result.put("ipAddress", networkStatus?.getIpAddress())
            
            call.resolve(result)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting status: ${e.message}", e)
            call.reject("Failed to get status: ${e.message}")
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    @PermissionCallback
    private fun cameraPermissionCallback(call: PluginCall) {
        android.util.Log.i(TAG, "Permission callback called")
        
        if (hasCameraPermission()) {
            android.util.Log.i(TAG, "Permission granted, starting streaming")
            startStreaming(call)
        } else {
            android.util.Log.w(TAG, "Permission denied")
            call.reject("Camera permission denied")
        }
    }
    
    /**
     * Clean up resources when the plugin is destroyed.
     */
    fun cleanup() {
        android.util.Log.i(TAG, "Cleaning up resources")
        stopInternal()
        batteryMonitor?.unregister()
        networkStatus?.unregister()
    }
}
