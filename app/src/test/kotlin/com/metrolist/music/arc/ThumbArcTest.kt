package com.metrolist.music.arc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbArcTest {
    private val width = 1080f
    private val bottom = 2100f

    @Test
    fun rightHandedArcRisesTowardsTheRightEdge() {
        val arc = ThumbArc.default(width, bottom, mirrored = false)
        val foot = arc.point(0f)
        val higher = arc.point(0.5f)
        assertEquals(bottom, foot.y, 0.01f)
        assertTrue(foot.x in width * 0.4f..width * 0.65f)
        assertTrue(higher.x > foot.x && higher.y < foot.y)
    }

    @Test
    fun leftHandedArcIsAMirrorImage() {
        val right = ThumbArc.default(width, bottom, mirrored = false).point(0.4f)
        val left = ThumbArc.default(width, bottom, mirrored = true).point(0.4f)
        assertEquals(width - right.x, left.x, 0.01f)
        assertEquals(right.y, left.y, 0.01f)
    }

    @Test
    fun angleOfUndoesPointOnBothSides() {
        for (mirrored in listOf(false, true)) {
            val arc = ThumbArc.default(width, bottom, mirrored)
            assertEquals(0.7f, arc.angleOf(arc.point(0.7f, arc.radius * 0.9f)), 0.0001f)
            assertEquals(0.7f, arc.angleAtX(arc.point(0.7f).x)!!, 0.0001f)
        }
    }

    @Test
    fun angleAtXIsNullWhereTheCircleNeverReaches() {
        val arc = ThumbArc.default(width, bottom, mirrored = false)
        assertNull(arc.angleAtX(arc.pivot.x - arc.radius * 1.5f))
    }
}
