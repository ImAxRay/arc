package com.metrolist.music.arc

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import coil3.request.transformations
import com.metrolist.music.ui.utils.resize

/** One big thin number on a list's header, with its small caps name under it ("128" / "SONGS"). */
class HeaderStat(val value: String, val label: String)

/**
 * The top of a song list in Arc's VHS-box style: a small caps kind, the name beside a stack of covers, a row of big
 * thin numbers, a few facts, and Shuffle / Play / more at thumb height. Used by the auto playlists first.
 */
@Composable
fun ArcListHeader(
    kind: String,
    title: String,
    covers: List<String?>,
    stats: List<HeaderStat>,
    facts: List<Pair<String, String>>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                CapsLabel(kind)
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 34.sp,
                )
            }
            CoverStack(covers.take(3))
        }

        Rule()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            stats.forEach { stat ->
                Column {
                    Text(stat.value, fontSize = 44.sp, fontWeight = FontWeight.Thin, lineHeight = 46.sp, maxLines = 1)
                    CapsLabel(stat.label)
                }
            }
        }
        if (facts.isNotEmpty()) {
            Rule()
            facts.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    CapsLabel(label, Modifier.width(96.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(R.drawable.shuffle, stringResource(R.string.shuffle), filled = false, onClick = onShuffle, modifier = Modifier.weight(1f))
            Pill(R.drawable.play, stringResource(R.string.play), filled = true, onClick = onPlay, modifier = Modifier.weight(1.4f))
            Surface(onClick = onMore, shape = CircleShape, color = colors.surfaceContainerHigh, modifier = Modifier.size(52.dp).tapBounce()) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun CoverStack(covers: List<String?>) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(width = 128.dp, height = 112.dp)) {
        covers.reversed().forEachIndexed { reversedIndex, cover ->
            val index = covers.size - 1 - reversedIndex
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(cover?.resize(360, 360))
                    .transformations(LetterboxCrop)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer {
                        translationX = -index * 10.dp.toPx()
                        translationY = -index * 4.dp.toPx()
                        rotationZ = index * -5f
                        alpha = 1f - index * 0.18f
                    }
                    .size(104.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceVariant),
            )
        }
    }
}

@Composable
private fun Pill(icon: Int, label: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = if (filled) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (filled) colors.onPrimary else colors.onSurface,
        modifier = modifier.height(52.dp).tapBounce(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Rule() {
    Box(
        Modifier
            .padding(vertical = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    )
}

@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}
