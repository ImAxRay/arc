package com.metrolist.music.arc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private class ScrollProxy(
    val fraction: () -> Float,
    val canScroll: () -> Boolean,
    val isScrolling: () -> Boolean,
    val jump: suspend (Float) -> Unit,
    val indexAt: (Float) -> Int = { -1 },
    val count: () -> Int = { 0 },
    val fractionOf: (Int) -> Float = { 0f },
)

/** A named stretch of a scrolling page, starting this far down it (0 top, 1 bottom). */
private class Section(val name: String, val at: Float)

/**
 * The titled groups on the page being scrolled, so its scroll bar can name the group under the finger the way
 * Niagara's side strip does. Provided around a settings page; each settings group adds itself.
 */
class ScrollSections {
    internal val groups = mutableStateMapOf<String, LayoutCoordinates>()
    internal var viewport: LayoutCoordinates? = null
}

val LocalScrollSections = compositionLocalOf<ScrollSections?> { null }

/** Puts a titled block on the page's scroll bar. */
@Composable
fun Modifier.scrollSection(title: String?): Modifier {
    val sections = LocalScrollSections.current ?: return this
    if (title.isNullOrBlank()) return this
    DisposableEffect(sections, title) { onDispose { sections.groups.remove(title) } }
    return onGloballyPositioned { sections.groups[title] = it }
}

/** [verticalScroll] with Arc's scroll bar: the line with its dot, shown while scrolling, naming the page's groups. */
@Composable
fun Modifier.arcScroll(state: ScrollState = rememberScrollState(), insets: PaddingValues = PaddingValues()): Modifier {
    val sections = LocalScrollSections.current
    val proxy = remember(state) {
        ScrollProxy(
            fraction = { if (state.maxValue in 1 until Int.MAX_VALUE) state.value.toFloat() / state.maxValue else 0f },
            canScroll = { state.maxValue in 1 until Int.MAX_VALUE },
            isScrolling = { state.isScrollInProgress },
            jump = { state.scrollTo((it * state.maxValue).roundToInt()) },
        )
    }
    val named: (() -> List<Section>)? = sections?.let { s ->
        {
            val viewport = s.viewport
            val max = state.maxValue
            if (viewport == null || !viewport.isAttached || max !in 1 until Int.MAX_VALUE) {
                emptyList()
            } else {
                s.groups.mapNotNull { (name, coords) ->
                    if (!coords.isAttached) return@mapNotNull null
                    val y = viewport.localPositionOf(coords, Offset.Zero).y + state.value
                    Section(name, (y / max).coerceIn(0f, 1f))
                }.sortedBy { it.at }
            }
        }
    }
    val placed = if (sections != null) Modifier.onPlaced { sections.viewport = it } else Modifier
    return arcScrollbar(proxy, insets, label = null, sectionsOf = named, persistent = false).then(placed).verticalScroll(state)
}

/**
 * Arc's scroll bar over a lazy list. Long lists keep a handle to grab, like Google Photos. [label] is the bubble
 * beside the handle while dragged (a letter, a full date); [section] groups the list (a month, a folder) and puts a
 * mark on the track where each group starts. Both take the list's own item index.
 */
@Composable
fun Modifier.arcScrollbar(
    state: LazyListState,
    insets: PaddingValues = PaddingValues(),
    labelKey: Any? = null,
    section: ((Int) -> String?)? = null,
    label: ((Int) -> String?)? = null,
): Modifier {
    val proxy = remember(state) {
        fun range(): Int = (state.layoutInfo.totalItemsCount - state.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
        ScrollProxy(
            fraction = {
                val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
                val part = if (first != null && first.size > 0) state.firstVisibleItemScrollOffset.toFloat() / first.size else 0f
                ((state.firstVisibleItemIndex + part) / range()).coerceIn(0f, 1f)
            },
            canScroll = { state.canScrollForward || state.canScrollBackward },
            isScrolling = { state.isScrollInProgress },
            jump = { state.scrollToItem((it * range()).roundToInt().coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))) },
            indexAt = { (it * range()).roundToInt() },
            count = { state.layoutInfo.totalItemsCount },
            fractionOf = { (it.toFloat() / range()).coerceIn(0f, 1f) },
        )
    }
    return lazyScrollbar(proxy, insets, labelKey, section ?: label, label ?: section)
}

@Composable
fun Modifier.arcScrollbar(
    state: LazyGridState,
    insets: PaddingValues = PaddingValues(),
    labelKey: Any? = null,
    section: ((Int) -> String?)? = null,
    label: ((Int) -> String?)? = null,
): Modifier {
    val proxy = remember(state) {
        fun range(): Int = (state.layoutInfo.totalItemsCount - state.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
        ScrollProxy(
            fraction = { (state.firstVisibleItemIndex.toFloat() / range()).coerceIn(0f, 1f) },
            canScroll = { state.canScrollForward || state.canScrollBackward },
            isScrolling = { state.isScrollInProgress },
            jump = { state.scrollToItem((it * range()).roundToInt().coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))) },
            indexAt = { (it * range()).roundToInt() },
            count = { state.layoutInfo.totalItemsCount },
            fractionOf = { (it.toFloat() / range()).coerceIn(0f, 1f) },
        )
    }
    return lazyScrollbar(proxy, insets, labelKey, section ?: label, label ?: section)
}

