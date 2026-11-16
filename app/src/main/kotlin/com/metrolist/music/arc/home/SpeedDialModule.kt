package com.metrolist.music.arc.home

import com.metrolist.music.constants.DialCornerRadius

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.CreatePlaylistDialog
import com.metrolist.music.ui.component.SpeedDialGridItem
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
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
    val sizes by store.sizes.collectAsStateWithLifecycle()
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
    val cellPx = with(LocalDensity.current) { itemWidth.toPx() }

    val panels = storedPanels ?: remember(columns, rows) { defaultDialPanels(columns, rows) }
    val hasContinue = DialPanel.Continue in panels.values
    val hiddenId = (env.viewModel.dialHiddenId ?: continueId).takeIf { hasContinue }
    val byId = remember(dialItems) { dialItems.associateBy { it.id } }
    val placedIds = remember(panels) { panels.values.mapNotNullTo(HashSet()) { (it as? DialPanel.Item)?.id } }
    val pinned = env.pinnedTypes.value
    // an item taken off the dial comes back if the user pins it again later
    val allItems = remember(dialItems, hiddenId, placedIds, hiddenItems, pinned) { dialItems.filterNot { it.id == hiddenId || it.id in placedIds || (it.id in hiddenItems && it.id !in pinned) } }
    // a cell whose pinned item was unpinned from a menu is free again
    val livePanels = remember(panels, byId) { panels.filterValues { it !is DialPanel.Item || it.id in byId } }

    val big = rows >= 3 && columns >= 3
    val pages = (0 until 3).map { dialPageSlots(it, columns, rows, big, livePanels, continueTile = continueId != null, sizes = sizes) }
    val capacities = pages.map { page -> page.count { it.panel == null } }
    // only whole pages, at most three; a half-empty last page looks broken; pages holding panels always show
    // a val, so the pager's page count lambda stays the same object; a captured var made a new one each composition,
    // and the pager re-reading it recomposed the dial every frame
    val panelPages = livePanels.keys.filter { it.row < rows && it.col < columns }.maxOfOrNull { it.page + 1 } ?: 1
    val pageCount = maxOf(panelPages, (1 until pages.size).takeWhile { capacities.take(it + 1).sum() <= allItems.size }.lastOrNull()?.plus(1) ?: 1).coerceAtMost(pages.size)
    val pageStarts = capacities.runningFold(0) { sum, c -> sum + c }
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val shownIds = remember(allItems, pageCount, placedIds) { allItems.take(pageStarts[pageCount]).mapTo(HashSet()) { it.id } + placedIds + listOfNotNull(hiddenId) }
    SideEffect { if (!preview) env.viewModel.dialShownIds.value = shownIds }
    val radioSeeds = remember(dialItems) { dialItems.filter { it is SongItem && !isOwnFile(it.id) }.map { it.id } }

    fun save(new: Map<DialCell, DialPanel>, newSizes: Map<DialCell, Int> = sizes) = store.setPanels(new, newSizes)

    // pins made or taken away during a change go into its undo step
    fun pin(item: YTItem) {
        if (item.id in env.pinnedTypes.value) return
        edit?.open?.unpin?.add(item.id)
        env.scope.launch(Dispatchers.IO) { env.database.speedDialDao.insert(SpeedDialItem.fromYTItem(item)) }
    }

    fun unpin(id: String) {
        byId[id]?.let { edit?.open?.repin?.add(SpeedDialItem.fromYTItem(it)) }
        env.scope.launch(Dispatchers.IO) { env.database.speedDialDao.delete(id) }
    }

    /** Runs one edit as one undo step. */
    fun change(action: () -> Unit) {
        val step = DialStep(store.panels.value, store.sizes.value, store.hidden.value)
        edit?.open = step
        action()
        edit?.open = null
        edit?.steps?.add(step)
    }

    /** Takes [panel] off every other cell when it's one of a kind, then puts it at [cell] (null clears the cell). */
    fun put(cell: DialCell, panel: DialPanel?) {
        val single = panel != null && panel !is DialPanel.Mood && panel !is DialPanel.Item
        val kept = panels.filter { (c, p) -> c != cell && !(single && p == panel) }
        save(if (panel == null) kept else kept + (cell to panel))
    }

    /** Grows or shrinks the tile at [at] to [span] cells across; whatever it grows over moves to free cells, on another page if need be. */
    fun resize(at: DialCell, panel: DialPanel, span: Int) {
        val anchor = DialCell(at.page, minOf(at.row, rows - span), minOf(at.col, columns - span))
        val displaced = panels.filterKeys { it != at && it.page == anchor.page && it.row in anchor.row until anchor.row + span && it.col in anchor.col until anchor.col + span }
        var next = panels - at - displaced.keys + (anchor to panel)
        val nextSizes = sizes - at - displaced.keys + (anchor to span)
        // this page's free cells first, then the other pages'
        val free = (listOf(at.page) + (0 until 3).filter { it != at.page }).flatMap { page ->
            dialPageSlots(page, columns, rows, big, next, sizes = nextSizes).filter { it.panel == null && it.span == 1 }.map { DialCell(page, it.row, it.col) }
        }
        displaced.values.zip(free).forEach { (p, cell) -> next = next + (cell to p) }
        save(next, nextSizes)
    }

    Column(Modifier.fillMaxWidth()) {
        // the dial has no title on Home; in edit mode it gets one like every other module
        if (LocalHomeEditing.current || edit != null) ModuleHeader(stringResource(R.string.speed_dial))
        HorizontalPager(
            state = pagerState,
            flingBehavior = rememberPageFling(pagerState),
            // while editing, a swipe on a tile moves the tile; the dots below turn the page
            userScrollEnabled = edit == null,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth().height(itemWidth * rows),
        ) { page ->
            val pageItems = allItems.drop(pageStarts[page]).take(capacities[page])
            var next = 0
            // the tile being dragged and the one it would swap with, which slides into its place meanwhile
            var lifted by remember { mutableStateOf<DialSlot?>(null) }
            var over by remember { mutableStateOf<DialSlot?>(null) }
            // where a dropped tile was let go, relative to its new cell, so it settles from there
            val landings = remember { mutableStateMapOf<DialCell, Offset>() }
            // a switched-on mood here turns the tiles nearest it into its mixes, nearest first; Continue stays
            val moodSlot = pages[page].firstOrNull { dialMood != null && it.panel == dialMood }
            val flipped: Map<DialSlot, Pair<YTItem, Int>> =
                remember(moodSlot, moodMixes, pages[page], edit) {
                    val mixes = moodMixes
                    if (moodSlot == null || mixes == null || edit != null) return@remember emptyMap()
                    fun centre(s: DialSlot) = Offset(s.col + s.span / 2f, s.row + s.span / 2f)
                    val from = centre(moodSlot)
                    pages[page]
                        .filter { it != moodSlot && it.panel != DialPanel.Continue }
                        .sortedBy { (centre(it) - from).getDistanceSquared() }
                        .zip(mixes)
                        .withIndex()
                        .associate { (rank, pair) -> pair.first to (pair.second to rank) }
                }

            fun targetOf(slot: DialSlot, travel: Offset): DialSlot? {
                val col = ((slot.col + slot.span / 2f) + travel.x / cellPx).toInt().coerceIn(0, columns - 1)
                val row = ((slot.row + slot.span / 2f) + travel.y / cellPx).toInt().coerceIn(0, rows - 1)
                // a drop on any of a 2x2 tile's cells lands on the tile
                return pages[page].firstOrNull { col in it.col until it.col + it.span && row in it.row until it.row + it.span }?.takeIf { it != slot }
            }
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
                                                    thumbnailSize = if (slot.span > 1) 544 else 200,
                                                    modifier = Modifier.homeTile(onClick = { env.openYtItem(item) }, onLongClick = { env.showYtItemMenu(item) }, cover = item.thumbnail, glowCorner = DialCornerRadius).fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                            }
                        }

                        if (edit == null) {
                            val mix = flipped[slot]
                            key(slot.col, slot.row) { FlipTile(
                                back = mix?.first,
                                delayMs = (mix?.second ?: 0) * FLIP_STAGGER_MS,
                                modifier = placed,
                                backContent = { mixItem ->
                                    Box(cell) {
                                        SpeedDialGridItem(
                                            item = mixItem,
                                            isPinned = false,
                                            isActive = env.isActive(mixItem.id),
                                            isPlaying = env.isPlaying.value,
                                            thumbnailSize = if (slot.span > 1) 544 else 200,
                                            modifier = Modifier.homeTile(onClick = { env.openYtItem(mixItem) }, onLongClick = { env.showYtItemMenu(mixItem) }, cover = mixItem.thumbnail, glowCorner = DialCornerRadius).fillMaxSize(),
                                        )
                                    }
                                },
                            ) { Tile(cell) } }
                        } else key(slot.col, slot.row) {
                            val shift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
                            val from = lifted
                            val goal = if (from != null && over == slot) Offset((from.col - slot.col) * cellPx, (from.row - slot.row) * cellPx) else Offset.Zero
                            val landing = landings[at]
                            LaunchedEffect(goal, landing) {
                                if (landing != null) {
                                    shift.snapTo(landing)
                                    landings.remove(at)
                                }
                                shift.animateTo(goal, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                            }
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
                                                    current is DialPanel.Item -> { { change { unpin(current.id); put(at, null) }; env.menuState.dismiss() } }
                                                    current == null && item != null -> { { change { pin(item); put(at, DialPanel.Item(item.id)) }; env.menuState.dismiss() } }
                                                    else -> null
                                                },
                                            onPut = { panel ->
                                                change { put(at, panel) }
                                                env.menuState.dismiss()
                                            },
                                            onRemove = if (current != null || item != null) { { change(::remove); env.menuState.dismiss() } } else null,
                                            size = slot.span,
                                            maxSize = minOf(rows, columns, 3),
                                            onSize =
                                                (current ?: item?.let { DialPanel.Item(it.id) })?.let { panel ->
                                                    { span: Int ->
                                                        change {
                                                            if (current == null) item?.let(::pin)
                                                            resize(at, panel, span)
                                                        }
                                                        env.menuState.dismiss()
                                                    }
                                                },
                                        )
                                    }
                                },
                                onRemove = if (current != null || item != null) { { change(::remove) } } else null,
                                onDrag = { travel ->
                                    lifted = slot.takeIf { travel != null }
                                    over = travel?.let { targetOf(slot, it) }
                                },
                                onDrop = { travel ->
                                    // let go past the dial's side: the tile goes to the next page over, at the same height
                                    val x = (slot.col + slot.span / 2f) * cellPx + travel.x
                                    val side = when {
                                        x < 0f && page > 0 -> -1
                                        x > columns * cellPx && page < pages.size - 1 -> 1
                                        else -> 0
                                    }
                                    val to =
                                        if (side != 0) {
                                            DialCell(page + side, slot.row, if (side > 0) 0 else columns - slot.span)
                                        } else {
                                            val target = targetOf(slot, travel) ?: return@DialEditCell
                                            landings[DialCell(page, target.row, target.col)] = Offset((slot.col - target.col) * cellPx + travel.x, (slot.row - target.row) * cellPx + travel.y)
                                            DialCell(page, target.row, target.col)
                                        }
                                    change {
                                        val moving = current ?: item?.also(::pin)?.let { DialPanel.Item(it.id) } ?: return@change
                                        // whatever sat there swaps back; a size goes with its tile
                                        save(
                                            panels - at - to + (to to moving) + listOfNotNull(panels[to]?.let { at to it }),
                                            sizes - at - to + listOfNotNull(sizes[at]?.let { to to it }, sizes[to]?.let { at to it }),
                                        )
                                    }
                                    if (side != 0) {
                                        scope.launch {
                                            // a page that only now holds a tile appears a frame later
                                            snapshotFlow { pagerState.pageCount }.first { it > page + side }
                                            pagerState.animateScrollToPage(page + side)
                                        }
                                    }
                                },
                                shift = { landings[at] ?: shift.value },
                            ) { Tile(cell) }
                        }
                    }
                }
            }
        }

        if (pageCount > 1) {
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                PageDots(
                    pagerState,
                    if (edit == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(pageCount) {
                            detectTapGestures { tap -> scope.launch { pagerState.animateScrollToPage((tap.x / size.width * pageCount).toInt().coerceIn(0, pageCount - 1)) } }
                        }
                    },
                )
            }
        }
    }
}

