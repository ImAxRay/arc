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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.metrolist.music.constants.EnableHighRefreshRateKey
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch

internal val VisualMode.label get() = when (this) {
    VisualMode.SAVER -> R.string.arc_visual_saver
    VisualMode.NORMAL -> R.string.arc_visual_normal
    VisualMode.FULL -> R.string.arc_visual_full
}

private val VisualMode.cost get() = when (this) {
    VisualMode.SAVER -> 0.06f
    VisualMode.NORMAL -> 0.8f
    VisualMode.FULL -> 1f
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
                    onClick = { navController.navigate("settings/appearance") },
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
                    Spacer(Modifier.height(6.dp))
                    // how much it costs, from Home at rest on the emulator: Saver ~1%, Normal ~24%, Full that plus tilt
                    Box(Modifier.width(32.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(step.cost).height(3.dp).background(ink))
                    }
                }
            }
        }
    }
}

/** Home-at-rest processor share measured on the emulator (debug build), per background. */
private val HomeBackground.cpu get() = when (this) {
    HomeBackground.FIELD -> 24
    HomeBackground.WAVES -> 21
    HomeBackground.BLADES -> 15
    HomeBackground.OFF -> 0
}

@Composable
private fun costLine(heavy: Boolean, text: String) = buildAnnotatedString {
    withStyle(SpanStyle(color = if (heavy) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)) {
        append(stringResource(if (heavy) R.string.arc_cost_heavy else R.string.arc_cost_light))
    }
    append("  ")
    append(text)
}

private fun Material3SettingsItem.cost(heavy: Boolean, text: Int) = copy(description = { Text(costLine(heavy, stringResource(text))) })

/** Every visual effect with its own switch and what it costs; locked while Saver holds them off. */
@Composable
fun VisualEffects(picking: Boolean, onPicking: (Boolean) -> Unit) {
    val (homeLine, onHomeLine) = rememberPreference(ArcHomeLineKey, true)
    val (homeBackground, onHomeBackground) = rememberEnumPreference(ArcHomeBackgroundKey, HomeBackground.FIELD)
    val (tiltCovers, onTiltCovers) = rememberPreference(ArcTiltCoversKey, false)
    val (pressGlow, onPressGlow) = rememberPreference(ArcPressGlowKey, true)
    val (motionCovers, onMotionCovers) = rememberPreference(ArcMotionCoversKey, true)
    val (motionCoversMobile, onMotionCoversMobile) = rememberPreference(ArcMotionCoversMobileKey, false)
    val (highRefresh, onHighRefresh) = rememberPreference(EnableHighRefreshRateKey, true)
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
        HomeBackgroundPicker(current = homeBackground, onSelect = onHomeBackground, onDismiss = { onPicking(false) }, label = backgroundText)
    }
    Material3SettingsGroup(
        title = stringResource(R.string.arc_visual_effects),
        icon = painterResource(R.drawable.gradient),
        items = listOfNotNull(
            Material3SettingsItem(
                icon = painterResource(R.drawable.gradient),
                title = { Text(stringResource(R.string.arc_home_background)) },
                description = {
                    val name = backgroundText(homeBackground)
                    Text(if (homeBackground == HomeBackground.OFF) AnnotatedString(name) else costLine(true, stringResource(R.string.arc_cost_home_cpu, name, homeBackground.cpu)))
                },
                enabled = !locked,
                onClick = { onPicking(true) },
            ),
            switchItem(R.drawable.linear_scale, R.string.arc_home_line, R.string.arc_home_line_desc, homeLine, onHomeLine, enabled = !locked).cost(false, R.string.arc_home_line_desc),
            switchItem(R.drawable.gradient, R.string.arc_press_glow, R.string.arc_press_glow_desc, pressGlow, onPressGlow, enabled = !locked).cost(false, R.string.arc_press_glow_desc),
            switchItem(R.drawable.grid_view, R.string.arc_tilt_covers, R.string.arc_tilt_covers_desc, tiltCovers, onTiltCovers, enabled = !locked).cost(true, R.string.arc_tilt_covers_desc),
            switchItem(R.drawable.slow_motion_video, R.string.arc_motion_covers, R.string.arc_motion_covers_desc, motionCovers, onMotionCovers, enabled = !locked).cost(true, R.string.arc_motion_covers_desc),
            if (motionCovers) {
                switchItem(R.drawable.signal_cellular, R.string.arc_motion_covers_mobile, R.string.arc_motion_covers_mobile_desc, motionCoversMobile, onMotionCoversMobile, enabled = !locked)
            } else null,
            switchItem(R.drawable.speed, R.string.enable_high_refresh_rate, R.string.enable_high_refresh_rate_desc, highRefresh, onHighRefresh, enabled = !locked).cost(true, R.string.enable_high_refresh_rate_desc),
        ),
    )
    Spacer(Modifier.height(27.dp))
}
