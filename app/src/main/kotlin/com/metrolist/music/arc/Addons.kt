package com.metrolist.music.arc

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

val ArcAddonsOnKey = booleanPreferencesKey("arcAddonsOn")
private val ArcAddonsKey = stringPreferencesKey("arcAddons")

/**
 * Music source add-ons (Beta): an add-on is a web address that answers `/manifest.json`, `/search?q=` and
 * `/stream/{id}` with JSON, the protocol BitChord's add-ons speak (BitChord, GPLv3). Nothing is run on the phone;
 * Arc only asks the add-on for a track matching the song and plays the address it returns. Arc ships no add-ons.
 */
object Addons {
    @Serializable
    data class Addon(val base: String, val name: String, val enabled: Boolean = true)

    @Serializable
    private data class Manifest(
        val id: String = "",
        val name: String = "",
        val settings: List<Setting> = emptyList(),
        val bitchordAuth: JsonElement? = null,
    )

    @Serializable
    private data class Setting(val key: String = "", val default: JsonElement? = null, val options: List<Option> = emptyList())

    @Serializable
    private data class Option(val value: JsonElement? = null)

    @Serializable
    private data class SearchResponse(val tracks: List<Track> = emptyList())

    @Serializable
    private data class Track(
        val id: String = "",
        val title: String = "",
        val artist: String = "",
        val duration: Double? = null,
    )

    @Serializable
    private data class Stream(
        val url: String = "",
        @SerialName("error") val error: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
    }
    private val manifests = HashMap<String, Manifest>()

    fun list(context: Context): Flow<List<Addon>> =
        context.dataStore.data.map { prefs -> prefs[ArcAddonsKey]?.let { runCatching { json.decodeFromString<List<Addon>>(it) }.getOrNull() }.orEmpty() }

    private suspend fun save(context: Context, addons: List<Addon>) {
        context.dataStore.edit { it[ArcAddonsKey] = json.encodeToString(addons) }
    }

    /** Checks the address answers like an add-on and adds it under the name it gives itself. */
    suspend fun add(context: Context, raw: String): Result<Addon> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeBase(raw)
            val manifest = manifest(base) ?: error("No add-on answers at that address")
            // these add-ons check they are talking to the official BitChord app and refuse every other one
            if (manifest.bitchordAuth?.text().let { it == "true" || it == "1" }) error("This add-on only works in the BitChord app")
            val addon = Addon(base, manifest.name.ifBlank { base.toHttpUrlOrNull()?.host ?: base })
            save(context, list(context).first().filterNot { it.base == base } + addon)
            addon
        }
    }

    suspend fun remove(context: Context, addon: Addon) = save(context, list(context).first().filterNot { it.base == addon.base })

    suspend fun setEnabled(context: Context, addon: Addon, enabled: Boolean) =
        save(context, list(context).first().map { if (it.base == addon.base) it.copy(enabled = enabled) else it })

    /** A playable address for the song from the first enabled add-on that has a close enough match, or null. */
    suspend fun streamFor(context: Context, title: String, artist: String, durationSeconds: Int): String? = withContext(Dispatchers.IO) {
        if (context.dataStore.data.first()[ArcAddonsOnKey] != true) return@withContext null
        for (addon in list(context).first().filter { it.enabled }) {
            val found = runCatching { find(addon.base, title, artist, durationSeconds) }
                .onFailure { Timber.tag("ArcAddons").w("${addon.name}: ${it.message}") }
                .getOrNull()
            if (found != null) return@withContext found
        }
        null
    }

    private fun find(base: String, title: String, artist: String, durationSeconds: Int): String? {
        val params = params(base)
        val search = get<SearchResponse>(endpoint(base, listOf("search"), params + ("q" to "$artist $title".trim()))) ?: return null
        val wantTitle = normalize(title)
        val wantArtist = normalize(artist)
        val track =
            search.tracks.firstOrNull { track ->
                val sameLength = durationSeconds <= 0 || track.duration == null || abs(track.duration - durationSeconds) <= 4
                val t = normalize(track.title)
                val a = normalize(track.artist)
                sameLength && (t == wantTitle || t.startsWith(wantTitle) || wantTitle.startsWith(t)) &&
                    (wantArtist.isEmpty() || a.contains(wantArtist) || wantArtist.contains(a))
            } ?: return null
        val stream = get<Stream>(endpoint(base, listOf("stream", track.id), params)) ?: return null
        return stream.url.takeIf { it.startsWith("http") && stream.error == null }
    }

    /** The add-on's declared setting defaults, with its best listed quality asked for. */
    private fun params(base: String): Map<String, String> {
        val manifest = manifest(base) ?: return mapOf("quality" to "LOSSLESS")
        val params = LinkedHashMap<String, String>()
        manifest.settings.forEach { s -> s.default?.text()?.let { if (s.key.isNotBlank()) params[s.key] = it } }
        params["quality"] = manifest.settings.firstOrNull { it.key == "quality" }?.options?.firstOrNull()?.value?.text() ?: "LOSSLESS"
        return params
    }

    private fun manifest(base: String): Manifest? =
        synchronized(manifests) { manifests[base] } ?: get<Manifest>("$base/manifest.json")?.also { synchronized(manifests) { manifests[base] = it } }

    private fun endpoint(base: String, path: List<String>, query: Map<String, String>): String {
        val builder: HttpUrl.Builder = base.toHttpUrlOrNull()?.newBuilder() ?: error("Bad add-on address")
        // ids go in as path segments so one holding a / or ? can't turn into another request
        path.forEach { builder.addPathSegment(it) }
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private inline fun <reified T> get(url: String): T? =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            json.decodeFromString<T>(response.body.string())
        }

    private fun normalizeBase(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.endsWith(".json", ignoreCase = true)) trimmed.substringBeforeLast('/') else trimmed
    }

    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\(.*?\\)|\\[.*?]|- remaster.*|feat\\..*"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
