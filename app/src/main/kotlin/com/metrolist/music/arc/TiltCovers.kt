package com.metrolist.music.arc

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.utils.rememberPreference
import kotlin.math.abs

val ArcTiltCoversKey = booleanPreferencesKey("arcTiltCovers")

/** Where the eye sits over the screen, in screen widths from its centre; tilting the phone moves it. */
@Stable
class TiltEye {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
}

/** Follows the phone's tilt while the caller is on screen, resumed and not under the open player; null when tilt covers are off. */
@Composable
fun rememberTiltEye(): TiltEye? {
    val enabled by rememberPreference(ArcTiltCoversKey, false)
    val eye = remember { TiltEye() }
    if (!enabled) return null
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val covered = ArcLineSignals.playerExpanded
    DisposableEffect(lifecycle, context, covered) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val matrix = FloatArray(9)
        var baseX = Float.NaN
        var baseY = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // "up" in the phone's own axes: x to the right edge, y to the top edge
                val upX: Float
                val upY: Float
                if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    upX = matrix[6]
                    upY = matrix[7]
                } else {
                    upX = event.values[0] / SensorManager.GRAVITY_EARTH
                    upY = event.values[1] / SensorManager.GRAVITY_EARTH
                }
                if (baseX.isNaN()) {
                    baseX = upX
                    baseY = upY
                }
                // the resting pose drifts to however the phone is held, so only a tilt moves the eye
                baseX += (upX - baseX) * REST_DRIFT
                baseY += (upY - baseY) * REST_DRIFT
                val x = (-(upX - baseX) * TILT_GAIN).coerceIn(-MAX_EYE, MAX_EYE)
                val y = (-(upY - baseY) * TILT_GAIN).coerceIn(-MAX_EYE, MAX_EYE)
                if (abs(x - eye.x) > EYE_STEP || abs(y - eye.y) > EYE_STEP) {
                    eye.x = x
                    eye.y = y
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var listening = false
        fun start() {
            if (listening || covered || sensor == null || power?.isPowerSaveMode == true) return
            listening = sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        fun stop() {
            if (listening) sensors?.unregisterListener(listener)
            listening = false
            baseX = Float.NaN
            eye.x = 0f
            eye.y = 0f
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
    return eye
}

/**
 * A cover sunk into the screen, seen from an eye above the screen's centre: covers away from the
 * centre show more depth and an inner wall on their outer side, and tilting the phone moves the eye.
 * The parent should clip; [content] is the image.
 */
@Composable
fun RecessedCover(eye: TiltEye?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (eye == null) {
        Box(modifier, content = content)
        return
    }
    var spot by remember { mutableStateOf(Offset.Zero) }
    var rootWidth by remember { mutableFloatStateOf(0f) }
    var tileSize by remember { mutableStateOf(Size.Zero) }
    fun shift(): Offset {
        if (rootWidth == 0f) return Offset.Zero
        val max = tileSize.width * MAX_SHIFT
        val dx = (eye.x - spot.x / rootWidth) * DEPTH * tileSize.width
        val dy = (eye.y - spot.y / rootWidth) * DEPTH * tileSize.height
        return Offset(dx.coerceIn(-max, max), dy.coerceIn(-max, max))
    }
    Box(
        modifier
            .onGloballyPositioned {
                val root = it.findRootCoordinates().size
                val bounds = it.boundsInRoot()
                rootWidth = root.width.toFloat()
                spot = Offset(bounds.center.x - root.width / 2f, bounds.center.y - root.height / 2f)
                tileSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
            }.drawWithContent {
                drawContent()
                val s = shift()
                val max = size.width * MAX_SHIFT
                if (max <= 0f) return@drawWithContent
                // the wall shows on the side the cover moved away from
                val wallX = abs(s.x) * WALL_WIDTH
                if (wallX > 0.5f) {
                    val a = WALL_ALPHA * abs(s.x) / max
                    val fromLeft = s.x > 0
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = a), Color.Transparent),
                            startX = if (fromLeft) 0f else size.width,
                            endX = if (fromLeft) wallX else size.width - wallX,
                        ),
                    )
                }
                val wallY = abs(s.y) * WALL_WIDTH
                if (wallY > 0.5f) {
                    val a = WALL_ALPHA * abs(s.y) / max
                    val fromTop = s.y > 0
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = a), Color.Transparent),
                            startY = if (fromTop) 0f else size.height,
                            endY = if (fromTop) wallY else size.height - wallY,
                        ),
                    )
                }
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val s = shift()
                    scaleX = COVER_SCALE
                    scaleY = COVER_SCALE
                    translationX = s.x
                    translationY = s.y
                },
            content = content,
        )
    }
}

private const val REST_DRIFT = 0.012f
private const val TILT_GAIN = 1.9f
private const val MAX_EYE = 0.6f
private const val EYE_STEP = 0.002f
private const val DEPTH = 0.13f
private const val MAX_SHIFT = 0.08f
private const val COVER_SCALE = 1f + 2 * MAX_SHIFT + 0.02f
private const val WALL_WIDTH = 1.8f
private const val WALL_ALPHA = 0.45f
