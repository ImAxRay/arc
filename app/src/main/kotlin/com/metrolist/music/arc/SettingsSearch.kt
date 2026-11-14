package com.metrolist.music.arc

import com.metrolist.music.arc.snippet.ArcSnippetKeepKey
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.R
import android.content.Context
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.constants.AudioNormalizationKey
import com.metrolist.music.constants.AudioQualityDownloadKey
import com.metrolist.music.constants.AudioQualityKey
import com.metrolist.music.constants.AudioQualityMobileKey
import com.metrolist.music.constants.AutoplayKey
import com.metrolist.music.constants.CrossfadeDurationKey
import com.metrolist.music.constants.CrossfadeEnabledKey
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.HistoryDuration
import com.metrolist.music.constants.KeepScreenOn
import com.metrolist.music.constants.PauseOnMute
import com.metrolist.music.constants.ResumeOnBluetoothConnectKey
import com.metrolist.music.constants.SkipSilenceKey
import com.metrolist.music.constants.StopMusicOnTaskClearKey
import com.metrolist.music.constants.VarispeedKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference

private class SettingsEntry(val title: Int, val route: String, val section: Int)

private val RecentSettingsKey = stringPreferencesKey("arcRecentSettings")
private const val RECENT_SETTINGS = 3

private val LOOKS = R.string.appearance

// Preference key → the row that changes it. Only these count as "recently changed"; the rest of the
// store also holds things like volume and the queue, which change all the time.
private val TRACKED_SETTINGS: Map<String, SettingsEntry> =
    mapOf(
        AudioQualityKey.name to SettingsEntry(R.string.arc_quality_wifi, "settings/sound", R.string.arc_settings_sound),
        AudioQualityMobileKey.name to SettingsEntry(R.string.arc_quality_mobile, "settings/sound", R.string.arc_settings_sound),
        AudioQualityDownloadKey.name to SettingsEntry(R.string.arc_quality_download, "settings/sound", R.string.arc_settings_sound),
        CrossfadeEnabledKey.name to SettingsEntry(R.string.crossfade, "settings/sound", R.string.arc_settings_sound),
        CrossfadeDurationKey.name to SettingsEntry(R.string.crossfade_duration, "settings/sound", R.string.arc_settings_sound),
        HistoryDuration.name to SettingsEntry(R.string.arc_history_duration, "settings/sound", R.string.arc_settings_sound),
        SkipSilenceKey.name to SettingsEntry(R.string.skip_silence, "settings/sound", R.string.arc_settings_sound),
        ArcSkipOfftopicKey.name to SettingsEntry(R.string.arc_skip_offtopic, "settings/sound", R.string.arc_settings_sound),
        ArcMusicVideosKey.name to SettingsEntry(R.string.arc_music_videos, "settings/sound", R.string.arc_settings_sound),
        ArcVideoQualityWifiKey.name to SettingsEntry(R.string.arc_video_quality_wifi, "settings/sound", R.string.arc_settings_sound),
        ArcVideoQualityMobileKey.name to SettingsEntry(R.string.arc_video_quality_mobile, "settings/sound", R.string.arc_settings_sound),
        ArcRadioSongsOnlyKey.name to SettingsEntry(R.string.arc_radio_songs_only, "settings/sound", R.string.arc_settings_sound),
        ArcSearchVideosKey.name to SettingsEntry(R.string.arc_search_videos, "settings/sound", R.string.arc_settings_sound),
        AudioNormalizationKey.name to SettingsEntry(R.string.audio_normalization, "settings/sound", R.string.arc_settings_sound),
        VarispeedKey.name to SettingsEntry(R.string.arc_varispeed, "settings/sound", R.string.arc_settings_sound),
        AutoplayKey.name to SettingsEntry(R.string.autoplay, "settings/queue", R.string.arc_settings_queue),
        StopMusicOnTaskClearKey.name to SettingsEntry(R.string.stop_music_on_task_clear, "settings/sound", R.string.arc_settings_sound),
        PauseOnMute.name to SettingsEntry(R.string.pause_music_when_media_is_muted, "settings/sound", R.string.arc_settings_sound),
        ResumeOnBluetoothConnectKey.name to SettingsEntry(R.string.resume_on_bluetooth_connect, "settings/sound", R.string.arc_settings_sound),
        KeepScreenOn.name to SettingsEntry(R.string.arc_keep_screen_on, "settings/sound", R.string.arc_settings_sound),
        PlayHoldActionKey.name to SettingsEntry(R.string.arc_hold_play, "settings/sound", R.string.arc_settings_sound),
        ArcSnippetKeepKey.name to SettingsEntry(R.string.arc_snippet_keep, "settings/sound", R.string.arc_settings_sound),
        ArcStartupSongKey.name to SettingsEntry(R.string.startup_song, "settings/queue", R.string.arc_settings_queue),
        DarkModeKey.name to SettingsEntry(R.string.theme, "settings/appearance", LOOKS),
        SearchMicKey.name to SettingsEntry(R.string.arc_search_mic, "settings/appearance", LOOKS),
        ArcHomeLineKey.name to SettingsEntry(R.string.arc_home_line, "settings/appearance", LOOKS),
        ArcHomeBackgroundKey.name to SettingsEntry(R.string.arc_home_background, "settings/appearance", LOOKS),
        ArcTiltCoversKey.name to SettingsEntry(R.string.arc_tilt_covers, "settings/appearance", LOOKS),
        SpeedDialRowsKey.name to SettingsEntry(R.string.arc_speed_dial_rows, "settings/appearance", LOOKS),
        ArcAudioDetailsKey.name to SettingsEntry(R.string.arc_audio_details, "settings/appearance", LOOKS),
        ArcMotionCoversKey.name to SettingsEntry(R.string.arc_motion_covers, "settings/appearance", LOOKS),
        ArcMotionCoversMobileKey.name to SettingsEntry(R.string.arc_motion_covers_mobile, "settings/appearance", LOOKS),
    )

