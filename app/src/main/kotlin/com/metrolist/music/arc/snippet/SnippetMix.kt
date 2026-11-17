package com.metrolist.music.arc.snippet

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/** How the two parts of a [Snippet.Take] sound together. [syncMs] moves the voice earlier, to undo the delay of what the user heard. */
data class SnippetMix(val from: Int, val to: Int, val songGain: Float, val voiceGain: Float, val syncMs: Int)

/** Mixes frames [start, end) of the take into [out] as interleaved stereo. */
fun Snippet.Take.mixInto(out: ShortArray, start: Int, end: Int, mix: SnippetMix) {
    val step = voiceRate.toDouble() / rate
    val shift = mix.syncMs * voiceRate / 1000.0
    var o = 0
    for (frame in start until end) {
        val at = frame * step + shift
        val i = at.toInt()
        val v = if (i >= 0 && i + 1 < voice.size) {
            val t = at - i
            voice[i] * (1 - t) + voice[i + 1] * t
        } else {
            0.0
        }
        val voicePart = v * mix.voiceGain * voiceLevel
        out[o] = clip(song[frame * 2] * mix.songGain + voicePart)
        out[o + 1] = clip(song[frame * 2 + 1] * mix.songGain + voicePart)
        o += 2
    }
}

private const val KNEE = 24_000.0

/** Rounds peaks off softly above [KNEE] instead of cutting them flat, since the levelled voice can push past full scale. */
private fun clip(value: Double): Short {
    val size = kotlin.math.abs(value)
    val out = if (size <= KNEE) size else KNEE + (32_767 - KNEE) * kotlin.math.tanh((size - KNEE) / (32_767 - KNEE))
    return (if (value < 0) -out else out).roundToInt().toShort()
}

/** Loudest point of each of [buckets] stretches, 0..1, for drawing. */
fun peaks(samples: ShortArray, channels: Int, buckets: Int): FloatArray {
    val frames = samples.size / channels
    if (frames == 0) return FloatArray(buckets)
    return FloatArray(buckets) { b ->
        val start = (b.toLong() * frames / buckets).toInt()
        val end = ((b + 1L) * frames / buckets).toInt().coerceAtLeast(start + 1)
        var max = 0
        var f = start
        val stride = maxOf(1, (end - start) / 256)
        while (f < end) {
            val s = kotlin.math.abs(samples[f * channels].toInt())
            if (s > max) max = s
            f += stride
        }
        max / 32768f
    }
}

/** Encodes the mixed snippet as AAC in an .m4a file. */
suspend fun Snippet.Take.export(context: Context, mix: SnippetMix, folder: String = "snippets"): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, folder).apply {
        listFiles()?.forEach(File::delete)
        mkdirs()
    }
    val name = "${title} snippet".replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Snippet" }
    val file = File(dir, "$name.m4a")
    encodeAac(file, rate, 2, mix.to - mix.from, 256_000) { out, start, end -> mixInto(out, mix.from + start, mix.from + end, mix) }
    file
}

/** Writes [frames] frames of [channels]-channel audio as AAC, asking [fill] for them a chunk at a time. */
fun encodeAac(file: File, rate: Int, channels: Int, frames: Int, bitRate: Int, fill: (out: ShortArray, start: Int, end: Int) -> Unit) {
    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
    }
    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    try {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val info = MediaCodec.BufferInfo()
        val chunkFrames = 2048
        val chunk = ShortArray(chunkFrames * channels)
        var frame = 0
        var inputDone = false
        var track = -1
        while (true) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)!!
                    val n = minOf(chunkFrames, frames - frame, buffer.capacity() / (2 * channels))
                    val timeUs = frame * 1_000_000L / rate
                    if (n <= 0) {
                        codec.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        fill(chunk, frame, frame + n)
                        buffer.clear()
                        buffer.asShortBuffer().put(chunk, 0, n * channels)
                        codec.queueInputBuffer(index, 0, n * 2 * channels, timeUs, 0)
                        frame += n
                    }
                }
            }
            val out = codec.dequeueOutputBuffer(info, 10_000)
            when {
                out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                out >= 0 -> {
                    val buffer = codec.getOutputBuffer(out)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && track >= 0) {
                        buffer.position(info.offset).limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    } finally {
        runCatching { codec.stop() }
        codec.release()
        runCatching { muxer.stop() }
        muxer.release()
    }
}

