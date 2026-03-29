package com.camari.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for CameraManager resolution logic.
 *
 * Tests use the pure-Kotlin companion functions (presetToDimensions, chooseBestDimensions)
 * which operate on Pair<Int, Int> and have no Android framework dependencies.
 */
class CameraManagerTest {

    // -------------------------------------------------------------------------
    // presetToDimensions() — preset label → (width, height) mapping
    // -------------------------------------------------------------------------

    @Test
    fun `presetToDimensions returns 854x480 for 480p`() {
        val (w, h) = CameraManager.presetToDimensions("480p")
        assertEquals(854, w)
        assertEquals(480, h)
    }

    @Test
    fun `presetToDimensions returns 1280x720 for 720p`() {
        val (w, h) = CameraManager.presetToDimensions("720p")
        assertEquals(1280, w)
        assertEquals(720, h)
    }

    @Test
    fun `presetToDimensions returns 1920x1080 for 1080p`() {
        val (w, h) = CameraManager.presetToDimensions("1080p")
        assertEquals(1920, w)
        assertEquals(1080, h)
    }

    @Test
    fun `presetToDimensions returns 1280x720 for unknown preset`() {
        val (w, h) = CameraManager.presetToDimensions("4k")
        assertEquals(1280, w)
        assertEquals(720, h)
    }

    @Test
    fun `presetToDimensions returns 1280x720 for empty string`() {
        val (w, h) = CameraManager.presetToDimensions("")
        assertEquals(1280, w)
        assertEquals(720, h)
    }

    // -------------------------------------------------------------------------
    // chooseBestDimensions() — exact match
    // -------------------------------------------------------------------------

    @Test
    fun `chooseBestDimensions returns exact match for 720p`() {
        val target = Pair(1280, 720)
        val available = listOf(Pair(640, 480), Pair(1280, 720), Pair(1920, 1080))
        val (w, h) = CameraManager.chooseBestDimensions(available, target)
        assertEquals(1280, w)
        assertEquals(720, h)
    }

    @Test
    fun `chooseBestDimensions returns exact match for 1080p`() {
        val target = Pair(1920, 1080)
        val available = listOf(Pair(640, 480), Pair(1280, 720), Pair(1920, 1080))
        val (w, h) = CameraManager.chooseBestDimensions(available, target)
        assertEquals(1920, w)
        assertEquals(1080, h)
    }

    @Test
    fun `chooseBestDimensions returns exact match for 480p`() {
        val target = Pair(854, 480)
        val available = listOf(Pair(854, 480), Pair(1280, 720))
        val (w, h) = CameraManager.chooseBestDimensions(available, target)
        assertEquals(854, w)
        assertEquals(480, h)
    }

    // -------------------------------------------------------------------------
    // chooseBestDimensions() — fallback: largest within bounds
    // -------------------------------------------------------------------------

    @Test
    fun `chooseBestDimensions returns largest size within target when no exact match`() {
        val target = Pair(1280, 720)
        val available = listOf(Pair(640, 480), Pair(800, 600), Pair(1920, 1080))
        val (w, h) = CameraManager.chooseBestDimensions(available, target)
        // 800x600 = 480_000 px fits; 640x480 = 307_200 px fits; 1920x1080 exceeds target
        assertEquals(800, w)
        assertEquals(600, h)
    }

    @Test
    fun `chooseBestDimensions falls back to closest when nothing fits within bounds`() {
        // Device only has sizes larger than the 720p target
        val target = Pair(1280, 720)
        val available = listOf(Pair(1920, 1080), Pair(2560, 1440))
        val (w, h) = CameraManager.chooseBestDimensions(available, target)
        // 1920x1080 is closer to 1280x720 than 2560x1440
        assertEquals(1920, w)
        assertEquals(1080, h)
    }

    @Test
    fun `chooseBestDimensions on 1080p target falls back gracefully when only 720p available`() {
        val target = Pair(1920, 1080)
        val available = listOf(Pair(640, 480), Pair(1280, 720))
        val (w, h) = CameraManager.chooseBestDimensions(available, target)
        // 1280x720 fits within 1920x1080 and is larger than 640x480
        assertEquals(1280, w)
        assertEquals(720, h)
    }

    // -------------------------------------------------------------------------
    // chooseBestDimensions() — never returns null, handles edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `chooseBestDimensions never returns null even with single odd size`() {
        val target = Pair(1280, 720)
        val available = listOf(Pair(320, 240))
        val result = CameraManager.chooseBestDimensions(available, target)
        assertNotNull(result)
        val (w, h) = result
        assertEquals(320, w)
        assertEquals(240, h)
    }

    @Test
    fun `chooseBestDimensions with single exact match returns it`() {
        val target = Pair(1280, 720)
        val available = listOf(Pair(1280, 720))
        val (w, h) = CameraManager.chooseBestDimensions(available, target)
        assertEquals(1280, w)
        assertEquals(720, h)
    }

    @Test
    fun `chooseBestDimensions returns target when available list is empty`() {
        val target = Pair(1280, 720)
        val available = emptyList<Pair<Int, Int>>()
        val (w, h) = CameraManager.chooseBestDimensions(available, target)
        // Falls through all three steps and returns target via ?: target
        assertEquals(1280, w)
        assertEquals(720, h)
    }

    // -------------------------------------------------------------------------
    // chooseBestDimensions() — 480p preset round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `480p preset selects 854x480 when available`() {
        val target = CameraManager.presetToDimensions("480p")
        val available = listOf(Pair(640, 360), Pair(854, 480), Pair(1280, 720))
        val (w, h) = CameraManager.chooseBestDimensions(available, target)
        assertEquals(854, w)
        assertEquals(480, h)
    }

    @Test
    fun `480p preset falls back to largest sub-480p size when 854x480 unavailable`() {
        val target = CameraManager.presetToDimensions("480p")
        val available = listOf(Pair(640, 360), Pair(1280, 720))
        val (w, h) = CameraManager.chooseBestDimensions(available, target)
        // 640x360 fits within 854x480; 1280x720 does not
        assertEquals(640, w)
        assertEquals(360, h)
    }
}
