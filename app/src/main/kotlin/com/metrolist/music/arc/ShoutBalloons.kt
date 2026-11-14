package com.metrolist.music.arc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class Balloon(val start: Long, val duration: Int, val x: Float, val size: Float, val sway: Float, val phase: Float, val color: Color)

private val BalloonColors = listOf(
    Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759),
    Color(0xFF0A84FF), Color(0xFFAF52DE), Color(0xFFFF2D55),
)

/** Balloons that float up over the lyrics each time [trigger] changes to a new line index; draws nothing in between. */
@Composable
fun ShoutBalloons(trigger: Int, modifier: Modifier = Modifier) {
    val balloons = remember { mutableStateListOf<Balloon>() }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(trigger) {
        if (trigger < 0) return@LaunchedEffect
        val start = withFrameMillis { it }
        val rnd = Random(start)
        repeat(7) {
            balloons += Balloon(
                start = start + rnd.nextLong(0, 500),
                duration = rnd.nextInt(2600, 3600),
                x = 0.08f + 0.84f * rnd.nextFloat(),
                size = 36f + 22f * rnd.nextFloat(),
                sway = 0.6f + 0.8f * rnd.nextFloat(),
                phase = rnd.nextFloat() * 2 * PI.toFloat(),
                color = BalloonColors[rnd.nextInt(BalloonColors.size)],
            )
        }
    }
    LaunchedEffect(balloons.isNotEmpty()) {
        while (balloons.isNotEmpty()) {
            now = withFrameMillis { it }
            balloons.removeAll { now - it.start > it.duration }
        }
    }
    if (balloons.isEmpty()) return
    Canvas(modifier.fillMaxSize()) {
        balloons.forEach { b ->
            val t = (now - b.start) / b.duration.toFloat()
            if (t < 0f) return@forEach
            val w = b.size.dp.toPx()
            val rise = 0.65f * t + 0.35f * t * t
            val y = size.height + w * 1.2f - rise * (size.height + w * 3.6f)
            val swing = sin(t * b.sway * 2 * PI.toFloat() + b.phase)
            val x = b.x * size.width + swing * w * 0.5f
            rotate(degrees = swing * 8f, pivot = Offset(x, y)) { balloon(x, y, w, b.color) }
        }
    }
}

private fun DrawScope.balloon(x: Float, y: Float, w: Float, color: Color) {
    val h = w * 1.18f
    val bottom = y + h / 2
    val dark = lerp(color, Color.Black, 0.3f)
    drawPath(
        Path().apply {
            moveTo(x, bottom + w * 0.08f)
            cubicTo(x + w * 0.16f, bottom + w * 0.5f, x - w * 0.16f, bottom + w * 0.9f, x + w * 0.06f, bottom + w * 1.35f)
        },
        Color(0x99A0A0A0),
        style = Stroke(1.dp.toPx()),
    )
    drawPath(
        Path().apply {
            moveTo(x, bottom - 1f)
            lineTo(x - w * 0.07f, bottom + w * 0.09f)
            lineTo(x + w * 0.07f, bottom + w * 0.09f)
            close()
        },
        dark,
    )
    drawOval(
        Brush.radialGradient(
            listOf(lerp(color, Color.White, 0.35f), color, dark),
            center = Offset(x - w * 0.18f, y - h * 0.22f),
            radius = w * 0.8f,
        ),
        Offset(x - w / 2, y - h / 2),
        Size(w, h),
    )
    drawOval(Color.White.copy(alpha = 0.4f), Offset(x - w * 0.3f, y - h * 0.32f), Size(w * 0.16f, h * 0.24f))
}
