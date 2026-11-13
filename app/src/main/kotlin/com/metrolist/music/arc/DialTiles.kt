package com.metrolist.music.arc

import androidx.compose.ui.unit.Dp
import androidx.datastore.preferences.core.intPreferencesKey

val SpeedDialRowsKey = intPreferencesKey("arcSpeedDialRows")
val SpeedDialRowChoices = listOf(3, 4, 5)

/**
 * Side of a square speed dial cell (tile plus its gap) for [columns] across [width]: full width,
 * shrunk to fit [rows] into [maxHeight] above the mini player, but never below 80%.
 * Adapted from Dial (github.com/steeben/Dial, GPL-3.0).
 */
fun speedDialCellSize(width: Dp, columns: Int, rows: Int, maxHeight: Dp): Dp {
    val full = width / columns
    val fit = maxHeight / rows
    return minOf(full, maxOf(fit, full * 0.8f))
}
