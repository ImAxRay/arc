package com.metrolist.music.arc

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/**
 * The phone's media volume as 0..1, which is what the knob and the menu slider move: one volume, the same as the
 * phone's buttons, rather than a second app-only level on top of it.
 */
object DeviceVolume {
    private fun audio(context: Context) = context.getSystemService(AudioManager::class.java)

    fun read(context: Context): Float {
        val audio = audio(context)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun write(context: Context, volume: Float) {
        val audio = audio(context)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume.coerceIn(0f, 1f) * max).roundToInt(), 0)
    }
}
