package com.metrolist.music.arc.home

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.arc.CapsLabel
import com.metrolist.music.utils.rememberPreference
import kotlin.math.sin

/** Present on Home only: holding a module title (or the day line) calls [enter]. */
class HomeEdit(val enter: () -> Unit)

val LocalHomeEdit = staticCompositionLocalOf<HomeEdit?> { null }

private const val EDIT_SCALE = 0.92f

/** One clock for every module's wobble, ticking only while editing, and not at all in battery saver or with animations off. */
@Composable
fun rememberWobbleClock(editing: Boolean): MutableLongState {
    val clock = remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    LaunchedEffect(editing) {
        val still =
            context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true ||
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        if (!editing || still) {
            clock.longValue = 0L
            return@LaunchedEffect
        }
        while (true) withFrameMillis { clock.longValue = it }
    }
    return clock
}

/**
 * A module while Home is being edited: shrunk, wobbling, tiles asleep under a cover that takes the touches (and carries
 * the drag), a minus at the top left and the module's gear at the top right.
 */
@Composable
fun EditFrame(
    module: HomeModule,
    index: Int,
    clock: MutableLongState,
    dragging: Boolean,
    cover: Modifier,
    onHide: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // read here, so the bob only redraws; a slow float out of step with the neighbours, the same for any module height
                val t = clock.longValue
                translationY = if (dragging || t == 0L) 0f else sin(t / 1000f * 3.5f + index * 1.9f) * 1.5.dp.toPx()
                val s = if (dragging) 0.96f else EDIT_SCALE
                scaleX = s
                scaleY = s
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            // some modules draw nothing while their data is missing; the stub keeps them grabbable
            var empty by remember { mutableStateOf(false) }
            Box(Modifier.onSizeChanged { empty = it.height == 0 }) { content() }
            if (empty) EmptyModuleStub(module)
        }
        Box(Modifier.matchParentSize().then(cover))
        val badge = 28.dp
        Badge(R.drawable.remove, stringResource(R.string.arc_home_hide), colors.error, colors.onError, Modifier.align(Alignment.TopStart).offset(x = -badge / 3, y = -badge / 3), onHide)
        if (module.setting != null) {
            Badge(R.drawable.settings, stringResource(R.string.settings), colors.secondaryContainer, colors.onSecondaryContainer, Modifier.align(Alignment.TopEnd).offset(x = badge / 3, y = -badge / 3), onSettings)
        }
    }
}

@Composable
private fun Badge(icon: Int, label: String, ground: Color, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(ground)
                .clickable(onClick = onClick),
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** Shown in edit mode in place of a module that has nothing to show right now, so it can still be moved or hidden. */
@Composable
fun EmptyModuleStub(module: HomeModule) {
    Box(Modifier.graphicsLayer { alpha = 0.5f }) { ModuleHeader(stringResource(module.title)) }
}

/** The bottom of Home while editing: every hidden module as a chip that brings it back. */
@Composable
fun AddModulesRow(hidden: List<HomeModule>, onAdd: (HomeModuleId) -> Unit) {
    if (hidden.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        CapsLabel(stringResource(R.string.arc_home_add_module))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            hidden.forEach { module ->
                SuggestionChip(
                    onClick = { onAdd(module.id) },
                    label = { Text(stringResource(module.title)) },
                    icon = { Icon(painterResource(R.drawable.add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

/** The gear's sheet: the module's one setting as a row of choices. */
@Composable
fun ModuleSettingsSheet(module: HomeModule) {
    val setting = module.setting ?: return
    var value by rememberPreference(setting.key, setting.default)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        CapsLabel(stringResource(module.title))
        Text(stringResource(setting.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
            setting.choices.forEach { choice ->
                FilterChip(selected = value == choice, onClick = { value = choice }, label = { Text(choice.toString()) })
            }
        }
    }
}

/** Home's own snackbar host, whose snackbars can be swiped away. */
@Composable
fun SwipeSnackbarHost(state: SnackbarHostState, modifier: Modifier) {
    SnackbarHost(state, modifier) { data ->
        val swipe = rememberSwipeToDismissBoxState()
        LaunchedEffect(swipe.currentValue) { if (swipe.currentValue != SwipeToDismissBoxValue.Settled) data.dismiss() }
        SwipeToDismissBox(swipe, backgroundContent = {}) { Snackbar(data) }
    }
}

/** The last thing on Home, for anyone who never finds the hold. */
@Composable
fun EditHomeRow(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = onClick) {
            Icon(painterResource(R.drawable.edit), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.arc_home_edit), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
