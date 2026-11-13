package com.metrolist.music.arc

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

private const val THEME_FADE_MS = 500

@Composable
private fun Color.animated(label: String): Color {
    val color by animateColorAsState(this, tween(THEME_FADE_MS), label = label)
    return color
}

/**
 * The theme follows the playing song's cover, which changes every color on screen at once. Fading
 * between schemes makes that, and the jump from the default colors at startup, gentle instead of a flash.
 */
@Composable
fun ColorScheme.animated(): ColorScheme =
    copy(
        primary = primary.animated("primary"),
        onPrimary = onPrimary.animated("onPrimary"),
        primaryContainer = primaryContainer.animated("primaryContainer"),
        onPrimaryContainer = onPrimaryContainer.animated("onPrimaryContainer"),
        inversePrimary = inversePrimary.animated("inversePrimary"),
        secondary = secondary.animated("secondary"),
        onSecondary = onSecondary.animated("onSecondary"),
        secondaryContainer = secondaryContainer.animated("secondaryContainer"),
        onSecondaryContainer = onSecondaryContainer.animated("onSecondaryContainer"),
        tertiary = tertiary.animated("tertiary"),
        onTertiary = onTertiary.animated("onTertiary"),
        tertiaryContainer = tertiaryContainer.animated("tertiaryContainer"),
        onTertiaryContainer = onTertiaryContainer.animated("onTertiaryContainer"),
        background = background.animated("background"),
        onBackground = onBackground.animated("onBackground"),
        surface = surface.animated("surface"),
        onSurface = onSurface.animated("onSurface"),
        surfaceVariant = surfaceVariant.animated("surfaceVariant"),
        onSurfaceVariant = onSurfaceVariant.animated("onSurfaceVariant"),
        surfaceTint = surfaceTint.animated("surfaceTint"),
        outline = outline.animated("outline"),
        outlineVariant = outlineVariant.animated("outlineVariant"),
        surfaceContainerLowest = surfaceContainerLowest.animated("surfaceContainerLowest"),
        surfaceContainerLow = surfaceContainerLow.animated("surfaceContainerLow"),
        surfaceContainer = surfaceContainer.animated("surfaceContainer"),
        surfaceContainerHigh = surfaceContainerHigh.animated("surfaceContainerHigh"),
        surfaceContainerHighest = surfaceContainerHighest.animated("surfaceContainerHighest"),
    )
