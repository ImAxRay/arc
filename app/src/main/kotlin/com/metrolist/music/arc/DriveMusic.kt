package com.metrolist.music.arc

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.BuildConfig
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.ServerSocket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Music in the user's Google Drive, as songs with ids "DRIVE_" plus the file id, streamed straight from Drive. Two ways in:
 * songs picked in Google's own picker, which gives Arc those files and nothing else and works for anyone; or whole
 * folders, which needs read access to the whole Drive. Google only grants that to accounts on the project's tester
 * list, and makes them sign in again every 7 days while the project is in testing.
 */
object DriveMusic {
    private const val PREFIX = "DRIVE_"
    private const val PICKED_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val FOLDERS_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
    private const val SIGN_IN_DAYS = 7L
    private const val DAY_MS = 24 * 60 * 60_000L
    private const val FOLDER = "application/vnd.google-apps.folder"
    private const val PICKABLE = "$FOLDER,audio/flac,audio/x-flac,audio/mpeg,audio/mp4,audio/x-m4a,audio/aac,audio/ogg,audio/opus,audio/wav,audio/x-wav,audio/aiff,audio/x-aiff"
    private val RefreshKey = stringPreferencesKey("arcDriveRefresh")
    private val FoldersKey = stringPreferencesKey("arcDriveFolders")
    private val WholeFoldersKey = booleanPreferencesKey("arcDriveWholeFolders")
    private val SignedInAtKey = longPreferencesKey("arcDriveSignedInAt")
    private val LastingKey = booleanPreferencesKey("arcDriveLasting")
    private val AUDIO = setOf("flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "alac", "aiff", "aif", "wma")

    /** Something picked in Drive: a folder, read with everything under it, or a single song. */
    @Serializable
    data class Picked(val id: String, val name: String, val folder: Boolean = true)

    @Serializable
    private data class Token(val access_token: String = "", val expires_in: Long = 0, val refresh_token: String? = null)

    /** What a sync did; [unreachable] are picked folders Drive showed nothing in, so nothing was removed. */
    class SyncResult(val added: Int, val unreachable: List<Picked>)

    @Serializable
    data class Item(val id: String = "", val name: String = "", val mimeType: String = "", val size: String? = null)

    @Serializable
    private data class Listing(val files: List<Item> = emptyList(), val nextPageToken: String? = null)

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build() }
    @Volatile private var access: Pair<String, Long>? = null

    fun isDriveSong(mediaId: String?) = mediaId?.startsWith(PREFIX) == true

    fun mediaUrl(mediaId: String) = "https://www.googleapis.com/drive/v3/files/${mediaId.removePrefix(PREFIX)}?alt=media&supportsAllDrives=true"

    fun signedIn(context: Context): Flow<Boolean> = context.dataStore.data.map { it[RefreshKey] != null }

    fun wholeFolders(context: Context): Flow<Boolean> = context.dataStore.data.map { it[WholeFoldersKey] == true }

    /** When whole-folder access runs out, or null when it doesn't (songs picked, or Google keeps it for good). */
    fun folderAccessEnds(context: Context): Flow<Long?> =
        context.dataStore.data.map { prefs ->
            prefs[SignedInAtKey]?.takeIf { prefs[WholeFoldersKey] == true && prefs[LastingKey] != true && prefs[RefreshKey] != null }
                ?.plus(SIGN_IN_DAYS * DAY_MS)
        }

    /** True a day before whole-folder access runs out, for a reminder to sign in again. */
    suspend fun signInDueSoon(context: Context): Boolean =
        folderAccessEnds(context).first()?.let { System.currentTimeMillis() > it - DAY_MS } == true

    fun picked(context: Context): Flow<List<Picked>> =
        context.dataStore.data.map { prefs -> prefs[FoldersKey]?.let { runCatching { json.decodeFromString<List<Picked>>(it) }.getOrNull() }.orEmpty() }

    suspend fun setPicked(context: Context, picked: List<Picked>) {
        context.dataStore.edit { it[FoldersKey] = json.encodeToString(picked.distinctBy { p -> p.id }) }
    }

