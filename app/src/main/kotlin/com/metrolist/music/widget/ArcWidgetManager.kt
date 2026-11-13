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
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arc's home-screen widgets. The main one follows its size: small is cover and play, a strip
 * adds title and skips, and the player adds the counter. The vinyl one is a record that spins
 * while music plays, and the speed dial one is a grid of tiles that grows with the widget.
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
        val shuffle: Boolean = false,
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

    private enum class Size { Small, Strip, Player, Vinyl, Dial }

    private class Dial(val tile: AutoSpeedDial.Tile, val art: Bitmap?)

    private val widgets get() = AppWidgetManager.getInstance(context)
    private val mutex = Mutex()

    @Volatile private var now = NowPlaying()
    @Volatile private var sizes: Map<Int, Size> = emptyMap()
    private var cover: Bitmap? = null
    private var label: Bitmap? = null
    private var coverUri: String? = null
    private var dial: List<Dial>? = null

    suspend fun update(state: NowPlaying) {
        val trackChanged = state.title != now.title
        now = state
        render(full = trackChanged)
    }

    /** Redraws every widget; play and pause alone skip the speed dial, which doesn't show them. */
    suspend fun render(full: Boolean = true) = mutex.withLock {
        fun ids(receiver: Class<*>) = widgets.getAppWidgetIds(ComponentName(context, receiver))
        sizes = ids(MusicWidgetReceiver::class.java).associateWith { sizeOf(it) } +
            ids(VinylWidgetReceiver::class.java).associateWith { Size.Vinyl } +
            ids(DialWidgetReceiver::class.java).associateWith { Size.Dial }
        if (sizes.isEmpty()) return@withLock
        val state = now
        if (state.artworkUri != coverUri) {
            val art = state.artworkUri?.let { loadArt(it, 320) }
            cover = art?.let { shaped(it, round = false) }
            label = art?.let { shaped(it, round = true) }
            coverUri = state.artworkUri
        }
        val dialShown = Size.Dial in sizes.values
        if (dialShown && (dial == null || full)) dial = loadDial()
        sizes.forEach { (id, size) ->
            if (size != Size.Dial || full) widgets.updateAppWidget(id, views(id, size, state))
        }
    }

    /** Moves the line and resyncs the counter without resending the covers. */
    fun updateProgress(durationMs: Long, positionMs: Long) {
        val state = now.copy(durationMs = durationMs, positionMs = positionMs, stampedAt = SystemClock.elapsedRealtime())
        now = state
        val lined = sizes.filterValues { it == Size.Strip || it == Size.Player }.keys
        if (lined.isEmpty()) return
        val line = RemoteViews(context.packageName, R.layout.widget_arc_player).apply { setLine(state) }
        widgets.partiallyUpdateAppWidget(lined.toIntArray(), line)
        val counted = sizes.filterValues { it == Size.Player || it == Size.Vinyl }.keys
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
            else -> Size.Player
        }
    }

    private fun views(id: Int, size: Size, state: NowPlaying): RemoteViews {
        val layout = when (size) {
            Size.Small -> R.layout.widget_arc_small
            Size.Strip -> R.layout.widget_arc_strip
            Size.Vinyl -> R.layout.widget_arc_vinyl
            Size.Dial -> R.layout.widget_arc_dial
            else -> R.layout.widget_arc_player
        }
        return RemoteViews(context.packageName, layout).apply {
            setOnClickPendingIntent(R.id.arc_root, openApp())
            if (size == Size.Dial) return@apply setGrid(id)
            val art = if (size == Size.Vinyl) label else cover
            when {
                art != null -> setImageViewBitmap(R.id.arc_cover, art)
                size == Size.Vinyl -> setImageViewResource(R.id.arc_cover, 0)
                else -> setImageViewResource(R.id.arc_cover, R.drawable.arc_widget_blank)
            }
            setImageViewResource(R.id.arc_play_icon, if (state.isPlaying) R.drawable.arc_widget_pause else R.drawable.arc_widget_play)
            setOnClickPendingIntent(R.id.arc_play, playback(MusicWidgetReceiver.ACTION_PLAY_PAUSE, 1))
            if (size == Size.Small) return@apply

            setTextViewText(R.id.arc_title, state.title ?: context.getString(R.string.no_song_playing))
            setTextViewText(R.id.arc_artist, state.artist ?: context.getString(R.string.tap_to_open))
            setOnClickPendingIntent(R.id.arc_prev, playback(MusicWidgetReceiver.ACTION_PREVIOUS, 2))
            setOnClickPendingIntent(R.id.arc_next, playback(MusicWidgetReceiver.ACTION_NEXT, 3))
            if (size != Size.Vinyl) setLine(state)
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
            if (size == Size.Vinyl) setVinyl(id, state)
        }
    }

    private fun RemoteViews.setVinyl(id: Int, state: NowPlaying) {
        setOnClickPendingIntent(R.id.arc_shuffle, playback(MusicWidgetReceiver.ACTION_SHUFFLE, 4))
        setInt(R.id.arc_shuffle, "setImageAlpha", if (state.shuffle) 255 else 90)
        val spinning = if (state.isPlaying) View.VISIBLE else View.GONE
        val still = if (state.isPlaying) View.GONE else View.VISIBLE
        setViewVisibility(R.id.arc_vinyl_spin, spinning)
        setViewVisibility(R.id.arc_vinyl_label_spin, spinning)
        setViewVisibility(R.id.arc_vinyl_still, still)
        setViewVisibility(R.id.arc_vinyl_label_still, still)

        val options = widgets.getAppWidgetOptions(id)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (width == 0 || height == 0) return
        setViewVisibility(R.id.arc_time, if (height >= 160) View.VISIBLE else View.GONE)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        // The record fills the height and hangs 28% off the right edge, leaving the buttons room on the left.
        val disc = minOf(height - 12f, (width - 212f) / 0.72f).coerceIn(96f, 300f)
        val dip = TypedValue.COMPLEX_UNIT_DIP
        setViewLayoutWidth(R.id.arc_vinyl, disc, dip)
        setViewLayoutHeight(R.id.arc_vinyl, disc, dip)
        setViewLayoutMargin(R.id.arc_vinyl, RemoteViews.MARGIN_END, -disc * 0.28f, dip)
        listOf(RemoteViews.MARGIN_LEFT, RemoteViews.MARGIN_TOP, RemoteViews.MARGIN_RIGHT, RemoteViews.MARGIN_BOTTOM).forEach {
            setViewLayoutMargin(R.id.arc_cover, it, disc * 0.31f, dip)
        }
        setViewLayoutMargin(R.id.arc_vinyl_info, RemoteViews.MARGIN_END, disc * 0.72f + 8f, dip)
    }

    private fun RemoteViews.setLine(state: NowPlaying) {
        setProgressBar(R.id.arc_line, 1000, state.permille(), false)
    }

    // The launcher ticks the counter itself, so it costs Arc nothing while it runs.
    private fun RemoteViews.setCounter(state: NowPlaying) {
        setChronometer(R.id.arc_time, SystemClock.elapsedRealtime() - state.positionNow(), null, state.isPlaying)
    }

    // Fills the visible slots row by row: one tile at 1x1, up to three across and four down.
    private fun RemoteViews.setGrid(id: Int) {
        val options = widgets.getAppWidgetOptions(id)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val columns = when {
            width == 0 || width >= 250 -> 3
            width >= 150 -> 2
            else -> 1
        }
        val rows = if (height == 0) 2 else (height / 90).coerceIn(1, 4)
        val titled = height == 0 || height / rows >= 100
        // Full-width widgets keep clear of the edge strips launchers such as Niagara use for their own gestures.
        val side = dp(if (width >= 300) 40 else 6)
        setViewPadding(R.id.arc_root, side, dp(6), side, dp(6))
        TILE_ROWS.forEachIndexed { row, rowId -> setViewVisibility(rowId, if (row < rows) View.VISIBLE else View.GONE) }
        val tiles = dial.orEmpty()
        TILES.forEachIndexed { index, (slot, art, title) ->
            val row = index / 3
            val column = index % 3
            if (row >= rows || column >= columns) {
                setViewVisibility(slot, View.GONE)
                return@forEachIndexed
            }
            val item = tiles.getOrNull(row * columns + column)
            setViewVisibility(slot, if (item == null) View.INVISIBLE else View.VISIBLE)
            if (item == null) return@forEachIndexed
            item.art?.let { setImageViewBitmap(art, it) } ?: setImageViewResource(art, R.drawable.arc_widget_blank)
            setViewVisibility(title, if (titled) View.VISIBLE else View.GONE)
            setTextViewText(title, item.tile.title)
            setContentDescription(slot, item.tile.title)
            setOnClickPendingIntent(
                slot,
                playback(MusicWidgetReceiver.ACTION_PLAY_TILE, 30 + index) { putExtra(MusicWidgetReceiver.EXTRA_TILE, item.tile.mediaId) },
            )
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private suspend fun loadDial(): List<Dial> =
        runCatching { AutoSpeedDial.tiles(database).take(TILES.size) }
            .getOrDefault(emptyList())
            .map { Dial(it, it.art?.let { url -> loadArt(url, 128)?.let { art -> shaped(art, round = false) } }) }

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
                SingletonImageLoader.get(context).execute(request).image?.toBitmap()
            }.getOrNull()
        }

    /** A square crop of [source] with softened corners, or a circle for the record's label. */
    private fun shaped(source: Bitmap, round: Boolean): Bitmap {
        val side = minOf(source.width, source.height)
        val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-(source.width - side) / 2f, -(source.height - side) / 2f) })
            }
        }
        val radius = side * if (round) 0.5f else 0.08f
        Canvas(output).drawRoundRect(0f, 0f, side.toFloat(), side.toFloat(), radius, radius, paint)
        return output
    }

    private companion object {
        const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val TILE_ROWS = listOf(R.id.arc_tile_row_0, R.id.arc_tile_row_1, R.id.arc_tile_row_2, R.id.arc_tile_row_3)

        val TILES = listOf(
            Triple(R.id.arc_tile_0, R.id.arc_tile_0_art, R.id.arc_tile_0_title),
            Triple(R.id.arc_tile_1, R.id.arc_tile_1_art, R.id.arc_tile_1_title),
            Triple(R.id.arc_tile_2, R.id.arc_tile_2_art, R.id.arc_tile_2_title),
            Triple(R.id.arc_tile_3, R.id.arc_tile_3_art, R.id.arc_tile_3_title),
            Triple(R.id.arc_tile_4, R.id.arc_tile_4_art, R.id.arc_tile_4_title),
            Triple(R.id.arc_tile_5, R.id.arc_tile_5_art, R.id.arc_tile_5_title),
            Triple(R.id.arc_tile_6, R.id.arc_tile_6_art, R.id.arc_tile_6_title),
            Triple(R.id.arc_tile_7, R.id.arc_tile_7_art, R.id.arc_tile_7_title),
            Triple(R.id.arc_tile_8, R.id.arc_tile_8_art, R.id.arc_tile_8_title),
            Triple(R.id.arc_tile_9, R.id.arc_tile_9_art, R.id.arc_tile_9_title),
            Triple(R.id.arc_tile_10, R.id.arc_tile_10_art, R.id.arc_tile_10_title),
            Triple(R.id.arc_tile_11, R.id.arc_tile_11_art, R.id.arc_tile_11_title),
        )
    }
}
