package com.metrolist.music.arc

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxScope
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

/** What the cover spot shows for the playing song. */
object VideoState {
    val view = mutableStateOf(VideoView.SONG)
    val current = mutableStateOf<SongVideo?>(null)
    /** The player's ground, around a video that isn't as tall as the cover spot. */
    val ground = mutableStateOf(Color.Black)

    /** Where the video in the cover spot ends, in root pixels, so the arc's names can sit below it. */
    val videoBottom = mutableStateOf<Float?>(null)
    val watching = mutableStateOf(false)
    var seekLead = 600L
    var startLag = 60L
    private var shownFor: String? = null
    private var lastPick = VideoView.SONG

    fun pick(view: VideoView) {
        lastPick = view
        this.view.value = view
    }

    fun songChanged(video: SongVideo?, id: String?) {
        current.value = video
        if (id == shownFor) return
        shownFor = id
        view.value = when {
            video == null -> VideoView.SONG
            video.watch -> VideoView.VIDEO
            else -> lastPick
        }
        if (view.value != VideoView.VIDEO) watching.value = false
    }
}

/**
 * The video for the playing song: the song itself when it is a video, or for an album track its music video,
 * played muted over the song's own audio with [time] mapping the song's position into it.
 */
class SongVideo(
    val songId: String,
    val counterpart: Counterpart?,
    val watch: Boolean,
) {
    val videoId: String get() = counterpart?.videoId ?: songId

    fun time(ms: Long) = counterpart?.map(ms) ?: ms
}

private const val WATCH_MIN_SECONDS = 8 * 60

@Composable
fun rememberSongVideo(metadata: MediaMetadata?): SongVideo? {
    val enabled by rememberPreference(ArcMusicVideosKey, true)
    val resolved by produceState<Pair<String?, SongVideo?>?>(null, metadata?.id, enabled) {
        value = null
        val song = metadata?.takeIf { enabled && MusicVideos.canHaveVideo(it) }
        value = metadata?.id to when {
            song == null -> null
            song.isVideoSong -> SongVideo(song.id, counterpart = null, watch = song.duration <= 0 || song.duration >= WATCH_MIN_SECONDS)
            else -> withContext(Dispatchers.IO) { Counterparts.counterpart(song) }?.let { SongVideo(song.id, it, watch = false) }
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
fun VideoToggle(modifier: Modifier = Modifier) {
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
                VideoState.pick(if (toVideo) VideoView.VIDEO else VideoView.SONG)
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

private const val SAMPLE_EVERY_MS = 5_000L
private const val SEEK_OVER_MS = 400L
private const val NUDGE_OVER_MS = 35L

/**
 * A muted video that follows the music player's clock: [time] maps the song's position into the video. It
 * nudges its own speed for small drift and seeks for large drift, so the music itself never changes engine.
 * [crop] fills a square; otherwise the whole picture shows at its own shape, [top] down, on the player's ground. Played only while
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
    top: Dp = 0.dp,
    report: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
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
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { Timber.tag("ArcVideo").w(error, "video failed") }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            })
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
            if (report) VideoState.videoBottom.value = null
        }
    }
    LaunchedEffect(player) { follow(player, playerConnection.player) { mapTime(it) } }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(500), label = "videoAlpha")
    val groundColor by VideoState.ground

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { this.alpha = alpha }
            .then(if (crop || !ground) Modifier else Modifier.background(groundColor)),
    ) {
        val video = @Composable {
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
                    Modifier.fillMaxSize()
                },
            )
        }
        if (crop) {
            video()
        } else {
            Box(
                Modifier
                    .then(if (top > 0.dp) Modifier.align(Alignment.TopCenter).padding(top = top) else Modifier)
                    .aspectRatio(aspect.coerceIn(0.5f, 3f))
                    .then(if (report) Modifier.onGloballyPositioned { VideoState.videoBottom.value = it.boundsInRoot().bottom } else Modifier),
            ) {
                video()
                if (shown) overlay()
            }
        }
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
