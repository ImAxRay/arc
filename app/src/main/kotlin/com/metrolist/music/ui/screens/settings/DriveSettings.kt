package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.metrolist.music.arc.TopAppBar
import com.metrolist.music.arc.arcScroll
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import kotlinx.coroutines.launch

/** Google Drive as a music source: songs picked in Google's picker, or whole folders for testers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveSettings(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val signedIn by remember { DriveMusic.signedIn(context) }.collectAsState(initial = false)
    val wholeFolders by remember { DriveMusic.wholeFolders(context) }.collectAsState(initial = false)
    val accessEnds by remember { DriveMusic.folderAccessEnds(context) }.collectAsState(initial = null)
    val picked by remember { DriveMusic.picked(context) }.collectAsState(initial = emptyList())
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var unreachable by remember { mutableStateOf(emptySet<String>()) }
    val signIn = { folders: Boolean ->
        busy = true
        error = null
        scope.launch {
            DriveMusic.signIn(context, folders).onFailure { error = it.message }
            busy = false
        }
    }
    // The folders being browsed, from the top; empty means the two starting places.
    val path = remember { mutableStateListOf<DriveMusic.Picked>() }
    var children by remember { mutableStateOf<List<DriveMusic.Item>?>(null) }
    var sharedView by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(signedIn, wholeFolders, path.toList(), sharedView) {
        children = null
        if (!signedIn || !wholeFolders || sharedView == null) return@LaunchedEffect
        children = runCatching { DriveMusic.subfolders(context, path.lastOrNull()?.id ?: if (sharedView == true) null else "root") }
            .onFailure { error = it.message }
            .getOrDefault(emptyList())
    }

    val errorText: @Composable () -> Unit = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .arcScroll()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        if (!signedIn) {
            Text(
                stringResource(R.string.arc_drive_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
            )
            Material3SettingsGroup(
                title = stringResource(R.string.arc_sign_in_again),
                icon = R.drawable.cloud,
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.music_note),
                        title = { Text(stringResource(R.string.arc_drive_pick)) },
                        description = { Text(stringResource(R.string.arc_drive_pick_note)) },
                        enabled = !busy,
                        onClick = { signIn(false) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.folder),
                        title = { Text(stringResource(R.string.arc_drive_whole_folders)) },
                        description = { Text(stringResource(R.string.arc_drive_whole_folders_note)) },
                        enabled = !busy,
                        onClick = { signIn(true) },
                    ),
                ),
            )
            if (error != null) {
                Column(Modifier.padding(start = 4.dp, top = 12.dp)) { errorText() }
            }
        } else {
            Material3SettingsGroup(
                title = stringResource(R.string.account),
                icon = R.drawable.account,
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.cloud),
                        title = { Text(stringResource(if (wholeFolders) R.string.arc_drive_whole_folders else R.string.arc_drive_pick)) },
                        description = accessEnds?.let { ends ->
                            {
                                Text(stringResource(R.string.arc_drive_access_until, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(ends))))
                            }
                        },
                        trailingContent = accessEnds?.let {
                            { TextButton(enabled = !busy, onClick = { signIn(true) }) { Text(stringResource(R.string.arc_sign_in_again)) } }
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.sync),
                        title = { Text(stringResource(R.string.arc_drive_sync)) },
                        description = when {
                            busy -> { { Text(stringResource(R.string.arc_drive_syncing)) } }
                            error != null -> errorText
                            else -> null
                        },
                        trailingContent = if (busy) { { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } } else null,
                        enabled = picked.isNotEmpty() && !busy,
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                runCatching { DriveMusic.sync(context, database) }
                                    .onSuccess { result ->
                                        unreachable = result.unreachable.map { it.id }.toSet()
                                        if (result.unreachable.isNotEmpty()) error = context.getString(R.string.arc_drive_unreachable)
                                        android.widget.Toast.makeText(context, context.getString(R.string.arc_drive_synced, result.added), android.widget.Toast.LENGTH_SHORT).show()
                                    }.onFailure { error = it.message }
                                busy = false
                            }
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.logout),
                        title = { Text(stringResource(R.string.arc_drive_sign_out)) },
                        onClick = { scope.launch { DriveMusic.signOut(context) } },
                    ),
                ),
            )

            Spacer(Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.arc_drive_folders),
                icon = R.drawable.library_music,
                items = buildList {
                    if (picked.isEmpty()) {
                        add(Material3SettingsItem(title = { Text(stringResource(R.string.arc_drive_no_folders)) }))
                    }
                    picked.forEach { item ->
                        add(
                            Material3SettingsItem(
                                icon = painterResource(if (item.folder) R.drawable.folder else R.drawable.music_note),
                                title = { Text(item.name) },
                                description = if (item.id in unreachable) {
                                    { Text(stringResource(R.string.arc_drive_cant_open), color = MaterialTheme.colorScheme.error) }
                                } else null,
                                trailingContent = {
                                    androidx.compose.material3.IconButton(onClick = { scope.launch { DriveMusic.setPicked(context, picked - item) } }) {
                                        Icon(painterResource(R.drawable.close), null)
                                    }
                                },
                            ),
                        )
                    }
                    if (!wholeFolders) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.add),
                                title = { Text(stringResource(R.string.arc_drive_pick_more)) },
                                enabled = !busy,
                                onClick = { signIn(false) },
                            ),
                        )
                    }
                },
            )

            if (wholeFolders) {
                Spacer(Modifier.height(27.dp))
                Material3SettingsGroup(
                    title = stringResource(R.string.arc_drive_browse),
                    icon = R.drawable.folder,
                    items = buildList {
                        if (sharedView == null) {
                            add(folderItem(stringResource(R.string.arc_drive_my_drive)) { sharedView = false })
                            add(folderItem(stringResource(R.string.arc_drive_shared)) { sharedView = true })
                        } else {
                            val here = path.lastOrNull()
                            add(
                                Material3SettingsItem(
                                    icon = painterResource(R.drawable.arrow_back),
                                    title = { Text(here?.name ?: stringResource(if (sharedView == true) R.string.arc_drive_shared else R.string.arc_drive_my_drive)) },
                                    trailingContent = here?.let {
                                        {
                                            OutlinedButton(
                                                onClick = { scope.launch { DriveMusic.setPicked(context, picked + here) } },
                                                enabled = picked.none { it.id == here.id },
                                            ) { Text(stringResource(R.string.arc_drive_use_folder)) }
                                        }
                                    },
                                    onClick = { if (path.isEmpty()) sharedView = null else path.removeAt(path.lastIndex) },
                                ),
                            )
                            val list = children
                            if (list == null) {
                                add(Material3SettingsItem(leadingContent = { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }, title = {}))
                            } else {
                                list.forEach { item -> add(folderItem(item.name) { path.add(DriveMusic.Picked(item.id, item.name)) }) }
                            }
                        }
                    },
                )
            }
        }
        Text(
            stringResource(R.string.arc_drive_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 16.dp),
        )
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
private fun folderItem(name: String, onClick: () -> Unit) = Material3SettingsItem(
    icon = painterResource(R.drawable.folder),
    title = { Text(name) },
    opensPage = true,
    onClick = onClick,
)
