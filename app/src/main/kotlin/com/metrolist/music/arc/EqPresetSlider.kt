package com.metrolist.music.arc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.eq.data.SavedEQProfile
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The EQ presets on one slider, for the player menu: each step is heard while sliding and the one
 * it stops on is kept. The same presets the play-button hold slides through, but it stays put.
 */
@Composable
fun EqPresetSlider(connection: PlayerConnection, modifier: Modifier = Modifier) {
    val service = connection.service
    val repository = service.eqProfileRepository
    val profiles by repository.profiles.collectAsState()
    val active by repository.activeProfile.collectAsState()
    val presets = remember(profiles) { listOf<SavedEQProfile?>(null) + repository.getSortedProfiles() }
    if (presets.size < 2) return
    val activeIndex = presets.indexOfFirst { it?.id == active?.id }.coerceAtLeast(0)
    var position by remember(activeIndex, presets.size) { mutableFloatStateOf(activeIndex.toFloat()) }
    val shown = position.roundToInt().coerceIn(0, presets.lastIndex)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.arc_eq_label),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                presets[shown]?.name ?: stringResource(R.string.arc_hold_eq_off),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        ArcSlider(
            value = position,
            onValueChange = { value ->
                val index = value.roundToInt().coerceIn(0, presets.lastIndex)
                if (index != shown) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    presets[index]?.let { service.equalizerService.applyProfile(it) } ?: service.equalizerService.disable()
                }
                position = value
            },
            onValueChangeFinished = {
                position = shown.toFloat()
                scope.launch { repository.setActiveProfile(presets[shown]?.id) }
            },
            valueRange = 0f..presets.lastIndex.toFloat(),
            steps = (presets.size - 2).coerceAtLeast(0),
        )
    }
}
