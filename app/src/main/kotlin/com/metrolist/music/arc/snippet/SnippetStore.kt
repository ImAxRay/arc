package com.metrolist.music.arc.snippet

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File

val ArcSnippetKeepKey = booleanPreferencesKey("arcSnippetKeep")

/**
 * Kept snippets, so they can be opened and mixed again later. Each one keeps its song and voice as separate AAC files
 * (about 3 MB a minute together) and how it was last mixed; the mix itself goes to Recordings/Arc.
 */
object SnippetStore {
    class Entry(val id: Long, val title: String, val artist: String, val cover: String?, val lengthMs: Long)

    /** Work that has to finish after the editor has closed. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Bumped whenever a snippet is kept or deleted, so the list reloads. */
    val changes = MutableStateFlow(0)
    private val lock = Mutex()

    private fun root(context: Context) = File(context.filesDir, "snippets")
    private fun folder(context: Context, id: Long) = File(root(context), id.toString())

    suspend fun list(context: Context): List<Entry> = withContext(Dispatchers.IO) {
        root(context).listFiles().orEmpty().mapNotNull { dir ->
            runCatching {
                val info = JSONObject(File(dir, "info.json").readText())
                val mix = info.getJSONObject("mix")
                Entry(
                    id = info.getLong("id"),
                    title = info.getString("title"),
                    artist = info.getString("artist"),
                    cover = info.optString("cover").ifEmpty { null },
                    lengthMs = (mix.getInt("to") - mix.getInt("from")) * 1000L / info.getInt("rate"),
                )
            }.getOrNull()
        }.sortedByDescending { it.id }
    }

    /** Keeps [take] mixed as [mix]: the stems the first time, then the mix in Recordings/Arc and how it was made. */
    suspend fun keep(context: Context, take: Snippet.Take, mix: SnippetMix): Snippet.Stored = lock.withLock {
        withContext(Dispatchers.IO) {
            val dir = folder(context, take.id).apply { mkdirs() }
            val song = File(dir, "song.m4a")
            val voice = File(dir, "voice.m4a")
            if (!song.exists() || !voice.exists()) {
                encodeAac(song, take.rate, 2, take.frames, 256_000) { out, start, end ->
                    System.arraycopy(take.song, start * 2, out, 0, (end - start) * 2)
                }
                val voiceFrames = take.voice.size
                encodeAac(voice, take.voiceRate, 1, voiceFrames, 128_000) { out, start, end ->
                    System.arraycopy(take.voice, start, out, 0, end - start)
                }
            }
            val old = File(dir, "info.json").takeIf { it.exists() }?.let { JSONObject(it.readText()).optString("savedAt").ifEmpty { null } }
            val savedAt = saveSnippet(context, take.export(context, mix, folder = "snippets-keep"), old ?: take.stored?.savedAt)
            val info = JSONObject()
                .put("id", take.id)
                .put("title", take.title)
                .put("artist", take.artist)
                .put("cover", take.cover ?: "")
                .put("bluetooth", take.bluetooth)
                .put("voiceLevel", take.voiceLevel.toDouble())
                .put("rate", take.rate)
                .put("voiceRate", take.voiceRate)
                .put("savedAt", savedAt)
                .put(
                    "mix",
                    JSONObject()
                        .put("from", mix.from)
                        .put("to", mix.to)
                        .put("songGain", mix.songGain.toDouble())
                        .put("voiceGain", mix.voiceGain.toDouble())
                        .put("syncMs", mix.syncMs),
                )
            File(dir, "info.json.tmp").apply { writeText(info.toString()) }.renameTo(File(dir, "info.json"))
            changes.value++
            Snippet.Stored(mix, savedAt)
        }
    }

    /** Opens a kept snippet for editing. The stems come back a few ms longer at the start (the encoder's lead-in), the same for both, so the voice stays in time with the song. */
    suspend fun load(context: Context, id: Long): Snippet.Take? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = folder(context, id)
            val info = JSONObject(File(dir, "info.json").readText())
            val mix = info.getJSONObject("mix")
            val song = decodeAudio(File(dir, "song.m4a"), 2)
            val voice = decodeAudio(File(dir, "voice.m4a"), 1)
            val frames = song.size / 2
            Snippet.Take(
                song = song,
                rate = info.getInt("rate"),
                voice = voice,
                voiceRate = info.getInt("voiceRate"),
                title = info.getString("title"),
                artist = info.getString("artist"),
                cover = info.optString("cover").ifEmpty { null },
                bluetooth = info.getBoolean("bluetooth"),
                voiceLevel = info.getDouble("voiceLevel").toFloat(),
                id = id,
                stored = Snippet.Stored(
                    SnippetMix(
                        from = mix.getInt("from").coerceIn(0, frames),
                        to = mix.getInt("to").coerceIn(0, frames),
                        songGain = mix.getDouble("songGain").toFloat(),
                        voiceGain = mix.getDouble("voiceGain").toFloat(),
                        syncMs = mix.getInt("syncMs"),
                    ),
                    info.optString("savedAt").ifEmpty { null },
                ),
            )
        }.onFailure { Timber.w(it, "snippet $id") }.getOrNull()
    }

    /** Deletes the snippet with [id], its copy in Recordings/Arc included. */
    suspend fun delete(context: Context, id: Long, savedAt: String?) = lock.withLock {
        withContext(Dispatchers.IO) {
            val dir = folder(context, id)
            val where = savedAt ?: runCatching { JSONObject(File(dir, "info.json").readText()).optString("savedAt") }.getOrNull()
            where?.takeIf { it.isNotEmpty() }?.let { unsaveSnippet(context, it) }
            dir.deleteRecursively()
            changes.value++
        }
    }
}
