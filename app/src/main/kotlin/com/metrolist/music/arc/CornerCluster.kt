package com.metrolist.music.arc

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.cos
import kotlin.math.sin

internal val PlaySize = 64.dp
internal val IconTarget = 48.dp
private val ArcRadius = 104.dp
val CornerBoxHeight = 164.dp
private val Edge = 4.dp
private val ExtraStep = 54.dp
private val ExtrasHeight = PlaySize + Edge * 2

/**
 * The quieter buttons on their own, at the same spots they take in the corner layout, so a layout
 * without the corner controls (the vinyl) keeps every button where the thumb expects it.
 */
@Composable
fun CornerExtras(extras: List<CapsuleButton>, side: Float, width: Dp, content: Color) {
    Box(Modifier.size(width, ExtrasHeight)) {
        extraCentres(extras.size, side, width).forEachIndexed { index, x ->
            FloatingIcon(extras[index], content, Modifier.offset(x - IconTarget / 2, ExtrasHeight / 2 - IconTarget / 2), toggle = true)
        }
    }
}


/**
 * Where play and then previous, share, like and next sit inside a [CornerBoxHeight] tall box of
 * [width], so other layouts can animate into the same spots.
 */
internal fun cornerLayout(side: Float, width: Dp): Pair<DpOffset, List<DpOffset>> {
    fun mirrored(right: DpOffset, left: DpOffset) = DpOffset(lerp(right.x, left.x, side), lerp(right.y, left.y, side))

    val rowY = CornerBoxHeight - PlaySize / 2 - Edge
    val rightPlay = DpOffset(width - Edge - PlaySize / 2, rowY)
    val leftPlay = DpOffset(Edge + PlaySize / 2, rowY)
    return mirrored(rightPlay, leftPlay) to
        List(4) { index ->
            val rightAngle = Math.toRadians(180.0 + 30.0 * index)
            val leftAngle = Math.toRadians(270.0 + 30.0 * index)
            mirrored(
                DpOffset(rightPlay.x + ArcRadius * cos(rightAngle).toFloat(), rowY + ArcRadius * sin(rightAngle).toFloat()),
                DpOffset(leftPlay.x + ArcRadius * cos(leftAngle).toFloat(), rowY + ArcRadius * sin(leftAngle).toFloat()),
            )
        }
}

internal fun extraCentres(count: Int, side: Float, width: Dp): List<Dp> {
    val reach = ArcRadius + PlaySize / 2
    val rightFirst = width - Edge - reach
    val leftFirst = Edge + reach
    val step = minOf(ExtraStep, (rightFirst - Edge - IconTarget / 2) / count)
    return List(count) { index ->
        val fromPanel = count - index
        lerp(rightFirst - step * fromPanel, leftFirst + step * fromPanel, side)
    }
}

@Composable
internal fun PlayButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    hold: PlayHold,
    fill: Color,
    tint: Color,
    modifier: Modifier,
) {
    val currentHold by rememberUpdatedState(hold)
    val held = remember { mutableStateOf(false) }
    val snippet by com.metrolist.music.arc.snippet.Snippet.recording.collectAsState()
    val stopsSnippet = snippet?.locked == true
    val interactions = remember { MutableInteractionSource() }
    val glow = rememberPressDisc(interactions)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .tapBounce()
                .size(PlaySize)
                .drawBehind {
                    val g = glow.value
                    if (g > 0f) {
                        val radius = size.minDimension * 0.95f
                        drawCircle(
                            Brush.radialGradient(
                                0f to fill.copy(alpha = 0.6f * g),
                                0.5f to fill.copy(alpha = 0.35f * g),
                                1f to Color.Transparent,
                                center = center,
                                radius = radius,
                            ),
                            radius = radius,
                        )
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (held.value) {
                                    change.consume()
                                    currentHold.onMove(change.position - down.position)
                                }
                                if (!change.pressed) break
                            }
                        } finally {
                            if (held.value) {
                                held.value = false
                                currentHold.onEnd()
                            }
                        }
                    }
                }.clip(CircleShape)
                .background(fill)
                .combinedClickable(
                    interactionSource = interactions,
                    indication = LocalIndication.current,
                    onClick = { if (stopsSnippet) com.metrolist.music.arc.snippet.Snippet.stop("stop tapped") else onClick() },
                    onLongClick = {
                        if (stopsSnippet) return@combinedClickable
                        held.value = true
                        currentHold.onStart()
                    },
                ),
    ) {
        if (stopsSnippet) {
            Box(Modifier.size(22.dp).background(tint, androidx.compose.foundation.shape.RoundedCornerShape(5.dp)))
        } else {
            Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(30.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FloatingIcon(button: CapsuleButton, content: Color, modifier: Modifier, toggle: Boolean = false, remoteTaps: Int = 0) {
    val icon = button.icon ?: return
    val tint = button.tint ?: if (toggle && !button.active) content.copy(alpha = 0.6f) else content
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .tapBounce(remoteTaps)
                .size(IconTarget)
                .alpha(if (button.enabled) 1f else 0.35f)
                .clip(CircleShape)
                .combinedClickable(enabled = button.enabled, onClick = button.onClick, onLongClick = button.onLongClick),
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = button.iconModifier.size(26.dp))
        button.hint?.invoke(this)
        if (toggle && button.active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(4.dp)
                    .background(content, CircleShape),
            )
        }
    }
}

@Composable
fun animatedPanelSide(onLeft: Boolean): Float {
    val side by animateFloatAsState(if (onLeft) 1f else 0f, ArcMotion.snappy(), label = "panelSide")
    return side
}
