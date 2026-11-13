package com.metrolist.music.arc

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import com.metrolist.music.LocalPlayerAwareWindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.utils.makeTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Which of the user's own songs (phone and Drive) have been sorted. A song left out keeps its file and its place under
 * On device, but loses its library date, so mixes, shuffle-all and the library leave it alone.
 */
object SongTriage {
    @Volatile private var decided: MutableSet<String>? = null

    private fun file(context: Context) = File(context.filesDir, "arc-triage.txt")

    fun decided(context: Context): Set<String> = synchronized(this) {
        decided ?: runCatching { file(context).readLines().filterTo(HashSet()) { it.isNotBlank() } }
            .getOrDefault(HashSet()).also { decided = it }
    }

    fun mark(context: Context, ids: Collection<String>) = synchronized(this) {
        val set = decided(context) as MutableSet<String>
        val fresh = ids.filter { set.add(it) }
        if (fresh.isNotEmpty()) runCatching { file(context).appendText(fresh.joinToString("\n", postfix = "\n")) }
    }

    fun unmark(context: Context, ids: Collection<String>) = synchronized(this) {
        val set = decided(context) as MutableSet<String>
        if (set.removeAll(ids.toSet())) runCatching { file(context).writeText(set.joinToString("\n", postfix = "\n")) }
    }

    fun isOwn(id: String) = DeviceMusic.isDeviceSong(id) || id.startsWith("DRIVE_")

    private suspend fun own(database: MusicDatabase) =
        database.songsWithIdPrefix("LOCAL_").first() + database.songsWithIdPrefix("DRIVE_").first()

    suspend fun unsorted(context: Context, database: MusicDatabase): List<Song> = withContext(Dispatchers.IO) {
        val done = decided(context)
        own(database).filter { it.id !in done }
    }

    suspend fun leftOut(context: Context, database: MusicDatabase): List<Song> = withContext(Dispatchers.IO) {
        val done = decided(context)
        own(database).filter { it.id in done && it.song.inLibrary == null }
    }

    enum class How { ONE, FOLDER, LIBRARY, RULE, EARLIER }

    /** One go of leaving songs out: one card, a whole folder, a selection in the library, or a length rule. */
    class Batch(val how: How, val label: String, val at: Long, val ids: Set<String>)

    private fun batchFile(context: Context) = File(context.filesDir, "arc-left-out.txt")

    fun batches(context: Context): List<Batch> = synchronized(this) {
        runCatching {
            batchFile(context).readLines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 4) return@mapNotNull null
                Batch(How.valueOf(parts[0]), parts[1], parts[2].toLong(), parts[3].split(',').filterTo(HashSet()) { it.isNotBlank() })
            }
        }.getOrDefault(emptyList())
    }

    private fun writeBatches(context: Context, list: List<Batch>) = synchronized(this) {
        runCatching {
            batchFile(context).writeText(list.filter { it.ids.isNotEmpty() }.joinToString("") {
                "${it.how}\t${it.label.replace('\t', ' ')}\t${it.at}\t${it.ids.joinToString(",")}\n"
            })
        }
    }

    private fun forget(context: Context, ids: Collection<String>) {
        val gone = ids.toSet()
        writeBatches(context, batches(context).map { Batch(it.how, it.label, it.at, it.ids - gone) })
    }

    /** Keeps the songs in the library or leaves them out of it, and counts them as sorted. */
    suspend fun apply(
        context: Context,
        database: MusicDatabase,
        songs: List<Song>,
        kept: Boolean,
        how: How = How.ONE,
        label: String = "",
    ) = withContext(Dispatchers.IO) {
        database.transaction {
            songs.forEach { s ->
                if (kept && s.song.inLibrary == null) update(s.song.copy(inLibrary = LocalDateTime.now()))
                if (!kept && s.song.inLibrary != null) update(s.song.copy(inLibrary = null))
            }
        }
        mark(context, songs.map { it.id })
        forget(context, songs.map { it.id })
        if (!kept && songs.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val ids = songs.mapTo(HashSet()) { it.id }
            val list = batches(context)
            val day = now / 86_400_000L
            val sameDay = list.indexOfLast { it.how == How.ONE && it.at / 86_400_000L == day }
            writeBatches(
                context,
                if (how == How.ONE && sameDay >= 0) {
                    list.mapIndexed { i, b -> if (i == sameDay) Batch(b.how, b.label, now, b.ids + ids) else b }
                } else {
                    list + Batch(how, label, now, ids)
                },
            )
        }
    }

    /** Puts the songs back as they were before they were sorted, unsorted again. */
    suspend fun restore(context: Context, database: MusicDatabase, songs: List<Song>) = withContext(Dispatchers.IO) {
        database.transaction { songs.forEach { update(it.song) } }
        unmark(context, songs.map { it.id })
        forget(context, songs.map { it.id })
    }

    suspend fun applyIds(context: Context, database: MusicDatabase, ids: Collection<String>, kept: Boolean) {
        val songs = withContext(Dispatchers.IO) { database.getSongsByIds(ids.filter(::isOwn)) }
        apply(context, database, songs, kept, How.LIBRARY)
    }
}

