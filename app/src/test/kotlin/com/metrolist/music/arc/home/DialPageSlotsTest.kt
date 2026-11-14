package com.metrolist.music.arc.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DialPageSlotsTest {
    private fun cells(slots: List<DialSlot>) = slots.flatMap { s -> (0 until s.span).flatMap { r -> (0 until s.span).map { c -> (s.row + r) to (s.col + c) } } }

    @Test
    fun `every page covers the whole grid exactly once`() {
        for (rows in 3..5) for (page in 0..2) {
            val all = cells(dialPageSlots(page, columns = 3, rows = rows, big = true))
            assertEquals(3 * rows, all.size)
            assertEquals(3 * rows, all.toSet().size)
        }
    }

    @Test
    fun `the big tile comes first and the first page keeps three buttons`() {
        val first = dialPageSlots(0, 3, 3, big = true)
        assertEquals(DialSlot(0, 0, span = 2), first.first())
        assertEquals(3, first.count { it.button != null })
        assertEquals(3, first.count { it.button == null })
        assertEquals(6, dialPageSlots(1, 3, 3, big = true).count { it.button == null })
    }
}
