package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.metrolist.music.arc.home.homeTile
import com.metrolist.music.constants.DialCornerRadius

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RandomizeGridItem(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // When isLoading is true, multiplier goes to 0 (moving dots to center)
    // When isLoading is false, multiplier goes to 1 (moving dots to corners)
    val dotOffsetMultiplier by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "dotOffset",
    )

    val loadingAlpha by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "loadingAlpha",
    )

    Box(
        modifier =
            modifier
                .homeTile(onClick = onClick, glowCorner = DialCornerRadius)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(DialCornerRadius))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        // Die Dots (5-pattern)
        val dotColor = MaterialTheme.colorScheme.onSecondaryContainer
        val dotSize = 14.dp
        val padding = 24.dp

        // Using a single Center alignment and offsetting FROM center ensures they
        // collapse TO center correctly.

        // Top Left
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((-padding * dotOffsetMultiplier).roundToPx(), (-padding * dotOffsetMultiplier).roundToPx()) }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        // Top Right
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((padding * dotOffsetMultiplier).roundToPx(), (-padding * dotOffsetMultiplier).roundToPx()) }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        // Center
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        // Bottom Left
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((-padding * dotOffsetMultiplier).roundToPx(), (padding * dotOffsetMultiplier).roundToPx()) }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        // Bottom Right
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((padding * dotOffsetMultiplier).roundToPx(), (padding * dotOffsetMultiplier).roundToPx()) }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
        )

        // The spinner animates every frame, so it only exists while it shows.
        if (loadingAlpha > 0f) {
            Box(modifier = Modifier.alpha(loadingAlpha)) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
