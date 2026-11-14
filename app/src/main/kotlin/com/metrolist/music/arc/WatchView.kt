package com.metrolist.music.arc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.utils.makeTimeString
import kotlinx.coroutines.delay

private const val CONTROLS_FOR_MS = 3_000L
private const val SEEK_STEP_MS = 10_000L
private val CLOSE_PULL = 120.dp

/** Opens the watch view: the playing video full screen, sideways. */
@Composable
fun WatchButton(modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressBounce(press)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(press, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                VideoState.watching.value = true
            },
    ) {
        Icon(painterResource(R.drawable.fullscreen), stringResource(R.string.arc_watch), tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * The watch view, kept by the player itself so it outlives the player's own switch to its landscape layout.
 * The screen turns sideways only while it is open; the system rotation setting is never touched.
 */
@Composable
fun WatchHost() {
    val playerConnection = LocalPlayerConnection.current ?: return
    val watching by VideoState.watching
    val view by VideoState.view
    val metadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val current by VideoState.current
    val video = current?.takeIf { watching && view == VideoView.VIDEO && it.songId == metadata?.id }
    val videoId = video?.videoId

    val activity = LocalContext.current.findActivity()
    DisposableEffect(watching, activity) {
        if (!watching || activity == null) return@DisposableEffect onDispose {}
        val before = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity.requestedOrientation =
                if (before == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else before
        }
    }
    LaunchedEffect(watching, videoId) {
        if (watching && videoId == null) VideoState.watching.value = false
    }
    if (videoId == null) return

    Dialog(
        onDismissRequest = { VideoState.watching.value = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val host = LocalView.current
        DisposableEffect(host) {
            val window = (host.parent as? DialogWindowProvider)?.window
            host.keepScreenOn = true
            window?.let {
                it.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                it.setDimAmount(0f)
                it.attributes = it.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose { host.keepScreenOn = false }
        }
        var shown by remember { mutableStateOf(true) }
        var touched by remember { mutableIntStateOf(0) }
        LaunchedEffect(shown, touched) {
            if (shown) {
                delay(CONTROLS_FOR_MS)
                shown = false
            }
        }
        val player = playerConnection.player
        val haptics = LocalHapticFeedback.current
        val density = LocalDensity.current
        var jump by remember { mutableStateOf<Pair<Int, Long>?>(null) }
        var fast by remember { mutableStateOf(false) }
        var pull by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(jump) {
            if (jump != null) {
                delay(700)
                jump = null
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { shown = !shown },
                        onDoubleTap = { at ->
                            val forward = at.x > size.width / 2
                            val step = if (forward) SEEK_STEP_MS else -SEEK_STEP_MS
                            val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                            player.seekTo((player.currentPosition + step).coerceIn(0, duration))
                            val total = jump?.takeIf { (it.first > 0) == forward }?.second?.plus(step) ?: step
                            jump = (if (forward) 1 else -1) to total
                        },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            fast = true
                        },
                        onPress = {
                            tryAwaitRelease()
                            fast = false
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (pull > with(density) { CLOSE_PULL.toPx() }) VideoState.watching.value = false
                            pull = 0f
                        },
                        onDragCancel = { pull = 0f },
                    ) { _, dy -> pull = (pull + dy).coerceAtLeast(0f) }
                }
                .graphicsLayer {
                    translationY = pull / 2
                    alpha = 1f - (pull / with(density) { (CLOSE_PULL * 4).toPx() }).coerceIn(0f, 0.5f)
                },
        ) {
            DisposableEffect(fast) {
                if (!fast) return@DisposableEffect onDispose {}
                val before = player.playbackParameters
                player.playbackParameters = before.withSpeed(before.speed * 2)
                onDispose { player.playbackParameters = before }
            }
            MusicVideoLayer(videoId, video::time, crop = false, active = true, ground = false)
            jump?.let { (side, total) ->
                Text(
                    (if (total > 0) "+" else "−") + "${kotlin.math.abs(total) / 1000}s",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(if (side > 0) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 72.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (fast) {
                Text(
                    "2×",
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                WatchControls(
                    title = metadata?.title.orEmpty(),
                    artist = metadata?.artists?.joinToString { it.name }.orEmpty(),
                    onTouch = { touched++ },
                )
            }
        }
    }
}

@Composable
private fun WatchControls(title: String, artist: String, onTouch: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val playing by playerConnection.isPlaying.collectAsStateWithLifecycle()
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    var dragging by remember { mutableStateOf(false) }
    var dragAt by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration
            delay(250)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()) {
            WatchIcon(R.drawable.close, stringResource(R.string.arc_watch_close)) { VideoState.watching.value = false }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
        ) {
            WatchIcon(R.drawable.skip_previous, null) {
                onTouch()
                player.seekToPrevious()
            }
            WatchIcon(if (playing) R.drawable.pause else R.drawable.play, null, size = 64.dp) {
                onTouch()
                playerConnection.togglePlayPause()
            }
            WatchIcon(R.drawable.skip_next, null) {
                onTouch()
                player.seekToNext()
            }
        }
        val known = duration != C.TIME_UNSET && duration > 0
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            val shownAt = if (dragging) (dragAt * duration).toLong() else position
            Text(makeTimeString(shownAt), color = Color.White, fontSize = 12.sp)
            ArcSlider(
                value = if (!known) 0f else if (dragging) dragAt else (position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = {
                    onTouch()
                    dragging = true
                    dragAt = it
                },
                onValueChangeFinished = {
                    if (known) player.seekTo((dragAt * duration).toLong())
                    dragging = false
                },
                enabled = known,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text(if (known) makeTimeString(duration) else "", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WatchIcon(icon: Int, description: String?, size: Dp = 40.dp, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce(press)
            .size(size + 8.dp)
            .clip(CircleShape)
            .clickable(press, indication = null, onClick = onClick),
    ) {
        Icon(painterResource(icon), description, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
