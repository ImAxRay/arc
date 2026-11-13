package com.metrolist.music.arc.snippet

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.metrolist.music.arc.ArcMotion
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private val SyncKey = intPreferencesKey("arcSnippetSyncSpeaker")
private val SyncBluetoothKey = intPreferencesKey("arcSnippetSyncHeadphones")
private const val BARS = 120

/** Opens the editor over everything once a snippet has been recorded or a kept one opened, and the list of kept ones. */
@Composable
fun SnippetEditorHost() {
    val take by Snippet.take.collectAsStateWithLifecycle()
    val listOpen by Snippet.listOpen.collectAsStateWithLifecycle()
    if (listOpen) SnippetList()
    take?.let { current -> key(current) { SnippetEditor(current) { Snippet.take.value = null } } }
}

@Composable
private fun SnippetEditor(take: Snippet.Take, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val keepAll by rememberPreference(ArcSnippetKeepKey, true)
    var stored by remember { mutableStateOf(take.stored) }
    var confirmDelete by remember { mutableStateOf(false) }
    val (speakerSync, saveSpeakerSync) = rememberPreference(SyncKey, 0)
    val (bluetoothSync, saveBluetoothSync) = rememberPreference(SyncBluetoothKey, -1)
    // headphones take what the speaker taught, plus the usual Bluetooth delay, until the user sets their own
    val savedSync = if (take.bluetooth) bluetoothSync.takeIf { it >= 0 } ?: speakerSync else speakerSync
    val saveSync: (Int) -> Unit = if (take.bluetooth) saveBluetoothSync else saveSpeakerSync
    val kept = take.stored?.mix
    var sync by remember { mutableFloatStateOf((kept?.syncMs ?: savedSync).toFloat()) }
    var autoSynced by remember { mutableStateOf(false) }
    var touchedSync by remember { mutableStateOf(false) }
    LaunchedEffect(take) {
        if (kept != null) return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { take.findSync(expectedMs = savedSync) } ?: return@LaunchedEffect
        if (touchedSync) return@LaunchedEffect
        sync = found.toFloat()
        autoSynced = true
        if (!take.bluetooth) saveSpeakerSync(found)
    }
    var voiceGain by remember { mutableFloatStateOf(kept?.voiceGain ?: 1f) }
    var songGain by remember { mutableFloatStateOf(kept?.songGain ?: 0.8f) }
    var from by remember { mutableIntStateOf(kept?.from ?: 0) }
    var to by remember { mutableIntStateOf(kept?.to ?: take.frames) }
    var head by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var startAt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val mix = SnippetMix(from, to, songGain, voiceGain, sync.roundToInt())
    val currentMix by rememberUpdatedState(mix)

    val songPeaks = remember(take) { peaks(take.song, 2, BARS).normalized() }
    val voicePeaks = remember(take) { peaks(take.voice, 1, BARS).normalized() }

    LaunchedEffect(playing, startAt) {
        if (!playing) return@LaunchedEffect
        playerConnection?.player?.pause()
        withContext(Dispatchers.Default) {
            val format = AudioFormat.Builder()
                .setSampleRate(take.rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            val min = AudioTrack.getMinBufferSize(take.rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(format)
                .setBufferSizeInBytes(min * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val chunk = ShortArray(2048 * 2)
            var frame = startAt
            try {
                track.play()
                while (isActive) {
                    val m = currentMix
                    if (frame >= m.to) break
                    val n = minOf(2048, m.to - frame)
                    take.mixInto(chunk, frame, frame + n, m)
                    track.write(chunk, 0, n * 2)
                    frame += n
                    head = startAt + track.playbackHeadPosition
                }
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
        playing = false
        head = from
    }

    fun play(at: Int) {
        startAt = at.coerceIn(from, (to - take.rate / 10).coerceAtLeast(from))
        head = startAt
        playing = true
    }

    fun export(then: suspend (java.io.File) -> Unit) {
        if (busy) return
        busy = true
        playing = false
        saveSync(sync.roundToInt())
        scope.launch {
            runCatching { then(take.export(context, mix)) }
                .onFailure { Toast.makeText(context, it.message ?: it.javaClass.simpleName, Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    // Kept snippets are saved again on the way out when anything changed; with keeping off, a new take that wasn't
    // saved is let go.
    fun close() {
        playing = false
        val keep = stored != null || keepAll
        if (keep && stored?.mix != mix) {
            val app = context.applicationContext
            val first = stored == null
            val keeping = take
            val finalMix = mix
            saveSync(sync.roundToInt())
            SnippetStore.scope.launch {
                runCatching { SnippetStore.keep(app, keeping, finalMix) }
                    .onSuccess { if (first) withContext(Dispatchers.Main) { Toast.makeText(app, R.string.arc_snippet_saved, Toast.LENGTH_SHORT).show() } }
                    .onFailure { withContext(Dispatchers.Main) { Toast.makeText(app, it.message ?: it.javaClass.simpleName, Toast.LENGTH_LONG).show() } }
            }
        }
        onClose()
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.arc_snippet_delete_ask)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    playing = false
                    val app = context.applicationContext
                    val savedAt = stored?.savedAt
                    SnippetStore.scope.launch { SnippetStore.delete(app, take.id, savedAt) }
                    onClose()
                }) { Text(stringResource(R.string.arc_snippet_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .safeDrawingPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::close) { Icon(painterResource(R.drawable.close), contentDescription = null) }
                Spacer(Modifier.weight(1f))
                Label(stringResource(R.string.arc_snippet))
                IconButton(onClick = {
                    Snippet.listOpen.value = true
                    close()
                }) { Icon(painterResource(R.drawable.list), contentDescription = stringResource(R.string.arc_snippets)) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(take.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(take.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(makeTimeString((to - from) * 1000L / take.rate), fontSize = 64.sp, fontWeight = FontWeight.Thin, lineHeight = 64.sp, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.padding(bottom = 10.dp)) { Label(stringResource(R.string.arc_snippet_length)) }
                }
                Spacer(Modifier.height(16.dp))

                val songColor = MaterialTheme.colorScheme.onSurfaceVariant
                val voiceColor = MaterialTheme.colorScheme.primary
                val dim = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                val handle = MaterialTheme.colorScheme.onSurface
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .pointerInput(take) {
                            detectTapGestures { play((it.x / size.width * take.frames).toInt()) }
                        }
                        .pointerInput(take) {
                            var dragging = 0
                            detectDragGestures(
                                onDragStart = { start ->
                                    val at = start.x / size.width * take.frames
                                    dragging = if (abs(at - from) < abs(at - to)) -1 else 1
                                    playing = false
                                },
                                onDragEnd = { play(if (dragging < 0) from else (to - take.rate * 3).coerceAtLeast(from)) },
                            ) { change, _ ->
                                change.consume()
                                val at = (change.position.x / size.width * take.frames).toInt().coerceIn(0, take.frames)
                                if (dragging < 0) from = at.coerceAtMost(to - take.rate) else to = at.coerceAtLeast(from + take.rate)
                            }
                        },
                ) {
                    val bar = size.width / BARS
                    val mid = size.height / 2
                    val shift = sync / 1000f * take.rate / take.frames * size.width
                    for (i in 0 until BARS) {
                        val h = (songPeaks[i] * songGain).coerceIn(0.02f, 1f) * mid
                        drawRoundRect(songColor.copy(alpha = 0.35f), Offset(i * bar + bar * 0.2f, mid - h), Size(bar * 0.6f, h * 2), CornerRadius(bar))
                    }
                    for (i in 0 until BARS) {
                        val h = (voicePeaks[i] * voiceGain).coerceIn(0.02f, 1f) * mid * 0.8f
                        drawRoundRect(voiceColor, Offset(i * bar + bar * 0.3f - shift, mid - h), Size(bar * 0.4f, h * 2), CornerRadius(bar))
                    }
                    val left = from.toFloat() / take.frames * size.width
                    val right = to.toFloat() / take.frames * size.width
                    drawRect(dim, Offset.Zero, Size(left, size.height))
                    drawRect(dim, Offset(right, 0f), Size(size.width - right, size.height))
                    drawRoundRect(handle, Offset(left - 3.dp.toPx(), 0f), Size(6.dp.toPx(), size.height), CornerRadius(3.dp.toPx()))
                    drawRoundRect(handle, Offset(right - 3.dp.toPx(), 0f), Size(6.dp.toPx(), size.height), CornerRadius(3.dp.toPx()))
                    if (playing) {
                        val x = head.toFloat() / take.frames * size.width
                        drawRect(voiceColor, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(makeTimeString(from * 1000L / take.rate), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = songColor)
                    Spacer(Modifier.weight(1f))
                    Text(makeTimeString(to * 1000L / take.rate), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = songColor)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { if (playing) playing = false else play(from) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (playing) R.drawable.pause else R.drawable.play),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Level(stringResource(R.string.arc_snippet_voice), "${(voiceGain * 100).roundToInt()}%", voiceGain, 0f..3f) { voiceGain = it }
                Level(stringResource(R.string.arc_snippet_song), "${(songGain * 100).roundToInt()}%", songGain, 0f..1.5f) { songGain = it }
                val seconds = "%.2f".format(abs(sync) / 1000f)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label(stringResource(R.string.arc_snippet_sync))
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            sync.roundToInt() == 0 -> stringResource(R.string.arc_snippet_in_step)
                            sync > 0 -> stringResource(R.string.arc_snippet_earlier, seconds)
                            else -> stringResource(R.string.arc_snippet_later, seconds)
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                SyncWheel(sync, take.frames * 1000f / take.rate) {
                    sync = it
                    touchedSync = true
                    autoSynced = false
                }
                Text(
                    stringResource(
                        when {
                            autoSynced -> R.string.arc_snippet_synced
                            take.bluetooth -> R.string.arc_snippet_sync_bluetooth
                            else -> R.string.arc_snippet_sync_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (stored != null || keepAll) {
                    OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f), onClick = { confirmDelete = true }) {
                        Icon(painterResource(R.drawable.delete), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.arc_snippet_delete))
                    }
                } else {
                    OutlinedButton(
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (busy) return@OutlinedButton
                            busy = true
                            playing = false
                            saveSync(sync.roundToInt())
                            val app = context.applicationContext
                            val keeping = mix
                            scope.launch {
                                runCatching { SnippetStore.keep(app, take, keeping) }
                                    .onSuccess {
                                        stored = it
                                        Toast.makeText(context, R.string.arc_snippet_saved, Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { Toast.makeText(context, it.message ?: it.javaClass.simpleName, Toast.LENGTH_LONG).show() }
                                busy = false
                            }
                        },
                    ) {
                        Icon(painterResource(R.drawable.download), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.arc_snippet_save))
                    }
                }
                Button(enabled = !busy, modifier = Modifier.weight(1f), onClick = { export { shareSnippet(context, it) } }) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(painterResource(R.drawable.share), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.arc_snippet_share))
                }
            }
        }
    }
}

/** Every kept snippet, newest first; tapping one opens it in the editor, with this list waiting behind it. */
@Composable
private fun SnippetList() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val changes by SnippetStore.changes.collectAsStateWithLifecycle()
    var entries by remember { mutableStateOf<List<SnippetStore.Entry>?>(null) }
    var opening by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(changes) { entries = SnippetStore.list(context) }
    Dialog(
        onDismissRequest = { Snippet.listOpen.value = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .safeDrawingPadding(),
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Snippet.listOpen.value = false }) { Icon(painterResource(R.drawable.close), contentDescription = null) }
                Spacer(Modifier.weight(1f))
                Label(stringResource(R.string.arc_snippets))
            }
            val list = entries
            when {
                list == null -> Unit
                list.isEmpty() -> Text(
                    stringResource(R.string.arc_snippets_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = opening == null) {
                                    opening = entry.id
                                    scope.launch {
                                        val take = SnippetStore.load(context, entry.id)
                                        opening = null
                                        if (take != null) Snippet.take.value = take
                                        else Toast.makeText(context, R.string.arc_snippet_open_failed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = entry.cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${entry.artist} · ${DateUtils.getRelativeTimeSpanString(entry.id)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            if (opening == entry.id) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    makeTimeString(entry.lengthMs),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FloatArray.normalized(): FloatArray {
    val max = maxOrNull()?.takeIf { it > 0f } ?: return this
    return FloatArray(size) { this[it] / max }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Level(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(label)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = current, onValueChange = onChange, valueRange = range)
    }
}

/** While a snippet records: a bar at the top of the screen, clear of every player layout, with the time and the lock. */
@Composable
fun SnippetRecordingBar(modifier: Modifier = Modifier) {
    val recording by Snippet.recording.collectAsStateWithLifecycle()
    val shown by animateFloatAsState(if (recording != null) 1f else 0f, ArcMotion.bouncy(), label = "snippetBar")
    if (shown < 0.01f) return
    val rec = recording
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(rec?.startedAt) {
        val started = rec?.startedAt ?: return@LaunchedEffect
        while (true) {
            seconds = ((System.currentTimeMillis() - started) / 1000).toInt()
            delay(250)
        }
    }
    val dot by rememberInfiniteTransition(label = "recDot").animateFloat(
        1f, 0.25f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "recDotAlpha",
    )
    val locked = rec?.locked == true
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 8.dp)
            .graphicsLayer {
                alpha = shown.coerceIn(0f, 1f)
                translationY = (1f - shown) * -40.dp.toPx()
            }
            .background(MaterialTheme.colorScheme.inverseSurface, CircleShape)
            .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer { alpha = dot }
                .background(Color(0xFFE5484D), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        Spacer(Modifier.width(14.dp))
        Icon(
            painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { translationY = -Snippet.lockPull * 6.dp.toPx() },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (locked) R.string.arc_snippet_locked else R.string.arc_snippet_lock),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

/**
 * A ruler rolled sideways to move the voice against the song, as far as the snippet is long. Ticks every 10 ms,
 * a tap of feel every 100 ms, and a flick keeps it spinning.
 */
@Composable
private fun SyncWheel(valueMs: Float, maxMs: Float, onChange: (Float) -> Unit) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val change by rememberUpdatedState(onChange)
    val msPerPx = with(density) { MS_PER_DP / 1.dp.toPx() }
    var value by remember { mutableFloatStateOf(valueMs) }
    LaunchedEffect(valueMs) { if (abs(valueMs - value) > 0.5f) value = valueMs }

    fun set(next: Float): Boolean {
        val clamped = next.coerceIn(-maxMs, maxMs)
        if ((clamped / 100).toInt() != (value / 100).toInt()) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        value = clamped
        change(clamped)
        return clamped == next
    }

    val drag = rememberDraggableState { delta -> set(value - delta * msPerPx) }
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val centre = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = tick)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .draggable(
                drag,
                Orientation.Horizontal,
                onDragStopped = { velocity ->
                    var last = 0f
                    AnimationState(0f, -velocity * msPerPx).animateDecay(exponentialDecay(frictionMultiplier = 1.6f)) {
                        val moved = this.value - last
                        last = this.value
                        if (!set(value + moved)) cancelAnimation()
                    }
                },
            ),
    ) {
        val mid = size.width / 2
        val halfMs = mid * msPerPx
        val first = (((value - halfMs) / 10).toInt() - 1) * 10
        val last = (((value + halfMs) / 10).toInt() + 1) * 10
        for (t in first..last step 10) {
            if (t < -maxMs || t > maxMs) continue
            val x = mid + (t - value) / msPerPx
            val fade = (1f - abs(x - mid) / mid).coerceIn(0f, 1f)
            val major = t % 500 == 0
            val h = when {
                major -> size.height * 0.45f
                t % 100 == 0 -> size.height * 0.3f
                else -> size.height * 0.15f
            }
            drawLine(tick.copy(alpha = 0.25f + 0.6f * fade), Offset(x, size.height - h), Offset(x, size.height), strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx())
            if (major) {
                val text = measurer.measure("%.1fs".format(t / 1000f), labelStyle)
                drawText(text, topLeft = Offset(x - text.size.width / 2f, 0f), alpha = fade)
            }
        }
        drawLine(centre, Offset(mid, size.height * 0.2f), Offset(mid, size.height), strokeWidth = 3.dp.toPx())
    }
}

private const val MS_PER_DP = 4f
