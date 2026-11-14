package com.metrolist.music.arc.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.arc.isOwnFile
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.MenuState
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.viewmodels.HomeViewModel
import kotlinx.coroutines.CoroutineScope

/** What every Home module needs from the screen around it. Built once per screen size. */
@Stable
class HomeEnv(
    val viewModel: HomeViewModel,
    val navController: NavController,
    val playerConnection: PlayerConnection,
    val menuState: MenuState,
    val haptic: HapticFeedback,
    val scope: CoroutineScope,
    val database: MusicDatabase,
    val isListenTogetherGuest: Boolean,
    val autoRadioQueue: Boolean,
    val mediaMetadata: State<MediaMetadata?>,
    val isPlaying: State<Boolean>,
    val pinnedTypes: State<Map<String, String>>,
    val listState: LazyListState,
    val width: Dp,
    val height: Dp,
) {
    /** Width of one column in the side-scrolling song grids: nearly the whole screen on phones, half on tablets. */
    val rowGridFactor: Float get() = if (width * 0.475f >= Dp(320f)) 0.475f else 0.9f

    fun isActive(id: String?): Boolean = id != null && id in listOf(mediaMetadata.value?.album?.id, mediaMetadata.value?.id)

    fun openYtItem(item: YTItem) {
        when (item) {
            is SongItem -> {
                if (!isListenTogetherGuest) {
                    playerConnection.playQueue(
                        if (autoRadioQueue && !isOwnFile(item.id)) {
                            YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata())
                        } else {
                            ListQueue(title = item.title, items = listOf(item.toMediaItem()))
                        },
                    )
                }
            }

            is AlbumItem -> navController.navigate("album/${item.id}")

            is ArtistItem -> navController.navigate("artist/${item.id}")

            is PlaylistItem ->
                navController.navigate(
                    if (pinnedTypes.value[item.id] == "LOCAL_PLAYLIST") "local_playlist/${item.id}" else "online_playlist/${item.id}",
                )

            is PodcastItem -> navController.navigate("online_podcast/${item.id}")

            is EpisodeItem -> {
                if (!isListenTogetherGuest) {
                    playerConnection.playQueue(ListQueue(title = item.title, items = listOf(item.toMediaMetadata().toMediaItem())))
                }
            }
        }
    }

    /** Opens an item's menu. A hold on a [homeTile] gives the haptic; rows and ⋮ buttons call [holdHaptic] themselves. */
    fun showYtItemMenu(item: YTItem) {
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
                is PodcastItem -> YouTubePlaylistMenu(playlist = item.asPlaylistItem(), coroutineScope = scope, onDismiss = menuState::dismiss)
                is EpisodeItem -> YouTubeSongMenu(song = item.asSongItem(), onDismiss = menuState::dismiss)
            }
        }
    }

    /** A library song tapped on Home: pause or resume it if it's playing, otherwise start it (with a radio if that's on). */
    fun playSong(song: Song) {
        if (isListenTogetherGuest) return
        if (song.id == mediaMetadata.value?.id) {
            playerConnection.togglePlayPause()
        } else {
            playerConnection.playQueue(
                if (autoRadioQueue && !isOwnFile(song.id)) {
                    YouTubeQueue.radio(song.toMediaMetadata())
                } else {
                    ListQueue(title = song.title, items = listOf(song.toMediaItem()))
                },
            )
        }
    }

    fun holdHaptic() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)

    fun showSongMenu(song: Song) {
        menuState.show { SongMenu(originalSong = song, onDismiss = menuState::dismiss) }
    }

    /** A YouTube song tapped on Home: start it, with its radio if that's on. */
    fun playYtSong(song: SongItem) {
        if (isListenTogetherGuest) return
        playerConnection.playQueue(
            if (autoRadioQueue) {
                YouTubeQueue(song.endpoint ?: WatchEndpoint(videoId = song.id), song.toMediaMetadata())
            } else {
                ListQueue(title = song.title, items = listOf(song.toMediaItem()))
            },
        )
    }

    fun playAll(title: String, items: List<androidx.media3.common.MediaItem>) {
        if (!isListenTogetherGuest && items.isNotEmpty()) playerConnection.playQueue(ListQueue(title = title, items = items))
    }
}
