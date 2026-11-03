package com.printwise.layout

import com.printwise.model.GAP_IN
import com.printwise.model.MARGIN_IN

/**
 * Finds room for one more piece on a page that already has pieces on it (hand-placed or packed).
 * Candidate corners are the page's top-left and the right and bottom edges of every existing piece; the first
 * one, top to bottom and then left to right, that stays inside the margins and keeps [GAP_IN] from every
 * other piece wins. Not optimal packing, but predictable: new pieces fill gaps from the top.
 */
object FreeSpace {
    private const val EPSILON = 1e-4f

    /** Top-left corner (inches) for a [widthIn] × [heightIn] piece, or null when it doesn't fit. [ignore] is a placement id to treat as absent. */
    fun find(
        onPage: List<Placement>,
        widthIn: Float,
        heightIn: Float,
        pageWidthIn: Float,
        pageHeightIn: Float,
        ignore: String? = null
    ): Pair<Float, Float>? {
        val others = onPage.filter { it.id != ignore }
        val candidates = buildList {
            add(MARGIN_IN to MARGIN_IN)
            others.forEach { p ->
                add(p.xIn + p.widthIn + GAP_IN to p.yIn)
                add(p.xIn to p.yIn + p.heightIn + GAP_IN)
                add(MARGIN_IN to p.yIn + p.heightIn + GAP_IN)
            }
        }.sortedWith(compareBy<Pair<Float, Float>> { it.second }.thenBy { it.first })
        return candidates.firstOrNull { (x, y) ->
            fits(x, y, widthIn, heightIn, pageWidthIn, pageHeightIn) && others.none { overlaps(x, y, widthIn, heightIn, it) }
        }
    }

    /** True when the rectangle stays inside the margins. */
    fun fits(x: Float, y: Float, widthIn: Float, heightIn: Float, pageWidthIn: Float, pageHeightIn: Float) =
        x >= MARGIN_IN - EPSILON && y >= MARGIN_IN - EPSILON &&
            x + widthIn <= pageWidthIn - MARGIN_IN + EPSILON && y + heightIn <= pageHeightIn - MARGIN_IN + EPSILON

    /** True when the rectangle comes closer than [GAP_IN] to [other]. */
    fun overlaps(x: Float, y: Float, widthIn: Float, heightIn: Float, other: Placement) =
        x < other.xIn + other.widthIn + GAP_IN - EPSILON && other.xIn < x + widthIn + GAP_IN - EPSILON &&
            y < other.yIn + other.heightIn + GAP_IN - EPSILON && other.yIn < y + heightIn + GAP_IN - EPSILON
}
