package com.metrolist.music.arc.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSlotsTest {
    @Test
    fun nothingSavedGivesTheDefaultsThenTheRestHidden() {
        val slots = parseHomeSlots("")
        assertEquals(DefaultHomeModules, slots.filter { it.shown }.map { it.id })
        assertEquals(HomeModuleId.entries.size - 1, slots.size)
        assertEquals(false, slots.any { it.id == HomeModuleId.WRAPPED })
    }

    @Test
    fun savedOrderIsKeptUnknownKeysDroppedMissingAppendedHidden() {
        val slots = parseHomeSlots("moods,gone_module,-speed_dial,moods,wrapped")
        assertEquals(HomeSlot(HomeModuleId.MOODS, true), slots[0])
        assertEquals(HomeSlot(HomeModuleId.SPEED_DIAL, false), slots[1])
        assertEquals(listOf(HomeModuleId.MOODS), slots.filter { it.shown }.map { it.id })
        assertEquals(HomeModuleId.entries.size - 1, slots.size)
    }

    @Test
    fun encodeRoundTrips() {
        val slots = parseHomeSlots("recents,-moods,speed_dial")
        assertEquals(slots, parseHomeSlots(slots.encode()))
    }
}
