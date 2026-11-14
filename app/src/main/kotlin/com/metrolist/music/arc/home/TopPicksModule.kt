package com.metrolist.music.arc.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.R
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.ui.utils.resize

@Composable
internal fun topPicksAvailable(env: HomeEnv): Boolean {
    val forgotten by env.viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val discover by env.viewModel.dailyDiscover.collectAsStateWithLifecycle()
    return !forgotten.isNullOrEmpty() || discover.orEmpty().any { it.recommendation is SongItem }
}

/** Two picks side by side: a forgotten favourite and one for you. */
@Composable
fun TopPicksModule(env: HomeEnv) {
    val forgotten by env.viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val discover by env.viewModel.dailyDiscover.collectAsStateWithLifecycle()

    val rediscover = forgotten.orEmpty().getOrNull(0)
    val forYou = discover.orEmpty().firstNotNullOfOrNull { it.recommendation as? SongItem }
    val secondForgotten = forgotten.orEmpty().getOrNull(1)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height((env.width - 40.dp) / 2),
    ) {
        rediscover?.let { song ->
            PickCard(
                label = stringResource(R.string.arc_home_rediscover),
                title = song.title,
                subtitle = null,
                thumbnail = song.thumbnailUrl,
                modifier = Modifier.weight(1f).fillMaxHeight().homeTile(onClick = { env.playSong(song) }, onLongClick = { env.showSongMenu(song) }),
            )
        }
        if (forYou != null) {
            PickCard(
                label = stringResource(R.string.arc_home_for_you),
                title = forYou.title,
                subtitle = null,
                thumbnail = forYou.thumbnail,
                modifier = Modifier.weight(1f).fillMaxHeight().homeTile(onClick = { env.playYtSong(forYou) }, onLongClick = { env.showYtItemMenu(forYou) }),
            )
        } else if (secondForgotten != null) {
            PickCard(
                label = stringResource(R.string.arc_home_rediscover),
                title = secondForgotten.title,
                subtitle = null,
                thumbnail = secondForgotten.thumbnailUrl,
                modifier = Modifier.weight(1f).fillMaxHeight().homeTile(onClick = { env.playSong(secondForgotten) }, onLongClick = { env.showSongMenu(secondForgotten) }),
            )
        }
    }
}

@Composable
internal fun PickCard(
    label: String,
    title: String,
    subtitle: String?,
    thumbnail: String?,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    val shape = RoundedCornerShape(ThumbnailCornerRadius)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AsyncImage(
            model = thumbnail?.resize(if (big) 720 else 360, if (big) 720 else 360),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // a dark fade under the words only, drawn once
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)),
                    topLeft = Offset.Zero,
                    size = size,
                )
            },
        )
        Column(Modifier.align(Alignment.BottomStart).padding(if (big) 12.dp else 8.dp)) {
            Text(label.uppercase(), fontSize = 9.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            Text(
                title,
                fontSize = if (big) 18.sp else 12.sp,
                lineHeight = if (big) 22.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
