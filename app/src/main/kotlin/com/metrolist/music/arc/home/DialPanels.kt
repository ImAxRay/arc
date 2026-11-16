package com.metrolist.music.arc.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.R
import com.metrolist.music.arc.CapsLabel
import com.metrolist.music.constants.DialCornerRadius
import com.metrolist.music.playback.queues.YouTubeQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/** Set while the dial is being edited: its tiles bob, take no taps, and can be moved, replaced or removed. */
class DialEdit(val clock: MutableLongState)

val LocalDialEdit = staticCompositionLocalOf<DialEdit?> { null }

private const val ROLL_MS = 750L

/** Pip spots on a 3x3 grid (0..2, 0..2) for faces 1 to 6. */
private val PIPS =
    listOf(
        listOf(1 to 1),
        listOf(0 to 0, 2 to 2),
        listOf(0 to 0, 1 to 1, 2 to 2),
        listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2),
        listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2),
        listOf(0 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 2 to 2),
    )

/**
 * The die: always a die, never explained. A tap rolls it (it tumbles through faces and lands), the face stays across
 * restarts, and its mood's mix plays at once; the mix is fetched while the die still tumbles.
 */
@Composable
fun DiceTile(env: HomeEnv, modifier: Modifier) {
    val store = remember { DialStore.get(env.viewModel.context) }
    val saved by store.dice.collectAsStateWithLifecycle()
    var shown by remember { mutableIntStateOf(0) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var rolling by remember { mutableStateOf(false) }
    val face = (if (rolling) shown else saved).takeIf { it in 1..6 } ?: 5
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .homeTile(
                    onClick = {
                        if (rolling || env.isListenTogetherGuest) return@homeTile
                        shown = face
                        rolling = true
                        val landed = Random.nextInt(1, 7)
                        // the mix is fetched and played on the screen's scope, so scrolling the die away doesn't drop it
                        env.scope.launch {
                            val start = System.currentTimeMillis()
                            store.setDice(landed)
                            val mix = env.viewModel.diceMix(landed)
                            delay(ROLL_MS - (System.currentTimeMillis() - start))
                            when {
                                mix != null -> env.playerConnection.playQueue(YouTubeQueue(mix.playEndpoint ?: WatchEndpoint(playlistId = mix.id)))
                                // offline or no moods on this account: something of the user's own, as the old random button did
                                else -> env.viewModel.getRandomItem()?.let(env::openYtItem)
                            }
                        }
                        scope.launch {
                            // faces flick by fast, then slower, like a die running out of push
                            val flick =
                                launch {
                                    var wait = 45L
                                    while (true) {
                                        shown = (1..6).filter { it != shown }.random()
                                        delay(wait)
                                        wait = (wait * 1.25f).toLong()
                                    }
                                }
                            spin.snapTo(0f)
                            spin.animateTo(1f, tween(ROLL_MS.toInt(), easing = FastOutSlowInEasing))
                            flick.cancel()
                            shown = landed
                            rolling = false
                        }
                    },
                    glowCorner = DialCornerRadius,
                ).fillMaxSize()
                .clip(RoundedCornerShape(DialCornerRadius))
                .background(colors.secondaryContainer),
    ) {
        val pip = colors.secondaryContainer
        val body = colors.onSecondaryContainer
        Box(
            Modifier
                .fillMaxSize(0.52f)
                .graphicsLayer {
                    val t = spin.value
                    rotationZ = t * 540f
                    // a hop: up and back while it turns
                    val hop = sin(t * Math.PI).toFloat()
                    scaleX = 1f + hop * 0.18f
                    scaleY = 1f + hop * 0.18f
                }.drawBehind {
                    val corner = size.minDimension * 0.22f
                    drawRoundRect(body, cornerRadius = CornerRadius(corner))
                    val cell = size.minDimension / 4f
                    val r = size.minDimension * 0.085f
                    PIPS[face - 1].forEach { (x, y) -> drawCircle(pip, r, Offset(cell * (x + 1), cell * (y + 1))) }
                },
        )
    }
}

/** A pinned mood: tapped, the dial's free tiles turn into its mixes; tapped again, they come back. */
@Composable
fun MoodTile(env: HomeEnv, mood: DialPanel.Mood, active: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val title = env.viewModel.resolveMood(mood.title, mood.params)?.title ?: mood.title
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .homeTile(onClick = { env.viewModel.toggleDialMood(mood) }, glowCorner = DialCornerRadius)
                .fillMaxSize()
                .clip(RoundedCornerShape(DialCornerRadius))
                .background(if (active) colors.primary else colors.secondaryContainer)
                .padding(8.dp),
    ) {
        Text(
            title,
            color = if (active) colors.onPrimary else colors.onSecondaryContainer,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Discover radio: new songs near one the user plays a lot, a different seed each tap. */
fun playDiscoverRadio(env: HomeEnv, seeds: List<String>) {
    if (env.isListenTogetherGuest) return
    val seed = seeds.randomOrNull() ?: return
    env.playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = seed, playlistId = "RDATiXv$seed")))
}

