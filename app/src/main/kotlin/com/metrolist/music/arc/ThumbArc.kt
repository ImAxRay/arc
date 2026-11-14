package com.metrolist.music.arc

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.ui.input.pointer.PointerEventType
import com.metrolist.music.ui.player.LYRICS_FADE_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.SweepGradient
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import com.metrolist.music.utils.makeTimeString
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.withTimeoutOrNull

/** Which layout the normal player uses: 0 the thumb arc, 1 the small corner arc. Holding play switches them. */
val ArcPlayerLayoutKey = intPreferencesKey("arcPlayerLayout")

/**
 * The curve a thumb sweeps without effort: a circle around the base of the thumb, which sits off the
 * side of the screen. Angles are in radians from the bottom of the sweep, growing upwards. Right-handed,
 * the pivot is right of the screen and the curve rises to the right; left-handed it's mirrored.
 */
class ThumbArc(val pivot: Offset, val radius: Float, private val mirrored: Boolean) {
    private val sideways = if (mirrored) 1f else -1f

    fun point(angle: Float, radius: Float = this.radius) =
        Offset(pivot.x + sideways * radius * cos(angle), pivot.y - radius * sin(angle))

    fun angleOf(position: Offset): Float = atan2(pivot.y - position.y, sideways * (position.x - pivot.x))

    fun distanceTo(position: Offset): Float = hypot(position.x - pivot.x, position.y - pivot.y)

    /** The angle at which a circle of [radius] reaches [x], or null if it never does. */
    fun angleAtX(x: Float, radius: Float = this.radius): Float? {
        val reach = sideways * (x - pivot.x) / radius
        return if (reach in -1f..1f) acos(reach) else null
    }

    companion object {
        // From the sweep the user drew on a Pixel 7, as fractions of the player's width.
        const val PIVOT_X = 1.63f
        const val RADIUS = 1.1f

        fun default(width: Float, bottomRowY: Float, mirrored: Boolean): ThumbArc {
            val pivotX = if (mirrored) width * (1f - PIVOT_X) else width * PIVOT_X
            return ThumbArc(Offset(pivotX, bottomRowY), width * RADIUS, mirrored)
        }
    }
}

private val ButtonStep = 66.dp
private val TrackOutset = 60.dp
private val TrackGrab = 32.dp
private val TrackWidth = 14.dp
private val EdgeMargin = 22.dp
private val TrackLift = 46.dp
private val CornerInset = 12.dp
private val SwipeDistance = 64.dp
private val DarkInk = Color(0xFF15110F)

private class Shade(val rest: Brush, val fill: Brush, val text: android.graphics.Shader, val darkUntil: Float)
private const val FILL_IN_MS = 650
private const val DOUBLE_TAP_MS = 250L

/**
 * The normal player's controls laid along the thumb's arc: previous, play and next with share and
 * like either side, a curved progress line just outside the arc, and the quieter buttons continuing
 * from the arc's foot along the bottom. [toCorner] slides every button into the compact corner
 * layout used with lyrics, and the progress line, which runs edge to edge, bends into that shape
 * too, with the title in the corner it leaves.
 */
