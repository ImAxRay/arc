package com.metrolist.music.arc.vinyl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.app.Activity

/**
 * Decides whether the phone is lying still, face up. It has to stay flat for a moment before
 * counting, since a phone passing through flat in the hand shouldn't change the screen.
 * Leaving takes more of a tilt than entering, so it can't flicker at the boundary.
 */
internal class FlatDetector(private val settleMs: Long = 1_500L) {
    private var flatSince = -1L
    var isFlat = false
        private set

    fun update(gravityZ: Float, nowMs: Long): Boolean {
        if (isFlat) {
            if (gravityZ < EXIT_GRAVITY) {
                isFlat = false
                flatSince = -1L
            }
        } else if (gravityZ >= ENTER_GRAVITY) {
            if (flatSince < 0L) flatSince = nowMs
            if (nowMs - flatSince >= settleMs) isFlat = true
        } else {
            flatSince = -1L
        }
        return isFlat
    }

    private companion object {
        const val ENTER_GRAVITY = 9.6f // About 12 degrees from level
        const val EXIT_GRAVITY = 9.1f // About 22 degrees from level
    }
}

/** True while the phone lies flat. The sensor only runs while [enabled] and the app is on screen. */
@Composable
fun rememberPhoneLyingFlat(enabled: Boolean): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val listening = enabled && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var flat by remember { mutableStateOf(false) }

    DisposableEffect(listening) {
        if (!listening) {
            flat = false
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val detector = FlatDetector()
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    flat = detector.update(event.values[2], SystemClock.uptimeMillis())
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { manager.unregisterListener(listener) }
    }
    return flat
}

/** While the record is on the table, the bars go away and the screen stays on. [keepStatusHidden] leaves the status bar hidden afterwards. */
@Composable
fun TurntableWindow(active: Boolean, keepStatusHidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(if (keepStatusHidden) WindowInsetsCompat.Type.navigationBars() else WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}
