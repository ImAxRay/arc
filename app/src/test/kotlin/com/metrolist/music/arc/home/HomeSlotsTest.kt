package com.metrolist.music.arc.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSlotsTest {
    private val arrangeable = HomeModuleId.entries.size - 1

    @Test
    fun nothingSavedGivesTheDefaultsThenTheRestHidden() {
        val slots = parseHomeSlots("")
        assertEquals(DefaultHomeModules.map { it.key }, slots.filter { it.shown }.map { it.key })
        assertEquals(arrangeable, slots.size)
        assertEquals(false, slots.any { it.key == HomeModuleId.WRAPPED.key })
    }

    @Test
    fun savedOrderIsKeptUnknownKeysDroppedMissingAppendedHidden() {
        val slots = parseHomeSlots("moods,gone_module,-speed_dial,moods,wrapped")
        assertEquals(HomeSlot("moods", true), slots[0])
        assertEquals(HomeSlot("speed_dial", false), slots[1])
        assertEquals(listOf("moods"), slots.filter { it.shown }.map { it.key })
        assertEquals(arrangeable, slots.size)
    }

    @Test
    fun encodeRoundTrips() {
        val slots = parseHomeSlots("recents,-moods,speed_dial,yt:mixedforyou")
        assertEquals(slots, parseHomeSlots(slots.encode()))
    }

    @Test
    fun youTubeRowsKeepTheirPlaceEvenWhenTodaysPageLacksThemAndNewOnesStartHidden() {
        val slots = parseHomeSlots("yt:hiphop,speed_dial,-yt:longlistens", rows = listOf("yt:mixedforyou", "yt:hiphop"))
        assertEquals(HomeSlot("yt:hiphop", true), slots[0])
        assertEquals(HomeSlot("yt:longlistens", false), slots[2])
        assertEquals(HomeSlot("yt:mixedforyou", false), slots.last())
        // hidden rows aren't written, so the saved list doesn't grow with every row YouTube ever showed
        assertEquals("yt:hiphop,speed_dial", slots.encode().split(',').take(2).joinToString(","))
        assertEquals(false, slots.encode().contains("longlistens") || slots.encode().contains("mixedforyou"))
    }

    @Test
    fun aTitleWithACommaMakesAKeyWithout() {
        val section = com.metrolist.innertube.pages.HomePage.Section("Fresh finds, old favorites", null, null, null, emptyList())
        assertEquals("yt:freshfindsoldfavorites", section.rowKey())
        assertEquals(listOf(HomeSlot(section.rowKey(), true)), parseHomeSlots(listOf(HomeSlot(section.rowKey(), true)).encode()).take(1))
    }

    @Test
    fun todaysPageShowsMoodMixesThenTheRowsInOrderAndNothingTwice() {
        val rows = listOf("yt:listenagain", "yt:mixedforyou")
        val slots = parseHomeSlots(todaysPagePreset(rows) + ",yt:mixedforyou", rows)
        assertEquals(listOf("youtube_home") + rows, slots.filter { it.shown }.map { it.key })
        assertEquals(slots.size, slots.distinctBy { it.key }.size)
    }
}
