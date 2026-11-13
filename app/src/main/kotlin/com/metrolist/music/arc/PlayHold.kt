package com.metrolist.music.arc

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.PlaybackParameters
import com.metrolist.music.R
import com.metrolist.music.eq.data.SavedEQProfile
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.metrolist.music.arc.snippet.Snippet
import kotlin.math.abs
import kotlin.math.roundToInt

/** What holding the play button does. [ASK] until the user has picked, so the first hold asks them. */
enum class PlayHoldAction(@StringRes val title: Int, @StringRes val description: Int, @DrawableRes val icon: Int) {
    ASK(R.string.arc_hold_play, R.string.arc_hold_play_ask, R.drawable.play),
    SPEED(R.string.arc_hold_speed, R.string.arc_hold_speed_desc, R.drawable.speed),
    EQ(R.string.arc_hold_eq, R.string.arc_hold_eq_desc, R.drawable.equalizer),
    LAYOUT(R.string.arc_hold_layout, R.string.arc_hold_layout_desc, R.drawable.sync),
    DISLIKE(R.string.arc_dislike, R.string.arc_hold_dislike_desc, R.drawable.block),
    SNIPPET(R.string.arc_hold_snippet, R.string.arc_hold_snippet_desc, R.drawable.mic),
}

val PlayHoldActionKey = stringPreferencesKey("arcPlayHoldAction")
internal val PlayHoldTriedKey = booleanPreferencesKey("arcPlayHoldTried")
internal val PlayerRunsKey = intPreferencesKey("arcPlayerRuns")

/** The hint first shows on the second run of the app that opens the player, and gives up after a few. */
internal const val HINT_FIRST_RUN = 2
private const val HINT_LAST_RUN = 4
private var runCounted = false
private var hintDismissed = false

/** Lets the hold hint come back on the next opening of the player, for "show tips again". */
internal fun rearmPlayHoldHint() {
    runCounted = false
    hintDismissed = false
}

/** A press on play that lasts: started by the long press, then told how far the finger has moved and when it lifts. */
class PlayHold(
    val onStart: () -> Unit,
    val onMove: (Offset) -> Unit = {},
    val onEnd: () -> Unit = {},
)

private val EqRow = 40.dp
private val LockPull = 72.dp
private const val HOLD_SPEED = 2f

@Stable
class PlayHoldState internal constructor() {
    var speeding by mutableStateOf(false)
        internal set
    var presets by mutableStateOf<List<SavedEQProfile?>?>(null)
        internal set
    var presetIndex by mutableIntStateOf(0)
        internal set
    var hint by mutableStateOf(false)
        internal set
    var hold = PlayHold({})
        internal set
}

/**
 * The hold behind the play button. In lyrics it opens the lyrics menu, unless it records snippets; elsewhere it runs [action],
 * or opens the picker while nothing has been chosen.
 */
