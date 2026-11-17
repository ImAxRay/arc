package com.metrolist.music.arc

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlin.math.pow

val ArcLearnUsageKey = booleanPreferencesKey("arcLearnUsage")

/** What Arc has counted about one album, playlist, mix, page or song. */
@Serializable
data class UsageEntry(
    val title: String? = null,
    val plays: Int = 0,
    val opens: Int = 0,
    val homeTaps: Int = 0,
    val skips: Int = 0,
    val score: Double = 0.0,
    val first: Long = 0,
    val last: Long = 0,
    /** Uses by time of day: night (0-6), morning (6-12), afternoon (12-18), evening (18-24). */
    val hours: List<Int> = listOf(0, 0, 0, 0),
    val weekend: Int = 0,
)

enum class UsageAction(val weight: Double) { PLAY(1.0), OPEN(0.5), HOME_TAP(1.0), SKIP(0.0) }

/** The counting itself, kept free of Android so it can be tested; [Usage] keeps it on disk. */
class UsageBook(val entries: MutableMap<String, UsageEntry> = mutableMapOf()) {
    fun record(key: String, title: String?, action: UsageAction, now: ZonedDateTime) {
        val at = now.toInstant().toEpochMilli()
        val old = entries[key] ?: UsageEntry(first = at)
        entries[key] =
            old.copy(
                title = title?.takeIf { it.isNotBlank() } ?: old.title,
                plays = old.plays + if (action == UsageAction.PLAY) 1 else 0,
                opens = old.opens + if (action == UsageAction.OPEN) 1 else 0,
                homeTaps = old.homeTaps + if (action == UsageAction.HOME_TAP) 1 else 0,
                skips = old.skips + if (action == UsageAction.SKIP) 1 else 0,
                score = scoreAt(old, at) + action.weight,
                last = at,
                hours = old.hours.toMutableList().also { it[now.hour / 6]++ },
                weekend = old.weekend + if (now.dayOfWeek >= DayOfWeek.SATURDAY) 1 else 0,
            )
        if (entries.size > MAX_ENTRIES) {
            entries.keys.filter { it != key }.minByOrNull { scoreAt(entries.getValue(it), at) }?.let(entries::remove)
        }
    }

    companion object {
        const val MAX_ENTRIES = 500
        const val HALF_LIFE_MS = 14L * 24 * 60 * 60 * 1000

        /** The score as it stands at [at]: it halves every two weeks without use. */
        fun scoreAt(entry: UsageEntry, at: Long): Double =
            if (entry.last == 0L) 0.0 else entry.score * 0.5.pow((at - entry.last).coerceAtLeast(0).toDouble() / HALF_LIFE_MS)
    }
}

/**
 * Counts, on the phone only, what the user plays from, opens and taps on Home, for the speed dial's suggestions.
 * Song plays themselves are already in the listen history; this adds where they came from.
 */
object Usage {
    private const val PREFS = "arc_usage"
    private const val DATA = "data"
    private const val SAVE_DELAY_MS = 10_000L
    private const val SKIP_BEFORE_MS = 30_000L

    private val json = Json { ignoreUnknownKeys = true }
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val save = Runnable { write() }
    private var prefs: SharedPreferences? = null
    private val book by lazy { UsageBook(load()) }
    private val seenEntries = LinkedHashSet<String>()

    /** Off when the user turned learning off or paused listen history. */
    @Volatile var enabled = true

    /** The album, artist or playlist page on screen, so a play started there is counted for it. */
    @Volatile var page: String? = null
        private set

    /** The last source a queue was started from, so a skip counts against it too. */
    private var source: String? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** One key per YouTube list: tuned radios by their tuning (the seed song changes each time), other mixes by id. */
    fun listKey(id: String): String =
        when {
            id.startsWith("RDAT") -> "radio:${id.take(7)}"
            id.startsWith("RD") -> "mix:$id"
            else -> "playlist:${id.removePrefix("VL")}"
        }

    /** Called for every screen shown; counts an open the first time each back stack entry appears. */
    fun onScreen(entryId: String?, route: String?, args: Bundle?) {
        val kind = route?.substringBefore('/')?.let(PAGE_KINDS::get)
        val arg = route?.let { ARG.find(it)?.groupValues?.get(1) }
        val id = arg?.let { args?.getString(it) }
        page =
            when {
                kind == null || id == null -> null
                kind == "playlist" -> listKey(id)
                else -> "$kind:$id"
            }
        val key = page ?: return
        if (entryId == null || !seenEntries.add(entryId)) return
        if (seenEntries.size > 50) seenEntries.remove(seenEntries.first())
        record(key, null, UsageAction.OPEN)
    }

    fun played(key: String?, title: String?) {
        source = key
        if (key != null) record(key, title, UsageAction.PLAY)
    }

    fun homeTap(key: String, title: String?) = record(key, title, UsageAction.HOME_TAP)

    /** A song left for the next one within its first 30 s. */
    fun skipped(songId: String, title: String?, playedMs: Long) {
        if (playedMs >= SKIP_BEFORE_MS) return
        record("song:$songId", title, UsageAction.SKIP)
        source?.let { record(it, null, UsageAction.SKIP) }
    }

    @Synchronized
    private fun record(key: String, title: String?, action: UsageAction) {
        if (!enabled || prefs == null) return
        book.record(key, title, action, ZonedDateTime.now())
        handler.removeCallbacks(save)
        handler.postDelayed(save, SAVE_DELAY_MS)
    }

    @Synchronized
    fun snapshot(): Map<String, UsageEntry> = book.entries.toMap()

    @Synchronized
    fun forget() {
        handler.removeCallbacks(save)
        book.entries.clear()
        source = null
        prefs?.edit()?.clear()?.apply()
    }

    private fun load(): MutableMap<String, UsageEntry> =
        prefs?.getString(DATA, null)?.let {
            runCatching { json.decodeFromString<Map<String, UsageEntry>>(it).toMutableMap() }.getOrNull()
        } ?: mutableMapOf()

    @Synchronized
    private fun write() {
        prefs?.edit()?.putString(DATA, json.encodeToString(book.entries.toMap()))?.apply()
    }

    private val ARG = Regex("\\{(\\w+)\\}")
    private val PAGE_KINDS =
        mapOf(
            "album" to "album",
            "artist" to "artist",
            "online_playlist" to "playlist",
            "local_playlist" to "local_playlist",
            "auto_playlist" to "auto_playlist",
            "top_playlist" to "top_playlist",
            "youtube_browse" to "browse",
            "online_podcast" to "podcast",
        )
}
