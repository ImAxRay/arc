package com.metrolist.music.arc

import androidx.annotation.StringRes
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.R
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

enum class ArtistSongSort(@StringRes val label: Int) {
    POPULAR(R.string.sort_popular),
    PLAYS(R.string.sort_by_plays),
    NEWEST(R.string.sort_newest),
    NAME(R.string.sort_by_name),
}

/** Turns "2.3M plays" or "2,3 млн прослушиваний" into a number, or null if there isn't one. */
internal fun parsePlayCount(text: String?): Long? {
    val match = Regex("""^(\d+(?:[.,]\d+)?)\s*(\p{L}*)""").find(text?.trim().orEmpty()) ?: return null
    val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val multiplier =
        when (match.groupValues[2].lowercase()) {
            "k", "тыс" -> 1_000.0
            "m", "млн" -> 1_000_000.0
            "b", "млрд" -> 1_000_000_000.0
            else -> 1.0
        }
    return (number * multiplier).toLong()
}

internal fun sortSongs(items: List<YTItem>, sort: ArtistSongSort, albumYears: Map<String, Int>): List<YTItem> =
    when (sort) {
        ArtistSongSort.POPULAR -> items
        ArtistSongSort.PLAYS -> items.sortedByDescending { parsePlayCount((it as? SongItem)?.playsText) ?: -1L }
        ArtistSongSort.NEWEST -> items.sortedByDescending { albumYears[(it as? SongItem)?.album?.id] ?: -1 }
        ArtistSongSort.NAME -> items.sortedBy { it.title.lowercase() }
    }

/**
 * Songs don't come with dates, but their albums do. This collects the release year of everything
 * on the artist's page, including the full album and single lists behind it.
 */
internal suspend fun artistAlbumYears(artistId: String): Map<String, Int> = coroutineScope {
    val page = YouTube.artist(artistId).getOrNull() ?: return@coroutineScope emptyMap()
    val shown = page.sections.flatMap { it.items }.filterIsInstance<AlbumItem>()
    val more =
        page.sections
            .filter { it.items.firstOrNull() is AlbumItem }
            .mapNotNull { it.moreEndpoint }
            .map { endpoint -> async { YouTube.artistItems(endpoint).getOrNull()?.items.orEmpty().filterIsInstance<AlbumItem>() } }
            .awaitAll()
            .flatten()
    (shown + more).mapNotNull { album -> album.year?.let { album.id to it } }.toMap()
}
