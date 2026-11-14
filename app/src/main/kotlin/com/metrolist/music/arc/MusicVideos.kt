package com.metrolist.music.arc

import android.content.Context
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val ArcMusicVideosKey = booleanPreferencesKey("arcMusicVideos")
val ArcVideoQualityWifiKey = stringPreferencesKey("arcVideoQualityWifi")
val ArcVideoQualityMobileKey = stringPreferencesKey("arcVideoQualityMobile")
val ArcRadioSongsOnlyKey = booleanPreferencesKey("arcRadioSongsOnly")
val ArcSearchVideosKey = booleanPreferencesKey("arcSearchVideos")
val ArcVideoButtonKey = booleanPreferencesKey("arcVideoButton")

enum class VideoQuality(val height: Int, @StringRes val label: Int) {
    AUTO(0, R.string.audio_quality_auto),
    P360(360, R.string.arc_video_360),
    P480(480, R.string.arc_video_480),
    P720(720, R.string.arc_video_720),
    P1080(1080, R.string.arc_video_1080),
    P1440(1440, R.string.arc_video_1440),
    P2160(2160, R.string.arc_video_2160),
}

class VideoStream(
    val videoId: String,
    val url: String,
    val height: Int,
    val contentLength: Long,
    val userAgent: String,
    val headers: Map<String, String>,
)

object MusicVideos {
    /** Radio and autoplay leave out music videos, whose skits and noise don't belong in a stream of songs. */
    @Volatile var radioSongsOnly = true

    private const val CHUNK = 4L * 1024 * 1024
    private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    private class Known(val formats: InnerTubeXPlayer.VideoFormats, val until: Long)

    private val known = object : LinkedHashMap<String, Known>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Known>?) = size > 12
    }

    /** Own files, Drive, untitled, uploads and podcasts have no YouTube picture. */
    fun canHaveVideo(metadata: MediaMetadata?) =
        metadata != null && !metadata.isEpisode && metadata.uploadEntityId == null && YOUTUBE_ID.matches(metadata.id)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val asking = HashMap<String, Deferred<InnerTubeXPlayer.VideoFormats?>>()
    private val missed = HashMap<String, Long>()

    /** One lookup per video, outside any screen, so leaving the player mid-request doesn't throw it away. */
    private suspend fun formats(videoId: String): InnerTubeXPlayer.VideoFormats? {
        val now = System.currentTimeMillis()
        synchronized(known) {
            known[videoId]?.takeIf { it.until > now }?.let { return it.formats }
            if ((missed[videoId] ?: 0) > now) return null
        }
        val job = synchronized(asking) {
            asking.getOrPut(videoId) {
                scope.async {
                    val found = InnerTubeXPlayer.videoFormats(videoId)
                    synchronized(known) {
                        if (found != null) {
                            known[videoId] = Known(found, System.currentTimeMillis() + found.expiresInSeconds * 900L)
                        } else {
                            missed[videoId] = System.currentTimeMillis() + 60_000
                        }
                    }
                    synchronized(asking) { asking.remove(videoId) }
                    found
                }
            }
        }
        return job.await()
    }

    fun metered(context: Context) =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true


    suspend fun stream(videoId: String, quality: VideoQuality, metered: Boolean): VideoStream? {
        val formats = formats(videoId) ?: return null
        val target = if (quality == VideoQuality.AUTO) (if (metered) 720 else 1080) else quality.height
        val (videoOnly, muxed) = formats.formats.filter { it.mimeType.startsWith("video/") }.partition { "mp4a" !in it.mimeType && "opus" !in it.mimeType }
        val usable = videoOnly.filter { (it.height ?: 0) > 0 && decodes(it.mimeType) }
        val picked =
            usable.filter { it.height!! <= target }
                .sortedWith(compareByDescending<Fmt> { it.height }.thenBy { codecRank(it.mimeType) })
                .firstOrNull()
                ?: usable.minByOrNull { it.height!! }
                ?: muxed.firstOrNull { decodes(it.mimeType) }
                ?: return null
        val url = picked.url ?: return null
        val length = picked.contentLength?.takeIf { it > 0 }
            ?: YouTube.extractionTransport().innerTube.mediaContentLength(url, formats.headers + ("User-Agent" to formats.userAgent))
            ?: return null
        return VideoStream(videoId, url, picked.height ?: 0, length, formats.userAgent, formats.headers)
    }

    @OptIn(UnstableApi::class)
    fun player(context: Context, stream: VideoStream): ExoPlayer {
        val upstream = OkHttpDataSource.Factory(http).setUserAgent(stream.userAgent).setDefaultRequestProperties(stream.headers)
        val chunked = DataSource.Factory { ChunkedDataSource(upstream.createDataSource(), stream.contentLength) }
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(chunked))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 25_000, 800, 1_500).build())
            .build()
            .apply {
                volume = 0f
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                setMediaItem(MediaItem.fromUri(stream.url))
                prepare()
            }
    }

    private fun codecRank(mime: String) = when {
        "avc1" in mime -> 0
        "vp9" in mime || "vp09" in mime -> 1
        else -> 2
    }

    private val decoders = HashMap<String, Boolean>()

    private fun decodes(mime: String): Boolean {
        val type = when {
            "avc1" in mime -> return true
            "vp9" in mime || "vp09" in mime -> "video/x-vnd.on2.vp9"
            "av01" in mime -> "video/av01"
            else -> return false
        }
        return synchronized(decoders) {
            decoders.getOrPut(type) {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                    !info.isEncoder && info.supportedTypes.any { it.equals(type, true) } &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || info.isHardwareAccelerated)
                }
            }
        }
    }
}

private typealias Fmt = com.metrolist.innertubex.models.response.PlayerResponse.StreamingData.Format

/** YouTube refuses open-ended ranges on these links, so every read asks for at most [CHUNK] bytes at a time. */
@OptIn(UnstableApi::class)
private class ChunkedDataSource(
    private val upstream: HttpDataSource,
    private val total: Long,
    private val chunk: Long = 4L * 1024 * 1024,
) : BaseDataSource(true) {
    private var spec: DataSpec? = null
    private var position = 0L
    private var end = 0L
    private var left = 0L
    private var fresh = false
    private var open = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        spec = dataSpec
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) position + dataSpec.length else total
        openChunk()
        open = true
        transferStarted(dataSpec)
        return end - position
    }

    private fun openChunk() {
        val base = spec!!
        left = minOf(chunk, end - position)
        upstream.open(base.subrange(position - base.position, left))
        fresh = true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= end) return C.RESULT_END_OF_INPUT
        if (left <= 0) {
            upstream.close()
            openChunk()
        }
        val read = upstream.read(buffer, offset, minOf(length.toLong(), left).toInt())
        if (read == C.RESULT_END_OF_INPUT) {
            if (fresh) return C.RESULT_END_OF_INPUT
            left = 0
            return read(buffer, offset, length)
        }
        fresh = false
        position += read
        left -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        spec = null
        upstream.close()
        if (open) {
            open = false
            transferEnded()
        }
    }
}
