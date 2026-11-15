package com.metrolist.music.arc.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.constants.ChipSortTypeKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.launch

/** The signed-in account's saved YouTube playlists; the title opens Library on its Playlists chip. */
@Composable
fun AccountPlaylistsModule(env: HomeEnv) {
    val context = LocalContext.current
    val accountPlaylists by env.viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val playlists = accountPlaylists?.takeIf { it.isNotEmpty() } ?: return
    Column {
        ModuleHeader(
            title = stringResource(R.string.arc_home_your_playlists),
            onClick = {
                env.scope.launch {
                    context.dataStore.edit { it[ChipSortTypeKey] = LibraryFilter.PLAYLISTS.name }
                    env.navController.navigate(Screens.Library.route) {
                        popUpTo(env.navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            },
        )
        HomeYtRow(env, playlists, "home_account_playlist")
    }
}
