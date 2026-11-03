package com.printwise.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpscalerTest {
    private val red = 0xFFE03030.toInt()
    private val blue = 0xFF3050E0.toInt()

    @Test
    fun noUpscaleWhenThereAreEnoughPixels() {
        // 900 px printed 3 in tall is exactly 300 ppi.
        assertNull(Upscaler.targetSize(600, 900, 3f))
    }

    @Test
    fun smallPieceIsScaledToThreeHundredPpi() {
        assertEquals(400 to 600, Upscaler.targetSize(200, 300, 2f))
    }

    @Test
    fun hugeTargetIsCapped() {
        val (w, h) = Upscaler.targetSize(100, 200, 20f)!!
        assertEquals(3000, maxOf(w, h))
        assertEquals(1500, w)
    }

    @Test
    fun uniformColourStaysUniform() {
        val out = Upscaler.resize(IntArray(16) { red }, 4, 4, 13, 9)
        assertEquals(13 * 9, out.size)
        assertTrue(out.all { it == red })
    }

    @Test
    fun transparentStaysTransparentWithoutDarkFringes() {
        // Left half transparent, right half opaque red: the blend should never darken toward black.
        val src = IntArray(8 * 2) { i -> if (i % 8 < 4) 0 else red }
        val out = Upscaler.resize(src, 8, 2, 32, 8)
        assertEquals(0, out[0])
        out.filter { it ushr 24 > 0 }.forEach { c -> assertTrue((c shr 16) and 0xFF >= 0xD0) }
    }

    @Test
    fun hardEdgeKeepsBothColoursAtTheEnds() {
        val src = IntArray(8 * 2) { i -> if (i % 8 < 4) red else blue }
        val out = Upscaler.resize(src, 8, 2, 32, 8)
        assertEquals(red, out[0])
        assertEquals(blue, out[31])
    }

    @Test
    fun sharpenLeavesFlatAreasAlone() {
        val flat = IntArray(25) { blue }
        assertTrue(Upscaler.sharpen(flat, 5, 5, 0.5f).all { it == blue })
    }
}
