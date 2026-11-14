package com.metrolist.music.arc

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MAX_DRAWN_STEPS = 40

/**
 * Arc's slider: the same line and end dot as the player and Home. It swells under the thumb,
 * ticks on every step, and a tap jumps straight to where it lands. Takes the same arguments as
 * Material's Slider so it can stand in for it.
 */
@Composable
fun ArcSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    var held by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val thickness by animateDpAsState(if (held) 14.dp else 6.dp, ArcMotion.springy(), label = "arcSliderThickness")
    val start = valueRange.start
    val span = (valueRange.endInclusive - start).takeIf { it > 0f } ?: 1f
    val tick = if (steps > 20) HapticFeedbackType.SegmentFrequentTick else HapticFeedbackType.SegmentTick

    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        held = true
        delay(140)
        held = false
    }

    fun valueAt(x: Float): Float {
        if (widthPx <= 0f) return current
        val fraction = ((if (rtl) widthPx - x else x) / widthPx).coerceIn(0f, 1f)
        if (steps <= 0) return start + fraction * span
        val parts = steps + 1
        return start + span * (fraction * parts).roundToInt() / parts
    }

    fun moveTo(x: Float) {
        val next = valueAt(x)
        if (next == current) return
        if (steps > 0 || next == start || next == valueRange.endInclusive) haptics.performHapticFeedback(tick)
        onChange(next)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
                if (enabled) {
                    setProgress { target ->
                        onChange(target.coerceIn(valueRange))
                        onFinished?.invoke()
                        true
                    }
                }
            }.pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = {
                    taps++
                    moveTo(it.x)
                    onFinished?.invoke()
                })
            }.draggable(
                state = rememberDraggableState { delta ->
                    dragX += delta
                    moveTo(dragX)
                },
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStarted = { at ->
                    held = true
                    dragX = at.x
                    moveTo(at.x)
                },
                onDragStopped = {
                    held = false
                    onFinished?.invoke()
                },
            ).drawBehind {
                val stroke = thickness.toPx()
                val cy = size.height / 2f
                val left = stroke / 2f
                val right = size.width - stroke / 2f
                fun xOf(fraction: Float) = if (rtl) right - fraction * (right - left) else left + fraction * (right - left)
                val fraction = ((value - start) / span).coerceIn(0f, 1f)
                val fillX = xOf(fraction)
                drawLine(colors.onSurface.copy(alpha = 0.2f), Offset(left, cy), Offset(right, cy), stroke, StrokeCap.Round)
                drawLine(colors.primary, Offset(xOf(0f), cy), Offset(fillX, cy), stroke, StrokeCap.Round)
                if (steps in 1..MAX_DRAWN_STEPS) {
                    val mark = 1.dp.toPx()
                    for (i in 1..steps) {
                        val at = i.toFloat() / (steps + 1)
                        val color = if (at <= fraction) colors.onPrimary.copy(alpha = 0.45f) else colors.onSurface.copy(alpha = 0.35f)
                        drawCircle(color, mark, Offset(xOf(at), cy))
                    }
                }
                drawCircle(colors.onPrimary, (stroke * 0.17f).coerceAtLeast(1.5.dp.toPx()), Offset(fillX, cy))
            },
    )
}
