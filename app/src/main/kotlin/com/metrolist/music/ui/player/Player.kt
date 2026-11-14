/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import com.metrolist.music.arc.ArcSlider
import com.metrolist.music.arc.ArcAudioDetailsKey
import com.metrolist.music.arc.rememberAudioDetails
import com.metrolist.music.arc.rememberOutputIcon
import com.metrolist.music.arc.showOutputSwitcher
import androidx.compose.ui.graphics.luminance
import com.metrolist.music.arc.StatusLine
import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import com.metrolist.music.LocalNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CropAlbumArtKey
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.HidePlayerThumbnailKey
import com.metrolist.music.constants.HideStatusBarOnFullscreenKey
import com.metrolist.music.constants.KeepScreenOn
import com.metrolist.music.constants.CrossfadeEnabledKey
import com.metrolist.music.constants.CrossfadeDurationKey
import com.metrolist.music.constants.CrossfadeGaplessKey
import com.metrolist.music.constants.PlayerBackgroundStyle
import com.metrolist.music.constants.PlayerBackgroundStyleKey
import com.metrolist.music.constants.PlayerButtonsStyle
import com.metrolist.music.constants.PlayerButtonsStyleKey
import com.metrolist.music.arc.ArcPanelLeftKey
import com.metrolist.music.arc.CapsuleButton
import com.metrolist.music.arc.ArcPlayerLayoutKey
import com.metrolist.music.arc.ArcLineSignals
import com.metrolist.music.arc.VolumeKnobOverlay
import com.metrolist.music.arc.VolumeKnobState
import com.metrolist.music.arc.twistVolume
import com.metrolist.music.arc.PlayHoldAction
import com.metrolist.music.arc.PlayHoldActionKey
import com.metrolist.music.arc.PlayHoldPicker
import com.metrolist.music.arc.rememberPlayHold
import com.metrolist.music.arc.ArcLabels
import com.metrolist.music.arc.NextUp
import com.metrolist.music.arc.ArcTimeLine
import com.metrolist.music.arc.CornerBoxHeight
import com.metrolist.music.arc.ThumbArcControls
import com.metrolist.music.arc.pressBounce
import androidx.compose.material3.IconButton
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.metrolist.music.ui.theme.LocalThemeColor
import com.metrolist.music.ui.theme.MetrolistTheme
import com.metrolist.music.arc.coverGround
import com.metrolist.music.arc.rememberCoverArt
import com.metrolist.music.arc.CornerExtras
import com.metrolist.music.arc.playerExtras
import com.metrolist.music.arc.animatedPanelSide
import com.metrolist.music.arc.CapsuleRow
import com.metrolist.music.arc.CapsuleSize
import com.metrolist.music.arc.swipeUpOpens
import com.metrolist.music.arc.likePop
import com.metrolist.music.arc.vinyl.TurntableWindow
import com.metrolist.music.arc.vinyl.rememberPhoneLyingFlat
import com.metrolist.music.ui.menu.PlayerMenu
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.arc.rememberEndDot
import com.metrolist.music.arc.songHandoverLeadMs
import com.metrolist.music.arc.tapBounce
import com.metrolist.music.arc.vinyl.ArcVinylPlayerDefault
import com.metrolist.music.arc.vinyl.ArcVinylPlayerKey
import com.metrolist.music.arc.vinyl.VinylPlayer
import com.metrolist.music.arc.vinyl.VinylPlayerToggle
import com.metrolist.music.arc.vinyl.vinylSwitchTransition
import com.metrolist.music.constants.PlayerHorizontalPadding
import com.metrolist.music.constants.QueuePeekHeight
import com.metrolist.music.constants.SleepTimerDefaultKey
import com.metrolist.music.constants.SleepTimerFadeOutKey
import com.metrolist.music.constants.SleepTimerStopAfterCurrentSongKey
import com.metrolist.music.constants.SliderStyle
import com.metrolist.music.constants.SliderStyleKey
import com.metrolist.music.constants.SquigglySliderKey
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.constants.UseNewPlayerDesignKey
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.extensions.toggleRepeatMode
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.BottomSheet
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.Lyrics
import com.metrolist.music.ui.component.PlayerSliderTrack
import com.metrolist.music.ui.component.ResizableIconButton
import com.metrolist.music.ui.component.SquigglySlider
import com.metrolist.music.ui.component.WavySlider
import com.metrolist.music.ui.component.rememberBottomSheetState
import com.metrolist.music.ui.menu.PlayerMenu
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.theme.PlayerColorExtractor
import com.metrolist.music.ui.theme.PlayerSliderColors
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.ui.utils.ShowOffsetDialog
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import com.metrolist.music.ui.component.Icon as MIcon
import com.metrolist.music.constants.SleepTimerDefaultKey
import com.metrolist.music.constants.SleepTimerFadeOutKey
import com.metrolist.music.constants.SleepTimerStopAfterCurrentSongKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val systemDark = isSystemInDarkTheme()
    val darkMode by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val appDark = if (darkMode == DarkMode.AUTO) systemDark else darkMode == DarkMode.ON
    val appScheme = MaterialTheme.colorScheme
    val player = @Composable {
        CompositionLocalProvider(LocalAppColorScheme provides appScheme) {
            PlayerSheet(state, navController, modifier, pureBlack, appDark)
        }
    }
    // The open player is always dark, in the cover's colours, whatever the app's theme.
    if (appDark) player() else MetrolistTheme(darkTheme = true, themeColor = LocalThemeColor.current, content = player)
}

