package com.metrolist.music.arc

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.utils.rememberEnumPreference

/** Video quality for the network in use, from what [videoId] has; a pick is saved as that network's quality. */
@Composable
fun VideoQualityMenu(videoId: String, open: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var wifi by rememberEnumPreference(ArcVideoQualityWifiKey, VideoQuality.AUTO)
    var mobile by rememberEnumPreference(ArcVideoQualityMobileKey, VideoQuality.AUTO)
    val metered = remember(open) { MusicVideos.metered(context) }
    val current = if (metered) mobile else wifi
    val options by produceState(listOf(VideoQuality.AUTO), videoId, open) {
        if (open) value = MusicVideos.qualities(videoId)
    }
    val playing by VideoState.height
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.Black.copy(alpha = 0.8f),
    ) {
        options.forEach { quality ->
            val picked = quality == current
            DropdownMenuItem(
                text = {
                    val name = stringResource(quality.label)
                    Text(
                        if (quality == VideoQuality.AUTO && playing > 0) "$name · ${heightName(playing)}" else name,
                        fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                trailingIcon = if (picked) {
                    { Icon(painterResource(R.drawable.check), null, tint = Color.White) }
                } else {
                    null
                },
                colors = MenuDefaults.itemColors(textColor = Color.White),
                onClick = {
                    if (metered) mobile = quality else wifi = quality
                    onDismiss()
                },
            )
        }
    }
}

@Composable
fun heightName(height: Int): String =
    VideoQuality.entries.firstOrNull { it.height == height && it != VideoQuality.AUTO }?.let { stringResource(it.label) } ?: "${height}p"