    suspend fun signOut(context: Context) {
        access = null
        context.dataStore.edit {
            it.remove(RefreshKey)
            it.remove(FoldersKey)
            it.remove(WholeFoldersKey)
            it.remove(SignedInAtKey)
            it.remove(LastingKey)
        }
    }

    /**
     * Signs in through a browser tab: with [wholeFolders], for reading the whole Drive; otherwise through Google's
     * picker, adding what the user picked. Google sends the browser back to a one-off address on this phone
     * (127.0.0.1), where Arc is listening for the answer; nothing passes through any other server. Returns how many
     * things were picked.
     */
    suspend fun signIn(context: Context, wholeFolders: Boolean): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val verifier = randomString()
            val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 5 * 60_000
                val redirect = "http://127.0.0.1:${server.localPort}"
                val url =
                    "https://accounts.google.com/o/oauth2/v2/auth".toHttpUrl().newBuilder()
                        .addQueryParameter("client_id", BuildConfig.ARC_DRIVE_CLIENT_ID)
                        .addQueryParameter("redirect_uri", redirect)
                        .addQueryParameter("response_type", "code")
                        .addQueryParameter("scope", if (wholeFolders) FOLDERS_SCOPE else PICKED_SCOPE)
                        .addQueryParameter("code_challenge", challenge)
                        .addQueryParameter("code_challenge_method", "S256")
                        .addQueryParameter("access_type", "offline")
                        .addQueryParameter("prompt", "consent")
                        .apply {
                            if (!wholeFolders) {
                                addQueryParameter("trigger_onepick", "true")
                                addQueryParameter("allow_multiple", "true")
                                addQueryParameter("mimetypes", PICKABLE)
                            }
                        }
                        .build().toString()
                withContext(Dispatchers.Main) {
                    CustomTabsIntent.Builder().build().apply { intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                        .launchUrl(context, Uri.parse(url))
                }
                val params =
                    server.accept().use { socket ->
                        val request = socket.getInputStream().bufferedReader().readLine().orEmpty()
                        val query = request.substringAfter("?", "").substringBefore(" ")
                        val params = query.split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("=", ""), "UTF-8") }
                        val page = "<html><body style=\"font-family:sans-serif;background:#111;color:#eee;text-align:center;padding-top:30vh\">" +
                            (if (params["code"] != null) "Signed in. You can go back to Arc." else "Sign-in was cancelled.") + "</body></html>"
                        socket.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${page.toByteArray().size}\r\nConnection: close\r\n\r\n$page".toByteArray(),
                        )
                        params
                    }
                val code = params["code"] ?: error(params["error"] ?: "Sign-in was cancelled")
                val token =
                    post(
                        FormBody.Builder()
                            .add("code", code)
                            .add("client_id", BuildConfig.ARC_DRIVE_CLIENT_ID)
                            .add("client_secret", BuildConfig.ARC_DRIVE_CLIENT_SECRET)
                            .add("code_verifier", verifier)
                            .add("grant_type", "authorization_code")
                            .add("redirect_uri", redirect)
                            .build(),
                    )
                val refresh = token.refresh_token ?: error("Google gave no lasting sign-in")
                access = token.access_token to System.currentTimeMillis() + token.expires_in * 1000
                context.dataStore.edit {
                    it[RefreshKey] = refresh
                    it[WholeFoldersKey] = wholeFolders
                    it[SignedInAtKey] = System.currentTimeMillis()
                    it.remove(LastingKey)
                }
                val ids = params["picked_file_ids"].orEmpty().split(",").filter { it.isNotBlank() }
                val items = ids.mapNotNull { id -> runCatching { item(id, token.access_token) }.getOrNull() }
                setPicked(context, picked(context).first() + items.map { Picked(it.id, it.name, it.mimeType == FOLDER) })
                items.size
            }
        }.onFailure { Timber.tag("ArcDrive").w(it, "Sign-in failed") }
    }

    private fun item(id: String, token: String): Item =
        http.newCall(authed("https://www.googleapis.com/drive/v3/files/$id?fields=id,name,mimeType&supportsAllDrives=true", token)).execute().use { r ->
            if (!r.isSuccessful) error("Drive answered ${r.code}")
            json.decodeFromString<Item>(r.body.string())
        }

    /** A fresh access token, renewed a minute before it runs out; null when not signed in. */
    suspend fun accessToken(context: Context): String? = withContext(Dispatchers.IO) {
        access?.takeIf { it.second - 60_000 > System.currentTimeMillis() }?.let { return@withContext it.first }
        val refresh = context.dataStore.data.first()[RefreshKey] ?: return@withContext null
        runCatching {
            post(
                FormBody.Builder()
                    .add("refresh_token", refresh)
                    .add("client_id", BuildConfig.ARC_DRIVE_CLIENT_ID)
                    .add("client_secret", BuildConfig.ARC_DRIVE_CLIENT_SECRET)
                    .add("grant_type", "refresh_token")
                    .build(),
            )
        }.onFailure {
            if (it.message?.contains("answered 400") == true || it.message?.contains("answered 401") == true) SignedOut.drive.value = true
        }.getOrNull()?.let { token ->
            SignedOut.drive.value = false
            // still renewing after the testing limit means Google keeps this sign-in for good
            val prefs = context.dataStore.data.first()
            if (prefs[LastingKey] != true && prefs[SignedInAtKey]?.let { System.currentTimeMillis() - it > (SIGN_IN_DAYS + 1) * DAY_MS } == true) {
                context.dataStore.edit { it[LastingKey] = true }
            }
            access = token.access_token to System.currentTimeMillis() + token.expires_in * 1000
            token.access_token
        }
    }

    /** Downloads the song's file into [dir] under its Drive name, for handing to another app. */
    suspend fun download(context: Context, mediaId: String, dir: java.io.File): java.io.File = withContext(Dispatchers.IO) {
        val token = accessToken(context) ?: error("Not signed in to Google Drive")
        val id = mediaId.removePrefix(PREFIX)
        val name =
            http.newCall(authed("https://www.googleapis.com/drive/v3/files/$id?fields=name&supportsAllDrives=true", token)).execute().use { r ->
                if (!r.isSuccessful) error("Drive answered ${r.code}")
                json.decodeFromString<Item>(r.body.string()).name
            }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        dir.mkdirs()
        val file = java.io.File(dir, name)
        http.newCall(authed(mediaUrl(mediaId), token)).execute().use { r ->
            if (!r.isSuccessful) error("Drive answered ${r.code}")
            file.outputStream().use { r.body.byteStream().copyTo(it) }
        }
        file
    }

    /** The folders inside [parent] ("root" for My Drive, null for folders shared with the user). */
    suspend fun subfolders(context: Context, parent: String?): List<Item> =
        list(context, if (parent == null) "sharedWithMe and mimeType='$FOLDER' and trashed=false" else "'$parent' in parents and mimeType='$FOLDER' and trashed=false")
            .sortedBy { it.name.lowercase() }

    private fun authed(url: String, token: String) = Request.Builder().url(url).header("Authorization", "Bearer $token").build()

    private suspend fun list(context: Context, query: String): List<Item> = withContext(Dispatchers.IO) {
        val token = accessToken(context) ?: return@withContext emptyList()
        val all = ArrayList<Item>()
        var page: String? = null
        do {
            val url =
                "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size)")
                    .addQueryParameter("pageSize", "1000")
                    .addQueryParameter("supportsAllDrives", "true")
                    .addQueryParameter("includeItemsFromAllDrives", "true")
                    .apply { page?.let { addQueryParameter("pageToken", it) } }
                    .build()
            val listing =
                http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { r ->
                    if (!r.isSuccessful) error("Drive answered ${r.code}")
                    json.decodeFromString<Listing>(r.body.string())
                }
            all += listing.files
            page = listing.nextPageToken
        } while (page != null)
        all
    }

    /**
     * Adds the picked songs and the audio files in the picked folders (and their subfolders) to the library and drops
     * the ones that are gone. Tags and cover are read from the start of each file over the network, once per new file.
     * A picked folder that shows nothing usually means Google gave access to the folder but not to what's inside it
     * (picked rather than whole-folder sign-in), so then nothing is removed, and it fails when nothing at all was found.
     */
    suspend fun sync(context: Context, database: MusicDatabase): SyncResult = withContext(Dispatchers.IO) {
        val token = accessToken(context) ?: error(context.getString(com.metrolist.music.R.string.arc_signed_out_drive))
        val found = LinkedHashMap<String, Pair<Item, String>>()
        val picked = picked(context).first()
        picked.filterNot { it.folder }.forEach { found[PREFIX + it.id] = Item(it.id, it.name) to "" }
        val pending = ArrayDeque(picked.filter { it.folder }.map { it.id to it.name })
        val top = picked.filter { it.folder }.associateBy { it.id }
        val unreachable = ArrayList<Picked>()
        val seen = HashSet<String>()
        while (pending.isNotEmpty()) {
            val (folder, folderName) = pending.removeFirst()
            if (!seen.add(folder)) continue
            val items = list(context, "'$folder' in parents and trashed=false")
            if (items.isEmpty()) top[folder]?.let { unreachable += it }
            items.forEach { item ->
                when {
                    item.mimeType == FOLDER -> pending += item.id to item.name
                    item.mimeType.startsWith("audio/") || item.name.substringAfterLast('.').lowercase() in AUDIO -> found[PREFIX + item.id] = item to folderName
                }
            }
        }
        if (found.isEmpty() && picked.isNotEmpty()) error(context.getString(com.metrolist.music.R.string.arc_drive_nothing_found))
        val known = database.driveSongIds().toSet()
        val fresh = found.filterKeys { it !in known }
        val gone = known - found.keys
        if (gone.isNotEmpty() && unreachable.isEmpty()) database.query { deleteSongs(gone.toList()) }
        fresh.forEach { (id, entry) ->
            val (item, folderName) = entry
            val tags = readTags(context, id, token)
            database.transaction {
                insert(
                    SongEntity(
                        id = id,
                        title = tags.title ?: titleFromName(item.name),
                        duration = tags.seconds,
                        thumbnailUrl = tags.art,
                        albumName = tags.album ?: folderName.ifEmpty { null },
                        year = tags.year,
                        inLibrary = LocalDateTime.now(),
                        isLocal = true,
                    ),
                )
                val name = tags.artist ?: return@transaction
                val artistId = artistByName(name)?.id ?: ArtistEntity.generateArtistId().also { insert(ArtistEntity(id = it, name = name, isLocal = true)) }
                insert(SongArtistMap(songId = id, artistId = artistId, position = 0))
            }
        }
        SyncResult(fresh.size, unreachable)
    }

    private class Tags(val title: String?, val artist: String?, val album: String?, val year: Int?, val seconds: Int, val art: String?)

    private fun readTags(context: Context, id: String, token: String): Tags {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(mediaUrl(id), mapOf("Authorization" to "Bearer $token"))
            fun get(key: Int) = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }
            Tags(
                title = get(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = get(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: get(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = get(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM),
                year = get(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull(),
                seconds = ((get(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000).toInt(),
                art = retriever.embeddedPicture?.let { DeviceMusic.saveArtBytes(context, id, it) },
            )
        } catch (e: Exception) {
            Timber.tag("ArcDrive").w("Tags unreadable for $id: ${e.message}")
            Tags(null, null, null, null, 0, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    // "03 - Song.flac" or "03. Song.flac" becomes "Song"
    private fun titleFromName(name: String) = name.substringBeforeLast('.').replace(Regex("^\\d{1,3}\\s*[-.]\\s*"), "").trim()

    private fun post(body: FormBody): Token =
        http.newCall(Request.Builder().url("https://oauth2.googleapis.com/token").post(body).build()).execute().use { r ->
            if (!r.isSuccessful) error("Google answered ${r.code}")
            json.decodeFromString<Token>(r.body.string())
        }

    private fun randomString(): String {
        val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
