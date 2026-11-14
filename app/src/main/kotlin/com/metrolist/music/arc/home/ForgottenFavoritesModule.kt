package com.metrolist.music.arc.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem

@Composable
internal fun forgottenShown(env: HomeEnv): List<Song> {
    val forgotten by env.viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    return forgotten.notOnDial(env)
}

@Composable
fun ForgottenFavoritesModule(env: HomeEnv) {
    val songs = forgottenShown(env).ifEmpty { return }
    val title = stringResource(R.string.forgotten_favorites)
    Column {
        ModuleHeader(
            title = title,
            onPlay = if (env.isListenTogetherGuest) null else { { env.playAll(title, songs.map { it.toMediaItem() }) } },
        )
        HomeSongRows(env, songs, rows = minOf(3, songs.size))
    }
}
