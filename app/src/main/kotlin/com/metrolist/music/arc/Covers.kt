package com.metrolist.music.arc

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/** Cover pictures as YouTube serves them, made to fill a square cover. */
object Covers {
    private val videoThumb = Regex("https://i\\.ytimg\\.com/vi(?:_webp)?/([A-Za-z0-9_-]{11})/(?:hq|sd|mq)?default\\.(?:jpg|webp).*")

    /** The large frame of a video's thumbnail instead of the small letterboxed one, or null if [url] isn't one. */
    fun sharper(url: String?): String? =
        url?.let { videoThumb.matchEntire(it) }?.let { "https://i.ytimg.com/vi/${it.groupValues[1]}/maxresdefault.jpg" }

    /**
     * [bitmap] without the black bars baked into its edges (a 16:9 video inside a 4:3 thumbnail), which would show as
     * stripes on a square cover and darken its glow. Returns [bitmap] itself when there are none.
     */
    fun withoutBars(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return bitmap
        fun dark(x: Int, y: Int): Boolean {
            val c = bitmap.getPixel(x, y)
            return ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) < 3 * 24
        }
        fun rowDark(y: Int) = (0 until 24).all { dark(w * (it * 2 + 1) / 48, y) }
        fun columnDark(x: Int) = (0 until 24).all { dark(x, h * (it * 2 + 1) / 48) }
        var top = 0
        while (top < h / 4 && rowDark(top)) top++
        var bottom = h - 1
        while (bottom > h * 3 / 4 && rowDark(bottom)) bottom--
        var left = 0
        while (left < w / 4 && columnDark(left)) left++
        var right = w - 1
        while (right > w * 3 / 4 && columnDark(right)) right--
        val cutRows = top + (h - 1 - bottom)
        val cutColumns = left + (w - 1 - right)
        if (cutRows < h / 25 && cutColumns < w / 25) return bitmap
        // a dark picture is not a letterbox: only cut what is dark all the way across
        val y0 = if (cutRows >= h / 25) top else 0
        val y1 = if (cutRows >= h / 25) bottom else h - 1
        val x0 = if (cutColumns >= w / 25) left else 0
        val x1 = if (cutColumns >= w / 25) right else w - 1
        return Bitmap.createBitmap(bitmap, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }
}

/** [Covers.withoutBars] as a Coil step, for covers loaded straight into an image. */
object LetterboxCrop : Transformation() {
    override val cacheKey = "arcLetterboxCrop"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = Covers.withoutBars(input)
}
