package com.metrolist.music.arc

import android.content.Context
import android.widget.Toast
import androidx.navigation.NavController
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the album or single a song is on. A song saved without its album (a video, or one added from a mix) asks
 * YouTube Music which release it belongs to first.
 */
fun openSongAlbum(context: Context, scope: CoroutineScope, navController: NavController, mediaId: String, albumId: String?, onOpened: () -> Unit = {}) {
    if (albumId != null) {
        navController.navigate("album/$albumId")
        onOpened()
        return
    }
    if (isOwnFile(mediaId)) {
        Toast.makeText(context, R.string.arc_no_album, Toast.LENGTH_SHORT).show()
        return
    }
    scope.launch {
        val found = withContext(Dispatchers.IO) { YouTube.queue(listOf(mediaId)).getOrNull()?.firstOrNull()?.album?.id }
        if (found != null) {
            navController.navigate("album/$found")
            onOpened()
        } else {
            Toast.makeText(context, R.string.arc_no_album, Toast.LENGTH_SHORT).show()
        }
    }
}
