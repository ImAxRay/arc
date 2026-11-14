package com.metrolist.music.arc

import android.graphics.Bitmap
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import timber.log.Timber

enum class VideoView { SONG, VIDEO }

/** What the cover spot shows for the playing song, and the colour the video's frames give the ground. */
object VideoState {
    val view = mutableStateOf(VideoView.SONG)
    val frameEdge = mutableStateOf<Int?>(null)
    val watching = mutableStateOf(false)
    var seekLead = 600L
    var startLag = 60L
    var shownFor: String? = null
        private set
    private var lastPick = VideoView.SONG
    private var pending: Pair<String, VideoView>? = null

    fun pick(view: VideoView, forId: String?) {
        lastPick = view
        this.view.value = view
        shownFor = forId
    }

    /** Sets the view the next item [id] opens with, for a swap that changes the playing id. */
    fun expect(id: String, view: VideoView) {
        pending = id to view
        lastPick = view
    }

    fun songChanged(video: SongVideo?, id: String?) {
        if (id == shownFor) return
        shownFor = id
        val waiting = pending?.takeIf { it.first == id }?.second
        pending = null
        view.value = when {
            video == null -> VideoView.SONG
            waiting != null -> waiting
            video.back != null -> VideoView.VIDEO
            video.watch -> VideoView.VIDEO
            lastPick == VideoView.VIDEO && video.native -> VideoView.VIDEO
            else -> VideoView.SONG
        }
        if (view.value != VideoView.VIDEO) watching.value = false
    }
}

/**
 * The video side of the playing song: [native] when the song is itself a video, [counterpart] for an album
 * track with a music video, [back] for a video swapped in for a song.
 */
class SongVideo(
    val songId: String,
    val native: Boolean,
    val counterpart: Counterpart?,
    val back: Counterpart?,
    val watch: Boolean,
)

private const val WATCH_MIN_SECONDS = 8 * 60

@Composable
fun rememberSongVideo(metadata: MediaMetadata?): SongVideo? {
    val enabled by rememberPreference(ArcMusicVideosKey, true)
    val resolved by produceState<Pair<String?, SongVideo?>?>(null, metadata?.id, enabled) {
        value = null
        val song = metadata?.takeIf { enabled && MusicVideos.canHaveVideo(it) }
        if (song == null) {
            value = metadata?.id to null
            return@produceState
        }
        val back = VideoSwap.swappedFrom(song.id)
        value = song.id to when {
            back != null -> SongVideo(song.id, native = true, counterpart = null, back = back, watch = false)
            song.isVideoSong -> SongVideo(song.id, native = true, counterpart = null, back = null, watch = song.duration <= 0 || song.duration >= WATCH_MIN_SECONDS)
            else -> withContext(Dispatchers.IO) { VideoSwap.counterpart(song) }?.let {
                SongVideo(song.id, native = false, counterpart = it, back = null, watch = false)
            }
        }
    }
    val current = resolved?.takeIf { it.first == metadata?.id }
    LaunchedEffect(current) {
        if (current != null) VideoState.songChanged(current.second, current.first)
    }
    return current?.second
}