private class Decision(val songs: List<Song>, val kept: Boolean)

private val addedFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun SongTriageScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val deck = remember { mutableStateListOf<Song>() }
    val history = remember { mutableStateListOf<Decision>() }
    var loaded by remember { mutableStateOf(false) }
    var leftOutCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        deck.addAll(SongTriage.unsorted(context, database))
        leftOutCount = SongTriage.leftOut(context, database).size
        loaded = true
    }
    val current = deck.firstOrNull()
    val playingId = playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()?.value?.id
    val isPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle()?.value == true
    val previewing = isPlaying && playingId == current?.id
    // once the user has listened in the deck, every card that comes up plays on its own
    var listening by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var lengthRule by remember { mutableStateOf(false) }
    var artistFor by remember { mutableStateOf<List<Song>?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // the next cards sit in the player's queue, so moving on is a jump within it rather than a new queue
    var deckQueue by remember { mutableStateOf(emptyList<String>()) }
    DisposableEffect(playerConnection) {
        val player = playerConnection?.player
        val before = player?.pauseAtEndOfMediaItems
        player?.pauseAtEndOfMediaItems = true
        onDispose { if (before != null) player.pauseAtEndOfMediaItems = before }
    }

    fun play(song: Song, from: Long = 0L) {
        listening = true
        val connection = playerConnection ?: return
        val player = connection.player
        val at = deckQueue.indexOf(song.id)
        val ours = at >= 0 && player.mediaItemCount == deckQueue.size && player.getMediaItemAt(0).mediaId == deckQueue.first()
        if (ours) {
            player.seekTo(at, from)
            player.play()
        } else {
            val upcoming = deck.dropWhile { it.id != song.id }.ifEmpty { listOf(song) }.take(DECK_QUEUE)
            deckQueue = upcoming.map { it.id }
            connection.playQueue(ListQueue(context.getString(R.string.arc_triage_title), upcoming.map { it.toMediaItem() }, position = from))
        }
    }

    fun undo() {
        val last = history.removeLastOrNull() ?: return
        deck.addAll(0, last.songs)
        if (!last.kept) leftOutCount = (leftOutCount - last.songs.size).coerceAtLeast(0)
        scope.launch { SongTriage.restore(context, database, last.songs) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (listening) play(last.songs.first())
    }

    fun decide(songs: List<Song>, kept: Boolean, how: SongTriage.How = SongTriage.How.ONE, label: String = "", playNext: Boolean = true) {
        if (songs.isEmpty()) return
        deck.removeAll(songs.toSet())
        history.add(Decision(songs, kept))
        if (!kept) leftOutCount += songs.size
        scope.launch { SongTriage.apply(context, database, songs, kept, how, label) }
        if (listening && playNext) deck.firstOrNull()?.let { play(it) } ?: playerConnection?.player?.pause()
        if (songs.size > 1) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val message = context.resources.getQuantityString(
                    if (kept) R.plurals.arc_triage_kept_n else R.plurals.arc_triage_left_out_n, songs.size, songs.size,
                )
                val result = snackbar.showSnackbar(message, context.getString(R.string.arc_triage_undo), duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed && history.lastOrNull()?.songs == songs) undo()
            }
        }
    }

    val info by produceState<Pair<String?, String?>>(null to null, current?.id) {
        value = null to null
        val id = current?.id ?: return@produceState
        value = when {
            DeviceMusic.isDeviceSong(id) -> withContext(Dispatchers.IO) { DeviceMusic.fileInfo(context, id) }
            id.startsWith("DRIVE_") -> context.getString(R.string.arc_drive) to null
            else -> null to null
        }
    }
    val folder = info.first
    val folderSongs by produceState<Set<String>>(emptySet(), folder, current?.id) {
        val f = folder
        value = if (f != null && current != null && DeviceMusic.isDeviceSong(current.id)) {
            withContext(Dispatchers.IO) { DeviceMusic.songsInFolder(context, f) }.toSet()
        } else {
            emptySet()
        }
    }
    val inFolder = deck.filter { it.id in folderSongs }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
            if (leftOutCount > 0) {
                TextButton(onClick = { navController.navigate("arc/left_out") }) {
                    Text(stringResource(R.string.arc_triage_left_out_count, leftOutCount))
                }
            }
            if (history.isNotEmpty()) {
                IconButton(onClick = ::undo) {
                    Icon(painterResource(R.drawable.replay), contentDescription = stringResource(R.string.arc_triage_undo))
                }
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = deck.isNotEmpty()) {
                    Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.arc_triage_length_rule)) },
                        onClick = {
                            menu = false
                            lengthRule = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.arc_triage_keep_all)) },
                        onClick = {
                            menu = false
                            decide(deck.toList(), kept = true)
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = deck.size.toString(), fontSize = 72.sp, fontWeight = FontWeight.Thin, lineHeight = 72.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(bottom = 12.dp)) {
                TriageLabel(stringResource(R.string.arc_triage_left))
                Text(stringResource(R.string.arc_triage_title), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (loaded && current == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.arc_triage_done), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.arc_triage_done_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            deck.getOrNull(1)?.let { next ->
                key(next.id) {
                    TriageCard(next, null to null, Modifier.graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; alpha = 0.5f; translationY = 18.dp.toPx() })
                }
            }
            current?.let { song ->
                key(song.id) {
                    SwipeCard(
                        // the next song starts as the card is let go, not once it has flown off
                        onRelease = { if (listening) deck.getOrNull(1)?.let { play(it) } ?: playerConnection?.player?.pause() },
                        onDecide = { kept -> decide(listOf(song), kept, playNext = false) },
                        onTap = { if (previewing) playerConnection?.player?.pause() else play(song) },
                    ) { stamp -> TriageCard(song, info, stamp = stamp) }
                }
            }
        }

        if (current != null) {
            val position by produceState(0L, previewing, current.id) {
                val player = playerConnection?.player
                if (player == null || player.currentMediaItem?.mediaId != current.id) {
                    value = 0L
                    return@produceState
                }
                do {
                    value = player.currentPosition
                    delay(250)
                } while (previewing)
            }
            PreviewBar(position, current.song.duration * 1000L) { at ->
                if (playingId == current.id) {
                    playerConnection?.player?.seekTo(at)
                    playerConnection?.player?.play()
                } else {
                    play(current, at)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundAction(R.drawable.close, stringResource(R.string.arc_triage_stamp_out), 60, tint = MaterialTheme.colorScheme.error) { decide(listOf(current), false) }
                RoundAction(if (previewing) R.drawable.pause else R.drawable.play, null, 72, filled = true) {
                    if (previewing) {
                        listening = false
                        playerConnection?.player?.pause()
                    } else {
                        play(current)
                    }
                }
                RoundAction(R.drawable.check, stringResource(R.string.arc_triage_stamp_in), 60, tint = MaterialTheme.colorScheme.primary) { decide(listOf(current), true) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { artistFor = listOf(current) }) {
                    Text(stringResource(R.string.arc_set_artist))
                }
            }
            if (inFolder.size > 1) {
                TriageLabel(stringResource(R.string.arc_triage_whole_folder, inFolder.size))
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { decide(inFolder, kept = false, SongTriage.How.FOLDER, folder.orEmpty()) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.arc_triage_out_n, inFolder.size), maxLines = 1)
                    }
                    androidx.compose.material3.OutlinedButton(onClick = { artistFor = inFolder }, modifier = Modifier.weight(0.8f)) {
                        Text(stringResource(R.string.arc_triage_artist), maxLines = 1)
                    }
                    androidx.compose.material3.FilledTonalButton(onClick = { decide(inFolder, kept = true) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.arc_triage_keep_n, inFolder.size), maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbar)
    }
    if (lengthRule) {
        LengthRuleDialog(
            songs = deck.toList(),
            onDismiss = { lengthRule = false },
            onKeepLonger = { seconds ->
                lengthRule = false
                decide(deck.filter { it.song.duration >= seconds }, kept = true)
            },
            onLeaveShorter = { seconds ->
                lengthRule = false
                decide(deck.filter { it.song.duration < seconds }, kept = false, SongTriage.How.RULE, makeTimeString(seconds * 1000L))
            },
        )
    }
    artistFor?.let { songs ->
        SetArtistDialog(
            songIds = songs.map { it.id },
            initial = songs.first().artists.joinToString { it.name },
            onDismiss = { artistFor = null },
            onDone = {
                artistFor = null
                scope.launch {
                    val fresh = withContext(Dispatchers.IO) { database.getSongsByIds(songs.map { it.id }) }.associateBy { it.id }
                    deck.replaceAll { fresh[it.id] ?: it }
                }
            },
        )
    }
}

