/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import com.metrolist.music.arc.ArcSlider
import com.metrolist.music.constants.ShowCachedPlaylistKey
import com.metrolist.music.constants.ShowDownloadedPlaylistKey
import com.metrolist.music.constants.ShowLikedPlaylistKey
import com.metrolist.music.constants.ShowTopPlaylistKey
import com.metrolist.music.constants.ShowUploadedPlaylistKey
import com.metrolist.music.arc.arcScroll
import com.metrolist.music.arc.switchItem
import android.content.Intent
import android.app.LocaleManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.metrolist.music.arc.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AddToPlaylistPosition
import com.metrolist.music.constants.AddToPlaylistPositionKey
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.ContentCountryKey
import com.metrolist.music.constants.ContentLanguageKey
import com.metrolist.music.constants.CountryCodeToName
import com.metrolist.music.constants.EnableBetterLyricsKey
import com.metrolist.music.constants.EnableKugouKey
import com.metrolist.music.constants.EnableLrcLibKey
import com.metrolist.music.constants.EnablePaxsenixKey
import com.metrolist.music.constants.EnableLyricsPlus
import com.metrolist.music.constants.EnableZemerKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.LanguageCodeToName
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.constants.ProxyEnabledKey
import com.metrolist.music.constants.ProxyPasswordKey
import com.metrolist.music.constants.ProxyTypeKey
import com.metrolist.music.constants.ProxyUrlKey
import com.metrolist.music.constants.ProxyUsernameKey
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.constants.ShowMostStatsPlaylistsKey
import com.metrolist.music.constants.TopSize
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.DraggableLyricsProviderItem
import com.metrolist.music.ui.component.DraggableLyricsProviderList
import com.metrolist.music.lyrics.LyricsProviderRegistry
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import java.net.Proxy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    section: String? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    // Used only before Android 13
    val (appLanguage, onAppLanguageChange) = rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)
    val (showLikedPlaylist, onShowLikedPlaylistChange) =
        rememberPreference(
            ShowLikedPlaylistKey,
            defaultValue = true,
        )
    val (showDownloadedPlaylist, onShowDownloadedPlaylistChange) =
        rememberPreference(
            ShowDownloadedPlaylistKey,
            defaultValue = true,
        )
    val (showTopPlaylist, onShowTopPlaylistChange) =
        rememberPreference(
            ShowTopPlaylistKey,
            defaultValue = true,
        )
    val (showCachedPlaylist, onShowCachedPlaylistChange) =
        rememberPreference(
            ShowCachedPlaylistKey,
            defaultValue = true,
        )
    val (showUploadedPlaylist, onShowUploadedPlaylistChange) =
        rememberPreference(
            ShowUploadedPlaylistKey,
            defaultValue = true,
        )


    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (hideVideoSongs, onHideVideoSongsChange) = rememberPreference(key = HideVideoSongsKey, defaultValue = false)
    val (hideYoutubeShorts, onHideYoutubeShortsChange) = rememberPreference(key = HideYoutubeShortsKey, defaultValue = false)
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")
    val (proxyUsername, onProxyUsernameChange) = rememberPreference(key = ProxyUsernameKey, defaultValue = "username")
    val (proxyPassword, onProxyPasswordChange) = rememberPreference(key = ProxyPasswordKey, defaultValue = "password")
    val (enableZemer, onEnableZemerChange) = rememberPreference(key = EnableZemerKey, defaultValue = true)
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(key = EnableBetterLyricsKey, defaultValue = true)
    val (enablePaxsenix, onEnablePaxsenixChange) = rememberPreference(key = EnablePaxsenixKey, defaultValue = true)
    val (enableLyricsPlus, onEnableLyricsPlusChange) = rememberPreference(key = EnableLyricsPlus, defaultValue = true)
    val (lyricsProviderOrder, onLyricsProviderOrderChange) = rememberPreference(
        key = LyricsProviderOrderKey,
        defaultValue = LyricsProviderRegistry.serializeProviderOrder(LyricsProviderRegistry.getDefaultProviderOrder())
    )
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    val (showMostStatsPlaylists, onShowMostStatsPlaylistsChange) =
        rememberPreference(key = ShowMostStatsPlaylistsKey, defaultValue = true)
    val (addToPlaylistPosition, onAddToPlaylistPositionChange) = rememberEnumPreference(
        AddToPlaylistPositionKey,
        AddToPlaylistPosition.BEGINNING,
    )

    LaunchedEffect(showMostStatsPlaylists) {
        if (!showMostStatsPlaylists) {
            database.withTransaction {
                clearPlaylist(com.metrolist.music.db.entities.PlaylistEntity.WEEKLY_MOST_PLAYLIST_ID)
                clearPlaylist(com.metrolist.music.db.entities.PlaylistEntity.MONTHLY_MOST_PLAYLIST_ID)
                delete(
                    com.metrolist.music.db.entities.PlaylistEntity(
                        id = com.metrolist.music.db.entities.PlaylistEntity.WEEKLY_MOST_PLAYLIST_ID,
                        name = "",
                    ),
                )
                delete(
                    com.metrolist.music.db.entities.PlaylistEntity(
                        id = com.metrolist.music.db.entities.PlaylistEntity.MONTHLY_MOST_PLAYLIST_ID,
                        name = "",
                    ),
                )
            }
        }
    }

    val providerDisplayNames =
        mapOf(
            "BetterLyrics" to "Better Lyrics",
            "Paxsenix" to "Paxsenix",
            "LrcLib" to "LrcLib",
            "KuGou" to "KuGou",
            "LyricsPlus" to "LyricsPlus",
            "Zemer" to "Zemer",
            "YouTubeSubtitle" to "YouTube Subtitles",
            "YouTube" to "YouTube",
        )

    var showProxyConfigurationDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showProxyConfigurationDialog) {
        var expandedDropdown by remember { mutableStateOf(false) }

        var tempProxyUrl by rememberSaveable { mutableStateOf(proxyUrl) }
        var tempProxyUsername by rememberSaveable { mutableStateOf(proxyUsername) }
        var tempProxyPassword by rememberSaveable { mutableStateOf(proxyPassword) }
        var authEnabled by rememberSaveable { mutableStateOf(proxyUsername.isNotBlank() || proxyPassword.isNotBlank()) }
        var tempEnabled by rememberSaveable { mutableStateOf(proxyEnabled) }

        AlertDialog(
            onDismissRequest = { showProxyConfigurationDialog = false },
            title = {
                Text(stringResource(R.string.proxy))
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .arcScroll(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.enable_proxy))
                        Switch(checked = tempEnabled, onCheckedChange = { tempEnabled = it })
                    }
                    Text(
                        stringResource(R.string.restart_to_take_effect),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExposedDropdownMenuBox(
                        expanded = expandedDropdown,
                        onExpandedChange = { expandedDropdown = !expandedDropdown },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = proxyType.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.proxy_type)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS).forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        onProxyTypeChange(type)
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = tempProxyUrl,
                        onValueChange = { tempProxyUrl = it },
                        label = { Text(stringResource(R.string.proxy_url)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.enable_authentication))
                        Switch(
                            checked = authEnabled,
                            onCheckedChange = {
                                authEnabled = it
                                if (!it) {
                                    tempProxyUsername = ""
                                    tempProxyPassword = ""
                                }
                            }
                        )
                    }

                    AnimatedVisibility(visible = authEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempProxyUsername,
                                onValueChange = { tempProxyUsername = it },
                                label = { Text(stringResource(R.string.proxy_username)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tempProxyPassword,
                                onValueChange = { tempProxyPassword = it },
                                label = { Text(stringResource(R.string.proxy_password)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onProxyEnabledChange(tempEnabled)
                        onProxyUrlChange(tempProxyUrl)
                        onProxyUsernameChange(if (authEnabled) tempProxyUsername else "")
                        onProxyPasswordChange(if (authEnabled) tempProxyPassword else "")
                        showProxyConfigurationDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProxyConfigurationDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val videoFilter = if (hideVideoSongs) 2 else if (hideYoutubeShorts) 1 else 0
    var showVideosDialog by rememberSaveable { mutableStateOf(false) }
    if (showVideosDialog) {
        EnumDialog(
            onDismiss = { showVideosDialog = false },
            onSelect = {
                onHideVideoSongsChange(it == 2)
                onHideYoutubeShortsChange(it >= 1)
                showVideosDialog = false
            },
            title = stringResource(R.string.arc_videos),
            current = videoFilter,
            values = listOf(0, 1, 2),
            valueText = { stringResource(videosLabel(it)) }
        )
    }

    var showContentLanguageDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showContentLanguageDialog) {
        EnumDialog(
            onDismiss = { showContentLanguageDialog = false },
            onSelect = {
                onContentLanguageChange(it)
                showContentLanguageDialog = false
            },
            title = stringResource(R.string.content_language),
            current = contentLanguage,
            values = (listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList()),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showContentCountryDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showContentCountryDialog) {
        EnumDialog(
            onDismiss = { showContentCountryDialog = false },
            onSelect = {
                onContentCountryChange(it)
                showContentCountryDialog = false
            },
            title = stringResource(R.string.content_country),
            current = contentCountry,
            values = (listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList()),
            valueText = {
                CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showAppLanguageDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showAppLanguageDialog) {
        EnumDialog(
            onDismiss = { showAppLanguageDialog = false },
            onSelect = {
                onAppLanguageChange(it)
                showAppLanguageDialog = false
            },
            title = stringResource(R.string.app_language),
            current = appLanguage,
            values = (listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList()),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    val autoPlaylists = listOf(
        Triple(R.string.liked, showLikedPlaylist, onShowLikedPlaylistChange),
        Triple(R.string.offline, showDownloadedPlaylist, onShowDownloadedPlaylistChange),
        Triple(R.string.my_top, showTopPlaylist, onShowTopPlaylistChange),
        Triple(R.string.cached_playlist, showCachedPlaylist, onShowCachedPlaylistChange),
        Triple(R.string.uploaded_playlist, showUploadedPlaylist, onShowUploadedPlaylistChange),
        Triple(R.string.arc_weekly_monthly_top, showMostStatsPlaylists, onShowMostStatsPlaylistsChange),
    )
    var showAutoPlaylistsDialog by rememberSaveable { mutableStateOf(false) }
    if (showAutoPlaylistsDialog) {
        AlertDialog(
            onDismissRequest = { showAutoPlaylistsDialog = false },
            title = { Text(stringResource(R.string.auto_playlists)) },
            text = {
                Column {
                    autoPlaylists.forEach { (name, on, set) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { set(!on) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(name), modifier = Modifier.weight(1f))
                            Switch(checked = on, onCheckedChange = set)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoPlaylistsDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    var showAddToPlaylistPositionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showAddToPlaylistPositionDialog) {
        EnumDialog(
            onDismiss = { showAddToPlaylistPositionDialog = false },
            onSelect = {
                onAddToPlaylistPositionChange(it)
                showAddToPlaylistPositionDialog = false
            },
            title = stringResource(R.string.add_to_playlist_position),
            current = addToPlaylistPosition,
            values = AddToPlaylistPosition.entries,
            valueText = {
                when (it) {
                    AddToPlaylistPosition.BEGINNING -> stringResource(R.string.playlist_position_beginning)
                    AddToPlaylistPosition.END -> stringResource(R.string.playlist_position_end)
                }
            },
            valueDescription = {
                when (it) {
                    AddToPlaylistPosition.BEGINNING -> stringResource(R.string.playlist_position_beginning_desc)
                    AddToPlaylistPosition.END -> stringResource(R.string.playlist_position_end_desc)
                }
            },
        )
    }

    var showTopLengthDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showTopLengthDialog) {
        var tempLength by rememberSaveable { mutableFloatStateOf(lengthTop.toFloat()) }

        AlertDialog(
            onDismissRequest = { showTopLengthDialog = false },
            title = { Text(stringResource(R.string.top_length)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(tempLength.toInt().toString())
                    ArcSlider(
                        value = tempLength,
                        onValueChange = { tempLength = it },
                        valueRange = 1f..100f,
                        steps = 98
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLengthTopChange(tempLength.toInt().toString())
                        showTopLengthDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        )
    }

    val providerSwitches = mapOf(
        "LrcLib" to (enableLrclib to onEnableLrclibChange),
        "KuGou" to (enableKugou to onEnableKugouChange),
        "BetterLyrics" to (enableBetterLyrics to onEnableBetterLyricsChange),
        "Paxsenix" to (enablePaxsenix to onEnablePaxsenixChange),
        "LyricsPlus" to (enableLyricsPlus to onEnableLyricsPlusChange),
        "Zemer" to (enableZemer to onEnableZemerChange),
    )
    val providerOrder = LyricsProviderRegistry.deserializeProviderOrder(lyricsProviderOrder)
    val sourcesOn = providerOrder.filter { providerSwitches[it]?.first != false }.map { providerDisplayNames[it] ?: it }
    var showSourcesDialog by rememberSaveable { mutableStateOf(false) }

    if (showSourcesDialog) {
        val lyricsIcon = painterResource(R.drawable.lyrics)
        val draggableItems = remember { mutableStateListOf<DraggableLyricsProviderItem>() }

        LaunchedEffect(providerOrder) {
            draggableItems.clear()
            draggableItems.addAll(
                providerOrder.filter { it in providerSwitches }.map { DraggableLyricsProviderItem(id = it, name = providerDisplayNames[it] ?: it, icon = lyricsIcon) }
            )
        }

        AlertDialog(
            onDismissRequest = { showSourcesDialog = false },
            title = { Text(stringResource(R.string.arc_lyrics_sources)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                ) {
                    Text(
                        stringResource(R.string.lyrics_provider_priority_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DraggableLyricsProviderList(
                        items = draggableItems,
                        onItemsReordered = { items ->
                            // YouTube sources stay last: they are the unsynced fallback
                            onLyricsProviderOrderChange(
                                LyricsProviderRegistry.serializeProviderOrder(items.map { it.id } + providerOrder.filter { it !in providerSwitches })
                            )
                        },
                        trailing = { item ->
                            providerSwitches[item.id]?.let { (on, set) -> Switch(checked = on, onCheckedChange = set) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourcesDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .arcScroll()
            .padding(horizontal = 16.dp),
    ) {
        if (section == null || section == "region") {
            Material3SettingsGroup(
                title = stringResource(R.string.arc_language),
                icon = R.drawable.language,
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.language),
                        title = { Text(stringResource(R.string.app_language)) },
                        description = {
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    context.getSystemService(LocaleManager::class.java).applicationLocales
                                        .takeUnless { it.isEmpty }?.get(0)?.let { it.getDisplayName(it) }
                                        ?: stringResource(R.string.system_default)
                                } else {
                                    LanguageCodeToName.getOrElse(appLanguage) { stringResource(R.string.system_default) }
                                }
                            )
                        },
                        opensPage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri())
                                )
                            } else {
                                showAppLanguageDialog = true
                            }
                        }
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.translate),
                        title = { Text(stringResource(R.string.content_language)) },
                        description = {
                            Text(LanguageCodeToName.getOrElse(contentLanguage) { stringResource(R.string.system_default) })
                        },
                        onClick = { showContentLanguageDialog = true }
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.location_on),
                        title = { Text(stringResource(R.string.content_country)) },
                        description = {
                            Text(CountryCodeToName.getOrElse(contentCountry) { stringResource(R.string.system_default) })
                        },
                        onClick = { showContentCountryDialog = true }
                    ),
                )
            )

            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.arc_filters),
                icon = R.drawable.tune,
                items = listOf(
                    switchItem(R.drawable.explicit, R.string.hide_explicit, null, hideExplicit, onHideExplicitChange),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.slow_motion_video),
                        title = { Text(stringResource(R.string.arc_videos)) },
                        description = { Text(stringResource(videosLabel(videoFilter))) },
                        onClick = { showVideosDialog = true }
                    ),
                )
            )

            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.arc_network),
                icon = R.drawable.wifi_proxy,
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.wifi_proxy),
                        title = { Text(stringResource(R.string.proxy)) },
                        description = {
                            Text(if (proxyEnabled) "${proxyType.name} · $proxyUrl" else stringResource(R.string.arc_off))
                        },
                        onClick = { showProxyConfigurationDialog = true }
                    )
                )
            )

            Spacer(modifier = Modifier.height(27.dp))
        }

        if (section == null || section == "library") {
            Material3SettingsGroup(
                title = stringResource(R.string.playlists),
                icon = R.drawable.queue_music,
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.playlist_add),
                        title = { Text(stringResource(R.string.arc_add_songs_to)) },
                        description = {
                            Text(
                                when (addToPlaylistPosition) {
                                    AddToPlaylistPosition.BEGINNING -> stringResource(R.string.playlist_position_beginning)
                                    AddToPlaylistPosition.END -> stringResource(R.string.playlist_position_end)
                                }
                            )
                        },
                        onClick = { showAddToPlaylistPositionDialog = true },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.playlist_play),
                        title = { Text(stringResource(R.string.auto_playlists)) },
                        description = {
                            val shown = autoPlaylists.filter { it.second }.map { stringResource(it.first) }
                            Text(
                                when {
                                    shown.isEmpty() -> stringResource(R.string.none)
                                    shown.size > 3 -> stringResource(R.string.arc_and_more, shown.take(3).joinToString(", "), shown.size - 3)
                                    else -> shown.joinToString(", ")
                                }
                            )
                        },
                        onClick = { showAutoPlaylistsDialog = true },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.trending_up),
                        title = { Text(stringResource(R.string.top_length)) },
                        description = { Text(lengthTop) },
                        onClick = { showTopLengthDialog = true }
                    ),
                )
            )

            Spacer(modifier = Modifier.height(27.dp))
        }

        if (section == null || section == "lyrics") {
            Material3SettingsGroup(
                title = stringResource(R.string.lyrics),
                icon = R.drawable.lyrics,
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.lyrics),
                        title = { Text(stringResource(R.string.arc_lyrics_sources)) },
                        description = {
                            Text(
                                if (sourcesOn.size > 3) {
                                    stringResource(R.string.arc_and_more, sourcesOn.take(3).joinToString(", "), sourcesOn.size - 3)
                                } else {
                                    sourcesOn.joinToString(", ")
                                }
                            )
                        },
                        onClick = { showSourcesDialog = true }
                    )
                )
            )

            Spacer(modifier = Modifier.height(27.dp))
        }

    }

    TopAppBar(
        title = {
            Text(
                when (section) {
                    "region" -> stringResource(R.string.arc_settings_region)
                    "library" -> stringResource(R.string.arc_settings_library)
                    "lyrics" -> stringResource(R.string.lyrics)
                    else -> stringResource(R.string.content)
                }
            )
        },
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
        }
    )
}

private fun videosLabel(filter: Int) = when (filter) {
    2 -> R.string.arc_videos_none
    1 -> R.string.arc_videos_no_shorts
    else -> R.string.arc_videos_all
}
