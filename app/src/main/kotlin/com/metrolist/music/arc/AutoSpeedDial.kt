package com.metrolist.music.arc

import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import kotlinx.coroutines.flow.first

/** Speed dial in Android Auto: a grid where every tile plays at once, since a driver shouldn't have to browse. */
object AutoSpeedDial {
    const val ID = "speed_dial"
    // One screen of a 3 by 4 grid; Auto lays the tiles out itself, so order is all Arc controls.
    private const val TILES = 12

    class Tile(val type: String, val id: String, val title: String, val subtitle: String?, val art: String?) {
        val mediaId get() = "$ID/$type/$id"
    }

    /** Pinned items first, where the user put them, then songs Arc expects you to want, refreshed each visit. */
    suspend fun tiles(database: MusicDatabase): List<Tile> {
        val pinned = database.speedDialDao.getAll().first().mapNotNull { item ->
            when (item.type) {
                "SONG" -> database.song(item.id).first()?.let { Tile("song", it.id, it.song.title, item.subtitle, it.song.thumbnailUrl) }
                // only what can play from the library, so no tile is a dead end
                "ALBUM", "ARTIST", "PLAYLIST", "LOCAL_PLAYLIST" ->
                    Tile(item.type.lowercase(), item.id, item.title, item.subtitle, item.thumbnailUrl)
                        .takeIf { queue(database, listOf(ID, it.type, it.id)) != null }
                else -> null
            }
        }
        val picks = database.quickPicks().first().map { Tile("song", it.id, it.song.title, it.artists.joinToString { a -> a.name }, it.song.thumbnailUrl) }
        return (pinned + picks).distinctBy { it.mediaId }.take(TILES)
    }

    /** The songs a tapped tile plays and where to start; songs play on through the grid's other songs. */
    suspend fun queue(database: MusicDatabase, path: List<String>): Pair<List<Song>, Int>? {
        val type = path.getOrNull(1) ?: return null
        val id = path.drop(2).joinToString("/").ifEmpty { return null }
        val songs = when (type) {
            "song" -> {
                val songs = tiles(database).filter { it.type == "song" }.mapNotNull { database.song(it.id).first() }
                return songs.takeIf { it.isNotEmpty() }?.let { it to it.indexOfFirst { song -> song.id == id }.coerceAtLeast(0) }
            }
            "album" -> database.albumWithSongs(id).first()?.songs
            "artist" -> database.artistSongsByCreateDateAsc(id).first().shuffled()
            "local_playlist" -> database.playlistSongs(id).first().map { it.song }
            "playlist" -> database.playlistByBrowseId(id).first()?.let { playlist -> database.playlistSongs(playlist.id).first().map { it.song } }
            else -> null
        }
        return songs?.takeIf { it.isNotEmpty() }?.let { it to 0 }
    }
}
