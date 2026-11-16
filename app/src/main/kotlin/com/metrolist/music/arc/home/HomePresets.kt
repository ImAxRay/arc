package com.metrolist.music.arc.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.music.R
import com.metrolist.music.arc.CapsLabel
import com.metrolist.music.utils.rememberPreference

/** Layouts the user saved, each "name\nmodules" with modules as in [ArcHomeModulesKey]. */
val ArcHomePresetsKey = stringSetPreferencesKey("arcHomePresets")

/** Mood mixes on top, then today's rows in the order they come; everything else hidden. */
fun todaysPagePreset(rows: Collection<String>): String = (listOf(HomeModuleId.YOUTUBE_HOME.key) + rows).joinToString(",")

/** Default, Today's page and the user's own layouts; [current] is what Save keeps. "" applies the defaults. */
@Composable
fun PresetSheet(current: String, rows: Collection<String>, onApply: (name: String, modules: String) -> Unit) {
    var saved by rememberPreference(ArcHomePresetsKey, emptySet())
    var name by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.arc_home_preset_default)
    val todayName = stringResource(R.string.arc_home_preset_today)
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        CapsLabel(stringResource(R.string.arc_home_presets), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        PresetRow(defaultName, stringResource(R.string.arc_home_preset_default_about)) { onApply(defaultName, "") }
        PresetRow(todayName, stringResource(R.string.arc_home_preset_today_about)) { onApply(todayName, todaysPagePreset(rows)) }
        saved.sorted().forEach { entry ->
            val presetName = entry.substringBefore('\n')
            PresetRow(presetName, null, onDelete = { saved = saved - entry }) { onApply(presetName, entry.substringAfter('\n')) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace("\n", "") },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.arc_home_preset_name)) },
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val trimmed = name.trim()
                    // the same name replaces the older layout rather than listing it twice
                    saved = saved.filterNot { it.substringBefore('\n') == trimmed }.toSet() + "$trimmed\n$current"
                    name = ""
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(stringResource(R.string.arc_home_preset_save)) }
        }
    }
}

@Composable
private fun PresetRow(title: String, about: String?, onDelete: (() -> Unit)? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = about?.let { { Text(it) } },
        trailingContent =
            onDelete?.let {
                {
                    IconButton(onClick = it) { Icon(painterResource(R.drawable.delete), contentDescription = stringResource(R.string.delete)) }
                }
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}
