package com.metrolist.music.arc.vinyl

/**
 * The neighbouring queue songs from one album, laid out as bands on a single record so the needle
 * travels across the whole album. Progress runs from 0 at the first song to 1 at the end of the last.
 */
internal class AlbumSide(
    val currentIndex: Int,
    private val firstIndex: Int,
    private val durationsMs: List<Long>,
) {
    private val totalMs = durationsMs.sum()

    /** Where each song after the first begins, as progress across the album. */
    val bandEdges: List<Float> =
        durationsMs.runningFold(0L, Long::plus).drop(1).dropLast(1).map { it.toFloat() / totalMs }

    fun contains(index: Int): Boolean = index in firstIndex until firstIndex + durationsMs.size

    fun durationOf(index: Int): Long = durationsMs[index - firstIndex]

    fun progress(index: Int, positionMs: Long): Float {
        val before = durationsMs.take(index - firstIndex).sum()
        return ((before + positionMs.coerceIn(0L, durationOf(index))).toFloat() / totalMs).coerceIn(0f, 1f)
    }

    /** The song and position under a needle at [progress]. */
    fun locate(progress: Float): Pair<Int, Long> {
        var remaining = (progress.coerceIn(0f, 1f) * totalMs).toLong()
        durationsMs.forEachIndexed { offset, duration ->
            if (remaining < duration) return firstIndex + offset to remaining
            remaining -= duration
        }
        return firstIndex + durationsMs.lastIndex to durationsMs.last()
    }
}

/** Finds the album around [current], or null when the song isn't next to others from its album or a length is unknown. */
internal fun albumSideFor(albumIds: List<String?>, durationsMs: List<Long>, current: Int): AlbumSide? {
    val album = albumIds.getOrNull(current) ?: return null
    var first = current
    while (first > 0 && albumIds[first - 1] == album) first--
    var last = current
    while (last < albumIds.lastIndex && albumIds[last + 1] == album) last++
    if (first == last) return null
    val durations = durationsMs.subList(first, last + 1)
    if (durations.any { it <= 0L }) return null
    return AlbumSide(current, first, durations)
}
