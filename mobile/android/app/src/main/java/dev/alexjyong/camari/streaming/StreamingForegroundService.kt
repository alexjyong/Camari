package dev.alexjyong.camari.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.OrientationEventListener
import androidx.core.app.NotificationCompat
import dev.alexjyong.camari.MainActivity
import dev.alexjyong.camari.R

/**
 * Foreground service that owns the HttpServer and CameraManager lifecycle.
 *
 * Running as a foreground service keeps the streaming process alive when the app
 * is minimized, Android won't kill foreground services unless under extreme memory
 * pressure, and they survive the app being swiped away from recents on most devices.
 *
 * Lifecycle:
 * - CameraStreamPlugin binds on load() and unbinds on cleanup()
 * - startStreaming() moves the service to foreground and starts camera + HTTP server
 * - stopStreaming() stops camera + server, removes the foreground notification, stops the service
 */
class StreamingForegroundService : Service() {

    inner class LocalBinder : Binder() {
        fun getService() = this@StreamingForegroundService
    }

    private val binder = LocalBinder()
    private var httpServer: HttpServer? = null
    private var cameraManager: CameraManager? = null
    private var activePort: Int = 0
    private var orientationListener: OrientationEventListener? = null

    companion object {
        private const val TAG = "StreamingService"
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "camari_streaming"
        private const val CHANNEL_NAME = "Camari Streaming"
        val PORTS_TO_TRY = listOf(8080, 8081, 8082, 8083)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    /**
     * Called by startForegroundService(). Must call startForeground() within 5 seconds.
     * If startStreaming() already completed on the plugin thread before this runs, activePort
     * is set and we use the real text. Otherwise we show "Starting…" which startStreaming()
     * will immediately overwrite via another startForeground() call.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = if (activePort > 0) "Streaming on port $activePort, open app to stop"
                   else "Starting…"
        startForeground(NOTIFICATION_ID, buildNotification(text))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopStreamingInternal()
        Log.i(TAG, "Service destroyed")
    }

    // -------------------------------------------------------------------------
    // Public API: called by CameraStreamPlugin
    // -------------------------------------------------------------------------

    /**
     * Starts camera capture and HTTP server. Updates the foreground notification with the port.
     * @return port the server is listening on, or -1 on failure
     */
    fun startStreaming(cameraType: String, ipAddress: String, resolution: String = "720p"): Int {
        stopStreamingInternal()

        val cm = CameraManager(this)
        cm.setCameraType(cameraType)
        cm.setResolution(CameraManager.presetToSize(resolution))
        cm.startCamera()
        cameraManager = cm

        var port = -1
        for (p in PORTS_TO_TRY) {
            try {
                val server = HttpServer(p, cm)
                server.start()
                httpServer = server
                port = p
                Log.i(TAG, "HTTP server started on port $p")
                break
            } catch (e: Exception) {
                Log.w(TAG, "Port $p unavailable: ${e.message}")
            }
        }

        if (port == -1) {
            cm.stopCamera()
            cameraManager = null
            Log.e(TAG, "Could not start server on any port")
            return -1
        }

        activePort = port

        // Listen to physical device rotation and update the camera orientation automatically.
        // OrientationEventListener reads the accelerometer directly, works even when the
        // screen orientation is locked or the activity handles configChanges itself.
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // Snap to nearest 90° step.
                // Front camera sensor is mirrored so rotation direction is inverted
                // relative to the back camera.
                val snapped = ((orientation + 45) / 90 * 90) % 360
                val isFront = cameraManager?.getCameraType() == "front"
                val rotation = if (isFront) (360 - snapped) % 360 else snapped
                cameraManager?.setOrientation(rotation)
            }
        }.also { if (it.canDetectOrientation()) it.enable() }

        startForeground(NOTIFICATION_ID, buildNotification("Live at http://$ipAddress:$port/  open app to stop"))
        Log.i(TAG, "Streaming started on port $port")
        return port
    }

    /**
     * Stops camera + server, removes the foreground notification, and stops the service.
     * Safe to call even if not currently streaming.
     */
    fun stopStreaming() {
        stopStreamingInternal()
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
        Log.i(TAG, "Streaming stopped, service stopping")
    }

    fun switchCamera(cameraType: String) {
        cameraManager?.setCameraType(cameraType)
    }

    fun setOrientation(degrees: Int) {
        cameraManager?.setOrientation(degrees)
    }

    fun isCameraOpen(): Boolean = cameraManager?.isCameraOpen() == true

    fun isObsConnected(): Boolean = httpServer?.isClientConnected() == true

    fun getCameraType(): String = cameraManager?.getCameraType() ?: "front"

    fun getActualResolution(): String? = cameraManager?.getActualSize()?.let { "${it.width}x${it.height}" }

    fun getSessionStartTime(): Long = cameraManager?.getSessionStartTime() ?: 0L

    fun getActivePort(): Int = activePort

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun stopStreamingInternal() {
        orientationListener?.disable()
        orientationListener = null
        httpServer?.stop()
        httpServer = null
        cameraManager?.stopCamera()
        cameraManager = null
        activePort = 0
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW // no sound, still visible in status bar
        ).apply {
            description = "Shows while Camari is streaming to OBS"
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camari Streaming")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(tapPendingIntent)
            .build()
    }
}
