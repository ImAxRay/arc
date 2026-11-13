package com.metrolist.music.arc

import android.content.Context
import androidx.core.content.edit
import com.metrolist.innertube.models.AlbumItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ARTISTS_FILE = "arc_ignored_artists"
private const val RELEASES_FILE = "arc_hidden_releases"
private const val SEP = '\u0001'

/** Artists and releases the user took out of New releases. Kept in prefs, so backups carry them. */
object ReleaseFilter {
    data class Entry(val id: String, val title: String, val subtitle: String?, val thumbnail: String?)

    sealed interface Change {
        val entry: Entry

        data class ArtistIgnored(override val entry: Entry) : Change
        data class ReleaseHidden(override val entry: Entry) : Change
    }

    private val _artists = MutableStateFlow<List<Entry>?>(null)
    private val _releases = MutableStateFlow<List<Entry>?>(null)
    private val _changes = MutableSharedFlow<Change>(extraBufferCapacity = 4)

    /** Each ignore or hide, for the app-wide pop-up with Undo. */
    val changes = _changes.asSharedFlow()

    fun artists(context: Context) = load(context).let { _artists.asStateFlow() }

    fun releases(context: Context) = load(context).let { _releases.asStateFlow() }

    fun keeps(context: Context, album: AlbumItem): Boolean {
        load(context)
        val artistIds = _artists.value.orEmpty().mapTo(HashSet()) { it.id }
        if (_releases.value.orEmpty().any { it.id == album.id }) return false
        return album.artists.orEmpty().none { it.id in artistIds }
    }

    fun ignoreArtist(context: Context, id: String, name: String, thumbnail: String?) {
        val entry = Entry(id, name, null, thumbnail)
        put(context, ARTISTS_FILE, entry)
        _changes.tryEmit(Change.ArtistIgnored(entry))
    }

    fun hideRelease(context: Context, album: AlbumItem) {
        val entry = Entry(album.id, album.title, album.artists?.joinToString { it.name }, album.thumbnail)
        put(context, RELEASES_FILE, entry)
        _changes.tryEmit(Change.ReleaseHidden(entry))
    }

    fun undo(context: Context, change: Change) =
        when (change) {
            is Change.ArtistIgnored -> remove(context, ARTISTS_FILE, change.entry.id)
            is Change.ReleaseHidden -> remove(context, RELEASES_FILE, change.entry.id)
        }

    fun unignoreArtist(context: Context, id: String) = remove(context, ARTISTS_FILE, id)

    fun unhideRelease(context: Context, id: String) = remove(context, RELEASES_FILE, id)

    private fun put(context: Context, file: String, entry: Entry) {
        prefs(context, file).edit {
            putString(entry.id, listOf(System.currentTimeMillis().toString(), entry.title, entry.subtitle.orEmpty(), entry.thumbnail.orEmpty()).joinToString(SEP.toString()))
        }
        reload(context)
    }

    private fun remove(context: Context, file: String, id: String) {
        prefs(context, file).edit { remove(id) }
        reload(context)
    }

    private fun load(context: Context) {
        if (_artists.value == null) reload(context)
    }

    private fun reload(context: Context) {
        _artists.value = read(context, ARTISTS_FILE)
        _releases.value = read(context, RELEASES_FILE)
    }

    private fun read(context: Context, file: String): List<Entry> =
        prefs(context, file)
            .all
            .mapNotNull { (id, value) ->
                val parts = (value as? String)?.split(SEP) ?: return@mapNotNull null
                if (parts.size < 4) return@mapNotNull null
                (parts[0].toLongOrNull() ?: 0L) to Entry(id, parts[1], parts[2].ifEmpty { null }, parts[3].ifEmpty { null })
            }.sortedByDescending { it.first }
            .map { it.second }

    private fun prefs(context: Context, file: String) = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)
}
