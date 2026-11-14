package com.metrolist.music.arc.home

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.R
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.ListThumbnailSize
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.utils.rememberPreference

@Composable
fun AccountPlaylistsModule(env: HomeEnv) {
    val accountPlaylists by env.viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val playlists = accountPlaylists?.takeIf { it.isNotEmpty() } ?: return
    val accountName by env.viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by env.viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }
    val url = if (isLoggedIn) accountImageUrl else null

    NavigationTitle(
        label = stringResource(R.string.mixes),
        title = accountName,
        thumbnail = {
            if (url != null) {
                AsyncImage(
                    model =
                        ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(url)
                            .crossfade(false)
                            .build(),
                    placeholder = painterResource(R.drawable.person),
                    error = painterResource(R.drawable.person),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(ListThumbnailSize).clip(CircleShape),
                )
            } else {
                Icon(painterResource(R.drawable.person), contentDescription = null, modifier = Modifier.size(ListThumbnailSize))
            }
        },
        onClick = { env.navController.navigate("account") },
    )
    HomeYtRow(env, playlists, "home_account_playlist")
}
