package com.metrolist.music.arc.snippet

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.metrolist.music.arc.vinyl.FrameCapture
import com.metrolist.music.arc.vinyl.VinylAudioBuffer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import kotlin.concurrent.thread

/**
 * A snippet: a stretch of the playing song with the user's voice over it. The song comes from the audio tap
 * (what the player is sending out, after EQ and normalization) and the voice from the phone's own microphone,
 * never a Bluetooth headset's, which would drop the music to call quality.
 */
object Snippet {
    const val MAX_SECONDS = 60
    private const val TAG = "ArcSnippet"
    private const val OUTPUT_LEAD_MS = 300L
    // Bluetooth on some phones holds seconds of audio before it reaches the ears; the ring buffer keeps 30 s
    private const val MAX_LEAD_US = 20_000_000L
    private const val BLUETOOTH_GUESS_MS = 250L

    class Recording(val startedAt: Long, val locked: Boolean)

    class Take(
        val song: ShortArray,
        val rate: Int,
        val voice: ShortArray,
        val voiceRate: Int,
        val title: String,
        val artist: String,
        val cover: String?,
        val bluetooth: Boolean,
        /** What brings the voice up to the song's loudness: a phone mic a hand away records far quieter. */
        val voiceLevel: Float,
        val id: Long = System.currentTimeMillis(),
        /** Set once the take is kept in [SnippetStore]: how it was last mixed and where that mix was saved. */
        val stored: Stored? = null,
    ) {
        val frames get() = song.size / 2
    }

    class Stored(val mix: SnippetMix, val savedAt: String?)

    val recording = MutableStateFlow<Recording?>(null)

    /** How far the finger has slid toward locking the recording, 0..1. */
    var lockPull by mutableFloatStateOf(0f)
    val take = MutableStateFlow<Take?>(null)
    /** The list of kept snippets is open; it stays behind the editor while one of them is being edited. */
    val listOpen = MutableStateFlow(false)

