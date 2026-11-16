package com.metrolist.music.arc.home

import com.metrolist.music.constants.DialCornerRadius

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.arc.isOwnFile
import com.metrolist.music.db.entities.SpeedDialItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.arc.SpeedDialRowsKey
import com.metrolist.music.arc.SpeedDialShortcut
import com.metrolist.music.arc.SpeedDialSkeleton
import com.metrolist.music.arc.rememberTiltEye
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.CreatePlaylistDialog
import com.metrolist.music.ui.component.SpeedDialGridItem
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
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
    val continueReady by env.viewModel.continueReady.collectAsStateWithLifecycle()
    val speedDialRows by rememberPreference(SpeedDialRowsKey, 3)
    if (dialItems.isEmpty() || !continueReady) {
        SpeedDialSkeleton(speedDialRows)
        return
    }
    val store = remember { DialStore.get(env.viewModel.context) }
    val storedPanels by store.panels.collectAsStateWithLifecycle()
    val hiddenItems by store.hidden.collectAsStateWithLifecycle()
    val dialMood by env.viewModel.dialMood.collectAsStateWithLifecycle()
    val moodMixes by env.viewModel.dialMoodMixes.collectAsStateWithLifecycle()
    val edit = LocalDialEdit.current
    val continueId = env.mediaMetadata.value?.id ?: continueSong?.id
    // The Continue tile's song isn't repeated in the grid, judged once per dial: a tile tapped later stays put, lit as playing.
    val preview = LocalModulePreview.current
    if (env.viewModel.dialHiddenId == null && !preview) env.viewModel.dialHiddenId = continueId

    val targetItemSize = 160.dp
    val availableWidth = env.width - 32.dp
    val columns = (availableWidth / targetItemSize).toInt().coerceAtLeast(3)
    val rows =
        when {
            columns >= 6 -> 1
            columns >= 4 -> 2
            else -> speedDialRows
        }
    // full-width cells: more rows make the dial taller, never thinner
    val itemWidth = availableWidth / columns
    val tiltEye = rememberTiltEye()
    val cellPx = with(LocalDensity.current) { itemWidth.toPx() }

    val panels = storedPanels ?: remember(columns, rows) { defaultDialPanels(columns, rows) }
    val hasContinue = DialPanel.Continue in panels.values
    val hiddenId = (env.viewModel.dialHiddenId ?: continueId).takeIf { hasContinue }
    val byId = remember(dialItems) { dialItems.associateBy { it.id } }
    val placedIds = remember(panels) { panels.values.mapNotNullTo(HashSet()) { (it as? DialPanel.Item)?.id } }
    val allItems = remember(dialItems, hiddenId, placedIds, hiddenItems) { dialItems.filterNot { it.id == hiddenId || it.id in placedIds || it.id in hiddenItems } }
    // a switched-on mood fills the free tiles with its mixes; the dial's own items wait underneath
    val freeItems = moodMixes?.takeIf { dialMood != null && edit == null } ?: allItems

    val big = rows >= 3 && columns >= 3
    val pages = (0 until 3).map { dialPageSlots(it, columns, rows, big, panels, continueTile = continueId != null) }
    val capacities = pages.map { page -> page.count { it.panel == null } }
    // only whole pages, at most three; a half-empty last page looks broken; pages holding panels always show
    // a val, so the pager's page count lambda stays the same object; a captured var made a new one each composition,
    // and the pager re-reading it recomposed the dial every frame
    val panelPages = panels.keys.filter { it.row < rows && it.col < columns }.maxOfOrNull { it.page + 1 } ?: 1
    val pageCount = maxOf(panelPages, (1 until pages.size).takeWhile { capacities.take(it + 1).sum() <= allItems.size }.lastOrNull()?.plus(1) ?: 1).coerceAtMost(pages.size)
    val pageStarts = capacities.runningFold(0) { sum, c -> sum + c }
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val shownIds = remember(allItems, pageCount, placedIds) { allItems.take(pageStarts[pageCount]).mapTo(HashSet()) { it.id } + placedIds + listOfNotNull(hiddenId) }
    SideEffect { if (!preview) env.viewModel.dialShownIds.value = shownIds }
    val radioSeeds = remember(dialItems) { dialItems.filter { it is SongItem && !isOwnFile(it.id) }.map { it.id } }

    fun save(new: Map<DialCell, DialPanel>) = store.setPanels(new)

    fun pin(item: YTItem) {
        if (item.id !in env.pinnedTypes.value) env.scope.launch(Dispatchers.IO) { env.database.speedDialDao.insert(SpeedDialItem.fromYTItem(item)) }
    }

    fun unpin(id: String) = env.scope.launch(Dispatchers.IO) { env.database.speedDialDao.delete(id) }

    /** Takes [panel] off every other cell when it's one of a kind, then puts it at [cell] (null clears the cell). */
    fun put(cell: DialCell, panel: DialPanel?) {
        val single = panel != null && panel !is DialPanel.Mood && panel !is DialPanel.Item
        val kept = panels.filter { (c, p) -> c != cell && !(single && p == panel) }
        save(if (panel == null) kept else kept + (cell to panel))
    }

    Column(Modifier.fillMaxWidth()) {
        // the dial has no title on Home; in edit mode it gets one like every other module
        if (LocalHomeEditing.current || edit != null) ModuleHeader(stringResource(R.string.speed_dial))
        HorizontalPager(
            state = pagerState,
            flingBehavior = rememberPageFling(pagerState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth().height(itemWidth * rows),
        ) { page ->
            val pageItems = freeItems.drop(pageStarts[page]).take(capacities[page])
            var next = 0
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.size(itemWidth * columns, itemWidth * rows)) {
                    pages[page].forEachIndexed { index, slot ->
                        val at = DialCell(page, slot.row, slot.col)
                        val placed = Modifier.offset(itemWidth * slot.col, itemWidth * slot.row).size(itemWidth * slot.span)
                        val cell = Modifier.padding(4.dp)
                        val item: YTItem? =
                            when (val panel = slot.panel) {
                                null -> pageItems.getOrNull(next++)
                                is DialPanel.Item -> byId[panel.id]
                                else -> null
                            }

                        @Composable
                        fun Tile(modifier: Modifier) {
                            when (val panel = slot.panel) {
                                DialPanel.Continue -> ContinueTile(env, continueSong, big = slot.span > 1, modifier = modifier)
                                DialPanel.Dice -> Box(modifier) { DiceTile(env, Modifier) }
                                DialPanel.Radio -> Box(modifier) { SpeedDialShortcut(icon = R.drawable.radio, label = null, onClick = { playDiscoverRadio(env, radioSeeds) }) }
                                DialPanel.Moods -> Box(modifier) { SpeedDialShortcut(icon = R.drawable.discover_tune, label = null, onClick = { env.navController.navigate("mood_and_genres") }) }
                                is DialPanel.Mood -> Box(modifier) { MoodTile(env, panel, active = dialMood == panel, modifier = Modifier) }
                                DialPanel.Plus -> {
                                    var showCreatePlaylist by remember { mutableStateOf(false) }
                                    if (showCreatePlaylist) CreatePlaylistDialog(onDismiss = { showCreatePlaylist = false })
                                    Box(modifier) { SpeedDialShortcut(icon = R.drawable.add, label = null, onClick = { showCreatePlaylist = true }) }
                                }
                                null, is DialPanel.Item ->
                                    if (item == null) {
                                        if (edit != null) EmptyDialCell(modifier)
                                    } else {
                                        key(item.id) {
                                            Box(modifier) {
                                                SpeedDialGridItem(
                                                    item = item,
                                                    isPinned = item.id in env.pinnedTypes.value,
                                                    isActive = env.isActive(item.id),
                                                    isPlaying = env.isPlaying.value,
                                                    tiltEye = tiltEye,
                                                    thumbnailSize = if (slot.span > 1) 544 else 200,
                                                    modifier = Modifier.homeTile(onClick = { env.openYtItem(item) }, onLongClick = { env.showYtItemMenu(item) }, cover = item.thumbnail, glowCorner = DialCornerRadius).fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                            }
                        }

                        if (edit == null) {
                            Box(placed) { Tile(cell) }
                        } else {
                            val current = slot.panel
                            fun remove() {
                                when {
                                    current is DialPanel.Item -> {
                                        unpin(current.id)
                                        put(at, null)
                                    }
                                    current != null -> put(at, null)
                                    item != null -> {
                                        if (item.id in env.pinnedTypes.value) unpin(item.id)
                                        store.hide(item.id)
                                    }
                                }
                            }
                            DialEditCell(
                                edit = edit,
                                index = index,
                                modifier = placed,
                                onTap = {
                                    env.menuState.show {
                                        DialCellSheet(
                                            env = env,
                                            current = current,
                                            itemTitle = item?.title,
                                            onPin =
                                                when {
                                                    current is DialPanel.Item -> { { unpin(current.id); put(at, null); env.menuState.dismiss() } }
                                                    current == null && item != null -> { { pin(item); put(at, DialPanel.Item(item.id)); env.menuState.dismiss() } }
                                                    else -> null
                                                },
                                            onPut = { panel ->
                                                put(at, panel)
                                                env.menuState.dismiss()
                                            },
                                            onRemove = if (current != null || item != null) { { remove(); env.menuState.dismiss() } } else null,
                                        )
                                    }
                                },
                                onRemove = if (current != null || item != null) ::remove else null,
                                onDrop = { travel ->
                                    val col = ((slot.col + slot.span / 2f) + travel.x / cellPx).toInt().coerceIn(0, columns - 1)
                                    val row = ((slot.row + slot.span / 2f) + travel.y / cellPx).toInt().coerceIn(0, rows - 1)
                                    // a drop on any of a 2x2 tile's cells lands on the tile
                                    val target = pages[page].firstOrNull { col in it.col until it.col + it.span && row in it.row until it.row + it.span } ?: return@DialEditCell
                                    if (target == slot) return@DialEditCell
                                    val to = DialCell(page, target.row, target.col)
                                    val moving = current ?: item?.also(::pin)?.let { DialPanel.Item(it.id) } ?: return@DialEditCell
                                    save(panels - at - to + (to to moving) + listOfNotNull(target.panel?.let { at to it }))
                                },
                            ) { Tile(cell) }
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

/** One tile on a dial page, in cells: a square [span] cells wide, holding a [panel] or, without one, one of the dial's items. */
internal data class DialSlot(val col: Int, val row: Int, val span: Int = 1, val panel: DialPanel? = null)

/**
 * Where tiles sit on one dial page: the user's [panels] at their cells, Continue 2x2 when [big] and the 2x2 is clear
 * (the top item takes the spot when there is nothing to continue), every other cell left for items in reading order.
 */
internal fun dialPageSlots(page: Int, columns: Int, rows: Int, big: Boolean, panels: Map<DialCell, DialPanel>, continueTile: Boolean = true): List<DialSlot> {
    val taken = Array(rows) { BooleanArray(columns) }
    val slots = mutableListOf<DialSlot>()
    fun place(slot: DialSlot) {
        for (r in slot.row until slot.row + slot.span) for (c in slot.col until slot.col + slot.span) taken[r][c] = true
        slots += slot
    }
    val here = panels.filterKeys { it.page == page && it.row < rows && it.col < columns }
    here.entries.firstOrNull { it.value == DialPanel.Continue }?.key?.let { cell ->
        val fits = big && cell.row + 1 < rows && cell.col + 1 < columns &&
            here.keys.none { it != cell && it.row in cell.row..cell.row + 1 && it.col in cell.col..cell.col + 1 }
        place(DialSlot(cell.col, cell.row, span = if (fits) 2 else 1, panel = DialPanel.Continue.takeIf { continueTile }))
    }
    here.forEach { (cell, panel) -> if (panel != DialPanel.Continue && !taken[cell.row][cell.col]) place(DialSlot(cell.col, cell.row, panel = panel)) }
    for (r in 0 until rows) for (c in 0 until columns) if (!taken[r][c]) place(DialSlot(c, r))
    return slots
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
            val player = if (env.playerConnection.service.isPlayerReady.value) env.playerConnection.player else null
            val duration = player?.duration ?: 0L
            if (player != null && current != null && !playing && duration > 0) makeTimeString(duration - player.currentPosition) else null
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
                glowCorner = DialCornerRadius,
            ),
    )
}
