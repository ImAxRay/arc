package com.metrolist.music.arc

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.menu.AddToPlaylistDialogOnline
import com.metrolist.music.ui.menu.LoadingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** A Spotify playlist turned into an Arc playlist: its songs are looked up on YouTube Music one by one. */
object SpotifyImport {
    private val pending = MutableStateFlow<SpotifyLinks.Found?>(null)

    /** [fromLink]: the songs came from the embed page, which stops at 100. */
    fun show(context: Context, found: SpotifyLinks.Found, fromLink: Boolean = true) {
        if (found.tracks.isEmpty()) {
            Toast.makeText(context, R.string.arc_spotify_not_found, Toast.LENGTH_LONG).show()
            return
        }
        if (fromLink && found.tracks.size >= SpotifyLinks.EMBED_TRACK_LIMIT) {
            Toast.makeText(context, context.getString(R.string.arc_spotify_playlist_cap, SpotifyLinks.EMBED_TRACK_LIMIT), Toast.LENGTH_LONG).show()
        }
        pending.value = found
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun open(context: Context, url: String) {
        val app = context.applicationContext
        Toast.makeText(app, R.string.arc_spotify_reading_playlist, Toast.LENGTH_SHORT).show()
        scope.launch {
            val found = SpotifyLinks.resolve(url)
            if (found == null || found.kind != SpotifyLinks.Kind.PLAYLIST) {
                Toast.makeText(app, R.string.arc_spotify_not_playlist, Toast.LENGTH_LONG).show()
            } else {
                show(app, found)
            }
        }
    }

    @Composable
    internal fun pendingState() = pending.collectAsState()

    internal fun clear() {
        pending.value = null
    }
}

@Composable
fun SpotifyImportHost() {
    val found by SpotifyImport.pendingState()
    // Only refilled for a new playlist: the import keeps reading this list after the dialog closes.
    val songs = remember { mutableStateListOf<Song>() }
    var importing by remember { mutableStateOf(false) }
    var percent by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf("") }

    LaunchedEffect(found) {
        val playlist = found ?: return@LaunchedEffect
        songs.clear()
        playlist.tracks.mapTo(songs) { track ->
            Song(song = SongEntity(id = "", title = track.title, duration = track.seconds, albumName = track.album.ifEmpty { null }), artists = track.artists.map { ArtistEntity(id = "", name = it) })
        }
    }

    AddToPlaylistDialogOnline(
        isVisible = found != null && songs.isNotEmpty(),
        allowSyncing = false,
        initialTextFieldValue = found?.title,
        songs = songs,
        onDismiss = SpotifyImport::clear,
        onProgressStart = { importing = it },
        onPercentageChange = { percent = it },
        onSongChange = { current = it },
    )

    LoadingScreen(isVisible = importing, value = percent, songTitle = current)
}

@Composable
fun SpotifyImportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    TextFieldDialog(
        icon = { Icon(painterResource(R.drawable.playlist_add), contentDescription = null) },
        title = { Text(stringResource(R.string.arc_spotify_import)) },
        placeholder = { Text("open.spotify.com/playlist/…") },
        isInputValid = { SpotifyLinks.findIn(it) != null },
        onDone = { text -> SpotifyLinks.findIn(text)?.let { SpotifyImport.open(context, it) } },
        onDismiss = onDismiss,
    )
}
