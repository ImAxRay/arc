package com.metrolist.music.arc.vinyl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.SweepGradient
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal enum class VinylFinish { BLACK, COLOR, MARBLE, SPLATTER, CLEAR }

internal fun finishFor(seed: Int): VinylFinish = VinylFinish.entries[abs(seed % VinylFinish.entries.size)]

private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Int = 255): Int =
    android.graphics.Color.HSVToColor(alpha, floatArrayOf((hue + 360f) % 360f, saturation, value))

private fun mix(from: Int, to: Int, amount: Float): Int {
    val inverse = 1f - amount
    val red = (from shr 16 and 255) * inverse + (to shr 16 and 255) * amount
    val green = (from shr 8 and 255) * inverse + (to shr 8 and 255) * amount
    val blue = (from and 255) * inverse + (to and 255) * amount
    return (255 shl 24) or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
}

private const val DEAD_WAX_END = 0.5f
private val SINGLE_SONG_GAPS = listOf(0.6f, 0.69f, 0.78f, 0.87f)

/** The part of the record that turns: its colour or pattern plus small marks that make the rotation visible. */
internal fun buildRecordSurface(seed: Int, sizePx: Int): Bitmap {
    val random = Random(seed)
    val hue = random.nextFloat() * 360f
    val center = sizePx / 2f
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val fill = { color: Int ->
        paint.color = color
        canvas.drawCircle(center, center, center, paint)
    }

    when (finishFor(seed)) {
        VinylFinish.BLACK -> fill(0xFF0A0A0C.toInt())
        VinylFinish.COLOR -> fill(hsv(hue, 0.78f, 0.55f))
        VinylFinish.CLEAR -> fill(hsv(hue, 0.55f, 0.85f, alpha = 72))
        VinylFinish.SPLATTER -> {
            fill(hsv(hue, 0.14f, 0.9f))
            val inks = intArrayOf(hsv(hue + 150f, 0.85f, 0.7f), hsv(hue + 200f, 0.8f, 0.42f), 0xFF141210.toInt())
            // A blot near the rim would otherwise poke out past the edge of the record.
            canvas.save()
            canvas.clipPath(Path().apply { addCircle(center, center, center, Path.Direction.CW) })
            repeat(640) { index ->
                val direction = random.nextFloat() * 6.283f
                val distance = sqrt(random.nextFloat()) * center
                val blot = random.nextFloat().pow(3) * center * 0.035f + 1.2f
                paint.color = inks[index % inks.size]
                canvas.drawCircle(
                    center + kotlin.math.cos(direction) * distance,
                    center + sin(direction) * distance,
                    blot,
                    paint,
                )
            }
            canvas.restore()
        }
        VinylFinish.MARBLE -> {
            val dark = hsv(hue, 0.78f, 0.45f)
            val bright = hsv(hue + 35f, 0.7f, 0.85f)
            val cream = 0xFFEEE4D4.toInt()
            val phaseA = random.nextFloat() * 6f
            val phaseB = random.nextFloat() * 6f
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                for (x in 0 until sizePx) {
                    val dx = x - center
                    val dy = y - center
                    val ring = hypot(dx, dy) / center
                    if (ring > 1f) continue
                    val direction = atan2(dy, dx)
                    val wave =
                        0.5f + 0.5f * sin(
                            direction * 3f + ring * 9f +
                                3f * sin(direction * 2f + phaseA) +
                                2f * sin(ring * 13f + phaseB),
                        )
                    val edge = (abs(wave - 0.5f) * 4f).coerceAtMost(1f)
                    pixels[y * sizePx + x] =
                        if (wave < 0.5f) mix(bright, dark, edge) else mix(cream, bright, edge)
                }
            }
            bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }

    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    repeat(22) {
        val radius = (DEAD_WAX_END + random.nextFloat() * (0.98f - DEAD_WAX_END)) * center
        val start = random.nextFloat() * 360f
        paint.color = android.graphics.Color.argb((14 + random.nextInt(28)), 255, 255, 255)
        paint.strokeWidth = sizePx / 1000f * (0.8f + random.nextFloat() * 1.6f)
        canvas.drawArc(
            center - radius, center - radius, center + radius, center + radius,
            start, 4f + random.nextFloat() * 18f, false, paint,
        )
    }
    return bitmap
}

/** The part that stays still: grooves are rotationally symmetric and the light does not turn with the record. */
internal fun buildGrooves(sizePx: Int, bandGaps: List<Float> = SINGLE_SONG_GAPS): Bitmap {
    val random = Random(33)
    val center = sizePx / 2f
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val trackGaps = bandGaps.map { it * center }
    val step = (sizePx / 700f).coerceAtLeast(1.5f)
    paint.strokeWidth = step * 0.6f

    // Real records stop short of the label, leaving smooth dead wax with only a couple of run-out lines.
    paint.color = android.graphics.Color.argb(40, 255, 255, 255)
    canvas.drawCircle(center, center, center * 0.37f, paint)
    canvas.drawCircle(center, center, center * 0.45f, paint)

    var radius = center * DEAD_WAX_END
    while (radius < center * 0.985f) {
        if (trackGaps.none { abs(it - radius) < step * 2.5f }) {
            val strength = 0.05f + random.nextFloat() * 0.12f
            paint.color =
                if (random.nextFloat() < 0.55f) {
                    android.graphics.Color.argb((strength * 360f).toInt().coerceAtMost(255), 0, 0, 0)
                } else {
                    android.graphics.Color.argb((strength * 128f).toInt(), 255, 255, 255)
                }
            canvas.drawCircle(center, center, radius, paint)
        }
        radius += step
    }

    paint.strokeWidth = step
    paint.color = android.graphics.Color.argb(52, 255, 255, 255)
    canvas.drawCircle(center, center, center * 0.992f, paint)
    paint.color = android.graphics.Color.argb(128, 0, 0, 0)
    canvas.drawCircle(center, center, center * 0.306f, paint)

    val white = { alpha: Int -> android.graphics.Color.argb(alpha, 255, 255, 255) }
    val sheen =
        SweepGradient(
            center, center,
            intArrayOf(white(0), white(50), white(13), white(0), white(0), white(33), white(8), white(0), white(0)),
            floatArrayOf(0f, 0.06f, 0.1f, 0.18f, 0.5f, 0.56f, 0.6f, 0.68f, 1f),
        ).apply { setLocalMatrix(Matrix().apply { setRotate(-140f, center, center) }) }
    val ring =
        Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addCircle(center, center, center, Path.Direction.CW)
            addCircle(center, center, center * 0.306f, Path.Direction.CW)
        }
    canvas.drawPath(ring, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sheen })
    return bitmap
}