/** Turns the cover spot between the cover and the video; shows what a tap switches to. */
@Composable
fun VideoToggle(video: SongVideo, modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptics = LocalHapticFeedback.current
    val view by VideoState.view
    val press = remember { MutableInteractionSource() }
    val toVideo = view == VideoView.SONG
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressBounce(press)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(press, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                choose(playerConnection.player, video, if (toVideo) VideoView.VIDEO else VideoView.SONG)
            },
    ) {
        Icon(
            painterResource(if (toVideo) R.drawable.arc_video else R.drawable.music_note),
            contentDescription = stringResource(if (toVideo) R.string.arc_show_video else R.string.arc_show_cover),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun choose(player: Player, video: SongVideo, option: VideoView) {
    val back = video.back
    val counterpart = video.counterpart
    when {
        // a video swapped in for a song gives the song back
        back != null && option == VideoView.SONG -> {
            VideoState.expect(back.videoId, option)
            VideoSwap.swap(player, back)
        }
        option == VideoView.VIDEO && !video.native && counterpart != null -> {
            VideoState.expect(counterpart.videoId, option)
            VideoSwap.swap(player, counterpart)
        }
        else -> VideoState.pick(option, video.songId)
    }
}

private const val SAMPLE_EVERY_MS = 5_000L
private const val SEEK_OVER_MS = 400L
private const val NUDGE_OVER_MS = 35L

/**
 * A muted video that follows the music player's clock: [time] maps the song's position into the video. It
 * nudges its own speed for small drift and seeks for large drift, so the music itself never changes engine.
 * [crop] fills a square; otherwise the whole picture shows at its own shape on [ground]. Played only while
 * [active] and the app is on screen; the cover underneath shows until the first frame.
 */
@OptIn(UnstableApi::class)
@Composable
fun MusicVideoLayer(
    videoId: String,
    time: (Long) -> Long,
    crop: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    ground: Boolean = true,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val wifiQuality by rememberEnumPreference(ArcVideoQualityWifiKey, VideoQuality.AUTO)
    val mobileQuality by rememberEnumPreference(ArcVideoQualityMobileKey, VideoQuality.AUTO)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = active && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val metered = remember(running) { MusicVideos.metered(context) }
    val quality = if (metered) mobileQuality else wifiQuality

    val stream by produceState<VideoStream?>(null, videoId, quality, metered) {
        value = withContext(Dispatchers.IO) {
            runCatching { MusicVideos.stream(videoId, quality, metered) }.onFailure { Timber.tag("ArcVideo").w(it, "stream failed") }.getOrNull()
        }
    }
    val source = stream?.takeIf { it.videoId == videoId && running } ?: return
    val mapTime by rememberUpdatedState(time)

    var shown by remember(source) { mutableStateOf(false) }
    var aspect by remember(source) { mutableFloatStateOf(16f / 9f) }
    var view by remember(source) { mutableStateOf<TextureView?>(null) }
    val player = remember(source) {
        MusicVideos.player(context.applicationContext, source).apply {
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { shown = true }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            })
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
            VideoState.frameEdge.value = null
        }
    }
    LaunchedEffect(player) { follow(player, playerConnection.player) { mapTime(it) } }
    LaunchedEffect(player, shown) {
        if (!shown) return@LaunchedEffect
        val frame = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
        while (true) {
            view?.takeIf { it.isAvailable }?.let { v ->
                v.getBitmap(frame)
                VideoState.frameEdge.value = withContext(Dispatchers.Default) { edgeColor(frame, 0, 28, 64, 36) }
            }
            delay(SAMPLE_EVERY_MS)
        }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(500), label = "videoAlpha")
    val groundColor by animateColorAsState(
        VideoState.frameEdge.value?.let { coverGround(it).near } ?: Color.Black,
        tween(800),
        label = "videoGround",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { this.alpha = alpha }
            .then(if (crop || !ground) Modifier else Modifier.background(groundColor)),
    ) {
        AndroidView(
            factory = { TextureView(it).apply { isOpaque = false }.also { v -> view = v } },
            update = { player.setVideoTextureView(it) },
            onRelease = { player.clearVideoTextureView(it) },
            modifier = if (crop) {
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = aspect.coerceAtLeast(1f)
                    scaleY = (1f / aspect).coerceAtLeast(1f)
                }
            } else {
                Modifier.aspectRatio(aspect.coerceIn(0.5f, 3f))
            },
        )
    }
}

/** Keeps [video] on [music]'s clock until cancelled. */
private suspend fun follow(video: ExoPlayer, music: Player, time: (Long) -> Long) {
    while (true) {
        val target = time(music.currentPosition)
        val speed = music.playbackParameters.speed
        if (!music.isPlaying) {
            video.playWhenReady = false
            if (abs(video.currentPosition - target) > NUDGE_OVER_MS && video.playbackState != Player.STATE_BUFFERING) video.seekTo(target)
            delay(250)
            continue
        }
        if (video.playbackState == Player.STATE_BUFFERING || video.playbackState == Player.STATE_IDLE) {
            video.playWhenReady = true
            delay(150)
            continue
        }
        val drift = target - video.currentPosition
        when {
            video.playbackState == Player.STATE_ENDED && drift < 0 -> delay(500)
            abs(drift) > SEEK_OVER_MS -> {
                // seek ahead of the music, hold the first frame, and start the moment the music gets there
                val at = target + VideoState.seekLead
                video.setPlaybackSpeed(speed)
                video.playWhenReady = false
                video.seekTo(at)
                val asked = System.currentTimeMillis()
                while (video.playbackState != Player.STATE_READY && System.currentTimeMillis() - asked < 6_000) delay(20)
                val took = System.currentTimeMillis() - asked
                while (music.isPlaying && time(music.currentPosition) < at - VideoState.startLag) delay(5)
                video.playWhenReady = true
                VideoState.seekLead = ((VideoState.seekLead + took + 250) / 2).coerceIn(300, 4_000)
                delay(1_000)
                // a held video takes a moment to get going; start that much earlier next time
                val late = time(music.currentPosition) - video.currentPosition
                if (music.isPlaying && abs(late) < SEEK_OVER_MS) VideoState.startLag = (VideoState.startLag + late / 2).coerceIn(0, 400)
            }
            abs(drift) > NUDGE_OVER_MS -> {
                video.setPlaybackSpeed(speed * (1f + (drift / 2_000f).coerceIn(-0.1f, 0.1f)))
                video.playWhenReady = true
                delay(400)
            }
            else -> {
                video.setPlaybackSpeed(speed)
                video.playWhenReady = true
                delay(1_000)
            }
        }
    }
}
