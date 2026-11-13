package com.metrolist.music.arc

import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Songs the user never wants to hear. Kept on the phone in preferences, so the database stays as it is. */
private val DislikedSongsKey = stringSetPreferencesKey("arcDislikedSongs")

fun dislikedSongs(context: Context): Flow<Set<String>> = context.dataStore.data.map { it[DislikedSongsKey].orEmpty() }

suspend fun toggleDislike(context: Context, songId: String) {
    context.dataStore.edit {
        val current = it[DislikedSongsKey].orEmpty()
        it[DislikedSongsKey] = if (songId in current) current - songId else current + songId
    }
}

/** The menu row that dislikes a song, or lets a disliked one play again. */
@Composable
fun dislikeMenuItem(songId: String, onDone: () -> Unit): Material3MenuItemData {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val disliked by remember(context) { dislikedSongs(context) }.collectAsState(initial = emptySet())
    val isDisliked = songId in disliked
    return Material3MenuItemData(
        title = { Text(stringResource(if (isDisliked) R.string.arc_undislike else R.string.arc_dislike)) },
        description = { Text(stringResource(if (isDisliked) R.string.arc_undislike_desc else R.string.arc_dislike_desc)) },
        icon = { Icon(painterResource(R.drawable.block), contentDescription = null) },
        onClick = {
            scope.launch { toggleDislike(context, songId) }
            onDone()
        },
    )
}
