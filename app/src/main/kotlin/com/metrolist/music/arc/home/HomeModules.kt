package com.metrolist.music.arc.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.arc.SpeedDialRowsKey
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
    val setting: ModuleSetting? = null,
    val content: @Composable (HomeEnv) -> Unit,
)

/** The one number a module lets you change from its gear in edit mode. */
class ModuleSetting(@StringRes val label: Int, val key: androidx.datastore.preferences.core.Preferences.Key<Int>, val default: Int, val choices: List<Int>)

val DialRowsSetting = ModuleSetting(R.string.arc_home_setting_rows, SpeedDialRowsKey, 3, listOf(3, 4, 5))
val RecentsRowsSetting = ModuleSetting(R.string.arc_home_setting_rows, intPreferencesKey("arcHomeRecentsRows"), 2, listOf(1, 2, 3))
val SimilarRowsSetting = ModuleSetting(R.string.arc_home_setting_rows, intPreferencesKey("arcHomeSimilarRows"), 1, listOf(1, 2, 3))
val ForgottenRowsSetting = ModuleSetting(R.string.arc_home_setting_rows, intPreferencesKey("arcHomeForgottenRows"), 3, listOf(1, 2, 3))

val HomeModules: Map<HomeModuleId, HomeModule> =
    listOf(
        HomeModule(HomeModuleId.WRAPPED, R.string.wrapped_ready_title, { env -> wrappedAvailable(env) }) { WrappedModule(it) },
        HomeModule(HomeModuleId.SPEED_DIAL, R.string.speed_dial, { env -> speedDialAvailable(env) }, DialRowsSetting) { SpeedDialModule(it) },
        HomeModule(HomeModuleId.NEW_RELEASES, R.string.release_radar, { true }) { NewReleasesModule(it.navController) },
        HomeModule(HomeModuleId.RECENTS, R.string.arc_home_recents, { env -> recentsShown(env).isNotEmpty() }, RecentsRowsSetting) { RecentsModule(it) },
        HomeModule(HomeModuleId.QUICK_PICKS, R.string.quick_picks, { env -> quickPicksShown(env).isNotEmpty() }) { QuickPicksModule(it) },
        HomeModule(HomeModuleId.COMMUNITY, R.string.from_the_community, { env -> env.viewModel.communityPlaylists.notEmpty() }) { CommunityModule(it) },
        HomeModule(HomeModuleId.DAILY_DISCOVER, R.string.your_daily_discover, { env -> env.viewModel.dailyDiscover.notEmpty() }) { DailyDiscoverModule(it) },
        HomeModule(HomeModuleId.KEEP_LISTENING, R.string.keep_listening, { env -> env.viewModel.keepListening.notEmpty() }) { KeepListeningModule(it) },
        HomeModule(HomeModuleId.ACCOUNT_PLAYLISTS, R.string.arc_home_your_playlists, { env -> env.viewModel.accountPlaylists.notEmpty() }) { AccountPlaylistsModule(it) },
        HomeModule(HomeModuleId.FORGOTTEN_FAVORITES, R.string.forgotten_favorites, { env -> forgottenShown(env).isNotEmpty() }, ForgottenRowsSetting) { ForgottenFavoritesModule(it) },
        HomeModule(HomeModuleId.SIMILAR_TO, R.string.similar_to, { env -> env.viewModel.similarRecommendations.notEmpty() }, SimilarRowsSetting) { SimilarToModule(it) },
        HomeModule(HomeModuleId.YOUTUBE_HOME, R.string.arc_home_youtube, { true }) { YouTubeHomeModule(it) },
        HomeModule(HomeModuleId.MOODS, R.string.mood_and_genres, { env -> moodsAvailable(env) }) { MoodsModule(it) },
    ).associateBy { it.id }

/** The user's modules in order, `-` before a hidden one, e.g. "speed_dial,new_releases,-moods". */
val ArcHomeModulesKey = stringPreferencesKey("arcHomeModules")

data class HomeSlot(val id: HomeModuleId, val shown: Boolean)

/** What a new install shows; every other module starts hidden. */
val DefaultHomeModules =
    listOf(
        HomeModuleId.SPEED_DIAL,
        HomeModuleId.NEW_RELEASES,
        HomeModuleId.RECENTS,
        HomeModuleId.QUICK_PICKS,
        HomeModuleId.FORGOTTEN_FAVORITES,
        HomeModuleId.ACCOUNT_PLAYLISTS,
    )

/** Wrapped is a seasonal card that shows itself first, so it isn't one the user arranges. */
private val Arrangeable = HomeModuleId.entries - HomeModuleId.WRAPPED

/** Every arrangeable module once: the saved ones in their order, then any the saved list doesn't name, hidden. */
fun parseHomeSlots(saved: String): List<HomeSlot> {
    val byKey = Arrangeable.associateBy { it.key }
    val slots =
        if (saved.isEmpty()) {
            DefaultHomeModules.map { HomeSlot(it, true) }
        } else {
            saved.split(',').mapNotNull { key -> byKey[key.removePrefix("-")]?.let { HomeSlot(it, !key.startsWith('-')) } }.distinctBy { it.id }
        }
    val named = slots.mapTo(HashSet()) { it.id }
    return slots + Arrangeable.filterNot { it in named }.map { HomeSlot(it, false) }
}

fun List<HomeSlot>.encode(): String = joinToString(",") { if (it.shown) it.id.key else "-" + it.id.key }

@Composable
private fun kotlinx.coroutines.flow.StateFlow<List<*>?>.notEmpty(): Boolean {
    val list by collectAsStateWithLifecycle()
    return !list.isNullOrEmpty()
}