/** Decodes an audio file written by [encodeAac] back to interleaved 16-bit samples with [channels] channels (1 or 2). */
fun decodeAudio(file: File, channels: Int): ShortArray {
    val extractor = MediaExtractor()
    extractor.setDataSource(file.path)
    val format = extractor.getTrackFormat(0)
    extractor.selectTrack(0)
    val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    var out = ShortArray(1 shl 16)
    var size = 0
    var decoded = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    try {
        codec.configure(format, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        while (true) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val read = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                    if (read < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, read, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val index = codec.dequeueOutputBuffer(info, 10_000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) decoded = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (index >= 0) {
                val samples = codec.getOutputBuffer(index)!!.apply { position(info.offset).limit(info.offset + info.size) }
                    .order(ByteOrder.nativeOrder()).asShortBuffer()
                val n = samples.remaining()
                if (size + n > out.size) out = out.copyOf(maxOf(out.size * 2, size + n))
                samples.get(out, size, n)
                size += n
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
    } finally {
        runCatching { codec.stop() }
        codec.release()
        extractor.release()
    }
    return when {
        decoded == channels -> out.copyOf(size)
        channels == 1 -> ShortArray(size / decoded) { out[it * decoded] }
        else -> ShortArray(size * 2) { out[it / 2] }
    }
}

fun shareSnippet(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("audio/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/**
 * Copies the snippet into Recordings/Arc, where other apps can find it, over the copy saved before at [replacing] when
 * that is still there. Returns where it went.
 */
suspend fun saveSnippet(context: Context, file: File, replacing: String? = null): String = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT >= 29) {
        val resolver = context.contentResolver
        replacing?.let(Uri::parse)?.let { old ->
            val written = runCatching {
                resolver.openOutputStream(old, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
            }.getOrDefault(false)
            if (written) return@withContext replacing
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            // Recordings are left out of music libraries, Arc's own included, so snippets don't turn up as songs
            val folder = if (Build.VERSION.SDK_INT >= 31) Environment.DIRECTORY_RECORDINGS else Environment.DIRECTORY_MUSIC
            put(MediaStore.Audio.Media.RELATIVE_PATH, "$folder/Arc")
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: error("Couldn't save")
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        uri.toString()
    } else {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Arc").apply { mkdirs() }
        val target = replacing?.let(::File) ?: File(dir, file.name)
        file.copyTo(target, overwrite = true)
        target.path
    }
}

/** Removes a copy [saveSnippet] made. */
fun unsaveSnippet(context: Context, where: String) {
    runCatching {
        if (where.startsWith("content:")) context.contentResolver.delete(Uri.parse(where), null, null) else File(where).delete()
    }
}

private const val HOPS_PER_SECOND = 200
private const val MS_PER_HOP = 1000 / HOPS_PER_SECOND
private const val CONTEXT_MS = 3000

/**
 * How many ms late the voice track is against the song, found by lining up the beats the microphone heard from the
 * speaker with the song itself. Null when the microphone didn't hear the song clearly, as with headphones.
 * Music repeats every beat, so a match a whole beat or more away scores nearly as well as the true one: the search
 * stays near [expectedMs] and, of the near-equal matches, takes the closest.
 */
fun Snippet.Take.findSync(expectedMs: Int = 0, reachMs: Int = 300): Int? {
    val song = onsets(song, 2, rate / HOPS_PER_SECOND)
    val voice = onsets(voice, 1, voiceRate / HOPS_PER_SECOND)
    if (song.size < HOPS_PER_SECOND * 2 || voice.size < HOPS_PER_SECOND * 2) return null
    // scored over a wide span so a match can be told from chance, then chosen only near where it's expected
    val lags = (expectedMs - CONTEXT_MS) / MS_PER_HOP..(expectedMs + CONTEXT_MS) / MS_PER_HOP
    val scores = DoubleArray(lags.count())
    for ((k, lag) in lags.withIndex()) {
        var sum = 0.0
        var count = 0
        for (i in song.indices) {
            val j = i + lag
            if (j < 0 || j >= voice.size) continue
            sum += song[i] * voice[j]
            count++
        }
        scores[k] = if (count > HOPS_PER_SECOND) sum / count else 0.0
    }
    val mean = scores.average()
    val spread = kotlin.math.sqrt(scores.sumOf { (it - mean) * (it - mean) } / scores.size)
    val expected = expectedMs / MS_PER_HOP - lags.first
    val near = (expected - reachMs / MS_PER_HOP).coerceAtLeast(0)..(expected + reachMs / MS_PER_HOP).coerceAtMost(scores.lastIndex)
    val top = near.maxOf { scores[it] }
    // within 15% of the top score; measured from |top| since every score can be below zero
    val best = near.filter { scores[it] >= top - abs(top) * 0.15 }.minByOrNull { abs(it - expected) } ?: return null
    val strength = if (spread > 0) (scores[best] - mean) / spread else 0.0
    val ms = (lags.first + best) * MS_PER_HOP
    return ms.takeIf { strength >= 4 }
}

/** Where the sound gets louder, hop by hop, scaled to a mean of 0 and a spread of 1. */
private fun onsets(samples: ShortArray, channels: Int, hop: Int): DoubleArray {
    val hops = samples.size / channels / hop
    val energy = DoubleArray(hops) { h ->
        var sum = 0.0
        val start = h * hop
        for (f in start until start + hop) sum += kotlin.math.abs(samples[f * channels].toInt())
        sum / hop
    }
    val rise = DoubleArray(hops) { h -> if (h == 0) 0.0 else maxOf(0.0, energy[h] - energy[h - 1]) }
    val mean = rise.average()
    val spread = kotlin.math.sqrt(rise.sumOf { (it - mean) * (it - mean) } / hops).takeIf { it > 0 } ?: 1.0
    return DoubleArray(hops) { (rise[it] - mean) / spread }
}
