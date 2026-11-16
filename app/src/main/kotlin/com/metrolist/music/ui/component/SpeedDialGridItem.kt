package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.R
import com.metrolist.music.arc.MarqueeText
import com.metrolist.music.constants.DialCornerRadius
import com.metrolist.music.ui.utils.resize

@Composable
fun SpeedDialGridItem(
    item: YTItem,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    thumbnailSize: Int = 200,
) {
    if (item is ArtistItem) {
        ArtistDialTile(item, isPinned, isActive, isPlaying, modifier, thumbnailSize)
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(DialCornerRadius))
            // a flat square the cover fades in over, so a tile never pops in from nothing
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        ItemThumbnail(
            thumbnailUrl = item.thumbnail?.resize(thumbnailSize, thumbnailSize),
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(DialCornerRadius),
            modifier = Modifier.fillMaxSize()
        )
        NameFade()
        MarqueeText(
            text = item.title,
            style = DialNameStyle,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        if (isPinned) PinMark(Modifier.align(Alignment.TopEnd))
    }
}

// A soft fade under the name plus a slight shadow on the letters, so names read on light covers too.
private val DialNameStyle =
    androidx.compose.ui.text.TextStyle(
        fontSize = 15.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = Color.White,
        shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(0f, 1f), blurRadius = 8f),
    )

@Composable
private fun BoxScope.NameFade() {
    Spacer(
        Modifier
            .matchParentSize()
            .drawBehind { drawRect(Brush.verticalGradient(0.68f to Color.Transparent, 0.85f to Color.Black.copy(alpha = 0.12f), 1f to Color.Black.copy(alpha = 0.32f))) },
    )
}

@Composable
private fun PinMark(modifier: Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_push_pin),
        contentDescription = null,
        tint = Color.White,
        modifier = modifier.padding(8.dp).size(16.dp)
    )
}

/** Artists: their round photo alone, no square behind it, the name over its foot and an arrow saying the tile opens a page. */
@Composable
private fun ArtistDialTile(
    item: ArtistItem,
    isPinned: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier,
    thumbnailSize: Int,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        ItemThumbnail(
            thumbnailUrl = item.thumbnail?.resize(thumbnailSize, thumbnailSize),
            isActive = isActive,
            isPlaying = isPlaying,
            shape = CircleShape,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, bottom = 8.dp),
        ) {
            // no square fade behind a round photo, so a stronger halo carries the name
            MarqueeText(
                text = item.title,
                style = DialNameStyle.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), androidx.compose.ui.geometry.Offset(0f, 1f), blurRadius = 14f)),
                modifier = Modifier.weight(1f),
            )
            Icon(painterResource(R.drawable.navigate_next), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        if (isPinned) PinMark(Modifier.align(Alignment.TopEnd))
    }
}
