package com.metrolist.music.arc

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.metrolist.music.R
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal val HintTail = 10.dp

/** Whether the search bar shows a mic for song recognition. Holding Search does the same. */
val SearchMicKey = booleanPreferencesKey("arcSearchMic")
private val SearchHoldHintShownKey = booleanPreferencesKey("arcSearchHoldHintShown")

/** Hints waiting for their moment. Lives outside Compose so a hint survives the screen it was earned on. */
object ArcHints {
    /** Recognition was opened from the search bar's mic, so closing it should teach the Search hold. */
    var recognitionFromMic = false

    var searchHold by mutableStateOf(false)
}

/**
 * The speech bubble every hint uses: a rounded box with its tail on the bottom edge, [tailInset]
 * from the end the tail sits at ([tailAtEnd]), pointing down at the thing it explains.
 */
internal fun Modifier.hintBubble(color: Color, tailInset: Dp, tailAtEnd: Boolean): Modifier =
    drawBehind {
        val tail = HintTail.toPx()
        val tip = if (tailAtEnd) size.width - tailInset.toPx() else tailInset.toPx()
        drawRoundRect(color, size = Size(size.width, size.height - tail), cornerRadius = CornerRadius(18.dp.toPx()))
        drawPath(
            Path().apply {
                moveTo(tip - tail, size.height - tail - 1f)
                lineTo(tip, size.height)
                lineTo(tip + tail, size.height - tail - 1f)
                close()
            },
            color,
        )
    }.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp + HintTail)

/** The gentle pop-in and bob a hint bubble moves with, scaled from its tail. */
@Composable
internal fun Modifier.hintMotion(visible: Boolean, tailFraction: Float): Modifier {
    val shown by animateFloatAsState(if (visible) 1f else 0f, ArcMotion.springy(), label = "hint")
    val bob by rememberInfiniteTransition(label = "hintBob")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    return graphicsLayer {
        alpha = shown.coerceIn(0f, 1f)
        scaleX = shown
        scaleY = shown
        translationY = -bob * 4.dp.toPx()
        transformOrigin = TransformOrigin(tailFraction, 1f)
    }
}

private val SearchHintTailInset = 28.dp

/**
 * Over the Search button after the user found recognition through the mic: tells them holding
 * Search does the same, and offers to drop the mic from the search bar.
 */
@Composable
fun SearchHoldHint(tailAtEnd: Boolean) {
    if (!ArcHints.searchHold) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bubble = MaterialTheme.colorScheme.onSurface
    val text = MaterialTheme.colorScheme.surface
    // The bubble leaves only after the write, since leaving cancels this scope.
    fun close(removeMic: Boolean) {
        scope.launch {
            context.dataStore.edit {
                it[SearchHoldHintShownKey] = true
                if (removeMic) it[SearchMicKey] = false
            }
            ArcHints.searchHold = false
        }
    }
    Popup(
        popupPositionProvider = rememberHintPosition(tailAtEnd),
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .widthIn(max = 280.dp)
                    .hintMotion(true, if (tailAtEnd) 1f else 0f)
                    .hintBubble(bubble, SearchHintTailInset, tailAtEnd),
        ) {
            Text(stringResource(R.string.arc_hint_search_hold), style = MaterialTheme.typography.titleSmall, color = text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HintAction(stringResource(R.string.arc_hint_remove_mic), text, strong = true) { close(removeMic = true) }
                HintAction(stringResource(R.string.arc_hint_keep_mic), text, strong = false) { close(removeMic = false) }
            }
        }
    }
}

@Composable
private fun HintAction(label: String, color: Color, strong: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
        color = color.copy(alpha = if (strong) 1f else 0.75f),
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** Puts the bubble's tail on the anchor's centre, just above it, with the bubble running inward. */
@Composable
private fun rememberHintPosition(tailAtEnd: Boolean): PopupPositionProvider {
    val density = LocalDensity.current
    val inset = with(density) { SearchHintTailInset.roundToPx() }
    val gap = with(density) { 6.dp.roundToPx() }
    return remember(tailAtEnd, inset, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val centre = anchorBounds.center.x
                val x = if (tailAtEnd) centre + inset - popupContentSize.width else centre - inset
                return IntOffset(x, anchorBounds.top - popupContentSize.height - gap)
            }
        }
    }
}

/**
 * Brings every first-use tip back for testing: the hold hint shows on the next opening of the
 * player, the first hold asks again, and the mic is back so its hint can be earned again.
 */
suspend fun resetHints(context: android.content.Context) {
    rearmPlayHoldHint()
    ArcHints.recognitionFromMic = false
    ArcHints.searchHold = false
    context.dataStore.edit {
        it.remove(PlayHoldTriedKey)
        it[PlayerRunsKey] = HINT_FIRST_RUN - 1
        it.remove(SearchHoldHintShownKey)
        it.remove(PlayHoldActionKey)
        it[SearchMicKey] = true
    }
}

/** Called when recognition closes: if the mic opened it and the hint hasn't been shown, it's time. */
suspend fun earnSearchHoldHint(context: android.content.Context): Boolean {
    if (!ArcHints.recognitionFromMic) return false
    ArcHints.recognitionFromMic = false
    if (context.dataStore.data.first()[SearchHoldHintShownKey] == true) return false
    ArcHints.searchHold = true
    return true
}
