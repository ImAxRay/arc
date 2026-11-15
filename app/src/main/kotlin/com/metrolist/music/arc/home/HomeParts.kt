package com.metrolist.music.arc.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.arc.CircleGlowCorner
import com.metrolist.music.arc.GlowArea
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.AlbumGridItem
import com.metrolist.music.ui.component.ArtistGridItem
import com.metrolist.music.ui.component.SongGridItem
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.ArtistMenu
import com.metrolist.music.ui.utils.SnapLayoutInfoProvider
import com.metrolist.music.ui.utils.resize

val HomeRowPadding @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()

@Composable
fun HomeYtGridItem(env: HomeEnv, item: YTItem) {
    YouTubeGridItem(
        item = item,
        isActive = env.isActive(item.id),
        isPlaying = env.isPlaying.value,
        coroutineScope = env.scope,
        thumbnailRatio = 1f,
        modifier = Modifier.homeTile(onClick = { env.openYtItem(item) }, onLongClick = { env.showYtItemMenu(item) }, cover = item.thumbnail, glowArea = GlowArea.Cover),
    )
}

/** One side-scrolling row of YouTube items. */
@Composable
fun HomeYtRow(env: HomeEnv, items: List<YTItem>, keyPrefix: String) {
    LazyRow(contentPadding = HomeRowPadding) {
        items(items.distinctBy { it.id }, key = { "${keyPrefix}_${it.id}" }) { HomeYtGridItem(env, it) }
    }
}

@Composable
fun HomeLocalGridItem(env: HomeEnv, item: LocalItem) {
    when (item) {
        is Song ->
            SongGridItem(
                song = item,
                modifier = Modifier.homeTile(onClick = { env.playSong(item) }, onLongClick = { env.showSongMenu(item) }, cover = item.thumbnailUrl, glowArea = GlowArea.Cover).fillMaxWidth(),
                isActive = item.id == env.mediaMetadata.value?.id,
                isPlaying = env.isPlaying.value,
            )

        is Album ->
            AlbumGridItem(
                album = item,
                isActive = item.id == env.mediaMetadata.value?.album?.id,
                isPlaying = env.isPlaying.value,
                coroutineScope = env.scope,
                modifier =
                    Modifier
                        .homeTile(
                            onClick = { env.navController.navigate("album/${item.id}") },
                            onLongClick = { env.menuState.show { AlbumMenu(originalAlbum = item, onDismiss = env.menuState::dismiss) } },
                            cover = item.thumbnailUrl,
                            glowArea = GlowArea.Cover,
                        ).fillMaxWidth(),
            )

        is Artist ->
            ArtistGridItem(
                artist = item,
                modifier =
                    Modifier
                        .homeTile(
                            onClick = { env.navController.navigate("artist/${item.id}") },
                            onLongClick = { env.menuState.show { ArtistMenu(originalArtist = item, coroutineScope = env.scope, onDismiss = env.menuState::dismiss) } },
                            cover = item.thumbnailUrl,
                            glowArea = GlowArea.Cover,
                            glowCorner = CircleGlowCorner,
                        ).fillMaxWidth(),
            )

        is Playlist -> {}
    }
}

/** Library songs as rows in a side-scrolling grid that snaps to a column, as in Quick picks. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeSongRows(env: HomeEnv, songs: List<Song>, rows: Int) {
    val gridState = rememberLazyGridState()
    // keyed on ids: a like or download re-emits the list but must not throw the grid back to the start
    LaunchedEffect(songs.map { it.id }) { gridState.scrollToItem(0) }
    val factor = env.rowGridFactor
    val snap =
        remember(gridState, factor) {
            SnapLayoutInfoProvider(lazyGridState = gridState, positionInLayout = { layoutSize, itemSize -> layoutSize * factor / 2f - itemSize / 2f })
        }
    LazyHorizontalGrid(
        state = gridState,
        rows = GridCells.Fixed(rows),
        flingBehavior = rememberSnapFlingBehavior(snap),
        contentPadding = HomeRowPadding,
        modifier = Modifier.fillMaxWidth().height(ListItemHeight * rows),
    ) {
        items(songs.distinctBy { it.id }, key = { it.id }) { song ->
            HomeSongRow(env, song, Modifier.width(env.width * factor))
        }
    }
}

/** One library song as a row, read from the database so likes and downloads stay current. */
@Composable
fun HomeSongRow(env: HomeEnv, song: Song, modifier: Modifier = Modifier) {
    val live by env.database.song(song.id).collectAsStateWithLifecycle(initialValue = song)
    val shown = live ?: song
    SongListItem(
        song = shown,
        showInLibraryIcon = true,
        isActive = shown.id == env.mediaMetadata.value?.id,
        isPlaying = env.isPlaying.value,
        isSwipeable = false,
        trailingContent = {
            IconButton(onClick = { env.showSongMenu(shown) }) {
                Icon(painterResource(R.drawable.more_vert), contentDescription = null)
            }
        },
        modifier = modifier.homeTile(onClick = { env.playSong(shown) }, onLongClick = { env.showSongMenu(shown) }, cover = shown.thumbnailUrl, glowCorner = 12.dp),
    )
}

/** Songs that aren't already on the speed dial above, so Home never shows one twice. */
@Composable
internal fun List<Song>?.notOnDial(env: HomeEnv): List<Song> {
    val dial by env.viewModel.dialShownIds.collectAsStateWithLifecycle()
    return remember(this, dial) { orEmpty().distinctBy { it.id }.filterNot { it.id in dial } }
}

@Composable
internal fun PickCard(
    label: String,
    title: String,
    subtitle: String?,
    thumbnail: String?,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    val shape = RoundedCornerShape(com.metrolist.music.constants.DialCornerRadius)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AsyncImage(
            model = thumbnail?.resize(if (big) 720 else 360, if (big) 720 else 360),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // a dark fade under the words only, drawn once
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)),
                    topLeft = Offset.Zero,
                    size = size,
                )
            },
        )
        Column(Modifier.align(Alignment.BottomStart).padding(if (big) 12.dp else 8.dp)) {
            Text(
                label.uppercase(),
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                title,
                fontSize = if (big) 18.sp else 12.sp,
                lineHeight = if (big) 22.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
