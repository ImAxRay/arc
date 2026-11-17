/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.arc.TopAppBar
import com.metrolist.music.arc.arcScroll
import com.metrolist.music.arc.switchItem
import com.metrolist.music.constants.AutoDownloadOnLikeKey
import com.metrolist.music.constants.MaxImageCacheSizeKey
import com.metrolist.music.constants.MaxSongCacheSizeKey
import com.metrolist.music.extensions.tryOrNull
import com.metrolist.music.ui.component.ActionPromptDialog
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import java.io.File

private const val MB = 1024 * 1024L
private val songCacheValues = listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192, -1)
private val imageCacheValues = listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192)

private class Slice(val label: Int, val bytes: Long, val color: Color)

@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class, DelicateCoilApi::class)
@Composable
fun StorageSettings(
    navController: NavController
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val imageDiskCache = context.imageLoader.diskCache ?: return
    val playerCache = LocalPlayerConnection.current?.service?.playerCache ?: return
    val downloadCache = LocalPlayerConnection.current?.service?.downloadCache ?: return

    val coroutineScope = rememberCoroutineScope()
    val songCacheString = stringResource(R.string.arc_storage_song_cache).lowercase()
    val imageCacheString = stringResource(R.string.arc_storage_image_cache).lowercase()
    val (maxImageCacheSize, onMaxImageCacheSizeChange) = rememberPreference(MaxImageCacheSizeKey, 512)
    val (maxSongCacheSize, onMaxSongCacheSizeChange) = rememberPreference(MaxSongCacheSizeKey, 1024)
    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) = rememberPreference(AutoDownloadOnLikeKey, false)
    val downloadedCount by remember { database.downloadedSongsByNameAsc().map { it.size } }.collectAsState(0)

    // Both caches read their size limit only when the app starts.
    val startSongSize = remember { maxSongCacheSize }
    val startImageSize = remember { maxImageCacheSize }

    var clearDownloads by remember { mutableStateOf(false) }
    var clearCacheDialog by remember { mutableStateOf(false) }
    var clearImageCacheDialog by remember { mutableStateOf(false) }
    var songSizeDialog by remember { mutableStateOf(false) }
    var imageSizeDialog by remember { mutableStateOf(false) }

    var showCacheWarningDialog by remember { mutableStateOf(false) }
    var cacheType by remember { mutableStateOf("") }
    var cacheUsage by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var onConfirmAction by remember { mutableStateOf<() -> Unit>({}) }

    var measureTick by remember { mutableIntStateOf(0) }
    var sizes by remember { mutableStateOf<LongArray?>(null) }
    LaunchedEffect(measureTick) {
        sizes = withContext(Dispatchers.IO) {
            fun dir(f: File?) = f?.walkBottomUp()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
            val parts = longArrayOf(
                tryOrNull { downloadCache.cacheSpace } ?: 0L,
                tryOrNull { playerCache.cacheSpace } ?: 0L,
                imageDiskCache.size,
                dir(File(context.cacheDir, "motion_covers")),
                dir(File(context.filesDir, "snippets")),
                dir(context.getDatabasePath("song.db").parentFile),
            )
            val other = (dir(context.dataDir) - parts.sum()).coerceAtLeast(0L)
            parts + other + StatFs(context.filesDir.path).availableBytes
        }
    }
    fun clear(block: suspend () -> Unit) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) { block() }
            measureTick++
        }
    }
    val downloadSize = sizes?.get(0) ?: 0L
    val songCacheSize = sizes?.get(1) ?: 0L
    val imageCacheSize = sizes?.get(2) ?: 0L

    fun clearSongCache() = clear { playerCache.keys.forEach { playerCache.removeResource(it) } }
    fun clearImageCache() = clear { imageDiskCache.clear() }

    fun setSongSize(value: Int) {
        val apply = {
            onMaxSongCacheSizeChange(value)
            if (value == 0) clearSongCache()
        }
        val limit = if (value == -1) Long.MAX_VALUE else value * MB
        if (limit < songCacheSize) {
            cacheUsage = songCacheSize
            cacheType = songCacheString
            onConfirmAction = apply
            showCacheWarningDialog = true
        } else apply()
    }
    fun setImageSize(value: Int) {
        val apply = {
            onMaxImageCacheSizeChange(value)
            if (value == 0) clearImageCache()
        }
        if (value * MB < imageCacheSize) {
            cacheUsage = imageCacheSize
            cacheType = imageCacheString
            onConfirmAction = apply
            showCacheWarningDialog = true
        } else apply()
    }

    @Composable
    fun sizeLabel(value: Int) = when (value) {
        0 -> stringResource(R.string.arc_off)
        -1 -> stringResource(R.string.unlimited)
        else -> if (value >= 1024) "${value / 1024} GB" else "$value MB"
    }

    if (songSizeDialog) {
        EnumDialog(
            onDismiss = { songSizeDialog = false },
            onSelect = { songSizeDialog = false; setSongSize(it) },
            title = stringResource(R.string.max_song_cache_size),
            current = maxSongCacheSize,
            values = songCacheValues,
            valueText = { sizeLabel(it) },
        )
    }
    if (imageSizeDialog) {
        EnumDialog(
            onDismiss = { imageSizeDialog = false },
            onSelect = { imageSizeDialog = false; setImageSize(it) },
            title = stringResource(R.string.max_image_cache_size),
            current = maxImageCacheSize,
            values = imageCacheValues,
            valueText = { sizeLabel(it) },
        )
    }

    if (clearDownloads) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_all_downloads),
            onDismiss = { clearDownloads = false },
            onConfirm = {
                clear { downloadCache.keys.forEach { downloadCache.removeResource(it) } }
                clearDownloads = false
            },
            onCancel = { clearDownloads = false },
            content = {
                Text(text = stringResource(R.string.clear_downloads_dialog))
            },
        )
    }
    if (clearCacheDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_song_cache),
            onDismiss = { clearCacheDialog = false },
            onConfirm = {
                clearSongCache()
                clearCacheDialog = false
            },
            onCancel = { clearCacheDialog = false },
            content = {
                Text(text = stringResource(R.string.clear_song_cache_dialog))
            },
        )
    }
    if (clearImageCacheDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_image_cache),
            onDismiss = { clearImageCacheDialog = false },
            onConfirm = {
                clear {
                    val urlsToPreserve = mutableSetOf<String>()
                    val downloadedSongs =
                        try {
                            database.downloadedSongsByNameAsc().first()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    downloadedSongs.forEach { song ->
                        song.song.thumbnailUrl?.let { urlsToPreserve.add(it.encodeUtf8().sha256().hex()) }
                        song.album?.thumbnailUrl?.let { urlsToPreserve.add(it.encodeUtf8().sha256().hex()) }
                    }
                    val directory = imageDiskCache.directory.toFile()
                    if (directory.exists() && directory.isDirectory) {
                        directory.listFiles()?.forEach { file ->
                            if (file.isFile && !file.name.startsWith("journal")) {
                                val isPreserved = urlsToPreserve.any { hash -> file.name.startsWith(hash) }
                                if (!isPreserved) {
                                    file.delete()
                                }
                            }
                        }
                    }
                    imageDiskCache.clear()
                }
                clearImageCacheDialog = false
            },
            onCancel = { clearImageCacheDialog = false },
            content = {
                Text(text = stringResource(R.string.clear_image_cache_dialog))
            },
        )
    }

    if (showCacheWarningDialog) {
        AlertDialog(
            onDismissRequest = { showCacheWarningDialog = false },
            title = { Text(stringResource(R.string.cache_size_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cache_size_warning_message,
                        Formatter.formatShortFileSize(context, cacheUsage),
                        cacheType,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmAction()
                        showCacheWarningDialog = false
                    },
                ) {
                    Text(
                        stringResource(R.string.cache_size_warning_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCacheWarningDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    fun bin(enabled: Boolean, onClick: () -> Unit): @Composable () -> Unit = {
        IconButton(onClick = onClick, onLongClick = {}, enabled = enabled) {
            Icon(painterResource(R.drawable.delete), contentDescription = stringResource(R.string.clear))
        }
    }

    @Composable
    fun cacheDescription(used: Long, limit: Int, startLimit: Int) {
        Column {
            Text(
                if (limit == 0) stringResource(R.string.arc_off)
                else stringResource(R.string.arc_storage_of, Formatter.formatShortFileSize(context, used), sizeLabel(limit))
            )
            if (limit != startLimit) Text(stringResource(R.string.restart_to_take_effect), color = MaterialTheme.colorScheme.primary)
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ).arcScroll()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top,
                ),
            ),
        )

        sizes?.let { s ->
            StorageColumn(
                slices = listOf(
                    Slice(R.string.arc_storage_downloads, s[0], Color(0xFF5B8DEF)),
                    Slice(R.string.arc_storage_song_cache, s[1], Color(0xFFB05CD6)),
                    Slice(R.string.arc_storage_images, s[2], Color(0xFF5DBB63)),
                    Slice(R.string.arc_motion_covers, s[3], Color(0xFFF2B33D)),
                    Slice(R.string.arc_snippets, s[4], Color(0xFFE8735A)),
                    Slice(R.string.arc_storage_library, s[5], Color(0xFF4FC3D9)),
                    Slice(R.string.other, s[6], Color(0xFF9E9E9E)),
                ),
                free = s[7],
            )
            Spacer(Modifier.height(16.dp))
        }

        Material3SettingsGroup(
            title = stringResource(R.string.arc_storage_downloads),
            icon = painterResource(R.drawable.download),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.download),
                    title = { Text(stringResource(R.string.downloaded_songs)) },
                    description = {
                        Text(pluralStringResource(R.plurals.arc_storage_downloaded, downloadedCount, Formatter.formatShortFileSize(context, downloadSize), downloadedCount))
                    },
                    trailingContent = bin(downloadSize > 0) { clearDownloads = true },
                    opensPage = true,
                    onClick = { navController.navigate("auto_playlist/downloaded") },
                ),
                switchItem(R.drawable.favorite, R.string.arc_download_liked, R.string.auto_download_on_like_desc, autoDownloadOnLike, onAutoDownloadOnLikeChange),
            ),
        )
        Spacer(Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.arc_storage_caches),
            icon = painterResource(R.drawable.cached),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.arc_storage_song_cache)) },
                    description = { cacheDescription(songCacheSize, maxSongCacheSize, startSongSize) },
                    trailingContent = bin(songCacheSize > 0) { clearCacheDialog = true },
                    onClick = { songSizeDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.insert_photo),
                    title = { Text(stringResource(R.string.arc_storage_image_cache)) },
                    description = { cacheDescription(imageCacheSize, maxImageCacheSize, startImageSize) },
                    trailingContent = bin(imageCacheSize > 0) { clearImageCacheDialog = true },
                    onClick = { imageSizeDialog = true },
                ),
            ),
        )
        Spacer(Modifier.height(27.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.storage)) },
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
        },
    )
}

/** Morphe-style summary: Arc's total and the phone's free space, then one column split by what fills it. */
@Composable
private fun StorageColumn(slices: List<Slice>, free: Long) {
    val context = LocalContext.current
    val total = slices.sumOf { it.bytes }
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.primaryContainer)
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Formatter.formatShortFileSize(context, total),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Light,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.arc_storage_free, Formatter.formatShortFileSize(context, free)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.height(IntrinsicSize.Min).padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                Column(
                    Modifier
                        .width(44.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .background(scheme.surfaceVariant),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (total > 0) slices.filter { it.bytes > 0 }.forEach {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(maxOf(it.bytes.toFloat() / total, 0.01f))
                                .background(it.color)
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    slices.forEach {
                        val empty = it.bytes == 0L
                        Row {
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (empty) scheme.outlineVariant else it.color)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    stringResource(it.label),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (empty) scheme.onSurfaceVariant.copy(alpha = 0.6f) else scheme.onSurface,
                                )
                                Text(
                                    Formatter.formatShortFileSize(context, it.bytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant.copy(alpha = if (empty) 0.6f else 1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
