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
     * Binds to all network interfaces (0.0.0.0) to allow connections from WebView.
     */
    fun start() {
        if (isRunning.get()) {
            Log.w(TAG, "Server already running")
            return
        }

        // First, try to clean up any existing server socket
        stop()
        
        try {
            // Allow socket reuse to prevent EADDRINUSE
            val serverSocketTemp = ServerSocket()
            serverSocketTemp.reuseAddress = true
            serverSocketTemp.bind(java.net.InetSocketAddress(port), 1)
            serverSocketTemp.close()
            
            // Small delay to ensure port is fully released
            Thread.sleep(100)
            
            // Bind to all interfaces (0.0.0.0) to allow WebView connections
            serverSocket = ServerSocket(port)
            serverSocket?.reuseAddress = true
            isRunning.set(true)
            
            val localAddress = serverSocket?.inetAddress?.hostAddress ?: "0.0.0.0"
            Log.i(TAG, "HTTP server started on $localAddress:$port")
            Log.i(TAG, "Stream URL: http://$localAddress:$port/stream")

            // Start accepting connections in background thread
            Thread {
                acceptConnections()
            }.apply {
                isDaemon = true
                name = "HttpServer-Thread"
                start()
            }

        } catch (e: IOException) {
            Log.e(TAG, "Failed to start HTTP server on port $port: ${e.message}", e)
            throw RuntimeException("Failed to start HTTP server: ${e.message}")
        }
    }

    /**
     * Stop the HTTP server and close all connections.
     */
    fun stop() {
        Log.i(TAG, "Stopping HTTP server...")
        
        isRunning.set(false)
        
        // Close client connection
        try {
            clientSocket?.close()
            clientSocket = null
            Log.i(TAG, "Client connection closed")
        } catch (e: IOException) {
            Log.w(TAG, "Error closing client socket", e)
        }
        
        // Close server socket
        try {
            serverSocket?.close()
            serverSocket = null
            Log.i(TAG, "Server socket closed")
        } catch (e: IOException) {
            Log.w(TAG, "Error closing server socket", e)
        }
        
        Log.i(TAG, "HTTP server stopped")
    }

    /**
     * Accept incoming HTTP connections.
     */
    private fun acceptConnections() {
        Log.i(TAG, "Server accepting connections...")
        
        while (isRunning.get()) {
            try {
                val socket = serverSocket?.accept() ?: continue
                Log.d(TAG, "New connection from: ${socket.inetAddress.hostAddress}")
                handleConnection(socket)
            } catch (e: IOException) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error accepting connection", e)
                } else {
                    Log.d(TAG, "Server stopped, exiting accept loop")
                }
            }
        }
        
        Log.i(TAG, "Accept loop exited")
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
            
            Log.d(TAG, "HTTP request: $request from ${socket.inetAddress.hostAddress}")
            
            // Route request
            when {
                request.startsWith("GET /stream") -> {
                    Log.i(TAG, "Starting stream to ${socket.inetAddress.hostAddress}")
                    handleStreamRequest(socket, outputStream)
                }
                request.startsWith("GET /status") -> {
                    Log.d(TAG, "Status request")
                    handleStatusRequest(outputStream)
                }
                else -> {
                    Log.w(TAG, "Unknown request: $request")
                    handleNotFound(outputStream)
                }
            }
            
        } catch (e: IOException) {
            Log.w(TAG, "Client disconnected: ${e.message}")
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
        var frameCount = 0
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
                
                frameCount++
                if (frameCount % 30 == 0) {
                    Log.d(TAG, "Sent $frameCount frames to ${socket.inetAddress.hostAddress}")
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Stream interrupted after $frameCount frames", e)
        } finally {
            Log.i(TAG, "Streaming stopped after $frameCount frames")
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
