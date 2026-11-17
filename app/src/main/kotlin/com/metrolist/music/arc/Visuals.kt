package com.metrolist.music.arc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** How much of Arc's visual extras run: picking one sets the effect switches, which can then be fine-tuned one by one. */
enum class VisualMode { SAVER, NORMAL, FULL }

val ArcVisualModeKey = stringPreferencesKey("arcVisualMode")

/** The stored mode, or for installs from before modes existed, the one their effect switches look like. */
fun visualMode(prefs: Preferences): VisualMode {
    prefs[ArcVisualModeKey]?.let { name -> VisualMode.entries.firstOrNull { it.name == name }?.let { return it } }
    return when {
        prefs[ArcTiltCoversKey] == true -> VisualMode.FULL
        prefs[ArcHomeBackgroundKey] == HomeBackground.OFF.name && prefs[ArcMotionCoversKey] == false -> VisualMode.SAVER
        else -> VisualMode.NORMAL
    }
}

/**
 * Writes [mode]. Normal and Full also set the effect switches they stand for (a chosen Home background other than Off
 * is kept); Saver leaves them alone and holds every effect off while it lasts, so leaving it brings them back.
 */
fun MutablePreferences.applyVisualMode(mode: VisualMode) {
    this[ArcVisualModeKey] = mode.name
    if (mode == VisualMode.SAVER) return
    val background = this[ArcHomeBackgroundKey]
    if (background == null || background == HomeBackground.OFF.name) this[ArcHomeBackgroundKey] = HomeBackground.FIELD.name
    this[ArcHomeLineKey] = true
    this[ArcPressGlowKey] = true
    this[ArcMotionCoversKey] = true
    this[ArcTiltCoversKey] = mode == VisualMode.FULL
}

object Visuals {
    /** The phone's Battery Saver, kept current by a receiver. */
    val phoneSaver = MutableStateFlow(false)
    val mode = MutableStateFlow(VisualMode.NORMAL)

    /** Saver, chosen or forced by the phone's Battery Saver: every visual effect is off and its switch locked. */
    val still = MutableStateFlow(false)

    fun init(context: Context, scope: CoroutineScope) {
        val power = context.getSystemService(PowerManager::class.java)
        phoneSaver.value = power?.isPowerSaveMode == true
        still.value = phoneSaver.value
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    phoneSaver.value = power?.isPowerSaveMode == true
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { combine(phoneSaver, mode) { saver, m -> saver || m == VisualMode.SAVER }.collect { still.value = it } }
        scope.launch {
            // Installs from before modes existed keep the mode their switches look like, so nobody is asked again.
            context.dataStore.edit { if (it[ArcVisualModeKey] == null) it[ArcVisualModeKey] = visualMode(it).name }
            context.dataStore.data.map(::visualMode).distinctUntilChanged().collect { mode.value = it }
        }
    }
}

@Composable
fun rememberVisualsStill(): Boolean {
    val still by Visuals.still.collectAsState()
    return still
}

/** [basicMarquee] that stays still in Saver. */
@Composable
fun Modifier.arcMarquee(iterations: Int, initialDelayMillis: Int = 1200, velocity: Dp = 30.dp): Modifier =
    if (rememberVisualsStill()) this else basicMarquee(iterations = iterations, initialDelayMillis = initialDelayMillis, velocity = velocity)
