package com.camari.streaming

import android.util.Log
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Simple HTTP server that streams MJPEG video to OBS browser sources.
 *
 * Supports:
 * - GET /stream - MJPEG video stream (multipart/x-mixed-replace)
 * - GET /status - JSON status endpoint
 * - GET /health - Health check
 * - GET /     - Info page
 */
class HttpServer(
    private val port: Int,
    private val cameraManager: CameraManager
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val clientSocket = AtomicReference<Socket?>(null)

    companion object {
        private const val TAG = "HttpServer"
        private const val BOUNDARY = "frame"
        private const val MIME_TYPE = "multipart/x-mixed-replace; boundary=$BOUNDARY"
        private const val TARGET_FRAME_MS = 1000L / 30 // ~33ms per frame at 30fps
    }

    fun start() {
        if (isRunning.get()) {
            Log.w(TAG, "Server already running")
            return
        }

        try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(java.net.InetSocketAddress("0.0.0.0", port))
            serverSocket = ss
            isRunning.set(true)

            Log.i(TAG, "HTTP SERVER STARTED on port $port")

            Thread {
                acceptConnections()
            }.apply {
                isDaemon = true
                name = "HttpServer-Accept"
                start()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start HTTP server on port $port: ${e.message}", e)
            throw RuntimeException("Failed to start HTTP server: ${e.message}")
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping HTTP server...")
        isRunning.set(false)

        try { clientSocket.getAndSet(null)?.close() } catch (e: IOException) { /* ignore */ }
        try { serverSocket?.close(); serverSocket = null } catch (e: IOException) { /* ignore */ }

        Log.i(TAG, "HTTP server stopped")
    }

    private fun acceptConnections() {
        Log.i(TAG, "Accepting connections on port $port...")
        while (isRunning.get()) {
            try {
                val socket = serverSocket?.accept() ?: continue
                Log.i(TAG, "Connection from ${socket.inetAddress.hostAddress}")
                Thread {
                    handleConnection(socket)
                }.apply {
                    isDaemon = true
                    name = "HttpServer-Conn"
                    start()
                }
            } catch (e: IOException) {
                if (isRunning.get()) Log.e(TAG, "Accept error: ${e.message}")
            }
        }
        Log.i(TAG, "Accept loop exited")
    }

    private fun handleConnection(socket: Socket) {
        try {
            // Disable Nagle's algorithm so small responses aren't held back
            socket.tcpNoDelay = true
            socket.soTimeout = 5000

            val requestLine = readRequestLine(socket.getInputStream())
            Log.i(TAG, "Request: '$requestLine'")

            if (requestLine.isNullOrBlank()) {
                socket.close()
                return
            }

            val out = BufferedOutputStream(socket.getOutputStream())
            when {
                requestLine.startsWith("GET /stream") -> handleStreamRequest(socket, out)
                requestLine.startsWith("GET /status") -> { handleStatusRequest(out); socket.close() }
                requestLine.startsWith("GET /health") -> { handleHealthCheck(out); socket.close() }
                requestLine.startsWith("GET /")       -> { handleRootRequest(out); socket.close() }
                else -> { handleNotFound(out); socket.close() }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Timeout reading request from ${socket.inetAddress.hostAddress}")
            try { socket.close() } catch (_: Exception) {}
        } catch (e: IOException) {
            Log.w(TAG, "IO error handling connection: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /**
     * Read the HTTP request line byte-by-byte to avoid BufferedReader consuming headers
     * into its internal buffer and then blocking waiting for the request body.
     */
    private fun readRequestLine(input: InputStream): String? {
        val sb = StringBuilder(128)
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) break
            if (prev == '\r'.code && b == '\n'.code) {
                // Strip trailing \r
                if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
                break
            }
            sb.append(b.toChar())
            prev = b
        }
        return sb.toString().ifBlank { null }
    }

    private fun handleStreamRequest(socket: Socket, out: BufferedOutputStream) {
        clientSocket.set(socket)

        val headers = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: $MIME_TYPE\r\n" +
            "Cache-Control: no-cache\r\n" +
            "Pragma: no-cache\r\n" +
            "Connection: keep-alive\r\n" +
            "\r\n"
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.flush()

        Log.i(TAG, "Streaming to ${socket.inetAddress.hostAddress}")

        var frameCount = 0
        try {
            while (isRunning.get() && !socket.isClosed) {
                val frameStart = System.currentTimeMillis()

                val jpegBytes = cameraManager.captureFrame() ?: continue

                val frameHeader = "--$BOUNDARY\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: ${jpegBytes.size}\r\n" +
                    "\r\n"
                out.write(frameHeader.toByteArray(Charsets.US_ASCII))
                out.write(jpegBytes)
                out.write("\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()

                frameCount++
                if (frameCount % 30 == 0) {
                    Log.d(TAG, "Sent $frameCount frames")
                }

                // Pace frames to ~30fps
                val elapsed = System.currentTimeMillis() - frameStart
                val sleep = TARGET_FRAME_MS - elapsed
                if (sleep > 0) Thread.sleep(sleep)
            }
        } catch (e: IOException) {
            Log.i(TAG, "Stream ended after $frameCount frames: ${e.message}")
        } finally {
            clientSocket.compareAndSet(socket, null)
            try { socket.close() } catch (_: IOException) {}
            Log.i(TAG, "Stream closed after $frameCount frames")
        }
    }

    private fun handleStatusRequest(out: BufferedOutputStream) {
        val body = if (cameraManager.isCameraOpen()) {
            """{"status":"streaming","camera":"${cameraManager.getCameraType()}","resolution":"1280x720","frameRate":30,"connectedClients":${if (clientSocket.get() != null) 1 else 0},"uptime":${System.currentTimeMillis() - cameraManager.getSessionStartTime()}}"""
        } else {
            """{"status":"idle","camera":null,"resolution":null,"frameRate":null,"connectedClients":0,"uptime":0}"""
        }
        writeResponse(out, "200 OK", "application/json", body)
    }

    private fun handleRootRequest(out: BufferedOutputStream) {
        val body = """<!DOCTYPE html><html><head><title>Camari</title></head><body>
<h1>Camari Webcam Server</h1><p>Server is running on port $port</p>
<ul><li><a href="/stream">/stream</a> - MJPEG stream (paste into OBS)</li>
<li><a href="/health">/health</a> - Health check</li>
<li><a href="/status">/status</a> - Status JSON</li></ul>
</body></html>"""
        writeResponse(out, "200 OK", "text/html", body)
        Log.i(TAG, "Root served")
    }

    private fun handleHealthCheck(out: BufferedOutputStream) {
        writeResponse(out, "200 OK", "text/plain", "OK")
        Log.i(TAG, "Health check served")
    }

    private fun handleNotFound(out: BufferedOutputStream) {
        writeResponse(out, "404 Not Found", "text/plain", "Not Found")
    }

    private fun writeResponse(out: BufferedOutputStream, status: String, contentType: String, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Connection: close\r\n" +
            "\r\n"
        out.write(response.toByteArray(Charsets.US_ASCII))
        out.write(bodyBytes)
        out.flush()
    }
}
