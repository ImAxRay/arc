package com.metrolist.music.arc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class UsageBookTest {
    private val saturdayEvening = ZonedDateTime.of(2026, 11, 14, 20, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun countsActionsTimeOfDayAndWeekend() {
        val book = UsageBook()
        book.record("album:a", "A", UsageAction.PLAY, saturdayEvening)
        book.record("album:a", null, UsageAction.OPEN, saturdayEvening.withHour(3))
        val entry = book.entries.getValue("album:a")
        assertEquals("A", entry.title)
        assertEquals(1, entry.plays)
        assertEquals(1, entry.opens)
        assertEquals(listOf(1, 0, 0, 1), entry.hours)
        assertEquals(2, entry.weekend)
        assertEquals(1.5, entry.score, 0.01)
    }

    @Test
    fun scoreHalvesEveryTwoWeeks() {
        val book = UsageBook()
        book.record("mix:x", null, UsageAction.PLAY, saturdayEvening)
        val later = saturdayEvening.plusDays(14).toInstant().toEpochMilli()
        assertEquals(0.5, UsageBook.scoreAt(book.entries.getValue("mix:x"), later), 0.001)
    }

    @Test
    fun dropsTheWeakestWhenFullButNeverTheNewOne() {
        val book = UsageBook()
        repeat(UsageBook.MAX_ENTRIES) { book.record("song:$it", null, UsageAction.PLAY, saturdayEvening) }
        book.record("song:0", null, UsageAction.PLAY, saturdayEvening)
        book.record("song:new", null, UsageAction.SKIP, saturdayEvening)
        assertEquals(UsageBook.MAX_ENTRIES, book.entries.size)
        assertTrue("song:new" in book.entries)
        assertTrue("song:0" in book.entries)
        assertFalse(book.entries.keys.containsAll((0 until UsageBook.MAX_ENTRIES).map { "song:$it" }))
    }

    @Test
    fun fourthVersionPartShowsAsLetter() {
        assertEquals("0.9.4a", displayVersion("0.9.4.1"))
        assertEquals("0.9.4b", displayVersion("0.9.4.2+abc123"))
        assertEquals("0.9.4", displayVersion("0.9.4"))
    }

    @Test
    fun listKeysGroupRadiosByTuning() {
        assertEquals("radio:RDATiXv", Usage.listKey("RDATiXvabc123"))
        assertEquals("mix:RDCLAK5", Usage.listKey("RDCLAK5"))
        assertEquals("playlist:PL1", Usage.listKey("VLPL1"))
    }
}
