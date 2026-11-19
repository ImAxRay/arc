package com.metrolist.music.arc

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.component.ListDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/** Spotify's "Download your data" archive: Playlist1.json, Playlist2.json… and YourLibrary.json (Liked Songs), zipped or not. */
object SpotifyExport {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val fileName = Regex("""(?:^|/)(Playlist\d*|YourLibrary)\.json$""", RegexOption.IGNORE_CASE)

    suspend fun read(context: Context, uri: Uri, likedTitle: String): List<SpotifyLinks.Found> = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)!!.use { read(it, likedTitle) } }.getOrDefault(emptyList())
    }

    internal fun read(stream: InputStream, likedTitle: String): List<SpotifyLinks.Found> {
        val input = stream.buffered()
        input.mark(4)
        val zipped = input.read() == 'P'.code && input.read() == 'K'.code
        input.reset()
        val files = if (!zipped) {
            listOf("YourLibrary.json" to input.reader().readText())
        } else {
            val zip = ZipInputStream(input)
            generateSequence { zip.nextEntry }
                .filter { !it.isDirectory && fileName.containsMatchIn(it.name) }
                .map { it.name to zip.reader().readText() }
                .toList()
        }
        // Playlist2 after Playlist1, Liked Songs first.
        return files.sortedBy { (name, _) -> name.substringAfterLast('/').filter(Char::isDigit).toIntOrNull() ?: 0 }
            .flatMap { (_, text) -> parse(text, likedTitle) }
            .sortedByDescending { it.title == likedTitle }
    }

    internal fun parse(text: String, likedTitle: String): List<SpotifyLinks.Found> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        (root["playlists"] as? JsonArray)?.let { lists ->
            return lists.mapNotNull { it as? JsonObject }.mapNotNull { list ->
                val tracks = (list["items"] as? JsonArray).orEmpty().mapNotNull { item ->
                    val entry = item as? JsonObject ?: return@mapNotNull null
                    (entry["track"] as? JsonObject)?.let { track(it.text("trackName"), it.text("artistName"), it.text("albumName")) }
                        ?: (entry["localTrack"] as? JsonObject)?.let { local(it.text("uri")) }
                }
                SpotifyLinks.Found(SpotifyLinks.Kind.PLAYLIST, list.text("name").ifEmpty { "Spotify" }, null, tracks)
                    .takeIf { tracks.isNotEmpty() }
            }
        }
        val liked = (root["tracks"] as? JsonArray).orEmpty().mapNotNull { item ->
            (item as? JsonObject)?.let { track(it.text("track"), it.text("artist"), it.text("album")) }
        }
        return if (liked.isEmpty()) emptyList() else listOf(SpotifyLinks.Found(SpotifyLinks.Kind.PLAYLIST, likedTitle, null, liked))
    }

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun track(title: String, artist: String, album: String) =
        title.takeIf { it.isNotEmpty() }?.let { SpotifyLinks.Track(it, listOfNotNull(artist.takeIf(String::isNotEmpty)), album = album) }

    // spotify:local:Artist:Album:Title:seconds, each part form-encoded.
    private fun local(uri: String): SpotifyLinks.Track? {
        val parts = uri.split(':').takeIf { it.size >= 6 && it[1] == "local" } ?: return null
        fun part(i: Int) = runCatching { URLDecoder.decode(parts[i], "UTF-8") }.getOrDefault(parts[i]).trim()
        return track(part(4), part(2), part(3))?.copy(seconds = parts[5].toIntOrNull()?.takeIf { it > 0 } ?: -1)
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}

/** The playlists in an export; each tap opens the usual import into an Arc playlist, and the list stays for the next one. */
@Composable
fun SpotifyExportDialog(playlists: List<SpotifyLinks.Found>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val done = remember(playlists) { mutableStateListOf<SpotifyLinks.Found>() }
    ListDialog(onDismiss = onDismiss) {
        items(playlists) { found ->
            ListItem(
                headlineContent = { Text(found.title) },
                supportingContent = { Text(pluralStringResource(R.plurals.n_song, found.tracks.size, found.tracks.size)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                trailingContent = if (found in done) ({ Icon(painterResource(R.drawable.check), contentDescription = null) }) else null,
                modifier = Modifier.padding(horizontal = 8.dp).clickable {
                    done += found
                    SpotifyImport.show(context, found, fromLink = false)
                },
            )
        }
    }
}
