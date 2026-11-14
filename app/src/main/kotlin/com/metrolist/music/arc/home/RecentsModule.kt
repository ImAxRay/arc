package com.metrolist.music.arc.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song

private const val RECENTS_SHOWN = 5

@Composable
internal fun recentsShown(env: HomeEnv): List<Song> {
    val recent by env.viewModel.recentSongs.collectAsStateWithLifecycle()
    return recent.notOnDial(env).take(RECENTS_SHOWN)
}

/** The last few songs played that the dial doesn't already hold, as rows; the title opens the whole history. */
@Composable
fun RecentsModule(env: HomeEnv) {
    val songs = recentsShown(env).ifEmpty { return }
    Column {
        ModuleHeader(title = stringResource(R.string.arc_home_recents), onClick = { env.navController.navigate("history") })
        songs.forEach { song -> key(song.id) { HomeSongRow(env, song, Modifier.fillMaxWidth()) } }
    }
}
