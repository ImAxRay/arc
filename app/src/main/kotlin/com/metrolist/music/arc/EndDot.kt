package com.metrolist.music.arc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private const val SQUEEZE_MS = 4_000L
private const val POP_BEFORE_END_MS = 350L
private const val POP_BEFORE_CROSSFADE_MS = 100L
private const val MAX_NATURAL_JUMP_MS = 1_500L
private const val DOT_RADIUS_DP = 2f
private const val CROSSFADE_ARM_MARGIN_MS = 300L

/**
 * How long before the end of the file the song really gives way. With crossfade the player swaps
 * to the next song this early, so the dot has to burst this early too. Mirrors the checks in
 * MusicService.scheduleCrossfade.
 */
fun songHandoverLeadMs(player: Player, crossfadeEnabled: Boolean, crossfadeSeconds: Float, keepAlbumsGapless: Boolean): Long {
    if (!crossfadeEnabled || crossfadeSeconds <= 0f) return POP_BEFORE_END_MS
    val lead = (crossfadeSeconds * 1000f).toLong()
    val duration = player.duration
    if (duration == C.TIME_UNSET || duration <= lead) return POP_BEFORE_END_MS
    if (!player.hasNextMediaItem() && player.repeatMode != Player.REPEAT_MODE_ONE) return POP_BEFORE_END_MS
    if (keepAlbumsGapless && player.hasNextMediaItem()) {
        val album = player.currentMediaItem?.mediaMetadata?.albumTitle
        val nextAlbum = player.getMediaItemAt(player.nextMediaItemIndex).mediaMetadata.albumTitle
        if (album != null && album == nextAlbum) return POP_BEFORE_END_MS
    }
    return lead / 2 + POP_BEFORE_CROSSFADE_MS
}

/**
 * The dot on the progress bar. It swells as the thumb closes in, gets squeezed, and bursts at the
 * moment the song hands over. Everything is drawn from draw-phase state, and the frame loop only
 * runs near that moment.
 */
@Stable
class EndDot internal constructor(private val density: Float, private val haptics: HapticFeedback) {
    private val sim = PopSim(density)
    private var frame by mutableLongStateOf(0L)

    var songId: String? by mutableStateOf(null)
    var positionMs: Long by mutableStateOf(0L)
    var durationMs: Long by mutableStateOf(0L)
    var crossfadeLeadMs: Long by mutableStateOf(POP_BEFORE_END_MS)
    var isPlaying: Boolean by mutableStateOf(false)

    // Crossfade only starts if the song plays into its starting point. Jumping past it plays the song
    // out to its real end, so the burst has to wait for that instead.
    private var crossfadeArmed = true
    private var lastPositionMs = -1L

    val remainingMs: Long
        get() {
            if (durationMs <= 0L) return Long.MAX_VALUE
            val lead = if (crossfadeArmed) crossfadeLeadMs else POP_BEFORE_END_MS
            return durationMs - lead - positionMs
        }

    private var seenSong: String? = null
    private var seenRemaining = Long.MAX_VALUE
    private var popped by mutableStateOf(false)
    private var tension = 0f
    private var grow by mutableStateOf(1f)
    private var growSpeed = 0f
    private var clock = 0f
    private var ticked = 0

    // Set while drawing, since only the slider knows where the bar is on screen.
    private var dotX = 0f
    private var barLeft = 0f
    private var barWidth = 0f
    private var barY = 0f
    private var floorY = 0f

    internal val needsFrames: Boolean
        get() =
            sim.isAlive ||
                songId != seenSong ||
                abs(1f - grow) > 0.002f ||
                abs(growSpeed) > 0.01f ||
                (popped && remainingMs > SQUEEZE_MS) ||
                (!popped && remainingMs <= SQUEEZE_MS && (isPlaying || remainingMs != seenRemaining))

    private fun trackJumps(seconds: Float) {
        val expected = lastPositionMs + if (isPlaying) (seconds * 1000f).toLong() else 0L
        if (lastPositionMs < 0L || abs(positionMs - expected) > MAX_NATURAL_JUMP_MS) {
            crossfadeArmed = positionMs < durationMs - crossfadeLeadMs - CROSSFADE_ARM_MARGIN_MS
        }
        lastPositionMs = positionMs
    }