/** The app's own colours, for the mini player, which stays in the app's theme while the open player is always dark. */
private val LocalAppColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheet(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier,
    pureBlack: Boolean,
    appDark: Boolean,
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val menuState = LocalMenuState.current
    val sleepTimerDefaultSetTemplate = stringResource(R.string.sleep_timer_default_set)
    val copiedTitleStr = stringResource(R.string.copied_title)
    val copiedArtistStr = stringResource(R.string.copied_artist)
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val hidePlayerThumbnail = false
    val cropAlbumArt = false

    var showInlineLyrics by rememberSaveable {
        mutableStateOf(false)
    }

    var isFullScreen by rememberSaveable {
        mutableStateOf(false)
    }

    val playerBackground = PlayerBackgroundStyle.DEFAULT
    val playerButtonsStyle = PlayerButtonsStyle.DEFAULT

    val useDarkTheme = true

    val shouldUseDarkButtonColors =
        remember(playerBackground, useDarkTheme) {
            when (playerBackground) {
                PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> true
                PlayerBackgroundStyle.DEFAULT -> useDarkTheme
            }
        }

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val isKeepScreenOn by rememberPreference(KeepScreenOn, false)
    // Arc: the player is the only place that holds the screen on; the lyrics view used to clear it whenever it was rebuilt.
    // a video in the cover spot is watched like the full-screen one, so it holds the screen on too
    val videoShown = com.metrolist.music.arc.VideoState.current.value != null && com.metrolist.music.arc.VideoState.view.value == com.metrolist.music.arc.VideoView.VIDEO
    val keepScreenOn = isPlaying && (isKeepScreenOn || showInlineLyrics || videoShown)

    // Holding the main control opens the lyrics menu in lyrics mode. The song list opens by swiping up.
    // 0 is the thumb arc, 1 the small corner arc. A stored 2 is the removed cluster layout.
    val (storedLayout, onPlayerLayoutChange) = rememberPreference(ArcPlayerLayoutKey, 0)
    val playerLayout = storedLayout.coerceIn(0, 1)
    val cornerArc = playerLayout == 1
    // Lyrics fill the screen, so they keep the compact corner controls whatever the layout.
    val thumbArcMode = !showInlineLyrics && !cornerArc
    // Both layouts show the cover edge to edge under the status bar, with its glow below.
    // A sideways swipe below the cover nudges the cover and its glow along with the finger.
    val coverSwipeShift = remember { Animatable(0f) }
    val bareCover = !showInlineLyrics
    // The cover keeps its square box until it has faded out for the lyrics, so it never shows centred in the taller lyrics box.
    var coverSquare by remember { mutableStateOf(bareCover) }
    LaunchedEffect(bareCover) {
        if (!bareCover) delay(LYRICS_FADE_MS.toLong())
        coverSquare = bareCover
    }
    // The line bends around the corner controls, with the title beside it, in lyrics mode and in the corner layout.
    val lyricsLine = (showInlineLyrics || cornerArc) && !isFullScreen

    DisposableEffect(playerBackground, state.isExpanded, appDark, keepScreenOn, isFullScreen, bareCover) {
        val window = (context as? android.app.Activity)?.window
        if (window != null && state.isExpanded) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            when (playerBackground) {
                PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> {
                    insetsController.isAppearanceLightStatusBars = false
                }

                PlayerBackgroundStyle.DEFAULT -> {
                    insetsController.isAppearanceLightStatusBars = !useDarkTheme
                }
            }

            // The arc player's cover runs under the status bar, so the player draws its own time and battery instead.
            if (bareCover) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }

            if (keepScreenOn && state.isExpanded) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !appDark
                insetsController.show(WindowInsetsCompat.Type.statusBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    BackHandler(enabled = state.isExpanded) {
        state.collapseSoft()
    }

    val onBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onSurface
        }
    val useBlackBackground = pureBlack

    val playbackState by playerConnection.playbackState.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val automix by playerConnection.service.automixItems.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val isMuted by playerConnection.isMuted.collectAsStateWithLifecycle()

    val sliderStyle = SliderStyle.DEFAULT
    val squigglySlider = false

    // Listen Together state (reactive)
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsStateWithLifecycle(initialValue = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST

    // Cast state - safely access castConnectionHandler to prevent crashes during service lifecycle changes
    val castHandler =
        remember(playerConnection) {
            try {
                playerConnection.service.castConnectionHandler
            } catch (e: Exception) {
                null
            }
        }
    val isCasting by castHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val castPosition by castHandler?.castPosition?.collectAsStateWithLifecycle() ?: remember { mutableLongStateOf(0L) }
    val castDuration by castHandler?.castDuration?.collectAsStateWithLifecycle() ?: remember { mutableLongStateOf(0L) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsState() ?: remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) {
            delay(100)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore if focus request fails
            }
        }
    }

    // Use Cast state when casting, otherwise local player
    val effectiveIsPlaying = if (isCasting) castIsPlaying else isPlaying

    // Use State objects for position/duration to pass to MiniPlayer without causing recomposition
    // These states persist across playback state changes to ensure continuous progress updates.
    // Seed from the player's current values so re-entering composition on resume shows the real
    // time immediately instead of flashing 0:00 until the first poll fires. runCatching guards the
    // player-not-ready race; the poll loop corrects duration if it isn't known yet.
    val positionState = remember { mutableLongStateOf(runCatching { playerConnection.player.currentPosition }.getOrDefault(0L)) }
    val durationState = remember {
        mutableLongStateOf(
            (mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L))
                ?: runCatching { playerConnection.player.duration }.getOrDefault(0L).coerceAtLeast(0L),
        )
    }

    // Convenience accessors for local use
    var position by positionState
    var duration by durationState

    val effectivePosition by remember {
        derivedStateOf {
            if (isCasting) {
                castPosition
            } else {
                position
            }
        }
    }

    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }
    // Track when we last manually set position to avoid Cast overwriting it
    var lastManualSeekTime by remember { mutableLongStateOf(0L) }

    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    val gradientColorsCache = remember { mutableMapOf<String, List<Color>>() }

    if (!canSkipNext && automix.isNotEmpty()) {
        playerConnection.service.addToQueueAutomix(automix[0], 0)
    }

    val defaultGradientColors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()

    LaunchedEffect(mediaMetadata?.id, playerBackground) {
        if (playerBackground == PlayerBackgroundStyle.GRADIENT) {
            val currentMetadata = mediaMetadata
            if (currentMetadata != null && currentMetadata.thumbnailUrl != null) {
                val cachedColors = gradientColorsCache[currentMetadata.id]
                if (cachedColors != null) {
                    gradientColors = cachedColors
                    return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(currentMetadata.thumbnailUrl)
                            .size(100, 100)
                            .allowHardware(false)
                            .memoryCacheKey("gradient_${currentMetadata.id}")
                            .build()

                    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
                    val loaded = result?.image?.toBitmap()
                    if (loaded == null) withContext(Dispatchers.Main) { gradientColors = emptyList() }
                    if (result != null) {
                        val bitmap = loaded
                        if (bitmap != null) {
                            val palette =
                                withContext(Dispatchers.Default) {
                                    Palette
                                        .from(bitmap)
                                        .maximumColorCount(8)
                                        .resizeBitmapArea(100 * 100)
                                        .generate()
                                }
                            val extractedColors =
                                PlayerColorExtractor.extractGradientColors(
                                    palette = palette,
                                    fallbackColor = fallbackColor,
                                )
                            gradientColorsCache[currentMetadata.id] = extractedColors
                            withContext(Dispatchers.Main) { gradientColors = extractedColors }
                        }
                    }
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val TextBackgroundColor by animateColorAsState(
        targetValue =
            when (playerBackground) {
                PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
                PlayerBackgroundStyle.BLUR -> Color.White
                PlayerBackgroundStyle.GRADIENT -> Color.White
            },
        label = "TextBackgroundColor",
    )

    val icBackgroundColor by animateColorAsState(
        targetValue =
            when (playerBackground) {
                PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
                PlayerBackgroundStyle.BLUR -> Color.Black
                PlayerBackgroundStyle.GRADIENT -> Color.Black
            },
        label = "icBackgroundColor",
    )

    val (textButtonColor, iconButtonColor) =
        when {
            playerBackground == PlayerBackgroundStyle.BLUR ||
                playerBackground == PlayerBackgroundStyle.GRADIENT -> {
                when (playerButtonsStyle) {
                    PlayerButtonsStyle.DEFAULT -> {
                        Pair(Color.White, Color.Black)
                    }

                    PlayerButtonsStyle.PRIMARY -> {
                        Pair(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.onPrimary,
                        )
                    }

                    PlayerButtonsStyle.TERTIARY -> {
                        Pair(
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
            }

            else -> {
                when (playerButtonsStyle) {
                    PlayerButtonsStyle.DEFAULT -> {
                        if (useDarkTheme) {
                            Pair(Color.White, Color.Black)
                        } else {
                            Pair(Color.Black, Color.White)
                        }
                    }

                    PlayerButtonsStyle.PRIMARY -> {
                        Pair(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.onPrimary,
                        )
                    }

                    PlayerButtonsStyle.TERTIARY -> {
                        Pair(
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
            }
        }

    // Separate colors for Previous/Next buttons in PRIMARY/TERTIARY modes
    val (sideButtonContainerColor, sideButtonContentColor) =
        when {
            playerBackground == PlayerBackgroundStyle.BLUR ||
                playerBackground == PlayerBackgroundStyle.GRADIENT -> {
                when (playerButtonsStyle) {
                    PlayerButtonsStyle.DEFAULT -> {
                        Pair(
                            Color.White.copy(alpha = 0.2f),
                            Color.White,
                        )
                    }

                    PlayerButtonsStyle.PRIMARY -> {
                        Pair(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    PlayerButtonsStyle.TERTIARY -> {
                        Pair(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            else -> {
                when (playerButtonsStyle) {
                    PlayerButtonsStyle.DEFAULT -> {
                        Pair(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    PlayerButtonsStyle.PRIMARY -> {
                        Pair(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    PlayerButtonsStyle.TERTIARY -> {
                        Pair(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }

    val download by LocalDownloadUtil.current
        .getDownload(mediaMetadata?.id ?: "")
        .collectAsStateWithLifecycle(initialValue = null)

    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer?.triggerTime,
            playerConnection.service.sleepTimer?.pauseWhenSongEnd,
        ) {
            playerConnection.service.sleepTimer?.isActive ?: false
        }

    var sleepTimerTimeLeft by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer?.pauseWhenSongEnd == true) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        (playerConnection.service.sleepTimer?.triggerTime ?: 0L) - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    val scope = rememberCoroutineScope()
    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    val sleepTimerDefault by rememberPreference(SleepTimerDefaultKey, 30f)
    var sleepTimerValue by remember { mutableFloatStateOf(sleepTimerDefault) }
    val isAtDefault by remember {
        derivedStateOf { sleepTimerValue.roundToInt() == sleepTimerDefault.roundToInt() }
    }
    LaunchedEffect(sleepTimerDefault) { sleepTimerValue = sleepTimerDefault }
    val sleepTimerStopAfterCurrentSong by rememberPreference(SleepTimerStopAfterCurrentSongKey, false)
    val sleepTimerFadeOut by rememberPreference(SleepTimerFadeOutKey, false)

    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer?.start(
                            minute = sleepTimerValue.roundToInt(),
                            stopAfterCurrentSong = sleepTimerStopAfterCurrentSong,
                            fadeOut = sleepTimerFadeOut,
                        )
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.minute,
                                sleepTimerValue.roundToInt(),
                                sleepTimerValue.roundToInt(),
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    ArcSlider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (isAtDefault) {
                            FilledIconButton(
                                onClick = {
                                    scope.launch {
                                        context.safeDataStoreEdit { settings ->
                                            settings[SleepTimerDefaultKey] = sleepTimerValue
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        String.format(sleepTimerDefaultSetTemplate, sleepTimerValue.roundToInt()),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text(stringResource(R.string.set_as_default))
                            }
                        } else {
                            OutlinedIconButton(
                                onClick = {
                                    scope.launch {
                                        context.safeDataStoreEdit { settings ->
                                            settings[SleepTimerDefaultKey] = sleepTimerValue
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        String.format(sleepTimerDefaultSetTemplate, sleepTimerValue.roundToInt()),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ) {
                                Text(stringResource(R.string.set_as_default))
                            }
                        }

                        OutlinedIconButton(
                            onClick = {
                                showSleepTimerDialog = false
                                playerConnection.service.sleepTimer?.start(minute = -1)
                            },
                        ) {
                            Text(stringResource(R.string.end_of_song))
                        }
                    }
                }
            },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    // Position update - only for local playback
    // When casting, we use castPosition directly to avoid sync issues
    // Use isPlaying instead of playbackState to ensure continuous updates during playback
    LaunchedEffect(isPlaying, isCasting) {
        if (!isCasting && isPlaying) {
            while (isActive) {
                delay(100) // Update more frequently for smoother progress bar
                if (sliderPosition == null) { // Only update if user isn't dragging
                    position = playerConnection.player.currentPosition
                    // Don't clobber a valid (metadata-derived) duration with 0/UNSET mid-resolve.
                    playerConnection.player.duration.takeIf { it > 0 }?.let { duration = it }
                }
            }
        }
    }

    // Also update position when playback state changes (e.g., song change, seek)
    LaunchedEffect(playbackState, mediaMetadata?.id) {
        if (!isCasting) {
            position = playerConnection.player.currentPosition
            // Prefer the song's known duration (from metadata, available instantly from the restored
            // queue) so the slider range is right even when restored paused / before the stream
            // resolves; fall back to the player's duration once it is known.
            duration = (mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L))
                ?: playerConnection.player.duration
        }
    }

    // Auto-switch from repeat one to repeat all when song ends naturally
    var previousMediaId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playbackState, mediaMetadata?.id) {
        val currentId = mediaMetadata?.id

        // Only switch from REPEAT_ONE to REPEAT_ALL when playback naturally ended
        // (i.e., the player transitioned to ENDED state and then moved to next track).
        // Do NOT switch on manual skips.
        if (currentId != null &&
            currentId != previousMediaId &&
            previousMediaId != null &&
            playbackState == Player.STATE_ENDED &&
            repeatMode == Player.REPEAT_MODE_ONE &&
            !isListenTogetherGuest) {
            playerConnection.player.setRepeatMode(Player.REPEAT_MODE_ALL)
        }

        previousMediaId = currentId
    }

    // When casting, use Cast position/duration directly
    // But wait a bit after manual seeks to let Cast catch up
    LaunchedEffect(isCasting, castPosition, castDuration) {
        if (isCasting && sliderPosition == null) {
            val timeSinceManualSeek = System.currentTimeMillis() - lastManualSeekTime
            if (timeSinceManualSeek > 1500) {
                // Only update from Cast if we haven't manually seeked recently
                position = castPosition
                if (castDuration > 0) duration = castDuration
            }
        }
    }

    val navBottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    // The queue no longer peeks out as a bar. It rests just off screen until something opens it.
    val dismissedBound = 0.dp

    // The arc player hides the status bar and menus bring it back, so the gap ignores whether it's showing; otherwise the queue jumps.
    @OptIn(ExperimentalLayoutApi::class)
    val queueTopGap = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding() + 20.dp
    val queueSheetState =
        rememberBottomSheetState(
            dismissedBound = dismissedBound,
            // Stops short of the top so it looks like the bar opened up, not a new screen.
            expandedBound = state.expandedBound - queueTopGap,
            collapsedBound = dismissedBound + 1.dp,
            initialAnchor = 1,
        )

    val sleepDialog = remember { mutableStateOf(false) }
    val panelOnLeft by rememberPreference(ArcPanelLeftKey, false)
    val sleepTimerOn =
        remember(playerConnection.service.sleepTimer?.triggerTime, playerConnection.service.sleepTimer?.pauseWhenSongEnd) {
            playerConnection.service.sleepTimer?.isActive ?: false
        }
    val toggleSleepTimer: () -> Unit = {
        if (sleepTimerOn) {
            playerConnection.service.sleepTimer?.clear()
        } else {
            sleepDialog.value = true
        }
    }
    val heldLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val openLyricsMenu = {
        menuState.show {
            com.metrolist.music.ui.menu.LyricsMenu(
                lyricsProvider = { heldLyrics },
                songProvider = { currentSong?.song },
                mediaMetadataProvider = { mediaMetadata ?: error("No song playing") },
                onDismiss = menuState::dismiss,
                onShowOffsetDialog = {
                    bottomSheetPageState.show { ShowOffsetDialog(songProvider = { currentSong?.song }) }
                },
            )
        }
    }
    val openLyricsMenuTaught = {
        openLyricsMenu()
        scope.launch { com.metrolist.music.arc.earnLyricsHoldHint(context) }
        Unit
    }
    val openLyricsMenuByHold = {
        openLyricsMenu()
        scope.launch { com.metrolist.music.arc.lyricsHoldFound(context) }
        Unit
    }
    val (playHoldAction, onPlayHoldAction) = rememberEnumPreference(PlayHoldActionKey, PlayHoldAction.ASK)
    val openPlayHoldPicker = {
        menuState.show {
            PlayHoldPicker(playHoldAction) {
                onPlayHoldAction(it)
                menuState.dismiss()
            }
        }
    }
    val openPlayerMenu = {
        menuState.show {
            PlayerMenu(
                mediaMetadata = mediaMetadata,
                playerBottomSheetState = state,
                onShowDetailsDialog = {
                    mediaMetadata?.id?.let { id -> bottomSheetPageState.show { ShowMediaInfo(id) } }
                },
                onDismiss = menuState::dismiss,
                onSleepTimer = toggleSleepTimer,
                onLyrics = openLyricsMenuTaught,
                playerLayout = playerLayout,
                onPlayerLayout = onPlayerLayoutChange,
            )
        }
    }
    val albumScope = rememberCoroutineScope()
    val openAlbum = {
        val albumId = mediaMetadata?.album?.id ?: currentSong?.album?.id ?: currentSong?.song?.albumId
        mediaMetadata?.id?.let { id ->
            com.metrolist.music.arc.openSongAlbum(context, albumScope, navController, id, albumId) { state.collapseSoft() }
        }
        Unit
    }
    val toCorner by animateFloatAsState(if (showInlineLyrics || cornerArc) 1f else 0f, spring(0.72f, 260f), label = "arcToCorner")
    val arcCoverBottom = remember { mutableFloatStateOf(0f) }
    val holdMainControl = {
        if (showInlineLyrics) openLyricsMenuTaught() else onPlayerLayoutChange((playerLayout + 1) % 2)
    }
    LaunchedEffect(state.isExpanded) { ArcLineSignals.playerExpanded = state.isExpanded }
    // a snippet is sung along to the lyrics, then the player goes back to how it was
    val snippetRecording by com.metrolist.music.arc.snippet.Snippet.recording.collectAsStateWithLifecycle()
    var lyricsBeforeSnippet by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(snippetRecording != null) {
        if (snippetRecording != null) {
            val text = heldLyrics?.lyrics
            if (!text.isNullOrBlank() && text != com.metrolist.music.db.entities.LyricsEntity.LYRICS_NOT_FOUND) {
                lyricsBeforeSnippet = showInlineLyrics
                showInlineLyrics = true
            }
        } else {
            lyricsBeforeSnippet?.let { showInlineLyrics = it }
            lyricsBeforeSnippet = null
        }
    }
    val playHold =
        rememberPlayHold(
            action = playHoldAction,
            playerConnection = playerConnection,
            inLyrics = showInlineLyrics,
            expanded = state.isExpanded,
            onLyricsMenu = openLyricsMenuTaught,
            onSwitchLayout = { onPlayerLayoutChange((playerLayout + 1) % 2) },
            onPick = openPlayHoldPicker,
        )

    val bottomSheetBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> {
                MaterialTheme.colorScheme.surfaceContainer
            }

            else -> {
                if (useBlackBackground) {
                    Color.Black
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            }
        }

    val backgroundAlpha = state.progress.coerceIn(0f, 1f)

    val vinylPlayerEnabled by rememberPreference(ArcVinylPlayerKey, ArcVinylPlayerDefault)
    // The cover and arc layouts sit on a ground in the cover's own colour, so the screen carries on from the cover.
    val coverGroundOn = !vinylPlayerEnabled && LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
    val groundArt = rememberCoverArt(mediaMetadata?.thumbnailUrl.takeIf { coverGroundOn }, upcoming = null, titleOnLeft = panelOnLeft)
    val coverGroundTarget = remember(groundArt?.edge) { groundArt?.let { coverGround(it.edge) } }
    val groundNear by animateColorAsState(coverGroundTarget?.near ?: bottomSheetBackgroundColor, tween(GROUND_FADE_MS), label = "groundNear")
    androidx.compose.runtime.SideEffect { com.metrolist.music.arc.VideoState.ground.value = groundNear }
    val groundDeep by animateColorAsState(coverGroundTarget?.deep ?: bottomSheetBackgroundColor, tween(GROUND_FADE_MS), label = "groundDeep")
    val accent by animateColorAsState(coverGroundTarget?.accent ?: textButtonColor, tween(GROUND_FADE_MS), label = "accent")
    val onAccent by animateColorAsState(coverGroundTarget?.onAccent ?: iconButtonColor, tween(GROUND_FADE_MS), label = "onAccent")

    val volumeKnob = remember { VolumeKnobState() }
    val knobHaptics = LocalHapticFeedback.current
    val castDeviceName by castHandler?.castDeviceName?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    com.metrolist.music.arc.WatchHost()
    BottomSheet(
        state = state,
        modifier = modifier,
        // On the content, which only exists while the player is open, so Home keeps its taps. Switching the sheet's
        // own modifiers when it left "expanded" restarted its drag handler and dropped every swipe down.
        contentModifier =
            Modifier.twistVolume(
                state = volumeKnob,
                haptics = knobHaptics,
                enabled = { state.isExpanded },
                read = { if (isCasting) castHandler?.castVolume?.value ?: 1f else com.metrolist.music.arc.DeviceVolume.read(context) },
                write = { volume ->
                    if (isCasting) {
                        castHandler?.setVolume(volume)
                    } else {
                        playerConnection.service.playerVolume.value = 1f
                        com.metrolist.music.arc.DeviceVolume.write(context, volume)
                    }
                },
            ),
        background = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(bottomSheetBackgroundColor)
                        .drawBehind { if (coverGroundOn) drawRect(groundNear, alpha = backgroundAlpha) },
            ) {
                when (playerBackground) {
                    PlayerBackgroundStyle.BLUR -> {
                        AnimatedContent(
                            targetState = mediaMetadata?.thumbnailUrl,
                            transitionSpec = {
                                fadeIn(tween(800)).togetherWith(fadeOut(tween(800)))
                            },
                            label = "blurBackground",
                        ) { thumbnailUrl ->
                            if (thumbnailUrl != null) {
                                Box(modifier = Modifier.alpha(backgroundAlpha)) {
                                    AsyncImage(
                                        model =
                                            ImageRequest
                                                .Builder(context)
                                                .data(thumbnailUrl)
                                                .size(100, 100)
                                                .allowHardware(false)
                                                .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .blur(if (useDarkTheme) 150.dp else 100.dp),
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.3f)),
                                    )
                                }
                            }
                        }
                    }

                    PlayerBackgroundStyle.GRADIENT -> {
                        AnimatedContent(
                            targetState = gradientColors,
                            transitionSpec = {
                                fadeIn(tween(800)).togetherWith(fadeOut(tween(800)))
                            },
                            label = "gradientBackground",
                        ) { colors ->
                            if (colors.isNotEmpty()) {
                                val gradientColorStops =
                                    if (colors.size >= 3) {
                                        arrayOf(
                                            0.0f to colors[0],
                                            0.5f to colors[1],
                                            1.0f to colors[2],
                                        )
                                    } else {
                                        arrayOf(
                                            0.0f to colors[0],
                                            0.6f to colors[0].copy(alpha = 0.7f),
                                            1.0f to Color.Black,
                                        )
                                    }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .alpha(backgroundAlpha)
                                        .background(Brush.verticalGradient(colorStops = gradientColorStops))
                                        .background(Color.Black.copy(alpha = 0.2f)),
                                )
                            }
                        }
                    }

                    else -> {
                        PlayerBackgroundStyle.DEFAULT
                    }
                }
            }
        },
        onDismiss =
            if (!isListenTogetherGuest) {
                {
                    playerConnection.service.clearAutomix()
                    playerConnection.player.stop()
                    playerConnection.player.clearMediaItems()
                }
            } else {
                null
            },
        collapsedContent = {
            MaterialTheme(colorScheme = LocalAppColorScheme.current ?: MaterialTheme.colorScheme) {
                MiniPlayer(
                    positionState = positionState,
                    durationState = durationState,
                    onClick = { state.expandSoft() },
                )
            }
        },
    ) {
        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            val playPauseRoundness by animateDpAsState(
                targetValue = if (isPlaying) 24.dp else 36.dp,
                animationSpec = tween(durationMillis = 90, easing = LinearEasing),
                label = "playPauseRoundness",
            )

            if (!thumbArcMode && !lyricsLine) Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
            ) {
                AnimatedContent(
                    targetState = showInlineLyrics,
                    label = "ThumbnailAnimation",
                ) { showLyrics ->
                    if (showLyrics) {
                        val showThumbnailOverlay =
                            isFullScreen &&
                                (
                                    (isListenTogetherGuest && isMuted) ||
                                        (!isListenTogetherGuest && (!effectiveIsPlaying || playbackState == STATE_ENDED))
                                )
                        val thumbnailActionLabel =
                            if (isListenTogetherGuest) {
                                if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute)
                            } else if (effectiveIsPlaying) {
                                stringResource(R.string.pause)
                            } else {
                                stringResource(R.string.play)
                            }
                        Row {
                            Box(
                                modifier =
                                    Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                        .then(
                                            if (isFullScreen) {
                                                Modifier.semantics { contentDescription = thumbnailActionLabel }
                                            } else {
                                                Modifier.clearAndSetSemantics {}
                                            },
                                        )
                                        .clickable(
                                            enabled = isFullScreen,
                                            role = Role.Button,
                                            onClickLabel = thumbnailActionLabel,
                                        ) {
                                            if (isListenTogetherGuest) {
                                                playerConnection.toggleMute()
                                            } else if (isCasting) {
                                                if (castIsPlaying) castHandler?.pause() else castHandler?.play()
                                            } else if (playbackState == STATE_ENDED) {
                                                playerConnection.player.seekTo(0, 0)
                                                playerConnection.player.playWhenReady = true
                                            } else {
                                                playerConnection.togglePlayPause()
                                            }
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (hidePlayerThumbnail) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.small_icon),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            tint = textButtonColor.copy(alpha = 0.7f),
                                        )
                                    }
                                } else {
                                    AsyncImage(
                                        model = mediaMetadata.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                if (showThumbnailOverlay) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.4f)),
                                    )
                                    Icon(
                                        painter =
                                            painterResource(
                                                when {
                                                    isListenTogetherGuest ->
                                                        if (isMuted) R.drawable.volume_off else R.drawable.volume_up
                                                    playbackState == STATE_ENDED -> R.drawable.replay
                                                    else -> R.drawable.play
                                                },
                                            ),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    AnimatedContent(
                        targetState = mediaMetadata.title,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "",
                    ) { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = TextBackgroundColor,
                            modifier =
                                Modifier
                                    .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                    .combinedClickable(
                                        enabled = true,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {
                                            val albumId = mediaMetadata.album?.id
                                                ?: currentSong?.album?.id
                                                ?: currentSong?.song?.albumId
                                            if (albumId != null) {
                                                navController.navigate("album/$albumId")
                                                state.collapseSoft()
                                            }
                                        },
                                        onLongClick = {
                                            val clip = ClipData.newPlainText(copiedTitleStr, title)
                                            clipboardManager.setPrimaryClip(clip)
                                            Toast
                                                .makeText(context, copiedTitleStr, Toast.LENGTH_SHORT)
                                                .show()
                                        },
                                    ),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (mediaMetadata.explicit) MIcon.Explicit()

                        if (mediaMetadata.artists.any { it.name.isNotBlank() }) {
                            val annotatedString =
                                buildAnnotatedString {
                                    mediaMetadata.artists.forEachIndexed { index, artist ->
                                        val tag = "artist_${artist.id.orEmpty()}"
                                        pushStringAnnotation(tag = tag, annotation = artist.id.orEmpty())
                                        withStyle(SpanStyle(color = TextBackgroundColor, fontSize = 16.sp)) {
                                            append(artist.name)
                                        }
                                        pop()
                                        if (index != mediaMetadata.artists.lastIndex) append(", ")
                                    }
                                }

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                        .padding(end = 12.dp),
                            ) {
                                var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                var clickOffset by remember { mutableStateOf<Offset?>(null) }
                                Text(
                                    text = annotatedString,
                                    style = MaterialTheme.typography.titleMedium.copy(color = TextBackgroundColor),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { layoutResult = it },
                                    modifier =
                                        Modifier
                                            .pointerInput(Unit) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val tapPosition = event.changes.firstOrNull()?.position
                                                        if (tapPosition != null) {
                                                            clickOffset = tapPosition
                                                        }
                                                    }
                                                }
                                            }.combinedClickable(
                                                enabled = true,
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() },
                                                onClick = {
                                                    val tapPosition = clickOffset
                                                    val layout = layoutResult
                                                    if (tapPosition != null && layout != null) {
                                                        val offset = layout.getOffsetForPosition(tapPosition)
                                                        annotatedString
                                                            .getStringAnnotations(offset, offset)
                                                            .firstOrNull()
                                                            ?.let { ann ->
                                                                val artistId = ann.item
                                                                if (artistId.isNotBlank()) {
                                                                    navController.navigate("artist/$artistId")
                                                                    state.collapseSoft()
                                                                }
                                                            }
                                                    }
                                                },
                                                onLongClick = {
                                                    val clip =
                                                        ClipData.newPlainText(
                                                            copiedArtistStr,
                                                            annotatedString,
                                                        )
                                                    clipboardManager.setPrimaryClip(clip)
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            copiedArtistStr,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                },
                                            ),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (showInlineLyrics) {
                    CapsuleRow(
                        buttons =
                            listOf(
                                CapsuleButton(R.drawable.fullscreen, { isFullScreen = !isFullScreen }),
                                CapsuleButton(R.drawable.more_horiz, openLyricsMenu),
                            ),
                        color = onBackgroundColor,
                    )
                }
            }

            if (thumbArcMode) {
                // The arc layer drawn over the player takes this space, so the art and title sit above it.
                Spacer(Modifier.weight(1f))
            } else Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = ((toCorner - 0.5f) * 2f).coerceIn(0f, 1f) },
            ) {
                Spacer(Modifier.height(24.dp))

                if (!lyricsLine) {
                    when (sliderStyle) {
                        SliderStyle.DEFAULT -> {
                            val sliderValue = (sliderPosition ?: effectivePosition).toFloat()
                            val sliderRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat())
                            val sliderState = remember(sliderRange) {
                                SliderState(value = sliderValue, trackRange = sliderRange)
                            }
                            sliderState.value = sliderValue
                            val crossfadeOn by rememberPreference(CrossfadeEnabledKey, false)
                            val crossfadeSeconds by rememberPreference(CrossfadeDurationKey, 5f)
                            val keepAlbumsGapless by rememberPreference(CrossfadeGaplessKey, true)
                            val handoverLead =
                                remember(mediaMetadata.id, duration, crossfadeOn, crossfadeSeconds, keepAlbumsGapless) {
                                    songHandoverLeadMs(playerConnection.player, crossfadeOn, crossfadeSeconds, keepAlbumsGapless)
                                }
                            val hasDuration = duration != C.TIME_UNSET && duration > 0L
                            val endDot =
                                rememberEndDot(
                                    songId = mediaMetadata.id,
                                    positionMs = sliderValue.toLong(),
                                    durationMs = if (hasDuration) duration else 0L,
                                    crossfadeLeadMs = handoverLead,
                                    isPlaying = effectiveIsPlaying,
                                )
                            Slider(
                                state = sliderState,
                                onValueChange = {
                                    if (!isListenTogetherGuest) {
                                        sliderPosition = it.toLong()
                                    }
                                },
                                onValueChangeFinished = {
                                    if (!isListenTogetherGuest) {
                                        sliderPosition?.let {
                                            if (isCasting) {
                                                castHandler?.seekTo(it)
                                                lastManualSeekTime = System.currentTimeMillis()
                                            } else {
                                                playerConnection.player.seekTo(it)
                                            }
                                            position = it
                                        }
                                        sliderPosition = null
                                    }
                                },
                                enabled = !isListenTogetherGuest,
                                colors = PlayerSliderColors.getSliderColors(textButtonColor, playerBackground, useDarkTheme),
                                track = { trackState ->
                                    // Without a gap, the bar stays whole right up to the end of the song.
                                    SliderDefaults.Track(
                                        sliderState = trackState,
                                        colors = PlayerSliderColors.getSliderColors(textButtonColor, playerBackground, useDarkTheme),
                                        enabled = !isListenTogetherGuest,
                                        thumbTrackGapSize = 0.dp,
                                        trackInsideCornerSize = 0.dp,
                                        drawStopIndicator = { offset ->
                                            endDot.draw(this, offset, textButtonColor)
                                        },
                                    )
                                },
                                // The dot's burst flies over the buttons below.
                                modifier = Modifier.zIndex(1f).padding(horizontal = PlayerHorizontalPadding),
                            )
                        }

                        SliderStyle.WAVY -> {
                            if (squigglySlider) {
                                SquigglySlider(
                                    value = (sliderPosition ?: effectivePosition).toFloat(),
                                    valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                                    onValueChange = {
                                        sliderPosition = it.toLong()
                                    },
                                    onValueChangeFinished = {
                                        sliderPosition?.let {
                                            if (isCasting) {
                                                castHandler?.seekTo(it)
                                                lastManualSeekTime = System.currentTimeMillis()
                                            } else {
                                                playerConnection.player.seekTo(it)
                                            }
                                            position = it
                                        }
                                        sliderPosition = null
                                    },
                                    modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                                    colors = PlayerSliderColors.getSliderColors(textButtonColor, playerBackground, useDarkTheme),
                                    isPlaying = effectiveIsPlaying,
                                )
                            } else {
                                WavySlider(
                                    value = (sliderPosition ?: effectivePosition).toFloat(),
                                    valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                                    onValueChange = {
                                        sliderPosition = it.toLong()
                                    },
                                    onValueChangeFinished = {
                                        sliderPosition?.let {
                                            if (isCasting) {
                                                castHandler?.seekTo(it)
                                                lastManualSeekTime = System.currentTimeMillis()
                                            } else {
                                                playerConnection.player.seekTo(it)
                                            }
                                            position = it
                                        }
                                        sliderPosition = null
                                    },
                                    colors = PlayerSliderColors.getSliderColors(textButtonColor, playerBackground, useDarkTheme),
                                    modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                                    isPlaying = effectiveIsPlaying,
                                )
                            }
                        }

                        SliderStyle.SLIM -> {
                            val sliderValue = (sliderPosition ?: effectivePosition).toFloat()
                            val sliderRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat())
                            val sliderState = remember(sliderRange) {
                                SliderState(value = sliderValue, trackRange = sliderRange)
                            }
                            sliderState.value = sliderValue
                            Slider(
                                state = sliderState,
                                onValueChange = {
                                    if (!isListenTogetherGuest) {
                                        sliderPosition = it.toLong()
                                    }
                                },
                                onValueChangeFinished = {
                                    if (!isListenTogetherGuest) {
                                        sliderPosition?.let {
                                            if (isCasting) {
                                                castHandler?.seekTo(it)
                                                lastManualSeekTime = System.currentTimeMillis()
                                            } else {
                                                playerConnection.player.seekTo(it)
                                            }
                                            position = it
                                        }
                                        sliderPosition = null
                                    }
                                },
                                enabled = !isListenTogetherGuest,
                                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                                track = { sliderState ->
                                    PlayerSliderTrack(
                                        sliderState = sliderState,
                                        colors = PlayerSliderColors.getSliderColors(textButtonColor, playerBackground, useDarkTheme),
                                    )
                                },
                                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding + 4.dp),
                    ) {
                        Text(
                            text = makeTimeString(sliderPosition ?: effectivePosition),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = TextBackgroundColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Text(
                            text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = TextBackgroundColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }

                AnimatedVisibility(
                    visible = !isFullScreen,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    // The arc layer moves its buttons into this space.
                    Spacer(Modifier.fillMaxWidth().height(CornerBoxHeight))
                }
            }
        }

        val lyingFlat =
            rememberPhoneLyingFlat(vinylPlayerEnabled && state.isExpanded && !showInlineLyrics && queueSheetState.isCollapsed)
        // Back leaves turntable mode until the phone has been picked up again.
        var turntableDismissed by remember { mutableStateOf(false) }
        LaunchedEffect(lyingFlat) { if (!lyingFlat) turntableDismissed = false }
        val turntable = lyingFlat && !turntableDismissed
        BackHandler(enabled = turntable) { turntableDismissed = true }
        TurntableWindow(turntable, keepStatusHidden = bareCover)

        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Calculate vertical padding like OuterTune
                val density = LocalDensity.current
                val verticalPadding =
                    max(
                        WindowInsets.systemBars.getTop(density),
                        WindowInsets.systemBars.getBottom(density),
                    )
                val verticalPaddingDp = with(density) { verticalPadding.toDp() }
                val verticalWindowInsets = WindowInsets(left = 0.dp, top = verticalPaddingDp, right = 0.dp, bottom = verticalPaddingDp)

                Row(
                    modifier =
                        Modifier
                            .windowInsetsPadding(
                                WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).add(verticalWindowInsets),
                            ).padding(bottom = 24.dp)
                            .fillMaxSize(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .weight(1f)
                                .nestedScroll(state.preUpPostDownNestedScrollConnection),
                    ) {
                        // Remember lambdas to prevent unnecessary recomposition
                        val currentSliderPosition by rememberUpdatedState(sliderPosition)
                        val sliderPositionProvider = remember { { currentSliderPosition } }
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        AnimatedContent(
                            targetState = showInlineLyrics,
                            label = "Lyrics",
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                        ) { showLyrics ->
                            if (showLyrics) {
                                InlineLyricsView(
                                    mediaMetadata = mediaMetadata,
                                    showLyrics = showLyrics,
                                    sliderPositionProvider = sliderPositionProvider,
                                )
                            } else {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier.animateContentSize(),
                                    isPlayerExpanded = isExpandedProvider,
                                    isLandscape = true,
                                    isListenTogetherGuest = isListenTogetherGuest,
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .weight(if (showInlineLyrics) 0.65f else 1f, false)
                                .animateContentSize()
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                    ) {
                        Spacer(Modifier.weight(1f))

                        mediaMetadata?.let {
                            controlsContent(it)
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            else -> {
                val bottomPadding by animateDpAsState(
                    targetValue = if (isFullScreen) 0.dp else queueSheetState.collapsedBound,
                    label = "bottomPadding",
                )
                AnimatedContent(
                    targetState = vinylPlayerEnabled,
                    transitionSpec = { vinylSwitchTransition() },
                    label = "vinylSwitch",
                    modifier = Modifier.fillMaxSize().swipeUpOpens(queueSheetState),
                ) { showVinyl -> if (showVinyl) {
                    VinylPlayer(
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        canControl = !isListenTogetherGuest,
                        scratchPlayer = playerConnection.player.takeUnless { isCasting },
                        showLyrics = showInlineLyrics,
                        bottomInset = navBottomInset + CapsuleSize + 24.dp,
                        positionProvider = { effectivePosition },
                        durationProvider = { duration },
                        contentColor = onBackgroundColor,
                        onTogglePlay = playerConnection::togglePlayPause,
                        onPrevious = {
                            if (isCasting) castHandler?.skipToPrevious() else playerConnection.player.seekToPreviousMediaItem()
                        },
                        onNext = playerConnection::seekToNext,
                        liked = currentSong?.song?.liked == true,
                        onToggleLike = playerConnection::toggleLike,
                        turntable = turntable,
                        leftHanded = animatedPanelSide(panelOnLeft),
                        onHoldLabel = holdMainControl,
                        bottomBar = {
                            val shuffled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
                            // Same buttons at the same spots as the normal player, so switching layouts moves nothing.
                            BoxWithConstraints(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = navBottomInset),
                            ) {
                                CornerExtras(
                                    extras =
                                        playerExtras(
                                            shuffled = shuffled,
                                            repeatMode = repeatMode,
                                            lyricsOpen = showInlineLyrics,
                                            canControl = !isListenTogetherGuest,
                                            onShuffle = { playerConnection.player.shuffleModeEnabled = !shuffled },
                                            onRepeat = { playerConnection.player.toggleRepeatMode() },
                                            onLyrics = { showInlineLyrics = !showInlineLyrics },
                                            onLyricsMenu = openLyricsMenuByHold,
                                            onMenu = openPlayerMenu,
                                            outputIcon = rememberOutputIcon(),
                                            onOutput = { showOutputSwitcher(context) },
                                        ),
                                    side = animatedPanelSide(panelOnLeft),
                                    width = maxWidth,
                                    content = Color.White,
                                )
                            }
                        },
                        onOpenArtist = { id ->
                            navController.navigate("artist/$id")
                            state.collapseSoft()
                        },
                        onOpenAlbum = {
                            val albumId = mediaMetadata?.album?.id ?: currentSong?.album?.id ?: currentSong?.song?.albumId
                            if (albumId != null) {
                                navController.navigate("album/$albumId")
                                state.collapseSoft()
                            }
                        },
                        onSeek = { target ->
                            if (isCasting) {
                                castHandler?.seekTo(target)
                                lastManualSeekTime = System.currentTimeMillis()
                            } else {
                                playerConnection.player.seekTo(target)
                            }
                            position = target
                        },
                        lyrics = {
                            InlineLyricsView(
                                mediaMetadata = mediaMetadata,
                                showLyrics = true,
                                sliderPositionProvider = { sliderPosition },
                            )
                        },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                    )
                } else Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                            .padding(bottom = bottomPadding + navBottomInset)
                            .animateContentSize(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            (if (coverSquare) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.weight(1f))
                                .onGloballyPositioned { arcCoverBottom.floatValue = it.boundsInParent().bottom }
                                .graphicsLayer { translationX = coverSwipeShift.value },
                    ) {
                        if (bareCover) CoverPeeks(coverSwipeShift, mediaMetadata?.id)
                        // Remember lambdas to prevent unnecessary recomposition
                        val currentSliderPosition by rememberUpdatedState(sliderPosition)
                        val sliderPositionProvider = remember { { currentSliderPosition } }
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        AnimatedContent(
                            targetState = showInlineLyrics,
                            label = "Lyrics",
                            transitionSpec = { fadeIn(tween(LYRICS_FADE_MS)) togetherWith fadeOut(tween(LYRICS_FADE_MS)) },
                        ) { showLyrics ->
                            if (showLyrics) {
                                InlineLyricsView(
                                    mediaMetadata = mediaMetadata,
                                    showLyrics = showLyrics,
                                    sliderPositionProvider = sliderPositionProvider,
                                )
                            } else {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection),
                                    isPlayerExpanded = isExpandedProvider,
                                    isListenTogetherGuest = isListenTogetherGuest,
                                    fullBleed = true,
                                    onCoverTap = openAlbum,
                                )
                            }
                        }
                    }

                    mediaMetadata?.let {
                        controlsContent(it)
                    }

                    Spacer(Modifier.height(8.dp))
                }
                val arcMetadata = mediaMetadata
                if (!showVinyl && !isFullScreen && arcMetadata != null) {
                    val shuffled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
                    val crossfadeOn by rememberPreference(CrossfadeEnabledKey, false)
                    val crossfadeSeconds by rememberPreference(CrossfadeDurationKey, 5f)
                    val keepAlbumsGapless by rememberPreference(CrossfadeGaplessKey, true)
                    val handoverLead =
                        remember(arcMetadata.id, duration, crossfadeOn, crossfadeSeconds, keepAlbumsGapless) {
                            songHandoverLeadMs(playerConnection.player, crossfadeOn, crossfadeSeconds, keepAlbumsGapless)
                        }
                    val arcEndDot =
                        rememberEndDot(
                            songId = arcMetadata.id,
                            positionMs = sliderPosition ?: effectivePosition,
                            durationMs = if (duration != C.TIME_UNSET && duration > 0L) duration else 0L,
                            crossfadeLeadMs = handoverLead,
                            isPlaying = effectiveIsPlaying,
                        )
                    val liked = currentSong?.song?.liked == true
                    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
                    val currentWindow by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
                    val nextUp =
                        remember(queueWindows, currentWindow, shuffled, repeatMode, isListenTogetherGuest, isCasting) {
                            val player = playerConnection.player
                            val index = player.nextMediaItemIndex
                            if (isListenTogetherGuest || isCasting || index == C.INDEX_UNSET || index == player.currentMediaItemIndex) {
                                null
                            } else {
                                val item = player.getMediaItemAt(index)
                                val meta = item.mediaMetadata
                                NextUp(item.mediaId, meta.title?.toString().orEmpty(), meta.artist?.toString().orEmpty(), meta.artworkUri?.toString()) {
                                    player.removeMediaItem(index)
                                }
                            }
                        }
                    val showAudioDetails by rememberPreference(ArcAudioDetailsKey, true)
                    val storedFormat by LocalDatabase.current.format(arcMetadata.id).collectAsStateWithLifecycle(null)
                    val audioDetails = if (showAudioDetails) rememberAudioDetails(playerConnection.player, storedFormat?.bitrate) else null
                    val videoBottom by com.metrolist.music.arc.VideoState.videoBottom
                    val videoLabelTop by animateFloatAsState(videoBottom ?: 0f, tween(GROUND_FADE_MS), label = "videoLabelTop")
                    ThumbArcControls(
                        audioDetails = audioDetails,
                        mainIcon =
                            when {
                                isListenTogetherGuest -> if (isMuted) R.drawable.volume_off else R.drawable.volume_up
                                playbackState == STATE_ENDED -> R.drawable.replay
                                effectiveIsPlaying -> R.drawable.pause
                                else -> R.drawable.play
                            },
                        onMain = {
                            when {
                                isListenTogetherGuest -> playerConnection.toggleMute()
                                isCasting -> if (castIsPlaying) castHandler?.pause() else castHandler?.play()
                                playbackState == STATE_ENDED -> {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                }
                                else -> playerConnection.player.togglePlayPause()
                            }
                        },
                        hold = playHold,
                        previous = CapsuleButton(R.drawable.skip_previous, playerConnection::seekToPrevious, enabled = !isListenTogetherGuest && canSkipPrevious),
                        next = CapsuleButton(R.drawable.skip_next, playerConnection::seekToNext, enabled = !isListenTogetherGuest && canSkipNext),
                        share =
                            CapsuleButton(R.drawable.share, {
                                com.metrolist.music.arc.shareSong(context, arcMetadata.id, arcMetadata.title, arcMetadata.artists.firstOrNull()?.name)
                            }),
                        like =
                            CapsuleButton(
                                if (liked) R.drawable.favorite else R.drawable.favorite_border,
                                playerConnection::toggleLike,
                                active = liked,
                                tint = if (liked) MaterialTheme.colorScheme.error else null,
                                iconModifier = Modifier.likePop(liked, arcMetadata.id),
                                tickOnPress = false,
                            ),
                        extras =
                            playerExtras(
                                shuffled = shuffled,
                                repeatMode = repeatMode,
                                lyricsOpen = showInlineLyrics,
                                canControl = !isListenTogetherGuest,
                                onShuffle = { playerConnection.player.shuffleModeEnabled = !shuffled },
                                onRepeat = { playerConnection.player.toggleRepeatMode() },
                                onLyrics = { showInlineLyrics = !showInlineLyrics },
                                onLyricsMenu = openLyricsMenuByHold,
                                onMenu = openPlayerMenu,
                                outputIcon = rememberOutputIcon(),
                                onOutput = { showOutputSwitcher(context) },
                            ),
                        leftHanded = panelOnLeft,
                        toCorner = toCorner,
                        bottomInset = bottomPadding + navBottomInset + 8.dp,
                        endDot = arcEndDot,
                        nextUp = nextUp,
                        glowArtwork = if (bareCover) arcMetadata.thumbnailUrl else null,
                        coverShown = bareCover,
                        cornerArc = cornerArc,
                        ground = groundDeep,
                        groundNear = groundNear,
                        labels =
                            ArcLabels(
                                title = arcMetadata.title,
                                artists = arcMetadata.artists.filter { it.name.isNotBlank() }.map { it.name to it.id },
                                onTitle = openAlbum,
                                onTitleHold = {
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText(copiedTitleStr, arcMetadata.title))
                                    Toast.makeText(context, copiedTitleStr, Toast.LENGTH_SHORT).show()
                                },
                                onArtist = { id ->
                                    navController.navigate("artist/$id")
                                    state.collapseSoft()
                                },
                                onArtistHold = {
                                    val names = arcMetadata.artists.joinToString(", ") { it.name }
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText(copiedArtistStr, names))
                                    Toast.makeText(context, copiedArtistStr, Toast.LENGTH_SHORT).show()
                                },
                            ),
                        topLimit = { arcCoverBottom.floatValue },
                        labelTop = videoLabelTop,
                        swipeShift = coverSwipeShift,
                        content = TextBackgroundColor,
                        mainFill = accent,
                        mainTint = onAccent,
                        positionMs = { sliderPosition ?: effectivePosition },
                        durationMs = { if (duration == C.TIME_UNSET) 0L else duration },
                        onScrub = { sliderPosition = it },
                        onSeek = { target ->
                            if (isCasting) {
                                castHandler?.seekTo(target)
                                lastManualSeekTime = System.currentTimeMillis()
                            } else {
                                playerConnection.player.seekTo(target)
                            }
                            position = target
                        },
                    )
                }
                }
            }
        }

        if (!turntable) VinylPlayerToggle(onBackgroundColor, Modifier.align(Alignment.TopEnd))
        if (!turntable) CollapsePlayerButton(onBackgroundColor, onClick = { state.collapseSoft() }, modifier = Modifier.align(Alignment.TopStart))
        // The vinyl hides the status bar like the cover layouts, so it gets the same clock and battery line.
        if (!turntable && vinylPlayerEnabled && bareCover) StatusLine(dark = onBackgroundColor.luminance() < 0.5f)
        VolumeKnobOverlay(
            volumeKnob,
            output = if (isCasting) castDeviceName ?: stringResource(R.string.arc_this_phone) else stringResource(R.string.arc_this_phone),
            modifier = Modifier.matchParentSize(),
        )

        AnimatedVisibility(
            visible = !isFullScreen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Queue(
                state = queueSheetState,
                topGap = queueTopGap,
                sleepDialog = sleepDialog,
                playerBottomSheetState = state,
                background =
                    if (useBlackBackground) {
                        Color.Black
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                onBackgroundColor = onBackgroundColor,
                TextBackgroundColor = TextBackgroundColor,
                textButtonColor = textButtonColor,
                iconButtonColor = iconButtonColor,
                pureBlack = pureBlack,
                showInlineLyrics = showInlineLyrics,
                playerBackground = playerBackground,
                onToggleLyrics = {
                    showInlineLyrics = !showInlineLyrics
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineLyricsView(
    mediaMetadata: MediaMetadata?,
    showLyrics: Boolean,
    sliderPositionProvider: () -> Long?,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle(initialValue = -1)
    val lyrics = remember(currentLyrics) { currentLyrics?.lyrics?.trim() }
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var appInForeground by remember {
        mutableStateOf(
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }
    DisposableEffect(Unit) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer =
            LifecycleEventObserver { _, _ ->
                appInForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val nextMetadata =
        remember(queueWindows, currentWindowIndex) {
            if (currentWindowIndex >= 0 && currentWindowIndex + 1 < queueWindows.size) {
                queueWindows[currentWindowIndex + 1].mediaItem.metadata
            } else {
                null
            }
        }

    LaunchedEffect(mediaMetadata?.id, currentLyrics) {
        if (mediaMetadata != null && currentLyrics == null) {
            delay(500)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val entryPoint =
                        EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            com.metrolist.music.di.LyricsHelperEntryPoint::class.java,
                        )
                    val lyricsHelper = entryPoint.lyricsHelper()
                    val fetchedLyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                    database.query {
                        upsert(LyricsEntity(mediaMetadata.id, fetchedLyricsWithProvider.lyrics, fetchedLyricsWithProvider.provider))
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }

    // Prefetch lyrics for the next queue item only while the lyrics pane is visible, the app is in the
    // foreground, and the current track's lyrics row has finished loading (avoids competing with the
    // active fetch).
    LaunchedEffect(
        nextMetadata?.id,
        showLyrics,
        appInForeground,
        mediaMetadata?.id,
        currentLyrics,
    ) {
        if (!showLyrics || !appInForeground || nextMetadata == null) return@LaunchedEffect
        val loadedForCurrent =
            currentLyrics?.let { lyrics ->
                mediaMetadata == null || lyrics.id == mediaMetadata.id
            } == true
        if (mediaMetadata != null && !loadedForCurrent) return@LaunchedEffect
        val nextId = nextMetadata.id
        delay(400)
        if (!showLyrics || !appInForeground || !isActive) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val existing = database.lyrics(nextId).first()
                if (existing != null) return@withContext
                val entryPoint =
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.metrolist.music.di.LyricsHelperEntryPoint::class.java,
                    )
                val lyricsHelper = entryPoint.lyricsHelper()
                val fetched = lyricsHelper.getLyrics(nextMetadata)
                database.query {
                    upsert(LyricsEntity(nextId, fetched.lyrics, fetched.provider))
                }
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            lyrics == null -> {
                ContainedLoadingIndicator()
            }

            lyrics == LyricsEntity.LYRICS_NOT_FOUND -> {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                val lyricsContent: @Composable () -> Unit = {
                    Lyrics(
                        sliderPositionProvider = sliderPositionProvider,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        showLyrics = showLyrics,
                    )
                }
                ProvideTextStyle(
                    value =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        ),
                ) {
                    lyricsContent()
                }
            }
        }
    }
}

@Composable
private fun PlayerMoreMenuButton(
    mediaMetadata: MediaMetadata,
    state: BottomSheetState,
    textButtonColor: Color,
    iconButtonColor: Color,
) {
    val navController = LocalNavController.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(textButtonColor)
                .clickable {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = {
                                mediaMetadata.id.let {
                                    bottomSheetPageState.show {
                                        ShowMediaInfo(it)
                                    }
                                }
                            },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
    ) {
        Image(
            painter = painterResource(R.drawable.more_horiz),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconButtonColor),
        )
    }
}

/** The songs on either side, just past the cover's edges, so they slide into view while a sideways swipe nudges the cover. */
@Composable
private fun BoxScope.CoverPeeks(shift: Animatable<Float, *>, songId: String?) {
    val peeking by remember { derivedStateOf { shift.value != 0f } }
    if (!peeking) return
    val player = LocalPlayerConnection.current?.player ?: return
    val (previous, next) =
        remember(songId) {
            fun artwork(index: Int) = if (index == C.INDEX_UNSET) null else player.getMediaItemAt(index).mediaMetadata.artworkUri?.toString()
            artwork(player.previousMediaItemIndex) to artwork(player.nextMediaItemIndex)
        }
    listOf(previous to -1f, next to 1f).forEach { (artwork, side) ->
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { translationX = side * size.width },
            )
        }
    }
}

internal const val LYRICS_FADE_MS = 250

private const val GROUND_FADE_MS = 500

/** Rolls the player down, for anyone who doesn't know the swipe. Sits opposite the vinyl toggle. */
@Composable
private fun CollapsePlayerButton(contentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    IconButton(
        interactionSource = press,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(top = 8.dp, start = 8.dp)
                .pressBounce(press),
    ) {
        Icon(painterResource(R.drawable.expand_more), contentDescription = stringResource(R.string.arc_collapse_player), tint = contentColor)
    }
}