@Composable
private fun LengthRuleDialog(songs: List<Song>, onDismiss: () -> Unit, onKeepLonger: (Int) -> Unit, onLeaveShorter: (Int) -> Unit) {
    var seconds by remember { mutableStateOf(90f) }
    val cut = (seconds / 15f).roundToInt() * 15
    val longer = songs.count { it.song.duration >= cut }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arc_triage_length_rule)) },
        text = {
            Column {
                Text(makeTimeString(cut * 1000L), fontSize = 44.sp, fontWeight = FontWeight.Thin, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                androidx.compose.material3.Slider(value = seconds, onValueChange = { seconds = it }, valueRange = 15f..900f)
                Text(
                    stringResource(R.string.arc_triage_length_split, longer, songs.size - longer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onKeepLonger(cut) }) { Text(stringResource(R.string.arc_triage_keep_n, longer)) } },
        dismissButton = { TextButton(onClick = { onLeaveShorter(cut) }) { Text(stringResource(R.string.arc_triage_out_n, songs.size - longer)) } },
    )
}

/** The user's own songs left out of the library, grouped by how they were left out, each a tap from being kept. */
@Composable
fun LeftOutScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val songs = remember { mutableStateListOf<Song>() }
    var batches by remember { mutableStateOf(emptyList<SongTriage.Batch>()) }
    LaunchedEffect(Unit) {
        songs.addAll(SongTriage.leftOut(context, database))
        batches = withContext(Dispatchers.IO) { SongTriage.batches(context) }
    }
    val groups = remember(songs.toList(), batches) {
        val byId = songs.associateBy { it.id }
        val placed = HashSet<String>()
        val list = batches.sortedByDescending { it.at }.mapNotNull { batch ->
            val inside = batch.ids.mapNotNull { id -> byId[id]?.takeIf { placed.add(id) } }
            if (inside.isEmpty()) null else batch to inside
        }
        val rest = songs.filter { it.id !in placed }
        if (rest.isEmpty()) list else list + (SongTriage.Batch(SongTriage.How.EARLIER, "", 0L, emptySet()) to rest)
    }
    fun keep(chosen: List<Song>) {
        songs.removeAll(chosen.toSet())
        scope.launch { SongTriage.apply(context, database, chosen, kept = true) }
    }
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = state,
            contentPadding = insets,
            modifier = Modifier.arcScrollbar(state, insets),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text(songs.size.toString(), fontSize = 64.sp, fontWeight = FontWeight.Thin, lineHeight = 64.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(bottom = 10.dp)) {
                        TriageLabel(stringResource(R.string.arc_triage_out))
                        Text(stringResource(R.string.arc_left_out_title), style = MaterialTheme.typography.titleMedium)
                    }
                    if (songs.isNotEmpty()) {
                        TextButton(onClick = { keep(songs.toList()) }) { Text(stringResource(R.string.arc_triage_keep_all)) }
                    }
                }
            }
            groups.forEach { (batch, inside) ->
                item(key = "batch_${batch.how}_${batch.at}") {
                    val name = when (batch.how) {
                        SongTriage.How.ONE -> stringResource(R.string.arc_left_out_one)
                        SongTriage.How.FOLDER -> batch.label.ifEmpty { stringResource(R.string.arc_left_out_folder) }
                        SongTriage.How.LIBRARY -> stringResource(R.string.arc_left_out_library)
                        SongTriage.How.RULE -> stringResource(R.string.arc_left_out_rule, batch.label)
                        SongTriage.How.EARLIER -> stringResource(R.string.arc_left_out_earlier)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            TriageLabel(
                                listOfNotNull(
                                    if (batch.how == SongTriage.How.FOLDER) stringResource(R.string.arc_left_out_folder) else null,
                                    batch.at.takeIf { it > 0 }?.let {
                                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(addedFormat)
                                    },
                                    inside.size.toString(),
                                ).joinToString(" · "),
                            )
                            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { keep(inside) }) { Text(stringResource(R.string.arc_triage_keep_n, inside.size)) }
                    }
                }
                items(inside, key = { it.id }) { song ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playerConnection?.playQueue(ListQueue(title = song.song.title, items = listOf(song.toMediaItem()))) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        AsyncImage(
                            model = song.song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(song.artists.joinToString { it.name }.ifEmpty { null }, makeTimeString(song.song.duration * 1000L))
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { keep(listOf(song)) }) { Text(stringResource(R.string.arc_triage_keep)) }
                    }
                }
            }
        }
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )
    }
}

