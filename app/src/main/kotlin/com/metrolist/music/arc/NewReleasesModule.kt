package com.metrolist.music.arc

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.hct.Hct
import com.metrolist.music.ui.theme.extractThemeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        badges = {},
        coverOverlay = { if (kind != null) ReleaseTag(kind, album.thumbnail, Modifier.align(Alignment.TopStart).padding(6.dp)) },
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

/** Single, EP or album, on the cover's top corner in the cover's own hue: light over a dark corner, dark over a light one. */
@Composable
private fun ReleaseTag(kind: ReleaseKind, cover: String?, modifier: Modifier) {
    val context = LocalContext.current
    var colors by remember(cover) { mutableStateOf(cover?.let { TagColors.cached(it) }) }
    LaunchedEffect(cover) { if (colors == null && cover != null) colors = TagColors.load(context, cover) }
    val (ground, ink) = colors ?: (Color.Black.copy(alpha = 0.55f) to Color.White)
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
        color = ink,
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ground)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private object TagColors {
    private val seen = LruCache<String, Pair<Color, Color>>(64)

    fun cached(url: String): Pair<Color, Color>? = seen.get(url)

    suspend fun load(context: Context, url: String): Pair<Color, Color>? =
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context).data(url).size(64, 64).allowHardware(false).build()
            val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull() ?: return@withContext null
            val hue = Hct.fromInt(bitmap.extractThemeColor().toArgb())
            // How light the corner under the tag is: the top-left quarter of the cover.
            var sum = 0.0
            val w = bitmap.width / 2
            val h = bitmap.height / 4
            for (y in 0 until h) for (x in 0 until w) sum += Color(bitmap.getPixel(x, y)).luminance()
            val darkCorner = sum / (w * h) < 0.4
            val chroma = if (hue.chroma < 8.0) 0.0 else 36.0
            val ground = Color(Hct.from(hue.hue, chroma, if (darkCorner) 92.0 else 22.0).toInt())
            val ink = Color(Hct.from(hue.hue, chroma / 2, if (darkCorner) 20.0 else 94.0).toInt())
            (ground to ink).also { seen.put(url, it) }
        }
}

/** One square button inside speed dial that opens a screen instead of playing something. */
@Composable
fun SpeedDialShortcut(icon: Int, label: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        modifier =
            modifier
                .homeTile(onClick = onClick, glowCorner = com.metrolist.music.constants.DialCornerRadius)
                .fillMaxSize()
                .clip(RoundedCornerShape(com.metrolist.music.constants.DialCornerRadius))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(8.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            // alone on the tile, the icon grows with it (a 2x2 radio gets a big one)
            modifier = if (label == null) Modifier.fillMaxSize(0.42f) else Modifier.size(28.dp),
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