@Composable
fun rememberPlayHold(
    action: PlayHoldAction,
    playerConnection: PlayerConnection,
    inLyrics: Boolean,
    expanded: Boolean,
    onLyricsMenu: () -> Unit,
    onSwitchLayout: () -> Unit,
    onPick: () -> Unit,
): PlayHoldState {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = remember { PlayHoldState() }
    val current by rememberUpdatedState(action)
    val lyrics by rememberUpdatedState(inLyrics)
    val lyricsMenu by rememberUpdatedState(onLyricsMenu)
    val switchLayout by rememberUpdatedState(onSwitchLayout)
    val pick by rememberUpdatedState(onPick)
    val rowPx = with(density) { EqRow.toPx() }
    val lockPx = with(density) { LockPull.toPx() }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, R.string.arc_snippet_mic, Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(expanded) {
        state.hint = false
        if (!expanded || hintDismissed) return@LaunchedEffect
        if (context.dataStore.data.first()[PlayHoldTriedKey] == true) return@LaunchedEffect
        if (!runCounted) {
            runCounted = true
            context.dataStore.edit { it[PlayerRunsKey] = (it[PlayerRunsKey] ?: 0) + 1 }
        }
        val runs = context.dataStore.data.first()[PlayerRunsKey] ?: 0
        if (runs in HINT_FIRST_RUN..HINT_LAST_RUN) {
            delay(1200)
            state.hint = true
        }
    }

    remember(playerConnection) {
        val player = playerConnection.player
        val service = playerConnection.service
        var speedBefore: PlaybackParameters? = null
        var startIndex = 0

        fun preview(index: Int) {
            val presets = state.presets ?: return
            val clamped = index.coerceIn(0, presets.lastIndex)
            if (clamped == state.presetIndex) return
            state.presetIndex = clamped
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            presets[clamped]?.let { service.equalizerService.applyProfile(it) } ?: service.equalizerService.disable()
        }

        state.hold =
            PlayHold(
                onStart = {
                    state.hint = false
                    scope.launch { context.dataStore.edit { it[PlayHoldTriedKey] = true } }
                    when {
                        lyrics && current != PlayHoldAction.SNIPPET -> lyricsMenu()
                        current == PlayHoldAction.ASK -> pick()
                        current == PlayHoldAction.LAYOUT -> switchLayout()
                        current == PlayHoldAction.DISLIKE ->
                            player.currentMediaItem?.mediaId?.let { id -> scope.launch { toggleDislike(context, id) } }
                        current == PlayHoldAction.SPEED -> {
                            speedBefore = player.playbackParameters
                            player.playbackParameters = PlaybackParameters(HOLD_SPEED, player.playbackParameters.pitch)
                            state.speeding = true
                        }
                        current == PlayHoldAction.SNIPPET -> {
                            Snippet.lockPull = 0f
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                askMic.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                if (!player.isPlaying) player.play()
                                val song = player.mediaMetadata
                                val started = Snippet.start(
                                    context,
                                    player,
                                    title = song.title?.toString().orEmpty(),
                                    artist = song.artist?.toString().orEmpty(),
                                    cover = song.artworkUri?.toString(),
                                )
                                if (!started) Toast.makeText(context, R.string.arc_snippet_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                        current == PlayHoldAction.EQ -> {
                            val presets = listOf(null) + service.eqProfileRepository.getSortedProfiles()
                            val active = service.eqProfileRepository.activeProfile.value?.id
                            startIndex = presets.indexOfFirst { it?.id == active }.coerceAtLeast(0)
                            state.presetIndex = startIndex
                            state.presets = presets
                        }
                    }
                },
                onMove = { moved ->
                    // Sliding up walks down the list, like pulling the next row under the thumb.
                    if (state.presets != null) preview(startIndex + (-moved.y / rowPx).roundToInt())
                    val recording = Snippet.recording.value
                    if (recording != null && !recording.locked) {
                        Snippet.lockPull = (-moved.y / lockPx).coerceIn(0f, 1f)
                        if (Snippet.lockPull >= 1f) {
                            Snippet.lock()
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                    }
                },
                onEnd = {
                    if (Snippet.recording.value?.locked == false) Snippet.stop("finger lifted")
                    Snippet.lockPull = 0f
                    speedBefore?.let { player.playbackParameters = it }
                    speedBefore = null
                    state.speeding = false
                    state.presets?.let { presets ->
                        val chosen = presets[state.presetIndex]?.id
                        scope.launch { service.eqProfileRepository.setActiveProfile(chosen) }
                    }
                    state.presets = null
                },
            )
    }
    return state
}

/** What a running hold shows beside the play button at [centre]: the speed, or the presets being tried. */
@Composable
fun PlayHoldOverlay(
    state: PlayHoldState,
    centre: Offset,
    inward: Boolean,
    width: Float,
    content: Color,
    ground: Color,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { (PlaySize / 2 + 16.dp).toPx() }
    val speedShown by animateFloatAsState(if (state.speeding) 1f else 0f, ArcMotion.bouncy(), label = "holdSpeed")
    if (speedShown > 0.01f) {
        val widthPx = with(density) { 160.dp.toPx() }
        val edge = with(density) { 24.dp.toPx() }
        // Against the screen edge, as far from the arc as it can be, so a long name running along the arc never meets it.
        val left = if (inward) edge else width - edge - widthPx
        Column(
            horizontalAlignment = if (inward) Alignment.Start else Alignment.End,
            modifier =
                Modifier
                    .offset { IntOffset(left.roundToInt(), (centre.y - 150.dp.toPx()).roundToInt()) }
                    .width(160.dp)
                    .graphicsLayer {
                        alpha = speedShown.coerceIn(0f, 1f)
                        scaleX = 0.6f + 0.4f * speedShown
                        scaleY = scaleX
                    },
        ) {
            Text(stringResource(R.string.arc_hold_speed_label), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f), letterSpacing = 2.sp)
            Text("2×", fontSize = 80.sp, fontWeight = FontWeight.Thin, color = content)
        }
    }

    HoldHint(state.hint, centre, inward, content, ground) {
        hintDismissed = true
        state.hint = false
    }

    val presets = state.presets ?: return
    val slide = remember { Animatable(state.presetIndex.toFloat()) }
    LaunchedEffect(state.presetIndex) { slide.animateTo(state.presetIndex.toFloat(), ArcMotion.snappy()) }
    val rowPx = with(density) { EqRow.toPx() }
    val widthPx = with(density) { 168.dp.toPx() }
    val left = if (inward) centre.x - gapPx - widthPx else centre.x + gapPx
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), (centre.y - rowPx * (presets.size - slide.value) + rowPx / 2).roundToInt()) }
            .width(168.dp)
            .background(ground.copy(alpha = 0.92f), RoundedCornerShape(20.dp)),
    ) {
        Column {
            presets.asReversed().forEachIndexed { reversedIndex, preset ->
                val index = presets.lastIndex - reversedIndex
                val distance = abs(index - slide.value)
                Box(
                    contentAlignment = if (inward) Alignment.CenterEnd else Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth().height(EqRow).padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = preset?.name ?: stringResource(R.string.arc_hold_eq_off),
                        style = if (index == state.presetIndex) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = (1f - distance * 0.3f).coerceIn(0.25f, 1f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The picker the first hold opens. */
@Composable
fun ColumnScope.PlayHoldPicker(current: PlayHoldAction, onPick: (PlayHoldAction) -> Unit) {
    Text(
        stringResource(R.string.arc_hold_play),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp),
    )
    Text(
        stringResource(R.string.arc_hold_play_ask),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    Box(Modifier.padding(16.dp)) {
        Material3MenuGroup(
            items =
                PlayHoldAction.entries.filter { it != PlayHoldAction.ASK }.map { choice ->
                Material3MenuItemData(
                    icon = { Icon(painterResource(choice.icon), contentDescription = null) },
                    title = { Text(stringResource(choice.title)) },
                    description = { Text(stringResource(choice.description)) },
                    trailingContent =
                        if (choice == current) {
                            { Icon(painterResource(R.drawable.check), contentDescription = null) }
                        } else {
                            null
                        },
                    onClick = { onPick(choice) },
                )
            },
        )
    }
}

private val HintWidth = 176.dp
private val HoldHintTailInset = 28.dp

/** A speech bubble rising from the play button that asks to be held, until the user has tried it. */
@Composable
private fun HoldHint(visible: Boolean, centre: Offset, inward: Boolean, bubble: Color, text: Color, onDismiss: () -> Unit) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, label = "holdHintGate")
    if (shown < 0.01f && !visible) return
    val density = LocalDensity.current
    val widthPx = with(density) { HintWidth.toPx() }
    val tailInset = with(density) { HoldHintTailInset.toPx() }
    // The tail points at the button's inner shoulder, so the bubble runs inward and clears the buttons on the arc.
    val tip = if (inward) centre.x - with(density) { 16.dp.toPx() } else centre.x + with(density) { 16.dp.toPx() }
    val left = if (inward) tip - widthPx + tailInset else tip - tailInset
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), (centre.y - PlaySize.toPx() / 2 - 64.dp.toPx()).roundToInt()) }
            .width(HintWidth)
            .hintMotion(visible, if (inward) 1f - tailInset / widthPx else tailInset / widthPx)
            .clickable(interactionSource = null, indication = null, onClick = onDismiss)
            .hintBubble(bubble, HoldHintTailInset, tailAtEnd = inward),
    ) {
        Text(stringResource(R.string.arc_hold_hint), style = MaterialTheme.typography.titleSmall, color = text)
    }
}

