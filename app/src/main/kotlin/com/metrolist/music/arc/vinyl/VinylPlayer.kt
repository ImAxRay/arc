package com.metrolist.music.arc.vinyl

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate as rotateModifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.common.Player
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.constants.CrossfadeDurationKey
import com.metrolist.music.constants.CrossfadeEnabledKey
import com.metrolist.music.extensions.metadata
import com.metrolist.music.arc.ArcMotion
import com.metrolist.music.arc.pressBounce
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt


val ArcVinylPlayerKey = booleanPreferencesKey("arcVinylPlayer")

private const val PLAY_DEGREES_PER_SECOND = 200f // 33 1/3 RPM
private const val SCRUB_MS_PER_TURN = 1_800f // A real record: one turn at 33 1/3 RPM
private const val LIKED_HEART_ID = "liked"
private const val TURNTABLE_RADIUS = 0.47f
private const val HOLD_LABEL_MS = 420L
private const val MAX_SURFACE_PX = 1024
private const val MAX_GROOVES_PX = 1440
private const val MULTI_TAP_MS = 260L
private const val LYRICS_SCALE = 0.75f
private const val LYRICS_DROP = 0.2f
private const val ARM_LIFT_DEGREES = 22f
private const val THROW_POINT = 0.2f
private const val NEEDLE_TICKS = 50
private const val GROOVE_TICK_DEGREES = 15f
// After letting go, scratch audio keeps playing while the real player gets ready this far ahead.
private const val HANDOFF_LEAD_MS = 500L
private const val HANDOFF_START_LATENCY_MS = 60L
private const val HANDOFF_AUDIBLE_MS = 30L
private const val HANDOFF_TIMEOUT_MS = 1_500L

private class RecordLook(val surface: ImageBitmap?, val grooves: ImageBitmap?, val art: ImageBitmap?)