@Composable
fun ThumbArcControls(
    mainIcon: Int,
    onMain: () -> Unit,
    hold: PlayHoldState,
    previous: CapsuleButton,
    next: CapsuleButton,
    share: CapsuleButton,
    like: CapsuleButton,
    extras: List<CapsuleButton>,
    leftHanded: Boolean,
    toCorner: Float,
    bottomInset: Dp,
    content: Color,
    mainFill: Color,
    mainTint: Color,
    endDot: EndDot,
    labels: ArcLabels,
    nextUp: NextUp?,
    audioDetails: String? = null,
    glowArtwork: String?,
    coverShown: Boolean = glowArtwork != null,
    cornerArc: Boolean,
    ground: Color,
    groundNear: Color = ground,
    topLimit: () -> Float,
    swipeShift: Animatable<Float, AnimationVector1D>,
    positionMs: () -> Long,
    durationMs: () -> Long,
    onScrub: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeFromButtons = remember { SongSwitch() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val haptics = LocalHapticFeedback.current
        val width = constraints.maxWidth.toFloat()
        val px = { dp: Dp -> with(density) { dp.toPx() } }
        val bottomRowY = constraints.maxHeight - px(bottomInset) - px(PlaySize) / 2 - px(4.dp)
        val arc = ThumbArc.default(width, bottomRowY, leftHanded)
        val step = px(ButtonStep) / arc.radius
        val playAngle = (24.0 * PI / 180).toFloat()

        val trackRadius = arc.radius + px(TrackOutset)
        // Starts above the bottom row, so it clears the buttons there.
        val startAngle = asin((px(TrackLift) / trackRadius).coerceIn(0f, 1f))
        val edgeX = if (leftHanded) px(EdgeMargin) else width - px(EdgeMargin)
        val edgeAngle = arc.angleAtX(edgeX, trackRadius) ?: (PI / 3).toFloat()
        val limit = topLimit()
        val endAngle = edgeAngle
        val currentPosition by rememberUpdatedState(positionMs)
        val currentDuration by rememberUpdatedState(durationMs)
        val currentScrub by rememberUpdatedState(onScrub)
        val currentSeek by rememberUpdatedState(onSeek)
        // Gone by the halfway point, so the straight bar only appears once the arc has faded.
        val trackShown = (1f - toCorner * 2f).coerceIn(0f, 1f)
        val progressAt = { position: Offset ->
            val along = ((arc.angleOf(position) - startAngle) / (endAngle - startAngle)).coerceIn(0f, 1f)
            // Left-handed, the line fills from its far end, so the song starts at the top.
            if (leftHanded) 1f - along else along
        }
        val lines = rememberCurvedLines(labels, arc, trackRadius + px(TrackWidth) / 2, width, leftHanded, content)
        val marqueeClock = remember { mutableLongStateOf(0L) }
        if (lines.any { it.overflows } && trackShown > 0f) {
            val marqueeSpeed = px(MarqueeSpeed)
            LaunchedEffect(lines) {
                runMarqueeClock({ lines.filter { it.overflows }.map { it.loop } }, marqueeSpeed) { marqueeClock.longValue = it }
            }
        }

        val cornerWidth = with(density) { (width - px(CornerInset) * 2).toDp() }
        val side = if (leftHanded) 1f else 0f
        val cornerTop = bottomRowY + px(PlaySize) / 2 + px(4.dp) - px(CornerBoxHeight)
        val toScreen = { offset: DpOffset -> Offset(px(CornerInset) + px(offset.x), cornerTop + px(offset.y)) }
        val (cornerPlay, cornerPieces) = cornerLayout(side = side, width = cornerWidth)
        val cornerExtras = extraCentres(extras.size, side = side, width = cornerWidth)
        val cornerPlayAt = toScreen(cornerPlay)
        val corner =
            remember(width, cornerPlayAt, leftHanded, density) { lyricsLine(width, cornerPlayAt, px(TrackWidth), leftHanded, density) }
        // The line runs off the screen, except at the bottom, where it stops above the gesture bar.
        val lineBottom = constraints.maxHeight - WindowInsets.navigationBars.getBottom(density) - px(TrackWidth) / 2 - px(4.dp)
        val thumb =
            remember(width, lineBottom, arc.pivot, trackRadius, startAngle, endAngle, leftHanded) {
                thumbLine(width, lineBottom, arc, trackRadius, startAngle, endAngle, px(TrackWidth), leftHanded)
            }
        // Over a light cover's glow the line and text turn dark, and back to light where the glow has faded.
        // Lyrics drop the cover, so the glow fades but keeps the last art for when it comes back.
        val lastArtwork = remember { mutableStateOf(glowArtwork) }
        if (glowArtwork != null) lastArtwork.value = glowArtwork
        val art = rememberCoverArt(lastArtwork.value, upcoming = nextUp?.artwork, titleOnLeft = leftHanded)
        val glowShown by animateFloatAsState(if (glowArtwork != null) 1f else 0f, tween(LYRICS_FADE_MS), label = "glow")
        // the time and battery belong to the cover view, whether or not the song has art
        val statusShown by animateFloatAsState(if (coverShown) 1f else 0f, tween(LYRICS_FADE_MS), label = "status")
        val bright = glowArtwork != null && art?.titleBright == true
        val shade =
            remember(bright, limit, width, content, mainFill) {
                if (!bright || limit <= 0f) {
                    null
                } else {
                    val until = limit + width * 0.45f
                    fun brush(dark: Color, light: Color) = Brush.verticalGradient(0f to dark, 0.45f to dark, 1f to light, startY = limit, endY = until)
                    Shade(
                        rest = brush(DarkInk.copy(alpha = 0.25f), content.copy(alpha = 0.2f)),
                        fill = brush(DarkInk, mainFill),
                        text =
                            android.graphics.LinearGradient(
                                0f, limit, 0f, until,
                                intArrayOf(DarkInk.toArgb(), DarkInk.toArgb(), content.toArgb()), floatArrayOf(0f, 0.45f, 1f),
                                android.graphics.Shader.TileMode.CLAMP,
                            ),
                        darkUntil = limit + width * 0.25f,
                    )
                }
            }
        val paths = remember { LinePaths() }
        val cornerShown = ((toCorner - 0.5f) * 2f).coerceIn(0f, 1f)
        val fillIn = remember { Animatable(1f) }
        LaunchedEffect(endDot.songId) {
            fillIn.snapTo(0f)
            fillIn.animateTo(1f, tween(FILL_IN_MS, easing = FastOutSlowInEasing))
        }

        val currentMain by rememberUpdatedState(onMain)
        val currentPrevious by rememberUpdatedState(previous)
        val currentNext by rememberUpdatedState(next)
        val switchSong = { forward: Int ->
            val button = if (forward > 0) currentNext else currentPrevious
            if (button.enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                button.onClick()
            }
        }
        val swipeDistance = px(SwipeDistance)
        val gestures = WindowInsets.systemGestures
        val edgeLeft = gestures.getLeft(density, LayoutDirection.Ltr).toFloat().coerceAtLeast(px(24.dp))
        val edgeRight = gestures.getRight(density, LayoutDirection.Ltr).toFloat().coerceAtLeast(px(24.dp))
        val shiftScope = rememberCoroutineScope()
        val pendingTap = remember { mutableStateOf<Job?>(null) }
        val currentLike by rememberUpdatedState(like)
        // Where each small button sits, so a tap that just misses one presses it instead of pausing the song.
        val buttonTargets = remember { ArrayList<Pair<Offset, CapsuleButton>>() }
        val nearMiss = px(NearMissRadius)
        val nearTaps = remember { androidx.compose.runtime.mutableStateMapOf<Int, Int>() }
        SideEffect {
            swipeFromButtons.nearButton = { at ->
                buttonTargets
                    .filter { it.second.enabled && it.second.icon != null }
                    .minByOrNull { (it.first - at).getDistance() }
                    ?.takeIf { (it.first - at).getDistance() <= nearMiss }
                    ?.second
                    ?.let { button ->
                        {
                            val icon = button.icon!!
                            nearTaps[icon] = (nearTaps[icon] ?: 0) + 1
                            button.onClick()
                        }
                    }
            }
            swipeFromButtons.distance = swipeDistance
            swipeFromButtons.tapZone = edgeLeft..(width - edgeRight)
            swipeFromButtons.onSwitch = switchSong
            swipeFromButtons.onTap = {
                val first = pendingTap.value
                if (first?.isActive == true) {
                    // A second tap likes the song, like a double tap on a photo. It never unlikes.
                    first.cancel()
                    pendingTap.value = null
                    if (currentLike.active) haptics.performHapticFeedback(HapticFeedbackType.Confirm) else currentLike.onClick()
                } else {
                    pendingTap.value =
                        shiftScope.launch {
                            delay(DOUBLE_TAP_MS)
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            currentMain()
                        }
                }
            }
            swipeFromButtons.onShift = { shift -> shiftScope.launch { swipeShift.snapTo(shift) } }
            swipeFromButtons.onPassed = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
            swipeFromButtons.onSettle = { shiftScope.launch { swipeShift.animateTo(0f, ArcMotion.springy()) } }
        }

        // Both layouts keep the cover, its glow and the status line. The glow's cut-out inside the arc shrinks as the line moves to the corner.
        val chromeShown = glowShown
        // Below the cover the ground deepens from the cover's edge colour towards the buttons.
        val groundBrush = Brush.verticalGradient(listOf(groundNear, ground), startY = limit, endY = constraints.maxHeight.toFloat())
        if (groundNear != ground) {
            Canvas(Modifier.fillMaxSize()) { drawRect(groundBrush, topLeft = Offset(0f, limit), size = Size(size.width, size.height - limit)) }
        }
        CoverGlow(art, limit, arc.pivot, trackRadius * (1f - toCorner.coerceIn(0f, 1f)), chromeShown, shift = { swipeShift.value })
        if (statusShown > 0.01f) StatusLine(dark = glowArtwork != null && art?.statusBright == true, modifier = Modifier.graphicsLayer { alpha = statusShown })
        if (limit > 0f) {
            // Below the cover or lyrics, a sideways swipe anywhere off the line and buttons changes the song.
            Box(
                Modifier
                    .offset { IntOffset(0, limit.roundToInt()) }
                    .size(with(density) { width.toDp() }, with(density) { (constraints.maxHeight - limit).coerceAtLeast(0f).toDp() })
                    .pointerInput(Unit) {
                        awaitEachGesture { swipeSongs(awaitFirstDown(requireUnconsumed = false), swipeFromButtons, originY = limit, background = true) }
                    },
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            // Inside the arc is plain ground, so where the line runs over the cover it cuts the corner off.
            if (trackShown > 0.01f) drawCircle(groundBrush, trackRadius, arc.pivot, alpha = trackShown)
            val duration = currentDuration().coerceAtLeast(1L)
            val progress = (currentPosition().toFloat() / duration).coerceIn(0f, 1f)
            val end =
                drawArcLine(
                    thumb, corner.shape, toCorner.coerceIn(-0.2f, 1.2f), progress, fillIn.value,
                    shade?.rest ?: SolidColor(content.copy(alpha = 0.2f)), shade?.fill ?: SolidColor(mainFill), TrackWidth.toPx(), paths,
                )
            val ledge = if (leftHanded) end.x else end.x - 40.dp.toPx()
            val dotColor = if (shade != null && end.y < shade.darkUntil && toCorner < 0.5f) DarkInk else mainFill
            endDot.draw(this, end, dotColor, ledgeLeft = ledge)
            if (trackShown > 0.01f) lines.forEach { line ->
                val text = if (line.isTime) "${makeTimeString(currentPosition())} / ${makeTimeString(currentDuration())}${audioDetails?.let { "  ·  $it" }.orEmpty()}" else null
                line.draw(this, trackShown, text, marqueeClock.longValue, shade?.text)
            }
        }

        if (trackShown > 0.5f) {
            // Only the band around the track takes touches, so the art and title under this layer still get theirs.
            val ends =
                listOf(arc.point(startAngle, trackRadius), arc.point(endAngle, trackRadius), arc.point((startAngle + endAngle) / 2, trackRadius)) +
                    lines.flatMap { it.corners() }
            val grab = px(TrackGrab)
            val bandLeft = ends.minOf { it.x } - grab
            val bandTop = ends.minOf { it.y } - grab
            val bandRight = ends.maxOf { it.x } + grab
            val bandBottom = ends.maxOf { it.y } + grab
            val bandOrigin = Offset(bandLeft, bandTop)
            Box(
                Modifier
                    .offset { IntOffset(bandLeft.roundToInt(), bandTop.roundToInt()) }
                    .size(with(density) { (bandRight - bandLeft).toDp() }, with(density) { (bandBottom - bandTop).toDp() })
                    .pointerInput(arc.pivot, trackRadius, startAngle, endAngle, lines) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val at = down.position + bandOrigin
                            val onTrack =
                                abs(arc.distanceTo(at) - trackRadius) < px(TrackGrab) &&
                                    arc.angleOf(at) in startAngle - 0.05f..endAngle + 0.05f
                            val duration = currentDuration()
                            if (!onTrack) {
                                val hit = lines.firstNotNullOfOrNull { line -> line.hit(at)?.let { line to it } }
                                if (hit == null) {
                                    swipeSongs(down, swipeFromButtons, originX = bandLeft)
                                    return@awaitEachGesture
                                }
                                down.consume()
                                var timedOut = true
                                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation().also { timedOut = false } }
                                if (timedOut) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hit.first.onHold()
                                } else if (up != null) {
                                    hit.first.onTap(hit.second)
                                }
                                return@awaitEachGesture
                            }
                            if (duration <= 0L) return@awaitEachGesture
                            down.consume()
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            var target = (progressAt(at) * duration).toLong()
                            currentScrub(target)
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                target = (progressAt(change.position + bandOrigin) * duration).toLong()
                                currentScrub(target)
                            }
                            currentSeek(target)
                            currentScrub(null)
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        }
                    },
            )
        }

        if (cornerShown > 0.5f) {
            val grab = px(TrackGrab) * 0.75f
            val song = corner.shape.song
            val bandLeft = song.minOf { it.x } - grab
            val bandTop = song.minOf { it.y } - grab
            val bandRight = song.maxOf { it.x } + grab
            val bandBottom = song.maxOf { it.y } + grab
            val bandOrigin = Offset(bandLeft, bandTop)
            val fractionAt = { at: Offset -> nearestPoint(song, at).first.toFloat() / (SONG_POINTS - 1) }
            Box(
                Modifier
                    .offset { IntOffset(bandLeft.roundToInt(), bandTop.roundToInt()) }
                    .size(with(density) { (bandRight - bandLeft).toDp() }, with(density) { (bandBottom - bandTop).toDp() })
                    .pointerInput(corner) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val at = down.position + bandOrigin
                            val duration = currentDuration()
                            if (nearestPoint(song, at).second > grab) {
                                swipeSongs(down, swipeFromButtons, originX = bandLeft)
                                return@awaitEachGesture
                            }
                            if (duration <= 0L) return@awaitEachGesture
                            down.consume()
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            var target = (fractionAt(at) * duration).toLong()
                            currentScrub(target)
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                target = (fractionAt(change.position + bandOrigin) * duration).toLong()
                                currentScrub(target)
                            }
                            currentSeek(target)
                            currentScrub(null)
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        }
                    },
            )
        }
        if (cornerShown > 0.01f) {
            // Sized down to the line but without touch handling of its own, so the lyrics above still scroll.
            Box(
                contentAlignment = Alignment.BottomStart,
                modifier =
                    Modifier
                        .offset { IntOffset(corner.titleLeft.roundToInt(), 0) }
                        .size(with(density) { (corner.titleRight - corner.titleLeft).toDp() }, with(density) { corner.titleBottom.toDp() })
                        .graphicsLayer { alpha = cornerShown },
            ) {
                androidx.compose.foundation.layout.Column {
                    if (cornerArc && nextUp != null && chromeShown > 0.01f) {
                        NextUpCard(nextUp, content, Modifier.fillMaxWidth().height(NextUpHeight).graphicsLayer { alpha = chromeShown })
                        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    }
                    ArcLyricsTitle(labels, currentPosition, currentDuration, content)
                }
            }
        }

        if (nextUp != null && !cornerArc && trackShown > 0.01f) {
            // Inside the arc, beside share and above the bottom row.
            val share = arc.point(playAngle - step * 2)
            val gap = px(IconTarget) / 2 + px(10.dp)
            val left = if (leftHanded) px(12.dp) else share.x + gap
            val right = if (leftHanded) share.x - gap else width - px(12.dp)
            if (right - left > px(96.dp)) {
                NextUpCard(
                    nextUp,
                    content,
                    Modifier
                        .offset { IntOffset(left.roundToInt(), (share.y - px(NextUpHeight) / 2).roundToInt()) }
                        .size(with(density) { (right - left).toDp() }, NextUpHeight)
                        .graphicsLayer { alpha = trackShown },
                )
            }
        }

        fun blend(arcPoint: Offset, cornerPoint: Offset) =
            Offset(arcPoint.x + (cornerPoint.x - arcPoint.x) * toCorner, arcPoint.y + (cornerPoint.y - arcPoint.y) * toCorner)

        // Along the arc from its foot: share, previous, play, next, like; left-handed the arc runs the other way, so the two swap
        // to keep previous on the left. The corner layout orders them previous, share, like, next.
        val arcSlots =
            if (leftHanded) {
                listOf(share to 1, next to 3, null to -1, previous to 0, like to 2)
            } else {
                listOf(share to 1, previous to 0, null to -1, next to 3, like to 2)
            }
        var playCentre = Offset.Zero
        buttonTargets.clear()
        arcSlots.forEachIndexed { index, (button, cornerIndex) ->
            val onArc = arc.point(playAngle + step * (index - 2))
            if (button == null) {
                val centre = blend(onArc, toScreen(cornerPlay))
                playCentre = centre
                PlayButton(mainIcon, onMain, hold.hold, mainFill, mainTint, Modifier.centredAt(centre, px(PlaySize)).swipeFromButton(swipeFromButtons))
            } else {
                val centre = blend(onArc, toScreen(cornerPieces[cornerIndex]))
                buttonTargets += centre to button
                FloatingIcon(button, content, Modifier.centredAt(centre, px(IconTarget)).swipeFromButton(swipeFromButtons), remoteTaps = button.icon?.let { nearTaps[it] } ?: 0)
            }
        }

        val foot = arc.point(0f)
        extras.forEachIndexed { index, button ->
            // Shuffle sits right at the foot of the arc, the rest run along the bottom under the arc, clear of the line.
            val fromArc = extras.size - 1 - index
            val room = if (leftHanded) foot.x - px(28.dp) else width - px(28.dp) - foot.x
            val extraStep = minOf(px(54.dp), room / (extras.size - 1).coerceAtLeast(1))
            val onArc = Offset(foot.x + (if (leftHanded) -1f else 1f) * extraStep * fromArc, bottomRowY)
            val inCorner = Offset(px(CornerInset) + px(cornerExtras[index]), bottomRowY)
            val centre = blend(onArc, inCorner)
            buttonTargets += centre to button
            FloatingIcon(button, content, Modifier.centredAt(centre, px(IconTarget)), toggle = true, remoteTaps = button.icon?.let { nearTaps[it] } ?: 0)
        }

        PlayHoldOverlay(hold, playCentre, inward = playCentre.x > width / 2, width = width, content = content, ground = ground)
    }
}

