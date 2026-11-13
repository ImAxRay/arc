package com.metrolist.music.arc.snippet

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * How far the audio being handed to the output runs ahead of what the listener hears: the output buffer plus any
 * Bluetooth delay. Both ends come from the same audio sink, in its own time, so no offsets have to be known.
 */
object SnippetClock {
    @Volatile var leadUs = 0L
        internal set

    @Volatile var measuredAt = 0L
        internal set

    fun fresh() = System.currentTimeMillis() - measuredAt < 1_000
}

@UnstableApi
class SnippetClockSink(sink: AudioSink, private val isCurrent: () -> Boolean) : ForwardingAudioSink(sink) {
    private var frameSize = 0
    private var rate = 0
    private var lastBuffer: ByteBuffer? = null
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        val pcm = inputFormat.pcmEncoding
        frameSize = if (pcm != Format.NO_VALUE && pcm != C.ENCODING_INVALID) Util.getPcmFrameSize(pcm, inputFormat.channelCount) else 0
        rate = inputFormat.sampleRate
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (buffer !== lastBuffer) {
            lastBuffer = buffer
            val current = isCurrent()
            val heardUs = getCurrentPositionUs(false)
            val endUs = if (frameSize > 0 && rate > 0) presentationTimeUs + buffer.remaining() / frameSize * 1_000_000L / rate else -1L
            if (current && endUs >= 0 && heardUs != AudioSink.CURRENT_POSITION_NOT_SET && endUs >= heardUs) {
                SnippetClock.leadUs = endUs - heardUs
                SnippetClock.measuredAt = System.currentTimeMillis()
            }
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun flush() {
        lastBuffer = null
        super.flush()
    }
}