fun AnimatedContentTransitionScope<Boolean>.vinylSwitchTransition(): ContentTransform =
    if (targetState) {
        fadeIn(tween(120)) togetherWith (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.08f))
    } else {
        (fadeIn(tween(220, delayMillis = 100)) + scaleIn(spring(0.6f, 300f), initialScale = 0.88f)) togetherWith
            fadeOut(tween(120, delayMillis = 160))
    } using SizeTransform(clip = false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimatedVisibilityScope.VinylPlayer(
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    canControl: Boolean,
    liked: Boolean,
    onToggleLike: () -> Unit,
    turntable: Boolean,
    leftHanded: Float,
    onHoldLabel: () -> Unit,
    bottomBar: @Composable () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: () -> Unit,
    scratchPlayer: Player?,
    showLyrics: Boolean,
    bottomInset: Dp,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    contentColor: Color,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    lyrics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val insetPx = with(density) { bottomInset.toPx() }
        val fullRadius = min(widthPx * 0.68f, (heightPx - insetPx) * 0.46f)
        val lyricsAmount by animateFloatAsState(if (showLyrics) 1f else 0f, label = "vinylLyricsAmount")
        val turntableAmount by animateFloatAsState(if (turntable) 1f else 0f, ArcMotion.landing(), label = "vinylTurntable")
        val chromeAlpha = (1f - turntableAmount * 2f).coerceIn(0f, 1f)
        val radius = lerp(fullRadius * (1f - (1f - LYRICS_SCALE) * lyricsAmount), widthPx * TURNTABLE_RADIUS, turntableAmount)
        val center =
            lerp(
                // The left-handed layout moves the record to the left edge, so the thumb reaches it from that side.
                Offset(widthPx * (0.76f - 0.52f * leftHanded), heightPx - insetPx - radius * (0.4f - LYRICS_DROP * lyricsAmount)),
                Offset(widthPx / 2f, heightPx / 2f),
                turntableAmount,
            )
        // Stays fixed so shrinking for lyrics scales the record instead of rebuilding its art every frame.
        val diameterPx = (fullRadius * 2f).roundToInt().coerceAtLeast(1)

        val context = LocalContext.current
        val haptics = LocalHapticFeedback.current
        val touchSlop = LocalViewConfiguration.current.touchSlop
        val armTouchPx = with(density) { 24.dp.toPx() }
        val bottomEdgePx = insetPx
        val gestureInsets = WindowInsets.systemGestures
        val backZoneLeftPx = gestureInsets.getLeft(density, LayoutDirection.Ltr).toFloat()
        val backZoneRightPx = gestureInsets.getRight(density, LayoutDirection.Ltr).toFloat()
        val flickPx = with(density) { 700.dp.toPx() }

        val seed = remember(mediaMetadata?.album?.id, mediaMetadata?.id) {
            (mediaMetadata?.album?.id ?: mediaMetadata?.id).hashCode()
        }
        val surfaceReadyFor = remember { mutableStateOf<Int?>(null) }
        val currentSeed by rememberUpdatedState(seed)
        val surface by produceState<ImageBitmap?>(null, seed, diameterPx) {
            value =
                withContext(Dispatchers.Default) {
                    buildRecordSurface(seed, min(diameterPx, MAX_SURFACE_PX)).asImageBitmap()
                }
            surfaceReadyFor.value = seed
        }
        val albumSide =
            remember(mediaMetadata?.id, scratchPlayer) {
                scratchPlayer?.takeUnless { it.shuffleModeEnabled }?.let { player ->
                    val songs = List(player.mediaItemCount) { player.getMediaItemAt(it).metadata }
                    albumSideFor(
                        songs.map { it?.album?.id },
                        songs.map { (it?.duration ?: 0) * 1000L },
                        player.currentMediaItemIndex,
                    )
                }
            }
        val currentAlbumSide by rememberUpdatedState(albumSide)
        val bandEdges = albumSide?.bandEdges
        val grooves by produceState<ImageBitmap?>(null, diameterPx, bandEdges) {
            value =
                withContext(Dispatchers.Default) {
                    val size = min(diameterPx, MAX_GROOVES_PX)
                    if (bandEdges == null) {
                        buildGrooves(size).asImageBitmap()
                    } else {
                        buildGrooves(size, bandEdges.map(::needleReach)).asImageBitmap()
                    }
                }
        }
        val thumbnailUrl = mediaMetadata?.thumbnailUrl
        val artReadyFor = remember { mutableStateOf<String?>(null) }
        val currentThumbnail by rememberUpdatedState(thumbnailUrl)
        // The old cover stays until the new one is ready, so a record never flashes an empty label.
        val art by produceState<ImageBitmap?>(null, thumbnailUrl) {
            val url = thumbnailUrl
            if (url == null) {
                value = null
                return@produceState
            }
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val request = ImageRequest.Builder(context).data(url).size(512).allowHardware(false).build()
                        context.imageLoader.execute(request).image?.toBitmap()?.asImageBitmap()
                    }.getOrNull()
                }
            artReadyFor.value = url
        }

        val sound = remember { VinylScrubSound() }

        val angle = remember { mutableFloatStateOf(0f) }
        val fling = remember { mutableFloatStateOf(0f) }
        val scrubMs = remember { mutableStateOf<Long?>(null) }
        val resumeAfterScrub = remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        val scratch = remember { mutableStateOf<ScratchSession?>(null) }
        val needleSong = remember { mutableStateOf<Int?>(null) }
        val crossfadeEnabled by rememberPreference(CrossfadeEnabledKey, false)
        val crossfadeSeconds by rememberPreference(CrossfadeDurationKey, 5f)

        val currentPlaying by rememberUpdatedState(isPlaying)
        val currentCanControl by rememberUpdatedState(canControl)
        val currentPosition by rememberUpdatedState(positionProvider)
        val currentDuration by rememberUpdatedState(durationProvider)
        val currentSeek by rememberUpdatedState(onSeek)
        val currentToggle by rememberUpdatedState(onTogglePlay)
        val currentScratchPlayer by rememberUpdatedState(scratchPlayer)
        val needleProgress = { positionMs: Long ->
            val side = currentAlbumSide
            if (side != null) {
                side.progress(needleSong.value ?: side.currentIndex, positionMs)
            } else {
                positionMs.toFloat() / currentDuration().coerceAtLeast(1L)
            }
        }
        val currentPrevious by rememberUpdatedState(onPrevious)
        val currentNext by rememberUpdatedState(onNext)
        val currentCenter by rememberUpdatedState(center)
        val currentLeftHanded by rememberUpdatedState(leftHanded)
        val currentRadius by rememberUpdatedState(radius)
        val currentWidth by rememberUpdatedState(widthPx)

        val clampScrub = { value: Long -> value.coerceIn(0L, (currentDuration() - 1_000L).coerceAtLeast(0L)) }
        val moveScrub = { from: Long, degrees: Float -> clampScrub(from + (degrees / 360f * SCRUB_MS_PER_TURN).toLong()) }
        val startScrub = {
            val player = currentScratchPlayer
            resumeAfterScrub.value = currentPlaying
            scrubMs.value = player?.currentPosition ?: currentPosition()
            player
        }
        // A lifted needle is silent, so the real player just pauses.
        val beginNeedle = {
            if (scrubMs.value == null) startScrub()?.pause()
            sound.stop()
        }
        val beginScratch = {
            if (scrubMs.value == null) {
                startScrub()?.let { player ->
                    sound.start(player.currentPosition)
                    scratch.value = ScratchSession(player)
                }
            }
        }
        val scope = rememberCoroutineScope()
        val handoff = remember { mutableStateOf<Job?>(null) }
        val endScrub = { seek: Boolean ->
            handoff.value?.cancel()
            handoff.value = null
            sound.stop()
            val resume = resumeAfterScrub.value
            val session = scratch.value
            scratch.value = null
            if (!resume) currentScratchPlayer?.pause()
            session?.restoreSpeed()
            if (seek) scrubMs.value?.let { currentSeek(it) }
            scrubMs.value = null
            session?.restoreVolume()
            if (resume) currentScratchPlayer?.play()
            resumeAfterScrub.value = false
        }
        // Seeking takes a moment, so scratch audio carries on at normal speed until the real player can take over in step.
        val handOff = { session: ScratchSession, target: Long ->
            handoff.value =
                scope.launch {
                    session.handingOff = true
                    sound.coast()
                    session.restoreSpeed()
                    session.player.pause()
                    currentSeek(target + HANDOFF_LEAD_MS)
                    val started = SystemClock.uptimeMillis()
                    while (true) {
                        delay(8L)
                        val elapsed = SystemClock.uptimeMillis() - started
                        scrubMs.value = target + elapsed
                        val inStep = elapsed >= HANDOFF_LEAD_MS - HANDOFF_START_LATENCY_MS
                        if ((inStep && session.player.playbackState == Player.STATE_READY) || elapsed > HANDOFF_TIMEOUT_MS) break
                    }
                    val landing = session.player.currentPosition
                    val framesAtPlay = VinylAudioBuffer.writtenFrames
                    session.player.play()
                    // Once the player's new audio starts arriving, the scratch sound moves onto it at
                    // the matching point in the song, so there's no run-out before the player is heard.
                    val rate = VinylAudioBuffer.sampleRate
                    val arrived =
                        withTimeoutOrNull(HANDOFF_TIMEOUT_MS) {
                            while (VinylAudioBuffer.writtenFrames - framesAtPlay < rate / 20) delay(4L)
                            true
                        } == true
                    if (arrived && rate > 0) {
                        val songNow = target + (SystemClock.uptimeMillis() - started)
                        sound.continueFrom(framesAtPlay + (songNow - landing).coerceAtLeast(0L) * rate / 1000)
                    }
                    // The player is still muted. Its position only moves once its sound is really
                    // coming out, so that's when the scratch sound can stop.
                    withTimeoutOrNull(HANDOFF_TIMEOUT_MS) {
                        while (session.player.currentPosition < landing + HANDOFF_AUDIBLE_MS) delay(4L)
                    }
                    delay(HANDOFF_START_LATENCY_MS)
                    session.restoreVolume()
                    sound.stop()
                    scratch.value = null
                    scrubMs.value = null
                    resumeAfterScrub.value = false
                }
        }
        val commitScrub = {
            val session = scratch.value
            val target = scrubMs.value
            val roomToLand = target != null && target + HANDOFF_LEAD_MS < currentDuration() - 1_000L
            if (session != null && target != null && resumeAfterScrub.value && roomToLand) {
                handOff(session, target)
            } else {
                endScrub(true)
            }
        }
        // Grabbing the record again mid-handoff finishes it first, and the music was playing either way.
        val interruptHandoff = {
            val active = handoff.value?.isActive == true
            if (active) endScrub(false)
            active
        }

        val throwX = remember { Animatable(0f) }
        val recordScale = remember { Animatable(1f) }
        val armSwing = remember { Animatable(0f) }
        val throwJob = remember { mutableStateOf<Job?>(null) }
        val currentId by rememberUpdatedState(mediaMetadata?.id)
        val offScreen = { side: Float ->
            if (side > 0f) currentWidth - currentCenter.x + currentRadius * 1.1f else -(currentCenter.x + currentRadius * 1.1f)
        }
        val settleRecord = {
            scope.launch { throwX.animateTo(0f, ArcMotion.bouncy()) }
            scope.launch { recordScale.animateTo(1f, ArcMotion.bouncy()) }
            scope.launch { armSwing.animateTo(0f, ArcMotion.bouncy()) }
        }
        val enterRecord: suspend (Float) -> Unit = { side ->
            throwX.snapTo(offScreen(side))
            recordScale.snapTo(0.9f)
            armSwing.snapTo(1f)
            withTimeoutOrNull(350L) {
                snapshotFlow { surfaceReadyFor.value == currentSeed && (currentThumbnail == null || artReadyFor.value == currentThumbnail) }
                    .first { it }
            }
            coroutineScope {
                launch { recordScale.animateTo(1f, ArcMotion.bouncy()) }
                launch {
                    delay(260L)
                    armSwing.animateTo(0f, ArcMotion.bouncy())
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                throwX.animateTo(0f, ArcMotion.landing())
            }
        }
        val throwRecord = { side: Float, velocity: Float, changeSong: () -> Unit ->
            throwJob.value?.cancel()
            throwJob.value =
                scope.launch {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    val oldId = currentId
                    launch { armSwing.animateTo(1f, tween(140)) }
                    throwX.animateTo(offScreen(side), tween(170, easing = FastOutLinearInEasing), velocity)
                    changeSong()
                    withTimeoutOrNull(600L) { snapshotFlow { currentId }.first { it != oldId } }
                    enterRecord(-side)
                }
        }
        // On the same record the needle hops to the next band. Only a different record gets thrown away.
        val changeSong = { side: Float, velocity: Float ->
            val album = currentAlbumSide
            val neighbour = album?.currentIndex?.plus(if (side < 0f) 1 else -1)
            val changeTo = if (side < 0f) currentNext else currentPrevious
            if (album != null && neighbour != null && album.contains(neighbour)) {
                throwJob.value?.cancel()
                throwJob.value =
                    scope.launch {
                        settleRecord()
                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        armSwing.animateTo(1f, tween(140))
                        changeTo()
                        delay(120L)
                        armSwing.animateTo(0f, ArcMotion.bouncy())
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
            } else {
                throwRecord(side, velocity) { changeTo() }
            }
        }

        val hearts = remember { VinylHearts(scope, density.density) }
        val heartPainter = painterResource(R.drawable.favorite)
        val currentLiked by rememberUpdatedState(liked)
        val currentToggleLike by rememberUpdatedState(onToggleLike)
        val currentHoldLabel by rememberUpdatedState(onHoldLabel)

        val tapCount = remember { mutableIntStateOf(0) }
        val tapJob = remember { mutableStateOf<Job?>(null) }
        val pulseRecord = {
            scope.launch {
                recordScale.snapTo(0.96f)
                recordScale.animateTo(1f, ArcMotion.bouncy())
            }
        }
        // One tap waits a moment in case a second follows. Every tap after the first pops a heart, and it only ever likes.
        val registerTap = { at: Offset ->
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            pulseRecord()
            tapCount.intValue++
            if (tapCount.intValue >= 2) {
                hearts.spawn(at)
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                if (!currentLiked) currentToggleLike()
            }
            tapJob.value?.cancel()
            tapJob.value =
                scope.launch {
                    delay(MULTI_TAP_MS)
                    val taps = tapCount.intValue
                    tapCount.intValue = 0
                    if (taps == 1 && currentCanControl) currentToggle()
                }
        }
        // The turntable's motor: it keeps running while the record is lifted or held.
        val motorOn = { if (scrubMs.value != null) resumeAfterScrub.value else currentPlaying }

        DisposableEffect(sound) {
            onDispose { if (scrubMs.value != null) endScrub(false) else sound.stop() }
        }

        val knownSeed = remember { mutableStateOf(seed) }
        val leavingLook = remember { mutableStateOf<RecordLook?>(null) }
        val textFade = remember { Animatable(1f) }
        val fadedInFor = remember { mutableStateOf(mediaMetadata?.id) }
        LaunchedEffect(mediaMetadata?.id) {
            if (fadedInFor.value == mediaMetadata?.id) return@LaunchedEffect
            fadedInFor.value = mediaMetadata?.id
            textFade.snapTo(0f)
            textFade.animateTo(1f, tween(380))
        }
        LaunchedEffect(mediaMetadata?.id) {
            fling.floatValue = 0f
            needleSong.value = null
            if (scrubMs.value != null) endScrub(false)
            if (knownSeed.value != seed) {
                knownSeed.value = seed
                // A song that ends on its own gets the same send-off as a swipe: the old record leaves
                // looking like itself, then the new one lands. Thrown records handle their own landing.
                if (throwJob.value?.isActive != true) {
                    leavingLook.value = RecordLook(surface, grooves, art)
                    try {
                        coroutineScope {
                            launch { armSwing.animateTo(1f, tween(140)) }
                            throwX.animateTo(offScreen(-1f), tween(240, easing = FastOutLinearInEasing))
                        }
                    } finally {
                        leavingLook.value = null
                    }
                    enterRecord(1f)
                }
            }
        }

        // Only when switching layouts, not every time the player sheet opens.
        LaunchedEffect(Unit) {
            if (transition.currentState != EnterExitState.Visible) {
                armSwing.snapTo(1f)
                delay(380L)
                armSwing.animateTo(0f, ArcMotion.bouncy())
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }

        val activeScratch = scratch.value
        LaunchedEffect(activeScratch) {
            activeScratch ?: return@LaunchedEffect
            while (true) {
                delay(100L)
                if (activeScratch.handingOff) break
                val endMargin = 1_500L + if (crossfadeEnabled) (crossfadeSeconds * 1000f).toLong() else 0L
                activeScratch.keepAhead(scrubMs.value ?: break, currentDuration(), endMargin)
            }
        }

        // Only the Canvas draw phase reads these values, so spinning never recomposes the player.
        // This content only exists while the player sheet is on screen, so it also spins while the sheet slides.
        LaunchedEffect(Unit) {
            var spin = if (motorOn()) PLAY_DEGREES_PER_SECOND else 0f
            var last = withFrameNanos { it }
            var sinceFlingTick = 0f
            while (true) {
                val target = if (motorOn()) PLAY_DEGREES_PER_SECOND else 0f
                if (target == 0f && spin == 0f && fling.floatValue == 0f) {
                    snapshotFlow { motorOn() || fling.floatValue != 0f }.first { it }
                    last = withFrameNanos { it }
                    continue
                }
                val now = withFrameNanos { it }
                val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                spin += (target - spin) * (1f - exp(-seconds * 4.5f))
                if (abs(target - spin) < 0.05f) spin = target
                if (dragging) continue

                val velocity = fling.floatValue
                angle.floatValue = (angle.floatValue + (spin + velocity) * seconds) % 360f
                if (velocity != 0f) {
                    scrubMs.value = scrubMs.value?.let { moveScrub(it, (spin + velocity) * seconds) }
                    scrubMs.value?.let(sound::moveTo)
                    sinceFlingTick += abs((spin + velocity) * seconds)
                    if (sinceFlingTick >= GROOVE_TICK_DEGREES * 2f) {
                        sinceFlingTick = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    val slowed = velocity * exp(-seconds * 2.6f)
                    if (abs(slowed) < 40f) {
                        fling.floatValue = 0f
                        commitScrub()
                    } else {
                        fling.floatValue = slowed
                    }
                }
            }
        }

        VinylBackdrop(art, Modifier.fillMaxSize())
        // In lyrics mode the text gets a solid color, which melts into the cover around the record.
        val lyricsFloor = with(density) { (center.y - radius * 1.12f).toDp() }
        val floorFade = 150.dp
        val solid = MaterialTheme.colorScheme.surface
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(lyricsFloor + floorFade)
                    .graphicsLayer { alpha = lyricsAmount * chromeAlpha }
                    .background(
                        Brush.verticalGradient(
                            0f to solid,
                            lyricsFloor / (lyricsFloor + floorFade) to solid,
                            1f to Color.Transparent,
                        ),
                    ),
        )
        val textColor = if (art != null) Color.White else contentColor

        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = slideInVertically(spring(0.6f, 220f, IntOffset.VisibilityThreshold)) { it },
                        exit = slideOutVertically(tween(260, easing = FastOutLinearInEasing)) { it },
                    ).pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // The screen edges belong to the system back swipe, so nothing on the record starts there.
                            if (down.position.x < backZoneLeftPx || down.position.x > size.width - backZoneRightPx) {
                                return@awaitEachGesture
                            }
                            val discCenter = currentCenter
                            val discRadius = currentRadius
                            val duration = currentDuration()
                            val canScrub = currentCanControl && duration > 0L
                            val startDistance = (down.position - discCenter).getDistance()
                            val startProgress = needleProgress(scrubMs.value ?: currentPosition())
                            val arm = tonearmGeometry(discCenter, discRadius, currentWidth, startProgress, currentLeftHanded > 0.5f)

                            if (canScrub && distanceToSegment(down.position, arm.pivot, arm.tip) < armTouchPx) {
                                down.consume()
                                fling.floatValue = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                scope.launch { armSwing.animateTo(ArcMotion.NEEDLE_LIFT, ArcMotion.snappy()) }
                                var needleStep = -1
                                val wasHandingOff = interruptHandoff()
                                beginNeedle()
                                needleSong.value = currentAlbumSide?.currentIndex
                                if (wasHandingOff) resumeAfterScrub.value = true
                                // The arm can be grabbed anywhere along its length, so moves are relative to the grab point.
                                val grabOffset = startDistance / discRadius - needleReach(startProgress)
                                var released = false
                                try {
                                while (true) {
                                        val change =
                                            awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        change.consume()
                                        if (!change.pressed) break
                                        val reach = (change.position - discCenter).getDistance() / discRadius - grabOffset
                                        val progress = ((ARM_OUTER - reach) / (ARM_OUTER - ARM_INNER)).coerceIn(0f, 1f)
                                        val side = currentAlbumSide
                                        if (side != null) {
                                            val (song, position) = side.locate(progress)
                                            if (song != needleSong.value) {
                                                needleSong.value = song
                                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                            }
                                            scrubMs.value = position
                                        } else {
                                            scrubMs.value = clampScrub((progress * duration).toLong())
                                        }
                                        val step = (progress * NEEDLE_TICKS).toInt()
                                        if (step != needleStep) {
                                            if (needleStep >= 0) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            needleStep = step
                                        }
                                }
                                released = true
                                } finally {
                                    // A gesture cut off midway would otherwise leave the arm held and the song preview stuck.
                                    if (!released) {
                                        needleSong.value = null
                                        if (scrubMs.value != null) endScrub(false)
                                        scope.launch { armSwing.animateTo(0f, ArcMotion.bouncy()) }
                                    }
                                }
                                val song = needleSong.value
                                val position = scrubMs.value
                                val player = currentScratchPlayer
                                needleSong.value = null
                                if (song != null && position != null && player != null && song != currentAlbumSide?.currentIndex) {
                                    endScrub(false)
                                    player.seekTo(song, position)
                                } else {
                                    commitScrub()
                                }
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                scope.launch { armSwing.animateTo(0f, ArcMotion.bouncy()) }
                                return@awaitEachGesture
                            }

                            val inCenter = startDistance < discRadius * LABEL_RATIO
                            // The bottom edge stays free of scratching, so swiping up from it opens the song list.
                            val inBottomEdge = down.position.y > size.height - bottomEdgePx
                            // Only the label throws the record, so a swipe in the empty space around it can't change the song.
                            if (!inCenter && (startDistance > discRadius || inBottomEdge)) return@awaitEachGesture
                            if (inCenter) {
                                val labelHold =
                                    scope.launch {
                                        delay(HOLD_LABEL_MS)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pulseRecord()
                                        currentHoldLabel()
                                    }
                                var moved = false
                                var sideways = false
                                var lastX = 0f
                                var pastThrowPoint = false
                                val tracker = VelocityTracker()
                                while (true) {
                                    val change =
                                        awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed) break
                                    if (!change.pressed) {
                                        val quick = change.uptimeMillis - down.uptimeMillis < 320L
                                        if (quick && !moved) registerTap(change.position)
                                        break
                                    }
                                    val travelled = change.position - down.position
                                    if (travelled.getDistance() > touchSlop) {
                                        moved = true
                                        labelHold.cancel()
                                    }
                                    if (!sideways) {
                                        // Claiming the gesture early is what stops the sheet closing on a slightly diagonal swipe.
                                        if (abs(travelled.x) > touchSlop / 2f && abs(travelled.x) >= abs(travelled.y) * 0.7f) {
                                            sideways = true
                                            throwJob.value?.cancel()
                                            scope.launch { recordScale.animateTo(ArcMotion.LIFTED_SCALE, ArcMotion.snappy()) }
                                        } else if (abs(travelled.y) > touchSlop) {
                                            break
                                        }
                                    }
                                    if (sideways) {
                                        change.consume()
                                        tracker.addPointerInputChange(change)
                                        lastX = travelled.x
                                        val pastPoint = abs(lastX) > currentWidth * THROW_POINT
                                        if (pastPoint != pastThrowPoint) {
                                            pastThrowPoint = pastPoint
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        }
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) { throwX.snapTo(lastX) }
                                    }
                                }
                                labelHold.cancel()
                                if (sideways) {
                                    val velocity = tracker.calculateVelocity().x
                                    val flicked = abs(velocity) > flickPx && velocity * lastX > 0f
                                    val thrown = currentCanControl && (flicked || abs(lastX) > currentWidth * THROW_POINT)
                                    when {
                                        !thrown -> settleRecord()
                                        lastX < 0f -> changeSong(-1f, velocity)
                                        else -> changeSong(1f, velocity)
                                    }
                                }
                                return@awaitEachGesture
                            }

                            // Touching the record stops the music, like a hand on a real platter.
                            down.consume()
                            fling.floatValue = 0f
                            dragging = true
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            scope.launch { recordScale.animateTo(ArcMotion.HELD_SCALE, ArcMotion.snappy()) }
                            if (canScrub) {
                                val wasHandingOff = interruptHandoff()
                                beginScratch()
                                if (wasHandingOff) resumeAfterScrub.value = true
                            }

                            var previous = (down.position - discCenter).let { atan2(it.y, it.x) }
                            var velocity = 0f
                            var previousTime = down.uptimeMillis
                            var sinceTick = 0f
                            var endTime = down.uptimeMillis

                            try {
                                while (true) {
                                    val change =
                                        awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    endTime = change.uptimeMillis
                                    if (!change.pressed) break

                                    val heading = (change.position - discCenter).let { atan2(it.y, it.x) }
                                    var delta = heading - previous
                                    if (delta > PI) delta -= (2 * PI).toFloat()
                                    if (delta < -PI) delta += (2 * PI).toFloat()
                                    previous = heading
                                    // In forward degrees: the left-handed record turns the other way, into its mirrored needle.
                                    val degrees = delta * 180f / PI.toFloat() * (if (currentLeftHanded > 0.5f) -1f else 1f)

                                    val elapsed = (change.uptimeMillis - previousTime).coerceAtLeast(1L) / 1000f
                                    previousTime = change.uptimeMillis
                                    velocity = velocity * 0.7f + (degrees / elapsed) * 0.3f
                                    angle.floatValue = (angle.floatValue + degrees) % 360f
                                    if (canScrub) {
                                        scrubMs.value = moveScrub(scrubMs.value ?: currentPosition(), degrees)
                                        scrubMs.value?.let(sound::moveTo)
                                        sinceTick += abs(degrees)
                                        if (sinceTick >= GROOVE_TICK_DEGREES) {
                                            sinceTick = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                        }
                                    }
                                }
                            } finally {
                                dragging = false
                            }

                            scope.launch { recordScale.animateTo(1f, ArcMotion.bouncy()) }
                            if (scrubMs.value != null) {
                                val stillMoving = endTime - previousTime < 90L && abs(velocity) > 80f
                                if (stillMoving) {
                                    fling.floatValue = velocity.coerceIn(-1800f, 1800f)
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    commitScrub()
                                }
                            }
                        }
                    },
        ) {
            val thrown = throwX.value
            translate(left = thrown) {
                scale(recordScale.value, center) {
                    val rolled = angle.floatValue * (if (leftHanded > 0.5f) -1f else 1f) + thrown / radius * 180f / PI.toFloat()
                    val look = leavingLook.value
                    drawRecord(
                        center,
                        radius,
                        rolled,
                        look?.surface ?: surface,
                        look?.grooves ?: grooves,
                        if (look != null) look.art else art,
                        isPlaying || scrubMs.value != null,
                    )
                }
            }
            val progress = needleProgress(scrubMs.value ?: currentPosition()).coerceIn(0f, 1f)
            val arm = tonearmGeometry(center, radius, size.width, progress, leftHanded > 0.5f)
            rotate(armSwing.value * ARM_LIFT_DEGREES * (if (leftHanded > 0.5f) -1f else 1f), arm.pivot) { drawTonearm(arm) }
            hearts.draw(this, heartPainter)
        }

        if (showLyrics) {
            val armTop = with(density) { (center.y - radius * 1.12f).toDp() }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(armTop)
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                        .padding(top = 48.dp),
            ) {
                lyrics()
            }
        } else {
            // Left-handed, the text moves across too, out of the way of the record and the thumb.
            val textOnRight = leftHanded > 0.5f
            // While the needle moves across an album, the text previews the song it would land on.
            val shownMetadata =
                needleSong.value
                    ?.takeUnless { it == albumSide?.currentIndex }
                    ?.let { scratchPlayer?.getMediaItemAt(it)?.metadata }
                    ?: mediaMetadata
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .animateEnterExit(
                            enter = fadeIn(tween(200, delayMillis = 120)) +
                                slideInVertically(spring(0.6f, 300f, IntOffset.VisibilityThreshold)) { -it / 3 },
                            exit = fadeOut(tween(120)),
                        ).graphicsLayer {
                            translationX = throwX.value * 0.3f
                            alpha = (1f - abs(throwX.value) / size.width * 1.2f).coerceIn(0f, 1f) * textFade.value * chromeAlpha
                        }.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                        .padding(start = 24.dp, end = if (textOnRight) 64.dp else 24.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = if (textOnRight) Alignment.End else Alignment.Start,
            ) {
                val showLikedHeart = liked && needleSong.value == null
                Text(
                    text =
                        buildAnnotatedString {
                            append(shownMetadata?.title.orEmpty())
                            if (showLikedHeart) {
                                append(" ")
                                appendInlineContent(LIKED_HEART_ID)
                            }
                        },
                    inlineContent =
                        mapOf(
                            LIKED_HEART_ID to
                                InlineTextContent(Placeholder(0.75.em, 0.75.em, PlaceholderVerticalAlign.TextCenter)) {
                                    Icon(
                                        painterResource(R.drawable.favorite),
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                },
                        ),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (textOnRight) TextAlign.End else TextAlign.Start,
                    modifier =
                        Modifier
                            .padding(end = if (textOnRight) 0.dp else 40.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpenAlbum),
                )
                val artistLine =
                    buildAnnotatedString {
                        val artists = shownMetadata?.artists.orEmpty()
                        artists.forEachIndexed { index, artist ->
                            val id = artist.id
                            if (id != null) {
                                withLink(LinkAnnotation.Clickable(id, TextLinkStyles(SpanStyle(textDecoration = TextDecoration.None))) { onOpenArtist(id) }) { append(artist.name) }
                            } else {
                                append(artist.name)
                            }
                            if (index != artists.lastIndex) append(", ")
                        }
                    }
                Text(
                    text = artistLine,
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VinylTime(
                    { scrubMs.value ?: currentPosition() },
                    { needleSong.value?.let { currentAlbumSide?.durationOf(it) } ?: currentDuration() },
                    textColor,
                )
            }
        }

        if (!turntable) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = chromeAlpha },
            ) {
                bottomBar()
            }
        }
    }
}