/**
 * Follows a sideways drag from [down]: the cover follows it, and letting go far enough switches to
 * the next song (swiped left) or the previous one. A tap that doesn't move toggles play.
 * Vertical drags are left to the sheet.
 */
private suspend fun AwaitPointerEventScope.swipeSongs(
    down: PointerInputChange,
    switch: SongSwitch,
    originX: Float = 0f,
    originY: Float = 0f,
    background: Boolean = false,
) {
    var total = 0f
    val drag =
        awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            total += over
            switch.drag(total)
        }
    if (drag == null) {
        val up = currentEvent.changes.firstOrNull { it.id == down.id }
        val still = up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop
        // A back swipe starts as a touch at the screen edge and is then cancelled, so it must never count as a tap.
        val lifted = currentEvent.type == PointerEventType.Release && up != null && !up.pressed
        val clearOfEdges = originX + down.position.x in switch.tapZone
        if (lifted && still && clearOfEdges && up!!.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
            val missed = if (background) switch.nearButton(Offset(originX + down.position.x, originY + down.position.y)) else null
            (missed ?: switch.onTap)()
        }
        return
    }
    horizontalDrag(drag.id) { change ->
        total += change.positionChange().x
        change.consume()
        switch.drag(total)
    }
    switch.release(total)
}

