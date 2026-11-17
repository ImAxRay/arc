/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import com.metrolist.music.arc.ArcSlider
import com.metrolist.music.arc.arcScroll
import com.metrolist.music.constants.AudioQualityDownloadKey
import com.metrolist.music.arc.PlayHoldAction
import com.metrolist.music.arc.PlayHoldActionKey
import com.metrolist.music.arc.snippet.ArcSnippetKeepKey
import com.metrolist.music.arc.snippet.Snippet
import com.metrolist.music.constants.AudioQualityMobileKey
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.metrolist.music.arc.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AudioNormalizationKey
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.constants.AudioQualityKey
import com.metrolist.music.constants.CrossfadeDurationKey
import com.metrolist.music.constants.CrossfadeEnabledKey
import com.metrolist.music.constants.AutoLoadMoreKey
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.AutoSkipNextOnErrorKey
import com.metrolist.music.constants.EnableGoogleCastKey
import com.metrolist.music.constants.KeepScreenOn
import com.metrolist.music.constants.LoudnessLevel
import com.metrolist.music.constants.LoudnessLevelKey
import com.metrolist.music.constants.PauseOnMute
import com.metrolist.music.arc.ArcStartupSongKey
import com.metrolist.music.arc.DefaultStartupSong
import com.metrolist.music.arc.StartupSong
import com.metrolist.music.arc.startupSongLabel
import com.metrolist.music.constants.PersistentShuffleAcrossQueuesKey
import com.metrolist.music.constants.PreventDuplicateTracksInQueueKey
import com.metrolist.music.constants.ResumeOnBluetoothConnectKey
import com.metrolist.music.constants.ShufflePlaylistFirstKey
import com.metrolist.music.constants.SimilarContent
import com.metrolist.music.constants.SkipSilenceInstantKey
import com.metrolist.music.constants.SkipSilenceKey
import com.metrolist.music.arc.ArcSkipOfftopicKey
import com.metrolist.music.constants.StopMusicOnTaskClearKey
import com.metrolist.music.constants.VarispeedKey
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.ui.component.SleepTimerDialog
import com.metrolist.music.constants.SleepTimerEnabledKey
import com.metrolist.music.constants.SleepTimerRepeatKey
import com.metrolist.music.constants.SleepTimerCustomDaysKey
import com.metrolist.music.constants.SleepTimerEndTimeKey
import com.metrolist.music.constants.SleepTimerStartTimeKey
import com.metrolist.music.constants.SleepTimerDayTimesKey
import com.metrolist.music.ui.component.decodeDayTimes
import com.metrolist.music.ui.component.encodeDayTimes
import com.metrolist.music.constants.SleepTimerFadeOutKey
import com.metrolist.music.constants.SleepTimerStopAfterCurrentSongKey
import com.metrolist.music.ui.utils.getLoudnessLevelLabel
import com.metrolist.music.arc.switchItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    section: String,
) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val (mobileQuality, onMobileQualityChange) = rememberEnumPreference(
        AudioQualityMobileKey,
        defaultValue = AudioQuality.LOW
    )
    var showMobileQualityDialog by remember { mutableStateOf(false) }
    val (downloadQuality, onDownloadQualityChange) = rememberEnumPreference(
        AudioQualityDownloadKey,
        defaultValue = AudioQuality.HIGH
    )
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false
    )
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = 5f
    )
    val (skipOfftopic, onSkipOfftopicChange) = rememberPreference(ArcSkipOfftopicKey, defaultValue = true)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false
    )
    val (skipSilenceInstant, onSkipSilenceInstantChange) = rememberPreference(
        SkipSilenceInstantKey,
        defaultValue = false
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true
    )

    val (loudnessLevel, onLoudnessLevelChange) = rememberEnumPreference(
        LoudnessLevelKey,
        defaultValue = LoudnessLevel.BALANCED,
    )

    val (varispeed, onVarispeedChange) = rememberPreference(
        key = VarispeedKey,
        defaultValue = false
    )

    val (enableGoogleCast, onEnableGoogleCastChange) = rememberPreference(
        key = EnableGoogleCastKey,
        defaultValue = true
    )

    val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(
        AutoLoadMoreKey,
        defaultValue = true
    )
    val (autoRadioQueue, onAutoRadioQueueChange) = rememberPreference(
        AutoRadioQueueKey,
        defaultValue = true
    )
    val (similarContentEnabled, similarContentEnabledChange) = rememberPreference(
        key = SimilarContent,
        defaultValue = true
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false
    )
    val (persistentShuffleAcrossQueues, onPersistentShuffleAcrossQueuesChange) = rememberPreference(
        PersistentShuffleAcrossQueuesKey,
        defaultValue = false
    )
    val (shufflePlaylistFirst, onShufflePlaylistFirstChange) = rememberPreference(
        ShufflePlaylistFirstKey,
        defaultValue = false
    )
    val (preventDuplicateTracksInQueue, onPreventDuplicateTracksInQueueChange) = rememberPreference(
        PreventDuplicateTracksInQueueKey,
        defaultValue = false
    )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = true
    )
    val (pauseOnMute, onPauseOnMuteChange) = rememberPreference(
        PauseOnMute,
        defaultValue = true
    )
    val (resumeOnBluetoothConnect, onResumeOnBluetoothConnectChange) = rememberPreference(
        ResumeOnBluetoothConnectKey,
        defaultValue = true
    )
    val (keepScreenOn, onKeepScreenOnChange) = rememberPreference(
        KeepScreenOn,
        defaultValue = false
    )

    val (startupSong, onStartupSongChange) = rememberEnumPreference(ArcStartupSongKey, DefaultStartupSong)
    var showStartupSongDialog by remember { mutableStateOf(false) }
    if (showStartupSongDialog) {
        EnumDialog(
            onDismiss = { showStartupSongDialog = false },
            onSelect = {
                onStartupSongChange(it)
                showStartupSongDialog = false
            },
            title = stringResource(R.string.startup_song),
            current = startupSong,
            values = StartupSong.entries,
            valueText = { startupSongLabel(it) },
        )
    }

    val (playHoldAction, onPlayHoldActionChange) = rememberEnumPreference(PlayHoldActionKey, PlayHoldAction.ASK)
    var showPlayHoldDialog by remember { mutableStateOf(false) }
    val (keepSnippets, onKeepSnippetsChange) = rememberPreference(ArcSnippetKeepKey, defaultValue = true)
    if (showPlayHoldDialog) {
        EnumDialog(
            onDismiss = { showPlayHoldDialog = false },
            onSelect = {
                onPlayHoldActionChange(it)
                showPlayHoldDialog = false
            },
            title = stringResource(R.string.arc_hold_play),
            current = playHoldAction,
            values = PlayHoldAction.entries.filter { it != PlayHoldAction.ASK },
            valueText = { stringResource(it.title) },
            valueDescription = { stringResource(it.description) },
        )
    }

    var showAudioQualityDialog by remember {
        mutableStateOf(false)
    }

    var showLoudnessLevelDialog by remember {
        mutableStateOf(false)
    }

    if (showAudioQualityDialog) {
        EnumDialog(
            onDismiss = { showAudioQualityDialog = false },
            onSelect = {
                onAudioQualityChange(it)
                showAudioQualityDialog = false
            },
            title = stringResource(R.string.arc_quality_wifi),
            current = audioQuality,
            values = AudioQuality.values().toList(),
            valueText = {
                when (it) {
                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                    AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                }
            }
        )
    }

    if (showMobileQualityDialog) {
        EnumDialog(
            onDismiss = { showMobileQualityDialog = false },
            onSelect = {
                onMobileQualityChange(it)
                showMobileQualityDialog = false
            },
            title = stringResource(R.string.arc_quality_mobile),
            current = mobileQuality,
            values = AudioQuality.values().toList(),
            valueText = { qualityLabel(it) }
        )
    }

    if (showDownloadQualityDialog) {
        EnumDialog(
            onDismiss = { showDownloadQualityDialog = false },
            onSelect = {
                onDownloadQualityChange(it)
                showDownloadQualityDialog = false
            },
            title = stringResource(R.string.arc_quality_download),
            current = downloadQuality,
            values = AudioQuality.values().toList(),
            valueText = { qualityLabel(it) }
        )
    }

    if (showLoudnessLevelDialog) {
        EnumDialog(
            onDismiss = { showLoudnessLevelDialog = false },
            onSelect = {
                onAudioNormalizationChange(it != null)
                it?.let(onLoudnessLevelChange)
                showLoudnessLevelDialog = false
            },
            title = stringResource(R.string.arc_even_loudness),
            current = loudnessLevel.takeIf { audioNormalization },
            values = listOf(null) + LoudnessLevel.entries,
            valueText = { level -> level?.let { getLoudnessLevelLabel(it) } ?: stringResource(R.string.arc_off) }
        )
    }

    val silenceMode = when {
        !skipSilence -> SILENCE_OFF
        skipSilenceInstant -> SILENCE_JUMP
        else -> SILENCE_SPEED
    }
    var showSilenceDialog by remember { mutableStateOf(false) }
    if (showSilenceDialog) {
        EnumDialog(
            onDismiss = { showSilenceDialog = false },
            onSelect = {
                onSkipSilenceChange(it != SILENCE_OFF)
                if (it != SILENCE_OFF) onSkipSilenceInstantChange(it == SILENCE_JUMP)
                showSilenceDialog = false
            },
            title = stringResource(R.string.skip_silence),
            current = silenceMode,
            values = listOf(SILENCE_OFF, SILENCE_SPEED, SILENCE_JUMP),
            valueText = { silenceLabel(it) }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .arcScroll()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        if (section == "sound") {
            Material3SettingsGroup(
                title = stringResource(R.string.arc_sound_quality),
                icon = painterResource(R.drawable.headphones),
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.wifi),
                        title = { Text(stringResource(R.string.arc_quality_wifi)) },
                        description = { Text(qualityLabel(audioQuality)) },
                        onClick = { showAudioQualityDialog = true }
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.signal_cellular),
                        title = { Text(stringResource(R.string.arc_quality_mobile)) },
                        description = { Text(qualityLabel(mobileQuality)) },
                        onClick = { showMobileQualityDialog = true }
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.download),
                        title = { Text(stringResource(R.string.arc_quality_download)) },
                        description = { Text(qualityLabel(downloadQuality)) },
                        onClick = { showDownloadQualityDialog = true }
                    ),
                )
            )
            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.arc_sound_between),
                icon = painterResource(R.drawable.linear_scale),
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.linear_scale),
                        title = { Text(stringResource(R.string.crossfade)) },
                        description = {
                            if (crossfadeEnabled) {
                                Column {
                                    Text(pluralStringResource(R.plurals.seconds, crossfadeDuration.toInt(), crossfadeDuration.toInt()))
                                    ArcSlider(
                                        value = crossfadeDuration,
                                        onValueChange = onCrossfadeDurationChange,
                                        valueRange = 1f..15f,
                                        steps = 14
                                    )
                                }
                            } else {
                                Text(stringResource(R.string.crossfade_desc))
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = crossfadeEnabled,
                                onCheckedChange = onCrossfadeEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(id = if (crossfadeEnabled) R.drawable.check else R.drawable.close),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onCrossfadeEnabledChange(!crossfadeEnabled) }
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.fast_forward),
                        title = { Text(stringResource(R.string.skip_silence)) },
                        description = { Text(silenceLabel(silenceMode)) },
                        onClick = { showSilenceDialog = true }
                    ),
                    switchItem(R.drawable.skip_next, R.string.arc_skip_offtopic, R.string.arc_skip_offtopic_desc, skipOfftopic, onSkipOfftopicChange),
                )
            )
            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.arc_sound_loudness),
                icon = painterResource(R.drawable.volume_up),
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.volume_up),
                        title = { Text(stringResource(R.string.arc_even_loudness)) },
                        description = {
                            Text(if (audioNormalization) getLoudnessLevelLabel(loudnessLevel) else stringResource(R.string.arc_off))
                        },
                        onClick = { showLoudnessLevelDialog = true }
                    ),
                )
            )
            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.arc_sound_controls),
                icon = painterResource(R.drawable.tune),
                items = buildList {
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.equalizer),
                        title = { Text(stringResource(R.string.equalizer)) },
                        opensPage = true,
                        onClick = { navController.navigate("equalizer") }
                    ))
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.play),
                        title = { Text(stringResource(R.string.arc_hold_play)) },
                        description = {
                            Text(stringResource(if (playHoldAction == PlayHoldAction.ASK) R.string.arc_hold_play_unset else playHoldAction.title))
                        },
                        onClick = { showPlayHoldDialog = true }
                    ))
                    add(switchItem(R.drawable.speed, R.string.arc_varispeed, R.string.arc_varispeed_desc, varispeed, onVarispeedChange))
                    // Only in GMS builds (not F-Droid/FOSS)
                    if (BuildConfig.CAST_AVAILABLE) {
                        add(switchItem(R.drawable.cast, R.string.google_cast, R.string.google_cast_description, enableGoogleCast, onEnableGoogleCastChange))
                    }
                }
            )
            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.arc_snippets),
                icon = painterResource(R.drawable.mic),
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.list),
                        title = { Text(stringResource(R.string.arc_snippets)) },
                        description = { Text(stringResource(R.string.arc_snippets_desc)) },
                        onClick = { Snippet.listOpen.value = true }
                    ),
                    switchItem(R.drawable.mic, R.string.arc_snippet_keep, R.string.arc_snippet_keep_desc, keepSnippets, onKeepSnippetsChange),
                )
            )

            Spacer(modifier = Modifier.height(27.dp))
            com.metrolist.music.arc.MusicVideoSettings()
        }

        var showSleepTimerDialog by remember { mutableStateOf(false) }

        val (sleepTimerEnabled, onSleepTimerEnabledChange) = rememberPreference(
            SleepTimerEnabledKey,
            defaultValue = false
        )
        val (sleepTimerRepeat, onSleepTimerRepeatChange) = rememberPreference(
            SleepTimerRepeatKey,
            defaultValue = "daily"
        )
        val (sleepTimerStartTime, onSleepTimerStartTimeChange) = rememberPreference(
            SleepTimerStartTimeKey,
            defaultValue = "22:00"
        )
        val (sleepTimerEndTime, onSleepTimerEndTimeChange) = rememberPreference(
            SleepTimerEndTimeKey,
            defaultValue = "06:00"
        )
        val (sleepTimerCustomDays, onSleepTimerCustomDaysChange) = rememberPreference(
            SleepTimerCustomDaysKey,
            defaultValue = "0,1,2,3,4"
        )
        // Per-day time ranges used in custom mode
        val (sleepTimerDayTimes, onSleepTimerDayTimesChange) = rememberPreference(
            SleepTimerDayTimesKey,
            defaultValue = ""
        )

        val (sleepTimerStopAfterCurrentSong, onSleepTimerStopAfterCurrentSongChange) = rememberPreference (
        SleepTimerStopAfterCurrentSongKey,
        defaultValue = false)
        val (sleepTimerFadeOut, onSleepTimerFadeOutChange) = rememberPreference(
            SleepTimerFadeOutKey,
            false
        )

        if (showSleepTimerDialog) {
            val customDays = sleepTimerCustomDays.split(",").mapNotNull { it.toIntOrNull() }
            val dayTimesMap = decodeDayTimes(sleepTimerDayTimes)

            SleepTimerDialog(
                isVisible = true,
                onDismiss = { showSleepTimerDialog = false },
                onConfirm = { repeat, startTime, endTime, days, dayTimes ->
                    onSleepTimerRepeatChange(repeat)
                    onSleepTimerStartTimeChange(startTime)
                    onSleepTimerEndTimeChange(endTime)
                    onSleepTimerCustomDaysChange(days?.joinToString(",") ?: "0,1,2,3,4")
                    onSleepTimerDayTimesChange(encodeDayTimes(dayTimes))
                    showSleepTimerDialog = false
                },
                initialRepeat = sleepTimerRepeat,
                initialStartTime = sleepTimerStartTime,
                initialEndTime = sleepTimerEndTime,
                initialCustomDays = customDays,
                initialDayTimes = dayTimesMap
            )
        }

        if (section == "queue") {
            Material3SettingsGroup(
                title = stringResource(R.string.arc_queue_starting),
                icon = painterResource(R.drawable.play),
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.play),
                        title = { Text(stringResource(R.string.startup_song)) },
                        description = { Text(startupSongLabel(startupSong)) },
                        onClick = { showStartupSongDialog = true }
                    ),
                    switchItem(R.drawable.radio, R.string.arc_queue_radio, R.string.auto_radio_queue_desc, autoRadioQueue, onAutoRadioQueueChange),
                )
            )
            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.arc_queue_ends),
                icon = painterResource(R.drawable.playlist_add),
                items = listOf(
                    switchItem(R.drawable.playlist_add, R.string.arc_queue_keep_going, R.string.arc_queue_keep_going_desc, autoLoadMore || similarContentEnabled, {
                        onAutoLoadMoreChange(it)
                        similarContentEnabledChange(it)
                    }),
                )
            )
            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.shuffle),
                icon = painterResource(R.drawable.shuffle),
                items = listOf(
                    switchItem(R.drawable.shuffle, R.string.arc_shuffle_keep, R.string.persistent_shuffle_desc, persistentShuffleAcrossQueues, onPersistentShuffleAcrossQueuesChange),
                    switchItem(R.drawable.shuffle_on, R.string.arc_shuffle_playlist_first, R.string.shuffle_playlist_first_desc, shufflePlaylistFirst, onShufflePlaylistFirstChange),
                )
            )
            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.queue),
                icon = painterResource(R.drawable.queue_music),
                items = listOf(
                    switchItem(R.drawable.remove, R.string.arc_no_duplicates, R.string.prevent_duplicate_tracks_in_queue_desc, preventDuplicateTracksInQueue, onPreventDuplicateTracksInQueueChange),
                    switchItem(R.drawable.skip_next, R.string.arc_skip_unplayable, R.string.arc_skip_unplayable_desc, autoSkipNextOnError, onAutoSkipNextOnErrorChange),
                )
            )
            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.sleep_timer),
                icon = painterResource(R.drawable.bedtime),
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.time_auto),
                        title = { Text(stringResource(R.string.enable_automatic_sleeptimer)) },
                        description = {
                            Text(
                                if (sleepTimerEnabled) {
                                    stringResource(
                                        when (sleepTimerRepeat) {
                                            "daily" -> R.string.sleep_timer_daily
                                            "custom" -> R.string.sleep_timer_custom
                                            else -> R.string.sleep_timer_weekdays_weekends
                                        }
                                    ) + if (sleepTimerRepeat == "custom") "" else " · $sleepTimerStartTime–$sleepTimerEndTime"
                                } else {
                                    stringResource(R.string.sleeptimer_description)
                                }
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = sleepTimerEnabled,
                                onCheckedChange = onSleepTimerEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(id = if (sleepTimerEnabled) R.drawable.check else R.drawable.close),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { showSleepTimerDialog = true }
                    ),
                    switchItem(R.drawable.more_time, R.string.sleep_timer_stop_after_current_song_title, R.string.sleep_timer_stop_after_current_song_description, sleepTimerStopAfterCurrentSong, onSleepTimerStopAfterCurrentSongChange),
                    switchItem(R.drawable.timer_arrow_down, R.string.sleep_timer_fade_out_title, R.string.sleep_timer_fade_out_description, sleepTimerFadeOut, onSleepTimerFadeOutChange),
                )
            )
            Spacer(modifier = Modifier.height(27.dp))

            AlarmSettingsSection()
            Spacer(modifier = Modifier.height(27.dp))
        }

        if (section == "sound") {
            Material3SettingsGroup(
                title = stringResource(R.string.arc_sound_while_playing),
                icon = painterResource(R.drawable.play),
                items = listOf(
                    switchItem(R.drawable.screenshot, R.string.arc_keep_screen_on, R.string.arc_keep_screen_on_desc, keepScreenOn, onKeepScreenOnChange, enabled = !com.metrolist.music.arc.rememberVisualsStill()),
                    switchItem(R.drawable.volume_off_pause, R.string.pause_music_when_media_is_muted, null, pauseOnMute, onPauseOnMuteChange),
                    switchItem(R.drawable.bluetooth, R.string.resume_on_bluetooth_connect, null, resumeOnBluetoothConnect, onResumeOnBluetoothConnectChange),
                    switchItem(R.drawable.clear_all, R.string.stop_music_on_task_clear, null, stopMusicOnTaskClear, onStopMusicOnTaskClearChange),
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    TopAppBar(
        title = {
            Text(
                stringResource(if (section == "sound") R.string.arc_settings_sound else R.string.arc_settings_queue)
            )
        },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}

@Composable
private fun qualityLabel(quality: AudioQuality): String =
    when (quality) {
        AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
        AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
        AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
    }

private const val SILENCE_OFF = 0
private const val SILENCE_SPEED = 1
private const val SILENCE_JUMP = 2

@Composable
private fun silenceLabel(mode: Int): String = stringResource(
    when (mode) {
        SILENCE_SPEED -> R.string.arc_silence_speed
        SILENCE_JUMP -> R.string.arc_silence_jump
        else -> R.string.arc_off
    }
)
