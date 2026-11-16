/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music

import com.metrolist.music.arc.swipeUpOpens
import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.constants.AppBarHeight
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.arc.playStartupSong
import com.metrolist.music.constants.CheckForUpdatesKey
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.DefaultOpenTabKey
import com.metrolist.music.constants.DismissedKmpUpdateKey
import com.metrolist.music.constants.DismissedStandaloneUpdateKey
import com.metrolist.music.constants.DensityScaleKey
import com.metrolist.music.constants.DisableScreenshotKey
import com.metrolist.music.constants.DynamicThemeKey
import com.metrolist.music.constants.EnableHighRefreshRateKey
import com.metrolist.music.constants.EnableLandscapeScalingKey
import com.metrolist.music.constants.ExperimentalLyricsKey
import com.metrolist.music.constants.ForceBottomNavBarKey
import com.metrolist.music.constants.LastSeenVersionKey
import com.metrolist.music.constants.ListenTogetherInTopBarKey
import com.metrolist.music.constants.ListenTogetherUsernameKey
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.constants.MiniPlayerBottomSpacing
import com.metrolist.music.arc.CapsuleButton
import com.metrolist.music.arc.CapsuleNavBar
import com.metrolist.music.arc.earnSearchHoldHint
import com.metrolist.music.arc.trackRecentSettings
import com.metrolist.music.arc.CapsuleNavHeight
import com.metrolist.music.arc.CapsuleRow
import com.metrolist.music.arc.TopBarEndPadding
import com.metrolist.music.arc.TopBarTopPadding
import com.metrolist.music.constants.MiniPlayerHeight
import com.metrolist.music.constants.NavigationBarAnimationSpec
import com.metrolist.music.constants.NavigationBarHeight
import com.metrolist.music.constants.PauseListenHistoryKey
import com.metrolist.music.constants.PauseSearchHistoryKey
import com.metrolist.music.constants.PreferredLyricsProvider
import com.metrolist.music.constants.PreferredLyricsProviderKey
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.constants.SelectedThemeColorKey
import com.metrolist.music.constants.SimpMusicMigrationDoneKey
import com.metrolist.music.constants.SlimNavBarHeight
import com.metrolist.music.constants.SlimNavBarKey
import com.metrolist.music.constants.StopMusicOnTaskClearKey
import com.metrolist.music.constants.UpdateNotificationsEnabledKey
import com.metrolist.music.constants.UseNewMiniPlayerDesignKey
import com.metrolist.music.constants.VideoThumbnailMigrationDoneKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.SearchHistory
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.lyrics.LyricsProviderRegistry
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.MusicService.MusicBinder
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.AccountSettingsDialog
import com.metrolist.music.ui.component.AppNavigationBar
import com.metrolist.music.ui.component.isRouteSelected
import com.metrolist.music.ui.component.AppNavigationRail
import com.metrolist.music.ui.component.BottomSheetMenu
import com.metrolist.music.ui.component.BottomSheetPage
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.rememberBottomSheetState
import com.metrolist.music.ui.component.shimmer.ShimmerTheme
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.player.BottomSheetPlayer
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.ui.screens.navigationBuilder
import com.metrolist.music.ui.screens.settings.ChangelogScreen
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.screens.settings.NavigationTab
import com.metrolist.music.ui.theme.ColorSaver
import com.metrolist.music.ui.theme.DefaultThemeColor
import com.metrolist.music.ui.theme.MetrolistTheme
import com.metrolist.music.ui.theme.extractThemeColor
import com.metrolist.music.ui.utils.appBarScrollBehavior
import com.metrolist.music.ui.utils.resetHeightOffset
import com.metrolist.music.utils.ReleaseInfo
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.utils.get
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.setAppLocale
import com.metrolist.music.viewmodels.HomeViewModel
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

private data class AvailableUpdate(
    val release: ReleaseInfo,
    val downloadUrl: String,
    val isKmp: Boolean,
) {
    val dismissalKey = if (isKmp) DismissedKmpUpdateKey else DismissedStandaloneUpdateKey
}

