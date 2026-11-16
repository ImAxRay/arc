package com.metrolist.music.arc.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    /** Now YouTube's mood chips alone; once all of YouTube's home, whose rows are now modules of their own. */
    YOUTUBE_HOME("youtube_home"),
    MOODS("moods"),
}

/** What a module is for; the Add to Home picker sorts modules by it. */
enum class ModuleGroup(@StringRes val title: Int) {
    YOURS(R.string.arc_home_group_yours),
    DISCOVER(R.string.arc_home_group_discover),
    MOODS(R.string.arc_home_group_moods),
    YOUTUBE(R.string.arc_home_group_youtube),
}

class HomeModule(
    val key: String,
    val name: (Context) -> String,
    val group: ModuleGroup,
    @StringRes val description: Int,
    /** Whether the module has anything to show right now. Read inside composition so it follows the data. */
    val available: @Composable (HomeEnv) -> Boolean,
    val setting: ModuleSetting? = null,
    /** Whether its title can be hidden; the dial has none, and Similar to's titles are its seeds. */
    val nameToggle: Boolean = true,
    val content: @Composable (HomeEnv) -> Unit,
) {
    constructor(
        id: HomeModuleId,
        @StringRes title: Int,
        group: ModuleGroup,
        @StringRes description: Int,
        available: @Composable (HomeEnv) -> Boolean,
        setting: ModuleSetting? = null,
        nameToggle: Boolean = true,
        content: @Composable (HomeEnv) -> Unit,
    ) : this(id.key, { it.getString(title) }, group, description, available, setting, nameToggle, content)
}

/** The one number a module lets you change from its gear in edit mode. */
class ModuleSetting(@StringRes val label: Int, val key: androidx.datastore.preferences.core.Preferences.Key<Int>, val default: Int, val choices: List<Int>)

val DialRowsSetting = ModuleSetting(R.string.arc_home_setting_rows, SpeedDialRowsKey, 3, listOf(3, 4, 5))
val RecentsRowsSetting = ModuleSetting(R.string.arc_home_setting_rows, intPreferencesKey("arcHomeRecentsRows"), 2, listOf(1, 2, 3))
val SimilarRowsSetting = ModuleSetting(R.string.arc_home_setting_rows, intPreferencesKey("arcHomeSimilarRows"), 1, listOf(1, 2, 3))
val ForgottenRowsSetting = ModuleSetting(R.string.arc_home_setting_rows, intPreferencesKey("arcHomeForgottenRows"), 3, listOf(1, 2, 3))

