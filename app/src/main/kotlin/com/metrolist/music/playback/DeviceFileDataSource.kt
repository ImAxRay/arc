package com.metrolist.music.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.metrolist.music.arc.BundledTracks
import com.metrolist.music.arc.DeviceMusic
import com.metrolist.music.arc.UntitledMusic

/** Plays songs stored on the phone straight from their file, leaving the stream resolver and its caches out. */
@UnstableApi
class DeviceFileDataSource(
    private val context: Context,
    private val file: DataSource,
    private val stream: DataSource,
    private val untitled: DataSource? = null,
) : DataSource {
    private var open: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        file.addTransferListener(transferListener)
        stream.addTransferListener(transferListener)
        untitled?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        // Untitled songs are streamed and never cached: the artist decides whether listeners may keep a copy
        if (untitled != null && (UntitledMusic.isUntitledSong(dataSpec.key) || UntitledMusic.isUntitledSong(dataSpec.uri.toString()))) {
            open = untitled
            return untitled.open(dataSpec)
        }
        val id = dataSpec.key ?: dataSpec.uri.toString()
        val bundled = BundledTracks.isBundled(id)
        val local = bundled || DeviceMusic.isDeviceSong(dataSpec.key) || DeviceMusic.isDeviceSong(dataSpec.uri.toString())
        val target = if (local) file else stream
        open = target
        val spec = when {
            bundled -> dataSpec.withUri(BundledTracks.audioUri(context, id))
            local -> dataSpec.withUri(DeviceMusic.fileUri(id) ?: error("Bad device song id $id"))
            else -> dataSpec
        }
        return target.open(spec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int) = open!!.read(buffer, offset, length)

    override fun getUri(): Uri? = open?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = open?.responseHeaders ?: emptyMap()

    override fun close() {
        open?.close()
        open = null
    }

    class Factory(
        private val context: Context,
        private val stream: DataSource.Factory,
        private val untitled: DataSource.Factory? = null,
    ) : DataSource.Factory {
        override fun createDataSource() =
            DeviceFileDataSource(context, DefaultDataSource(context, false), stream.createDataSource(), untitled?.createDataSource())
    }
}
