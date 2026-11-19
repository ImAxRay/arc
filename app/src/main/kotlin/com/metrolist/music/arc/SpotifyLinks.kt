package com.metrolist.music.arc

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Spotify links (open.spotify.com and spotify.link) read for what they point at, so Arc can find the same song or
 * album on YouTube Music.
 */
object SpotifyLinks {
    enum class Kind { SONG, ALBUM, PLAYLIST, OTHER }

    data class Track(val title: String, val artists: List<String>, val seconds: Int = -1)

    data class Found(val kind: Kind, val title: String, val artist: String?, val tracks: List<Track> = emptyList())

    /** The embed page lists at most this many songs of a playlist. */
    const val EMBED_TRACK_LIMIT = 100

    private const val AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    private const val DESKTOP = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    private val http by lazy { OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build() }
    private val linkInText = Regex("https?://(?:open\\.spotify\\.com|spotify\\.link|spotify\\.app\\.link)/\\S+")

    fun isSpotify(uri: Uri?) = uri?.host?.let { it == "open.spotify.com" || it == "spotify.link" || it == "spotify.app.link" } == true

    /** The Spotify address inside shared text like "Listen to Hype Boy on Spotify https://open.spotify.com/...". */
    fun findIn(text: String?): String? = text?.let { linkInText.find(it)?.value }

    private val itemPath = Regex("open\\.spotify\\.com/(?:intl-[a-z-]+/)?(track|album|playlist|artist)/([A-Za-z0-9]+)")

    /** The kind and id a link points at; short spotify.link addresses are followed to the full one first. */
    private fun item(url: String): Pair<String, String>? {
        var next = url
        repeat(4) {
            itemPath.find(java.net.URLDecoder.decode(next, "UTF-8"))?.let { return it.groupValues[1] to it.groupValues[2] }
            next = runCatching {
                noRedirects.newCall(Request.Builder().url(next).header("User-Agent", AGENT).build()).execute().use { r ->
                    r.header("Location") ?: r.body.string().let { body -> itemPath.find(java.net.URLDecoder.decode(body, "UTF-8"))?.value?.let { "https://$it" } }
                }
            }.getOrNull() ?: return null
        }
        return null
    }

    private val noRedirects by lazy { http.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    /** Spotify's player page is drawn by script, but its embed page carries the name and artists as data. */
    suspend fun resolve(url: String): Found? = withContext(Dispatchers.IO) {
        val (type, id) = item(url) ?: return@withContext null
        val page = runCatching {
            http.newCall(Request.Builder().url("https://open.spotify.com/embed/$type/$id").header("User-Agent", DESKTOP).build()).execute().use { r ->
                if (r.isSuccessful) r.body.string() else null
            }
        }.getOrNull() ?: return@withContext null
        val title = Regex("\"title\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(page)?.groupValues?.get(1)?.let(::unescapeJson)
            ?: return@withContext null
        val artists = Regex("\"artists\":\\[(.*?)\\]").find(page)?.groupValues?.get(1)
            ?.let { list -> Regex("\"name\":\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(list).map { unescapeJson(it.groupValues[1]) }.toList() }
            .orEmpty()
        val kind = when (type) {
            "track" -> Kind.SONG
            "album" -> Kind.ALBUM
            "playlist" -> Kind.PLAYLIST
            else -> Kind.OTHER
        }
        Found(kind, title, artists.firstOrNull()?.takeIf { type != "artist" && type != "playlist" }, if (kind == Kind.PLAYLIST) tracks(page) else emptyList())
    }

    private fun tracks(page: String): List<Track> = runCatching {
        val data = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL).find(page)!!.groupValues[1]
        val list = org.json.JSONObject(data).getJSONObject("props").getJSONObject("pageProps").getJSONObject("state")
            .getJSONObject("data").getJSONObject("entity").getJSONArray("trackList")
        (0 until list.length()).map { list.getJSONObject(it) }.mapNotNull { t ->
            // org.json reads a JSON null as the text "null".
            fun text(key: String) = if (t.isNull(key)) "" else t.optString(key).trim()
            val name = text("title").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // The embed separates artists with a comma and a no-break space.
            val artists = text("subtitle").replace('\u00A0', ' ').split(',').map { it.trim() }.filter { it.isNotEmpty() }
            Track(name, artists, (t.optLong("duration") / 1000).toInt().takeIf { it > 0 } ?: -1)
        }
    }.getOrDefault(emptyList())

    private fun fold(text: String) =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "")
            .substringBefore(" - ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /** Whether a YouTube Music result is the song or album the link named: the same title and, if known, artist. */
    fun matches(found: Found, title: String, artists: List<String>): Boolean {
        val want = fold(found.title)
        val got = fold(title)
        if (want.isEmpty() || (got != want && !got.startsWith("$want ") && !want.startsWith("$got "))) return false
        val artist = found.artist?.let(::fold)?.takeIf { it.isNotEmpty() } ?: return true
        return artists.map(::fold).any { it == artist || it.contains(artist) || artist.contains(it) && it.length > 2 }
    }

    private fun unescapeJson(text: String) =
        runCatching { org.json.JSONObject("{\"v\":\"$text\"}").getString("v") }.getOrDefault(text)
}
