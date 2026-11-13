package com.metrolist.music.arc

import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Top bars of screens opened from home float like its icons: no solid bar, just a fade so the list can pass under. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun arcTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)

@Composable
fun Modifier.arcTopBarBackdrop(): Modifier {
    val background = MaterialTheme.colorScheme.background
    return background(Brush.verticalGradient(0.6f to background, 1f to background.copy(alpha = 0f)))
}

/**
 * Material's [androidx.compose.material3.TopAppBar] in Arc's floating style. Screens that bring their own [colors]
 * (a cover behind the bar) keep them and get no fade.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    androidx.compose.material3.TopAppBar(
        title = title,
        modifier = if (colors == null) Modifier.arcTopBarBackdrop().then(modifier) else modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors ?: arcTopBarColors(),
        scrollBehavior = scrollBehavior,
    )
}
