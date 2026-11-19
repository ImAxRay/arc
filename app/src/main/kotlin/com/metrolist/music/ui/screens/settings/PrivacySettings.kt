/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.metrolist.music.arc.ArcLearnUsageKey
import com.metrolist.music.arc.ArcSlider
import com.metrolist.music.arc.TopAppBar
import com.metrolist.music.arc.Usage
import com.metrolist.music.arc.arcScroll
import com.metrolist.music.arc.shareUsageExport
import com.metrolist.music.arc.switchItem
import com.metrolist.music.constants.DisableScreenshotKey
import com.metrolist.music.constants.HistoryDuration
import com.metrolist.music.constants.PauseListenHistoryKey
import com.metrolist.music.constants.PauseSearchHistoryKey
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettings(
    navController: NavController
) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (pauseListenHistory, onPauseListenHistoryChange) = rememberPreference(PauseListenHistoryKey, defaultValue = false)
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val (historyDuration, onHistoryDurationChange) = rememberPreference(HistoryDuration, defaultValue = 30f)
    val (disableScreenshot, onDisableScreenshotChange) = rememberPreference(DisableScreenshotKey, defaultValue = false)
    val (learnUsage, onLearnUsageChange) = rememberPreference(ArcLearnUsageKey, defaultValue = true)

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    if (showClearHistoryDialog) {
        var clearListen by remember { mutableStateOf(true) }
        var clearSearch by remember { mutableStateOf(true) }
        @Composable
        fun tick(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) = Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 6.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = onChange)
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        }
        DefaultDialog(
            onDismiss = { showClearHistoryDialog = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.arc_clear_history)) },
            content = {
                tick(R.string.listen_history, clearListen) { clearListen = it }
                tick(R.string.search_history, clearSearch) { clearSearch = it }
            },
            buttons = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    enabled = clearListen || clearSearch,
                    onClick = {
                        showClearHistoryDialog = false
                        database.query {
                            if (clearListen) clearListenHistory()
                            if (clearSearch) clearSearchHistory()
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.clear))
                }
            },
        )
    }

    var showForgetUsageDialog by remember { mutableStateOf(false) }
    if (showForgetUsageDialog) {
        DefaultDialog(
            onDismiss = { showForgetUsageDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.arc_usage_forget_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showForgetUsageDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showForgetUsageDialog = false
                        Usage.forget()
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .arcScroll()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        Material3SettingsGroup(
            title = stringResource(R.string.history),
            icon = R.drawable.history,
            items = listOf(
                switchItem(R.drawable.history, R.string.arc_save_listen_history, null, !pauseListenHistory, { onPauseListenHistoryChange(!it) }),
                switchItem(R.drawable.search, R.string.arc_save_search_history, null, !pauseSearchHistory, { onPauseSearchHistoryChange(!it) }),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.delete_history),
                    title = { Text(stringResource(R.string.arc_clear_history)) },
                    onClick = { showClearHistoryDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.timer),
                    title = { Text(stringResource(R.string.arc_history_duration)) },
                    description = {
                        Column {
                            Text(stringResource(R.string.arc_history_duration_desc, historyDuration.roundToInt()))
                            ArcSlider(
                                value = historyDuration,
                                onValueChange = onHistoryDurationChange,
                                valueRange = 1f..100f
                            )
                        }
                    }
                ),
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.arc_usage),
            icon = R.drawable.trending_up,
            items = listOf(
                switchItem(R.drawable.trending_up, R.string.arc_usage_learn, R.string.arc_usage_learn_desc, learnUsage, onLearnUsageChange),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.share),
                    title = { Text(stringResource(R.string.arc_usage_export)) },
                    description = { Text(stringResource(R.string.arc_usage_export_desc)) },
                    onClick = { scope.launch { runCatching { shareUsageExport(context, database) }.onFailure(Timber::e) } }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.delete),
                    title = { Text(stringResource(R.string.arc_usage_forget)) },
                    onClick = { showForgetUsageDialog = true }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.arc_privacy_screen),
            icon = R.drawable.screenshot,
            items = listOf(
                switchItem(R.drawable.screenshot, R.string.disable_screenshot, R.string.disable_screenshot_desc, disableScreenshot, onDisableScreenshotChange),
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.privacy)) },
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
