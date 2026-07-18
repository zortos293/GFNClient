package com.opencloudgaming.opennow

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.SignalCellular0Bar
import androidx.compose.material.icons.rounded.SignalCellular4Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt1Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt2Bar
import androidx.compose.material.icons.rounded.SignalWifi0Bar
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Wifi1Bar
import androidx.compose.material.icons.rounded.Wifi2Bar
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val Green = Color(0xff6af0a0)
private val Background = Color(0xff090b0d)
private val Panel = Color(0xff11161a)
private val PanelAlt = Color(0xff171d22)
private val TextPrimary = Color(0xffeef3f5)
private val TextMuted = Color(0xff98a4aa)
private val ChromeScrim = Color.Black.copy(alpha = 0.16f)
private val TopBarCompactControlHeight = 30.dp
private const val DEVICE_LOGIN_SIDE_BY_SIDE_MIN_WIDTH_DP = 520
private const val COMPACT_STREAM_DEVICE_STATUS_REFRESH_MS = 5_000L
private const val QUEUE_POSITION_VISUAL_SETTLE_MS = 1100L
private val UiAccent.color: Color
    get() = when (this) {
        UiAccent.OpenNow -> Green
        UiAccent.Pixel -> Color(0xff8ab4f8)
        UiAccent.HotPink -> Color(0xffff4fb8)
        UiAccent.Lime -> Color(0xffc7ef6b)
        UiAccent.Coral -> Color(0xffff8d7a)
        UiAccent.Violet -> Color(0xffc7a4ff)
    }

@Composable
internal fun uiAccentLabel(accent: UiAccent): String = when (accent) {
    UiAccent.OpenNow -> stringResource(R.string.accent_opennow)
    UiAccent.Pixel -> stringResource(R.string.accent_pixel)
    UiAccent.HotPink -> stringResource(R.string.accent_hot_pink)
    UiAccent.Lime -> stringResource(R.string.accent_lime)
    UiAccent.Coral -> stringResource(R.string.accent_coral)
    UiAccent.Violet -> stringResource(R.string.accent_violet)
}

@Composable
fun OpenNowTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val accent = settings.uiAccent.color
    val fallbackScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xff08090c),
        background = Background,
        surface = Panel,
        surfaceVariant = PanelAlt,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextMuted,
    )
    val colorScheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(
            primary = accent,
            onPrimary = Color(0xff08090c),
            secondary = accent,
            tertiary = Green,
        )
    } else {
        fallbackScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

@Composable
fun OpenNowApp(viewModel: OpenNowViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launchAudioController = remember(context) { AndroidNerdAudioController(context.applicationContext) }
    val playIntroOnAppLaunch = remember { state.settings.streamIntroMusic }
    val introStartsMutedOnLaunch = remember {
        state.settings.streamIntroMusic && state.settings.streamIntroStartMode == IntroMusicStartMode.Muted
    }
    val musicControlsEnabled = state.settings.streamIntroMusic || state.settings.queueReadyMusic
    val streamActive = state.page == AppPage.Stream || state.streamStatus != "idle"
    var launchIntroStarted by remember { mutableStateOf(false) }
    var launchMusicMuted by remember { mutableStateOf(introStartsMutedOnLaunch) }
    var launchMusicPlaying by remember { mutableStateOf(false) }
    var previousStreamStatus by remember { mutableStateOf(state.streamStatus) }
    var queuedForStartCue by remember { mutableStateOf(false) }
    var lastStartCueSessionId by remember { mutableStateOf<String?>(null) }
    var hiddenUpdatePromptKey by remember { mutableStateOf<String?>(null) }
    val updatePromptKey = state.androidUpdate.visibleNoticeKey(state.dismissedAndroidUpdateNoticeKey)
    val showAnalyticsConsent = !state.settings.analyticsConsentAsked
    val showUpdatePrompt = updatePromptKey != null &&
        updatePromptKey != hiddenUpdatePromptKey &&
        !showAnalyticsConsent &&
        state.androidUpdate.status in setOf(AndroidUpdateStatus.Available, AndroidUpdateStatus.Downloaded)

    DisposableEffect(launchAudioController) {
        onDispose {
            launchAudioController.release()
        }
    }
    LaunchedEffect(
        playIntroOnAppLaunch,
        state.settings.streamIntroMusic,
        state.settings.queueReadyMusic,
        launchMusicMuted,
        state.page,
        state.streamStatus,
        state.launchPhase,
        state.queuePosition,
        state.streamSession?.sessionId,
        state.streamSession?.queuePosition,
        state.streamSession?.seatSetupStep,
    ) {
        val sessionId = state.streamSession?.sessionId
        val queueReadyForStream =
            previousStreamStatus == "queue" &&
                state.streamStatus == "connecting" &&
                sessionId != null &&
                sessionId != lastStartCueSessionId
        previousStreamStatus = state.streamStatus
        if (state.streamStatus == "queue") {
            queuedForStartCue = queuedForStartCue ||
                queueDisplayPosition(state) != null ||
                state.launchPhase.equals("Queue", ignoreCase = true)
        }

        if (!musicControlsEnabled) {
            launchIntroStarted = false
            launchMusicMuted = false
            launchAudioController.stopAll { launchMusicPlaying = it }
            if (state.streamStatus == "idle") {
                queuedForStartCue = false
            }
            return@LaunchedEffect
        }
        if (!state.settings.streamIntroMusic && launchMusicMuted) {
            launchMusicMuted = false
        }
        if (!state.settings.streamIntroMusic) {
            launchAudioController.stopIntro { launchMusicPlaying = it }
        }
        if (!state.settings.queueReadyMusic) {
            launchAudioController.stopQueueReadyReminder { launchMusicPlaying = it }
        }

        if (!state.settings.streamIntroMusic && !state.settings.queueReadyMusic) {
            launchAudioController.stopAll { launchMusicPlaying = it }
        } else if (queueReadyForStream && queuedForStartCue) {
            launchMusicMuted = false
            lastStartCueSessionId = sessionId
            queuedForStartCue = false
            launchAudioController.startQueueReadyReminder(enabled = state.settings.queueReadyMusic) { launchMusicPlaying = it }
        } else if (launchMusicMuted) {
            launchAudioController.stopIntro { launchMusicPlaying = it }
        } else if (playIntroOnAppLaunch && state.settings.streamIntroMusic && !streamActive) {
            if (!launchIntroStarted) {
                launchIntroStarted = true
                launchAudioController.startIntro(enabled = true) { launchMusicPlaying = it }
            }
        } else {
            launchAudioController.stopIntro { launchMusicPlaying = it }
        }
        if (state.streamStatus == "idle") {
            queuedForStartCue = false
        }
    }
    val musicControl = TopBarMusicControl(
        visible = musicControlsEnabled,
        playing = launchMusicPlaying,
        muted = launchMusicMuted,
        onToggle = {
            when {
                launchMusicMuted -> {
                    launchMusicMuted = false
                    if (state.settings.streamIntroMusic && !streamActive) {
                        launchIntroStarted = true
                        launchAudioController.startIntro(enabled = true) { launchMusicPlaying = it }
                    }
                }
                launchMusicPlaying -> {
                    launchMusicMuted = true
                    launchAudioController.stopAll { launchMusicPlaying = it }
                }
                state.settings.streamIntroMusic && !streamActive -> {
                    launchIntroStarted = true
                    launchAudioController.startIntro(enabled = true) { launchMusicPlaying = it }
                }
                else -> {
                    launchMusicMuted = true
                    launchAudioController.stopAll { launchMusicPlaying = it }
                }
            }
        },
    )

    OpenNowTheme(state.settings) {
        val primaryColor = MaterialTheme.colorScheme.primary
        CompositionLocalProvider(LocalTvLoadingProfile provides state.androidTvProfile) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .drawWithCache {
                        val brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            center = Offset(size.width, 0f),
                            radius = size.width.coerceAtLeast(size.height) * 0.8f
                        )
                        onDrawBehind {
                            drawRect(brush)
                        }
                    }
            ) {
                Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                    when {
                        state.authSession != null -> MainShell(state, viewModel, musicControl)
                        else -> LoginScreen(state, viewModel)
                    }
                }
                updatePromptKey?.takeIf { showUpdatePrompt }?.let { promptKey ->
                    AndroidUpdatePromptDialog(
                        update = state.androidUpdate,
                        onPrimary = {
                            hiddenUpdatePromptKey = promptKey
                            when (state.androidUpdate.status) {
                                AndroidUpdateStatus.Available -> viewModel.downloadAndroidUpdate()
                                AndroidUpdateStatus.Downloaded -> viewModel.installAndroidUpdate()
                                else -> Unit
                            }
                        },
                        onDetails = {
                            hiddenUpdatePromptKey = promptKey
                            viewModel.openAndroidUpdateSettings()
                        },
                        onDismiss = viewModel::dismissAndroidUpdateNotice,
                    )
                }
                if (showAnalyticsConsent) {
                    AnalyticsConsentDialog(
                        onAllow = {
                            viewModel.updateSettings(
                                state.settings.copy(
                                    analyticsConsentAsked = true,
                                    analyticsOptOut = false,
                                ),
                            )
                        },
                        onDecline = {
                            viewModel.updateSettings(
                                state.settings.copy(
                                    analyticsConsentAsked = true,
                                    analyticsOptOut = true,
                                ),
                            )
                        },
                    )
                }
                DiagnosticShareDialog(
                    state = state,
                    onUpload = viewModel::uploadDiagnosticShare,
                    onDismiss = viewModel::dismissDiagnosticShare,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticShareDialog(
    state: OpenNowUiState,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val share = state.diagnosticShare
    if (!share.awaitingConsent && !share.uploading && share.pasteUrl == null) return
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(share.clipboardSummary, state.androidTvProfile) {
        if (!state.androidTvProfile) {
            share.clipboardSummary?.let { clipboard.setText(AnnotatedString(it)) }
        }
    }
    when {
        share.uploading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Preparing diagnostics") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Removing sensitive values and creating a temporary paste…")
                }
            },
            confirmButton = {},
        )
        share.pasteUrl != null -> {
            val qrCode = remember(share.pasteUrl) { QrCode.encodeText(share.pasteUrl) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(if (state.androidTvProfile) "Scan diagnostics link" else "Diagnostics copied") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.androidTvProfile) {
                            if (qrCode != null) {
                                QrCodeView(qrCode, Modifier.size(240.dp))
                                Text("Scan this QR code on your phone. The sanitized paste expires within 24 hours.")
                            } else {
                                Text("Could not create the QR code. Close this dialog and try again.")
                            }
                        } else {
                            Text("Device, account type, stream profile, current status, and the temporary paste URL were copied to the clipboard.")
                            Text(
                                share.pasteUrl,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                },
                confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
            )
        }
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Create temporary diagnostics paste?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("OpenNOW will remove tokens, account identifiers, email addresses, session IDs, and network addresses before uploading.")
                    Text("The randomized link is unlisted but not encrypted, and the paste service deletes uploads within 24 hours.", color = TextMuted)
                    share.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = onUpload) { Text(if (share.error == null) "Sanitize and upload" else "Retry") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AnalyticsConsentDialog(
    onAllow: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Share diagnostics?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Share anonymous diagnostics to help us find patterns in bugs, crashes, and performance problems. Sensitive data is removed, and we do not sell your data.",
                )
                Text(
                    "If sharing is off during a crash, we may not have enough information to investigate your report. It is off by default and can be changed in Privacy settings.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onAllow) {
                Text("Share analytics")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Keep off")
            }
        },
    )
}

@Composable
private fun AndroidUpdatePromptDialog(
    update: AndroidUpdateState,
    onPrimary: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val version = update.availableVersionName?.let { "Version $it" }
        ?: update.availableVersionCode?.let { "Build $it" }
        ?: "A new build"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (update.status == AndroidUpdateStatus.Downloaded) "Update ready" else "OpenNOW update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (update.status == AndroidUpdateStatus.Downloaded) {
                        "$version is downloaded and ready to install."
                    } else {
                        "$version is available for this device."
                    },
                )
                update.releaseNotes?.trim()?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        notes,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onPrimary) {
                Text(if (update.status == AndroidUpdateStatus.Downloaded) "Install" else "Download")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDetails) {
                    Text("Details")
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun LoadingScreen(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OpenNowMark(72.dp)
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(text, color = TextMuted)
        }
    }
}

@Composable
private fun LoginScreen(state: OpenNowUiState, viewModel: OpenNowViewModel) {
    val signInFocusRequester = remember { FocusRequester() }
    val tvLogin = state.androidTvProfile
    val deviceCodeLoginAvailable = state.selectedProvider.supportsDeviceCodeLogin
    val preferDeviceCodeLogin = tvLogin && deviceCodeLoginAvailable
    val deviceLoginPrompt = state.deviceLoginPrompt.takeIf { deviceCodeLoginAvailable }
    val normalLoginBusy = state.launchPhase.isNotBlank() && deviceLoginPrompt == null
    LaunchedEffect(preferDeviceCodeLogin, deviceLoginPrompt == null) {
        if (preferDeviceCodeLogin && deviceLoginPrompt == null) {
            runCatching { signInFocusRequester.requestFocus() }
        }
    }
    if (preferDeviceCodeLogin && deviceLoginPrompt != null) {
        TvDeviceLoginScreen(
            prompt = deviceLoginPrompt,
            phase = state.launchPhase,
            onCancel = viewModel::cancelLogin,
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactForPhonePairing = tvLogin && state.localTvConnector.hosting
        val dedicatedPhonePairing = shouldUseDedicatedTvPairingLayout(
            tvProfile = tvLogin,
            hosting = state.localTvConnector.hosting,
            availableWidthDp = maxWidth.value,
            availableHeightDp = maxHeight.value,
        )
        if (dedicatedPhonePairing) {
            TvPhoneSignInConnector(
                state = state,
                viewModel = viewModel,
                dedicated = true,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(if (compactForPhonePairing) 12.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OpenNowMark(if (compactForPhonePairing) 56.dp else 88.dp)
                Spacer(Modifier.height(if (compactForPhonePairing) 8.dp else 20.dp))
                Text(
                    "OpenNOW",
                    color = TextPrimary,
                    style = if (compactForPhonePairing) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Native Android GeForce NOW client",
                    color = TextMuted,
                    style = if (compactForPhonePairing) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(if (compactForPhonePairing) 12.dp else 28.dp))
                ProviderPicker(state.providers, state.selectedProvider, viewModel::selectProvider)
                Spacer(Modifier.height(if (compactForPhonePairing) 8.dp else 16.dp))
                deviceLoginPrompt?.let { prompt ->
                    DeviceLoginPanel(prompt = prompt, phase = state.launchPhase, onCancel = viewModel::cancelLogin)
                } ?: Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.login() },
                        enabled = !normalLoginBusy,
                        modifier = Modifier.focusRequester(signInFocusRequester),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (normalLoginBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            when {
                                state.launchPhase.isNotBlank() -> state.launchPhase
                                preferDeviceCodeLogin -> stringResource(R.string.login_tv_start, state.selectedProvider.displayName)
                                else -> stringResource(R.string.login_with_provider, state.selectedProvider.displayName)
                            },
                        )
                    }
                    if (!tvLogin && deviceCodeLoginAvailable) {
                        TextButton(onClick = { viewModel.loginWithCode() }, enabled = !normalLoginBusy) {
                            Text("Use code sign-in")
                        }
                    }
                    if (tvLogin) {
                        TvPhoneSignInConnector(state = state, viewModel = viewModel)
                    }
                }
                if (state.error != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(state.error.orEmpty(), color = Color(0xffff9f9f))
                }
            }
        }
    }
}

internal fun shouldUseDedicatedTvPairingLayout(
    tvProfile: Boolean,
    hosting: Boolean,
    availableWidthDp: Float,
    availableHeightDp: Float,
): Boolean = tvProfile && hosting && (availableHeightDp < 500f || availableWidthDp < 760f)

