package com.metrolist.music.arc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.arc.home.ModuleHeader
import com.metrolist.music.arc.home.homeTile
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.shimmer.GridItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberEnumPreference
import kotlinx.coroutines.launch

private const val MODULE_ITEMS = 30

/** New albums, EPs and singles as a home module, with artists you follow or play most first. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewReleasesModule(navController: NavController) {
    val viewModel: ReleaseRadarViewModel = hiltViewModel()
    val albums by viewModel.releases.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    if (!loaded) {
        ModuleSkeleton(stringResource(R.string.release_radar))
        return
    }
    if (albums.isEmpty()) return
    Column {
        ModuleHeader(
            title = stringResource(R.string.release_radar),
            onClick = { navController.navigate("release_radar") },
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 6.dp)) {
            items(albums.take(MODULE_ITEMS), key = { "arc_new_${it.id}" }) { album ->
                ReleaseRadarTile(album, viewModel, navController, fillMaxWidth = false)
            }
        }
    }
}

/** The whole Release Radar, the same tiles as the Home module laid out as a grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseRadarScreen(navController: NavController) {
    val viewModel: ReleaseRadarViewModel = hiltViewModel()
    val albums by viewModel.releases.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        items(albums, key = { "arc_radar_${it.id}" }) { album ->
            ReleaseRadarTile(album, viewModel, navController, fillMaxWidth = true)
        }
        if (!loaded) items(8) { ShimmerHost { GridItemPlaceHolder(fillMaxWidth = true) } }
    }
    TopAppBar(
        title = { Text(stringResource(R.string.release_radar)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

@Composable
private fun ReleaseRadarTile(album: AlbumItem, viewModel: ReleaseRadarViewModel, navController: NavController, fillMaxWidth: Boolean) {
    val trackCounts by viewModel.trackCounts.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val kind = releaseKind(trackCounts[album.id], album.releaseType)
    YouTubeGridItem(
        item = album,
        coroutineScope = scope,
        fillMaxWidth = fillMaxWidth,
        badges = { if (kind != null) ReleaseTag(kind) },
        modifier =
            Modifier.homeTile(
                cover = album.thumbnail,
                glowArea = GlowArea.Cover,
                // A release with one song just plays it. Anything longer opens, so you can see what's on it.
                onClick = {
                    scope.launch {
                        val songId = viewModel.singleSong(album)
                        if (songId != null && playerConnection != null) {
                            // Starting from the tile's own title and cover lets the song begin before the queue has loaded.
                            val preload =
                                MediaMetadata(
                                    id = songId,
                                    title = album.title,
                                    artists = album.artists.orEmpty().map { MediaMetadata.Artist(it.id, it.name) },
                                    duration = -1,
                                    thumbnailUrl = album.thumbnail,
                                )
                            playerConnection.playQueue(
                                YouTubeQueue(WatchEndpoint(videoId = songId, playlistId = album.playlistId), preloadItem = preload),
                            )
                        } else {
                            navController.navigate("album/${album.id}")
                        }
                    }
                },
                onLongClick = {
                    menuState.show { YouTubeAlbumMenu(albumItem = album, onDismiss = menuState::dismiss, fromNewReleases = true) }
                },
            ),
    )
}

@Composable
private fun ReleaseTag(kind: ReleaseKind) {
    Text(
        text =
            stringResource(
                when (kind) {
                    ReleaseKind.SINGLE -> R.string.release_single
                    ReleaseKind.EP -> R.string.release_ep
                    ReleaseKind.ALBUM -> R.string.release_album
                },
            ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** One square button inside speed dial that opens a screen instead of playing something. */
@Composable
fun SpeedDialShortcut(icon: Int, label: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        modifier =
            modifier
                .homeTile(onClick = onClick)
                .fillMaxSize()
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(8.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(if (label == null) 56.dp else 28.dp),
        )
        if (label != null) Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ModuleSkeleton(title: String) {
    Column {
        ModuleHeader(title = title)
        ShimmerHost {
            androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 6.dp)) {
                repeat(3) { GridItemPlaceHolder() }
            }
        }
    }
}

@Composable
fun SpeedDialSkeleton(rows: Int = 3) {
    Column {
        ShimmerHost(modifier = Modifier.padding(horizontal = 16.dp)) {
            repeat(rows) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                    repeat(3) {
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .weight(1f)
                                .padding(4.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                .background(MaterialTheme.colorScheme.onSurface),
                        )
                    }
                }
            }
        }
    }
}
