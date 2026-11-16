/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.utils.completed
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.QuickPicks
import com.metrolist.music.constants.QuickPicksKey
import com.metrolist.music.constants.ShowWrappedCardKey
import com.metrolist.music.constants.WrappedSeenKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.arc.BundledTracks
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.extensions.filterVideoSongs
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.models.SimilarRecommendation
import com.metrolist.music.ui.screens.wrapped.WrappedAudioService
import com.metrolist.music.ui.screens.wrapped.WrappedManager
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

/** Most speed dial can hold: three full pages of the biggest grid. The screen trims it to whole pages. */
internal const val SPEED_DIAL_MAX = 45
private const val MAX_HOME_PAGES = 6

/** Fewer songs than this and Quick picks or Forgotten favourites get topped up with liked songs. */
private const val MIN_PICKS = 10

private val bracketed = Regex("""\s*[(\[][^)\]]*[)\]]""")
private val nonWord = Regex("""[^\p{L}\p{N}]+""")

/**
 * What makes two dial items the same thing: a song uploaded as a song and again as a video has two ids, so
 * songs also match on title and first artist, without "(Official Video)" tails or an "Artist - " prefix.
 */
internal fun speedDialKey(item: YTItem): String {
    val artist = (item as? SongItem)?.artists?.firstOrNull()?.name ?: (item as? AlbumItem)?.artists?.firstOrNull()?.name
    if (item !is SongItem && item !is AlbumItem) return item.id
    fun norm(text: String) = text.lowercase().replace(bracketed, "").replace(nonWord, " ").trim()
    val a = artist?.let(::norm).orEmpty()
    val title = norm(item.title).removePrefix("$a ").trim().ifEmpty { return item.id }
    return "${if (item is SongItem) "song" else "album"}:$title|$a"
}

internal fun buildSpeedDialItems(
    pinned: List<YTItem>,
    keepListening: List<YTItem>,
    quickPicks: List<YTItem>,
    home: List<YTItem>,
    library: List<YTItem> = emptyList(),
): List<YTItem> {
    val seenIds = HashSet<String>()
    val seenKeys = HashSet<String>()
    val items = mutableListOf<YTItem>()
    // pinned ones are the user's own choice and stay even if they're videos
    pinned.forEach { if (seenIds.add(it.id)) items += it.also { item -> seenKeys += speedDialKey(item) } }
    for (item in keepListening + quickPicks + home + library) {
        if (items.size >= SPEED_DIAL_MAX) break
        if (item is SongItem && item.isVideoSong) continue
        if (item.id in seenIds || !seenKeys.add(speedDialKey(item))) continue
        seenIds += item.id
        items += item
    }
    val bundled = items.indexOfFirst { item -> BundledTracks.isBundled(item.id) && pinned.any { it.id == item.id } }
    if (bundled >= 0) items.add(minOf(BundledTracks.DIAL_SLOT, items.size - 1), items.removeAt(bundled))
    return items
}

/**
 * The dial as it stays between refreshes: every tile keeps its item while plays and history change underneath.
 * An unpinned item gives its tile to the next new one, a new pin joins the pins at the front, and only empty
 * slots at the end take items from [fresh].
 */
internal fun keepDialInPlace(
    shown: List<YTItem>,
    fresh: List<YTItem>,
    pinnedBefore: Set<String>,
    pinnedNow: Set<String>,
): List<YTItem> {
    val unpinned = pinnedBefore - pinnedNow
    val used = HashSet<String>()
    shown.forEach { if (it.id !in unpinned) used += speedDialKey(it) }
    val spare = fresh.filter { it.id !in pinnedNow && it.id !in unpinned && speedDialKey(it) !in used }.iterator()
    fun nextSpare(): YTItem? {
        while (spare.hasNext()) {
            val item = spare.next()
            if (used.add(speedDialKey(item))) return item
        }
        return null
    }
    val result = shown.mapNotNull { if (it.id in unpinned) nextSpare() else it }.toMutableList()
    val newPins = fresh.filter { it.id in pinnedNow && it.id !in pinnedBefore && used.add(speedDialKey(it)) }
    result.addAll(result.indexOfLast { it.id in pinnedNow } + 1, newPins)
    while (result.size < fresh.size) result += nextSpare() ?: break
    return result.take(SPEED_DIAL_MAX)
}