@Composable
private fun SwipeCard(onRelease: () -> Unit, onDecide: (Boolean) -> Unit, onTap: () -> Unit, content: @Composable (Float) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    var width by remember { mutableStateOf(1f) }
    var past by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = x.value
                translationY = y.value
                rotationZ = x.value / size.width * 14f
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val threshold = size.width * 0.28f
                        if (abs(x.value) > threshold) {
                            val direction = sign(x.value)
                            onRelease()
                            scope.launch {
                                x.animateTo(direction * size.width * 1.6f, ArcMotion.snappy())
                                onDecide(direction > 0)
                            }
                        } else {
                            scope.launch { x.animateTo(0f, ArcMotion.bouncy()) }
                            scope.launch { y.animateTo(0f, ArcMotion.bouncy()) }
                        }
                        past = false
                    },
                ) { change, drag ->
                    change.consume()
                    scope.launch { x.snapTo(x.value + drag.x) }
                    scope.launch { y.snapTo(y.value + drag.y * 0.3f) }
                    val now = abs(x.value) > size.width * 0.28f
                    if (now != past) {
                        past = now
                        haptics.performHapticFeedback(if (now) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick)
                    }
                }
            },
    ) {
        content((x.value / (width * 0.28f)).coerceIn(-1f, 1f))
    }
}

