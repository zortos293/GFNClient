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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
    val showUpdatePrompt = updatePromptKey != null &&
        updatePromptKey != hiddenUpdatePromptKey &&
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
        Box(Modifier.fillMaxSize()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
        }
    }
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
    val tvLogin = state.codecReport?.androidTvProfile == true
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OpenNowMark(88.dp)
        Spacer(Modifier.height(20.dp))
        Text("OpenNOW", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Native Android GeForce NOW client", color = TextMuted)
        Spacer(Modifier.height(28.dp))
        ProviderPicker(state.providers, state.selectedProvider, viewModel::selectProvider)
        Spacer(Modifier.height(16.dp))
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
        }
        if (state.error != null) {
            Spacer(Modifier.height(14.dp))
            Text(state.error.orEmpty(), color = Color(0xffff9f9f))
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
                Text(stringResource(R.string.login_tv_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            Text(stringResource(R.string.login_tv_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
private fun QrCodeView(qrCode: QrCode, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(14.dp),
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
    data object Default : CatalogWallpaperSelection
    data class Custom(val source: String) : CatalogWallpaperSelection
}

internal fun catalogWallpaperSelection(customSource: String?): CatalogWallpaperSelection =
    customSource
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(CatalogWallpaperSelection::Custom)
        ?: CatalogWallpaperSelection.Default

@Composable
private fun CatalogWallpaperBackdrop(
    settings: AppSettings,
    tvProfile: Boolean,
    width: Dp,
    height: Dp,
) {
    val showBackdrop = settings.nerdCatalogBackground || tvProfile
    if (!showBackdrop) {
        return
    }
    val wallpaper = catalogWallpaperSelection(settings.nerdCatalogBackgroundUri)
    val scrimAlpha = when {
        tvProfile -> 0.48f
        width > height -> 0.28f
        else -> 0.36f
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        when (wallpaper) {
            CatalogWallpaperSelection.Default -> {
                CatalogDefaultWallpaperBackdrop(Modifier.matchParentSize())
            }
            is CatalogWallpaperSelection.Custom -> {
                val fallbackPainter = painterResource(R.drawable.catalog_default_background)
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

@Composable
private fun CatalogDefaultWallpaperBackdrop(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.catalog_default_background),
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
    val tvProfile = state.codecReport?.androidTvProfile == true
    val navAudioController = remember(context) { AndroidNerdAudioController(context.applicationContext) }
    var visibleSearchTarget by remember { mutableStateOf<SearchTarget?>(null) }
    var settingsSearchQuery by remember { mutableStateOf("") }
    var settingsDetailRouteOpen by remember { mutableStateOf(false) }
    var settingsBackRequestToken by remember { mutableStateOf(0) }
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
    BackHandler(enabled = state.selectedGame != null && !inStream) {
        viewModel.clearSelectedGame()
    }
    BackHandler(enabled = tvProfile && !inStream && state.selectedGame == null && state.page != AppPage.Home) {
        viewModel.setPage(AppPage.Home)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        var phoneLandscapeScrollChromeHidden by remember { mutableStateOf(false) }
        val horizontalChrome = maxWidth > maxHeight
        val controllerLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val physicalControllerConnected = rememberPhysicalControllerConnected(
            enabled = controllerLandscape && !tvProfile,
        )
        val controllerCatalogActionsVisible =
            controllerLandscape &&
                !tvProfile &&
                physicalControllerConnected
        val phoneLandscapeChrome = !tvProfile && !inStream && isPhoneLandscape(maxWidth, maxHeight)
        val portraitChrome = !inStream && maxHeight >= maxWidth
        val showNavigationRail = !inStream && (tvProfile || phoneLandscapeChrome)
        val scrollChromePage = state.page == AppPage.Home || state.page == AppPage.Library
        val storeControlsInTopBar = phoneLandscapeChrome && state.page == AppPage.Home
        val libraryControlsInTopBar = phoneLandscapeChrome && state.page == AppPage.Library
        val tvSafeAreaPadding = if (tvProfile && !inStream) state.settings.tvSafeAreaPaddingDp.dp else 0.dp
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
            contentWindowInsets = if (streamingActive) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
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
                                    visibleSearchTarget = null
                                    viewModel.setPage(AppPage.Settings)
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
                    .padding(tvSafeAreaPadding)
                    .onPreviewKeyEvent { event ->
                        if (isNavigationToneKey(event)) {
                            navAudioController.playButtonTone(navigationToneEnabled)
                        }
                        false
                    },
            ) {
                Row(Modifier.fillMaxSize()) {
                    if (showNavigationRail) {
                        AppNavigationRail(
                            state = state,
                            activeSearchTarget = visibleSearchTarget,
                            showAppIcon = showNavigationRail && horizontalChrome,
                            largeIcons = phoneLandscapeChrome,
                            showSettingsBack = state.page == AppPage.Settings && horizontalChrome && settingsDetailRouteOpen,
                            showCatalogControllerActions = controllerCatalogActionsVisible &&
                                state.selectedGame == null &&
                                !modalPickerOpen &&
                                (state.page == AppPage.Home || state.page == AppPage.Library),
                            onNavigate = { page ->
                                visibleSearchTarget = null
                                viewModel.setPage(page)
                            },
                            onSearch = { revealSearch(it) },
                            onSettingsBack = { settingsBackRequestToken += 1 },
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        AnimatedVisibility(
                            visible = portraitChrome || (phoneLandscapeChrome && !phoneLandscapeScrollChromeHidden),
                        ) {
                            if (!inStream) {
                                TopStatusBar(
                                    state = state,
                                    onResumeActiveSession = viewModel::resumeActiveSession,
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
                            onPlay = viewModel::play,
                            onChooseStore = viewModel::chooseStore,
                            onFavorite = viewModel::updateFavorites,
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

@Composable
private fun AppNavigationRailItem(selected: Boolean, onClick: () -> Unit, iconRes: Int, label: String, iconSize: Dp = 24.dp) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            unselectedIconColor = TextMuted,
            unselectedTextColor = TextMuted,
        ),
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(iconSize),
            )
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
                    TopStatusDetails(state)
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
private fun TopStatusDetails(state: OpenNowUiState) {
    val stream = state.activeStreamSettings ?: state.settings.stream
    val summary = streamStatusSummary(stream)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.height(TopBarCompactControlHeight),
            shape = RoundedCornerShape(999.dp),
            color = PanelAlt.copy(alpha = 0.9f),
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
    focusManager.moveFocus(direction)
    return true
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
                Text("Resume cloud session", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun GameGridSkeleton(
    settings: AppSettings,
    tvProfile: Boolean,
    storeLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = settings.posterSizeScale.coerceIn(0.82f, 1.08f)
    val compact = settings.compactGameCards
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val shimmerBrush = rememberLoadingShimmerBrush(label = "game-grid-skeleton-shimmer")
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
                        shimmerBrush = shimmerBrush,
                    )
                }
            }
            gridItems(placeholderItems, key = { it }) {
                GameCardSkeleton(
                    cardHeight = gridSpec.cardHeight * scale,
                    squareCard = gridSpec.squareCards,
                    thumbnailPlayOverlay = !tvProfile,
                    showStoreLabels = settings.showGameStoreLabels,
                    shimmerBrush = shimmerBrush,
                )
            }
        }
    }
}

@Composable
private fun StoreStartRailsSkeleton(
    settings: AppSettings,
    tvProfile: Boolean,
    shimmerBrush: Brush,
) {
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardWidth = storeRailCardWidth(tvProfile, landscapeLayout)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            StoreRailSectionSkeleton(
                cardWidth = cardWidth,
                expressiveUi = settings.expressiveUi,
                shimmerBrush = shimmerBrush,
            )
        }
    }
}

@Composable
private fun StoreRailSectionSkeleton(
    cardWidth: Dp,
    expressiveUi: Boolean,
    shimmerBrush: Brush,
) {
    val spacing = 10.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonLine(widthFraction = 0.34f, height = 15.dp, shimmerBrush = shimmerBrush)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            val visibleCount = (((maxWidth.value + spacing.value) / (cardWidth.value + spacing.value)).toInt())
                .coerceAtLeast(1)
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
                        shimmerBrush = shimmerBrush,
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
    shimmerBrush: Brush,
) {
    val shape = RoundedCornerShape(if (expressiveUi) 12.dp else 8.dp)
    Surface(
        modifier = Modifier
            .width(width)
            .aspectRatio(1f)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
        shape = shape,
        color = Color.Black,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            LoadingShimmer(Modifier.fillMaxSize(), shimmerBrush = shimmerBrush)
            SkeletonCircle(
                size = 44.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                shimmerBrush = shimmerBrush,
            )
            SkeletonCircle(
                size = 44.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                shimmerBrush = shimmerBrush,
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
    shimmerBrush: Brush,
) {
    val cardShape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (squareCard) Modifier.aspectRatio(1f) else Modifier.height(cardHeight)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
        shape = cardShape,
    ) {
        if (thumbnailPlayOverlay) {
            Box(Modifier.fillMaxSize()) {
                LoadingShimmer(Modifier.fillMaxSize(), shimmerBrush = shimmerBrush)
                SkeletonCircle(
                    size = 44.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    shimmerBrush = shimmerBrush,
                )
                SkeletonCircle(
                    size = 44.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shimmerBrush = shimmerBrush,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LoadingShimmer(Modifier.fillMaxSize(), shimmerBrush = shimmerBrush)
                    SkeletonCircle(
                        size = 44.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shimmerBrush = shimmerBrush,
                    )
                }
                if (showStoreLabels) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SkeletonLine(widthFraction = 0.62f, shimmerBrush = shimmerBrush)
                    }
                }
                Box(Modifier.padding(start = 9.dp, end = 9.dp, bottom = 9.dp)) {
                    LoadingShimmer(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        shimmerBrush = shimmerBrush,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: Dp = 9.dp, shimmerBrush: Brush) {
    LoadingShimmer(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(999.dp)),
        shimmerBrush = shimmerBrush,
    )
}

@Composable
private fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier, shimmerBrush: Brush) {
    LoadingShimmer(
        modifier
            .size(size)
            .clip(CircleShape),
        shimmerBrush = shimmerBrush,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToRefreshContainer(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    showRefreshIndicator: Boolean = true,
    content: @Composable () -> Unit,
) {
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
    val scale = settings.posterSizeScale.coerceIn(0.82f, 1.08f)
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
                    settings = settings,
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
    val scale = settings.posterSizeScale.coerceIn(0.82f, 1.08f)
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
            gridItems(games, key = { it.id }) { game ->
                GameCard(
                    game = game,
                    favorite = game.id in favoriteIds,
                    settings = settings,
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
            StoreComingNextCarousel(
                title = stringResource(R.string.store_coming_next),
                games = comingNext,
                favoriteIds = favoriteIds,
                settings = settings,
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
    val focusBorderColor = controllerFocusBorderColor(
        active = focused && controllerActionMode,
        animate = settings.controllerBackgroundAnimations,
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
                    fontWeight = FontWeight.Bold,
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
                            focused && controllerActionMode -> focusBorderColor
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
                            controllerActionMode && handleCatalogControllerAction(
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
                    if (!controllerActionMode) {
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
                    ControllerFocusSheen(
                        visible = focused && controllerActionMode && settings.controllerBackgroundAnimations,
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
    val cardWidth = storeRailCardWidth(tvProfile, landscapeLayout)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(games, key = { storeRailGameKey(it) }) { game ->
                StoreRailGameCard(
                    game = game,
                    favorite = game.id in favoriteIds,
                    settings = settings,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreRailGameCard(
    game: GameInfo,
    favorite: Boolean,
    settings: AppSettings,
    width: Dp,
    controllerActionMode: Boolean,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(if (settings.expressiveUi) 12.dp else 8.dp)
    val actionButtonSize = 34.dp
    val focusBorderColor = controllerFocusBorderColor(
        active = focused && controllerActionMode,
        animate = settings.controllerBackgroundAnimations,
    )
    Surface(
        modifier = Modifier
            .width(width)
            .aspectRatio(1f)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    focused && controllerActionMode -> focusBorderColor
                    focused -> Color.White
                    else -> Color.White.copy(alpha = 0.08f)
                },
                shape = shape,
            )
            .onPreviewKeyEvent { event ->
                when {
                    controllerActionMode && handleCatalogControllerAction(
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
            UrlImage(game.imageUrl, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.06f)))
            if (!controllerActionMode) {
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
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    size = actionButtonSize,
                )
            }
            ControllerFocusSheen(
                visible = focused && controllerActionMode && settings.controllerBackgroundAnimations,
                cornerRadius = if (settings.expressiveUi) 12.dp else 8.dp,
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
private fun controllerFocusBorderColor(active: Boolean, animate: Boolean): Color {
    if (!active) return Color.Transparent
    val accent = MaterialTheme.colorScheme.primary
    if (!animate) return accent
    val transition = rememberInfiniteTransition(label = "controller-focus")
    val glow by transition.animateFloat(
        initialValue = 0.46f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "controller-focus-glow",
    )
    return accent.copy(alpha = glow)
}

@Composable
private fun BoxScope.ControllerFocusSheen(visible: Boolean, cornerRadius: Dp) {
    if (!visible) return
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "controller-focus-sheen")
    val travel by transition.animateFloat(
        initialValue = -0.55f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "controller-focus-sheen-travel",
    )
    Canvas(Modifier.matchParentSize().padding(1.dp)) {
        val diagonal = size.width + size.height
        val center = diagonal * travel
        val brush = Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = 0.12f),
                accent.copy(alpha = 0.32f),
                Color.White.copy(alpha = 0.96f),
                accent.copy(alpha = 0.34f),
                accent.copy(alpha = 0.12f),
            ),
            start = Offset(center - size.width * 0.42f, center - size.height * 0.42f),
            end = Offset(center + size.width * 0.42f, center + size.height * 0.42f),
        )
        drawRoundRect(
            brush = brush,
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
            style = Stroke(width = 2.4.dp.toPx()),
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
        landscapeLayout -> GameGridSpec(
            columns = landscapePosterColumnCount(
                maxWidth = maxWidth,
                horizontalSpacing = landscapeHorizontalSpacing,
                horizontalContentPadding = landscapeContentHorizontalPadding,
                handheldLayout = handheldLayout,
            ),
            cardHeight = if (compact) 188.dp else 214.dp,
            horizontalSpacing = landscapeHorizontalSpacing,
            verticalSpacing = if (handheldLayout) 16.dp else compactVerticalSpacing,
            contentPadding = PaddingValues(horizontal = landscapeContentHorizontalPadding, vertical = 4.dp),
            squareCards = handheldLayout,
        )
        compact -> GameGridSpec(
            columns = gameGridColumnCount(maxWidth, minimumPortraitColumns),
            cardHeight = 218.dp,
            horizontalSpacing = compactHorizontalSpacing,
            verticalSpacing = compactVerticalSpacing,
            contentPadding = PaddingValues(4.dp),
            squareCards = false,
        )
        else -> GameGridSpec(
            columns = gameGridColumnCount(maxWidth, minimumPortraitColumns),
            cardHeight = 246.dp,
            horizontalSpacing = compactHorizontalSpacing,
            verticalSpacing = compactVerticalSpacing,
            contentPadding = PaddingValues(4.dp),
            squareCards = false,
        )
    }
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
    settings: AppSettings,
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
    val cardShape = RoundedCornerShape(if (settings.expressiveUi) 12.dp else 8.dp)
    val launcherTile = squareCard && thumbnailPlayOverlay
    val overlayActionSize = if (launcherTile) 34.dp else 44.dp
    val overlayActionPadding = if (launcherTile) 6.dp else 8.dp
    val focusBorderColor = controllerFocusBorderColor(
        active = focused && controllerActionMode,
        animate = settings.controllerBackgroundAnimations,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (squareCard) Modifier.aspectRatio(1f) else Modifier.height(cardHeight))
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    focused && controllerActionMode -> focusBorderColor
                    focused -> Color.White
                    else -> Color.Transparent
                },
                shape = cardShape,
            )
            .onPreviewKeyEvent { event ->
                when {
                    controllerActionMode && handleCatalogControllerAction(
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
            containerColor = if (settings.expressiveUi) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f) else Panel,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (focused) 8.dp else 0.dp),
        shape = cardShape,
    ) {
        Box(
            Modifier
                .weight(1f)
                .clickable { onSelect(game) },
        ) {
            UrlImage(game.imageUrl, Modifier.fillMaxSize())
            if (thumbnailPlayOverlay) {
                if (!controllerActionMode) {
                    FavoriteIconButton(
                        favorite = favorite,
                        onClick = { onFavorite(game.id) },
                        modifier = Modifier
                            .align(if (launcherTile) Alignment.TopEnd else Alignment.BottomStart)
                            .padding(overlayActionPadding),
                        size = overlayActionSize,
                    )
                    ThumbnailPlayButton(
                        onClick = { onPlay(game) },
                        onLongClick = { onChooseStore(game) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(overlayActionPadding),
                        buttonSize = overlayActionSize,
                    )
                }
            } else {
                FavoriteIconButton(
                    favorite = favorite,
                    onClick = { onFavorite(game.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
            ControllerFocusSheen(
                visible = focused && controllerActionMode && settings.controllerBackgroundAnimations,
                cornerRadius = if (settings.expressiveUi) 12.dp else 8.dp,
            )
        }
        if (!thumbnailPlayOverlay) {
            Column(
                Modifier
                    .clickable { onSelect(game) }
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                /*
                Text(
                    game.title,
                    fontWeight = FontWeight.Bold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                */
                if (settings.showGameStoreLabels) {
                    Text(displayStoresForGame(game), color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (!thumbnailPlayOverlay) {
            Box(Modifier.padding(start = 9.dp, end = 9.dp, bottom = 9.dp)) {
                LongPressPlayButton(
                    onClick = { onPlay(game) },
                    onLongClick = { onChooseStore(game) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        ZortosPlayMark(
            modifier = Modifier.fillMaxSize().padding(buttonSize * 0.13f),
            ringColor = MaterialTheme.colorScheme.onPrimary,
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
        val stroke = (size.minDimension * 0.105f).coerceAtLeast(1f)
        val gapAngle = 13f
        listOf(0.44f, 0.30f).forEach { radiusFraction ->
            val radius = size.minDimension * radiusFraction
            drawArc(
                color = ringColor,
                startAngle = -90f + gapAngle,
                sweepAngle = 360f - gapAngle * 2f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke),
            )
        }
        val play = Path().apply {
            moveTo(size.width * 0.43f, size.height * 0.34f)
            lineTo(size.width * 0.43f, size.height * 0.66f)
            lineTo(size.width * 0.68f, size.height * 0.5f)
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
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val playFocusRequester = remember(game.id) { FocusRequester() }
    LaunchedEffect(game.id) {
        runCatching { playFocusRequester.requestFocus() }
    }
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
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clickable(onClick = {}),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Panel,
            tonalElevation = 8.dp,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
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
                        onDismiss = onDismiss,
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
                        onDismiss = onDismiss,
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
    onDismiss: () -> Unit,
    playFocusRequester: FocusRequester,
    shortHeight: Boolean,
    imageActionsOverlay: Boolean,
) {
    val description = gameDescriptionForDetails(game)
    val context = LocalContext.current
    val sideScrollState = rememberScrollState()
    val detailsSpacing = if (shortHeight) 8.dp else 10.dp
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
                .clip(RoundedCornerShape(20.dp)),
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
            FavoriteIconButton(
                favorite = favorite,
                onClick = { onFavorite(game.id) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
            if (imageActionsOverlay) {
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
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .width(126.dp)
                        .focusRequester(playFocusRequester),
                )
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
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(0.8f)) {
                        Text("Dismiss", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            .weight(1.2f)
                            .focusRequester(playFocusRequester),
                    )
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
    onDismiss: () -> Unit,
    playFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
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
                        .height(220.dp),
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
                    FavoriteIconButton(
                        favorite = favorite,
                        onClick = { onFavorite(game.id) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val description = gameDescriptionForDetails(game)
                    OwnershipStatusRow(game = game, compact = false)
                    GameGenreChips(game = game, compact = false)
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
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(0.8f)) {
                    Text("Dismiss", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        .weight(1.2f)
                        .focusRequester(playFocusRequester),
                )
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
    Surface(
        modifier = modifier
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
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
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TextPrimary else Color.Transparent,
                shape = shape,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZortosPlayMark(
                modifier = Modifier.size(26.dp),
                ringColor = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.action_play),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun variantDetailsText(variant: GameVariant): String =
    listOfNotNull(
        variant.libraryStatus?.takeIf { it.isNotBlank() }?.let(::formatGameMetadataLabel),
        variant.supportedControls.takeIf { it.isNotEmpty() }?.joinToString(", ") { formatGameMetadataLabel(it) },
        variant.lastPlayedDate?.takeIf { it.isNotBlank() }?.let { "Last played $it" },
    ).joinToString(" - ")

@Composable
private fun ImageCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.58f),
        tonalElevation = 3.dp,
    ) {
        IconButton(onClick = onClick) {
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
    Surface(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.52f),
        tonalElevation = 3.dp,
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

private fun gameDescriptionForDetails(game: GameInfo): String? =
    game.longDescription?.takeIf { it.isNotBlank() }
        ?: game.description?.takeIf { it.isNotBlank() }

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
private fun GameDescriptionDisclosure(description: String?, compact: Boolean) {
    var expanded by remember(description) { mutableStateOf(true) }
    val text = description?.takeIf { it.isNotBlank() } ?: "No description is available for this game yet."
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
        color = PanelAlt,
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
                colors = CardDefaults.cardColors(containerColor = Panel),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
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
            TextButton(onClick = onDismiss, modifier = Modifier.weight(0.85f)) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = {
                    val variant = selectedVariant ?: return@Button
                    onContinue(variant)
                },
                enabled = selectedVariant != null,
                modifier = Modifier
                    .weight(1.15f)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else PanelAlt,
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
    val audioController = remember(context) { AndroidNerdAudioController(context.applicationContext) }
    val session = state.streamSession
    val game = state.streamGame
    var streamState by remember { mutableStateOf("Preparing") }
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
    val streamReady = session?.isReadyForStream() == true
    val tvProfile = state.codecReport?.androidTvProfile == true
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
    val touchControlsVisible =
        touchInputEnabled &&
            state.settings.androidTouch.enabled &&
            !touchControlsSuppressedByPhysicalController
    val sessionStartedAtMs = remember(session?.sessionId) { System.currentTimeMillis() }
    var timerNowMs by remember(session?.sessionId) { mutableStateOf(System.currentTimeMillis()) }
    val smartSessionLimit = smartSessionLimitFor(state.subscriptionInfo, state.authSession?.user?.membershipTier)
    val buttonToneEnabled = state.settings.controllerUiSounds
    val stretchToFill = state.settings.stretchStreamToFill
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
        StreamStatsPosition.Right -> Alignment.TopEnd
    }
    var resolutionMismatchStats by remember(session?.sessionId, launchStreamSettings.resolution, launchStreamSettings.aspectRatio) { mutableStateOf(0) }
    var resolutionMismatchRestartRequested by remember(session?.sessionId, launchStreamSettings.resolution, launchStreamSettings.aspectRatio) { mutableStateOf(false) }
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
    val streamOverlayOpen = controlsOpen || exitConfirmOpen || keyboardOpen || streamGuideOpen || physicalControllerPromptOpen
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
            onSafeVideoFallbackRequired = {
                streamState = it
                viewModel.restartStreamWithSafeVideoProfile(it)
            },
            onSessionRecoveryRequired = {
                streamState = it
                viewModel.recoverStreamSession(it)
            },
            onStats = {
                streamStats = it
                viewModel.updateStreamRuntimeStats(it)
            },
        )
    }

    DisposableEffect(Unit) {
        val decor = activity?.window?.decorView
        NativeStreamInputRouter.attach(client)
        onDispose {
            if (Build.VERSION.SDK_INT >= 26) {
                decor?.releasePointerCapture()
            }
            NativeStreamInputRouter.clearUiTouchPassthroughBounds()
            NativeStreamInputRouter.clearStreamPanelTouchPassthroughBounds()
            NativeStreamInputRouter.setSystemMenuHandler(null)
            NativeStreamInputRouter.setSystemBackHandler(null)
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

    LaunchedEffect(streamReady, streamOverlayOpen, streamGuideOpen, streamGuideStep) {
        NativeStreamInputRouter.setStreamUiActive(streamReady && streamOverlayOpen)
        NativeStreamInputRouter.setSystemMenuHandler {
            openControlsForGuide()
        }
        NativeStreamInputRouter.setSystemBackHandler {
            handleStreamBack()
        }
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
    LaunchedEffect(session?.sessionId, session?.status, streamReady, launchStreamSettings) {
        if (session != null && streamReady) {
            client.start(session, launchStreamSettings)
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
        when {
            !streamReady || streamStats.resolution == null -> Unit
            mismatch == null -> resolutionMismatchStats = 0
            else -> {
                resolutionMismatchStats += 1
                if (resolutionMismatchStats >= 3 && !resolutionMismatchRestartRequested) {
                    resolutionMismatchRestartRequested = true
                    client.stop()
                    viewModel.restartStreamForResolutionMismatch(
                        actualResolution = mismatch.actualResolution,
                        expectedResolution = mismatch.expectedResolution,
                    )
                }
            }
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
            )
            if (statsVisible) {
                StreamStatsPill(
                    gameTitle = game?.title ?: "Stream",
                    streamStats = streamStats,
                    streamSettings = launchStreamSettings,
                    style = state.settings.streamStatsStyle,
                    modifier = Modifier.align(statsAlignment),
                )
            }
            if (touchControlsVisible) {
                TouchOverlay(
                    client = client,
                    touch = state.settings.androidTouch.copy(enabled = true),
                    onButtonTone = playButtonTone,
                    layoutEditing = touchLayoutEditing,
                    onLeftOffsetChange = { x, y ->
                        viewModel.updateSettings(
                            state.settings.copy(
                                androidTouch = state.settings.androidTouch.copy(leftOffsetXDp = x, leftOffsetYDp = y),
                            ),
                        )
                    },
                    onRightOffsetChange = { x, y ->
                        viewModel.updateSettings(
                            state.settings.copy(
                                androidTouch = state.settings.androidTouch.copy(rightOffsetXDp = x, rightOffsetYDp = y),
                            ),
                        )
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
                    touchControlsVisible = touchControlsVisible,
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
                        viewModel.updateSettings(state.settings.copy(stretchStreamToFill = !state.settings.stretchStreamToFill))
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
        Modifier.fillMaxSize()
    } else if (viewportAspectRatio > streamAspectRatio) {
        Modifier
            .fillMaxHeight()
            .aspectRatio(streamAspectRatio)
    } else {
        Modifier
            .fillMaxWidth()
            .aspectRatio(streamAspectRatio)
    }
    LaunchedEffect(
        settings.resolution,
        settings.aspectRatio,
        stretchToFill,
        streamAspectRatio,
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        zoomScale = 1f
        zoomOffset = Offset.Zero
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
                        }
                    },
                    update = { renderer ->
                        client.updateRendererSettings(settings, stretchToFill)
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
    touchControlsVisible: Boolean,
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
    onPhoneRumbleFallbackToggle: () -> Unit,
    onTouchLayoutEditingToggle: () -> Unit,
    onKeyboardOpen: () -> Unit,
    onEsc: () -> Unit,
    onEnter: () -> Unit,
    onBackspace: () -> Unit,
    onExit: () -> Unit,
    onTouchControlsToggle: () -> Unit,
    onMousePadToggle: () -> Unit,
    onSharpeningToggle: () -> Unit,
    onSharpeningAmountChange: (Float) -> Unit,
    onStretchToFillToggle: () -> Unit,
    onTouchScaleChange: (Float) -> Unit,
    onButtonScaleChange: (Float) -> Unit,
    onStickScaleChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onTouchEdgePaddingChange: (Float) -> Unit,
    onTouchBottomPaddingChange: (Float) -> Unit,
    onTouchLeftOffsetChange: (Float) -> Unit,
    onTouchRightOffsetChange: (Float) -> Unit,
    onButtonTone: () -> Unit,
    highlightDone: Boolean = false,
    onClose: () -> Unit,
) {
    val doneFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { doneFocusRequester.requestFocus() }
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
        tonalElevation = 6.dp,
    ) {
        LazyColumn(
            modifier = Modifier.onPreviewKeyEvent { handleVerticalDpadFocusMove(it, focusManager) },
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("Exit")
                    }
                    val doneAction = {
                        onButtonTone()
                        onClose()
                    }
                    val doneModifier = Modifier.focusRequester(doneFocusRequester)
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
                    StreamControlSwitch("Stream stats", if (statsVisible) "On" else "Off", statsVisible) {
                        onButtonTone()
                        onStatsToggle()
                    }
                    StreamControlAction("Stats style", settings.streamStatsStyle.label) {
                        onButtonTone()
                        onStatsStyleCycle()
                    }
                    StreamControlAction("Stats position", settings.streamStatsPosition.label) {
                        onButtonTone()
                        onStatsPositionCycle()
                    }
                    StreamControlSwitch("Stream sharpening", if (settings.stream.streamSharpeningEnabled) "On" else "Off", settings.stream.streamSharpeningEnabled) {
                        onButtonTone()
                        onSharpeningToggle()
                    }
                    if (settings.stream.streamSharpeningEnabled) {
                        CompactSlider("Sharpness amount", settings.stream.streamSharpeningAmount, 0f, 1f, onSharpeningAmountChange)
                    }
                    StreamControlSwitch("Stretch to fill", if (settings.stretchStreamToFill) "On" else "Off", settings.stretchStreamToFill) {
                        onButtonTone()
                        onStretchToFillToggle()
                    }
                }
            }
            item {
                StreamPanelSection("Input") {
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
                    StreamControlSwitch("Finger mouse", if (settings.androidTouch.mousePad) "On" else "Off", settings.androidTouch.mousePad) {
                        onButtonTone()
                        onMousePadToggle()
                    }
                    StreamControlSwitch("Touch controller", if (touchControlsVisible) "Visible" else "Hidden", touchControlsVisible) {
                        onButtonTone()
                        onTouchControlsToggle()
                    }
                    StreamControlSwitch("Phone rumble fallback", if (settings.phoneRumbleFallback) "On" else "Off", settings.phoneRumbleFallback) {
                        onButtonTone()
                        onPhoneRumbleFallbackToggle()
                    }
                }
            }
            item {
                StreamPanelSection("Touch Layout") {
                    StreamControlSwitch("Drag edit mode", if (touchLayoutEditing) "On" else "Off", touchLayoutEditing) {
                        onButtonTone()
                        onTouchLayoutEditingToggle()
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
    DisposableEffect(Unit) {
        onDispose {
            NativeStreamInputRouter.clearStreamPanelTouchPassthroughBounds()
        }
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
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
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
private fun StreamControlAction(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(value, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CompactSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val focusManager = LocalFocusManager.current
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text("${(local * 100).roundToInt()}%", color = TextMuted)
        }
        Slider(
            modifier = Modifier.onPreviewKeyEvent { handleVerticalDpadFocusMove(it, focusManager) },
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
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text("${local.roundToInt()} dp", color = TextMuted)
        }
        Slider(
            modifier = Modifier.onPreviewKeyEvent { handleVerticalDpadFocusMove(it, focusManager) },
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

@Composable
private fun StreamStatsPill(
    gameTitle: String,
    streamStats: StreamRuntimeStats,
    streamSettings: StreamSettings,
    style: StreamStatsStyle,
    modifier: Modifier = Modifier,
) {
    if (style == StreamStatsStyle.Compact) {
        val deviceStatus = rememberCompactStreamDeviceStatus()
        Surface(
            modifier = modifier.padding(8.dp),
            shape = RoundedCornerShape(999.dp),
            color = Panel.copy(alpha = 0.48f),
            tonalElevation = 0.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("FPS ${streamStats.fps?.toString() ?: "--"}", color = TextPrimary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text("Ping ${streamStats.pingMs?.let { "${it}ms" } ?: "--"}", color = TextPrimary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                StreamBatteryIndicator(deviceStatus)
                StreamNetworkIndicator(deviceStatus)
            }
        }
        return
    }
    Surface(
        modifier = modifier.padding(8.dp).widthIn(max = 560.dp),
        shape = RoundedCornerShape(999.dp),
        color = Panel.copy(alpha = 0.56f),
        tonalElevation = 0.dp,
    ) {
        Text(
            streamStatsDetailedLine(gameTitle, streamStats, streamSettings),
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

private data class CompactStreamDeviceStatus(
    val batteryPercent: Int? = null,
    val batteryCharging: Boolean = false,
    val networkKind: AndroidNetworkKind = AndroidNetworkKind.Unknown,
    val networkBars: Int? = null,
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
        CompactBatteryGlyph(status.batteryPercent, status.batteryCharging)
        Text(
            status.batteryPercent?.let { "$it%" } ?: "--%",
            color = compactBatteryColor(status.batteryPercent, status.batteryCharging),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactBatteryGlyph(percent: Int?, charging: Boolean) {
    val fillColor = compactBatteryColor(percent, charging)
    Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
        val capGap = size.width * 0.03f
        val capWidth = size.width * 0.08f
        val bodyWidth = size.width - capGap - capWidth
        val cornerRadius = size.height * 0.22f
        drawRoundRect(
            color = TextPrimary.copy(alpha = 0.24f),
            topLeft = Offset.Zero,
            size = Size(bodyWidth, size.height),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        )
        val inset = size.height * 0.18f
        val fillWidth = (bodyWidth - inset * 2f) * ((percent ?: 0).coerceIn(0, 100) / 100f)
        if (fillWidth > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(inset, inset),
                size = Size(fillWidth, size.height - inset * 2f),
                cornerRadius = CornerRadius(cornerRadius * 0.7f, cornerRadius * 0.7f),
            )
        }
        drawRoundRect(
            color = TextPrimary.copy(alpha = 0.58f),
            topLeft = Offset(bodyWidth + capGap, size.height * 0.32f),
            size = Size(capWidth, size.height * 0.36f),
            cornerRadius = CornerRadius(capWidth, capWidth),
        )
    }
}

private fun compactBatteryColor(percent: Int?, charging: Boolean): Color = when {
    charging -> Green
    percent == null -> TextMuted
    percent <= 15 -> Color(0xffff8d8d)
    percent <= 30 -> Color(0xffffc95a)
    else -> TextPrimary
}

@Composable
private fun StreamNetworkIndicator(status: CompactStreamDeviceStatus) {
    val bars = status.networkBars?.coerceIn(0, 4)
    val description = "${status.networkKind.label} signal ${bars?.toString() ?: "unknown"} bars"
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            status.networkKind.label,
            color = if (status.networkKind == AndroidNetworkKind.None) TextMuted else TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        CompactSignalBars(bars)
    }
}

@Composable
private fun CompactSignalBars(bars: Int?) {
    val activeBars = bars?.coerceIn(0, 4) ?: 0
    Canvas(Modifier.size(width = 18.dp, height = 14.dp)) {
        val gap = size.width * 0.1f
        val barWidth = (size.width - gap * 3f) / 4f
        repeat(4) { index ->
            val barHeight = size.height * (0.32f + index * 0.16f)
            val x = index * (barWidth + gap)
            val y = size.height - barHeight
            drawRoundRect(
                color = if (index < activeBars) TextPrimary else TextPrimary.copy(alpha = 0.22f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth * 0.45f, barWidth * 0.45f),
            )
        }
    }
}

private fun streamStatsDetailedLine(
    gameTitle: String,
    streamStats: StreamRuntimeStats,
    streamSettings: StreamSettings,
): String {
    val fps = streamStats.fps?.let { "$it Fps" } ?: "${streamSettings.fps} Fps"
    val bitrate = formatRuntimeBitrate(streamStats.bitrateKbps)
    val resolution = streamStats.resolution
        ?.let(::formatRuntimeResolution)
        ?: formatRuntimeResolution(normalizeStreamResolutionForAspect(streamSettings.resolution, streamSettings.aspectRatio))
    val codec = streamStats.codec?.takeIf { it.isNotBlank() } ?: streamSettings.codec.name
    val ping = streamStats.pingMs?.let { "Ping ${it}ms" } ?: "Ping --"
    return listOf(
        gameTitle.ifBlank { "Game" },
        "$fps @ $bitrate",
        resolution,
        streamSettings.aspectRatio,
        codec,
        ping,
    ).joinToString(" • ")
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
                    playbackKey = "${session?.sessionId.orEmpty()}:${state.queueAdPlaybackEpoch}",
                    compact = useLandscapeAdLayout,
                    onMinimize = viewModel::minimizeStreamLaunch,
                    onCancel = viewModel::stopStream,
                    modifier = Modifier
                        .fillMaxWidth(if (useLandscapeAdLayout) 0.72f else 1f)
                        .widthIn(max = if (useLandscapeAdLayout) 900.dp else 620.dp),
                    playerModifier = if (useLandscapeAdLayout) {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    },
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
    playerModifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Panel.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
    ) {
        Column(
            Modifier.padding(if (compact) 14.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Advertisement",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        game?.title ?: "Starting stream",
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                queuePosition?.let {
                    Text(
                        it.toString(),
                        color = queueUrgencyColor(it),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                        maxLines = 1,
                    )
                }
            }
            QueueAdPlayer(
                adId = ad.adId,
                url = mediaUrl,
                playbackKey = playbackKey,
                modifier = playerModifier,
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
            AnimatedQueueStatusText(
                queueCopy = queueCopy,
                queuePosition = queuePosition,
                compact = true,
            )
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
            error?.let {
                Text(it, color = Color(0xffff9f9f), textAlign = TextAlign.Center)
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
    onLeftOffsetChange: (Float, Float) -> Unit,
    onRightOffsetChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val opacity = touch.opacity
    val layoutScale = touch.scale
    val buttonScale = touch.buttonScale
    val stickScale = touch.stickScale
    val leftOffsetX = touch.leftOffsetXDp.dp
    val leftOffsetY = touch.leftOffsetYDp.dp
    val rightOffsetX = touch.rightOffsetXDp.dp
    val rightOffsetY = touch.rightOffsetYDp.dp

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
            if (landscape) {
                LandscapeTouchControls(
                    client = client,
                    opacity = opacity,
                    layoutScale = layoutScale,
                    buttonScale = buttonScale,
                    stickScale = stickScale,
                    viewportHeight = maxHeight,
                    layoutEditing = layoutEditing,
                    leftOffsetX = leftOffsetX,
                    leftOffsetY = leftOffsetY,
                    rightOffsetX = rightOffsetX,
                    rightOffsetY = rightOffsetY,
                    onButtonTone = onButtonTone,
                    onLeftOffsetChange = onLeftOffsetChange,
                    onRightOffsetChange = onRightOffsetChange,
                )
            } else {
                PortraitTouchControls(
                    client = client,
                    opacity = opacity,
                    layoutScale = layoutScale,
                    buttonScale = buttonScale,
                    stickScale = stickScale,
                    layoutEditing = layoutEditing,
                    leftOffsetX = leftOffsetX,
                    leftOffsetY = leftOffsetY,
                    rightOffsetX = rightOffsetX,
                    rightOffsetY = rightOffsetY,
                    onButtonTone = onButtonTone,
                    onLeftOffsetChange = onLeftOffsetChange,
                    onRightOffsetChange = onRightOffsetChange,
                )
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
    leftOffsetX: Dp,
    leftOffsetY: Dp,
    rightOffsetX: Dp,
    rightOffsetY: Dp,
    onButtonTone: () -> Unit,
    onLeftOffsetChange: (Float, Float) -> Unit,
    onRightOffsetChange: (Float, Float) -> Unit,
) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        TouchControlGroup(
            id = "portrait-left",
            layoutEditing = layoutEditing,
            offsetX = leftOffsetX,
            offsetY = leftOffsetY,
            onOffsetChange = onLeftOffsetChange,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamepadButton("LB", 0x0100, client, opacity, 48.dp * buttonScale * layoutScale, onButtonTone)
                    GamepadTriggerButton("LT", left = true, client = client, opacity = opacity, size = 48.dp * buttonScale * layoutScale, onPressTone = onButtonTone)
                }
                Spacer(Modifier.height(44.dp * buttonScale * layoutScale))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    StickWithThumbButton(
                        stickLabel = "L",
                        thumbLabel = "L3",
                        thumbMask = GamepadButtonMapping.LEFT_THUMB,
                        client = client,
                        opacity = opacity,
                        diameter = 116.dp * stickScale * layoutScale,
                        buttonScale = buttonScale * layoutScale,
                        onButtonTone = onButtonTone,
                        onChange = client::setVirtualLeftStick,
                    )
                    DpadCluster(client, opacity, buttonScale * layoutScale, onButtonTone)
                }
            }
        }
        TouchControlGroup(
            id = "portrait-right",
            layoutEditing = layoutEditing,
            offsetX = rightOffsetX,
            offsetY = rightOffsetY,
            onOffsetChange = onRightOffsetChange,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamepadTriggerButton("RT", left = false, client = client, opacity = opacity, size = 48.dp * buttonScale * layoutScale, onPressTone = onButtonTone)
                    GamepadButton("RB", 0x0200, client, opacity, 48.dp * buttonScale * layoutScale, onButtonTone)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamepadButton("View", 0x0020, client, opacity, 44.dp * buttonScale * layoutScale, onButtonTone)
                    GamepadButton("Menu", 0x0010, client, opacity, 44.dp * buttonScale * layoutScale, onButtonTone)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    StickWithThumbButton(
                        stickLabel = "R",
                        thumbLabel = "R3",
                        thumbMask = GamepadButtonMapping.RIGHT_THUMB,
                        client = client,
                        opacity = opacity,
                        diameter = 104.dp * stickScale * layoutScale,
                        buttonScale = buttonScale * layoutScale,
                        onButtonTone = onButtonTone,
                        onChange = client::setVirtualRightStick,
                    )
                    FaceButtonCluster(client, opacity, buttonScale * layoutScale, onButtonTone)
                }
            }
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
    leftOffsetX: Dp,
    leftOffsetY: Dp,
    rightOffsetX: Dp,
    rightOffsetY: Dp,
    onButtonTone: () -> Unit,
    onLeftOffsetChange: (Float, Float) -> Unit,
    onRightOffsetChange: (Float, Float) -> Unit,
) {
    val controlScale = buttonScale * layoutScale
    val topControlClearance = landscapeTouchTopControlClearanceDp(viewportHeight.value, controlScale).dp
    TouchControlGroup(
        id = "landscape-top-left",
        layoutEditing = layoutEditing,
        offsetX = leftOffsetX,
        offsetY = leftOffsetY + topControlClearance,
        onOffsetChange = onLeftOffsetChange,
        modifier = Modifier.align(Alignment.TopStart),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GamepadButton("LB", 0x0100, client, opacity, 46.dp * controlScale, onButtonTone)
            GamepadTriggerButton("LT", left = true, client = client, opacity = opacity, size = 50.dp * controlScale, onPressTone = onButtonTone)
        }
    }
    TouchControlGroup(
        id = "landscape-top-center",
        layoutEditing = false,
        offsetX = 0.dp,
        offsetY = topControlClearance,
        onOffsetChange = { _, _ -> },
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GamepadPillButton("View", 0x0020, client, opacity, width = 76.dp * controlScale, height = 42.dp * controlScale, onPressTone = onButtonTone)
            GamepadPillButton("Start", 0x0010, client, opacity, width = 84.dp * controlScale, height = 42.dp * controlScale, onPressTone = onButtonTone)
        }
    }
    TouchControlGroup(
        id = "landscape-top-right",
        layoutEditing = layoutEditing,
        offsetX = rightOffsetX,
        offsetY = rightOffsetY + topControlClearance,
        onOffsetChange = onRightOffsetChange,
        modifier = Modifier.align(Alignment.TopEnd),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GamepadTriggerButton("RT", left = false, client = client, opacity = opacity, size = 50.dp * controlScale, onPressTone = onButtonTone)
            GamepadButton("RB", 0x0200, client, opacity, 46.dp * controlScale, onButtonTone)
        }
    }
    TouchControlGroup(
        id = "landscape-bottom-left",
        layoutEditing = layoutEditing,
        offsetX = leftOffsetX,
        offsetY = leftOffsetY,
        onOffsetChange = onLeftOffsetChange,
        modifier = Modifier.align(Alignment.BottomStart),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            StickWithThumbButton(
                stickLabel = "L",
                thumbLabel = "L3",
                thumbMask = GamepadButtonMapping.LEFT_THUMB,
                client = client,
                opacity = opacity,
                diameter = 112.dp * stickScale * layoutScale,
                buttonScale = controlScale * 0.88f,
                onButtonTone = onButtonTone,
                onChange = client::setVirtualLeftStick,
            )
            DpadCluster(client, opacity, controlScale * 0.88f, onButtonTone)
        }
    }
    TouchControlGroup(
        id = "landscape-bottom-right",
        layoutEditing = layoutEditing,
        offsetX = rightOffsetX,
        offsetY = rightOffsetY,
        onOffsetChange = onRightOffsetChange,
        modifier = Modifier.align(Alignment.BottomEnd),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
            FaceButtonCluster(client, opacity, controlScale * 0.9f, onButtonTone)
            StickWithThumbButton(
                stickLabel = "R",
                thumbLabel = "R3",
                thumbMask = GamepadButtonMapping.RIGHT_THUMB,
                client = client,
                opacity = opacity,
                diameter = 98.dp * stickScale * layoutScale,
                buttonScale = controlScale * 0.9f,
                onButtonTone = onButtonTone,
                onChange = client::setVirtualRightStick,
            )
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
                    .pointerInput(offsetX, offsetY) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaXDp = with(density) { dragAmount.x.toDp().value }
                            val deltaYDp = with(density) { dragAmount.y.toDp().value }
                            onOffsetChange(
                                (offsetX.value + deltaXDp).coerceIn(-220f, 220f),
                                (offsetY.value + deltaYDp).coerceIn(-160f, 160f),
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
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val accent = MaterialTheme.colorScheme.primary
    val idleSurface = MaterialTheme.colorScheme.surfaceVariant

    DisposableEffect(client, onChange) {
        onDispose {
            onChange(0f, 0f)
        }
    }

    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(idleSurface.copy(alpha = opacity * 0.72f))
            .border(1.dp, accent.copy(alpha = opacity), CircleShape)
            .pointerInput(client, onChange) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.pressed }
                        val maxRadius = min(size.width, size.height) * 0.34f
                        if (change == null) {
                            if (knobOffset != Offset.Zero) {
                                onChange(0f, 0f)
                                knobOffset = Offset.Zero
                            }
                            continue
                        }
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val clamped = clampStickOffset(change.position - center, maxRadius)
                        onChange(
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
        Box(
            Modifier
                .size(diameter * 0.44f)
                .graphicsLayer {
                    translationX = knobOffset.x
                    translationY = knobOffset.y
                }
                .clip(CircleShape)
                .background(accent.copy(alpha = opacity))
                .border(1.dp, Color.White.copy(alpha = opacity * 0.65f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun FaceButtonCluster(client: NativeStreamClient, opacity: Float, scale: Float, onButtonTone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GamepadButton("Y", 0x8000, client, opacity, 54.dp * scale, onButtonTone)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GamepadButton("X", 0x4000, client, opacity, 54.dp * scale, onButtonTone)
            Spacer(Modifier.size(54.dp * scale))
            GamepadButton("B", 0x2000, client, opacity, 54.dp * scale, onButtonTone)
        }
        GamepadButton("A", 0x1000, client, opacity, 54.dp * scale, onButtonTone)
    }
}

@Composable
private fun DpadCluster(client: NativeStreamClient, opacity: Float, scale: Float, onButtonTone: () -> Unit) {
    val buttonSize = 54.dp * scale
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GamepadButton("↑", 0x0001, client, opacity, buttonSize, onButtonTone)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GamepadButton("←", 0x0004, client, opacity, buttonSize, onButtonTone)
            Spacer(Modifier.size(buttonSize))
            GamepadButton("→", 0x0008, client, opacity, buttonSize, onButtonTone)
        }
        GamepadButton("↓", 0x0002, client, opacity, buttonSize, onButtonTone)
    }
}

@Composable
private fun GamepadTriggerButton(
    label: String,
    left: Boolean,
    client: NativeStreamClient,
    opacity: Float,
    size: androidx.compose.ui.unit.Dp,
    onPressTone: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val idleSurface = MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier
            .width(size * 1.24f)
            .height(size * 0.78f)
            .clip(RoundedCornerShape(999.dp))
            .background((if (pressed) accent else idleSurface).copy(alpha = opacity))
            .border(1.dp, accent.copy(alpha = opacity), RoundedCornerShape(999.dp))
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
        Text(label, fontWeight = FontWeight.Bold, color = if (pressed) MaterialTheme.colorScheme.onPrimary else TextPrimary)
    }
    DisposableEffect(client, left) {
        onDispose {
            client.setVirtualTrigger(left, false)
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
    var pressed by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val idleSurface = MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background((if (pressed) accent else idleSurface).copy(alpha = opacity))
            .border(1.dp, accent.copy(alpha = opacity), CircleShape)
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
        Text(label, fontWeight = FontWeight.Bold, color = if (pressed) MaterialTheme.colorScheme.onPrimary else TextPrimary)
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
    var pressed by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val idleSurface = MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background((if (pressed) accent else idleSurface).copy(alpha = opacity))
            .border(1.dp, accent.copy(alpha = opacity), RoundedCornerShape(999.dp))
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
        Text(
            label,
            fontWeight = FontWeight.Bold,
            color = if (pressed) MaterialTheme.colorScheme.onPrimary else TextPrimary,
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
    val controlColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(if (compact) TopBarCompactControlHeight else 40.dp),
            shape = controlShape,
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
    val filterControlColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(if (compact) TopBarCompactControlHeight else 36.dp),
            shape = filterControlShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = filterControlColor,
                contentColor = TextPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text(if (selectedIds.isEmpty()) "Filters" else "Filters ${selectedIds.size}", maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (option.id in selectedIds) "✓" else "", modifier = Modifier.width(24.dp))
                            Text(option.label)
                        }
                    },
                    onClick = { onToggle(option.id) },
                )
            }
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
    val scope = rememberCoroutineScope()
    fun selectZoneAt(index: Int) {
        val next = zones.getOrNull(index) ?: return
        onSelectZone(next.zoneId)
        scope.launch {
            zoneListState.animateScrollToItem(index)
        }
    }
    LaunchedEffect(state.printedWasteLoading, state.printedWasteError, zones.size) {
        if (!state.printedWasteLoading && state.printedWasteError == null && zones.isNotEmpty()) {
            runCatching { zoneListFocusRequester.requestFocus() }
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
            LazyColumn(
                state = zoneListState,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(zoneListFocusRequester)
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
                                        false
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
                    PrintedWasteZoneRow(
                        zoneOption = zoneOption,
                        selected = zoneOption.zoneId == selectedZoneId,
                        onClick = { onSelectZone(zoneOption.zoneId) },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            OutlinedButton(onClick = onDefault, modifier = Modifier.weight(1f)) {
                Text("Default", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(
                onClick = onLaunch,
                enabled = !state.printedWasteLoading && selectedZone != null,
                modifier = Modifier.weight(1f),
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
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Recommended: ${zoneOption.zoneId}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    zoneOption.pingMs?.let { "${it}ms" },
                    "Queue ${zoneOption.zone.QueuePosition}",
                    zoneOption.zone.eta?.let { formatPrintedWasteWait(it) },
                ).joinToString(" · "),
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PrintedWasteZoneRow(
    zoneOption: PrintedWasteZoneOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val zone = zoneOption.zone
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else PanelAlt,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(zoneOption.zoneId, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else TextPrimary)
                Text(regionLabel(zone.Region), color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text(zoneOption.pingMs?.let { "${it}ms" } ?: "--", color = zoneOption.pingMs?.let(::pingColor) ?: TextMuted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text("Q ${zone.QueuePosition}", color = queueColor(zone.QueuePosition), fontWeight = FontWeight.Bold)
            zone.eta?.let {
                Spacer(Modifier.width(10.dp))
                Text(formatPrintedWasteWait(it), color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
        scheme == "content" || scheme == "android.resource" -> uri
        scheme == "file" -> uri.path?.let(::File)?.takeIf { it.isFile && it.canRead() } ?: uri
        scheme.isBlank() && key.startsWith("/") -> File(key).takeIf { it.isFile && it.canRead() }
        else -> uri
    }
}

@Composable
internal fun UrlImage(url: String?, modifier: Modifier = Modifier, fallbackUrl: String? = null) {
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
                    contentScale = ContentScale.Crop,
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
private fun LoadingShimmer(modifier: Modifier = Modifier, shimmerBrush: Brush = rememberLoadingShimmerBrush()) {
    Box(
        modifier
            .background(Color(0xff0d1216))
            .background(shimmerBrush),
    )
}

@Composable
private fun rememberLoadingShimmerBrush(label: String = "loading-shimmer"): Brush {
    val transition = rememberInfiniteTransition(label = label)
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
        ),
        label = "$label-offset",
    )
    val base = Color(0xff0d1216)
    return Brush.linearGradient(
        colors = listOf(
            base,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            base,
        ),
        start = Offset(-720f + shimmer * 1440f, -120f),
        end = Offset(-240f + shimmer * 1440f, 520f),
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
