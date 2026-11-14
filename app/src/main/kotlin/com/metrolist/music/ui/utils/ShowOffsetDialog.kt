package com.metrolist.music.ui.utils

import com.metrolist.music.arc.ArcSlider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.lyrics.LyricsUtils
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.FlowPreview

private const val MAX_OFFSET = 10_000

@OptIn(FlowPreview::class)
@Composable
fun ShowOffsetDialog(songProvider: () -> SongEntity?) {
    val database = LocalDatabase.current
    val song = songProvider()
    var lyricsOffset by rememberSaveable { mutableIntStateOf(song?.lyricsOffset ?: 0) }
    var textFieldValue by rememberSaveable { mutableStateOf(lyricsOffset.toString()) }

    LaunchedEffect(song?.id) {
        song?.let {
            lyricsOffset = it.lyricsOffset
            textFieldValue = lyricsOffset.toString()
        }
    }

    LaunchedEffect(lyricsOffset) {
        songProvider()?.let { song ->
            database.query {
                upsert(
                    song.copy(
                        lyricsOffset = lyricsOffset
                    )
                )
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.fast_forward),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.lyrics_offset),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = textFieldValue,
                onValueChange = { newText ->
                    val sanitized = newText.filter {
                        it.isDigit() || (it == '-' && newText.indexOf('-') == 0)
                    }

                    val limited = if (sanitized.startsWith('-')) {
                        sanitized.take(6)
                    } else {
                        sanitized.take(5)
                    }

                    textFieldValue = limited

                    when {
                        limited.isEmpty() -> {
                            lyricsOffset = 0
                            textFieldValue = "0"
                        }

                        limited == "-" -> {
                        }

                        else -> {
                            limited.toIntOrNull()?.let { parsedValue ->
                                val clampedValue = parsedValue.coerceIn(-MAX_OFFSET, MAX_OFFSET)
                                lyricsOffset = clampedValue

                                if (parsedValue != clampedValue) {
                                    textFieldValue = clampedValue.toString()
                                }

                                if (clampedValue == 0 && limited.startsWith('-')) {
                                    textFieldValue = "0"
                                }
                            }
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.widthIn(min = 120.dp, max = 160.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = MaterialTheme.colorScheme.error
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "ms",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            if (lyricsOffset != 0) {
                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        lyricsOffset = 0
                        textFieldValue = "0"
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.replay),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = "Reset"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    lyricsOffset = (lyricsOffset - 50).coerceIn(-MAX_OFFSET, MAX_OFFSET)
                    textFieldValue = lyricsOffset.toString()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.remove),
                    contentDescription = "Decrease"
                )
            }

            ArcSlider(
                value = lyricsOffset.toFloat(),
                onValueChange = { newValue ->
                    val rounded = (newValue / 100).toInt() * 100
                    lyricsOffset = rounded
                    textFieldValue = rounded.toString()
                },
                valueRange = -MAX_OFFSET.toFloat()..MAX_OFFSET.toFloat(),
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    lyricsOffset = (lyricsOffset + 50).coerceIn(-MAX_OFFSET, MAX_OFFSET)
                    textFieldValue = lyricsOffset.toString()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = "Increase"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        ) {
            Text(
                text = "-${MAX_OFFSET / 1000}s",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "+${MAX_OFFSET / 1000}s",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TapToSync(song, lyricsOffset) {
            lyricsOffset = it
            textFieldValue = it.toString()
        }
    }
}

/**
 * Sync by ear: shows a line that is coming up and sets the offset from the moment the user taps as they hear
 * it start, instead of guessing a number of milliseconds.
 */
@Composable
private fun TapToSync(song: SongEntity?, offset: Int, onOffset: (Int) -> Unit) {
    val database = LocalDatabase.current
    val player = LocalPlayerConnection.current?.player ?: return
    val lyrics by database.lyrics(song?.id).collectAsState(initial = null)
    val lines = remember(lyrics?.lyrics) {
        lyrics?.lyrics?.let { LyricsUtils.parseLyrics(it) }.orEmpty().filter { it.text.isNotBlank() && it.time > 0 }
    }
    if (lines.isEmpty()) return
    var target by remember(lines) { mutableStateOf<Int?>(null) }
    LaunchedEffect(lines, target) {
        // until the user picks a line, aim at the one coming up next by the current offset
        while (target == null) {
            val shown = player.currentPosition + offset
            target = lines.indexOfFirst { it.time > shown + 1500 }.takeIf { it >= 0 }
            if (target == null) delay(500)
        }
    }
    val index = target ?: return
    val line = lines[index]

    Spacer(Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.lyrics_tap_sync_hint),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
                onOffset((line.time - player.currentPosition).toInt().coerceIn(-MAX_OFFSET, MAX_OFFSET))
                target = (index + 1).takeIf { it <= lines.lastIndex }
            }
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    TextButton(onClick = { target = (index + 1).takeIf { it <= lines.lastIndex } }) {
        Text(stringResource(R.string.lyrics_tap_sync_skip))
    }
}
