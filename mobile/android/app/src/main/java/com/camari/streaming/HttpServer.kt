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
     * Binds to all network interfaces (0.0.0.0) to allow connections from WebView and host.
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
            
            // Force IPv4 stack for compatibility
            System.setProperty("java.net.preferIPv4Stack", "true")
            
            // Bind to ALL interfaces (0.0.0.0) - this is critical for external access
            serverSocket = ServerSocket(port)
            serverSocket?.reuseAddress = true
            isRunning.set(true)
            
            // Log all possible access URLs
            val bindAddress = serverSocket?.inetAddress
            Log.i(TAG, "===========================================")
            Log.i(TAG, "HTTP SERVER STARTED")
            Log.i(TAG, "Bound to: ${bindAddress?.hostAddress ?: "0.0.0.0"}:$port")
            Log.i(TAG, "java.net.preferIPv4Stack: ${System.getProperty("java.net.preferIPv4Stack")}")
            Log.i(TAG, "Access URLs:")
            Log.i(TAG, "  - From WebView: http://localhost:$port/stream")
            Log.i(TAG, "  - From host (emulator): http://10.0.2.2:$port/stream")
            Log.i(TAG, "  - From network: http://<device-ip>:$port/stream")
            Log.i(TAG, "===========================================")

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
        Log.i(TAG, "Server accepting connections on port $port...")
        
        while (isRunning.get()) {
            try {
                val socket = serverSocket?.accept() ?: continue
                val clientAddress = socket.inetAddress.hostAddress
                Log.i(TAG, "===========================================")
                Log.i(TAG, "NEW CONNECTION ACCEPTED")
                Log.i(TAG, "Client: $clientAddress")
                Log.i(TAG, "Local: ${socket.localAddress.hostAddress}:${socket.localPort}")
                Log.i(TAG, "===========================================")
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
        Log.i(TAG, "=== HANDLE CONNECTION START ===")
        Log.i(TAG, "Socket: $socket")
        
        try {
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            
            Log.i(TAG, "Got input/output streams")
            
            // Read HTTP request with timeout
            socket.soTimeout = 2000 // 2 second timeout
            val reader = inputStream.bufferedReader()
            val request = reader.readLine()
            
            Log.i(TAG, "Received request: '$request'")
            
            if (request.isNullOrBlank()) {
                Log.w(TAG, "Empty request, closing connection")
                socket.close()
                return
            }
            
            // Route request
            when {
                request.startsWith("GET /stream") -> {
                    Log.i(TAG, "Routing to stream handler")
                    handleStreamRequest(socket, outputStream)
                }
                request.startsWith("GET /status") -> {
                    Log.i(TAG, "Routing to status handler")
                    handleStatusRequest(outputStream)
                    socket.close()
                }
                request.startsWith("GET /health") -> {
                    Log.i(TAG, "Routing to health handler")
                    handleHealthCheck(outputStream)
                    socket.close()
                }
                request.startsWith("GET /") -> {
                    Log.i(TAG, "Routing to root handler")
                    handleRootRequest(outputStream)
                    socket.close()
                }
                else -> {
                    Log.w(TAG, "Unknown request: $request")
                    handleNotFound(outputStream)
                    socket.close()
                }
            }
            
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Socket timeout waiting for request")
            try { socket.close() } catch (ex: Exception) {}
        } catch (e: IOException) {
            Log.w(TAG, "IO error: ${e.message}")
            try { socket.close() } catch (ex: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            try { socket.close() } catch (ex: Exception) {}
        }
        
        Log.i(TAG, "=== HANDLE CONNECTION END ===")
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
     * Handle root endpoint - simple hello world.
     */
    private fun handleRootRequest(outputStream: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Camari Webcam Server</title></head>
            <body>
                <h1>🎥 Camari Webcam Server</h1>
                <p>Server is running!</p>
                <ul>
                    <li><a href="/stream">/stream</a> - MJPEG video stream (for OBS)</li>
                    <li><a href="/health">/health</a> - Health check</li>
                    <li><a href="/status">/status</a> - Server status</li>
                </ul>
            </body>
            </html>
        """.trimIndent()
        
        val response = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html\r\n")
            append("Content-Length: ${html.length}\r\n")
            append("\r\n")
            append(html)
        }
        
        outputStream.write(response.toByteArray())
        outputStream.flush()
        Log.i(TAG, "Root request served")
    }

    /**
     * Handle health check endpoint.
     */
    private fun handleHealthCheck(outputStream: OutputStream) {
        val response = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/plain\r\n")
            append("\r\n")
            append("OK - Server is running")
        }
        
        outputStream.write(response.toByteArray())
        outputStream.flush()
        Log.i(TAG, "Health check served")
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
