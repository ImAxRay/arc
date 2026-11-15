package com.metrolist.music.arc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.delay

/** What other screens need to know about the player to pick its line up when it closes. */
object ArcLineSignals {
    var playerExpanded by mutableStateOf(false)
}

val ArcHomeLineKey = booleanPreferencesKey("arcHomeLine")

// Progress steps about a pixel at a time, so the line creeps instead of jumping, without redrawing every frame.
private const val MIN_TICK_MS = 33L
private const val MAX_TICK_MS = 500L
private const val RUN_IN_MS = 380
private const val SHEET_SETTLE_MS = 60L

// Room the line's path keeps from the edges; the strokes themselves are thinner.
private val LineWidth = 14.dp
private val TrackWidth = 2.dp
private val PlayedWidth = 5.dp
// The played part ends in a dot, like the logo's line.
private val HeadRadius = 6.dp
private val CornerRadius = 36.dp
// Like the player's line, the song starts a little in from the edge the line enters at.
private val SongInset = 22.dp

/**
 * Arc's line on Home, drawn like the player's and running edge to edge the same way: in from the
 * left under the mini player, round a corner and straight up between the tiles to the gap at
 * [uprightFromEnd] between two of the top bar's icons, round again and
 * out to the right under the status bar. It sits behind everything, so it shows in the gaps. The
 * solid part is how far the song has played; it runs in when the player closes.
 */
@Composable
fun Modifier.homeLine(connection: PlayerConnection, track: Color, line: Color, uprightFromEnd: Dp): Modifier {
    val isPlaying by connection.isPlaying.collectAsState()
    val song by connection.mediaMetadata.collectAsState()
    // On a slow cold start Home shows before the service has its player.
    val ready by connection.service.isPlayerReady.collectAsState()
    var progress by remember { mutableFloatStateOf(0f) }
    val songLengthPx = remember { floatArrayOf(0f) }
    LaunchedEffect(isPlaying, song?.id, ready) {
        if (!ready) return@LaunchedEffect
        val player = connection.player
        while (true) {
            val duration = player.duration
            progress = if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            if (!isPlaying) break
            val perPixel = if (duration > 0 && songLengthPx[0] > 0f) (duration / songLengthPx[0]).toLong() else MAX_TICK_MS
            delay(perPixel.coerceIn(MIN_TICK_MS, MAX_TICK_MS))
            // Pauses with the frame clock while Arc is in the background.
            withFrameMillis { }
        }
    }
    val runIn = remember { Animatable(0f) }
    val expanded = ArcLineSignals.playerExpanded
    LaunchedEffect(expanded) {
        if (expanded) return@LaunchedEffect
        runIn.snapTo(0f)
        delay(SHEET_SETTLE_MS)
        runIn.animateTo(1f, tween(RUN_IN_MS, easing = FastOutSlowInEasing))
    }
    val hasSong = song != null
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val bottom = WindowInsets.navigationBars.getBottom(density).toFloat()

    return drawWithCache {
        val width = LineWidth.toPx()
        val radius = CornerRadius.toPx()
        val x = size.width - uprightFromEnd.toPx()
        // The top corner is a quarter circle around the icon right of the gap, so the line leaves the screen in the
        // strip between the status bar and the icons.
        val topRadius = (CapsuleSize / 2 + CapsuleGap / 2).toPx()
        val topY = top + (TopBarTopPadding + CapsuleSize / 2).toPx() - topRadius
        // In the gap between the mini player and the nav icons below it.
        val bottomY = size.height - bottom - (CapsuleNavHeight + 6.dp).toPx()
        val path =
            Path().apply {
                moveTo(-width, bottomY)
                lineTo(x - radius, bottomY)
                arcTo(Rect(Offset(x - radius * 2, bottomY - radius * 2), Size(radius * 2, radius * 2)), 90f, -90f, false)
                lineTo(x, topY + topRadius)
                arcTo(Rect(Offset(x, topY), Size(topRadius * 2, topRadius * 2)), 180f, 90f, false)
                lineTo(size.width + width, topY)
            }
        val measure = PathMeasure().apply { setPath(path, false) }
        val length = measure.length
        val songStart = width + SongInset.toPx()
        val songEnd = length - songStart
        songLengthPx[0] = songEnd - songStart
        val trackStroke = Stroke(TrackWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val playedStroke = Stroke(PlayedWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val head = HeadRadius.toPx()
        val trackPath = Path()
        val donePath = Path()
        onDrawBehind {
            if (!hasSong) return@onDrawBehind
            val reach = length * runIn.value
            trackPath.reset()
            measure.getSegment(0f, reach, trackPath, true)
            drawPath(trackPath, track, style = trackStroke)
            val played = minOf(songStart + (songEnd - songStart) * progress, reach)
            donePath.reset()
            measure.getSegment(0f, played, donePath, true)
            drawPath(donePath, line, style = playedStroke)
            if (played > 0f) drawCircle(line, head, measure.getPosition(played))
        }
    }
}