/**
 * A dial tile while the dial is edited: bobbing, asleep under a cover that takes a tap (opens [onTap]) or a hold and
 * drag (moves it; [onDrop] gets the finger's travel), with a minus to take it off the dial.
 */
@Composable
fun DialEditCell(
    edit: DialEdit,
    index: Int,
    modifier: Modifier,
    onTap: () -> Unit,
    onRemove: (() -> Unit)?,
    onDrop: (Offset) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var drag by remember { mutableStateOf<Offset?>(null) }
    // the gestures below live as long as the cell, while the dial around them changes
    val tap by rememberUpdatedState(onTap)
    val drop by rememberUpdatedState(onDrop)
    Box(
        modifier
            .graphicsLayer {
                val d = drag
                val t = edit.clock.longValue
                translationX = d?.x ?: 0f
                translationY = d?.y ?: if (t == 0L) 0f else sin(t / 1000f * 3.5f + index * 1.9f) * 1.5.dp.toPx()
                val s = if (d != null) 1.06f else 0.9f
                scaleX = s
                scaleY = s
                shadowElevation = if (d != null) 12.dp.toPx() else 0f
            },
    ) {
        content()
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { drag = Offset.Zero },
                        onDragEnd = {
                            drag?.let(drop)
                            drag = null
                        },
                        onDragCancel = { drag = null },
                        onDrag = { change, amount ->
                            change.consume()
                            drag = (drag ?: Offset.Zero) + amount
                        },
                    )
                },
        )
        if (onRemove != null) {
            val colors = MaterialTheme.colorScheme
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset((-6).dp, (-6).dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.error)
                        .clickable(onClick = onRemove),
            ) {
                Icon(painterResource(R.drawable.remove), contentDescription = stringResource(R.string.arc_dial_remove), tint = colors.onError, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A free cell with nothing in it while editing: an outline to tap and fill. */
@Composable
fun EmptyDialCell(modifier: Modifier) {
    Box(modifier.fillMaxSize().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(DialCornerRadius)))
}

/** What a tapped cell can become, plus pinning its item there and taking it off the dial. */
@Composable
fun DialCellSheet(
    env: HomeEnv,
    current: DialPanel?,
    itemTitle: String?,
    onPin: (() -> Unit)?,
    onPut: (DialPanel?) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        if (itemTitle != null) CapsLabel(itemTitle, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        onPin?.let { SheetRow(stringResource(if (current is DialPanel.Item) R.string.arc_dial_unpin else R.string.arc_dial_pin_here), R.drawable.ic_push_pin, it) }
        onRemove?.let { SheetRow(stringResource(R.string.arc_dial_remove), R.drawable.remove, it) }
        CapsLabel(stringResource(R.string.arc_dial_put_here), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        if (current != null && current !is DialPanel.Item) SheetRow(stringResource(R.string.arc_dial_changing), R.drawable.shuffle) { onPut(null) }
        if (current != DialPanel.Continue) SheetRow(stringResource(R.string.arc_home_continue), R.drawable.play) { onPut(DialPanel.Continue) }
        if (current != DialPanel.Dice) SheetRow(stringResource(R.string.arc_dial_dice), R.drawable.casino) { onPut(DialPanel.Dice) }
        if (current != DialPanel.Radio) SheetRow(stringResource(R.string.arc_dial_radio), R.drawable.radio) { onPut(DialPanel.Radio) }
        if (current != DialPanel.Plus) SheetRow(stringResource(R.string.arc_dial_plus), R.drawable.add) { onPut(DialPanel.Plus) }
        if (current != DialPanel.Moods) SheetRow(stringResource(R.string.mood_and_genres), R.drawable.discover_tune) { onPut(DialPanel.Moods) }
        env.viewModel.moodChips().forEach { chip ->
            val mood = DialPanel.Mood(chip.title, chip.endpoint?.params)
            if (current != mood) SheetRow(stringResource(R.string.arc_dial_mood) + ": " + chip.title, R.drawable.discover_tune) { onPut(mood) }
        }
    }
}

@Composable
private fun SheetRow(title: String, icon: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}