/** What a sideways swipe does, shared by every place one can start. */
private class SongSwitch {
    var distance = 0f
    var onSwitch: (Int) -> Unit = {}
    var onTap: () -> Unit = {}
    var tapZone: ClosedFloatingPointRange<Float> = 0f..Float.MAX_VALUE
    var nearButton: (Offset) -> (() -> Unit)? = { null }
    var onShift: (Float) -> Unit = {}
    var onPassed: () -> Unit = {}
    var onSettle: () -> Unit = {}
    private var passed = false

    fun drag(total: Float) {
        // The cover moves less than the finger and stops short, so it reads as a nudge, not a page turn.
        onShift(total.coerceIn(-distance * 3, distance * 3) * 0.4f)
        val beyond = abs(total) >= distance
        if (beyond != passed) {
            passed = beyond
            if (beyond) onPassed()
        }
    }

    fun release(total: Float) {
        passed = false
        if (abs(total) >= distance) onSwitch(if (total < 0f) 1 else -1)
        onSettle()
    }
}

/**
 * Lets a song swipe start on a button too. It sits on the button itself and watches before the
 * button's own click does, taking the touch only once the finger clearly moves sideways, which
 * also cancels the tap. On a full-screen layer it would block the lyrics and cover underneath.
 */
private fun Modifier.swipeFromButton(switch: SongSwitch) =
    pointerInput(switch) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            var dragging = false
            var moved = Offset.Zero
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                moved = change.position - down.position
                if (!dragging) {
                    // Past a long press the button's own hold owns the finger.
                    if (change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) break
                    if (abs(moved.y) > slop && abs(moved.y) > abs(moved.x)) break
                    if (abs(moved.x) > slop) dragging = true
                }
                if (dragging) change.consume()
                if (!change.pressed) break
                if (dragging) switch.drag(moved.x)
            }
            if (dragging) switch.release(moved.x)
        }
    }

