package dev.alexjyong.camari.streaming

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import dev.alexjyong.camari.network.NetworkStatus
import dev.alexjyong.camari.network.BatteryMonitor

/**
 * Capacitor plugin for camera streaming to OBS via HTTP server.
 *
 * Delegates camera and server lifecycle to StreamingForegroundService so that
 * the stream survives the app being backgrounded.
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

    private var streamingService: StreamingForegroundService? = null
    private var serviceBound = false
    private var networkStatus: NetworkStatus? = null
    private var batteryMonitor: BatteryMonitor? = null
    private var currentCameraType = "front"

    companion object {
        private const val TAG = "CameraStreamPlugin"
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: android.os.IBinder) {
            streamingService = (binder as StreamingForegroundService.LocalBinder).getService()
            serviceBound = true
            android.util.Log.i(TAG, "Bound to StreamingForegroundService")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            streamingService = null
            serviceBound = false
            android.util.Log.w(TAG, "StreamingForegroundService disconnected unexpectedly")
        }
    }

    override fun load() {
        super.load()
        try {
            networkStatus = NetworkStatus(activity)
            batteryMonitor = BatteryMonitor(activity)

            // Bind to the service now so it's ready by the time the user taps Start.
            // BIND_AUTO_CREATE creates the service process without starting it as foreground yet.
            val intent = Intent(activity, StreamingForegroundService::class.java)
            activity.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

            // Android 13+ requires POST_NOTIFICATIONS at runtime for the foreground service
            // notification to be visible. Request it early so it's granted before streaming starts.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        /* requestCode = */ 1002
                    )
                }
            }

            android.util.Log.i(TAG, "Plugin loaded, binding to StreamingForegroundService")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error loading plugin: ${e.message}")
        }
    }

    @PluginMethod
    fun startStreaming(call: PluginCall) {
        android.util.Log.i(TAG, "startStreaming called")

        if (!hasCameraPermission()) {
            android.util.Log.w(TAG, "Camera permission not granted, requesting…")
            requestPermissionForAlias("camera", call, "cameraPermissionCallback")
            return
        }

        val service = streamingService
        if (service == null) {
            call.reject("Streaming service not ready, please try again")
            return
        }

        try {
            // startForegroundService ensures the service survives if we later unbind.
            // The service calls startForeground() in onStartCommand() within the 5-second window.
            val intent = Intent(activity, StreamingForegroundService::class.java)
            activity.startForegroundService(intent)

            val resolution = call.getString("resolution") ?: "720p"
            val ipAddress = networkStatus?.getIpAddress() ?: "192.168.1.100"
            val port = service.startStreaming(currentCameraType, ipAddress, resolution)
            if (port == -1) {
                call.reject("Could not start server on any available port")
                return
            }
            val ssid = networkStatus?.getNetworkSsid() ?: "WiFi"
            val streamUrl = "http://$ipAddress:$port/"
            val actualResolution = service.getActualResolution() ?: "1280x720"

            android.util.Log.i(TAG, "Streaming started: $streamUrl at $actualResolution")

            val result = JSObject()
            result.put("streamUrl", streamUrl)
            result.put("ipAddress", ipAddress)
            result.put("port", port)
            result.put("networkSsid", ssid)
            result.put("cameraType", currentCameraType)
            result.put("resolution", actualResolution)
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
            streamingService?.stopStreaming()
            call.resolve()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping streaming: ${e.message}", e)
            call.reject("Failed to stop streaming: ${e.message}")
        }
    }

    @PluginMethod
    fun switchCamera(call: PluginCall) {
        android.util.Log.i(TAG, "switchCamera called")

        val service = streamingService
        if (service == null || !service.isCameraOpen()) {
            call.reject("Not currently streaming")
            return
        }

        try {
            currentCameraType = if (currentCameraType == "front") "back" else "front"
            service.switchCamera(currentCameraType)

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
    fun setOrientation(call: PluginCall) {
        val degrees = call.getInt("degrees") ?: 0
        streamingService?.setOrientation(degrees)
        android.util.Log.i(TAG, "Orientation set to ${degrees}°")
        call.resolve()
    }

    @PluginMethod
    fun getStatus(call: PluginCall) {
        try {
            val service = streamingService
            val isStreaming = service?.isCameraOpen() == true

            val result = JSObject()
            val connType = networkStatus?.getConnectionType()?.name?.lowercase() ?: "none"
            result.put("status", if (isStreaming) "streaming" else "idle")
            result.put("cameraType", if (isStreaming) service?.getCameraType() else null)
            result.put("batteryLevel", batteryMonitor?.getBatteryLevel() ?: 0)
            result.put("isCharging", batteryMonitor?.isCharging() ?: false)
            result.put("isLowBattery", batteryMonitor?.isLowBattery() ?: false)
            result.put("connectionType", connType)
            result.put("networkSsid", networkStatus?.getNetworkSsid())
            result.put("ipAddress", networkStatus?.getIpAddress())
            result.put("obsConnected", service?.isObsConnected() == true)
            result.put("resolution", if (isStreaming) service?.getActualResolution() else null)
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
            startStreaming(call)
        } else {
            // shouldShowRequestPermissionRationale returns false after the user has ticked
            // "Don't ask again", meaning the system won't show the dialog anymore.
            val permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA
            )
            if (permanentlyDenied) {
                call.reject("CAMERA_PERMISSION_PERMANENTLY_DENIED")
            } else {
                call.reject("Camera permission denied")
            }
        }
    }

    @PluginMethod
    fun openAppSettings(call: PluginCall) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        call.resolve()
    }

    /**
     * Called when the plugin is destroyed (activity finishing).
     * Stops the service fully and unbinds.
     */
    fun cleanup() {
        android.util.Log.i(TAG, "Cleaning up plugin")
        streamingService?.stopStreaming()
        if (serviceBound) {
            try { activity.unbindService(serviceConnection) } catch (_: Exception) {}
            serviceBound = false
        }
        batteryMonitor?.unregister()
        networkStatus?.unregister()
    }
}
