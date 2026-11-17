package com.metrolist.music.arc

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
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
    // the same eye trailing behind, so covers that lean on it lag the others
    var slowX by mutableFloatStateOf(0f)
    var slowY by mutableFloatStateOf(0f)
}

/** One eye for a whole screen, so every cover on it shares one sensor; null when tilt covers are off. */
val LocalTiltEye = staticCompositionLocalOf<TiltEye?> { null }

/** Follows the phone's tilt while the caller is on screen, resumed and not under the open player; null when tilt covers are off. */
@Composable
fun rememberTiltEye(): TiltEye? {
    val enabled by rememberPreference(ArcTiltCoversKey, false)
    val eye = remember { TiltEye() }
    if (!enabled || rememberVisualsStill()) return null
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val covered = ArcLineSignals.playerExpanded
    DisposableEffect(lifecycle, context, covered) {
        val sensors = context.getSystemService(SensorManager::class.java)
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
                if (abs(x - eye.slowX) > EYE_STEP || abs(y - eye.slowY) > EYE_STEP) {
                    eye.slowX += (x - eye.slowX) * SLOW_FOLLOW
                    eye.slowY += (y - eye.slowY) * SLOW_FOLLOW
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var listening = false
        fun start() {
            if (listening || covered || sensor == null) return
            listening = sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        fun stop() {
            if (listening) sensors?.unregisterListener(listener)
            listening = false
            baseX = Float.NaN
            eye.x = 0f
            eye.y = 0f
            eye.slowX = 0f
            eye.slowY = 0f
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
 * [seed] (the cover's id or URL) gives each cover its own depth and lag, so a grid doesn't swing as one.
 * The parent should clip; [content] is the image.
 */
@Composable
fun RecessedCover(eye: TiltEye?, modifier: Modifier = Modifier, seed: Any? = null, content: @Composable BoxScope.() -> Unit) {
    if (eye == null) {
        Box(modifier, content = content)
        return
    }
    var spot by remember { mutableStateOf(Offset.Zero) }
    var rootWidth by remember { mutableFloatStateOf(0f) }
    var tileSize by remember { mutableStateOf(Size.Zero) }
    val feel = remember(seed) { seed?.let { TileFeel(it.hashCode()) } ?: TileFeel.Plain }
    fun shift(): Offset {
        if (rootWidth == 0f) return Offset.Zero
        val max = tileSize.width * MAX_SHIFT
        val ex = eye.x + (eye.slowX - eye.x) * feel.lag
        val ey = eye.y + (eye.slowY - eye.y) * feel.lag
        val dx = (ex - spot.x / rootWidth) * DEPTH * feel.depthX * tileSize.width
        val dy = (ey - spot.y / rootWidth) * DEPTH * feel.depthY * tileSize.height
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
                            wallStops(a),
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
                            wallStops(a),
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

/** Per-cover depth (more across than up and down) and how far it trails the eye, from a hash. */
private class TileFeel(val depthX: Float, val depthY: Float, val lag: Float) {
    constructor(hash: Int) : this(
        0.55f + 0.9f * ((hash and 0xFF) / 255f),
        0.8f + 0.4f * (((hash ushr 8) and 0xFF) / 255f),
        ((hash ushr 16) and 0xFF) / 255f,
    )

    companion object {
        /** A lone cover (the album page) keeps the plain depth. */
        val Plain = TileFeel(1f, 1f, 0f)
    }
}

// an inner wall that fades out softly instead of ending on a straight edge
private fun wallStops(alpha: Float) =
    listOf(Color.Black.copy(alpha = alpha), Color.Black.copy(alpha = alpha * 0.4f), Color.Black.copy(alpha = alpha * 0.1f), Color.Transparent)

private const val SLOW_FOLLOW = 0.06f
private const val REST_DRIFT = 0.012f
private const val TILT_GAIN = 1.9f
private const val MAX_EYE = 0.6f
private const val EYE_STEP = 0.002f
private const val DEPTH = 0.13f
private const val MAX_SHIFT = 0.08f
private const val COVER_SCALE = 1f + 2 * MAX_SHIFT + 0.02f
private const val WALL_WIDTH = 1.4f
private const val WALL_ALPHA = 0.22f
