package com.metrolist.music.arc.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.GridItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.screens.MoodAndGenresButtonHeight

internal fun HomePage.Chip?.isPodcasts(): Boolean = this?.title?.contains("Podcast", ignoreCase = true) == true

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
        key to HomeModule(key, { section.title }, { true }) { env -> HomePageSection(env, section, key) }
    }

/** YouTube's mood chips (Relax, Energize, Commute, Sleep...); the tapped chip's rows show under them, mixes first. */
@Composable
fun YouTubeHomeModule(env: HomeEnv) {
    val homePage by env.viewModel.homePage.collectAsStateWithLifecycle()
    val chipPage by env.viewModel.chipPage.collectAsStateWithLifecycle()
    val selectedChip by env.viewModel.selectedChip.collectAsStateWithLifecycle()
    val isLoading by env.viewModel.isLoading.collectAsStateWithLifecycle()

    Column {
        ModuleHeader(stringResource(R.string.arc_home_mood_mixes))
        ChipsRow(
            chips = homePage?.chips?.map { it to it.title } ?: emptyList(),
            currentValue = selectedChip,
            onValueUpdate = env.viewModel::toggleChip,
        )
        if (isLoading && homePage?.chips.isNullOrEmpty()) ChipsShimmer()
        // folded to its chips while editing, so it drags like any other module
        if (selectedChip != null && !LocalHomeEditing.current) {
            val sections = remember(chipPage) { chipPage?.sections.orEmpty().sortedByDescending { it.endpoint?.browseId == "FEmusic_mixed_for_you" } }
            if (selectedChip.isPodcasts()) {
                PodcastRows(env, chipPage)
                // the chip's own rows, minus the ones the podcast rows above already show
                val skipTitles = listOf("your shows", "episodes for later", "podcast channels", "new episodes")
                sections.forEachIndexed { index, section ->
                    if (skipTitles.none { section.title.lowercase().contains(it) }) {
                        key("chip_$index") {
                            SectionTitle(env, section, playAll = false)
                            HomeYtRow(env, section.items, "home_chip_section")
                        }
                    }
                }
            } else {
                sections.forEachIndexed { index, section ->
                    key("chip_$index") { HomePageSection(env, section, "chip_$index") }
                }
            }
            if (chipPage == null) SectionsShimmer()
        }
    }
}

@Composable
private fun PodcastRows(env: HomeEnv, homePage: HomePage?) {
    val savedShows by env.viewModel.savedPodcastShows.collectAsStateWithLifecycle()
    val episodesForLater by env.viewModel.episodesForLater.collectAsStateWithLifecycle()
    // podcasts pulled out of the chip's episodes; the last good set stays while a refresh is empty
    var cachedPodcasts by remember { mutableStateOf<List<PodcastItem>>(emptyList()) }
    val featuredPodcasts =
        remember(homePage) {
            val fresh =
                homePage?.sections.orEmpty()
                    .flatMap { it.items }
                    .filterIsInstance<EpisodeItem>()
                    .mapNotNull { episode ->
                        episode.podcast?.let { podcast ->
                            PodcastItem(
                                id = podcast.id,
                                title = podcast.name,
                                author = episode.author,
                                episodeCountText = null,
                                thumbnail = episode.thumbnail,
                                playEndpoint = null,
                                shuffleEndpoint = null,
                            )
                        }
                    }.distinctBy { it.id }
                    .shuffled()
                    .take(10)
            if (fresh.isNotEmpty()) cachedPodcasts = fresh
            cachedPodcasts
        }

    if (savedShows.isNotEmpty()) {
        NavigationTitle(
            title = stringResource(R.string.your_shows),
            onClick = { env.navController.navigate("youtube_browse/FEmusic_library_non_music_audio_list") },
        )
        HomeYtRow(env, savedShows, "home_saved_podcast")
    }
    if (episodesForLater.isNotEmpty()) {
        NavigationTitle(title = stringResource(R.string.episodes_for_later), onClick = { env.navController.navigate("online_playlist/SE") })
        HomeYtRow(env, episodesForLater, "home_episode_later")
    }
    // only when Your shows is empty, so the same shows don't appear twice
    if (featuredPodcasts.isNotEmpty() && savedShows.isEmpty()) {
        NavigationTitle(title = stringResource(R.string.podcast_channels))
        HomeYtRow(env, featuredPodcasts, "home_featured_podcast")
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

@Composable
private fun SectionsShimmer() {
    ShimmerHost {
        repeat(2) {
            TextPlaceholder(height = 36.dp, modifier = Modifier.padding(12.dp).width(250.dp))
            LazyRow(contentPadding = HomeRowPadding) { items(4) { GridItemPlaceHolder() } }
        }
        TextPlaceholder(height = 36.dp, modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp).width(250.dp))
        repeat(4) {
            Row {
                repeat(2) {
                    TextPlaceholder(
                        height = MoodAndGenresButtonHeight,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp).width(200.dp),
                    )
                }
            }
        }
    }
}
