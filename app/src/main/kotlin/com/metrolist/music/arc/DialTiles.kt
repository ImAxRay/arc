package com.metrolist.music.arc

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * A bevelled rim that catches light along the top, fades down the sides and returns faintly at the
 * foot, with a soft sheen just inside. Goes after the clip, before the tile's background.
 * Adapted from Dial's tile edge (github.com/steeben/Dial, GPL-3.0).
 */
fun Modifier.dialRim(shape: Shape): Modifier =
    drawWithCache {
        val rimWidth = 1.5.dp.toPx()
        val outline = shape.createOutline(size.copy(width = size.width - rimWidth, height = size.height - rimWidth), layoutDirection, this)
        val rim = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.55f),
            0.18f to Color.White.copy(alpha = 0.22f),
            0.5f to Color.White.copy(alpha = 0.07f),
            0.85f to Color.White.copy(alpha = 0.05f),
            1f to Color.White.copy(alpha = 0.16f),
        )
        val sheen = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.12f to Color.Transparent)
        onDrawWithContent {
            drawContent()
            drawRect(sheen)
            translate(rimWidth / 2f, rimWidth / 2f) { drawOutline(outline, rim, style = Stroke(rimWidth)) }
        }
    }