@Composable
private fun TriageCard(song: Song, info: Pair<String?, String?>, modifier: Modifier = Modifier, stamp: Float = 0f) {
    val (folder, format) = info
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Box(Modifier.weight(1f, fill = false).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Icon(
                painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Center).size(56.dp),
            )
            AsyncImage(
                model = song.song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (stamp != 0f) {
                val color = if (stamp > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = abs(stamp) }.background(color.copy(alpha = 0.35f)))
                Stamp(
                    stringResource(if (stamp > 0f) R.string.arc_triage_stamp_in else R.string.arc_triage_stamp_out),
                    color,
                    abs(stamp),
                    Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(song.song.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            song.artists.joinToString { it.name }.ifEmpty { song.song.albumName.orEmpty() },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TriageFact(stringResource(R.string.arc_triage_length), makeTimeString(song.song.duration * 1000L))
            song.song.inLibrary?.let { TriageFact(stringResource(R.string.arc_triage_added), it.format(addedFormat)) }
            format?.let { TriageFact(stringResource(R.string.arc_triage_format), it) }
        }
        folder?.let {
            Spacer(Modifier.height(8.dp))
            TriageFact(stringResource(R.string.arc_triage_folder), it)
        }
    }
}

@Composable
private fun Stamp(text: String, color: Color, strength: Float, modifier: Modifier) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 40.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 4.sp,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer {
                alpha = strength
                rotationZ = -8f
                scaleX = 0.8f + 0.2f * strength
                scaleY = scaleX
            }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 6.dp),
    )
}

@Composable
private fun TriageLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TriageFact(label: String, value: String) {
    Column {
        TriageLabel(label)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Where the preview is, and a place to tap or drag to jump through the song. */
@Composable
private fun PreviewBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val length = duration.coerceAtLeast(1L)
    val shown = dragging ?: (position.toFloat() / length).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(length) {
                    detectTapGestures { onSeek((it.x / size.width * length).toLong().coerceIn(0L, length)) }
                }
                .pointerInput(length) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { onSeek((it * length).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        change.consume()
                        dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Box(Modifier.fillMaxWidth(shown).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(makeTimeString((shown * length).toLong()), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(makeTimeString(duration), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val DECK_QUEUE = 20

@Composable
private fun RoundAction(icon: Int, label: String?, sizeDp: Int, filled: Boolean = false, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(sizeDp.dp)
                .tapBounce()
                .clip(CircleShape)
                .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = label,
                tint = if (filled) MaterialTheme.colorScheme.onPrimary else tint,
                modifier = Modifier.size((sizeDp * 0.42f).dp),
            )
        }
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            TriageLabel(label)
        }
    }
}

/** The way into [SongTriageScreen] above a list of the user's own songs, and to the ones left out. */
@Composable
fun TriageBanner(count: Int, leftOut: Int, onClick: () -> Unit, onLeftOut: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (count > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(count.toString(), fontSize = 40.sp, fontWeight = FontWeight.Thin, lineHeight = 40.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.arc_triage_banner, count, count),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        stringResource(R.string.arc_triage_banner_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    )
                }
                Icon(painterResource(R.drawable.swipe), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        if (leftOut > 0) {
            TextButton(onClick = onLeftOut) {
                Text(stringResource(R.string.arc_triage_left_out_count, leftOut))
            }
        }
    }
}