/** Routes that cover the whole screen with a tool of their own, so the bottom navigation steps aside. */
private val NoNavigationBarRoutes = listOf("recognition", "wrapped", "login", "equalizer", "listen_together")

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    companion object {
        private const val ACTION_SEARCH = "com.metrolist.music.action.SEARCH"
        private const val ACTION_LIBRARY = "com.metrolist.music.action.LIBRARY"
        const val ACTION_RECOGNITION = "com.metrolist.music.action.RECOGNITION"
        const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"
    }

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var listenTogetherManager: com.metrolist.music.listentogether.ListenTogetherManager

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var latestVersionName by mutableStateOf(BuildConfig.BASE_VERSION_NAME)

    // Keep PlayerConnection as regular property - NOT mutableStateOf to prevent UI recomposition
    // when it becomes null during onStop. Only update the snapshot for Compose when needed.
    private var playerConnection: PlayerConnection? = null

    // This is the snapshot we pass to Compose - changes here trigger recomposition
    private var playerConnectionSnapshot by mutableStateOf<PlayerConnection?>(null)

    private var isServiceBound = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                if (service is MusicBinder) {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playerConnectionSnapshot = playerConnection
                    listenTogetherManager.setPlayerConnection(playerConnection)
                    playerConnection?.let { connection ->
                        lifecycleScope.launch {
                            playStartupSong(this@MainActivity, database, connection, intent?.action == Intent.ACTION_MAIN)
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Disconnect Listen Together manager
                listenTogetherManager.setPlayerConnection(null)
                playerConnection?.dispose()
                // DO NOT null out playerConnection here - keep it for when service reconnects
                // DO NOT update playerConnectionSnapshot - this is the key to preventing recomposition
            }
        }

    private fun safeUnbindService(source: String) {
        if (!isServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Timber.tag("MainActivity").w(e, "Service was not bound when attempting to unbind in $source")
        } finally {
            isServiceBound = false
            listenTogetherManager.setPlayerConnection(null)
            playerConnection?.dispose()
            // DO NOT null out playerConnection here - keep it for reconnection
            // DO NOT update playerConnectionSnapshot - this prevents UI recomposition
        }
    }

    override fun onStart() {
        super.onStart()
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
            }
        }

        // Start the playback service explicitly once so it can outlive binding.
        // Re-issuing startForegroundService() while an existing service instance is already
        // running can trigger "did not then call startForeground" on some Android 9 devices
        // when the framework expects a fresh foreground promotion for that start request.
        if (!MusicService.isRunning) {
            val serviceIntent = Intent(this, MusicService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.startForegroundService(this, serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Timber.w(e, "Cannot start foreground service from background")
            } catch (e: IllegalStateException) {
                Timber.w(e, "Failed to start foreground service")
            }
        }

        // Bind to service - if already bound, this is a no-op but ensures we stay connected
        if (!isServiceBound) {
            bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                BIND_AUTO_CREATE,
            )
            isServiceBound = true
        }
    }

    override fun onStop() {
        // Keep the service binding, PlayerConnection and Listen Together wiring alive while
        // the Activity is backgrounded. The MusicService is a foreground service and keeps
        // running, so the host must keep reporting playback state to the LT server; detaching
        // the player listener here used to break LT for any host that wasn't staring at the
        // app the whole session. Full teardown happens in onDestroy() via safeUnbindService().
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            listenTogetherManager.disconnect()
        }
        super.onDestroy()
        val stopServiceOnClear = dataStore.get(StopMusicOnTaskClearKey, true) && isFinishing

        // Full cleanup - only on actual destroy
        playerConnection?.dispose()
        playerConnection = null
        playerConnectionSnapshot = null

        // Unbind before stopService: a started+bound service does not stop until all clients unbind.
        safeUnbindService("onDestroy()")

        if (stopServiceOnClear) {
            stopService(Intent(this, MusicService::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::navController.isInitialized) {
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize Listen Together manager
        listenTogetherManager.initialize()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locale =
                dataStore[AppLanguageKey]
                    ?.takeUnless { it == SYSTEM_DEFAULT }
                    ?.let { Locale.forLanguageTag(it) }
                    ?: Locale.getDefault()
            setAppLocale(this, locale)
        }

        lifecycleScope.launch { trackRecentSettings(this@MainActivity) }
        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        // Defer migration and version tracking to avoid blocking first frame
        lifecycleScope.launch(Dispatchers.IO) {
            val preferences = dataStore.data.first()
            val currentVersion = BuildConfig.BASE_VERSION_NAME

            // SimpMusic Removal Migration
            if (preferences[SimpMusicMigrationDoneKey] != true) {
                safeDataStoreEdit { settings ->
                    val currentOrder = settings[LyricsProviderOrderKey] ?: ""
                    if (currentOrder.contains("SimpMusic")) {
                        val orderList =
                            currentOrder
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() && it != "SimpMusic" }
                                .toMutableList()
                        if (orderList.isEmpty()) {
                            settings[LyricsProviderOrderKey] = ""
                        } else {
                            settings[LyricsProviderOrderKey] = orderList.joinToString(",")
                        }
                    }
                    if (settings[PreferredLyricsProviderKey] == "SIMPMUSIC") {
                        settings[PreferredLyricsProviderKey] = PreferredLyricsProvider.LRCLIB.name
                    }
                    settings[SimpMusicMigrationDoneKey] = true
                    settings[LastSeenVersionKey] = currentVersion
                }
            }

            if (preferences[VideoThumbnailMigrationDoneKey] != true) {
                database.repairMissingVideoThumbnails()
                safeDataStoreEdit { settings ->
                    settings[VideoThumbnailMigrationDoneKey] = true
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            safeDataStoreEdit { settings ->
                settings[LastSeenVersionKey] = BuildConfig.BASE_VERSION_NAME
            }
        }

        setContent {
            MetrolistApp(
                latestVersionName = latestVersionName,
                onLatestVersionNameChange = { latestVersionName = it },
                playerConnection = playerConnectionSnapshot,
                database = database,
                downloadUtil = downloadUtil,
                syncUtils = syncUtils,
            )
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MetrolistApp(
        latestVersionName: String,
        onLatestVersionNameChange: (String) -> Unit,
        playerConnection: PlayerConnection?,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        syncUtils: SyncUtils,
    ) {
        val checkForUpdates by rememberPreference(CheckForUpdatesKey, defaultValue = true)
        var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }

        if (BuildConfig.UPDATER_AVAILABLE) {
            LaunchedEffect(checkForUpdates) {
                if (checkForUpdates) {
                    val preferences = dataStore.data.first()
                    val notificationsEnabled = preferences[UpdateNotificationsEnabledKey] ?: true
                    val (releaseInfo, hasUpdate) = Updater.checkForUpdate().getOrNull() ?: (null to false)
                    // only a newer release counts; a build ahead of GitHub's latest isn't offered the older one
                    onLatestVersionNameChange(releaseInfo?.takeIf { hasUpdate }?.versionName ?: BuildConfig.BASE_VERSION_NAME)

                    val standaloneUpdate =
                        releaseInfo
                            ?.takeIf { hasUpdate }
                            ?.let { release ->
                                Updater.getDownloadUrlForCurrentVariant(release)?.let { downloadUrl ->
                                    AvailableUpdate(release, downloadUrl, isKmp = false)
                                }
                            }
                    val kmpUpdate =
                        Updater.getLatestKmpRelease().getOrNull()?.let { release ->
                            release.assets.firstOrNull()?.let { asset ->
                                AvailableUpdate(release, asset.downloadUrl, isKmp = true)
                            }
                        }
                    val update = kmpUpdate ?: standaloneUpdate
                    availableUpdate = update?.takeUnless {
                        it.release.tagName == preferences[it.dismissalKey]
                    }

                    if (update != null && availableUpdate != null && notificationsEnabled) {
                        // Arc downloads its own updates, so the notification just opens the app and its update dialog.
                        val intent =
                            if (update.isKmp) {
                                Intent(Intent.ACTION_VIEW, update.downloadUrl.toUri())
                            } else {
                                Intent(this@MainActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        val pending = PendingIntent.getActivity(this@MainActivity, 1001, intent, flags)
                        val notificationText =
                            if (update.isKmp) {
                                getString(R.string.kmp_upgrade_warning)
                            } else {
                                update.release.versionName
                            }
                        val notification =
                            NotificationCompat
                                .Builder(this@MainActivity, "updates")
                                .setSmallIcon(R.drawable.update)
                                .setContentTitle(
                                    if (update.isKmp) {
                                        getString(R.string.kmp_upgrade_title, update.release.versionName)
                                    } else {
                                        getString(R.string.update_available_title)
                                    },
                                )
                                .setContentText(notificationText)
                                .apply {
                                    if (update.isKmp) {
                                        setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
                                    }
                                }
                                .setContentIntent(pending)
                                .setAutoCancel(true)
                                .build()

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            NotificationManagerCompat.from(this@MainActivity).notify(1001, notification)
                        }
                    }
                } else {
                    onLatestVersionNameChange(BuildConfig.BASE_VERSION_NAME)
                    availableUpdate = null
                }
            }
        }

        val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
        val enableHighRefreshRate by rememberPreference(EnableHighRefreshRateKey, defaultValue = true)

        LaunchedEffect(enableHighRefreshRate) {
            val window = this@MainActivity.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val layoutParams = window.attributes
                if (enableHighRefreshRate) {
                    layoutParams.preferredDisplayModeId = 0
                } else {
                    val modes = window.windowManager.defaultDisplay.supportedModes
                    val mode60 =
                        modes.firstOrNull { kotlin.math.abs(it.refreshRate - 60f) < 1f }
                            ?: modes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }

                    if (mode60 != null) {
                        layoutParams.preferredDisplayModeId = mode60.modeId
                    }
                }
                window.attributes = layoutParams
            } else {
                val params = window.attributes
                if (enableHighRefreshRate) {
                    params.preferredRefreshRate = 0f
                } else {
                    params.preferredRefreshRate = 60f
                }
                window.attributes = params
            }
        }

        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isSystemInDarkTheme()
        val useDarkTheme =
            remember(darkTheme, isSystemInDarkTheme) {
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            }

        LaunchedEffect(useDarkTheme) {
            setSystemBarAppearance(useDarkTheme)
        }

        val userDensityScale by rememberPreference(DensityScaleKey, defaultValue = 1f)
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val pureBlack =
            remember(pureBlackEnabled, useDarkTheme) {
                pureBlackEnabled && useDarkTheme
            }

        val (selectedThemeColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
        val selectedThemeColor = Color(selectedThemeColorInt)

        val showChangelog = rememberSaveable { mutableStateOf(false) }

        var themeColor by rememberSaveable(stateSaver = ColorSaver) {
            mutableStateOf(selectedThemeColor)
        }

        val themeColorCache = remember { mutableMapOf<String, Color>() }

        LaunchedEffect(selectedThemeColor) {
            if (!enableDynamicTheme) {
                themeColor = selectedThemeColor
            }
        }

        LaunchedEffect(playerConnection, enableDynamicTheme, selectedThemeColor) {
            val playerConnection = playerConnection
            if (!enableDynamicTheme || playerConnection == null) {
                themeColor = selectedThemeColor
                return@LaunchedEffect
            }

            playerConnection.service.currentMediaMetadata
                .distinctUntilChanged { old, new -> old?.id == new?.id && old?.thumbnailUrl == new?.thumbnailUrl }
                .collectLatest { song ->
                    if (song?.thumbnailUrl != null) {
                        val cached = themeColorCache[song.thumbnailUrl]
                        if (cached != null) {
                            withFrameNanos { }
                            themeColor = cached
                            return@collectLatest
                        }
                        withContext(Dispatchers.IO) {
                            try {
                                val result =
                                    imageLoader.execute(
                                        ImageRequest
                                            .Builder(this@MainActivity)
                                            .data(song.thumbnailUrl)
                                            .allowHardware(false)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .networkCachePolicy(CachePolicy.ENABLED)
                                            .crossfade(false)
                                            .build(),
                                    )
                                // a cover that failed to load is tried again next time instead of pinning the theme colour
                                val extractedColor = result.image?.toBitmap()?.extractThemeColor()
                                if (extractedColor != null) themeColorCache[song.thumbnailUrl] = extractedColor
                                withFrameNanos { }
                                themeColor = extractedColor ?: selectedThemeColor
                            } catch (e: Exception) {
                                withFrameNanos { }
                                themeColor = selectedThemeColor
                            }
                        }
                    } else {
                        themeColor = selectedThemeColor
                    }
                }
        }

        MetrolistTheme(
            darkTheme = useDarkTheme,
            pureBlack = pureBlack,
            themeColor = themeColor,
        ) {
            val currentDensity = LocalDensity.current
            val scaledDensity: Density = remember(currentDensity, userDensityScale) {
                Density(
                    density = currentDensity.density * userDensityScale,
                    fontScale = currentDensity.fontScale,
                )
            }

            CompositionLocalProvider(LocalDensity provides scaledDensity) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface),
            ) {
                val density = LocalDensity.current
                val configuration = LocalWindowInfo.current
                val windowsInsets = WindowInsets.systemBars
                val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }

                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    val lastSeenVersion = dataStore.data.first()[LastSeenVersionKey] ?: ""
                    val currentVersion = BuildConfig.BASE_VERSION_NAME
                    if (lastSeenVersion != currentVersion) {
                        showChangelog.value = true
                    }
                }

                val homeViewModel: HomeViewModel = hiltViewModel()
                val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
                val navigationItems =
                    remember(listenTogetherInTopBar) {
                        if (listenTogetherInTopBar) {
                            Screens.MainScreens.filter { it != Screens.ListenTogether }
                        } else {
                            Screens.MainScreens
                        }
                    }
                val routeIndexMap = remember(navigationItems) {
                    navigationItems.mapIndexed { i, s -> s.route to i }.toMap()
                }
                val (defaultOpenTabInt) = rememberPreference(DefaultOpenTabKey, defaultValue = NavigationTab.HOME.name)
                val defaultOpenTab = remember(defaultOpenTabInt) {
                    try {
                        NavigationTab.valueOf(defaultOpenTabInt)
                    } catch (_: IllegalArgumentException) {
                        NavigationTab.HOME
                    }
                }
                val tabOpenedFromShortcut =
                    remember {
                        when (intent?.action) {
                            ACTION_SEARCH -> NavigationTab.SEARCH
                            ACTION_LIBRARY -> NavigationTab.LIBRARY
                            else -> null
                        }
                    }

                val topLevelScreens =
                    remember {
                        listOf(
                            Screens.Home.route,
                            Screens.Library.route,
                            Screens.ListenTogether.route,
                            "settings",
                        )
                    }

                val (query, onQueryChange) =
                    rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        mutableStateOf(TextFieldValue())
                    }

                val onSearch: (String) -> Unit =
                    remember {
                        { searchQuery ->
                            if (searchQuery.isNotEmpty()) {
                                navController.navigate(SearchRoutes.resultRoute(searchQuery))

                                if (dataStore[PauseSearchHistoryKey] != true) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        runCatching {
                                            database.insert(SearchHistory(query = searchQuery))
                                        }.onFailure { throwable ->
                                            Timber
                                                .tag("MainActivity")
                                                .w(throwable, "Failed to save search history for query: %s", searchQuery)
                                        }
                                    }
                                }
                            }
                        }
                    }

                val currentRoute by remember {
                    derivedStateOf { navBackStackEntry?.destination?.route }
                }

                val inSearchScreen by remember {
                    derivedStateOf { currentRoute?.startsWith("search/") == true }
                }
                // Leaving recognition that the search bar's mic opened goes home, where the Search hold gets explained.
                var lastRoute by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(currentRoute) {
                    val left = lastRoute?.startsWith("recognition") == true && currentRoute?.startsWith("recognition") != true
                    lastRoute = currentRoute
                    if (left && earnSearchHoldHint(this@MainActivity)) {
                        navController.navigate(Screens.Home.route) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
                val navigationItemRoutes =
                    remember(navigationItems) {
                        navigationItems.map { it.route }.toSet()
                    }

                val shouldShowNavigationBar =
                    remember(currentRoute, navigationItemRoutes) {
                        // Everywhere except the screens that are tools of their own, like settings.
                        val route = currentRoute
                        route == null ||
                            navigationItemRoutes.contains(route) ||
                            NoNavigationBarRoutes.none { route.startsWith(it) }
                    }

                val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height
                val isTablet = configuration.containerDpSize.width >= 600.dp

                val showRail = (isLandscape || isTablet) && !inSearchScreen

                val navPadding =
                    if (shouldShowNavigationBar && !showRail) {
                        CapsuleNavHeight + 8.dp
                    } else {
                        0.dp
                    }

                val playerBottomSheetState =
                    rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        collapsedBound =
                            bottomInset +
                                (if (!showRail && shouldShowNavigationBar) navPadding else 0.dp) +
                                MiniPlayerBottomSpacing +
                                MiniPlayerHeight,
                        expandedBound = maxHeight,
                    )

                val playerReadyState =
                    playerConnection?.service?.isPlayerReady?.collectAsStateWithLifecycle()
                        ?: remember { mutableStateOf(false) }
                val playerReady by playerReadyState
                val activePlayerConnection = if (playerReady) playerConnection else null

                val playerAwareWindowInsets =
                    remember(
                        bottomInset,
                        shouldShowNavigationBar,
                        playerBottomSheetState.isDismissed,
                        showRail,
                    ) {
                        var bottom = bottomInset
                        if (shouldShowNavigationBar && !showRail) {
                            bottom += NavigationBarHeight
                        }
                        if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                    }
                appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    },
                )

                val topAppBarScrollBehavior =
                    appBarScrollBehavior(
                        canScroll = {
                            !inSearchScreen &&
                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        },
                    )

                // Navigation tracking
                LaunchedEffect(navBackStackEntry) {
                    if (inSearchScreen) {
                        val searchQuery =
                            SearchRoutes.decodeQuery(
                                navBackStackEntry?.arguments?.getString("query").orEmpty(),
                            )
                        onQueryChange(
                            TextFieldValue(
                                searchQuery,
                                TextRange(searchQuery.length),
                            ),
                        )
                    } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        onQueryChange(TextFieldValue())
                    }

                    // Reset scroll behavior for main navigation items
                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        if (navigationItems.fastAny { it.route == previousTab }) {
                            topAppBarScrollBehavior.state.resetHeightOffset()
                        }
                    }

                    topAppBarScrollBehavior.state.resetHeightOffset()

                    // Collapse player when navigating to equalizer
                    if (navBackStackEntry?.destination?.route == "equalizer" &&
                        playerBottomSheetState.isExpanded
                    ) {
                        playerBottomSheetState.collapseSoft()
                    }

                    // Track previous tab for animations
                    navController.currentBackStackEntry?.destination?.route?.let {
                        setPreviousTab(it)
                    }
                }

                LaunchedEffect(activePlayerConnection) {
                    val player = runCatching { activePlayerConnection?.player }.getOrNull()
                    if (player?.currentMediaItem == null) {
                        if (!playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.dismiss()
                        }
                        return@LaunchedEffect
                    }

                    if (playerBottomSheetState.isDismissed) {
                        playerBottomSheetState.collapseSoft()
                    }
                }

                DisposableEffect(activePlayerConnection, playerBottomSheetState) {
                    val player = runCatching { activePlayerConnection?.player }.getOrNull()
                        ?: return@DisposableEffect onDispose { }
                    val listener =
                        object : Player.Listener {
                            override fun onMediaItemTransition(
                                mediaItem: MediaItem?,
                                reason: Int,
                            ) {
                                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                    mediaItem != null &&
                                    playerBottomSheetState.isDismissed
                                ) {
                                    playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                    }
                }

                var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(navBackStackEntry, listenTogetherInTopBar) {
                    val currentRoute = navBackStackEntry?.destination?.route
                    val isListenTogetherScreen =
                        currentRoute == Screens.ListenTogether.route ||
                            currentRoute == "listen_together_from_topbar"
                    shouldShowTopBar = currentRoute in topLevelScreens &&
                        currentRoute != "settings" &&
                        !(isListenTogetherScreen && listenTogetherInTopBar)
                }

                val coroutineScope = rememberCoroutineScope()
                var sharedSong: SongItem? by remember {
                    mutableStateOf(null)
                }
                val snackbarHostState = remember { SnackbarHostState() }
                val youTubeSignedOut by com.metrolist.music.arc.SignedOut.youTube.collectAsStateWithLifecycle()
                val driveSignedOut by com.metrolist.music.arc.SignedOut.drive.collectAsStateWithLifecycle()
                LaunchedEffect(youTubeSignedOut) {
                    if (!youTubeSignedOut) return@LaunchedEffect
                    val result = snackbarHostState.showSnackbar(
                        getString(R.string.arc_signed_out_youtube),
                        getString(R.string.arc_sign_in_again),
                        withDismissAction = true,
                        duration = androidx.compose.material3.SnackbarDuration.Indefinite,
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) navController.navigate("login")
                }
                LaunchedEffect(Unit) {
                    com.metrolist.music.arc.ReleaseFilter.changes.collectLatest { change ->
                        val message =
                            when (change) {
                                is com.metrolist.music.arc.ReleaseFilter.Change.ArtistIgnored -> getString(R.string.arc_artist_ignored, change.entry.title)
                                is com.metrolist.music.arc.ReleaseFilter.Change.ReleaseHidden -> getString(R.string.arc_release_hidden, change.entry.title)
                            }
                        val result = snackbarHostState.showSnackbar(message, getString(R.string.arc_triage_undo), duration = androidx.compose.material3.SnackbarDuration.Long)
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) com.metrolist.music.arc.ReleaseFilter.undo(this@MainActivity, change)
                    }
                }
                LaunchedEffect(driveSignedOut) {
                    if (!driveSignedOut) return@LaunchedEffect
                    val result = snackbarHostState.showSnackbar(
                        getString(R.string.arc_signed_out_drive),
                        getString(R.string.arc_sign_in_again),
                        withDismissAction = true,
                        duration = androidx.compose.material3.SnackbarDuration.Indefinite,
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) navController.navigate("settings/drive")
                }
                com.metrolist.music.arc.FirstLookChoice()
                LaunchedEffect(Unit) {
                    if (!com.metrolist.music.arc.DriveMusic.signInDueSoon(this@MainActivity)) return@LaunchedEffect
                    val result = snackbarHostState.showSnackbar(
                        getString(R.string.arc_drive_due_soon),
                        getString(R.string.arc_sign_in_again),
                        withDismissAction = true,
                        duration = androidx.compose.material3.SnackbarDuration.Indefinite,
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) navController.navigate("settings/drive")
                }

                LaunchedEffect(Unit) {
                    if (pendingIntent != null) {
                        handleRecognitionIntent(pendingIntent!!, navController)
                        handleDeepLinkIntent(pendingIntent!!, navController)
                        pendingIntent = null
                    } else {
                        handleRecognitionIntent(intent, navController)
                        handleDeepLinkIntent(intent, navController)
                    }
                }

                DisposableEffect(Unit) {
                    val listener =
                        Consumer<Intent> { intent ->
                            handleRecognitionIntent(intent, navController)
                            handleDeepLinkIntent(intent, navController)
                        }

                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                var showAccountDialog by remember { mutableStateOf(false) }

                val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
                val eventCount by database.eventCount().collectAsStateWithLifecycle(initialValue = 0)
                val showHistoryButton =
                    remember(pauseListenHistory, eventCount) {
                        !(pauseListenHistory && eventCount == 0)
                    }

                val artistNameAliases by ArtistNameAliases.aliases.collectAsStateWithLifecycle()

                CompositionLocalProvider(
                    LocalDatabase provides database,
                    LocalNavController provides navController,
                    LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                    LocalPlayerConnection provides playerConnection,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalDownloadUtil provides downloadUtil,
                    LocalShimmerTheme provides ShimmerTheme,
                    LocalSyncUtils provides syncUtils,
                    LocalListenTogetherManager provides listenTogetherManager,
                    LocalChangelogState provides showChangelog,
                    LocalArtistNameAliases provides artistNameAliases,
                ) {
                    if (showChangelog.value) {
                        ChangelogScreen(onDismiss = { showChangelog.value = false })
                    }

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            AnimatedVisibility(
                                visible = shouldShowTopBar,
                                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                                exit = fadeOut(animationSpec = tween(durationMillis = 200)),
                            ) {
                                // Arc: the route has already changed while this fades out, so it can't depend on the route or the old solid bar flashes in.
                                CapsuleRow(
                                    buttons =
                                        buildList {
                                            if (showHistoryButton) {
                                                add(CapsuleButton(R.drawable.history, { navController.navigate("history") }))
                                            }
                                            add(CapsuleButton(R.drawable.stats, { navController.navigate("stats") }))
                                            if (listenTogetherInTopBar) {
                                                add(CapsuleButton(R.drawable.group_outlined, { navController.navigate("listen_together_from_topbar") }))
                                            }
                                            add(
                                                CapsuleButton(
                                                    onClick = { showAccountDialog = true },
                                                    content = {
                                                        BadgedBox(badge = {
                                                            if (latestVersionName != BuildConfig.BASE_VERSION_NAME) Badge()
                                                        }) {
                                                            if (accountImageUrl != null) {
                                                                AsyncImage(
                                                                    model = accountImageUrl,
                                                                    contentDescription = stringResource(R.string.account),
                                                                    modifier = Modifier.size(24.dp).clip(CircleShape),
                                                                )
                                                            } else {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.account),
                                                                    contentDescription = stringResource(R.string.account),
                                                                    modifier = Modifier.size(24.dp),
                                                                )
                                                            }
                                                        }
                                                    },
                                                ),
                                            )
                                        },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                                            .padding(top = TopBarTopPadding, end = TopBarEndPadding)
                                            .wrapContentWidth(Alignment.End),
                                )
                            }
                        },
                        bottomBar = {
                            val currentBackStackEntry = navController.currentBackStackEntry // reads reactively outside remember

                            var currentTab by remember { mutableStateOf(navigationItems.firstOrNull()?.route) }
                            LaunchedEffect(currentRoute) {
                                navigationItems.firstOrNull { isRouteSelected(currentRoute, it.route, navigationItems) }?.let { currentTab = it.route }
                            }
                            val onNavItemClick: (Screens, Boolean) -> Unit =
                                remember(
                                    navController,
                                    coroutineScope,
                                    topAppBarScrollBehavior,
                                    playerBottomSheetState,
                                    currentBackStackEntry,
                                    currentTab,
                                ) {
                                    { screen: Screens, isSelected: Boolean ->
                                        if (playerBottomSheetState.isExpanded) {
                                            playerBottomSheetState.collapseSoft()
                                        }
                                        if (isSelected) {
                                            val targetEntry =
                                                try {
                                                    val route = navController.currentBackStackEntry?.destination?.route
                                                    if (route == SearchRoutes.ROUTE || route == "search_input") {
                                                        // For search screens, use search_input entry
                                                        navController.getBackStackEntry("search_input")
                                                    } else {
                                                        // For other screens, use current entry
                                                        navController.currentBackStackEntry
                                                    }
                                                } catch (e: Exception) {
                                                    null
                                                }

                                            // Use appropriate key based on screen type
                                            if (screen == Screens.Search) {
                                                val current = targetEntry?.savedStateHandle?.get<Int>("scrollToTopCount") ?: 0
                                                targetEntry?.savedStateHandle?.set("scrollToTopCount", current + 1)
                                            } else {
                                                targetEntry?.savedStateHandle?.set("scrollToTop", true)
                                            }

                                            coroutineScope.launch {
                                                topAppBarScrollBehavior.state.resetHeightOffset()
                                            }
                                        } else if (screen.route == currentTab && navController.popBackStack(screen.route, inclusive = false)) {
                                            coroutineScope.launch { topAppBarScrollBehavior.state.resetHeightOffset() }
                                        } else {
                                            // a tab button lands on that tab's own screen, not on whatever page was last open in it
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }

                            val onSearchLongClick: () -> Unit =
                                remember(navController) {
                                    {
                                        navController.navigate("recognition") {
                                            launchSingleTop = true
                                        }
                                    }
                                }

                            // Pre-calculate values for graphicsLayer to avoid reading state during composition

                            if (!showRail && currentRoute != "wrapped") {
                                Box {
                                    if (activePlayerConnection != null) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack,
                                        )
                                    }

                                    if (shouldShowNavigationBar) {
                                        // Lists scroll on under the floating nav; the page fades in behind it so rows never sit under its icons.
                                        val navGround = MaterialTheme.colorScheme.background
                                        Box(
                                            Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .height(CapsuleNavHeight + bottomInset + 28.dp)
                                                .graphicsLayer { alpha = 1f - playerBottomSheetState.progress.coerceIn(0f, 1f) }
                                                .background(
                                                    // Light where Home's line runs above the icons, solid from the icons down.
                                                    (CapsuleNavHeight + bottomInset + 28.dp).let { height ->
                                                        Brush.verticalGradient(
                                                            0f to navGround.copy(alpha = 0f),
                                                            22.dp / height to navGround.copy(alpha = 0.3f),
                                                            34.dp / height to navGround,
                                                            1f to navGround,
                                                        )
                                                    },
                                                ),
                                        )
                                        CapsuleNavBar(
                                            items = navigationItems,
                                            currentRoute = currentRoute,
                                            onItemClick = onNavItemClick,
                                            onSearchLongClick = onSearchLongClick,
                                            modifier =
                                                Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(start = 8.dp, end = 8.dp, bottom = bottomInset + 8.dp)
                                                    .graphicsLayer {
                                                        val progress = playerBottomSheetState.progress.coerceIn(0f, 1f)
                                                        translationY = (size.height + 60f) * progress
                                                        alpha = 1f - progress
                                                    }
                                                    // After the slide, so it leaves with the bar; before it, it would sit over the open player's buttons.
                                                    .then(if (playerBottomSheetState.isCollapsed) Modifier.swipeUpOpens(playerBottomSheetState) else Modifier),
                                        )
                                    }
                                }
                            } else {
                                if (currentRoute != "wrapped") {
                                    if (activePlayerConnection != null) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack,
                                        )
                                    }
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            val onRailItemClick: (Screens, Boolean) -> Unit =
                                remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState) {
                                    { screen: Screens, isSelected: Boolean ->
                                        if (playerBottomSheetState.isExpanded) {
                                            playerBottomSheetState.collapseSoft()
                                        }

                                        if (isSelected) {
                                            navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                            coroutineScope.launch {
                                                topAppBarScrollBehavior.state.resetHeightOffset()
                                            }
                                        } else {
                                            // a tab button lands on that tab's own screen, not on whatever page was last open in it
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }

                            val onRailSearchLongClick: () -> Unit =
                                remember(navController) {
                                    {
                                        navController.navigate("recognition") {
                                            launchSingleTop = true
                                        }
                                    }
                                }

                            if (showRail && currentRoute != "wrapped") {
                                AppNavigationRail(
                                    navigationItems = navigationItems,
                                    currentRoute = currentRoute,
                                    onItemClick = onRailItemClick,
                                    pureBlack = pureBlack,
                                    onSearchLongClick = onRailSearchLongClick,
                                    onHomeLongHold = { showAccountDialog = true },
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                // NavHost with animations (Material 3 Expressive style)
                                NavHost(
                                    navController = navController,
                                    startDestination =
                                        when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                            NavigationTab.HOME -> Screens.Home
                                            NavigationTab.SEARCH -> Screens.Search
                                            NavigationTab.LIBRARY -> Screens.Library
                                        }.route,
                                    enterTransition = {
                                        val currentRouteIndex = routeIndexMap[targetState.destination.route] ?: -1
                                        val previousRouteIndex = routeIndexMap[initialState.destination.route] ?: -1

                                        if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex) {
                                            slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                        } else {
                                            slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                        }
                                    },
                                    exitTransition = {
                                        val currentRouteIndex = routeIndexMap[initialState.destination.route] ?: -1
                                        val targetRouteIndex = routeIndexMap[targetState.destination.route] ?: -1

                                        if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex) {
                                            slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                        } else {
                                            slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                        }
                                    },
                                    popEnterTransition = {
                                        val currentRouteIndex = routeIndexMap[targetState.destination.route] ?: -1
                                        val previousRouteIndex = routeIndexMap[initialState.destination.route] ?: -1

                                        if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex) {
                                            slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                        } else {
                                            slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                        }
                                    },
                                    popExitTransition = {
                                        val currentRouteIndex = routeIndexMap[initialState.destination.route] ?: -1
                                        val targetRouteIndex = routeIndexMap[targetState.destination.route] ?: -1

                                        if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex) {
                                            slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                        } else {
                                            slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                        }
                                    },
                                    modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                                ) {
                                    navigationBuilder(
                                        navController = navController,
                                        scrollBehavior = topAppBarScrollBehavior,
                                        latestVersionName = latestVersionName,
                                        activity = this@MainActivity,
                                        snackbarHostState = snackbarHostState,
                                    )
                                }
                            }
                        }
                    }

                    BottomSheetMenu(
                        state = LocalMenuState.current,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    BottomSheetPage(
                        state = LocalBottomSheetPageState.current,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    com.metrolist.music.arc.snippet.SnippetRecordingBar(Modifier.align(Alignment.TopCenter))
                    com.metrolist.music.arc.snippet.SnippetEditorHost()
                    com.metrolist.music.arc.SpotifyImportHost()

                    if (showAccountDialog) {
                        AccountSettingsDialog(
                            onDismiss = {
                                showAccountDialog = false
                                homeViewModel.refresh()
                            },
                            latestVersionName = latestVersionName,
                        )
                    }

                    sharedSong?.let { song ->
                        playerConnection?.let {
                            Dialog(
                                onDismissRequest = { sharedSong = null },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                Surface(
                                    modifier = Modifier.padding(24.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = AlertDialogDefaults.containerColor,
                                    tonalElevation = AlertDialogDefaults.TonalElevation,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        YouTubeSongMenu(
                                            song = song,
                                            onDismiss = { sharedSong = null },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!showChangelog.value) {
                        availableUpdate?.let { update ->
                            val dismissUpdate: () -> Unit = {
                                availableUpdate = null
                                lifecycleScope.launch {
                                    safeDataStoreEdit {
                                        it[update.dismissalKey] = update.release.tagName
                                    }
                                }
                            }
                            val updateState by com.metrolist.music.arc.AppUpdate.state.collectAsState()
                            AlertDialog(
                                // Only "Later" remembers the dismissal; tapping outside just hides it until the next check.
                                onDismissRequest = { availableUpdate = null },
                                title = { Text(stringResource(R.string.update_available_title)) },
                                text = {
                                    Column(
                                        modifier =
                                            Modifier
                                                .heightIn(max = 480.dp)
                                                .verticalScroll(rememberScrollState()),
                                    ) {
                                        Text(
                                            text =
                                                stringResource(
                                                    if (update.isKmp) {
                                                        R.string.kmp_upgrade_title
                                                    } else {
                                                        R.string.update_available_message
                                                    },
                                                    update.release.versionName,
                                                ),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        if (update.isKmp) {
                                            Text(
                                                text = stringResource(R.string.kmp_upgrade_warning),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 12.dp),
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.changelog),
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                                        )
                                        com.metrolist.music.arc.AppUpdateProgress(Modifier.padding(bottom = 12.dp))
                                        com.metrolist.music.arc.MarkdownText(
                                            update.release.description.ifBlank { stringResource(R.string.changelog_empty) },
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        enabled = updateState !is com.metrolist.music.arc.AppUpdate.State.Downloading &&
                                            updateState !is com.metrolist.music.arc.AppUpdate.State.Installing,
                                        onClick = {
                                            if (update.isKmp) {
                                                dismissUpdate()
                                                startActivity(Intent(Intent.ACTION_VIEW, update.downloadUrl.toUri()))
                                            } else {
                                                com.metrolist.music.arc.AppUpdate.start(this@MainActivity, update.downloadUrl)
                                            }
                                        },
                                    ) {
                                        Text(
                                            stringResource(
                                                when {
                                                    update.isKmp -> R.string.kmp_upgrade_action
                                                    updateState is com.metrolist.music.arc.AppUpdate.State.Failed -> R.string.try_again
                                                    else -> R.string.update_action
                                                },
                                            ),
                                        )
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = dismissUpdate) {
                                        Text(stringResource(R.string.kmp_upgrade_later))
                                    }
                                },
                            )
                        }
                    }
                }
            }
            }
        }
    }

    /**
     * Handles the ACTION_RECOGNITION intent from recognition results and the launch activity.
     * Always navigates to the recognition screen to show the result.
     */
    private fun handleRecognitionIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        if (intent.action != ACTION_RECOGNITION) return
        val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START_RECOGNITION, false)
        intent.action = null
        intent.removeExtra(EXTRA_AUTO_START_RECOGNITION)
        navController.navigate(if (autoStart) "recognition?autoStart=true" else "recognition") {
            launchSingleTop = true
        }
    }

    private fun handleDeepLinkIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        val sharedText = intent.extras?.getString(Intent.EXTRA_TEXT)
        val uri = intent.data ?: (com.metrolist.music.arc.SpotifyLinks.findIn(sharedText) ?: sharedText)?.toUri() ?: return
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        val coroutineScope = lifecycle.coroutineScope

        if (com.metrolist.music.arc.SpotifyLinks.isSpotify(uri)) {
            openSpotifyLink(uri.toString(), navController)
            return
        }

        val listenCode =
            uri.getQueryParameter("code")
                ?: uri.getQueryParameter("room")
                ?: uri.pathSegments.getOrNull(1)
        val isListenLink = uri.pathSegments.firstOrNull() == "listen" || uri.host?.equals("listen", ignoreCase = true) == true
        if (!listenCode.isNullOrBlank() && isListenLink) {
            val username = dataStore.get(ListenTogetherUsernameKey, "").ifBlank { "Guest" }
            listenTogetherManager.joinRoom(listenCode, username)
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> {
                val playlistId = uri.getQueryParameter("list")
                if (playlistId.isNullOrBlank() || playlistId.equals("null", ignoreCase = true)) {
                    Toast.makeText(this, R.string.playlist_unavailable, Toast.LENGTH_SHORT).show()
                    return
                }

                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube
                            .albumSongs(playlistId)
                            .onSuccess { songs ->
                                songs.firstOrNull()?.album?.id?.let { browseId ->
                                    withContext(Dispatchers.Main) {
                                        navController.navigate("album/$browseId")
                                    }
                                }
                            }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId")
                }
            }

            "browse" -> {
                uri.lastPathSegment?.let { browseId ->
                    navController.navigate("album/$browseId")
                }
            }

            "channel", "c" -> {
                uri.lastPathSegment?.let { artistId ->
                    navController.navigate("artist/$artistId")
                }
            }

            "search" -> {
                uri.getQueryParameter("q")?.let {
                    navController.navigate(SearchRoutes.resultRoute(it))
                }
            }

            else -> {
                val videoId =
                    when {
                        path == "watch" -> uri.getQueryParameter("v")
                        uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                        else -> null
                    }

                val playlistId = uri.getQueryParameter("list")

                if (videoId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube
                            .queue(listOf(videoId), playlistId)
                            .onSuccess { queue ->
                                withContext(Dispatchers.Main) {
                                    playerConnection?.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = queue.firstOrNull()?.id, playlistId = playlistId),
                                            queue.firstOrNull()?.toMediaMetadata(),
                                        ),
                                    )
                                }
                            }.onFailure {
                                reportException(it)
                            }
                    }
                } else if (playlistId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube
                            .queue(null, playlistId)
                            .onSuccess { queue ->
                                val firstItem = queue.firstOrNull()
                                withContext(Dispatchers.Main) {
                                    playerConnection?.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                                            firstItem?.toMediaMetadata(),
                                        ),
                                    )
                                }
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    private fun openSpotifyLink(url: String, navController: NavHostController) {
        Toast.makeText(this, R.string.arc_spotify_finding, Toast.LENGTH_SHORT).show()
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            val found = com.metrolist.music.arc.SpotifyLinks.resolve(url)
            if (found == null) {
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, R.string.arc_spotify_not_found, Toast.LENGTH_LONG).show() }
                return@launch
            }
            val query = listOfNotNull(found.title, found.artist).joinToString(" ")
            when (found.kind) {
                com.metrolist.music.arc.SpotifyLinks.Kind.SONG -> {
                    suspend fun pick(filter: YouTube.SearchFilter) = YouTube.search(query, filter).getOrNull()
                        ?.items?.filterIsInstance<com.metrolist.innertube.models.SongItem>()
                        ?.firstOrNull { com.metrolist.music.arc.SpotifyLinks.matches(found, it.title, it.artists.map { a -> a.name }) }
                    val song = pick(YouTube.SearchFilter.FILTER_SONG) ?: pick(YouTube.SearchFilter.FILTER_VIDEO)
                    withContext(Dispatchers.Main) {
                        if (song != null) {
                            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata()))
                        } else {
                            navController.navigate(SearchRoutes.resultRoute(query))
                        }
                    }
                }
                com.metrolist.music.arc.SpotifyLinks.Kind.ALBUM -> {
                    val album = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                        ?.items?.filterIsInstance<com.metrolist.innertube.models.AlbumItem>()
                        ?.firstOrNull { com.metrolist.music.arc.SpotifyLinks.matches(found, it.title, it.artists.orEmpty().map { a -> a.name }) }
                    withContext(Dispatchers.Main) {
                        if (album != null) navController.navigate("album/${album.browseId}") else navController.navigate(SearchRoutes.resultRoute(query))
                    }
                }
                com.metrolist.music.arc.SpotifyLinks.Kind.PLAYLIST -> withContext(Dispatchers.Main) {
                    com.metrolist.music.arc.SpotifyImport.show(this@MainActivity, found)
                }
                com.metrolist.music.arc.SpotifyLinks.Kind.OTHER -> withContext(Dispatchers.Main) { navController.navigate(SearchRoutes.resultRoute(query)) }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalNavController = staticCompositionLocalOf<NavController> { error("No NavController provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalListenTogetherManager = staticCompositionLocalOf<com.metrolist.music.listentogether.ListenTogetherManager?> { null }
val LocalChangelogState = staticCompositionLocalOf<MutableState<Boolean>> { error("No LocalChangelogState provided") }
val LocalArtistNameAliases = staticCompositionLocalOf<Map<String, String>> { emptyMap() }
val LocalIsPlayerExpanded = compositionLocalOf { false }
