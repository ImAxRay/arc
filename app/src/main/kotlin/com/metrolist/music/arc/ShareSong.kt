package com.metrolist.music.arc

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Songs that are the user's own files (on the phone or in their Drive), which can be sent as the file itself. */
fun isOwnFile(mediaId: String?) = DeviceMusic.isDeviceSong(mediaId) || DriveMusic.isDriveSong(mediaId)

/** Shares a song: the file itself for the user's own songs, the YouTube Music link for everything else. */
fun shareSong(context: Context, mediaId: String, title: String?, artist: String?) {
    if (!isOwnFile(mediaId)) {
        val text = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=$mediaId")
        context.startActivity(Intent.createChooser(text, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    android.widget.Toast.makeText(context, com.metrolist.music.R.string.arc_share_file_preparing, android.widget.Toast.LENGTH_SHORT).show()
    val app = context.applicationContext
    androidx.lifecycle.ProcessLifecycleOwner.get().lifecycleScope.launch {
        runCatching { shareSongFile(app, mediaId, listOfNotNull(artist, title).joinToString(" - ")) }
            .onFailure { android.widget.Toast.makeText(app, it.message, android.widget.Toast.LENGTH_LONG).show() }
    }
}

/**
 * Opens the share sheet with the song's audio file. Phone songs are shared in place; Drive songs are downloaded
 * to the cache first, replacing the last one shared so the cache never holds more than one.
 */
suspend fun shareSongFile(context: Context, mediaId: String, name: String? = null) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared_song")
        dir.listFiles()?.forEach(File::delete)
        dir.mkdirs()
        if (DeviceMusic.isDeviceSong(mediaId)) {
            // a MediaStore link can't be handed on to another app, so the file itself goes through Arc's provider
            val source = DeviceMusic.fileUri(mediaId) ?: error("File not found")
            val (_, format) = DeviceMusic.fileInfo(context, mediaId)
            val base = name?.replace(Regex("[\\\\/:*?\"<>|]"), " ")?.trim()?.takeIf { it.isNotEmpty() } ?: mediaId
            val target = File(dir, "$base.${format?.lowercase() ?: "audio"}")
            context.contentResolver.openInputStream(source)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: error("File not found")
            target
        } else {
            DriveMusic.download(context, mediaId, dir)
        }
    }
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    val type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "audio/*"
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(
        Intent.createChooser(send, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}
