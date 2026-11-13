package com.metrolist.music.arc.vinyl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player

/**
 * While the record is moved by hand, the player keeps decoding silently and a little ahead, so
 * pushing it forward has real audio to play. It never gets far enough to reach the next song.
 */
internal class ScratchSession(val player: Player) {
    var handingOff = false

    private val volume = player.volume
    private val parameters = player.playbackParameters

    init {
        player.volume = 0f
        player.playbackParameters = PlaybackParameters(2f, 1f)
        player.play()
    }

    /** [endMarginMs] keeps the silent player away from the song's end, and from where a crossfade would begin. */
    fun keepAhead(scrubMs: Long, durationMs: Long, endMarginMs: Long) {
        val position = player.currentPosition
        val ahead = position - scrubMs
        val nearEnd = position > durationMs - endMarginMs
        when {
            ahead > 4_000L || nearEnd -> player.pause()
            ahead < 2_000L -> player.play()
        }
    }

    fun restoreSpeed() {
        player.playbackParameters = parameters
    }

    fun restoreVolume() {
        player.volume = volume
    }
}
