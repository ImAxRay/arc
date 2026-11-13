package com.metrolist.music.arc

import androidx.compose.ui.draw.clipToBounds
import kotlin.math.roundToInt
import com.metrolist.music.constants.AppBarHeight
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.R
import com.metrolist.music.ui.component.YouTubeGridItem
import kotlinx.coroutines.CoroutineScope

private const val ALBUM_GRID_COLUMNS = 3
private val StatPattern = Regex("""^([\d.,]+\s*\p{L}{0,3}\.?)\s+(.+)$""")

/** Splits "12.3M monthly listeners" into the number and its label, so the number can be the big part. */
internal fun splitStat(text: String): Pair<String, String> {
    val match = StatPattern.find(text.trim()) ?: return text to ""
    return match.groupValues[1] to match.groupValues[2]
}

@Composable
fun ArtistStats(stats: List<String>, modifier: Modifier = Modifier) {
    if (stats.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = modifier) {
        stats.forEach { stat ->
            val (number, label) = splitStat(stat)
            Column {
                Text(number, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                if (label.isNotEmpty()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Subscribe gets the words, the play actions are icons, all one height and evenly spaced. */
@Composable
fun ArtistActions(
    subscribed: Boolean,
    onSubscribe: () -> Unit,
    onRadio: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
    online: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        ArtistAction(
            label = stringResource(if (subscribed) R.string.subscribed else R.string.subscribe),
            icon = null,
            filled = false,
            onClick = onSubscribe,
            modifier = Modifier.weight(2f),
        )
        // Radio and shuffle hold their places while the artist page loads, so the row doesn't jump when they arrive.
        if (online) {
            ArtistAction(null, R.drawable.radio, false, onRadio ?: {}, Modifier.weight(1f), enabled = onRadio != null)
            ArtistAction(null, R.drawable.shuffle, false, onShuffle ?: {}, Modifier.weight(1f), enabled = onShuffle != null)
        }
        onPlay?.let { ArtistAction(null, R.drawable.play, true, it, Modifier.weight(1f)) }
    }
}

@Composable
private fun ArtistAction(label: String?, @DrawableRes icon: Int?, filled: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    val shown by animateFloatAsState(if (enabled) 1f else 0.35f, label = "artistAction")
    val shape = RoundedCornerShape(50)
    val colors = MaterialTheme.colorScheme
    val content = if (filled) colors.onPrimary else colors.onSurface
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .graphicsLayer { alpha = shown }
                .then(if (enabled) Modifier.tapBounce() else Modifier)
                .height(44.dp)
                .clip(shape)
                .background(if (filled) colors.primary else Color.Transparent)
                .border(1.dp, if (filled) Color.Transparent else colors.outlineVariant, shape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(20.dp))
        }
        if (label != null) {
            if (icon != null) Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A short album section opened out into a grid, for artists whose albums have no full list to go to. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistAlbumGrid(
    albums: List<AlbumItem>,
    activeAlbumId: String?,
    isPlaying: Boolean,
    scope: CoroutineScope,
    onClick: (AlbumItem) -> Unit,
    onLongClick: (AlbumItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Plain rows, because FlowRow's weights ask its children for intrinsic sizes and image loaders can't answer that.
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        albums.chunked(ALBUM_GRID_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { album ->
                    Box(Modifier.weight(1f)) {
                        YouTubeGridItem(
                            item = album,
                            isActive = album.id == activeAlbumId,
                            isPlaying = isPlaying,
                            coroutineScope = scope,
                            fillMaxWidth = true,
                            modifier = Modifier.combinedClickable(onClick = { onClick(album) }, onLongClick = { onLongClick(album) }),
                        )
                    }
                }
                repeat(ALBUM_GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The artist's name, stats and buttons, kept at the top once the photo scrolls away. As it nears
 * the top the name slides up beside the back arrow and shrinks, the stats fold away, and a backdrop
 * fades in behind, so the page needs no separate title bar. Collapse follows its own position on
 * screen, so it tracks the finger with no animation lag.
 */
@Composable
fun ArtistStickyHeader(
    name: String,
    stats: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density).toFloat()
    val barHeight = with(density) { AppBarHeight.toPx() }
    val range = statusBar + barHeight
    var top by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val collapse = { ((range - top) / range).coerceIn(0f, 1f) }
    val background = MaterialTheme.colorScheme.background
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .onGloballyPositioned { top = it.positionInRoot().y }
                .drawBehind {
                    val t = collapse()
                    if (t > 0f) {
                        drawRect(
                            Brush.verticalGradient(
                                0.8f to background.copy(alpha = t),
                                1f to background.copy(alpha = 0f),
                            ),
                        )
                    }
                }.padding(bottom = 16.dp),
    ) {
        // Clears the status bar once it's pinned to the top.
        Spacer(Modifier.layout { m, c ->
            val p = m.measure(c)
            layout(p.width, (statusBar * collapse()).roundToInt()) { p.place(0, 0) }
        })
        Text(
            text = name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .layout { m, c ->
                        val t = collapse()
                        val start = lerp(16.dp, 60.dp, t).roundToPx()
                        val end = lerp(16.dp, 56.dp, t).roundToPx()
                        // The text shrinks to bar size, so its box is measured narrower to keep the ellipsis inside the bar.
                        val scale = lerp(1f, NAME_SCALE, t)
                        val p = m.measure(c.copy(minWidth = 0, maxWidth = ((c.maxWidth - start - end) / scale).roundToInt().coerceAtLeast(0)))
                        val shown = (p.height * scale).roundToInt()
                        val rowHeight = lerp(p.height + 12.dp.roundToPx(), barHeight.roundToInt(), t)
                        layout(c.maxWidth, rowHeight) {
                            p.placeWithLayer(start, ((rowHeight - shown) / 2f * t).roundToInt()) {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                        }
                    },
        )
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .clipToBounds()
                .layout { m, c ->
                    val t = collapse()
                    val p = m.measure(c)
                    layout(p.width, (p.height * (1f - t)).roundToInt()) { p.placeWithLayer(0, 0) { alpha = 1f - t } }
                },
        ) { stats() }
        Box(Modifier.padding(horizontal = 16.dp)) { actions() }
    }
}

private const val NAME_SCALE = 0.66f