private val NearMissRadius = 40.dp

private fun Modifier.centredAt(centre: Offset, size: Float) =
    offset { IntOffset((centre.x - size / 2).roundToInt(), (centre.y - size / 2).roundToInt()) }

/** Elapsed and total time on one line, read in its own scope so the ticking only recomposes this text. */
@Composable
fun ArcTimeLine(
    positionMs: () -> Long,
    durationMs: () -> Long,
    color: Color,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium,
) {
    androidx.compose.material3.Text(
        text = "${makeTimeString(positionMs())} / ${makeTimeString(durationMs())}",
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color.copy(alpha = 0.85f),
        modifier = modifier,
    )
}

/** What the thumb arc writes along its outside. [artists] are names with their ids, joined with commas. */
class ArcLabels(
    val title: String,
    val artists: List<Pair<String, String?>>,
    val onTitle: () -> Unit,
    val onTitleHold: () -> Unit,
    val onArtist: (String) -> Unit,
    val onArtistHold: () -> Unit,
) {
    val artistText = artists.joinToString(", ") { it.first }

    /** The id of the artist whose name covers [offset] in [artistText]. */
    fun artistAt(offset: Int): String? {
        var at = 0
        artists.forEach { (name, id) ->
            if (offset in at until at + name.length) return id?.takeIf { it.isNotBlank() }
            at += name.length + 2
        }
        return null
    }
}