    private var song: SongCapture? = null
    private var mic: AudioRecord? = null
    private var micThread: Thread? = null
    private var voice = ShortArray(0)
    @Volatile private var voiceLength = 0
    private var voiceRate = 0
    private var meta: Triple<String, String, String?> = Triple("", "", null)
    private var bluetooth = false
    private var player: Player? = null
    private val stopWhenPaused = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) stop("playback paused")
        }
    }

    private class SongCapture(val rate: Int, val lead: Int) : FrameCapture {
        val samples = ShortArray((rate * MAX_SECONDS + lead + rate) * 2)
        @Volatile var frames = 0
        @Volatile var broken = false

        override fun put(left: Short, right: Short) {
            if (broken || frames * 2 + 1 >= samples.size) return
            samples[frames * 2] = left
            samples[frames * 2 + 1] = right
            frames++
        }

        override fun cut() {
            broken = true
        }
    }

    /** Starts recording; false if there is no song audio to record over or the microphone won't open. */
    @SuppressLint("MissingPermission")
    fun start(context: Context, player: Player, title: String, artist: String, cover: String?): Boolean {
        if (recording.value != null) return true
        val rate = VinylAudioBuffer.sampleRate
        if (rate <= 0) return false
        val audio = context.getSystemService(AudioManager::class.java)
        val outputs = if (Build.VERSION.SDK_INT >= 33) {
            audio.getAudioDevicesForAttributes(
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build(),
            )
        } else {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        bluetooth = outputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }

        // The tap runs ahead of the ears by the output buffer and, on Bluetooth, the headphones' delay, so the song as
        // heard right now is back in it by that much. The player's position is what is heard, so the gap can be measured.
        val measured = SnippetClock.leadUs.takeIf { SnippetClock.fresh() && it in 0..MAX_LEAD_US }
        val leadUs = measured ?: ((OUTPUT_LEAD_MS + if (bluetooth) BLUETOOTH_GUESS_MS else 0L) * 1000)
        Timber.tag(TAG).i("lead ${leadUs / 1000} ms (${if (measured != null) "measured" else "guessed"}), bluetooth $bluetooth")
        val lead = (rate * leadUs / 1_000_000).toInt()
        val capture = SongCapture(rate, lead)
        val written = VinylAudioBuffer.writtenFrames
        val ring = VinylAudioBuffer.samples
        val ringFrames = ring.size / 2
        val from = maxOf(written - lead, VinylAudioBuffer.firstValidFrame)
        repeat((from - (written - lead)).toInt()) { capture.put(0, 0) }
        for (frame in from until written) {
            val slot = (frame % ringFrames).toInt() * 2
            capture.put(ring[slot], ring[slot + 1])
        }

        val source = if (Build.VERSION.SDK_INT >= 29) MediaRecorder.AudioSource.VOICE_PERFORMANCE else MediaRecorder.AudioSource.MIC
        val record = runCatching {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate / 5 * 2))
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.onFailure { Timber.tag(TAG).w(it, "microphone") }.getOrNull() ?: return false
        audio.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?.let(record::setPreferredDevice)

        voice = ShortArray(rate * (MAX_SECONDS + 1))
        voiceLength = 0
        voiceRate = rate
        meta = Triple(title, artist, cover)
        song = capture
        mic = record
        this.player = player
        VinylAudioBuffer.capture = capture
        record.startRecording()
        recording.value = Recording(System.currentTimeMillis(), locked = false)
        micThread = thread(name = "ArcSnippetMic") {
            val chunk = ShortArray(rate / 50)
            while (recording.value != null) {
                val read = record.read(chunk, 0, chunk.size)
                if (read <= 0) continue
                val room = voice.size - voiceLength
                if (room <= 0) {
                    Handler(Looper.getMainLooper()).post { stop("time limit") }
                    break
                }
                val n = minOf(read, room)
                System.arraycopy(chunk, 0, voice, voiceLength, n)
                voiceLength += n
            }
        }
        player.addListener(stopWhenPaused)
        return true
    }

    fun lock() {
        recording.value?.takeIf { !it.locked }?.let { recording.value = Recording(it.startedAt, locked = true) }
    }

    /** Stops recording and hands the take to the editor. */
    @Synchronized
    fun stop(why: String) {
        if (recording.value == null) return
        Timber.tag(TAG).i("stop: $why")
        recording.value = null
        VinylAudioBuffer.capture = null
        player?.removeListener(stopWhenPaused)
        player = null
        micThread?.takeIf { it !== Thread.currentThread() }?.join(500)
        micThread = null
        mic?.let { runCatching { it.stop() }; it.release() }
        mic = null
        val capture = song ?: return
        song = null
        // the last stretch of captured song hadn't reached the ears yet when the user stopped
        val heard = (capture.frames - capture.lead).coerceAtLeast(0)
        val voiceFrames = voiceLength
        if (heard < capture.rate / 2 || voiceFrames < voiceRate / 2) return
        val length = maxOf(heard, (voiceFrames.toLong() * capture.rate / voiceRate).toInt())
        val songOut = ShortArray(length * 2)
        System.arraycopy(capture.samples, 0, songOut, 0, heard * 2)
        val songRms = rms(songOut, 2)
        val voiceRms = rms(voice, 1, voiceFrames)
        val level = if (voiceRms > 0) (songRms / voiceRms).toFloat().coerceIn(1f, 64f) else 1f
        Timber.tag(TAG).i("take: song ${heard * 1000L / capture.rate} ms rms $songRms, voice ${voiceFrames * 1000L / voiceRate} ms rms $voiceRms, level $level, bluetooth $bluetooth")
        take.value = Take(
            song = songOut,
            rate = capture.rate,
            voice = voice.copyOf(voiceFrames),
            voiceRate = voiceRate,
            title = meta.first,
            artist = meta.second,
            cover = meta.third,
            bluetooth = bluetooth,
            voiceLevel = level,
        )
        voice = ShortArray(0)
    }

    /** Loudness of the louder half of the audio, so pauses between phrases don't count. */
    private fun rms(samples: ShortArray, channels: Int, frames: Int = samples.size / channels): Double {
        val block = 2048
        val blocks = ArrayList<Double>()
        var start = 0
        while (start + block <= frames) {
            var sum = 0.0
            for (f in start until start + block) {
                val v = samples[f * channels].toDouble()
                sum += v * v
            }
            blocks += sum / block
            start += block
        }
        if (blocks.isEmpty()) return 0.0
        blocks.sortDescending()
        val loud = blocks.take(maxOf(1, blocks.size / 2))
        return kotlin.math.sqrt(loud.average())
    }
}
