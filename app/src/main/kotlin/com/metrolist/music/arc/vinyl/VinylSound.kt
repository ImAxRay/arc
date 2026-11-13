package com.metrolist.music.arc.vinyl

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Plays the song from [VinylAudioBuffer] at whatever speed and direction the record is moved by hand. */
internal class VinylScrubSound {
    @Volatile private var active = false
    @Volatile private var targetFrame = 0.0
    @Volatile private var jumpTo: Double? = null
    @Volatile private var coasting = false
    @Volatile private var coastLive = false
    private var coastWritten = 0L
    private var coastOldest = 0L
    private var anchorMs = 0L
    private var anchorFrame = 0L
    private var thread: Thread? = null

    fun start(positionMs: Long) {
        val rate = VinylAudioBuffer.sampleRate
        if (rate == 0) return
        anchorMs = positionMs
        // The buffer is ahead of what is audible by roughly the output track's length.
        anchorFrame = VinylAudioBuffer.writtenFrames - rate * OUTPUT_LATENCY_MS / 1000
        targetFrame = anchorFrame.toDouble()
        coasting = false
        coastLive = false
        jumpTo = targetFrame
        active = true
        if (thread?.isAlive == true) return
        thread = Thread({ run(rate) }, "VinylScrubSound").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun moveTo(positionMs: Long) {
        targetFrame = anchorFrame + (positionMs - anchorMs) * VinylAudioBuffer.sampleRate / 1000.0
    }

    /**
     * Plays on at normal speed from the current position. The player is about to seek, which starts
     * a new stretch of audio in the buffer, so reading stays within what has been written so far.
     */
    fun coast() {
        coastWritten = VinylAudioBuffer.writtenFrames
        coastOldest = VinylAudioBuffer.firstValidFrame
        coastLive = false
        coasting = true
    }

    /**
     * Carries on into the audio the player has started writing after its seek, from [frame]. Without
     * this the coast runs out of the old audio before the player can be heard, which left a gap.
     */
    fun continueFrom(frame: Long) {
        targetFrame = frame.toDouble()
        jumpTo = targetFrame
        coastLive = true
    }

    fun stop() {
        active = false
    }

    private fun run(rate: Int) {
        val track = runCatching { createTrack(rate) }.getOrNull() ?: return
        val output = ShortArray(FRAMES_PER_WRITE * 2)
        val random = Random(1)
        // Touch events arrive in steps about 10 ms apart. Smoothing them twice keeps the speed, and so
        // the pitch, steady between events while the position still lands exactly where the record is.
        val chase = 1.0 / (CHASE_SECONDS * rate)
        var head = targetFrame
        var smoothed = head
        var level = 0f
        try {
            track.play()
            while (active || level > 0.002f) {
                jumpTo?.let {
                    head = it
                    smoothed = it
                    jumpTo = null
                }
                val store = VinylAudioBuffer.samples
                val capacity = store.size / 2
                val coast = coasting
                val frozen = coast && !coastLive
                val written = if (frozen) coastWritten else VinylAudioBuffer.writtenFrames
                val firstValid = if (frozen) coastOldest else VinylAudioBuffer.firstValidFrame
                val oldest = max(firstValid, written - capacity + FRAMES_PER_WRITE)
                for (index in 0 until FRAMES_PER_WRITE) {
                    if (coast) targetFrame += 1.0
                    smoothed += (targetFrame - smoothed) * chase
                    val speed = ((smoothed - head) * chase).coerceIn(-MAX_SPEED, MAX_SPEED)
                    head += speed
                    val targetLevel = if (active) min(1f, abs(speed).toFloat() * 10f) else 0f
                    level += (targetLevel - level) * 0.004f

                    var left = 0f
                    var right = 0f
                    val whole = floor(head).toLong()
                    if (capacity > 0 && whole >= oldest && whole < written - 1) {
                        val fraction = (head - whole).toFloat()
                        val first = (whole % capacity).toInt() * 2
                        val second = ((whole + 1) % capacity).toInt() * 2
                        left = store[first] + (store[second] - store[first]) * fraction
                        right = store[first + 1] + (store[second + 1] - store[first + 1]) * fraction
                    }
                    val crackle = if (random.nextFloat() < 0.0004f) (random.nextFloat() - 0.5f) * 5_000f else 0f
                    output[index * 2] = ((left + crackle) * level).toInt().coerceIn(-32768, 32767).toShort()
                    output[index * 2 + 1] = ((right + crackle) * level).toInt().coerceIn(-32768, 32767).toShort()
                }
                track.write(output, 0, output.size)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun createTrack(rate: Int): AudioTrack {
        val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            ).setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            ).setBufferSizeInBytes(max(minimum, FRAMES_PER_WRITE * 8))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private companion object {
        const val FRAMES_PER_WRITE = 256
        const val OUTPUT_LATENCY_MS = 300L
        const val CHASE_SECONDS = 0.012
        const val MAX_SPEED = 6.0
    }
}
