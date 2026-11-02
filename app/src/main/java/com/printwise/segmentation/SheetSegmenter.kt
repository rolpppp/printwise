package com.printwise.segmentation

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One printable piece found on the sheet. Bounds are in the analysed image's pixels; right/bottom are exclusive. */
data class Piece(
    val id: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val pixelCount: Int
) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * Result of segmenting a sheet. [labels] maps each pixel to a piece id (or -1 for background), so a tap is
 * resolved to the exact piece under the finger rather than to whichever bounding box happens to contain it.
 */
class SheetSegmentation(
    val width: Int,
    val height: Int,
    private val labels: IntArray,
    private val foreground: BooleanArray,
    val pieces: List<Piece>
) {
    private val byId = pieces.associateBy { it.id }

    /**
     * Bounds of the artwork (non-background pixels) inside the given rectangle, as [left, top, right, bottom)
     * — used to snap a roughly drawn box tight around what the user circled. Null if the area is empty.
     */
    fun contentBounds(left: Int, top: Int, right: Int, bottom: Int): IntArray? {
        val l = left.coerceIn(0, width)
        val t = top.coerceIn(0, height)
        val r = right.coerceIn(0, width)
        val b = bottom.coerceIn(0, height)
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in t until b) {
            val row = y * width
            for (x in l until r) {
                if (foreground[row + x]) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else intArrayOf(minX, minY, maxX + 1, maxY + 1)
    }

    /** The piece at (x, y), or the nearest one within [searchRadius] pixels when the tap lands just outside. */
    fun pieceAt(x: Int, y: Int, searchRadius: Int): Piece? {
        if (x in 0 until width && y in 0 until height) {
            byId[labels[y * width + x]]?.let { return it }
        }
        var best: Piece? = null
        var bestDistance = Int.MAX_VALUE
        for (dy in -searchRadius..searchRadius) {
            val py = y + dy
            if (py !in 0 until height) continue
            for (dx in -searchRadius..searchRadius) {
                val px = x + dx
                if (px !in 0 until width) continue
                val piece = byId[labels[py * width + px]] ?: continue
                val distance = dx * dx + dy * dy
                if (distance < bestDistance && distance <= searchRadius * searchRadius) {
                    bestDistance = distance
                    best = piece
                }
            }
        }
        return best
    }
}

/**
 * Finds the separate pieces on a decoration sheet: artwork laid out on a plain, roughly uniform background
 * (or a transparent one, for PNGs).
 *
 * 1. Estimate the background colour from the image border.
 * 2. Mark pixels that clearly differ from it as foreground. The threshold sits above JPEG ringing so the faint
 *    halo around edges does not bridge neighbouring pieces.
 * 3. Label 8-connected foreground regions.
 * 4. Fold tiny detached fragments (sparkles, loose dots) into the piece they sit next to, and drop lone specks.
 *
 * Pieces that physically overlap on the sheet come out as one piece; the editor lets the user split those
 * by drawing a box.
 */
object SheetSegmenter {
    private const val MIN_PIECE_FRACTION = 0.0005f
    private const val MIN_THRESHOLD = 30f
    private const val MAX_THRESHOLD = 90f

    fun segment(argb: IntArray, width: Int, height: Int): SheetSegmentation =
        segment(argb, width, height, MIN_THRESHOLD)

    internal fun segment(argb: IntArray, width: Int, height: Int, minThreshold: Float): SheetSegmentation {
        require(argb.size == width * height) { "Pixel buffer does not match $width x $height" }
        val foreground = foregroundMask(argb, width, height, minThreshold)
        val labels = IntArray(width * height) { -1 }
        val queue = IntArray(width * height)
        val regions = ArrayList<Region>()

        for (start in 0 until width * height) {
            if (!foreground[start] || labels[start] != -1) continue
            val region = Region(regions.size, width, height, -1, -1, 0)
            var head = 0
            var tail = 0
            queue[tail++] = start
            labels[start] = region.id
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                region.include(x, y)
                for (ny in max(0, y - 1)..min(height - 1, y + 1)) {
                    for (nx in max(0, x - 1)..min(width - 1, x + 1)) {
                        val next = ny * width + nx
                        if (foreground[next] && labels[next] == -1) {
                            labels[next] = region.id
                            queue[tail++] = next
                        }
                    }
                }
            }
            regions += region
        }

        val minPixels = max(24, (width * height * MIN_PIECE_FRACTION).toInt())
        val attachGap = max(3, min(width, height) / 150)
        val big = regions.filter { it.count >= minPixels }
        // Owner of each region after folding fragments in: itself for big ones, a big neighbour or -1 for small.
        val owner = IntArray(regions.size) { -1 }
        big.forEach { owner[it.id] = it.id }
        regions.filter { it.count < minPixels }.forEach { fragment ->
            val host = big
                .filter { it.gapTo(fragment) <= attachGap }
                .minByOrNull { it.gapTo(fragment) }
            if (host != null) {
                owner[fragment.id] = host.id
                host.absorb(fragment)
            }
        }
        for (i in labels.indices) if (labels[i] != -1) labels[i] = owner[labels[i]]

        val pieces = big
            .filterNot { it.width() >= width * 0.95f && it.height() >= height * 0.95f } // the "background" itself
            .map { Piece(it.id, it.left, it.top, it.right + 1, it.bottom + 1, it.count) }
        val kept = pieces.map { it.id }.toHashSet()
        for (i in labels.indices) if (labels[i] != -1 && labels[i] !in kept) labels[i] = -1
        // Reading order: rows top-to-bottom, then left-to-right.
        val rowHeight = max(1, height / 12)
        val ordered = pieces.sortedWith(compareBy<Piece> { (it.top + it.bottom) / 2 / rowHeight }.thenBy { it.left })
        return SheetSegmentation(width, height, labels, foreground, ordered)
    }

    private class Region(val id: Int, var left: Int, var top: Int, var right: Int, var bottom: Int, var count: Int) {
        fun include(x: Int, y: Int) {
            count++
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        fun absorb(other: Region) {
            left = min(left, other.left); top = min(top, other.top)
            right = max(right, other.right); bottom = max(bottom, other.bottom)
            count += other.count
        }
        fun width() = right - left + 1
        fun height() = bottom - top + 1
        fun gapTo(other: Region): Int {
            val dx = max(0, max(other.left - right, left - other.right))
            val dy = max(0, max(other.top - bottom, top - other.bottom))
            return max(dx, dy)
        }
    }

    private fun foregroundMask(argb: IntArray, width: Int, height: Int, minThreshold: Float): BooleanArray {
        val band = max(2, min(width, height) / 100)
        val border = ArrayList<Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x < band || y < band || x >= width - band || y >= height - band) border += argb[y * width + x]
            }
        }
        val transparentBorder = border.count { (it ushr 24) < 128 } > border.size / 2
        if (transparentBorder) {
            return BooleanArray(argb.size) { (argb[it] ushr 24) >= 128 }
        }
        val bgR = median(border.map { (it shr 16) and 0xFF })
        val bgG = median(border.map { (it shr 8) and 0xFF })
        val bgB = median(border.map { it and 0xFF })
        fun distance(color: Int): Float {
            val dr = ((color shr 16) and 0xFF) - bgR
            val dg = ((color shr 8) and 0xFF) - bgG
            val db = (color and 0xFF) - bgB
            return sqrt((dr * dr + dg * dg + db * db).toFloat())
        }
        // Pieces often touch the border, so use the lower 80% of border pixels as the noise sample.
        val borderNoise = border.map { distance(it) }.sorted().let { it[(it.size * 0.8f).toInt().coerceAtMost(it.size - 1)] }
        val threshold = (borderNoise * 2f + 14f).coerceIn(minThreshold, MAX_THRESHOLD)
        return BooleanArray(argb.size) { index ->
            val color = argb[index]
            (color ushr 24) >= 128 && distance(color) > threshold
        }
    }

    private fun median(values: List<Int>): Int = values.sorted()[values.size / 2]
}
