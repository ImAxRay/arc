package com.metrolist.music.arc.home

import com.metrolist.music.arc.Usage
import com.metrolist.music.constants.DialCornerRadius

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.PlaylistItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.metrolist.music.arc.LocalTiltEye
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
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
    val storedSizes by store.sizes.collectAsStateWithLifecycle()
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
    val sizes = if (storedPanels != null) storedSizes else remember(columns, rows) { defaultDialSizes(columns, rows) }
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
    // while editing, all three pages show, so a tile can be carried to an empty one
    val shownPages = if (edit != null) pages.size else pageCount
    val pagerState = rememberPagerState(pageCount = { shownPages })
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

    fun spanIn(sizes: Map<DialCell, Int>, cell: DialCell, panel: DialPanel) = sizes[cell] ?: if (panel == DialPanel.Continue && big) 2 else 1

    fun fit(cell: DialCell, span: Int) = DialCell(cell.page, cell.row.coerceIn(0, rows - span), cell.col.coerceIn(0, columns - span))

    fun overlaps(a: DialCell, sa: Int, b: DialCell, sb: Int) =
        a.page == b.page && a.row < b.row + sb && b.row < a.row + sa && a.col < b.col + sb && b.col < a.col + sa

    /**
     * Puts [panel] at [to] as a tile [span] cells across, kept whole on the page. Panels it lands on go to where it came
     * from ([from]) when they fit there whole, else to free cells, on another page if need be.
     */
    fun place(from: DialCell, panel: DialPanel, span: Int, to: DialCell) {
        val anchor = fit(to, span)
        val base = panels - from
        val baseSizes = sizes - from
        val covered = base.filter { (c, p) -> overlaps(c, spanIn(baseSizes, c, p), anchor, span) }
        var next = base - covered.keys + (anchor to panel)
        var nextSizes = baseSizes - covered.keys + (anchor to span)
        val homeless = mutableListOf<DialPanel>()
        covered.forEach { (c, p) ->
            val s = spanIn(baseSizes, c, p)
            val home = fit(from, s)
            if (next.none { (oc, op) -> overlaps(oc, spanIn(nextSizes, oc, op), home, s) }) {
                next = next + (home to p)
                if (s > 1) nextSizes = nextSizes + (home to s)
            } else {
                homeless += p
            }
        }
        // this page's free cells first, then the other pages'
        val free = (listOf(anchor.page) + (0 until 3).filter { it != anchor.page }).flatMap { page ->
            dialPageSlots(page, columns, rows, big, next, sizes = nextSizes).filter { it.panel == null && it.span == 1 }.map { DialCell(page, it.row, it.col) }
        }
        homeless.zip(free).forEach { (p, cell) -> next = next + (cell to p) }
        save(next, nextSizes)
    }

    @Composable
    fun Tile(slot: DialSlot, item: YTItem?, modifier: Modifier) {
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

    // the tile being dragged floats above the pages, so it can be carried to another page; held at the dial's side,
    // it turns the page
    var dragFrom by remember { mutableStateOf<DialDrag?>(null) }
    var dragTravel by remember { mutableStateOf(Offset.Zero) }
    // where a dropped tile was let go, relative to its new cell, so it settles from there
    val landings = remember { mutableStateMapOf<DialCell, Offset>() }

    fun centreOf(slot: DialSlot, travel: Offset) = Offset((slot.col + slot.span / 2f) * cellPx + travel.x, (slot.row + slot.span / 2f) * cellPx + travel.y)

    fun cellUnder(point: Offset) = (point.x / cellPx).toInt().coerceIn(0, columns - 1) to (point.y / cellPx).toInt().coerceIn(0, rows - 1)

    val overCell by remember(cellPx, columns, rows) { derivedStateOf { dragFrom?.let { cellUnder(centreOf(it.slot, dragTravel)) } } }
    val edgeSide by remember(cellPx, columns) {
        derivedStateOf {
            val x = dragFrom?.let { centreOf(it.slot, dragTravel).x } ?: return@derivedStateOf 0
            when {
                x < cellPx * EDGE_ZONE -> -1
                x > (columns - EDGE_ZONE) * cellPx -> 1
                else -> 0
            }
        }
    }
    LaunchedEffect(edgeSide) {
        if (edgeSide == 0) return@LaunchedEffect
        while (true) {
            delay(EDGE_DWELL_MS)
            val to = pagerState.currentPage + edgeSide
            if (to !in 0 until pagerState.pageCount) break
            pagerState.animateScrollToPage(to)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // the dial has no title on Home; in edit mode it gets one like every other module
        if (LocalHomeEditing.current || edit != null) ModuleHeader(stringResource(R.string.speed_dial))
        Box(Modifier.fillMaxWidth().height(itemWidth * rows)) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = rememberPageFling(pagerState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            // a dragged tile's own page stays composed while the dial turns away from it, so the drag carries on
            beyondViewportPageCount = if (edit != null) pages.size else 0,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pageItems = allItems.drop(pageStarts[page]).take(capacities[page])
            var next = 0
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
            // the tile under a dragged one on its own page slides into the dragged tile's cell meanwhile
            val lifted = dragFrom?.takeIf { it.page == page && it.span == 1 }?.slot
            val over = overCell?.takeIf { lifted != null && pagerState.currentPage == page }?.let { (col, row) ->
                pages[page].firstOrNull { col in it.col until it.col + it.span && row in it.row until it.row + it.span }?.takeIf { it != lifted && it.span == 1 }
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

                        if (edit == null) {
                            val mix = flipped[slot]
                            key(slot.col, slot.row) {
                                FlipTile(
                                    back = mix?.first,
                                    delayMs = (mix?.second ?: 0) * FLIP_STAGGER_MS,
                                    modifier = placed,
                                    backContent = { mixItem ->
                                        // flat, so the whole mix cover shows and its number reads
                                        CompositionLocalProvider(LocalTiltEye provides null) {
                                            Box(cell) {
                                                SpeedDialGridItem(
                                                    item = mixItem,
                                                    isPinned = false,
                                                    isActive = env.isActive(mixItem.id),
                                                    isPlaying = env.isPlaying.value,
                                                    thumbnailSize = if (slot.span > 1) 544 else 200,
                                                    modifier = Modifier.homeTile(onClick = { playMix(env, mixItem) }, onLongClick = { env.showYtItemMenu(mixItem) }, cover = mixItem.thumbnail, glowCorner = DialCornerRadius).fillMaxSize(),
                                                )
                                            }
                                        }
                                    },
                                ) { Tile(slot, item, cell) }
                            }
                        } else key(slot.col, slot.row) {
                            val shift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
                            val goal = if (lifted != null && over == slot) Offset((lifted.col - slot.col) * cellPx, (lifted.row - slot.row) * cellPx) else Offset.Zero
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
                                                            place(at, panel, span, at)
                                                        }
                                                        env.menuState.dismiss()
                                                    }
                                                },
                                        )
                                    }
                                },
                                onRemove = if (current != null || item != null) { { change(::remove) } } else null,
                                onDrag = { travel ->
                                    if (travel == null) {
                                        dragFrom = null
                                    } else {
                                        if (dragFrom == null) dragFrom = DialDrag(page, slot, item, slot.panel?.let { spanIn(sizes, at, it) } ?: slot.span)
                                        dragTravel = travel
                                    }
                                },
                                onDrop = { travel ->
                                    // it lands whole on whichever page is showing now, where its top left corner is
                                    val moving = current ?: item?.let { DialPanel.Item(it.id) } ?: return@DialEditCell
                                    val span = if (current != null) spanIn(sizes, at, current) else slot.span
                                    val anchor = fit(DialCell(pagerState.currentPage, (slot.row + travel.y / cellPx).roundToInt(), (slot.col + travel.x / cellPx).roundToInt()), span)
                                    if (anchor == at) return@DialEditCell
                                    landings[anchor] = Offset((slot.col - anchor.col) * cellPx + travel.x, (slot.row - anchor.row) * cellPx + travel.y)
                                    change {
                                        if (current == null) item?.let(::pin)
                                        place(at, moving, span, anchor)
                                    }
                                },
                                shift = { landings[at] ?: shift.value },
                            ) { Tile(slot, item, cell) }
                        }
                    }
                }
            }
        }
        dragFrom?.let { drag ->
            // where it would land, at its own size
            val ghost = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .offset {
                        val a = fit(DialCell(0, (drag.slot.row + dragTravel.y / cellPx).roundToInt(), (drag.slot.col + dragTravel.x / cellPx).roundToInt()), drag.span)
                        IntOffset((16.dp.toPx() + a.col * cellPx).roundToInt(), (a.row * cellPx).roundToInt())
                    }.size(itemWidth * drag.span)
                    .padding(4.dp)
                    .background(ghost.copy(alpha = 0.12f), RoundedCornerShape(DialCornerRadius))
                    .border(2.dp, ghost, RoundedCornerShape(DialCornerRadius)),
            )
            Box(
                Modifier
                    .offset(16.dp + itemWidth * drag.slot.col, itemWidth * drag.slot.row)
                    .size(itemWidth * drag.slot.span)
                    .graphicsLayer {
                        translationX = dragTravel.x
                        translationY = dragTravel.y
                        scaleX = 1.06f
                        scaleY = 1.06f
                        shadowElevation = 12.dp.toPx()
                        shape = RoundedCornerShape(DialCornerRadius)
                    },
            ) { Tile(drag.slot, drag.item, Modifier.padding(4.dp)) }
        }
        }

        if (pagerState.pageCount > 1) {
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                PageDots(
                    pagerState,
                    if (edit == null) {
                        Modifier
                    } else {
                        // a tap on the dots turns the page, also with a tile held in the other hand
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { tap -> scope.launch { pagerState.animateScrollToPage((tap.x / size.width * pagerState.pageCount).toInt().coerceIn(0, pagerState.pageCount - 1)) } }
                        }
                    },
                )
            }
        }
    }
}

/** A mood's mix plays at once from the dial instead of opening its page. */
private fun playMix(env: HomeEnv, mix: YTItem) {
    if (env.isListenTogetherGuest) return env.openYtItem(mix)
    Usage.homeTap(Usage.listKey(mix.id), mix.title)
    env.playerConnection.playQueue(YouTubeQueue((mix as? PlaylistItem)?.playEndpoint ?: WatchEndpoint(playlistId = mix.id)))
}

/** A tile being dragged in dial edit: where it came from and what it shows. */
private data class DialDrag(val page: Int, val slot: DialSlot, val item: YTItem?, val span: Int)

// a dragged tile's centre within this share of a cell from the dial's side turns the page after a short hold
private const val EDGE_ZONE = 0.3f
private const val EDGE_DWELL_MS = 550L
private const val PULSE_SETTLE_MS = 150L

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
    // a press-like bump whenever playback pauses or resumes, from anywhere; a brief buffering blip doesn't count
    val pulse = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(playing) }
    LaunchedEffect(playing) {
        delay(PULSE_SETTLE_MS)
        if (playing == settled) return@LaunchedEffect
        settled = playing
        pulse.animateTo(0.93f, tween(90))
        pulse.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
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
            modifier.graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }.homeTile(
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
