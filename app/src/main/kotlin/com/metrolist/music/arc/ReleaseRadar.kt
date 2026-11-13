package com.metrolist.music.arc

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.DateTimeException
import java.time.LocalDate
import javax.inject.Inject

private const val RADAR_ARTISTS = 20
private const val RELEASES_PER_ARTIST = 3
private const val PARALLEL_REQUESTS = 4
private const val RECENT_DAYS = 60L
private const val DATES_FILE = "arc_release_dates"
private const val TRACKS_FILE = "arc_release_tracks"
private const val SINGLES_FILE = "arc_release_singles"
/** Releases looked up ahead of a tap, so a single can start without first asking YouTube what is on it. */
private const val PREFETCH_RELEASES = 12

enum class ReleaseKind { SINGLE, EP, ALBUM }

/** YouTube's rule of thumb: up to three songs is a single, up to six an EP. */
internal fun releaseKind(trackCount: Int?, youtubeWord: String?): ReleaseKind? {
    if (trackCount != null && trackCount > 0) {
        return when {
            trackCount <= 3 -> ReleaseKind.SINGLE
            trackCount <= 6 -> ReleaseKind.EP
            else -> ReleaseKind.ALBUM
        }
    }
    val word = youtubeWord?.lowercase() ?: return null
    return when {
        word.startsWith("single") || word.startsWith("сингл") -> ReleaseKind.SINGLE
        word == "ep" || word.startsWith("мини") -> ReleaseKind.EP
        word.startsWith("album") || word.startsWith("альбом") -> ReleaseKind.ALBUM
        else -> null
    }
}

private val MonthNames =
    mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "янв" to 1, "фев" to 2, "мар" to 3, "апр" to 4, "мая" to 5, "май" to 5, "июн" to 6,
        "июл" to 7, "авг" to 8, "сен" to 9, "окт" to 10, "ноя" to 11, "дек" to 12,
    )

/** Reads dates like "Oct 3, 2026", "Premiered 3 Oct 2026" or "3 окт. 2026 г.", or returns null. */
internal fun parseVideoDate(text: String?): LocalDate? {
    val value = text ?: return null
    val year = Regex("""\b(20\d{2})\b""").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val day = Regex("""\b(\d{1,2})\b""").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val month =
        Regex("""\p{L}{3,}""")
            .findAll(value)
            .firstNotNullOfOrNull { MonthNames[it.value.take(3).lowercase()] }
            ?: return null
    return try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        null
    }
}

/** Takes turns between artists, so one artist with a big year can't fill the whole row. */
internal fun <T> takeTurns(perArtist: List<List<T>>): List<T> {
    val longest = perArtist.maxOfOrNull { it.size } ?: return emptyList()
    return (0 until longest).flatMap { round -> perArtist.mapNotNull { it.getOrNull(round) } }
}

/**
 * Newest first: your artists' last two months by date, then YouTube's feed of new music by artists you
 * already have, then anything older or undated from your artists.
 */
internal fun <T> orderReleases(own: List<T>, feed: List<T>, dateOf: (T) -> LocalDate?, today: LocalDate): List<T> {
    val cutoff = today.minusDays(RECENT_DAYS)
    val (recent, older) = own.partition { dateOf(it)?.isAfter(cutoff) == true }
    return recent.sortedByDescending { dateOf(it) } + feed + older.sortedByDescending { dateOf(it) }
}

/**
 * This year's albums, EPs and singles from the artists you follow and play most, mixed with
 * YouTube's feed. Artist pages only list a year, so the exact date comes from each release's first
 * song, and is kept on disk since it never changes.
 */
