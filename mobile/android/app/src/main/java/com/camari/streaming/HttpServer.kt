package com.camari.streaming

import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple HTTP server that streams MJPEG video to OBS browser sources.
 * 
 * Supports:
 * - GET /stream - MJPEG video stream (multipart/x-mixed-replace)
 * - GET /status - JSON status endpoint
 */
class HttpServer(
    private val port: Int,
    private val cameraManager: CameraManager
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = AtomicBoolean(false)
    private var clientSocket: Socket? = null
    private var streamThread: Thread? = null
    
    companion object {
        private const val TAG = "HttpServer"
        private const val BOUNDARY = "frame"
        private const val MIME_TYPE = "multipart/x-mixed-replace; boundary=$BOUNDARY"
    }

    /**
     * Start the HTTP server on the specified port.
     */
    fun start() {
        if (isRunning.get()) {
            Log.w(TAG, "Server already running")
            return
        }

        try {
            serverSocket = ServerSocket(port)
            isRunning.set(true)
            
            // Start accepting connections
            Thread {
                acceptConnections()
            }.apply {
                isDaemon = true
                start()
            }
            
            Log.i(TAG, "HTTP server started on port $port")
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start HTTP server", e)
            throw RuntimeException("Failed to start HTTP server: ${e.message}")
        }
    }

    /**
     * Stop the HTTP server and close all connections.
     */
    fun stop() {
        isRunning.set(false)
        
        // Close client connection
        try {
            clientSocket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing client socket", e)
        }
        clientSocket = null
        
        // Close server socket
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        
        Log.i(TAG, "HTTP server stopped")
    }

    /**
     * Accept incoming HTTP connections.
     */
    private fun acceptConnections() {
        while (isRunning.get()) {
            try {
                val socket = serverSocket?.accept() ?: continue
                handleConnection(socket)
            } catch (e: IOException) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error accepting connection", e)
                }
            }
        }
    }

    /**
     * Handle an incoming HTTP connection.
     */
    private fun handleConnection(socket: Socket) {
        try {
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            
            // Read HTTP request
            val request = inputStream.bufferedReader().readLines().firstOrNull() ?: return
            
            Log.d(TAG, "HTTP request: $request")
            
            // Route request
            when {
                request.startsWith("GET /stream") -> {
                    handleStreamRequest(socket, outputStream)
                }
                request.startsWith("GET /status") -> {
                    handleStatusRequest(outputStream)
                }
                else -> {
                    handleNotFound(outputStream)
                }
            }
            
        } catch (e: IOException) {
            Log.w(TAG, "Client disconnected", e)
            try {
                socket.close()
            } catch (ex: IOException) {
                // Ignore
            }
        }
    }

    /**
     * Handle MJPEG stream request from OBS.
     */
    private fun handleStreamRequest(socket: Socket, outputStream: OutputStream) {
        clientSocket = socket
        
        // Send HTTP response headers
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $MIME_TYPE\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Pragma: no-cache\r\n")
            append("Expires: 0\r\n")
            append("Connection: keep-alive\r\n")
            append("\r\n")
        }
        
        outputStream.write(headers.toByteArray())
        outputStream.flush()
        
        Log.i(TAG, "Streaming started to ${socket.inetAddress.hostAddress}")
        
        // Stream frames continuously
        try {
            while (isRunning.get() && !socket.isClosed) {
                // Capture frame from camera
                val jpegBytes = cameraManager.captureFrame() ?: continue
                
                // Send MJPEG frame
                val frameHeader = buildString {
                    append("--$BOUNDARY\r\n")
                    append("Content-Type: image/jpeg\r\n")
                    append("Content-Length: ${jpegBytes.size}\r\n")
                    append("\r\n")
                }
                
                outputStream.write(frameHeader.toByteArray())
                outputStream.write(jpegBytes)
                outputStream.write("\r\n".toByteArray())
                outputStream.flush()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Stream interrupted", e)
        } finally {
            Log.i(TAG, "Streaming stopped")
            try {
                socket.close()
            } catch (ex: IOException) {
                // Ignore
            }
            clientSocket = null
        }
    }

    /**
     * Handle status endpoint request.
     */
    private fun handleStatusRequest(outputStream: OutputStream) {
        val status = if (isRunning.get() && cameraManager.isCameraOpen()) {
            """{
                "status": "streaming",
                "camera": "${cameraManager.getCameraType()}",
                "resolution": "1280x720",
                "frameRate": 30,
                "connectedClients": ${if (clientSocket != null) 1 else 0},
                "uptime": ${System.currentTimeMillis() - cameraManager.getSessionStartTime()}
            }"""
        } else {
            """{
                "status": "idle",
                "camera": null,
                "resolution": null,
                "frameRate": null,
                "connectedClients": 0,
                "uptime": 0
            }"""
        }
        
        val response = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${status.length}\r\n")
            append("\r\n")
            append(status)
        }
        
        outputStream.write(response.toByteArray())
        outputStream.flush()
    }

    /**
     * Handle 404 Not Found.
     */
    private fun handleNotFound(outputStream: OutputStream) {
        val response = buildString {
            append("HTTP/1.1 404 Not Found\r\n")
            append("Content-Type: text/plain\r\n")
            append("\r\n")
            append("Not Found")
        }
        
        outputStream.write(response.toByteArray())
        outputStream.flush()
    }
}
