package com.printwise.segmentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File

class SheetSegmenterTest {
    private fun sheet(width: Int, height: Int, background: Int, vararg rects: IntArray): IntArray {
        val pixels = IntArray(width * height) { background }
        rects.forEach { (l, t, r, b, color) ->
            for (y in t until b) for (x in l until r) pixels[y * width + x] = color
        }
        return pixels
    }

    @Test
    fun findsSeparatePiecesOnGrayBackground() {
        val gray = 0xFFE5E5E5.toInt()
        val pixels = sheet(
            400, 600, gray,
            intArrayOf(20, 20, 120, 140, 0xFFFFFFFF.toInt()),   // white piece: low contrast against gray
            intArrayOf(200, 40, 380, 200, 0xFFE91E63.toInt()),
            intArrayOf(50, 300, 150, 380, 0xFF3F51B5.toInt())
        )
        val result = SheetSegmenter.segment(pixels, 400, 600)
        assertEquals(3, result.pieces.size)
        val white = result.pieces.first { it.left < 100 && it.top < 100 }
        assertEquals(20, white.left)
        assertEquals(20, white.top)
        assertEquals(120, white.right)
        assertEquals(140, white.bottom)
    }

    @Test
    fun tapResolvesToPieceUnderFingerOrNearby() {
        val pixels = sheet(300, 300, 0xFFDDDDDD.toInt(), intArrayOf(100, 100, 200, 200, 0xFF000000.toInt()))
        val result = SheetSegmenter.segment(pixels, 300, 300)
        assertNotNull(result.pieceAt(150, 150, 10))
        assertNotNull(result.pieceAt(205, 150, 10))
        assertNull(result.pieceAt(20, 20, 10))
    }

    @Test
    fun nearbyPiecesStaySeparateAndSpecksAttach() {
        val gray = 0xFFE5E5E5.toInt()
        val pink = 0xFFE91E63.toInt()
        val pixels = sheet(
            400, 400, gray,
            intArrayOf(20, 20, 120, 120, pink),
            intArrayOf(126, 20, 226, 120, pink),     // 6px away: must not merge
            intArrayOf(121, 60, 123, 62, pink)       // 2x2 speck touching-distance from the first piece
        )
        val result = SheetSegmenter.segment(pixels, 400, 400)
        assertEquals(2, result.pieces.size)
        val first = result.pieces.minByOrNull { it.left }!!
        assertEquals(123, first.right)
    }

    @Test
    fun transparentPngUsesAlpha() {
        val pixels = sheet(200, 200, 0x00000000, intArrayOf(50, 50, 100, 120, 0xFFFFFFFF.toInt()))
        val result = SheetSegmenter.segment(pixels, 200, 200)
        assertEquals(1, result.pieces.size)
    }

    /**
     * Manual check on a real sheet: set PRINTWISE_SAMPLE_RAW to a raw dump (int width, int height, ARGB ints)
     * and PRINTWISE_SAMPLE_BOXES to where the found boxes should be written.
     */
    @Test
    fun sampleSheet() {
        val raw = System.getenv("PRINTWISE_SAMPLE_RAW")
        assumeTrue(raw != null && File(raw).exists())
        val (width, height, pixels) = DataInputStream(BufferedInputStream(File(raw!!).inputStream())).use { input ->
            val w = input.readInt()
            val h = input.readInt()
            Triple(w, h, IntArray(w * h) { input.readInt() })
        }
        val out = System.getenv("PRINTWISE_SAMPLE_BOXES") ?: return
        val started = System.nanoTime()
        val result = SheetSegmenter.segment(pixels, width, height)
        val ms = (System.nanoTime() - started) / 1_000_000
        val report = buildString {
            appendLine("# ${width}x$height -> ${result.pieces.size} pieces in ${ms}ms")
            result.pieces.forEach { appendLine("${it.left} ${it.top} ${it.right} ${it.bottom} ${it.pixelCount}") }
        }
        File(out).writeText(report)
    }
}
