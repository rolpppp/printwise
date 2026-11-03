package com.printwise.layout

import com.printwise.model.GAP_IN
import com.printwise.model.MARGIN_IN
import com.printwise.model.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSpaceTest {
    private val pageW = PaperSize.A4.widthIn
    private val pageH = PaperSize.A4.heightIn

    @Test
    fun emptyPageUsesTopLeftMargin() {
        val spot = FreeSpace.find(emptyList(), 2f, 2f, pageW, pageH)
        assertEquals(MARGIN_IN to MARGIN_IN, spot)
    }

    @Test
    fun fullPageHasNoRoom() {
        val full = Placement("a", 0, MARGIN_IN, MARGIN_IN, pageW - 2 * MARGIN_IN, pageH - 2 * MARGIN_IN)
        assertNull(FreeSpace.find(listOf(full), 1f, 1f, pageW, pageH))
    }

    @Test
    fun tooBigForThePageHasNoRoom() {
        assertNull(FreeSpace.find(emptyList(), pageW, 1f, pageW, pageH))
    }

    @Test
    fun repeatedAddsStayInsideMarginsAndKeepTheirGap() {
        val placed = mutableListOf<Placement>()
        while (true) {
            val (x, y) = FreeSpace.find(placed, 1.7f, 2.3f, pageW, pageH) ?: break
            placed += Placement("p${placed.size}", 0, x, y, 1.7f, 2.3f)
        }
        assertTrue(placed.size >= 8)
        placed.forEach { assertTrue(FreeSpace.fits(it.xIn, it.yIn, it.widthIn, it.heightIn, pageW, pageH)) }
        for (i in placed.indices) for (j in i + 1 until placed.size) {
            val a = placed[i]
            assertFalse(FreeSpace.overlaps(a.xIn, a.yIn, a.widthIn, a.heightIn, placed[j]))
        }
    }

    @Test
    fun ignoredPlacementCanBeReplacedOnItsOwnPage() {
        val only = Placement("a", 0, MARGIN_IN, MARGIN_IN, pageW - 2 * MARGIN_IN, 3f, id = "keep")
        assertNull(FreeSpace.find(listOf(only), pageW - 2 * MARGIN_IN, 9f, pageW, pageH))
        assertNotNull(FreeSpace.find(listOf(only), pageW - 2 * MARGIN_IN, 9f, pageW, pageH, ignore = "keep"))
    }

    @Test
    fun secondPieceSitsToTheRightOfTheFirst() {
        val first = Placement("a", 0, MARGIN_IN, MARGIN_IN, 2f, 2f)
        assertEquals(MARGIN_IN + 2f + GAP_IN to MARGIN_IN, FreeSpace.find(listOf(first), 2f, 2f, pageW, pageH))
    }
}
