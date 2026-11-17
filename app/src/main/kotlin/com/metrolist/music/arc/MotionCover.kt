package com.metrolist.music.arc

import android.content.Context
import android.net.ConnectivityManager
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.metrolist.music.utils.rememberPreference
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

val ArcMotionCoversKey = booleanPreferencesKey("arcMotionCovers")
private const val FRAME = 96
private const val FRAME_EVERY_MS = 250L
val ArcMotionCoversMobileKey = booleanPreferencesKey("arcMotionCoversMobile")

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MotionCoverEntryPoint {
    fun databaseProvider(): DatabaseProvider
}

/**
 * Apple Music's motion artwork (the looping album sleeve, `editorialVideo` in its catalog), found with the web
 * player's anonymous token read from music.apple.com's script, as ink and BitChord do. Unofficial, so it is Beta.
 */
object MotionCovers {
    private const val TAG = "ArcMotionCover"
    private const val CATALOG = "https://amp-api.music.apple.com/v1/catalog"
    private const val AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
    private const val MISS_TTL = 7 * 86_400_000L

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    }
    private val storefront by lazy { Locale.getDefault().country.lowercase(Locale.ROOT).takeIf { it.length == 2 } ?: "us" }

    private val known = HashMap<String, Pair<String?, Long>>()
    private var loaded = false
    private var token: String? = null
    private var tokenUntil = 0L
    private var nextTokenTry = 0L
    private val refused = HashSet<String>()

    @Volatile private var cache: SimpleCache? = null

    /** A small, recent frame of the playing motion cover, for the glow under it; null when none is playing. */
    val liveFrame = androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)

    fun allowed(context: Context, onMobile: Boolean): Boolean {
        if (Visuals.still.value) return false
        val metered = context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
        return onMobile || !metered
    }

    /** The square motion cover (an HLS link) for the song's album, or null when Apple has none or can't be asked. */
    @Synchronized
    fun find(context: Context, title: String, artist: String, album: String?): String? {
        if (title.isBlank() || artist.isBlank()) return null
        load(context)
        val key = fold("$artist|${cut(title)}|${album?.let(::cut).orEmpty()}")
        known[key]?.let { (url, at) -> if (url != null || System.currentTimeMillis() - at < MISS_TTL) return url }
        var result: Result<String?> = Result.failure(IllegalStateException("no token"))
        for (attempt in 0 until 3) {
            val bearer = token() ?: return null
            result = runCatching { lookup(title, artist, album, bearer) }
            if (bearer !in refused) break
        }
        result.onFailure { Timber.tag(TAG).w(it.message) }
        if (result.isFailure) return null
        val url = result.getOrNull()
        known[key] = url to System.currentTimeMillis()
        save(context)
        return url
    }

    private fun lookup(title: String, artist: String, album: String?, bearer: String): String? {
        val songs = get(search("$artist ${cut(title)}", "songs"), bearer).results("songs")
        val wantTitle = fold(cut(title))
        val wantAlbum = album?.let { fold(cut(it)) }?.takeIf { it.isNotEmpty() }
        val albumIds = (0 until (songs?.length() ?: 0)).mapNotNull { i ->
            val at = songs!!.optJSONObject(i)?.optJSONObject("attributes") ?: return@mapNotNull null
            if (!sameArtist(artist, at.optString("artistName"))) return@mapNotNull null
            val theirs = fold(cut(at.optString("name")))
            if (theirs != wantTitle && !theirs.startsWith("$wantTitle ") && !wantTitle.startsWith("$theirs ")) return@mapNotNull null
            val id = at.optString("url").substringAfter("/album/", "").substringBefore("?").substringAfterLast("/")
                .takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return@mapNotNull null
            val score = (if (wantAlbum != null && fold(cut(at.optString("albumName"))) == wantAlbum) 2 else 0) +
                (if (theirs == wantTitle) 1 else 0)
            score to id
        }.sortedByDescending { it.first }.map { it.second }.distinct()
        for (id in albumIds.take(3)) {
            val data = JSONObject(get("$CATALOG/$storefront/albums/$id?extend=editorialVideo", bearer))
                .optJSONArray("data")?.optJSONObject(0)?.optJSONObject("attributes") ?: continue
            motion(data)?.let { return it }
        }
        if (wantAlbum == null) return null
        val albums = get(search("$artist ${cut(album)}", "albums", extend = true), bearer).results("albums") ?: return null
        for (i in 0 until albums.length()) {
            val at = albums.optJSONObject(i)?.optJSONObject("attributes") ?: continue
            if (!sameArtist(artist, at.optString("artistName")) || fold(cut(at.optString("name"))) != wantAlbum) continue
            motion(at)?.let { return it }
        }
        return null
    }

    private fun search(term: String, type: String, extend: Boolean = false): String =
        "$CATALOG/$storefront/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("types", type)
            .addQueryParameter("limit", "10")
            .apply { if (extend) addQueryParameter("extend", "editorialVideo") }
            .build().toString()

    private fun String.results(type: String) =
        JSONObject(this).optJSONObject("results")?.optJSONObject(type)?.optJSONArray("data")

    private fun motion(attributes: JSONObject): String? {
        val video = attributes.optJSONObject("editorialVideo") ?: return null
        return listOf("motionDetailSquare", "motionSquareVideo1x1").firstNotNullOfOrNull {
            video.optJSONObject(it)?.optString("video")?.takeIf { url -> url.startsWith("https://") }
        }
    }

    private fun sameArtist(ours: String, theirs: String): Boolean {
        val a = fold(ours)
        val credited = fold(theirs)
        if (a.isEmpty() || credited.isEmpty()) return false
        return credited == a || credited.split(Regex(" (?:and|feat|ft|x|with) | ?[,&] ?")).any { it.trim() == a } ||
            credited.startsWith("$a ")
    }

    /** A name without its edition: "Blue (Deluxe Edition)" and "Blue - Remastered" are both Blue. */
    private fun cut(name: String) =
        name.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").substringBefore(" - ").trim()

    private fun fold(text: String) =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}&,]+"), " ")
            .trim()

    private fun token(): String? {
        val now = System.currentTimeMillis()
        token?.let { if (now < tokenUntil - 3_600_000L) return it }
        if (now < nextTokenTry) return null
        nextTokenTry = now + 30 * 60_000L
        val page = runCatching { get("https://music.apple.com/us/browse", null) }.getOrNull() ?: return null
        val scripts = Regex("/assets/index(?:-legacy)?[~-][A-Za-z0-9_-]+\\.js").findAll(page).map { it.value }.distinct()
        for (path in scripts) {
            val js = runCatching { get("https://music.apple.com$path", null) }.getOrNull() ?: continue
            val found = Regex("ey[A-Za-z0-9_-]+\\.ey[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").findAll(js).map { it.value }.distinct()
                .mapNotNull { jwt ->
                    runCatching {
                        val parts = jwt.split(".")
                        val head = String(Base64.getUrlDecoder().decode(parts[0]))
                        val body = String(Base64.getUrlDecoder().decode(parts[1]))
                        val exp = Regex("\"exp\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toLong()?.times(1000)
                            ?: return@runCatching null
                        Triple(jwt, exp, "WebPlayKid" in head || "AMPWebPlay" in body)
                    }.getOrNull()
                }
                .filter { it.second > now && it.first !in refused }.toList()
            val best = found.firstOrNull { it.third } ?: found.firstOrNull() ?: continue
            token = best.first
            tokenUntil = best.second
            return best.first
        }
        return null
    }

    private fun get(url: String, bearer: String?): String {
        val request = Request.Builder().url(url).header("User-Agent", AGENT).apply {
            if (bearer != null) {
                header("Authorization", "Bearer $bearer")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
            }
        }.build()
        http.newCall(request).execute().use { response ->
            if ((response.code == 401 || response.code == 403) && bearer != null) {
                refused += bearer
                token = null
                tokenUntil = 0
                nextTokenTry = 0
            }
            if (!response.isSuccessful) error("http ${response.code}")
            return response.body.string()
        }
    }

    private fun file(context: Context) = File(context.filesDir, "motion-covers.json")

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(file(context).readText())
            o.keys().forEach { k ->
                val e = o.getJSONObject(k)
                known[k] = e.optString("url").ifEmpty { null } to e.optLong("at")
            }
        }
    }

    private fun save(context: Context) {
        runCatching {
            val o = JSONObject()
            known.forEach { (k, v) -> o.put(k, JSONObject().put("url", v.first ?: "").put("at", v.second)) }
            file(context).writeText(o.toString())
        }
    }

    @OptIn(UnstableApi::class)
    fun clipCache(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.cacheDir, "motion_covers"),
                LeastRecentlyUsedCacheEvictor(80L * 1024 * 1024),
                EntryPointAccessors.fromApplication(context.applicationContext, MotionCoverEntryPoint::class.java)
                    .databaseProvider(),
            ).also { cache = it }
        }

    @OptIn(UnstableApi::class)
    fun player(context: Context): ExoPlayer {
        val upstream = OkHttpDataSource.Factory(http).setUserAgent(AGENT)
        val source = CacheDataSource.Factory().setCache(clipCache(context)).setUpstreamDataSourceFactory(upstream)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(HlsMediaSource.Factory(source))
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setMaxVideoSize(1080, 1080)
                    // the loop is only seconds long, so adaptive streaming never gets the chance to step up from
                    // the small first rendition: take the sharpest one up to the cap from the start
                    .setForceHighestSupportedBitrate(true)
                    .build()
            }
    }
}

