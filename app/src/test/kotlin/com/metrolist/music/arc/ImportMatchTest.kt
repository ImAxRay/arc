package com.metrolist.music.arc

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportMatchTest {
    private fun song(id: String, title: String, vararg artists: String, seconds: Int? = null) =
        SongItem(id = id, title = title, artists = artists.map { Artist(it, null) }, duration = seconds, thumbnail = "")

    private fun best(title: String, artists: List<String>, vararg hits: SongItem, seconds: Int = -1) =
        ImportMatch.best(title, artists, seconds, hits.toList())?.id

    @Test
    fun skipsWrongTitleAndArtistBeforeTheRecording() {
        val hits = arrayOf(song("live", "Song live", "Artist"), song("cover", "Song", "Cover band"), song("exact", "Song!", "Artist"))
        assertEquals("exact", best("Song", listOf("Artist"), *hits))
    }

    @Test
    fun durationPicksBetweenSameTitles() {
        assertEquals("b", best("Song", listOf("Artist"), song("a", "Song", "Artist", seconds = 240), song("b", "Song", "Artist", seconds = 181), seconds = 180))
    }

    @Test
    fun featuredCreditsInTitlesStillMatch() {
        val featured = song("featured", "Starboy (feat. Daft Punk)", "The Weeknd")
        val remix = song("remix", "Starboy (Remix)", "The Weeknd")
        assertEquals("featured", best("Starboy", listOf("The Weeknd", "Daft Punk"), remix, featured))
        assertEquals("featured", best("Starboy ft. Daft Punk", listOf("The Weeknd"), remix, song("featured", "Starboy", "The Weeknd")))
        assertEquals("featured", best("Starboy", listOf("The Weeknd"), remix, song("featured", "Starboy [with Daft Punk]", "The Weeknd")))
    }

    @Test
    fun remasterTailsMatchThePlainTitle() {
        assertEquals("plain", best("Song - Remastered 2011", listOf("Artist"), song("other", "Other", "Artist"), song("plain", "Song", "Artist")))
    }

    @Test
    fun artistNamesMatchWholeCreditsOnly() {
        val queen = song("queen", "Song", "Queen")
        val qotsa = song("qotsa", "Song", "Queens of the Stone Age")
        assertEquals("qotsa", best("Song", listOf("Queens of the Stone Age"), queen, qotsa))
        assertEquals("tyler", best("See You Again", listOf("Tyler", "The Creator", "Kali Uchis"), song("mhd", "See You Again", "MHD"), song("tyler", "See You Again (feat. Kali Uchis)", "Tyler, The Creator")))
        listOf("feat.", "featuring", "ft.", "and", "with", "vs.", "x", "+", "/", ";", "&").forEach { separator ->
            assertEquals(separator, "one", best("Song", listOf("Artist one $separator Artist two"), song("other", "Song", "Somebody"), song("one", "Song", "Artist one")))
        }
    }

    @Test
    fun fallsBackToTheArtistThenTheFirstHit() {
        assertEquals("artist", best("Song", listOf("Artist"), song("first", "Else", "Other"), song("artist", "Else", "Artist")))
        assertEquals("first", best("Song", listOf("Artist"), song("first", "Else", "Other")))
        assertEquals(null, best("Song", listOf("Artist")))
    }
}
