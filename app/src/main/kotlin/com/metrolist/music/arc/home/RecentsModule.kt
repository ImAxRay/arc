package com.metrolist.music.arc.home

import com.metrolist.music.utils.rememberPreference
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

@Composable
internal fun recentsShown(env: HomeEnv): List<Song> {
    val recent by env.viewModel.recentSongs.collectAsStateWithLifecycle()
    val count by rememberPreference(RecentsCountSetting.key, RecentsCountSetting.default)
    return recent.notOnDial(env).take(count)
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
