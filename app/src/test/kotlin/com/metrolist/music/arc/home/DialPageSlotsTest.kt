package com.metrolist.music.arc.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DialPageSlotsTest {
    private fun cells(slots: List<DialSlot>) = slots.flatMap { s -> (0 until s.span).flatMap { r -> (0 until s.span).map { c -> (s.row + r) to (s.col + c) } } }

    @Test
    fun `every page covers the whole grid exactly once`() {
        for (rows in 3..5) for (page in 0..2) {
            for (continueTile in listOf(true, false)) {
                val all = cells(dialPageSlots(page, columns = 3, rows = rows, big = true, panels = defaultDialPanels(3, rows), continueTile = continueTile))
                assertEquals(3 * rows, all.size)
                assertEquals(3 * rows, all.toSet().size)
            }
        }
    }

    @Test
    fun `by default page one opens with Continue and keeps three buttons, later pages are plain`() {
        val first = dialPageSlots(0, 3, 3, big = true, panels = defaultDialPanels(3, 3))
        assertEquals(DialSlot(0, 0, span = 2, panel = DialPanel.Continue), first.first())
        assertEquals(3, first.count { it.panel in setOf(DialPanel.Dice, DialPanel.Moods, DialPanel.Plus) })
        assertEquals(2, first.count { it.panel == null })
        assertEquals(9, dialPageSlots(1, 3, 3, big = true, panels = defaultDialPanels(3, 3)).count { it.panel == null && it.span == 1 })
    }

    @Test
    fun `without anything to continue the top item takes the big tile`() {
        val first = dialPageSlots(0, 3, 3, big = true, panels = defaultDialPanels(3, 3), continueTile = false)
        assertEquals(DialSlot(0, 0, span = 2), first.first())
        assertEquals(3, first.count { it.panel == null })
    }

    @Test
    fun `wide grids get a one-cell Continue tile`() {
        val all = dialPageSlots(0, 5, 2, big = false, panels = defaultDialPanels(5, 2))
        assertEquals(DialSlot(0, 0, panel = DialPanel.Continue), all.first())
        assertEquals(10, cells(all).toSet().size)
    }

    @Test
    fun `panels sit anywhere, and Continue shrinks when its 2x2 isn't clear`() {
        val panels = mapOf(DialCell(0, 1, 1) to DialPanel.Continue, DialCell(0, 2, 2) to DialPanel.Dice, DialCell(1, 0, 0) to DialPanel.Item("x"))
        val first = dialPageSlots(0, 3, 3, big = true, panels = panels)
        assertEquals(DialSlot(1, 1, span = 1, panel = DialPanel.Continue), first.first())
        assertEquals(9, cells(first).toSet().size)
        assertEquals(DialSlot(0, 0, panel = DialPanel.Item("x")), dialPageSlots(1, 3, 3, big = true, panels = panels).first())
    }

    @Test
    fun `a sized tile grows but shrinks around other panels`() {
        val panels = mapOf(DialCell(0, 0, 0) to DialPanel.Dice, DialCell(0, 2, 2) to DialPanel.Continue)
        val slots = dialPageSlots(0, 3, 3, big = true, panels = panels, sizes = mapOf(DialCell(0, 0, 0) to 3))
        assertEquals(DialSlot(0, 0, span = 2, panel = DialPanel.Dice), slots.first())
        assertEquals(DialSlot(2, 2, span = 1, panel = DialPanel.Continue), slots[1])
        assertEquals(9, cells(slots).toSet().size)
        assertEquals(mapOf(DialCell(0, 1, 2) to 2), parseDialSizes(encodeDialSizes(mapOf(DialCell(0, 1, 2) to 2)) + "\n0.0.0=9"))
    }

    @Test
    fun `the layout reads back what was written, dropping lines it doesn't know`() {
        val panels = mapOf(DialCell(0, 0, 0) to DialPanel.Continue, DialCell(0, 2, 1) to DialPanel.Mood("Relax", "ggMP"), DialCell(2, 1, 0) to DialPanel.Item("VLabc"), DialCell(0, 1, 2) to DialPanel.Radio)
        assertEquals(panels, parseDialPanels(encodeDialPanels(panels) + "\n0.1.1=futurepanel\nnonsense"))
        assertEquals(DialPanel.Mood("Sleep", null), parseDialPanels("0.0.0=mood:Sleep\t").values.single())
    }
}
