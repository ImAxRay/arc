package com.metrolist.music.arc.home

import com.metrolist.music.utils.rememberPreference
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song

@Composable
internal fun recentsShown(env: HomeEnv): List<Song> {
    val recent by env.viewModel.recentSongs.collectAsStateWithLifecycle()
    return recent.notOnDial(env)
}

/** The songs played lately that the dial doesn't already hold, as a side-scrolling grid; the title opens the whole history. */
@Composable
fun RecentsModule(env: HomeEnv) {
    val songs = recentsShown(env).ifEmpty { return }
    Column {
        ModuleHeader(title = stringResource(R.string.arc_home_recents), onClick = { env.navController.navigate("history") })
        val rows by rememberPreference(RecentsRowsSetting.key, RecentsRowsSetting.default)
        HomeSongRows(env, songs, rows = minOf(rows, songs.size))
    }
}
