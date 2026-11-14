package com.metrolist.music.arc.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R

/** The signed-in account's saved YouTube playlists; the title opens the account page. */
@Composable
fun AccountPlaylistsModule(env: HomeEnv) {
    val accountPlaylists by env.viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val playlists = accountPlaylists?.takeIf { it.isNotEmpty() } ?: return
    Column {
        ModuleHeader(title = stringResource(R.string.arc_home_your_playlists), onClick = { env.navController.navigate("account") })
        HomeYtRow(env, playlists, "home_account_playlist")
    }
}
