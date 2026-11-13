package com.metrolist.music.arc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.metrolist.music.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.hypot

internal val NextUpHeight = 52.dp
private val DropDistance = 56.dp
private val ThrowDistance = 480.dp
private const val THROW_SPEED = 1400f

/** The song after this one. [onDrop] takes it out of the queue. */
class NextUp(val key: String, val title: String, val artist: String, val artwork: String?, val onDrop: () -> Unit)

/** The next song as a small bar like the mini player. Flicking it away in any direction drops that song from the queue. */
@Composable
internal fun NextUpCard(next: NextUp, color: Color, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val drag = remember(next.key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val appear = remember(next.key) { Animatable(0f) }
    val currentDrop by rememberUpdatedState(next.onDrop)
    LaunchedEffect(next.key) { appear.animateTo(1f, ArcMotion.bouncy()) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier =
            modifier
                .graphicsLayer {
                    val away = (drag.value.getDistance() / DropDistance.toPx()).coerceIn(0f, 1f)
                    translationX = drag.value.x
                    translationY = drag.value.y
                    rotationZ = drag.value.x / DropDistance.toPx() * 4f
                    val scale = (0.7f + 0.3f * appear.value) * (1f - 0.15f * away)
                    scaleX = scale
                    scaleY = scale
                    alpha = appear.value.coerceIn(0f, 1f) * (1f - 0.4f * away)
                }
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.08f))
                .padding(6.dp)
                .pointerInput(next.key) {
                    val tracker = VelocityTracker()
                    var passed = false
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            passed = false
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val moved = drag.value + amount
                            // The card moves with the finger, so its own coordinates stand still; track the total instead.
                            tracker.addPosition(change.uptimeMillis, moved)
                            if (!passed && moved.getDistance() > DropDistance.toPx()) {
                                passed = true
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            scope.launch { drag.snapTo(moved) }
                        },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity()
                            val speed = hypot(velocity.x, velocity.y)
                            val moved = drag.value
                            scope.launch {
                                if (moved.getDistance() > DropDistance.toPx() || speed > THROW_SPEED) {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    val direction = if (speed > THROW_SPEED) Offset(velocity.x, velocity.y) / speed else moved / moved.getDistance()
                                    drag.animateTo(moved + direction * ThrowDistance.toPx(), tween(220))
                                    currentDrop()
                                } else {
                                    drag.animateTo(Offset.Zero, ArcMotion.springy())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(Offset.Zero, ArcMotion.springy()) } },
                    )
                },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(NextUpHeight - 12.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)),
        ) {
            if (next.artwork == null) {
                Icon(painterResource(R.drawable.music_note), null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
            AsyncImage(model = next.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.align(Alignment.CenterVertically)) {
            MarqueeText(next.title, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color))
            if (next.artist.isNotBlank()) {
                Text(next.artist, style = TextStyle(fontSize = 12.sp, color = color.copy(alpha = 0.7f)), maxLines = 1)
            }
        }
    }
}
