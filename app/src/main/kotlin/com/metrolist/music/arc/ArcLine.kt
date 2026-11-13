package com.metrolist.music.arc

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

internal const val SONG_POINTS = 160
private const val LEAD_POINTS = 24

private val LineLift = 34.dp
private val BendRadius = 24.dp
private val AroundPlay = 138.dp
private val SongStart = 22.dp
private val TitleGap = 8.dp

/**
 * Arc's line, edge to edge like tape between two reels: [lead] comes in from off the screen,
 * [song] is the stretch that stands for the song, and [tail] runs off the other side.
 * Each part always has the same number of points, so one shape can morph into another.
 */
internal class LineShape(val lead: List<Offset>, val song: List<Offset>, val tail: List<Offset>) {
    /** The same line walked from its other end. Left-handed, the mirrored line still has to fill left to right. */
    fun reversed() = LineShape(tail.asReversed(), song.asReversed(), lead.asReversed())
}

/** The lyrics shape, plus the corner it leaves free for the title. */
internal class LyricsLine(val shape: LineShape, val titleLeft: Float, val titleRight: Float, val titleBottom: Float)

// Angles here are on screen: 0 points right and they grow clockwise, since y grows downwards.
private sealed interface Piece {
    val length: Float

    fun at(t: Float): Offset
}

private class Straight(val from: Offset, val to: Offset) : Piece {
    override val length = (to - from).getDistance()

    override fun at(t: Float) = lerp(from, to, t)
}

private class Turn(val centre: Offset, val radius: Float, val from: Float, val to: Float) : Piece {
    override val length = radius * abs(to - from)

    override fun at(t: Float): Offset {
        val angle = from + (to - from) * t
        return Offset(centre.x + radius * cos(angle), centre.y + radius * sin(angle))
    }
}

private fun sample(pieces: List<Piece>, count: Int, width: Float, mirrored: Boolean): List<Offset> {
    val total = pieces.sumOf { it.length.toDouble() }.toFloat()
    return List(count) { index ->
        var along = total * index / (count - 1)
        var point = pieces.last().at(1f)
        for (piece in pieces) {
            if (along <= piece.length || piece === pieces.last()) {
                point = piece.at(if (piece.length > 0f) (along / piece.length).coerceIn(0f, 1f) else 1f)
                break
            }
            along -= piece.length
        }
        if (mirrored) Offset(width - point.x, point.y) else point
    }
}

private fun Density.px(dp: Dp) = dp.toPx()

/** A line at [lineY] bending up into the circle around [centre]. Returns the bend's centre and the angle where they meet. */
private fun bendInto(centre: Offset, radius: Float, lineY: Float, bend: Float): Pair<Offset, Float> {
    val lift = centre.y - lineY
    val bendCentre = Offset(centre.x - sqrt((radius + bend) * (radius + bend) - (lift + bend) * (lift + bend)), lineY - bend)
    return bendCentre to atan2(centre.y - bendCentre.y, centre.x - bendCentre.x)
}

/** The normal player: the thumb arc itself, carried on round its circle down to [bottom] and off the screen at the top. */
internal fun thumbLine(width: Float, bottom: Float, arc: ThumbArc, radius: Float, startAngle: Float, endAngle: Float, lineWidth: Float, mirrored: Boolean): LineShape {
    val below = minOf(-asin(((bottom - arc.pivot.y) / radius).coerceIn(-1f, 1f)), startAngle)
    val exit = arc.angleAtX(if (mirrored) -lineWidth else width + lineWidth, radius) ?: (PI / 2).toFloat()
    fun along(from: Float, to: Float, count: Int) = List(count) { arc.point(from + (to - from) * it / (count - 1), radius) }
    val line =
        LineShape(
            lead = along(below, startAngle, LEAD_POINTS),
            song = along(startAngle, endAngle, SONG_POINTS),
            tail = along(endAngle, maxOf(exit, endAngle), LEAD_POINTS),
        )
    return if (mirrored) line.reversed() else line
}

/** Lyrics mode: along the bottom row, up and over the buttons around [play], then straight out at the side. */
internal fun lyricsLine(width: Float, play: Offset, lineWidth: Float, mirrored: Boolean, density: Density): LyricsLine =
    with(density) {
        val centre = if (mirrored) Offset(width - play.x, play.y) else play
        val around = px(AroundPlay)
        val bend = px(BendRadius)
        val lineY = centre.y - px(LineLift)
        val (bendCentre, meet) = bendInto(centre, around, lineY, bend)
        val start = px(SongStart).coerceAtMost(bendCentre.x)
        val top = 1.5f * PI.toFloat()
        val topY = centre.y - around
        val shape =
            LineShape(
                lead = sample(listOf(Straight(Offset(-lineWidth, lineY), Offset(start, lineY))), LEAD_POINTS, width, mirrored),
                song =
                    sample(
                        listOf(Straight(Offset(start, lineY), Offset(bendCentre.x, lineY)), Turn(bendCentre, bend, PI.toFloat() / 2, meet), Turn(centre, around, meet + PI.toFloat(), top)),
                        SONG_POINTS, width, mirrored,
                    ),
                tail = sample(listOf(Straight(Offset(centre.x, topY), Offset(width + lineWidth, topY))), LEAD_POINTS, width, mirrored),
            ).let { if (mirrored) it.reversed() else it }
        val right = bendCentre.x - px(TitleGap)
        LyricsLine(
            shape = shape,
            titleLeft = if (mirrored) width - right else start,
            titleRight = if (mirrored) width - start else right,
            titleBottom = lineY - lineWidth / 2 - px(TitleGap),
        )
    }

