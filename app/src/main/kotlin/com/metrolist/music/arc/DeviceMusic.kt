package com.metrolist.music.arc

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Music files on the phone, kept in the library as songs whose id is "LOCAL_" plus the file's MediaStore id,
 * so the file is found again from the id alone (scanner after Mochy Music's DeviceMusicScanner, GPLv3).
 */
object DeviceMusic {
    private const val PREFIX = "LOCAL_"
    private const val ART_SIZE = 720

    val permission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasAccess(context: Context) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isDeviceSong(mediaId: String?) = mediaId?.startsWith(PREFIX) == true

    fun fileUri(mediaId: String): Uri? =
        mediaId.removePrefix(PREFIX).toLongOrNull()?.let { ContentUris.withAppendedId(collection, it) }

    private val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    private class Found(
        val id: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val year: Int?,
        val seconds: Int,
        val added: LocalDateTime,
    )

    /** Adds new music files to the library and drops the ones deleted from the phone. Returns how many were added. */
    suspend fun sync(context: Context, database: MusicDatabase): Int = withContext(Dispatchers.IO) {
        if (!hasAccess(context)) return@withContext 0
        val found = scan(context) ?: return@withContext 0
        val known = database.deviceSongIds().toSet()
        val fresh = found.filter { it.id !in known }
        val gone = known - found.mapTo(HashSet()) { it.id }
        val arts = (fresh.map { it.id } + database.deviceSongsWithAlbumArt()).associateWith { saveArt(context, it) }
        database.transaction {
            if (gone.isNotEmpty()) deleteSongs(gone.toList())
            arts.forEach { (id, url) -> if (id in known) setThumbnail(id, url) }
            fresh.forEach { f ->
                insert(
                    SongEntity(
                        id = f.id,
                        title = f.title,
                        duration = f.seconds,
                        thumbnailUrl = arts[f.id],
                        albumName = f.album,
                        year = f.year,
                        inLibrary = f.added,
                        isLocal = true,
                    ),
                )
                val name = f.artist ?: return@forEach
                val artistId = artistByName(name)?.id ?: ArtistEntity.generateArtistId().also {
                    insert(ArtistEntity(id = it, name = name, isLocal = true))
                }
                insert(SongArtistMap(songId = f.id, artistId = artistId, position = 0))
            }
        }
        fresh.size
    }

    private val searched = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Gives the artists read from file tags their YouTube Music page: only an artist of exactly the same name is
     * taken, and each name is asked about once per run of the app.
     */
    suspend fun matchArtists(database: MusicDatabase) = withContext(Dispatchers.IO) {
        database.unmatchedDeviceArtists().filter { searched.add(it.name) }.take(MATCHES_PER_SYNC).forEach { local ->
            val match = YouTube.search(local.name, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                ?.items
                ?.filterIsInstance<ArtistItem>()
                ?.firstOrNull { it.title.trim().equals(local.name.trim(), ignoreCase = true) }
                ?: return@forEach
            database.transaction {
                if (getArtistById(match.id) == null) {
                    insert(ArtistEntity(id = match.id, name = match.title, thumbnailUrl = match.thumbnail, channelId = match.channelId))
                }
                moveArtistSongs(local.id, match.id)
                deleteArtist(local.id)
            }
        }
    }

    private const val MATCHES_PER_SYNC = 40

    private fun scan(context: Context): List<Found>? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        return try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null,
            )?.use { c ->
                val list = ArrayList<Found>(c.count)
                while (c.moveToNext()) {
                    list += Found(
                        id = PREFIX + c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Unknown song",
                        artist = c.getString(2).cleanTag(),
                        album = c.getString(3).cleanTag(),
                        year = c.getInt(5).takeIf { it > 0 },
                        seconds = (c.getLong(6) / 1000).toInt(),
                        added = LocalDateTime.ofInstant(Instant.ofEpochSecond(c.getLong(7)), ZoneId.systemDefault()),
                    )
                }
                list
            }
        } catch (e: Exception) {
            Timber.w(e, "Scanning the phone for music failed")
            null
        }
    }

    /**
     * The picture inside the file itself, saved small next to the app's files: Android's album art is shared by
     * every song it files under one album, so untagged songs in a folder all showed the same cover.
     */
    private fun saveArt(context: Context, id: String): String? {
        val file = java.io.File(context.filesDir, "device_art/$id.jpg")
        if (file.exists()) return file.toUri().toString()
        val bytes = runCatching {
            android.media.MediaMetadataRetriever().use { r ->
                r.setDataSource(context, fileUri(id) ?: return null)
                r.embeddedPicture
            }
        }.getOrNull() ?: return null
        return saveArtBytes(context, id, bytes)
    }

    /** Saves a cover picture taken from inside a file, scaled down, and returns its address for the library. */
    fun saveArtBytes(context: Context, id: String, bytes: ByteArray): String? {
        val file = java.io.File(context.filesDir, "device_art/$id.jpg")
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= ART_SIZE) sample *= 2
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file.toUri().toString()
    }

    private val folderColumn: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA

    private fun String.asFolder(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) trimEnd('/') else substringBeforeLast('/').substringAfter("/0/")

    /** The folder a phone song's file sits in, like "Music/Albums/Blue", and its format, like "FLAC". */
    fun fileInfo(context: Context, mediaId: String): Pair<String?, String?> {
        val uri = fileUri(mediaId) ?: return null to null
        return runCatching {
            context.contentResolver.query(uri, arrayOf(folderColumn, MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null to null
                c.getString(0)?.asFolder() to c.getString(1)?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.length in 2..5 }
            }
        }.getOrNull() ?: (null to null)
    }

    class Facts(val folder: String?, val format: String?, val bytes: Long)

    /** Every phone song's folder, format and size, by library id, in one query. */
    fun facts(context: Context): Map<String, Facts> = runCatching {
        val columns = arrayOf(MediaStore.Audio.Media._ID, folderColumn, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.SIZE)
        context.contentResolver.query(collection, columns, null, null, null)?.use { c ->
            buildMap {
                while (c.moveToNext()) {
                    val format = c.getString(2)?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.length in 2..5 }
                    put(PREFIX + c.getLong(0), Facts(c.getString(1)?.asFolder(), format, c.getLong(3)))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyMap())

    /** The library ids of every music file directly in [folder]. */
    fun songsInFolder(context: Context, folder: String): List<String> = runCatching {
        val (where, arg) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ?" to "$folder/"
        } else {
            "${MediaStore.Audio.Media.DATA} LIKE ?" to "%/$folder/%"
        }
        context.contentResolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), where, arrayOf(arg), null)?.use { c ->
            buildList { while (c.moveToNext()) add(PREFIX + c.getLong(0)) }
        }.orEmpty()
    }.getOrDefault(emptyList())

    // MediaStore writes "<unknown>" into a column it has nothing for
    private fun String?.cleanTag() = this?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
}
