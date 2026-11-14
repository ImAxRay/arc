package com.metrolist.music.arc

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

@Composable
fun MusicVideoSettings() {
    val (enabled, onEnabled) = rememberPreference(ArcMusicVideosKey, true)
    val (wifi, onWifi) = rememberEnumPreference(ArcVideoQualityWifiKey, VideoQuality.AUTO)
    val (mobile, onMobile) = rememberEnumPreference(ArcVideoQualityMobileKey, VideoQuality.AUTO)
    val (radioSongsOnly, onRadioSongsOnly) = rememberPreference(ArcRadioSongsOnlyKey, true)
    val (searchVideos, onSearchVideos) = rememberPreference(ArcSearchVideosKey, false)
    var picking by remember { mutableStateOf<Boolean?>(null) }

    picking?.let { onWifiList ->
        EnumDialog(
            onDismiss = { picking = null },
            onSelect = {
                if (onWifiList) onWifi(it) else onMobile(it)
                picking = null
            },
            title = stringResource(if (onWifiList) R.string.arc_video_quality_wifi else R.string.arc_video_quality_mobile),
            current = if (onWifiList) wifi else mobile,
            values = VideoQuality.entries,
            valueText = { stringResource(it.label) },
        )
    }

    Material3SettingsGroup(
        title = stringResource(R.string.arc_music_videos),
        items = buildList {
            add(switchItem(R.drawable.arc_video, R.string.arc_music_videos, R.string.arc_music_videos_desc, enabled, onEnabled))
            if (enabled) {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.wifi),
                    title = { Text(stringResource(R.string.arc_video_quality_wifi)) },
                    description = { Text(stringResource(wifi.label)) },
                    onClick = { picking = true },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.signal_cellular),
                    title = { Text(stringResource(R.string.arc_video_quality_mobile)) },
                    description = { Text(stringResource(mobile.label)) },
                    onClick = { picking = false },
                ))
            }
            add(switchItem(R.drawable.radio, R.string.arc_radio_songs_only, R.string.arc_radio_songs_only_desc, radioSongsOnly, onRadioSongsOnly))
            add(switchItem(R.drawable.search, R.string.arc_search_videos, R.string.arc_search_videos_desc, searchVideos, onSearchVideos))
        },
    )
    Spacer(Modifier.height(27.dp))
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
