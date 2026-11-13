package com.metrolist.music.arc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PopPhysicsTest {
    private val barY = 0f
    private val barRight = 300f
    private val floor = 1_000f

    private fun PopSim.run(seconds: Float): Int {
        var impacts = 0
        repeat((seconds * 60f).toInt()) { impacts += step(1f / 60f, 0f, barRight, barY, floor) }
        return impacts
    }

    @Test
    fun `every pop kind finishes on its own`() {
        PopKind.entries.forEach { kind ->
            val sim = PopSim(unit = 1f)
            sim.pop(kind, 280f, barY, Random(1))
            assertTrue(sim.isAlive)
            sim.run(seconds = 8f)
            assertFalse("$kind should be gone", sim.isAlive)
        }
    }

    @Test
    fun `a heavy drop thrown up lands on the bar and bounces`() {
        val sim = PopSim(unit = 1f)
        sim.pop(PopKind.TENNIS, 280f, barY, Random(1))
        assertTrue(sim.run(seconds = 3f) > 1)
    }

    @Test
    fun `a drip passes through the bar`() {
        val sim = PopSim(unit = 1f)
        sim.pop(PopKind.DRIP, 150f, barY, Random(1))
        assertEquals(0, sim.run(seconds = 4f))
        assertFalse(sim.isAlive)
    }

    @Test
    fun `a splash leaves a puddle that lingers before fading`() {
        val sim = PopSim(unit = 1f)
        sim.pop(PopKind.SPLASH, 150f, barY, Random(1))
        sim.run(seconds = 1f)
        assertTrue(sim.puddles.isNotEmpty())
        sim.run(seconds = 4f)
        assertTrue(sim.puddles.isEmpty())
    }

    @Test
    fun `a comet keeps a trail`() {
        val sim = PopSim(unit = 1f)
        sim.pop(PopKind.COMET, 280f, barY, Random(1))
        sim.run(seconds = 0.5f)
        assertEquals(Droplet.TRAIL_POINTS, sim.droplets.single().trailCount)
    }

    @Test
    fun `a song always gets the same pop`() {
        assertEquals(PopKind.forSeed(1234), PopKind.forSeed(1234))
        assertEquals(PopKind.entries.toSet(), (0..200).map(PopKind::forSeed).toSet())
    }

    @Test
    fun `taking turns spreads artists out`() {
        assertEquals(listOf("a1", "b1", "a2", "a3"), takeTurns(listOf(listOf("a1", "a2", "a3"), listOf("b1"))))
        assertEquals(emptyList<String>(), takeTurns(emptyList<List<String>>()))
    }
}

class ArtistSongsTest {
    @Test
    fun `reads play counts in english and russian`() {
        assertEquals(2_300_000L, parsePlayCount("2.3M plays"))
        assertEquals(2_300_000L, parsePlayCount("2,3 млн прослушиваний"))
        assertEquals(381_000L, parsePlayCount("381K plays"))
        assertEquals(950L, parsePlayCount("950 plays"))
        assertEquals(null, parsePlayCount("no plays"))
        assertEquals(null, parsePlayCount(null))
    }

    @Test
    fun `splits a stat into number and label`() {
        assertEquals("12.3M" to "monthly listeners", splitStat("12.3M monthly listeners"))
        assertEquals("12,3 млн" to "слушателей", splitStat("12,3 млн слушателей"))
        assertEquals("500" to "subscribers", splitStat("500 subscribers"))
    }
}

class ReleaseOrderTest {
    private val today = java.time.LocalDate.of(2026, 11, 5)

    @Test
    fun `reads video dates in english and russian`() {
        assertEquals(java.time.LocalDate.of(2026, 10, 3), parseVideoDate("Oct 3, 2026"))
        assertEquals(java.time.LocalDate.of(2026, 10, 3), parseVideoDate("Premiered 3 Oct 2026"))
        assertEquals(java.time.LocalDate.of(2026, 10, 3), parseVideoDate("3 окт. 2026 г."))
        assertEquals(null, parseVideoDate("yesterday"))
        assertEquals(null, parseVideoDate(null))
    }

    @Test
    fun `your recent releases come first, newest on top, then the feed, then older ones`() {
        val dates =
            mapOf(
                "older" to today.minusDays(200),
                "week" to today.minusDays(7),
                "yesterday" to today.minusDays(1),
            )
        val ordered = orderReleases(listOf("older", "week", "unknown", "yesterday"), listOf("feed"), { dates[it] }, today)
        assertEquals(listOf("yesterday", "week", "feed", "older", "unknown"), ordered)
    }
}

class RingAndReleaseTest {
    @Test
    fun `release kind comes from track count, then from youtube's word`() {
        assertEquals(ReleaseKind.SINGLE, releaseKind(1, null))
        assertEquals(ReleaseKind.EP, releaseKind(5, "Album"))
        assertEquals(ReleaseKind.ALBUM, releaseKind(12, null))
        assertEquals(ReleaseKind.SINGLE, releaseKind(null, "Сингл"))
        assertEquals(null, releaseKind(null, "LIDA"))
    }

    @Test
    fun `the shotgun fires to the right`() {
        val sim = PopSim(unit = 1f)
        sim.pop(PopKind.SHOTGUN, 0f, 0f, kotlin.random.Random(1))
        assertTrue(sim.droplets.count { it.vx > 0f } >= 20)
    }
}
