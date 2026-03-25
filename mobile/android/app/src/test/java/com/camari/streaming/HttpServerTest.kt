package com.camari.streaming

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * Unit tests for HttpServer.
 *
 * Tests the real HttpServer code against a mock CameraManager — we're testing the
 * HTTP layer, not the camera hardware. CameraManager is mocked at the hardware boundary.
 */
class HttpServerTest {

    private lateinit var server: HttpServer
    private lateinit var mockCameraManager: CameraManager
    private var port: Int = 0

    /** Minimal fake JPEG bytes — content doesn't matter for HTTP header tests. */
    private val fakeJpeg = ByteArray(64) { it.toByte() }

    @Before
    fun setUp() {
        mockCameraManager = mock()
        whenever(mockCameraManager.captureFrame()).thenReturn(fakeJpeg)
        whenever(mockCameraManager.isCameraOpen()).thenReturn(true)
        whenever(mockCameraManager.getCameraType()).thenReturn("front")
        whenever(mockCameraManager.getSessionStartTime()).thenReturn(System.currentTimeMillis())

        port = findFreePort()
        server = HttpServer(port, mockCameraManager)
        server.start()

        // Give the accept loop a moment to start
        Thread.sleep(100)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    // -------------------------------------------------------------------------
    // /health
    // -------------------------------------------------------------------------

    @Test
    fun `GET health returns 200 OK with body OK`() {
        val (statusLine, _, body) = request("GET /health HTTP/1.1")
        assertEquals("HTTP/1.1 200 OK", statusLine)
        assertEquals("OK", body)
    }

    // -------------------------------------------------------------------------
    // / (root HTML wrapper)
    // -------------------------------------------------------------------------

    @Test
    fun `GET root returns 200 with text-html content type`() {
        val (statusLine, headers, _) = request("GET / HTTP/1.1")
        assertEquals("HTTP/1.1 200 OK", statusLine)
        assertTrue(headers.any { it.startsWith("Content-Type: text/html") })
    }

    @Test
    fun `GET root body contains img tag pointing at stream`() {
        val (_, _, body) = request("GET / HTTP/1.1")
        assertTrue("Body should contain <img pointing at /stream", body.contains("<img"))
        assertTrue("Body should reference /stream", body.contains("/stream"))
    }

    // -------------------------------------------------------------------------
    // /status
    // -------------------------------------------------------------------------

    @Test
    fun `GET status returns 200 with application-json content type`() {
        val (statusLine, headers, _) = request("GET /status HTTP/1.1")
        assertEquals("HTTP/1.1 200 OK", statusLine)
        assertTrue(headers.any { it.startsWith("Content-Type: application/json") })
    }

    @Test
    fun `GET status body contains status field`() {
        val (_, _, body) = request("GET /status HTTP/1.1")
        assertTrue("Status JSON should contain 'status' key", body.contains("\"status\""))
    }

    // -------------------------------------------------------------------------
    // /stream
    // -------------------------------------------------------------------------

    @Test
    fun `GET stream returns multipart content type`() {
        // Connect, read just the response line + headers, then close.
        // The stream loop will exit when the socket is closed (IOException).
        val socket = Socket("127.0.0.1", port)
        socket.soTimeout = 3000
        try {
            val out = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            out.write("GET /stream HTTP/1.1\r\n\r\n".toByteArray())
            out.flush()

            val statusLine = reader.readLine()
            assertEquals("HTTP/1.1 200 OK", statusLine)

            val headers = mutableListOf<String>()
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                headers.add(line)
                line = reader.readLine()
            }

            assertTrue(
                "Stream must use multipart/x-mixed-replace",
                headers.any { it.contains("multipart/x-mixed-replace") }
            )
        } finally {
            socket.close()
        }
    }

    // -------------------------------------------------------------------------
    // 404
    // -------------------------------------------------------------------------

    @Test
    fun `GET unknown path returns 404`() {
        val (statusLine, _, _) = request("GET /does-not-exist HTTP/1.1")
        assertEquals("HTTP/1.1 404 Not Found", statusLine)
    }

    // -------------------------------------------------------------------------
    // Server lifecycle
    // -------------------------------------------------------------------------

    @Test
    fun `server stops cleanly and rejects new connections`() {
        server.stop()
        Thread.sleep(200)

        try {
            Socket("127.0.0.1", port).use { it.getInputStream().read() }
            fail("Expected connection refused after server stop")
        } catch (e: Exception) {
            // Expected — server is stopped
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Make a simple HTTP request and return (statusLine, headers, body). */
    private fun request(requestLine: String): Triple<String, List<String>, String> {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 3000
            val out = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            out.write("$requestLine\r\n\r\n".toByteArray())
            out.flush()

            val statusLine = reader.readLine() ?: ""
            val headers = mutableListOf<String>()
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                headers.add(line)
                line = reader.readLine()
            }
            val body = reader.readText()
            return Triple(statusLine, headers, body.trim())
        }
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }
}
