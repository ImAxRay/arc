package com.metrolist.music.arc

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch

internal val VisualMode.label get() = when (this) {
    VisualMode.SAVER -> R.string.arc_visual_saver
    VisualMode.NORMAL -> R.string.arc_visual_normal
    VisualMode.FULL -> R.string.arc_visual_full
}

internal val VisualMode.desc get() = when (this) {
    VisualMode.SAVER -> R.string.arc_visual_saver_desc
    VisualMode.NORMAL -> R.string.arc_visual_normal_desc
    VisualMode.FULL -> R.string.arc_visual_full_desc
}

/** Saver / Normal / Full, a line on what the step does, and (on the settings home) the way into each effect's own switch. */
@Composable
fun VisualModeControl(navController: NavController, fineTune: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode by Visuals.mode.collectAsState()
    val phoneSaver by Visuals.phoneSaver.collectAsState()
    Material3SettingsGroup(
        title = stringResource(R.string.arc_visual_mode),
        icon = painterResource(R.drawable.contrast),
        items = listOfNotNull(
            Material3SettingsItem(
                title = {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        VisualModeSteps(mode) { picked -> scope.launch { context.dataStore.edit { it.applyVisualMode(picked) } } }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(if (phoneSaver) R.string.arc_visual_phone_saver else mode.desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (phoneSaver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            ),
            if (fineTune) {
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.arc_visual_fine_tune)) },
                    description = { Text(stringResource(R.string.arc_visual_fine_tune_desc)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/visual") },
                )
            } else null,
        ),
    )
    Spacer(Modifier.height(27.dp))
}

@Composable
private fun VisualModeSteps(mode: VisualMode, onPick: (VisualMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHighest).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VisualMode.entries.forEach { step ->
            val selected = step == mode
            val fill by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHighest, label = "step")
            Box(
                Modifier
                    .weight(1f)
                    .tapBounce(tick = false)
                    .clip(RoundedCornerShape(16.dp))
                    .background(fill)
                    .clickable { onPick(step) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                val ink = if (selected) colors.onPrimary else colors.onSurfaceVariant
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(
                            when (step) {
                                VisualMode.SAVER -> R.drawable.speed
                                VisualMode.NORMAL -> R.drawable.palette
                                VisualMode.FULL -> R.drawable.gradient
                            },
                        ),
                        null,
                        tint = ink,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(step.label), style = MaterialTheme.typography.labelLarge, color = ink)
                }
            }
        }
    }
}

/** Every visual effect with its own switch, the fine-tuning under the visual mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualSettings(navController: NavController) {
    val (homeLine, onHomeLine) = rememberPreference(ArcHomeLineKey, true)
    val (homeBackground, onHomeBackground) = rememberEnumPreference(ArcHomeBackgroundKey, HomeBackground.FIELD)
    val (tiltCovers, onTiltCovers) = rememberPreference(ArcTiltCoversKey, false)
    val (pressGlow, onPressGlow) = rememberPreference(ArcPressGlowKey, true)
    val (motionCovers, onMotionCovers) = rememberPreference(ArcMotionCoversKey, true)
    val (motionCoversMobile, onMotionCoversMobile) = rememberPreference(ArcMotionCoversMobileKey, false)
    var picking by rememberSaveable { mutableStateOf(false) }
    val locked = rememberVisualsStill()
    val backgroundText: @Composable (HomeBackground) -> String = {
        stringResource(
            when (it) {
                HomeBackground.FIELD -> R.string.arc_home_background_field
                HomeBackground.BLADES -> R.string.arc_home_background_blades
                HomeBackground.WAVES -> R.string.arc_home_background_waves
                HomeBackground.OFF -> R.string.arc_home_background_off
            },
        )
    }
    if (picking) {
        HomeBackgroundPicker(current = homeBackground, onSelect = onHomeBackground, onDismiss = { picking = false }, label = backgroundText)
    }

    // the picker sheet shows its own copy
    HomeBackdropAsSet(paused = picking)
    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .arcScroll()
            .padding(horizontal = 16.dp),
    ) {
        VisualModeControl(navController, fineTune = false)
        Material3SettingsGroup(
            title = stringResource(R.string.home),
            icon = painterResource(R.drawable.home_outlined),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.gradient),
                    title = { Text(stringResource(R.string.arc_home_background)) },
                    description = { Text(backgroundText(homeBackground)) },
                    enabled = !locked,
                    onClick = { picking = true },
                ),
                switchItem(R.drawable.linear_scale, R.string.arc_home_line, R.string.arc_home_line_desc, homeLine, onHomeLine, enabled = !locked),
                switchItem(R.drawable.gradient, R.string.arc_press_glow, R.string.arc_press_glow_desc, pressGlow, onPressGlow, enabled = !locked),
            ),
        )
        Spacer(Modifier.height(27.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.arc_visual_covers),
            icon = painterResource(R.drawable.album),
            items = listOfNotNull(
                switchItem(R.drawable.grid_view, R.string.arc_tilt_covers, R.string.arc_tilt_covers_desc, tiltCovers, onTiltCovers, enabled = !locked),
                switchItem(R.drawable.slow_motion_video, R.string.arc_motion_covers, R.string.arc_motion_covers_desc, motionCovers, onMotionCovers, enabled = !locked),
                if (motionCovers) {
                    switchItem(R.drawable.signal_cellular, R.string.arc_motion_covers_mobile, R.string.arc_motion_covers_mobile_desc, motionCoversMobile, onMotionCoversMobile, enabled = !locked)
                } else null,
            ),
        )
        Spacer(Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.arc_visual_mode)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}
