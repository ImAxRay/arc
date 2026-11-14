package com.metrolist.music.arc

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

private val BundledAddedKey = stringSetPreferencesKey("arcBundledAdded")

/**
 * Songs shipped inside the APK (audio, cover and word-timed lyrics in assets/bundled), added to the library once. The audio
 * is copied into app storage, so a song keeps playing after a later build leaves its files out.
 */
object BundledTracks {
    private const val PREFIX = "BUNDLED_"
    const val DIAL_SLOT = 4

    private class Track(val key: String, val title: String, val artist: String, val seconds: Int, val lyricsVersion: Int) {
        val lyricsTag get() = "$key@$lyricsVersion"
    }

    private val tracks = listOf(Track("lynxi_inro", "Inro", "Lynxi", 114, lyricsVersion = 5))

    /** The first bundled song's id: a fresh install's Continue tile until something else was played. */
    val firstSongId get() = tracks.firstOrNull()?.let { PREFIX + it.key }

    fun isBundled(mediaId: String?) = mediaId?.startsWith(PREFIX) == true

    private fun audioPath(mediaId: String) = "bundled/${mediaId.removePrefix(PREFIX)}.ogg"

    private fun savedAudio(context: Context, key: String) = File(context.filesDir, "bundled/$key.ogg")

    private fun savedAudioOf(context: Context, mediaId: String) = savedAudio(context, mediaId.removePrefix(PREFIX)).takeIf { it.length() > 0 }

    fun audioUri(context: Context, mediaId: String): Uri =
        savedAudioOf(context, mediaId)?.let(Uri::fromFile) ?: "asset:///${audioPath(mediaId)}".toUri()

    fun openAudio(context: Context, mediaId: String) =
        savedAudioOf(context, mediaId)?.inputStream() ?: context.assets.open(audioPath(mediaId))

    private fun shipped(context: Context) = context.assets.list("bundled").orEmpty().toSet()

    /** Copies a track's audio out of the APK, synced to disk before it takes its name, so a power cut never leaves half a file. */
    private fun saveAudio(context: Context, key: String) {
        val target = savedAudio(context, key)
        val size = runCatching { context.assets.openFd("bundled/$key.ogg").use { it.length } }.getOrNull()
        if (target.length() > 0 && (size == null || target.length() == size)) return
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        context.assets.open("bundled/$key.ogg").use { input ->
            part.outputStream().use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        part.renameTo(target)
    }

    /**
     * Adds each new track to the library and pins it to the speed dial; a track the user removes stays removed.
     * Lyrics are written again whenever a track's lyrics version goes up.
     */
    suspend fun addNew(context: Context, database: MusicDatabase) = withContext(Dispatchers.IO) {
        val added = context.dataStore.data.first()[BundledAddedKey].orEmpty()
        val shipped = shipped(context)
        val tracks = tracks.filter { "${it.key}.ogg" in shipped }
        tracks.filter { it.key in added }.forEach { saveAudio(context, it.key) }
        val newLyrics = tracks.filter { it.key in added && it.lyricsTag !in added }
        newLyrics.forEach { t -> database.query { upsert(LyricsEntity(PREFIX + t.key, lyrics(context, t), t.artist)) } }
        val fresh = tracks.filter { it.key !in added }
        if (fresh.isEmpty() && newLyrics.isEmpty()) return@withContext
        fresh.forEach { t ->
            val id = PREFIX + t.key
            saveAudio(context, t.key)
            val art = context.assets.open("bundled/${t.key}.jpg").use { DeviceMusic.saveArtBytes(context, id, it.readBytes()) }
            val lyrics = lyrics(context, t)
            val artistId = database.artistByName(t.artist)?.id ?: ArtistEntity.generateArtistId().also {
                database.insert(ArtistEntity(id = it, name = t.artist, isLocal = true))
            }
            database.transaction {
                insert(SongEntity(id = id, title = t.title, duration = t.seconds, thumbnailUrl = art, inLibrary = LocalDateTime.now(), isLocal = true))
                insert(SongArtistMap(songId = id, artistId = artistId, position = 0))
                upsert(LyricsEntity(id, lyrics, t.artist))
            }
            database.speedDialDao.insert(
                SpeedDialItem(id = id, title = t.title, subtitle = t.artist, subtitleIds = artistId, thumbnailUrl = art, type = "SONG"),
            )
        }
        context.dataStore.edit { it[BundledAddedKey] = added + (fresh + newLyrics).flatMap { listOf(it.key, it.lyricsTag) } }
    }

    private fun lyrics(context: Context, t: Track) = context.assets.open("bundled/${t.key}.lrc").use { it.reader().readText() }
}
