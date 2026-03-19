package com.camari.streaming

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.app.Activity
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages Camera2 API capture and delivers JPEG frames to the HTTP server.
 *
 * Design:
 * - Camera2 captures continuously via setRepeatingRequest into an ImageReader (JPEG format).
 * - Each new frame is stored in [latestFrame] (AtomicReference).
 * - [captureFrame] is called by the HTTP server thread and reads the latest frame.
 * - Front camera is default; [setCameraType] restarts the session with the other camera.
 */
class CameraManager(private val activity: Activity) {

    private var currentCameraType = "front"
    private var sessionStartTime = 0L
    private var cameraOpen = false

    private val systemCameraManager =
        activity.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    // Latest JPEG frame — written by camera thread, read by HTTP server thread
    private val latestFrame = AtomicReference<ByteArray?>(null)

    // Guards against concurrent startCamera calls (e.g. during camera switch)
    private val openLock = Semaphore(1)

    // Degrees the raw JPEG output must be rotated CW to appear upright (from SENSOR_ORIENTATION)
    private var sensorOrientation = 0

    // Additional CW rotation selected by the user (0, 90, 180, 270)
    @Volatile private var userRotationDegrees = 0

    companion object {
        private const val TAG = "CameraManager"
        private const val TARGET_WIDTH = 1280
        private const val TARGET_HEIGHT = 720
        private const val JPEG_QUALITY: Byte = 85
    }

    fun setCameraType(type: String) {
        if (currentCameraType == type) return
        currentCameraType = type
        Log.i(TAG, "Switching to $type camera")
        if (cameraOpen) {
            stopCamera()
            startCamera()
        }
    }

    fun getCameraType(): String = currentCameraType

    /**
     * Set additional CW rotation on top of sensor auto-correction.
     * degrees: 0 = upright portrait, 90 = landscape-left, 180 = portrait-flipped, 270 = landscape-right
     */
    fun setOrientation(degrees: Int) {
        userRotationDegrees = ((degrees % 360) + 360) % 360
        Log.i(TAG, "User rotation set to ${userRotationDegrees}°")
    }

    fun isCameraOpen(): Boolean = cameraOpen

    fun getSessionStartTime(): Long = sessionStartTime

    fun startCamera() {
        if (!openLock.tryAcquire(2, TimeUnit.SECONDS)) {
            Log.e(TAG, "Timeout acquiring camera lock")
            return
        }
        try {
            startCameraThread()
            val cameraId = findCameraId(currentCameraType)
            if (cameraId == null) {
                Log.e(TAG, "No $currentCameraType camera found on this device")
                stopCameraThread()
                return
            }
            val size = chooseBestSize(cameraId)
            sensorOrientation = systemCameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            Log.i(TAG, "Opening $currentCameraType camera ($cameraId) at ${size.width}x${size.height}, sensor=${sensorOrientation}°")
            openCamera(cameraId, size)
            sessionStartTime = System.currentTimeMillis()
            cameraOpen = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera: ${e.message}", e)
            cameraOpen = false
            stopCameraThread()
        } finally {
            openLock.release()
        }
    }

    fun stopCamera() {
        Log.i(TAG, "Stopping camera...")
        cameraOpen = false
        latestFrame.set(null)
        try { captureSession?.close() } catch (e: Exception) { Log.w(TAG, "Session close: ${e.message}") }
        try { cameraDevice?.close() } catch (e: Exception) { Log.w(TAG, "Device close: ${e.message}") }
        try { imageReader?.close() } catch (e: Exception) { Log.w(TAG, "ImageReader close: ${e.message}") }
        captureSession = null
        cameraDevice = null
        imageReader = null
        stopCameraThread()
        Log.i(TAG, "Camera stopped")
    }

    fun release() = stopCamera()

    /**
     * Returns the latest JPEG frame rotated to the correct orientation, or null if not ready.
     *
     * Total rotation = sensorOrientation (auto-corrects physical sensor mounting angle)
     *                + userRotationDegrees (user preference on top)
     */
    fun captureFrame(): ByteArray? {
        val jpeg = latestFrame.get() ?: return null
        val totalRotation = (sensorOrientation + userRotationDegrees) % 360
        return if (totalRotation == 0) jpeg else rotateJpeg(jpeg, totalRotation)
    }

    private fun rotateJpeg(jpeg: ByteArray, degrees: Int): ByteArray {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: return jpeg
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = android.graphics.Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        bitmap.recycle()
        val out = java.io.ByteArrayOutputStream()
        rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY.toInt(), out)
        rotated.recycle()
        return out.toByteArray()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun findCameraId(type: String): String? {
        val facing = if (type == "front") CameraCharacteristics.LENS_FACING_FRONT
                     else CameraCharacteristics.LENS_FACING_BACK
        return systemCameraManager.cameraIdList.firstOrNull { id ->
            systemCameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == facing
        }
    }

    /**
     * Find the best JPEG output size ≤ 720p.
     * Prefers 1280x720 exactly, otherwise largest size that fits within the target.
     */
    private fun chooseBestSize(cameraId: String): Size {
        val map = systemCameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val sizes = map.getOutputSizes(ImageFormat.JPEG)

        // Exact match first
        sizes.firstOrNull { it.width == TARGET_WIDTH && it.height == TARGET_HEIGHT }
            ?.let { return it }

        // Largest size that fits within our target
        sizes.filter { it.width <= TARGET_WIDTH && it.height <= TARGET_HEIGHT }
            .maxByOrNull { it.width * it.height }
            ?.let { return it }

        // Fallback: closest by area
        return sizes.minByOrNull {
            val dw = (it.width - TARGET_WIDTH).toLong()
            val dh = (it.height - TARGET_HEIGHT).toLong()
            dw * dw + dh * dh
        } ?: Size(TARGET_WIDTH, TARGET_HEIGHT)
    }

    @SuppressLint("MissingPermission") // Permission is checked by CameraStreamPlugin before calling startCamera()
    private fun openCamera(cameraId: String, size: Size) {
        imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    latestFrame.set(bytes)
                } finally {
                    image.close()
                }
            }, cameraHandler)
        }

        systemCameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                Log.i(TAG, "Camera opened: $cameraId")
                cameraDevice = device
                createCaptureSession(device)
            }

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera disconnected")
                device.close()
                cameraDevice = null
                cameraOpen = false
            }

            override fun onError(device: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error $error on $cameraId")
                device.close()
                cameraDevice = null
                cameraOpen = false
            }
        }, cameraHandler)
    }

    @Suppress("DEPRECATION") // createCaptureSession(List, StateCallback, Handler) deprecated in API 30
    private fun createCaptureSession(device: CameraDevice) {
        val surface = imageReader!!.surface
        device.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    startRepeatingCapture(session, device, surface)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session configuration failed")
                    cameraOpen = false
                }
            },
            cameraHandler
        )
    }

    private fun startRepeatingCapture(
        session: CameraCaptureSession,
        device: CameraDevice,
        surface: android.view.Surface
    ) {
        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.JPEG_QUALITY, JPEG_QUALITY)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }
        session.setRepeatingRequest(request.build(), null, cameraHandler)
        Log.i(TAG, "Repeating capture started")
    }

    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraManager-Thread").also {
            it.start()
            cameraHandler = Handler(it.looper)
        }
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        try { cameraThread?.join(1000) } catch (_: InterruptedException) {}
        cameraThread = null
        cameraHandler = null
    }
}
