package com.metrolist.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import com.metrolist.music.arc.AutoSpeedDial
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.MusicService
import com.metrolist.music.recognition.RecognitionLaunchActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one Arc home-screen widget. Its layout follows its size: small is cover and play, a strip
 * adds title and skips, the player adds the counter, and a tall one adds the speed dial.
 */
@Singleton
class ArcWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    data class NowPlaying(
        val title: String? = null,
        val artist: String? = null,
        val artworkUri: String? = null,
        val isPlaying: Boolean = false,
        val durationMs: Long = 0,
        val positionMs: Long = 0,
        val stampedAt: Long = SystemClock.elapsedRealtime(),
    ) {
        fun positionNow(): Long {
            val moved = if (isPlaying) SystemClock.elapsedRealtime() - stampedAt else 0
            val position = positionMs + moved
            return if (durationMs > 0) position.coerceIn(0, durationMs) else position.coerceAtLeast(0)
        }

        fun permille() = if (durationMs > 0) (positionNow() * 1000 / durationMs).toInt() else 0
    }

    private enum class Size { Small, Strip, Player, Tall }

    private class Dial(val tile: AutoSpeedDial.Tile, val art: Bitmap?)

    private val widgets get() = AppWidgetManager.getInstance(context)
    private val mutex = Mutex()

    @Volatile private var now = NowPlaying()
    @Volatile private var sizes: Map<Int, Size> = emptyMap()
    private var cover: Bitmap? = null
    private var coverUri: String? = null
    private var dial: List<Dial>? = null

    suspend fun update(state: NowPlaying) {
        val trackChanged = state.title != now.title
        now = state
        render(refreshDial = trackChanged)
    }

    suspend fun render(refreshDial: Boolean = false) = mutex.withLock {
        val ids = widgets.getAppWidgetIds(ComponentName(context, MusicWidgetReceiver::class.java))
        sizes = ids.associateWith { sizeOf(it) }
        if (ids.isEmpty()) return@withLock
        val state = now
        if (state.artworkUri != coverUri) {
            cover = state.artworkUri?.let { loadArt(it, 320) }
            coverUri = state.artworkUri
        }
        if (Size.Tall in sizes.values && (dial == null || refreshDial)) dial = loadDial()
        ids.forEach { id -> widgets.updateAppWidget(id, views(id, sizes.getValue(id), state)) }
    }

    /** Moves the line and resyncs the counter without resending the covers. */
    fun updateProgress(durationMs: Long, positionMs: Long) {
        val state = now.copy(durationMs = durationMs, positionMs = positionMs, stampedAt = SystemClock.elapsedRealtime())
        now = state
        val lined = sizes.filterValues { it != Size.Small }.keys
        if (lined.isEmpty()) return
        val line = RemoteViews(context.packageName, R.layout.widget_arc_player).apply { setLine(state) }
        widgets.partiallyUpdateAppWidget(lined.toIntArray(), line)
        val counted = sizes.filterValues { it == Size.Player || it == Size.Tall }.keys
        if (counted.isEmpty()) return
        val counter = RemoteViews(context.packageName, R.layout.widget_arc_player).apply { setCounter(state) }
        widgets.partiallyUpdateAppWidget(counted.toIntArray(), counter)
    }

    // Portrait size: the launcher reports width as the minimum and height as the maximum.
    private fun sizeOf(id: Int): Size {
        val options = widgets.getAppWidgetOptions(id)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return when {
            width == 0 -> Size.Player
            width < 200 -> Size.Small
            height < 135 -> Size.Strip
            height < 250 -> Size.Player
            else -> Size.Tall
        }
    }

    private fun views(id: Int, size: Size, state: NowPlaying): RemoteViews {
        val layout = when (size) {
            Size.Small -> R.layout.widget_arc_small
            Size.Strip -> R.layout.widget_arc_strip
            else -> R.layout.widget_arc_player
        }
        return RemoteViews(context.packageName, layout).apply {
            setOnClickPendingIntent(R.id.arc_root, openApp())
            cover?.let { setImageViewBitmap(R.id.arc_cover, it) } ?: setImageViewResource(R.id.arc_cover, R.drawable.arc_widget_blank)
            setImageViewResource(R.id.arc_play_icon, if (state.isPlaying) R.drawable.arc_widget_pause else R.drawable.arc_widget_play)
            setOnClickPendingIntent(R.id.arc_play, playback(MusicWidgetReceiver.ACTION_PLAY_PAUSE, 1))
            if (size == Size.Small) return@apply

            setTextViewText(R.id.arc_title, state.title ?: context.getString(R.string.no_song_playing))
            setTextViewText(R.id.arc_artist, state.artist ?: context.getString(R.string.tap_to_open))
            setOnClickPendingIntent(R.id.arc_prev, playback(MusicWidgetReceiver.ACTION_PREVIOUS, 2))
            setOnClickPendingIntent(R.id.arc_next, playback(MusicWidgetReceiver.ACTION_NEXT, 3))
            setLine(state)
            if (size == Size.Strip) {
                val roomy = widgets.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) >= 300
                setViewVisibility(R.id.arc_prev, if (roomy) View.VISIBLE else View.GONE)
                return@apply
            }

            val label = when {
                state.title == null -> R.string.arc_widget_name
                state.isPlaying -> R.string.arc_widget_playing
                else -> R.string.arc_widget_paused
            }
            setTextViewText(R.id.arc_label, context.getString(label))
            setCounter(state)
            if (size == Size.Tall) setDial()
        }
    }

    private fun RemoteViews.setLine(state: NowPlaying) {
        setProgressBar(R.id.arc_line, 1000, state.permille(), false)
    }

    // The launcher ticks the counter itself, so it costs Arc nothing while it runs.
    private fun RemoteViews.setCounter(state: NowPlaying) {
        setChronometer(R.id.arc_time, SystemClock.elapsedRealtime() - state.positionNow(), null, state.isPlaying)
    }

    private fun RemoteViews.setDial() {
        setViewVisibility(R.id.arc_mic, View.VISIBLE)
        setOnClickPendingIntent(
            R.id.arc_mic,
            PendingIntent.getActivity(context, 20, Intent(context, RecognitionLaunchActivity::class.java), FLAGS),
        )
        val tiles = dial.orEmpty()
        setViewVisibility(R.id.arc_dial, if (tiles.isEmpty()) View.GONE else View.VISIBLE)
        DIAL_SLOTS.forEachIndexed { index, (slot, art, title) ->
            val item = tiles.getOrNull(index)
            setViewVisibility(slot, if (item == null) View.INVISIBLE else View.VISIBLE)
            if (item == null) return@forEachIndexed
            item.art?.let { setImageViewBitmap(art, it) } ?: setImageViewResource(art, R.drawable.arc_widget_blank)
            setTextViewText(title, item.tile.title)
            setContentDescription(slot, item.tile.title)
            setOnClickPendingIntent(
                slot,
                playback(MusicWidgetReceiver.ACTION_PLAY_TILE, 10 + index) { putExtra(MusicWidgetReceiver.EXTRA_TILE, item.tile.mediaId) },
            )
        }
    }

    private suspend fun loadDial(): List<Dial> =
        runCatching { AutoSpeedDial.tiles(database).take(DIAL_SLOTS.size) }
            .getOrDefault(emptyList())
            .map { Dial(it, it.art?.let { url -> loadArt(url, 160) }) }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), FLAGS)

    // Widget taps may start a foreground service from the background, so play goes straight to it.
    private fun playback(action: String, code: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            code,
            Intent(context, MusicService::class.java).setAction(action).apply(extras),
            FLAGS,
        )

    private suspend fun loadArt(url: String, size: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).size(size, size).allowHardware(false).build()
                SingletonImageLoader.get(context).execute(request).image?.toBitmap()?.let(::rounded)
            }.getOrNull()
        }

    private fun rounded(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-(source.width - side) / 2f, -(source.height - side) / 2f) })
            }
        }
        val radius = side * 0.08f
        Canvas(output).drawRoundRect(0f, 0f, side.toFloat(), side.toFloat(), radius, radius, paint)
        return output
    }

    private companion object {
        const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val DIAL_SLOTS = listOf(
            Triple(R.id.arc_dial_0, R.id.arc_dial_0_art, R.id.arc_dial_0_title),
            Triple(R.id.arc_dial_1, R.id.arc_dial_1_art, R.id.arc_dial_1_title),
            Triple(R.id.arc_dial_2, R.id.arc_dial_2_art, R.id.arc_dial_2_title),
            Triple(R.id.arc_dial_3, R.id.arc_dial_3_art, R.id.arc_dial_3_title),
        )
    }
}
