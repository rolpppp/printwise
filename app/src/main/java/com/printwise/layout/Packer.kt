package com.printwise.layout

import com.printwise.model.GAP_IN
import com.printwise.model.MARGIN_IN
import com.printwise.model.Orientation
import com.printwise.model.PaperSize
import java.util.UUID

data class PackItem(val pieceId: String, val widthIn: Float, val heightIn: Float)

/** One printed copy on a page. [id] identifies this copy alone, so it can be moved or resized on its own. */
data class Placement(
    val pieceId: String,
    val page: Int,
    val xIn: Float,
    val yIn: Float,
    val widthIn: Float,
    val heightIn: Float,
    val id: String = UUID.randomUUID().toString()
)

data class PageLayout(
    val paper: PaperSize,
    val landscape: Boolean,
    val pageCount: Int,
    val placements: List<Placement>,
    /** Pieces that are larger than the printable area and were left out. */
    val tooBig: Set<String>
) {
    val pageWidthIn get() = if (landscape) paper.heightIn else paper.widthIn
    val pageHeightIn get() = if (landscape) paper.widthIn else paper.heightIn
}

/**
 * Shelf packing (first-fit decreasing height): items are sorted tallest first and placed left-to-right on
 * shelves; each item goes on the first shelf on any page where it fits, else a new shelf, else a new page.
 * Copies of the same piece have the same size, so they end up next to each other, which makes cutting easier.
 */
object Packer {
    fun pack(items: List<PackItem>, paper: PaperSize, orientation: Orientation): PageLayout = when (orientation) {
        Orientation.PORTRAIT -> packOn(items, paper, landscape = false)
        Orientation.LANDSCAPE -> packOn(items, paper, landscape = true)
        Orientation.BEST -> {
            val portrait = packOn(items, paper, landscape = false)
            val landscape = packOn(items, paper, landscape = true)
            when {
                landscape.tooBig.size < portrait.tooBig.size -> landscape
                landscape.tooBig.size > portrait.tooBig.size -> portrait
                landscape.pageCount < portrait.pageCount -> landscape
                else -> portrait
            }
        }
    }

    private class Shelf(val page: Int, val y: Float, val height: Float, var x: Float)

    private fun packOn(items: List<PackItem>, paper: PaperSize, landscape: Boolean): PageLayout {
        val pageWidth = if (landscape) paper.heightIn else paper.widthIn
        val pageHeight = if (landscape) paper.widthIn else paper.heightIn
        val right = pageWidth - MARGIN_IN
        val bottom = pageHeight - MARGIN_IN
        val usableWidth = right - MARGIN_IN
        val usableHeight = bottom - MARGIN_IN
        val epsilon = 1e-4f

        val shelves = mutableListOf<Shelf>()
        val nextY = mutableListOf<Float>() // per page: where the next shelf would start
        val placements = mutableListOf<Placement>()
        val tooBig = mutableSetOf<String>()

        val sorted = items.sortedWith(compareByDescending<PackItem> { it.heightIn }.thenByDescending { it.widthIn })
        for (item in sorted) {
            if (item.widthIn > usableWidth + epsilon || item.heightIn > usableHeight + epsilon) {
                tooBig += item.pieceId
                continue
            }
            var shelf = shelves.firstOrNull { it.height + epsilon >= item.heightIn && it.x + item.widthIn <= right + epsilon }
            if (shelf == null) {
                val page = nextY.indices.firstOrNull { nextY[it] + item.heightIn <= bottom + epsilon }
                    ?: run { nextY += MARGIN_IN; nextY.lastIndex }
                shelf = Shelf(page, nextY[page], item.heightIn, MARGIN_IN)
                nextY[page] = shelf.y + item.heightIn + GAP_IN
                shelves += shelf
            }
            placements += Placement(item.pieceId, shelf.page, shelf.x, shelf.y, item.widthIn, item.heightIn)
            shelf.x += item.widthIn + GAP_IN
        }
        return PageLayout(paper, landscape, nextY.size, placements, tooBig)
    }
}
