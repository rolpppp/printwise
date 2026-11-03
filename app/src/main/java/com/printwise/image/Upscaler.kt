package com.printwise.image

import android.graphics.Bitmap
import com.printwise.model.TARGET_PRINT_PPI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Longest edge an export upscale may produce, so a huge piece can't exhaust memory (10 in at 300 ppi). */
private const val MAX_UPSCALED_EDGE_PX = 3000
/** Gentle: enough to crisp up edges softened by resizing without halos. */
private const val SHARPEN_AMOUNT = 0.35f

/**
 * Export-time upscaling for small pieces. Without it the PDF holds the piece at its few original pixels and the
 * printer stretches them, often blocky or blurry. A careful bicubic resize plus a light sharpen gives cleaner
 * edges. It cannot add detail that isn't in the picture; it only stops quality being lost at print time.
 *
 * The pixel functions work on plain ARGB [IntArray]s (non-premultiplied, as [Bitmap.getPixels] returns them)
 * so they can be unit-tested without Android.
 */
object Upscaler {
    /** [bitmap] resized to print [printHeightIn] tall at [TARGET_PRINT_PPI], or [bitmap] itself if it already has enough pixels. */
    fun forPrint(bitmap: Bitmap, printHeightIn: Float): Bitmap {
        val (outW, outH) = targetSize(bitmap.width, bitmap.height, printHeightIn) ?: return bitmap
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val resized = sharpen(resize(pixels, bitmap.width, bitmap.height, outW, outH), outW, outH, SHARPEN_AMOUNT)
        return Bitmap.createBitmap(resized, outW, outH, Bitmap.Config.ARGB_8888)
    }

    /** Output size for printing [height]-pixel art [printHeightIn] tall, or null when no upscale is needed. */
    fun targetSize(width: Int, height: Int, printHeightIn: Float): Pair<Int, Int>? {
        if (width <= 0 || height <= 0 || printHeightIn <= 0f) return null
        val wanted = ceil(printHeightIn * TARGET_PRINT_PPI).toInt()
        // Within 5% of the target, a resize isn't worth the softening it brings.
        if (wanted <= height * 1.05f) return null
        val scale = min(wanted.toFloat() / height, MAX_UPSCALED_EDGE_PX.toFloat() / max(width, height))
        if (scale <= 1.05f) return null
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /**
     * Catmull-Rom bicubic resize, done as two separable passes. Colours are premultiplied by alpha while
     * filtering so transparent edges don't bleed dark fringes into the artwork.
     */
    fun resize(src: IntArray, width: Int, height: Int, outWidth: Int, outHeight: Int): IntArray {
        // Premultiplied channels: a, r·a, g·a, b·a.
        val input = FloatArray(width * height * 4)
        for (i in 0 until width * height) {
            val c = src[i]
            val a = (c ushr 24) / 255f
            input[i * 4] = a
            input[i * 4 + 1] = ((c shr 16) and 0xFF) * a
            input[i * 4 + 2] = ((c shr 8) and 0xFF) * a
            input[i * 4 + 3] = (c and 0xFF) * a
        }

        val (xIndex, xWeight) = taps(width, outWidth)
        val across = FloatArray(outWidth * height * 4)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until outWidth) {
                val out = (y * outWidth + x) * 4
                for (k in 0 until 4) {
                    val from = (row + xIndex[x * 4 + k]) * 4
                    val w = xWeight[x * 4 + k]
                    across[out] += input[from] * w
                    across[out + 1] += input[from + 1] * w
                    across[out + 2] += input[from + 2] * w
                    across[out + 3] += input[from + 3] * w
                }
            }
        }

        val (yIndex, yWeight) = taps(height, outHeight)
        val result = IntArray(outWidth * outHeight)
        for (y in 0 until outHeight) {
            for (x in 0 until outWidth) {
                var a = 0f
                var r = 0f
                var g = 0f
                var b = 0f
                for (k in 0 until 4) {
                    val from = (yIndex[y * 4 + k] * outWidth + x) * 4
                    val w = yWeight[y * 4 + k]
                    a += across[from] * w
                    r += across[from + 1] * w
                    g += across[from + 2] * w
                    b += across[from + 3] * w
                }
                result[y * outWidth + x] = unpremultiply(a, r, g, b)
            }
        }
        return result
    }

    /** Unsharp mask on colour (alpha untouched): each pixel moves [amount] further away from its 3×3 blur. */
    fun sharpen(pixels: IntArray, width: Int, height: Int, amount: Float): IntArray {
        if (amount <= 0f || width < 3 || height < 3) return pixels
        val out = IntArray(pixels.size)
        val kernel = intArrayOf(1, 2, 1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = pixels[y * width + x]
                if (c ushr 24 == 0) {
                    out[y * width + x] = c
                    continue
                }
                var r = 0
                var g = 0
                var b = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val n = pixels[yy * width + (x + dx).coerceIn(0, width - 1)]
                        val w = kernel[dy + 1] * kernel[dx + 1]
                        r += ((n shr 16) and 0xFF) * w
                        g += ((n shr 8) and 0xFF) * w
                        b += (n and 0xFF) * w
                    }
                }
                fun channel(value: Int, blurred: Int) = (value + amount * (value - blurred / 16f)).roundToInt().coerceIn(0, 255)
                out[y * width + x] = (c and 0xFF000000.toInt()) or
                    (channel((c shr 16) and 0xFF, r) shl 16) or
                    (channel((c shr 8) and 0xFF, g) shl 8) or
                    channel(c and 0xFF, b)
            }
        }
        return out
    }

    /** For each output position, the 4 source indices (edge-clamped) and Catmull-Rom weights that make it. */
    private fun taps(sourceSize: Int, outSize: Int): Pair<IntArray, FloatArray> {
        val indices = IntArray(outSize * 4)
        val weights = FloatArray(outSize * 4)
        val ratio = sourceSize.toFloat() / outSize
        for (i in 0 until outSize) {
            val center = (i + 0.5f) * ratio - 0.5f
            val base = floor(center).toInt()
            var total = 0f
            for (k in 0 until 4) {
                val src = base - 1 + k
                val w = catmullRom(center - src)
                indices[i * 4 + k] = src.coerceIn(0, sourceSize - 1)
                weights[i * 4 + k] = w
                total += w
            }
            if (total != 0f) for (k in 0 until 4) weights[i * 4 + k] /= total
        }
        return indices to weights
    }

    private fun catmullRom(distance: Float): Float {
        val t = abs(distance)
        return when {
            t < 1f -> 1.5f * t * t * t - 2.5f * t * t + 1f
            t < 2f -> -0.5f * t * t * t + 2.5f * t * t - 4f * t + 2f
            else -> 0f
        }
    }

    private fun unpremultiply(a: Float, r: Float, g: Float, b: Float): Int {
        val alpha = a.coerceIn(0f, 1f)
        if (alpha <= 1f / 512f) return 0
        fun channel(v: Float) = (v / alpha).roundToInt().coerceIn(0, 255)
        return ((alpha * 255f).roundToInt() shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}