/** Buffers reused from frame to frame. */
internal class LinePaths {
    val rest = Path()
    val done = Path()
    val xs = FloatArray(LEAD_POINTS * 2 + SONG_POINTS)
    val ys = FloatArray(xs.size)
    val lengths = FloatArray(xs.size)
}

/**
 * Draws the whole line [shape] of the way from [from] to [to]. Everything behind the song's
 * [progress] is filled, everything ahead isn't. [fillIn] runs from 0 to 1 after a song change,
 * so the fill sweeps in from the screen edge up to where the song is. Returns where the song ends.
 */
internal fun DrawScope.drawArcLine(
    from: LineShape,
    to: LineShape,
    shape: Float,
    progress: Float,
    fillIn: Float,
    rest: Brush,
    fill: Brush,
    width: Float,
    paths: LinePaths,
): Offset {
    val xs = paths.xs
    val ys = paths.ys
    val lengths = paths.lengths
    var count = 0
    for ((a, b) in listOf(from.lead to to.lead, from.song to to.song, from.tail to to.tail)) {
        for (index in a.indices) {
            val point = lerp(a[index], b[index], shape)
            xs[count] = point.x
            ys[count] = point.y
            lengths[count] = if (count == 0) 0f else lengths[count - 1] + hypot(point.x - xs[count - 1], point.y - ys[count - 1])
            count++
        }
    }
    val songStart = from.lead.size
    val songEnd = songStart + from.song.size - 1
    val reached = lengths[songStart] + progress.coerceIn(0f, 1f) * (lengths[songEnd] - lengths[songStart])
    val filled = reached * fillIn.coerceIn(0f, 1f)

    val stroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    paths.rest.reset()
    paths.rest.moveTo(xs[0], ys[0])
    for (index in 1 until count) paths.rest.lineTo(xs[index], ys[index])
    drawPath(paths.rest, rest, style = stroke)

    paths.done.reset()
    paths.done.moveTo(xs[0], ys[0])
    for (index in 1 until count) {
        if (lengths[index] <= filled) {
            paths.done.lineTo(xs[index], ys[index])
            continue
        }
        val step = lengths[index] - lengths[index - 1]
        val t = if (step > 0f) (filled - lengths[index - 1]) / step else 0f
        paths.done.lineTo(xs[index - 1] + (xs[index] - xs[index - 1]) * t, ys[index - 1] + (ys[index] - ys[index - 1]) * t)
        break
    }
    if (filled > 0f) drawPath(paths.done, fill, style = stroke)
    return Offset(xs[songEnd], ys[songEnd])
}

/** The index of the point in [points] closest to [at], with its distance. */
internal fun nearestPoint(points: List<Offset>, at: Offset): Pair<Int, Float> {
    var best = 0
    var bestDistance = Float.MAX_VALUE
    points.forEachIndexed { index, point ->
        val distance = (point - at).getDistance()
        if (distance < bestDistance) {
            best = index
            bestDistance = distance
        }
    }
    return best to bestDistance
}

/** Time, title and artist in the corner that the lyrics line leaves free. */
@Composable
internal fun ArcLyricsTitle(labels: ArcLabels, positionMs: () -> Long, durationMs: () -> Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        ArcTimeLine(positionMs, durationMs, color, style = MaterialTheme.typography.labelLarge)
        TappableLine(
            text = labels.title,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = color,
            onTap = { labels.onTitle() },
            onHold = labels.onTitleHold,
        )
        TappableLine(
            text = labels.artistText,
            style = TextStyle(fontSize = 16.sp),
            color = color.copy(alpha = 0.9f),
            onTap = { offset -> labels.artistAt(offset)?.let(labels.onArtist) },
            onHold = labels.onArtistHold,
        )
    }
}

@Composable
private fun TappableLine(text: String, style: TextStyle, color: Color, onTap: (Int) -> Unit, onHold: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var shift by remember { mutableStateOf({ 0f }) }
    val currentTap by rememberUpdatedState(onTap)
    val currentHold by rememberUpdatedState(onHold)
    MarqueeText(
        text = text,
        style = style.copy(color = color),
        onLayout = { result, scrolled ->
            layout = result
            shift = scrolled
        },
        modifier =
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = { at ->
                        val result = layout ?: return@detectTapGestures currentTap(0)
                        val loop = result.size.width + MarqueeGap.toPx()
                        val x = (at.x + shift()) % loop
                        currentTap(result.getOffsetForPosition(at.copy(x = x)))
                    },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentHold()
                    },
                )
            },
    )
}
