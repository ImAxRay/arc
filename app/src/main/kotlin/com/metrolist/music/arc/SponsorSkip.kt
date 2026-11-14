package com.metrolist.music.arc

import android.os.Looper
import android.util.LruCache
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

val ArcSkipOfftopicKey = booleanPreferencesKey("arcSkipOfftopic")

/**
 * Skips the parts of a song that SponsorBlock's community marked as not music (talk, skits,
 * long silent intros in music videos). Only the first 4 characters of the video id's SHA-256 leave
 * the phone, so SponsorBlock never learns which song plays.
 */
object SponsorSkip {
    private const val API = "https://sponsor.ajay.app/api/skipSegments/"
    private const val CATEGORY = "music_offtopic"
    private const val MIN_SEGMENT_MS = 1_000L
    private const val END_SLACK_MS = 1_500L

    private val http by lazy { OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build() }
    private val known = LruCache<String, List<LongRange>>(128)
    private val messages = mutableListOf<PlayerMessage>()

    private suspend fun segments(videoId: String): List<LongRange> {
        known.get(videoId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val prefix = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray()).joinToString("") { "%02x".format(it) }.take(4)
            val request = Request.Builder().url("$API$prefix?categories=[\"$CATEGORY\"]").build()
            val found =
                runCatching {
                    http.newCall(request).execute().use { response ->
                        // 404 means no song with this prefix has segments
                        if (!response.isSuccessful) return@use emptyList()
                        val videos = JSONArray(response.body.string())
                        (0 until videos.length()).map(videos::getJSONObject).firstOrNull { it.optString("videoID") == videoId }?.let { video ->
                            val list = video.getJSONArray("segments")
                            (0 until list.length()).map(list::getJSONObject).filter { it.optString("actionType", "skip") == "skip" }.mapNotNull { seg ->
                                val span = seg.getJSONArray("segment")
                                val start = (span.getDouble(0) * 1000).toLong()
                                val end = (span.getDouble(1) * 1000).toLong()
                                if (end - start >= MIN_SEGMENT_MS) start..end else null
                            }.sortedBy { it.first }
                        }.orEmpty()
                    }
                }.getOrNull()
            found?.also { known.put(videoId, it) }.orEmpty()
        }
    }

    fun cancel() {
        messages.forEach(PlayerMessage::cancel)
        messages.clear()
    }

    /** Arms skips for the song now playing on [player]. Call on every song change, on the main thread. */
    suspend fun arm(player: ExoPlayer, isCurrent: () -> Boolean) {
        cancel()
        val item = player.currentMediaItem ?: return
        val id = item.mediaId
        if (isOwnFile(id)) return
        val found = segments(id)
        if (found.isEmpty() || !isCurrent() || player.currentMediaItem?.mediaId != id) return
        val index = player.currentMediaItemIndex
        found.forEach { span ->
            messages +=
                player.createMessage { _, _ -> skip(player, id, span, isCurrent) }.apply {
                    setLooper(Looper.getMainLooper())
                    setPosition(index, span.first)
                    setDeleteAfterDelivery(false)
                    send()
                }
        }
        val now = player.currentPosition
        found.firstOrNull { now >= it.first && now < it.last - END_SLACK_MS }?.let { skip(player, id, it, isCurrent) }
    }

    private fun skip(player: ExoPlayer, id: String, span: LongRange, isCurrent: () -> Boolean) {
        if (!isCurrent() || player.currentMediaItem?.mediaId != id) return
        val duration = player.duration
        if (duration != C.TIME_UNSET && span.last >= duration - END_SLACK_MS) {
            if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(duration)
        } else {
            player.seekTo(span.last)
        }
    }
}
