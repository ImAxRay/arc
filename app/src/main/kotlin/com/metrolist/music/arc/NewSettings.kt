package com.metrolist.music.arc

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings added in this version: a NEW tag on their rows and a list at the top of Settings saying what each does.
 * A row's tag goes once the row is tapped; the list stays until its cross is pressed. Both go two weeks after this version first ran.
 */
object NewSettings {
    class Entry(val id: String, val title: Int, val desc: Int, val route: String)

    // Bump the version and replace the list for the next release; the seen set resets with it.
    private const val VERSION = 907
    val entries = listOf(
        Entry("visual_mode", R.string.arc_visual_mode, R.string.arc_new_visual_mode, "settings/appearance"),
        Entry("repeat_one", R.string.arc_repeat_one_persists, R.string.arc_new_repeat_one, "settings/queue"),
        Entry("storage", R.string.storage, R.string.arc_new_storage, "settings/storage"),
        Entry("playlist_file", R.string.arc_import_playlist_file, R.string.arc_new_playlist_file, "settings/backup_restore"),
        Entry("spotify_export", R.string.arc_spotify_export, R.string.arc_new_spotify_export, "settings/backup_restore"),
        Entry("android_auto", R.string.android_auto, R.string.arc_new_android_auto, "settings/android_auto"),
    )

    private const val KEEP_MS = 14L * 24 * 60 * 60 * 1000
    private val SinceKey = stringPreferencesKey("arcNewSince")
    private val SeenKey = stringSetPreferencesKey("arcNewSeen")
    private val ListClosedKey = stringPreferencesKey("arcNewListClosed")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ids still new; null until read. */
    val active = MutableStateFlow<Set<String>?>(null)
    val listOpen = MutableStateFlow(false)

    internal fun load(context: Context) {
        if (active.value != null) return
        scope.launch {
            val now = System.currentTimeMillis()
            val prefs = context.dataStore.data.first()
            val since = prefs[SinceKey]?.split('@')?.takeIf { it.firstOrNull() == "$VERSION" }?.getOrNull(1)?.toLongOrNull()
            if (since == null) {
                context.dataStore.edit {
                    it[SinceKey] = "$VERSION@$now"
                    it[SeenKey] = emptySet()
                }
            }
            val seen = if (since == null) emptySet() else prefs[SeenKey].orEmpty()
            val expired = since != null && now - since > KEEP_MS
            listOpen.value = !expired && prefs[ListClosedKey] != "$VERSION"
            active.value = if (expired) emptySet() else entries.map { it.id }.toSet() - seen
        }
    }

    fun seen(context: Context, vararg ids: String) {
        val current = active.value ?: return
        if (ids.none { it in current }) return
        active.value = current - ids.toSet()
        scope.launch { context.dataStore.edit { it[SeenKey] = it[SeenKey].orEmpty() + ids } }
    }

    fun closeList(context: Context) {
        listOpen.value = false
        seen(context, *entries.map { it.id }.toTypedArray())
        scope.launch { context.dataStore.edit { it[ListClosedKey] = "$VERSION" } }
    }
}

@Composable
fun rememberNewSettings(): Set<String> {
    val context = LocalContext.current
    LaunchedEffect(Unit) { NewSettings.load(context) }
    val active by NewSettings.active.collectAsState()
    return active.orEmpty()
}

/** The small NEW tag beside a row's title. */
@Composable
fun NewTag(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.arc_new_tag),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onTertiary,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.tertiary).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Top of Settings: what this version added, one line each; a tap jumps to the setting, the cross hides the list. */
@Composable
fun NewSettingsList(navController: NavController) {
    val context = LocalContext.current
    val active = rememberNewSettings()
    val open by NewSettings.listOpen.collectAsState()
    if (!open) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.arc_new_in, displayVersion(BuildConfig.BASE_VERSION_NAME)),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        IconButton(onClick = { NewSettings.closeList(context) }) {
            Icon(painterResource(R.drawable.close), stringResource(R.string.arc_new_hide), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
    NewSettings.entries.forEach { entry ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        NewSettings.seen(context, entry.id)
                        SettingsHighlight.pending = context.getString(entry.title)
                        navController.navigate(entry.route)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
                if (entry.id in active) {
                    Spacer(Modifier.width(8.dp))
                    NewTag()
                }
            }
            Text(stringResource(entry.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.padding(bottom = 12.dp))
}