/**
 * Watches the preference store and remembers which tracked settings changed last, wherever they
 * were changed from, so the settings home can offer them first.
 */
suspend fun trackRecentSettings(context: Context) {
    var before: Map<String, Any>? = null
    context.dataStore.data.collect { prefs ->
        val now = prefs.asMap().mapKeys { it.key.name }
        val previous = before
        before = now
        if (previous == null) return@collect
        val changed = TRACKED_SETTINGS.keys.filter { previous[it] != now[it] }
        if (changed.isEmpty()) return@collect
        context.dataStore.edit { store ->
            val recent = store[RecentSettingsKey].orEmpty().split(',').filter { it.isNotEmpty() }
            store[RecentSettingsKey] = (changed + recent).distinct().take(RECENT_SETTINGS).joinToString(",")
        }
    }
}

/** The last few settings the user changed, at the top of the settings home. */
@Composable
fun RecentSettings(navController: NavController) {
    val recent by rememberPreference(RecentSettingsKey, "")
    val entries = recent.split(',').mapNotNull { TRACKED_SETTINGS[it] }
    if (entries.isEmpty()) return
    Text(
        stringResource(R.string.arc_recent_settings),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
    )
    val context = LocalContext.current
    entries.forEach { entry ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        SettingsHighlight.pending = context.getString(entry.title)
                        navController.navigate(entry.route)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(entry.section), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp))
}

