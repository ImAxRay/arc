package com.metrolist.music.arc

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

val ArcUntitledOnKey = booleanPreferencesKey("arcUntitledOn")

/**
 * Untitled (untitled.stream) as a music source (Beta): the user signs in on Untitled's own page, and the projects
 * in their library become songs "UNTITLED_" plus the track id, streamed the way Untitled's web player streams them
 * (its page data, then a signed address for the transcoded MP3). Unofficial, so it can stop working with any
 * Untitled update. Nothing is kept offline: the artist may have limited what listeners can do.
 */
object UntitledMusic {
    const val PREFIX = "UNTITLED_"
    const val SITE = "https://untitled.stream"
    const val SIGN_IN_PAGE = "$SITE/library"
    private const val TAG = "ArcUntitled"
    private val CookieKey = stringPreferencesKey("arcUntitledCookie")
    private val LastSyncKey = longPreferencesKey("arcUntitledLastSync")
    private const val SYNC_COOLDOWN_MS = 5 * 60_000L

    private val http by lazy { OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).followRedirects(false).build() }

    /** Where each synced track's transcoded file lives in Untitled's storage, by library id. */
    private val paths = HashMap<String, String>()
    private var pathsLoaded = false
    private val signed = HashMap<String, Pair<String, Long>>()

    fun isUntitledSong(mediaId: String?) = mediaId?.startsWith(PREFIX) == true

    fun signedIn(context: Context): Flow<Boolean> = context.dataStore.data.map { it[CookieKey] != null }

    /** Takes the session the sign-in page left in the web view's cookies. */
    suspend fun keepSession(context: Context): Boolean {
        val cookies = CookieManager.getInstance().getCookie(SITE)?.takeIf { it.contains("=") } ?: return false
        context.dataStore.edit { it[CookieKey] = cookies }
        // a page in the library can still be the signed-out one: keep the cookies only if they open the library
        val works = runCatching {
            get(context, "/library?_data=" + URLEncoder.encode("routes/library._index", "UTF-8")).has("projects")
        }.getOrDefault(false)
        if (!works) context.dataStore.edit { it.remove(CookieKey) }
        return works
    }

    suspend fun signOut(context: Context, database: MusicDatabase) {
        context.dataStore.edit {
            it.remove(CookieKey)
            it.remove(LastSyncKey)
        }
        CookieManager.getInstance().apply {
            getCookie(SITE)?.split(';')?.map { it.substringBefore('=').trim() }?.forEach { name ->
                setCookie(SITE, "$name=; Max-Age=0; Path=/")
            }
            flush()
        }
        withContext(Dispatchers.IO) { database.query { deleteSongs(database.songIdsWithPrefix(PREFIX)) } }
        synchronized(this) {
            paths.clear()
            signed.clear()
            File(context.filesDir, "untitled-tracks.json").delete()
        }
    }

    private suspend fun get(context: Context, path: String): JSONObject = withContext(Dispatchers.IO) {
        val cookie = context.dataStore.data.first()[CookieKey] ?: error("Not signed in to Untitled")
        val request = Request.Builder().url(SITE + path)
            .header("Cookie", cookie)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            // the site renews the session in cookies it sends back; keep them, or the next call is signed out
            val renewed = response.headers("Set-Cookie")
            if (renewed.isNotEmpty()) context.dataStore.edit { it[CookieKey] = mergeCookies(cookie, renewed) }
            if (response.code in 300..399 || response.code == 401) error("Untitled signed you out, sign in again")
            if (!response.isSuccessful) error("Untitled answered ${response.code}")
            JSONObject(response.body.string())
        }
    }

    private fun mergeCookies(old: String, setCookies: List<String>): String {
        val jar = LinkedHashMap<String, String>()
        old.split(';').map { it.trim() }.filter { '=' in it }.forEach { jar[it.substringBefore('=')] = it.substringAfter('=') }
        setCookies.forEach { header ->
            val pair = header.substringBefore(';')
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=', "")
            if (value.isEmpty() || header.contains("Max-Age=0", ignoreCase = true)) jar.remove(name) else jar[name] = value
        }
        return jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** Adds the tracks of every project in the user's Untitled library and drops ones no longer there. */
    suspend fun sync(context: Context, database: MusicDatabase, force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        if (prefs[ArcUntitledOnKey] == false || prefs[CookieKey] == null) return@withContext 0
        if (!force && System.currentTimeMillis() - (prefs[LastSyncKey] ?: 0L) < SYNC_COOLDOWN_MS) return@withContext 0
        val library = get(context, "/library?_data=" + URLEncoder.encode("routes/library._index", "UTF-8"))
        val projects = library.optJSONArray("projects") ?: return@withContext 0
        class Found(val title: String, val artist: String, val album: String, val seconds: Int, val path: String, val art: String?)
        val found = LinkedHashMap<String, Found>()
        for (i in 0 until projects.length()) {
            val slug = projects.optJSONObject(i)?.optJSONObject("project")?.optString("slug")?.takeIf { it.isNotBlank() } ?: continue
            val page = runCatching {
                get(context, "/library/project/$slug?_data=" + URLEncoder.encode("routes/library.project.\$projectSlug", "UTF-8"))
            }.onFailure { Timber.tag(TAG).w("project $slug: ${it.message}") }.getOrNull() ?: continue
            val project = page.optJSONObject("project") ?: continue
            val info = project.optJSONObject("project") ?: continue
            val album = info.optString("title").ifBlank { "Untitled" }
            val art = info.optString("artwork_small_signed_url").takeIf { it.startsWith("https://") }
                ?: info.optString("artwork_signed_url").takeIf { it.startsWith("https://") }
            val tracks = project.optJSONArray("tracks") ?: continue
            for (t in 0 until tracks.length()) {
                val track = tracks.optJSONObject(t) ?: continue
                val id = track.optString("id").takeIf { it.isNotBlank() } ?: continue
                val path = track.optString("audio_fallback_url").substringAfter("/private-transcoded-audio/", "").substringBefore('?')
                if (path.isBlank()) continue
                found[PREFIX + id] = Found(
                    title = track.optString("title").ifBlank { "untitled track" },
                    artist = track.optString("username").ifBlank { info.optString("username") },
                    album = album,
                    seconds = track.optDouble("duration", -1.0).takeIf { it > 0 }?.roundToInt() ?: -1,
                    path = path,
                    art = art,
                )
            }
        }
        val known = database.songIdsWithPrefix(PREFIX).toSet()
        val gone = known - found.keys
        if (gone.isNotEmpty()) database.query { deleteSongs(gone.toList()) }
        val arts = HashMap<String, String?>()
        found.forEach { (id, f) ->
            val art = f.art?.let { url -> arts.getOrPut(f.album) { saveArt(context, id, url) } }
            database.transaction {
                if (id in known) {
                    getSongByIdBlocking(id)?.song?.let { update(it.copy(title = f.title, albumName = f.album, duration = f.seconds, thumbnailUrl = art ?: it.thumbnailUrl)) }
                } else {
                    insert(
                        SongEntity(
                            id = id,
                            title = f.title,
                            duration = f.seconds,
                            thumbnailUrl = art,
                            albumName = f.album,
                            inLibrary = LocalDateTime.now(),
                            isLocal = true,
                        ),
                    )
                    if (f.artist.isNotBlank()) {
                        val artistId = artistByName(f.artist)?.id
                            ?: ArtistEntity.generateArtistId().also { insert(ArtistEntity(id = it, name = f.artist, isLocal = true)) }
                        insert(SongArtistMap(songId = id, artistId = artistId, position = 0))
                    }
                }
            }
        }
        synchronized(this@UntitledMusic) {
            paths.clear()
            found.forEach { (id, f) -> paths[id] = f.path }
            pathsLoaded = true
            runCatching {
                File(context.filesDir, "untitled-tracks.json").writeText(JSONObject(paths as Map<*, *>).toString())
            }
        }
        context.dataStore.edit { it[LastSyncKey] = System.currentTimeMillis() }
        (found.keys - known).size
    }

    private fun saveArt(context: Context, id: String, url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            DeviceMusic.saveArtBytes(context, id, r.body.bytes())
        }
    }.getOrNull()

    private fun path(context: Context, mediaId: String): String? = synchronized(this) {
        if (!pathsLoaded) {
            pathsLoaded = true
            runCatching {
                val saved = JSONObject(File(context.filesDir, "untitled-tracks.json").readText())
                saved.keys().forEach { paths[it] = saved.getString(it) }
            }
        }
        paths[mediaId]
    }

    /** A playable address for the track, signed for three hours by Untitled the way its own player asks. */
    suspend fun streamUrl(context: Context, mediaId: String): String {
        synchronized(this) { signed[mediaId]?.let { (url, until) -> if (System.currentTimeMillis() < until - 600_000L) return url } }
        val path = path(context, mediaId) ?: error("This Untitled song isn't synced yet")
        val answer = get(
            context,
            "/api/storage/buckets/private-transcoded-audio/objects/" + URLEncoder.encode(path, "UTF-8").replace("+", "%20") +
                "/signedUrl?durationInSeconds=10800&cacheBufferInSeconds=600",
        )
        val url = answer.optString("url").takeIf { it.startsWith("https://") } ?: error("Untitled gave no address")
        val lasts = answer.optLong("expiresInMs", 3 * 3_600_000L)
        synchronized(this) { signed[mediaId] = url to System.currentTimeMillis() + lasts }
        return url
    }
}
