package com.metrolist.music.arc

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.Normalizer
import kotlin.math.abs

/** Finds an imported song (Spotify, CSV, M3U) on YouTube Music: the result whose title and artist agree, not just the first hit. */
object ImportMatch {
    private val featureCredit =
        Regex("""\s*[(\[](?:feat\.?|ft\.?|featuring|with)\s[^)\]]*[)\]]|\s+(?:feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
    private val creditSeparator =
        Regex("""\s*(?:,|&|\+|/|;|\b(?:featuring|feat|ft|with|vs|and|x)\b\.?)\s*""", RegexOption.IGNORE_CASE)

    private fun plain(text: String) =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).filter(Char::isLetterOrDigit)

    private fun exactTitle(text: String) = plain(text.replace(featureCredit, ""))

    // "Song - Remastered 2011" vs "Song": a second chance, after exact titles, since it also equates "Song (Remix)".
    private fun looseTitle(text: String) =
        exactTitle(text.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").substringBefore(" - "))

    private fun credits(names: List<String>) =
        names.flatMap { it.split(creditSeparator) }.map(::plain).filter { it.isNotEmpty() }.toSet()

    /** [album] and [seconds] only rank songs that already agree on title and artist. */
    fun best(title: String, artists: List<String>, seconds: Int, candidates: List<SongItem>, album: String = ""): SongItem? {
        val wanted = credits(artists)
        val wantedAlbum = looseTitle(album)
        fun artistOk(song: SongItem) = wanted.isEmpty() || credits(song.artists.map { it.name }).any { it in wanted }
        fun gap(song: SongItem) = song.duration?.takeIf { seconds > 0 }?.let { abs(seconds - it) } ?: Int.MAX_VALUE
        val rank = compareByDescending<SongItem> { wantedAlbum.isNotEmpty() && looseTitle(it.album?.name.orEmpty()) == wantedAlbum }.thenBy(::gap)
        fun sameBy(key: (String) -> String) =
            key(title).takeIf { it.isNotEmpty() }?.let { want -> candidates.filter { key(it.title) == want && artistOk(it) }.minWithOrNull(rank) }
        return sameBy(::exactTitle) ?: sameBy(::looseTitle) ?: candidates.firstOrNull(::artistOk) ?: candidates.firstOrNull()
    }

    /** Null when YouTube has nothing; search errors are retried, since a lost song is never noticed in a long playlist. */
    suspend fun find(title: String, artists: List<String>, seconds: Int, album: String = ""): SongItem? {
        val query = "$title - ${artists.joinToString(" ")}"
        repeat(3) { attempt ->
            try {
                val items = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow().items
                return best(title, artists, seconds, items.filterIsInstance<SongItem>().distinctBy { it.id }, album)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                delay(500L * (attempt + 1))
            }
        }
        return null
    }
}
