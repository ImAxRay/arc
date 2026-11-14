package com.metrolist.music.arc.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.ui.component.NavigationTitle

@Composable
fun QuickPicksModule(env: HomeEnv) {
    val quickPicks by env.viewModel.quickPicks.collectAsStateWithLifecycle()
    val songs = quickPicks?.takeIf { it.isNotEmpty() } ?: return
    val title = stringResource(R.string.quick_picks)
    NavigationTitle(
        title = title,
        onPlayAllClick = if (env.isListenTogetherGuest) null else { { env.playAll(title, songs.distinctBy { it.id }.map { it.toMediaItem() }) } },
    )
    HomeSongRows(env, songs, rows = 4)
}
