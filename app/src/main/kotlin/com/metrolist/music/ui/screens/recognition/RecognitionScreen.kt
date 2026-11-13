/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.recognition

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.metrolist.music.arc.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.entities.RecognitionHistory
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.shazamkit.models.RecognitionResult
import com.metrolist.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    navController: NavController,
    autoStart: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    // Only reset in Ready state: Listening/Processing belong to a running widget-service
    // recognition that must not be cancelled; Success/NoMatch/Error are results pending
    // display and history saving.
    LaunchedEffect(Unit) {
        if (com.metrolist.music.recognition.MusicRecognitionService.recognitionStatus.value
                is RecognitionStatus.Ready
        ) {
            com.metrolist.music.recognition.MusicRecognitionService
                .reset()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.metrolist.music.recognition.MusicRecognitionService
                .reset()
        }
    }

    // Observe recognition status from service for real-time updates (Listening -> Processing -> Result)
    val recognitionStatus by com.metrolist.music.recognition.MusicRecognitionService.recognitionStatus
        .collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                coroutineScope.launch {
                    com.metrolist.music.recognition.MusicRecognitionService
                        .recognize(context)
                }
            }
        }

    fun startRecognition() {
        if (hasPermission) {
            coroutineScope.launch {
                com.metrolist.music.recognition.MusicRecognitionService
                    .recognize(context)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (autoStart &&
            com.metrolist.music.recognition.MusicRecognitionService.recognitionStatus.value
                is RecognitionStatus.Ready
        ) {
            startRecognition()
        }
    }

    fun resetToReady() {
        com.metrolist.music.recognition.MusicRecognitionService
            .reset()
    }

    fun saveToHistory(result: RecognitionResult) {
        coroutineScope.launch(Dispatchers.IO) {
            database.query {
                insert(
                    RecognitionHistory(
                        trackId = result.trackId,
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        coverArtUrl = result.coverArtUrl,
                        coverArtHqUrl = result.coverArtHqUrl,
                        genre = result.genre,
                        releaseDate = result.releaseDate,
                        label = result.label,
                        shazamUrl = result.shazamUrl,
                        appleMusicUrl = result.appleMusicUrl,
                        spotifyUrl = result.spotifyUrl,
                        isrc = result.isrc,
                        youtubeVideoId = result.youtubeVideoId,
                        recognizedAt = LocalDateTime.now(),
                    ),
                )
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(
                onClick = { navController.navigateUp() },
                onLongClick = { navController.backToMain() },
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { navController.navigate("recognition_history") }) {
                Icon(painterResource(R.drawable.history), contentDescription = stringResource(R.string.recognition_history))
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
        ) {
            AnimatedContent(
                targetState = recognitionStatus,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                contentKey = { it::class },
                label = "recognition_content",
            ) { status ->
                when (status) {
                    is RecognitionStatus.Ready ->
                        ListenStage(RingMode.READY, stringResource(R.string.tap_to_recognize), onRing = ::startRecognition)

                    is RecognitionStatus.Listening ->
                        ListenStage(
                            RingMode.LISTENING,
                            stringResource(R.string.listening),
                            onRing = { com.metrolist.music.recognition.MusicRecognitionService.reset() },
                            action = stringResource(R.string.cancel) to { com.metrolist.music.recognition.MusicRecognitionService.reset() },
                        )

                    is RecognitionStatus.Processing ->
                        ListenStage(RingMode.MATCHING, stringResource(R.string.processing), onRing = {})

                    is RecognitionStatus.Success ->
                        SuccessState(
                            result = status.result,
                            onPlayOnApp = { result ->
                                navController.navigate(SearchRoutes.resultRoute("${result.title} ${result.artist}"))
                            },
                            onTryAgain = ::startRecognition,
                            onClose = ::resetToReady,
                            onSaveToHistory = ::saveToHistory,
                        )

                    is RecognitionStatus.NoMatch ->
                        MissState(stringResource(R.string.no_match_found), status.message, onTryAgain = ::startRecognition)

                    is RecognitionStatus.Error ->
                        MissState(stringResource(R.string.recognition_error), status.message, onTryAgain = ::startRecognition)
                }
            }
        }
    }
}

private enum class RingMode { READY, LISTENING, MATCHING }

private val RingSize = 184.dp

/** Small caps, spaced out, like the labels on the tape boxes Arc borrows from. */
@Composable
private fun StageLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 2.5.sp,
        color = color,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun QuietAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/**
 * One thin ring around the mic for every stage before a result: still when ready, faint rings
 * rippling out while listening, and Arc's line with its end dot running round while matching.
 */
@Composable
private fun ListenStage(mode: RingMode, label: String, onRing: () -> Unit, action: Pair<String, () -> Unit>? = null) {
    val line = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "listen")
    val ripple by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ripple")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(RingSize + 72.dp)
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        val radius = RingSize.toPx() / 2
                        when (mode) {
                            RingMode.READY -> drawCircle(line, radius, style = Stroke(stroke))
                            RingMode.LISTENING -> {
                                drawCircle(line, radius, style = Stroke(stroke))
                                repeat(3) { index ->
                                    val t = (ripple + index / 3f) % 1f
                                    drawCircle(line.copy(alpha = (1f - t) * 0.45f), radius + t * 36.dp.toPx(), style = Stroke(stroke * (1f - t * 0.5f)))
                                }
                            }
                            RingMode.MATCHING -> {
                                drawCircle(line.copy(alpha = 0.2f), radius, style = Stroke(stroke))
                                drawArc(
                                    line,
                                    startAngle = spin,
                                    sweepAngle = 110f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(stroke * 1.5f, cap = StrokeCap.Round),
                                )
                                val end = Math.toRadians((spin + 110f).toDouble())
                                drawCircle(line, stroke * 2.4f, Offset(center.x + radius * kotlin.math.cos(end).toFloat(), center.y + radius * kotlin.math.sin(end).toFloat()))
                            }
                        }
                    },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(RingSize).clip(CircleShape).clickable(onClick = onRing),
            ) {
                Icon(
                    painter = painterResource(if (mode == RingMode.MATCHING) R.drawable.music_note else R.drawable.arc_recognize),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = line,
                )
            }
        }
        StageLabel(label, if (mode == RingMode.READY) MaterialTheme.colorScheme.onSurfaceVariant else line)
        if (action != null) QuietAction(action.first, action.second) else Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SuccessState(
    result: RecognitionResult,
    onPlayOnApp: (RecognitionResult) -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
    onSaveToHistory: (RecognitionResult) -> Unit,
) {
    LaunchedEffect(result) { onSaveToHistory(result) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = result.coverArtHqUrl ?: result.coverArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(com.metrolist.music.constants.ThumbnailCornerRadius)),
        )
        Spacer(Modifier.height(28.dp))
        StageLabel(stringResource(R.string.arc_recognised))
        Spacer(Modifier.height(8.dp))
        Text(
            text = result.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(result.artist, result.album).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = { onPlayOnApp(result) }, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
            Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.play_on_app))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            QuietAction(stringResource(R.string.re_listen), onTryAgain)
            QuietAction(stringResource(R.string.close), onClose)
        }
    }
}

/** No match and errors look alike: a quiet note and a way to try again, nothing alarming. */
@Composable
private fun MissState(title: String, message: String, onTryAgain: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StageLabel(title, MaterialTheme.colorScheme.onSurface)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onTryAgain, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.try_again))
        }
    }
}
