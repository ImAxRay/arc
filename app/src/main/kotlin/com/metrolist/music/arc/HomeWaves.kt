package com.metrolist.music.arc

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Tuned on the laptop demo (demo/home-background.html) with the user.
private val CellSize = 3.dp
private const val LINES = 3
private const val ANGLE = 19.0
private const val SPREAD = 52.0
// Slow enough that the motion is only noticed by looking away and back.
private const val SPEED = 0.006
private const val WOBBLE = 0.024
private const val WOBBLE_FREQ = 0.9
private const val WOBBLE_SPEED = 0.012
private const val WIDTH = 0.063
private const val CHROMA = 0.04
private const val SLATS = 0.24
private const val TINT_MIX = 0.49
private const val STRENGTH = 0.09
// Where the lines are is worked out a few times a second; in between, each pixel only eases toward that, so dots
// fade in and out where they stand instead of sliding along with the line.
private const val FRAME_MS = 125L
private const val TARGET_EVERY = 3
private const val EASE = 0.18f

// Kept across visits so the lines pick up where they were.
private var wavesSeconds = 0.0

/**
 * Three wide, faint, wavy lines crossing Home, each in its own direction, dithered with warm and cool edges in [tint].
 * Where they fall is worked out a cell at a time off the main thread a few times a second, every dot easing in or out
 * where it stands, and only this layer redraws. It stands still in battery saver or with animations turned off, and stops with Home.
 */
