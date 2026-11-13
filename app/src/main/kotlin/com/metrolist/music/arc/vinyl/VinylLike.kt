package com.metrolist.music.arc.vinyl

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val HEART_SECONDS = 0.9f
private const val POP_IN_END = 0.14f
private const val SETTLE_END = 0.26f
private const val LEAVE_START = 0.58f
private const val HEART_DP = 96f
private const val RISE_DP = 70f

private val HeartRed = Color(0xFFFF2D55)
private val HeartTint = ColorFilter.tint(HeartRed)

private class FloatingHeart(val center: Offset, val tilt: Float) {
    var age = 0f
}

/** Hearts that pop up under the finger on a double tap, tilted a little differently each time. */
@Stable
internal class VinylHearts(private val scope: CoroutineScope, private val density: Float) {
    private val hearts = ArrayList<FloatingHeart>()
    private var frame by mutableLongStateOf(0L)
    private var loop: Job? = null

    fun spawn(at: Offset) {
        hearts += FloatingHeart(at, tilt = Random.nextFloat() * 40f - 20f)
        if (loop?.isActive == true) return
        loop =
            scope.launch {
                var last = withFrameNanos { it }
                while (hearts.isNotEmpty()) {
                    val now = withFrameNanos { it }
                    val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    hearts.forEach { it.age += seconds }
                    hearts.removeAll { it.age >= HEART_SECONDS }
                    frame++
                }
            }
    }

    fun draw(scope: DrawScope, painter: Painter) {
        with(scope) {
            frame
            val full = HEART_DP * density
            for (heart in hearts) {
                val t = heart.age / HEART_SECONDS
                val scale =
                    when {
                        t < POP_IN_END -> 1.25f * (t / POP_IN_END)
                        t < SETTLE_END -> 1.25f - 0.25f * ((t - POP_IN_END) / (SETTLE_END - POP_IN_END))
                        t < LEAVE_START -> 1f
                        else -> 1f + 0.5f * ((t - LEAVE_START) / (1f - LEAVE_START))
                    }
                val leaving = ((t - LEAVE_START) / (1f - LEAVE_START)).coerceIn(0f, 1f)
                val side = full * scale
                val center = heart.center - Offset(0f, RISE_DP * density * leaving)
                rotate(heart.tilt, center) {
                    translate(center.x - side / 2f, center.y - side / 2f) {
                        with(painter) { draw(Size(side, side), alpha = 1f - leaving, colorFilter = HeartTint) }
                    }
                }
            }
        }
    }
}
