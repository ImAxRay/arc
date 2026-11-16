package com.metrolist.music.arc

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import com.metrolist.music.BuildConfig
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the covers of own files (device, Drive, bundled songs) read-only, so Android Auto and other
 * media controllers, which can't open a file:// path inside Arc's storage, can show them.
 */
class ArtProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment
        if (mode != "r" || name == null || '/' in name || name.startsWith(".") || !name.endsWith(".jpg")) throw FileNotFoundException(uri.toString())
        val file = File(File(context!!.filesDir, ART_DIR), name)
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/jpeg"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null

    override fun insert(uri: Uri, values: ContentValues?) = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val ART_DIR = "device_art"
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.art"

        /** [url] as other apps can open it: own-file covers through this provider, anything else unchanged. */
        fun sharedUri(url: String?): Uri? {
            val uri = url?.toUri() ?: return null
            val name = uri.lastPathSegment
            if (uri.scheme != "file" || name == null || uri.pathSegments.getOrNull(uri.pathSegments.size - 2) != ART_DIR) return uri
            return Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(name).build()
        }
    }
}
