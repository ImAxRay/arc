package com.metrolist.music.arc

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSettingsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun keepsYoungEntriesInOrder() {
        assertEquals(listOf("a" to now - day, "b" to now - 2 * day), freshRecent("a@${now - day},b@${now - 2 * day}", now))
    }

    @Test
    fun dropsEntriesOlderThanThreeDays() {
        assertEquals(listOf("a" to now), freshRecent("a@$now,b@${now - 3 * day}", now))
    }

    @Test
    fun dropsOldKeysOnlyFormat() {
        assertEquals(emptyList<Pair<String, Long>>(), freshRecent("a,b", now))
        assertEquals(emptyList<Pair<String, Long>>(), freshRecent("", now))
    }
}
