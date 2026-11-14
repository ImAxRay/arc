package com.metrolist.music.arc.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.arc.ARC_HOME_SPEED_DIAL_ONLY
import com.metrolist.music.arc.NewReleasesModule

/** Every module Home can show. The key is stored, so never change one. */
enum class HomeModuleId(val key: String) {
    WRAPPED("wrapped"),
    SPEED_DIAL("speed_dial"),
    NEW_RELEASES("new_releases"),
    RECENTS("recents"),
    QUICK_PICKS("quick_picks"),
    COMMUNITY("community"),
    DAILY_DISCOVER("daily_discover"),
    KEEP_LISTENING("keep_listening"),
    ACCOUNT_PLAYLISTS("account_playlists"),
    FORGOTTEN_FAVORITES("forgotten_favorites"),
    SIMILAR_TO("similar_to"),
    YOUTUBE_HOME("youtube_home"),
    MOODS("moods"),
}

class HomeModule(
    val id: HomeModuleId,
    @StringRes val title: Int,
    /** Whether the module has anything to show right now. Read inside composition so it follows the data. */
    val available: @Composable (HomeEnv) -> Boolean,
    val content: @Composable (HomeEnv) -> Unit,
)

val HomeModules: Map<HomeModuleId, HomeModule> =
    listOf(
        HomeModule(HomeModuleId.WRAPPED, R.string.wrapped_ready_title, { env -> wrappedAvailable(env) }) { WrappedModule(it) },
        HomeModule(HomeModuleId.SPEED_DIAL, R.string.speed_dial, { env -> speedDialAvailable(env) }) { SpeedDialModule(it) },
        HomeModule(HomeModuleId.NEW_RELEASES, R.string.release_radar, { true }) { NewReleasesModule(it.navController) },
        HomeModule(HomeModuleId.RECENTS, R.string.arc_home_recents, { env -> recentsShown(env).isNotEmpty() }) { RecentsModule(it) },
        HomeModule(HomeModuleId.QUICK_PICKS, R.string.quick_picks, { env -> quickPicksShown(env).isNotEmpty() }) { QuickPicksModule(it) },
        HomeModule(HomeModuleId.COMMUNITY, R.string.from_the_community, { env -> env.viewModel.communityPlaylists.notEmpty() }) { CommunityModule(it) },
        HomeModule(HomeModuleId.DAILY_DISCOVER, R.string.your_daily_discover, { env -> env.viewModel.dailyDiscover.notEmpty() }) { DailyDiscoverModule(it) },
        HomeModule(HomeModuleId.KEEP_LISTENING, R.string.keep_listening, { env -> env.viewModel.keepListening.notEmpty() }) { KeepListeningModule(it) },
        HomeModule(HomeModuleId.ACCOUNT_PLAYLISTS, R.string.arc_home_your_playlists, { env -> env.viewModel.accountPlaylists.notEmpty() }) { AccountPlaylistsModule(it) },
        HomeModule(HomeModuleId.FORGOTTEN_FAVORITES, R.string.forgotten_favorites, { env -> forgottenShown(env).isNotEmpty() }) { ForgottenFavoritesModule(it) },
        HomeModule(HomeModuleId.SIMILAR_TO, R.string.similar_to, { env -> env.viewModel.similarRecommendations.notEmpty() }) { SimilarToModule(it) },
        HomeModule(HomeModuleId.YOUTUBE_HOME, R.string.home, { true }) { YouTubeHomeModule(it) },
        HomeModule(HomeModuleId.MOODS, R.string.mood_and_genres, { env -> moodsAvailable(env) }) { MoodsModule(it) },
    ).associateBy { it.id }

/** The modules Home shows, in order. Chat 2 makes this the user's own list. */
val HomeModuleOrder: List<HomeModuleId> =
    if (ARC_HOME_SPEED_DIAL_ONLY) {
        listOf(
            HomeModuleId.SPEED_DIAL,
            HomeModuleId.NEW_RELEASES,
            HomeModuleId.RECENTS,
            HomeModuleId.QUICK_PICKS,
            HomeModuleId.FORGOTTEN_FAVORITES,
            HomeModuleId.ACCOUNT_PLAYLISTS,
        )
    } else {
        HomeModuleId.entries
    }

@Composable
private fun kotlinx.coroutines.flow.StateFlow<List<*>?>.notEmpty(): Boolean {
    val list by collectAsStateWithLifecycle()
    return !list.isNullOrEmpty()
}
