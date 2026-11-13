package com.metrolist.music.arc

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import com.metrolist.music.ui.component.BottomSheetState
import kotlin.math.abs

/**
 * Swiping up pulls [sheet] open and it follows the finger. Anything underneath that already took the
 * touch, like the record or the slider, keeps it, and swipes in other directions are left alone.
 */
fun Modifier.swipeUpOpens(sheet: BottomSheetState): Modifier = composed {
    // Swipes that start near the bottom edge belong to the system (home, switching apps), not to the player.
    val edge = WindowInsets.systemGestures.getBottom(LocalDensity.current) + with(LocalDensity.current) { EdgeMargin.roundToPx() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    onGloballyPositioned { coordinates = it }.pointerInput(sheet, edge) {
        awaitEachGesture {
            // Tap handlers like the cover's double tap take the down, but not the movement, so the swipe can still win.
            val down = awaitFirstDown(requireUnconsumed = false)
            val coords = coordinates
            if (coords != null) {
                val windowHeight = coords.findRootCoordinates().size.height
                if (coords.localToRoot(down.position).y > windowHeight - edge) return@awaitEachGesture
            }
            val slop = viewConfiguration.touchSlop
            val tracker = VelocityTracker()
            var travelled = Offset.Zero
            var claimed = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!claimed && change.isConsumed) return@awaitEachGesture
                val delta = change.positionChange()
                travelled += delta
                if (!claimed) {
                    when {
                        travelled.y < -slop && abs(travelled.y) > abs(travelled.x) -> claimed = true
                        travelled.y > slop || abs(travelled.x) > slop -> return@awaitEachGesture
                    }
                }
                if (claimed) {
                    change.consume()
                    tracker.addPointerInputChange(change)
                    sheet.dispatchRawDelta(delta.y)
                }
            }
            if (claimed) sheet.performFling(-tracker.calculateVelocity().y, null)
        }
    }
}

private val EdgeMargin = 16.dp
