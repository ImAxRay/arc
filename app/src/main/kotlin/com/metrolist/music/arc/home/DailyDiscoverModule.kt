package com.metrolist.music.arc.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalNavController
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.joinToArtistString

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DailyDiscoverCard(
    dailyDiscover: com.metrolist.music.viewmodels.DailyDiscoverItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val playCount by database.getLifetimePlayCount(dailyDiscover.recommendation.id).collectAsStateWithLifecycle(initialValue = 0)
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val artistNameAliases = LocalArtistNameAliases.current

    val song = dailyDiscover.recommendation as? SongItem
    val playsString = stringResource(R.string.plays)

    Card(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (song != null) {
                            menuState.show {
                                YouTubeSongMenu(
                                    song = song,
                                    onDismiss = { menuState.dismiss() },
                                )
                            }
                        }
                    },
                ),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        shape = RoundedCornerShape(28.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(dailyDiscover.recommendation.thumbnail?.resize(1080, 1080))
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize(),
            )

            if (maxWidth > 200.dp) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Black.copy(alpha = 0.3f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.6f),
                                                Color.Black.copy(alpha = 0.9f),
                                            ),
                                    ),
                            ),
                )

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = dailyDiscover.recommendation.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Text(
                            text =
                                buildString {
                                    append(
                                        (dailyDiscover.recommendation as? SongItem)
                                            ?.artists
                                            ?.joinToArtistString(" ${stringResource(R.string.and)} ") {
                                                ArtistNameAliases.resolve(artistNameAliases, it.id, it.name)
                                            }.orEmpty(),
                                    )
                                    if (playCount > 0) {
                                        append(" | $playCount $playsString")
                                    }
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }

                    val messages =
                        listOf(
                            R.string.daily_discover_sounds_like,
                            R.string.daily_discover_because_you_listen_to,
                            R.string.daily_discover_similar_to,
                            R.string.daily_discover_based_on,
                            R.string.daily_discover_for_fans_of,
                        )
                    val messageRes =
                        remember(dailyDiscover.seed.id) {
                            messages[kotlin.math.abs(dailyDiscover.seed.id.hashCode()) % messages.size]
                        }

                    Text(
                        text =
                            stringResource(
                                messageRes,
                                "${dailyDiscover.seed.title} • ${dailyDiscover.seed.artists.joinToArtistString(" ${stringResource(R.string.and)} ") {
                                    ArtistNameAliases.resolve(artistNameAliases, it.id, it.name)
                                }}",
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyDiscoverModule(env: HomeEnv) {
    val discover by env.viewModel.dailyDiscover.collectAsStateWithLifecycle()
    val discoverList = discover?.takeIf { it.isNotEmpty() } ?: return
    val title = stringResource(R.string.your_daily_discover)
    Column {
        ModuleHeader(
            title = title,
            onPlay = {
                env.playAll(title, discoverList.mapNotNull { (it.recommendation as? SongItem)?.toMediaMetadata()?.toMediaItem() })
            },
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(340.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val carouselState = rememberCarouselState { discoverList.size }
            HorizontalMultiBrowseCarousel(
                state = carouselState,
                preferredItemWidth = 320.dp,
                itemSpacing = 16.dp,
                modifier = Modifier.fillMaxWidth().height(320.dp),
            ) { i ->
                val item = discoverList[i]
                DailyDiscoverCard(
                    dailyDiscover = item,
                    onClick = { (item.recommendation as? SongItem)?.let(env::playYtSong) },
                    modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge),
                )
            }
        }
    }
}
