/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import com.metrolist.music.arc.arcScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.metrolist.music.arc.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.lyrics.JapaneseDictionary
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.metrolist.music.constants.LyricsRomanizeAsMainKey
import com.metrolist.music.constants.LyricsRomanizeCyrillicByLineKey
import com.metrolist.music.constants.LyricsRomanizeList
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference

val defaultList = mutableListOf(
    "Japanese" to true,
    "Korean" to true,
    "Chinese" to true,
    "Hindi" to true,
    "Punjabi" to true,
    "Russian" to true,
    "Ukrainian" to true,
    "Serbian" to true,
    "Bulgarian" to true,
    "Belarusian" to true,
    "Kyrgyz" to true,
    "Macedonian" to true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomanizationSettings(
    navController: NavController
) {
    val (pref, prefValue) = rememberPreference(LyricsRomanizeList, "")

    val initialList = remember(pref) {
        if (pref.isEmpty()) defaultList
        else {
            val savedMap = pref.split(",").associate { entry ->
                val (lang, checked) = entry.split(":")
                lang to checked.toBoolean()
            }

            defaultList.map { (lang, defaultChecked) ->
                Pair(lang, savedMap[lang] ?: defaultChecked)
            }
        }
    }

    val states = remember(initialList) { mutableStateListOf(*initialList.toTypedArray()) }

    val parentState = when {
        states.all { it.component2() } -> ToggleableState.On
        states.none { it.component2() } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    val (lyricsRomanizeAsMain, onLyricsRomanizeAsMainChange) = rememberPreference(
        LyricsRomanizeAsMainKey,
        defaultValue = false
    )

    val (lyricsRomanizeCyrillicByLine, onLyricsRomanizeCyrillicByLineChange) = rememberPreference(
        LyricsRomanizeCyrillicByLineKey,
        defaultValue = false
    )

    var showLanguagePicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .arcScroll()
            .padding(horizontal = 16.dp)
    ) {
        val dictionaryInstalled by JapaneseDictionary.installed.collectAsState()
        val dictionaryProgress by JapaneseDictionary.progress.collectAsState()
        val dictionaryScope = rememberCoroutineScope()
        val dictionaryContext = LocalContext.current
        Material3SettingsGroup(
            title = stringResource(R.string.arc_romanize_what),
            icon = R.drawable.language,
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.romanize_languages)) },
                    description = {
                        Text(
                            states
                                .filter { it.second }
                                .joinToString { it.first }
                                .ifEmpty { stringResource(R.string.romanize_languages_none) },
                        )
                    },
                    icon = painterResource(R.drawable.language),
                    onClick = { showLanguagePicker = true },
                ),
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.arc_japanese_dictionary)) },
                    description = {
                        Text(
                            when {
                                dictionaryProgress != null -> stringResource(R.string.arc_japanese_dictionary_loading, ((dictionaryProgress ?: 0f) * 100).toInt())
                                dictionaryInstalled -> stringResource(R.string.arc_japanese_dictionary_installed)
                                else -> stringResource(R.string.arc_japanese_dictionary_desc, JapaneseDictionary.DOWNLOAD_MB)
                            },
                        )
                    },
                    icon = painterResource(R.drawable.language_korean_latin),
                    trailingContent = {
                        if (dictionaryProgress == null) {
                            TextButton(onClick = {
                                if (dictionaryInstalled) {
                                    JapaneseDictionary.remove()
                                } else {
                                    dictionaryScope.launch {
                                        JapaneseDictionary.install().onFailure {
                                            android.widget.Toast.makeText(dictionaryContext, it.message, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }) {
                                Text(stringResource(if (dictionaryInstalled) R.string.arc_japanese_dictionary_remove else R.string.arc_japanese_dictionary_download))
                            }
                        }
                    },
                ),
            ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.arc_romanize_how),
            icon = R.drawable.lyrics,
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.lyrics_romanize_as_main)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeAsMain,
                            onCheckedChange = onLyricsRomanizeAsMainChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeAsMain) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        )
                    },
                    icon = painterResource(R.drawable.queue_music)
                ),
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.line_by_line_option_title)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeCyrillicByLine,
                            onCheckedChange = onLyricsRomanizeCyrillicByLineChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeCyrillicByLine) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        )
                    },
                    icon = painterResource(R.drawable.info)
                )
            )
        )
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(R.string.romanize_languages)) },
            text = {
                LazyColumn {
                    itemsIndexed(states.toList()) { index, (language, checked) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        states[index] = Pair(language, !checked)
                                        prefValue(states.joinToString(",") { (lang, c) -> "$lang:$c" })
                                    },
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Text(language)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.lyrics_romanize_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
