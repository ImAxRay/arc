package com.metrolist.music.arc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

/** Arc's shared motion, so every bounce in the app feels like the same material. */
object ArcMotion {
    const val PRESSED_SCALE = 0.88f
    const val TAPPED_SCALE = 0.82f
    const val HELD_SCALE = 0.975f
    const val LIFTED_SCALE = 1.04f
    const val NEEDLE_LIFT = 0.05f

    /** Lets go with a visible overshoot: buttons, records settling, the needle dropping. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.4f, stiffness = 500f)

    /** The little overshoot after tapping something, faster and livelier than [bouncy]. */
    fun <T> springy(): SpringSpec<T> = spring(dampingRatio = 0.34f, stiffness = 900f)

    /** Quick and tight, for the moment a finger takes hold of something. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.7f, stiffness = 900f)

    /** Slower with a softer overshoot, for something heavy arriving from off screen. */
    fun <T> landing(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 190f)
}

/** Squishes while pressed and springs back on release. */
@Composable
fun Modifier.pressBounce(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) ArcMotion.PRESSED_SCALE else 1f, ArcMotion.bouncy(), label = "pressBounce")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A press that always shows, even on the quickest tap: dips, then springs past full size on release.
 * It only watches the touch, so the button underneath still gets its click. Put it first in the
 * modifier chain so borders and backgrounds scale with it.
 */
@Composable
fun Modifier.tapBounce(remoteTaps: Int = 0, tick: Boolean = true): Modifier {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // a tap that landed beside the button still bounces it, as if it hit
    LaunchedEffect(remoteTaps) {
        if (remoteTaps == 0) return@LaunchedEffect
        if (tick) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        scale.animateTo(ArcMotion.TAPPED_SCALE, tween(60))
        scale.animateTo(1f, ArcMotion.springy())
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (tick) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            val dip = scope.launch { scale.animateTo(ArcMotion.TAPPED_SCALE, tween(60)) }
            waitForUpOrCancellation(PointerEventPass.Initial)
            scope.launch {
                dip.join()
                scale.animateTo(1f, ArcMotion.springy())
            }
        }
    }
}

/** Pops the icon when a song gets liked or unliked, but not when the next song just happens to be liked already. */
@Composable
fun Modifier.likePop(liked: Boolean, songId: String?, known: Boolean = true): Modifier {
    val scale = remember { Animatable(1f) }
    val haptics = LocalHapticFeedback.current
    val seen = remember { mutableStateOf(Triple(songId, liked, known)) }
    LaunchedEffect(liked, songId, known) {
        val (lastSong, wasLiked, wasKnown) = seen.value
        seen.value = Triple(songId, liked, known)
        // A like that was only just loaded from the library isn't a change the user made.
        if (!known || !wasKnown || lastSong != songId || wasLiked == liked) return@LaunchedEffect
        // ToggleOff is too faint to feel on many phones, so an unlike gets a plain tick.
        haptics.performHapticFeedback(if (liked) HapticFeedbackType.Confirm else HapticFeedbackType.SegmentTick)
        scale.snapTo(if (liked) 0.4f else 0.75f)
        scale.animateTo(1f, ArcMotion.springy())
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
