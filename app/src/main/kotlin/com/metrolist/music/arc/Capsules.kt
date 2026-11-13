package com.metrolist.music.arc

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.metrolist.music.R

val CapsuleSize = 44.dp
val CapsuleGap = 8.dp
val TopBarEndPadding = 12.dp
val TopBarTopPadding = 4.dp

class CapsuleButton(
    @DrawableRes val icon: Int? = null,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val tint: Color? = null,
    val iconModifier: Modifier = Modifier,
    val content: (@Composable () -> Unit)? = null,
    val hint: (@Composable BoxScope.() -> Unit)? = null,
)

/** The app's button style: a row of bare icons, the active one marked with a dot beneath it. */
@Composable
fun CapsuleRow(
    buttons: List<CapsuleButton>,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = CapsuleSize,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CapsuleGap), modifier = modifier) {
        buttons.forEachIndexed { index, button ->
            CapsulePiece(button, color, Modifier.size(size))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CapsulePiece(button: CapsuleButton, color: Color, sizing: Modifier) {
    val haptics = LocalHapticFeedback.current
    val tint = button.tint ?: if (button.active) color else color.copy(alpha = 0.6f)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .tapBounce()
                .then(sizing)
                .alpha(if (button.enabled) 1f else 0.35f)
                .clip(CircleShape)
                .combinedClickable(
                    enabled = button.enabled,
                    onClick = button.onClick,
                    onLongClick =
                        button.onLongClick?.let { hold ->
                            {
                                hold()
                            }
                        },
                ),
    ) {
        val custom = button.content
        when {
            custom != null -> custom()
            button.icon != null -> Icon(painterResource(button.icon), null, tint = tint, modifier = button.iconModifier.size(24.dp))
        }
        button.hint?.invoke(this)
        if (button.active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(4.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

val ArcPanelLeftKey = androidx.datastore.preferences.core.booleanPreferencesKey("arcPanelLeft")

/** The menu, lyrics, repeat and shuffle, in the same order in every player layout, with shuffle nearest the corner controls. */
fun playerExtras(
    shuffled: Boolean,
    repeatMode: Int,
    lyricsOpen: Boolean,
    canControl: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onLyrics: () -> Unit,
    onLyricsMenu: () -> Unit,
    onMenu: () -> Unit,
    outputIcon: Int,
    onOutput: () -> Unit,
): List<CapsuleButton> =
    listOf(
        CapsuleButton(R.drawable.more_vert, onMenu),
        CapsuleButton(outputIcon, onOutput),
        CapsuleButton(R.drawable.lyrics, onLyrics, onLongClick = onLyricsMenu, active = lyricsOpen, hint = { LyricsHoldHint() }),
        CapsuleButton(
            if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
            onRepeat,
            active = repeatMode != Player.REPEAT_MODE_OFF,
            enabled = canControl,
        ),
        CapsuleButton(R.drawable.shuffle, onShuffle, active = shuffled, enabled = canControl),
    )
