package com.metrolist.music.ui.screens.settings

import com.metrolist.music.arc.arcScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.metrolist.music.arc.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.arc.DriveMusic
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import kotlinx.coroutines.launch

/** Google Drive as a music source: pick folders or songs in Google's picker, then load them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveSettings(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val signedIn by remember { DriveMusic.signedIn(context) }.collectAsState(initial = false)
    val picked by remember { DriveMusic.picked(context) }.collectAsState(initial = emptyList())
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = {
        busy = true
        error = null
        scope.launch {
            DriveMusic.pick(context).onFailure { error = it.message }
            busy = false
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .arcScroll()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(72.dp))
        if (!signedIn) {
            Text(stringResource(R.string.arc_drive_intro), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(enabled = !busy, onClick = { pick() }) { Text(stringResource(R.string.arc_drive_pick)) }
        } else {
            Text(stringResource(R.string.arc_drive_folders), style = MaterialTheme.typography.titleMedium)
            if (picked.isEmpty()) {
                Text(stringResource(R.string.arc_drive_no_folders), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            picked.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(if (item.folder) R.drawable.folder else R.drawable.music_note), null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(item.name, modifier = Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = { scope.launch { DriveMusic.setPicked(context, picked - item) } }) {
                        Icon(painterResource(R.drawable.close), null)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(enabled = !busy, onClick = { pick() }) { Text(stringResource(R.string.arc_drive_pick_more)) }
            Spacer(Modifier.height(24.dp))
            Row {
                Button(
                    enabled = picked.isNotEmpty() && !busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            runCatching { DriveMusic.sync(context, database) }
                                .onSuccess { android.widget.Toast.makeText(context, context.getString(R.string.arc_drive_synced, it), android.widget.Toast.LENGTH_SHORT).show() }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.arc_drive_sync)) }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { scope.launch { DriveMusic.signOut(context) } }) { Text(stringResource(R.string.arc_drive_sign_out)) }
            }
            if (busy) Text(stringResource(R.string.arc_drive_syncing), style = MaterialTheme.typography.bodySmall)
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.arc_drive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    TopAppBar(
        title = { Text(stringResource(R.string.arc_drive)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}
