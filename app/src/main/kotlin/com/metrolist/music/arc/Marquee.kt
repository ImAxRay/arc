package com.metrolist.music.arc

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal val MarqueeGap = 56.dp
internal val MarqueeSpeed = 30.dp
internal val MarqueeFade = 18.dp
internal const val MARQUEE_PAUSE_MS = 2500L

/** How far a name of [loop] (its width plus the gap) has scrolled at [clockMs]: still for a pause, then one full loop. */
internal fun marqueeScroll(clockMs: Long, loop: Float, speed: Float): Float {
    val cycle = MARQUEE_PAUSE_MS + (loop / speed * 1000f).toLong()
    val t = clockMs % cycle
    return if (t < MARQUEE_PAUSE_MS) 0f else (t - MARQUEE_PAUSE_MS) / 1000f * speed
}

/** How long a name of [loop] still rests before it moves again at [clockMs], or 0 while it's moving. */
internal fun marqueeRest(clockMs: Long, loop: Float, speed: Float): Long {
    val cycle = MARQUEE_PAUSE_MS + (loop / speed * 1000f).toLong()
    val t = clockMs % cycle
    return if (t < MARQUEE_PAUSE_MS) MARQUEE_PAUSE_MS - t else 0L
}

/**
 * Drives the marquee clock with [onTime]. While every name in [loops] is resting it sleeps until
 * the first one moves again instead of asking for a new frame each refresh, which keeps the screen
 * from redrawing 120 times a second over still text.
 */
internal suspend fun runMarqueeClock(loops: () -> List<Float>, speed: Float, onTime: (Long) -> Unit) {
    val start = withFrameMillis { it }
    while (true) {
        val now = withFrameMillis { it } - start
        onTime(now)
        val rest = loops().minOfOrNull { marqueeRest(now, it, speed) } ?: return
        if (rest > MIN_SLEEP_MS) delay(rest - FRAME_MS)
    }
}

private const val MIN_SLEEP_MS = 40L
private const val FRAME_MS = 16L

/**
 * How strongly the edge a name scrolls out of fades, 0 to 1. Nothing while it rests, so the first
 * letter reads in full, easing in as it starts moving and out again as the next copy arrives.
 */
internal fun leadingFade(scroll: Float, loop: Float, fade: Float): Float = (minOf(scroll, loop - scroll) / fade).coerceIn(0f, 1f)

/**
 * One line of text that scrolls when it doesn't fit, the same way as the names on the arc: a
 * pause, one slow loop, soft edges while moving. [onLayout] gets the text layout and how far it has scrolled, for mapping taps.
 */
@Composable
internal fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onLayout: (TextLayoutResult, () -> Float) -> Unit = { _, _ -> },
) {
    var scroll by remember { mutableFloatStateOf(0f) }
    var textWidth by remember { mutableIntStateOf(0) }
    var boxWidth by remember { mutableIntStateOf(0) }
    val overflows = textWidth > boxWidth && boxWidth > 0
    val loopPx = remember { mutableFloatStateOf(0f) }
    val speed = with(LocalDensity.current) { MarqueeSpeed.toPx() }
    val shift = { if (overflows) scroll else 0f }

    if (overflows) {
        LaunchedEffect(text, textWidth, boxWidth) {
            runMarqueeClock({ listOf(loopPx.floatValue) }, speed) { scroll = marqueeScroll(it, loopPx.floatValue, speed) }
        }
    }

    Layout(
        content = {
            Text(text, style = style, maxLines = 1, softWrap = false, onTextLayout = { layout -> onLayout(layout, shift) })
            if (overflows) Text(text, style = style, maxLines = 1, softWrap = false)
        },
        modifier =
            modifier
                .clipToBounds()
                .graphicsLayer { compositingStrategy = if (overflows) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
                .drawWithContent {
                    drawContent()
                    if (!overflows) return@drawWithContent
                    val fade = MarqueeFade.toPx()
                    val start = leadingFade(shift(), loopPx.floatValue, fade)
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 1f - start),
                            fade / size.width to Color.Black,
                            1f - fade / size.width to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val first = placeables.first()
        textWidth = first.width
        val width = constraints.constrainWidth(first.width)
        boxWidth = width
        val gap = MarqueeGap.roundToPx()
        loopPx.floatValue = (first.width + gap).toFloat()
        layout(width, first.height) {
            first.placeWithLayer(0, 0) { translationX = -shift() }
            placeables.getOrNull(1)?.placeWithLayer(first.width + gap, 0) { translationX = -shift() }
        }
    }
}