@Composable
fun HomeWaves(tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = rememberVisualsStill() || remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val cell = with(LocalDensity.current) { CellSize.toPx().roundToInt().coerceAtLeast(1) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(size, tint, still) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        val painter = WavesPainter(size, cell, tint)
        var last = withFrameMillis { it }
        var tick = 0
        while (true) {
            val fresh = tick++ % TARGET_EVERY == 0
            frame = withContext(Dispatchers.Default) { painter.paint(wavesSeconds, fresh, settle = still) }
            if (still) break
            delay(FRAME_MS)
            // waits on the frame clock, so it stops while Arc is in the background
            val now = withFrameMillis { it }
            wavesSeconds += (now - last).coerceAtMost(FRAME_MS * 3) / 1000.0
            last = now
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            // its own layer, so a new frame redraws only the lines and not Home on top of them
            .graphicsLayer()
            .drawBehind {
                val image = frame ?: return@drawBehind
                drawImage(
                    image,
                    dstSize = IntSize(image.width * cell, image.height * cell),
                    filterQuality = FilterQuality.None,
                )
            },
    )
}

private class Line(val nx: Double, val ny: Double, val reach: Double, val start: Double, val phase: Double, val freq: Double)

private class WavesPainter(size: IntSize, private val cell: Int, tint: Color) {
    private val columns = ceil(size.width.toDouble() / cell).toInt()
    private val rows = ceil(size.height.toDouble() / cell).toInt()
    private val height = size.height.toDouble()
    private val aspect = size.width / height
    private val pixels = IntArray(columns * rows)
    // premultiplied red, green, blue and alpha per cell: where each dot is heading, and where it is now
    private val target = FloatArray(columns * rows * 4)
    private val shown = FloatArray(columns * rows * 4)
    // two pictures taken in turns, so the one on screen is never being written
    private val bitmaps = Array(2) { Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888) }
    private val images = bitmaps.map { it.asImageBitmap() }
    private var turn = 0
    private val tint = doubleArrayOf(tint.red.toDouble(), tint.green.toDouble(), tint.blue.toDouble())
    private val lines = List(LINES) { k ->
        val middle = (LINES - 1) / 2.0
        val theta = (ANGLE + (k - middle) * SPREAD) * PI / 180
        val nx = cos(theta)
        val ny = sin(theta)
        val margin = WIDTH * 2 + WOBBLE + CHROMA
        Line(
            nx = nx,
            ny = ny,
            reach = aspect / 2 * abs(nx) + 0.5 * abs(ny) + margin,
            start = k.toDouble() / LINES,
            phase = k * 2.1,
            freq = WOBBLE_FREQ * (1 + k * 0.23),
        )
    }

    fun paint(seconds: Double, fresh: Boolean, settle: Boolean): ImageBitmap {
        if (fresh) aim(seconds)
        val ease = if (settle) 1f else EASE
        for (i in 0 until columns * rows) {
            val o = i * 4
            var a = shown[o + 3]
            if (a == 0f && target[o + 3] == 0f) {
                pixels[i] = 0
                continue
            }
            for (c in 0..3) {
                val now = shown[o + c] + (target[o + c] - shown[o + c]) * ease
                shown[o + c] = if (abs(now - target[o + c]) < 0.001f) target[o + c] else now
            }
            a = shown[o + 3]
            pixels[i] = if (a <= 0.002f) {
                0
            } else {
                ((a * 255).roundToInt() shl 24) or
                    (min(255, (shown[o] / a * 255).roundToInt()) shl 16) or
                    (min(255, (shown[o + 1] / a * 255).roundToInt()) shl 8) or
                    min(255, (shown[o + 2] / a * 255).roundToInt())
            }
        }
        turn = 1 - turn
        bitmaps[turn].setPixels(pixels, 0, columns, 0, 0, columns, rows)
        return images[turn]
    }

    private fun aim(seconds: Double) {
        target.fill(0f)
        // Each line travels across the whole screen and comes back in on the far side as it leaves. All of them take
        // the same time per trip, starting a third of a trip apart, so at most one is ever off screen.
        val trip = seconds * SPEED
        val along = DoubleArray(LINES) { k ->
            val line = lines[k]
            ((line.start + trip) % 1.0) * 2 * line.reach - line.reach
        }
        val light = DoubleArray(3)
        // the faintest dot needs about 3% light, which a line gives out to 1.9 widths from its middle
        val near = WIDTH * REACH + CHROMA
        // cells this far from every line can't be lit whatever the wobble, so they skip the costly part
        val far = near + WOBBLE
        for (cy in 0 until rows) {
            val py = cy * cell / height - 0.5
            for (cx in 0 until columns) {
                val px = cx * cell / height - aspect / 2
                light.fill(0.0)
                var any = false
                for (k in 0 until LINES) {
                    val line = lines[k]
                    val across = px * line.nx + py * line.ny
                    if (abs(across - along[k]) > far) continue
                    val tangent = -px * line.ny + py * line.nx
                    val base = across - along[k] - WOBBLE * fastSin(tangent * line.freq * 2 * PI + seconds * WOBBLE_SPEED + line.phase)
                    if (abs(base) > near) continue
                    any = true
                    for (channel in 0..2) {
                        light[channel] += glow((base + (channel - 1) * CHROMA) / WIDTH)
                    }
                }
                if (!any) continue
                val stripe = ((px + py) / 0.014).let { it - floor(it) } >= 0.5
                val slat = 1 - SLATS + if (stripe) SLATS else 0.0
                val threshold = (4 * bayer2(cx % 2, cy % 2) + bayer2(cx / 2 % 2, cy / 2 % 2) + 0.5) / 16.0
                var r = 0.0
                var g = 0.0
                var b = 0.0
                for (channel in 0..2) {
                    val on = if (light[channel] * slat + threshold >= 1.0) 1.0 else 0.0
                    val value = (on * (1 - TINT_MIX) + on * tint[channel] * 1.5 * TINT_MIX) * STRENGTH
                    when (channel) {
                        0 -> r = value
                        1 -> g = value
                        else -> b = value
                    }
                }
                val alpha = min(1.0, max(r, max(g, b)))
                if (alpha <= 0) continue
                val o = (cy * columns + cx) * 4
                target[o] = r.toFloat()
                target[o + 1] = g.toFloat()
                target[o + 2] = b.toFloat()
                target[o + 3] = alpha.toFloat()
            }
        }
    }
}

private fun bayer2(x: Int, y: Int) = (2 * x + 3 * y) % 4

private const val REACH = 1.9
private const val GLOW_STEPS = 64
private val GLOW = DoubleArray((REACH * GLOW_STEPS).toInt() + 2) { i -> exp(-(i.toDouble() / GLOW_STEPS).let { it * it }) }

/** exp(-d²) from a table; nothing past [REACH], where it's too faint to light a dot. */
private fun glow(d: Double): Double {
    val i = (abs(d) * GLOW_STEPS).toInt()
    return if (i < GLOW.size) GLOW[i] else 0.0
}

private const val SIN_STEPS = 1024
private val SIN = DoubleArray(SIN_STEPS) { sin(2 * PI * it / SIN_STEPS) }

private fun fastSin(x: Double): Double = SIN[Math.floorMod((x / (2 * PI) * SIN_STEPS).toLong(), SIN_STEPS.toLong()).toInt()]
