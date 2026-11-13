package com.metrolist.music.arc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.DatabaseDao
import com.metrolist.music.db.entities.ArtistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gives songs the artists typed in, comma separated: existing artists by name, made-up ones created on the spot.
 * Artists of the user's own files are suggested first, then the library's, matching what is being typed.
 * [prepare] runs first in the same transaction, for a song that may not be saved yet.
 */
@Composable
fun SetArtistDialog(
    songIds: List<String>,
    initial: String = "",
    prepare: DatabaseDao.() -> Unit = {},
    onDismiss: () -> Unit,
    onDone: () -> Unit = {},
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val known by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) {
            (database.ownSongArtists() + database.libraryArtistEntities()).map { it.name }.distinctBy { it.lowercase() }
        }
    }
    val typing = field.text.substringAfterLast(',').trim()
    val taken = field.text.split(',').map { it.trim().lowercase() }.toSet()
    val suggestions = remember(known, typing, taken) {
        known.filter { it.lowercase() !in taken && (typing.isEmpty() || it.contains(typing, ignoreCase = true)) }
            .sortedBy { if (it.startsWith(typing, ignoreCase = true)) 0 else 1 }
            .take(6)
    }

    fun save() {
        val names = field.text.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
        if (names.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    database.withTransaction {
                        prepare()
                        val artists = names.map { name ->
                            artistByName(name) ?: ArtistEntity(id = ArtistEntity.generateArtistId(), name = name, isLocal = true)
                        }
                        songIds.forEach { replaceSongArtists(it, artists) }
                    }
                }
                songIds.forEach { id -> database.song(id).first()?.let { playerConnection?.refreshSongMetadata(it) } }
                onDone()
            }
        } else {
            onDone()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.person), null) },
        title = { Text(stringResource(R.string.arc_set_artist)) },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text(stringResource(R.string.artist_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    suggestions.forEach { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val before = field.text.substringBeforeLast(',', "").let { if (it.isBlank()) "" else "$it, " }
                                    val text = before + name
                                    field = TextFieldValue(text, TextRange(text.length))
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
