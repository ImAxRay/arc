package com.metrolist.music.viewmodels

import com.metrolist.innertube.models.Artist
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

    @Test
    fun `a song and its music video take one tile, and videos are left out`() {
        val track = song("a1", "Blue World", "Mac Miller")
        val video = song("v1", "Mac Miller - Blue World (Official Video)", "Mac Miller", "MUSIC_VIDEO_TYPE_OMV")
        val otherVideo = song("v2", "Something Else", "Mac Miller", "MUSIC_VIDEO_TYPE_UGC")
        val sameUnderAnotherId = song("a2", "Blue World [Remastered]", "Mac Miller")

        val result = buildSpeedDialItems(emptyList(), listOf(track), listOf(video, otherVideo), listOf(sameUnderAnotherId))

        assertEquals(listOf(track), result)
    }

    @Test
    fun `the library tops speed dial up after everything else`() {
        val home = song("home")
        val library = (1..3).map { song("lib$it") }

        val result = buildSpeedDialItems(emptyList(), emptyList(), emptyList(), listOf(home), library)

        assertEquals(listOf(home) + library, result)
    }

    @Test
    fun `tiles keep their items when the sources reshuffle, and new items only fill empty slots`() {
        val shown = (1..4).map { song("s$it") }
        val fresh = listOf(song("s4"), song("n1"), song("s2"), song("n2"), song("s1"), song("n3"))

        val result = keepDialInPlace(shown, fresh, emptySet(), emptySet())

        assertEquals(shown + song("n1") + song("n2"), result)
    }

    @Test
    fun `an unpinned item hands its tile on, a new pin joins the pins`() {
        val shown = listOf(song("p1"), song("p2"), song("s1"), song("s2"))
        val fresh = listOf(song("p1"), song("p3"), song("s2"), song("s1"), song("n1"))

        val result = keepDialInPlace(shown, fresh, pinnedBefore = setOf("p1", "p2"), pinnedNow = setOf("p1", "p3"))

        assertEquals(listOf("p1", "p3", "n1", "s1", "s2"), result.map { it.id })
    }

    @Test
    fun `a cached dial comes back in order and stays put while first sources arrive`() {
        val cached = listOf(song("a", artist = "A"), song("b"), song("c"))
        val restored = decodeDialCache(encodeDialCache(cached))

        assertEquals(cached.map { it.id to it.title }, restored.map { it.id to it.title })
        assertEquals(restored, keepDialInPlace(restored, listOf(song("x")), emptySet(), emptySet()).take(3))
        assertEquals(emptyList<Any>(), decodeDialCache("not json"))
    }

    private fun song(
        id: String,
        title: String = id,
        artist: String? = null,
        videoType: String? = null,
    ) = SongItem(
        id = id,
        title = title,
        artists = listOfNotNull(artist?.let { Artist(name = it, id = null) }),
        musicVideoType = videoType,
        thumbnail = "",
    )
}
