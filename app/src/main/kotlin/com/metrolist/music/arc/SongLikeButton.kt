package com.metrolist.music.arc

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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

/** Every heart on screen, so a drag that starts on one can paint the rows it passes. */
private object LikeHearts {
    class Heart(val bounds: () -> Rect?, val setLiked: (Boolean) -> Unit)

    val hearts = HashMap<Any, Heart>()
}

@Composable
fun SongLikeButton(song: SongItem, modifier: Modifier = Modifier) = SongLikeButton(remember(song.id) { song.toMediaMetadata() }, modifier)

/**
 * The heart on every song row: likes a song from any list, whether or not it's in the library yet.
 * Holding it and dragging up or down gives every row the finger passes the same like as the first one.
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
    val currentToggle by rememberUpdatedState(toggle)
    val setLiked = { want: Boolean -> if (want != currentLiked) currentToggle() }

    val token = remember { Any() }
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val bounds = { place[0]?.takeIf { it.isAttached }?.boundsInWindow() }
    DisposableEffect(token) {
        LikeHearts.hearts[token] = LikeHearts.Heart(bounds, setLiked)
        onDispose { LikeHearts.hearts.remove(token) }
    }

    IconButton(
        modifier =
            modifier
                .onGloballyPositioned { place[0] = it }
                .pointerInput(Unit) {
                    var target = false
                    var lastY = 0f
                    val painted = HashSet<Any>()
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            target = !currentLiked
                            painted.clear()
                            painted += token
                            setLiked(target)
                            lastY = bounds()?.center?.y ?: 0f
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val coordinates = place[0] ?: return@detectDragGesturesAfterLongPress
                            val own = bounds() ?: return@detectDragGesturesAfterLongPress
                            val y = coordinates.localToWindow(change.position).y
                            val from = min(lastY, y)
                            val to = max(lastY, y)
                            lastY = y
                            // Every heart in this column whose middle the finger crossed since the last move, so a quick drag skips none.
                            LikeHearts.hearts.forEach { (key, heart) ->
                                if (key in painted) return@forEach
                                val other = heart.bounds() ?: return@forEach
                                if (abs(other.center.x - own.center.x) > own.width || other.center.y !in from..to) return@forEach
                                painted += key
                                heart.setLiked(target)
                            }
                        },
                    )
                }.tapBounce(),
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