val HomeModules: Map<String, HomeModule> =
    listOf(
        HomeModule(HomeModuleId.WRAPPED, R.string.wrapped_ready_title, ModuleGroup.YOURS, R.string.arc_home_about_wrapped, { env -> wrappedAvailable(env) }) { WrappedModule(it) },
        HomeModule(HomeModuleId.SPEED_DIAL, R.string.speed_dial, ModuleGroup.YOURS, R.string.arc_home_about_dial, { env -> speedDialAvailable(env) }, DialRowsSetting, nameToggle = false) { SpeedDialModule(it) },
        HomeModule(HomeModuleId.NEW_RELEASES, R.string.release_radar, ModuleGroup.DISCOVER, R.string.arc_home_about_new_releases, { true }) { NewReleasesModule(it.navController) },
        HomeModule(HomeModuleId.RECENTS, R.string.arc_home_recents, ModuleGroup.YOURS, R.string.arc_home_about_recents, { env -> recentsShown(env).isNotEmpty() }, RecentsRowsSetting) { RecentsModule(it) },
        HomeModule(HomeModuleId.QUICK_PICKS, R.string.quick_picks, ModuleGroup.DISCOVER, R.string.arc_home_about_quick_picks, { env -> quickPicksShown(env).isNotEmpty() }) { QuickPicksModule(it) },
        HomeModule(HomeModuleId.COMMUNITY, R.string.from_the_community, ModuleGroup.DISCOVER, R.string.arc_home_about_community, { env -> env.viewModel.communityPlaylists.notEmpty() }) { CommunityModule(it) },
        HomeModule(HomeModuleId.DAILY_DISCOVER, R.string.your_daily_discover, ModuleGroup.DISCOVER, R.string.arc_home_about_daily_discover, { env -> env.viewModel.dailyDiscover.notEmpty() }) { DailyDiscoverModule(it) },
        HomeModule(HomeModuleId.KEEP_LISTENING, R.string.keep_listening, ModuleGroup.YOURS, R.string.arc_home_about_keep_listening, { env -> env.viewModel.keepListening.notEmpty() }) { KeepListeningModule(it) },
        HomeModule(HomeModuleId.ACCOUNT_PLAYLISTS, R.string.arc_home_your_playlists, ModuleGroup.YOURS, R.string.arc_home_about_playlists, { env -> env.viewModel.accountPlaylists.notEmpty() }) { AccountPlaylistsModule(it) },
        HomeModule(HomeModuleId.FORGOTTEN_FAVORITES, R.string.forgotten_favorites, ModuleGroup.YOURS, R.string.arc_home_about_forgotten, { env -> forgottenShown(env).isNotEmpty() }, ForgottenRowsSetting) { ForgottenFavoritesModule(it) },
        HomeModule(HomeModuleId.SIMILAR_TO, R.string.similar_to, ModuleGroup.DISCOVER, R.string.arc_home_about_similar, { env -> env.viewModel.similarRecommendations.notEmpty() }, SimilarRowsSetting, nameToggle = false) { SimilarToModule(it) },
        HomeModule(HomeModuleId.YOUTUBE_HOME, R.string.arc_home_mood_mixes, ModuleGroup.MOODS, R.string.arc_home_about_mood_mixes, { true }) { YouTubeHomeModule(it) },
        HomeModule(HomeModuleId.MOODS, R.string.mood_and_genres, ModuleGroup.MOODS, R.string.arc_home_about_moods, { env -> moodsAvailable(env) }) { MoodsModule(it) },
    ).associateBy { it.key }

/** The user's modules in order, `-` before a hidden one, e.g. "speed_dial,new_releases,-moods". */
val ArcHomeModulesKey = stringPreferencesKey("arcHomeModules")

/** Modules whose title the user hid. */
val ArcHomeUntitledKey = stringSetPreferencesKey("arcHomeUntitled")

data class HomeSlot(val key: String, val shown: Boolean)

/** What a new install shows; every other module starts hidden. */
val DefaultHomeModules =
    listOf(
        HomeModuleId.SPEED_DIAL,
        HomeModuleId.NEW_RELEASES,
        HomeModuleId.YOUTUBE_HOME,
        HomeModuleId.RECENTS,
        HomeModuleId.QUICK_PICKS,
        HomeModuleId.FORGOTTEN_FAVORITES,
        HomeModuleId.ACCOUNT_PLAYLISTS,
    )

/** Wrapped is a seasonal card that shows itself first, so it isn't one the user arranges. */
private val Arrangeable = (HomeModuleId.entries - HomeModuleId.WRAPPED).map { it.key }

/**
 * Every arrangeable module once: the saved ones in their order, then any the saved list doesn't name, hidden, then
 * today's YouTube [rows] the list doesn't name, hidden. Saved YouTube rows are kept even when today's page lacks them.
 */
fun parseHomeSlots(saved: String, rows: Collection<String> = emptyList()): List<HomeSlot> {
    val slots =
        if (saved.isEmpty()) {
            DefaultHomeModules.map { HomeSlot(it.key, true) }
        } else {
            saved.split(',').mapNotNull { raw ->
                val key = raw.removePrefix("-")
                if (key in Arrangeable || key.startsWith(YT_ROW)) HomeSlot(key, !raw.startsWith('-')) else null
            }.distinctBy { it.key }
        }
    val named = slots.mapTo(HashSet()) { it.key }
    return slots + (Arrangeable + rows).filterNot { it in named }.distinct().map { HomeSlot(it, false) }
}

/** Hidden YouTube rows aren't written: hidden is their default, and the page names new ones every day. */
fun List<HomeSlot>.encode(): String = filter { it.shown || !it.key.startsWith(YT_ROW) }.joinToString(",") { if (it.shown) it.key else "-" + it.key }

@Composable
private fun kotlinx.coroutines.flow.StateFlow<List<*>?>.notEmpty(): Boolean {
    val list by collectAsStateWithLifecycle()
    return !list.isNullOrEmpty()
}
