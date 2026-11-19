package com.metrolist.music.arc

import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Carries a setting's title from the settings home (recent or search result) to the page it opens,
 * where the row with that title is scrolled into view and pulsed.
 */
object SettingsHighlight {
    var pending: String? = null
    internal var rect by mutableStateOf<Rect?>(null)
    internal val pulse = Animatable(0f)
}

private const val NAV_SETTLE_MS = 450L
private const val SCROLL_SETTLE_MS = 380L

/** Hosts the highlight inside a settings group: finds the pending row, scrolls to it and draws the pulse. */
@Composable
fun Modifier.settingsHighlight(): Modifier {
    val view = LocalView.current
    val cornerPx = with(LocalDensity.current) { 20.dp.toPx() }
    val color = MaterialTheme.colorScheme.primary
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(Unit) {
        if (SettingsHighlight.pending == null) return@LaunchedEffect
        // Waits out the page transition, so the settings home's own copy of the title is gone.
        delay(NAV_SETTLE_MS)
        val wanted = SettingsHighlight.pending ?: return@LaunchedEffect
        SettingsHighlight.pending = null
        val root = { (view as? RootForTest)?.semanticsOwner?.unmergedRootSemanticsNode }
        val found = root()?.let { findTitle(it, wanted, view.width.toFloat()) } ?: return@LaunchedEffect
        val row = found.clickableAncestor() ?: found
        val scroller = generateSequence(found.parent) { it.parent }
            .firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
        if (scroller != null) {
            // boundsInRoot is clipped to the viewport, so a row below the fold reads as empty
            val dy = row.positionInRoot.y + row.size.height / 2f - scroller.boundsInRoot.center.y
            if (kotlin.math.abs(dy) > 1f) {
                scroller.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, dy)
                delay(SCROLL_SETTLE_MS)
            }
        }
        val settled = root()?.let { findTitle(it, wanted, view.width.toFloat()) } ?: return@LaunchedEffect
        // a group's own title has no row to light, so the whole group lights up
        SettingsHighlight.rect = (settled.clickableAncestor() ?: settled.groupAncestor() ?: settled).boundsInRoot
        try {
            repeat(3) {
                SettingsHighlight.pulse.animateTo(1f, tween(220))
                SettingsHighlight.pulse.animateTo(0.25f, tween(380))
            }
            SettingsHighlight.pulse.animateTo(0f, tween(300))
        } finally {
            SettingsHighlight.rect = null
            SettingsHighlight.pulse.snapTo(0f)
        }
    }

    return this
        .onGloballyPositioned { coordinates = it }
        .drawWithContent {
            drawContent()
            val target = SettingsHighlight.rect ?: return@drawWithContent
            val origin = coordinates?.positionInRoot() ?: return@drawWithContent
            val local = target.translate(-origin)
            if (local.bottom < 0f || local.top > size.height) return@drawWithContent
            val pulse = SettingsHighlight.pulse.value
            drawRoundRect(
                color = color.copy(alpha = 0.4f * pulse),
                topLeft = local.topLeft,
                size = local.size,
                cornerRadius = CornerRadius(cornerPx),
            )
            drawRoundRect(
                color = color.copy(alpha = pulse),
                topLeft = local.topLeft,
                size = local.size,
                cornerRadius = CornerRadius(cornerPx),
                style = Stroke(2.dp.toPx()),
            )
        }
}

private fun findTitle(node: SemanticsNode, wanted: String, windowWidth: Float): SemanticsNode? {
    val texts = node.config.getOrNull(SemanticsProperties.Text)
    // The page navigated from keeps an unplaced copy of the title (the search result) for a moment.
    if (texts != null && texts.any { it.text == wanted } && node.layoutInfo.isPlaced &&
        node.positionInRoot.x + node.size.width / 2f in 0f..windowWidth
    ) return node
    for (child in node.children) findTitle(child, wanted, windowWidth)?.let { return it }
    return null
}

const val SETTINGS_GROUP_TAG = "settingsGroup"

private fun SemanticsNode.groupAncestor(): SemanticsNode? =
    generateSequence(this) { it.parent }.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == SETTINGS_GROUP_TAG }

private fun SemanticsNode.clickableAncestor(): SemanticsNode? =
    generateSequence(this) { it.parent }.firstOrNull { it.config.getOrNull(SemanticsActions.OnClick) != null }
