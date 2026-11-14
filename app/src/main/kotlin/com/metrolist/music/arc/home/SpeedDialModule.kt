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
import com.metrolist.music.ui.component.CreatePlaylistDialog
import com.metrolist.music.ui.component.RandomizeGridItem
import com.metrolist.music.ui.component.SpeedDialGridItem
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
    val allItems by env.viewModel.speedDialItems.collectAsStateWithLifecycle()
    val isRandomizing by env.viewModel.isRandomizing.collectAsStateWithLifecycle()
    val speedDialRows by rememberPreference(SpeedDialRowsKey, 3)
    if (allItems.isEmpty()) {
        SpeedDialSkeleton(speedDialRows)
        return
    }
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
    // title above and page dots below stay on screen with the grid
    val gridHeight = env.height - insets.calculateTopPadding() - insets.calculateBottomPadding() - ModuleHeaderHeight - 24.dp
    val itemWidth = speedDialCellSize(availableWidth, columns, rows, gridHeight)
    val tiltEye = rememberTiltEye()

    val big = rows >= 3 && columns >= 3
    val pages = (0 until 3).map { dialPageSlots(it, columns, rows, big) }
    val capacities = pages.map { page -> page.count { it.button == null } }
    // only whole pages, at most three; a half-empty last page looks broken
    var pageCount = 1
    while (pageCount < pages.size && capacities.take(pageCount + 1).sum() <= allItems.size) pageCount++
    val pageStarts = capacities.runningFold(0) { sum, c -> sum + c }
    val pagerState = rememberPagerState(pageCount = { pageCount })

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
                                            modifier = Modifier.homeTile(onClick = { env.openYtItem(item) }, onLongClick = { env.showYtItemMenu(item) }).fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pagerState.pageCount > 1) {
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) { LinePager(pagerState) }
        }
    }
}

internal enum class DialButton { NEW_PLAYLIST, MOODS, RANDOM }

/** One tile on a dial page, in cells: a square [span] cells wide, holding an item or one of the first page's buttons. */
internal data class DialSlot(val col: Int, val row: Int, val span: Int = 1, val button: DialButton? = null)

/**
 * Where tiles sit on one dial page. The first page keeps its buttons in the bottom-right corner (random in the corner, new
 * playlist above it, moods beside it). With [big], each page has one 2x2 tile, moving corner to corner from page to page,
 * and it comes first so it gets the page's top item.
 */
internal fun dialPageSlots(page: Int, columns: Int, rows: Int, big: Boolean): List<DialSlot> {
    val taken = Array(rows) { BooleanArray(columns) }
    val slots = mutableListOf<DialSlot>()
    fun place(slot: DialSlot) {
        for (r in slot.row until slot.row + slot.span) for (c in slot.col until slot.col + slot.span) taken[r][c] = true
        slots += slot
    }
    if (big) {
        val (col, row) =
            when (page % 3) {
                0 -> 0 to 0
                1 -> (columns - 2) to (rows - 2)
                else -> (columns - 2) to 0
            }
        place(DialSlot(col, row, span = 2))
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