private val dialCacheJson = Json { ignoreUnknownKeys = true }

/** The dial as saved for the next cold start, in the form pinned tiles are already stored in. */
internal fun encodeDialCache(items: List<YTItem>): String =
    dialCacheJson.encodeToString(ListSerializer(SpeedDialItem.serializer()), items.filterNot { it is PodcastItem || it is EpisodeItem }.map(SpeedDialItem::fromYTItem))

internal fun decodeDialCache(text: String): List<YTItem> =
    runCatching { dialCacheJson.decodeFromString(ListSerializer(SpeedDialItem.serializer()), text).map { it.toYTItem() } }.getOrDefault(emptyList())

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val wrappedManager: WrappedManager,
    private val wrappedAudioService: WrappedAudioService,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)

    /** Quick picks without the liked-song filler, for the dial, which shows what is played. */
    private val quickPicksForDial = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    /** The song the dial's Continue tile falls back to when the player is empty: the last one played, else the bundled song. */
    val continueSong: StateFlow<Song?> =
        combine(database.latestEvent(), database.song(BundledTracks.firstSongId)) { last, bundled -> last?.song ?: bundled }
            .distinctUntilChanged()
            .onEach { continueReady.value = true }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** What the speed dial shows right now, so the modules under it skip those. */
    val dialShownIds = MutableStateFlow<Set<String>>(emptySet())

    /** Recently played songs for Home's Recents; more than it shows, since the ones on the dial are skipped. */
    val recentSongs: StateFlow<List<Song>> =
        database.recentSongs(40).distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val pinnedSpeedDialItems: StateFlow<List<SpeedDialItem>> =
        database.speedDialDao.getAll()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** The library's most played songs, so speed dial never has empty slots. */
    private val speedDialTopUp =
        database.mostPlayedSongs(fromTimeStamp = LocalDateTime.now().minusYears(30), limit = SPEED_DIAL_MAX)
            .map { songs -> songs.filterNot { it.song.isVideo }.map { it.toSpeedDialSong() } }
            .distinctUntilChanged()

    /** Bumped once when Home's first load is in (or took too long) and on every pull to refresh; only then is the dial rebuilt. */
    private val dialGeneration = MutableStateFlow(0)
    private var dialShown: List<YTItem> = emptyList()
    private var dialPinned: Set<String> = emptySet()
    private var dialBuiltFor = 0

    /** Next cold start's dial: the latest full build, so Home opens with tiles at once and they still change per launch. */
    private val dialCache = File(context.filesDir, "arc-home-dial.json")
    private var dialCacheJob: Job? = null
    private val homeLoaded = MutableStateFlow(false)

    /** Whether the Continue tile's song lookup has answered once; until then page 1 can't be laid out. */
    val continueReady = MutableStateFlow(false)

    /**
     * The song the dial leaves out because the Continue tile shows it, fixed when the dial is built, so a tile tapped
     * later stays put. Kept here, as the dial's own state is lost whenever Home scrolls it away or another tab opens.
     */
    var dialHiddenId: String? = null

    private val freshSpeedDial =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicksForDial,
            homePage,
            speedDialTopUp,
        ) { pinned, keepListening, quick, home, library ->
            pinned.mapTo(HashSet()) { it.id } to buildSpeedDialItems(
                pinned = pinned.map { it.toYTItem() },
                keepListening =
                    keepListening.orEmpty().mapNotNull { item ->
                        when (item) {
                            is Song -> item.takeUnless { it.song.isVideo }?.toSpeedDialSong()

                            is Album ->
                                AlbumItem(
                                    browseId = item.id,
                                    playlistId = item.album.playlistId ?: "",
                                    title = item.title,
                                    artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                    year = item.album.year,
                                    thumbnail = item.thumbnailUrl ?: "",
                                )

                            is com.metrolist.music.db.entities.Artist ->
                                ArtistItem(
                                    id = item.id,
                                    title = item.title,
                                    thumbnail = item.thumbnailUrl,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                )

                            else -> null
                        }
                    },
                quickPicks = quick.orEmpty().filterNot { it.song.isVideo }.map { it.toSpeedDialSong() },
                home = home?.sections.orEmpty().flatMap { it.items },
                library = library,
            )
        }

    /**
     * Speed dial's items. Its sources arrive one by one and reshuffle on every play, so the dial waits for the first
     * load (a skeleton holds its place), then keeps each tile's item until the user refreshes or Arc restarts.
     */
    val speedDialItems: StateFlow<List<YTItem>> =
        combine(freshSpeedDial, dialGeneration, homeLoaded) { (pinned, fresh), generation, loaded ->
            if (generation == 0) return@combine emptyList()
            dialShown =
                if (generation != dialBuiltFor) {
                    dialHiddenId = null
                    fresh
                } else {
                    keepDialInPlace(dialShown, fresh, dialPinned, pinned)
                }
            dialBuiltFor = generation
            dialPinned = pinned
            if (loaded) saveDialCache(fresh)
            dialShown
        }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun saveDialCache(fresh: List<YTItem>) {
        dialCacheJob?.cancel()
        dialCacheJob =
            viewModelScope.launch(Dispatchers.IO) {
                delay(3_000L)
                val part = File(dialCache.path + ".part")
                runCatching {
                    part.writeText(encodeDialCache(fresh))
                    part.renameTo(dialCache)
                }
            }
    }

    private fun Song.toSpeedDialSong() =
        SongItem(
            id = id,
            title = title,
            artists = artists.map { Artist(name = it.name, id = it.id) },
            thumbnail = thumbnailUrl ?: "",
        )

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            // Visual feedback for the animation
            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { songs ->
                userSongs.addAll(songs.map { song ->
                    SongItem(
                        id = song.id,
                        title = song.title,
                        artists = song.artists.map { Artist(name = it.name, id = it.id) },
                        thumbnail = song.thumbnailUrl ?: "",
                        explicit = false
                    )
                })
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        is com.metrolist.music.db.entities.Artist -> otherSources.add(ArtistItem(
                            id = item.id,
                            title = item.title,
                            thumbnail = item.thumbnailUrl,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            // Probability: 80% User Songs, 20% Other Sources
            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

	val showWrappedCard: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        val showWrappedPref = prefs[ShowWrappedCardKey] ?: false
        val seen = prefs[WrappedSeenKey] ?: false
        val isBeforeDate = LocalDate.now().isBefore(LocalDate.of(2026, 2, 1))

        isBeforeDate && (!seen || showWrappedPref)
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val wrappedSeen: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[WrappedSeenKey] ?: false
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun markWrappedAsSeen() {
        viewModelScope.launch(Dispatchers.IO) {
            context.safeDataStoreEdit {
                it[WrappedSeenKey] = true
            }
        }
    }
    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        // own files have no YouTube id, so they can't seed related songs
        val likedSongs = database.likedSongsByCreateDateAsc().first().filterNot { it.song.isLocal }
        // few online likes: the most played online songs seed the rest
        val played = database.mostPlayedSongs(LocalDateTime.now().minusMonths(3), limit = 30).first().filterNot { it.song.isLocal }
        val seeds = (likedSongs.shuffled() + played.shuffled()).distinctBy { it.id }.take(5)
        if (seeds.isEmpty()) return
        
        // Use a synchronized list to collect results safely from concurrent coroutines
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val recommendations = page.songs
                                .filter { item ->
                                    if (hideVideoSongs && item.isVideoSong) return@filter false
                                    if (hideExplicit && item.explicit) return@filter false
                                    true
                                }
                                .shuffled()

                            // Simple check to avoid immediate duplicate of seed
                            val recommendation = recommendations.firstOrNull { rec ->
                                rec.id != seed.id
                            }

                            if (recommendation != null) {
                                items.add(
                                    DailyDiscoverItem(
                                        seed = seed,
                                        recommendation = recommendation,
                                        relatedEndpoint = endpoint
                                    )
                                )
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }
        
        // Final deduplication just in case multiple seeds recommended the same song
        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }.shuffled()
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                // Get similar songs from YouTube based on recent listening
                val recentSong = database.latestEvent().first()?.song
                val ytSimilarSongs = mutableListOf<Song>()

                if (recentSong != null) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = recentSong.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            // Convert YouTube songs to local Song format if they exist in database
                            page.songs.take(10).forEach { ytSong ->
                                database.song(ytSong.id).first()?.let { localSong ->
                                    if (!hideVideoSongs || !localSong.song.isVideo) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                }
                            }
                        }
                    }
                }

                // Combine all sources and remove duplicates
                val combined = (relatedSongs + forgotten + ytSimilarSongs)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(20)

                quickPicksForDial.value = combined
                // related songs build up as songs play; until there are enough, liked songs fill the row (not the dial)
                quickPicks.value =
                    if (combined.size >= MIN_PICKS) combined
                    else (combined + database.likedSongsByCreateDateAsc().first().filterVideoSongs(hideVideoSongs).shuffled()).distinctBy { it.id }.take(20)
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.latestEvent().first()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first().filterVideoSongs(hideVideoSongs).shuffled().take(20)
                    quickPicksForDial.value = quickPicks.value
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fresh = communityPlaylists.value == null
        val fromTimeStamp = LocalDateTime.now().minusWeeks(4)
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 5, offset = 0, toTimeStamp = LocalDateTime.now()).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    YouTube.artist(seed.id).onSuccess { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" && 
                                    playlist.author?.name != "YouTube" && 
                                    playlist.author?.name != "Playlist" &&
                                    playlist.author?.name != seed.artist.name &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
            
            songSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.playlists.forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" && 
                                    playlist.author?.name != "YouTube" && 
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(5)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            uniqueCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {
                            // Use song count from the playlist page if available, otherwise use original
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                            // on the first load each card shows as soon as it's in, instead of all after the slowest
                            if (fresh) communityPlaylists.value = playlists.toList()
                        }
                    }
                }
            }.forEach { it.join() }
        }

        communityPlaylists.value = playlists.toList()
    }

    private suspend fun load() {
        isLoading.value = true
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        val fromTimeStamp = LocalDateTime.now().minusWeeks(2)

        // Phase 1: Load essential sections in parallel — local DB (fast) + YouTube home page.
        // isLoading is set to false as soon as all Phase 1 tasks complete so the UI appears quickly.
        // its own chain of requests, so it starts now rather than after YouTube's home
        viewModelScope.launch(Dispatchers.IO) { getCommunityPlaylists() }
        coroutineScope {
            launch(Dispatchers.IO) { getQuickPicks() }

            launch(Dispatchers.IO) {
                // the query needs a month of history; until it finds enough, liked songs not heard in two weeks fill in
                val found = database.forgottenFavorites().first()
                val forgotten =
                    if (found.size >= MIN_PICKS) found
                    else {
                        val recent = database.mostPlayedSongs(LocalDateTime.now().minusWeeks(2), limit = 500).first().mapTo(HashSet()) { it.id }
                        found + database.likedSongsByCreateDateAsc().first().filterNot { it.id in recent }.shuffled().take(40)
                    }
                forgottenFavorites.value = forgotten.filterVideoSongs(hideVideoSongs).shuffled().take(20)
            }

            launch(Dispatchers.IO) {
                val songs = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 15, offset = 5, toTimeStamp = LocalDateTime.now()).first()
                    .filterVideoSongs(hideVideoSongs).shuffled().take(10)
                val albums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first()
                    .filter { it.album.thumbnailUrl != null }.shuffled().take(5)
                val artists = database.mostPlayedArtists(fromTimeStamp).first()
                    .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
                keepListening.value = (songs + albums + artists).shuffled()
            }

            launch(Dispatchers.IO) {
                YouTube.home().onSuccess { page ->
                    val first = page.copy(sections = page.sections.filtered(hideExplicit, hideVideoSongs, hideYoutubeShorts))
                    // on a refresh the old full page stays until the new one is whole, so rows further down don't blink out
                    if (homePage.value == null) homePage.value = first
                    viewModelScope.launch(Dispatchers.IO) { loadRestOfHome(first, hideExplicit, hideVideoSongs, hideYoutubeShorts) }
                }.onFailure { reportException(it) }
            }

            if (YouTube.cookie != null) {
                launch(Dispatchers.IO) { loadAccountInfo() }
                launch(Dispatchers.IO) { loadAccountPlaylists() }
            }
        }

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }
        isLoading.value = false
        homeLoaded.value = true
        dialGeneration.compareAndSet(0, 1)

        // Phase 2: Heavy multi-request operations — run in background without blocking the UI.
        viewModelScope.launch(Dispatchers.IO) { getDailyDiscover() }

        viewModelScope.launch(Dispatchers.IO) {
            YouTube.explore().onSuccess { page ->
                explorePage.value = page.copy(
                    newReleaseAlbums = page.newReleaseAlbums.filterOutNulls().filterExplicit(hideExplicit),
                    moodAndGenres = page.moodAndGenres.filterOutNulls()
                )
            }.onFailure { reportException(it) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val artistRecommendations = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(4)
                .mapNotNull {
                    val items = mutableListOf<YTItem>()
                    YouTube.artist(it.id).onSuccess { page ->
                        page.sections.takeLast(3).forEach { section -> items += section.items }
                    }
                    SimilarRecommendation(
                        title = it,
                        items = items
                            .distinctBy { item -> item.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled().take(12)
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            val songRecommendations = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 15, offset = 0, toTimeStamp = LocalDateTime.now()).first()
                .filter { it.album != null }
                .shuffled().take(3)
                .mapNotNull { song ->
                    val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                        ?: return@mapNotNull null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                    SimilarRecommendation(
                        title = song,
                        items = (page.songs.shuffled().take(10) +
                                page.albums.shuffled().take(5) +
                                page.artists.shuffled().take(3) +
                                page.playlists.shuffled().take(3))
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            val albumRecommendations = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
                .filter { it.album.thumbnailUrl != null }
                .shuffled().take(2)
                .mapNotNull { album ->
                    val items = mutableListOf<YTItem>()
                    YouTube.album(album.id).onSuccess { page ->
                        page.otherVersions.let { items += it }
                    }
                    album.artists.firstOrNull()?.id?.let { artistId ->
                        YouTube.artist(artistId).onSuccess { page ->
                            page.sections.lastOrNull()?.items?.let { items += it }
                        }
                    }
                    SimilarRecommendation(
                        title = album,
                        items = items
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled().take(10)
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            similarRecommendations.value = (artistRecommendations + songRecommendations + albumRecommendations).shuffled()
            allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                    homePage.value?.sections?.flatMap { it.items }.orEmpty()
        }
    }

    /** YouTube's home comes in pages (five on a signed-in account); every row is a module, so all of them are fetched. */
    private suspend fun loadRestOfHome(first: HomePage, hideExplicit: Boolean, hideVideoSongs: Boolean, hideYoutubeShorts: Boolean) {
        val fresh = homePage.value === first
        var page = first
        repeat(MAX_HOME_PAGES) {
            val next = page.continuation?.let { YouTube.home(it).getOrNull() } ?: return@repeat
            page = HomePage(first.chips, page.sections + next.sections.filtered(hideExplicit, hideVideoSongs, hideYoutubeShorts), next.continuation)
            if (fresh) homePage.value = page
        }
        homePage.value = page
    }

    private fun List<HomePage.Section>.filtered(hideExplicit: Boolean, hideVideoSongs: Boolean, hideYoutubeShorts: Boolean) =
        mapNotNull { section ->
            val items = section.items.filterOutNulls().filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
            if (items.isEmpty()) null else section.copy(items = items)
        }

    private suspend fun loadAccountInfo() {
        YouTube.accountInfo().onSuccess { info ->
            accountName.value = info.name
            accountImageUrl.value = info.thumbnailUrl
            com.metrolist.music.arc.SignedOut.youTube.value = false
        }.onFailure {
            // a saved cookie that no longer opens the account menu means Google ended the session
            if (YouTube.cookie != null && it is NullPointerException) com.metrolist.music.arc.SignedOut.youTube.value = true
            reportException(it)
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterOutNulls()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
        }.onFailure {
            reportException(it)
        }
    }

    /**
     * Safely filters out null items from a list whose type says non-null
     * but may contain nulls at runtime due to JSON parsing.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> List<T>.filterOutNulls(): List<T> =
        (this as List<T?>).filterNotNull()

    /** [fromUser] is a pull to refresh, the one time besides a restart that the dial gets new items. */
    fun refresh(fromUser: Boolean = true) {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) { com.metrolist.music.arc.OwnMusicSync.onPullToRefresh(context, database) }
        viewModelScope.launch(Dispatchers.IO) {
            load()
            if (fromUser) dialGeneration.value++
            isRefreshing.value = false
        }
        // Run sync when user manually refreshes
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    override fun onCleared() {
        super.onCleared()
        wrappedManager.dispose()
    }

    init {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { if (dialCache.exists()) decodeDialCache(dialCache.readText()) else emptyList() }
            if (cached.isNotEmpty() && dialGeneration.value == 0) {
                dialShown = cached
                dialBuiltFor = 1
                dialGeneration.value = 1
            }
        }
        // a slow network must not keep the dial a skeleton
        viewModelScope.launch {
            delay(2_500L)
            dialGeneration.compareAndSet(0, 1)
        }

        // Run sync in separate coroutine with cooldown to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }

        var wasOffline = !networkConnectivity.networkStatus.value
        viewModelScope.launch(Dispatchers.IO) {
            networkConnectivity.networkStatus.collect { isConnected ->
                if (!isConnected) {
                    wasOffline = true
                } else if (wasOffline) {
                    wasOffline = false
                    refresh(fromUser = false)
                }
            }
        }

        // Prepare wrapped data in background
        viewModelScope.launch(Dispatchers.IO) {
            showWrappedCard.collect { shouldShow ->
                if (shouldShow && !wrappedManager.state.value.isDataReady) {
                    try {
                        wrappedManager.prepare()
                        val state = wrappedManager.state.first { it.isDataReady }
                        val trackMap = state.trackMap
                        if (trackMap.isNotEmpty()) {
                            val firstTrackId = trackMap.entries.first().value
                            wrappedAudioService.prepareTrack(firstTrackId)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
        }

        // Listen for cookie changes and reload account data
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] to it[AccountNameKey] }
                .distinctUntilChanged()
                .collect { (cookie, savedAccountName) ->
                    if (!cookie.isNullOrEmpty()) {
                        YouTube.cookie = cookie
                        accountName.value = savedAccountName.orEmpty().ifBlank { "Guest" }
                        loadAccountInfo()
                    } else {
                        accountName.value = "Guest"
                        accountImageUrl.value = null
                        accountPlaylists.value = null
                    }
                }
        }

        // Listen for HideYoutubeShorts preference changes and reload account playlists instantly
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }
    }

    private var isHomeDataLoaded = false

    fun loadHomeData() {
        if (isHomeDataLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cookie = context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .distinctUntilChanged()
                    .first()

                if (!cookie.isNullOrEmpty()) {
                    YouTube.cookie = cookie
                }

                isHomeDataLoaded = true
                load()
            } catch (e: Exception) {
                isHomeDataLoaded = false
                Timber.e(e, "Failed to load home data")
            }
        }
    }
}
