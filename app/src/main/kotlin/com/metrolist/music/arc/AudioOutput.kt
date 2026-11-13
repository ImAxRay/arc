package com.metrolist.music.arc

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.mediarouter.app.SystemOutputSwitcherDialogController
import com.metrolist.music.R
import java.util.Locale

val ArcAudioDetailsKey = booleanPreferencesKey("arcAudioDetails")

/** Opens the phone's own picker for where the sound goes (speaker, headphones, Bluetooth, cast). */
fun showOutputSwitcher(context: Context) {
    SystemOutputSwitcherDialogController.showDialog(context)
}

/** An icon for where the sound is going now, following headphones as they connect and disconnect. */
@Composable
fun rememberOutputIcon(): Int {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var icon by remember { mutableStateOf(outputIcon(audio)) }
    DisposableEffect(audio) {
        val callback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    icon = outputIcon(audio)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    icon = outputIcon(audio)
                }
            }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    return icon
}

private fun outputIcon(audio: AudioManager): Int {
    val types = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
    return when {
        types.any { it in BLUETOOTH } -> R.drawable.bluetooth
        types.any { it in WIRED } -> R.drawable.headphones
        else -> R.drawable.phone_speaker
    }
}

private val BLUETOOTH = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
private val WIRED = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)

/**
 * A short line about what is playing, like "OPUS · 141 kbps" or "FLAC · 24-bit · 96 kHz": lossy streams are told
 * by their bitrate, lossless ones by bit depth and sample rate, since a lossless bitrate says nothing useful.
 * [storedBitrate] (bits per second) is used when the stream itself doesn't say.
 */
@Composable
fun rememberAudioDetails(player: Player, storedBitrate: Int?): String? {
    var format by remember(player) { mutableStateOf(audioFormat(player.currentTracks)) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    format = audioFormat(tracks)
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return format?.let { describe(it, storedBitrate) }
}

private fun audioFormat(tracks: Tracks): Format? =
    tracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let(group::getTrackFormat) }

private fun describe(format: Format, storedBitrate: Int?): String? {
    val mime = format.sampleMimeType ?: return null
    val codec =
        when (mime) {
            MimeTypes.AUDIO_OPUS -> "OPUS"
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_ALAC -> "ALAC"
            MimeTypes.AUDIO_MPEG -> "MP3"
            MimeTypes.AUDIO_VORBIS -> "VORBIS"
            MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_WAV -> "PCM"
            MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "DOLBY"
            else -> mime.substringAfter('/').uppercase(Locale.ROOT)
        }
    val lossless = mime in LOSSLESS
    val parts = mutableListOf(codec)
    if (lossless) {
        bitDepth(format.pcmEncoding)?.let { parts += "$it-bit" }
        if (format.sampleRate > 0) parts += kiloHertz(format.sampleRate)
    } else {
        val bitrate = listOf(format.averageBitrate, format.bitrate, format.peakBitrate, storedBitrate ?: -1).firstOrNull { it > 0 }
        if (bitrate != null) parts += "${bitrate / 1000} kbps"
    }
    return parts.joinToString(" · ")
}

private val LOSSLESS = setOf(MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_ALAC, MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_WAV)

private fun bitDepth(encoding: Int) =
    when (encoding) {
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
        C.ENCODING_PCM_8BIT -> 8
        else -> null
    }

private fun kiloHertz(rate: Int) =
    if (rate % 1000 == 0) "${rate / 1000} kHz" else String.format(Locale.ROOT, "%.1f kHz", rate / 1000f)
