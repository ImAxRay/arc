package com.metrolist.music.arc.vinyl

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class VinylDrawingTest {
    private val center = Offset(800f, 1800f)
    private val radius = 700f

    @Test
    fun `needle starts at the outer edge and ends near the label`() {
        assertEquals(ARM_OUTER, needleReach(0f), 0.0001f)
        assertEquals(ARM_INNER, needleReach(1f), 0.0001f)
    }

    @Test
    fun `needle reach ignores progress outside the song`() {
        assertEquals(ARM_OUTER, needleReach(-1f), 0.0001f)
        assertEquals(ARM_INNER, needleReach(2f), 0.0001f)
    }

    @Test
    fun `needle tip sits at the reach for the song's progress`() {
        for (progress in listOf(0f, 0.25f, 0.5f, 1f)) {
            val tip = tonearmGeometry(center, radius, 1080f, progress).tip
            assertEquals(radius * needleReach(progress), (tip - center).getDistance(), 0.5f)
        }
    }

    @Test
    fun `touching the arm's middle counts as touching the arm`() {
        val distance = distanceToSegment(Offset(50f, 10f), Offset(0f, 0f), Offset(100f, 0f))
        assertEquals(10f, distance, 0.0001f)
    }
}
