package com.metrolist.music.arc.home

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder

/** Start of the stored key of one of YouTube's own home rows. */
const val YT_ROW = "yt:"

/**
 * A YouTube row's stored key: its title folded to letters and digits. The row's endpoint isn't stable (its params differ
 * from page to page) and most rows have none. shortcut: titles are in the account's language, so changing it orphans
 * saved rows; key on something else if YouTube ever gives rows a stable id.
 */
internal fun HomePage.Section.rowKey(): String = YT_ROW + title.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { title.hashCode().toString() }

/** Today's YouTube home rows, each a module of its own, by key; the first of two same-named rows wins. */
fun youTubeRowModules(page: HomePage?): Map<String, HomeModule> =
    page?.sections.orEmpty().distinctBy { it.rowKey() }.associate { section ->
        val key = section.rowKey()
        key to HomeModule(key, { section.title }, ModuleGroup.YOUTUBE, { section.about(it) }, { true }) { env -> HomePageSection(env, section, key) }
    }

/** What a row holds: by its endpoint, then by its English title, then by what kind of items it carries. */
private fun HomePage.Section.about(context: Context): String {
    val known =
        when (endpoint?.browseId) {
            "FEmusic_listen_again" -> R.string.arc_home_about_row_listen_again
            "FEmusic_new_releases_albums" -> R.string.arc_home_about_row_new_releases
            "FEmusic_library_landing" -> R.string.arc_home_about_row_library
            "FEmusic_mixed_for_you" -> R.string.arc_home_about_row_mixed_for_you
            "FEmusic_moods_and_genres_category" -> return context.getString(R.string.arc_home_about_row_genre, title)
            else ->
                when (rowKey().removePrefix(YT_ROW)) {
                    "songmixes" -> R.string.arc_home_about_row_song_mixes
                    "trendingsongsforyou" -> R.string.arc_home_about_row_trending
                    "freshfindsoldfavorites" -> R.string.arc_home_about_row_fresh_finds
                    "fromthecommunity" -> R.string.arc_home_about_row_community
                    "forgottenfavorites" -> R.string.arc_home_about_row_forgotten
                    "yourdailydiscover" -> R.string.arc_home_about_row_daily_discover
                    "recaps" -> R.string.arc_home_about_row_recaps
                    "longlistens" -> R.string.arc_home_about_row_long_listens
                    else -> null
                }
        }
    if (known != null) return context.getString(known)
    val kind =
        when {
            items.all { it is SongItem } -> R.string.arc_home_about_row_songs
            items.all { it is PlaylistItem } -> R.string.arc_home_about_row_playlists
            items.all { it is AlbumItem } -> R.string.arc_home_about_row_albums
            else -> R.string.arc_home_about_row_mixed
        }
    return context.getString(kind, title)
}

/** The mood chips (Relax, Energize, Commute, Sleep...); a tapped chip opens that mood's page, mixes first. */
@Composable
fun YouTubeHomeModule(env: HomeEnv) {
    val homePage by env.viewModel.homePage.collectAsStateWithLifecycle()
    val isLoading by env.viewModel.isLoading.collectAsStateWithLifecycle()

    Column {
        ModuleHeader(stringResource(R.string.arc_home_mood_mixes))
        ChipsRow(
            chips = homePage?.chips?.map { it to it.title } ?: emptyList(),
            currentValue = null,
            onValueUpdate = { chip -> chip?.endpoint?.params?.let { env.navController.navigate("youtube_browse/FEmusic_home?params=${Uri.encode(it)}&title=${Uri.encode(chip.title)}") } },
        )
        if (isLoading && homePage?.chips.isNullOrEmpty()) ChipsShimmer()
    }
}

@Composable
private fun SectionTitle(env: HomeEnv, section: HomePage.Section, playAll: Boolean) {
    val songs = section.items.filterIsInstance<SongItem>()
    ModuleHeader(
        title = section.title,
        onClick = section.endpoint?.let { endpoint -> { openSection(env, endpoint) } },
        onPlay =
            if (playAll && songs.isNotEmpty() && !env.isListenTogetherGuest) {
                { env.playAll(section.title, songs.map { it.toMediaMetadata().toMediaItem() }) }
            } else {
                null
            },
    )
}

private fun openSection(env: HomeEnv, endpoint: BrowseEndpoint) {
    val nav = env.navController
    when {
        endpoint.browseId == "FEmusic_moods_and_genres" -> nav.navigate("mood_and_genres")
        endpoint.browseId.startsWith("FEmusic_library_non_music_audio") || endpoint.browseId.startsWith("FEmusic_non_music_audio") ->
            nav.navigate("youtube_browse/${endpoint.browseId}")
        endpoint.params != null -> nav.navigate("youtube_browse/${endpoint.browseId}?params=${endpoint.params}")
        else -> nav.navigate("browse/${endpoint.browseId}")
    }
}

@Composable
private fun HomePageSection(env: HomeEnv, section: HomePage.Section, keyPrefix: String) {
    // a module of its own now, so it stacks its parts itself
    Column {
        SectionTitle(env, section, playAll = true)
        if (section.items.isNotEmpty() && section.items.all { it is SongItem }) {
            // songs only (like YouTube's Quick picks): rows in a side-scrolling grid
            val songs = section.items.filterIsInstance<SongItem>().distinctBy { it.id }
            LazyHorizontalGrid(
                state = rememberLazyGridState(),
                rows = GridCells.Fixed(4),
                contentPadding = HomeRowPadding,
                modifier = Modifier.fillMaxWidth().height(ListItemHeight * 4),
            ) {
                items(songs, key = { "${keyPrefix}_song_${it.id}" }) { song ->
                    YouTubeListItem(
                        item = song,
                        isActive = song.id == env.mediaMetadata.value?.id,
                        isPlaying = env.isPlaying.value,
                        isSwipeable = false,
                        trailingContent = {
                            IconButton(onClick = { env.showYtItemMenu(song) }) {
                                Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                            }
                        },
                        modifier =
                            Modifier
                                .width(env.width * env.rowGridFactor)
                                .combinedClickable(
                                    onClick = { env.playYtSong(song) },
                                    onLongClick = {
                                        env.holdHaptic()
                                        env.showYtItemMenu(song)
                                    },
                                ),
                    )
                }
            }
        } else {
            HomeYtRow(env, section.items, "${keyPrefix}_item")
        }
    }
}

@Composable
private fun ChipsShimmer() {
    ShimmerHost(showGradient = false) {
        LazyRow(
            contentPadding = HomeRowPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(5) { TextPlaceholder(height = 30.dp, shape = RoundedCornerShape(16.dp), modifier = Modifier.width(72.dp)) }
        }
    }
}
