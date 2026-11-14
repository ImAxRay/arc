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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Rect
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
import androidx.palette.graphics.Palette
import com.materialkolor.hct.Hct
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val BLEND_INTO_COVER = 0.18f
private const val GLOW_LENGTH = 0.8f
private const val CROSSFADE_MS = 220
private const val ART_SIZE = 96
private const val SPILL_COLUMNS = 5
private val EvenSpill = FloatArray(SPILL_COLUMNS) { 1f }

/**
 * A small copy of the cover, loaded once per song for the glow and for deciding where text needs
 * to turn dark. [statusBright] is the patch behind the time and battery, [titleBright] the corner
 * the curved title runs over.
 */
internal class CoverArt(val image: ImageBitmap, val statusBright: Boolean, val titleBright: Boolean, val edge: Int, val spill: FloatArray = EvenSpill)

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
        edge = edgeColor(bitmap, left, top + side * 4 / 5, left + side, top + side),
        spill = spill(bitmap, left, top, side),
    )
}

/**
 * How far the glow reaches below each part of the cover's bottom edge, around 1: further under bright parts,
 * with a little jitter fixed per cover, so the glow ends unevenly instead of along one straight line.
 */
private fun spill(bitmap: Bitmap, left: Int, top: Int, side: Int): FloatArray {
    val band = side / SPILL_COLUMNS
    val light = FloatArray(SPILL_COLUMNS) { i -> luminance(bitmap, left + i * band, left + (i + 1) * band, top + side * 3 / 4, top + side) }
    val brightest = light.max().coerceAtLeast(0.05f)
    val random = java.util.Random(bitmap.getPixel(left + side / 2, top + side - 1).toLong())
    return FloatArray(SPILL_COLUMNS) { i -> 0.7f + 0.35f * light[i] / brightest + 0.3f * random.nextFloat() }
}

/**
 * The colour the player's ground carries on from: the main colour of the cover's lower edge, or of
 * the whole cover when that edge is close to grey, so a dark band along the bottom doesn't wash it out.
 */
internal fun edgeColor(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
    fun dominant(region: Boolean): Int? {
        val builder = Palette.from(bitmap).maximumColorCount(8).clearFilters()
        if (region) builder.setRegion(x0, y0, x1, y1)
        return builder.generate().dominantSwatch?.rgb
    }
    val edge = dominant(region = true) ?: bitmap.getPixel((x0 + x1) / 2, (y0 + y1) / 2)
    if (Hct.fromInt(edge).chroma >= GREY_CHROMA) return edge
    return dominant(region = false)?.takeIf { Hct.fromInt(it).chroma >= GREY_CHROMA } ?: edge
}

private const val GREY_CHROMA = 8.0

/**
 * The ground under the player, one colour carried on from the cover's lower edge at about the
 * cover's own depth: [near] meets the cover, [deep] is a touch darker behind the buttons. [accent]
 * is a pale tint of the same hue for the play button and the line, with [onAccent] on it.
 */
internal class CoverGround(val near: Color, val deep: Color, val accent: Color, val onAccent: Color)

internal fun coverGround(edge: Int): CoverGround {
    val hct = Hct.fromInt(edge)
    val grey = hct.chroma < GREY_CHROMA
    val chroma = if (grey) hct.chroma else (hct.chroma * 1.15).coerceIn(24.0, 56.0)
    val depth = hct.tone.coerceIn(16.0, 32.0)
    fun tone(tone: Double, chroma: Double) = Color(Hct.from(hct.hue, chroma, tone).toInt())
    return CoverGround(
        near = tone(depth, chroma),
        deep = tone(depth - 6.0, chroma),
        accent = tone(88.0, if (grey) hct.chroma else chroma.coerceAtMost(36.0)),
        onAccent = tone(18.0, chroma),
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
    val spill = art?.spill ?: EvenSpill
    val maskPaint = remember { Paint().apply { blendMode = BlendMode.DstIn } }
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
                val w = size.width
                val from = coverBottom - w * BLEND_INTO_COVER
                val to = coverBottom + w * GLOW_LENGTH * 0.75f
                val at = { y: Float -> ((y - from) / (to - from)).coerceIn(0f, 1f) }
                // The mask is a layer of its own, so the soft lobes hanging below the even fade add up instead of cutting each other.
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, size), maskPaint)
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            at(coverBottom) to Color.Black,
                            at(coverBottom + w * 0.2f) to Color.Black.copy(alpha = 0.85f),
                            at(coverBottom + w * 0.38f) to Color.Black.copy(alpha = 0.45f),
                            at(coverBottom + w * 0.5f) to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Transparent,
                            startY = from,
                            endY = to,
                        ),
                    )
                    // below the cover only, or the lobes would lay the blurred copy over the cover itself
                    clipRect(top = coverBottom) { spill.forEachIndexed { i, reach ->
                        val centre = Offset(w * (i + 0.5f) / spill.size, coverBottom + w * 0.1f)
                        val radius = w * 0.62f * reach
                        drawCircle(
                            Brush.radialGradient(
                                0f to Color.Black.copy(alpha = 0.55f),
                                0.45f to Color.Black.copy(alpha = 0.3f),
                                0.75f to Color.Black.copy(alpha = 0.08f),
                                1f to Color.Transparent,
                                center = centre,
                                radius = radius,
                            ),
                            radius = radius,
                            center = centre,
                        )
                    } }
                    canvas.restore()
                }
                if (cutRadius > 0f) drawCircle(Color.Black, cutRadius, pivot, blendMode = BlendMode.DstOut)
            }.blur(20.dp),
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
