package com.metrolist.music.arc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.roundToInt

/** Seconds; positive keeps songs at least this long, negative keeps shorter ones, 0 is off. */
val ArcSongLengthKey = intPreferencesKey("arcSongLength")

fun List<Song>.byLength(rule: Int): List<Song> =
    when {
        rule > 0 -> filter { it.song.duration >= rule }
        rule < 0 -> filter { it.song.duration in 1 until -rule }
        else -> this
    }

@Composable
fun LengthFilterChip(modifier: Modifier = Modifier) {
    val (rule, onRuleChange) = rememberPreference(ArcSongLengthKey, 0)
    var open by remember { mutableStateOf(false) }
    FilterChip(
        selected = rule != 0,
        onClick = { open = true },
        label = {
            Text(
                when {
                    rule > 0 -> "≥ " + makeTimeString(rule * 1000L)
                    rule < 0 -> "< " + makeTimeString(-rule * 1000L)
                    else -> stringResource(R.string.arc_length)
                },
            )
        },
        modifier = modifier,
    )
    if (open) {
        LengthDialog(
            rule = rule,
            onDismiss = { open = false },
            onApply = {
                onRuleChange(it)
                open = false
            },
        )
    }
}

@Composable
private fun LengthDialog(rule: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var seconds by remember { mutableFloatStateOf(if (rule == 0) 180f else abs(rule).toFloat()) }
    var longer by remember { mutableStateOf(rule >= 0) }
    val cut = (seconds / 15f).roundToInt() * 15
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arc_length)) },
        text = {
            Column {
                Text(
                    (if (longer) "≥ " else "< ") + makeTimeString(cut * 1000L),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Thin,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                )
                ArcSlider(value = seconds, onValueChange = { seconds = it }, valueRange = 15f..900f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(selected = longer, onClick = { longer = true }, label = { Text(stringResource(R.string.arc_length_longer)) })
                    FilterChip(selected = !longer, onClick = { longer = false }, label = { Text(stringResource(R.string.arc_length_shorter)) })
                }
                Text(
                    stringResource(R.string.arc_length_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(if (longer) cut else -cut) }) { Text(stringResource(R.string.arc_apply)) } },
        dismissButton = {
            if (rule != 0) TextButton(onClick = { onApply(0) }) { Text(stringResource(R.string.arc_length_off)) }
        },
    )
}
