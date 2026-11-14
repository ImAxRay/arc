package com.metrolist.music.arc.home

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.LocalAlbumRadio
import com.metrolist.music.playback.queues.YouTubeAlbumRadio
import com.metrolist.music.playback.queues.YouTubeQueue
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home's shuffle button: a radio from one random thing in the library or on YouTube's home. */
fun HomeEnv.playLuckyItem(localItems: List<LocalItem>, ytItems: List<YTItem>) {
    if (isListenTogetherGuest || (localItems.isEmpty() && ytItems.isEmpty())) return
    val local = ytItems.isEmpty() || (localItems.isNotEmpty() && Random.nextFloat() < 0.5)
    scope.launch(Dispatchers.Main) {
        if (local) {
            when (val lucky = localItems.random()) {
                is Song -> playerConnection.playQueue(YouTubeQueue.radio(lucky.toMediaMetadata()))
                is Album -> withContext(Dispatchers.IO) { database.albumWithSongs(lucky.id).first() }?.let { playerConnection.playQueue(LocalAlbumRadio(it)) }
                is Artist, is Playlist -> {}
            }
        } else {
            when (val lucky = ytItems.random()) {
                is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(lucky.toMediaMetadata()))
                is AlbumItem -> playerConnection.playQueue(YouTubeAlbumRadio(lucky.playlistId))
                is ArtistItem -> lucky.radioEndpoint?.let { playerConnection.playQueue(YouTubeQueue(it)) }
                is PlaylistItem -> lucky.playEndpoint?.let { playerConnection.playQueue(YouTubeQueue(it)) }
                is PodcastItem -> lucky.playEndpoint?.let { playerConnection.playQueue(YouTubeQueue(it)) }
                is EpisodeItem -> playerConnection.playQueue(ListQueue(title = lucky.title, items = listOf(lucky.toMediaMetadata().toMediaItem())))
            }
        }
    }
}