@Composable
private fun TvPhoneSignInConnector(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    dedicated: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val connector = state.localTvConnector
    if (!connector.hosting) {
        OutlinedButton(
            onClick = viewModel::startLocalTvConnector,
            enabled = !connector.busy,
            modifier = modifier,
        ) {
            Text(if (connector.busy) "Starting phone pairing…" else "Sign in from OpenNOW on phone")
        }
    } else {
        val qrCode = remember(connector.pairUri) { connector.pairUri?.let(QrCode::encodeText) }
        Card(
            colors = CardDefaults.cardColors(containerColor = PanelAlt),
            shape = RoundedCornerShape(if (dedicated) 26.dp else 18.dp),
            modifier = if (dedicated) {
                modifier.padding(12.dp)
            } else {
                modifier.fillMaxWidth().padding(top = 8.dp)
            },
        ) {
            BoxWithConstraints(if (dedicated) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                val qrSize = if (dedicated) {
                    minOf(maxHeight - 40.dp, maxWidth * 0.36f, 240.dp).coerceAtLeast(152.dp)
                } else {
                    188.dp
                }
                Row(
                    (if (dedicated) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                        .padding(if (dedicated) 18.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (dedicated) 20.dp else 12.dp),
                ) {
                    if (connector.pairedDeviceName == null) {
                        qrCode?.let { code ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White,
                                border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                            ) {
                                QrCodeView(code, Modifier.size(qrSize))
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (dedicated) 10.dp else 8.dp)) {
                        Text(
                            if (connector.pairedDeviceName == null) "Pair your phone" else "Phone connected",
                            color = TextPrimary,
                            style = if (dedicated) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (connector.pairedDeviceName == null) {
                                "Your TV and phone must be on the same Wi-Fi. Scan the QR code with your phone camera; the pairing code expires after five minutes."
                            } else {
                                "${connector.pairedDeviceName} can launch games. Approve trust below for settings, overlays, sessions, and account switching."
                            },
                            color = TextMuted,
                            style = if (dedicated) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                            maxLines = if (dedicated) 3 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (connector.pairedDeviceName == null) {
                            PairingCodeDisplay(connector.pairingCode, compact = !dedicated)
                        }
                        if (connector.pairedDeviceName != null) {
                            SettingSwitch(
                                label = "Trust this phone",
                                checked = connector.pairedDeviceTrusted,
                                description = "Required before the phone can transfer an account or control TV settings and sessions.",
                            ) { trusted -> viewModel.setLocalTvDeviceTrusted(trusted) }
                        }
                        OutlinedButton(onClick = viewModel::stopLocalTvConnector) {
                            Text(if (connector.pairedDeviceName == null) "Cancel pairing" else "Disconnect phone")
                        }
                    }
                }
            }
        }
    }
    connector.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun PairingCodeDisplay(code: String?, compact: Boolean) {
    val digits = code?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: "----"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("PAIRING CODE", color = TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
            digits.forEach { digit ->
                Surface(
                    modifier = Modifier.size(if (compact) 38.dp else 46.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            digit.toString(),
                            color = TextPrimary,
                            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDeviceLoginScreen(prompt: DeviceLoginPrompt, phase: String, onCancel: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        val landscape = maxWidth >= 720.dp
        val qrMaxSize = minOf(
            maxWidth * if (landscape) 0.28f else 0.68f,
            maxHeight * if (landscape) 0.58f else 0.38f,
            340.dp,
        )
        DeviceLoginPanel(
            prompt = prompt,
            phase = phase,
            onCancel = onCancel,
            modifier = Modifier.fillMaxWidth(if (landscape) 0.86f else 1f),
            qrMaxSize = qrMaxSize,
            preferLandscapeLayout = landscape,
            focusCancelOnPrompt = false,
        )
    }
}

@Composable
internal fun DeviceLoginPanel(
    prompt: DeviceLoginPrompt,
    phase: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    qrMaxSize: androidx.compose.ui.unit.Dp = 360.dp,
    preferLandscapeLayout: Boolean = false,
    focusCancelOnPrompt: Boolean = true,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val initialFocusRequester = remember { FocusRequester() }
    val sideBySideLayout = shouldUseSideBySideDeviceLoginLayout(
        orientation = configuration.orientation,
        preferLandscapeLayout = preferLandscapeLayout,
        availableWidthDp = configuration.screenWidthDp,
    )
    val launchUrl = remember(prompt.verificationUriComplete, prompt.verificationUri) {
        prompt.verificationUriComplete ?: prompt.verificationUri
    }
    val qrContent = launchUrl
    var urlActionMessage by remember(launchUrl) { mutableStateOf<String?>(null) }
    val qrCode = remember(qrContent, prompt.verificationUri) {
        QrCode.encodeText(qrContent) ?: QrCode.encodeText(prompt.verificationUri)
    }
    val remainingSeconds by produceState(initialValue = secondsUntil(prompt.expiresAt), prompt.expiresAt) {
        while (value > 0) {
            delay(1000L)
            value = secondsUntil(prompt.expiresAt)
        }
    }
    LaunchedEffect(prompt.userCode, focusCancelOnPrompt) {
        runCatching { initialFocusRequester.requestFocus() }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelAlt, contentColor = TextPrimary),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        if (sideBySideLayout) {
            Row(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceLoginQr(
                    qrCode = qrCode,
                    qrMaxSize = qrMaxSize,
                    modifier = Modifier.weight(0.9f),
                )
                DeviceLoginControls(
                    launchUrl = launchUrl,
                    prompt = prompt,
                    phase = phase,
                    remainingSeconds = remainingSeconds,
                    urlActionMessage = urlActionMessage,
                    onUrlActionMessage = { urlActionMessage = it },
                    onCancel = onCancel,
                    focusRequester = initialFocusRequester,
                    focusCancel = focusCancelOnPrompt,
                    context = context,
                    clipboardManager = clipboardManager,
                    modifier = Modifier.weight(1.1f),
                    showTitle = true,
                )
            }
        } else {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.login_tv_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DeviceLoginQr(qrCode = qrCode, qrMaxSize = qrMaxSize)
                DeviceLoginControls(
                    launchUrl = launchUrl,
                    prompt = prompt,
                    phase = phase,
                    remainingSeconds = remainingSeconds,
                    urlActionMessage = urlActionMessage,
                    onUrlActionMessage = { urlActionMessage = it },
                    onCancel = onCancel,
                    focusRequester = initialFocusRequester,
                    focusCancel = focusCancelOnPrompt,
                    context = context,
                    clipboardManager = clipboardManager,
                    showTitle = false,
                )
            }
        }
    }
}

internal fun shouldUseSideBySideDeviceLoginLayout(
    orientation: Int,
    preferLandscapeLayout: Boolean,
    availableWidthDp: Int,
): Boolean =
    preferLandscapeLayout ||
        (orientation == Configuration.ORIENTATION_LANDSCAPE && availableWidthDp >= DEVICE_LOGIN_SIDE_BY_SIDE_MIN_WIDTH_DP)

@Composable
private fun DeviceLoginQr(qrCode: QrCode?, qrMaxSize: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    qrCode?.let {
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val qrDisplaySize = minOf(maxWidth * 0.92f, qrMaxSize)
            QrCodeView(it, Modifier.size(qrDisplaySize))
        }
    }
}

@Composable
private fun DeviceLoginControls(
    launchUrl: String,
    prompt: DeviceLoginPrompt,
    phase: String,
    remainingSeconds: Int,
    urlActionMessage: String?,
    onUrlActionMessage: (String) -> Unit,
    onCancel: () -> Unit,
    focusRequester: FocusRequester,
    focusCancel: Boolean,
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showTitle) {
            Text(stringResource(R.string.login_tv_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = {
                val opened = openExternalUrl(context, launchUrl)
                if (opened) {
                    onUrlActionMessage("Opening sign-in URL")
                } else {
                    clipboardManager.setText(AnnotatedString(launchUrl))
                    onUrlActionMessage("URL copied")
                }
            },
            modifier = if (focusCancel) Modifier else Modifier.focusRequester(focusRequester),
        ) {
            Text(
                launchUrl,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(prompt.userCode, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.login_tv_status, phase.ifBlank { stringResource(R.string.login_tv_waiting) }), color = TextMuted)
        urlActionMessage?.let {
            Text(it, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.login_tv_expires, remainingSeconds / 60, remainingSeconds % 60), color = TextMuted)
        OutlinedButton(
            onClick = onCancel,
            modifier = if (focusCancel) Modifier.focusRequester(focusRequester) else Modifier,
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

internal fun openExternalUrl(context: android.content.Context, url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

@Composable
internal fun QrCodeView(qrCode: QrCode, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
    ) {
        val quiet = 4
        val cells = qrCode.size + quiet * 2
        val cellSize = floor(min(size.width, size.height) / cells).coerceAtLeast(1f)
        val qrSize = cellSize * cells
        val originX = floor((size.width - qrSize) / 2f)
        val originY = floor((size.height - qrSize) / 2f)
        for (y in 0 until qrCode.size) {
            for (x in 0 until qrCode.size) {
                if (!qrCode.isDark(x, y)) continue
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(originX + (x + quiet) * cellSize, originY + (y + quiet) * cellSize),
                    size = Size(cellSize, cellSize),
                )
            }
        }
    }
}

private fun secondsUntil(deadlineMs: Long): Int =
    ((deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()

private fun isPhoneLandscape(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp): Boolean =
    width > height && minOf(width, height) < PHONE_NAV_RAIL_MAX_SMALLEST_WIDTH

private fun isPhonePortrait(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp): Boolean =
    height >= width && minOf(width, height) < PHONE_NAV_RAIL_MAX_SMALLEST_WIDTH

@Composable
internal fun rememberPhysicalControllerConnected(enabled: Boolean): Boolean {
    val context = LocalContext.current.applicationContext
    var connected by remember { mutableStateOf(enabled && hasConnectedPhysicalController()) }
    DisposableEffect(context, enabled) {
        fun refresh() {
            connected = enabled && hasConnectedPhysicalController()
        }
        refresh()
        if (!enabled) {
            onDispose {}
        } else {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            val listener = object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) = refresh()
                override fun onInputDeviceRemoved(deviceId: Int) = refresh()
                override fun onInputDeviceChanged(deviceId: Int) = refresh()
            }
            inputManager?.registerInputDeviceListener(listener, null)
            onDispose {
                inputManager?.unregisterInputDeviceListener(listener)
            }
        }
    }
    return connected
}

private fun hasConnectedPhysicalController(): Boolean =
    InputDevice.getDeviceIds().any { deviceId ->
        AndroidControllerInput.isControllerDevice(InputDevice.getDevice(deviceId))
    }

internal sealed interface CatalogWallpaperSelection {
    data class BuiltIn(val preset: CatalogBackgroundPreset) : CatalogWallpaperSelection
    data class Custom(val source: String) : CatalogWallpaperSelection
}

internal fun catalogWallpaperSelection(
    preset: CatalogBackgroundPreset,
    customSource: String?,
): CatalogWallpaperSelection =
    customSource
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(CatalogWallpaperSelection::Custom)
        ?: CatalogWallpaperSelection.BuiltIn(preset)

internal fun shouldShowCatalogWallpaper(settings: AppSettings): Boolean =
    settings.nerdCatalogBackground

@Composable
private fun CatalogWallpaperBackdrop(
    settings: AppSettings,
    tvProfile: Boolean,
    width: Dp,
    height: Dp,
) {
    val showBackdrop = shouldShowCatalogWallpaper(settings)
    if (!showBackdrop) {
        return
    }
    val wallpaper = catalogWallpaperSelection(
        preset = settings.catalogBackgroundPreset,
        customSource = settings.nerdCatalogBackgroundUri,
    )
    val scrimAlpha = when {
        tvProfile -> 0.48f
        width > height -> 0.28f
        else -> 0.36f
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        when (wallpaper) {
            is CatalogWallpaperSelection.BuiltIn -> {
                CatalogBuiltInWallpaperBackdrop(wallpaper.preset, Modifier.matchParentSize())
            }
            is CatalogWallpaperSelection.Custom -> {
                val fallbackPainter = painterResource(settings.catalogBackgroundPreset.drawableRes)
                AsyncImage(
                    model = imageDataForSource(wallpaper.source),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = fallbackPainter,
                    error = fallbackPainter,
                    fallback = fallbackPainter,
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = scrimAlpha)),
        )
    }
}

private val CatalogBackgroundPreset.drawableRes: Int
    get() = when (this) {
        CatalogBackgroundPreset.ColorfulAbstract -> R.drawable.catalog_colorful_abstract_background
        CatalogBackgroundPreset.Original -> R.drawable.catalog_default_background
    }

@Composable
private fun CatalogBuiltInWallpaperBackdrop(
    preset: CatalogBackgroundPreset,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(preset.drawableRes),
        contentDescription = null,
        modifier = modifier.background(Color(0xff07100b)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun MainShell(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    musicControl: TopBarMusicControl,
) {
    val context = LocalContext.current
    val inStream = state.page == AppPage.Stream
    val streamingActive = inStream && state.streamStatus != "idle"
    val modalPickerOpen = state.pendingPrintedWasteGame != null || state.pendingStoreChoiceGame != null
    val tvProfile = state.androidTvProfile
    val navAudioController = remember(context) { AndroidNerdAudioController(context.applicationContext) }
    var visibleSearchTarget by remember { mutableStateOf<SearchTarget?>(null) }
    var settingsSearchQuery by remember { mutableStateOf("") }
    var settingsDetailRouteOpen by remember { mutableStateOf(false) }
    var settingsBackRequestToken by remember { mutableStateOf(0) }
    val tvStreamReturnFocusRequester = remember { FocusRequester() }
    var previouslyInStream by remember { mutableStateOf(inStream) }
    val navigationToneEnabled = state.settings.controllerUiSounds && !inStream
    val showMinimizedQueueDock = state.streamLaunchMinimized && shouldShowQueueLaunchStatus(state)
    DisposableEffect(navAudioController) {
        onDispose {
            navAudioController.release()
        }
    }
    LaunchedEffect(state.page) {
        if (state.page != AppPage.Settings) {
            settingsDetailRouteOpen = false
        }
    }
    LaunchedEffect(inStream, tvProfile) {
        val shouldRestoreFocus = shouldRestoreTvNavigationFocus(
            previouslyInStream = previouslyInStream,
            currentlyInStream = inStream,
            tvProfile = tvProfile,
        )
        previouslyInStream = inStream
        if (shouldRestoreFocus) {
            delay(120)
            repeat(3) { attempt ->
                if (runCatching { tvStreamReturnFocusRequester.requestFocus() }.isSuccess) {
                    return@LaunchedEffect
                }
                if (attempt < 2) delay(80)
            }
        }
    }
    fun revealSearch(
        target: SearchTarget = when (state.page) {
            AppPage.Library -> SearchTarget.Library
            AppPage.Settings -> SearchTarget.Settings
            else -> SearchTarget.Store
        },
    ) {
        visibleSearchTarget = target
        if (target == SearchTarget.Store && state.page != AppPage.Home) {
            viewModel.setPage(AppPage.Home)
        } else if (target == SearchTarget.Library && state.page != AppPage.Library) {
            viewModel.setPage(AppPage.Library)
        } else if (target == SearchTarget.Settings && state.page != AppPage.Settings) {
            viewModel.setPage(AppPage.Settings)
        }
    }
    fun navigateFromAppChrome(page: AppPage) {
        if (page == AppPage.Settings) {
            viewModel.recordSettingsIconTap()
        }
        visibleSearchTarget = null
        viewModel.setPage(page)
    }
    BackHandler(enabled = state.selectedGame != null && !inStream) {
        viewModel.clearSelectedGame()
    }
    BackHandler(enabled = tvProfile && !inStream && state.selectedGame == null && state.page != AppPage.Home) {
        viewModel.setPage(AppPage.Home)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        var phoneLandscapeScrollChromeHidden by remember { mutableStateOf(false) }
        val horizontalChrome = maxWidth > maxHeight
        val phoneLandscapeChrome = !tvProfile && !inStream && isPhoneLandscape(maxWidth, maxHeight)
        val portraitChrome = !inStream && maxHeight >= maxWidth
        val showNavigationRail = !inStream && (tvProfile || phoneLandscapeChrome)
        val scrollChromePage = state.page == AppPage.Home || state.page == AppPage.Library
        val tvCatalogChrome = tvProfile && scrollChromePage
        val storeControlsInTopBar = (phoneLandscapeChrome || tvCatalogChrome) && state.page == AppPage.Home
        val libraryControlsInTopBar = (phoneLandscapeChrome || tvCatalogChrome) && state.page == AppPage.Library
        val screenEdgePadding = appContentEdgePaddingDp(state.settings, inStream).dp
        LaunchedEffect(phoneLandscapeChrome, scrollChromePage) {
            if (!phoneLandscapeChrome || !scrollChromePage) {
                phoneLandscapeScrollChromeHidden = false
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        if (!inStream && (state.page == AppPage.Home || state.page == AppPage.Library)) {
            CatalogWallpaperBackdrop(
                settings = state.settings,
                tvProfile = tvProfile,
                width = maxWidth,
                height = maxHeight,
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = if (streamingActive || tvProfile) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            bottomBar = {
                if (!inStream && !showNavigationRail) {
                    Column {
                        if (showMinimizedQueueDock) {
                            MinimizedQueueDock(
                                state = state,
                                onRestore = viewModel::restoreStreamLaunch,
                                onCancel = viewModel::stopStream,
                            )
                        }
                        NavigationBar(
                            containerColor = if (state.page == AppPage.Settings) SettingsBackground else MaterialTheme.colorScheme.background,
                            tonalElevation = 0.dp,
                        ) {
                            BottomNavItem(
                                selected = state.page == AppPage.Home && visibleSearchTarget != SearchTarget.Store,
                                onClick = {
                                    visibleSearchTarget = null
                                    viewModel.setPage(AppPage.Home)
                                },
                                iconRes = R.drawable.ic_tab_store,
                                label = stringResource(R.string.nav_store),
                            )
                            BottomNavItem(
                                selected = visibleSearchTarget != null,
                                onClick = { revealSearch() },
                                iconRes = R.drawable.ic_search,
                                label = stringResource(R.string.nav_search),
                            )
                            BottomNavItem(
                                selected = state.page == AppPage.Library && visibleSearchTarget != SearchTarget.Library,
                                onClick = {
                                    visibleSearchTarget = null
                                    viewModel.setPage(AppPage.Library)
                                },
                                iconRes = R.drawable.ic_tab_library,
                                label = stringResource(R.string.nav_library),
                            )
                            BottomNavItem(
                                selected = state.page == AppPage.Settings && visibleSearchTarget != SearchTarget.Settings,
                                onClick = {
                                    navigateFromAppChrome(AppPage.Settings)
                                },
                                iconRes = R.drawable.ic_tab_settings,
                                label = stringResource(R.string.nav_settings),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .onPreviewKeyEvent { event ->
                        if (isNavigationToneKey(event)) {
                            navAudioController.playButtonTone(navigationToneEnabled)
                        }
                        false
                    },
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(screenEdgePadding),
                ) {
                    if (showNavigationRail) {
                        AppNavigationRail(
                            state = state,
                            activeSearchTarget = visibleSearchTarget,
                            showAppIcon = showNavigationRail && horizontalChrome,
                            largeIcons = phoneLandscapeChrome,
                            showSettingsBack = shouldShowSettingsBackRail(
                                tvProfile = tvProfile,
                                settingsPageOpen = state.page == AppPage.Settings,
                                horizontalChrome = horizontalChrome,
                                detailRouteOpen = settingsDetailRouteOpen,
                            ),
                            showCatalogControllerActions = false,
                            onNavigate = { page ->
                                navigateFromAppChrome(page)
                            },
                            onSearch = { revealSearch(it) },
                            onSettingsBack = { settingsBackRequestToken += 1 },
                            streamReturnFocusRequester = tvStreamReturnFocusRequester,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        AnimatedVisibility(
                            visible =
                                portraitChrome ||
                                    (phoneLandscapeChrome && !phoneLandscapeScrollChromeHidden) ||
                                    tvCatalogChrome,
                        ) {
                            if (!inStream) {
                                TopStatusBar(
                                    state = state,
                                    onResumeActiveSession = viewModel::resumeActiveSession,
                                    onOpenStreamSettings = viewModel::openStreamSettings,
                                    musicControl = musicControl,
                                    showLogo = portraitChrome,
                                ) {
                                    if (storeControlsInTopBar) {
                                        StoreCatalogToolbar(
                                            state = state,
                                            onSortChange = viewModel::setCatalogSort,
                                            onFilterToggle = viewModel::toggleCatalogFilter,
                                            modifier = Modifier.widthIn(max = 220.dp),
                                            compact = true,
                                        )
                                    } else if (libraryControlsInTopBar) {
                                        val orderedLibraryGames = remember(state.libraryGames, state.settings.favoriteGameIds) {
                                            favoriteOrderedGames(state.libraryGames, state.settings.favoriteGameIds)
                                        }
                                        val visibleLibraryGames = remember(orderedLibraryGames, state.librarySearch, state.libraryFilterIds) {
                                            orderedLibraryGames.filter { game ->
                                                gameMatchesSearch(game, state.librarySearch) &&
                                                    gameMatchesLibraryFilters(game, state.libraryFilterIds)
                                            }
                                        }
                                        val libraryFilterOptions = remember(orderedLibraryGames) {
                                            libraryStoreFilterOptions(orderedLibraryGames)
                                        }
                                        LibraryFilterControls(
                                            gameCount = visibleLibraryGames.size,
                                            totalCount = state.libraryGames.size,
                                            options = libraryFilterOptions,
                                            selectedIds = state.libraryFilterIds,
                                            onToggle = viewModel::toggleLibraryFilter,
                                            modifier = Modifier.widthIn(max = 190.dp),
                                            compact = true,
                                            showSelectedChips = false,
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            when (state.page) {
                                AppPage.Home -> HomeScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    tvProfile = tvProfile,
                                    hideChromeWhenScrolled = phoneLandscapeChrome,
                                    controlsInTopBar = storeControlsInTopBar,
                                    searchRequested = visibleSearchTarget == SearchTarget.Store,
                                    onSearchDismissed = {
                                        if (visibleSearchTarget == SearchTarget.Store) visibleSearchTarget = null
                                    },
                                    onScrollChromeHiddenChange = { phoneLandscapeScrollChromeHidden = it },
                                )
                                AppPage.Library -> LibraryScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    tvProfile = tvProfile,
                                    hideChromeWhenScrolled = phoneLandscapeChrome,
                                    controlsInTopBar = libraryControlsInTopBar,
                                    searchRequested = visibleSearchTarget == SearchTarget.Library,
                                    onSearchDismissed = {
                                        if (visibleSearchTarget == SearchTarget.Library) visibleSearchTarget = null
                                    },
                                    onScrollChromeHiddenChange = { phoneLandscapeScrollChromeHidden = it },
                                )
                                AppPage.Settings -> SettingsScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    tvProfile = tvProfile,
                                    searchRequested = visibleSearchTarget == SearchTarget.Settings,
                                    searchQuery = settingsSearchQuery,
                                    backRequestToken = settingsBackRequestToken,
                                    onSearchQueryChange = { next ->
                                        settingsSearchQuery = next
                                        if (next.isBlank() && visibleSearchTarget == SearchTarget.Settings) {
                                            visibleSearchTarget = null
                                        }
                                    },
                                    onDetailRouteChange = { settingsDetailRouteOpen = it },
                                )
                                AppPage.Stream -> StreamScreen(state, viewModel)
                            }
                        }
                        if (showMinimizedQueueDock && showNavigationRail) {
                            MinimizedQueueDock(
                                state = state,
                                onRestore = viewModel::restoreStreamLaunch,
                                onCancel = viewModel::stopStream,
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = state.selectedGame != null && !inStream && !modalPickerOpen,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }) + scaleIn(initialScale = 0.96f),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }) + scaleOut(targetScale = 0.96f),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    state.selectedGame?.let { game ->
                        GameDetailsSheet(
                            game = game,
                            favorite = game.id in state.settings.favoriteGameIds,
                            defaultVariantId = state.settings.defaultGameVariantIds[game.id],
                            fullScreen = tvProfile,
                            safeAreaPadding = screenEdgePadding,
                            onPlay = viewModel::play,
                            onChooseStore = viewModel::chooseStore,
                            onFavorite = viewModel::updateFavorites,
                            connectedTvName = state.localTvConnector.connectedTvName,
                            onPlayOnTv = viewModel::playOnLocalTv,
                            onDismiss = viewModel::clearSelectedGame,
                        )
                    }
                }
                state.pendingPrintedWasteGame?.let { game ->
                    AnimatedLaunchOverlay(Modifier.align(Alignment.Center)) {
                        PrintedWasteSelector(state, game, viewModel)
                    }
                }
                state.pendingStoreChoiceGame?.let { game ->
                    AnimatedLaunchOverlay(Modifier.align(Alignment.Center)) {
                        StoreLaunchSelector(
                            game = game,
                            defaultVariantId = state.settings.defaultGameVariantIds[game.id],
                            onLaunch = viewModel::playVariant,
                            onSetDefaultStore = viewModel::setDefaultGameVariant,
                            onDismiss = viewModel::dismissStoreChoice,
                        )
                    }
                }
            }
        }
    }
}

internal fun shouldRestoreTvNavigationFocus(
    previouslyInStream: Boolean,
    currentlyInStream: Boolean,
    tvProfile: Boolean,
): Boolean = tvProfile && previouslyInStream && !currentlyInStream

@Composable
private fun AppNavigationRail(
    state: OpenNowUiState,
    activeSearchTarget: SearchTarget?,
    showAppIcon: Boolean,
    largeIcons: Boolean,
    showSettingsBack: Boolean,
    showCatalogControllerActions: Boolean,
    onNavigate: (AppPage) -> Unit,
    onSearch: (SearchTarget) -> Unit,
    onSettingsBack: () -> Unit,
    streamReturnFocusRequester: FocusRequester,
) {
    Box(
        modifier = Modifier
            .width(APP_NAV_RAIL_WIDTH)
            .fillMaxHeight()
            .padding(start = 6.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(26.dp),
            color = ChromeScrim,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val canFitCatalogControllerActions = maxHeight >= 440.dp
                if (showAppIcon) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .focusProperties { canFocus = false }
                            .clickable { onNavigate(AppPage.Home) }
                            .padding(top = 12.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        OpenNowAppIcon(
                            if (largeIcons) 44.dp else 34.dp,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(if (showSettingsBack) Alignment.BottomCenter else Alignment.Center)
                        .fillMaxWidth()
                        .padding(bottom = if (showSettingsBack) 8.dp else 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!showAppIcon) {
                        Spacer(Modifier.height(8.dp))
                    }
                    AppNavigationRailItem(
                        selected = state.page == AppPage.Home && activeSearchTarget != SearchTarget.Store,
                        onClick = { onNavigate(AppPage.Home) },
                        iconRes = R.drawable.ic_tab_store,
                        label = stringResource(R.string.nav_store),
                        iconSize = if (largeIcons) 30.dp else 24.dp,
                        focusRequester = streamReturnFocusRequester,
                    )
                    AppNavigationRailItem(
                        selected = activeSearchTarget != null,
                        onClick = {
                            onSearch(
                                when (state.page) {
                                    AppPage.Library -> SearchTarget.Library
                                    AppPage.Settings -> SearchTarget.Settings
                                    else -> SearchTarget.Store
                                },
                            )
                        },
                        iconRes = R.drawable.ic_search,
                        label = stringResource(R.string.nav_search),
                        iconSize = if (largeIcons) 30.dp else 24.dp,
                    )
                    AppNavigationRailItem(
                        selected = state.page == AppPage.Library && activeSearchTarget != SearchTarget.Library,
                        onClick = { onNavigate(AppPage.Library) },
                        iconRes = R.drawable.ic_tab_library,
                        label = stringResource(R.string.nav_library),
                        iconSize = if (largeIcons) 30.dp else 24.dp,
                    )
                    AppNavigationRailItem(
                        selected = state.page == AppPage.Settings && activeSearchTarget != SearchTarget.Settings,
                        onClick = { onNavigate(AppPage.Settings) },
                        iconRes = R.drawable.ic_tab_settings,
                        label = stringResource(R.string.nav_settings),
                        iconSize = if (largeIcons) 30.dp else 24.dp,
                        showConnectionDot = shouldShowLocalTvConnectionDot(
                            tvProfile = state.androidTvProfile,
                            pairedDeviceName = state.localTvConnector.pairedDeviceName,
                        ),
                    )
                    AnimatedVisibility(visible = showCatalogControllerActions && canFitCatalogControllerActions) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(8.dp))
                            ControllerCatalogRailActionHints()
                        }
                    }
                    AnimatedVisibility(visible = showSettingsBack) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(6.dp))
                            AppNavigationRailItem(
                                selected = false,
                                onClick = onSettingsBack,
                                iconRes = R.drawable.ic_arrow_back,
                                label = "Back",
                                iconSize = if (largeIcons) 30.dp else 24.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldShowLocalTvConnectionDot(tvProfile: Boolean, pairedDeviceName: String?): Boolean =
    tvProfile && !pairedDeviceName.isNullOrBlank()

internal fun shouldShowSettingsBackRail(
    tvProfile: Boolean,
    settingsPageOpen: Boolean,
    horizontalChrome: Boolean,
    detailRouteOpen: Boolean,
): Boolean = !tvProfile && settingsPageOpen && horizontalChrome && detailRouteOpen

@Composable
private fun AppNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    focusRequester: FocusRequester? = null,
    showConnectionDot: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .then(
                if (focused) Modifier.border(2.dp, accent, RoundedCornerShape(12.dp)) else Modifier
            ),
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = accent,
            selectedTextColor = accent,
            indicatorColor = if (focused) accent.copy(alpha = 0.35f) else accent.copy(alpha = 0.18f),
            unselectedIconColor = if (focused) Color.White else TextMuted,
            unselectedTextColor = if (focused) Color.White else TextMuted,
        ),
        icon = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    modifier = Modifier.size(iconSize),
                )
                if (showConnectionDot) {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xffb56cff)),
                    )
                }
            }
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

private data class TopBarMusicControl(
    val visible: Boolean,
    val playing: Boolean,
    val muted: Boolean,
    val onToggle: () -> Unit,
)

@Composable
private fun RowScope.BottomNavItem(selected: Boolean, onClick: () -> Unit, iconRes: Int, label: String) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            unselectedIconColor = TextMuted,
            unselectedTextColor = TextMuted,
        ),
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun TopStatusBar(
    state: OpenNowUiState,
    onResumeActiveSession: () -> Unit,
    onOpenStreamSettings: () -> Unit,
    musicControl: TopBarMusicControl,
    showLogo: Boolean = true,
    content: @Composable RowScope.() -> Unit = {},
) {
    val displayName = state.authSession?.user?.displayName ?: "OpenNOW"
    val tier = state.subscriptionInfo?.membershipTier ?: state.authSession?.user?.membershipTier ?: "GFN"
    val barScrim = if (showLogo) ChromeScrim else Color.Transparent
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 5.dp),
        shape = RoundedCornerShape(24.dp),
        color = barScrim,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showLogo) {
                OpenNowMark(30.dp)
                Spacer(Modifier.width(8.dp))
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOf(displayName, tier).filter { it.isNotBlank() }.joinToString(" • "),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                content()
                if (state.settings.nerdMode) {
                    TopStatusDetails(state, onOpenStreamSettings)
                }
            }
            if (musicControl.visible) {
                Spacer(Modifier.width(6.dp))
                TopBarMusicButton(musicControl)
            }
            if (state.activeSession != null) {
                Spacer(Modifier.width(6.dp))
                ElevatedButton(
                    onClick = onResumeActiveSession,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(stringResource(R.string.action_resume), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TopStatusDetails(
    state: OpenNowUiState,
    onOpenStreamSettings: () -> Unit,
) {
    val stream = state.activeStreamSettings ?: state.settings.stream
    val summary = streamStatusSummary(stream)
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .height(TopBarCompactControlHeight)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = "Open Stream settings: $summary" }
                .clickable(onClick = onOpenStreamSettings)
                .then(
                    if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier,
                ),
            shape = shape,
            color = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else PanelAlt.copy(alpha = 0.9f),
            tonalElevation = 0.dp,
        ) {
            Box(Modifier.fillMaxHeight().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    summary,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TopBarMusicButton(control: TopBarMusicControl) {
    val description = when {
        control.muted -> "Music muted"
        control.playing -> "Music playing"
        else -> "Music ready"
    }
    Surface(
        modifier = Modifier
            .width(38.dp)
            .height(TopBarCompactControlHeight)
            .semantics { contentDescription = description }
            .clickable(onClick = control.onToggle),
        shape = RoundedCornerShape(999.dp),
        color = if (control.muted) Color(0xff33181c).copy(alpha = 0.92f) else PanelAlt.copy(alpha = 0.78f),
        tonalElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (control.muted) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume_off),
                    contentDescription = null,
                    tint = Color(0xffffb8bf),
                    modifier = Modifier.size(17.dp),
                )
            } else {
                MusicBars(playing = control.playing)
            }
        }
    }
}

@Composable
private fun MusicBars(playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "top-bar-music-bars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "top-bar-music-bars-phase",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(width = 18.dp, height = 16.dp)) {
        val barWidth = size.width / 5.8f
        val gap = (size.width - barWidth * 3f) / 2f
        repeat(3) { index ->
            val wave = if (playing) {
                ((sin((phase.toDouble() * 6.283185307179586) + index * 1.35) + 1.0) / 2.0).toFloat()
            } else {
                0.36f + index * 0.12f
            }
            val barHeight = size.height * (0.32f + wave * 0.58f)
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth, barWidth),
            )
        }
    }
}

private fun streamStatusSummary(stream: StreamSettings): String =
    listOf(
        formatTopBarResolution(stream.resolution),
        stream.aspectRatio,
        stream.codec.name,
        "${stream.fps} FPS",
    ).filter { it.isNotBlank() }.joinToString(" • ")

private fun formatTopBarResolution(resolution: String): String {
    val parts = resolution.lowercase(Locale.US).split("x", limit = 2)
    return if (parts.size == 2 && parts.all { it.trim().isNotBlank() }) {
        "${parts[0].trim()} × ${parts[1].trim()}"
    } else {
        resolution
    }
}

@Composable
internal fun NativeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    searching: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onOpen: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) onQueryChange(spoken)
        }
    }
    val voiceSearchIntent = remember(placeholder) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, placeholder)
    }
    Surface(
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = if (query.isBlank()) 18.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .onFocusChanged { if (it.isFocused) onOpen?.invoke() }
                    .onPreviewKeyEvent { handleDpadFocusMove(it, focusManager) },
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth()) {
                        if (query.isBlank()) {
                            Text(
                                placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotBlank()) {
                if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clear),
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
            } else {
                if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { runCatching { speechLauncher.launch(voiceSearchIntent) } }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = stringResource(R.string.search_voice),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private fun handleDpadFocusMove(event: androidx.compose.ui.input.key.KeyEvent, focusManager: FocusManager): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val direction = when (event.key) {
        Key.DirectionUp -> FocusDirection.Up
        Key.DirectionDown -> FocusDirection.Down
        Key.DirectionLeft -> FocusDirection.Left
        Key.DirectionRight -> FocusDirection.Right
        else -> return false
    }
    return focusManager.moveFocus(direction)
}

internal fun Modifier.lockedFocusGroup(): Modifier =
    focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()

private fun isNavigationToneKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        event.key in setOf(
            Key.DirectionUp,
            Key.DirectionDown,
            Key.DirectionLeft,
            Key.DirectionRight,
        )

internal fun handleVerticalDpadFocusMove(event: androidx.compose.ui.input.key.KeyEvent, focusManager: FocusManager): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val direction = when (event.key) {
        Key.DirectionUp -> FocusDirection.Up
        Key.DirectionDown -> FocusDirection.Down
        else -> return false
    }
    return focusManager.moveFocus(direction)
}

/**
 * Key event handler for Compose Sliders when navigated by TV remote or D-pad controller.
 * - D-pad Up/Down  → moves focus to the next/previous focusable element.
 * - D-pad Left     → decrements the slider value by [step], clamped to [min].
 * - D-pad Right    → increments the slider value by [step], clamped to [max].
 * Returns true when the event is consumed (Left/Right) so that Compose does not
 * move focus sideways instead of changing the value.
 */
internal fun handleSliderDpadInput(
    event: androidx.compose.ui.input.key.KeyEvent,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    focusManager: FocusManager,
    onValueAdjusted: (Float) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
        Key.DirectionLeft -> {
            val newValue = (value - step).coerceIn(min, max)
            onValueAdjusted(newValue)
            true
        }
        Key.DirectionRight -> {
            val newValue = (value + step).coerceIn(min, max)
            onValueAdjusted(newValue)
            true
        }
        else -> false
    }
}

internal fun isTvActivateKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
    event.type == KeyEventType.KeyUp &&
        event.key in setOf(
            Key.DirectionCenter,
            Key.Enter,
            Key.NumPadEnter,
        )

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeScreen(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    tvProfile: Boolean,
    hideChromeWhenScrolled: Boolean,
    controlsInTopBar: Boolean,
    searchRequested: Boolean,
    onSearchDismissed: () -> Unit,
    onScrollChromeHiddenChange: (Boolean) -> Unit,
) {
    val visibleGames = state.games.ifEmpty { state.catalogResult.games }
    val searchingCatalog = state.loadingGames && state.catalogSearch.isNotBlank()
    val gridState = rememberLazyGridState()
    val searchFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val showSearch = searchRequested || state.catalogSearch.isNotBlank()
    val showScrollActions = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 80
    val scrolledAwayFromTop = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
    val hideScrollChrome = hideChromeWhenScrolled && scrolledAwayFromTop
    LaunchedEffect(hideScrollChrome) {
        onScrollChromeHiddenChange(hideScrollChrome)
    }
    DisposableEffect(Unit) {
        onDispose { onScrollChromeHiddenChange(false) }
    }
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            delay(90)
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }
    SwipeToRefreshContainer(
        refreshing = state.loadingGames,
        enabled = !tvProfile,
        showRefreshIndicator = !searchingCatalog,
        onRefresh = viewModel::refreshGames,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        top = if (controlsInTopBar) 4.dp else 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(visible = showSearch) {
                    NativeSearchField(
                        modifier = Modifier.fillMaxWidth(),
                        query = state.catalogSearch,
                        onQueryChange = { next ->
                            viewModel.setCatalogSearch(next)
                            if (next.isBlank()) onSearchDismissed()
                        },
                        placeholder = stringResource(R.string.search_games),
                        searching = searchingCatalog,
                        focusRequester = searchFocusRequester,
                        onOpen = {
                            if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
                                scope.launch { gridState.animateScrollToItem(0) }
                            }
                        },
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        },
                ) {
                    if (state.loadingGames && visibleGames.isEmpty()) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StoreScrollableControls(
                                state = state,
                                onSortChange = viewModel::setCatalogSort,
                                onFilterToggle = viewModel::toggleCatalogFilter,
                                showToolbar = !controlsInTopBar,
                            )
                            RefreshingGamesPlaceholder(
                                settings = state.settings,
                                tvProfile = tvProfile,
                                storeLayout = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        StoreGameGrid(
                            games = visibleGames,
                            favoriteIds = state.settings.favoriteGameIds,
                            settings = state.settings,
                            tvProfile = tvProfile,
                            state = state,
                            onSelect = viewModel::selectGame,
                            onFavorite = viewModel::updateFavorites,
                            onPlay = viewModel::play,
                            onChooseStore = viewModel::chooseStore,
                            onSortChange = viewModel::setCatalogSort,
                            onFilterToggle = viewModel::toggleCatalogFilter,
                            onClearSearch = {
                                viewModel.setCatalogSearch("")
                                onSearchDismissed()
                            },
                            onClearFilters = viewModel::clearCatalogFilters,
                            gridState = gridState,
                            showToolbar = !controlsInTopBar,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (showScrollActions) {
                        Box(Modifier.align(Alignment.BottomEnd).padding(2.dp)) {
                            StoreScrollActionButton(
                                iconRes = R.drawable.ic_arrow_up,
                                contentDescription = stringResource(R.string.action_scroll_top),
                            ) {
                                scope.launch { gridState.animateScrollToItem(0) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreScrollableControls(
    state: OpenNowUiState,
    onSortChange: (String) -> Unit,
    onFilterToggle: (String) -> Unit,
    showToolbar: Boolean = true,
) {
    val filterGroups = catalogVisibleFilterGroups(state.catalogResult.filterGroups)
    val filterOptions = catalogFilterOptions(filterGroups)
    val hasSelectedFilters = state.catalogFilterIds.isNotEmpty()
    val hasError = !state.error.isNullOrBlank()
    if (!showToolbar && !hasSelectedFilters && !hasError) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showToolbar) {
            StoreCatalogToolbar(
                state = state,
                onSortChange = onSortChange,
                onFilterToggle = onFilterToggle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SelectedFilterChips(options = filterOptions, selectedIds = state.catalogFilterIds, onToggle = onFilterToggle)
        InlineErrorNotice(error = state.error)
    }
}

@Composable
private fun StoreCatalogToolbar(
    state: OpenNowUiState,
    onSortChange: (String) -> Unit,
    onFilterToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val filterGroups = catalogVisibleFilterGroups(state.catalogResult.filterGroups)
    val filterOptions = catalogFilterOptions(filterGroups)
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortPicker(
            options = state.catalogResult.sortOptions,
            selected = state.catalogSortId,
            onSelect = onSortChange,
            modifier = Modifier.width(if (compact) 118.dp else 172.dp),
            compact = compact,
        )
        if (filterOptions.isNotEmpty()) {
            FilterMenu(options = filterOptions, selectedIds = state.catalogFilterIds, onToggle = onFilterToggle, compact = compact)
        }
    }
}

@Composable
private fun InlineErrorNotice(error: String?) {
    if (error.isNullOrBlank()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xff33181c),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                compactErrorTitle(error),
                color = Color(0xffffb8bf),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                compactErrorBody(error),
                color = Color(0xffffb8bf),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun compactErrorTitle(error: String): String =
    when {
        error.contains("DNS lookup failed", ignoreCase = true) -> "Network lookup failed"
        error.contains("Unable to resolve host", ignoreCase = true) -> "Network lookup failed"
        else -> "Something went wrong"
    }

private fun compactErrorBody(error: String): String =
    error
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .let { if (it.length > 180) "${it.take(177)}..." else it }

@Composable
private fun StoreScrollActionButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = PanelAlt.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    tvProfile: Boolean,
    hideChromeWhenScrolled: Boolean,
    controlsInTopBar: Boolean,
    searchRequested: Boolean,
    onSearchDismissed: () -> Unit,
    onScrollChromeHiddenChange: (Boolean) -> Unit,
) {
    val orderedGames = remember(state.libraryGames, state.settings.favoriteGameIds) {
        favoriteOrderedGames(state.libraryGames, state.settings.favoriteGameIds)
    }
    val filterOptions = remember(orderedGames) {
        libraryStoreFilterOptions(orderedGames)
    }
    val games = remember(orderedGames, state.librarySearch, state.libraryFilterIds) {
        orderedGames.filter { game ->
            gameMatchesSearch(game, state.librarySearch) && gameMatchesLibraryFilters(game, state.libraryFilterIds)
        }
    }
    val gridState = rememberLazyGridState()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val showSearch = searchRequested || state.librarySearch.isNotBlank()
    val scrolledAwayFromTop = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
    val hideScrollChrome = hideChromeWhenScrolled && scrolledAwayFromTop
    LaunchedEffect(hideScrollChrome) {
        onScrollChromeHiddenChange(hideScrollChrome)
    }
    DisposableEffect(Unit) {
        onDispose { onScrollChromeHiddenChange(false) }
    }
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            delay(90)
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }
    SwipeToRefreshContainer(
        refreshing = state.loadingGames,
        enabled = !tvProfile,
        onRefresh = viewModel::refreshGames,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        top = if (controlsInTopBar) 4.dp else 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(visible = showSearch) {
                    NativeSearchField(
                        modifier = Modifier.fillMaxWidth(),
                        query = state.librarySearch,
                        onQueryChange = { next ->
                            viewModel.setLibrarySearch(next)
                            if (next.isBlank()) onSearchDismissed()
                        },
                        placeholder = "Search library",
                        focusRequester = searchFocusRequester,
                    )
                }
                LibraryFilterControls(
                    gameCount = games.size,
                    totalCount = state.libraryGames.size,
                    options = filterOptions,
                    selectedIds = state.libraryFilterIds,
                    onToggle = viewModel::toggleLibraryFilter,
                    showToolbar = !controlsInTopBar,
                )
                if (state.loadingGames && state.libraryGames.isEmpty()) {
                    RefreshingGamesPlaceholder(
                        settings = state.settings,
                        tvProfile = tvProfile,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    GameGrid(
                        games,
                        state.settings.favoriteGameIds,
                        state.settings,
                        tvProfile,
                        viewModel::selectGame,
                        viewModel::updateFavorites,
                        viewModel::play,
                        viewModel::chooseStore,
                        modifier = Modifier.weight(1f),
                        gridState = gridState,
                        emptyContent = {
                            val hasSearch = state.librarySearch.isNotBlank()
                            val hasFilters = state.libraryFilterIds.isNotEmpty()
                            if ((hasSearch || hasFilters) && state.libraryGames.isNotEmpty()) {
                                SearchEmptyState(
                                    title = stringResource(R.string.library_empty_search_title),
                                    message = when {
                                        hasSearch && hasFilters -> stringResource(R.string.library_empty_search_filters_body)
                                        hasSearch -> stringResource(R.string.library_empty_search_body)
                                        else -> stringResource(R.string.library_empty_filters_body)
                                    },
                                    onClearSearch = if (hasSearch) {
                                        {
                                            viewModel.setLibrarySearch("")
                                            onSearchDismissed()
                                        }
                                    } else {
                                        null
                                    },
                                    onClearFilters = if (hasFilters) {
                                        viewModel::clearLibraryFilters
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                Text(stringResource(R.string.no_games_loaded), color = TextMuted)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryFilterControls(
    gameCount: Int,
    totalCount: Int,
    options: List<CatalogFilterOption>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showToolbar: Boolean = true,
    showSelectedChips: Boolean = true,
) {
    if (!showToolbar && (!showSelectedChips || selectedIds.isEmpty())) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showToolbar) {
            Row(
                if (compact) Modifier else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val countModifier = if (compact) Modifier else Modifier.weight(1f)
                Text(
                    text = if (gameCount == totalCount) {
                        stringResource(R.string.library_count, totalCount)
                    } else {
                        "$gameCount / ${stringResource(R.string.library_count, totalCount)}"
                    },
                    color = TextMuted,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = countModifier,
                )
                if (options.isNotEmpty()) {
                    FilterMenu(options = options, selectedIds = selectedIds, onToggle = onToggle, compact = compact)
                }
            }
        }
        if (showSelectedChips) {
            SelectedFilterChips(options = options, selectedIds = selectedIds, onToggle = onToggle)
        }
    }
}

private fun libraryStoreFilterOptions(games: List<GameInfo>): List<CatalogFilterOption> {
    val labelsById = linkedMapOf<String, String>()
    games.forEach { game ->
        libraryStoreFilterIds(game).forEach { (id, label) ->
            labelsById.putIfAbsent(id, label)
        }
    }
    return labelsById.entries
        .sortedBy { it.value.lowercase(Locale.US) }
        .map { (id, label) ->
            CatalogFilterOption(
                id = id,
                rawId = id.removePrefix(LIBRARY_STORE_FILTER_PREFIX),
                label = label,
                groupId = "library_store",
                groupLabel = "Launcher",
            )
        }
}

private fun gameMatchesLibraryFilters(game: GameInfo, selectedIds: List<String>): Boolean {
    if (selectedIds.isEmpty()) return true
    val gameFilterIds = libraryStoreFilterIds(game).map { it.first }.toSet()
    return selectedIds.any { it in gameFilterIds }
}

private fun libraryStoreFilterIds(game: GameInfo): List<Pair<String, String>> {
    val labels = libraryStoreDisplayNames(game)
    return labels
        .mapNotNull { label ->
            val normalized = normalizeGameStore(label)
            if (normalized.isBlank()) return@mapNotNull null
            LIBRARY_STORE_FILTER_PREFIX + normalized to label
        }
        .distinctBy { it.first }
}

private const val LIBRARY_STORE_FILTER_PREFIX = "library_store:"

@Composable
private fun ActiveSessionResumeCard(
    state: OpenNowUiState,
    onResumeActiveSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.activeSession ?: return
    val game = activeSessionGame(state, active)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PanelAlt.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UrlImage(
                game?.imageUrl,
                Modifier
                    .width(44.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Resume cloud session", color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    game?.title ?: "App ${active.appId}",
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    activeSessionSummary(active),
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onResumeActiveSession, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.action_resume), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun activeSessionGame(state: OpenNowUiState, active: ActiveSessionInfo): GameInfo? =
    (state.games + state.libraryGames).firstOrNull { game ->
        game.launchAppId == active.appId.toString() ||
            game.variants.any { variant -> variant.id == active.appId.toString() }
    }

private fun activeSessionSummary(active: ActiveSessionInfo): String =
    listOfNotNull(
        when (active.status) {
            1 -> active.queuePosition?.takeIf { it > 0 }?.let { "Queue $it" } ?: "Starting"
            2, 3 -> "Ready"
            else -> "Active"
        },
        active.resolution,
        active.fps?.let { "${it} FPS" },
        active.gpuType,
        active.sessionId.take(8).takeIf { it.isNotBlank() }?.let { "Session $it" },
    ).joinToString(" - ")

@Composable
private fun SearchEmptyState(
    title: String,
    message: String,
    onClearSearch: (() -> Unit)? = null,
    onClearFilters: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onClearSearch?.let { clearSearch ->
                OutlinedButton(onClick = clearSearch) {
                    Text(stringResource(R.string.search_clear), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            onClearFilters?.let { clearFilters ->
                OutlinedButton(onClick = clearFilters) {
                    Text(stringResource(R.string.action_clear_filters), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RefreshingGamesPlaceholder(
    settings: AppSettings,
    tvProfile: Boolean,
    storeLayout: Boolean = false,
    modifier: Modifier = Modifier,
) {
    GameGridSkeleton(
        settings = settings,
        tvProfile = tvProfile,
        storeLayout = storeLayout,
        modifier = modifier,
    )
}

private val LocalShimmerOffset = staticCompositionLocalOf<Float?> { null }
private val LocalTvLoadingPulse = staticCompositionLocalOf<Float?> { null }
private val LocalTvLoadingProfile = staticCompositionLocalOf { false }
private val LocalTouchControllerStyle = staticCompositionLocalOf { TouchControllerStyle.V1 }

@Composable
private fun GameGridSkeleton(
    settings: AppSettings,
    tvProfile: Boolean,
    storeLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = settings.posterSizeScale.coerceIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE)
    val compact = settings.compactGameCards
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val shimmerOffset: Float?
    val tvPulse: Float?
    if (tvProfile) {
        val transition = rememberInfiniteTransition(label = "loading-pulse-global")
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loading-pulse-global",
        )
        shimmerOffset = null
        tvPulse = pulse
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer-global")
        val shimmer by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_150, easing = LinearEasing),
            ),
            label = "shimmer-offset-global",
        )
        shimmerOffset = shimmer
        tvPulse = null
    }

    CompositionLocalProvider(
        LocalShimmerOffset provides shimmerOffset,
        LocalTvLoadingPulse provides tvPulse,
    ) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val gridSpec = gameGridSpec(maxWidth, compact, landscapeLayout, settings, handheldLayout = !tvProfile)
            val placeholderItems = remember(gridSpec.columns, storeLayout) {
                List(gridSpec.columns * if (storeLayout) 4 else 3) { it }
            }
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(gridSpec.columns),
                contentPadding = gridSpec.contentPadding,
                horizontalArrangement = Arrangement.spacedBy(gridSpec.horizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpec.verticalSpacing),
                userScrollEnabled = false,
            ) {
                if (storeLayout) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StoreStartRailsSkeleton(
                            settings = settings,
                            tvProfile = tvProfile,
                        )
                    }
                }
                gridItems(placeholderItems, key = { it }) {
                    GameCardSkeleton(
                        cardHeight = gridSpec.cardHeight * scale,
                        squareCard = gridSpec.squareCards,
                        thumbnailPlayOverlay = !tvProfile,
                        showStoreLabels = shouldShowGameStoreLabels(
                            tvProfile = tvProfile,
                            enabled = settings.showGameStoreLabels,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreStartRailsSkeleton(
    settings: AppSettings,
    tvProfile: Boolean,
) {
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            StoreRailSectionSkeleton(
                expressiveUi = settings.expressiveUi,
                tvProfile = tvProfile,
                landscapeLayout = landscapeLayout,
                cardScale = settings.posterSizeScale,
            )
        }
    }
}

@Composable
private fun StoreRailSectionSkeleton(
    expressiveUi: Boolean,
    tvProfile: Boolean,
    landscapeLayout: Boolean,
    cardScale: Float,
) {
    val spacing = 10.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonLine(widthFraction = 0.34f, height = 15.dp)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            val baseCardWidth = storeRailCardWidth(tvProfile, landscapeLayout)
            val visibleCount = storeRailVisibleCardCount(
                availableWidthDp = maxWidth.value,
                baseCardWidthDp = baseCardWidth.value,
                spacingDp = spacing.value,
                cardScale = cardScale,
            )
            val fittedCardWidth = ((maxWidth.value - spacing.value * (visibleCount - 1)) / visibleCount)
                .coerceAtLeast(1f)
                .dp
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                repeat(visibleCount) {
                    StoreRailGameCardSkeleton(
                        width = fittedCardWidth,
                        expressiveUi = expressiveUi,
                        portraitCard = !tvProfile,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreRailGameCardSkeleton(
    width: Dp,
    expressiveUi: Boolean,
    portraitCard: Boolean,
) {
    val shape = RoundedCornerShape(if (expressiveUi) 12.dp else 8.dp)
    Surface(
        modifier = Modifier
            .width(width)
            .aspectRatio(if (portraitCard) GAME_BOX_ART_ASPECT_RATIO else 1f)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
        shape = shape,
        color = Color.Black,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            LoadingShimmer(Modifier.fillMaxSize())
            SkeletonCircle(
                size = 44.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )
            SkeletonCircle(
                size = 44.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun GameCardSkeleton(
    cardHeight: Dp,
    squareCard: Boolean,
    thumbnailPlayOverlay: Boolean,
    showStoreLabels: Boolean,
) {
    val cardShape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    thumbnailPlayOverlay -> Modifier.aspectRatio(GAME_BOX_ART_ASPECT_RATIO)
                    squareCard -> Modifier.aspectRatio(1f)
                    else -> Modifier.height(cardHeight)
                },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
        shape = cardShape,
    ) {
        if (thumbnailPlayOverlay) {
            Box(Modifier.fillMaxSize()) {
                LoadingShimmer(Modifier.fillMaxSize())
                SkeletonCircle(
                    size = 44.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
                SkeletonCircle(
                    size = 44.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LoadingShimmer(Modifier.fillMaxSize())
                    SkeletonCircle(
                        size = 44.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
                if (showStoreLabels) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SkeletonLine(widthFraction = 0.62f)
                    }
                }
                Box(Modifier.padding(start = 9.dp, end = 9.dp, bottom = 9.dp)) {
                    LoadingShimmer(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(999.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: Dp = 9.dp) {
    LoadingShimmer(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(999.dp)),
    )
}

@Composable
private fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    LoadingShimmer(
        modifier
            .size(size)
            .clip(CircleShape),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToRefreshContainer(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showRefreshIndicator: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) {
            content()
        }
        return
    }
    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pullRefreshState,
        indicator = {
            if (showRefreshIndicator) {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
    ) {
        content()
    }
}

@Composable
private fun GameGrid(
    games: List<GameInfo>,
    favoriteIds: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    modifier: Modifier = Modifier,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    emptyContent: (@Composable () -> Unit)? = null,
) {
    if (games.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (emptyContent != null) {
                emptyContent()
            } else {
                Text(stringResource(R.string.no_games_loaded), color = TextMuted)
            }
        }
        return
    }
    val scale = settings.posterSizeScale.coerceIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE)
    val compact = settings.compactGameCards
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val physicalControllerConnected = rememberPhysicalControllerConnected(enabled = landscapeLayout && !tvProfile)
    val controllerActionMode = landscapeLayout && !tvProfile && physicalControllerConnected
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gridSpec = gameGridSpec(maxWidth, compact, landscapeLayout, settings, handheldLayout = !tvProfile)
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            columns = GridCells.Fixed(gridSpec.columns),
            contentPadding = gridSpec.contentPadding,
            horizontalArrangement = Arrangement.spacedBy(gridSpec.horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(gridSpec.verticalSpacing),
        ) {
            gridItems(games, key = { it.id }) { game ->
                GameCard(
                    game = game,
                    favorite = game.id in favoriteIds,
                    tvProfile = tvProfile,
                    expressiveUi = settings.expressiveUi,
                    controllerBackgroundAnimations = settings.controllerBackgroundAnimations,
                    showGameStoreLabels = shouldShowGameStoreLabels(
                        tvProfile = tvProfile,
                        enabled = settings.showGameStoreLabels,
                    ),
                    cardHeight = gridSpec.cardHeight * scale,
                    squareCard = gridSpec.squareCards,
                    thumbnailPlayOverlay = !tvProfile,
                    controllerActionMode = controllerActionMode,
                    onSelect = onSelect,
                    onFavorite = onFavorite,
                    onPlay = onPlay,
                    onChooseStore = onChooseStore,
                )
            }
        }
    }
}

@Composable
private fun StoreGameGrid(
    games: List<GameInfo>,
    favoriteIds: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    state: OpenNowUiState,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    onSortChange: (String) -> Unit,
    onFilterToggle: (String) -> Unit,
    onClearSearch: () -> Unit,
    onClearFilters: () -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    showToolbar: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StoreScrollableControls(state, onSortChange, onFilterToggle, showToolbar = showToolbar)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val hasSearch = state.catalogSearch.isNotBlank()
                val hasFilters = state.catalogFilterIds.isNotEmpty()
                if (hasSearch || hasFilters) {
                    SearchEmptyState(
                        title = stringResource(R.string.store_empty_search_title),
                        message = when {
                            hasSearch && hasFilters -> stringResource(R.string.store_empty_search_filters_body)
                            hasSearch -> stringResource(R.string.store_empty_search_body)
                            else -> stringResource(R.string.store_empty_filters_body)
                        },
                        onClearSearch = if (hasSearch) onClearSearch else null,
                        onClearFilters = if (hasFilters) onClearFilters else null,
                    )
                } else {
                    Text(stringResource(R.string.no_games_loaded), color = TextMuted)
                }
            }
        }
        return
    }
    val scale = settings.posterSizeScale.coerceIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE)
    val compact = settings.compactGameCards
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val physicalControllerConnected = rememberPhysicalControllerConnected(enabled = landscapeLayout && !tvProfile)
    val controllerActionMode = landscapeLayout && !tvProfile && physicalControllerConnected
    val showControlsHeader = showToolbar || state.catalogFilterIds.isNotEmpty() || !state.error.isNullOrBlank()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gridSpec = gameGridSpec(maxWidth, compact, landscapeLayout, settings, handheldLayout = !tvProfile)
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            columns = GridCells.Fixed(gridSpec.columns),
            contentPadding = gridSpec.contentPadding,
            horizontalArrangement = Arrangement.spacedBy(gridSpec.horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(gridSpec.verticalSpacing),
        ) {
            if (showControlsHeader) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StoreScrollableControls(state, onSortChange, onFilterToggle, showToolbar = showToolbar)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                StoreStartRails(
                    games = games,
                    libraryGames = state.libraryGames,
                    favoriteIds = favoriteIds,
                    queuedGameKeys = state.queuedGameKeys,
                    settings = settings,
                    tvProfile = tvProfile,
                    controllerActionMode = controllerActionMode,
                    onSelect = onSelect,
                    onFavorite = onFavorite,
                    onPlay = onPlay,
                    onChooseStore = onChooseStore,
                )
            }
            if (games.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Recommendations",
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
            }
            gridItems(games, key = { it.id }) { game ->
                GameCard(
                    game = game,
                    favorite = game.id in favoriteIds,
                    tvProfile = tvProfile,
                    expressiveUi = settings.expressiveUi,
                    controllerBackgroundAnimations = settings.controllerBackgroundAnimations,
                    showGameStoreLabels = shouldShowGameStoreLabels(
                        tvProfile = tvProfile,
                        enabled = settings.showGameStoreLabels,
                    ),
                    cardHeight = gridSpec.cardHeight * scale,
                    squareCard = gridSpec.squareCards,
                    thumbnailPlayOverlay = !tvProfile,
                    controllerActionMode = controllerActionMode,
                    onSelect = onSelect,
                    onFavorite = onFavorite,
                    onPlay = onPlay,
                    onChooseStore = onChooseStore,
                )
            }
        }
    }
}

@Composable
private fun StoreStartRails(
    games: List<GameInfo>,
    libraryGames: List<GameInfo>,
    favoriteIds: List<String>,
    queuedGameKeys: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    controllerActionMode: Boolean,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    val jumpBackIn = remember(games, libraryGames, favoriteIds, queuedGameKeys) {
        jumpBackInGames(games, libraryGames, favoriteIds, queuedGameKeys)
    }
    val comingNext = remember(games, jumpBackIn) {
        comingNextStoreGames(
            games = games,
            excludedGames = jumpBackIn,
        )
    }
    if (jumpBackIn.isEmpty() && comingNext.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (jumpBackIn.isNotEmpty()) {
            StoreRailSection(
                title = stringResource(R.string.store_jump_back_in),
                games = jumpBackIn,
                favoriteIds = favoriteIds,
                settings = settings,
                tvProfile = tvProfile,
                controllerActionMode = controllerActionMode,
                onSelect = onSelect,
                onFavorite = onFavorite,
                onPlay = onPlay,
                onChooseStore = onChooseStore,
            )
        }
        if (comingNext.isNotEmpty()) {
            StoreRailSection(
                title = stringResource(R.string.store_coming_next),
                games = comingNext,
                favoriteIds = favoriteIds,
                settings = settings,
                tvProfile = tvProfile,
                controllerActionMode = controllerActionMode,
                onSelect = onSelect,
                onFavorite = onFavorite,
                onPlay = onPlay,
                onChooseStore = onChooseStore,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreComingNextCarousel(
    title: String,
    games: List<GameInfo>,
    favoriteIds: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    controllerActionMode: Boolean,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    if (games.isEmpty()) return
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var page by remember(games) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val enhancedControllerFocus = shouldShowEnhancedControllerFocus(
        focused = focused,
        tvProfile = tvProfile,
        controllerActionMode = controllerActionMode,
    )
    LaunchedEffect(games, page, focused) {
        if (games.size > 1 && !focused && settings.controllerBackgroundAnimations) {
            delay(6_000L)
            page = (page + 1) % games.size
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Fresh arrivals from GeForce NOW",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                games.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .width(if (index == page) 22.dp else 7.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(if (index == page) MaterialTheme.colorScheme.primary else TextMuted.copy(alpha = 0.32f)),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
            label = "coming-next-carousel",
        ) { targetPage ->
            val featured = games[targetPage.coerceIn(games.indices)]
            val shape = RoundedCornerShape(if (settings.expressiveUi) 24.dp else 16.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (landscape) 176.dp else 218.dp)
                    .onFocusChanged { focused = it.isFocused || it.hasFocus }
                    .border(
                        width = if (focused) 3.dp else 1.dp,
                        color = when {
                            enhancedControllerFocus -> Color.Transparent
                            focused -> Color.White
                            else -> Color.White.copy(alpha = 0.08f)
                        },
                        shape = shape,
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when {
                            controllerActionMode && event.key == Key.DirectionLeft && games.size > 1 -> {
                                page = (page - 1 + games.size) % games.size
                                true
                            }
                            controllerActionMode && event.key == Key.DirectionRight && games.size > 1 -> {
                                page = (page + 1) % games.size
                                true
                            }
                            !tvProfile && controllerActionMode && handleCatalogControllerAction(
                                event = event,
                                onFavorite = { onFavorite(featured.id) },
                                onPlay = { onPlay(featured) },
                            ) -> true
                            isTvActivateKey(event) -> {
                                onSelect(featured)
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .combinedClickable(
                        onClick = { onSelect(featured) },
                        onLongClick = { onChooseStore(featured) },
                        onLongClickLabel = stringResource(R.string.store_selector_play_long_press),
                    ),
                shape = shape,
                color = Panel,
                tonalElevation = if (focused) 5.dp else 0.dp,
                shadowElevation = if (focused) 9.dp else 1.dp,
            ) {
                Box(Modifier.fillMaxSize()) {
                    UrlImage(gameHeroImageUrl(context, featured), Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.3f), Color.Transparent),
                                ),
                            ),
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(0.66f)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            featured.title,
                            color = Color.White,
                            style = if (landscape) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(featured.publisherName, displayStoresForGame(featured).takeIf { it.isNotBlank() })
                                .distinct()
                                .joinToString("  •  "),
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (shouldShowCatalogCardActions(tvProfile, controllerActionMode)) {
                        ThumbnailPlayButton(
                            onClick = { onPlay(featured) },
                            onLongClick = { onChooseStore(featured) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                            buttonSize = 50.dp,
                        )
                        FavoriteIconButton(
                            favorite = featured.id in favoriteIds,
                            onClick = { onFavorite(featured.id) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                            size = 38.dp,
                        )
                    }
                    ControllerFocusFrame(
                        visible = enhancedControllerFocus,
                        animate = settings.controllerBackgroundAnimations,
                        cornerRadius = if (settings.expressiveUi) 24.dp else 16.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreRailSection(
    title: String,
    games: List<GameInfo>,
    favoriteIds: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    controllerActionMode: Boolean,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val spacing = 10.dp
            val baseCardWidth = storeRailCardWidth(tvProfile, landscapeLayout)
            val visibleCount = storeRailVisibleCardCount(
                availableWidthDp = maxWidth.value,
                baseCardWidthDp = baseCardWidth.value,
                spacingDp = spacing.value,
                cardScale = settings.posterSizeScale,
            )
            val cardWidth = ((maxWidth.value - spacing.value * (visibleCount - 1) - 4.dp.value) / visibleCount)
                .coerceAtLeast(1f)
                .dp
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(games, key = { storeRailGameKey(it) }) { game ->
                    StoreRailGameCard(
                        game = game,
                        favorite = game.id in favoriteIds,
                        tvProfile = tvProfile,
                        expressiveUi = settings.expressiveUi,
                        controllerBackgroundAnimations = settings.controllerBackgroundAnimations,
                        width = cardWidth,
                        controllerActionMode = controllerActionMode,
                        onSelect = onSelect,
                        onFavorite = onFavorite,
                        onPlay = onPlay,
                        onChooseStore = onChooseStore,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreRailGameCard(
    game: GameInfo,
    favorite: Boolean,
    tvProfile: Boolean,
    expressiveUi: Boolean,
    controllerBackgroundAnimations: Boolean,
    width: Dp,
    controllerActionMode: Boolean,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(if (expressiveUi) 12.dp else 8.dp)
    val actionButtonSize = 34.dp
    val enhancedControllerFocus = shouldShowEnhancedControllerFocus(
        focused = focused,
        tvProfile = tvProfile,
        controllerActionMode = controllerActionMode,
    )
    Surface(
        modifier = Modifier
            .width(width)
            .aspectRatio(if (tvProfile) 1f else GAME_BOX_ART_ASPECT_RATIO)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    enhancedControllerFocus -> Color.Transparent
                    focused -> Color.White
                    else -> Color.White.copy(alpha = 0.08f)
                },
                shape = shape,
            )
            .onPreviewKeyEvent { event ->
                when {
                    !tvProfile && controllerActionMode && handleCatalogControllerAction(
                        event = event,
                        onFavorite = { onFavorite(game.id) },
                        onPlay = { onPlay(game) },
                    ) -> true
                    isTvActivateKey(event) -> {
                        onSelect(game)
                        true
                    }
                    else -> handleDpadFocusMove(event, focusManager)
                }
            }
            .focusable()
            .combinedClickable(
                onClick = { onSelect(game) },
                onLongClick = { onChooseStore(game) },
                onLongClickLabel = stringResource(R.string.store_selector_play_long_press),
            ),
        shape = shape,
        color = Color.Black,
        tonalElevation = if (focused) 4.dp else 0.dp,
        shadowElevation = if (focused) 8.dp else 1.dp,
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            UrlImage(
                catalogCardImageUrl(game, tvProfile),
                Modifier.fillMaxSize(),
                contentScale = if (tvProfile) ContentScale.Crop else ContentScale.Fit,
            )
            if (shouldOverlayCatalogCardTitle(tvProfile)) {
                GameCardTitleOverlay(game.title)
            }
            if (shouldShowCatalogCardActions(tvProfile, controllerActionMode)) {
                ThumbnailPlayButton(
                    onClick = { onPlay(game) },
                    onLongClick = { onChooseStore(game) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    buttonSize = actionButtonSize,
                )
                FavoriteIconButton(
                    favorite = favorite,
                    onClick = { onFavorite(game.id) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    size = actionButtonSize,
                )
            }
            ControllerFocusFrame(
                visible = enhancedControllerFocus,
                animate = controllerBackgroundAnimations,
                cornerRadius = if (expressiveUi) 12.dp else 8.dp,
            )
        }
    }
}

private fun jumpBackInGames(
    games: List<GameInfo>,
    libraryGames: List<GameInfo>,
    favoriteIds: List<String>,
    queuedGameKeys: List<String>,
): List<GameInfo> {
    val favoriteSet = favoriteIds.toSet()
    val combined = distinctStoreGames(libraryGames + games)
    val byKey = combined.associateBy(::storeRailGameKey)
    val queued = queuedGameKeys.mapNotNull(byKey::get)
    val favorites = combined.filter { it.id in favoriteSet }
    val recent = combined
        .filter { it.recentPlaySortKey() != null }
        .sortedByDescending { it.recentPlaySortKey() }
    val owned = combined.filter(::isGameInLibrary)
    return distinctStoreGames(queued + favorites + recent + owned).take(STORE_RAIL_GAME_LIMIT)
}

internal fun comingNextStoreGames(
    games: List<GameInfo>,
    excludedGames: List<GameInfo>,
): List<GameInfo> {
    val excludedKeys = excludedGames.map(::storeRailGameKey).toSet()
    return distinctStoreGames(games)
        .filterNot { storeRailGameKey(it) in excludedKeys }
        .filter(GameInfo::isNewOrUpdatedCatalogSection)
        .take(STORE_RAIL_GAME_LIMIT)
}

private fun GameInfo.isNewOrUpdatedCatalogSection(): Boolean {
    val section = catalogSectionTitle?.lowercase(Locale.US)?.trim().orEmpty()
    return section.contains("new") ||
        section.contains("recent") ||
        section.contains("updated") ||
        section.contains("just added")
}

private fun GameInfo.recentPlaySortKey(): String? =
    listOfNotNull(
        lastPlayed?.takeIf { it.isNotBlank() },
        variants.mapNotNull { it.lastPlayedDate?.takeIf(String::isNotBlank) }.maxOrNull(),
    ).maxOrNull()

private fun distinctStoreGames(games: List<GameInfo>): List<GameInfo> {
    val byKey = linkedMapOf<String, GameInfo>()
    games.forEach { game ->
        byKey.putIfAbsent(storeRailGameKey(game), game)
    }
    return byKey.values.toList()
}

private fun storeRailGameKey(game: GameInfo): String =
    gameTrackingKey(game)

private const val STORE_RAIL_GAME_LIMIT = 14
private const val GAME_BOX_ART_ASPECT_RATIO = 628f / 888f

internal fun shouldShowEnhancedControllerFocus(
    focused: Boolean,
    tvProfile: Boolean,
    controllerActionMode: Boolean,
): Boolean = focused && (tvProfile || controllerActionMode)

internal fun shouldInitiallyFocusGameDetailsPlay(tvProfile: Boolean): Boolean = tvProfile

internal fun controllerFocusPulseStrokeWidthDp(progress: Float): Float =
    4f + (9f * progress.coerceIn(0f, 1f))

internal fun controllerFocusPulseAlpha(progress: Float): Float {
    val remaining = 1f - progress.coerceIn(0f, 1f)
    return 0.58f * remaining * remaining
}

private data class GameGridSpec(
    val columns: Int,
    val cardHeight: Dp,
    val horizontalSpacing: Dp,
    val verticalSpacing: Dp,
    val contentPadding: PaddingValues,
    val squareCards: Boolean,
)

private fun storeRailCardWidth(tvProfile: Boolean, landscapeLayout: Boolean): Dp =
    when {
        tvProfile -> 158.dp
        landscapeLayout -> 146.dp
        else -> 142.dp
    }

@Composable
private fun BoxScope.ControllerFocusFrame(
    visible: Boolean,
    animate: Boolean,
    cornerRadius: Dp,
) {
    if (!visible) return
    val accent = MaterialTheme.colorScheme.primary
    if (animate) {
        val transition = rememberInfiniteTransition(label = "controller-focus-pulse")
        val pulseProgress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "controller-focus-pulse-progress",
        )
        ControllerFocusFrameCanvas(
            accent = accent,
            cornerRadius = cornerRadius,
            pulseProgress = pulseProgress,
        )
    } else {
        ControllerFocusFrameCanvas(
            accent = accent,
            cornerRadius = cornerRadius,
            pulseProgress = null,
        )
    }
}

@Composable
private fun BoxScope.ControllerFocusFrameCanvas(
    accent: Color,
    cornerRadius: Dp,
    pulseProgress: Float?,
) {
    Canvas(Modifier.matchParentSize().padding(2.dp)) {
        val outerRadius = (cornerRadius - 2.dp).toPx().coerceAtLeast(0f)

        // Keep every animated pixel on the card edge. The pulse expands by
        // widening the outer stroke and fading; it never creates an inner box.
        drawRoundRect(
            color = accent.copy(alpha = 0.18f),
            cornerRadius = CornerRadius(outerRadius, outerRadius),
            style = Stroke(width = 8.dp.toPx()),
        )
        pulseProgress?.let { progress ->
            drawRoundRect(
                color = accent.copy(alpha = controllerFocusPulseAlpha(progress)),
                cornerRadius = CornerRadius(outerRadius, outerRadius),
                style = Stroke(width = controllerFocusPulseStrokeWidthDp(progress).dp.toPx()),
            )
        }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.96f),
            cornerRadius = CornerRadius(outerRadius, outerRadius),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private fun gameGridSpec(
    maxWidth: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    landscapeLayout: Boolean,
    settings: AppSettings,
    handheldLayout: Boolean,
): GameGridSpec {
    val minimumPortraitColumns = if (!landscapeLayout && handheldLayout) 3 else 2
    val compactHorizontalSpacing = if (compact) 8.dp else 10.dp
    val compactVerticalSpacing = if (compact) 10.dp else 12.dp
    val landscapeHorizontalSpacing = if (handheldLayout) 10.dp else compactHorizontalSpacing
    val landscapeContentHorizontalPadding = 4.dp
    return when {
        handheldLayout -> GameGridSpec(
            columns = scaledGameCardColumnCount(
                baseColumns = if (landscapeLayout) {
                    landscapePosterColumnCount(
                        maxWidth = maxWidth,
                        horizontalSpacing = landscapeHorizontalSpacing,
                        horizontalContentPadding = landscapeContentHorizontalPadding,
                        handheldLayout = true,
                    )
                } else {
                    3
                },
                cardScale = settings.posterSizeScale,
                minimumColumns = if (landscapeLayout) 4 else 2,
            ),
            cardHeight = if (compact) 188.dp else 214.dp,
            horizontalSpacing = landscapeHorizontalSpacing,
            verticalSpacing = if (landscapeLayout) 16.dp else compactVerticalSpacing,
            contentPadding = PaddingValues(horizontal = landscapeContentHorizontalPadding, vertical = 4.dp),
            squareCards = false,
        )
        landscapeLayout -> GameGridSpec(
            columns = scaledGameCardColumnCount(
                baseColumns = landscapePosterColumnCount(
                    maxWidth = maxWidth,
                    horizontalSpacing = landscapeHorizontalSpacing,
                    horizontalContentPadding = landscapeContentHorizontalPadding,
                    handheldLayout = handheldLayout,
                ),
                cardScale = settings.posterSizeScale,
                minimumColumns = 3,
            ),
            cardHeight = if (compact) 188.dp else 214.dp,
            horizontalSpacing = landscapeHorizontalSpacing,
            verticalSpacing = if (handheldLayout) 16.dp else compactVerticalSpacing,
            contentPadding = PaddingValues(horizontal = landscapeContentHorizontalPadding, vertical = 4.dp),
            squareCards = false,
        )
        compact -> GameGridSpec(
            columns = scaledGameCardColumnCount(
                baseColumns = gameGridColumnCount(maxWidth, minimumPortraitColumns),
                cardScale = settings.posterSizeScale,
                minimumColumns = minimumPortraitColumns,
            ),
            cardHeight = 218.dp,
            horizontalSpacing = compactHorizontalSpacing,
            verticalSpacing = compactVerticalSpacing,
            contentPadding = PaddingValues(4.dp),
            squareCards = false,
        )
        else -> GameGridSpec(
            columns = scaledGameCardColumnCount(
                baseColumns = gameGridColumnCount(maxWidth, minimumPortraitColumns),
                cardScale = settings.posterSizeScale,
                minimumColumns = minimumPortraitColumns,
            ),
            cardHeight = 246.dp,
            horizontalSpacing = compactHorizontalSpacing,
            verticalSpacing = compactVerticalSpacing,
            contentPadding = PaddingValues(4.dp),
            squareCards = false,
        )
    }
}

internal fun appContentEdgePaddingDp(settings: AppSettings, inStream: Boolean): Float =
    if (inStream) 0f else settings.tvSafeAreaPaddingDp.coerceIn(0f, 120f)

internal fun scaledGameCardColumnCount(
    baseColumns: Int,
    cardScale: Float,
    minimumColumns: Int,
): Int = (baseColumns / cardScale.coerceIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE))
    .roundToInt()
    .coerceIn(minimumColumns, 12)

internal fun storeRailVisibleCardCount(
    availableWidthDp: Float,
    baseCardWidthDp: Float,
    spacingDp: Float,
    cardScale: Float,
): Int {
    val scaledCardWidth = baseCardWidthDp * cardScale.coerceIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE)
    return ((availableWidthDp + spacingDp) / (scaledCardWidth + spacingDp))
        .toInt()
        .coerceAtLeast(1)
}

private fun landscapePosterColumnCount(
    maxWidth: androidx.compose.ui.unit.Dp,
    horizontalSpacing: Dp,
    horizontalContentPadding: Dp,
    handheldLayout: Boolean,
): Int {
    val minCardWidth = when {
        handheldLayout -> 96.dp
        else -> 150.dp
    }
    val preferredColumns = when {
        handheldLayout && maxWidth >= 520.dp -> 5
        handheldLayout -> 5
        maxWidth >= 1440.dp -> 8
        maxWidth >= 1050.dp -> 7
        maxWidth >= 520.dp -> 6
        else -> 5
    }
    val minimumColumns = if (handheldLayout) 4 else 3
    for (columns in preferredColumns downTo minimumColumns) {
        if (landscapeCardWidth(maxWidth, columns, horizontalSpacing, horizontalContentPadding) >= minCardWidth) {
            return columns
        }
    }
    return minimumColumns
}

private fun landscapeCardWidth(
    maxWidth: androidx.compose.ui.unit.Dp,
    columns: Int,
    horizontalSpacing: Dp,
    horizontalContentPadding: Dp,
): Dp {
    val reservedWidth = horizontalContentPadding.value * 2f + horizontalSpacing.value * (columns - 1).coerceAtLeast(0)
    return ((maxWidth.value - reservedWidth).coerceAtLeast(0f) / columns.coerceAtLeast(1)).dp
}

private fun gameGridColumnCount(maxWidth: androidx.compose.ui.unit.Dp, minimumColumns: Int = 2): Int =
    when {
        maxWidth >= 1100.dp -> 5
        maxWidth >= 840.dp -> 4
        maxWidth >= 600.dp -> 3
        else -> minimumColumns
    }

@Composable
private fun GameCard(
    game: GameInfo,
    favorite: Boolean,
    tvProfile: Boolean,
    expressiveUi: Boolean,
    controllerBackgroundAnimations: Boolean,
    showGameStoreLabels: Boolean,
    cardHeight: androidx.compose.ui.unit.Dp,
    squareCard: Boolean,
    thumbnailPlayOverlay: Boolean,
    controllerActionMode: Boolean,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val cardShape = RoundedCornerShape(if (expressiveUi) 12.dp else 8.dp)
    val handheldPosterCard = !tvProfile
    val launcherTile = handheldPosterCard && thumbnailPlayOverlay
    val overlayActionSize = if (launcherTile) 34.dp else 44.dp
    val overlayActionPadding = if (launcherTile) 6.dp else 8.dp
    val enhancedControllerFocus = shouldShowEnhancedControllerFocus(
        focused = focused,
        tvProfile = tvProfile,
        controllerActionMode = controllerActionMode,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    handheldPosterCard -> Modifier.aspectRatio(GAME_BOX_ART_ASPECT_RATIO)
                    squareCard -> Modifier.aspectRatio(1f)
                    else -> Modifier.height(cardHeight)
                },
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    enhancedControllerFocus -> Color.Transparent
                    focused -> Color.White
                    else -> Color.Transparent
                },
                shape = cardShape,
            )
            .onPreviewKeyEvent { event ->
                when {
                    !tvProfile && controllerActionMode && handleCatalogControllerAction(
                        event = event,
                        onFavorite = { onFavorite(game.id) },
                        onPlay = { onPlay(game) },
                    ) -> true
                    isTvActivateKey(event) -> {
                        onSelect(game)
                        true
                    }
                    else -> handleDpadFocusMove(event, focusManager)
                }
            }
            .focusable(),
        colors = CardDefaults.cardColors(
            containerColor = if (expressiveUi) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f) else Panel,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (focused) 8.dp else 0.dp),
        shape = cardShape,
    ) {
        Box(
            Modifier
                .weight(1f)
                .clickable { onSelect(game) },
        ) {
            UrlImage(
                catalogCardImageUrl(game, tvProfile),
                Modifier.fillMaxSize(),
                contentScale = if (handheldPosterCard) ContentScale.Fit else ContentScale.Crop,
            )
            if (shouldOverlayCatalogCardTitle(tvProfile)) {
                GameCardTitleOverlay(game.title)
            }
            if (thumbnailPlayOverlay && shouldShowCatalogCardActions(tvProfile, controllerActionMode)) {
                FavoriteIconButton(
                    favorite = favorite,
                    onClick = { onFavorite(game.id) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(overlayActionPadding),
                    size = overlayActionSize,
                )
                ThumbnailPlayButton(
                    onClick = { onPlay(game) },
                    onLongClick = { onChooseStore(game) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(overlayActionPadding),
                    buttonSize = overlayActionSize,
                )
            }
            ControllerFocusFrame(
                visible = enhancedControllerFocus,
                animate = controllerBackgroundAnimations,
                cornerRadius = if (expressiveUi) 12.dp else 8.dp,
            )
        }
        if (!thumbnailPlayOverlay && showGameStoreLabels) {
            Column(
                Modifier
                    .clickable { onSelect(game) }
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (showGameStoreLabels) {
                    Text(displayStoresForGame(game), color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

internal fun catalogCardImageUrl(game: GameInfo, tvProfile: Boolean): String? {
    val source = if (tvProfile) {
        game.tvCardImageUrl?.takeIf { it.isNotBlank() }
            ?: game.imageUrl?.takeIf { it.isNotBlank() }
    } else {
        game.imageUrl
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { !it.contains("img.nvidiagrid.net") || it.contains("/GAME_BOX_ART_") }
    } ?: return null
    return if (tvProfile) optimizedNvidiaImageUrl(source, 272) else source
}

internal fun shouldOverlayCatalogCardTitle(tvProfile: Boolean): Boolean = tvProfile

internal fun shouldShowCatalogCardActions(tvProfile: Boolean, controllerActionMode: Boolean): Boolean =
    !tvProfile && !controllerActionMode

internal fun shouldShowGameStoreLabels(tvProfile: Boolean, enabled: Boolean): Boolean =
    enabled && !tvProfile

@Composable
private fun GameCardTitleOverlay(title: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GameCardOverlayGradient),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

private fun handleCatalogControllerAction(
    event: androidx.compose.ui.input.key.KeyEvent,
    onFavorite: () -> Unit,
    onPlay: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyUp) return false
    return when (event.key) {
        Key.ButtonX -> {
            onFavorite()
            true
        }
        Key.ButtonY -> {
            onPlay()
            true
        }
        else -> false
    }
}

@Composable
private fun ControllerCatalogRailActionHints(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 3.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.8f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ControllerCatalogActionHint(
                button = "X",
                label = stringResource(R.string.action_save),
                buttonColor = Color(0xff4aa3ff),
            )
            ControllerCatalogActionHint(
                button = "Y",
                label = stringResource(R.string.action_play),
                buttonColor = Color(0xffffcf40),
            )
        }
    }
}

@Composable
private fun ControllerCatalogActionHint(
    button: String,
    label: String,
    buttonColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            modifier = Modifier.size(18.dp),
            shape = CircleShape,
            color = buttonColor,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    button,
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun launcherBadgeForStoreKey(storeKey: String?): LauncherBadge =
    when (storeKey) {
        "STEAM" -> LauncherBadge(R.drawable.ic_store_steam, "Steam", Color(0xff17324d))
        "EPIC", "EGS", "EPIC_GAMES_STORE" -> LauncherBadge(R.drawable.ic_store_epic, "Epic", Color(0xff111111))
        "HOYO", "HOYOVERSE", "HOYOPLAY", "HOYO_PLAY", "MIHOYO" -> LauncherBadge(R.drawable.ic_store_hoyo, "HoYo", Color(0xff2b62d9))
        "XBOX", "XBOX_GAME_PASS", "GAME_PASS" -> LauncherBadge(R.drawable.ic_store_xbox, "Xbox", Color(0xff107c10))
        "MICROSOFT", "MICROSOFT_STORE" -> LauncherBadge(R.drawable.ic_store_microsoft, "Microsoft Store", Color(0xff0067b8))
        "UBISOFT", "UBISOFT_CONNECT" -> LauncherBadge(R.drawable.ic_store_ubisoft, "Ubisoft Connect", Color(0xff006efc))
        "EA", "EA_APP", "ORIGIN" -> LauncherBadge(R.drawable.ic_store_ea, "EA app", Color(0xffff4747))
        "GOG", "GOG.COM", "GOG_COM" -> LauncherBadge(R.drawable.ic_store_gog, "GOG", Color(0xff6a35a8))
        "BATTLENET", "BATTLE.NET", "BATTLE_NET", "BLIZZARD" -> LauncherBadge(R.drawable.ic_store_battlenet, "Battle.net", Color(0xff148eff))
        "RIOT", "RIOT_CLIENT", "RIOT_GAMES" -> LauncherBadge(R.drawable.ic_store_riot, "Riot", Color(0xffd13639))
        "ROCKSTAR", "ROCKSTAR_GAMES", "ROCKSTAR_GAMES_LAUNCHER" -> LauncherBadge(R.drawable.ic_store_rockstar, "Rockstar", Color(0xffffc400), Color(0xff111111))
        "NCSOFT", "NC_SOFT", "PURPLE" -> LauncherBadge(R.drawable.ic_tab_store, "NCSOFT", Color(0xffb4822d), Color(0xff111111))
        "GOOGLE_PLAY", "PLAY_STORE", "ANDROID" -> LauncherBadge(R.drawable.ic_store_google_play, "Google Play", Color(0xff0f9d58))
        "AMAZON", "AMAZON_GAMES" -> LauncherBadge(R.drawable.ic_store_amazon, "Amazon Games", Color(0xffff9900), Color(0xff111111))
        else -> LauncherBadge(R.drawable.ic_tab_store, "GeForce NOW", Color.Black.copy(alpha = 0.72f))
    }

private fun displayStoresForGame(game: GameInfo): String {
    val stores = displayStoresForVariants(game.variants).ifEmpty {
        game.availableStores.map(::gameStoreDisplayName)
    }.distinctBy { normalizeGameStore(it) }
    return stores.joinToString(", ").ifBlank { "GeForce NOW" }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailPlayButton(onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier, buttonSize: Dp = 44.dp) {
    Surface(
        modifier = modifier
            .size(buttonSize)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.store_selector_play_long_press),
            ),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.35f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
    ) {
        ZortosPlayMark(
            modifier = Modifier.fillMaxSize().padding(buttonSize * 0.13f),
            ringColor = Color.White,
        )
    }
}

@Composable
private fun ZortosPlayMark(
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    playColor: Color = ringColor,
) {
    Canvas(modifier) {
        val play = Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.25f)
            lineTo(size.width * 0.35f, size.height * 0.75f)
            lineTo(size.width * 0.75f, size.height * 0.5f)
            close()
        }
        drawPath(play, playColor)
    }
}

@Composable
private fun AnimatedLaunchOverlay(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }) + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }) + scaleOut(targetScale = 0.94f),
        modifier = modifier,
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameDetailsSheet(
    game: GameInfo,
    favorite: Boolean,
    defaultVariantId: String?,
    fullScreen: Boolean,
    safeAreaPadding: Dp,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    connectedTvName: String?,
    onPlayOnTv: (GameInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val gameFocusRequester = remember(game.id) { FocusRequester() }
    val playFocusRequester = remember(game.id) { FocusRequester() }
    LaunchedEffect(game.id, fullScreen) {
        delay(80)
        val initialRequester = if (shouldInitiallyFocusGameDetailsPlay(tvProfile = fullScreen)) {
            playFocusRequester
        } else {
            gameFocusRequester
        }
        runCatching { initialRequester.requestFocus() }
    }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .lockedFocusGroup()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .then(
                    if (fullScreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                    },
                )
                .clickable(onClick = {}),
            shape = if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Panel,
            tonalElevation = 8.dp,
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(if (fullScreen) safeAreaPadding else 0.dp),
            ) {
                val aspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
                val landscapeTvLayout = maxWidth >= 720.dp && aspect >= 1.35f
                val phoneLandscapeLayout = landscapeTvLayout && minOf(maxWidth, maxHeight) < PHONE_NAV_RAIL_MAX_SMALLEST_WIDTH
                if (landscapeTvLayout) {
                    GameDetailsLandscapeContent(
                        game = game,
                        favorite = favorite,
                        defaultVariantId = defaultVariantId,
                        onPlay = onPlay,
                        onChooseStore = onChooseStore,
                        onFavorite = onFavorite,
                        connectedTvName = connectedTvName,
                        onPlayOnTv = onPlayOnTv,
                        onDismiss = onDismiss,
                        gameFocusRequester = gameFocusRequester,
                        playFocusRequester = playFocusRequester,
                        shortHeight = maxHeight <= 620.dp,
                        imageActionsOverlay = phoneLandscapeLayout,
                    )
                } else {
                    GameDetailsScrollableContent(
                        game = game,
                        favorite = favorite,
                        defaultVariantId = defaultVariantId,
                        onPlay = onPlay,
                        onChooseStore = onChooseStore,
                        onFavorite = onFavorite,
                        connectedTvName = connectedTvName,
                        onPlayOnTv = onPlayOnTv,
                        onDismiss = onDismiss,
                        gameFocusRequester = gameFocusRequester,
                        playFocusRequester = playFocusRequester,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameDetailsLandscapeContent(
    game: GameInfo,
    favorite: Boolean,
    defaultVariantId: String?,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    connectedTvName: String?,
    onPlayOnTv: (GameInfo) -> Unit,
    onDismiss: () -> Unit,
    gameFocusRequester: FocusRequester,
    playFocusRequester: FocusRequester,
    shortHeight: Boolean,
    imageActionsOverlay: Boolean,
) {
    val description = gameDescriptionForDetails(game)
    val context = LocalContext.current
    val sideScrollState = rememberScrollState()
    val detailsSpacing = if (shortHeight) 8.dp else 10.dp
    var gameFocused by remember(game.id) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = if (shortHeight) 18.dp else 24.dp, vertical = if (shortHeight) 16.dp else 22.dp),
        horizontalArrangement = Arrangement.spacedBy(if (shortHeight) 16.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(0.92f)
                .fillMaxHeight()
                .focusRequester(gameFocusRequester)
                .focusProperties { right = playFocusRequester }
                .onFocusChanged { gameFocused = it.isFocused }
                .border(
                    width = if (gameFocused) 3.dp else 1.dp,
                    color = if (gameFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp),
                )
                .clip(RoundedCornerShape(20.dp))
                .clickable {
                    onDismiss()
                    onPlay(game)
                },
        ) {
            UrlImage(gameHeroImageUrl(context, game), Modifier.fillMaxSize())
            GameImageTitleOverlay(
                game = game,
                compact = shortHeight,
                reserveEndSpace = imageActionsOverlay,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            if (imageActionsOverlay) {
                ImageCloseButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                )
            }
            if (imageActionsOverlay) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .width(150.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    connectedTvName?.let { tvName ->
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onPlayOnTv(game)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Play on TV", maxLines = 1)
                        }
                    }
                    LongPressPlayButton(
                        onClick = {
                            onDismiss()
                            onPlay(game)
                        },
                        onLongClick = {
                            onDismiss()
                            onChooseStore(game)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(playFocusRequester),
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1.08f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(detailsSpacing),
        ) {
            if (imageActionsOverlay) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(sideScrollState),
                    verticalArrangement = Arrangement.spacedBy(detailsSpacing),
                ) {
                    GameDetailsCompactInfoContent(
                        game = game,
                        defaultVariantId = defaultVariantId,
                        description = description,
                    )
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(sideScrollState),
                    verticalArrangement = Arrangement.spacedBy(detailsSpacing),
                ) {
                    GameDetailsCompactInfoContent(
                        game = game,
                        defaultVariantId = defaultVariantId,
                        description = description,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var dismissFocused by remember { mutableStateOf(false) }
                    val accent = MaterialTheme.colorScheme.primary
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, if (dismissFocused) accent else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .onFocusChanged { dismissFocused = it.isFocused }
                    ) {
                        Text(
                            "Dismiss", 
                            color = if (dismissFocused) accent else TextPrimary,
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LongPressPlayButton(
                        onClick = {
                            onDismiss()
                            onPlay(game)
                        },
                        onLongClick = {
                            onDismiss()
                            onChooseStore(game)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(playFocusRequester),
                    )
                    connectedTvName?.let {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onPlayOnTv(game)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text("Play on TV", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDetailsCompactInfoContent(
    game: GameInfo,
    defaultVariantId: String?,
    description: String?,
) {
    OwnershipStatusRow(game = game, compact = true)
    GameGenreChips(game = game, compact = true)
    GameScreenshotGallery(game = game, compact = true)
    GameDescriptionDisclosure(description = description, compact = true)
    CompactDetailRows(game)
    LaunchOptionsList(
        game = game,
        defaultVariantId = defaultVariantId,
        compact = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameDetailsScrollableContent(
    game: GameInfo,
    favorite: Boolean,
    defaultVariantId: String?,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    connectedTvName: String?,
    onPlayOnTv: (GameInfo) -> Unit,
    onDismiss: () -> Unit,
    gameFocusRequester: FocusRequester,
    playFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    var gameFocused by remember(game.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .focusRequester(gameFocusRequester)
                        .focusProperties { down = playFocusRequester }
                        .onFocusChanged { gameFocused = it.isFocused }
                        .border(
                            width = if (gameFocused) 3.dp else 1.dp,
                            color = if (gameFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            onDismiss()
                            onPlay(game)
                        },
                ) {
                    UrlImage(
                        gameHeroImageUrl(context, game),
                        Modifier.fillMaxSize(),
                    )
                    GameImageTitleOverlay(
                        game = game,
                        compact = false,
                        reserveEndSpace = false,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val description = gameDescriptionForDetails(game)
                    OwnershipStatusRow(game = game, compact = false)
                    GameGenreChips(game = game, compact = false)
                    GameScreenshotGallery(game = game, compact = false)
                    GameDescriptionDisclosure(description = description, compact = false)
                    DetailRows(game)
                    LaunchOptionsList(
                        game = game,
                        defaultVariantId = defaultVariantId,
                        compact = false,
                    )
                }
            }
        }
        Surface(color = Panel.copy(alpha = 0.98f), tonalElevation = 8.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var dismissFocused by remember { mutableStateOf(false) }
                val accent = MaterialTheme.colorScheme.primary
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, if (dismissFocused) accent else MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .onFocusChanged { dismissFocused = it.isFocused }
                ) {
                    Text(
                        "Dismiss", 
                        color = if (dismissFocused) accent else TextPrimary,
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LongPressPlayButton(
                    onClick = {
                        onDismiss()
                        onPlay(game)
                    },
                    onLongClick = {
                        onDismiss()
                        onChooseStore(game)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(playFocusRequester),
                )
                connectedTvName?.let {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onPlayOnTv(game)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Play on TV", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchOptionsList(
    game: GameInfo,
    defaultVariantId: String?,
    compact: Boolean,
) {
    val variants = launchableGameVariants(game.variants)
    if (variants.size <= 1) return
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        Text(
            stringResource(R.string.store_selector_launchers),
            color = TextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        variants.take(if (compact) 3 else variants.size).forEach { variant ->
            val isDefault = variant.id == defaultVariantId
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
                color = if (isDefault) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else PanelAlt,
                contentColor = TextPrimary,
            ) {
                Row(
                    Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(gameStoreDisplayName(variant.store), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val details = variantDetailsText(variant)
                        Text(
                            if (isDefault) {
                                listOf(stringResource(R.string.store_selector_default), details).filter { it.isNotBlank() }.joinToString(" - ")
                            } else {
                                details.ifBlank { stringResource(R.string.store_selector_available_launcher) }
                            },
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (compact) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LongPressPlayButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val accent = MaterialTheme.colorScheme.primary
    val focusScale by animateFloatAsState(
        targetValue = gameDetailsPlayFocusScale(focused),
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "game-details-play-focus-scale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (focused) Color.White else accent,
        animationSpec = tween(durationMillis = 120),
        label = "game-details-play-focus-color",
    )
    Surface(
        modifier = modifier
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            }
            .onPreviewKeyEvent { event ->
                if (isTvActivateKey(event)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.store_selector_play_long_press),
            )
            .then(
                if (focused) {
                    Modifier.border(
                        width = gameDetailsPlayFocusBorderWidthDp(focused).dp,
                        color = accent,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = if (focused) 12.dp else 0.dp,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZortosPlayMark(
                modifier = Modifier.size(20.dp),
                ringColor = Color.Black,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.action_play),
                color = Color.Black,
                fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun gameDetailsPlayFocusScale(focused: Boolean): Float = if (focused) 1.06f else 1f

internal fun gameDetailsPlayFocusBorderWidthDp(focused: Boolean): Float = if (focused) 4f else 0f

private fun variantDetailsText(variant: GameVariant): String =
    listOfNotNull(
        variant.libraryStatus?.takeIf { it.isNotBlank() }?.let(::formatGameMetadataLabel),
        variant.supportedControls.takeIf { it.isNotEmpty() }?.joinToString(", ") { formatGameMetadataLabel(it) },
        variant.lastPlayedDate?.takeIf { it.isNotBlank() }?.let { "Last played $it" },
    ).joinToString(" - ")

@Composable
private fun ImageCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .size(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (focused) accent else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.58f),
        tonalElevation = 3.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_clear),
                contentDescription = stringResource(R.string.action_cancel),
                tint = TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FavoriteIconButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val label = stringResource(if (favorite) R.string.action_saved else R.string.action_save)
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label }
            .clickable(onClick = onClick)
            .focusable()
            .then(
                if (focused) Modifier.border(2.dp, accent, CircleShape) else Modifier
            ),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.35f),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, if (focused) accent else Color.White.copy(alpha = 0.2f)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(if (favorite) R.drawable.ic_save_filled else R.drawable.ic_save),
                contentDescription = null,
                tint = if (favorite) MaterialTheme.colorScheme.primary else TextPrimary,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

internal fun gameDescriptionForDetails(game: GameInfo): String? =
    game.description?.takeIf { it.isNotBlank() }
        ?: game.longDescription?.takeIf { it.isNotBlank() }

private fun gameHeroImageUrl(context: Context, game: GameInfo?): String? {
    val url = game?.screenshotUrl?.takeIf { it.isNotBlank() }
        ?: game?.tvBannerUrl?.takeIf { it.isNotBlank() }
        ?: game?.imageUrl?.takeIf { it.isNotBlank() }
        ?: return null
    return optimizedNvidiaImageUrl(url, wideImageRequestWidth(context))
}

private fun gameTvBannerImageUrl(context: Context, game: GameInfo?): String? {
    val url = game?.tvBannerUrl?.takeIf { it.isNotBlank() }
        ?: game?.screenshotUrl?.takeIf { it.isNotBlank() }
        ?: game?.imageUrl?.takeIf { it.isNotBlank() }
        ?: return null
    return optimizedNvidiaImageUrl(url, wideImageRequestWidth(context))
}

private fun optimizedNvidiaImageUrl(url: String, width: Int): String {
    if (!url.contains("img.nvidiagrid.net")) return url
    val base = url
        .substringBefore(";f=")
        .substringBefore(";w=")
        .substringBefore(";h=")
        .substringBefore(";dpr=")
    return "$base;f=webp;w=$width"
}

private fun wideImageRequestWidth(context: Context): Int {
    val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
    val downstreamKbps = capabilities?.linkDownstreamBandwidthKbps ?: 0
    return when {
        downstreamKbps >= 25_000 -> 1920
        downstreamKbps in 10_000 until 25_000 -> 1600
        downstreamKbps in 3_000 until 10_000 -> 1280
        downstreamKbps in 1 until 3_000 -> 960
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true -> 1600
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> 960
        else -> 1280
    }
}

@Composable
private fun GameImageTitleOverlay(
    game: GameInfo,
    compact: Boolean,
    reserveEndSpace: Boolean,
    modifier: Modifier = Modifier,
) {
    val textShadow = Shadow(
        color = Color.Black,
        offset = Offset(0f, 3f),
        blurRadius = 14f,
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(
                start = if (compact) 12.dp else 16.dp,
                top = if (compact) 9.dp else 12.dp,
                end = if (reserveEndSpace) 154.dp else if (compact) 12.dp else 16.dp,
                bottom = if (compact) 10.dp else 14.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            game.title,
            color = TextPrimary,
            style = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall).copy(
                shadow = textShadow,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = if (compact) 2 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            game.publisherName?.takeIf { it.isNotBlank() } ?: "Unknown publisher",
            color = TextPrimary.copy(alpha = 0.88f),
            style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GameTitleBlock(game: GameInfo, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
        Text(
            game.title,
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            game.publisherName?.takeIf { it.isNotBlank() } ?: "Unknown publisher",
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OwnershipStatusRow(game: GameInfo, compact: Boolean) {
    val ownedStores = ownedStoreLabels(game)
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    if (ownedStores.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = Color(0xff4a1216),
            tonalElevation = 0.dp,
        ) {
            Text(
                "Not owned",
                color = Color(0xffffb8bf),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ownedStores.forEach { store ->
            val badge = launcherBadgeForStoreKey(normalizeGameStore(store))
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                tonalElevation = 0.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConnectorStoreIcon(badge)
                    Text(
                        "Owned on $store",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun ownedStoreLabels(game: GameInfo): List<String> =
    libraryStoreDisplayNames(game).ifEmpty {
        if (isGameInLibrary(game)) listOf("GeForce NOW") else emptyList()
    }

@Composable
private fun GameGenreChips(game: GameInfo, compact: Boolean) {
    val genres = game.genres
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map(::formatGameMetadataLabel)
        .filterNot(::isNoisyGameTag)
        .distinctBy { it.lowercase(Locale.US) }
        .take(if (compact) 12 else 20)
    if (genres.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp),
        contentPadding = PaddingValues(end = if (compact) 6.dp else 8.dp),
    ) {
        items(genres, key = { it }) { label ->
            AssistChip(onClick = {}, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

@Composable
private fun GameScreenshotGallery(game: GameInfo, compact: Boolean) {
    val screenshots = game.screenshotUrls
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    if (screenshots.isEmpty()) return
    val context = LocalContext.current
    val requestWidth = remember(context) { wideImageRequestWidth(context).coerceAtLeast(960) }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
    ) {
        Text(
            "Screenshots",
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(screenshots, key = { it }) { screenshot ->
                Surface(
                    modifier = Modifier
                        .width(if (compact) 224.dp else 288.dp)
                        .aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
                    color = Color.Black,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                ) {
                    UrlImage(
                        url = optimizedNvidiaImageUrl(screenshot, requestWidth),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameDescriptionDisclosure(description: String?, compact: Boolean) {
    var expanded by remember(description) { mutableStateOf(true) }
    val text = description?.takeIf { it.isNotBlank() } ?: "No description is available for this game yet."
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 12.dp else 14.dp))
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 1.dp,
                color = if (focused) accent else Color.Transparent,
                shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
            )
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
        color = if (focused) PanelAlt.copy(alpha = 0.85f) else PanelAlt,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Description",
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = if (expanded) "Hide description" else "Show description",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer(rotationZ = if (expanded) 90f else 0f),
                    )
                }
            }
            if (expanded) {
                Text(
                    text,
                    color = if (description == null) TextMuted else TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (compact) 8 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatGameMetadataLabel(raw: String): String {
    val compact = raw.trim()
        .removePrefix("GFN_")
        .removePrefix("GAME_")
        .replace(Regex("[_-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (compact.isBlank()) return ""
    val lower = compact.lowercase(Locale.US)
    return when (lower) {
        "full game" -> "Full game"
        "single player" -> "Single-player"
        "multi player", "multiplayer" -> "Multiplayer"
        "controller", "gamepad" -> "Controller"
        "keyboard mouse", "mouse keyboard" -> "Mouse and keyboard"
        else -> compact.split(" ").joinToString(" ") { word ->
            if (word.length <= 3 && word.all { it.isUpperCase() || it.isDigit() }) {
                word
            } else {
                word.lowercase(Locale.US).replaceFirstChar { char -> char.titlecase(Locale.US) }
            }
        }
    }
}

private fun isNoisyGameTag(label: String): Boolean {
    val normalized = label.trim().lowercase(Locale.US)
    return normalized.isBlank() ||
        normalized == "unknown" ||
        normalized == "gfn" ||
        normalized == "nvidia" ||
        normalized.contains("sku based tag") ||
        normalized.contains("catalog")
}

@Composable
private fun CompactDetailRows(game: GameInfo) {
    val rows = gameDetailRows(game).take(4)
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            DetailRow(row = row, compact = true)
        }
    }
}

@Composable
private fun DetailRows(game: GameInfo) {
    val rows = gameDetailRows(game)
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            DetailRow(row = row, compact = false)
        }
    }
}

private data class GameDetailRow(
    val label: String,
    val value: String,
    val copyValue: String? = null,
)

private fun gameDetailRows(game: GameInfo): List<GameDetailRow> =
    listOfNotNull(
        game.playabilityState?.takeIf { it.isNotBlank() }?.let { GameDetailRow("Status", formatGameMetadataLabel(it)) },
        gameAppIdForDetails(game)?.let { GameDetailRow("App ID", it, copyValue = it) },
        game.contentRatings.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { GameDetailRow("Rating", it) },
        game.lastPlayed?.takeIf { it.isNotBlank() }?.let { GameDetailRow("Last played", it) },
        game.availableStores.takeIf { it.isNotEmpty() }?.map(::gameStoreDisplayName)?.distinct()?.joinToString(", ")?.let { GameDetailRow("Stores", it) },
    )

private fun gameAppIdForDetails(game: GameInfo): String? =
    game.launchAppId?.takeIf { it.isNotBlank() }
        ?: game.variants.firstNotNullOfOrNull { variant -> variant.id.takeIf { it.isNotBlank() && it.all(Char::isDigit) } }
        ?: game.uuid?.takeIf { it.isNotBlank() }
        ?: game.id.takeIf { it.isNotBlank() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailRow(row: GameDetailRow, compact: Boolean) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PanelAlt)
            .combinedClickable(
                onClick = {},
                onLongClick = row.copyValue?.let { value ->
                    {
                        clipboard.setText(AnnotatedString(value))
                        Toast.makeText(context, "App ID copied", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 7.dp else 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
    ) {
        Text(
            row.label,
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(if (compact) 82.dp else 92.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (row.copyValue != null) "${row.value}" else row.value,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun gameMatchesSearch(game: GameInfo, query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return true
    val haystack = buildString {
        append(game.title).append(' ')
        append(game.description.orEmpty()).append(' ')
        append(game.longDescription.orEmpty()).append(' ')
        append(game.publisherName.orEmpty()).append(' ')
        append(game.genres.joinToString(" ")).append(' ')
        append(game.featureLabels.joinToString(" ")).append(' ')
        append(displayStoresForGame(game))
    }.lowercase()
    return normalized.split(Regex("\\s+")).all { it in haystack }
}

private fun favoriteOrderedGames(games: List<GameInfo>, favoriteIds: List<String>): List<GameInfo> {
    val favorites = games.filter { it.id in favoriteIds }
    return if (favorites.isNotEmpty()) favorites + games.filterNot { it.id in favoriteIds } else games
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StoreLaunchSelector(
    game: GameInfo,
    defaultVariantId: String?,
    onLaunch: (GameInfo, GameVariant) -> Unit,
    onSetDefaultStore: (String, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val variants = remember(game) { launchableGameVariants(game.variants) }
    val context = LocalContext.current
    val initialVariantId = remember(game.id, defaultVariantId, variants) {
        defaultVariantId?.takeIf { savedId -> variants.any { it.id == savedId } }
            ?: variants.firstOrNull()?.id
    }
    var selectedVariantId by remember(game.id, initialVariantId) { mutableStateOf(initialVariantId) }
    var rememberDefaultStore by remember(game.id, defaultVariantId) { mutableStateOf(defaultVariantId != null) }
    val selectedVariant = variants.firstOrNull { it.id == selectedVariantId }
    val continueFocusRequester = remember(game.id) { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(game.id, variants.size) {
        if (variants.isNotEmpty()) {
            runCatching { continueFocusRequester.requestFocus() }
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .lockedFocusGroup()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false) {},
    ) {
        val phoneLandscape = isPhoneLandscape(maxWidth, maxHeight)
        val landscape = maxWidth > maxHeight
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = if (phoneLandscape) Alignment.CenterEnd else Alignment.Center,
        ) {
            Card(
                modifier = modifier
                    .then(
                        if (phoneLandscape) {
                            Modifier
                                .padding(end = 12.dp)
                                .fillMaxWidth(0.9f)
                                .fillMaxHeight(0.9f)
                        } else {
                            Modifier
                                .fillMaxWidth(if (landscape) 0.78f else 0.92f)
                                .fillMaxHeight(if (landscape) 0.86f else 0.64f)
                        },
                    ),
                colors = CardDefaults.cardColors(containerColor = Panel, contentColor = TextPrimary),
                shape = RoundedCornerShape(22.dp),
            ) {
                if (phoneLandscape) {
                    Row(
                        Modifier.fillMaxSize().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LaunchGameSummary(
                            game = game,
                            subtitle = stringResource(R.string.store_selector_choose_launcher),
                            modifier = Modifier
                                .width(190.dp)
                                .fillMaxHeight(),
                        )
                        StoreLaunchOptionsColumn(
                            variants = variants,
                            selectedVariantId = selectedVariantId,
                            defaultVariantId = defaultVariantId,
                            rememberDefaultStore = rememberDefaultStore,
                            selectedVariant = selectedVariant,
                            continueFocusRequester = continueFocusRequester,
                            onSelectVariant = { selectedVariantId = it },
                            onRememberDefaultStoreChange = { rememberDefaultStore = it },
                            onDismiss = onDismiss,
                            onContinue = { variant ->
                                if (rememberDefaultStore || defaultVariantId != null) {
                                    onSetDefaultStore(game.id, if (rememberDefaultStore) variant.id else null)
                                }
                                if (rememberDefaultStore) {
                                    Toast.makeText(context, context.getString(R.string.store_selector_long_press_tip), Toast.LENGTH_LONG).show()
                                }
                                onLaunch(game, variant)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UrlImage(
                                game.imageUrl,
                                Modifier
                                    .width(58.dp)
                                    .height(76.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(game.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.store_selector_choose_launcher), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        StoreLaunchOptionsColumn(
                            variants = variants,
                            selectedVariantId = selectedVariantId,
                            defaultVariantId = defaultVariantId,
                            rememberDefaultStore = rememberDefaultStore,
                            selectedVariant = selectedVariant,
                            continueFocusRequester = continueFocusRequester,
                            onSelectVariant = { selectedVariantId = it },
                            onRememberDefaultStoreChange = { rememberDefaultStore = it },
                            onDismiss = onDismiss,
                            onContinue = { variant ->
                                if (rememberDefaultStore || defaultVariantId != null) {
                                    onSetDefaultStore(game.id, if (rememberDefaultStore) variant.id else null)
                                }
                                if (rememberDefaultStore) {
                                    Toast.makeText(context, context.getString(R.string.store_selector_long_press_tip), Toast.LENGTH_LONG).show()
                                }
                                onLaunch(game, variant)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchGameSummary(game: GameInfo, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UrlImage(
            game.imageUrl,
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(game.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StoreLaunchOptionsColumn(
    variants: List<GameVariant>,
    selectedVariantId: String?,
    defaultVariantId: String?,
    rememberDefaultStore: Boolean,
    selectedVariant: GameVariant?,
    continueFocusRequester: FocusRequester,
    onSelectVariant: (String) -> Unit,
    onRememberDefaultStoreChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onContinue: (GameVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(variants, key = { it.id }) { variant ->
                StoreLaunchVariantRow(
                    variant = variant,
                    selected = variant.id == selectedVariantId,
                    savedDefault = variant.id == defaultVariantId,
                    onClick = { onSelectVariant(variant.id) },
                )
            }
        }
        var checkFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .onFocusChanged { checkFocused = it.isFocused }
                .background(if (checkFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (checkFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onRememberDefaultStoreChange(!rememberDefaultStore) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = rememberDefaultStore,
                onCheckedChange = onRememberDefaultStoreChange,
            )
            Text(
                stringResource(R.string.store_selector_default_checkbox),
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = {
                    val variant = selectedVariant ?: return@Button
                    onContinue(variant)
                },
                enabled = selectedVariant != null,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(continueFocusRequester),
            ) {
                Text(stringResource(R.string.action_continue), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StoreLaunchVariantRow(
    variant: GameVariant,
    selected: Boolean,
    savedDefault: Boolean,
    onClick: () -> Unit,
) {
    val badge = launcherBadgeForStoreKey(splitGameStoreKeys(variant.store).firstOrNull())
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else PanelAlt,
        contentColor = TextPrimary,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectorStoreIcon(badge)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(gameStoreDisplayName(variant.store), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOf(
                    if (savedDefault) stringResource(R.string.store_selector_default) else "",
                    variantDetailsText(variant),
                ).filter { it.isNotBlank() }.joinToString(" - ")
                if (details.isNotBlank()) {
                    Text(details, color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (selected) {
                Text(
                    stringResource(R.string.store_selector_selected),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StreamScreen(state: OpenNowUiState, viewModel: OpenNowViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val audioController = remember(context) { AndroidNerdAudioController(context.applicationContext) }
    val session = state.streamSession
    val game = state.streamGame
    var streamState by remember { mutableStateOf("Preparing") }
    var initialVideoFrameRendered by remember(session?.sessionId) { mutableStateOf(false) }
    val markInitialVideoFrameRendered by rememberUpdatedState<() -> Unit> {
        initialVideoFrameRendered = true
    }
    var controlsOpen by remember { mutableStateOf(false) }
    var exitConfirmOpen by remember { mutableStateOf(false) }
    var keyboardOpen by remember { mutableStateOf(false) }
    var keyboardText by remember { mutableStateOf("") }
    var audioMuted by remember { mutableStateOf(false) }
    var touchLayoutEditing by remember { mutableStateOf(false) }
    var streamGuideOpen by remember(session?.sessionId) { mutableStateOf(false) }
    var streamGuideStep by remember(session?.sessionId) { mutableStateOf(StreamGuideStep.OpenControls) }
    var statsVisible by remember(state.settings.showStatsOnLaunch) { mutableStateOf(state.settings.showStatsOnLaunch) }
    var streamStats by remember { mutableStateOf(StreamRuntimeStats()) }
    var controllerMouseAssistEnabled by remember(session?.sessionId) { mutableStateOf(false) }
    var controllerMouseEmulationEnabled by remember(session?.sessionId) { mutableStateOf(state.settings.controllerMouseEmulation) }
    val streamReady = session?.isReadyForStream() == true
    val tvProfile = state.androidTvProfile
    val physicalControllerConnected = rememberPhysicalControllerConnected(enabled = streamReady)
    var showTouchControlsWithPhysicalController by remember(session?.sessionId) { mutableStateOf(false) }
    var physicalControllerPromptOpen by remember(session?.sessionId) { mutableStateOf(false) }
    var physicalControllerPromptHandled by remember(session?.sessionId) { mutableStateOf(false) }
    var physicalControllerPromptDoNotShowAgain by remember(session?.sessionId) { mutableStateOf(false) }
    val touchInputEnabled = !state.androidPictureInPictureActive
    val touchControlsSuppressedByPhysicalController =
        physicalControllerConnected &&
            state.settings.androidTouch.enabled &&
            !showTouchControlsWithPhysicalController
    val touchControlsVisible = shouldShowAndroidTouchControls(
        tvProfile = tvProfile,
        touchInputEnabled = touchInputEnabled,
        touchControlsEnabled = state.settings.androidTouch.enabled,
        suppressedByPhysicalController = touchControlsSuppressedByPhysicalController,
    )
    val sessionStartedAtMs = remember(session?.sessionId) { System.currentTimeMillis() }
    var timerNowMs by remember(session?.sessionId) { mutableStateOf(System.currentTimeMillis()) }
    val smartSessionLimit = smartSessionLimitFor(state.subscriptionInfo, state.authSession?.user?.membershipTier)
    val buttonToneEnabled = state.settings.controllerUiSounds
    val stretchToFill = state.settings.stretchStreamToFill
    val stretchToZoom = state.settings.stretchStreamToZoom
    val playButtonTone = {
        audioController.playButtonTone(buttonToneEnabled)
    }
    val launchStreamSettings = state.activeStreamSettings ?: state.settings.stream
    val streamSettings = launchStreamSettings.copy(
        mouseSensitivity = state.settings.stream.mouseSensitivity,
        mouseAcceleration = state.settings.stream.mouseAcceleration,
        streamSharpeningEnabled = launchStreamSettings.streamSharpeningEnabled && state.settings.stream.streamSharpeningEnabled,
        streamSharpeningAmount = state.settings.stream.streamSharpeningAmount,
    )
    val statsAlignment = when (state.settings.streamStatsPosition) {
        StreamStatsPosition.Left -> Alignment.TopStart
        StreamStatsPosition.Center -> Alignment.TopCenter
        StreamStatsPosition.Right -> Alignment.TopEnd
    }
    val dismissStreamGuide = {
        streamGuideOpen = false
        if (!state.settings.androidStreamGuideDismissed) {
            viewModel.updateSettings(state.settings.copy(androidStreamGuideDismissed = true))
        }
    }
    val openControlsForGuide = {
        keyboardOpen = false
        exitConfirmOpen = false
        physicalControllerPromptOpen = false
        if (streamGuideOpen && streamGuideStep == StreamGuideStep.OpenControls) {
            streamGuideStep = StreamGuideStep.PressDone
        }
        controlsOpen = true
    }
    LaunchedEffect(state.remoteStreamMenuRequestToken) {
        if (state.remoteStreamMenuRequestToken > 0 && streamReady) {
            openControlsForGuide()
        }
    }
    LaunchedEffect(state.remoteStatsToggleRequestToken) {
        if (state.remoteStatsToggleRequestToken > 0 && streamReady) {
            statsVisible = !statsVisible
        }
    }
    val streamOverlayOpen = controlsOpen || exitConfirmOpen || keyboardOpen || streamGuideOpen || physicalControllerPromptOpen || touchLayoutEditing
    val externalMousePassthroughActive = streamReady && !streamOverlayOpen
    val handleStreamBack = {
        when {
            streamGuideOpen && streamGuideStep == StreamGuideStep.OpenControls -> openControlsForGuide()
            streamGuideOpen && streamGuideStep == StreamGuideStep.PressDone && controlsOpen -> {
                controlsOpen = false
                dismissStreamGuide()
            }
            streamGuideOpen -> dismissStreamGuide()
            exitConfirmOpen -> exitConfirmOpen = false
            keyboardOpen -> keyboardOpen = false
            physicalControllerPromptOpen -> physicalControllerPromptOpen = false
            controlsOpen -> controlsOpen = false
            else -> controlsOpen = true
        }
    }
    BackHandler(enabled = streamReady) {
        handleStreamBack()
    }
    val client = remember {
        NativeStreamClient(
            context = context.applicationContext,
            onState = {
                streamState = it
                viewModel.recordNativeStreamState(it)
                if (it == "Streaming") viewModel.markStreamConnected()
            },
            onError = {
                streamState = it
                viewModel.markStreamError(it)
            },
            onSafeVideoFallbackApplied = {
                streamState = it
                viewModel.recordLocalSafeVideoFallback(it)
            },
            onSessionRecoveryRequired = {
                streamState = it
                viewModel.recoverStreamSession(it)
            },
            onFirstVideoFrameRendered = {
                markInitialVideoFrameRendered()
            },
            onStats = {
                streamStats = it
                viewModel.updateStreamRuntimeStats(it)
            },
            onControllerMouseAssistChanged = {
                controllerMouseAssistEnabled = it
            },
            onStreamStopped = {
                viewModel.stopStream()
            },
        )
    }

    DisposableEffect(Unit) {
        val decor = activity?.window?.decorView
        NativeStreamInputRouter.attach(client)
        NativeStreamInputRouter.setAndroidTvProfile(tvProfile)
        onDispose {
            if (Build.VERSION.SDK_INT >= 26) {
                decor?.releasePointerCapture()
            }
            NativeStreamInputRouter.clearUiTouchPassthroughBounds()
            NativeStreamInputRouter.clearStreamPanelTouchPassthroughBounds()
            NativeStreamInputRouter.setSystemMenuHandler(null)
            NativeStreamInputRouter.setSystemBackHandler(null)
            NativeStreamInputRouter.setAndroidTvProfile(false)
            NativeStreamInputRouter.setStreamUiActive(false)
            NativeStreamInputRouter.detach(client)
            client.release()
        }
    }
    DisposableEffect(audioController) {
        onDispose {
            audioController.release()
        }
    }

    LaunchedEffect(streamReady, streamOverlayOpen, streamGuideOpen, streamGuideStep, touchLayoutEditing) {
        NativeStreamInputRouter.setStreamUiActive(streamReady && streamOverlayOpen)
        NativeStreamInputRouter.setSystemMenuHandler {
            openControlsForGuide()
        }
        NativeStreamInputRouter.setSystemBackHandler {
            handleStreamBack()
        }
    }

    LaunchedEffect(client, tvProfile) {
        client.updateAndroidTvProfile(tvProfile)
        client.updateControllerMouseAssistAutoArm(tvProfile)
    }

    LaunchedEffect(streamReady, state.settings.androidStreamGuideDismissed, session?.sessionId) {
        val shouldOpenGuide = streamReady && !state.settings.androidStreamGuideDismissed
        streamGuideOpen = shouldOpenGuide
        if (shouldOpenGuide) {
            streamGuideStep = StreamGuideStep.OpenControls
        }
    }

    LaunchedEffect(controlsOpen, streamGuideOpen, streamGuideStep) {
        if (controlsOpen && streamGuideOpen && streamGuideStep == StreamGuideStep.OpenControls) {
            streamGuideStep = StreamGuideStep.PressDone
        }
    }

    LaunchedEffect(
        physicalControllerConnected,
        touchControlsSuppressedByPhysicalController,
        streamGuideOpen,
        controlsOpen,
        exitConfirmOpen,
        keyboardOpen,
    ) {
        if (!physicalControllerConnected) {
            showTouchControlsWithPhysicalController = false
            physicalControllerPromptOpen = false
            return@LaunchedEffect
        }
        if (
            !tvProfile &&
            touchControlsSuppressedByPhysicalController &&
            !state.settings.androidPhysicalControllerPromptDismissed &&
            !physicalControllerPromptHandled &&
            !streamGuideOpen &&
            !controlsOpen &&
            !exitConfirmOpen &&
            !keyboardOpen
        ) {
            physicalControllerPromptOpen = true
        }
    }

    LaunchedEffect(streamReady, state.settings.sessionCounterEnabled, session?.sessionId, sessionStartedAtMs, smartSessionLimit) {
        var previousRemainingSeconds: Int? = null
        val sentSessionWarnings = mutableSetOf<Int>()
        while (streamReady && state.settings.sessionCounterEnabled) {
            val nowMs = System.currentTimeMillis()
            timerNowMs = nowMs
            val remainingSeconds = sessionRemainingSeconds(smartSessionLimit, sessionStartedAtMs, nowMs)
            sessionWarningThresholdCrossed(previousRemainingSeconds, remainingSeconds)?.let { thresholdSeconds ->
                if (sentSessionWarnings.add(thresholdSeconds)) {
                    Toast.makeText(
                        context,
                        "${formatSessionWarningThreshold(thresholdSeconds)} left in this session",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            previousRemainingSeconds = remainingSeconds
            delay(1000L)
        }
    }

    LaunchedEffect(streamReady, touchInputEnabled, state.settings.androidTouch.mousePad) {
        NativeStreamInputRouter.setTouchMouseEnabled(streamReady && touchInputEnabled && state.settings.androidTouch.mousePad)
    }
    LaunchedEffect(state.settings.androidTouch.mouseDirectClick) {
        NativeStreamInputRouter.setMouseDirectClick(state.settings.androidTouch.mouseDirectClick)
    }

    LaunchedEffect(streamReady, touchInputEnabled, state.settings.androidTouch.mousePad, controlsOpen, exitConfirmOpen, keyboardOpen, streamGuideOpen, touchControlsVisible) {
        NativeStreamInputRouter.setCaptureAllTouch(
            streamReady &&
                touchInputEnabled &&
                state.settings.androidTouch.mousePad &&
                !controlsOpen &&
                !exitConfirmOpen &&
                !keyboardOpen &&
                !streamGuideOpen,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            NativeStreamInputRouter.setCaptureAllTouch(false)
        }
    }

    LaunchedEffect(state.settings.phoneRumbleFallback) {
        client.updateHapticsSettings(state.settings.phoneRumbleFallback)
    }
    LaunchedEffect(session?.sessionId, session?.status, streamReady) {
        if (session != null && streamReady) {
            client.start(session, launchStreamSettings)
        }
    }
    LaunchedEffect(client, controllerMouseEmulationEnabled, streamReady) {
        if (streamReady) {
            client.setControllerMouseEmulationActive(controllerMouseEmulationEnabled)
        }
    }
    LaunchedEffect(
        session?.sessionId,
        streamReady,
        streamStats,
        launchStreamSettings.resolution,
        launchStreamSettings.aspectRatio,
        session?.negotiatedStreamProfile?.resolution,
    ) {
        val mismatch = streamRuntimeResolutionMismatch(
            launchStreamSettings,
            streamStats.resolution,
            session?.negotiatedStreamProfile?.resolution,
        )
        if (streamReady && mismatch != null) {
            viewModel.recordRuntimeResolutionChange(
                actualResolution = mismatch.actualResolution,
                expectedResolution = mismatch.expectedResolution,
                serverNegotiatedFallback = mismatch.isServerNegotiatedFallback,
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (state.activeSessionDecision != null) {
            ActiveSessionDecisionScreen(
                state = state,
                onResumeSession = viewModel::resumeActiveSession,
                onReplaceSession = viewModel::terminateActiveSessionAndStartNew,
                onCancel = viewModel::dismissActiveSessionDecision,
            )
        } else if (session == null && state.streamStatus != "idle") {
            QueueLoadingScreen(state, viewModel)
        } else if (session == null) {
            NoActiveStreamScreen(
                canResumeSession = state.activeSession != null,
                canEndSession = state.authSession != null,
                onBack = { viewModel.setPage(AppPage.Home) },
                onResumeSession = viewModel::resumeActiveSession,
                onEndSession = viewModel::stopStream,
            )
        } else if (!streamReady) {
            QueueLoadingScreen(state, viewModel)
        } else {
            StreamVideoSurface(
                client = client,
                settings = streamSettings,
                decodedResolution = streamStats.resolution,
                serverNegotiatedResolution = session.negotiatedStreamProfile?.resolution,
                hideExternalMousePointer = externalMousePassthroughActive,
                touchMouseEnabled = touchInputEnabled && state.settings.androidTouch.mousePad,
                externalMouseRoot = activity?.window?.decorView,
                onMouseCaptureInput = { (activity as? MainActivity)?.enforceStreamSystemUiFromInput() },
                stretchToFill = stretchToFill,
                stretchToZoom = stretchToZoom,
            )
            if (statsVisible) {
                StreamStatsPill(
                    streamStats = streamStats,
                    streamSettings = launchStreamSettings,
                    style = state.settings.streamStatsStyle,
                    metrics = state.settings.streamStatsMetrics,
                    serverLocation = session.zone,
                    modifier = Modifier.align(statsAlignment),
                )
            }
            if (touchControlsVisible) {
                TouchOverlay(
                    client = client,
                    touch = state.settings.androidTouch.copy(enabled = true),
                    onButtonTone = {
                        if (state.settings.phoneRumbleFallback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                            )
                        }
                    },
                    layoutEditing = touchLayoutEditing,
                    onSaveAllOffsets = { allOffsets ->
                        var touch = state.settings.androidTouch
                        allOffsets.forEach { (key, offset) ->
                            touch = touch.withOffset(key, offset.x, offset.y)
                        }
                        viewModel.updateSettings(state.settings.copy(androidTouch = touch))
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            AnimatedVisibility(
                visible = !initialVideoFrameRendered,
                enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.96f),
                exit = fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.98f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                InitialStreamConnectionOverlay(
                    gameTitle = game?.title,
                    status = initialStreamConnectionStatus(streamState),
                )
            }
            if (touchLayoutEditing) {
                val doneButtonTone = playButtonTone
                Box(
                    Modifier
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = {
                            doneButtonTone()
                            touchLayoutEditing = false
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                        modifier = Modifier.pointerInteropFilter { event ->
                            if (event.action == MotionEvent.ACTION_UP ||
                                event.action == MotionEvent.ACTION_DOWN
                            ) {
                                false // let Button's click handling still work
                            } else {
                                false
                            }
                        },
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Done",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (streamGuideOpen) {
                AnimatedLaunchOverlay(Modifier.align(Alignment.Center)) {
                    StreamFirstLaunchGuide(
                        step = streamGuideStep,
                        controlsOpen = controlsOpen,
                        touchControlsEnabled = touchControlsVisible,
                        onOpenControls = {
                            playButtonTone()
                            openControlsForGuide()
                        },
                        onSkip = {
                            playButtonTone()
                            controlsOpen = false
                            dismissStreamGuide()
                        },
                    )
                }
            }
            if (physicalControllerPromptOpen) {
                PhysicalControllerTouchControlsDialog(
                    doNotShowAgain = physicalControllerPromptDoNotShowAgain,
                    onDoNotShowAgainChange = { physicalControllerPromptDoNotShowAgain = it },
                    onOk = {
                        physicalControllerPromptHandled = true
                        physicalControllerPromptOpen = false
                        showTouchControlsWithPhysicalController = false
                        if (physicalControllerPromptDoNotShowAgain) {
                            viewModel.updateSettings(
                                state.settings.copy(androidPhysicalControllerPromptDismissed = true),
                            )
                        }
                    },
                    onUndo = {
                        physicalControllerPromptHandled = true
                        physicalControllerPromptOpen = false
                        showTouchControlsWithPhysicalController = true
                        if (physicalControllerPromptDoNotShowAgain) {
                            viewModel.updateSettings(
                                state.settings.copy(androidPhysicalControllerPromptDismissed = true),
                            )
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = controlsOpen,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }) + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }) + scaleOut(targetScale = 0.96f),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                StreamControlsPanel(
                    gameTitle = game?.title ?: "Stream",
                    status = (state.queuePosition?.let { "Queue $it" } ?: streamState).takeUnless(::shouldHideStreamStatusText),
                    settings = state.settings,
                    tvProfile = tvProfile,
                    touchControlsVisible = touchControlsVisible,
                    controllerMouseAssistEnabled = controllerMouseAssistEnabled,
                    controllerMouseEmulationEnabled = controllerMouseEmulationEnabled,
                    showSessionTimer = state.settings.sessionCounterEnabled,
                    sessionTimerLimit = smartSessionLimit,
                    sessionStartedAtMs = sessionStartedAtMs,
                    sessionNowMs = timerNowMs,
                    audioMuted = audioMuted,
                    statsVisible = statsVisible,
                    touchLayoutEditing = touchLayoutEditing,
                    onAudioToggle = {
                        audioMuted = !audioMuted
                        client.setAudioMuted(audioMuted)
                    },
                    onStatsToggle = {
                        statsVisible = !statsVisible
                        viewModel.updateSettings(state.settings.copy(showStatsOnLaunch = statsVisible))
                    },
                    onStatsStyleCycle = {
                        viewModel.updateSettings(state.settings.copy(streamStatsStyle = state.settings.streamStatsStyle.next()))
                    },
                    onStatsPositionCycle = {
                        viewModel.updateSettings(state.settings.copy(streamStatsPosition = state.settings.streamStatsPosition.next()))
                    },
                    onStatsMetricsChange = { metrics ->
                        viewModel.updateSettings(state.settings.copy(streamStatsMetrics = metrics))
                    },
                    onPhoneRumbleFallbackToggle = {
                        viewModel.updateSettings(state.settings.copy(phoneRumbleFallback = !state.settings.phoneRumbleFallback))
                    },
                    onTouchLayoutEditingToggle = {
                        touchLayoutEditing = !touchLayoutEditing
                    },
                    onKeyboardOpen = {
                        controlsOpen = false
                        keyboardOpen = true
                    },
                    onEsc = { client.sendKeyCode(KeyEvent.KEYCODE_ESCAPE) },
                    onEnter = { client.sendKeyCode(KeyEvent.KEYCODE_ENTER) },
                    onBackspace = { client.sendKeyCode(KeyEvent.KEYCODE_DEL) },
                    onSteamMenuOpen = {
                        controlsOpen = false
                        client.openSteamMenu()
                    },
                    onControllerMouseAssistToggle = {
                        client.setControllerMouseAssistEnabled(!controllerMouseAssistEnabled)
                    },
                    onControllerMouseEmulationToggle = {
                        val newState = !controllerMouseEmulationEnabled
                        controllerMouseEmulationEnabled = newState
                        client.setControllerMouseEmulationActive(newState)
                    },
                    onExit = {
                        controlsOpen = false
                        exitConfirmOpen = true
                    },
                    onTouchControlsToggle = {
                        if (physicalControllerConnected && !touchControlsVisible) {
                            showTouchControlsWithPhysicalController = true
                            if (!state.settings.androidTouch.enabled) {
                                viewModel.updateSettings(
                                    state.settings.copy(
                                        androidTouch = state.settings.androidTouch.copy(enabled = true),
                                    ),
                                )
                            }
                        } else {
                            viewModel.updateSettings(
                                state.settings.copy(
                                    androidTouch = state.settings.androidTouch.copy(enabled = !state.settings.androidTouch.enabled),
                                ),
                            )
                        }
                    },
                    onMousePadToggle = {
                        viewModel.updateSettings(
                            state.settings.copy(
                                androidTouch = state.settings.androidTouch.copy(mousePad = !state.settings.androidTouch.mousePad),
                            ),
                        )
                    },
                    onMouseDirectClickToggle = {
                        viewModel.updateSettings(
                            state.settings.copy(
                                androidTouch = state.settings.androidTouch.copy(mouseDirectClick = !state.settings.androidTouch.mouseDirectClick),
                            ),
                        )
                    },
                    onToggleTouchControllerStyle = {
                        val nextStyle = if (state.settings.androidTouch.touchControllerStyle == TouchControllerStyle.V1) {
                            TouchControllerStyle.V2
                        } else {
                            TouchControllerStyle.V1
                        }
                        viewModel.updateSettings(
                            state.settings.copy(
                                androidTouch = state.settings.androidTouch.copy(touchControllerStyle = nextStyle),
                            ),
                        )
                    },
                    onSharpeningToggle = {
                        viewModel.updateStreamSettings { settings ->
                            settings.copy(streamSharpeningEnabled = !settings.streamSharpeningEnabled)
                        }
                    },
                    onSharpeningAmountChange = { value ->
                        viewModel.updateStreamSettings { settings ->
                            settings.copy(streamSharpeningAmount = value)
                        }
                    },
                    onStretchToFillToggle = {
                        // Mutually exclusive: turning on fill clears zoom.
                        val next = !state.settings.stretchStreamToFill
                        viewModel.updateSettings(
                            state.settings.copy(
                                stretchStreamToFill = next,
                                stretchStreamToZoom = if (next) false else state.settings.stretchStreamToZoom,
                            )
                        )
                    },
                    onStretchToZoomToggle = {
                        // Mutually exclusive: turning on zoom clears fill.
                        val next = !state.settings.stretchStreamToZoom
                        viewModel.updateSettings(
                            state.settings.copy(
                                stretchStreamToZoom = next,
                                stretchStreamToFill = if (next) false else state.settings.stretchStreamToFill,
                            )
                        )
                    },
                    onTouchScaleChange = { value ->
                        viewModel.updateSettings(state.settings.copy(androidTouch = state.settings.androidTouch.copy(scale = value)))
                    },
                    onButtonScaleChange = { value ->
                        viewModel.updateSettings(state.settings.copy(androidTouch = state.settings.androidTouch.copy(buttonScale = value)))
                    },
                    onStickScaleChange = { value ->
                        viewModel.updateSettings(state.settings.copy(androidTouch = state.settings.androidTouch.copy(stickScale = value)))
                    },
                    onOpacityChange = { value ->
                        viewModel.updateSettings(state.settings.copy(androidTouch = state.settings.androidTouch.copy(opacity = value)))
                    },
                    onTouchEdgePaddingChange = { value ->
                        viewModel.updateSettings(state.settings.copy(androidTouch = state.settings.androidTouch.copy(edgePaddingDp = value)))
                    },
                    onTouchBottomPaddingChange = { value ->
                        viewModel.updateSettings(state.settings.copy(androidTouch = state.settings.androidTouch.copy(bottomPaddingDp = value)))
                    },
                    onTouchLeftOffsetChange = { value ->
                        viewModel.updateSettings(state.settings.copy(androidTouch = state.settings.androidTouch.copy(leftOffsetYDp = value)))
                    },
                    onTouchRightOffsetChange = { value ->
                        viewModel.updateSettings(state.settings.copy(androidTouch = state.settings.androidTouch.copy(rightOffsetYDp = value)))
                    },
                    onTouchLayoutReset = {
                        viewModel.updateSettings(
                            state.settings.copy(
                                androidTouch = state.settings.androidTouch.withResetOffsets()
                            )
                        )
                    },
                    onButtonTone = playButtonTone,
                    highlightDone = streamGuideOpen && streamGuideStep == StreamGuideStep.PressDone,
                    onClose = {
                        controlsOpen = false
                        if (streamGuideOpen && streamGuideStep == StreamGuideStep.PressDone) {
                            dismissStreamGuide()
                        }
                    },
                )
            }
            if (keyboardOpen) {
                AnimatedLaunchOverlay(Modifier.align(Alignment.BottomCenter)) {
                    StreamKeyboardBar(
                        text = keyboardText,
                        onTextChange = { keyboardText = it },
                        onSend = {
                            val text = keyboardText
                            if (text.isNotBlank()) {
                                client.sendText(text)
                                keyboardText = ""
                                keyboardOpen = false
                            }
                        },
                        onBackspace = { client.sendKeyCode(KeyEvent.KEYCODE_DEL) },
                        onEnter = { client.sendKeyCode(KeyEvent.KEYCODE_ENTER) },
                        onEsc = { client.sendKeyCode(KeyEvent.KEYCODE_ESCAPE) },
                        onDone = { keyboardOpen = false },
                    )
                }
            }
            if (exitConfirmOpen) {
                AnimatedLaunchOverlay(Modifier.align(Alignment.Center)) {
                    StreamExitConfirmation(
                        gameTitle = game?.title ?: "this game",
                        onKeepPlaying = { exitConfirmOpen = false },
                        onExit = {
                            exitConfirmOpen = false
                            viewModel.stopStream()
                        },
                    )
                }
            }
        }
    }
}

internal fun shouldShowAndroidTouchControls(
    tvProfile: Boolean,
    touchInputEnabled: Boolean,
    touchControlsEnabled: Boolean,
    suppressedByPhysicalController: Boolean,
): Boolean =
    !tvProfile && touchInputEnabled && touchControlsEnabled && !suppressedByPhysicalController

private data class SessionTimerDisplay(
    val label: String,
    val value: String,
    val detail: String,
    val progress: Float,
    val warning: Boolean,
)

private enum class StreamGuideStep {
    OpenControls,
    PressDone,
}

private fun sessionTimerDisplay(limit: SmartSessionLimit, startedAtMs: Long, nowMs: Long): SessionTimerDisplay {
    val elapsedSeconds = sessionElapsedSeconds(startedAtMs, nowMs)
    val limitSeconds = limit.limitHours * 60 * 60
    val remainingSeconds = sessionRemainingSeconds(limit, startedAtMs, nowMs)
    val warning = remainingSeconds <= 10 * 60
    val progress = if (limitSeconds > 0) (elapsedSeconds.toFloat() / limitSeconds).coerceIn(0f, 1f) else 0f
    return when (limit.mode) {
        SessionTimerMode.Countdown -> SessionTimerDisplay(
            label = "${limit.tierLabel} countdown",
            value = formatSessionTimerDuration(remainingSeconds),
            detail = "${limit.limitHours}h session limit",
            progress = progress,
            warning = warning,
        )
        SessionTimerMode.Stopwatch -> SessionTimerDisplay(
            label = "${limit.tierLabel} session",
            value = "${formatSessionTimerDuration(elapsedSeconds)} / ${limit.limitHours}h",
            detail = "Session stopwatch",
            progress = progress,
            warning = warning,
        )
    }
}

@Composable
private fun StreamSessionTimerMenuRow(
    limit: SmartSessionLimit,
    startedAtMs: Long,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    val display = sessionTimerDisplay(limit, startedAtMs, nowMs)
    val progressColor = when {
        display.warning -> Color(0xffffc266)
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Session timer", fontWeight = FontWeight.SemiBold)
                Text(display.label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                display.value,
                color = if (display.warning) Color(0xffffc266) else TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(display.progress)
                    .height(4.dp)
                    .background(progressColor),
            )
        }
        Text(display.detail, color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatSessionTimerDuration(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(Locale.US, minutes, remainingSeconds)
    }
}

private fun formatSessionWarningThreshold(thresholdSeconds: Int): String {
    val minutes = thresholdSeconds / 60
    return if (minutes == 1) "1 minute" else "$minutes minutes"
}

@Composable
private fun StreamVideoSurface(
    client: NativeStreamClient,
    settings: StreamSettings,
    decodedResolution: String?,
    serverNegotiatedResolution: String?,
    hideExternalMousePointer: Boolean,
    touchMouseEnabled: Boolean,
    externalMouseRoot: android.view.View?,
    onMouseCaptureInput: () -> Unit,
    stretchToFill: Boolean,
    stretchToZoom: Boolean,
    modifier: Modifier = Modifier,
) {
    val rootView = LocalView.current
    val configuration = LocalConfiguration.current
    val pointerRootView = externalMouseRoot ?: rootView
    val currentOnMouseCaptureInput by rememberUpdatedState(onMouseCaptureInput)
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val streamAspectRatio = remember(decodedResolution, serverNegotiatedResolution, settings.resolution, settings.aspectRatio) {
        streamRendererAspectRatio(settings, decodedResolution, serverNegotiatedResolution)
    }
    val viewportAspectRatio = remember(viewportSize) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            viewportSize.width.toFloat() / viewportSize.height.toFloat()
        } else {
            0f
        }
    }
    val rendererModifier = if (stretchToFill || viewportAspectRatio <= 0f) {
        // For stretchToFill (native zoom), the View must fill all space.
        Modifier.fillMaxSize()
    } else if (viewportAspectRatio > streamAspectRatio) {
        // Screen is wider than stream (e.g. 2400×1080 screen, 1920×1080 stream).
        // Fit by height so the renderer has no black bars internally; horizontal
        // stretch (if enabled) is applied later via View.scaleX.
        Modifier
            .fillMaxHeight()
            .aspectRatio(streamAspectRatio)
    } else {
        // Screen is taller than stream — fit by width; vertical stretch via scaleY.
        Modifier
            .fillMaxWidth()
            .aspectRatio(streamAspectRatio)
    }

    // Non-uniform scale factors used to stretch the renderer view to fill the
    // viewport when stretchToZoom is active. SCALE_ASPECT_FIT keeps content
    // intact inside the renderer; these View-level scales expand it to screen
    // edges without any cropping.
    val stretchScaleX = remember(stretchToZoom, viewportAspectRatio, streamAspectRatio) {
        if (stretchToZoom && viewportAspectRatio > 0f && streamAspectRatio > 0f &&
            viewportAspectRatio > streamAspectRatio
        ) {
            (viewportAspectRatio / streamAspectRatio).coerceIn(1f, 3f)
        } else {
            1f
        }
    }
    val stretchScaleY = remember(stretchToZoom, viewportAspectRatio, streamAspectRatio) {
        if (stretchToZoom && viewportAspectRatio > 0f && streamAspectRatio > 0f &&
            viewportAspectRatio < streamAspectRatio
        ) {
            (streamAspectRatio / viewportAspectRatio).coerceIn(1f, 3f)
        } else {
            1f
        }
    }
    LaunchedEffect(
        settings.resolution,
        settings.aspectRatio,
        stretchToFill,
        stretchToZoom,
        streamAspectRatio,
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        zoomScale = 1f
        zoomOffset = Offset.Zero
    }
    LaunchedEffect(stretchToFill, stretchToZoom) {
        NativeStreamInputRouter.setStretchToFill(stretchToFill || stretchToZoom)
    }
    LaunchedEffect(streamAspectRatio) {
        NativeStreamInputRouter.setRenderingAspectRatio(streamAspectRatio)
    }
    DisposableEffect(client, rootView, pointerRootView, hideExternalMousePointer) {
        pointerRootView.configureAndroidMousePointerCapture(hideExternalMousePointer, { currentOnMouseCaptureInput() }) { event ->
            client.dispatchMotion(event)
        }
        if (hideExternalMousePointer) {
            pointerRootView.hideAndroidPointerTree()
        } else {
            pointerRootView.showAndroidPointerTree()
        }
        onDispose {
            pointerRootView.clearAndroidMousePointerCapture()
            pointerRootView.showAndroidPointerTree()
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                if (viewportSize != it) {
                    viewportSize = it
                    zoomScale = 1f
                    zoomOffset = Offset.Zero
                } else {
                    zoomOffset = clampStreamZoomOffset(zoomOffset, zoomScale, it)
                }
            }
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = zoomOffset.x
                    translationY = zoomOffset.y
                },
            contentAlignment = Alignment.Center,
        ) {
            // AndroidView resizes the SurfaceView in place. Re-keying it as the viewport
            // settles creates overlapping renderer surfaces during stream startup.
            key(settings.streamSharpeningEnabled) {
                AndroidView(
                    modifier = rendererModifier,
                    factory = { ctx ->
                        client.createRenderer(ctx, settings, stretchToFill).apply {
                            isFocusable = false
                            isFocusableInTouchMode = false
                            hideAndroidPointerTree()
                            scaleX = stretchScaleX
                            scaleY = stretchScaleY
                        }
                    },
                    update = { renderer ->
                        client.updateRendererSettings(settings, stretchToFill)
                        renderer.scaleX = stretchScaleX
                        renderer.scaleY = stretchScaleY
                        renderer.isFocusable = false
                        renderer.isFocusableInTouchMode = false
                        pointerRootView.configureAndroidMousePointerCapture(hideExternalMousePointer, { currentOnMouseCaptureInput() }) { event ->
                            client.dispatchMotion(event)
                        }
                        if (hideExternalMousePointer) {
                            pointerRootView.hideAndroidPointerTree()
                            renderer.hideAndroidPointerTree()
                        } else {
                            pointerRootView.showAndroidPointerTree()
                            renderer.showAndroidPointerTree()
                        }
                        renderer.setOnKeyListener(null)
                        renderer.setOnGenericMotionListener { _, event ->
                            if (hideExternalMousePointer) pointerRootView.hideAndroidPointerTree()
                            client.dispatchMotion(event)
                        }
                        renderer.setOnTouchListener { view, event ->
                            NativeStreamInputRouter.dispatchTouch(event, view.width, view.height)
                        }
                    },
                    onRelease = client::releaseRenderer,
                )
            }
        }
        FingerMouseInputLayer(
            enabled = touchMouseEnabled,
            onZoomGesture = { scaleChange, pan ->
                val nextScale = (zoomScale * scaleChange).coerceIn(1f, 3f)
                zoomScale = nextScale
                zoomOffset = if (nextScale <= 1.001f) {
                    Offset.Zero
                } else {
                    clampStreamZoomOffset(zoomOffset + pan, nextScale, viewportSize)
                }
            },
            modifier = Modifier.matchParentSize(),
        )
    }
}

internal fun streamRendererAspectRatio(
    settings: StreamSettings,
    decodedResolution: String?,
    serverNegotiatedResolution: String? = null,
): Float {
    val expectedPixels = streamResolutionPixels(settings)
    val expectedAspectRatio = streamAspectRatioForPixels(expectedPixels)
    val decodedPixels = parseResolutionPixelsOrNull(decodedResolution)
        ?.takeIf(::isStableDecodedStreamResolution)
        ?: return expectedAspectRatio
    val decodedAspectRatio = streamAspectRatioForPixels(decodedPixels)
    val negotiatedPixels = parseResolutionPixelsOrNull(serverNegotiatedResolution)
    return if (
        decodedPixels == expectedPixels ||
        decodedPixels == negotiatedPixels ||
        streamAspectRatiosClose(decodedAspectRatio, expectedAspectRatio)
    ) {
        decodedAspectRatio
    } else {
        expectedAspectRatio
    }
}

private fun streamAspectRatioForPixels(pixels: Pair<Int, Int>): Float {
    val (width, height) = pixels
    if (width <= 0 || height <= 0) return 16f / 9f
    return width.toFloat() / height.toFloat()
}

private fun isStableDecodedStreamResolution(pixels: Pair<Int, Int>): Boolean =
    pixels.first >= MIN_STABLE_DECODED_STREAM_WIDTH_PX &&
        pixels.second >= MIN_STABLE_DECODED_STREAM_HEIGHT_PX

private fun streamAspectRatiosClose(first: Float, second: Float): Boolean {
    val baseline = maxOf(second, 0.001f)
    return abs(first - second) / baseline <= STREAM_RENDERER_ASPECT_TOLERANCE
}

private const val MIN_STABLE_DECODED_STREAM_WIDTH_PX = 320
private const val MIN_STABLE_DECODED_STREAM_HEIGHT_PX = 180
private const val STREAM_RENDERER_ASPECT_TOLERANCE = 0.08f

private fun clampStreamZoomOffset(offset: Offset, zoomScale: Float, viewportSize: IntSize): Offset {
    if (zoomScale <= 1.001f || viewportSize.width <= 0 || viewportSize.height <= 0) return Offset.Zero
    val maxX = viewportSize.width * (zoomScale - 1f) / 2f
    val maxY = viewportSize.height * (zoomScale - 1f) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

private fun androidNullPointerIcon(view: android.view.View): PointerIcon? =
    if (Build.VERSION.SDK_INT >= 24) {
        runCatching { PointerIcon.getSystemIcon(view.context, PointerIcon.TYPE_NULL) }
            .onFailure { error -> NativeInputDiagnostics.add("pointer icon unavailable error=${error.javaClass.simpleName}") }
            .getOrNull()
    } else {
        null
    }

private fun View.configureAndroidMousePointerCapture(enabled: Boolean, onCaptureInput: () -> Unit = {}, onMotion: (MotionEvent) -> Boolean) {
    if (Build.VERSION.SDK_INT < 26) return
    if (!enabled) {
        clearAndroidMousePointerCapture()
        return
    }
    setOnCapturedPointerListener { _, event ->
        onCaptureInput()
        onMotion(event)
    }
    post {
        if (isAttachedToWindow && hasWindowFocus() && !hasPointerCapture()) {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            onCaptureInput()
            runCatching { requestPointerCapture() }
                .onFailure { error -> NativeInputDiagnostics.add("pointer capture request failed error=${error.javaClass.simpleName}") }
        }
    }
}

private fun View.clearAndroidMousePointerCapture() {
    if (Build.VERSION.SDK_INT < 26) return
    setOnCapturedPointerListener(null)
    runCatching { releasePointerCapture() }
        .onFailure { error -> NativeInputDiagnostics.add("pointer capture release failed error=${error.javaClass.simpleName}") }
}

private fun android.view.View.hideAndroidPointerTree() {
    if (Build.VERSION.SDK_INT < 24) return
    val icon = androidNullPointerIcon(this)
    applyAndroidPointerIconTree(icon)
}

private fun android.view.View.showAndroidPointerTree() {
    if (Build.VERSION.SDK_INT < 24) return
    applyAndroidPointerIconTree(null)
}

private fun android.view.View.applyAndroidPointerIconTree(icon: PointerIcon?) {
    if (Build.VERSION.SDK_INT < 24) return
    runCatching { pointerIcon = icon }
        .onFailure { error -> NativeInputDiagnostics.add("pointer icon apply failed error=${error.javaClass.simpleName}") }
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).applyAndroidPointerIconTree(icon)
        }
    }
}

@Composable
private fun FingerMouseInputLayer(
    enabled: Boolean,
    onZoomGesture: (scaleChange: Float, pan: Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    var width by remember { mutableStateOf(0) }
    var height by remember { mutableStateOf(0) }
    var pinchActive by remember { mutableStateOf(false) }
    var lastPinchDistance by remember { mutableFloatStateOf(0f) }
    var lastPinchCentroid by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onSizeChanged {
                width = it.width
                height = it.height
            }
            .pointerInteropFilter { event ->
                if (NativeStreamInputRouter.isNativeUiTouchGestureActive()) {
                    pinchActive = false
                    lastPinchDistance = 0f
                    lastPinchCentroid = Offset.Zero
                    return@pointerInteropFilter true
                }
                if (event.pointerCount >= 2) {
                    // 3-finger touch is reserved for the Direct Click toggle gesture
                    // (handled in NativeStreamInputRouter.dispatchTouch). Do not
                    // interpret it as a pinch-zoom — reset pinch state and let it through.
                    if (event.pointerCount >= 3) {
                        pinchActive = false
                        lastPinchDistance = 0f
                        lastPinchCentroid = Offset.Zero
                        NativeStreamInputRouter.dispatchTouch(event, width, height)
                        return@pointerInteropFilter true
                    }
                    NativeStreamInputRouter.cancelTouchMouse()
                    val distance = event.firstTwoPointerDistance()
                    val centroid = event.firstTwoPointerCentroid()
                    if (pinchActive && lastPinchDistance > 0f && distance > 0f) {
                        onZoomGesture(
                            (distance / lastPinchDistance).coerceIn(0.82f, 1.22f),
                            centroid - lastPinchCentroid,
                        )
                    }
                    pinchActive = true
                    lastPinchDistance = distance
                    lastPinchCentroid = centroid
                    return@pointerInteropFilter true
                }
                if (pinchActive) {
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        pinchActive = false
                        lastPinchDistance = 0f
                        lastPinchCentroid = Offset.Zero
                    }
                    return@pointerInteropFilter true
                }
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    NativeInputDiagnostics.add("compose finger layer down size=${width}x$height")
                }
                NativeStreamInputRouter.dispatchTouch(event, width, height)
            },
    )
}

private fun MotionEvent.firstTwoPointerDistance(): Float {
    if (pointerCount < 2) return 0f
    val dx = getX(1) - getX(0)
    val dy = getY(1) - getY(0)
    return sqrt(dx * dx + dy * dy)
}

private fun MotionEvent.firstTwoPointerCentroid(): Offset =
    if (pointerCount >= 2) {
        Offset((getX(0) + getX(1)) / 2f, (getY(0) + getY(1)) / 2f)
    } else {
        Offset.Zero
    }

@Composable
private fun ActiveSessionDecisionScreen(
    state: OpenNowUiState,
    onResumeSession: () -> Unit,
    onReplaceSession: () -> Unit,
    onCancel: () -> Unit,
) {
    val decision = state.activeSessionDecision ?: return
    val active = decision.activeSession
    val activeGame = activeSessionGame(state, active)
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            shape = RoundedCornerShape(18.dp),
            color = PanelAlt.copy(alpha = 0.96f),
            contentColor = TextPrimary,
            tonalElevation = 4.dp,
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    UrlImage(
                        activeGame?.imageUrl ?: state.streamGame?.imageUrl,
                        Modifier
                            .width(56.dp)
                            .height(74.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Cloud session already active", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            activeGame?.title ?: "App ${active.appId}",
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            activeSessionSummary(active),
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    "Resume the existing session, or terminate it and start ${decision.requestedGameTitle}.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    OutlinedButton(onClick = onReplaceSession) { Text("Terminate and start new") }
                    Button(onClick = onResumeSession) { Text(stringResource(R.string.action_resume)) }
                }
            }
        }
    }
}

@Composable
private fun NoActiveStreamScreen(
    canResumeSession: Boolean,
    canEndSession: Boolean,
    onBack: () -> Unit,
    onResumeSession: () -> Unit,
    onEndSession: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No active stream", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "OpenNOW does not have a local stream attached right now.",
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back to library") }
            if (canResumeSession) {
                Button(onClick = onResumeSession) { Text(stringResource(R.string.action_resume)) }
            }
            if (canEndSession) {
                Button(onClick = onEndSession) { Text("End cloud session") }
            }
        }
    }
}

@Composable
private fun StreamControlLauncher(
    controlsOpen: Boolean,
    status: String?,
    onToggle: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .padding(top = 10.dp, end = 10.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                NativeStreamInputRouter.setUiTouchPassthroughBounds(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt(),
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status != null) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Panel.copy(alpha = 0.8f),
                tonalElevation = 3.dp,
            ) {
                Text(
                    status,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Button(onClick = onToggle, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text(if (controlsOpen) "Close" else "Controls")
        }
        OutlinedButton(onClick = onExit, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Exit")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            NativeStreamInputRouter.clearUiTouchPassthroughBounds()
        }
    }
}

@Composable
private fun StreamFirstLaunchGuide(
    step: StreamGuideStep,
    controlsOpen: Boolean,
    touchControlsEnabled: Boolean,
    onOpenControls: () -> Unit,
    onSkip: () -> Unit,
) {
    val primaryFocusRequester = remember { FocusRequester() }
    val overlayInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(step, controlsOpen) {
        delay(80)
        if (step == StreamGuideStep.OpenControls || !controlsOpen) {
            runCatching { primaryFocusRequester.requestFocus() }
        }
    }
    BoxWithConstraints(
        if (step == StreamGuideStep.OpenControls) {
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = overlayInteraction,
                    indication = null,
                    onClick = {},
                )
        } else {
            Modifier.fillMaxSize()
        },
    ) {
        val landscape = maxWidth > maxHeight
        if (step == StreamGuideStep.OpenControls) {
            StreamGuideEdgeCue(Modifier.align(Alignment.CenterStart))
            StreamGuideCard(
                stepLabel = "Step 1 of 2",
                title = "Open the stream menu",
                body = "Press Android Back, Menu, or swipe from the left edge. That opens the menu without exiting the stream.",
                details = listOf(
                    "Back or the left-edge gesture opens controls.",
                    if (touchControlsEnabled) {
                        "Touch controls pause while this guide is up."
                    } else {
                        "You can turn touch controls on from the menu."
                    },
                    "Use Skip tutorial if you already know this flow.",
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(18.dp)
                    .fillMaxWidth(if (landscape) 0.54f else 0.92f)
                    .then(if (landscape) Modifier.fillMaxHeight(0.82f) else Modifier),
                primaryLabel = "Open controls",
                primaryFocusRequester = primaryFocusRequester,
                onPrimary = onOpenControls,
                secondaryLabel = "Skip tutorial",
                onSecondary = onSkip,
            )
        } else {
            StreamGuideDoneCallout(
                controlsOpen = controlsOpen,
                onOpenControls = onOpenControls,
                onSkip = onSkip,
                primaryFocusRequester = primaryFocusRequester,
                modifier = Modifier
                    .align(if (landscape) Alignment.TopStart else Alignment.TopCenter)
                    .padding(18.dp)
                    .then(if (landscape) Modifier.fillMaxWidth(0.34f) else Modifier.fillMaxWidth(0.86f))
                    .widthIn(max = 340.dp),
            )
        }
    }
}

@Composable
private fun StreamGuideCard(
    stepLabel: String,
    title: String,
    body: String,
    details: List<String>,
    primaryLabel: String,
    primaryFocusRequester: FocusRequester,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Panel.copy(alpha = 0.96f),
        contentColor = TextPrimary,
        tonalElevation = 8.dp,
    ) {
        Column(
            Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stepLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
            details.forEachIndexed { index, detail ->
                StreamGuidePoint(number = index + 1, body = detail)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = onPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(primaryFocusRequester),
                ) {
                    Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StreamGuideDoneCallout(
    controlsOpen: Boolean,
    onOpenControls: () -> Unit,
    onSkip: () -> Unit,
    primaryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Panel.copy(alpha = 0.9f),
        tonalElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Step 2 of 2", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Press Done", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(
                onClick = onSkip,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("Skip", maxLines = 1)
            }
            if (!controlsOpen) {
                Button(
                    onClick = onOpenControls,
                    modifier = Modifier.focusRequester(primaryFocusRequester),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("Open", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PhysicalControllerTouchControlsDialog(
    doNotShowAgain: Boolean,
    onDoNotShowAgainChange: (Boolean) -> Unit,
    onOk: () -> Unit,
    onUndo: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onOk,
        title = { Text("Controller detected") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The on-screen controller was hidden because a physical controller is connected.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDoNotShowAgainChange(!doNotShowAgain) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = doNotShowAgain,
                        onCheckedChange = onDoNotShowAgainChange,
                    )
                    Text("Don't show again", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOk) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onUndo) {
                Text("Undo")
            }
        },
    )
}

@Composable
private fun StreamGuideEdgeCue(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .width(112.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        Color.Transparent,
                    ),
                ),
            ),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            tonalElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Text("Back", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StreamGuidePoint(number: Int, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(body, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StreamControlsPanel(
    gameTitle: String,
    status: String?,
    settings: AppSettings,
    tvProfile: Boolean,
    touchControlsVisible: Boolean,
    controllerMouseAssistEnabled: Boolean,
    controllerMouseEmulationEnabled: Boolean,
    showSessionTimer: Boolean,
    sessionTimerLimit: SmartSessionLimit,
    sessionStartedAtMs: Long,
    sessionNowMs: Long,
    audioMuted: Boolean,
    statsVisible: Boolean,
    touchLayoutEditing: Boolean,
    onAudioToggle: () -> Unit,
    onStatsToggle: () -> Unit,
    onStatsStyleCycle: () -> Unit,
    onStatsPositionCycle: () -> Unit,
    onStatsMetricsChange: (StreamStatsMetrics) -> Unit,
    onPhoneRumbleFallbackToggle: () -> Unit,
    onTouchLayoutEditingToggle: () -> Unit,
    onKeyboardOpen: () -> Unit,
    onEsc: () -> Unit,
    onEnter: () -> Unit,
    onBackspace: () -> Unit,
    onSteamMenuOpen: () -> Unit,
    onControllerMouseAssistToggle: () -> Unit,
    onControllerMouseEmulationToggle: () -> Unit,
    onExit: () -> Unit,
    onTouchControlsToggle: () -> Unit,
    onMousePadToggle: () -> Unit,
    onMouseDirectClickToggle: () -> Unit,
    onToggleTouchControllerStyle: () -> Unit,
    onSharpeningToggle: () -> Unit,
    onSharpeningAmountChange: (Float) -> Unit,
    onStretchToFillToggle: () -> Unit,
    onStretchToZoomToggle: () -> Unit,
    onTouchScaleChange: (Float) -> Unit,
    onButtonScaleChange: (Float) -> Unit,
    onStickScaleChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onTouchEdgePaddingChange: (Float) -> Unit,
    onTouchBottomPaddingChange: (Float) -> Unit,
    onTouchLeftOffsetChange: (Float) -> Unit,
    onTouchRightOffsetChange: (Float) -> Unit,
    onTouchLayoutReset: () -> Unit,
    onButtonTone: () -> Unit,
    highlightDone: Boolean = false,
    onClose: () -> Unit,
) {
    val doneFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var statusBarOptionsOpen by remember { mutableStateOf(false) }
    var keyboardFocused by remember { mutableStateOf(false) }
    var exitFocused by remember { mutableStateOf(false) }
    var doneFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = statusBarOptionsOpen) {
        statusBarOptionsOpen = false
    }
    LaunchedEffect(statusBarOptionsOpen) {
        if (!statusBarOptionsOpen) {
            delay(120)
            runCatching { doneFocusRequester.requestFocus() }
        }
    }
    Surface(
        modifier = Modifier
            .padding(14.dp)
            .fillMaxWidth(0.94f)
            .fillMaxHeight(0.72f)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                NativeStreamInputRouter.setStreamPanelTouchPassthroughBounds(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt(),
                )
            },
        shape = RoundedCornerShape(18.dp),
        color = Panel.copy(alpha = 0.93f),
        contentColor = TextPrimary,
        tonalElevation = 6.dp,
    ) {
        LazyColumn(
            modifier = Modifier.onPreviewKeyEvent { handleVerticalDpadFocusMove(it, focusManager) },
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (statusBarOptionsOpen) {
                item {
                    StatusBarSettingsPage(
                        settings = settings,
                        statsVisible = statsVisible,
                        onStatsToggle = onStatsToggle,
                        onStatsStyleCycle = onStatsStyleCycle,
                        onStatsPositionCycle = onStatsPositionCycle,
                        onStatsMetricsChange = onStatsMetricsChange,
                        onButtonTone = onButtonTone,
                        onBack = { statusBarOptionsOpen = false },
                    )
                }
            } else {
                item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Stream Controls", fontWeight = FontWeight.Bold)
                        Text(gameTitle, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (status != null) {
                        Text(status, color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            onButtonTone()
                            onKeyboardOpen()
                        },
                        modifier = Modifier
                            .onFocusChanged { keyboardFocused = it.isFocused }
                            .border(
                                width = 1.dp,
                                color = if (keyboardFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = ButtonDefaults.outlinedShape
                            ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_keyboard),
                            contentDescription = "Open keyboard sender",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onButtonTone()
                            onExit()
                        },
                        modifier = Modifier
                            .onFocusChanged { exitFocused = it.isFocused }
                            .border(
                                width = 1.dp,
                                color = if (exitFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = ButtonDefaults.outlinedShape
                            ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("Exit")
                    }
                    val doneAction = {
                        onButtonTone()
                        onClose()
                    }
                    val doneModifier = Modifier
                        .focusRequester(doneFocusRequester)
                        .onFocusChanged { doneFocused = it.isFocused }
                        .border(
                            width = 1.dp,
                            color = if (doneFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = ButtonDefaults.outlinedShape
                        )
                    if (highlightDone) {
                        Button(
                            onClick = doneAction,
                            modifier = doneModifier,
                            border = BorderStroke(2.dp, TextPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Done")
                        }
                    } else {
                        OutlinedButton(
                            onClick = doneAction,
                            modifier = doneModifier,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
            if (showSessionTimer) {
                item {
                    StreamSessionTimerMenuRow(
                        limit = sessionTimerLimit,
                        startedAtMs = sessionStartedAtMs,
                        nowMs = sessionNowMs,
                    )
                }
            }
            item {
                StreamPanelSection("Display") {
                    StreamControlSwitch("Audio", if (audioMuted) "Muted" else "On", !audioMuted) {
                        onButtonTone()
                        onAudioToggle()
                    }
                    StreamControlNavigation(
                        "Status bar",
                        if (!statsVisible) "Off" else "${settings.streamStatsStyle.label} · ${settings.streamStatsMetrics.enabledCount()} items",
                    ) {
                        onButtonTone()
                        statusBarOptionsOpen = true
                    }
                    StreamControlSwitch("Stream sharpening", if (settings.stream.streamSharpeningEnabled) "On" else "Off", settings.stream.streamSharpeningEnabled) {
                        onButtonTone()
                        onSharpeningToggle()
                    }
                    if (settings.stream.streamSharpeningEnabled) {
                        CompactSlider("Sharpness amount", settings.stream.streamSharpeningAmount, 0f, 1f, onSharpeningAmountChange)
                    }
                    StreamControlSwitch("Stretch to fill", if (settings.stretchStreamToZoom) "On" else "Off", settings.stretchStreamToZoom) {
                        onButtonTone()
                        onStretchToZoomToggle()
                    }
                    StreamControlSwitch("Stretch to zoom", if (settings.stretchStreamToFill) "On" else "Off", settings.stretchStreamToFill) {
                        onButtonTone()
                        onStretchToFillToggle()
                    }
                }
            }
            item {
                StreamPanelSection("Input") {
                    StreamControlAction(
                        label = "Steam Menu",
                        value = "Send Home to the streamed PC",
                        action = "Open",
                    ) {
                        onButtonTone()
                        onSteamMenuOpen()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                onButtonTone()
                                onEsc()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Esc") }
                        OutlinedButton(
                            onClick = {
                                onButtonTone()
                                onEnter()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Enter") }
                        OutlinedButton(
                            onClick = {
                                onButtonTone()
                                onBackspace()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("⌫") }
                    }
                    if (tvProfile) {
                        StreamControlSwitch(
                            "Controller mouse",
                            if (controllerMouseAssistEnabled) "Right stick · A click · B right-click" else "Off",
                            controllerMouseAssistEnabled,
                        ) {
                            onButtonTone()
                            onControllerMouseAssistToggle()
                        }
                    } else {
                        StreamControlSwitch("Finger mouse", if (settings.androidTouch.mousePad) "On" else "Off", settings.androidTouch.mousePad) {
                            onButtonTone()
                            onMousePadToggle()
                        }
                        if (settings.androidTouch.mousePad) {
                            Box(Modifier.padding(start = 24.dp)) {
                                StreamControlSwitch("Direct click", if (settings.androidTouch.mouseDirectClick) "On" else "Off", settings.androidTouch.mouseDirectClick) {
                                    onButtonTone()
                                    onMouseDirectClickToggle()
                                }
                            }
                        }
                        StreamControlSwitch("Touch controller", if (touchControlsVisible) "Visible" else "Hidden", touchControlsVisible) {
                            onButtonTone()
                            onTouchControlsToggle()
                        }
                        if (touchControlsVisible) {
                            StreamControlSwitch("Clean style", if (settings.androidTouch.touchControllerStyle == TouchControllerStyle.V2) "On" else "Off", settings.androidTouch.touchControllerStyle == TouchControllerStyle.V2) {
                                onButtonTone()
                                onToggleTouchControllerStyle()
                            }
                        }
                        StreamControlSwitch("Phone rumble fallback", if (settings.phoneRumbleFallback) "On" else "Off", settings.phoneRumbleFallback) {
                            onButtonTone()
                            onPhoneRumbleFallbackToggle()
                        }
                    }
                    // Mouse mode (Left stick): shown for all profiles — works with both physical
                    // gamepad and touch controller.
                    StreamControlSwitch(
                        "Mouse mode (Left stick)",
                        if (controllerMouseEmulationEnabled) "L stick moves · A clicks · B right-clicks" else "Off",
                        controllerMouseEmulationEnabled,
                    ) {
                        onButtonTone()
                        onControllerMouseEmulationToggle()
                    }
                }
            }
            if (!tvProfile) item {
                StreamPanelSection("Touch Layout") {
                    StreamControlSwitch("Drag edit mode", if (touchLayoutEditing) "On" else "Off", touchLayoutEditing) {
                        onButtonTone()
                        onTouchLayoutEditingToggle()
                    }
                    StreamControlAction("Reset touch layout", "Reset positions to default", "Reset") {
                        onButtonTone()
                        onTouchLayoutReset()
                    }
                    CompactSlider("Layout scale", settings.androidTouch.scale, 0.6f, 1.4f, onTouchScaleChange)
                    CompactSlider("Button size", settings.androidTouch.buttonScale, 0.65f, 1.5f, onButtonScaleChange)
                    CompactSlider("Stick size", settings.androidTouch.stickScale, 0.65f, 1.5f, onStickScaleChange)
                    CompactSlider("Opacity", settings.androidTouch.opacity, 0.15f, 1f, onOpacityChange)
                    CompactDpSlider("Edge padding", settings.androidTouch.edgePaddingDp, 0f, 72f, onTouchEdgePaddingChange)
                    CompactDpSlider("Bottom padding", settings.androidTouch.bottomPaddingDp, 0f, 120f, onTouchBottomPaddingChange)
                    CompactDpSlider("Left position", settings.androidTouch.leftOffsetYDp, -160f, 160f, onTouchLeftOffsetChange)
                    CompactDpSlider("Right position", settings.androidTouch.rightOffsetYDp, -160f, 160f, onTouchRightOffsetChange)
                }
            }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            NativeStreamInputRouter.clearStreamPanelTouchPassthroughBounds()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusBarSettingsPage(
    settings: AppSettings,
    statsVisible: Boolean,
    onStatsToggle: () -> Unit,
    onStatsStyleCycle: () -> Unit,
    onStatsPositionCycle: () -> Unit,
    onStatsMetricsChange: (StreamStatsMetrics) -> Unit,
    onButtonTone: () -> Unit,
    onBack: () -> Unit,
) {
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { backFocusRequester.requestFocus() }
    }
    val metrics = settings.streamStatsMetrics
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    onButtonTone()
                    onBack()
                },
                modifier = Modifier.focusRequester(backFocusRequester),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Status bar", fontWeight = FontWeight.Bold)
                Text("Choose its layout and information", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
        StreamControlSwitch("Visible", if (statsVisible) "On" else "Off", statsVisible) {
            onButtonTone()
            onStatsToggle()
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBarOptionAction("Appearance", settings.streamStatsStyle.label, Modifier.weight(1f)) {
                onButtonTone()
                onStatsStyleCycle()
            }
            StatusBarOptionAction("Position", settings.streamStatsPosition.label, Modifier.weight(1f)) {
                onButtonTone()
                onStatsPositionCycle()
            }
        }
        Text("Items", color = TextMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 800.dp -> 5
                maxWidth >= 620.dp -> 4
                maxWidth >= 460.dp -> 3
                else -> 2
            }
            val gap = 8.dp
            val itemWidth = (maxWidth - gap * (columns - 1)) / columns.toFloat()
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                StatusBarMetricSwitch("FPS", metrics.fps, Modifier.width(itemWidth)) {
                    onButtonTone()
                    onStatsMetricsChange(metrics.copy(fps = !metrics.fps))
                }
                StatusBarMetricSwitch("Ping", metrics.ping, Modifier.width(itemWidth)) {
                    onButtonTone()
                    onStatsMetricsChange(metrics.copy(ping = !metrics.ping))
                }
                StatusBarMetricSwitch("Bitrate", metrics.bitrate, Modifier.width(itemWidth)) {
                    onButtonTone()
                    onStatsMetricsChange(metrics.copy(bitrate = !metrics.bitrate))
                }
                StatusBarMetricSwitch("Battery", metrics.battery, Modifier.width(itemWidth)) {
                    onButtonTone()
                    onStatsMetricsChange(metrics.copy(battery = !metrics.battery))
                }
                StatusBarMetricSwitch("Connection", metrics.connection, Modifier.width(itemWidth)) {
                    onButtonTone()
                    onStatsMetricsChange(metrics.copy(connection = !metrics.connection))
                }
                StatusBarMetricSwitch("Resolution", metrics.resolution, Modifier.width(itemWidth)) {
                    onButtonTone()
                    onStatsMetricsChange(metrics.copy(resolution = !metrics.resolution))
                }
                StatusBarMetricSwitch("Codec", metrics.codec, Modifier.width(itemWidth)) {
                    onButtonTone()
                    onStatsMetricsChange(metrics.copy(codec = !metrics.codec))
                }
                StatusBarMetricSwitch("Server", metrics.location, Modifier.width(itemWidth)) {
                    onButtonTone()
                    onStatsMetricsChange(metrics.copy(location = !metrics.location))
                }
                StatusBarMetricSwitch("Dec / Enc", metrics.latency, Modifier.width(itemWidth)) {
                    onButtonTone()
                    onStatsMetricsChange(metrics.copy(latency = !metrics.latency))
                }
                StatusBarMetricSwitch("Loss", metrics.packetLoss, Modifier.width(itemWidth)) {
                    onButtonTone()
                    onStatsMetricsChange(metrics.copy(packetLoss = !metrics.packetLoss))
                }
            }
        }
    }
}

@Composable
private fun StatusBarOptionAction(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(value, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun StatusBarMetricSwitch(label: String, checked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = { onClick() })
    }
}

@Composable
private fun StreamPanelSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun StreamControlSwitch(label: String, value: String, checked: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(value, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = { onClick() })
    }
}

@Composable
private fun StreamControlNavigation(label: String, value: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(value, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = "Open $label options",
            tint = TextPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun StreamControlAction(label: String, value: String, action: String = "Change", onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(value, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CompactSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text("${(local * 100).roundToInt()}%", color = TextMuted)
        }
        Slider(
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent {
                    handleSliderDpadInput(it, local, min, max, 0.05f, focusManager) { newVal ->
                        local = newVal
                        onChange(newVal)
                    }
                },
            value = local,
            onValueChange = {
                local = it.coerceIn(min, max)
                onChange(local)
            },
            valueRange = min..max,
        )
    }
}

@Composable
private fun CompactDpSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text("${local.roundToInt()} dp", color = TextMuted)
        }
        Slider(
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent {
                    handleSliderDpadInput(it, local, min, max, 2f, focusManager) { newVal ->
                        local = newVal
                        onChange(newVal)
                    }
                },
            value = local,
            onValueChange = {
                local = it.coerceIn(min, max)
                onChange(local)
            },
            valueRange = min..max,
        )
    }
}

@Composable
private fun StreamKeyboardBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onEsc: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val sendIfReady = {
        if (text.isNotBlank()) {
            onSend()
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { inputFocusRequester.requestFocus() }
        keyboardController?.show()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Panel.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocusRequester),
                singleLine = true,
                placeholder = { Text("Type into stream", color = TextMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendIfReady() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = sendIfReady, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Send") }
                OutlinedButton(onClick = onBackspace, modifier = Modifier.weight(1f)) { Text("⌫") }
                OutlinedButton(onClick = onEnter, modifier = Modifier.weight(1f)) { Text("Enter") }
                OutlinedButton(onClick = onEsc, modifier = Modifier.weight(1f)) { Text("Esc") }
                TextButton(
                    onClick = {
                        keyboardController?.hide()
                        onDone()
                    },
                ) { Text("Done") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamStatsPill(
    streamStats: StreamRuntimeStats,
    streamSettings: StreamSettings,
    style: StreamStatsStyle,
    metrics: StreamStatsMetrics,
    serverLocation: String?,
    modifier: Modifier = Modifier,
) {
    if (metrics.enabledCount() == 0) return
    val deviceStatus = rememberCompactStreamDeviceStatus()
    Surface(
        modifier = modifier.padding(8.dp).widthIn(max = if (style == StreamStatsStyle.Compact) 720.dp else 300.dp),
        shape = RoundedCornerShape(if (style == StreamStatsStyle.Compact) 999.dp else 16.dp),
        color = Panel.copy(alpha = 0.52f),
        tonalElevation = 0.dp,
    ) {
        if (style == StreamStatsStyle.Compact) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StreamStatsMetricItems(streamStats, streamSettings, metrics, deviceStatus, serverLocation)
            }
        } else {
            FlowRow(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StreamStatsMetricItems(streamStats, streamSettings, metrics, deviceStatus, serverLocation)
            }
        }
    }
}

@Composable
private fun StreamStatsMetricItems(
    streamStats: StreamRuntimeStats,
    streamSettings: StreamSettings,
    metrics: StreamStatsMetrics,
    deviceStatus: CompactStreamDeviceStatus,
    serverLocation: String?,
) {
    if (metrics.fps) {
        StreamStatsText("FPS ${streamStats.fps?.toString() ?: streamSettings.fps}")
    }
    if (metrics.ping) {
        val ping = streamStats.pingMs
        val color = when {
            ping == null -> TextPrimary
            ping >= 100 -> Color(0xffff4f4f) // Bright red
            ping >= 50 -> Color(0xffffa500) // Orange
            else -> TextPrimary
        }
        StreamStatsText("Ping ${ping?.let { "${it}ms" } ?: "--"}", color = color)
    }
    if (metrics.latency) {
        streamStats.decodeMs?.let {
            StreamStatsText("Dec %.1fms".format(java.util.Locale.US, it))
        }
        streamStats.encodeMs?.let {
            StreamStatsText("Enc %.1fms".format(java.util.Locale.US, it))
        }
    }
    if (metrics.packetLoss) {
        streamStats.packetLossPct?.let { loss ->
            val color = if (loss > 1.0) Color(0xffff4f4f) else TextPrimary
            StreamStatsText("Loss %.1f%%".format(java.util.Locale.US, loss), color = color)
        }
    }
    if (metrics.bitrate) {
        StreamStatsText(formatRuntimeBitrate(streamStats.bitrateKbps))
    }
    if (metrics.battery) {
        StreamBatteryIndicator(deviceStatus)
    }
    if (metrics.connection) {
        StreamNetworkIndicator(deviceStatus)
    }
    if (metrics.resolution) {
        StreamStatsText(
            streamStats.resolution?.let(::formatRuntimeResolution)
                ?: formatRuntimeResolution(normalizeStreamResolutionForAspect(streamSettings.resolution, streamSettings.aspectRatio)),
        )
    }
    if (metrics.codec) {
        StreamStatsText(streamStats.codec?.takeIf { it.isNotBlank() } ?: streamSettings.codec.name)
    }
    if (metrics.location && !serverLocation.isNullOrBlank()) {
        val displayName = serverLocation.removePrefix("NPA-").removePrefix("NP-").uppercase()
        StreamStatsText(displayName)
    }
}

@Composable
private fun StreamStatsText(value: String, color: Color = TextPrimary) {
    Text(
        value,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

private data class CompactStreamDeviceStatus(
    val batteryPercent: Int? = null,
    val batteryCharging: Boolean = false,
    val networkKind: AndroidNetworkKind = AndroidNetworkKind.Unknown,
    val networkBars: Int? = null,
    val cellularGeneration: String? = null,
)

@Composable
private fun rememberCompactStreamDeviceStatus(): CompactStreamDeviceStatus {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    var status by remember(appContext) { mutableStateOf(readCompactStreamDeviceStatus(appContext)) }
    LaunchedEffect(appContext) {
        while (true) {
            status = readCompactStreamDeviceStatus(appContext)
            delay(COMPACT_STREAM_DEVICE_STATUS_REFRESH_MS)
        }
    }
    return status
}

private fun readCompactStreamDeviceStatus(context: Context): CompactStreamDeviceStatus {
    val diagnostics = AndroidRuntimeDiagnostics.snapshot(context)
    return CompactStreamDeviceStatus(
        batteryPercent = diagnostics.batteryPercent,
        batteryCharging = diagnostics.batteryCharging,
        networkKind = diagnostics.networkKind,
        networkBars = diagnostics.networkSignalBars,
        cellularGeneration = diagnostics.cellularGeneration,
    )
}

@Composable
private fun StreamBatteryIndicator(status: CompactStreamDeviceStatus) {
    val description = status.batteryPercent?.let { percent ->
        "Battery $percent percent${if (status.batteryCharging) ", charging" else ""}"
    } ?: "Battery unknown"
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.BatteryFull,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 90f },
        )
        Text(
            status.batteryPercent?.let { "$it%" } ?: "--%",
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun StreamNetworkIndicator(status: CompactStreamDeviceStatus) {
    val bars = status.networkBars?.coerceIn(0, 4)
    val label = when (status.networkKind) {
        AndroidNetworkKind.Cellular -> status.cellularGeneration ?: status.networkKind.label
        AndroidNetworkKind.Ethernet,
        AndroidNetworkKind.Other,
        AndroidNetworkKind.None,
        AndroidNetworkKind.Unknown,
        -> status.networkKind.label
        AndroidNetworkKind.Wifi -> null
    }
    val description = "${label ?: status.networkKind.label} signal ${bars?.toString() ?: "unknown"} bars"
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(
                label,
                color = TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        if (status.networkKind == AndroidNetworkKind.Wifi) {
            Icon(
                imageVector = when (bars) {
                    4 -> Icons.Rounded.Wifi
                    3 -> Icons.Rounded.Wifi
                    2 -> Icons.Rounded.Wifi2Bar
                    1 -> Icons.Rounded.Wifi1Bar
                    0 -> Icons.Rounded.SignalWifi0Bar
                    else -> Icons.Rounded.WifiOff
                },
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        } else if (status.networkKind == AndroidNetworkKind.Cellular || status.networkKind == AndroidNetworkKind.Other || status.networkKind == AndroidNetworkKind.Unknown) {
            Icon(
                imageVector = when (bars) {
                    4 -> Icons.Rounded.SignalCellular4Bar
                    3 -> Icons.Rounded.SignalCellularAlt
                    2 -> Icons.Rounded.SignalCellularAlt2Bar
                    1 -> Icons.Rounded.SignalCellularAlt1Bar
                    else -> Icons.Rounded.SignalCellular0Bar
                },
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatRuntimeResolution(resolution: String): String {
    val parts = resolution.lowercase(Locale.US).split("x", limit = 2)
    return if (parts.size == 2 && parts.all { it.trim().isNotBlank() }) {
        "${parts[0].trim()}x${parts[1].trim()}"
    } else {
        resolution
    }
}

private fun formatRuntimeBitrate(bitrateKbps: Int?): String {
    val kbps = bitrateKbps ?: return "--"
    return if (kbps >= 1000) {
        "${(kbps / 1000.0).let { kotlin.math.round(it * 10.0) / 10.0 }} Mbps"
    } else {
        "$kbps Kbps"
    }
}

private fun shouldHideStreamStatusText(status: String): Boolean =
    status.trim().replace('_', ' ').let {
        it.equals("Streaming", ignoreCase = true) ||
            it.equals("ICE CONNECTED", ignoreCase = true) ||
            it.equals("ICE COMPLETED", ignoreCase = true)
    }

internal data class InitialStreamConnectionStatus(
    val phase: String,
    val title: String,
    val detail: String,
)

internal fun initialStreamConnectionStatus(nativeState: String): InitialStreamConnectionStatus {
    val normalized = nativeState.trim().replace('_', ' ')
    return when {
        normalized.equals("Preparing", ignoreCase = true) -> InitialStreamConnectionStatus(
            phase = "Preparing",
            title = "Preparing your stream",
            detail = "Getting the secure video connection ready.",
        )
        normalized.startsWith("Connecting signaling", ignoreCase = true) -> InitialStreamConnectionStatus(
            phase = "Connecting",
            title = "Connecting to your game",
            detail = "Opening a secure connection to the streaming server.",
        )
        normalized.startsWith("Waiting for offer", ignoreCase = true) -> InitialStreamConnectionStatus(
            phase = "Waiting for video",
            title = "Starting the video stream",
            detail = "The server is preparing the first video frame.",
        )
        normalized.equals("ICE CHECKING", ignoreCase = true) ||
            normalized.equals("ICE NEW", ignoreCase = true) -> InitialStreamConnectionStatus(
            phase = "Securing connection",
            title = "Almost ready",
            detail = "Checking the best route for the live video stream.",
        )
        normalized.equals("ICE DISCONNECTED", ignoreCase = true) ||
            normalized.equals("ICE FAILED", ignoreCase = true) -> InitialStreamConnectionStatus(
            phase = "Retrying",
            title = "Connection interrupted",
            detail = "OpenNOW is retrying the initial stream connection.",
        )
        normalized.startsWith("Recovering video", ignoreCase = true) -> InitialStreamConnectionStatus(
            phase = "Recovering video",
            title = "Waiting for a clear frame",
            detail = "Requesting a fresh video frame before showing the stream.",
        )
        normalized.contains("safe H264 profile", ignoreCase = true) -> InitialStreamConnectionStatus(
            phase = "Optimizing video",
            title = "Trying a compatible video mode",
            detail = "Restarting the initial video connection with safer settings.",
        )
        normalized.startsWith("Reconnecting", ignoreCase = true) -> InitialStreamConnectionStatus(
            phase = "Retrying connection",
            title = "Connecting again",
            detail = "The initial connection did not finish, so OpenNOW is retrying it.",
        )
        normalized.startsWith("Recovering cloud session", ignoreCase = true) -> InitialStreamConnectionStatus(
            phase = "Checking session",
            title = "Restoring your game session",
            detail = "Checking the existing cloud session before continuing.",
        )
        normalized.equals("Streaming", ignoreCase = true) -> InitialStreamConnectionStatus(
            phase = "Starting video",
            title = "Connection established",
            detail = "Waiting for the first video frame to appear.",
        )
        else -> InitialStreamConnectionStatus(
            phase = "Starting stream",
            title = "Preparing your game",
            detail = "OpenNOW is waiting for the live video to begin.",
        )
    }
}

@Composable
private fun InitialStreamConnectionOverlay(
    gameTitle: String?,
    status: InitialStreamConnectionStatus,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        val cardWidthFraction = if (maxWidth > maxHeight) 0.54f else 0.9f
        Surface(
            modifier = Modifier
                .fillMaxWidth(cardWidthFraction)
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(22.dp),
            color = Panel.copy(alpha = 0.96f),
            contentColor = TextPrimary,
            tonalElevation = 10.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(38.dp)
                        .semantics { contentDescription = status.phase },
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        gameTitle?.takeIf { it.isNotBlank() } ?: "OpenNOW stream",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        status.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        status.detail,
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        status.phase,
                        color = TextMuted.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamExitConfirmation(
    gameTitle: String,
    onKeepPlaying: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keepPlayingFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { keepPlayingFocusRequester.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onKeepPlaying),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Panel.copy(alpha = 0.95f),
            contentColor = TextPrimary,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Session Control", color = TextMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Exit Stream?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Do you really want to exit $gameTitle?", color = TextMuted)
                Text("Your current cloud gaming session will be closed.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onKeepPlaying,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(keepPlayingFocusRequester),
                    ) { Text("Keep Playing") }
                    Button(onClick = onExit, modifier = Modifier.weight(1f)) { Text("Exit Stream") }
                }
            }
        }
    }
}

@Composable
private fun QueueLoadingScreen(state: OpenNowUiState, viewModel: OpenNowViewModel) {
    val session = state.streamSession
    val game = state.streamGame
    val ads = sessionAdItems(session?.adState)
    val ad = ads.firstOrNull { it.adId == state.queueAdActiveId } ?: ads.firstOrNull()
    val mediaUrl = ad?.adMediaFiles?.firstOrNull { !it.mediaFileUrl.isNullOrBlank() }?.mediaFileUrl
        ?: ad?.adUrl
        ?: ad?.mediaUrl
    val queuePosition = activeQueuePosition(state)
    val visibleQueuePosition = rememberStableQueuePosition(queuePosition)
    val queueCopy = queueLaunchStatusText(state, visibleQueuePosition)
    val hasPlayableAd = ad != null && mediaUrl != null

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        QueueAmbientBackdrop(
            accent = state.settings.uiAccent.color,
            queuePosition = visibleQueuePosition,
        )
        val useLandscapeAdLayout = hasPlayableAd && maxWidth > maxHeight

        Box(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (ad != null && mediaUrl != null) {
                QueueAdPanel(
                    ad = ad,
                    mediaUrl = mediaUrl,
                    viewModel = viewModel,
                    game = game,
                    queueCopy = queueCopy,
                    queuePosition = visibleQueuePosition,
                    error = state.error,
                    playbackKey = session?.sessionId.orEmpty(),
                    compact = useLandscapeAdLayout,
                    onMinimize = viewModel::minimizeStreamLaunch,
                    onCancel = viewModel::stopStream,
                    modifier = Modifier
                        .fillMaxWidth(if (useLandscapeAdLayout) 0.72f else 1f)
                        .widthIn(max = if (useLandscapeAdLayout) 900.dp else 620.dp),
                )
            } else {
                QueueStatusPanel(
                    game = game,
                    queueCopy = queueCopy,
                    queuePosition = visibleQueuePosition,
                    error = state.error,
                    compact = false,
                    onMinimize = viewModel::minimizeStreamLaunch,
                    onCancel = viewModel::stopStream,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun QueueAmbientBackdrop(
    accent: Color,
    queuePosition: Int?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "queue-ambient")
    val driftA by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "queue-ambient-drift-a",
    )
    val driftB by transition.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "queue-ambient-drift-b",
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
        ),
        label = "queue-ambient-phase",
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
        ),
        label = "queue-ambient-shimmer",
    )
    val orbADim by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "queue-ambient-orb-a-dim",
    )
    val orbBDim by transition.animateFloat(
        initialValue = 0.26f,
        targetValue = 0.56f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "queue-ambient-orb-b-dim",
    )

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xff010203),
                        Color(0xff05080a),
                        Color(0xff020304),
                    ),
                ),
            ),
    ) {
        val baseSize = minOf(maxWidth, maxHeight)
        QueueAmbientOrb(
            color = accent,
            size = baseSize * 0.92f,
            alpha = orbADim,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = maxWidth * (-0.22f + 0.10f * driftA),
                    y = maxHeight * (0.02f + 0.08f * driftB),
                ),
        )
        QueueAmbientOrb(
            color = Color(0xff2bdcff),
            size = baseSize * 0.7f,
            alpha = orbBDim,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(
                    x = maxWidth * (0.15f + 0.08f * driftB),
                    y = maxHeight * (0.10f + 0.07f * driftA),
                ),
        )
        QueueSignalField(
            accent = accent,
            queuePosition = queuePosition,
            phase = phase,
            shimmer = shimmer,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.34f)),
        )
    }
}

@Composable
private fun QueueAmbientOrb(
    color: Color,
    size: Dp,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .blur(64.dp)
            .graphicsLayer(alpha = alpha.coerceIn(0f, 1f))
            .background(
                Brush.radialGradient(
                    listOf(
                        color.copy(alpha = 0.58f),
                        color.copy(alpha = 0.16f),
                        Color.Transparent,
                    ),
                ),
                CircleShape,
            ),
    )
}

@Composable
private fun QueueSignalField(
    accent: Color,
    queuePosition: Int?,
    phase: Float,
    shimmer: Float,
    modifier: Modifier = Modifier,
) {
    val heat = queueUrgency(queuePosition)
    Canvas(modifier) {
        val lineCount = 9
        val spacing = size.height / lineCount
        val offset = shimmer * spacing
        for (index in -1..lineCount) {
            val y = index * spacing + offset
            drawLine(
                color = accent.copy(alpha = 0.035f + heat * 0.035f),
                start = Offset(-size.width * 0.12f, y),
                end = Offset(size.width * 1.08f, y - size.height * 0.10f),
                strokeWidth = 1.dp.toPx(),
            )
        }
        repeat(12) { index ->
            val lane = index + 1
            val x = ((lane * 0.173f + phase * (0.08f + lane * 0.006f)) % 1f) * size.width
            val y = ((lane * 0.291f + shimmer * (0.12f + lane * 0.004f)) % 1f) * size.height
            drawCircle(
                color = accent.copy(alpha = 0.05f + heat * 0.04f),
                radius = (1.5f + (index % 4)) * density,
                center = Offset(x, y),
            )
        }
    }
}

@Composable
private fun AnimatedQueueStatusText(
    queueCopy: String,
    queuePosition: Int?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (queuePosition == null) {
        Text(
            queueCopy,
            modifier = modifier,
            color = queueIdleStatusColor(queueCopy),
            style = (if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium)
                .copy(fontWeight = FontWeight.Normal),
            textAlign = TextAlign.Center,
        )
        return
    }

    var previousQueuePosition by remember { mutableStateOf<Int?>(null) }
    val numberProgress = remember { Animatable(1f) }
    var numberTrigger by remember { mutableStateOf(0) }
    var numberFrom by remember { mutableStateOf(queuePosition.toString()) }
    var numberTo by remember { mutableStateOf(queuePosition.toString()) }
    val heat = queueUrgency(queuePosition)
    val hotQueue = queuePosition < 10
    val transition = rememberInfiniteTransition(label = "queue-status-glow")
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (hotQueue) 520 else 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "queue-status-glow-alpha",
    )
    val moleculePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (190 - heat * 95).roundToInt().coerceIn(92, 190),
                easing = LinearEasing,
            ),
        ),
        label = "queue-status-molecule-phase",
    )
    val statusColor by animateColorAsState(
        targetValue = queueUrgencyColor(queuePosition),
        animationSpec = tween(durationMillis = 240),
        label = "queue-status-color",
    )

    LaunchedEffect(queuePosition) {
        val current = queuePosition
        val previous = previousQueuePosition
        if (previous != null && current < previous) {
            numberFrom = previous.toString()
            numberTo = current.toString()
            numberTrigger += 1
        } else {
            numberFrom = current.toString()
            numberTo = current.toString()
        }
        previousQueuePosition = current
    }

    LaunchedEffect(numberTrigger) {
        if (numberTrigger == 0) return@LaunchedEffect
        numberProgress.snapTo(0f)
        numberProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = if (hotQueue) 320 else 420),
        )
    }

    val moleculeCagePx = with(LocalDensity.current) {
        (if (hotQueue) (0.45f + heat * 1.45f).dp else 0.dp).toPx()
    }
    val shakeX = if (hotQueue) {
        (sin(moleculePhase * 31.415928f) * 0.64f + sin(moleculePhase * 106.81416f) * 0.36f) * moleculeCagePx
    } else {
        0f
    }
    val shakeY = if (hotQueue) {
        (sin(moleculePhase * 43.982296f) * 0.55f + sin(moleculePhase * 81.68141f) * 0.45f) * moleculeCagePx * 0.55f
    } else {
        0f
    }
    val parts = queueStatusParts(queueCopy, queuePosition)
    val textStyle = (if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium)
        .copy(fontWeight = FontWeight.Normal)
    val numberPhase = numberProgress.value
    val numberAnimating = numberPhase < 1f
    val numberTravelPx = with(LocalDensity.current) { (if (compact) 18.dp else 22.dp).toPx() }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            parts.prefix,
            color = TextMuted,
            style = textStyle,
            textAlign = TextAlign.Center,
        )
        AnimatedQueueNumber(
            currentNumber = parts.number,
            previousNumber = numberFrom,
            targetNumber = numberTo,
            animating = numberAnimating,
            phase = numberPhase,
            travelPx = numberTravelPx,
            color = statusColor,
            style = textStyle.copy(
                shadow = Shadow(
                    color = statusColor.copy(alpha = heat * (0.38f + 0.42f * glow)),
                    offset = Offset(0f, 0f),
                    blurRadius = 18f + heat * 18f,
                ),
            ),
            shakeX = shakeX,
            shakeY = shakeY,
        )
        Text(
            parts.suffix,
            color = TextMuted,
            style = textStyle,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AnimatedQueueNumber(
    currentNumber: String,
    previousNumber: String,
    targetNumber: String,
    animating: Boolean,
    phase: Float,
    travelPx: Float,
    color: Color,
    style: TextStyle,
    shakeX: Float,
    shakeY: Float,
) {
    val fromNumber = if (animating) previousNumber else currentNumber
    val toNumber = if (animating) targetNumber else currentNumber
    val slotCount = toNumber.length

    Row(
        modifier = Modifier
            .clipToBounds()
            .graphicsLayer(
                translationX = shakeX,
                translationY = shakeY,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(slotCount) { slotIndex ->
            val fromDigit = fromNumber.rightAlignedCharAt(slotIndex, slotCount)
            val toDigit = toNumber.rightAlignedCharAt(slotIndex, slotCount)
            QueueNumberDigitSlot(
                fromDigit = fromDigit,
                toDigit = toDigit,
                digitChanged = animating && fromDigit != toDigit,
                phase = phase,
                travelPx = travelPx,
                color = color,
                style = style,
            )
        }
    }
}

@Composable
private fun QueueNumberDigitSlot(
    fromDigit: Char?,
    toDigit: Char?,
    digitChanged: Boolean,
    phase: Float,
    travelPx: Float,
    color: Color,
    style: TextStyle,
) {
    val from = fromDigit?.toString().orEmpty()
    val to = toDigit?.toString().orEmpty()
    Box(
        modifier = Modifier.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        if (from.isNotEmpty()) {
            Text(
                from,
                modifier = Modifier.graphicsLayer(alpha = 0f),
                color = color,
                style = style,
                textAlign = TextAlign.Center,
            )
        }
        if (to.isNotEmpty() && to != from) {
            Text(
                to,
                modifier = Modifier.graphicsLayer(alpha = 0f),
                color = color,
                style = style,
                textAlign = TextAlign.Center,
            )
        }
        if (digitChanged) {
            if (from.isNotEmpty()) {
                Text(
                    from,
                    modifier = Modifier.graphicsLayer(
                        translationY = -travelPx * phase,
                        scaleX = 1f - phase * 0.03f,
                        scaleY = 1f - phase * 0.03f,
                        alpha = 1f - phase,
                    ),
                    color = color,
                    style = style,
                    textAlign = TextAlign.Center,
                )
            }
            if (to.isNotEmpty()) {
                Text(
                    to,
                    modifier = Modifier.graphicsLayer(
                        translationY = travelPx * (1f - phase),
                        scaleX = 0.97f + phase * 0.03f,
                        scaleY = 0.97f + phase * 0.03f,
                        alpha = phase,
                    ),
                    color = color,
                    style = style,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (to.isNotEmpty()) {
            Text(
                to,
                color = color,
                style = style,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun String.rightAlignedCharAt(slotIndex: Int, slotCount: Int): Char? =
    getOrNull(length - slotCount + slotIndex)

private data class QueueStatusParts(
    val prefix: String,
    val number: String,
    val suffix: String,
)

private fun queueStatusParts(queueCopy: String, queuePosition: Int): QueueStatusParts {
    val number = queuePosition.toString()
    val index = queueCopy.indexOf(number)
    if (index < 0) {
        return QueueStatusParts(prefix = "$queueCopy ", number = number, suffix = "")
    }
    return QueueStatusParts(
        prefix = queueCopy.substring(0, index),
        number = number,
        suffix = queueCopy.substring(index + number.length),
    )
}

private fun queueUrgency(queuePosition: Int?): Float {
    val position = queuePosition ?: return 0f
    if (position >= 10) return 0f
    return ((10 - position).toFloat() / 9f).coerceIn(0f, 1f)
}

private fun activeQueuePosition(state: OpenNowUiState): Int? =
    queueDisplayPosition(state)

@Composable
private fun rememberStableQueuePosition(queuePosition: Int?): Int? {
    var stableQueuePosition by remember { mutableStateOf(queuePosition) }
    LaunchedEffect(queuePosition) {
        if (queuePosition == stableQueuePosition) return@LaunchedEffect
        if (queuePosition == null || stableQueuePosition == null) {
            stableQueuePosition = queuePosition
            return@LaunchedEffect
        }
        delay(QUEUE_POSITION_VISUAL_SETTLE_MS)
        stableQueuePosition = queuePosition
    }
    return stableQueuePosition
}

private fun queueLaunchStatusText(state: OpenNowUiState, queuePosition: Int?): String =
    queuePosition?.let { "Queue position $it" } ?: queueLaunchStatusText(state)

private fun queueIdleStatusColor(queueCopy: String): Color =
    if (queueCopy.equals("Starting session", ignoreCase = true)) Green else TextMuted

private fun queueUrgencyColor(queuePosition: Int?): Color {
    val heat = queueUrgency(queuePosition)
    if (heat <= 0f) return TextMuted
    val green = (0.57f - 0.49f * heat).coerceIn(0.06f, 0.57f)
    val blue = (0.25f - 0.17f * heat).coerceIn(0.08f, 0.25f)
    return Color(red = 1f, green = green, blue = blue, alpha = 1f)
}

@Composable
private fun QueueStatusPanel(
    game: GameInfo?,
    queueCopy: String,
    queuePosition: Int?,
    error: String?,
    compact: Boolean,
    onMinimize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val imageWidth = if (compact) 154.dp else 220.dp
        UrlImage(
            gameTvBannerImageUrl(context, game),
            Modifier
                .width(imageWidth)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
        Text(
            game?.title ?: "Starting stream",
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        AnimatedQueueStatusText(
            queueCopy = queueCopy,
            queuePosition = queuePosition,
            compact = compact,
        )
        Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth(if (compact) 0.9f else 0.7f))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(if (compact) 0.92f else 0.7f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onMinimize, modifier = Modifier.weight(1f)) {
                Text("Minimize", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (compact && queuePosition != null) {
            Spacer(Modifier.height(14.dp))
            LandscapeQueuePositionDock(queuePosition = queuePosition)
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xffff9f9f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LandscapeQueuePositionDock(queuePosition: Int, modifier: Modifier = Modifier) {
    val accent = queueUrgencyColor(queuePosition)
    val heat = queueUrgency(queuePosition)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth(0.92f)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.18f + heat * 0.16f),
                        PanelAlt.copy(alpha = 0.94f),
                        Color.Black.copy(alpha = 0.36f),
                    ),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.32f + heat * 0.36f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Queue",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Live position",
                    color = TextMuted.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                queuePosition.toString(),
                color = accent,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    shadow = Shadow(
                        color = accent.copy(alpha = 0.24f + heat * 0.42f),
                        offset = Offset(0f, 0f),
                        blurRadius = 18f + heat * 14f,
                    ),
                ),
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun QueueAdPanel(
    ad: SessionAdInfo,
    mediaUrl: String,
    viewModel: OpenNowViewModel,
    game: GameInfo?,
    queueCopy: String,
    queuePosition: Int?,
    error: String?,
    playbackKey: String,
    compact: Boolean,
    onMinimize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Panel.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
    ) {
        if (compact) {
            Row(
                Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QueueAdPlayback(
                    ad = ad,
                    mediaUrl = mediaUrl,
                    playbackKey = playbackKey,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1.55f)
                        .aspectRatio(16f / 9f),
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QueueAdHeading(game = game, compact = true)
                    QueueStatusAndActions(
                        queueCopy = queueCopy,
                        queuePosition = queuePosition,
                        compact = true,
                        stackActions = true,
                        onMinimize = onMinimize,
                        onCancel = onCancel,
                    )
                    error?.let {
                        Text(it, color = Color(0xffff9f9f), textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QueueAdHeading(game = game, compact = false)
                QueueAdPlayback(
                    ad = ad,
                    mediaUrl = mediaUrl,
                    playbackKey = playbackKey,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
                QueueStatusAndActions(
                    queueCopy = queueCopy,
                    queuePosition = queuePosition,
                    compact = false,
                    stackActions = false,
                    onMinimize = onMinimize,
                    onCancel = onCancel,
                )
                error?.let {
                    Text(it, color = Color(0xffff9f9f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun QueueAdPlayback(
    ad: SessionAdInfo,
    mediaUrl: String,
    playbackKey: String,
    viewModel: OpenNowViewModel,
    modifier: Modifier = Modifier,
) {
    QueueAdPlayer(
        adId = ad.adId,
        url = mediaUrl,
        playbackKey = playbackKey,
        modifier = modifier,
        onStarted = { viewModel.reportQueueAd(ad.adId, "start") },
        onPaused = { viewModel.reportQueueAd(ad.adId, "pause") },
        onResumed = { viewModel.reportQueueAd(ad.adId, "resume") },
        onFinished = { watchedTimeInMs ->
            viewModel.reportQueueAd(ad.adId, "finish", watchedTimeInMs = watchedTimeInMs)
        },
        onError = { watchedTimeInMs ->
            viewModel.reportQueueAd(
                ad.adId,
                "cancel",
                watchedTimeInMs = watchedTimeInMs,
                cancelReason = "error",
                errorInfo = "Error loading url",
            )
        },
    )
}

@Composable
private fun QueueAdHeading(game: GameInfo?, compact: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Advertisement",
            color = TextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            game?.title ?: "Starting stream",
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QueueStatusAndActions(
    queueCopy: String,
    queuePosition: Int?,
    compact: Boolean,
    stackActions: Boolean,
    onMinimize: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(if (compact) 1f else 0.7f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        AnimatedQueueStatusText(
            queueCopy = queueCopy,
            queuePosition = queuePosition,
            compact = compact,
        )
        LinearProgressIndicator(Modifier.fillMaxWidth())
        if (stackActions) {
            OutlinedButton(onClick = onMinimize, modifier = Modifier.fillMaxWidth()) {
                Text("Minimize", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onMinimize, modifier = Modifier.weight(1f)) {
                    Text("Minimize", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MinimizedQueueDock(
    state: OpenNowUiState,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queuePosition = activeQueuePosition(state)
    val visibleQueuePosition = rememberStableQueuePosition(queuePosition)
    val queueCopy = queueLaunchStatusText(state, visibleQueuePosition)
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = Panel.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    state.streamGame?.title ?: "Starting stream",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MinimizedQueueStatusText(
                    queueCopy = queueCopy,
                    queuePosition = visibleQueuePosition,
                )
            }
            TextButton(onClick = onRestore) { Text("View") }
            OutlinedButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun MinimizedQueueStatusText(
    queueCopy: String,
    queuePosition: Int?,
) {
    if (queuePosition == null) {
        Text(queueCopy, color = queueIdleStatusColor(queueCopy), style = MaterialTheme.typography.bodySmall)
        return
    }
    val parts = queueStatusParts(queueCopy, queuePosition)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(parts.prefix, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        Text(
            parts.number,
            color = queueUrgencyColor(queuePosition),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(parts.suffix, color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QueueAdPlayer(
    adId: String,
    url: String,
    playbackKey: String,
    modifier: Modifier = Modifier,
    onStarted: () -> Unit,
    onPaused: () -> Unit,
    onResumed: () -> Unit,
    onFinished: (watchedTimeInMs: Long) -> Unit,
    onError: (watchedTimeInMs: Long) -> Unit,
) {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(false) }
    val player = remember(adId, url, playbackKey) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            volume = if (muted) 0f else 1f
            prepare()
            playWhenReady = true
        }
    }
    var reportedStart by remember(adId, url, playbackKey) { mutableStateOf(false) }
    var reportedFinish by remember(adId, url, playbackKey) { mutableStateOf(false) }
    var reportedPause by remember(adId, url, playbackKey) { mutableStateOf(false) }
    var playing by remember(adId, url, playbackKey) { mutableStateOf(player.playWhenReady) }
    var controlsVisible by remember(adId, url, playbackKey) { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(2400L)
            controlsVisible = false
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (!isPlaying) controlsVisible = true
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!reportedStart || reportedFinish) return
                if (playWhenReady && reportedPause) {
                    reportedPause = false
                    onResumed()
                } else if (!playWhenReady && player.playbackState != Player.STATE_ENDED && !reportedPause) {
                    reportedPause = true
                    onPaused()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.playWhenReady && !reportedStart) {
                    reportedStart = true
                    onStarted()
                }
                if (playbackState == Player.STATE_ENDED && !reportedFinish) {
                    reportedFinish = true
                    onFinished(player.currentPosition.coerceAtLeast(0L))
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (!reportedFinish) {
                    reportedFinish = true
                    onError(player.currentPosition.coerceAtLeast(0L))
                }
            }
        }
        player.addListener(listener)
        listener.onIsPlayingChanged(player.isPlaying)
        listener.onPlaybackStateChanged(player.playbackState)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { controlsVisible = true },
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize(),
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false } },
            update = { it.player = player; it.useController = false },
        )
        AnimatedVisibility(
            visible = controlsVisible || !playing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QueueAdIconButton(
                    label = if (playing) "Pause ad" else "Play ad",
                    icon = if (playing) QueueAdControlIcon.Pause else QueueAdControlIcon.Play,
                    onClick = {
                        controlsVisible = true
                        if (playing) {
                            player.pause()
                            playing = false
                        } else {
                            player.play()
                            playing = true
                        }
                    },
                )
                QueueAdIconButton(
                    label = if (muted) "Unmute ad" else "Mute ad",
                    icon = if (muted) QueueAdControlIcon.Muted else QueueAdControlIcon.Volume,
                    onClick = {
                        controlsVisible = true
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                    },
                )
            }
        }
    }
}

private enum class QueueAdControlIcon { Play, Pause, Volume, Muted }

@Composable
private fun QueueAdIconButton(label: String, icon: QueueAdControlIcon, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .semantics { contentDescription = label },
    ) {
        QueueAdControlIconView(icon = icon, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun QueueAdControlIconView(icon: QueueAdControlIcon, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (icon) {
            QueueAdControlIcon.Play -> {
                val path = Path().apply {
                    moveTo(w * 0.35f, h * 0.24f)
                    lineTo(w * 0.35f, h * 0.76f)
                    lineTo(w * 0.76f, h * 0.5f)
                    close()
                }
                drawPath(path, Color.White)
            }
            QueueAdControlIcon.Pause -> {
                drawRoundRect(Color.White, Offset(w * 0.28f, h * 0.24f), Size(w * 0.14f, h * 0.52f), CornerRadius(w * 0.04f, w * 0.04f))
                drawRoundRect(Color.White, Offset(w * 0.58f, h * 0.24f), Size(w * 0.14f, h * 0.52f), CornerRadius(w * 0.04f, w * 0.04f))
            }
            QueueAdControlIcon.Volume, QueueAdControlIcon.Muted -> {
                val body = Path().apply {
                    moveTo(w * 0.18f, h * 0.42f)
                    lineTo(w * 0.34f, h * 0.42f)
                    lineTo(w * 0.52f, h * 0.26f)
                    lineTo(w * 0.52f, h * 0.74f)
                    lineTo(w * 0.34f, h * 0.58f)
                    lineTo(w * 0.18f, h * 0.58f)
                    close()
                }
                drawPath(body, Color.White)
                if (icon == QueueAdControlIcon.Volume) {
                    drawLine(Color.White, Offset(w * 0.62f, h * 0.38f), Offset(w * 0.72f, h * 0.5f), strokeWidth = w * 0.08f)
                    drawLine(Color.White, Offset(w * 0.72f, h * 0.5f), Offset(w * 0.62f, h * 0.62f), strokeWidth = w * 0.08f)
                } else {
                    drawLine(Color.White, Offset(w * 0.64f, h * 0.36f), Offset(w * 0.84f, h * 0.64f), strokeWidth = w * 0.08f)
                    drawLine(Color.White, Offset(w * 0.84f, h * 0.36f), Offset(w * 0.64f, h * 0.64f), strokeWidth = w * 0.08f)
                }
            }
        }
    }
}

@Composable
private fun TouchOverlay(
    client: NativeStreamClient,
    touch: AndroidTouchSettings,
    onButtonTone: () -> Unit,
    layoutEditing: Boolean,
    onSaveAllOffsets: (Map<String, TouchOffset>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val opacity = touch.opacity
    val layoutScale = touch.scale
    val buttonScale = touch.buttonScale
    val stickScale = touch.stickScale

    val localOffsets = remember(touch.offsets) {
        androidx.compose.runtime.mutableStateMapOf<String, TouchOffset>().apply {
            putAll(touch.offsets)
        }
    }

    fun getLocalOffset(key: String): TouchOffset {
        val saved = localOffsets[key]
        if (saved != null) return saved
        val baseKey = key.substringBeforeLast("_")
        return when (baseKey) {
            "lt", "lb", "lstick", "dpad", "l3" -> TouchOffset(touch.leftOffsetXDp, touch.leftOffsetYDp)
            "rt", "rb", "rstick", "face", "r3" -> TouchOffset(touch.rightOffsetXDp, touch.rightOffsetYDp)
            else -> TouchOffset()
        }
    }

    val onLocalOffsetChange = { key: String, x: Float, y: Float ->
        localOffsets[key] = TouchOffset(x, y)
    }

    val currentLocalOffsets by rememberUpdatedState(localOffsets.toMap())
    val currentOnSaveAllOffsets by rememberUpdatedState(onSaveAllOffsets)
    DisposableEffect(layoutEditing) {
        onDispose {
            if (layoutEditing) {
                currentOnSaveAllOffsets(currentLocalOffsets)
            }
        }
    }

    LaunchedEffect(client, touch.enabled) {
        client.setVirtualControllerVisible(touch.enabled)
        NativeStreamInputRouter.setTouchControllerVisible(touch.enabled)
    }
    DisposableEffect(client) {
        onDispose {
            client.setVirtualControllerVisible(false)
            NativeStreamInputRouter.setTouchControllerVisible(false)
            NativeStreamInputRouter.clearTouchControllerPassthroughBounds()
        }
    }

    CompositionLocalProvider(LocalTouchControllerStyle provides touch.touchControllerStyle) {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .padding(
                    start = touch.edgePaddingDp.dp,
                    top = 10.dp,
                    end = touch.edgePaddingDp.dp,
                    bottom = touch.bottomPaddingDp.dp,
                ),
        ) {
            if (touch.enabled) {
                val landscape = maxWidth > maxHeight
                val suffix = if (landscape) "_landscape" else "_portrait"
                val getOrientationLocalOffset = { key: String -> getLocalOffset(key + suffix) }
                val onOrientationLocalOffsetChange = { key: String, x: Float, y: Float ->
                    onLocalOffsetChange(key + suffix, x, y)
                }

                if (landscape) {
                    LandscapeTouchControls(
                        client = client,
                        opacity = opacity,
                        layoutScale = layoutScale,
                        buttonScale = buttonScale,
                        stickScale = stickScale,
                        viewportHeight = maxHeight,
                        layoutEditing = layoutEditing,
                        getLocalOffset = getOrientationLocalOffset,
                        onLocalOffsetChange = onOrientationLocalOffsetChange,
                        onButtonTone = onButtonTone,
                    )
                } else {
                    PortraitTouchControls(
                        client = client,
                        opacity = opacity,
                        layoutScale = layoutScale,
                        buttonScale = buttonScale,
                        stickScale = stickScale,
                        layoutEditing = layoutEditing,
                        getLocalOffset = getOrientationLocalOffset,
                        onLocalOffsetChange = onOrientationLocalOffsetChange,
                        onButtonTone = onButtonTone,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortraitTouchControls(
    client: NativeStreamClient,
    opacity: Float,
    layoutScale: Float,
    buttonScale: Float,
    stickScale: Float,
    layoutEditing: Boolean,
    getLocalOffset: (String) -> TouchOffset,
    onLocalOffsetChange: (String, Float, Float) -> Unit,
    onButtonTone: () -> Unit,
) {
    val leftStickDiameter = 116.dp * stickScale * layoutScale
    val rightStickDiameter = 104.dp * stickScale * layoutScale
    val buttonSize48 = 48.dp * buttonScale * layoutScale
    val buttonSize44 = 44.dp * buttonScale * layoutScale
    val faceWidth = buttonSize48 * 2.44f

    Box(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        val scale = buttonScale * layoutScale
        val triggerWidth = 64.dp * scale
        val bumperHeight = 32.dp * scale

        TouchControlGroup(
            id = "portrait-lt",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lt").x.dp,
            offsetY = getLocalOffset("lt").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lt", x, y) },
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            GamepadTriggerButton(
                label = "LT",
                left = true,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                shape = RoundedCornerShape(50),
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "portrait-lb",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lb").x.dp,
            offsetY = getLocalOffset("lb").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lb", x, y) },
            modifier = Modifier.align(Alignment.TopStart).padding(top = bumperHeight + 6.dp),
        ) {
            GamepadBumperButton(
                label = "LB",
                mask = 0x0100,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "portrait-lstick",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lstick").x.dp,
            offsetY = getLocalOffset("lstick").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lstick", x, y) },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            VirtualStick(
                label = "L",
                client = client,
                opacity = opacity,
                diameter = leftStickDiameter,
                onChange = client::setVirtualLeftStick,
            )
        }

        TouchControlGroup(
            id = "portrait-l3",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("l3").x.dp,
            offsetY = getLocalOffset("l3").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("l3", x, y) },
            modifier = Modifier.align(Alignment.BottomStart).padding(
                start = (leftStickDiameter - buttonSize48) / 2,
                bottom = leftStickDiameter + 6.dp
            ),
        ) {
            GamepadButton("LS", GamepadButtonMapping.LEFT_THUMB, client, opacity, buttonSize48, onButtonTone)
        }

        TouchControlGroup(
            id = "portrait-dpad",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("dpad").x.dp,
            offsetY = getLocalOffset("dpad").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("dpad", x, y) },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = leftStickDiameter + 12.dp),
        ) {
            DpadCluster(client, opacity, buttonScale * layoutScale, onButtonTone)
        }

        TouchControlGroup(
            id = "portrait-rt",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rt").x.dp,
            offsetY = getLocalOffset("rt").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rt", x, y) },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            GamepadTriggerButton(
                label = "RT",
                left = false,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                shape = RoundedCornerShape(50),
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "portrait-rb",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rb").x.dp,
            offsetY = getLocalOffset("rb").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rb", x, y) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = bumperHeight + 6.dp),
        ) {
            GamepadBumperButton(
                label = "RB",
                mask = 0x0200,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "portrait-select",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("select").x.dp,
            offsetY = getLocalOffset("select").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("select", x, y) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = buttonSize48 + 8.dp, end = buttonSize44 + 8.dp),
        ) {
            GamepadButton("◀", 0x0020, client, opacity, buttonSize44, onButtonTone)
        }

        TouchControlGroup(
            id = "portrait-start",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("start").x.dp,
            offsetY = getLocalOffset("start").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("start", x, y) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = buttonSize48 + 8.dp),
        ) {
            GamepadButton("▶", 0x0010, client, opacity, buttonSize44, onButtonTone)
        }

        TouchControlGroup(
            id = "portrait-rstick",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rstick").x.dp,
            offsetY = getLocalOffset("rstick").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rstick", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = faceWidth + 12.dp),
        ) {
            VirtualStick(
                label = "R",
                client = client,
                opacity = opacity,
                diameter = rightStickDiameter,
                onChange = client::setVirtualRightStick,
            )
        }

        TouchControlGroup(
            id = "portrait-r3",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("r3").x.dp,
            offsetY = getLocalOffset("r3").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("r3", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(
                end = faceWidth + 12.dp + (rightStickDiameter - buttonSize48) / 2,
                bottom = rightStickDiameter + 6.dp
            ),
        ) {
            GamepadButton("RS", GamepadButtonMapping.RIGHT_THUMB, client, opacity, buttonSize48, onButtonTone)
        }

        TouchControlGroup(
            id = "portrait-face",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("face").x.dp,
            offsetY = getLocalOffset("face").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("face", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            FaceButtonCluster(client, opacity, buttonScale * layoutScale, onButtonTone)
        }
    }
}

@Composable
private fun BoxScope.LandscapeTouchControls(
    client: NativeStreamClient,
    opacity: Float,
    layoutScale: Float,
    buttonScale: Float,
    stickScale: Float,
    viewportHeight: Dp,
    layoutEditing: Boolean,
    getLocalOffset: (String) -> TouchOffset,
    onLocalOffsetChange: (String, Float, Float) -> Unit,
    onButtonTone: () -> Unit,
) {
    val controlScale = buttonScale * layoutScale
    val topControlClearance = landscapeTouchTopControlClearanceDp(viewportHeight.value, controlScale).dp
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        val triggerWidth = 76.dp * controlScale
        val bumperHeight = 36.dp * controlScale

        TouchControlGroup(
            id = "landscape-lt",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lt").x.dp,
            offsetY = getLocalOffset("lt").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lt", x, y) },
            modifier = Modifier.align(Alignment.TopStart).padding(top = topControlClearance),
        ) {
            GamepadTriggerButton(
                label = "LT",
                left = true,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                shape = RoundedCornerShape(50),
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "landscape-lb",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lb").x.dp,
            offsetY = getLocalOffset("lb").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lb", x, y) },
            modifier = Modifier.align(Alignment.TopStart).padding(top = topControlClearance + bumperHeight + 6.dp),
        ) {
            GamepadBumperButton(
                label = "LB",
                mask = 0x0100,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                onPressTone = onButtonTone,
            )
        }

        val selectSize = 42.dp * controlScale
        TouchControlGroup(
            id = "landscape-select",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("select").x.dp,
            offsetY = getLocalOffset("select").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("select", x, y) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(end = selectSize / 2 + 27.dp),
        ) {
            GamepadButton("◀", 0x0020, client, opacity, selectSize, onButtonTone)
        }

        TouchControlGroup(
            id = "landscape-start",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("start").x.dp,
            offsetY = getLocalOffset("start").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("start", x, y) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = selectSize / 2 + 27.dp),
        ) {
            GamepadButton("▶", 0x0010, client, opacity, selectSize, onButtonTone)
        }

        TouchControlGroup(
            id = "landscape-rb",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rb").x.dp,
            offsetY = getLocalOffset("rb").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rb", x, y) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = topControlClearance + bumperHeight + 6.dp),
        ) {
            GamepadBumperButton(
                label = "RB",
                mask = 0x0200,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "landscape-rt",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rt").x.dp,
            offsetY = getLocalOffset("rt").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rt", x, y) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = topControlClearance),
        ) {
            GamepadTriggerButton(
                label = "RT",
                left = false,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                shape = RoundedCornerShape(50),
                onPressTone = onButtonTone,
            )
        }

        val dpadScale = controlScale * 0.88f
        val dpadButtonSize = 54.dp * dpadScale
        val dpadWidth = dpadButtonSize * 2.44f
        TouchControlGroup(
            id = "landscape-dpad",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("dpad").x.dp,
            offsetY = getLocalOffset("dpad").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("dpad", x, y) },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            DpadCluster(client, opacity, dpadScale, onButtonTone)
        }

        val leftStickDiameter = 112.dp * stickScale * layoutScale
        TouchControlGroup(
            id = "landscape-lstick",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lstick").x.dp,
            offsetY = getLocalOffset("lstick").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lstick", x, y) },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = dpadWidth + 14.dp),
        ) {
            VirtualStick(
                label = "L",
                client = client,
                opacity = opacity,
                diameter = leftStickDiameter,
                onChange = client::setVirtualLeftStick,
            )
        }

        val l3Size = 54.dp * controlScale
        TouchControlGroup(
            id = "landscape-l3",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("l3").x.dp,
            offsetY = getLocalOffset("l3").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("l3", x, y) },
            modifier = Modifier.align(Alignment.BottomStart).padding(
                start = dpadWidth + 14.dp + (leftStickDiameter - l3Size) / 2,
                bottom = leftStickDiameter + 6.dp
            ),
        ) {
            GamepadButton("LS", GamepadButtonMapping.LEFT_THUMB, client, opacity, l3Size, onButtonTone)
        }

        val faceScale = controlScale * 0.9f
        val faceButtonSize = 54.dp * faceScale
        val faceWidth = faceButtonSize * 2.44f
        val rightStickDiameter = 112.dp * stickScale * layoutScale
        TouchControlGroup(
            id = "landscape-rstick",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rstick").x.dp,
            offsetY = getLocalOffset("rstick").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rstick", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = faceWidth + 14.dp),
        ) {
            VirtualStick(
                label = "R",
                client = client,
                opacity = opacity,
                diameter = rightStickDiameter,
                onChange = client::setVirtualRightStick,
            )
        }

        val r3Size = 54.dp * controlScale
        TouchControlGroup(
            id = "landscape-r3",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("r3").x.dp,
            offsetY = getLocalOffset("r3").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("r3", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(
                end = faceWidth + 14.dp + (rightStickDiameter - r3Size) / 2,
                bottom = rightStickDiameter + 6.dp
            ),
        ) {
            GamepadButton("RS", GamepadButtonMapping.RIGHT_THUMB, client, opacity, r3Size, onButtonTone)
        }

        TouchControlGroup(
            id = "landscape-face",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("face").x.dp,
            offsetY = getLocalOffset("face").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("face", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            FaceButtonCluster(client, opacity, faceScale, onButtonTone)
        }
    }
}

internal fun landscapeTouchTopControlClearanceDp(viewportHeightDp: Float, controlScale: Float): Float {
    val viewportBand = (viewportHeightDp * 0.11f).coerceIn(34f, 58f)
    val scaledBand = viewportBand * controlScale.coerceIn(0.75f, 1.35f)
    return scaledBand.coerceIn(30f, 76f)
}

@Composable
private fun TouchControlGroup(
    id: String,
    layoutEditing: Boolean,
    offsetX: Dp,
    offsetY: Dp,
    onOffsetChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    val currentOnOffsetChange by rememberUpdatedState(onOffsetChange)
    Box(
        modifier
            .offset(x = offsetX, y = offsetY)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                NativeStreamInputRouter.setTouchControllerPassthroughBound(
                    id,
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt(),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (layoutEditing) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaXDp = with(density) { dragAmount.x.toDp().value }
                            val deltaYDp = with(density) { dragAmount.y.toDp().value }
                            currentOnOffsetChange(
                                (currentOffsetX.value + deltaXDp).coerceIn(-280f, 280f),
                                (currentOffsetY.value + deltaYDp).coerceIn(-280f, 280f),
                            )
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        "Drag",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
    DisposableEffect(id) {
        onDispose {
            NativeStreamInputRouter.clearTouchControllerPassthroughBound(id)
        }
    }
}

private fun clampStickOffset(offset: Offset, maxRadius: Float): Offset {
    val distance = sqrt(offset.x * offset.x + offset.y * offset.y)
    if (distance <= maxRadius || distance == 0f) return offset
    val scale = maxRadius / distance
    return Offset(offset.x * scale, offset.y * scale)
}

@Composable
private fun StickWithThumbButton(
    stickLabel: String,
    thumbLabel: String,
    thumbMask: Int,
    client: NativeStreamClient,
    opacity: Float,
    diameter: Dp,
    buttonScale: Float,
    onButtonTone: () -> Unit,
    onChange: (Float, Float) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GamepadPillButton(
            label = thumbLabel,
            mask = thumbMask,
            client = client,
            opacity = opacity,
            width = 56.dp * buttonScale,
            height = 34.dp * buttonScale,
            onPressTone = onButtonTone,
        )
        VirtualStick(
            label = stickLabel,
            client = client,
            opacity = opacity,
            diameter = diameter,
            onChange = onChange,
        )
    }
}

@Composable
private fun VirtualStick(
    label: String,
    client: NativeStreamClient,
    opacity: Float,
    diameter: androidx.compose.ui.unit.Dp,
    onChange: (Float, Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val style = LocalTouchControllerStyle.current

    DisposableEffect(client) {
        onDispose {
            currentOnChange(0f, 0f)
        }
    }

    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(1.dp, Color.White.copy(alpha = opacity * 0.3f), CircleShape)
            .pointerInput(client) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.pressed }
                        val maxRadius = min(size.width, size.height) * 0.34f
                        if (change == null) {
                            if (knobOffset != Offset.Zero) {
                                currentOnChange(0f, 0f)
                                knobOffset = Offset.Zero
                            }
                            continue
                        }
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val clamped = clampStickOffset(change.position - center, maxRadius)
                        currentOnChange(
                            (clamped.x / maxRadius).coerceIn(-1f, 1f),
                            (clamped.y / maxRadius).coerceIn(-1f, 1f),
                        )
                        knobOffset = clamped
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val knobBackground = if (style == TouchControllerStyle.V2) {
            Color.White.copy(alpha = opacity * 0.2f)
        } else {
            Color.LightGray.copy(alpha = opacity * 0.8f)
        }
        val knobBorderModifier = if (style == TouchControllerStyle.V2) {
            Modifier.border(1.dp, Color.White.copy(alpha = opacity * 0.5f), CircleShape)
        } else {
            Modifier
        }
        Box(
            Modifier
                .size(diameter * 0.44f)
                .graphicsLayer {
                    translationX = knobOffset.x
                    translationY = knobOffset.y
                }
                .clip(CircleShape)
                .background(knobBackground)
                .then(knobBorderModifier)
        )
    }
}

@Composable
private fun FaceButtonCluster(client: NativeStreamClient, opacity: Float, scale: Float, onButtonTone: () -> Unit) {
    val buttonSize = 54.dp * scale
    val distance = buttonSize * 1.05f
    val boxSize = distance * 2 + buttonSize
    Box(Modifier.size(boxSize)) {
        Box(Modifier.align(Alignment.Center).offset(y = -distance)) {
            GamepadButton("Y", 0x8000, client, opacity, buttonSize, onButtonTone)
        }
        Box(Modifier.align(Alignment.Center).offset(y = distance)) {
            GamepadButton("A", 0x1000, client, opacity, buttonSize, onButtonTone)
        }
        Box(Modifier.align(Alignment.Center).offset(x = -distance)) {
            GamepadButton("X", 0x4000, client, opacity, buttonSize, onButtonTone)
        }
        Box(Modifier.align(Alignment.Center).offset(x = distance)) {
            GamepadButton("B", 0x2000, client, opacity, buttonSize, onButtonTone)
        }
    }
}

@Composable
private fun DpadArrowhead(
    label: String,
    pressed: Boolean,
    opacity: Float,
) {
    val arrowColor = if (pressed) {
        Color.White
    } else {
        Color.White.copy(alpha = opacity * 0.8f)
    }
    Text(
        text = label,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = arrowColor
    )
}

@Composable
private fun DpadCluster(client: NativeStreamClient, opacity: Float, scale: Float, onButtonTone: () -> Unit) {
    val currentOnButtonTone by rememberUpdatedState(onButtonTone)
    val buttonSize = 54.dp * scale
    val distance = buttonSize * 1.05f
    val boxSize = distance * 2 + buttonSize

    var upPressed by remember { mutableStateOf(false) }
    var downPressed by remember { mutableStateOf(false) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    val style = LocalTouchControllerStyle.current
    val crossColor = if (style == TouchControllerStyle.V2) Color.Transparent else Color.Black.copy(alpha = opacity * 0.6f)
    val crossBorderColor = if (style == TouchControllerStyle.V2) Color.White.copy(alpha = opacity * 0.5f) else Color.White.copy(alpha = opacity * 0.4f)
    val crossBorderWidth = 1.dp

    DisposableEffect(client) {
        onDispose {
            client.setVirtualButton(0x0001, false)
            client.setVirtualButton(0x0002, false)
            client.setVirtualButton(0x0004, false)
            client.setVirtualButton(0x0008, false)
        }
    }

    Box(
        Modifier
            .size(boxSize)
            .pointerInput(client) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.pressed }
                        
                        if (change == null) {
                            if (upPressed) { client.setVirtualButton(0x0001, false); upPressed = false }
                            if (downPressed) { client.setVirtualButton(0x0002, false); downPressed = false }
                            if (leftPressed) { client.setVirtualButton(0x0004, false); leftPressed = false }
                            if (rightPressed) { client.setVirtualButton(0x0008, false); rightPressed = false }
                            continue
                        }
                        
                        val w = size.width
                        val h = size.height
                        val cx = w / 2f
                        val cy = h / 2f
                        val px = change.position.x
                        val py = change.position.y
                        val dx = px - cx
                        val dy = py - cy
                        val touchDist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        
                        val deadzone = 12.dp.toPx()
                        
                        var newUp = false
                        var newDown = false
                        var newLeft = false
                        var newRight = false
                        
                        if (touchDist > deadzone) {
                            val absDx = Math.abs(dx)
                            val absDy = Math.abs(dy)
                            if (dy < 0 && absDy > absDx * 0.414f) newUp = true
                            if (dy > 0 && absDy > absDx * 0.414f) newDown = true
                            if (dx < 0 && absDx > absDy * 0.414f) newLeft = true
                            if (dx > 0 && absDx > absDy * 0.414f) newRight = true
                        }
                        
                        val playTone = (!upPressed && newUp) || (!downPressed && newDown) || 
                                       (!leftPressed && newLeft) || (!rightPressed && newRight)
                        
                        if (upPressed != newUp) { client.setVirtualButton(0x0001, newUp); upPressed = newUp }
                        if (downPressed != newDown) { client.setVirtualButton(0x0002, newDown); downPressed = newDown }
                        if (leftPressed != newLeft) { client.setVirtualButton(0x0004, newLeft); leftPressed = newLeft }
                        if (rightPressed != newRight) { client.setVirtualButton(0x0008, newRight); rightPressed = newRight }
                        
                        if (playTone) currentOnButtonTone()
                        change.consume()
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val armSize = buttonSize.toPx()
            val cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())

            val crossPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = (w - armSize) / 2f,
                        top = 0f,
                        right = (w + armSize) / 2f,
                        bottom = h,
                        cornerRadius = cornerRadius
                    )
                )
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = (h - armSize) / 2f,
                        right = w,
                        bottom = (h + armSize) / 2f,
                        cornerRadius = cornerRadius
                    )
                )
            }

            if (style != TouchControllerStyle.V2) {
                drawPath(crossPath, crossColor)
            }

            val pressedColor = if (style == TouchControllerStyle.V2) {
                Color.White.copy(alpha = opacity * 0.15f)
            } else {
                Color.White.copy(alpha = opacity * 0.2f)
            }

            val pressedPath = Path()
            if (upPressed) {
                pressedPath.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = (w - armSize) / 2f,
                        top = 0f,
                        right = (w + armSize) / 2f,
                        bottom = h / 2f,
                        topLeftCornerRadius = cornerRadius,
                        topRightCornerRadius = cornerRadius
                    )
                )
            }
            if (downPressed) {
                pressedPath.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = (w - armSize) / 2f,
                        top = h / 2f,
                        right = (w + armSize) / 2f,
                        bottom = h,
                        bottomLeftCornerRadius = cornerRadius,
                        bottomRightCornerRadius = cornerRadius
                    )
                )
            }
            if (leftPressed) {
                pressedPath.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = (h - armSize) / 2f,
                        right = w / 2f,
                        bottom = (h + armSize) / 2f,
                        topLeftCornerRadius = cornerRadius,
                        bottomLeftCornerRadius = cornerRadius
                    )
                )
            }
            if (rightPressed) {
                pressedPath.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = w / 2f,
                        top = (h - armSize) / 2f,
                        right = w,
                        bottom = (h + armSize) / 2f,
                        topRightCornerRadius = cornerRadius,
                        bottomRightCornerRadius = cornerRadius
                    )
                )
            }
            drawPath(pressedPath, pressedColor)

            drawPath(
                path = crossPath,
                color = crossBorderColor,
                style = Stroke(width = crossBorderWidth.toPx())
            )
        }

        Box(Modifier.align(Alignment.Center).offset(y = -distance)) {
            DpadArrowhead("▲", upPressed, opacity)
        }
        Box(Modifier.align(Alignment.Center).offset(y = distance)) {
            DpadArrowhead("▼", downPressed, opacity)
        }
        Box(Modifier.align(Alignment.Center).offset(x = -distance)) {
            DpadArrowhead("◀", leftPressed, opacity)
        }
        Box(Modifier.align(Alignment.Center).offset(x = distance)) {
            DpadArrowhead("▶", rightPressed, opacity)
        }
    }
}

@Composable
private fun GamepadTriggerButton(
    label: String,
    left: Boolean,
    client: NativeStreamClient,
    opacity: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape,
    onPressTone: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val style = LocalTouchControllerStyle.current
    val buttonColor = if (style == TouchControllerStyle.V2) {
        Color.Transparent
    } else {
        Color.Black.copy(alpha = opacity * 0.6f)
    }
    val pressedColor = if (style == TouchControllerStyle.V2) {
        Color.White.copy(alpha = opacity * 0.15f)
    } else {
        Color.White.copy(alpha = opacity * 0.2f)
    }
    val borderColor = if (style == TouchControllerStyle.V2) {
        if (pressed) Color.White.copy(alpha = opacity * 0.9f) else Color.White.copy(alpha = opacity * 0.5f)
    } else {
        Color.White.copy(alpha = opacity * 0.4f)
    }
    val borderWidth = if (style == TouchControllerStyle.V2 && pressed) 2.dp else 1.dp
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(if (pressed) pressedColor else buttonColor)
            .border(borderWidth, borderColor, shape)
            .pointerInput(client, left) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.any { it.pressed }
                        if (down != pressed) {
                            client.setVirtualTrigger(left, down)
                            pressed = down
                            if (down) onPressTone()
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = opacity * 0.9f))
    }
    DisposableEffect(client, left) {
        onDispose {
            client.setVirtualTrigger(left, false)
        }
    }
}

@Composable
private fun GamepadBumperButton(
    label: String,
    mask: Int,
    client: NativeStreamClient,
    opacity: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onPressTone: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val style = LocalTouchControllerStyle.current
    val buttonColor = if (style == TouchControllerStyle.V2) {
        Color.Transparent
    } else {
        Color.Black.copy(alpha = opacity * 0.6f)
    }
    val pressedColor = if (style == TouchControllerStyle.V2) {
        Color.White.copy(alpha = opacity * 0.15f)
    } else {
        Color.White.copy(alpha = opacity * 0.2f)
    }
    val borderColor = if (style == TouchControllerStyle.V2) {
        if (pressed) Color.White.copy(alpha = opacity * 0.9f) else Color.White.copy(alpha = opacity * 0.5f)
    } else {
        Color.White.copy(alpha = opacity * 0.4f)
    }
    val borderWidth = if (style == TouchControllerStyle.V2 && pressed) 2.dp else 1.dp
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(if (pressed) pressedColor else buttonColor)
            .border(borderWidth, borderColor, shape)
            .pointerInput(client, mask) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.any { it.pressed }
                        if (down != pressed) {
                            client.setVirtualButton(mask, down)
                            pressed = down
                            if (down) onPressTone()
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = opacity * 0.9f))
    }
    DisposableEffect(client, mask) {
        onDispose {
            client.setVirtualButton(mask, false)
        }
    }
}

@Composable
private fun GamepadButton(
    label: String,
    mask: Int,
    client: NativeStreamClient,
    opacity: Float,
    size: androidx.compose.ui.unit.Dp,
    onPressTone: () -> Unit = {},
) {
    val currentOnPressTone by rememberUpdatedState(onPressTone)
    var pressed by remember { mutableStateOf(false) }
    val style = LocalTouchControllerStyle.current
    val buttonColor = if (style == TouchControllerStyle.V2) {
        Color.Transparent
    } else {
        Color.Black.copy(alpha = opacity * 0.6f)
    }
    val pressedColor = if (style == TouchControllerStyle.V2) {
        Color.White.copy(alpha = opacity * 0.15f)
    } else {
        Color.White.copy(alpha = opacity * 0.2f)
    }
    val borderColor = if (style == TouchControllerStyle.V2) {
        if (pressed) Color.White.copy(alpha = opacity * 0.9f) else Color.White.copy(alpha = opacity * 0.5f)
    } else {
        Color.White.copy(alpha = opacity * 0.4f)
    }
    val borderWidth = if (style == TouchControllerStyle.V2 && pressed) 2.dp else 1.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) pressedColor else buttonColor)
            .border(borderWidth, borderColor, CircleShape)
            .pointerInput(client, mask) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.any { it.pressed }
                        if (down != pressed) {
                            client.setVirtualButton(mask, down)
                            pressed = down
                            if (down) currentOnPressTone()
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = opacity * 0.9f),
        )
    }
    DisposableEffect(client, mask) {
        onDispose {
            client.setVirtualButton(mask, false)
        }
    }
}

@Composable
private fun GamepadPillButton(
    label: String,
    mask: Int,
    client: NativeStreamClient,
    opacity: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onPressTone: () -> Unit = {},
) {
    val currentOnPressTone by rememberUpdatedState(onPressTone)
    var pressed by remember { mutableStateOf(false) }
    val style = LocalTouchControllerStyle.current
    val buttonColor = if (style == TouchControllerStyle.V2) {
        Color.Transparent
    } else {
        Color.Black.copy(alpha = opacity * 0.6f)
    }
    val pressedColor = if (style == TouchControllerStyle.V2) {
        Color.White.copy(alpha = opacity * 0.15f)
    } else {
        Color.White.copy(alpha = opacity * 0.2f)
    }
    val borderColor = if (style == TouchControllerStyle.V2) {
        if (pressed) Color.White.copy(alpha = opacity * 0.9f) else Color.White.copy(alpha = opacity * 0.5f)
    } else {
        Color.White.copy(alpha = opacity * 0.4f)
    }
    val borderWidth = if (style == TouchControllerStyle.V2 && pressed) 2.dp else 1.dp
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(if (pressed) pressedColor else buttonColor)
            .border(borderWidth, borderColor, RoundedCornerShape(999.dp))
            .pointerInput(client, mask) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.any { it.pressed }
                        if (down != pressed) {
                            client.setVirtualButton(mask, down)
                            pressed = down
                            if (down) currentOnPressTone()
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = opacity * 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    DisposableEffect(client, mask) {
        onDispose {
            client.setVirtualButton(mask, false)
        }
    }
}

@Composable
private fun SortPicker(
    options: List<CatalogSortOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val labels = options.ifEmpty { listOf(CatalogSortOption("relevance", "Relevance", "")) }
    val selectedLabel = labels.firstOrNull { it.id == selected }?.label ?: labels.first().label
    var expanded by remember { mutableStateOf(false) }
    val controlShape = RoundedCornerShape(999.dp)
    val controlColor = Color.White.copy(alpha = 0.1f)
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(if (compact) TopBarCompactControlHeight else 40.dp),
            shape = controlShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = controlColor,
                contentColor = TextPrimary,
            ),
            contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 12.dp),
        ) {
            Text(
                "Sort: $selectedLabel",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (option.id == selected) "✓" else "", modifier = Modifier.width(24.dp))
                            Text(option.label)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.id)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedFilterChips(options: List<CatalogFilterOption>, selectedIds: List<String>, onToggle: (String) -> Unit) {
    val selectedOptions = options.filter { it.id in selectedIds }
    if (selectedOptions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        selectedOptions.take(4).forEach { option ->
            AssistChip(onClick = { onToggle(option.id) }, label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
        if (selectedOptions.size > 4) {
            AssistChip(onClick = {}, label = { Text("+${selectedOptions.size - 4}") })
        }
    }
}

private fun catalogVisibleFilterGroups(groups: List<CatalogFilterGroup>): List<CatalogFilterGroup> =
    groups.filter { it.id in setOf("digital_store", "genre", "subscriptions") }

private fun catalogFilterOptions(groups: List<CatalogFilterGroup>): List<CatalogFilterOption> =
    groups.flatMap { group -> group.options.take(if (group.id == "genre") 10 else group.options.size) }

@Composable
private fun FilterMenu(
    options: List<CatalogFilterOption>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    compact: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val filterControlShape = RoundedCornerShape(999.dp)
    val filterControlColor = Color.White.copy(alpha = 0.1f)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(if (compact) TopBarCompactControlHeight else 36.dp),
            shape = filterControlShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = filterControlColor,
                contentColor = TextPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text(if (selectedIds.isEmpty()) "Filters" else "Filters ${selectedIds.size}", maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                title = {
                    Text(
                        "Filters",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(0.6f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(options) { option ->
                            val isSelected = option.id in selectedIds
                            var rowFocused by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .onFocusChanged { rowFocused = it.isFocused }
                                    .background(if (rowFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (rowFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onToggle(option.id) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { expanded = false }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}

@Composable
private fun PrintedWasteSelector(
    state: OpenNowUiState,
    game: GameInfo,
    viewModel: OpenNowViewModel,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = viewModel::dismissPrintedWasteSelector)
    val zones = remember(state.printedWasteQueue, state.printedWasteMapping, state.printedWastePings) {
        state.printedWasteQueue
            .filter { (zoneId, _) -> isStandardPrintedWasteZone(zoneId) && state.printedWasteMapping[zoneId]?.nuked != true }
            .map { (zoneId, zone) ->
                val routingUrl = printedWasteZoneUrl(zoneId)
                PrintedWasteZoneOption(
                    zoneId = zoneId,
                    zone = zone,
                    routingUrl = routingUrl,
                    pingMs = state.printedWastePings[routingUrl],
                )
            }
    }
    val autoZone = remember(zones) { recommendedPrintedWasteZone(zones) }
    val sortedZones = remember(zones, autoZone) {
        val maxPing = zones.mapNotNull { it.pingMs }.maxOrNull()?.coerceAtLeast(1) ?: 1
        val maxQueue = zones.maxOfOrNull { it.zone.QueuePosition }?.coerceAtLeast(1) ?: 1
        zones.sortedWith(
            compareByDescending<PrintedWasteZoneOption> { it.zoneId == autoZone?.zoneId }
                .thenBy { printedWasteScore(it, maxPing, maxQueue) }
                .thenBy { it.zoneId },
        )
    }
    var selectedZoneId by remember(game.id, sortedZones) { mutableStateOf<String?>(autoZone?.zoneId) }
    val selectedZone = sortedZones.firstOrNull { it.zoneId == selectedZoneId } ?: autoZone
    val context = LocalContext.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .lockedFocusGroup()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false) {},
    ) {
        val phoneLandscape = isPhoneLandscape(maxWidth, maxHeight)
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = if (phoneLandscape) Alignment.CenterEnd else Alignment.Center,
        ) {
            Card(
                modifier = modifier
                    .then(
                        if (phoneLandscape) {
                            Modifier
                                .padding(end = 12.dp)
                                .fillMaxWidth(0.9f)
                                .fillMaxHeight(0.9f)
                        } else {
                            Modifier
                                .fillMaxWidth(0.94f)
                                .fillMaxHeight(0.82f)
                        },
                    ),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(22.dp),
            ) {
                if (phoneLandscape) {
                    Row(
                        Modifier.fillMaxSize().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PrintedWasteGameSummary(
                            game = game,
                            modifier = Modifier
                                .width(190.dp)
                                .fillMaxHeight(),
                        )
                        PrintedWasteOptionsColumn(
                            state = state,
                            zones = sortedZones,
                            selectedZoneId = selectedZoneId,
                            selectedZone = selectedZone,
                            autoZone = autoZone,
                            showRecommendedCard = true,
                            onSelectZone = { selectedZoneId = it },
                            onRetry = viewModel::refreshPrintedWasteQueues,
                            onDismiss = viewModel::dismissPrintedWasteSelector,
                            onDefault = { viewModel.launchWithPrintedWaste(null) },
                            onLaunch = { viewModel.launchWithPrintedWaste(selectedZone?.routingUrl) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UrlImage(
                                gameTvBannerImageUrl(context, game),
                                Modifier
                                    .width(98.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(game.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Free tier queue routing", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        PrintedWasteOptionsColumn(
                            state = state,
                            zones = sortedZones,
                            selectedZoneId = selectedZoneId,
                            selectedZone = selectedZone,
                            autoZone = autoZone,
                            showRecommendedCard = true,
                            onSelectZone = { selectedZoneId = it },
                            onRetry = viewModel::refreshPrintedWasteQueues,
                            onDismiss = viewModel::dismissPrintedWasteSelector,
                            onDefault = { viewModel.launchWithPrintedWaste(null) },
                            onLaunch = { viewModel.launchWithPrintedWaste(selectedZone?.routingUrl) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintedWasteGameSummary(
    game: GameInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UrlImage(
            gameTvBannerImageUrl(context, game),
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(game.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Free tier queue routing", color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun PrintedWasteOptionsColumn(
    state: OpenNowUiState,
    zones: List<PrintedWasteZoneOption>,
    selectedZoneId: String?,
    selectedZone: PrintedWasteZoneOption?,
    autoZone: PrintedWasteZoneOption?,
    showRecommendedCard: Boolean,
    onSelectZone: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onDefault: () -> Unit,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneListState = rememberLazyListState()
    val zoneListFocusRequester = remember { FocusRequester() }
    val defaultFocusRequester = remember { FocusRequester() }
    val launchFocusRequester = remember { FocusRequester() }
    var zoneListFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun selectZoneAt(index: Int) {
        val next = zones.getOrNull(index) ?: return
        onSelectZone(next.zoneId)
        scope.launch {
            zoneListState.animateScrollToItem(index)
        }
    }
    LaunchedEffect(state.printedWasteLoading, state.printedWasteError, zones.size) {
        delay(80)
        if (!state.printedWasteLoading && state.printedWasteError == null && zones.isNotEmpty()) {
            runCatching { launchFocusRequester.requestFocus() }
        } else {
            runCatching { defaultFocusRequester.requestFocus() }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.printedWasteLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Checking PrintedWaste queues and latency", color = TextMuted)
                }
            }
        } else if (state.printedWasteError != null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.printedWasteError, color = Color(0xffff9f9f))
                    OutlinedButton(onClick = onRetry) { Text("Retry") }
                }
            }
        } else {
            if (showRecommendedCard) {
                autoZone?.let {
                    RecommendedPrintedWasteCard(it)
                }
            }
            var listFocused by remember { mutableStateOf(false) }
            LazyColumn(
                state = zoneListState,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(zoneListFocusRequester)
                    .onFocusChanged { listFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (isTvActivateKey(event)) {
                            if (selectedZone != null) {
                                onLaunch()
                                true
                            } else {
                                false
                            }
                        } else if (event.type == KeyEventType.KeyDown) {
                            val selectedIndex = zones.indexOfFirst { it.zoneId == selectedZoneId }.let { if (it >= 0) it else 0 }
                            when (event.key) {
                                Key.DirectionUp -> {
                                    if (selectedIndex > 0) {
                                        selectZoneAt(selectedIndex - 1)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (selectedIndex < zones.lastIndex) {
                                        selectZoneAt(selectedIndex + 1)
                                        true
                                    } else {
                                        runCatching { launchFocusRequester.requestFocus() }.isSuccess
                                    }
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusable(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(zones, key = { it.zoneId }) { zoneOption ->
                    val isCurrent = zoneOption.zoneId == selectedZoneId
                    PrintedWasteZoneRow(
                        zoneOption = zoneOption,
                        selected = isCurrent,
                        focused = isCurrent && listFocused,
                        listFocused = listFocused,
                        onClick = { onSelectZone(zoneOption.zoneId) },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            OutlinedButton(
                onClick = onDefault,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(defaultFocusRequester),
            ) {
                Text("Default", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(
                onClick = onLaunch,
                enabled = !state.printedWasteLoading && selectedZone != null,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(launchFocusRequester)
                    .focusProperties { up = zoneListFocusRequester },
            ) {
                Text("Launch", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RecommendedPrintedWasteCard(zoneOption: PrintedWasteZoneOption) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Best available route", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    "${zoneOption.zoneId} · ${regionLabel(zoneOption.zone.Region)}",
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            QueueMetricPill("Ping", zoneOption.pingMs?.let { "$it ms" } ?: "Checking")
            QueueMetricPill("Ahead", zoneOption.zone.QueuePosition.toString(), queueColor(zoneOption.zone.QueuePosition))
        }
    }
}

@Composable
private fun PrintedWasteZoneRow(
    zoneOption: PrintedWasteZoneOption,
    selected: Boolean,
    focused: Boolean,
    listFocused: Boolean,
    onClick: () -> Unit,
) {
    val zone = zoneOption.zone
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else PanelAlt,
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = if (selected && listFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 520.dp
            if (compact) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(zoneOption.zoneId, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else TextPrimary)
                            Text(regionLabel(zone.Region), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected) {
                            Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueMetricPill("Ping", zoneOption.pingMs?.let { "$it ms" } ?: "--", zoneOption.pingMs?.let(::pingColor) ?: TextMuted)
                        QueueMetricPill("Ahead", zone.QueuePosition.toString(), queueColor(zone.QueuePosition))
                        zone.eta?.let { QueueMetricPill("Wait", formatPrintedWasteWait(it)) }
                    }
                }
            } else {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(zoneOption.zoneId, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else TextPrimary)
                        Text(regionLabel(zone.Region), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    QueueMetricPill("Ping", zoneOption.pingMs?.let { "$it ms" } ?: "--", zoneOption.pingMs?.let(::pingColor) ?: TextMuted)
                    QueueMetricPill("Ahead", zone.QueuePosition.toString(), queueColor(zone.QueuePosition))
                    zone.eta?.let { QueueMetricPill("Wait", formatPrintedWasteWait(it)) }
                }
            }
        }
    }
}

@Composable
private fun QueueMetricPill(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun isStandardPrintedWasteZone(zoneId: String): Boolean =
    zoneId.startsWith("NP-") && !zoneId.startsWith("NPA-")

private data class PrintedWasteZoneOption(
    val zoneId: String,
    val zone: PrintedWasteZone,
    val routingUrl: String,
    val pingMs: Long?,
)

private fun recommendedPrintedWasteZone(zones: List<PrintedWasteZoneOption>): PrintedWasteZoneOption? {
    if (zones.isEmpty()) return null
    val pool = zones.filter { it.pingMs != null }.ifEmpty { zones }
    val maxPing = pool.mapNotNull { it.pingMs }.maxOrNull()?.coerceAtLeast(1) ?: 1
    val maxQueue = pool.maxOfOrNull { it.zone.QueuePosition }?.coerceAtLeast(1) ?: 1
    return pool.minWithOrNull(
        compareBy<PrintedWasteZoneOption> { printedWasteScore(it, maxPing, maxQueue) }
            .thenBy { it.pingMs ?: Long.MAX_VALUE }
            .thenBy { it.zone.QueuePosition },
    )
}

private fun printedWasteScore(zone: PrintedWasteZoneOption, maxPing: Long, maxQueue: Int): Double {
    val pingScore = ((zone.pingMs ?: maxPing).toDouble() / maxPing.toDouble()) * 0.75
    val queueScore = (zone.zone.QueuePosition.toDouble() / maxQueue.toDouble()) * 0.25
    return pingScore + queueScore
}

private fun printedWasteZoneUrl(zoneId: String): String =
    "https://${zoneId.lowercase()}.cloudmatchbeta.nvidiagrid.net/"

private fun formatPrintedWasteWait(etaMs: Long): String {
    val minutes = ((etaMs + 59_999L) / 60_000L).coerceAtLeast(1L)
    return if (minutes < 60L) "${minutes}m" else "${minutes / 60L}h ${minutes % 60L}m"
}

private fun queueColor(queue: Int): Color = when {
    queue <= 5 -> Green
    queue <= 20 -> Color(0xffc7ef6b)
    queue <= 45 -> Color(0xffffc95a)
    else -> Color(0xffff8d8d)
}

private fun pingColor(pingMs: Long): Color = when {
    pingMs <= 60L -> Green
    pingMs <= 120L -> Color(0xffc7ef6b)
    pingMs <= 180L -> Color(0xffffc95a)
    else -> Color(0xffff8d8d)
}

private fun regionLabel(region: String): String = when (region) {
    "US" -> "North America"
    "CA" -> "Canada"
    "EU" -> "Europe"
    "JP" -> "Japan"
    "KR" -> "South Korea"
    "THAI" -> "Southeast Asia"
    "MY" -> "Malaysia"
    else -> region
}

@Composable
private fun ProviderPicker(providers: List<LoginProvider>, selected: LoginProvider, onSelect: (LoginProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(selected.displayName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(provider)
                    },
                )
            }
        }
    }
}

private sealed interface UrlImageState {
    data object Empty : UrlImageState
    data object Loading : UrlImageState
    data object Failed : UrlImageState
    data object Loaded : UrlImageState
}

private fun imageDataForSource(source: String): Any? {
    val key = source.trim()
    if (key.isBlank()) return null
    val uri = runCatching { Uri.parse(key) }.getOrNull() ?: return null
    val scheme = uri.scheme.orEmpty().lowercase(Locale.US)
    return when {
        scheme == "http" || scheme == "https" -> key
        scheme == "content" || scheme == "android.resource" || scheme == "file" -> uri
        scheme.isBlank() && key.startsWith("/") -> File(key)
        else -> uri
    }
}

@Composable
internal fun UrlImage(
    url: String?,
    modifier: Modifier = Modifier,
    fallbackUrl: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val source = url?.trim().orEmpty()
    val fallbackSource = fallbackUrl?.trim()?.takeIf { it.isNotBlank() && it != source }
    var activeSource by remember(source, fallbackSource) {
        mutableStateOf(source.takeIf { it.isNotBlank() } ?: fallbackSource)
    }
    var imageState by remember(source, fallbackSource) {
        mutableStateOf(if (activeSource == null) UrlImageState.Empty else UrlImageState.Loading)
    }
    val imageData = remember(activeSource) { activeSource?.let(::imageDataForSource) }
    LaunchedEffect(activeSource, imageData, fallbackSource, source) {
        if (activeSource == null) {
            imageState = UrlImageState.Empty
        } else if (imageData == null) {
            if (activeSource == source && fallbackSource != null) {
                activeSource = fallbackSource
                imageState = UrlImageState.Loading
            } else {
                imageState = UrlImageState.Failed
            }
        }
    }
    Box(modifier.background(Color(0xff102015)), contentAlignment = Alignment.Center) {
        if (imageData != null) {
            key(activeSource) {
                AsyncImage(
                    model = imageData,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    onLoading = { imageState = UrlImageState.Loading },
                    onSuccess = { imageState = UrlImageState.Loaded },
                    onError = {
                        if (activeSource == source && fallbackSource != null) {
                            activeSource = fallbackSource
                            imageState = UrlImageState.Loading
                        } else {
                            imageState = UrlImageState.Failed
                        }
                    },
                )
            }
        }
        when (imageState) {
            UrlImageState.Loading -> LoadingShimmer(Modifier.fillMaxSize())
            UrlImageState.Loaded -> Unit
            UrlImageState.Empty,
            UrlImageState.Failed,
            -> OpenNowMark(42.dp)
        }
    }
}

@Composable
private fun LoadingShimmer(modifier: Modifier = Modifier) {
    // Use the shared shimmer offset from GameGridSkeleton if available; fall back to a
    // local animation only when LoadingShimmer is used outside a GameGridSkeleton context.
    // Using nullable avoids treating 0f (a valid animation start value) as "not provided".
    val sharedPulse = LocalTvLoadingPulse.current
    val localPulse = if (LocalTvLoadingProfile.current && sharedPulse == null) {
        val transition = rememberInfiniteTransition(label = "loading-pulse-local")
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loading-pulse-local",
        )
        pulse
    } else {
        null
    }
    val pulse = sharedPulse ?: localPulse
    val shimmer = LocalShimmerOffset.current ?: if (pulse == null) run {
        val transition = rememberInfiniteTransition(label = "shimmer-local")
        val localOffset by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1150, easing = LinearEasing),
            ),
            label = "shimmer-offset-local",
        )
        localOffset
    } else 0f
    val baseColor = Color(0xff0d1216)
    val highlightColor1 = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    val highlightColor2 = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    Spacer(
        modifier = modifier
            .background(baseColor)
            .drawBehind {
                if (pulse != null) {
                    drawRect(highlightColor1.copy(alpha = 0.08f + pulse * 0.18f))
                } else {
                    val width = size.width
                    val height = size.height
                    val xOffset = -width + shimmer * (width * 2)
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            baseColor,
                            highlightColor1,
                            highlightColor2,
                            highlightColor1,
                            baseColor,
                        ),
                        start = Offset(xOffset, 0f),
                        end = Offset(xOffset + width, height),
                    )
                    drawRect(brush)
                }
            }
    )
}

@Composable
private fun OpenNowMark(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(R.drawable.opennow_logo_mark),
        contentDescription = "OpenNOW",
        modifier = Modifier
            .width(size * 1.85f)
            .height(size),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun OpenNowAppIcon(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(R.drawable.opennow_icon),
        contentDescription = "OpenNOW",
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

internal val ColorQuality.label: String
    get() = when (this) {
        ColorQuality.EightBit420 -> "8-bit 4:2:0"
        ColorQuality.EightBit444 -> "8-bit 4:4:4"
        ColorQuality.TenBit420 -> "10-bit 4:2:0"
        ColorQuality.TenBit444 -> "10-bit 4:4:4"
    }

private val GameCardOverlayGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.95f))
)
