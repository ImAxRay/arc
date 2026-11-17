/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.artist

import androidx.compose.foundation.layout.wrapContentHeight
import com.metrolist.music.arc.ArtistStickyHeader
import com.metrolist.music.arc.arcTopBarBackdrop
import com.metrolist.music.arc.arcTopBarColors
import com.metrolist.music.ui.menu.YouTubeSelectionSongMenu
import com.metrolist.music.ui.menu.SelectionSongMenu
import com.metrolist.music.arc.selectableSong
import com.metrolist.music.arc.rememberSongSelection
import com.metrolist.music.arc.SongSelectionBar
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AppBarHeight
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import com.metrolist.music.arc.ArtistActions
import com.metrolist.music.arc.ArtistAlbumGrid
import com.metrolist.music.arc.ArtistStats
import com.metrolist.music.ui.component.AlbumGridItem
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LinkSegment
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.ButtonPlaceholder
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.fadingEdge
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.ArtistViewModel
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val selection = rememberSongSelection()
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsStateWithLifecycle()
    val artistNameAliases = LocalArtistNameAliases.current
    val displayArtistName =
        ArtistNameAliases.resolve(
            artistNameAliases,
            viewModel.artistId,
            artistPage?.artist?.title ?: libraryArtist?.artist?.name.orEmpty(),
        ).ifEmpty { null }
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val ownSongs by viewModel.ownSongs.collectAsStateWithLifecycle()
    val libraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
    val isChannelSubscribed by viewModel.isChannelSubscribed.collectAsStateWithLifecycle()
    val albumGridSections = remember { mutableStateListOf<String>() }
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current

    // Calculate the offset value outside of the offset lambda
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val headerOffset =
        with(density) {
            -(systemBarsTopPadding + AppBarHeight).roundToPx()
        }

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    val distinctItemsBySection = remember(artistPage?.sections) {
        artistPage?.sections?.map { section ->
            section.items.distinctBy { it.id }
        } ?: emptyList()
    }
    // Only the sections drawn as song rows, so select all picks what's on screen, not videos or other shelves.
    val shownArtistSongs = {
        artistPage?.sections.orEmpty().flatMapIndexed { index, section ->
            if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                (distinctItemsBySection.getOrNull(index) ?: section.items).filterIsInstance<SongItem>()
            } else {
                emptyList()
            }
        }.distinctBy { it.id }
    }

    LaunchedEffect(libraryArtist) {
        // always show local page for local artists. Show local page remote artist when offline
        showLocal = libraryArtist?.artist?.isLocal == true
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
            val onPlayAllClick: () -> Unit = {
                if (!isGuest) {
                    if (showLocal) {
                        if (librarySongs.isNotEmpty()) {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = displayArtistName ?: "Unknown Artist",
                                    items = librarySongs.map { it.toMediaItem() },
                                ),
                            )
                        }
                    } else if (artistPage != null) {
                        val songSection =
                            artistPage.sections.find { section ->
                                (section.items.firstOrNull() as? SongItem)?.album != null
                            }

                        val moreEndpoint = songSection?.moreEndpoint
                        if (moreEndpoint != null) {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val result = YouTube.artistItems(moreEndpoint).getOrNull()
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (result != null && result.items.isNotEmpty()) {
                                        val songs = result.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = displayArtistName ?: artistPage.artist.title,
                                                items = songs,
                                            ),
                                        )
                                    } else {
                                        // Fallback to loaded items
                                        val songs = songSection.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                                        if (songs.isNotEmpty()) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = displayArtistName ?: artistPage.artist.title,
                                                    items = songs,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (songSection != null) {
                            // Use loaded items if no more endpoint
                            val songs = songSection.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                            playerConnection.playQueue(
                                ListQueue(
                                    title = displayArtistName ?: artistPage.artist.title,
                                    items = songs,
                                ),
                            )
                        } else {
                            // Fallback to shuffle endpoint (stripped) if no song section found
                            val shuffleEndpoint = artistPage.artist.shuffleEndpoint
                            if (shuffleEndpoint != null) {
                                val endpoint =
                                    if (shuffleEndpoint.playlistId != null) {
                                        WatchEndpoint(
                                            playlistId = shuffleEndpoint.playlistId,
                                            params = null, // Remove shuffle params to play in order
                                            videoId = null, // Ensure videoId is null to start from beginning of playlist
                                        )
                                    } else {
                                        shuffleEndpoint
                                    }
                                playerConnection.playQueue(YouTubeQueue(endpoint))
                            }
                        }
                    }
                }
            }

        com.metrolist.music.arc.WarmVisibleSongs(lazyListState, byKey = true) { key ->
            val k = key.toString()
            when {
                k.startsWith("local_song_") -> k.removePrefix("local_song_").substringBeforeLast('_')
                k.startsWith("youtube_song_") -> k.removePrefix("youtube_song_")
                else -> null
            }
        }
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (artistPage == null && !showLocal) {
                item(key = "shimmer") {
                    ShimmerHost(
                        modifier =
                            Modifier
                                .offset {
                                    IntOffset(x = 0, y = headerOffset)
                                },
                    ) {
                        // Artist Image Placeholder
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.1f),
                        ) {
                            Spacer(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .shimmer()
                                        .background(MaterialTheme.colorScheme.onSurface)
                                        .fadingEdge(
                                            top = systemBarsTopPadding + AppBarHeight,
                                            bottom = 200.dp,
                                        ),
                            )
                        }
                        // Artist Name and Controls Section
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                        ) {
                            // Artist Name Placeholder
                            TextPlaceholder(
                                height = 36.dp,
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.7f)
                                        .padding(bottom = 16.dp),
                            )

                            // Buttons Row Placeholder
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Subscribe Button Placeholder
                                ButtonPlaceholder(
                                    modifier =
                                        Modifier
                                            .width(120.dp)
                                            .height(40.dp),
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                // Right side buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Radio Button Placeholder
                                    ButtonPlaceholder(
                                        modifier =
                                            Modifier
                                                .width(100.dp)
                                                .height(40.dp),
                                    )

                                    // Shuffle Button Placeholder
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .shimmer()
                                                .background(
                                                    MaterialTheme.colorScheme.onSurface,
                                                    RoundedCornerShape(24.dp),
                                                ),
                                    )
                                }
                            }
                        }
                        // Songs List Placeholder
                        repeat(6) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            } else {
                val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl
                if (thumbnail != null) {
                    item(key = "header_photo") {
                        // Only as tall as the part above the name: the photo runs on underneath it and fades out there.
                        val overlap = LocalResources.current.displayMetrics.widthPixels.let { width -> with(density) { ((width / 1.2f) - 144).toDp() } }
                        val width = LocalResources.current.displayMetrics.widthPixels.let { with(density) { it.toDp() } }
                        Box(Modifier.fillMaxWidth().height(overlap)) {
                            AsyncImage(
                                model = thumbnail.resize(1200, 1200),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                        .height(width)
                                        .offset { IntOffset(x = 0, y = headerOffset) }
                                        .fadingEdge(bottom = 200.dp),
                            )
                        }
                    }
                }
                stickyHeader(key = "artist_bar") {
                    ArtistStickyHeader(
                        name = displayArtistName ?: "Unknown",
                        stats = {
                            if (!showLocal) {
                                ArtistStats(
                                    stats =
                                        listOfNotNull(
                                            artistPage?.monthlyListenerCount?.takeIf { it.isNotEmpty() },
                                            artistPage?.subscriberCountText?.takeIf { it.isNotEmpty() },
                                        ),
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                            }
                        },
                        actions = {
                            ArtistActions(
                                subscribed = isChannelSubscribed,
                                onSubscribe = { viewModel.toggleChannelSubscription() },
                                onRadio =
                                    artistPage?.artist?.radioEndpoint?.takeIf { !showLocal && !isGuest }?.let { endpoint ->
                                        { playerConnection.playQueue(YouTubeQueue(endpoint)) }
                                    },
                                onShuffle =
                                    artistPage?.artist?.shuffleEndpoint?.takeIf { !showLocal && !isGuest }?.let { endpoint ->
                                        { playerConnection.playQueue(YouTubeQueue(endpoint)) }
                                    },
                                onPlay = onPlayAllClick.takeIf { !isGuest },
                                online = !showLocal && !isGuest,
                            )
                        },
                        modifier = if (thumbnail == null) Modifier.padding(top = 16.dp) else Modifier,
                    )
                }

                // Show loading shimmer for sections when API hasn't returned yet
                if (artistPage == null && !showLocal) {
                    item(key = "section_shimmer") {
                        ShimmerHost {
                            repeat(4) { ListItemPlaceHolder() }
                        }
                    }
                }

                if (showLocal) {
                    if (librarySongs.isNotEmpty()) {
                        item(key = "local_songs_title") {
                            NavigationTitle(
                                title = stringResource(R.string.songs),
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                },
                            )
                        }

                        val filteredLibrarySongs =
                            if (hideExplicit) {
                                librarySongs.filter { !it.song.explicit }
                            } else {
                                librarySongs
                            }
                        itemsIndexed(
                            items = filteredLibrarySongs,
                            key = { index, item -> "local_song_${item.id}_$index" },
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                showInLibraryIcon = true,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
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
                                            if (!isGuest) {
                                                if (song.id == mediaMetadata?.id) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = displayArtistName ?: "Unknown Artist",
                                                            items = librarySongs.map { it.toMediaItem() },
                                                            startIndex = index,
                                                        ),
                                                    )
                                                }
                                            }
                                        }.animateItem(),
                            )
                        }
                    }

                    if (libraryAlbums.isNotEmpty()) {
                        item(key = "local_albums_title") {
                            NavigationTitle(
                                title = stringResource(R.string.albums),
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/albums")
                                },
                            )
                        }

                        item(key = "local_albums_list") {
                            val filteredLibraryAlbums =
                                if (hideExplicit) {
                                    libraryAlbums.filter { !it.album.explicit }
                                } else {
                                    libraryAlbums
                                }
                            LazyRow(
                                contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                            ) {
                                items(
                                    items = filteredLibraryAlbums,
                                    key = { "local_album_${it.id}_${filteredLibraryAlbums.indexOf(it)}" },
                                ) { album ->
                                    AlbumGridItem(
                                        album = album,
                                        isActive = mediaMetadata?.album?.id == album.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier =
                                            Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        navController.navigate("album/${album.id}")
                                                    },
                                                    onLongClick = {
                                                        menuState.show {
                                                            AlbumMenu(
                                                                originalAlbum = album,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    },
                                                ).animateItem(),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // the user's own files credited to this artist come first: online, the page wouldn't know them
                    if (ownSongs.isNotEmpty()) {
                        item(key = "own_songs_title") {
                            NavigationTitle(
                                title = stringResource(R.string.arc_artist_own_files),
                                modifier = Modifier.animateItem(),
                            )
                        }
                        itemsIndexed(
                            items = ownSongs,
                            key = { _, item -> "own_song_${item.id}" },
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
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
                                                        title = displayArtistName ?: "Unknown Artist",
                                                        items = ownSongs.map { it.toMediaItem() },
                                                        startIndex = index,
                                                    ),
                                                )
                                            }
                                        }.animateItem(),
                            )
                        }
                    }

                    artistPage?.sections?.forEachIndexed { index, section ->
                        if (section.items.isNotEmpty()) {
                            item(key = "section_${section.title}") {
                                NavigationTitle(
                                    title = section.title,
                                    modifier = Modifier.animateItem(),
                                    onClick =
                                        // With no full list to open, a short album section opens into a grid right here.
                                        if (section.moreEndpoint == null && section.items.firstOrNull() is AlbumItem) {
                                            {
                                                navController.navigate("arc_artist_albums/${viewModel.artistId}/${Uri.encode(section.title)}")
                                            }
                                        } else section.moreEndpoint?.let {
                                            {
                                                navController.navigate(
                                                    "artist/${viewModel.artistId}/items?browseId=${it.browseId}?params=${it.params}",
                                                )
                                            }
                                        },
                                )
                            }
                        }

                        if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                            items(
                                items = distinctItemsBySection.getOrNull(index) ?: section.items,
                                key = { "youtube_song_${it.id}" },
                            ) { song ->
                                YouTubeListItem(
                                    item = song as SongItem,
                                    isActive = mediaMetadata?.id == song.id,
                                    isPlaying = isPlaying,
                                    modifier =
                                        Modifier
                                            .background(if (song.id in selection.ids) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                            .selectableSong(song.id, selection) {
                                                if (!isGuest) {
                                                    if (song.id == mediaMetadata?.id) {
                                                        playerConnection.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                WatchEndpoint(videoId = song.id),
                                                                song.toMediaMetadata(),
                                                            ),
                                                        )
                                                    }
                                                }
                                            }.animateItem(),
                                )
                            }
                        } else {
                            if (section.title in albumGridSections) item(key = "section_grid_${section.title}") {
                                ArtistAlbumGrid(
                                    albums = section.items.filterIsInstance<AlbumItem>(),
                                    activeAlbumId = mediaMetadata?.album?.id,
                                    isPlaying = isPlaying,
                                    scope = coroutineScope,
                                    onClick = { album -> navController.navigate("album/${album.id}") },
                                    onLongClick = { album ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show { YouTubeAlbumMenu(albumItem = album, onDismiss = menuState::dismiss) }
                                    },
                                )
                            } else item(key = "section_list_${section.title}") {
                                LazyRow(
                                    contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                                ) {
                                    items(
                                        items = distinctItemsBySection.getOrNull(index) ?: section.items,
                                        key = { "youtube_album_${it.id}" },
                                    ) { item ->
                                        YouTubeGridItem(
                                            item = item,
                                            isActive =
                                                when (item) {
                                                    is SongItem -> mediaMetadata?.id == item.id
                                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                    else -> false
                                                },
                                            isPlaying = isPlaying,
                                            coroutineScope = coroutineScope,
                                            thumbnailRatio = if (item is SongItem && item.isVideoSong) 16f / 9 else 1f, // Use square thumbnails for all items in horizontal scroll
                                            modifier =
                                                Modifier
                                                    .combinedClickable(
                                                        onClick = {
                                                            when (item) {
                                                                is SongItem -> {
                                                                    if (!isGuest) {
                                                                        playerConnection.playQueue(
                                                                            YouTubeQueue(
                                                                                WatchEndpoint(videoId = item.id),
                                                                                item.toMediaMetadata(),
                                                                            ),
                                                                        )
                                                                    }
                                                                }

                                                                is AlbumItem -> {
                                                                    navController.navigate("album/${item.id}")
                                                                }

                                                                is ArtistItem -> {
                                                                    navController.navigate("artist/${item.id}")
                                                                }

                                                                is PlaylistItem -> {
                                                                    navController.navigate("online_playlist/${item.id}")
                                                                }

                                                                is PodcastItem -> {
                                                                    navController.navigate("online_podcast/${item.id}")
                                                                }

                                                                is EpisodeItem -> {
                                                                    if (!isGuest) {
                                                                        playerConnection.playQueue(
                                                                            YouTubeQueue(
                                                                                WatchEndpoint(videoId = item.id),
                                                                                item.toMediaMetadata(),
                                                                            ),
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                when (item) {
                                                                    is SongItem -> {
                                                                        YouTubeSongMenu(
                                                                            song = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is AlbumItem -> {
                                                                        YouTubeAlbumMenu(
                                                                            albumItem = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is ArtistItem -> {
                                                                        YouTubeArtistMenu(
                                                                            artist = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is PlaylistItem -> {
                                                                        YouTubePlaylistMenu(
                                                                            playlist = item,
                                                                            coroutineScope = coroutineScope,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is PodcastItem -> {
                                                                        YouTubePlaylistMenu(
                                                                            playlist = item.asPlaylistItem(),
                                                                            coroutineScope = coroutineScope,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is EpisodeItem -> {
                                                                        YouTubeSongMenu(
                                                                            song = item.asSongItem(),
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        },
                                                    ).animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // The artist's bio comes last, so the music is what you see first.
            if (!showLocal) {
                val description = artistPage?.description
                val descriptionRuns = artistPage?.descriptionRuns
                if (!description.isNullOrEmpty() || !descriptionRuns.isNullOrEmpty()) {
                    item(key = "about_artist") {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 16.dp, bottom = 16.dp)
                                    .animateItem(),
                        ) {
                            Text(
                                text = stringResource(R.string.about_artist),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            ExpandableText(
                                text = description.orEmpty(),
                                runs =
                                    descriptionRuns?.map {
                                        LinkSegment(
                                            text = it.text,
                                            url = it.navigationEndpoint?.urlEndpoint?.url,
                                        )
                                    },
                                collapsedMaxLines = 3,
                            )
                        }
                    }
                }
            }

            // Lets the end of the page be pulled up into easy reach, clear of the mini player.
            item(key = "artist_end_space") { Spacer(Modifier.height(160.dp)) }
        }

        SongSelectionBar(
            selection = selection,
            all = {
                if (showLocal) librarySongs.map { it.id }
                else shownArtistSongs().map { it.id }
            },
            picked = {
                if (showLocal) librarySongs.filter { it.id in selection.ids }.map { it.toMediaMetadata() }
                else shownArtistSongs().filter { it.id in selection.ids }.map { it.toMediaMetadata() }
            },
            onMore = {
                menuState.show {
                    if (showLocal) {
                        SelectionSongMenu(
                            songSelection = librarySongs.filter { it.id in selection.ids },
                            onDismiss = menuState::dismiss,
                            clearAction = selection::clear,
                        compact = true,
                        )
                    } else {
                        YouTubeSelectionSongMenu(
                            songSelection = shownArtistSongs().filter { it.id in selection.ids },
                            onDismiss = menuState::dismiss,
                            clearAction = selection::clear,
                        compact = true,
                        )
                    }
                }
            },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                    .align(Alignment.BottomCenter),
        )
    }

    TopAppBar(
        // The name lives in the sticky header, which slides it up here once the photo is gone.
        title = {},
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
        actions = {
            IconButton(
                onClick = {
                    viewModel.artistPage?.artist?.shareLink?.let { link ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Artist Link", link)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(
                    painterResource(R.drawable.link),
                    contentDescription = null,
                )
            }
        },
        // Never a solid bar: over the photo it's clear, further down a soft fade keeps the back button readable.
        colors = arcTopBarColors(),

    )
}