/** Search box for the settings home screen. Returns true while a search is showing, so the normal list can hide. */
@Composable
fun ArcSettingsSearch(navController: NavController): Boolean {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val entries = remember {
        SETTINGS_INDEX.map { Triple(context.getString(it.title), context.getString(it.section), it.route) }
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(stringResource(R.string.settings_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.search), null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.close), null) }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return false
    entries.filter { it.first.contains(trimmed, ignoreCase = true) }.forEach { (title, section, route) ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        SettingsHighlight.pending = title
                        navController.navigate(route)
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(section, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    return true
}

// Built from the titles in ui/screens/settings. A row added upstream won't appear here until it's listed.
private val SETTINGS_INDEX = listOf(
    SettingsEntry(R.string.ai_lyrics_translation, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.ai_provider_help, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.ai_translation_mode, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.ai_api_key, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.ai_base_url, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.ai_model, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.ai_system_prompt, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.ai_provider, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.ai_deepl_formality, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.ai_target_language, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.ai_setup_guide, "settings/ai", R.string.ai_lyrics_translation),
    SettingsEntry(R.string.arc_settings_sound, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.alarm_exact_permission_title, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.alarm_battery_optimization_title, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.alarm_add, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.alarm_empty, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.android_auto_target_playlist, "settings/android_auto", R.string.android_auto_target_playlist),
    SettingsEntry(R.string.android_auto_youtube_playlists, "settings/android_auto", R.string.android_auto_target_playlist),
    SettingsEntry(R.string.android_auto_search_local_songs_limit, "settings/android_auto", R.string.android_auto_target_playlist),
    SettingsEntry(R.string.android_auto, "settings/android_auto", R.string.android_auto_target_playlist),
    SettingsEntry(R.string.android_auto_visible_sections, "settings/android_auto", R.string.android_auto_target_playlist),
    SettingsEntry(R.string.mixes, "settings/android_auto", R.string.android_auto_target_playlist),
    SettingsEntry(R.string.android_auto_search_options, "settings/android_auto", R.string.android_auto_target_playlist),
    SettingsEntry(R.string.appearance, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.arc_search_mic, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.arc_home_line, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.arc_motion_covers, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.arc_untitled, "settings/untitled", R.string.arc_settings_sources),
    SettingsEntry(R.string.arc_motion_covers_mobile, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.arc_reset_hints, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.enable_high_refresh_rate, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.enable_dynamic_icon, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.theme, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.default_open_tab, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.default_lib_chips, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.listen_together_in_top_bar, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.display_density, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.show_liked_playlist, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.show_downloaded_playlist, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.show_top_playlist, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.show_cached_playlist, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.show_uploaded_playlist, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.misc, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.auto_playlists, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.backup_restore, "settings/backup_restore", R.string.backup_restore),
    SettingsEntry(R.string.action_backup, "settings/backup_restore", R.string.backup_restore),
    SettingsEntry(R.string.action_restore, "settings/backup_restore", R.string.backup_restore),
    SettingsEntry(R.string.import_online, "settings/backup_restore", R.string.backup_restore),
    SettingsEntry(R.string.import_csv, "settings/backup_restore", R.string.backup_restore),
    SettingsEntry(R.string.arc_spotify_import, "settings/backup_restore", R.string.backup_restore),
    SettingsEntry(R.string.restore_confirm_title, "settings/backup_restore", R.string.backup_restore),
    SettingsEntry(R.string.content, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.lyrics_provider_selection, "settings/lyrics", R.string.lyrics),
    SettingsEntry(R.string.top_length, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.lyrics_provider_priority, "settings/lyrics", R.string.lyrics),
    SettingsEntry(R.string.content_language, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.content_country, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.hide_explicit, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.hide_video_songs, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.hide_youtube_shorts, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.add_to_playlist_position, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.show_artist_description, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.show_artist_subscriber_count, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.show_artist_monthly_listeners, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.app_language, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.enable_proxy, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.config_proxy, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.lyrics_romanization, "settings/lyrics", R.string.lyrics),
    SettingsEntry(R.string.show_most_stats_playlists, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.show_wrapped_card, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.randomize_home_order, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.set_quick_picks, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.general, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.playlists, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.artist_page_settings, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.proxy, "settings/region", R.string.arc_settings_region),
    SettingsEntry(R.string.lyrics, "settings/lyrics", R.string.lyrics),
    SettingsEntry(R.string.misc, "settings/library", R.string.arc_settings_library),
    SettingsEntry(R.string.crossfade, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.crossfade_duration, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.crossfade_gapless, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.arc_history_duration, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.skip_silence, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.skip_silence_instant, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.audio_normalization, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.loudness_level, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.audio_offload, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.arc_varispeed, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.audio_track_playback_params, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.google_cast, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.seek_seconds_addup, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.enable_automatic_sleeptimer, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.sleep_timer_repeat, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.sleep_timer_stop_after_current_song_title, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.sleep_timer_fade_out_title, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.startup_song, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.arc_hold_play, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.auto_load_more, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.auto_radio_queue, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.autoplay, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.disable_load_more_when_repeat_all, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.auto_download_on_like, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.enable_similar_content, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.persistent_shuffle_title, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.remember_shuffle_and_repeat, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.shuffle_playlist_first, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.prevent_duplicate_tracks_in_queue, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.auto_skip_next_on_error, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.stop_music_on_task_clear, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.pause_music_when_media_is_muted, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.resume_on_bluetooth_connect, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.arc_keep_screen_on, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.player, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.sleep_timer, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.queue, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.misc, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.privacy, "settings/privacy", R.string.privacy),
    SettingsEntry(R.string.pause_listen_history, "settings/privacy", R.string.privacy),
    SettingsEntry(R.string.clear_listen_history, "settings/privacy", R.string.privacy),
    SettingsEntry(R.string.pause_search_history, "settings/privacy", R.string.privacy),
    SettingsEntry(R.string.clear_search_history, "settings/privacy", R.string.privacy),
    SettingsEntry(R.string.disable_screenshot, "settings/privacy", R.string.privacy),
    SettingsEntry(R.string.listen_history, "settings/privacy", R.string.privacy),
    SettingsEntry(R.string.search_history, "settings/privacy", R.string.privacy),
    SettingsEntry(R.string.misc, "settings/privacy", R.string.privacy),
    SettingsEntry(R.string.lyrics_romanize_as_main, "settings/content/romanization", R.string.content),
    SettingsEntry(R.string.line_by_line_option_title, "settings/content/romanization", R.string.content),
    SettingsEntry(R.string.lyrics_romanize_title, "settings/content/romanization", R.string.content),
    SettingsEntry(R.string.options, "settings/content/romanization", R.string.content),
    SettingsEntry(R.string.storage, "settings/storage", R.string.storage),
    SettingsEntry(R.string.cache_size_warning_title, "settings/storage", R.string.storage),
    SettingsEntry(R.string.downloaded_songs, "settings/storage", R.string.storage),
    SettingsEntry(R.string.clear_all_downloads, "settings/storage", R.string.storage),
    SettingsEntry(R.string.enable_song_cache, "settings/storage", R.string.storage),
    SettingsEntry(R.string.max_song_cache_size, "settings/storage", R.string.storage),
    SettingsEntry(R.string.clear_song_cache, "settings/storage", R.string.storage),
    SettingsEntry(R.string.max_image_cache_size, "settings/storage", R.string.storage),
    SettingsEntry(R.string.clear_image_cache, "settings/storage", R.string.storage),
    SettingsEntry(R.string.song_cache, "settings/storage", R.string.storage),
    SettingsEntry(R.string.image_cache, "settings/storage", R.string.storage),
    SettingsEntry(R.string.theme_colors, "settings/appearance/theme", R.string.appearance),
    SettingsEntry(R.string.updater, "settings/updater", R.string.updater),
    SettingsEntry(R.string.check_for_updates, "settings/updater", R.string.updater),
    SettingsEntry(R.string.update_notifications, "settings/updater", R.string.updater),
    SettingsEntry(R.string.current_version, "settings/updater", R.string.updater),
    SettingsEntry(R.string.update_settings, "settings/updater", R.string.updater),
    SettingsEntry(R.string.check_for_updates_title, "settings/updater", R.string.updater),
    SettingsEntry(R.string.discord_integration, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.enable_discord_rpc, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.discord_advanced_mode, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.discord_activity_type, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.discord_activity_name, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.discord_state, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.discord_details, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.discord_enable_button_1, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.discord_enable_button_2, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.discord_status, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.options, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.discord_presence, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.discord_buttons, "settings/integrations/discord", R.string.discord_integration),
    SettingsEntry(R.string.integrations, "settings/integrations", R.string.integrations),
    SettingsEntry(R.string.discord_integration, "settings/integrations", R.string.integrations),
    SettingsEntry(R.string.lastfm_integration, "settings/integrations", R.string.integrations),
    SettingsEntry(R.string.general, "settings/integrations", R.string.integrations),
    SettingsEntry(R.string.lastfm_integration, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.login, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.enable_scrobbling, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.lastfm_now_playing, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.last_fm_send_likes, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.scrobble_min_track_duration, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.scrobble_delay_percent, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.scrobble_delay_minutes, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.account, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.options, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.scrobbling_configuration, "settings/integrations/lastfm", R.string.lastfm_integration),
    SettingsEntry(R.string.integrations, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_username, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_create_room, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_join_room, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_blocked_users, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_server_url, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_auto_approval_joins, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_auto_approval_suggestions, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_sync_volume, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_view_logs, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_logs, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.listen_together_choose_server, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.settings, "settings/integrations/listen_together", R.string.integrations),
    SettingsEntry(R.string.arc_settings_look, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.arc_tilt_covers, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.home, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.arc_home_background, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.arc_speed_dial_rows, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.arc_audio_details, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.arc_settings_library_search, "settings/appearance", R.string.appearance),
    SettingsEntry(R.string.equalizer, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.arc_quality_wifi, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.arc_quality_mobile, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.arc_quality_download, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.arc_snippet_keep, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.arc_snippets, "settings/sound", R.string.arc_settings_sound),
    SettingsEntry(R.string.alarm, "settings/queue", R.string.arc_settings_queue),
    SettingsEntry(R.string.arc_drive, "settings/drive", R.string.arc_settings_sources),
    SettingsEntry(R.string.arc_addons, "settings/addons", R.string.arc_settings_sources),
    SettingsEntry(R.string.arc_japanese_dictionary, "settings/content/romanization", R.string.lyrics_romanization),
    SettingsEntry(R.string.romanize_languages, "settings/content/romanization", R.string.lyrics_romanization),
)
