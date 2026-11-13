package com.metrolist.music.arc

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.metrolist.music.LocalPlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val SETTLE_MS = 200L
private const val WHOLE_LIST_UP_TO = 25

/**
 * Once a song list has rested for a moment, reads ahead the first seconds of every song on screen, or of the whole
 * list when it is short, so the one tapped starts from the cache. The service skips it on mobile data and in battery
 * saver, and never fetches a song twice.
 */
@Composable
fun WarmVisibleSongs(state: LazyListState, byKey: Boolean = false, all: List<String> = emptyList(), idAt: (Any) -> String?) {
    val service = LocalPlayerConnection.current?.service ?: return
    val currentIdAt by rememberUpdatedState(idAt)
    val currentAll by rememberUpdatedState(all)
    LaunchedEffect(state, service) {
        snapshotFlow { Triple(state.isScrollInProgress, state.firstVisibleItemIndex, state.layoutInfo.totalItemsCount) }.collectLatest { (moving, _, _) ->
            if (moving) return@collectLatest
            delay(SETTLE_MS)
            val whole = currentAll.takeIf { it.size in 1..WHOLE_LIST_UP_TO }
            val ids = whole ?: state.layoutInfo.visibleItemsInfo.mapNotNull { currentIdAt(if (byKey) it.key else it.index) }
            if (ids.isNotEmpty()) service.warmStreams(ids, "screen")
        }
    }
}