private val LabelLift = 44.dp
private val LabelEdgeGap = 12.dp

// Below this the arc is too steep to read comfortably, so long names scroll instead of running down it.
private val MinReadAngle = (12.0 * PI / 180).toFloat()

@Composable
private fun rememberCurvedLines(
    labels: ArcLabels,
    arc: ThumbArc,
    trackOuter: Float,
    screenWidth: Float,
    mirrored: Boolean,
    color: Color,
): List<CurvedLine> {
    val density = LocalDensity.current
    return remember(labels.title, labels.artists, arc.pivot, arc.radius, trackOuter, screenWidth, mirrored, color, density) {
        val px = { dp: Dp -> with(density) { dp.toPx() } }
        val time = CurvedLine.Spec(with(density) { 14.sp.toPx() }, bold = false, alpha = 0.85f, tabular = true)
        val artist = CurvedLine.Spec(with(density) { 16.sp.toPx() }, bold = false, alpha = 0.9f)
        val title = CurvedLine.Spec(with(density) { 22.sp.toPx() }, bold = true, alpha = 1f)
        val timeBase = trackOuter + px(10.dp)
        val artistBase = timeBase + px(26.dp)
        // with no artist the title takes the artist's place instead of floating a line away from the arc
        val titleBase = if (labels.artistText.isBlank()) artistBase + px(4.dp) else artistBase + px(30.dp)
        fun line(spec: CurvedLine.Spec, base: Float, text: String?, onTap: (Int) -> Unit, onHold: () -> Unit): CurvedLine {
            val lowest = if (text == null) 0f else MinReadAngle
            val from = maxOf(asin((px(LabelLift) / base).coerceIn(0f, 1f)), lowest)
            val outer = base + spec.size * 0.8f
            // Names run on over the cover up to the screen edge, so long ones get the whole curve.
            val to = arc.angleAtX(if (mirrored) px(LabelEdgeGap) else screenWidth - px(LabelEdgeGap), outer) ?: (PI / 3).toFloat()
            return CurvedLine(arc.pivot, base, from, maxOf(from, to), mirrored, spec, color, text, px(MarqueeGap), px(MarqueeSpeed), px(MarqueeFade), onTap, onHold)
        }
        listOfNotNull(
            line(time, timeBase, null, {}, {}),
            labels.artistText.takeIf { it.isNotBlank() }?.let {
                line(artist, artistBase, it, { offset -> labels.artistAt(offset)?.let(labels.onArtist) }, labels.onArtistHold)
            },
            line(title, titleBase, labels.title, { labels.onTitle() }, labels.onTitleHold),
        )
    }
}

