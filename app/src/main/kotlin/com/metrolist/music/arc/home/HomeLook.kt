package com.metrolist.music.arc.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.arc.ArcMotion
import kotlinx.coroutines.launch

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
fun Modifier.homeTile(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val scale = remember { Animatable(1f) }
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
    }

    fun release() {
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
    }.pointerInput(Unit) {
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

/** A Home module's title: small caps name, an optional big thin number beside it, and a chevron when it opens a page. */
@Composable
fun ModuleHeader(
    title: String,
    modifier: Modifier = Modifier,
    numeral: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(ModuleHeaderHeight)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
        if (numeral != null) {
            Text(numeral, fontSize = 24.sp, fontWeight = FontWeight.ExtraLight, color = colors.onSurfaceVariant, maxLines = 1)
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

/** Page indicator in the line's style: a thin track that fills up to the current page and ends in a dot. */
@Composable
fun LinePager(state: PagerState, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val line = MaterialTheme.colorScheme.primary
    val pages = state.pageCount
    Spacer(
        modifier
            .width(LinePagerStep * pages)
            .height(24.dp)
            // reads the pager's position while drawing, so a swipe redraws this without recomposing anything
            .drawBehind {
                val y = size.height / 2
                val stroke = 2.dp.toPx()
                val dot = 3.dp.toPx()
                val start = dot
                val end = size.width - dot
                drawLine(track, Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
                val position = (state.currentPage + state.currentPageOffsetFraction).coerceIn(0f, (pages - 1).toFloat())
                val x = start + (end - start) * (position + 1) / pages
                drawLine(line, Offset(start, y), Offset(x, y), stroke, StrokeCap.Round)
                drawCircle(line, dot, Offset(x, y))
            },
    )
}

private val LinePagerStep = 24.dp
