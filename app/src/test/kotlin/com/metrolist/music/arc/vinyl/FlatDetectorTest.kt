package com.metrolist.music.arc.vinyl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlatDetectorTest {
    @Test
    fun `counts as flat only after staying flat`() {
        val detector = FlatDetector(settleMs = 1_000L)
        assertFalse(detector.update(9.8f, 0L))
        assertFalse(detector.update(9.8f, 900L))
        assertTrue(detector.update(9.8f, 1_000L))
    }

    @Test
    fun `passing through flat in the hand does not count`() {
        val detector = FlatDetector(settleMs = 1_000L)
        detector.update(9.8f, 0L)
        detector.update(7f, 500L)
        assertFalse(detector.update(9.8f, 1_200L))
    }

    @Test
    fun `a small wobble does not leave but picking it up does`() {
        val detector = FlatDetector(settleMs = 0L)
        assertTrue(detector.update(9.8f, 0L))
        assertTrue(detector.update(9.3f, 100L))
        assertFalse(detector.update(8f, 200L))
    }

    @Test
    fun `face down is never flat`() {
        val detector = FlatDetector(settleMs = 0L)
        assertFalse(detector.update(-9.8f, 0L))
    }
}