@Composable
private fun Modifier.lazyScrollbar(
    proxy: ScrollProxy,
    insets: PaddingValues,
    labelKey: Any?,
    section: ((Int) -> String?)?,
    label: ((Int) -> String?)?,
): Modifier {
    val currentSection by rememberUpdatedState(section)
    val count by remember(proxy) { derivedStateOf { proxy.count() } }
    val sections = remember(proxy, labelKey, section == null, count) {
        val of = currentSection ?: return@remember emptyList()
        buildList {
            var last: String? = null
            for (i in 0 until count) {
                val name = of(i) ?: continue
                if (name != last) {
                    add(Section(name, proxy.fractionOf(i)))
                    last = name
                }
            }
        }.takeIf { it.size in 2..120 }.orEmpty()
    }
    return arcScrollbar(proxy, insets, label, { sections }, persistent = true)
}

private const val HANDLE_FROM_ITEMS = 60

@Composable
private fun Modifier.arcScrollbar(
    proxy: ScrollProxy,
    insets: PaddingValues,
    label: ((Int) -> String?)?,
    sectionsOf: (() -> List<Section>)?,
    persistent: Boolean,
): Modifier {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val measurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val handleFill = MaterialTheme.colorScheme.secondaryContainer
    val handleInk = MaterialTheme.colorScheme.onSecondaryContainer
    val bubble = MaterialTheme.colorScheme.primaryContainer
    val onBubble = MaterialTheme.colorScheme.onPrimaryContainer

    val currentLabel by rememberUpdatedState(label)
    val currentSections by rememberUpdatedState(sectionsOf)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragLabel by remember { mutableStateOf<String?>(null) }
    var touched by remember { mutableIntStateOf(0) }
    val shown = remember { Animatable(0f) }
    val grab by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "scrollGrab")
    val count by remember(proxy) { derivedStateOf { proxy.count() } }
    val handle = persistent && count >= HANDLE_FROM_ITEMS

    LaunchedEffect(proxy) {
        var first = true
        snapshotFlow { Triple(proxy.isScrolling(), dragging, touched) }.collectLatest { (scrolling, held, _) ->
            if (first) {
                first = false
                return@collectLatest
            }
            if (proxy.canScroll()) shown.animateTo(1f, tween(120))
            if (!scrolling && !held) {
                delay(1400)
                shown.animateTo(0f, tween(320))
            }
        }
    }

    val top = with(density) { insets.calculateTopPadding().toPx() + 12.dp.toPx() }
    val bottom = with(density) { insets.calculateBottomPadding().toPx() + 12.dp.toPx() }

    fun nameAt(f: Float): String? {
        currentLabel?.let { l -> return l(proxy.indexAt(f)) }
        return currentSections?.invoke()?.lastOrNull { it.at <= f + 0.001f }?.name
    }

    return this
        .pointerInput(proxy, top, bottom, handle) {
            val strip = 28.dp.toPx()
            val handleX = 14.dp.toPx()
            val reachY = 40.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!proxy.canScroll()) return@awaitEachGesture
                val fromEdge = if (rtl) down.position.x else size.width - down.position.x
                val span = size.height - top - bottom
                if (span <= 0f || fromEdge > strip) return@awaitEachGesture
                touched++
                val handleY = top + span * proxy.fraction()
                val onHandle = handle && abs(fromEdge - handleX) < strip && abs(down.position.y - handleY) < reachY
                // a tap goes through to whatever is under the edge (a row's menu); only a vertical drag takes the bar
                var taken = false
                var lastName: String? = null
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (taken) change.consume()
                            break
                        }
                        if (!taken) {
                            val moved = change.position - down.position
                            if (abs(moved.y) > slop && abs(moved.y) > abs(moved.x)) {
                                taken = true
                                dragging = true
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (abs(moved.x) > slop) {
                                break
                            } else {
                                continue
                            }
                        }
                        val offset = if (onHandle) down.position.y - handleY else 0f
                        dragFraction = ((change.position.y - offset - top) / span).coerceIn(0f, 1f)
                        val f = dragFraction
                        scope.launch { proxy.jump(f) }
                        val name = nameAt(f)
                        if (name != lastName) {
                            if (lastName != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            lastName = name
                        }
                        dragLabel = name
                        change.consume()
                    }
                } finally {
                    dragging = false
                    dragLabel = null
                }
            }
        }
        .drawWithContent {
            drawContent()
            val alpha = shown.value
            if (alpha <= 0f && !handle) return@drawWithContent
            val span = size.height - top - bottom
            if (span <= 0f) return@drawWithContent
            val fraction = if (dragging) dragFraction else proxy.fraction()
            val y = top + span * fraction
            val inset = if (handle) 14.dp.toPx() else 6.dp.toPx()
            val x = if (rtl) inset else size.width - inset
            if (alpha > 0f) {
                currentSections?.invoke().orEmpty().forEach { m ->
                    val ty = top + span * m.at
                    drawLine(line.copy(alpha = (0.22f + 0.4f * grab) * alpha), Offset(x - 5.dp.toPx(), ty), Offset(x + 5.dp.toPx(), ty), 1.5.dp.toPx())
                }
            }
            if (handle) {
                if (alpha > 0f) drawPill(line.copy(alpha = 0.12f * alpha), x, top, top + span, 2.dp.toPx())
                drawHandle(x, y, if (dragging) accent else handleFill.copy(alpha = 0.75f + 0.25f * alpha), if (dragging) bubble else handleInk, 1f + 0.12f * grab)
            } else {
                val width = 2.dp.toPx() + 2.dp.toPx() * grab
                drawPill(line.copy(alpha = 0.14f * alpha), x, top, top + span, width)
                drawPill((if (dragging) accent else line).copy(alpha = (if (dragging) 1f else 0.55f) * alpha), x, top, y, width)
                drawCircle(if (dragging) accent else line.copy(alpha = 0.8f * alpha), radius = 3.dp.toPx() + 5.dp.toPx() * grab, center = Offset(x, y))
            }
            dragLabel?.let { drawBubble(measurer, it, if (rtl) x + 16.dp.toPx() else x - 16.dp.toPx(), y, rtl, bubble, onBubble) }
        }
}

