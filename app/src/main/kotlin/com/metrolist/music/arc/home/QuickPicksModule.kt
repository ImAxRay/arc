package com.metrolist.music.arc.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem

@Composable
internal fun quickPicksShown(env: HomeEnv): List<Song> {
    val quickPicks by env.viewModel.quickPicks.collectAsStateWithLifecycle()
    return quickPicks.notOnDial(env)
}

/** Quick picks as big cover cards, a little over two on screen so the row reads as one to scroll. */
@Composable
fun QuickPicksModule(env: HomeEnv) {
    val songs = quickPicksShown(env).ifEmpty { return }
    val title = stringResource(R.string.quick_picks)
    val card = (env.width - 16.dp - 8.dp * 2) / 2.3f
    Column {
        ModuleHeader(
            title = title,
            onPlay = if (env.isListenTogetherGuest) null else { { env.playAll(title, songs.map { it.toMediaItem() }) } },
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(songs, key = { "quick_${it.id}" }) { song ->
                PickCard(
                    label = song.artists.joinToString { it.name },
                    title = song.title,
                    subtitle = null,
                    thumbnail = song.thumbnailUrl,
                    modifier = Modifier.size(card).homeTile(onClick = { env.playSong(song) }, onLongClick = { env.showSongMenu(song) }, cover = song.thumbnailUrl),
                )
            }
        }
    }
}
