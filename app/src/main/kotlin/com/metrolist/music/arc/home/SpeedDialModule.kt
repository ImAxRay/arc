package com.metrolist.music.arc.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.arc.SpeedDialRowsKey
import com.metrolist.music.arc.SpeedDialShortcut
import com.metrolist.music.arc.SpeedDialSkeleton
import com.metrolist.music.arc.rememberTiltEye
import com.metrolist.music.arc.speedDialCellSize
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.CreatePlaylistDialog
import com.metrolist.music.ui.component.RandomizeGridItem
import com.metrolist.music.ui.component.SpeedDialGridItem
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Speed dial comes from the library, so it's empty only for a moment at startup; a skeleton holds its place meanwhile. */
@Composable
internal fun speedDialAvailable(env: HomeEnv): Boolean {
    val items by env.viewModel.speedDialItems.collectAsStateWithLifecycle()
    var starting by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3_000L)
        starting = false
    }
    return items.isNotEmpty() || starting
}

@Composable
fun SpeedDialModule(env: HomeEnv) {
    val dialItems by env.viewModel.speedDialItems.collectAsStateWithLifecycle()
    val continueSong by env.viewModel.continueSong.collectAsStateWithLifecycle()
    val isRandomizing by env.viewModel.isRandomizing.collectAsStateWithLifecycle()
    val speedDialRows by rememberPreference(SpeedDialRowsKey, 3)
    if (dialItems.isEmpty()) {
        SpeedDialSkeleton(speedDialRows)
        return
    }
    val continueId = env.mediaMetadata.value?.id ?: continueSong?.id
    // The Continue tile's song isn't repeated in the grid, judged once per dial: a tile tapped later stays put, lit as playing.
    if (env.viewModel.dialHiddenId == null) env.viewModel.dialHiddenId = continueId
    val hiddenId = env.viewModel.dialHiddenId
    val allItems = remember(dialItems, hiddenId) { dialItems.filterNot { it.id == hiddenId } }
    val scope = rememberCoroutineScope()
    var randomizeJob by remember { mutableStateOf<Job?>(null) }

    ModuleHeader(title = stringResource(R.string.speed_dial))

    val targetItemSize = 160.dp
    val availableWidth = env.width - 32.dp
    val columns = (availableWidth / targetItemSize).toInt().coerceAtLeast(3)
    val rows =
        when {
            columns >= 6 -> 1
            columns >= 4 -> 2
            else -> speedDialRows
        }
    val itemsPerPage = columns * rows
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    // Home's day header and the dial's title above, and page dots below, stay on screen with the grid
    val gridHeight = env.height - insets.calculateTopPadding() - insets.calculateBottomPadding() - DayHeaderHeight - ModuleHeaderHeight - 24.dp
    val itemWidth = speedDialCellSize(availableWidth, columns, rows, gridHeight)
    val tiltEye = rememberTiltEye()

    val big = rows >= 3 && columns >= 3
    val pages = (0 until 3).map { dialPageSlots(it, columns, rows, big, continueTile = continueId != null) }
    val capacities = pages.map { page -> page.count { it.button == null } }
    // only whole pages, at most three; a half-empty last page looks broken
    // a val, so the pager's page count lambda stays the same object; a captured var made a new one each composition,
    // and the pager re-reading it recomposed the dial every frame
    val pageCount = (1 until pages.size).takeWhile { capacities.take(it + 1).sum() <= allItems.size }.lastOrNull()?.plus(1) ?: 1
    val pageStarts = capacities.runningFold(0) { sum, c -> sum + c }
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val shownIds = remember(allItems, pageCount) { allItems.take(pageStarts[pageCount]).mapTo(HashSet()) { it.id } + listOfNotNull(hiddenId) }
    SideEffect { env.viewModel.dialShownIds.value = shownIds }

    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth().height(itemWidth * rows),
        ) { page ->
            val pageItems = allItems.drop(pageStarts[page]).take(capacities[page])
            var next = 0
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.size(itemWidth * columns, itemWidth * rows)) {
                    for (slot in pages[page]) {
                        val cell =
                            Modifier
                                .offset(itemWidth * slot.col, itemWidth * slot.row)
                                .size(itemWidth * slot.span)
                                .padding(4.dp)
                        when (slot.button) {
                            DialButton.CONTINUE -> ContinueTile(env, continueSong, big = slot.span > 1, modifier = cell)

                            DialButton.NEW_PLAYLIST -> {
                                var showCreatePlaylist by remember { mutableStateOf(false) }
                                if (showCreatePlaylist) CreatePlaylistDialog(onDismiss = { showCreatePlaylist = false })
                                Box(cell) { SpeedDialShortcut(icon = R.drawable.add, label = null, onClick = { showCreatePlaylist = true }) }
                            }

                            DialButton.MOODS ->
                                Box(cell) {
                                    SpeedDialShortcut(icon = R.drawable.discover_tune, label = null, onClick = { env.navController.navigate("mood_and_genres") })
                                }

                            DialButton.RANDOM ->
                                Box(cell) {
                                    RandomizeGridItem(
                                        isLoading = isRandomizing,
                                        onClick = {
                                            if (isRandomizing) {
                                                randomizeJob?.cancel()
                                            } else if (!env.isListenTogetherGuest) {
                                                randomizeJob = scope.launch { env.viewModel.getRandomItem()?.let(env::openYtItem) }
                                            }
                                        },
                                    )
                                }

                            null -> {
                                val item = pageItems.getOrNull(next++) ?: continue
                                key(item.id) {
                                    Box(cell) {
                                        SpeedDialGridItem(
                                            item = item,
                                            isPinned = item.id in env.pinnedTypes.value,
                                            isActive = env.isActive(item.id),
                                            isPlaying = env.isPlaying.value,
                                            tiltEye = tiltEye,
                                            thumbnailSize = if (slot.span > 1) 544 else 200,
                                            modifier = Modifier.homeTile(onClick = { env.openYtItem(item) }, onLongClick = { env.showYtItemMenu(item) }, cover = item.thumbnail).fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pageCount > 1) {
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) { PageDots(pagerState) }
        }
    }
}

internal enum class DialButton { CONTINUE, NEW_PLAYLIST, MOODS, RANDOM }

/** One tile on a dial page, in cells: a square [span] cells wide, holding an item or one of the first page's buttons. */
internal data class DialSlot(val col: Int, val row: Int, val span: Int = 1, val button: DialButton? = null)

/**
 * Where tiles sit on one dial page. The first page opens with the Continue tile in the top-left corner, 2x2 when [big]
 * (the top item takes the spot when there is nothing to continue), and keeps its buttons in the bottom-right corner
 * (random in the corner, new playlist above it, moods beside it). Later pages are plain grids.
 */
internal fun dialPageSlots(page: Int, columns: Int, rows: Int, big: Boolean, continueTile: Boolean = true): List<DialSlot> {
    val taken = Array(rows) { BooleanArray(columns) }
    val slots = mutableListOf<DialSlot>()
    fun place(slot: DialSlot) {
        for (r in slot.row until slot.row + slot.span) for (c in slot.col until slot.col + slot.span) taken[r][c] = true
        slots += slot
    }
    if (page == 0 && (big || continueTile)) {
        place(DialSlot(0, 0, span = if (big) 2 else 1, button = if (continueTile) DialButton.CONTINUE else null))
    }
    val buttons = mutableListOf<DialSlot>()
    if (page == 0) {
        buttons += DialSlot(columns - 1, rows - 1, button = DialButton.RANDOM)
        if (columns >= 2) buttons += DialSlot(columns - 2, rows - 1, button = DialButton.MOODS)
        if (rows >= 2) buttons += DialSlot(columns - 1, rows - 2, button = DialButton.NEW_PLAYLIST)
        buttons.forEach { taken[it.row][it.col] = true }
    }
    for (r in 0 until rows) for (c in 0 until columns) if (!taken[r][c]) place(DialSlot(c, r))
    return slots + buttons
}

/**
 * Page one's big tile: the song in the player (tap pauses or resumes it), else the last one played, else the song bundled
 * with Arc, which a fresh install plays from here. Time left is read once per pause, not ticked.
 */
@Composable
private fun ContinueTile(env: HomeEnv, fallback: Song?, big: Boolean, modifier: Modifier) {
    val current = env.mediaMetadata.value
    val playing = env.isPlaying.value
    val title = current?.title ?: fallback?.title ?: return
    val artists = current?.artists?.joinToString { it.name } ?: fallback?.artists?.joinToString { it.name }
    val left =
        remember(current?.id, playing) {
            val player = env.playerConnection.player
            val duration = player.duration
            if (current != null && !playing && duration > 0) makeTimeString(duration - player.currentPosition) else null
        }
    PickCard(
        label = stringResource(if (current != null && playing) R.string.arc_home_now_playing else R.string.arc_home_continue),
        title = title,
        subtitle =
            listOfNotNull(artists?.ifEmpty { null }, left?.let { stringResource(R.string.arc_home_time_left, it) })
                .joinToString(" · ")
                .ifEmpty { null },
        thumbnail = current?.thumbnailUrl ?: fallback?.thumbnailUrl,
        big = big,
        modifier =
            modifier.homeTile(
                onClick = {
                    when {
                        current != null -> env.playerConnection.togglePlayPause()
                        fallback != null -> env.playSong(fallback)
                    }
                },
                onLongClick = fallback?.takeIf { current == null }?.let { song -> { env.showSongMenu(song) } },
                cover = current?.thumbnailUrl ?: fallback?.thumbnailUrl,
            ),
    )
}
