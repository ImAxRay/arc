package com.metrolist.music.arc

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalDatabase
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R

/** Which songs of a list are picked for a group action. Rows register where they are, so a drag can pick the ones it passes. */
@Stable
class SongSelection {
    val ids = mutableStateListOf<String>()
    var active by mutableStateOf(false)
        private set
    internal val rows = HashMap<Any, Pair<String, () -> Rect?>>()

    fun start(id: String) {
        active = true
        if (id !in ids) ids += id
    }

    fun toggle(id: String) {
        if (id in ids) ids -= id else ids += id
    }

    fun selectAll(all: List<String>) {
        if (ids.size == all.size) {
            ids.clear()
        } else {
            ids.clear()
            ids += all
        }
    }

    fun clear() {
        active = false
        ids.clear()
    }
}

@Composable
fun rememberSongSelection(): SongSelection {
    val selection = remember { SongSelection() }
    BackHandler(enabled = selection.active) { selection.clear() }
    return selection
}

/**
 * The row gesture for a selectable list: a tap plays ([onClick]) or, while selecting, picks the
 * row; holding starts selecting; holding and dragging up or down picks every row the finger passes.
 */
@Composable
fun Modifier.selectableSong(id: String, selection: SongSelection, warmId: String = id, onClick: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val currentClick by rememberUpdatedState(onClick)
    val service = LocalPlayerConnection.current?.service
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    DisposableEffect(id, selection) {
        val token = Any()
        selection.rows[token] = id to { place[0]?.takeIf { it.isAttached }?.boundsInWindow() }
        onDispose { selection.rows.remove(token) }
    }
    return onGloballyPositioned { place[0] = it }
        .pointerInput(id, warmId, selection) {
            detectTapGestures(
                // the finger is down a good 100 ms before the tap counts: start fetching the song then
                onPress = { if (!selection.active) service?.warmStreams(listOf(warmId), "press") },
                onTap = { if (selection.active) selection.toggle(id) else currentClick() },
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    selection.start(id)
                },
            )
        }.pointerInput(id, selection) {
            detectDragGesturesAfterLongPress(
                onDrag = { change, _ ->
                    change.consume()
                    val at = place[0]?.localToWindow(change.position) ?: return@detectDragGesturesAfterLongPress
                    pickRowAt(at, selection, haptics)
                },
            )
        }
}

private fun pickRowAt(at: Offset, selection: SongSelection, haptics: HapticFeedback) {
    val hit = selection.rows.values.firstOrNull { (_, bounds) -> bounds()?.contains(at) == true }?.first ?: return
    if (hit in selection.ids) return
    selection.ids += hit
    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
}

/**
 * Shown while songs are picked: attached to the top of the mini player like a tab, or on its own
 * at the bottom when nothing plays. A row of the common actions sits right in it, and [onMore]
 * opens the full menu for the rest (playlists, downloads and so on). [picked] gives the chosen
 * songs in list order.
 */
@Composable
fun SongSelectionBar(
    selection: SongSelection,
    all: () -> List<String>,
    picked: () -> List<MediaMetadata>,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    ownFiles: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    val nowPlaying by (playerConnection?.mediaMetadata ?: remember { MutableStateFlow(null) }).collectAsState()
    // Over the mini player it tucks under its top edge, so the two read as one piece.
    val attached = nowPlaying != null
    val tuck = if (attached) 24.dp else 0.dp
    val shape = if (attached) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) else RoundedCornerShape(24.dp)
    AnimatedVisibility(
        visible = selection.active,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier.offset(y = tuck),
    ) {
        val colors = MaterialTheme.colorScheme
        val none = selection.ids.isEmpty()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (attached) 24.dp else 12.dp, vertical = if (attached) 0.dp else 8.dp)
                    .clip(shape)
                    .background(colors.surfaceContainer)
                    .border(1.dp, colors.outline.copy(alpha = 0.3f), shape)
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp + tuck),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = selection::clear, modifier = Modifier.tapBounce()) {
                    Icon(painterResource(R.drawable.close), contentDescription = null, tint = colors.onSurface)
                }
                Text(
                    pluralStringResource(R.plurals.n_selected, selection.ids.size, selection.ids.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { selection.selectAll(all()) }, modifier = Modifier.tapBounce()) {
                    Icon(painterResource(R.drawable.select_all), contentDescription = null, tint = colors.onSurface)
                }
            }
            Row {
                fun queueOf(songs: List<MediaMetadata>) = songs.map { it.toMediaItem() }
                SelectionAction(R.drawable.play, R.string.play, !none) {
                    playerConnection?.playQueue(ListQueue(items = queueOf(picked())))
                    selection.clear()
                }
                // Shuffling one song is just playing it, so a single online song gets Radio in that place.
                if (selection.ids.singleOrNull()?.let { !isOwnFile(it) } == true) {
                    SelectionAction(R.drawable.radio, R.string.arc_tile_radio, true) {
                        picked().firstOrNull()?.let { playerConnection?.playQueue(YouTubeQueue.radio(it)) }
                        selection.clear()
                    }
                } else {
                    SelectionAction(R.drawable.shuffle, R.string.shuffle, !none) {
                        playerConnection?.playQueue(ListQueue(items = queueOf(picked().shuffled())))
                        selection.clear()
                    }
                }
                SelectionAction(R.drawable.playlist_play, R.string.arc_next, !none) {
                    playerConnection?.playNext(queueOf(picked()))
                    selection.clear()
                }
                SelectionAction(R.drawable.queue_music, R.string.arc_queue, !none) {
                    playerConnection?.addToQueue(queueOf(picked()))
                    selection.clear()
                }
                SelectionAction(R.drawable.favorite, R.string.like_all, !none) {
                    val songs = picked()
                    scope.launch(Dispatchers.IO) { likeAll(database, syncUtils, songs) }
                }
                SelectionAction(R.drawable.more_horiz, R.string.arc_more, !none, onClick = onMore)
            }
            if (ownFiles) {
                Row {
                    SelectionAction(R.drawable.close, R.string.arc_triage_out, !none) {
                        val ids = selection.ids.toList()
                        scope.launch { SongTriage.applyIds(context, database, ids, kept = false) }
                        selection.clear()
                    }
                    SelectionAction(R.drawable.check, R.string.arc_triage_keep, !none) {
                        val ids = selection.ids.toList()
                        scope.launch { SongTriage.applyIds(context, database, ids, kept = true) }
                        selection.clear()
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SelectionAction(@DrawableRes icon: Int, @StringRes label: Int, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .weight(1f)
                .then(if (enabled) Modifier.tapBounce() else Modifier)
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 8.dp)
                .alpha(if (enabled) 1f else 0.4f),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Likes every picked song, or unlikes them all when they're all liked already, like the menu's "Like all". */
private suspend fun likeAll(database: MusicDatabase, syncUtils: SyncUtils, songs: List<MediaMetadata>) {
    val known = songs.map { it to database.song(it.id).first() }
    val allLiked = known.all { (_, song) -> song?.song?.liked == true }
    database.transaction {
        known.forEach { (meta, song) ->
            when {
                song == null -> insert(meta, SongEntity::toggleLike)
                song.song.liked == allLiked -> {
                    val updated = song.song.toggleLike()
                    update(updated)
                    syncUtils.likeSong(updated)
                }
            }
        }
    }
}
