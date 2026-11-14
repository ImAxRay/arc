package com.metrolist.music.arc

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalPlayerAwareWindowInsets
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sign
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val EdgeZone = 72.dp
private val MaxScrollPerSecond = 1400.dp
private val NoNestedScroll = object : NestedScrollConnection {}

/** Every heart on screen, so a drag that starts on one can paint the rows it passes. */
private object LikeHearts {
    class Heart(val songId: String, val bounds: () -> Rect?, val setLiked: (Boolean) -> Unit)

    val hearts = HashMap<Any, Heart>()
}

@Composable
fun SongLikeButton(song: SongItem, modifier: Modifier = Modifier) = SongLikeButton(remember(song.id) { song.toMediaMetadata() }, modifier)

/**
 * The heart on every song row: likes a song from any list, whether or not it's in the library yet.
 * Holding it and dragging up or down gives every row the finger passes the same like as the first one;
 * near the top or bottom of the list it scrolls on by itself.
 */
@Composable
fun SongLikeButton(song: MediaMetadata, modifier: Modifier = Modifier) {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val haptics = LocalHapticFeedback.current
    val loaded by remember(song.id) { database.song(song.id).map { it to true } }.collectAsState(initial = null to false)
    val librarySong = loaded.first
    val liked = librarySong?.song?.liked == true
    val toggle = {
        database.transaction {
            val current = librarySong
            val updated: SongEntity
            if (current == null) {
                insert(song, SongEntity::toggleLike)
                updated = song.toSongEntity().let(SongEntity::toggleLike)
            } else {
                updated = current.song.toggleLike()
                update(updated)
            }
            syncUtils.likeSong(updated)
        }
    }
    val currentLiked by rememberUpdatedState(liked)
    val currentId by rememberUpdatedState(song.id)
    val currentToggle by rememberUpdatedState(toggle)
    val setLiked = { want: Boolean -> if (want != currentLiked) currentToggle() }

    val token = remember { Any() }
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val bounds = { place[0]?.takeIf { it.isAttached }?.boundsInWindow() }
    DisposableEffect(token, song.id) {
        LikeHearts.hearts[token] = LikeHearts.Heart(song.id, bounds, setLiked)
        onDispose { LikeHearts.hearts.remove(token) }
    }

    val scope = rememberCoroutineScope()
    val scroller = remember { NestedScrollDispatcher() }
    val pinnable = LocalPinnableContainer.current
    val density = LocalDensity.current
    val insets = LocalPlayerAwareWindowInsets.current
    val windowHeight = LocalWindowInfo.current.containerSize.height

    IconButton(
        modifier =
            modifier
                .onGloballyPositioned { place[0] = it }
                .nestedScroll(NoNestedScroll, scroller)
                .pointerInput(Unit) {
                    var target = false
                    var fingerY = 0f
                    // Where the finger last painted, moved along with the list's own scrolling so rows that slide under a still finger count as passed.
                    // Not measured from this heart: once it scrolls far away, the list no longer places it at its real spot.
                    var lastY = 0f
                    var scrolledSince = 0f
                    var column = 0f
                    // By song, since a list reuses a row that scrolled away for one coming in.
                    val painted = HashSet<String>()
                    var pin: PinnableContainer.PinnedHandle? = null
                    var autoScroll: Job? = null

                    fun paintTo(y: Float) {
                        val coordinates = place[0]?.takeIf { it.isAttached } ?: return
                        val from = lastY + scrolledSince
                        scrolledSince = 0f
                        lastY = y
                        val top = min(from, y)
                        val bottom = max(from, y)
                        // Every heart in this column whose middle the finger crossed since the last move, so a quick drag skips none.
                        LikeHearts.hearts.values.forEach { heart ->
                            if (heart.songId in painted) return@forEach
                            val other = heart.bounds() ?: return@forEach
                            if (abs(other.center.x - column) > coordinates.size.width || other.center.y !in top..bottom) return@forEach
                            painted += heart.songId
                            heart.setLiked(target)
                        }
                    }

                    fun end() {
                        autoScroll?.cancel()
                        autoScroll = null
                        pin?.release()
                        pin = null
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            target = !currentLiked
                            painted.clear()
                            painted += currentId
                            setLiked(target)
                            val coordinates = place[0] ?: return@detectDragGesturesAfterLongPress
                            val middle = coordinates.localToWindow(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
                            column = middle.x
                            fingerY = middle.y
                            lastY = fingerY
                            scrolledSince = 0f
                            // Kept composed while the list scrolls it away, or the drag would end with it.
                            pin = pinnable?.pin()
                            autoScroll =
                                scope.launch {
                                    val zone = with(density) { EdgeZone.toPx() }
                                    val maxStep = with(density) { MaxScrollPerSecond.toPx() }
                                    val top = insets.getTop(density) + zone
                                    val bottom = windowHeight - insets.getBottom(density) - zone
                                    var last = withFrameNanos { it }
                                    while (isActive) {
                                        val now = withFrameNanos { it }
                                        val seconds = (now - last) / 1e9f
                                        last = now
                                        val depth =
                                            when {
                                                fingerY < top -> ((top - fingerY) / zone).coerceAtMost(1f)
                                                fingerY > bottom -> -((fingerY - bottom) / zone).coerceAtMost(1f)
                                                else -> 0f
                                            }
                                        if (depth == 0f) continue
                                        // The rows are laid out with this scroll before the next frame, when the finger paints again.
                                        paintTo(fingerY)
                                        scrolledSince += scroller.dispatchPostScroll(Offset.Zero, Offset(0f, depth * depth * sign(depth) * maxStep * seconds), NestedScrollSource.UserInput).y
                                    }
                                }
                        },
                        onDragEnd = ::end,
                        onDragCancel = ::end,
                        onDrag = { change, _ ->
                            change.consume()
                            val coordinates = place[0]?.takeIf { it.isAttached } ?: return@detectDragGesturesAfterLongPress
                            fingerY = coordinates.localToWindow(change.position).y
                            paintTo(fingerY)
                        },
                    )
                // no tick on touch-down: a finger resting on the heart isn't a like, and the like itself buzzes
                }.tapBounce(tick = false),
        onClick = toggle,
    ) {
        Icon(
            painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
            contentDescription = null,
            // Red like the player's heart, so a liked song looks the same everywhere.
            tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.likePop(liked, song.id, known = loaded.second),
        )
    }
}
