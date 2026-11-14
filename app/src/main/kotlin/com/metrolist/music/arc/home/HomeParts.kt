package com.metrolist.music.arc.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
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

val HomeRowPadding @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()

@Composable
fun HomeYtGridItem(env: HomeEnv, item: YTItem) {
    YouTubeGridItem(
        item = item,
        isActive = env.isActive(item.id),
        isPlaying = env.isPlaying.value,
        coroutineScope = env.scope,
        thumbnailRatio = 1f,
        modifier = Modifier.homeTile(onClick = { env.openYtItem(item) }, onLongClick = { env.showYtItemMenu(item) }),
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
                modifier = Modifier.homeTile(onClick = { env.playSong(item) }, onLongClick = { env.showSongMenu(item) }).fillMaxWidth(),
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
    LaunchedEffect(songs) { gridState.scrollToItem(0) }
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
        items(songs.distinctBy { it.id }, key = { it.id }) { originalSong ->
            // read the song from the database so likes and downloads stay current
            val song by env.database.song(originalSong.id).collectAsStateWithLifecycle(initialValue = originalSong)
            val shown = song ?: originalSong
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
                modifier =
                    Modifier
                        .width(env.width * factor)
                        .combinedClickable(
                            onClick = { env.playSong(shown) },
                            onLongClick = {
                                env.holdHaptic()
                                env.showSongMenu(shown)
                            },
                        ),
            )
        }
    }
}
