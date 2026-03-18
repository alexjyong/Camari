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
import com.camari.network.NetworkStatus
import com.camari.network.BatteryMonitor

/**
 * Capacitor plugin for camera streaming to OBS via HTTP server.
 * 
 * Methods:
 * - startStreaming(): Start camera and HTTP server, returns stream URL
 * - stopStreaming(): Stop camera and HTTP server
 * - switchCamera(): Toggle between front and rear cameras
 * - getStatus(): Get current streaming status
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
    private var currentCameraType = "front" // "front" or "back"
    
    companion object {
        private const val DEFAULT_PORT = 8080
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun load() {
        super.load()
        networkStatus = NetworkStatus(activity)
        batteryMonitor = BatteryMonitor(activity)
    }

    /**
     * Start streaming from the camera.
     * Returns the stream URL for OBS browser source.
     */
    @PluginMethod
    fun startStreaming(call: PluginCall) {
        // Check camera permission
        if (!hasCameraPermission()) {
            requestPermissionForAlias("camera", call, CAMERA_PERMISSION_REQUEST_CODE)
            return
        }

        try {
            // Initialize camera manager
            cameraManager = CameraManager(activity)
            cameraManager?.setCameraType(currentCameraType)
            
            // Initialize and start HTTP server
            httpServer = HttpServer(DEFAULT_PORT, cameraManager!!)
            httpServer?.start()
            
            // Get network info
            val ipAddress = networkStatus?.getIpAddress() ?: "0.0.0.0"
            val ssid = networkStatus?.getNetworkSsid() ?: "Unknown"
            
            // Build stream URL
            val streamUrl = "http://$ipAddress:$DEFAULT_PORT/stream"
            
            // Return result
            val result = JSObject()
            result.put("streamUrl", streamUrl)
            result.put("ipAddress", ipAddress)
            result.put("port", DEFAULT_PORT)
            result.put("networkSsid", ssid)
            result.put("cameraType", currentCameraType)
            
            isStreaming = true
            call.resolve(result)
            
        } catch (e: Exception) {
            call.reject("Failed to start streaming: ${e.message}")
        }
    }

    /**
     * Stop streaming and release resources.
     */
    @PluginMethod
    fun stopStreaming(call: PluginCall) {
        try {
            // Stop HTTP server
            httpServer?.stop()
            httpServer = null
            
            // Release camera
            cameraManager?.release()
            cameraManager = null
            
            isStreaming = false
            
            call.resolve()
            
        } catch (e: Exception) {
            call.reject("Failed to stop streaming: ${e.message}")
        }
    }

    /**
     * Switch between front and rear cameras.
     */
    @PluginMethod
    fun switchCamera(call: PluginCall) {
        if (!isStreaming) {
            call.reject("Not currently streaming")
            return
        }

        try {
            // Toggle camera type
            currentCameraType = if (currentCameraType == "front") "back" else "front"
            
            // Switch camera in camera manager
            cameraManager?.setCameraType(currentCameraType)
            
            val result = JSObject()
            result.put("cameraType", currentCameraType)
            result.put("success", true)
            
            call.resolve(result)
            
        } catch (e: Exception) {
            val result = JSObject()
            result.put("cameraType", currentCameraType)
            result.put("success", false)
            call.resolve(result)
        }
    }

    /**
     * Get current streaming status.
     */
    @PluginMethod
    fun getStatus(call: PluginCall) {
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
    }

    private fun hasCameraPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, can proceed with streaming
            } else {
                // Permission denied
                notifyListeners("permissionDenied", JSObject().apply {
                    put("message", "Camera permission was denied")
                })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup resources
        httpServer?.stop()
        cameraManager?.release()
        batteryMonitor?.unregister()
    }
}
