package com.metrolist.music.arc

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualModeTest {
    @Test
    fun neverAskedOrDismissedIsNormal() {
        assertEquals(VisualMode.NORMAL, visualMode(mutablePreferencesOf()))
    }

    @Test
    fun choseSmootherIsSaver() {
        val prefs = mutablePreferencesOf(ArcHomeBackgroundKey to HomeBackground.OFF.name, ArcTiltCoversKey to false, ArcMotionCoversKey to false)
        assertEquals(VisualMode.SAVER, visualMode(prefs))
    }

    @Test
    fun prettierOrTiltByHandIsFull() {
        assertEquals(VisualMode.FULL, visualMode(mutablePreferencesOf(ArcTiltCoversKey to true)))
    }

    @Test
    fun storedModeWins() {
        assertEquals(VisualMode.NORMAL, visualMode(mutablePreferencesOf(ArcVisualModeKey to "NORMAL", ArcTiltCoversKey to true)))
    }

    @Test
    fun normalKeepsChosenBackgroundAndSaverTurnsItOff() {
        val prefs = mutablePreferencesOf(ArcHomeBackgroundKey to HomeBackground.BLADES.name, ArcTiltCoversKey to true)
        prefs.applyVisualMode(VisualMode.NORMAL)
        assertEquals(HomeBackground.BLADES.name, prefs[ArcHomeBackgroundKey])
        assertEquals(false, prefs[ArcTiltCoversKey])
        prefs.applyVisualMode(VisualMode.SAVER)
        assertEquals(HomeBackground.OFF.name, prefs[ArcHomeBackgroundKey])
        assertEquals(false, prefs[ArcPressGlowKey])
        prefs.applyVisualMode(VisualMode.FULL)
        assertEquals(HomeBackground.FIELD.name, prefs[ArcHomeBackgroundKey])
        assertEquals(true, prefs[ArcTiltCoversKey])
        assertEquals(VisualMode.FULL, visualMode(prefs))
    }
}
