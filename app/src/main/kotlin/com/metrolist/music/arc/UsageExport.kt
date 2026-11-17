package com.metrolist.music.arc

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.metrolist.music.BuildConfig
import com.metrolist.music.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

private const val TOP = 100

/** Writes everything [Usage] counted, plus a summary of the listen history it builds on, and opens the share sheet. */
suspend fun shareUsageExport(context: Context, database: MusicDatabase) {
    val file =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val since = LocalDateTime.of(2000, 1, 1, 0, 0)
            val songs = database.mostPlayedSongs(since, limit = TOP).first()
            val artists = database.mostPlayedArtists(since, limit = TOP).first()
            val albums = database.mostPlayedAlbums(since, limit = TOP).first()
            val data =
                buildJsonObject {
                    put("format", "arc-usage")
                    put("version", 1)
                    put("app", displayVersion(BuildConfig.BASE_VERSION_NAME))
                    put("exportedAt", Instant.ofEpochMilli(now).toString())
                    put("note", "Counted on the phone only. Scores halve every 14 days without use.")
                    putJsonArray("usage") {
                        Usage.snapshot().entries.sortedByDescending { UsageBook.scoreAt(it.value, now) }.forEach { (key, e) ->
                            addJsonObject {
                                put("kind", key.substringBefore(':'))
                                put("id", key.substringAfter(':'))
                                put("title", e.title)
                                put("plays", e.plays)
                                put("opens", e.opens)
                                put("homeTaps", e.homeTaps)
                                put("skips", e.skips)
                                put("score", Math.round(UsageBook.scoreAt(e, now) * 100) / 100.0)
                                put("firstUsed", Instant.ofEpochMilli(e.first).toString())
                                put("lastUsed", Instant.ofEpochMilli(e.last).toString())
                                putJsonObject("byTimeOfDay") {
                                    listOf("night", "morning", "afternoon", "evening").forEachIndexed { i, name -> put(name, e.hours.getOrElse(i) { 0 }) }
                                }
                                put("onWeekends", e.weekend)
                            }
                        }
                    }
                    putJsonObject("history") {
                        putJsonArray("topSongs") {
                            songs.forEach { s ->
                                addJsonObject {
                                    put("id", s.id)
                                    put("title", s.song.title)
                                    putJsonArray("artists") { s.artists.forEach { add(it.name) } }
                                    minutes(s.song.totalPlayTime)
                                }
                            }
                        }
                        putJsonArray("topArtists") {
                            artists.forEach { a ->
                                addJsonObject {
                                    put("id", a.id)
                                    put("name", a.artist.name)
                                    put("plays", a.songCount)
                                    minutes(a.timeListened?.toLong())
                                }
                            }
                        }
                        putJsonArray("topAlbums") {
                            albums.forEach { a ->
                                addJsonObject {
                                    put("id", a.id)
                                    put("title", a.album.title)
                                    put("plays", a.songCountListened)
                                    minutes(a.timeListened)
                                }
                            }
                        }
                    }
                }
            val dir = File(context.cacheDir, "usage_export").apply { listFiles()?.forEach(File::delete); mkdirs() }
            File(dir, "arc-usage-${LocalDate.now()}.json").apply { writeText(Json { prettyPrint = true }.encodeToString(data)) }
        }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

private fun JsonObjectBuilder.minutes(ms: Long?) = put("minutesListened", (ms ?: 0L) / 60_000)

/** A fourth version part is shown as a Dota-style letter patch: 0.9.4.1 reads 0.9.4a. */
fun displayVersion(name: String): String {
    val parts = name.substringBefore('+').split('.')
    val patch = parts.getOrNull(3)?.toIntOrNull()?.takeIf { it in 1..26 } ?: return name.substringBefore('+')
    return parts.take(3).joinToString(".") + ('a' + patch - 1)
}
