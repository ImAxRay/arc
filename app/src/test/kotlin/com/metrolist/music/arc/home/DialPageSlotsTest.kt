package com.metrolist.music.arc.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DialPageSlotsTest {
    private fun cells(slots: List<DialSlot>) = slots.flatMap { s -> (0 until s.span).flatMap { r -> (0 until s.span).map { c -> (s.row + r) to (s.col + c) } } }

    @Test
    fun `every page covers the whole grid exactly once`() {
        for (rows in 3..5) for (page in 0..2) {
            for (continueTile in listOf(true, false)) {
                val all = cells(dialPageSlots(page, columns = 3, rows = rows, big = true, continueTile = continueTile))
                assertEquals(3 * rows, all.size)
                assertEquals(3 * rows, all.toSet().size)
            }
        }
    }

    @Test
    fun `page one opens with Continue and keeps three buttons, later pages are plain`() {
        val first = dialPageSlots(0, 3, 3, big = true)
        assertEquals(DialSlot(0, 0, span = 2, button = DialButton.CONTINUE), first.first())
        assertEquals(3, first.count { it.button in setOf(DialButton.RANDOM, DialButton.MOODS, DialButton.NEW_PLAYLIST) })
        assertEquals(2, first.count { it.button == null })
        assertEquals(9, dialPageSlots(1, 3, 3, big = true).count { it.button == null && it.span == 1 })
    }

    @Test
    fun `without anything to continue the top item takes the big tile`() {
        val first = dialPageSlots(0, 3, 3, big = true, continueTile = false)
        assertEquals(DialSlot(0, 0, span = 2), first.first())
        assertEquals(3, first.count { it.button == null })
    }

    @Test
    fun `wide grids get a one-cell Continue tile`() {
        val all = dialPageSlots(0, 5, 2, big = false)
        assertEquals(DialSlot(0, 0, button = DialButton.CONTINUE), all.first())
        assertEquals(10, cells(all).toSet().size)
    }
}