/**
 * The song's motion cover, looping and muted over the still one, which stays visible until the first frame and
 * whenever there is no clip. [active] pauses it while the player is hidden or the music is paused.
 */
@OptIn(UnstableApi::class)
@Composable
fun MotionCover(
    title: String,
    artist: String,
    album: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val enabled by rememberPreference(ArcMotionCoversKey, true)
    val onMobile by rememberPreference(ArcMotionCoversMobileKey, false)
    val url by produceState<String?>(null, title, artist, album, enabled, onMobile) {
        value = null
        if (!enabled || !MotionCovers.allowed(context, onMobile)) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { MotionCovers.find(context.applicationContext, title, artist, album) }
                .onFailure { Timber.tag("ArcMotionCover").w(it) }
                .getOrNull()
        }
    }
    val clip = url ?: return

    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = active && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    var shown by remember(clip) { mutableStateOf(false) }
    var aspect by remember(clip) { mutableFloatStateOf(1f) }
    val player = remember(clip) {
        MotionCovers.player(context.applicationContext).apply {
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { shown = true }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) aspect = videoSize.width.toFloat() / videoSize.height
                }
            })
            setMediaItem(MediaItem.fromUri(clip))
            prepare()
        }
    }
    var view by remember(clip) { mutableStateOf<TextureView?>(null) }
    DisposableEffect(player) {
        onDispose {
            MotionCovers.liveFrame.value = null
            player.release()
        }
    }
    LaunchedEffect(player, running) { player.playWhenReady = running }
    LaunchedEffect(player, running, shown) {
        if (!running || !shown) return@LaunchedEffect
        val frames = Array(2) { android.graphics.Bitmap.createBitmap(FRAME, FRAME, android.graphics.Bitmap.Config.ARGB_8888) }
        var next = 0
        while (true) {
            view?.takeIf { it.isAvailable }?.let { v ->
                v.getBitmap(frames[next])
                MotionCovers.liveFrame.value = frames[next].asImageBitmap()
                next = 1 - next
            }
            kotlinx.coroutines.delay(FRAME_EVERY_MS)
        }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(600), label = "motionCoverAlpha")

    Box(modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            factory = { TextureView(it).apply { isOpaque = false }.also { v -> view = v } },
            update = { player.setVideoTextureView(it) },
            onRelease = { player.clearVideoTextureView(it) },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = aspect.coerceAtLeast(1f)
                    scaleY = (1f / aspect).coerceAtLeast(1f)
                },
        )
    }
}
