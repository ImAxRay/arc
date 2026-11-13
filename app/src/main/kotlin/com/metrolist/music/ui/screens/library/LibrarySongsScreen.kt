/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import com.metrolist.music.models.toMediaMetadata
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.metrolist.music.ui.menu.SelectionSongMenu
import com.metrolist.music.arc.selectableSong
import com.metrolist.music.arc.DeviceMusic
import com.metrolist.music.arc.rememberSongSelection
import com.metrolist.music.arc.SongSelectionBar
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.innertube.YouTube
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.constants.CONTENT_TYPE_SONG
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.SongFilter
import com.metrolist.music.constants.SongFilterKey
import com.metrolist.music.constants.SongSortDescendingKey
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.arc.arcScrollbar
import com.metrolist.music.arc.LengthFilterChip
import com.metrolist.music.arc.byLength
import androidx.compose.runtime.produceState
import com.metrolist.music.LocalDatabase
import com.metrolist.music.arc.SongTriage
import com.metrolist.music.arc.TriageBanner
import com.metrolist.music.arc.scrollLetter
import com.metrolist.music.arc.scrollMonth
import com.metrolist.music.arc.scrollDay
import com.metrolist.music.arc.ArcDeviceGroupKey
import com.metrolist.music.arc.DeviceGroup
import com.metrolist.music.arc.sizeBand
import com.metrolist.music.arc.WarmVisibleSongs
import com.metrolist.music.constants.SongSortTypeKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.extensions.fileNameAndSize
import com.metrolist.music.extensions.matchesNormalizedQuery
import com.metrolist.music.extensions.normalizeForSearch
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.LibrarySearchEmptyPlaceholder
import com.metrolist.music.ui.component.LibrarySearchHeader
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.utils.isScrollingUp
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LibrarySongsViewModel
import timber.log.Timber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val uploadUnsupportedFormatStr = stringResource(R.string.upload_unsupported_format)
    val uploadFileTooLargeStr = stringResource(R.string.upload_file_too_large)
    val uploadFailedStr = stringResource(R.string.upload_failed)
    val uploadCompleteStr = stringResource(R.string.upload_complete)
    val queueAllSongsStr = stringResource(R.string.queue_all_songs)
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            SongSortTypeKey,
            SongSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val lengthRule by rememberPreference(com.metrolist.music.arc.ArcSongLengthKey, 0)

    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val debouncedSearchQuery by viewModel.debouncedSearchQuery.collectAsStateWithLifecycle()
    val normalizedQuery = remember(debouncedSearchQuery) { debouncedSearchQuery.normalizeForSearch() }

    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)

    // Upload state
    var showUploadDialog by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var currentUploadIndex by remember { mutableIntStateOf(0) }
    var totalUploads by remember { mutableIntStateOf(0) }
    var currentFileName by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val deviceAccessLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.syncDeviceSongs()
        }
    LaunchedEffect(filter) {
        if (filter == SongFilter.DEVICE) {
            if (DeviceMusic.hasAccess(context)) viewModel.syncDeviceSongs() else deviceAccessLauncher.launch(DeviceMusic.permission)
        }
        if (filter == SongFilter.DRIVE) viewModel.syncDriveSongs()
        if (filter == SongFilter.UNTITLED) viewModel.syncUntitledSongs()
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    try {
                        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (e: SecurityException) {
                        Timber.w(e, "Could not take persistable permission")
                    }
                }
                uploadJob =
                    scope.launch {
                        isUploading = true
                        showUploadDialog = true
                        totalUploads = uris.size
                        var successCount = 0

                        uris.forEachIndexed { index, uri ->
                            currentUploadIndex = index + 1
                            uploadProgress = 0f

                            try {
                                val (fileName, contentLength) =
                                    withContext(Dispatchers.IO) {
                                        context.contentResolver.fileNameAndSize(uri)
                                    }
                                currentFileName = fileName

                                if (fileName.substringAfterLast('.', "").lowercase() !in YouTube.SUPPORTED_UPLOAD_TYPES) {
                                    Toast.makeText(context, uploadUnsupportedFormatStr, Toast.LENGTH_SHORT).show()
                                    return@forEachIndexed
                                }
                                if (contentLength >= YouTube.MAX_UPLOAD_SIZE) {
                                    Toast.makeText(context, uploadFileTooLargeStr, Toast.LENGTH_SHORT).show()
                                    return@forEachIndexed
                                }
                                if (contentLength <= 0) {
                                    Toast.makeText(context, uploadFailedStr, Toast.LENGTH_SHORT).show()
                                    return@forEachIndexed
                                }

                                val result =
                                    YouTube.uploadSong(
                                        filename = fileName,
                                        contentLength = contentLength,
                                        content = { checkNotNull(context.contentResolver.openInputStream(uri)) },
                                        onProgress = { progress -> uploadProgress = progress },
                                    )

                                if (result.getOrThrow()) successCount++
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast
                                    .makeText(
                                        context,
                                        uploadFailedStr + ": ${e.message}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }

                        isUploading = false

                        if (successCount > 0) {
                            // Show completion briefly
                            uploadProgress = 1f
                            currentFileName = uploadCompleteStr
                            kotlinx.coroutines.delay(1000)

                            // Show toast on main thread
                            withContext(Dispatchers.Main) {
                                Toast
                                    .makeText(
                                        context,
                                        uploadCompleteStr,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }

                            showUploadDialog = false

                            // Refresh uploaded songs
                            viewModel.syncUploadedSongs()
                        } else {
                            showUploadDialog = false
                        }
                    }
            }
        }

    LaunchedEffect(Unit) {
        if (ytmSync) {
            when (filter) {
                SongFilter.LIKED -> viewModel.syncLikedSongs()
                SongFilter.LIBRARY -> viewModel.syncLibrarySongs()
                SongFilter.UPLOADED -> viewModel.syncUploadedSongs()
                else -> return@LaunchedEffect
            }
        }
    }

    val lazyListState = rememberLazyListState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val filteredSongs =
        (if (hideExplicit) {
            songs.filter { !it.song.explicit }
        } else {
            songs
        }).filter { song ->
            val artistNames = song.artists.map { it.name }.toTypedArray()
            matchesNormalizedQuery(normalizedQuery, song.song.title, song.album?.title, *artistNames)
        }.byLength(lengthRule)

    // Upload progress dialog
    if (showUploadDialog) {
        DefaultDialog(
            onDismiss = {
                if (isUploading) {
                    uploadJob?.cancel()
                    isUploading = false
                }
                showUploadDialog = false
            },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.upload),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.uploading)) },
            buttons = {
                TextButton(
                    onClick = {
                        if (isUploading) {
                            uploadJob?.cancel()
                            isUploading = false
                        }
                        showUploadDialog = false
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.upload_progress, currentUploadIndex, totalUploads),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentFileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { uploadProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val selection = rememberSongSelection()
    val database = LocalDatabase.current
    val ownFiles = filter == SongFilter.DEVICE || filter == SongFilter.DRIVE
    val sortCounts by produceState(0 to 0, filter, songs) {
        value = if (ownFiles) SongTriage.unsorted(context, database).size to SongTriage.leftOut(context, database).size else 0 to 0
    }
    val (toSort, leftOut) = sortCounts
    val headerItems = if (toSort > 0 || leftOut > 0) 3 else 2
    val (groupName, onGroupChange) = rememberPreference(ArcDeviceGroupKey, "")
    val group = if (filter == SongFilter.DEVICE) DeviceGroup.entries.firstOrNull { it.name == groupName } else null
    val facts by produceState(emptyMap<String, DeviceMusic.Facts>(), group != null, songs) {
        value = if (group != null) withContext(Dispatchers.IO) { DeviceMusic.facts(context) } else emptyMap()
    }
    val shownSongs = remember(filteredSongs, group, facts) {
        when (group) {
            DeviceGroup.FOLDER -> filteredSongs.sortedBy { facts[it.id]?.folder.orEmpty().lowercase() }
            DeviceGroup.FORMAT -> filteredSongs.sortedBy { facts[it.id]?.format.orEmpty() }
            DeviceGroup.SIZE -> filteredSongs.sortedByDescending { facts[it.id]?.bytes ?: 0L }
            null -> filteredSongs
        }
    }
    fun sectionOf(song: com.metrolist.music.db.entities.Song): String? = when {
        group == DeviceGroup.FOLDER -> facts[song.id]?.folder ?: "—"
        group == DeviceGroup.FORMAT -> facts[song.id]?.format ?: "—"
        group == DeviceGroup.SIZE -> sizeBand(facts[song.id]?.bytes ?: 0L)
        sortType == SongSortType.NAME -> scrollLetter(song.song.title)
        sortType == SongSortType.ARTIST -> song.artists.firstOrNull()?.name ?: "—"
        sortType == SongSortType.CREATE_DATE -> song.song.inLibrary?.format(scrollMonth)
        else -> null
    }
    val rows = remember(shownSongs, sortType, group, facts) {
        buildList<SongRow> {
            var last: String? = null
            shownSongs.forEachIndexed { i, song ->
                val name = sectionOf(song)
                if (name != null && name != last) {
                    add(SongRow.Header(name, size))
                    last = name
                }
                add(SongRow.Item(song, i))
            }
        }
    }
    var groupMenu by remember { mutableStateOf(false) }
    WarmVisibleSongs(lazyListState, all = shownSongs.map { it.id }) { index -> (rows.getOrNull(index as Int - headerItems) as? SongRow.Item)?.song?.id }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val listInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        LazyColumn(
            state = lazyListState,
            contentPadding = listInsets,
            modifier = Modifier.arcScrollbar(
                lazyListState,
                listInsets,
                labelKey = rows,
                section = { index ->
                    when (val row = rows.getOrNull(index - headerItems)) {
                        is SongRow.Header -> row.name
                        is SongRow.Item -> sectionOf(row.song)
                        null -> null
                    }
                },
                label = { index ->
                    val song = (rows.getOrNull(index - headerItems) as? SongRow.Item)?.song
                        ?: (rows.getOrNull(index - headerItems + 1) as? SongRow.Item)?.song
                        ?: return@arcScrollbar null
                    if (group == null && sortType == SongSortType.CREATE_DATE) song.song.inLibrary?.format(scrollDay) else sectionOf(song)
                },
            ),
        ) {
            item(
                key = "filter",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                Row {
                    Spacer(Modifier.width(12.dp))
                    FilterChip(
                        label = { Text(stringResource(R.string.songs)) },
                        selected = true,
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = onDeselect,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = "",
                            )
                        },
                    )
                    ChipsRow(
                        chips =
                            listOf(
                                SongFilter.LIKED to stringResource(R.string.filter_liked),
                                SongFilter.LIBRARY to stringResource(R.string.filter_library),
                                SongFilter.UPLOADED to stringResource(R.string.filter_uploaded),
                                SongFilter.DOWNLOADED to stringResource(R.string.filter_downloaded),
                                SongFilter.DEVICE to stringResource(R.string.filter_on_device),
                                SongFilter.DRIVE to stringResource(R.string.arc_drive),
                                SongFilter.UNTITLED to stringResource(R.string.arc_untitled),
                            ),
                        currentValue = filter,
                        onValueUpdate = {
                            filter = it
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                LibrarySearchHeader(
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onBack = {
                        isSearchActive = false
                        viewModel.updateSearchQuery("")
                    },
                    keyboardController = keyboardController,
                    modifier = Modifier.padding(start = 16.dp),
                ) {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { sortType ->
                            when (sortType) {
                                SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                SongSortType.NAME -> R.string.sort_by_name
                                SongSortType.ARTIST -> R.string.sort_by_artist
                                SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                            }
                        },
                    )

                    LengthFilterChip(Modifier.padding(start = 8.dp))

                    if (filter == SongFilter.DEVICE) {
                        Box {
                            FilterChip(
                                selected = group != null,
                                onClick = { groupMenu = true },
                                label = { Text(group?.let { stringResource(it.label) } ?: stringResource(R.string.arc_group)) },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                            androidx.compose.material3.DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.arc_group_none)) },
                                    onClick = { onGroupChange(""); groupMenu = false },
                                )
                                DeviceGroup.entries.forEach { g ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(g.label)) },
                                        onClick = { onGroupChange(g.name); groupMenu = false },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.n_song,
                                filteredSongs.size,
                                filteredSongs.size,
                            ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp).size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                }
            }

            if (toSort > 0 || leftOut > 0) {
                item(key = "triage", contentType = CONTENT_TYPE_HEADER) {
                    TriageBanner(
                        count = toSort,
                        leftOut = leftOut,
                        onClick = { navController.navigate("arc/triage") },
                        onLeftOut = { navController.navigate("arc/left_out") },
                    )
                }
            }

            if (filteredSongs.isEmpty() && searchQuery.isNotBlank()) {
                item(
                    key = "empty_search_result",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    LibrarySearchEmptyPlaceholder(modifier = Modifier.animateItem())
                }
            }

            items(
                items = rows,
                key = { row -> when (row) { is SongRow.Header -> "section_${row.at}_${row.name}"; is SongRow.Item -> "${row.song.song.id}_${row.index}" } },
                contentType = { row -> if (row is SongRow.Header) CONTENT_TYPE_HEADER else CONTENT_TYPE_SONG },
            ) { row ->
                if (row is SongRow.Header) {
                    com.metrolist.music.arc.ListSectionHeader(row.name, Modifier.animateItem())
                    return@items
                }
                val song = (row as SongRow.Item).song
                val index = row.index
                SongListItem(
                    song = song,
                    showInLibraryIcon = true,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    showLikedIcon = true,
                    showDownloadIcon = filter != SongFilter.DOWNLOADED,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(if (song.id in selection.ids) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .selectableSong(song.id, selection) {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = queueAllSongsStr,
                                            items = shownSongs.map { it.toMediaItem() },
                                            startIndex = index,
                                        ),
                                    )
                                }
                            }.animateItem(),
                )
            }
        }

        // Show upload FAB when on UPLOADED filter, shuffle FAB otherwise
        HideOnScrollFAB(
            visible = !selection.active && (if (filter == SongFilter.UPLOADED) true else filteredSongs.isNotEmpty()),
            lazyListState = lazyListState,
            icon = if (filter == SongFilter.UPLOADED) R.drawable.upload else R.drawable.shuffle,
            onClick = {
                if (filter == SongFilter.UPLOADED) {
                    filePickerLauncher.launch(
                        arrayOf(
                            "audio/mpeg",
                            "audio/mp4",
                            "audio/x-m4a",
                            "audio/flac",
                            "audio/ogg",
                            "audio/x-ms-wma",
                        ),
                    )
                } else {
                    playerConnection.playQueue(
                        ListQueue(
                            title = queueAllSongsStr,
                            items = filteredSongs.shuffled().map { it.toMediaItem() },
                        ),
                    )
                }
            },
        )

        SongSelectionBar(
            selection = selection,
            ownFiles = ownFiles,
            all = { filteredSongs.map { it.id } },
            picked = { filteredSongs.filter { it.id in selection.ids }.map { it.toMediaMetadata() } },
            onMore = {
                menuState.show {
                    SelectionSongMenu(
                        songSelection = filteredSongs.filter { it.id in selection.ids },
                        onDismiss = menuState::dismiss,
                        clearAction = selection::clear,
                        compact = true,
                    )
                }
            },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
        )
    }
}

private sealed interface SongRow {
    class Header(val name: String, val at: Int) : SongRow
    class Item(val song: com.metrolist.music.db.entities.Song, val index: Int) : SongRow
}
