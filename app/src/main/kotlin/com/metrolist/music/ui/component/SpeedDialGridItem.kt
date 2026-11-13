package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.R
import com.metrolist.music.arc.MarqueeText
import com.metrolist.music.arc.dialRim
import com.metrolist.music.arc.tapBounce
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.ui.utils.resize

@Composable
fun SpeedDialGridItem(
    item: YTItem,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) {
    if (item is ArtistItem) {
        ArtistDialTile(item, isPinned, isActive, isPlaying, modifier)
        return
    }
    Box(
        modifier = modifier
            .tapBounce()
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .dialRim(RoundedCornerShape(ThumbnailCornerRadius))
    ) {
        // Thumbnail
        ItemThumbnail(
            thumbnailUrl = item.thumbnail?.resize(200, 200),
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = Modifier.fillMaxSize()
        )

        MarqueeText(
            text = item.title,
            style = DialNameStyle,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )

        // Pinned Icon
        if (isPinned) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(16.dp)
            )
        }
    }
}

// White and bold with a dark halo instead of a scrim, so square tiles' names read on light photos too.
private val DialNameStyle =
    androidx.compose.ui.text.TextStyle(
        fontSize = 15.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = Color.White,
        shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.78f), androidx.compose.ui.geometry.Offset(0f, 1f), blurRadius = 14f),
    )

// The photo shrinks to leave room for the name, so the tile stays the same square as its neighbours.
private const val PHOTO_SHARE = 0.78f

/** Artists are their round photo with the plain name underneath. */
@Composable
private fun ArtistDialTile(
    item: ArtistItem,
    isPinned: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .tapBounce()
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize(),
        ) {
            ItemThumbnail(
                thumbnailUrl = item.thumbnail?.resize(200, 200),
                isActive = isActive,
                isPlaying = isPlaying,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(PHOTO_SHARE).aspectRatio(1f).clip(CircleShape).dialRim(CircleShape),
            )
            MarqueeText(
                text = item.title,
                style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            )
        }
        if (isPinned) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
            )
        }
    }
}
