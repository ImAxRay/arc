package com.metrolist.music.arc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.metrolist.music.utils.rememberEnumPreference

/** Home's background as it is set, held off in Saver; [paused] while something else draws its own copy. */
@Composable
fun HomeBackdropAsSet(paused: Boolean = false) {
    val style = rememberEnumPreference(ArcHomeBackgroundKey, HomeBackground.FIELD).value
    val colors = MaterialTheme.colorScheme
    val still = rememberVisualsStill()
    HomeBackdrop(if (paused || still) HomeBackground.OFF else style, colors.background, colors.primary)
}

/** The theme's three accent colours, so the row shows the palette in use. */
@Composable
fun ThemeSwatches() {
    val colors = MaterialTheme.colorScheme
    val swatches = listOf(colors.primary, colors.secondary, colors.tertiary)
    Canvas(Modifier.size(52.dp, 20.dp)) {
        val r = size.height / 2
        swatches.forEachIndexed { i, c -> drawCircle(c, r, Offset(r + i * (size.width - 2 * r) / 2, r)) }
    }
}

/** A speed dial page with [rows] rows of three tiles. */
@Composable
fun DialGlyph(rows: Int) {
    val tint = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(28.dp, 46.dp)) {
        val gap = 2.dp.toPx()
        val cell = minOf((size.width - 2 * gap) / 3, (size.height - (rows - 1) * gap) / rows)
        val top = (size.height - rows * cell - (rows - 1) * gap) / 2
        for (r in 0 until rows) for (c in 0 until 3) {
            drawRoundRect(tint, Offset(c * (cell + gap), top + r * (cell + gap)), Size(cell, cell), CornerRadius(1.5f.dp.toPx()))
        }
    }
}
