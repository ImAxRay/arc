package com.metrolist.music.arc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.areStatusBarsVisible
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Date

/** Time and battery in place of the hidden status bar, small enough to sit quietly on the cover. [dark] for light covers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatusLine(dark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var battery by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    DisposableEffect(context) {
        fun level(intent: Intent): Int {
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            return intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / scale
        }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    battery = level(intent)
                }
            }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { battery = level(it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    val time = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    // Pulling down from the top shows the real bar for a moment; this steps aside so the clocks don't overlap.
    val systemShown = WindowInsets.areStatusBarsVisible
    val shown by animateFloatAsState(if (systemShown) 0f else 1f, label = "statusLine")
    Box(modifier.graphicsLayer { alpha = shown }.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility), contentAlignment = Alignment.CenterStart) {
        Text(
            text = if (battery >= 0) "$time   $battery%" else time,
            style =
                TextStyle(
                    color = if (dark) Color(0xFF15110F).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow((if (dark) Color.White else Color.Black).copy(alpha = 0.5f), Offset(0f, 1f), blurRadius = 6f),
                ),
            modifier = Modifier.padding(start = 22.dp),
        )
    }
}

