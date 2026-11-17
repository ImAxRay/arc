/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import com.metrolist.music.arc.arcScroll
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.ReleaseNotesCard
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.arc.ArcSettingsSearch
import com.metrolist.music.arc.RecentSettings
import com.metrolist.music.utils.Updater
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val hasAndroidAuto = remember {
        try {
            context.packageManager.getPackageInfo(
                "com.google.android.projection.gearhead", 0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
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

        val settingsSearching = ArcSettingsSearch(navController)
        if (!settingsSearching) {
        com.metrolist.music.arc.VisualModeControl(navController)
        RecentSettings(navController)
        Material3SettingsGroup(
            title = stringResource(R.string.arc_settings_listening),
            icon = painterResource(R.drawable.headphones),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.arc_settings_sound)) },
                    description = { Text(stringResource(R.string.arc_settings_sound_desc)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/sound") }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.queue_music),
                    title = { Text(stringResource(R.string.arc_settings_queue)) },
                    description = { Text(stringResource(R.string.arc_settings_queue_desc)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/queue") }
                ))
                if (hasAndroidAuto) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.ic_android_auto),
                            title = { Text(stringResource(R.string.android_auto)) },
                            opensPage = true,
                            onClick = { navController.navigate("settings/android_auto") }
                        )
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.arc_settings_look),
            icon = painterResource(R.drawable.palette),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.appearance)) },
                    description = { Text(stringResource(R.string.arc_settings_appearance_desc)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/appearance") }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.library_music),
                    title = { Text(stringResource(R.string.arc_settings_library)) },
                    description = { Text(stringResource(R.string.arc_settings_library_desc)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/library") }
                ))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.lyrics),
            icon = painterResource(R.drawable.lyrics),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics)) },
                    description = { Text(stringResource(R.string.arc_settings_lyrics_desc)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/lyrics") }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.language_korean_latin),
                    title = { Text(stringResource(R.string.lyrics_romanization)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/content/romanization") }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text(stringResource(R.string.ai_lyrics_translation)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/ai") }
                ))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.arc_settings_sources),
            icon = painterResource(R.drawable.library_music),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.arc_settings_region)) },
                    description = { Text(stringResource(R.string.arc_settings_region_desc)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/region") }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.cloud),
                    title = { Text(stringResource(R.string.arc_drive)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/drive") }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.arc_addons)) },
                    description = { Text(stringResource(R.string.arc_beta)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/addons") }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.library_music),
                    title = { Text(stringResource(R.string.arc_untitled)) },
                    description = { Text(stringResource(R.string.arc_beta)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/untitled") }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.integration),
                    title = { Text(stringResource(R.string.integrations)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/integrations") }
                ))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.arc_settings_your_data),
            icon = painterResource(R.drawable.storage),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.security),
                    title = { Text(stringResource(R.string.privacy)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/privacy") }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.storage),
                    title = { Text(stringResource(R.string.storage)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/storage") }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.restore),
                    title = { Text(stringResource(R.string.backup_restore)) },
                    opensPage = true,
                    onClick = { navController.navigate("settings/backup_restore") }
                ))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // System & About Section
        Material3SettingsGroup(
            title = stringResource(R.string.arc_settings_app),
            icon = painterResource(R.drawable.settings),
            items = buildList {
                if (isAndroid12OrLater) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.link),
                            title = { Text(stringResource(R.string.default_links)) },
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        "package:${context.packageName}".toUri()
                                    )
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    when (e) {
                                        is ActivityNotFoundException -> {
                                            Toast.makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }

                                        is SecurityException -> {
                                            Toast.makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }

                                        else -> {
                                            Toast.makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
                        )
                    )
                }
                if (BuildConfig.UPDATER_AVAILABLE) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.update),
                            title = { Text(stringResource(R.string.updater)) },
                            opensPage = true,
                            onClick = { navController.navigate("settings/updater") }
                        )
                    )
                }
                val showChangelog = com.metrolist.music.LocalChangelogState.current
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.newspaper),
                        title = { Text(stringResource(R.string.changelog)) },
                        onClick = { showChangelog.value = true }
                    )
                )
                if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
                    val releaseInfo = Updater.getCachedLatestRelease()
                    val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }

                    if (downloadUrl != null) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.update),
                                title = { 
                                    Text(
                                        text = stringResource(R.string.new_version_available),
                                    )
                                },
                                description = {
                                    Text(
                                        text = latestVersionName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                showBadge = true,
                                onClick = { uriHandler.openUri(downloadUrl) }
                            )
                        )
                    }
                }
            }
        )
    if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
            Spacer(modifier = Modifier.height(16.dp))
            ReleaseNotesCard()
        }

        Spacer(modifier = Modifier.height(16.dp))
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
