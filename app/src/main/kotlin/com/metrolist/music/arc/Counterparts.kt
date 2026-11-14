package com.metrolist.music.arc

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistPanelVideoRenderer
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.metrolist.innertube.pages.NextPage
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import timber.log.Timber

/** One stretch where the song and its video play the same music: [from] in the song is [to] in the video. */
class Segment(val from: Long, val to: Long, val length: Long)

/** The other version of a song: its music video, or for a swapped-in video, the song it replaced. */
class Counterpart(val item: MediaItem, val segments: List<Segment>) {
    val videoId: String get() = item.mediaId

    fun map(ms: Long): Long = mapThrough(segments, ms)
}

private fun mapThrough(segments: List<Segment>, ms: Long): Long {
    if (segments.isEmpty()) return ms
    val inside = segments.firstOrNull { ms >= it.from && ms < it.from + it.length }
        ?: segments.minBy { minOf(kotlin.math.abs(ms - it.from), kotlin.math.abs(ms - it.from - it.length)) }
    return (ms - inside.from + inside.to).coerceAtLeast(0)
}

object VideoSwap {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val found = HashMap<String, Counterpart?>()

    /** Videos swapped in for songs, with the way back. */
    private val swappedIn = HashMap<String, Counterpart>()

    fun swappedFrom(videoId: String?): Counterpart? = synchronized(swappedIn) { videoId?.let { swappedIn[it] } }

    /**
     * The music video of an album track. YouTube Music lists it as the track's counterpart, with a segment map,
     * for signed-in requests; otherwise the first official video with the same title and artist, unaligned.
     */
    suspend fun counterpart(song: MediaMetadata): Counterpart? {
        synchronized(found) { if (song.id in found) return found[song.id] }
        val next = attempt { fromNext(song.id) }
        val result = next ?: attempt { fromSearch(song) }
        synchronized(found) { found[song.id] = result }
        return result
    }

    private suspend fun <T> attempt(block: suspend () -> T?): T? =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag("ArcVideo").w(error, "counterpart lookup failed")
            null
        }

    private suspend fun fromNext(id: String): Counterpart? {
        val text = YouTube.extractionTransport().innerTube
            .next(YouTubeClient.WEB_REMIX, id, null, null, null, null, null)
            .bodyAsText()
        val panel = JSONObject(text).optJSONObject("contents")
            ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            ?.optJSONObject("tabbedRenderer")?.optJSONObject("watchNextTabbedResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")
            ?.optJSONObject("musicQueueRenderer")?.optJSONObject("content")?.optJSONObject("playlistPanelRenderer")
            ?.optJSONArray("contents") ?: return null
        for (i in 0 until panel.length()) {
            val wrapper = panel.optJSONObject(i)?.optJSONObject("playlistPanelVideoWrapperRenderer") ?: continue
            val primary = wrapper.optJSONObject("primaryRenderer")?.optJSONObject("playlistPanelVideoRenderer")
            if (primary?.optString("videoId") != id) continue
            val other = wrapper.optJSONArray("counterpart")?.optJSONObject(0) ?: return null
            val renderer = other.optJSONObject("counterpartRenderer")?.optJSONObject("playlistPanelVideoRenderer") ?: return null
            val song = NextPage.fromPlaylistPanelVideoRenderer(
                json.decodeFromString(PlaylistPanelVideoRenderer.serializer(), renderer.toString()),
            ) ?: return null
            val segments = other.optJSONObject("segmentMap")?.optJSONArray("segment")?.let { list ->
                (0 until list.length()).mapNotNull { j ->
                    val s = list.optJSONObject(j) ?: return@mapNotNull null
                    Segment(
                        s.optString("primaryVideoStartTimeMilliseconds").toLongOrNull() ?: return@mapNotNull null,
                        s.optString("counterpartVideoStartTimeMilliseconds").toLongOrNull() ?: return@mapNotNull null,
                        s.optString("durationMilliseconds").toLongOrNull() ?: return@mapNotNull null,
                    )
                }
            }.orEmpty()
            return Counterpart(song.toMediaItem(), segments)
        }
        return null
    }

    private suspend fun fromSearch(song: MediaMetadata): Counterpart? {
        val artist = song.artists.firstOrNull()?.name ?: return null
        val title = fold(cut(song.title))
        if (title.isEmpty()) return null
        val items = YouTube.search("$artist ${song.title}", YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items ?: return null
        val match = items.filterIsInstance<SongItem>().firstOrNull { item ->
            item.musicVideoType == MUSIC_VIDEO_TYPE_OMV &&
                fold(cut(item.title)).let { it == title || it.startsWith("$title ") } &&
                item.artists.any { fold(it.name) == fold(artist) }
        } ?: return null
        return Counterpart(match.toMediaItem(), emptyList())
    }

    /** Plays [to] in place of the current item, at the matching moment. */
    fun swap(player: Player, to: Counterpart) {
        val index = player.currentMediaItemIndex
        val from = player.currentMediaItem ?: return
        val at = to.map(player.currentPosition)
        val back = to.segments.map { Segment(it.to, it.from, it.length) }
        synchronized(swappedIn) {
            swappedIn.remove(from.mediaId)
            if (from.metadata?.isVideoSong != true) swappedIn[to.videoId] = Counterpart(from, back)
        }
        // in next, straight to the matching moment, then out with the old one, so the position never passes 0;
        // the removal waits a moment so it isn't folded into the same batch of player events as the seek
        player.addMediaItem(index + 1, to.item)
        player.seekTo(index + 1, at)
        android.os.Handler(player.applicationLooper).postDelayed({
            if (index < player.mediaItemCount && player.getMediaItemAt(index).mediaId == from.mediaId &&
                player.currentMediaItem?.mediaId == to.videoId
            ) {
                player.removeMediaItem(index)
            }
        }, 400)
    }

    private fun cut(name: String) =
        name.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").substringBefore(" - ").trim()

    private fun fold(text: String) =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
