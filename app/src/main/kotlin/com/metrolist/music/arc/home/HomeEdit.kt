package com.metrolist.music.arc.home

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import kotlin.math.roundToInt
import com.metrolist.music.R
import com.metrolist.music.arc.CapsLabel
import com.metrolist.music.utils.rememberPreference
import kotlin.math.sin

/** Present on Home only: holding a module title (or the day line) calls [enter]. */
class HomeEdit(val enter: () -> Unit)

val LocalHomeEdit = staticCompositionLocalOf<HomeEdit?> { null }

/** True inside an Add to Home preview, where a module must not change what the rest of Home shows. */
val LocalModulePreview = staticCompositionLocalOf { false }

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
    Box(Modifier.graphicsLayer { alpha = 0.5f }) { ModuleHeader(module.name(LocalContext.current)) }
}

/** The bottom of Home while editing: every hidden module as a small live preview; tapping one brings it back. */
@Composable
fun AddModulesRow(env: HomeEnv, hidden: List<HomeModule>, onAdd: (String) -> Unit) {
    if (hidden.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        CapsLabel(stringResource(R.string.arc_home_add_module), Modifier.padding(horizontal = 20.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(hidden, key = { it.key }) { module -> ModulePreview(env, module) { onAdd(module.key) } }
        }
    }
}

private val PreviewWidth = 196.dp
private val PreviewHeight = 150.dp

/** The module as it would look on Home, shrunk and asleep, with its name and a plus under it. */
@Composable
private fun ModulePreview(env: HomeEnv, module: HomeModule, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.width(PreviewWidth).clip(shape).border(1.dp, colors.outlineVariant, shape)) {
        Column {
            Box(Modifier.fillMaxWidth().height(PreviewHeight).clipToBounds()) {
                if (module.available(env)) {
                    CompositionLocalProvider(LocalModulePreview provides true) { Box(Modifier.shrunk(env.width, PreviewWidth)) { module.content(env) } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(colors.surfaceContainer).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Icon(painterResource(R.drawable.add), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Column(Modifier.padding(start = 8.dp)) {
                    Text(module.name(LocalContext.current), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // some of YouTube's rows share a name with Arc's own modules
                    if (module.key.startsWith(YT_ROW)) {
                        Text(stringResource(R.string.arc_home_from_youtube), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        // on top, so the module under it never gets a tap
        val name = module.name(LocalContext.current)
        Box(Modifier.matchParentSize().clickable(onClick = onClick).semantics { contentDescription = name })
    }
}

/** Lays the content out at Home's full [width], then draws it scaled down to [to] from the top left. */
private fun Modifier.shrunk(width: Dp, to: Dp): Modifier =
    layout { measurable, constraints ->
        val scale = to / width
        val placeable = measurable.measure(Constraints(maxWidth = width.roundToPx()))
        val size = constraints.constrain(IntSize((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()))
        layout(size.width, size.height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }

/** The gear's sheet: the module's one setting as a row of choices. */
@Composable
fun ModuleSettingsSheet(module: HomeModule) {
    val setting = module.setting ?: return
    var value by rememberPreference(setting.key, setting.default)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        CapsLabel(module.name(LocalContext.current))
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