private fun DrawScope.drawHandle(x: Float, y: Float, fill: Color, ink: Color, scale: Float) {
    val w = 22.dp.toPx() * scale
    val h = 46.dp.toPx() * scale
    drawRoundRect(fill, Offset(x - w / 2, y - h / 2), Size(w, h), CornerRadius(w / 2))
    val stroke = 1.8.dp.toPx()
    val arm = 4.dp.toPx()
    val gap = 6.dp.toPx()
    drawLine(ink, Offset(x - arm, y - gap + arm / 2), Offset(x, y - gap - arm / 2), stroke)
    drawLine(ink, Offset(x, y - gap - arm / 2), Offset(x + arm, y - gap + arm / 2), stroke)
    drawLine(ink, Offset(x - arm, y + gap - arm / 2), Offset(x, y + gap + arm / 2), stroke)
    drawLine(ink, Offset(x, y + gap + arm / 2), Offset(x + arm, y + gap - arm / 2), stroke)
}

private fun DrawScope.drawPill(color: Color, x: Float, from: Float, to: Float, width: Float) {
    if (to <= from) return
    drawRoundRect(color, Offset(x - width / 2, from), Size(width, to - from), CornerRadius(width / 2))
}

private fun DrawScope.drawBubble(measurer: TextMeasurer, text: String, x: Float, y: Float, rtl: Boolean, fill: Color, ink: Color) {
    val layout = measurer.measure(text, TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Light, color = ink), maxLines = 1)
    val pad = 14.dp.toPx()
    val w = layout.size.width + pad * 2
    val h = layout.size.height + pad
    val left = if (rtl) x + 8.dp.toPx() else x - 8.dp.toPx() - w
    val topY = (y - h / 2).coerceIn(0f, size.height - h)
    drawRoundRect(fill, Offset(left, topY), Size(w, h), CornerRadius(h / 2))
    drawText(layout, topLeft = Offset(left + pad, topY + pad / 2))
}

fun scrollLetter(text: String): String? {
    val first = text.trim().firstOrNull() ?: return null
    return if (first.isLetter()) first.uppercase() else "#"
}

val scrollMonth: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy")
val scrollDay: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")

/** Gives the page inside its own [ScrollSections], so its scroll bar names that page's groups. */
@Composable
fun WithScrollSections(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalScrollSections provides remember { ScrollSections() }, content = content)
}

val ArcDeviceGroupKey = androidx.datastore.preferences.core.stringPreferencesKey("arcDeviceGroup")

/** Ways to group the phone's own songs, each with a header per group in the list and a mark on the scroll bar. */
enum class DeviceGroup(val label: Int) {
    FOLDER(com.metrolist.music.R.string.arc_folders),
    FORMAT(com.metrolist.music.R.string.arc_group_format),
    SIZE(com.metrolist.music.R.string.arc_group_size),
}

fun sizeBand(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return when {
        mb >= 100 -> "100 MB +"
        mb >= 50 -> "50–100 MB"
        mb >= 20 -> "20–50 MB"
        mb >= 10 -> "10–20 MB"
        mb >= 5 -> "5–10 MB"
        else -> "< 5 MB"
    }
}

/** A group's name inside a long list, in the small caps of the scroll bar's marks (a month, a letter, a folder). */
@Composable
fun ListSectionHeader(name: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    ) {
        androidx.compose.material3.Text(
            name.uppercase(),
            fontSize = 11.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        )
    }
}
