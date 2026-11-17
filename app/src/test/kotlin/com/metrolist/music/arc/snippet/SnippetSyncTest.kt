package com.metrolist.music.arc.snippet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class SnippetSyncTest {
    private val rate = 48_000

    private fun beats(seconds: Int, seed: Int): ShortArray {
        val random = Random(seed)
        val mono = ShortArray(rate * seconds)
        var at = 0
        while (at < mono.size) {
            for (i in 0 until minOf(2_000, mono.size - at)) mono[at + i] = ((random.nextInt(-12_000, 12_000)) * (1 - i / 2_000.0)).toInt().toShort()
            at += random.nextInt(rate / 5, rate / 2)
        }
        return mono
    }

    private fun take(song: ShortArray, voice: ShortArray) = Snippet.Take(
        song = ShortArray(song.size * 2) { song[it / 2] },
        rate = rate,
        voice = voice,
        voiceRate = rate,
        title = "",
        artist = "",
        cover = null,
        bluetooth = false,
        voiceLevel = 1f,
    )

    @Test
    fun findsHowLateTheMicHeardTheSong() {
        val song = beats(20, 1)
        val delay = rate * 180 / 1000
        val noise = Random(2)
        val mic = ShortArray(song.size) { i ->
            val heard = if (i >= delay) song[i - delay] / 3 else 0
            (heard + noise.nextInt(-800, 800)).toShort()
        }
        val found = take(song, mic).findSync()!!
        assertEquals(180.0, found.toDouble(), 10.0)
    }

    @Test
    fun givesUpWhenTheMicHeardSomethingElse() {
        assertNull(take(beats(20, 1), beats(20, 7)).findSync())
    }

    @Test
    fun staysOnTheNearBeatWhenTheSongRepeats() {
        val song = ShortArray(rate * 20)
        var at = 0
        while (at < song.size) {
            for (i in 0 until minOf(2_000, song.size - at)) song[at + i] = (10_000 * (1 - i / 2_000.0)).toInt().toShort()
            at += rate * 420 / 1000
        }
        val delay = rate * 30 / 1000
        val mic = ShortArray(song.size) { i -> if (i >= delay) (song[i - delay] / 2).toShort() else 0 }
        val found = take(song, mic).findSync()!!
        assertEquals(30.0, found.toDouble(), 10.0)
    }

    @Test
    fun givesUpWhenEveryNearMatchScoresBelowZero() {
        val song = ShortArray(rate * 20)
        var at = 0
        while (at < song.size) {
            for (i in 0 until minOf(2_000, song.size - at)) song[at + i] = (10_000 * (1 - i / 2_000.0)).toInt().toShort()
            at += rate * 420 / 1000
        }
        // the mic's beats fall halfway between the song's, so near the expected lag nothing lines up
        val delay = rate * 210 / 1000
        val mic = ShortArray(song.size) { i -> if (i >= delay) (song[i - delay] / 2).toShort() else 0 }
        assertNull(take(song, mic).findSync(expectedMs = 0, reachMs = 50))
    }
}
