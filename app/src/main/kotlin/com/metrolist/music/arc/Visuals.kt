package com.metrolist.music.arc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
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

/** Writes [mode] and the effect switches it stands for. A chosen Home background other than Off is kept. */
fun MutablePreferences.applyVisualMode(mode: VisualMode) {
    val on = mode != VisualMode.SAVER
    this[ArcVisualModeKey] = mode.name
    val background = this[ArcHomeBackgroundKey]
    if (!on) {
        this[ArcHomeBackgroundKey] = HomeBackground.OFF.name
    } else if (background == null || background == HomeBackground.OFF.name) {
        this[ArcHomeBackgroundKey] = HomeBackground.FIELD.name
    }
    this[ArcHomeLineKey] = on
    this[ArcPressGlowKey] = on
    this[ArcMotionCoversKey] = on
    this[ArcTiltCoversKey] = mode == VisualMode.FULL
}

object Visuals {
    /** The phone's Battery Saver, kept current by a receiver. */
    val phoneSaver = MutableStateFlow(false)
    val mode = MutableStateFlow(VisualMode.NORMAL)

    /** Effects with no switch of their own (moving backgrounds, glow, wobble, landing, marquees) stand still while this is true. */
    val still = MutableStateFlow(false)

    fun init(context: Context, scope: CoroutineScope) {
        val power = context.getSystemService(PowerManager::class.java)
        phoneSaver.value = power?.isPowerSaveMode == true
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
