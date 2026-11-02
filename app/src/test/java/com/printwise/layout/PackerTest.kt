package com.printwise.layout

import com.printwise.model.MARGIN_IN
import com.printwise.model.Orientation
import com.printwise.model.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackerTest {
    private fun overlaps(a: Placement, b: Placement) =
        a.page == b.page && a.xIn < b.xIn + b.widthIn && b.xIn < a.xIn + a.widthIn &&
            a.yIn < b.yIn + b.heightIn && b.yIn < a.yIn + a.heightIn

    @Test
    fun placementsStayInsideMarginsAndNeverOverlap() {
        val items = (0 until 40).map { PackItem("p${it % 5}", 1f + (it % 3) * 0.7f, 1f + (it % 4) * 0.5f) }
        val layout = Packer.pack(items, PaperSize.A4, Orientation.PORTRAIT)
        assertEquals(40, layout.placements.size)
        layout.placements.forEach {
            assertTrue(it.xIn >= MARGIN_IN - 1e-3f && it.yIn >= MARGIN_IN - 1e-3f)
            assertTrue(it.xIn + it.widthIn <= layout.pageWidthIn - MARGIN_IN + 1e-3f)
            assertTrue(it.yIn + it.heightIn <= layout.pageHeightIn - MARGIN_IN + 1e-3f)
        }
        for (i in layout.placements.indices) for (j in i + 1 until layout.placements.size) {
            assertFalse(overlaps(layout.placements[i], layout.placements[j]))
        }
    }

    @Test
    fun smallItemsFillEarlierPagesBeforeAddingPages() {
        val items = List(12) { PackItem("a", 2f, 2f) }
        assertEquals(1, Packer.pack(items, PaperSize.A4, Orientation.PORTRAIT).pageCount)
    }

    @Test
    fun oversizeItemsAreReportedNotDropped() {
        val layout = Packer.pack(listOf(PackItem("huge", 10f, 10f), PackItem("ok", 1f, 1f)), PaperSize.A4, Orientation.BEST)
        assertEquals(setOf("huge"), layout.tooBig)
        assertEquals(1, layout.placements.size)
    }

    @Test
    fun bestFitPicksLandscapeForWideItems() {
        val layout = Packer.pack(List(2) { PackItem("wide", 10f, 3f) }, PaperSize.A4, Orientation.BEST)
        assertTrue(layout.landscape)
        assertTrue(layout.tooBig.isEmpty())
    }
}
