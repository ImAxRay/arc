package com.metrolist.music.arc

import android.content.Context
import android.os.PowerManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber

private val DriveLastSyncKey = longPreferencesKey("arcDriveLastSync")
private const val DRIVE_EVERY_MS = 6 * 3_600_000L

private const val DRIVE_REFRESH_COOLDOWN_MS = 5 * 60_000L

/** Picks up new and removed songs on the phone and in Drive without the user asking, a little after start. */
object OwnMusicSync {
    /**
     * A pull to refresh on Home or the library also looks for new songs: the phone every time, Drive at most every
     * few minutes, so pulling again and again doesn't run into Google's request limits.
     */
    suspend fun onPullToRefresh(context: Context, database: MusicDatabase) {
        runCatching { DeviceMusic.sync(context, database) }.onFailure { Timber.tag("ArcOwnMusic").w(it) }
        runCatching { UntitledMusic.sync(context, database) }.onFailure { Timber.tag("ArcOwnMusic").w(it) }
        val last = context.dataStore.data.first()[DriveLastSyncKey] ?: 0L
        if (System.currentTimeMillis() - last < DRIVE_REFRESH_COOLDOWN_MS) return
        if (DriveMusic.accessToken(context) == null) return
        runCatching { DriveMusic.sync(context, database) }
            .onSuccess { context.dataStore.edit { it[DriveLastSyncKey] = System.currentTimeMillis() } }
            .onFailure { Timber.tag("ArcOwnMusic").w(it) }
    }

    suspend fun onAppStart(context: Context, database: MusicDatabase) {
        delay(8_000)
        if (context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) return
        runCatching { DeviceMusic.sync(context, database) }.onFailure { Timber.tag("ArcOwnMusic").w(it) }
        runCatching { UntitledMusic.sync(context, database) }.onFailure { Timber.tag("ArcOwnMusic").w(it) }
        val last = context.dataStore.data.first()[DriveLastSyncKey] ?: 0L
        if (System.currentTimeMillis() - last < DRIVE_EVERY_MS) return
        if (DriveMusic.accessToken(context) == null) return
        runCatching { DriveMusic.sync(context, database) }
            .onSuccess { context.dataStore.edit { it[DriveLastSyncKey] = System.currentTimeMillis() } }
            .onFailure { Timber.tag("ArcOwnMusic").w(it) }
    }
}
