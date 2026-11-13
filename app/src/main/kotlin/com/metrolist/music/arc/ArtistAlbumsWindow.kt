package com.metrolist.music.arc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.utils.backToMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val COLUMNS = 3

/** One section of an artist's page on its own, for sections YouTube gives no full list for. Tapping an album opens its songs under it. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumsWindow(navController: NavController, artistId: String, sectionTitle: String) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val albums by produceState(emptyList<AlbumItem>(), artistId, sectionTitle) {
        value =
            withContext(Dispatchers.IO) {
                YouTube
                    .artist(artistId)
                    .getOrNull()
                    ?.sections
                    ?.firstOrNull { it.title == sectionTitle }
                    ?.items
                    ?.filterIsInstance<AlbumItem>()
                    .orEmpty()
            }
    }
    var peeked by remember { mutableStateOf<AlbumItem?>(null) }

    LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
        albums.chunked(COLUMNS).forEachIndexed { rowIndex, row ->
            item(key = "row_$rowIndex") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    row.forEach { album ->
                        Box(Modifier.weight(1f)) {
                            YouTubeGridItem(
                                item = album,
                                isActive = album.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                coroutineScope = scope,
                                fillMaxWidth = true,
                                modifier =
                                    Modifier.combinedClickable(
                                        onClick = { peeked = if (peeked?.id == album.id) null else album },
                                        onLongClick = {
                                            menuState.show { YouTubeAlbumMenu(albumItem = album, onDismiss = menuState::dismiss) }
                                        },
                                    ),
                            )
                        }
                    }
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            val open = peeked
            if (open != null && row.any { it.id == open.id }) {
                item(key = "peek_${open.id}") {
                    AlbumPeek(
                        album = open,
                        currentSongId = mediaMetadata?.id,
                        onPlay = { songs, index ->
                            playerConnection.playQueue(ListQueue(title = open.title, items = songs.map { it.toMediaItem() }, startIndex = index))
                        },
                        onOpenAlbum = { navController.navigate("album/${open.id}") },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    TopAppBar(
        title = { Text(sectionTitle) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}
