package com.metrolist.music.viewmodels

import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSpeedDialTest {
    @Test
    fun `home recommendations fill sparse speed dial without replacing pinned items`() {
        val pinned = song("pinned")
        val home = song("home")

        val result =
            buildSpeedDialItems(
                pinned = listOf(pinned),
                keepListening = emptyList(),
                quickPicks = emptyList(),
                home = listOf(pinned.copy(title = "duplicate"), home),
            )

        assertEquals(listOf(pinned, home), result)
    }

    @Test
    fun `a pinned bundled song sits in the middle tile`() {
        val bundled = song("BUNDLED_x")
        val others = (1..6).map { song("s$it") }

        val result = buildSpeedDialItems(listOf(others[0], bundled), emptyList(), others.drop(1), emptyList())

        assertEquals(bundled, result[4])
        assertEquals(others, result - bundled)
    }

    private fun song(id: String) =
        SongItem(
            id = id,
            title = id,
            artists = emptyList(),
            thumbnail = "",
        )
}
