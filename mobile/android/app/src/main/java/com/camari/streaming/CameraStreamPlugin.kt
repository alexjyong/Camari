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
    }

    override fun load() {
        super.load()
        networkStatus = NetworkStatus(activity)
        batteryMonitor = BatteryMonitor(activity)
    }

    @PluginMethod
    fun startStreaming(call: PluginCall) {
        if (!hasCameraPermission()) {
            requestPermissionForAlias("camera", call, "cameraPermissionCallback")
            return
        }

        try {
            cameraManager = CameraManager(activity)
            cameraManager?.setCameraType(currentCameraType)
            cameraManager?.startCamera()
            
            httpServer = HttpServer(DEFAULT_PORT, cameraManager!!)
            httpServer?.start()
            
            val ipAddress = networkStatus?.getIpAddress() ?: "192.168.1.100"
            val ssid = networkStatus?.getNetworkSsid() ?: "WiFi"
            val streamUrl = "http://$ipAddress:$DEFAULT_PORT/stream"
            
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

    @PluginMethod
    fun stopStreaming(call: PluginCall) {
        try {
            httpServer?.stop()
            httpServer = null
            
            cameraManager?.stopCamera()
            cameraManager = null
            
            isStreaming = false
            call.resolve()
            
        } catch (e: Exception) {
            call.reject("Failed to stop streaming: ${e.message}")
        }
    }

    @PluginMethod
    fun switchCamera(call: PluginCall) {
        if (!isStreaming) {
            call.reject("Not currently streaming")
            return
        }

        try {
            currentCameraType = if (currentCameraType == "front") "back" else "front"
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

    @PermissionCallback
    private fun cameraPermissionCallback(call: PluginCall) {
        if (hasCameraPermission()) {
            startStreaming(call)
        } else {
            call.reject("Camera permission denied")
        }
    }
}