    internal fun update(seconds: Float) {
        clock += seconds
        if (songId != seenSong) {
            crossfadeArmed = true
            lastPositionMs = -1L
        }
        trackJumps(seconds)
        if (songId != seenSong) {
            val firstSong = seenSong == null
            seenSong = songId
            seenRemaining = remainingMs
            popped = remainingMs <= 0L
            tension = 0f
            ticked = 0
            if (!firstSong) regrow()
        }
        if (popped && remainingMs > SQUEEZE_MS) {
            popped = false
            tension = 0f
            ticked = 0
            regrow()
        }
        if (!popped) {
            if (remainingMs <= 0L) {
                // Only a song playing into this moment bursts, not a jump past it.
                val natural = seenRemaining > 0L && seenRemaining - remainingMs < MAX_NATURAL_JUMP_MS
                popped = true
                if (natural) burst()
            } else if (remainingMs <= SQUEEZE_MS) {
                val target = 1f - remainingMs.toFloat() / SQUEEZE_MS
                tension += (target - tension) * (1f - exp(-seconds * 8f))
                val ticks = (squeeze(tension) * 5f).toInt()
                if (ticks > ticked) {
                    ticked = ticks
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            } else {
                tension = 0f
            }
        }
        seenRemaining = remainingMs

        growSpeed += (GROW_STIFFNESS * (1f - grow) - GROW_DAMPING * growSpeed) * seconds
        grow += growSpeed * seconds

        if (sim.isAlive) {
            val impacts = sim.step(seconds, barLeft, barWidth, barY, floorY)
            if (impacts > 0) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        frame++
    }

    private fun regrow() {
        grow = 0f
        growSpeed = 0f
    }

    private fun burst() {
        val seed = songId.hashCode()
        val kind = PopKind.forSeed(seed)
        sim.pop(kind, dotX, barY, Random(seed))
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    /** [ledgeLeft] is where the bar drops can land on starts; a curved bar only has a short flat ledge at its end. */
    fun draw(scope: DrawScope, end: Offset, color: Color, ledgeLeft: Float = 0f) {
        with(scope) {
            frame
            dotX = end.x
            barLeft = ledgeLeft
            barWidth = size.width
            barY = end.y
            floorY = end.y + size.height * 40f

            val base = DOT_RADIUS_DP * density
            if (!popped) {
                val swell = 1f + 1.2f * smooth(tension)
                val pressed = squeeze(tension)
                val shake = if (isPlaying) sin(clock * 62f) * 0.5f * density * pressed else 0f
                val radius = base * swell * grow
                // The thumb pushes from the left: that side goes flat while the dot bulges out to the right and up.
                val flatAt = dotX - radius * (1f - 0.55f * pressed) + shake
                val width = radius * (1f + 0.25f * pressed)
                val height = radius * (1f + 0.4f * pressed)
                val center = Offset(dotX + radius * 0.3f * pressed + shake, barY - 1.2f * density * pressed)
                clipRect(left = flatAt) {
                    drawOval(color, Offset(center.x - width, center.y - height), Size(width * 2f, height * 2f))
                }
            }
            for (puddle in sim.puddles) {
                val height = 1.8f * density
                drawOval(
                    color.copy(alpha = puddle.alpha),
                    Offset(puddle.x - puddle.width / 2f, puddle.y - height),
                    Size(puddle.width, height * 2f),
                )
            }
            for (drop in sim.droplets) {
                for (age in 0 until drop.trailCount) {
                    val fade = 1f - age.toFloat() / Droplet.TRAIL_POINTS
                    drop.trailPoint(age) { x, y ->
                        drawCircle(color.copy(alpha = fade * 0.5f), drop.radius * fade, Offset(x, y))
                    }
                }
                drawDrop(drop, color)
            }
        }
    }

    private fun DrawScope.drawDrop(drop: Droplet, color: Color) {
        val speed = Offset(drop.vx, drop.vy).getDistance()
        val stretch = (speed / (900f * density)).coerceAtMost(1f) * 0.45f
        val length = drop.radius * (1f + stretch) * (1f - 0.3f * drop.squash)
        val thickness = drop.radius * (1f - stretch * 0.4f) * (1f + 0.5f * drop.squash)
        val center = Offset(drop.x, drop.y)
        // A squashed drop flattens against the bar, so it stops following its direction of travel.
        val degrees = if (drop.squash > 0.2f || speed == 0f) 0f else Math.toDegrees(atan2(drop.vy, drop.vx).toDouble()).toFloat()
        rotate(degrees, center) {
            val width = if (degrees == 0f) thickness else length
            val height = if (degrees == 0f) length.coerceAtMost(thickness) * (1f - 0.3f * drop.squash) else thickness
            drawOval(color, Offset(center.x - width, center.y - height), Size(width * 2f, height * 2f))
        }
    }

    private companion object {
        const val GROW_STIFFNESS = 320f
        const val GROW_DAMPING = 13f

        fun smooth(value: Float): Float = value * value * (3f - 2f * value)

        // The last stretch before the burst, when the dot is being pressed instead of just growing.
        fun squeeze(tension: Float): Float = ((tension - 0.6f) / 0.4f).coerceIn(0f, 1f)
    }
}

/** [crossfadeLeadMs] is how early the next song takes over when crossfade runs. */
@Composable
fun rememberEndDot(songId: String?, positionMs: Long, durationMs: Long, crossfadeLeadMs: Long, isPlaying: Boolean): EndDot {
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    val dot = remember { EndDot(density, haptics) }
    SideEffect {
        dot.songId = songId
        dot.positionMs = positionMs
        dot.durationMs = durationMs
        dot.crossfadeLeadMs = crossfadeLeadMs
        dot.isPlaying = isPlaying
    }
    LaunchedEffect(dot) {
        while (true) {
            snapshotFlow { dot.needsFrames }.first { it }
            var last = withFrameNanos { it }
            while (dot.needsFrames) {
                val now = withFrameNanos { it }
                dot.update(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
            }
        }
    }
    return dot
}
