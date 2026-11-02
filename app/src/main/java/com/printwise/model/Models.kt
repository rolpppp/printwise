package com.printwise.model

import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** A rectangle in source-image pixels. Immutable so Compose state comparisons stay cheap and correct. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width * height
    val aspect get() = if (height > 0f) width / height else 1f

    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom

    fun clampTo(imageWidth: Float, imageHeight: Float) = Box(
        left.coerceIn(0f, imageWidth), top.coerceIn(0f, imageHeight),
        right.coerceIn(0f, imageWidth), bottom.coerceIn(0f, imageHeight)
    )

    fun offset(dx: Float, dy: Float) = Box(left + dx, top + dy, right + dx, bottom + dy)

    companion object {
        fun spanning(x1: Float, y1: Float, x2: Float, y2: Float) = Box(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }
}

/**
 * One piece the user wants printed.
 * [heightIn] is null while the piece follows the sheet's proportions (see [PrintPlan.heightOf]).
 * [detectedId] links the piece back to the segmenter result it came from, if any.
 */
data class PrintPiece(
    val id: String = UUID.randomUUID().toString(),
    val box: Box,
    val heightIn: Float? = null,
    val copies: Int = 1,
    val detectedId: Int? = null
)

enum class LengthUnit(val label: String, val perInch: Float, val step: Float) {
    IN("in", 1f, 0.25f),
    CM("cm", 2.54f, 0.5f);

    fun fromInches(inches: Float) = inches * perInch
    fun toInches(value: Float) = value / perInch
    fun format(inches: Float): String {
        val value = fromInches(inches)
        val text = if (this == IN) "%.2f".format(value).trimEnd('0').trimEnd('.') else "%.1f".format(value).removeSuffix(".0")
        return "$text $label"
    }
}

enum class PaperSize(val label: String, val widthIn: Float, val heightIn: Float) {
    A4("A4", 8.27f, 11.69f),
    LETTER("Letter", 8.5f, 11f)
}

enum class Orientation(val label: String) { BEST("Best fit"), PORTRAIT("Portrait"), LANDSCAPE("Landscape") }

const val MARGIN_IN = 0.25f
const val GAP_IN = 0.10f
const val DEFAULT_TALLEST_IN = 3f
const val MIN_PIECE_IN = 0.25f

/** Sizing rules shared by the size screen, the packer and the exporters. */
object PrintPlan {
    /** Height of [piece] in inches: its own value, or proportional to the tallest piece on the sheet. */
    fun heightOf(piece: PrintPiece, pieces: List<PrintPiece>, tallestIn: Float): Float {
        piece.heightIn?.let { return it }
        val tallestPx = pieces.maxOfOrNull { it.box.height } ?: return tallestIn
        if (tallestPx <= 0f) return tallestIn
        return max(MIN_PIECE_IN, tallestIn * piece.box.height / tallestPx)
    }

    fun widthOf(piece: PrintPiece, heightIn: Float) = heightIn * piece.box.aspect
}
