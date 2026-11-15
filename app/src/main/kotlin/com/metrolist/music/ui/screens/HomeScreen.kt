/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalContext
import com.metrolist.music.arc.ArcMotion
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.arc.ARC_HOME_SPEED_DIAL_ONLY
import com.metrolist.music.arc.ArcHomeBackgroundKey
import com.metrolist.music.arc.ArcHomeLineKey
import com.metrolist.music.arc.CapsuleGap
import com.metrolist.music.arc.CapsuleSize
import com.metrolist.music.arc.HomeBackdrop
import com.metrolist.music.arc.ArcPressGlowKey
import com.metrolist.music.arc.LocalPressGlow
import com.metrolist.music.arc.PressGlowLayer
import com.metrolist.music.arc.rememberPressGlow
import com.metrolist.music.arc.HomeBackground
import com.metrolist.music.arc.TopBarEndPadding
import com.metrolist.music.arc.home.HomeEnv
import com.metrolist.music.arc.home.HomeModuleOrder
import com.metrolist.music.arc.home.HomeDayHeader
import com.metrolist.music.arc.home.HomeModules
import com.metrolist.music.arc.home.playLuckyItem
import com.metrolist.music.arc.homeLine
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.ListenTogetherInTopBarKey
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)
    val scope = rememberCoroutineScope()

    val isPlaying = playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata = playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val pinnedSpeedDialItems by viewModel.pinnedSpeedDialItems.collectAsStateWithLifecycle()
    val pinnedTypes = remember { mutableStateOf(emptyMap<String, String>()) }
    pinnedTypes.value = remember(pinnedSpeedDialItems) { pinnedSpeedDialItems.associate { it.id to it.type } }

    val speedDialItems by viewModel.speedDialItems.collectAsStateWithLifecycle()
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    LaunchedEffect(speedDialItems, quickPicks) {
        val likely = speedDialItems.filterIsInstance<SongItem>().map { it.id } + quickPicks.orEmpty().map { it.id }
        if (likely.isNotEmpty()) playerConnection.service.warmStreams(likely, "home")
    }

    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    val allLocalItems by viewModel.allLocalItems.collectAsStateWithLifecycle()
    val allYtItems by viewModel.allYtItems.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()
    val wrappedDismissed by backStackEntry
        ?.savedStateHandle
        ?.getStateFlow("wrapped_seen", false)
        ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    val foundInSettings = stringResource(R.string.found_in_settings_content)
    LaunchedEffect(wrappedDismissed) {
        if (wrappedDismissed) {
            viewModel.markWrappedAsSeen()
            snackbarHostState.showSnackbar(foundInSettings)
            backStackEntry?.savedStateHandle?.set("wrapped_seen", false)
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // more of YouTube's home loads near the end of the list; speed dial is topped up from it too
    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { lastVisibleIndex ->
            val len = lazylistState.layoutInfo.totalItemsCount
            if (lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                viewModel.loadMoreYouTubeItems(homePage?.continuation)
            }
        }
    }

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        indicator = {
            // grows and fades in place under the top bar instead of sliding out from under it
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                    .padding(top = 8.dp)
                    .graphicsLayer {
                        val pulled = pullRefreshState.distanceFraction.coerceIn(0f, 1f)
                        alpha = pulled
                        scaleX = 0.4f + 0.6f * pulled
                        scaleY = scaleX
                    },
            ) {
                if (isRefreshing) {
                    ContainedLoadingIndicator()
                } else {
                    ContainedLoadingIndicator(progress = { pullRefreshState.distanceFraction.coerceIn(0f, 1f) })
                }
            }
        },
    ) {
        val showHomeLine by rememberPreference(ArcHomeLineKey, true)
        val homeBackground by rememberEnumPreference(ArcHomeBackgroundKey, HomeBackground.FIELD)
        val listenTogetherInTopBar by rememberPreference(ListenTogetherInTopBarKey, true)
        val pressGlowOn by rememberPreference(ArcPressGlowKey, true)
        val pressGlow =
            rememberPressGlow(
                enabled = pressGlowOn,
                dark = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                fallback = MaterialTheme.colorScheme.primary,
            )
        HomeBackdrop(homeBackground, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primary)
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (showHomeLine) {
                            Modifier.homeLine(
                                connection = playerConnection,
                                track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                                line = MaterialTheme.colorScheme.primary,
                                // the gap left of Listen Together, or of the account icon when it's hidden
                                uprightFromEnd = TopBarEndPadding + (CapsuleSize + CapsuleGap) * (if (listenTogetherInTopBar) 2 else 1) - CapsuleGap / 2,
                            )
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.TopStart,
        ) {
            val env =
                remember(viewModel, navController, playerConnection, menuState, database, isListenTogetherGuest, autoRadioQueue, maxWidth, maxHeight) {
                    HomeEnv(
                        viewModel = viewModel,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope,
                        database = database,
                        isListenTogetherGuest = isListenTogetherGuest,
                        autoRadioQueue = autoRadioQueue,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        pinnedTypes = pinnedTypes,
                        listState = lazylistState,
                        width = maxWidth,
                        height = maxHeight,
                    )
                }
            val shown = HomeModuleOrder.mapNotNull { id -> key(id) { HomeModules.getValue(id).takeIf { it.available(env) } } }

            CompositionLocalProvider(LocalPressGlow provides pressGlow) {
                LazyColumn(
                    state = lazylistState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(key = "day", contentType = "day") { Column(Modifier.homeLanding(0)) { HomeDayHeader(lazylistState) } }
                    shown.forEachIndexed { index, module ->
                        item(key = module.id.key, contentType = module.id.key) { Column(Modifier.homeLanding(index + 1)) { module.content(env) } }
                    }
                }
            }
            PressGlowLayer(pressGlow)

            HideOnScrollFAB(
                visible = !ARC_HOME_SPEED_DIAL_ONLY && (allLocalItems.isNotEmpty() || allYtItems.isNotEmpty()),
                lazyListState = lazylistState,
                icon = R.drawable.shuffle,
                onClick = { env.playLuckyItem(allLocalItems, allYtItems) },
                onRecognitionClick = { navController.navigate("recognition") },
            )
        }
    }
}

private var homeFirstShown = 0L

/** On a cold start Home's parts land one after another; coming back to Home later shows them in place. */
@Composable
private fun Modifier.homeLanding(index: Int): Modifier {
    val context = LocalContext.current
    val land =
        remember {
            if (homeFirstShown == 0L) homeFirstShown = SystemClock.uptimeMillis()
            SystemClock.uptimeMillis() - homeFirstShown < 800 &&
                context.getSystemService(PowerManager::class.java)?.isPowerSaveMode != true &&
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }
    val progress = remember { Animatable(if (land) 0f else 1f) }
    if (land) {
        LaunchedEffect(Unit) {
            delay(40L * index)
            progress.animateTo(1f, ArcMotion.landing())
        }
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 40.dp.toPx()
    }
}