// Separate so the 10 Hz position updates only recompose this text.
@Composable
internal fun VinylTime(positionMs: () -> Long, durationMs: () -> Long, color: Color, big: TextUnit = 84.sp, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Text(
            text = makeTimeString(positionMs()),
            style = TextStyle(fontSize = big, lineHeight = big * 1.05f, fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"),
            color = color,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = makeTimeString(durationMs()),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = color.copy(alpha = 0.85f),
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VinylPlayerToggle(contentColor: Color, modifier: Modifier = Modifier) {
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) return
    var enabled by rememberPreference(ArcVinylPlayerKey, true)
    val haptics = LocalHapticFeedback.current
    val turn by animateFloatAsState(if (enabled) 360f else 0f, spring(0.5f, Spring.StiffnessLow), label = "vinylToggleTurn")
    val press = remember { MutableInteractionSource() }
    IconButton(
        interactionSource = press,
        onClick = {
            haptics.performHapticFeedback(if (enabled) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            enabled = !enabled
        },
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(top = 8.dp, end = 8.dp)
                .pressBounce(press),
    ) {
        Icon(
            painter = painterResource(R.drawable.album),
            contentDescription = stringResource(R.string.vinyl_player_toggle),
            tint = contentColor.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.rotateModifier(turn),
        )
    }
}
