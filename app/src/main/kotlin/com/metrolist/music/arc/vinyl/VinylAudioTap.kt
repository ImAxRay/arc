package com.metrolist.music.arc.vinyl

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/** The most recent decoded audio, so the record can be scratched backwards through the real song. */
internal object VinylAudioBuffer {
    private const val SECONDS = 30

    @Volatile var sampleRate = 0
        private set

    @Volatile var samples = ShortArray(0)
        private set

    @Volatile var writtenFrames = 0L
        private set

    // Frames count up forever. Audio from before a seek stays in the array but is out of bounds.
    @Volatile var firstValidFrame = 0L
        private set

    /** Where in the stream the audio after the last flush starts, as the audio sink counts it. */
    @Volatile var flushOffsetUs = 0L
        private set

    var channels = 2
        private set

    /** Gets every frame as it is written, while a snippet is being recorded. */
    @Volatile var capture: FrameCapture? = null

    fun reset(rate: Int, channelCount: Int, offsetUs: Long = 0L) {
        flushOffsetUs = offsetUs
        channels = channelCount
        if (rate != sampleRate) {
            samples = ShortArray(rate * SECONDS * 2)
            sampleRate = rate
        }
        firstValidFrame = writtenFrames
        capture?.cut()
    }

    fun write(input: ByteBuffer) {
        val store = samples
        val capacity = store.size / 2
        if (capacity == 0) return
        val frameBytes = channels * 2
        var index = input.position()
        var frame = writtenFrames
        while (index + frameBytes <= input.limit()) {
            val slot = (frame % capacity).toInt() * 2
            val left = input.getShort(index)
            store[slot] = left
            val right = if (channels == 2) input.getShort(index + 2) else left
            store[slot + 1] = right
            capture?.put(left, right)
            index += frameBytes
            frame++
        }
        writtenFrames = frame
    }
}

interface FrameCapture {
    fun put(left: Short, right: Short)

    /** The song jumped (a seek or the next song), so what follows no longer continues what came before. */
    fun cut()
}

/** Copies audio into [VinylAudioBuffer] and passes it on unchanged. */
internal class VinylTapProcessor : BaseAudioProcessor() {
    /** Crossfades run two players, and only the main one's audio belongs in the buffer. */
    @Volatile var isCurrentPlayer: () -> Boolean = { true }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount in 1..2) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        if (isActive && isCurrentPlayer()) VinylAudioBuffer.reset(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, streamMetadata.positionOffsetUs)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        if (isCurrentPlayer()) VinylAudioBuffer.write(inputBuffer)
        replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }
}