/**
 * One line of text on a circle around the thumb's pivot, read left to right on either hand and
 * running on up to the screen edge. [text] null means it changes every frame and is passed to [draw].
 */
private val DEGREES = (180.0 / PI).toFloat()

private class CurvedLine(
    private val pivot: Offset,
    private val radius: Float,
    private val from: Float,
    private val to: Float,
    private val mirrored: Boolean,
    private val spec: Spec,
    color: Color,
    text: String?,
    private val gap: Float,
    private val speed: Float,
    private val fadeLength: Float,
    val onTap: (Int) -> Unit,
    val onHold: () -> Unit,
) {
    class Spec(val size: Float, val bold: Boolean, val alpha: Float, val tabular: Boolean = false)

    val isTime = text == null
    private val length = radius * (to - from)
    private val paint =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = spec.size
            typeface = if (spec.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (spec.tabular) fontFeatureSettings = "tnum"
            setShadowLayer(spec.size * 0.3f, 0f, spec.size * 0.05f, android.graphics.Color.argb(110, 0, 0, 0))
        }
    private val baseColor = color
    private val shown = text
    private val width = text?.let { paint.measureText(it) } ?: 0f
    val overflows = width > length
    private var offset = shown?.let { offsetFor(it) } ?: 0f

    // Letters hanging off the end of a path get squashed onto it, so the path runs on past both ends
    // and a wedge cuts the scrolling text off cleanly instead.
    private val runOn = spec.size * 2f

    // Clockwise on screen keeps the letters upright, so the left-handed line starts at the top instead of the foot.
    private val path =
        Path().apply {
            val oval = RectF(pivot.x - radius, pivot.y - radius, pivot.x + radius, pivot.y + radius)
            val degrees = 180f / PI.toFloat()
            val extra = runOn / radius
            val start = if (mirrored) 360f - (to + extra) * degrees else 180f + (from - extra) * degrees
            addArc(oval, start, (to - from + extra * 2) * degrees)
        }

    private val window =
        Path().apply {
            val reach = radius + spec.size * 2f
            moveTo(pivot.x, pivot.y)
            for (step in 0..24) {
                val point = pointAt(from + (to - from) * step / 24f, reach)
                lineTo(point.x, point.y)
            }
            close()
        }

    private fun offsetFor(text: String) = if (mirrored) 0f else (length - paint.measureText(text)).coerceAtLeast(0f)

    /** One loop of the name and the gap after it, for the marquee clock. */
    val loop get() = width + gap

    private fun scrolled(clockMs: Long): Float = marqueeScroll(clockMs, width + gap, speed)

    // Screen angles of the window, clockwise from 3 o'clock, with the path's start the smaller one.
    private val windowFrom = if (mirrored) 360f - to * DEGREES else 180f + from * DEGREES
    private val windowTo = if (mirrored) 360f - from * DEGREES else 180f + to * DEGREES
    private val fadeDegrees = (fadeLength / radius * DEGREES).coerceAtMost((windowTo - windowFrom) / 3f)
    private val layerBounds = RectF().also { window.computeBounds(it, true) }
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    // The edge the name scrolls out of fades only while it moves; the far edge, where the rest of it waits, always does.
    private fun mask(scroll: Float): SweepGradient {
        val leading = leadingFade(scroll, width + gap, fadeLength)
        val black = android.graphics.Color.BLACK
        val faded = android.graphics.Color.argb((255 * (1f - leading)).toInt(), 0, 0, 0)
        return SweepGradient(
            pivot.x, pivot.y,
            intArrayOf(faded, faded, black, black, 0, 0),
            floatArrayOf(0f, windowFrom / 360f, (windowFrom + fadeDegrees) / 360f, (windowTo - fadeDegrees) / 360f, windowTo / 360f, 1f),
        )
    }

    fun draw(scope: DrawScope, alpha: Float, live: String?, clockMs: Long, shader: android.graphics.Shader?) {
        val text = live ?: shown ?: return
        if (live != null) offset = offsetFor(text)
        if (overflows) offset = -scrolled(clockMs)
        paint.shader = shader
        val color = if (shader != null) Color.White else baseColor
        paint.color = color.copy(alpha = baseColor.alpha * spec.alpha * alpha).toArgb()
        scope.drawIntoCanvas {
            val canvas = it.nativeCanvas
            if (overflows) {
                canvas.save()
                canvas.clipPath(window)
                canvas.saveLayer(layerBounds, null)
            }
            canvas.drawTextOnPath(text, path, runOn + offset, 0f, paint)
            if (overflows) {
                canvas.drawTextOnPath(text, path, runOn + offset + width + gap, 0f, paint)
                maskPaint.shader = mask(-offset)
                canvas.drawPath(window, maskPaint)
                canvas.restore()
                canvas.restore()
            }
        }
    }

    private fun pointAt(angle: Float, r: Float) =
        Offset(pivot.x + (if (mirrored) 1f else -1f) * r * cos(angle), pivot.y - r * sin(angle))

    fun corners(): List<Offset> {
        val outer = radius + spec.size
        return listOf(pointAt(from, radius), pointAt(to, radius), pointAt(from, outer), pointAt(to, outer))
    }

    /** The character under [at], or null when the touch misses this line's text. */
    fun hit(at: Offset): Int? {
        val text = shown ?: return null
        val distance = hypot(at.x - pivot.x, at.y - pivot.y)
        if (distance < radius - spec.size * 0.4f || distance > radius + spec.size * 1.1f) return null
        val angle = atan2(pivot.y - at.y, (if (mirrored) 1f else -1f) * (at.x - pivot.x))
        var along = (if (mirrored) to - angle else angle - from) * radius - offset
        if (overflows) along %= width + gap
        if (along < -spec.size || along > width + spec.size) return null
        val widths = FloatArray(text.length).also { paint.getTextWidths(text, it) }
        var x = 0f
        widths.forEachIndexed { index, w ->
            x += w
            if (along < x) return index
        }
        return text.lastIndex
    }
}