/** One tile on a dial page, in cells: a square [span] cells wide, holding a [panel] or, without one, one of the dial's items. */
internal data class DialSlot(val col: Int, val row: Int, val span: Int = 1, val panel: DialPanel? = null)

/**
 * Where tiles sit on one dial page: the user's [panels] at their cells, Continue 2x2 when [big] and the 2x2 is clear
 * (the top item takes the spot when there is nothing to continue), every other cell left for items in reading order.
 */
internal fun dialPageSlots(
    page: Int,
    columns: Int,
    rows: Int,
    big: Boolean,
    panels: Map<DialCell, DialPanel>,
    continueTile: Boolean = true,
    sizes: Map<DialCell, Int> = emptyMap(),
): List<DialSlot> {
    val taken = Array(rows) { BooleanArray(columns) }
    val slots = mutableListOf<DialSlot>()
    fun place(slot: DialSlot) {
        for (r in slot.row until slot.row + slot.span) for (c in slot.col until slot.col + slot.span) taken[r][c] = true
        slots += slot
    }
    val here = panels.filterKeys { it.page == page && it.row < rows && it.col < columns }
    // a big tile never covers another panel's cell; it shrinks instead, so nothing the user placed disappears
    fun fits(cell: DialCell, span: Int) =
        cell.row + span <= rows && cell.col + span <= columns &&
            (cell.row until cell.row + span).all { r -> (cell.col until cell.col + span).all { c -> !taken[r][c] } } &&
            here.keys.none { it != cell && it.row in cell.row until cell.row + span && it.col in cell.col until cell.col + span }
    fun spanOf(cell: DialCell, panel: DialPanel) = sizes[cell] ?: if (panel == DialPanel.Continue && big) 2 else 1
    here.entries.sortedByDescending { spanOf(it.key, it.value) }.forEach { (cell, panel) ->
        var span = spanOf(cell, panel)
        while (span > 1 && !fits(cell, span)) span--
        if (!taken[cell.row][cell.col]) place(DialSlot(cell.col, cell.row, span, panel.takeIf { it != DialPanel.Continue || continueTile }))
    }
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
