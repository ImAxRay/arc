package com.metrolist.music.arc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.res.painterResource
import com.metrolist.music.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.component.isRouteSelected
import com.metrolist.music.ui.screens.Screens

val CapsuleNavHeight = 48.dp

/** The app's navigation as a floating capsule on the thumb's side, under the mini player. */
@Composable
fun CapsuleNav(
    items: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    navOnLeft: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    CapsuleRow(
        buttons =
            items.map { screen ->
                val active = isRouteSelected(currentRoute, screen.route, items)
                val icon = if (active) screen.iconIdActive else screen.iconIdInactive
                CapsuleButton(
                    icon = icon,
                    onClick = { onItemClick(screen, active) },
                    onLongClick = if (screen == Screens.Search) onSearchLongClick else null,
                    active = active,
                    content =
                        if (screen == Screens.Search) {
                            {
                                androidx.compose.material3.Icon(
                                    painterResource(icon),
                                    null,
                                    tint = if (active) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp),
                                )
                                SearchHoldHint(tailAtEnd = !navOnLeft)
                            }
                        } else {
                            null
                        },
                )
            },
        color = colors.onSurfaceVariant,
        size = CapsuleNavHeight,
        modifier = modifier,
    )
}

/**
 * The whole bottom line under the mini player. The nav sits on the chosen side, and the empty side
 * is one wide zone that sends the panels over to it. Owning the full width also stops taps there
 * reaching the mini player's sheet underneath.
 */
@Composable
fun CapsuleNavBar(
    items: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var onLeft by com.metrolist.music.utils.rememberPreference(ArcPanelLeftKey, false)
    val side = animatedPanelSide(onLeft)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(CapsuleNavHeight)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureThresholdActivate)
                    onLeft = !onLeft
                },
    ) {
        androidx.compose.material3.Icon(
            painterResource(if (onLeft) R.drawable.arrow_forward else R.drawable.arrow_back),
            null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.align(BiasAlignment(-1f + 2f * side, 0f)).padding(horizontal = 18.dp).size(18.dp),
        )
        CapsuleNav(
            items = items,
            currentRoute = currentRoute,
            onItemClick = onItemClick,
            onSearchLongClick = {
                ArcHints.searchHold = false
                onSearchLongClick()
            },
            navOnLeft = onLeft,
            modifier = Modifier.align(BiasAlignment(1f - 2f * side, 0f)),
        )
    }
}