@HiltViewModel
class ReleaseRadarViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
) : ViewModel() {
    private val _releases = MutableStateFlow<List<AlbumItem>>(emptyList())
    val releases =
        combine(_releases, ReleaseFilter.artists(context), ReleaseFilter.releases(context)) { all, _, _ ->
            all.filter { ReleaseFilter.keeps(context, it) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val loaded = MutableStateFlow(false)
    private val _trackCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val trackCounts = _trackCounts.asStateFlow()

    init {
        viewModelScope.launch {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val known =
                database
                    .allArtistsByPlayTime()
                    .first()
                    .sortedByDescending { it.artist.bookmarkedAt != null }
                    .map { it.id }
                    .filter { it.startsWith("UC") }
                    .filterNot { id -> ReleaseFilter.artists(context).value.orEmpty().any { it.id == id } }
            val artists = known.take(RADAR_ARTISTS)
            val knownIds = known.toHashSet()

            // YouTube's feed is everyone's new music; only releases by artists already in the library belong here
            val feedRequest = async(Dispatchers.IO) {
                YouTube.newReleaseAlbums().getOrDefault(emptyList()).filter { release -> release.artists.orEmpty().any { a -> a.id?.let(knownIds::contains) == true } }
            }
            val own = withContext(Dispatchers.IO) { takeTurns(latestReleases(artists)) }
            val feed = feedRequest.await()
            val publish = { dates: Map<String, LocalDate> ->
                _releases.value =
                    orderReleases(own, feed, { dates[it.id] }, LocalDate.now())
                        .distinctBy { it.id }
                        .filterExplicit(hideExplicit)
            }
            publish(savedDates())
            loaded.value = true
            _trackCounts.value = savedTrackCounts()
            publish(withContext(Dispatchers.IO) { loadDates(own) })
            _trackCounts.value = savedTrackCounts()
            prefetchSongs(_releases.value.filter { ReleaseFilter.keeps(context, it) }.take(PREFETCH_RELEASES))
            _trackCounts.value = savedTrackCounts()
        }
    }

    /** The song of a one-song release, looking the release up if that hasn't happened yet; null for longer ones. */
    suspend fun singleSong(release: AlbumItem): String? {
        prefs(SINGLES_FILE).getString(release.id, null)?.let { return it }
        val count = _trackCounts.value[release.id]
        if (count != null && count != 1) return null
        val songs = withContext(Dispatchers.IO) { YouTube.album(release.id).getOrNull()?.songs } ?: return null
        saveSongs(release.id, songs)
        _trackCounts.value = savedTrackCounts()
        return songs.singleOrNull()?.id
    }

    private suspend fun prefetchSongs(releases: List<AlbumItem>) {
        val counts = _trackCounts.value
        val singles = prefs(SINGLES_FILE)
        val gate = Semaphore(PARALLEL_REQUESTS)
        withContext(Dispatchers.IO) {
            releases
                .filter { counts[it.id] == null || (counts[it.id] == 1 && !singles.contains(it.id)) }
                .map { release ->
                    async {
                        gate.withPermit {
                            YouTube.album(release.id).getOrNull()?.songs?.let { saveSongs(release.id, it) }
                        }
                    }
                }.awaitAll()
        }
    }

    private fun saveSongs(releaseId: String, songs: List<SongItem>) {
        if (songs.isEmpty()) return
        prefs(TRACKS_FILE).edit { putInt(releaseId, songs.size) }
        songs.singleOrNull()?.let { song -> prefs(SINGLES_FILE).edit { putString(releaseId, song.id) } }
    }

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun savedDates(): Map<String, LocalDate> =
        context
            .getSharedPreferences(DATES_FILE, Context.MODE_PRIVATE)
            .all
            .mapNotNull { (id, day) -> (day as? Long)?.let { id to LocalDate.ofEpochDay(it) } }
            .toMap()

    private fun savedTrackCounts(): Map<String, Int> =
        context
            .getSharedPreferences(TRACKS_FILE, Context.MODE_PRIVATE)
            .all
            .mapNotNull { (id, count) -> (count as? Int)?.let { id to it } }
            .toMap()

    private suspend fun loadDates(releases: List<AlbumItem>): Map<String, LocalDate> {
        val saved = savedDates()
        val gate = Semaphore(PARALLEL_REQUESTS)
        val found =
            withContext(Dispatchers.IO) {
                releases
                    .filter { it.id !in saved }
                    .map { release ->
                        async {
                            gate.withPermit {
                                val songs = YouTube.album(release.id).getOrNull()?.songs.orEmpty()
                                val firstSong = songs.firstOrNull() ?: return@withPermit null
                                saveSongs(release.id, songs)
                                val date = parseVideoDate(YouTube.videoDateText(firstSong.id).getOrNull())
                                date?.let { release.id to it }
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
                    .toMap()
            }
        context.getSharedPreferences(DATES_FILE, Context.MODE_PRIVATE).edit {
            found.forEach { (id, date) -> putLong(id, date.toEpochDay()) }
        }
        return saved + found
    }

    private suspend fun latestReleases(artistIds: List<String>): List<List<AlbumItem>> {
        val year = LocalDate.now().year
        val gate = Semaphore(PARALLEL_REQUESTS)
        return withContext(Dispatchers.IO) {
            artistIds
                .map { id ->
                    async {
                        gate.withPermit {
                            val page = YouTube.artist(id).getOrNull() ?: return@withPermit emptyList()
                            val credit = listOf(Artist(page.artist.title, id))
                            page.sections
                                .flatMap { it.items }
                                .filterIsInstance<AlbumItem>()
                                .filter { it.year == year }
                                .take(RELEASES_PER_ARTIST)
                                .map { it.copy(artists = credit) }
                        }
                    }
                }.awaitAll()
                .filter { it.isNotEmpty() }
        }
    }
}
