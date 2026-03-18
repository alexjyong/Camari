package com.camari.streaming

import android.app.Activity
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Manages Camera2 API for capturing video frames.
 * 
 * Supports:
 * - Front and rear camera switching
 * - 720p @ 30fps capture
 * - JPEG frame encoding for MJPEG streaming
 */
class CameraManager(private val activity: Activity) {
    
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    
    private var currentCameraType = "front"
    private var cameraId: String? = null
    private var sessionStartTime = 0L
    
    private val cameraOpenSemaphore = Semaphore(1)
    
    companion object {
        private const val TAG = "CameraManager"
        private const val PREVIEW_WIDTH = 1280
        private const val PREVIEW_HEIGHT = 720
        private const val JPEG_QUALITY = 80
    }

    /**
     * Set the camera type (front or back).
     */
    fun setCameraType(type: String) {
        if (currentCameraType == type && cameraDevice != null) {
            return // Already using this camera
        }
        
        currentCameraType = type
        restartCamera()
    }

    /**
     * Get the current camera type.
     */
    fun getCameraType(): String {
        return currentCameraType
    }

    /**
     * Check if camera is open and ready.
     */
    fun isCameraOpen(): Boolean {
        return cameraDevice != null
    }

    /**
     * Get the session start time.
     */
    fun getSessionStartTime(): Long {
        return sessionStartTime
    }

    /**
     * Start the camera and background thread.
     */
    fun startCamera() {
        if (cameraDevice != null) {
            Log.w(TAG, "Camera already started")
            return
        }

        // Start background thread
        startBackgroundThread()
        
        // Find camera ID
        cameraId = findCameraId()
        if (cameraId == null) {
            Log.e(TAG, "No suitable camera found")
            return
        }

        try {
            cameraOpenSemaphore.acquire()
            
            val cameraManager = activity.getSystemService(Activity.CAMERA_SERVICE) as CameraManager
            cameraManager.openCamera(cameraId!!, stateCallback, backgroundHandler)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            cameraOpenSemaphore.release()
        }
    }

    /**
     * Stop the camera and release resources.
     */
    fun stopCamera() {
        stopBackgroundThread()
        closeCamera()
    }

    /**
     * Release all camera resources.
     */
    fun release() {
        stopCamera()
    }

    /**
     * Capture a single frame as JPEG bytes.
     * This is called by the HTTP server for each MJPEG frame.
     */
    fun captureFrame(): ByteArray? {
        val image = imageReader?.acquireLatestImage() ?: return null
        
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            // If already JPEG, return directly
            if (image.format == ImageFormat.JPEG) {
                return bytes
            }
            
            // Convert YUV to JPEG if needed
            return convertYuvToJpeg(bytes, image.width, image.height)
            
        } finally {
            image.close()
        }
    }

    // Private methods

    private fun findCameraId(): String? {
        val cameraManager = activity.getSystemService(Activity.CAMERA_SERVICE) as CameraManager
        val cameraIdList = cameraManager.cameraIdList
        
        for (id in cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            
            val isFront = lensFacing == CameraCharacteristics.LENS_FACING_FRONT
            if ((isFront && currentCameraType == "front") || 
                (!isFront && currentCameraType == "back")) {
                return id
            }
        }
        
        return cameraIdList.firstOrNull()
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.i(TAG, "Camera opened: ${camera.id}")
            cameraDevice = camera
            sessionStartTime = System.currentTimeMillis()
            cameraOpenSemaphore.release()
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "Camera disconnected")
            cameraOpenSemaphore.release()
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            cameraOpenSemaphore.release()
            camera.close()
            cameraDevice = null
        }
    }

    private fun createCameraPreviewSession() {
        try {
            // Setup ImageReader for frame capture
            imageReader = ImageReader.newInstance(
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT,
                ImageFormat.JPEG,
                2 // Max images
            )

            val previewRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            previewRequest?.addTarget(imageReader!!.surface)
            previewRequest?.set(CaptureRequest.JPEG_ORIENTATION, getOrientation())
            previewRequest?.set(CaptureRequest.JPEG_QUALITY, JPEG_QUALITY.toByte())
            
            captureRequestBuilder = previewRequest

            imageReader?.setOnImageAvailableListener({ reader ->
                // Frame available - handled by captureFrame() polling
            }, backgroundHandler)

            cameraDevice?.createCaptureSession(
                listOf(imageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.i(TAG, "Capture session configured")
                        cameraCaptureSession = session
                        startPreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                    }
                },
                backgroundHandler
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture session", e)
        }
    }

    private fun startPreview() {
        try {
            val repeatingRequest = captureRequestBuilder?.build()
            cameraCaptureSession?.setRepeatingRequest(
                repeatingRequest!!,
                null,
                backgroundHandler
            )
            Log.i(TAG, "Preview started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start preview", e)
        }
    }

    private fun closeCamera() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            
            cameraDevice?.close()
            cameraDevice = null
            
            imageReader?.close()
            imageReader = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }

    private fun restartCamera() {
        closeCamera()
        startCamera()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    private fun getOrientation(): Int {
        // Simplified - should account for device rotation
        return when (currentCameraType) {
            "front" -> 270 // Mirror front camera
            else -> 90
        }
    }

    private fun convertYuvToJpeg(yuvData: ByteArray, width: Int, height: Int): ByteArray? {
        // Simplified JPEG conversion
        // In production, use a proper YUV to JPEG converter
        return try {
            val outputStream = ByteArrayOutputStream()
            // Placeholder - actual implementation would use Bitmap or native encoding
            outputStream.write(yuvData)
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "JPEG conversion failed", e)
            null
        }
    }
}
