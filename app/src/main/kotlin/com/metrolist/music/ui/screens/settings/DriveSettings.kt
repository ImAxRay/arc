package com.metrolist.music.ui.screens.settings

import com.metrolist.music.arc.arcScroll
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.metrolist.music.arc.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

/** Google Drive as a music source: sign in, then pick the folders Arc may read songs from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveSettings(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val signedIn by remember { DriveMusic.signedIn(context) }.collectAsState(initial = false)
    val chosen by remember { DriveMusic.folders(context) }.collectAsState(initial = emptyList())
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // The folders being browsed, from the top; empty means the two starting places.
    val path = remember { mutableStateListOf<DriveMusic.Folder>() }
    var children by remember { mutableStateOf<List<DriveMusic.Item>?>(null) }
    var sharedView by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(signedIn, path.toList(), sharedView) {
        children = null
        if (!signedIn || sharedView == null) return@LaunchedEffect
        children = runCatching { DriveMusic.subfolders(context, path.lastOrNull()?.id ?: if (sharedView == true) null else "root") }
            .onFailure { error = it.message }
            .getOrDefault(emptyList())
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
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        DriveMusic.signIn(context).onFailure { error = it.message }
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.arc_drive_sign_in)) }
        } else {
            Text(stringResource(R.string.arc_drive_folders), style = MaterialTheme.typography.titleMedium)
            if (chosen.isEmpty()) {
                Text(stringResource(R.string.arc_drive_no_folders), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            chosen.forEach { folder ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.folder), null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(folder.name, modifier = Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = { scope.launch { DriveMusic.setFolders(context, chosen - folder) } }) {
                        Icon(painterResource(R.drawable.close), null)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.arc_drive_browse), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (sharedView == null) {
                FolderRow(stringResource(R.string.arc_drive_my_drive)) { sharedView = false }
                FolderRow(stringResource(R.string.arc_drive_shared)) { sharedView = true }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (path.isEmpty()) sharedView = null else path.removeAt(path.lastIndex) }) {
                        Text(stringResource(R.string.arc_drive_back))
                    }
                    Text(
                        path.lastOrNull()?.name ?: stringResource(if (sharedView == true) R.string.arc_drive_shared else R.string.arc_drive_my_drive),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    path.lastOrNull()?.let { here ->
                        OutlinedButton(onClick = { scope.launch { DriveMusic.setFolders(context, chosen + here) } }, enabled = chosen.none { it.id == here.id }) {
                            Text(stringResource(R.string.arc_drive_use_folder))
                        }
                    }
                }
                val list = children
                if (list == null) {
                    CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
                } else {
                    list.forEach { item -> FolderRow(item.name) { path.add(DriveMusic.Folder(item.id, item.name)) } }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row {
                Button(
                    enabled = chosen.isNotEmpty() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val added = runCatching { DriveMusic.sync(context, database) }.onFailure { error = it.message }.getOrDefault(0)
                            android.widget.Toast.makeText(context, context.getString(R.string.arc_drive_synced, added), android.widget.Toast.LENGTH_SHORT).show()
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

@Composable
private fun FolderRow(name: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Icon(painterResource(R.drawable.folder), null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(name)
    }
}
