package com.metrolist.music.arc

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
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

/** Long names scroll this many times after they appear, then stay still. */
const val MARQUEE_LOOPS = 3

/**
 * Touches on a screen whose names stay still until touched (Home): each touch counts up, and the long names on
 * screen then scroll once. Null elsewhere, where names scroll [MARQUEE_LOOPS] times after they appear.
 */
val LocalMarqueeTouches = staticCompositionLocalOf<IntState?> { null }

// A touched name starts moving after this beat instead of the full rest.
private const val TOUCH_REST_MS = 300L

private fun marqueeCycle(loop: Float, speed: Float) = MARQUEE_PAUSE_MS + (loop / speed * 1000f).toLong()

/** How far a name of [loop] (its width plus the gap) has scrolled at [clockMs]: still for a pause, then one full loop, [MARQUEE_LOOPS] times. */
internal fun marqueeScroll(clockMs: Long, loop: Float, speed: Float, loops: Int = MARQUEE_LOOPS): Float {
    val cycle = marqueeCycle(loop, speed)
    if (clockMs >= cycle * loops) return 0f
    val t = clockMs % cycle
    return if (t < MARQUEE_PAUSE_MS) 0f else (t - MARQUEE_PAUSE_MS) / 1000f * speed
}

/** How long a name of [loop] still rests before it moves again at [clockMs], 0 while it's moving, or null once it has finished. */
private fun marqueeRest(clockMs: Long, loop: Float, speed: Float, loops: Int): Long? {
    val cycle = marqueeCycle(loop, speed)
    if (clockMs >= cycle * loops) return null
    val t = clockMs % cycle
    return if (t < MARQUEE_PAUSE_MS) MARQUEE_PAUSE_MS - t else 0L
}

/**
 * Drives the marquee clock with [onTime] until every name in [loops] has finished. While they all
 * rest it sleeps until the first one moves again instead of asking for a new frame each refresh,
 * which keeps the screen from redrawing 120 times a second over still text.
 */
internal suspend fun runMarqueeClock(loops: () -> List<Float>, speed: Float, times: Int = MARQUEE_LOOPS, fromMs: Long = 0L, onTime: (Long) -> Unit) {
    if (Visuals.still.value) return
    val start = withFrameMillis { it } - fromMs
    while (true) {
        val now = withFrameMillis { it } - start
        onTime(now)
        val rest = loops().mapNotNull { marqueeRest(now, it, speed, times) }.minOrNull() ?: return
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
 * pause, one slow loop, a few times over, soft edges while moving. [onLayout] gets the text layout and how far it has scrolled, for mapping taps.
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
    val touches = LocalMarqueeTouches.current

    if (overflows) {
        LaunchedEffect(text, textWidth, boxWidth) {
            if (touches == null) {
                runMarqueeClock({ listOf(loopPx.floatValue) }, speed) { scroll = marqueeScroll(it, loopPx.floatValue, speed) }
                return@LaunchedEffect
            }
            // Touches before this name appeared don't count, and ones during a pass are let go.
            var seen = touches.intValue
            snapshotFlow { touches.intValue }.collect { now ->
                if (now == seen) return@collect
                runMarqueeClock({ listOf(loopPx.floatValue) }, speed, times = 1, fromMs = MARQUEE_PAUSE_MS - TOUCH_REST_MS) {
                    scroll = marqueeScroll(it, loopPx.floatValue, speed, loops = 1)
                }
                seen = touches.intValue
            }
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
