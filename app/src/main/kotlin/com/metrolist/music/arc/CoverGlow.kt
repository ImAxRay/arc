package com.metrolist.music.arc

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val BLEND_INTO_COVER = 0.18f
private const val GLOW_LENGTH = 0.85f
private const val CROSSFADE_MS = 220
private const val ART_SIZE = 96

/**
 * A small copy of the cover, loaded once per song for the glow and for deciding where text needs
 * to turn dark. [statusBright] is the patch behind the time and battery, [titleBright] the corner
 * the curved title runs over.
 */
internal class CoverArt(val image: ImageBitmap, val statusBright: Boolean, val titleBright: Boolean)

/** The last few songs' art, so skipping back and forth swaps the glow in the same frame as the cover. */
private object CoverArtCache {
    private val entries = object : LinkedHashMap<String, CoverArt>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoverArt>?) = size > 6
    }

    fun get(key: String): CoverArt? = synchronized(entries) { entries[key] }

    fun put(key: String, art: CoverArt) = synchronized(entries) { entries[key] = art }
}

private suspend fun cachedCoverArt(context: Context, url: String, titleOnLeft: Boolean): CoverArt? {
    val key = "$url|$titleOnLeft"
    CoverArtCache.get(key)?.let { return it }
    return withContext(Dispatchers.IO) { loadCoverArt(context, url, titleOnLeft) }?.also { CoverArtCache.put(key, it) }
}

/**
 * Loads [url] through the same cache as the cover itself and keeps it ready for [upcoming], the
 * next song, so the glow changes together with the cover. Until new art arrives the last one
 * stays, so nothing blinks out in between.
 */
@Composable
internal fun rememberCoverArt(url: String?, upcoming: String?, titleOnLeft: Boolean): CoverArt? {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf<CoverArt?>(null) }
    val ready = remember(url, titleOnLeft) { url?.let { CoverArtCache.get("$it|$titleOnLeft") } }
    LaunchedEffect(url, titleOnLeft) {
        // a song without a cover, or one that failed to load, must not keep the last song's glow
        loaded = url?.let { ready ?: cachedCoverArt(context, it, titleOnLeft) }
    }
    LaunchedEffect(upcoming, titleOnLeft) {
        if (upcoming != null) cachedCoverArt(context, upcoming, titleOnLeft)
    }
    return if (url == null) null else ready ?: loaded
}

private suspend fun loadCoverArt(context: Context, url: String, titleOnLeft: Boolean): CoverArt? {
    val request = ImageRequest.Builder(context).data(url).size(ART_SIZE, ART_SIZE).allowHardware(false).build()
    val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()?.let(Covers::withoutBars) ?: return null
    // The player crops wide video thumbnails to a square, so measure inside that square.
    val side = minOf(bitmap.width, bitmap.height)
    val left = (bitmap.width - side) / 2
    val top = (bitmap.height - side) / 2
    fun bright(x0: Float, x1: Float, y0: Float, y1: Float, threshold: Float) =
        luminance(bitmap, left + (x0 * side).toInt(), left + (x1 * side).toInt(), top + (y0 * side).toInt(), top + (y1 * side).toInt()) > threshold
    return CoverArt(
        image = bitmap.asImageBitmap(),
        statusBright = bright(0f, 0.45f, 0f, 0.1f, threshold = 0.5f),
        titleBright = if (titleOnLeft) bright(0f, 0.45f, 0.7f, 1f, threshold = 0.55f) else bright(0.55f, 1f, 0.7f, 1f, threshold = 0.55f),
    )
}

private fun luminance(bitmap: Bitmap, x0: Int, x1: Int, y0: Int, y1: Int): Float {
    var sum = 0.0
    var count = 0
    for (y in y0 until y1.coerceAtLeast(y0 + 1)) {
        for (x in x0 until x1.coerceAtLeast(x0 + 1)) {
            sum += ColorUtils.calculateLuminance(bitmap.getPixel(x, y))
            count++
        }
    }
    return (sum / count.coerceAtLeast(1)).toFloat()
}

/**
 * The cover's light spilling onto the player below it, like an ambient-light TV. The bottom of
 * the cover melts into a blurred copy of itself, which carries on below as a blurred mirror and
 * fades out, so there's no seam. Inside [cutRadius] around [pivot] it stays the plain background.
 * A new song's art crossfades in over the old one. Needs Android 12 for the blur.
 */
@Composable
internal fun CoverGlow(art: CoverArt?, coverBottom: Float, pivot: Offset, cutRadius: Float, alpha: Float, shift: () -> Float = { 0f }, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    var leaving by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(art?.image) {
        val image = art?.image
        if (image == null) {
            shown = null
            return@LaunchedEffect
        }
        val old = shown
        shown = image
        if (old == null || old === image) return@LaunchedEffect
        leaving = old
        fade.snapTo(0f)
        fade.animateTo(1f, tween(CROSSFADE_MS))
        leaving = null
    }
    // a moving cover lights the glow with its own frames
    val live = MotionCovers.liveFrame.value
    val current = live ?: shown
    if (current == null || coverBottom <= 0f || alpha <= 0.01f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                this.alpha = alpha
            }
            .drawWithContent {
                drawContent()
                val from = coverBottom - size.width * BLEND_INTO_COVER
                val to = coverBottom + size.width * GLOW_LENGTH
                val at = { y: Float -> ((y - from) / (to - from)).coerceIn(0f, 1f) }
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        at(coverBottom) to Color.Black,
                        at(coverBottom + size.width * 0.15f) to Color.Black.copy(alpha = 0.8f),
                        at(coverBottom + size.width * 0.45f) to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                        startY = from,
                        endY = to,
                    ),
                    blendMode = BlendMode.DstIn,
                )
                if (cutRadius > 0f) drawCircle(Color.Black, cutRadius, pivot, blendMode = BlendMode.DstOut)
            }.blur(32.dp),
    ) {
        val moved = shift()
        translate(left = moved) {
            if (live == null) leaving?.let { drawCoverAndMirror(it, coverBottom, 1f, moved != 0f) }
            drawCoverAndMirror(current, coverBottom, if (live == null && leaving != null) fade.value else 1f, moved != 0f)
        }
    }
}

/** The cover square ending at [bottom], and its mirror image below it. */
private fun DrawScope.drawCoverAndMirror(image: ImageBitmap, bottom: Float, alpha: Float, sides: Boolean) {
    val side = minOf(image.width, image.height)
    val src = IntOffset((image.width - side) / 2, (image.height - side) / 2)
    val width = size.width.roundToInt()
    val square = IntSize(width, width)
    fun column() {
        drawImage(image, src, IntSize(side, side), IntOffset(0, (bottom - width).roundToInt()), square, alpha = alpha)
        scale(1f, -1f, pivot = Offset(size.width / 2, bottom + size.width / 2)) {
            drawImage(image, src, IntSize(side, side), IntOffset(0, bottom.roundToInt()), square, alpha = alpha)
        }
    }
    column()
    // Mirrored copies either side, so a glow slid sideways never blurs in empty space at its edge.
    if (sides) {
        for (at in listOf(-size.width, size.width)) {
            translate(left = at) { scale(-1f, 1f, pivot = Offset(size.width / 2, bottom)) { column() } }
        }
    }
}
