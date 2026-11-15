package com.metrolist.music.arc.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.R
import com.metrolist.music.arc.ArcMotion
import com.metrolist.music.arc.GlowArea
import com.metrolist.music.arc.LocalPressGlow
import com.metrolist.music.constants.ThumbnailCornerRadius
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Height of [ModuleHeader], so modules that fit themselves to the screen can leave room for it. */
val ModuleHeaderHeight = 48.dp

/** How far a Home tile dips under the finger. Gentler than a button's, since tiles are big. */
private const val TILE_PRESSED_SCALE = 0.96f

/** How far a pressed tile leans toward the finger, in degrees, like a card picked up in Balatro. */
private const val TILE_TILT = 7f

/** A press that starts this soon after touch-down; a scroll moves past the touch slop before then and shows nothing. */
private const val PRESS_DELAY_MS = 60L

/**
 * Every Home tile's press: the tile dips and leans toward the finger, then springs back with a little wobble. Nothing
 * happens when a touch turns into a scroll, and the only haptic is the hold, when the tile lifts and its menu opens.
 * Animates only while touched, so an idle Home draws nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.homeTile(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    cover: String? = null,
    glowArea: GlowArea = GlowArea.Whole,
    glowCorner: Dp = ThumbnailCornerRadius,
): Modifier {
    val scale = remember { Animatable(1f) }
    val glow = LocalPressGlow.current
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val layoutDensity = LocalDensity.current
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun press(at: Offset, size: IntSize) {
        val x = (at.x / size.width.coerceAtLeast(1) - 0.5f) * 2f
        val y = (at.y / size.height.coerceAtLeast(1) - 0.5f) * 2f
        scope.launch { scale.animateTo(TILE_PRESSED_SCALE, tween(80)) }
        scope.launch { tiltY.animateTo(x.coerceIn(-1f, 1f) * TILE_TILT, tween(80)) }
        scope.launch { tiltX.animateTo(-y.coerceIn(-1f, 1f) * TILE_TILT, tween(80)) }
        glow?.press(placed[0], with(layoutDensity) { glowCorner.toPx() }, glowArea, cover)
    }

    fun release() {
        glow?.release()
        scope.launch { scale.animateTo(1f, ArcMotion.springy()) }
        scope.launch { tiltX.animateTo(0f, ArcMotion.bouncy()) }
        scope.launch { tiltY.animateTo(0f, ArcMotion.bouncy()) }
    }

    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        rotationX = tiltX.value
        rotationY = tiltY.value
        cameraDistance = 12f * density
    }.onPlaced { placed[0] = it }
        .pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            // null: still held after the delay; true: lifted already (a quick tap); false: it became a scroll
            val early =
                withTimeoutOrNull(PRESS_DELAY_MS) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                        if (!change.pressed) return@withTimeoutOrNull true
                        if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
                    }
                    @Suppress("UNREACHABLE_CODE")
                    false
                }
            when (early) {
                false -> return@awaitEachGesture
                true -> {
                    scope.launch {
                        press(down.position, size)
                        kotlinx.coroutines.delay(80)
                        release()
                    }
                    return@awaitEachGesture
                }
                null -> press(down.position, size)
            }
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || (change.position - down.position).getDistance() > slop) break
            }
            release()
        }
    }.then(
        if (onLongClick == null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch { scale.animateTo(ArcMotion.LIFTED_SCALE, ArcMotion.snappy()) }
                    onLongClick()
                },
            )
        },
    )
}

/** A Home module's title: small caps name, a play button when the module can play as a queue, a chevron when it opens a page. */
@Composable
fun ModuleHeader(
    title: String,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val edit = LocalHomeEdit.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(ModuleHeaderHeight)
                .then(
                    when {
                        edit != null -> Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = edit.enter)
                        onClick != null -> Modifier.clickable(onClick = onClick)
                        else -> Modifier
                    },
                )
                .padding(start = 20.dp, end = 12.dp),
    ) {
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            letterSpacing = 1.8.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        if (onPlay != null) {
            IconButton(onClick = onPlay) {
                Icon(painterResource(R.drawable.play), contentDescription = stringResource(R.string.play_all), tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        if (onClick != null) {
            Icon(
                painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp).size(22.dp),
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * Page dots: the current page's dot is stretched into a pill, and the pill flows into the next dot as the pager moves.
 * Reads the pager's position while drawing, so a swipe redraws this without recomposing anything.
 */
@Composable
fun PageDots(state: PagerState, modifier: Modifier = Modifier) {
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val active = MaterialTheme.colorScheme.primary
    val pages = state.pageCount
    Spacer(
        modifier
            .width(PageDot * pages + (PagePill - PageDot) + PageDotGap * (pages - 1))
            .height(24.dp)
            .drawBehind {
                val dot = PageDot.toPx()
                val stretch = PagePill.toPx() - dot
                val gap = PageDotGap.toPx()
                val top = (size.height - dot) / 2
                val position = (state.currentPage + state.currentPageOffsetFraction).coerceIn(0f, (pages - 1).toFloat())
                var x = 0f
                for (page in 0 until pages) {
                    val near = (1f - abs(position - page)).coerceIn(0f, 1f)
                    val width = dot + stretch * near
                    drawRoundRect(lerp(idle, active, near), Offset(x, top), Size(width, dot), CornerRadius(dot / 2))
                    x += width + gap
                }
            },
    )
}

private val PageDot = 6.dp
private val PagePill = 20.dp
private val PageDotGap = 6.dp

/** Height of [HomeDayHeader], kept fixed so the dial under it can fit the rest of the screen. */
internal val DayHeaderHeight = 84.dp

/**
 * "SATURDAY · 14 NOV" in small caps over a thin greeting for the time of day; the top of Home. It fades out as Home
 * scrolls up, read while drawing so scrolling never recomposes it.
 */
@Composable
fun HomeDayHeader(listState: LazyListState) {
    // Home stays composed in the background, so the day and greeting are read again whenever the app comes back
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(LocalDateTime.now().truncatedTo(ChronoUnit.HOURS), lifecycle) {
        lifecycle.currentStateFlow.collect { if (it == Lifecycle.State.RESUMED) value = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS) }
    }
    val greeting =
        when (now.hour) {
            in 5..11 -> R.string.arc_home_morning
            in 12..17 -> R.string.arc_home_afternoon
            in 18..22 -> R.string.arc_home_evening
            else -> R.string.arc_home_night
        }
    Column(
        verticalArrangement = Arrangement.Bottom,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(DayHeaderHeight)
                .graphicsLayer {
                    alpha =
                        if (listState.firstVisibleItemIndex > 0) {
                            0f
                        } else {
                            1f - (listState.firstVisibleItemScrollOffset / (DayHeaderHeight.toPx() * 0.7f)).coerceIn(0f, 1f)
                        }
                }.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
    ) {
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE · d MMM")).uppercase(),
            fontSize = 11.sp,
            letterSpacing = 1.8.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            stringResource(greeting),
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
