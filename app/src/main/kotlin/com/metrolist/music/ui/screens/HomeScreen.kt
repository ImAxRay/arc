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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.height
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
import com.metrolist.music.arc.ArcHomeBackgroundKey
import com.metrolist.music.arc.ArcHomeLineKey
import com.metrolist.music.arc.CapsuleGap
import com.metrolist.music.arc.CapsuleSize
import com.metrolist.music.arc.HomeBackdrop
import com.metrolist.music.arc.ArcPressGlowKey
import com.metrolist.music.arc.LocalMarqueeTouches
import com.metrolist.music.arc.LocalPressGlow
import com.metrolist.music.arc.PressGlowLayer
import com.metrolist.music.arc.rememberPressGlow
import com.metrolist.music.arc.HomeBackground
import com.metrolist.music.arc.TopBarEndPadding
import com.metrolist.music.arc.home.HomeEnv
import com.metrolist.music.arc.home.HomeDayHeader
import com.metrolist.music.arc.home.HomeModules
import com.metrolist.music.arc.home.ArcHomeModulesKey
import com.metrolist.music.arc.home.PresetSheet
import com.metrolist.music.arc.home.EditFrame
import com.metrolist.music.arc.home.EditHomeRow
import com.metrolist.music.arc.home.EmptyModuleStub
import com.metrolist.music.arc.home.HomeEdit
import com.metrolist.music.arc.home.DialEdit
import com.metrolist.music.arc.home.DialStore
import com.metrolist.music.arc.home.undoDial
import com.metrolist.music.arc.home.LocalDialEdit
import com.metrolist.music.arc.LocalTiltEye
import com.metrolist.music.arc.rememberTiltEye
import com.metrolist.music.arc.home.HomeModuleId
import com.metrolist.music.arc.home.HomeSlot
import com.metrolist.music.arc.home.LocalHomeEdit
import com.metrolist.music.arc.home.ModuleSettingsSheet
import com.metrolist.music.arc.home.encode
import com.metrolist.music.arc.home.parseHomeSlots
import com.metrolist.music.arc.home.YT_ROW
import com.metrolist.music.arc.home.LocalHomeEditing
import com.metrolist.music.arc.home.LocalModuleKey
import com.metrolist.music.arc.home.LocalUntitled
import com.metrolist.music.arc.home.ArcHomeUntitledKey
import com.metrolist.music.arc.home.ModulePicker
import com.metrolist.music.arc.home.youTubeRowModules
import com.metrolist.music.arc.home.rememberWobbleClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.FilledTonalButton
import com.metrolist.music.arc.home.SwipeSnackbarHost
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.metrolist.music.arc.homeLine
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.ListenTogetherInTopBarKey
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
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    val context = LocalContext.current
    var savedModules by rememberPreference(ArcHomeModulesKey, "")
    // YouTube's own rows by their stored key; each is a module of its own
    val youTubeRows = remember(homePage) { youTubeRowModules(homePage) }
    var slots by remember { mutableStateOf(parseHomeSlots(savedModules, youTubeRows.keys)) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val untitled by rememberPreference(ArcHomeUntitledKey, emptySet())
    BackHandler(editing) { editing = false }
    val wobble = rememberWobbleClock(editing)
    var dialEditing by rememberSaveable { mutableStateOf(false) }
    BackHandler(dialEditing) { dialEditing = false }
    val dialWobble = rememberWobbleClock(dialEditing)
    val dialEdit = remember(dialWobble) { DialEdit(dialWobble) }
    // undo reaches back only to when dial edit opened
    LaunchedEffect(dialEditing) { if (!dialEditing) dialEdit.steps.clear() }
    // one sensor for every cover on Home
    val tiltEye = rememberTiltEye()
    val homeEdit =
        remember {
            HomeEdit {
                if (!editing) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // one edit at a time, so only one Done shows
                    dialEditing = false
                    editing = true
                }
            }
        }

    fun save(new: List<HomeSlot>) {
        slots = new
        savedModules = new.encode()
    }

    val lazylistState = rememberLazyListState()
    var moved by remember { mutableStateOf(false) }
    val reorder =
        rememberReorderableLazyListState(lazylistState, scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) { from, to ->
            val f = slots.indexOfFirst { it.key == from.key }
            val t = slots.indexOfFirst { it.key == to.key }
            if (f >= 0 && t >= 0) {
                slots = slots.toMutableList().apply { add(t, removeAt(f)) }
                moved = true
            }
        }
    // a drag only reorders the screen's copy; it's written once the module is let go
    LaunchedEffect(reorder.isAnyItemDragging) {
        if (!reorder.isAnyItemDragging && moved) {
            moved = false
            save(slots)
        }
    }
    LaunchedEffect(savedModules, youTubeRows.keys) { if (!reorder.isAnyItemDragging) slots = parseHomeSlots(savedModules, youTubeRows.keys) }
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
        val marqueeTouches = remember { mutableIntStateOf(0) }
        HomeBackdrop(homeBackground, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primary)
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Names on Home stay still until a touch, so a resting Home draws nothing for them.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            marqueeTouches.intValue++
                        }
                    }.then(
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
            val wrapped = key(HomeModuleId.WRAPPED) { HomeModules.getValue(HomeModuleId.WRAPPED.key).takeIf { !editing && it.available(env) } }

            // a saved YouTube row missing from today's page has no module and stays out of sight, but keeps its place
            fun moduleFor(slot: HomeSlot) = HomeModules[slot.key] ?: youTubeRows[slot.key]
            val shown =
                slots.filter { it.shown }.mapNotNull(::moduleFor).mapNotNull { module ->
                    key(module.key) {
                        val available = module.available(env)
                        if (editing || available) module to available else null
                    }
                }
            val undo = stringResource(R.string.undo)
            // the app's own snackbar sits above the player sheet, off screen here, so Undo gets its own spot over Done
            val editSnackbar = remember { SnackbarHostState() }

            fun offerUndo(message: String, restore: () -> Unit) {
                editSnackbar.currentSnackbarData?.dismiss()
                scope.launch { if (editSnackbar.showSnackbar(message, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) restore() }
            }

            fun hide(module: com.metrolist.music.arc.home.HomeModule) {
                // a hidden YouTube row isn't saved, so Undo puts it back by position rather than by flipping it
                val at = slots.indexOfFirst { it.key == module.key }
                save(slots.map { if (it.key == module.key) it.copy(shown = false) else it })
                offerUndo(context.getString(R.string.arc_home_module_hidden, module.name(context))) {
                    save(slots.filterNot { it.key == module.key }.toMutableList().apply { add(at.coerceIn(0, size), HomeSlot(module.key, true)) })
                }
            }

            fun applyPreset(name: String, modules: String) {
                menuState.dismiss()
                val before = savedModules
                slots = parseHomeSlots(modules, youTubeRows.keys)
                savedModules = modules
                offerUndo(context.getString(R.string.arc_home_preset_applied, name)) { savedModules = before }
            }

            CompositionLocalProvider(LocalPressGlow provides pressGlow, LocalMarqueeTouches provides marqueeTouches, LocalHomeEdit provides homeEdit, LocalHomeEditing provides editing, LocalUntitled provides untitled, LocalDialEdit provides dialEdit.takeIf { dialEditing }, LocalTiltEye provides tiltEye) {
                LazyColumn(
                    state = lazylistState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(key = "day", contentType = "day") {
                        Column(Modifier.homeLanding(0).pointerInput(Unit) { detectTapGestures(onLongPress = { homeEdit.enter() }) }) { HomeDayHeader(lazylistState) }
                    }
                    wrapped?.let { module -> item(key = module.key, contentType = module.key) { module.content(env) } }
                    shown.forEachIndexed { index, (module, available) ->
                        item(key = module.key, contentType = if (module.key.startsWith(YT_ROW)) YT_ROW else module.key) {
                            ReorderableItem(reorder, key = module.key, enabled = editing) { dragging ->
                                Column(Modifier.homeLanding(index + 1)) { CompositionLocalProvider(LocalModuleKey provides module.key) {
                                    if (editing) {
                                        EditFrame(
                                            module = module,
                                            index = index,
                                            clock = wobble,
                                            dragging = dragging,
                                            cover = Modifier.longPressDraggableHandle(onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }),
                                            onHide = { hide(module) },
                                            onSettings = { menuState.show { ModuleSettingsSheet(module) } },
                                            onEditInside =
                                                if (module.key == HomeModuleId.SPEED_DIAL.key) {
                                                    {
                                                        editing = false
                                                        dialEditing = true
                                                    }
                                                } else {
                                                    null
                                                },
                                        ) { if (available) module.content(env) else EmptyModuleStub(module) }
                                    } else {
                                        module.content(env)
                                    }
                                } }
                            }
                        }
                    }
                    if (editing) {
                        // room for Reset, Add and Done, which float over the bottom
                        item(key = "edit_buttons", contentType = "edit_buttons") { Spacer(Modifier.height(72.dp)) }
                    } else {
                        item(key = "edit", contentType = "edit") { EditHomeRow(homeEdit.enter) }
                    }
                }
            }
            PressGlowLayer(pressGlow)

            if (editing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                            .padding(bottom = 12.dp),
                ) {
                    FilledTonalButton(onClick = { menuState.show { PresetSheet(slots.encode(), youTubeRows.keys, ::applyPreset) } }) {
                        Text(stringResource(R.string.arc_home_presets))
                    }
                    FilledTonalButton(
                        onClick = {
                            menuState.show {
                                ModulePicker(
                                    env = env,
                                    // a fixed order, so adding one doesn't shuffle the cards under the finger
                                    modules = (HomeModules.values + youTubeRows.values).filter { it.key != HomeModuleId.WRAPPED.key },
                                    shown = { slots.filter { it.shown }.mapTo(HashSet()) { it.key } },
                                    onAdd = { key ->
                                        save(slots.filterNot { it.key == key } + HomeSlot(key, true))
                                        scope.launch { lazylistState.animateScrollToItem(lazylistState.layoutInfo.totalItemsCount - 1) }
                                    },
                                )
                            }
                        },
                    ) {
                        Icon(painterResource(R.drawable.add), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.arc_home_add), modifier = Modifier.padding(start = 6.dp))
                    }
                    Button(onClick = { editing = false }) { Text(stringResource(R.string.arc_home_done)) }
                }
            }
            if (dialEditing) {
                val dialStore = remember { DialStore.get(context) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                            .padding(bottom = 12.dp),
                ) {
                    FilledTonalButton(
                        enabled = dialEdit.steps.isNotEmpty(),
                        onClick = { undoDial(listOf(dialEdit.steps.removeAt(dialEdit.steps.lastIndex)), dialStore, database, scope) },
                    ) {
                        Icon(painterResource(R.drawable.undo), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.undo), modifier = Modifier.padding(start = 6.dp))
                    }
                    FilledTonalButton(
                        enabled = dialEdit.steps.isNotEmpty(),
                        onClick = {
                            undoDial(dialEdit.steps.toList(), dialStore, database, scope)
                            dialEdit.steps.clear()
                            dialEditing = false
                        },
                    ) { Text(stringResource(R.string.arc_dial_discard)) }
                    Button(onClick = { dialEditing = false }) { Text(stringResource(R.string.arc_home_done)) }
                }
            }
            SwipeSnackbarHost(
                editSnackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                    .padding(bottom = 64.dp),
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
