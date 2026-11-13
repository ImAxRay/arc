package com.metrolist.music.arc.vinyl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumSideTest {
    private val ids = listOf("other", "album", "album", "album", "next")
    private val durations = listOf(9_000L, 100_000L, 200_000L, 100_000L, 9_000L)

    @Test
    fun `finds only the neighbouring songs from the album`() {
        val side = checkNotNull(albumSideFor(ids, durations, 2))
        assertTrue(side.contains(1) && side.contains(3))
        assertTrue(!side.contains(0) && !side.contains(4))
    }

    @Test
    fun `band edges follow song lengths`() {
        val side = checkNotNull(albumSideFor(ids, durations, 1))
        assertEquals(listOf(0.25f, 0.75f), side.bandEdges)
    }

    @Test
    fun `progress and locate agree`() {
        val side = checkNotNull(albumSideFor(ids, durations, 2))
        assertEquals(0.5f, side.progress(2, 100_000L), 0.0001f)
        assertEquals(2 to 100_000L, side.locate(0.5f))
        assertEquals(3 to 100_000L, side.locate(1f))
    }

    @Test
    fun `no side for a lone song, a missing album or an unknown length`() {
        assertNull(albumSideFor(ids, durations, 0))
        assertNull(albumSideFor(listOf(null, null), listOf(1L, 1L), 0))
        assertNull(albumSideFor(listOf("a", "a"), listOf(1L, 0L), 0))
    }
}
