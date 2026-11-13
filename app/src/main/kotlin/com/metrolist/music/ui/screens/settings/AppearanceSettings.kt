/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import com.metrolist.music.arc.arcScroll
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.metrolist.music.arc.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.arc.ArcHomeLineKey
import com.metrolist.music.arc.ArcHomeBackgroundKey
import com.metrolist.music.arc.SpeedDialRowChoices
import com.metrolist.music.arc.SpeedDialRowsKey
import com.metrolist.music.arc.HomeBackground
import com.metrolist.music.arc.HomeBackgroundPicker
import com.metrolist.music.arc.ArcAudioDetailsKey
import com.metrolist.music.arc.ArcMotionCoversKey
import com.metrolist.music.arc.ArcMotionCoversMobileKey
import com.metrolist.music.arc.SearchMicKey
import com.metrolist.music.arc.resetHints
import com.metrolist.music.constants.ChipSortTypeKey
import com.metrolist.music.constants.CropAlbumArtKey
import com.metrolist.music.constants.DefaultOpenTabKey
import com.metrolist.music.constants.DensityScale
import com.metrolist.music.constants.DensityScaleKey
import com.metrolist.music.constants.DynamicThemeKey
import com.metrolist.music.constants.EnableDynamicIconKey
import com.metrolist.music.constants.EnableHighRefreshRateKey
import com.metrolist.music.constants.EnableLandscapeScalingKey
import com.metrolist.music.constants.ExperimentalLyricsKey
import com.metrolist.music.constants.ForceBottomNavBarKey
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.HidePlayerThumbnailKey
import com.metrolist.music.constants.HideStatusBarOnFullscreenKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.constants.ListenTogetherInTopBarKey
import com.metrolist.music.constants.LyricsAnimationStyle
import com.metrolist.music.constants.LyricsAnimationStyleKey
import com.metrolist.music.constants.LyricsClickKey
import com.metrolist.music.constants.LyricsGlowEffectKey
import com.metrolist.music.constants.LyricsLineSpacingKey
import com.metrolist.music.constants.LyricsScrollKey
import com.metrolist.music.constants.LyricsTextPositionKey
import com.metrolist.music.constants.LyricsTextSizeKey
import com.metrolist.music.constants.MiniPlayerBackgroundStyle
import com.metrolist.music.constants.MiniPlayerBackgroundStyleKey
import com.metrolist.music.constants.PlayerBackgroundStyle
import com.metrolist.music.constants.PlayerBackgroundStyleKey
import com.metrolist.music.constants.PlayerButtonsStyle
import com.metrolist.music.constants.PlayerButtonsStyleKey
import com.metrolist.music.constants.PureBlackMiniPlayerKey
import com.metrolist.music.constants.RespectAgentPositioningKey
import com.metrolist.music.constants.SelectedThemeColorKey
import com.metrolist.music.constants.ShowCachedPlaylistKey
import com.metrolist.music.constants.ShowDownloadedPlaylistKey
import com.metrolist.music.constants.ShowLikedPlaylistKey
import com.metrolist.music.constants.ShowTopPlaylistKey
import com.metrolist.music.constants.ShowUploadedPlaylistKey
import com.metrolist.music.constants.SliderStyle
import com.metrolist.music.constants.SliderStyleKey
import com.metrolist.music.constants.SlimNavBarKey
import com.metrolist.music.constants.SquigglySliderKey
import com.metrolist.music.constants.SwipeSensitivityKey
import com.metrolist.music.constants.SwipeThumbnailKey
import com.metrolist.music.constants.SwipeToRemoveSongKey
import com.metrolist.music.constants.SwipeToSongKey
import com.metrolist.music.constants.UseNewMiniPlayerDesignKey
import com.metrolist.music.constants.UseNewPlayerDesignKey
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.PlayerSliderTrack
import com.metrolist.music.ui.component.SquigglySlider
import com.metrolist.music.ui.component.WavySlider
import com.metrolist.music.ui.theme.DefaultThemeColor
import com.metrolist.music.ui.theme.PlayerSliderColors
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.IconUtils
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
) {
    val (dynamicTheme, onDynamicThemeChange) =
        rememberPreference(
            DynamicThemeKey,
            defaultValue = true,
        )
    val (enableDynamicIcon, onEnableDynamicIconPrefChange) =
        rememberPreference(
            EnableDynamicIconKey,
            defaultValue = false,
        )
    val iconContext = LocalContext.current
    val onEnableDynamicIconChange: (Boolean) -> Unit = { newValue ->
        onEnableDynamicIconPrefChange(newValue)
        IconUtils.setIcon(iconContext, newValue)
    }
    val (enableHighRefreshRate, onEnableHighRefreshRateChange) =
        rememberPreference(
            EnableHighRefreshRateKey,
            defaultValue = true,
        )
    val (selectedThemeColorInt) =
        rememberPreference(
            SelectedThemeColorKey,
            defaultValue = DefaultThemeColor.toArgb(),
        )
    // Check if user has selected a custom color (not the default/dynamic color)
    val isUsingCustomColor = selectedThemeColorInt != DefaultThemeColor.toArgb()

    val (defaultOpenTab, onDefaultOpenTabChange) =
        rememberEnumPreference(
            DefaultOpenTabKey,
            defaultValue = NavigationTab.HOME,
        )

    val (densityScale, onDensityScaleChange) = rememberPreference(DensityScaleKey, defaultValue = 1f)
    var showDensityScaleDialog by rememberSaveable { mutableStateOf(false) }

    val (searchMic, onSearchMicChange) = rememberPreference(SearchMicKey, defaultValue = true)
    val (homeLine, onHomeLineChange) = rememberPreference(ArcHomeLineKey, defaultValue = true)
    val (homeBackground, onHomeBackgroundChange) = rememberEnumPreference(ArcHomeBackgroundKey, defaultValue = HomeBackground.FIELD)
    var showHomeBackgroundDialog by rememberSaveable { mutableStateOf(false) }
    val homeBackgroundText: @Composable (HomeBackground) -> String = {
        stringResource(
            when (it) {
                HomeBackground.FIELD -> R.string.arc_home_background_field
                HomeBackground.BLADES -> R.string.arc_home_background_blades
                HomeBackground.WAVES -> R.string.arc_home_background_waves
                HomeBackground.OFF -> R.string.arc_home_background_off
            },
        )
    }
    if (showHomeBackgroundDialog) {
        HomeBackgroundPicker(
            current = homeBackground,
            onSelect = onHomeBackgroundChange,
            onDismiss = { showHomeBackgroundDialog = false },
            label = homeBackgroundText,
        )
    }
    val (speedDialRows, onSpeedDialRowsChange) = rememberPreference(SpeedDialRowsKey, defaultValue = 3)
    var showSpeedDialRowsDialog by rememberSaveable { mutableStateOf(false) }
    if (showSpeedDialRowsDialog) {
        EnumDialog(
            onDismiss = { showSpeedDialRowsDialog = false },
            onSelect = {
                onSpeedDialRowsChange(it)
                showSpeedDialRowsDialog = false
            },
            title = stringResource(R.string.arc_speed_dial_rows),
            current = speedDialRows,
            values = SpeedDialRowChoices,
            valueText = { pluralStringResource(R.plurals.arc_speed_dial_rows_value, it, it) },
        )
    }
    val (audioDetails, onAudioDetailsChange) = rememberPreference(ArcAudioDetailsKey, defaultValue = true)
    val (motionCovers, onMotionCoversChange) = rememberPreference(ArcMotionCoversKey, defaultValue = true)
    val (motionCoversMobile, onMotionCoversMobileChange) = rememberPreference(ArcMotionCoversMobileKey, defaultValue = false)
    val hintScope = rememberCoroutineScope()
    val hintContext = LocalContext.current
    val (listenTogetherInTopBar, onListenTogetherInTopBarChange) =
        rememberPreference(
            ListenTogetherInTopBarKey,
            defaultValue = true,
        )

    val (defaultChip, onDefaultChipChange) =
        rememberEnumPreference(
            key = ChipSortTypeKey,
            defaultValue = LibraryFilter.LIBRARY,
        )

    var showDefaultOpenTabDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showDefaultOpenTabDialog) {
        EnumDialog(
            onDismiss = { showDefaultOpenTabDialog = false },
            onSelect = {
                onDefaultOpenTabChange(it)
                showDefaultOpenTabDialog = false
            },
            title = stringResource(R.string.default_open_tab),
            current = defaultOpenTab,
            values = NavigationTab.values().toList(),
            valueText = {
                when (it) {
                    NavigationTab.HOME -> stringResource(R.string.home)
                    NavigationTab.SEARCH -> stringResource(R.string.search)
                    NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                }
            },
        )
    }

    var showDefaultChipDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showDefaultChipDialog) {
        EnumDialog(
            onDismiss = { showDefaultChipDialog = false },
            onSelect = {
                onDefaultChipChange(it)
                showDefaultChipDialog = false
            },
            title = stringResource(R.string.default_lib_chips),
            current = defaultChip,
            values = LibraryFilter.values().toList(),
            valueText = {
                when (it) {
                    LibraryFilter.SONGS -> stringResource(R.string.songs)
                    LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                    LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                    LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                    LibraryFilter.PODCASTS -> stringResource(R.string.filter_podcasts)
                    LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                }
            },
        )
    }

    if (showDensityScaleDialog) {
        DefaultDialog(
            onDismiss = { showDensityScaleDialog = false },
            buttons = {
                TextButton(
                    onClick = { showDensityScaleDialog = false },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        ) {
            Column {
                DensityScale.entries.forEach { scale ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDensityScaleChange(scale.value)
                                    showDensityScaleDialog = false
                                }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = scale.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (densityScale == scale.value) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .arcScroll()
            .padding(horizontal = 16.dp),
    ) {
        Material3SettingsGroup(
            title = stringResource(R.string.arc_settings_look),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.palette),
                        title = { Text(stringResource(R.string.theme)) },
                        // The theme screen's Dynamic palette is what turns cover colours on, so the row says which is in use.
                        description = { Text(stringResource(if (dynamicTheme) R.string.arc_theme_from_cover else R.string.theme_desc)) },
                        onClick = { navController.navigate("settings/appearance/theme") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.grid_view),
                        title = { Text(stringResource(R.string.display_density)) },
                        description = { Text(DensityScale.fromValue(densityScale).label) },
                        onClick = { showDensityScaleDialog = true },
                    ),
                    switchItem(R.drawable.speed, R.string.enable_high_refresh_rate, R.string.enable_high_refresh_rate_desc, enableHighRefreshRate, onEnableHighRefreshRateChange),
                    switchItem(R.drawable.palette, R.string.enable_dynamic_icon, R.string.enable_dynamic_icon_desc, enableDynamicIcon, onEnableDynamicIconChange),
                ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.home),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.gradient),
                        title = { Text(stringResource(R.string.arc_home_background)) },
                        description = { Text(homeBackgroundText(homeBackground)) },
                        onClick = { showHomeBackgroundDialog = true },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.grid_view),
                        title = { Text(stringResource(R.string.arc_speed_dial_rows)) },
                        description = { Text(pluralStringResource(R.plurals.arc_speed_dial_rows_value, speedDialRows, speedDialRows)) },
                        onClick = { showSpeedDialRowsDialog = true },
                    ),
                    switchItem(R.drawable.linear_scale, R.string.arc_home_line, R.string.arc_home_line_desc, homeLine, onHomeLineChange),
                    switchItem(R.drawable.group_outlined, R.string.listen_together_in_top_bar, R.string.listen_together_in_top_bar_desc, listenTogetherInTopBar, onListenTogetherInTopBarChange),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.nav_bar),
                        title = { Text(stringResource(R.string.default_open_tab)) },
                        description = {
                            Text(
                                when (defaultOpenTab) {
                                    NavigationTab.HOME -> stringResource(R.string.home)
                                    NavigationTab.SEARCH -> stringResource(R.string.search)
                                    NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                                },
                            )
                        },
                        onClick = { showDefaultOpenTabDialog = true },
                    ),
                ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.player),
            items =
                listOfNotNull(
                    switchItem(R.drawable.info, R.string.arc_audio_details, R.string.arc_audio_details_desc, audioDetails, onAudioDetailsChange),
                    switchItem(R.drawable.slow_motion_video, R.string.arc_motion_covers, R.string.arc_motion_covers_desc, motionCovers, onMotionCoversChange),
                    if (motionCovers) {
                        switchItem(R.drawable.signal_cellular, R.string.arc_motion_covers_mobile, R.string.arc_motion_covers_mobile_desc, motionCoversMobile, onMotionCoversMobileChange)
                    } else null,
                ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.arc_settings_library_search),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.tab),
                        title = { Text(stringResource(R.string.default_lib_chips)) },
                        description = {
                            Text(
                                when (defaultChip) {
                                    LibraryFilter.SONGS -> stringResource(R.string.songs)
                                    LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                                    LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                                    LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                                    LibraryFilter.PODCASTS -> stringResource(R.string.filter_podcasts)
                                    LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                                },
                            )
                        },
                        onClick = { showDefaultChipDialog = true },
                    ),
                    switchItem(R.drawable.arc_recognize, R.string.arc_search_mic, R.string.arc_search_mic_desc, searchMic, onSearchMicChange),
                ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.refresh),
                        title = { Text(stringResource(R.string.arc_reset_hints)) },
                        description = { Text(stringResource(R.string.arc_reset_hints_desc)) },
                        onClick = {
                            hintScope.launch {
                                resetHints(hintContext)
                                android.widget.Toast.makeText(hintContext, R.string.arc_reset_hints_done, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                    ),
                ),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.appearance)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun switchItem(icon: Int, title: Int, description: Int, checked: Boolean, onChange: (Boolean) -> Unit) =
    Material3SettingsItem(
        icon = painterResource(icon),
        title = { Text(stringResource(title)) },
        description = { Text(stringResource(description)) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                thumbContent = {
                    Icon(
                        painter = painterResource(if (checked) R.drawable.check else R.drawable.close),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
        onClick = { onChange(!checked) },
    )

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}
