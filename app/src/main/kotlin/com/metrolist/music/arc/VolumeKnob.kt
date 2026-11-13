package com.metrolist.music.arc

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A full turn of the wrist is about 270°, so that's the whole range from silent to full. */
private const val FULL_RANGE_DEGREES = 270f
private const val TICKS = 40
private const val HAPTIC_STEPS = 50

@Stable
class VolumeKnobState {
    var active by mutableStateOf(false)
        internal set
    var volume by mutableFloatStateOf(0f)
        internal set
}

/**
 * Two fingers twisted on the screen turn the volume like a knob: clockwise is louder. One finger is
 * left alone for everything else; once a second finger lands, the twist owns the gesture.
 */
fun Modifier.twistVolume(
    state: VolumeKnobState,
    haptics: HapticFeedback,
    enabled: () -> Boolean,
    read: () -> Float,
    write: (Float) -> Unit,
): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var lastAngle: Float? = null
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size < 2 || !enabled()) {
                        lastAngle = null
                        continue
                    }
                    val between = pressed[1].position - pressed[0].position
                    val angle = atan2(between.y, between.x) * 180f / PI.toFloat()
                    val previous = lastAngle
                    lastAngle = angle
                    if (previous == null) {
                        state.volume = read()
                        state.active = true
                    } else {
                        var turn = angle - previous
                        if (turn > 180f) turn -= 360f
                        if (turn < -180f) turn += 360f
                        val before = state.volume
                        state.volume = (before + turn / FULL_RANGE_DEGREES).coerceIn(0f, 1f)
                        if ((before * HAPTIC_STEPS).toInt() != (state.volume * HAPTIC_STEPS).toInt()) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                        write(state.volume)
                    }
                    event.changes.forEach { it.consume() }
                }
            } finally {
                state.active = false
            }
        }
    }

/** The knob while it turns: a ring of ticks lit up to the volume, the number in thin type, and where the sound plays. */
@Composable
fun VolumeKnobOverlay(state: VolumeKnobState, output: String, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(if (state.active) 1f else 0f, ArcMotion.snappy(), label = "volumeKnob")
    if (shown < 0.01f) return
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.graphicsLayer { alpha = shown }.background(Color.Black.copy(alpha = 0.55f)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(output, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(232.dp).graphicsLayer { scaleX = 0.9f + 0.1f * shown; scaleY = scaleX }) {
                Canvas(Modifier.size(232.dp)) {
                    val outer = size.minDimension / 2
                    val inner = outer - 16.dp.toPx()
                    val lit = (state.volume * TICKS).roundToInt()
                    for (tick in 0 until TICKS) {
                        // From the top, clockwise, like the knob's own scale.
                        val angle = (tick.toFloat() / TICKS * 360f - 90f) * PI.toFloat() / 180f
                        val direction = Offset(cos(angle), sin(angle))
                        drawLine(
                            Color.White.copy(alpha = if (tick < lit) 1f else 0.25f),
                            center + direction * inner,
                            center + direction * outer,
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                Text((state.volume * 100).roundToInt().toString(), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Thin)
            }
        }
    }
}
