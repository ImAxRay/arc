package com.metrolist.music.arc

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class StartupSong { OFF, LAST, LIKED }

val ArcStartupSongKey = stringPreferencesKey("arcStartupSong")
val DefaultStartupSong = StartupSong.OFF

private const val LIKED_SONGS = 500
private const val QUEUE_SIZE = 100

// Lives outside Compose because it must outlast the activity: reopening the app shouldn't change the song again.
private var handledThisProcess = false

@Composable
fun startupSongLabel(value: StartupSong): String =
    stringResource(
        when (value) {
            StartupSong.OFF -> R.string.startup_song_off
            StartupSong.LAST -> R.string.startup_song_last
            StartupSong.LIKED -> R.string.startup_song_liked
        },
    )

/** Starts a song the first time the app is opened from its icon, unless something is already playing. */
suspend fun playStartupSong(
    context: Context,
    database: MusicDatabase,
    connection: PlayerConnection,
    openedFromIcon: Boolean,
) {
    if (handledThisProcess) return
    handledThisProcess = true
    if (!openedFromIcon) return

    val stored = context.dataStore[ArcStartupSongKey]
    val choice = StartupSong.entries.firstOrNull { it.name == stored } ?: DefaultStartupSong
    if (choice == StartupSong.OFF) return

    connection.service.isPlayerReady.first { it }
    // The saved queue restores asynchronously and would replace this one if it arrived later.
    withTimeoutOrNull(1_500L) {
        while (connection.player.mediaItemCount == 0) delay(100L)
    }
    if (connection.player.isPlaying) return

    if (choice == StartupSong.LAST && connection.player.mediaItemCount > 0) {
        connection.player.play()
        return
    }

    val songs =
        withContext(Dispatchers.IO) {
            when (choice) {
                StartupSong.LAST -> database.arcRecentSongs(1)
                else -> database.likedSongsByCreateDateDesc(LIKED_SONGS, 0).shuffled()
            }
        }
    if (songs.isEmpty()) return
    connection.playQueue(ListQueue(items = songs.take(QUEUE_SIZE).map { it.toMediaItem() }))
}
