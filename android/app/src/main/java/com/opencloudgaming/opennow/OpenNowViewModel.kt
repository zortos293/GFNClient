package com.opencloudgaming.opennow

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class AppPage {
    Home,
    Library,
    Settings,
    Stream,
}

enum class SettingsRouteTarget {
    General,
    Stream,
}

private const val ANDROID_UPDATE_LAUNCH_CHECK_DELAY_MS = 5_000L
internal const val ANDROID_UPDATE_PERIODIC_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
private const val ANDROID_UPDATE_STREAMING_RETRY_DELAY_MS = 30_000L
private const val DEBUG_EVENT_LIMIT = 140
private const val DEBUG_EVENT_MESSAGE_LIMIT = 640
private const val DEBUG_PAYLOAD_LIMIT = 12
private const val DEBUG_PAYLOAD_BODY_LIMIT = 8_000
private const val LOGIN_PHASE_GETTING_TOKENS = "Getting sign-in tokens"
private const val STREAM_RUNTIME_STATS_EVENT_INTERVAL_MS = 30_000L

private data class DebugLogEvent(
    val timestampMs: Long,
    val category: String,
    val message: String,
)

private data class DebugPayloadEvent(
    val timestampMs: Long,
    val operation: String,
    val method: String,
    val url: String,
    val statusCode: Int,
    val requestBody: String,
    val body: String,
)

private data class TimedStreamRuntimeStats(
    val capturedAtMs: Long,
    val sessionId: String?,
    val stats: StreamRuntimeStats,
)

data class ActiveSessionDecision(
    val activeSession: ActiveSessionInfo,
    val requestedGameTitle: String,
)

@Immutable
data class DiagnosticShareState(
    val awaitingConsent: Boolean = false,
    val uploading: Boolean = false,
    val pasteUrl: String? = null,
    val clipboardSummary: String? = null,
    val error: String? = null,
)

private data class PendingActiveSessionLaunch(
    val game: GameInfo,
    val launchAppId: String,
    val baseUrl: String,
    val settings: StreamSettings,
    val accountLinked: Boolean,
    val activeSession: ActiveSessionInfo,
    val returnPage: AppPage,
)

@Immutable
data class OpenNowUiState(
    val initializing: Boolean = false,
    val page: AppPage = AppPage.Home,
    val authSession: AuthSession? = null,
    val providers: List<LoginProvider> = listOf(defaultProvider()),
    val selectedProvider: LoginProvider = defaultProvider(),
    val savedAccounts: List<SavedAccount> = emptyList(),
    val subscriptionInfo: SubscriptionInfo? = null,
    val accountConnectors: List<AccountConnector> = emptyList(),
    val loadingAccountConnectors: Boolean = false,
    val connectorActionStore: String? = null,
    val regions: List<StreamRegion> = emptyList(),
    val games: List<GameInfo> = emptyList(),
    val libraryGames: List<GameInfo> = emptyList(),
    val queuedGameKeys: List<String> = emptyList(),
    val catalogResult: CatalogBrowseResult = CatalogBrowseResult(emptyList()),
    val catalogSearch: String = "",
    val librarySearch: String = "",
    val catalogSortId: String = "relevance",
    val catalogFilterIds: List<String> = emptyList(),
    val libraryFilterIds: List<String> = emptyList(),
    val loadingGames: Boolean = false,
    val settingsRefreshing: Boolean = false,
    val settingsRouteTarget: SettingsRouteTarget? = null,
    val settings: AppSettings = AppSettings(),
    val androidTvProfile: Boolean = false,
    val codecReport: RuntimeCodecReport? = null,
    val selectedGame: GameInfo? = null,
    val activeSession: ActiveSessionInfo? = null,
    val activeSessionDecision: ActiveSessionDecision? = null,
    val streamSession: SessionInfo? = null,
    val activeStreamSettings: StreamSettings? = null,
    val streamGame: GameInfo? = null,
    val streamLaunchMinimized: Boolean = false,
    val streamReturnPage: AppPage? = null,
    val launchPhase: String = "",
    val queuePosition: Int? = null,
    val queueAdActiveId: String? = null,
    val streamStatus: String = "idle",
    val error: String? = null,
    val deviceLoginPrompt: DeviceLoginPrompt? = null,
    val pendingStoreChoiceGame: GameInfo? = null,
    val pendingPrintedWasteGame: GameInfo? = null,
    val printedWasteQueue: Map<String, PrintedWasteZone> = emptyMap(),
    val printedWasteMapping: Map<String, PrintedWasteServerMappingEntry> = emptyMap(),
    val printedWastePings: Map<String, Long?> = emptyMap(),
    val printedWasteLoading: Boolean = false,
    val printedWasteError: String? = null,
    val androidUpdate: AndroidUpdateState = AndroidUpdateState(),
    val dismissedAndroidUpdateNoticeKey: String? = null,
    val androidPictureInPictureActive: Boolean = false,
    val diagnosticShare: DiagnosticShareState = DiagnosticShareState(),
    val localTvConnector: LocalTvConnectorState = LocalTvConnectorState(),
    val remoteStreamMenuRequestToken: Int = 0,
    val remoteStatsToggleRequestToken: Int = 0,
)

internal fun OpenNowUiState.isAndroidUpdateCheckBlockedByStream(): Boolean =
    streamStatus != "idle" || streamSession != null || activeStreamSettings != null

class OpenNowViewModel(application: Application) : AndroidViewModel(application) {
    private val openNowApplication = application as OpenNowApplication
    private val http: OkHttpClient = openNowApplication.httpClient
    private val settingsStore = SettingsStore(application)
    private val authStore = openNowApplication.authStore
    private val authRepository = openNowApplication.authRepository
    private val catalogRepository = GfnCatalogRepository(http)
    private val catalogCacheStore = CatalogCacheStore(application)
    private val queuedGameStore = QueuedGameStore(application)
    private val subscriptionRepository = GfnSubscriptionRepository(http)
    private val accountConnectorRepository = GfnAccountConnectorRepository(http)
    private val printedWasteRepository = PrintedWasteRepository(http)
    private val sessionRepository = GfnSessionRepository(
        authStore = authStore,
        http = http,
        physicalDisplayResolutionProvider = { application.physicalStreamDisplayResolution() },
        diagnosticsSink = { response -> recordSessionDiagnosticResponse(response) },
    )
    private val appUpdater = AndroidAppUpdater(application, http)
    private val androidUpdateNoticeStore = AndroidUpdateNoticeStore(application)
    private val localTvConnector = openNowApplication.localTvConnector
    private val queueAdReportMutex = Mutex()
    private val accountConnectorRefreshMutex = Mutex()
    private val runtimeResolutionNoticeKeys = mutableSetOf<String>()
    private val debugEventsLock = Any()
    private val debugEvents = ArrayDeque<DebugLogEvent>()
    private val debugPayloadsLock = Any()
    private val debugPayloads = ArrayDeque<DebugPayloadEvent>()
    private val authRestoreMutex = Mutex()
    @Volatile
    private var latestStreamRuntimeStats: TimedStreamRuntimeStats? = null
    private var lastRuntimeStatsEventAtMs: Long = 0L
    private var deviceRecommendation: AndroidDeviceRecommendation? = null
    private val settingsDiagnosticTapTimes = ArrayDeque<Long>()

    private val initialAuthSession = authStore.activeSession()
    private val androidTvProfile = isAndroidTvProfile(application)
    private val initialSettings = settingsStore.settings.value.let { current ->
        if (androidTvProfile && current.tvLayoutProfileVersion < TV_LAYOUT_PROFILE_VERSION) {
            settingsStore.update { saved ->
                saved.copy(
                    // 36dp on every edge consumed 144 physical pixels per axis at the
                    // common TV density. Migrate only the legacy default; preserve custom values.
                    tvSafeAreaPaddingDp = if (saved.tvSafeAreaPaddingDp == 36f) 16f else saved.tvSafeAreaPaddingDp,
                    tvLayoutProfileVersion = TV_LAYOUT_PROFILE_VERSION,
                )
            }
            settingsStore.settings.value
        } else {
            current
        }
    }
    private val _state = MutableStateFlow(
        OpenNowUiState(
            page = defaultLaunchAppPage(initialSettings),
            authSession = initialAuthSession,
            providers = initialProviders(initialAuthSession),
            selectedProvider = authStore.state.value.selectedProvider ?: initialAuthSession?.provider ?: defaultProvider(),
            savedAccounts = authStore.state.value.sessions.map { session -> session.toSavedAccount() },
            loadingGames = initialAuthSession != null,
            settings = initialSettings,
            androidTvProfile = androidTvProfile,
            androidUpdate = appUpdater.state.value,
            dismissedAndroidUpdateNoticeKey = androidUpdateNoticeStore.dismissedKey(),
            queuedGameKeys = queuedGameStore.load(),
        ),
    )
    val state: StateFlow<OpenNowUiState> = _state.asStateFlow()

    private var gamesJob: Job? = null
    private var launchJob: Job? = null
    private var activeSubscriptionJob: Job? = null
    private var pendingActiveSessionLaunch: PendingActiveSessionLaunch? = null
    private var loginJob: Job? = null
    private var androidUpdateJob: Job? = null
    private var androidUpdateAutoJob: Job? = null
    private var settingsRefreshJob: Job? = null
    private var authRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { next ->
                OpenNowAnalytics.applyOptOut(!next.analyticsSharingEnabled)
                _state.update { it.copy(settings = next) }
            }
        }
        if (androidTvProfile) {
            viewModelScope.launch {
                settingsStore.settings
                    .map { it.localTvRemoteEnabled }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        if (enabled) localTvConnector.startHosting() else localTvConnector.stopHosting()
                    }
            }
        }
        viewModelScope.launch {
            localTvConnector.state.collect { next ->
                _state.update { it.copy(localTvConnector = next) }
            }
        }
        viewModelScope.launch {
            localTvConnector.launchRequests.collect { request ->
                if (!state.value.androidTvProfile) return@collect
                val allGames = state.value.games + state.value.libraryGames
                val game = allGames.firstOrNull { game ->
                    game.id == request.gameId ||
                        game.uuid == request.gameId ||
                        game.launchAppId == request.gameId ||
                        game.variants.any { it.id == request.gameId }
                } ?: GameInfo(
                    id = request.gameId,
                    uuid = request.gameId,
                    launchAppId = request.gameId.takeIf { it.all(Char::isDigit) },
                    title = request.title ?: "Game ${request.gameId}",
                    variants = listOf(GameVariant(id = request.gameId, store = "Unknown")),
                )
                recordDebugEvent("tv-connector", "Accepted encrypted local launch game=${game.title}")
                play(game)
            }
        }
        viewModelScope.launch {
            localTvConnector.signInRequests.collect { transferredSession ->
                if (!state.value.androidTvProfile) return@collect
                acceptLocalTvSignIn(transferredSession)
            }
        }
        viewModelScope.launch {
            localTvConnector.remoteRequests.collect { request ->
                if (!state.value.androidTvProfile) return@collect
                handleLocalTvRemoteRequest(request)
            }
        }
        viewModelScope.launch {
            appUpdater.state.collect { next ->
                _state.update { it.copy(androidUpdate = next) }
            }
        }
        viewModelScope.launch {
            state
                .map { it.isAndroidUpdateCheckBlockedByStream() to it.androidUpdate.status }
                .distinctUntilChanged()
                .collect { (blocked, updateStatus) ->
                    if (blocked && updateStatus == AndroidUpdateStatus.Checking) {
                        cancelAndroidUpdateCheckForStreaming()
                    }
                }
        }
        if (appUpdater.state.value.apkUpdatesAllowed) {
            startAndroidUpdateAutoChecks()
        }
        initialize()
    }

    private fun recordDebugEvent(category: String, message: String) {
        val oneLineMessage = message
            .lineSequence()
            .joinToString(" ") { it.trim() }
            .take(DEBUG_EVENT_MESSAGE_LIMIT)
        val event = DebugLogEvent(
            timestampMs = System.currentTimeMillis(),
            category = category,
            message = oneLineMessage,
        )
        synchronized(debugEventsLock) {
            debugEvents.addLast(event)
            while (debugEvents.size > DEBUG_EVENT_LIMIT) {
                debugEvents.removeFirst()
            }
        }
        Log.d(OPENNOW_DEBUG_LOG_TAG, "${event.category}: ${event.message}")
    }

    private fun debugEventSnapshot(): List<DebugLogEvent> =
        synchronized(debugEventsLock) { debugEvents.toList() }

    private fun recordSessionDiagnosticResponse(response: GfnSessionDiagnosticResponse) {
        val sanitizedBody = sanitizeDiagnosticLogPayload(response.responseBody, DEBUG_PAYLOAD_BODY_LIMIT)
        val event = DebugPayloadEvent(
            timestampMs = System.currentTimeMillis(),
            operation = response.operation,
            method = response.method,
            url = response.url,
            statusCode = response.statusCode,
            requestBody = response.requestBody
                .takeIf { it.isNotBlank() }
                ?.let { sanitizeDiagnosticLogPayload(it, DEBUG_PAYLOAD_BODY_LIMIT) }
                .orEmpty(),
            body = sanitizedBody,
        )
        synchronized(debugPayloadsLock) {
            debugPayloads.addLast(event)
            while (debugPayloads.size > DEBUG_PAYLOAD_LIMIT) {
                debugPayloads.removeFirst()
            }
        }
        Log.d(
            OPENNOW_DEBUG_LOG_TAG,
            "gfn-json: ${response.operation} ${response.method} http=${response.statusCode} requestBytes=${response.requestBody.length} responseBytes=${response.responseBody.length} captured=${sanitizedBody.length} host=${hostForDebug(response.url)}",
        )
    }

    private fun debugPayloadSnapshot(): List<DebugPayloadEvent> =
        synchronized(debugPayloadsLock) { debugPayloads.toList() }

    private fun defaultLaunchAppPage(settings: AppSettings = settingsStore.settings.value): AppPage =
        when (settings.launchPage) {
            AppLaunchPage.Store -> AppPage.Home
            AppLaunchPage.Library -> AppPage.Library
        }

    fun initialize() {
        viewModelScope.launch {
            val codecReport = withContext(Dispatchers.Default) {
                CodecProbe.report(getApplication())
            }
            val recommendation = recommendedAndroidStreamProfile(getApplication(), codecReport)
            deviceRecommendation = recommendation
            val currentSettings = settingsStore.settings.value
            if (
                currentSettings.streamPreset == StreamPreset.Recommended &&
                currentSettings.stream != recommendation.stream
            ) {
                settingsStore.update { settings ->
                    if (settings.streamPreset == StreamPreset.Recommended) {
                        settings.copy(stream = recommendation.stream)
                    } else {
                        settings
                    }
                }
            }
            _state.update {
                it.copy(
                    codecReport = codecReport,
                    settings = settingsStore.settings.value,
                    initializing = false,
                )
            }
            val restoreResult = restoreAuthSession()
            val providers = runCatching { authRepository.loginProviders() }.getOrDefault(listOf(defaultProvider()))
            val restored = restoreResult.getOrNull()
            val activeSession = restored ?: authStore.activeSession()
            val selected = activeSession?.provider ?: authStore.state.value.selectedProvider ?: providers.firstOrNull() ?: defaultProvider()
            val restoreError = restoreResult.exceptionOrNull()?.message?.takeIf { activeSession == null }
            _state.update {
                it.copy(
                    providers = providers,
                    selectedProvider = selected,
                    authSession = activeSession,
                    savedAccounts = authStore.state.value.sessions.map { session -> session.toSavedAccount() },
                    initializing = false,
                    launchPhase = "",
                    loadingGames = if (activeSession == null) false else it.loadingGames,
                    games = if (activeSession == null) emptyList() else it.games,
                    libraryGames = if (activeSession == null) emptyList() else it.libraryGames,
                    catalogResult = if (activeSession == null) CatalogBrowseResult(emptyList()) else it.catalogResult,
                    error = restoreError ?: it.error,
                )
            }
            if (activeSession != null) {
                refreshAfterAuth(activeSession)
            }
        }
    }

    private fun initialProviders(activeSession: AuthSession?): List<LoginProvider> =
        listOfNotNull(authStore.state.value.selectedProvider, activeSession?.provider, defaultProvider())
            .distinctBy { provider -> provider.code.uppercase(Locale.US) }

    private suspend fun restoreAuthSession(throwOnRefreshFailure: Boolean = false): Result<AuthSession?> =
        authRestoreMutex.withLock {
            runCatching {
                authRepository.restore(
                    throwOnRefreshFailure = throwOnRefreshFailure,
                    removeExpiredSessionOnFailure = !throwOnRefreshFailure,
                )
            }
        }

    fun refreshAuthSessionIfNeeded() {
        if (authRefreshJob?.isActive == true) return
        val expectedUserId = state.value.authSession?.user?.userId ?: return
        authRefreshJob = viewModelScope.launch {
            try {
                val result = restoreAuthSession(throwOnRefreshFailure = true)
                val refreshed = result.getOrNull()
                if (refreshed != null) {
                    val tokenChanged = refreshed.tokens != state.value.authSession?.tokens
                    _state.update { current ->
                        if (current.authSession?.user?.userId != expectedUserId) {
                            current
                        } else {
                            current.copy(
                                authSession = refreshed,
                                selectedProvider = refreshed.provider,
                                savedAccounts = authStore.state.value.sessions.map { session -> session.toSavedAccount() },
                            )
                        }
                    }
                    if (tokenChanged) {
                        recordDebugEvent("auth", "Refreshed saved sign-in tokens in the background")
                    }
                }
                result.exceptionOrNull()?.let { error ->
                    recordDebugEvent("auth", "Background sign-in refresh failed error=${error.debugMessage()}")
                }
            } finally {
                authRefreshJob = null
            }
        }
    }

    fun setPage(page: AppPage) {
        _state.update { it.copy(page = page, selectedGame = null) }
    }

    fun recordSettingsIconTap() {
        val now = SystemClock.elapsedRealtime()
        while (settingsDiagnosticTapTimes.firstOrNull()?.let { now - it > SETTINGS_DIAGNOSTIC_TAP_WINDOW_MS } == true) {
            settingsDiagnosticTapTimes.removeFirst()
        }
        settingsDiagnosticTapTimes.addLast(now)
        if (settingsDiagnosticTapTimes.size < SETTINGS_DIAGNOSTIC_TAP_COUNT) return
        settingsDiagnosticTapTimes.clear()
        requestDiagnosticShare()
    }

    fun dismissDiagnosticShare() {
        _state.update { it.copy(diagnosticShare = DiagnosticShareState()) }
    }

    fun requestDiagnosticShare() {
        _state.update {
            it.copy(diagnosticShare = DiagnosticShareState(awaitingConsent = true))
        }
    }

    fun startLocalTvConnector() {
        if (!state.value.androidTvProfile) return
        settingsStore.update { it.copy(localTvRemoteEnabled = true) }
        localTvConnector.startHosting()
    }

    fun stopLocalTvConnector() {
        if (state.value.androidTvProfile) {
            settingsStore.update { it.copy(localTvRemoteEnabled = false) }
        }
        localTvConnector.stopHosting()
    }

    fun refreshLocalTvPairingCode() {
        if (!state.value.androidTvProfile || !state.value.settings.localTvRemoteEnabled) return
        localTvConnector.refreshPairingCode()
    }

    fun setLocalTvDeviceTrusted(trusted: Boolean) {
        if (!state.value.androidTvProfile) return
        localTvConnector.setPairedDeviceTrusted(trusted)
    }

    fun setLocalTvTrustRequested(requested: Boolean) {
        if (state.value.androidTvProfile) return
        localTvConnector.setPhoneTrustRequest(requested)
    }

    fun forgetLocalTvConnector() {
        localTvConnector.forgetPhoneTarget()
    }

    fun playOnLocalTv(game: GameInfo) {
        if (state.value.androidTvProfile) return
        localTvConnector.sendLaunch(gameTrackingKey(game), game.title)
    }

    fun signInLocalTv() {
        if (state.value.androidTvProfile) return
        val session = state.value.authSession ?: run {
            _state.update { it.copy(error = "Sign in on the phone first") }
            return
        }
        localTvConnector.sendSignIn(session)
    }

    fun switchLocalTvAccount(userId: String) {
        if (state.value.androidTvProfile) return
        val session = authStore.state.value.sessions.firstOrNull { it.user.userId == userId } ?: run {
            _state.update { it.copy(error = "That account is no longer available on this phone") }
            return
        }
        localTvConnector.sendSignIn(session)
    }

    fun sendLocalTvRemoteAction(action: String, value: String? = null) {
        if (state.value.androidTvProfile) return
        localTvConnector.sendRemoteAction(action, value)
    }

    private fun handleLocalTvRemoteRequest(request: LocalTvRemoteRequest) {
        recordDebugEvent("tv-remote", "Accepted encrypted action=${request.action}")
        when (request.action) {
            "open_stream_menu" -> _state.update {
                it.copy(remoteStreamMenuRequestToken = it.remoteStreamMenuRequestToken + 1)
            }
            "toggle_stream_stats" -> _state.update {
                it.copy(remoteStatsToggleRequestToken = it.remoteStatsToggleRequestToken + 1)
            }
            "stop_stream" -> stopStream()
            "apply_recommended" -> applyStreamPreset(StreamPreset.Recommended)
            "set_codec" -> request.value
                ?.let { value -> runCatching { VideoCodec.valueOf(value) }.getOrNull() }
                ?.let { codec -> updateStreamSettings { it.copy(codec = codec) } }
            "set_resolution" -> request.value
                ?.takeIf { streamAspectRatioForResolution(it) != null }
                ?.let { resolution ->
                    updateStreamSettings {
                        it.copy(
                            resolution = resolution,
                            aspectRatio = streamAspectRatioForResolution(resolution) ?: it.aspectRatio,
                        )
                    }
                }
            "set_fps" -> request.value?.toIntOrNull()
                ?.takeIf { it in setOf(30, 60, 120) }
                ?.let { fps -> updateStreamSettings { it.copy(fps = fps) } }
            "set_background" -> request.value?.toBooleanStrictOrNull()?.let { enabled ->
                settingsStore.update { it.copy(nerdCatalogBackground = enabled) }
            }
            "set_ui_sounds" -> request.value?.toBooleanStrictOrNull()?.let { enabled ->
                settingsStore.update { it.copy(controllerUiSounds = enabled) }
            }
            "set_safe_area" -> request.value?.toFloatOrNull()?.coerceIn(0f, 120f)?.let { padding ->
                settingsStore.update { it.copy(tvSafeAreaPaddingDp = padding) }
            }
            "set_hide_server_selector" -> request.value?.toBooleanStrictOrNull()?.let { hidden ->
                settingsStore.update { it.copy(hideServerSelector = hidden) }
            }
        }
    }

    private fun acceptLocalTvSignIn(transferredSession: AuthSession) {
        viewModelScope.launch {
            authStore.upsertSession(transferredSession)
            val restored = restoreAuthSession(throwOnRefreshFailure = true).getOrElse { error ->
                authStore.removeSession(transferredSession.user.userId)
                _state.update { it.copy(error = "Phone sign-in could not be verified: ${error.message.orEmpty()}") }
                return@launch
            } ?: run {
                authStore.removeSession(transferredSession.user.userId)
                _state.update { it.copy(error = "Phone sign-in could not be verified") }
                return@launch
            }
            _state.update {
                it.copy(
                    authSession = restored,
                    selectedProvider = restored.provider,
                    savedAccounts = authStore.state.value.sessions.map { saved -> saved.toSavedAccount() },
                    error = null,
                    loadingGames = true,
                )
            }
            Toast.makeText(getApplication(), "Signed in securely from phone", Toast.LENGTH_SHORT).show()
            recordDebugEvent("tv-connector", "Accepted encrypted local sign-in provider=${restored.provider.code}")
            refreshAfterAuth(restored)
        }
    }

    fun uploadDiagnosticShare() {
        if (state.value.diagnosticShare.uploading) return
        _state.update {
            it.copy(diagnosticShare = DiagnosticShareState(uploading = true))
        }
        viewModelScope.launch {
            val snapshot = state.value
            val summaryHeader = diagnosticSummaryHeader(snapshot)
            val sanitizedLog = sanitizedDebugLogText()
            val payload = sanitizeDiagnosticExport(
                buildString {
                    appendLine(summaryHeader)
                    appendLine()
                    append(sanitizedLog)
                },
            )
            runCatching { uploadAndroidDiagnosticPaste(http, payload) }
                .onSuccess { pasteUrl ->
                    _state.update {
                        it.copy(
                            diagnosticShare = DiagnosticShareState(
                                pasteUrl = pasteUrl,
                                clipboardSummary = "$summaryHeader\nPaste: $pasteUrl",
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update {
                        it.copy(
                            diagnosticShare = DiagnosticShareState(
                                awaitingConsent = true,
                                error = error.message ?: "Could not upload diagnostics",
                            ),
                        )
                    }
                }
        }
    }

    private fun diagnosticSummaryHeader(snapshot: OpenNowUiState): String {
        val recommendation = deviceRecommendation
        val model = listOf(Build.MANUFACTURER, Build.MODEL)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" ")
        val accountType = snapshot.subscriptionInfo?.membershipTier
            ?: snapshot.authSession?.user?.membershipTier
            ?: "Unknown"
        val provider = snapshot.authSession?.provider?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown"
        return buildString {
            appendLine("OpenNOW Android ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Client: ${if (snapshot.androidTvProfile) "Android TV" else "Android mobile"}")
            appendLine("Hardware: $model · Android ${Build.VERSION.RELEASE}")
            appendLine("Screen: ${recommendation?.displayWidth ?: "?"}x${recommendation?.displayHeight ?: "?"} · processors ${recommendation?.processorCount ?: "?"} · memory ${recommendation?.totalMemoryMiB?.let { "$it MiB" } ?: "unknown"}")
            appendLine("Membership: $provider · $accountType")
            appendLine("Profile: ${snapshot.settings.streamPreset} · ${snapshot.settings.stream.resolution}@${snapshot.settings.stream.fps} · ${snapshot.settings.stream.codec} · ${snapshot.settings.stream.maxBitrateMbps} Mbps")
            append("Status: ${snapshot.streamStatus} · ${snapshot.error?.take(160)?.let(::sanitizeDiagnosticExport) ?: "no current error"}")
        }
    }

    fun openAndroidUpdateSettings() {
        _state.update {
            it.copy(
                page = AppPage.Settings,
                selectedGame = null,
                settingsRouteTarget = SettingsRouteTarget.General,
            )
        }
    }

    fun openStreamSettings() {
        _state.update {
            it.copy(
                page = AppPage.Settings,
                selectedGame = null,
                settingsRouteTarget = SettingsRouteTarget.Stream,
            )
        }
    }

    fun consumeSettingsRouteTarget(target: SettingsRouteTarget) {
        _state.update { current ->
            if (current.settingsRouteTarget == target) {
                current.copy(settingsRouteTarget = null)
            } else {
                current
            }
        }
    }

    fun handleControllerBackNavigation() {
        _state.update { current ->
            when {
                current.pendingStoreChoiceGame != null -> current.copy(pendingStoreChoiceGame = null)
                current.pendingPrintedWasteGame != null -> current.copy(
                    pendingPrintedWasteGame = null,
                    printedWasteLoading = false,
                    printedWasteError = null,
                )
                current.selectedGame != null -> current.copy(selectedGame = null)
                current.page != AppPage.Home -> current.copy(page = AppPage.Home, selectedGame = null)
                else -> current
            }
        }
    }

    fun selectProvider(provider: LoginProvider) {
        if (!provider.supportsDeviceCodeLogin && state.value.deviceLoginPrompt != null) {
            loginJob?.cancel()
            loginJob = null
        }
        _state.update {
            it.copy(
                selectedProvider = provider,
                deviceLoginPrompt = if (provider.supportsDeviceCodeLogin) it.deviceLoginPrompt else null,
                launchPhase = if (provider.supportsDeviceCodeLogin) it.launchPhase else "",
            )
        }
    }

    fun login(provider: LoginProvider = state.value.selectedProvider) {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            val useDeviceCode = state.value.androidTvProfile && provider.supportsDeviceCodeLogin
            _state.update {
                it.copy(
                    error = null,
                    launchPhase = if (useDeviceCode) "Requesting TV sign-in code" else "Opening ${provider.displayName} login",
                    deviceLoginPrompt = null,
                )
            }
            runCatching {
                loginWithBestAvailableMethod(provider, useDeviceCode)
            }
                .onSuccess { session ->
                    _state.update {
                        it.copy(
                            authSession = session,
                            selectedProvider = session.provider,
                            savedAccounts = authStore.state.value.sessions.map { saved -> saved.toSavedAccount() },
                            launchPhase = "",
                            deviceLoginPrompt = null,
                            error = null,
                            page = defaultLaunchAppPage(),
                        )
                    }
                    OpenNowAnalytics.capture(
                        event = "user_logged_in",
                        properties = mapOf(
                            "provider" to session.provider.code,
                            "membership_tier" to session.user.membershipTier,
                        ),
                    )
                    refreshAfterAuth(session)
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update { it.copy(error = error.message ?: "Login failed", launchPhase = "", deviceLoginPrompt = null) }
                }
        }
    }

    fun loginWithCode(provider: LoginProvider = state.value.selectedProvider) {
        if (!provider.supportsDeviceCodeLogin) {
            login(provider)
            return
        }
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    error = null,
                    launchPhase = "Requesting sign-in code",
                    deviceLoginPrompt = null,
                )
            }
            runCatching {
                loginWithDeviceCode(provider)
            }
                .onSuccess { session ->
                    _state.update {
                        it.copy(
                            authSession = session,
                            selectedProvider = session.provider,
                            savedAccounts = authStore.state.value.sessions.map { saved -> saved.toSavedAccount() },
                            launchPhase = "",
                            deviceLoginPrompt = null,
                            error = null,
                            page = defaultLaunchAppPage(),
                        )
                    }
                    OpenNowAnalytics.capture(
                        event = "user_logged_in",
                        properties = mapOf(
                            "provider" to session.provider.code,
                            "membership_tier" to session.user.membershipTier,
                            "login_method" to "device_code",
                        ),
                    )
                    refreshAfterAuth(session)
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update { it.copy(error = error.message ?: "Code sign-in failed", launchPhase = "", deviceLoginPrompt = null) }
                }
        }
    }

    private suspend fun loginWithBestAvailableMethod(provider: LoginProvider, useDeviceCode: Boolean): AuthSession {
        if (useDeviceCode) {
            return loginWithDeviceCode(provider)
        }

        return try {
            authRepository.login(provider) {
                _state.update { it.copy(launchPhase = LOGIN_PHASE_GETTING_TOKENS, error = null) }
            }
        } catch (error: Throwable) {
            if (error is CancellationException || !isLoopbackLoginFailure(error)) {
                throw error
            }
            if (!provider.supportsDeviceCodeLogin) {
                throw error
            }
            _state.update {
                it.copy(
                    launchPhase = "Requesting sign-in code",
                    error = "Browser sign-in could not reach the local callback. Use this code to finish sign-in.",
                )
            }
            loginWithDeviceCode(provider, clearErrorOnPrompt = false)
        }
    }

    private suspend fun loginWithDeviceCode(provider: LoginProvider, clearErrorOnPrompt: Boolean = true): AuthSession =
        authRepository.loginWithDeviceCode(provider) { prompt ->
            _state.update {
                it.copy(
                    deviceLoginPrompt = prompt,
                    launchPhase = "Waiting for sign-in",
                    error = if (clearErrorOnPrompt) null else it.error,
                )
            }
        }

    private fun isLoopbackLoginFailure(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return "oauth callback" in message ||
            "callback ports" in message ||
            "local callback" in message ||
            "localhost" in message ||
            "127.0.0.1" in message
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        _state.update { it.copy(launchPhase = "", deviceLoginPrompt = null) }
    }

    fun logout() {
        viewModelScope.launch {
            pendingActiveSessionLaunch = null
            OpenNowAnalytics.capture(event = "user_logged_out")
            OpenNowAnalytics.reset()
            authRepository.logout()
            val nextSession = authStore.activeSession()
            _state.update {
                it.copy(
                    authSession = nextSession,
                    selectedProvider = nextSession?.provider ?: it.selectedProvider,
                    savedAccounts = authStore.state.value.sessions.map { saved -> saved.toSavedAccount() },
                    subscriptionInfo = null,
                    accountConnectors = emptyList(),
                    loadingAccountConnectors = false,
                    connectorActionStore = null,
                    games = emptyList(),
                    libraryGames = emptyList(),
                    libraryFilterIds = emptyList(),
                    streamSession = null,
                    activeStreamSettings = null,
                    activeSession = null,
                    activeSessionDecision = null,
                    deviceLoginPrompt = null,
                    pendingStoreChoiceGame = null,
                    page = AppPage.Home,
                )
            }
            if (nextSession != null) {
                refreshAfterAuth(nextSession)
            }
        }
    }

    fun switchAccount(userId: String) {
        viewModelScope.launch {
            pendingActiveSessionLaunch = null
            authStore.setActiveSession(userId)
            val session = authRepository.restore(forceRefresh = false) ?: return@launch
            gamesJob?.cancel()
            _state.update {
                it.copy(
                    authSession = session,
                    selectedProvider = session.provider,
                    savedAccounts = authStore.state.value.sessions.map { saved -> saved.toSavedAccount() },
                    subscriptionInfo = null,
                    accountConnectors = emptyList(),
                    loadingAccountConnectors = false,
                    connectorActionStore = null,
                    games = emptyList(),
                    libraryGames = emptyList(),
                    catalogResult = CatalogBrowseResult(emptyList()),
                    libraryFilterIds = emptyList(),
                    selectedGame = null,
                    activeSession = null,
                    activeSessionDecision = null,
                    error = null,
                    page = AppPage.Home,
                )
            }
            OpenNowAnalytics.capture(
                event = "account_switched",
                properties = mapOf(
                    "provider" to session.provider.code,
                    "membership_tier" to session.user.membershipTier,
                ),
            )
            refreshAfterAuth(session)
        }
    }

    fun logoutAll() {
        pendingActiveSessionLaunch = null
        authRepository.logoutAll()
        _state.update {
            it.copy(
                authSession = null,
                savedAccounts = emptyList(),
                subscriptionInfo = null,
                accountConnectors = emptyList(),
                loadingAccountConnectors = false,
                connectorActionStore = null,
                games = emptyList(),
                libraryGames = emptyList(),
                libraryFilterIds = emptyList(),
                streamSession = null,
                activeStreamSettings = null,
                activeSession = null,
                activeSessionDecision = null,
                deviceLoginPrompt = null,
                pendingStoreChoiceGame = null,
                page = AppPage.Home,
            )
        }
    }

    fun refreshGames() {
        val session = state.value.authSession ?: return
        viewModelScope.launch {
            refreshAfterAuth(session, keepRefreshVisibleWithCache = true)
        }
    }

    fun setCatalogSearch(query: String) {
        _state.update { it.copy(catalogSearch = query) }
        if (query.isNotBlank()) {
            OpenNowAnalytics.capture(
                event = "catalog_searched",
                properties = mapOf("query" to query),
            )
        }
        refreshCatalogDebounced()
    }

    fun setLibrarySearch(query: String) {
        _state.update { it.copy(librarySearch = query) }
    }

    fun toggleLibraryFilter(filterId: String) {
        _state.update {
            val filters = if (filterId in it.libraryFilterIds) it.libraryFilterIds - filterId else it.libraryFilterIds + filterId
            it.copy(libraryFilterIds = filters)
        }
    }

    fun clearLibraryFilters() {
        _state.update { it.copy(libraryFilterIds = emptyList()) }
    }

    fun setCatalogSort(sortId: String) {
        _state.update { it.copy(catalogSortId = sortId) }
        refreshCatalogDebounced()
    }

    fun toggleCatalogFilter(filterId: String) {
        val adding = filterId !in state.value.catalogFilterIds
        _state.update {
            val filters = if (filterId in it.catalogFilterIds) it.catalogFilterIds - filterId else it.catalogFilterIds + filterId
            it.copy(catalogFilterIds = filters)
        }
        OpenNowAnalytics.capture(
            event = "catalog_filter_applied",
            properties = mapOf(
                "filter_id" to filterId,
                "action" to if (adding) "add" else "remove",
            ),
        )
        refreshCatalogDebounced()
    }

    fun clearCatalogFilters() {
        _state.update { it.copy(catalogFilterIds = emptyList()) }
        refreshCatalogDebounced()
    }

    fun selectGame(game: GameInfo) {
        _state.update { it.copy(selectedGame = game) }
        OpenNowAnalytics.capture(
            event = "game_selected",
            properties = mapOf(
                "game_id" to game.id,
                "game_title" to game.title,
            ),
        )
    }

    fun clearSelectedGame() {
        _state.update { it.copy(selectedGame = null) }
    }

    fun updateSettings(next: AppSettings) {
        settingsStore.replace(next)
    }

    fun checkAndroidUpdate() {
        startAndroidUpdateCheck(automatic = false)
    }

    fun dismissAndroidUpdateNotice() {
        val key = androidUpdateNoticeKey(state.value.androidUpdate) ?: return
        androidUpdateNoticeStore.dismiss(key)
        _state.update { it.copy(dismissedAndroidUpdateNoticeKey = key) }
    }

    fun downloadAndroidUpdate() {
        if (androidUpdateJob?.isActive == true || !state.value.androidUpdate.canDownload) return
        OpenNowAnalytics.capture(event = "app_update_downloaded")
        androidUpdateJob = viewModelScope.launch {
            appUpdater.downloadUpdate()
        }
    }

    fun installAndroidUpdate() {
        if (!state.value.androidUpdate.canInstall) return
        appUpdater.installDownloadedUpdate()
    }

    private fun startAndroidUpdateAutoChecks() {
        if (androidUpdateAutoJob?.isActive == true) return
        if (!state.value.androidUpdate.apkUpdatesAllowed) return
        androidUpdateAutoJob = viewModelScope.launch {
            delay(ANDROID_UPDATE_LAUNCH_CHECK_DELAY_MS)
            while (true) {
                runAutomaticAndroidUpdateCheck()
                delay(ANDROID_UPDATE_PERIODIC_CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun runAutomaticAndroidUpdateCheck() {
        if (!state.value.androidUpdate.apkUpdatesAllowed) return
        if (!state.value.settings.autoCheckForUpdates) return
        waitForAndroidUpdateCheckWindow()
        val snapshot = state.value
        if (!snapshot.settings.autoCheckForUpdates || !snapshot.androidUpdate.shouldRunAutomaticCheck()) return
        startAndroidUpdateCheck(automatic = true)?.join()
    }

    private suspend fun waitForAndroidUpdateCheckWindow() {
        while (state.value.isAndroidUpdateCheckBlockedByStream()) {
            delay(ANDROID_UPDATE_STREAMING_RETRY_DELAY_MS)
        }
    }

    private fun startAndroidUpdateCheck(automatic: Boolean): Job? {
        if (androidUpdateJob?.isActive == true) return null
        if (!state.value.androidUpdate.apkUpdatesAllowed) return null
        if (state.value.isAndroidUpdateCheckBlockedByStream()) {
            if (!automatic) {
                appUpdater.markCheckDeferredForStreaming()
            }
            return null
        }
        return viewModelScope.launch {
            if (state.value.isAndroidUpdateCheckBlockedByStream()) {
                if (!automatic) {
                    appUpdater.markCheckDeferredForStreaming()
                }
                return@launch
            }
            appUpdater.checkForUpdate()
        }.also { job ->
            androidUpdateJob = job
        }
    }

    private fun cancelAndroidUpdateCheckForStreaming() {
        if (state.value.androidUpdate.status != AndroidUpdateStatus.Checking) return
        androidUpdateJob?.cancel()
        androidUpdateJob = null
        appUpdater.markCheckDeferredForStreaming()
    }

    fun refreshSettings() {
        if (settingsRefreshJob?.isActive == true) return
        settingsRefreshJob = viewModelScope.launch {
            _state.update { it.copy(settingsRefreshing = true, error = null) }
            try {
                val updateJob = startAndroidUpdateCheck(automatic = false)
                val session = state.value.authSession
                val accountJob = session?.let { activeSession ->
                    launch { refreshSettingsAccountData(activeSession) }
                }
                val accountConnectorsJob = session?.let { activeSession ->
                    launch { refreshAccountConnectors(activeSession) }
                }
                updateJob?.join()
                accountJob?.join()
                accountConnectorsJob?.join()
            } finally {
                _state.update { it.copy(settingsRefreshing = false) }
            }
        }
    }

    fun resetSettings() {
        Toast.makeText(getApplication(), "Clearing app data and relaunching OpenNOW", Toast.LENGTH_SHORT).show()
        wipeAppDataAndRelaunch(getApplication())
    }

    fun resetStreamTutorial() {
        settingsStore.update { it.copy(androidStreamGuideDismissed = false) }
        Toast.makeText(getApplication(), "Tutorial will show on the next stream", Toast.LENGTH_SHORT).show()
    }

    fun clearCatalogCache() {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { catalogCacheStore.clear() }
            Toast.makeText(
                getApplication(),
                if (removed == 0) "Game cache was already clear" else "Cleared game cache",
                Toast.LENGTH_SHORT,
            ).show()
            state.value.authSession?.let { refreshAfterAuth(it) }
        }
    }

    fun refreshAccountConnectors() {
        val session = state.value.authSession ?: return
        viewModelScope.launch {
            refreshAccountConnectors(session)
        }
    }

    private suspend fun refreshAccountConnectors(session: AuthSession) {
        accountConnectorRefreshMutex.withLock {
            val token = accountConnectorAuthToken(session)
            _state.update { it.copy(loadingAccountConnectors = true) }
            runCatching { accountConnectorRepository.fetchConnectors(token) }
                .onSuccess { connectors ->
                    _state.update { it.copy(accountConnectors = connectors, loadingAccountConnectors = false) }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update { it.copy(loadingAccountConnectors = false, error = error.message ?: "Failed to load account connections") }
                }
        }
    }

    private suspend fun refreshSettingsAccountData(session: AuthSession) {
        val token = accountConnectorAuthToken(session)
        coroutineScope {
            val subscription = async {
                runCatching {
                    val vpcId = catalogRepository.getVpcId(token, session.provider.streamingServiceUrl)
                    subscriptionRepository.fetchSubscription(token, session.user.userId, vpcId)
                }.getOrNull()
            }
            val regions = async {
                runCatching { fetchDynamicRegions(http, token, session.provider.streamingServiceUrl).first }
                    .getOrDefault(emptyList())
            }
            val fetchedSubscription = subscription.await()
            val enrichedSession = persistSubscriptionTier(session, fetchedSubscription)
            val fetchedRegions = regions.await()
            _state.update { current ->
                current.copy(
                    authSession = current.authSession
                        ?.takeIf { it.user.userId == enrichedSession.user.userId }
                        ?.let { enrichedSession }
                        ?: current.authSession,
                    savedAccounts = savedAccountsSnapshot(),
                    subscriptionInfo = fetchedSubscription ?: current.subscriptionInfo,
                    regions = fetchedRegions.ifEmpty { current.regions },
                )
            }
        }
    }

    fun connectAccountConnector(store: String, openUrl: (String) -> Unit) {
        val session = state.value.authSession ?: return
        viewModelScope.launch {
            val token = accountConnectorAuthToken(session)
            _state.update { it.copy(connectorActionStore = store, error = null) }
            runCatching { withTimeout(20_000L) { accountConnectorRepository.loginUrl(store, token) } }
                .onSuccess { url ->
                    _state.update { it.copy(connectorActionStore = null) }
                    openUrl(url)
                    refreshAccountConnectorsAfterLinking()
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    val message = error.message ?: "Failed to start store connection"
                    Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
                    _state.update { it.copy(connectorActionStore = null, error = message) }
                }
        }
    }

    private suspend fun accountConnectorAuthToken(session: AuthSession): String =
        authRepository.restore(forceRefresh = false)?.tokens?.let { it.idToken ?: it.accessToken }
            ?: (session.tokens.idToken ?: session.tokens.accessToken)

    private fun refreshAccountConnectorsAfterLinking() {
        viewModelScope.launch {
            repeat(6) { attempt ->
                delay(if (attempt == 0) 5_000L else 10_000L)
                val session = state.value.authSession ?: return@launch
                refreshAccountConnectors(session)
            }
        }
    }

    fun disconnectAccountConnector(store: String) {
        val session = state.value.authSession ?: return
        viewModelScope.launch {
            val token = accountConnectorAuthToken(session)
            _state.update { it.copy(connectorActionStore = store, error = null) }
            runCatching { accountConnectorRepository.disconnect(store, token) }
                .onSuccess {
                    Toast.makeText(getApplication(), "Store disconnected", Toast.LENGTH_SHORT).show()
                    _state.update { it.copy(connectorActionStore = null) }
                    refreshAccountConnectors()
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    val message = error.message ?: "Failed to disconnect store"
                    Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
                    _state.update { it.copy(connectorActionStore = null, error = message) }
                }
        }
    }

    fun updateStreamSettings(transform: (StreamSettings) -> StreamSettings) {
        val snapshot = state.value
        settingsStore.update {
            it.copy(
                stream = transform(it.stream)
                    .withAndroidSettingsAvailability()
                    .withFpsAllowed(snapshot.subscriptionInfo, snapshot.authSession?.user?.membershipTier),
                streamPreset = StreamPreset.Custom,
            )
        }
    }

    fun applyStreamPreset(preset: StreamPreset) {
        val snapshot = state.value
        settingsStore.update { settings ->
            val presetStream = if (preset == StreamPreset.Recommended) {
                (deviceRecommendation ?: recommendedAndroidStreamProfile(getApplication(), snapshot.codecReport)).stream
            } else {
                settings.stream.applyingStreamPreset(preset)
            }
            settings.copy(
                streamPreset = preset,
                stream = presetStream
                    .withAndroidSettingsAvailability()
                    .withResolutionAllowed(snapshot.subscriptionInfo, snapshot.authSession?.user?.membershipTier)
                    .withFpsAllowed(snapshot.subscriptionInfo, snapshot.authSession?.user?.membershipTier),
            )
        }
    }

    fun updateFavorites(gameId: String) {
        val adding = gameId !in settingsStore.settings.value.favoriteGameIds
        settingsStore.update {
            val next = if (gameId in it.favoriteGameIds) it.favoriteGameIds - gameId else it.favoriteGameIds + gameId
            it.copy(favoriteGameIds = next)
        }
        OpenNowAnalytics.capture(
            event = "favorite_toggled",
            properties = mapOf(
                "game_id" to gameId,
                "action" to if (adding) "add" else "remove",
            ),
        )
    }

    fun setDefaultGameVariant(gameId: String, variantId: String?) {
        settingsStore.update {
            val next = it.defaultGameVariantIds.toMutableMap()
            if (variantId.isNullOrBlank()) {
                next.remove(gameId)
            } else {
                next[gameId] = variantId
            }
            it.copy(defaultGameVariantIds = next)
        }
    }

    fun dismissStoreChoice() {
        _state.update { it.copy(pendingStoreChoiceGame = null) }
    }

    fun chooseStore(game: GameInfo) {
        val launchVariants = launchableGameVariants(game.variants)
        if (launchVariants.size > 1) {
            _state.update { it.copy(pendingStoreChoiceGame = game, selectedGame = null, error = null) }
        } else {
            play(game, skipStoreChoice = true)
        }
    }

    fun playVariant(game: GameInfo, variant: GameVariant) {
        _state.update { it.copy(pendingStoreChoiceGame = null) }
        play(game.withSelectedVariant(variant.id), skipStoreChoice = true)
    }

    fun play(game: GameInfo, streamingBaseUrlOverride: String? = null, skipPrintedWaste: Boolean = false, skipStoreChoice: Boolean = false) {
        if (launchJob?.isActive == true) {
            recordDebugEvent("launch", "Ignored play request while another launch is active game=${game.title}")
            return
        }
        if (!skipStoreChoice) {
            val launchVariants = launchableGameVariants(game.variants)
            val defaultVariantId = state.value.settings.defaultGameVariantIds[game.id]
            val defaultVariant = launchVariants.firstOrNull { it.id == defaultVariantId }
            if (defaultVariant != null) {
                recordDebugEvent("launch", "Using default launcher ${gameStoreDisplayName(defaultVariant.store)} for ${game.title}")
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(
                        R.string.store_selector_default_launch_notice,
                        gameStoreDisplayName(defaultVariant.store),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                play(game.withSelectedVariant(defaultVariant.id), streamingBaseUrlOverride, skipPrintedWaste, skipStoreChoice = true)
                return
            }
            if (launchVariants.size > 1) {
                recordDebugEvent("launch", "Waiting for launcher choice game=${game.title} variants=${launchVariants.size}")
                _state.update { it.copy(pendingStoreChoiceGame = game, selectedGame = null, error = null) }
                return
            }
        }
        launchJob = viewModelScope.launch {
            val auth = state.value.authSession ?: run {
                recordDebugEvent("launch", "Play request ignored without an auth session game=${game.title}")
                return@launch
            }
            // Wait for active subscription fetch to finish so we have accurate membership info to allow resolutions
            activeSubscriptionJob?.join()
            val returnPage = state.value.page.takeUnless { it == AppPage.Stream } ?: state.value.streamReturnPage ?: AppPage.Home
            if (!skipPrintedWaste && streamingBaseUrlOverride == null && shouldUsePrintedWasteQueue(auth)) {
                recordDebugEvent("queue", "Opening PrintedWaste selector game=${game.title}")
                showPrintedWasteSelector(game)
                return@launch
            }
            val requestedSettings = streamSettingsBeforeDeviceAdjustment()
            val settings = requestedSettings.adjustedForDevice(state.value.codecReport)
            if (settings != requestedSettings) {
                recordDebugEvent(
                    "launch",
                    "Adjusted stream settings requested=${requestedSettings.debugSummary()} effective=${settings.debugSummary()}",
                )
            }
            val token = auth.tokens.idToken ?: auth.tokens.accessToken
            val baseUrl = streamingBaseUrlOverride ?: effectiveStreamingBaseUrl()
            pendingActiveSessionLaunch = null
            recordDebugEvent(
                "launch",
                "Starting launch game=${game.title} base=${hostForDebug(baseUrl)} settings=${settings.debugSummary()} override=${streamingBaseUrlOverride != null}",
            )
            recordQueuedGame(game)
            OpenNowAnalytics.capture(
                event = "stream_started",
                properties = mapOf(
                    "game_id" to game.id,
                    "game_title" to game.title,
                    "resolution" to settings.resolution,
                    "fps" to settings.fps,
                    "codec" to settings.codec.name,
                ),
            )
            _state.update {
                it.copy(
                    streamStatus = "queue",
                    launchPhase = "Resolving game",
                    streamGame = game,
                    activeStreamSettings = settings,
                    selectedGame = null,
                    page = AppPage.Stream,
                    streamReturnPage = returnPage,
                    streamLaunchMinimized = false,
                    error = null,
                    queuePosition = null,
                    queueAdActiveId = null,
                    pendingStoreChoiceGame = null,
                    pendingPrintedWasteGame = null,
                    activeSessionDecision = null,
                    printedWasteError = null,
                    printedWastePings = emptyMap(),
                )
            }
            runCatching {
                val selectedVariant = game.variants.getOrNull(game.selectedVariantIndex) ?: game.variants.firstOrNull()
                val candidateId = selectedVariant?.id ?: game.launchAppId ?: game.uuid ?: game.id
                val launchAppId = candidateId.takeIf { it.all(Char::isDigit) }
                    ?: game.launchAppId?.takeIf { it.all(Char::isDigit) }
                    ?: catalogRepository.resolveLaunchAppId(token, candidateId, baseUrl)
                    ?: error("Could not resolve numeric appId for ${game.title}")
                recordDebugEvent("launch", "Resolved appId=$launchAppId candidate=$candidateId game=${game.title}")

                _state.update { it.copy(launchPhase = "Checking active sessions") }
                val active = sessionRepository.getActiveSessions(token, baseUrl, settings)
                recordDebugEvent("queue", "Active sessions checked count=${active.size} ${active.joinToString(limit = 4) { it.debugSummary() }}")
                val numericLaunchAppId = launchAppId.toIntOrNull()
                val activeConflict = activeSessionLaunchConflict(active, numericLaunchAppId, settings)
                if (activeConflict != null) {
                    pendingActiveSessionLaunch = PendingActiveSessionLaunch(
                        game = game,
                        launchAppId = launchAppId,
                        baseUrl = baseUrl,
                        settings = settings,
                        accountLinked = shouldSendAccountLinked(game, selectedVariant),
                        activeSession = activeConflict,
                        returnPage = returnPage,
                    )
                    recordDebugEvent("queue", "Active session decision required ${activeConflict.debugSummary()} requestedApp=$launchAppId")
                    _state.update {
                        it.copy(
                            activeSession = activeConflict,
                            activeSessionDecision = ActiveSessionDecision(
                                activeSession = activeConflict,
                                requestedGameTitle = game.title,
                            ),
                            streamSession = null,
                            launchPhase = "Active session found",
                            queuePosition = activeConflict.queuePosition,
                            queueAdActiveId = null,
                        )
                    }
                    return@runCatching null
                }
                _state.update { it.copy(launchPhase = "Creating session") }
                val created = sessionRepository.createSession(
                    token = token,
                    streamingBaseUrl = baseUrl,
                    appId = launchAppId,
                    internalTitle = game.title,
                    zone = "prod",
                    settings = settings,
                    accountLinked = shouldSendAccountLinked(game, selectedVariant),
                )
                recordDebugEvent("queue", "Created session ${created.debugSummary()}")
                pollUntilReady(token, created, settings)
            }.onSuccess { readySession ->
                if (readySession == null) return@onSuccess
                markSessionReadyForNativeStream(readySession, settings)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                recordDebugEvent("launch", "Launch failed game=${game.title} error=${error.debugMessage()}")
                val returnPage = state.value.streamReturnPage ?: AppPage.Home
                _state.update {
                    it.copy(
                        error = normalizeLaunchError(error, game.title),
                        streamStatus = "idle",
                        activeStreamSettings = null,
                        streamReturnPage = null,
                        launchPhase = "",
                        streamLaunchMinimized = false,
                        queuePosition = null,
                        queueAdActiveId = null,
                        pendingStoreChoiceGame = null,
                        activeSessionDecision = null,
                        page = returnPage,
                    )
                }
            }
        }
    }

    private fun recordQueuedGame(game: GameInfo) {
        val next = queuedGameStore.record(gameTrackingKey(game))
        _state.update { it.copy(queuedGameKeys = next) }
    }

    private fun markSessionReadyForNativeStream(readySession: SessionInfo, settings: StreamSettings) {
        recordDebugEvent("stream", "Session ready for native stream ${readySession.debugSummary()}")
        _state.update {
            it.copy(
                streamSession = readySession,
                activeStreamSettings = settings,
                streamStatus = "connecting",
                launchPhase = "Connecting stream",
                streamLaunchMinimized = false,
                queuePosition = null,
                queueAdActiveId = null,
                activeSessionDecision = null,
                page = AppPage.Stream,
            )
        }
    }

    fun stopStream() {
        val beforeStop = state.value
        recordDebugEvent(
            "stream",
            "Stop requested status=${beforeStop.streamStatus} session=${beforeStop.streamSession?.shortDebugId().orEmpty()} game=${beforeStop.streamGame?.title.orEmpty()}",
        )
        launchJob?.cancel()
        launchJob = null
        pendingActiveSessionLaunch = null
        viewModelScope.launch {
            val auth = state.value.authSession
            val snapshot = state.value
            val returnPage = snapshot.streamReturnPage ?: AppPage.Home
            val session = snapshot.streamSession
            val streamSettings = snapshot.activeStreamSettings ?: effectiveStreamSettings()
            if (auth != null && session != null) {
                runCatching { sessionRepository.stopSession(auth.tokens.idToken ?: auth.tokens.accessToken, session, streamSettings) }
                    .onSuccess { recordDebugEvent("stream", "Stopped cloud session ${session.shortDebugId()}") }
                    .onFailure { error -> recordDebugEvent("stream", "Failed to stop cloud session ${session.shortDebugId()} error=${error.debugMessage()}") }
            } else if (auth != null) {
                val token = auth.tokens.idToken ?: auth.tokens.accessToken
                val active = snapshot.activeSession
                    ?: runCatching {
                        sessionRepository.getActiveSessions(token, effectiveStreamingBaseUrl(auth), streamSettings)
                            .firstOrNull { it.status in setOf(1, 2, 3) }
                    }.getOrNull()
                if (active != null) {
                    runCatching { sessionRepository.stopActiveSession(token, active, streamSettings) }
                        .onSuccess { recordDebugEvent("stream", "Stopped active session ${active.shortDebugId()}") }
                        .onFailure { error -> recordDebugEvent("stream", "Failed to stop active session ${active.shortDebugId()} error=${error.debugMessage()}") }
                } else {
                    recordDebugEvent("stream", "No cloud session found to stop")
                }
            }
            OpenNowAnalytics.capture(
                event = "stream_stopped",
                properties = mapOf(
                    "game_title" to (state.value.streamGame?.title ?: ""),
                    "game_id" to (state.value.streamGame?.id ?: ""),
                ),
            )
            _state.update {
                it.copy(
                    streamSession = null,
                    activeStreamSettings = null,
                    streamGame = null,
                    streamStatus = "idle",
                    streamLaunchMinimized = false,
                    streamReturnPage = null,
                    launchPhase = "",
                    queuePosition = null,
                    queueAdActiveId = null,
                    pendingStoreChoiceGame = null,
                    activeSessionDecision = null,
                    page = returnPage,
                )
            }
            refreshActiveSession()
            recordDebugEvent("stream", "Stream state reset returnPage=$returnPage")
        }
    }

    fun refreshPrintedWasteQueues() {
        val game = state.value.pendingPrintedWasteGame ?: return
        recordDebugEvent("queue", "Refreshing PrintedWaste queues game=${game.title}")
        viewModelScope.launch {
            loadPrintedWasteQueue(game)
        }
    }

    fun minimizeStreamLaunch() {
        recordDebugEvent("queue", "Minimize launch requested status=${state.value.streamStatus} phase=${state.value.launchPhase}")
        _state.update { current ->
            if (current.streamStatus == "idle" || current.streamSession?.isReadyForStream() == true) {
                current
            } else {
                current.copy(streamLaunchMinimized = true, page = current.streamReturnPage ?: AppPage.Home)
            }
        }
    }

    fun restoreStreamLaunch() {
        recordDebugEvent("queue", "Restore launch requested status=${state.value.streamStatus} phase=${state.value.launchPhase}")
        _state.update { current ->
            if (current.streamStatus == "idle") current else current.copy(streamLaunchMinimized = false, page = AppPage.Stream)
        }
    }

    fun dismissActiveSessionDecision() {
        val pending = pendingActiveSessionLaunch
        recordDebugEvent("queue", "Active session decision dismissed session=${pending?.activeSession?.shortDebugId().orEmpty()}")
        pendingActiveSessionLaunch = null
        val returnPage = pending?.returnPage ?: state.value.streamReturnPage ?: AppPage.Home
        _state.update {
            it.copy(
                streamStatus = "idle",
                activeStreamSettings = null,
                streamGame = null,
                streamSession = null,
                activeSessionDecision = null,
                streamReturnPage = null,
                launchPhase = "",
                streamLaunchMinimized = false,
                queuePosition = null,
                queueAdActiveId = null,
                page = returnPage,
            )
        }
    }

    fun terminateActiveSessionAndStartNew() {
        if (launchJob?.isActive == true) {
            recordDebugEvent("queue", "Ignored replace active session request while another launch is active")
            return
        }
        val pending = pendingActiveSessionLaunch ?: run {
            recordDebugEvent("queue", "Replace active session ignored without pending launch")
            return
        }
        pendingActiveSessionLaunch = null
        recordDebugEvent("queue", "Replace active session requested active=${pending.activeSession.debugSummary()} game=${pending.game.title}")
        launchJob = viewModelScope.launch {
            val auth = state.value.authSession ?: run {
                recordDebugEvent("queue", "Replace active session ignored without an auth session")
                return@launch
            }
            val token = auth.tokens.idToken ?: auth.tokens.accessToken
            _state.update {
                it.copy(
                    streamStatus = "queue",
                    launchPhase = "Ending active session",
                    activeSession = pending.activeSession,
                    activeSessionDecision = null,
                    streamSession = null,
                    streamGame = pending.game,
                    activeStreamSettings = pending.settings,
                    page = AppPage.Stream,
                    streamReturnPage = pending.returnPage,
                    streamLaunchMinimized = false,
                    error = null,
                    queuePosition = null,
                    queueAdActiveId = null,
                )
            }
            runCatching {
                runCatching { sessionRepository.stopActiveSession(token, pending.activeSession, pending.settings) }
                    .onSuccess { recordDebugEvent("queue", "Stopped active session before new launch ${pending.activeSession.shortDebugId()}") }
                    .onFailure { error -> recordDebugEvent("queue", "Failed to stop active session before new launch ${pending.activeSession.shortDebugId()} error=${error.debugMessage()}") }
                _state.update { it.copy(activeSession = null, launchPhase = "Creating session") }
                val created = sessionRepository.createSession(
                    token = token,
                    streamingBaseUrl = pending.baseUrl,
                    appId = pending.launchAppId,
                    internalTitle = pending.game.title,
                    zone = "prod",
                    settings = pending.settings,
                    accountLinked = pending.accountLinked,
                )
                recordDebugEvent("queue", "Created replacement session ${created.debugSummary()}")
                pollUntilReady(token, created, pending.settings)
            }.onSuccess { readySession ->
                markSessionReadyForNativeStream(readySession, pending.settings)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                recordDebugEvent("launch", "Replace active session launch failed game=${pending.game.title} error=${error.debugMessage()}")
                val returnPage = state.value.streamReturnPage ?: pending.returnPage
                _state.update {
                    it.copy(
                        error = normalizeLaunchError(error, pending.game.title),
                        streamStatus = "idle",
                        activeStreamSettings = null,
                        streamReturnPage = null,
                        activeSessionDecision = null,
                        launchPhase = "",
                        streamLaunchMinimized = false,
                        queuePosition = null,
                        queueAdActiveId = null,
                        pendingStoreChoiceGame = null,
                        page = returnPage,
                    )
                }
            }
        }
    }

    fun resumeActiveSession() {
        if (launchJob?.isActive == true) {
            recordDebugEvent("queue", "Ignored resume request while another launch is active")
            return
        }
        pendingActiveSessionLaunch?.let { pending ->
            resumePendingActiveSession(pending)
            return
        }
        recordDebugEvent("queue", "Resume active session requested cached=${state.value.activeSession?.debugSummary().orEmpty()}")
        launchJob = viewModelScope.launch {
            val auth = state.value.authSession ?: run {
                recordDebugEvent("queue", "Resume ignored without an auth session")
                return@launch
            }
            val settings = effectiveStreamSettings()
            val token = auth.tokens.idToken ?: auth.tokens.accessToken
            val baseUrl = effectiveStreamingBaseUrl(auth)
            val cachedActive = state.value.activeSession
            val returnPage = state.value.page.takeUnless { it == AppPage.Stream } ?: state.value.streamReturnPage ?: AppPage.Home
            _state.update {
                it.copy(
                    streamStatus = "queue",
                    launchPhase = "Checking active sessions",
                    activeStreamSettings = settings,
                    page = AppPage.Stream,
                    streamReturnPage = returnPage,
                    streamLaunchMinimized = false,
                    selectedGame = null,
                    pendingStoreChoiceGame = null,
                    pendingPrintedWasteGame = null,
                    activeSessionDecision = null,
                    error = null,
                    queuePosition = null,
                    queueAdActiveId = null,
                )
            }
            runCatching {
                val active = cachedActive ?: sessionRepository.getActiveSessions(token, baseUrl, settings)
                    .let { activeSessionLaunchConflict(it, launchAppId = null, settings = settings) }
                    ?: error("No active cloud session was found. Start a game to create a new one.")
                val resumeSettings = resumeSettingsForActiveSession(active, settings)
                recordDebugEvent("queue", "Resume found active ${active.debugSummary()} base=${hostForDebug(baseUrl)} settings=${resumeSettings.debugSummary()}")
                val matchingGame = gameForActiveSession(active)
                _state.update {
                    it.copy(
                        activeSession = active,
                        streamGame = matchingGame,
                        streamSession = active.toPendingSession(zone = "prod"),
                        activeStreamSettings = resumeSettings,
                        launchPhase = if (active.isReadyForClaim()) "Resuming session" else loadingPhaseFor(active.toPendingSession(zone = "prod")),
                    )
                }
                resumeKnownActiveSession(token, active, resumeSettings, baseUrl)
            }.onSuccess { readySession ->
                recordDebugEvent("stream", "Resume ready for native stream ${readySession.debugSummary()}")
                markSessionReadyForNativeStream(readySession, state.value.activeStreamSettings ?: settings)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                recordDebugEvent("queue", "Resume failed error=${error.debugMessage()}")
                val returnPage = state.value.streamReturnPage ?: AppPage.Home
                _state.update {
                    it.copy(
                        error = normalizeLaunchError(error, state.value.streamGame?.title),
                        streamStatus = "idle",
                        activeStreamSettings = null,
                        streamReturnPage = null,
                        launchPhase = "",
                        streamLaunchMinimized = false,
                        queuePosition = null,
                        queueAdActiveId = null,
                        pendingStoreChoiceGame = null,
                        pendingPrintedWasteGame = null,
                        activeSessionDecision = null,
                        page = returnPage,
                    )
                }
            }
        }
    }

    private fun resumePendingActiveSession(pending: PendingActiveSessionLaunch) {
        pendingActiveSessionLaunch = null
        recordDebugEvent("queue", "Resume pending active session requested active=${pending.activeSession.debugSummary()} requestedGame=${pending.game.title}")
        launchJob = viewModelScope.launch {
            val auth = state.value.authSession ?: run {
                recordDebugEvent("queue", "Pending resume ignored without an auth session")
                return@launch
            }
            val token = auth.tokens.idToken ?: auth.tokens.accessToken
            val resumeSettings = resumeSettingsForActiveSession(pending.activeSession, pending.settings)
            val pendingSession = pending.activeSession.toPendingSession(zone = "prod")
            _state.update {
                it.copy(
                    streamStatus = "queue",
                    launchPhase = if (pending.activeSession.isReadyForClaim()) "Resuming session" else loadingPhaseFor(pendingSession),
                    activeSession = pending.activeSession,
                    activeSessionDecision = null,
                    streamSession = pendingSession,
                    streamGame = gameForActiveSession(pending.activeSession) ?: pending.game.takeIf { pending.activeSession.appId == pending.launchAppId.toIntOrNull() },
                    activeStreamSettings = resumeSettings,
                    page = AppPage.Stream,
                    streamReturnPage = pending.returnPage,
                    streamLaunchMinimized = false,
                    error = null,
                    queuePosition = queueDisplayPosition(pendingSession),
                    queueAdActiveId = null,
                )
            }
            runCatching {
                resumeKnownActiveSession(token, pending.activeSession, resumeSettings, pending.baseUrl)
            }.onSuccess { readySession ->
                recordDebugEvent("stream", "Pending resume ready for native stream ${readySession.debugSummary()}")
                markSessionReadyForNativeStream(readySession, resumeSettings)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                recordDebugEvent("queue", "Pending resume failed error=${error.debugMessage()}")
                _state.update {
                    it.copy(
                        error = normalizeLaunchError(error, pending.game.title),
                        streamStatus = "idle",
                        activeStreamSettings = null,
                        streamReturnPage = null,
                        activeSessionDecision = null,
                        launchPhase = "",
                        streamLaunchMinimized = false,
                        queuePosition = null,
                        queueAdActiveId = null,
                        pendingStoreChoiceGame = null,
                        pendingPrintedWasteGame = null,
                        page = pending.returnPage,
                    )
                }
            }
        }
    }

    fun launchWithPrintedWaste(zoneUrl: String?) {
        val game = state.value.pendingPrintedWasteGame ?: return
        recordDebugEvent("queue", "PrintedWaste selection game=${game.title} zone=${hostForDebug(zoneUrl)} auto=${zoneUrl == null}")
        launchJob?.cancel()
        launchJob = null
        _state.update {
            it.copy(
                pendingPrintedWasteGame = null,
                printedWasteError = null,
                printedWasteLoading = false,
            )
        }
        play(game, streamingBaseUrlOverride = zoneUrl, skipPrintedWaste = true, skipStoreChoice = true)
    }

    private fun effectiveStreamSettings(): StreamSettings {
        return streamSettingsBeforeDeviceAdjustment().adjustedForDevice(state.value.codecReport)
    }

    private fun resumeSettingsForActiveSession(active: ActiveSessionInfo, requested: StreamSettings): StreamSettings {
        val resolution = active.resolution?.takeIf { parseResolutionPixelsOrNull(it) != null }
        return requested
            .let { settings ->
                if (resolution == null) {
                    settings
                } else {
                    settings.copy(
                        resolution = resolution,
                        aspectRatio = streamAspectRatioForResolution(resolution) ?: settings.aspectRatio,
                    )
                }
            }
            .let { settings -> active.fps?.takeIf { it > 0 }?.let { settings.copy(fps = it) } ?: settings }
            .withCodecColorCompatibility()
    }

    private fun gameForActiveSession(active: ActiveSessionInfo): GameInfo? =
        (state.value.games + state.value.libraryGames)
            .firstOrNull { game ->
                game.launchAppId == active.appId.toString() ||
                    game.variants.any { variant -> variant.id == active.appId.toString() }
            }

    private fun streamSettingsBeforeDeviceAdjustment(): StreamSettings {
        val snapshot = state.value
        return snapshot.settings.stream
            .withResolutionAllowed(snapshot.subscriptionInfo, snapshot.authSession?.user?.membershipTier)
            .withFpsAllowed(snapshot.subscriptionInfo, snapshot.authSession?.user?.membershipTier)
            .withHdrAllowed(snapshot.subscriptionInfo, snapshot.authSession?.user?.membershipTier)
            .withAndroidSettingsAvailability()
            .withCodecColorCompatibility()
    }

    private suspend fun resolveFallbackLaunchAppId(
        token: String,
        game: GameInfo?,
        active: ActiveSessionInfo?,
        baseUrl: String,
    ): String {
        if (game == null) {
            return active?.appId?.takeIf { it > 0 }?.toString()
                ?: error("Could not resolve appId for safe H264 retry.")
        }
        val selectedVariant = game.variants.getOrNull(game.selectedVariantIndex) ?: game.variants.firstOrNull()
        val candidateId = selectedVariant?.id ?: game.launchAppId ?: game.uuid ?: game.id
        return candidateId.takeIf { it.all(Char::isDigit) }
            ?: game.launchAppId?.takeIf { it.all(Char::isDigit) }
            ?: active?.appId?.takeIf { it > 0 }?.toString()
            ?: catalogRepository.resolveLaunchAppId(token, candidateId, baseUrl)
            ?: error("Could not resolve numeric appId for ${game.title}")
    }

    private fun String.isLikelyDirectServerUrl(): Boolean {
        val host = runCatching { Uri.parse(this).host.orEmpty() }.getOrDefault("")
        return Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)
    }

    fun dismissPrintedWasteSelector() {
        _state.update {
            it.copy(
                pendingPrintedWasteGame = null,
                printedWasteLoading = false,
                printedWasteError = null,
            )
        }
    }

    fun reportQueueAd(
        adId: String,
        action: String,
        watchedTimeInMs: Long? = null,
        pausedTimeInMs: Long? = null,
        cancelReason: String? = null,
        errorInfo: String? = null,
    ) {
        viewModelScope.launch {
            val auth = state.value.authSession ?: run {
                recordDebugEvent("ad", "Ignoring ad report without auth ad=${shortDebugId(adId)} action=$action")
                return@launch
            }
            val session = state.value.streamSession ?: run {
                recordDebugEvent("ad", "Ignoring ad report without session ad=${shortDebugId(adId)} action=$action")
                return@launch
            }
            val normalizedAction = action.lowercase()
            val isTerminalAction = normalizedAction == "finish" || normalizedAction == "cancel"
            recordDebugEvent(
                "ad",
                "Report action=$normalizedAction ad=${shortDebugId(adId)} session=${session.shortDebugId()} watched=${watchedTimeInMs ?: 0} reason=${cancelReason.orEmpty()}",
            )
            if (!isTerminalAction) {
                _state.update {
                    it.copy(queueAdActiveId = adId)
                }
            }
            runCatching {
                queueAdReportMutex.withLock {
                    val reportSession = state.value.streamSession
                        ?.takeIf { it.sessionId == session.sessionId }
                        ?: session
                    sessionRepository.reportSessionAd(
                        token = auth.tokens.idToken ?: auth.tokens.accessToken,
                        session = reportSession,
                        adId = adId,
                        action = normalizedAction,
                        settings = state.value.settings.stream,
                        watchedTimeInMs = watchedTimeInMs,
                        pausedTimeInMs = pausedTimeInMs ?: 0L,
                        cancelReason = cancelReason,
                        errorInfo = errorInfo,
                    )
                }
            }.onSuccess { updated ->
                recordDebugEvent("ad", "Report accepted action=$normalizedAction updated=${updated.debugSummary()}")
                _state.update { current ->
                    val previous = current.streamSession?.takeIf { it.sessionId == updated.sessionId } ?: session
                    val merged = mergeQueueSessionState(
                        previous,
                        updated,
                        preserveMissingAdState = !isTerminalAction,
                    )
                    current.copy(
                        streamSession = merged,
                        queuePosition = queueDisplayPosition(merged),
                        queueAdActiveId = if (isTerminalAction) {
                            nextSessionAdId(merged.adState, adId) ?: adId
                        } else {
                            chooseQueueAdActiveId(current.queueAdActiveId, merged)
                        },
                    )
                }
            }.onFailure { error ->
                recordDebugEvent("ad", "Report failed action=$normalizedAction ad=${shortDebugId(adId)} error=${error.debugMessage()}")
                _state.update { current ->
                    val currentSession = current.streamSession
                    if (normalizedAction == "finish" && currentSession?.adState != null) {
                        current.copy(
                            streamSession = currentSession.copy(
                                adState = currentSession.adState.copy(
                                    sessionAds = emptyList(),
                                    ads = emptyList(),
                                    serverSentEmptyAds = false,
                                ),
                            ),
                            queueAdActiveId = null,
                        )
                    } else {
                        current.copy(error = error.message ?: "Queue ad update failed")
                    }
                }
            }
        }
    }

    fun markStreamConnected() {
        recordDebugEvent("stream", "Native stream connected session=${state.value.streamSession?.shortDebugId().orEmpty()} game=${state.value.streamGame?.title.orEmpty()}")
        OpenNowAnalytics.capture(
            event = "stream_connected",
            properties = mapOf(
                "game_title" to (state.value.streamGame?.title ?: ""),
                "game_id" to (state.value.streamGame?.id ?: ""),
                "resolution" to (state.value.activeStreamSettings?.resolution ?: ""),
                "fps" to (state.value.activeStreamSettings?.fps ?: 0),
                "codec" to (state.value.activeStreamSettings?.codec?.name ?: ""),
            ),
        )
        _state.update { it.copy(streamStatus = "streaming", launchPhase = "") }
    }

    fun setAndroidPictureInPictureActive(active: Boolean) {
        _state.update { current ->
            if (current.androidPictureInPictureActive == active) current else current.copy(androidPictureInPictureActive = active)
        }
    }

    fun updateStreamRuntimeStats(stats: StreamRuntimeStats) {
        if (!stats.hasDebugValues()) return
        val now = System.currentTimeMillis()
        latestStreamRuntimeStats = TimedStreamRuntimeStats(
            capturedAtMs = now,
            sessionId = state.value.streamSession?.sessionId,
            stats = stats,
        )
        if (now - lastRuntimeStatsEventAtMs >= STREAM_RUNTIME_STATS_EVENT_INTERVAL_MS) {
            lastRuntimeStatsEventAtMs = now
            recordDebugEvent(
                "runtime",
                "stats ${stats.debugSummary()} device=${AndroidRuntimeDiagnostics.snapshot(getApplication()).debugSummary()}",
            )
        }
    }

    fun markStreamError(message: String) {
        recordDebugEvent("stream", "Native stream error message=${message.take(DEBUG_EVENT_MESSAGE_LIMIT)} session=${state.value.streamSession?.shortDebugId().orEmpty()}")
        OpenNowAnalytics.capture(
            event = "stream_error",
            properties = mapOf(
                "error_message" to message,
                "game_title" to (state.value.streamGame?.title ?: ""),
                "game_id" to (state.value.streamGame?.id ?: ""),
            ),
        )
        _state.update { it.copy(error = message, streamStatus = "idle", activeStreamSettings = null, launchPhase = "") }
    }

    fun recordNativeStreamState(message: String) {
        recordDebugEvent("native", "state=$message session=${state.value.streamSession?.shortDebugId().orEmpty()}")
    }

    fun recordLocalSafeVideoFallback(reason: String) {
        val currentSettings = state.value.activeStreamSettings ?: effectiveStreamSettings()
        val safeSettings = currentSettings.androidSafeVideoFallback()
        _state.update { current ->
            if (current.streamSession == null || current.streamStatus == "idle") {
                current
            } else {
                current.copy(activeStreamSettings = safeSettings)
            }
        }
        recordDebugEvent(
            "recovery",
            "Restarted local transport with safe video profile while keeping cloud session reason=${reason.take(DEBUG_EVENT_MESSAGE_LIMIT)} current=${currentSettings.debugSummary()} safe=${safeSettings.debugSummary()}",
        )
    }

    fun recordRuntimeResolutionChange(
        actualResolution: String,
        expectedResolution: String,
        serverNegotiatedFallback: Boolean,
    ) {
        val current = state.value
        val currentSettings = current.activeStreamSettings ?: effectiveStreamSettings()
        val noticeKey = listOf(
            current.streamGame?.id ?: current.activeSession?.appId?.toString() ?: current.streamSession?.sessionId.orEmpty(),
            streamSettingsSessionSignature(currentSettings),
            actualResolution,
            expectedResolution,
            serverNegotiatedFallback.toString(),
        ).joinToString("|")
        if (!runtimeResolutionNoticeKeys.add(noticeKey)) return
        val resolutionSource = if (serverNegotiatedFallback) {
            "Server negotiated fallback"
        } else {
            "Runtime video mode changed"
        }
        recordDebugEvent(
            "stream",
            "$resolutionSource actual=$actualResolution expected=$expectedResolution; keeping connected stream settings=${currentSettings.debugSummary()}",
        )
    }

    fun recoverStreamSession(reason: String) {
        if (launchJob?.isActive == true) {
            recordDebugEvent("recovery", "Ignored stream recovery while launch job is active reason=${reason.take(DEBUG_EVENT_MESSAGE_LIMIT)}")
            return
        }
        val initial = state.value
        val auth = initial.authSession ?: run {
            recordDebugEvent("recovery", "Recovery missing auth reason=${reason.take(DEBUG_EVENT_MESSAGE_LIMIT)}")
            markStreamError(reason)
            return
        }
        val initialSession = initial.streamSession ?: run {
            recordDebugEvent("recovery", "Recovery missing stream session reason=${reason.take(DEBUG_EVENT_MESSAGE_LIMIT)}")
            markStreamError(reason)
            return
        }
        val currentSettings = initial.activeStreamSettings ?: effectiveStreamSettings()
        recordDebugEvent("recovery", "Recovery requested reason=${reason.take(DEBUG_EVENT_MESSAGE_LIMIT)} session=${initialSession.debugSummary()} settings=${currentSettings.debugSummary()}")
        launchJob = viewModelScope.launch {
            val token = auth.tokens.idToken ?: auth.tokens.accessToken
            val snapshot = state.value
            val previousSession = snapshot.streamSession ?: initialSession
            val active = snapshot.activeSession
            val game = snapshot.streamGame
            val baseUrl = listOfNotNull(
                previousSession.streamingBaseUrl,
                active?.streamingBaseUrl,
                effectiveStreamingBaseUrl(auth),
            ).firstOrNull { !it.isLikelyDirectServerUrl() } ?: effectiveStreamingBaseUrl(auth)
            val returnPage = snapshot.streamReturnPage ?: snapshot.page.takeUnless { it == AppPage.Stream } ?: AppPage.Home

            _state.update {
                it.copy(
                    streamSession = null,
                    activeStreamSettings = currentSettings,
                    streamStatus = "connecting",
                    launchPhase = "Recovering stream",
                    page = AppPage.Stream,
                    streamReturnPage = returnPage,
                    streamLaunchMinimized = false,
                    error = null,
                    queuePosition = null,
                    queueAdActiveId = null,
                )
            }

            runCatching {
                val activeSessions = sessionRepository.getActiveSessions(token, baseUrl, currentSettings)
                recordDebugEvent("recovery", "Recovery active sessions count=${activeSessions.size} base=${hostForDebug(baseUrl)}")
                val resolvedAppId = runCatching {
                    resolveFallbackLaunchAppId(
                        token = token,
                        game = game,
                        active = active,
                        baseUrl = baseUrl,
                    ).toIntOrNull()
                }.getOrNull()
                val readyCandidate = activeSessionRecoveryCandidate(
                    sessions = activeSessions,
                    previousSessionId = previousSession.sessionId,
                    launchAppId = resolvedAppId,
                    settings = currentSettings,
                )
                if (readyCandidate?.sessionId == previousSession.sessionId && !readyCandidate.matchesStreamSettings(currentSettings)) {
                    recordDebugEvent(
                        "recovery",
                        "Reclaiming current session after local profile fallback active=${readyCandidate.debugSummary()} settings=${currentSettings.debugSummary()}",
                    )
                }
                val cachedCurrentSession = active?.takeIf {
                    it.sessionId == previousSession.sessionId && it.matchesStreamGeometry(currentSettings)
                }
                val fallbackCandidate = readyCandidate
                    ?: previousSession.toRecoveryActiveSession(
                        appId = resolvedAppId ?: active?.appId ?: 0,
                        fallbackActive = cachedCurrentSession,
                    )?.takeIf { it.matchesStreamGeometry(currentSettings) }
                    ?: error("The running session could not be found anymore, so recovery was not possible.")
                recordDebugEvent("recovery", "Claiming recovery candidate ${fallbackCandidate.debugSummary()}")
                claimActiveSessionOrContinuePolling(token, fallbackCandidate, currentSettings)
            }.onSuccess { readySession ->
                recordDebugEvent("recovery", "Recovery claim ready ${readySession.debugSummary()}")
                _state.update {
                    it.copy(
                        streamSession = readySession,
                        activeSession = readySession.toActiveRecoverySession(active),
                        activeStreamSettings = currentSettings,
                        streamStatus = "connecting",
                        launchPhase = "Reconnecting stream",
                        streamLaunchMinimized = false,
                        queuePosition = null,
                        queueAdActiveId = null,
                        page = AppPage.Stream,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                recordDebugEvent("recovery", "Recovery failed error=${error.debugMessage()}")
                _state.update {
                    it.copy(
                        error = normalizeLaunchError(error, game?.title),
                        streamStatus = "idle",
                        activeStreamSettings = null,
                        streamReturnPage = null,
                        launchPhase = "",
                        streamLaunchMinimized = false,
                        queuePosition = null,
                        queueAdActiveId = null,
                        page = returnPage,
                    )
                }
            }
        }
    }

    fun handleExternalLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val uri = intent.data
        if (localTvConnector.isPairUri(uri)) {
            if (state.value.androidTvProfile) {
                _state.update { it.copy(error = "Pairing links must be opened on the Android phone") }
            } else if (uri != null) {
                localTvConnector.pairPhone(uri)
            }
            return
        }
        if (authRepository.handleOAuthRedirect(uri)) {
            _state.update { it.copy(launchPhase = LOGIN_PHASE_GETTING_TOKENS, error = null) }
            return
        }
        val id = extractExternalLaunchId(intent)
        if (id.isNullOrBlank()) return
        val allGames = state.value.games + state.value.libraryGames
        val game = allGames.firstOrNull { game ->
            game.id == id || game.uuid == id || game.launchAppId == id || game.variants.any { it.id == id }
        } ?: GameInfo(
            id = id,
            uuid = id,
            launchAppId = id.takeIf { it.all(Char::isDigit) },
            title = intent.getStringExtra("title") ?: "Game $id",
            selectedVariantIndex = 0,
            variants = listOf(GameVariant(id = id, store = "Unknown")),
        )
        play(game)
    }

    private fun extractExternalLaunchId(intent: Intent): String? {
        val uri = intent.data
        return externalLaunchIdFromParts(
            extras = listOf(
                intent.getStringExtra("id"),
                intent.getStringExtra("appId"),
                intent.getStringExtra("launchAppId"),
            ),
            scheme = uri?.scheme,
            host = uri?.host,
            pathSegments = uri?.pathSegments.orEmpty(),
            schemeSpecificPart = uri?.schemeSpecificPart,
            queryParameters = mapOf(
                "id" to uri?.let { runCatching { it.getQueryParameter("id") }.getOrNull() },
                "appId" to uri?.let { runCatching { it.getQueryParameter("appId") }.getOrNull() },
                "launchAppId" to uri?.let { runCatching { it.getQueryParameter("launchAppId") }.getOrNull() },
            ),
        )
    }

    private fun GameInfo.withSelectedVariant(variantId: String): GameInfo {
        val selectedIndex = variants.indexOfFirst { it.id == variantId }
        return if (selectedIndex >= 0) copy(selectedVariantIndex = selectedIndex) else this
    }

    fun debugLogText(): String {
        val snapshot = state.value
        val session = snapshot.streamSession
        val codecReport = snapshot.codecReport
        return buildString {
            appendLine("OpenNOW Android diagnostics")
            appendLine(snapshot.androidUpdate.debugHeaderLine())
            appendLine("page=${snapshot.page} initializing=${snapshot.initializing} loadingGames=${snapshot.loadingGames}")
            appendLine("user=${snapshot.authSession?.user?.displayName.orEmpty()} tier=${snapshot.subscriptionInfo?.membershipTier ?: snapshot.authSession?.user?.membershipTier.orEmpty()} provider=${snapshot.authSession?.provider?.code.orEmpty()}")
            appendLine("streamStatus=${snapshot.streamStatus} launchPhase=${snapshot.launchPhase} queuePosition=${snapshot.queuePosition}")
            appendLine("streamGame=${snapshot.streamGame?.title.orEmpty()} selectedGame=${snapshot.selectedGame?.title.orEmpty()}")
            appendLine("sessionId=${session?.sessionId.orEmpty()} sessionStatus=${session?.status} seatSetupStep=${session?.seatSetupStep} serverIp=${session?.serverIp.orEmpty()} base=${session?.streamingBaseUrl.orEmpty()}")
            appendLine("adsRequired=${isSessionAdsRequired(session?.adState)} ads=${sessionAdItems(session?.adState).size} activeAd=${snapshot.queueAdActiveId.orEmpty()} queuePaused=${session?.adState?.isQueuePaused}")
            appendLine("adMessage=${session?.adState?.message.orEmpty()} grace=${session?.adState?.gracePeriodSeconds} serverSentEmptyAds=${session?.adState?.serverSentEmptyAds}")
            appendLine("negotiated=${session?.negotiatedStreamProfile?.debugSummary().orEmpty()} requestedFeatures=${session?.requestedStreamingFeatures?.debugSummary().orEmpty()} finalizedFeatures=${session?.finalizedStreamingFeatures?.debugSummary().orEmpty()}")
            appendLine("printedWaste.loading=${snapshot.printedWasteLoading} queueZones=${snapshot.printedWasteQueue.size} mappingZones=${snapshot.printedWasteMapping.size} pings=${snapshot.printedWastePings.size} error=${snapshot.printedWasteError.orEmpty()}")
            appendLine("settings.resolution=${snapshot.settings.stream.resolution} fps=${snapshot.settings.stream.fps} codec=${snapshot.settings.stream.codec} bitrate=${snapshot.settings.stream.maxBitrateMbps}")
            appendLine("settings.preset=${snapshot.settings.streamPreset} recommendation=${deviceRecommendation?.debugSummary() ?: "pending"}")
            snapshot.activeStreamSettings?.let { active ->
                appendLine("active.resolution=${active.resolution} fps=${active.fps} codec=${active.codec} bitrate=${active.maxBitrateMbps}")
            }
            appendLine("input.keyboardLayout=${snapshot.settings.stream.keyboardLayout} touch=${snapshot.settings.androidTouch}")
            appendLine("codec.native=${codecReport?.nativeRuntimeSummary.orEmpty()} lowPower=${codecReport?.lowPowerGpuProfile} tv=${codecReport?.androidTvProfile}")
            appendLine("device.runtime=${AndroidRuntimeDiagnostics.snapshot(getApplication()).debugSummary()}")
            appendLine("stream.runtime.latest=${latestStreamRuntimeStats?.debugSummary(System.currentTimeMillis()) ?: "empty"}")
            codecReport?.capabilities?.forEach { cap ->
                appendLine("codec.${cap.codec}: decoder=${cap.decoderName ?: "none"} hardware=${cap.hardwareDecoder} nativeAvailable=${cap.nativeDecoderAvailable ?: "unknown"} webRtc=${cap.webRtcDecoderName ?: "none"} webRtcAvailable=${cap.webRtcDecoderAvailable ?: "unknown"} webRtcHardware=${cap.webRtcHardwareDecoderAvailable ?: "unknown"} encoder=${cap.encoderName ?: "none"}")
            }
            appendLine(DisplayRefreshDiagnostics.snapshot())
            appendLine(NativeInputDiagnostics.snapshot())
            appendLine(OpenNowHttpDiagnostics.snapshot())
            snapshot.error?.let { appendLine("error=$it") }
            val events = debugEventSnapshot()
            appendLine("events.count=${events.size} max=$DEBUG_EVENT_LIMIT")
            if (events.isEmpty()) {
                appendLine("events=(empty)")
            } else {
                val formatter = DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.US)
                events.forEachIndexed { index, event ->
                    appendLine("event.${index + 1} ${formatter.format(Date(event.timestampMs))} [${event.category}] ${event.message}")
                }
            }
            val payloads = debugPayloadSnapshot()
            appendLine("advancedJson.count=${payloads.size} max=$DEBUG_PAYLOAD_LIMIT")
            if (payloads.isEmpty()) {
                appendLine("advancedJson=(empty)")
            } else {
                val formatter = DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.US)
                payloads.forEachIndexed { index, payload ->
                    appendLine("advancedJson.${index + 1} ${formatter.format(Date(payload.timestampMs))} [${payload.operation}] ${payload.method} http=${payload.statusCode} url=${payload.url}")
                    if (payload.requestBody.isNotBlank()) {
                        appendLine("request:")
                        appendLine(payload.requestBody)
                    }
                    appendLine("response:")
                    appendLine(payload.body)
                }
            }
        }
    }

    fun sanitizedDebugLogText(): String = sanitizeDiagnosticExport(debugLogText())

    fun debugLogFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "opennow-android-logs-$timestamp.txt"
    }

    private companion object {
        const val SETTINGS_DIAGNOSTIC_TAP_COUNT = 10
        const val SETTINGS_DIAGNOSTIC_TAP_WINDOW_MS = 8_000L
        const val TV_INITIAL_CATALOG_PAGE_COUNT = 1
        const val TV_INITIAL_CATALOG_GAME_LIMIT = 120
        const val TV_LAYOUT_PROFILE_VERSION = 1
    }

    private suspend fun refreshAfterAuth(session: AuthSession, keepRefreshVisibleWithCache: Boolean = false) {
        _state.update { it.copy(loadingGames = true, error = null) }
        val baseUrl = effectiveStreamingBaseUrl(session)
        val token = session.tokens.idToken ?: session.tokens.accessToken
        val initialCatalogSearch = state.value.catalogSearch
        val initialCatalogSortId = state.value.catalogSortId
        val initialCatalogFilterIds = state.value.catalogFilterIds
        val (cachedMain, cachedLibrary, unboundedCachedCatalog) = withContext(Dispatchers.IO) {
            Triple(
                catalogCacheStore.loadMainGames(session.user.userId, baseUrl),
                catalogCacheStore.loadLibraryGames(session.user.userId, baseUrl),
                catalogCacheStore.loadCatalog(
                    userId = session.user.userId,
                    providerStreamingBaseUrl = baseUrl,
                    searchQuery = initialCatalogSearch,
                    sortId = initialCatalogSortId,
                    filterIds = initialCatalogFilterIds,
                ),
            )
        }
        val cachedCatalog = if (androidTvProfile) {
            unboundedCachedCatalog?.copy(games = unboundedCachedCatalog.games.take(TV_INITIAL_CATALOG_GAME_LIMIT))
        } else {
            unboundedCachedCatalog
        }
        if (cachedMain != null || cachedLibrary != null || cachedCatalog != null) {
            val cachedMergedLibrary = withContext(Dispatchers.Default) {
                mergeKnownLibraryGames(
                    cachedLibrary.orEmpty(),
                    cachedMain.orEmpty(),
                    cachedCatalog?.games.orEmpty(),
                )
            }
            _state.update {
                it.copy(
                    games = cachedMain ?: it.games,
                    libraryGames = cachedMergedLibrary.ifEmpty { cachedLibrary ?: it.libraryGames },
                    catalogResult = cachedCatalog ?: it.catalogResult,
                    loadingGames = keepRefreshVisibleWithCache,
                    error = null,
                )
            }
        }
        val subscriptionJob = viewModelScope.launch {
            val sub = withContext(Dispatchers.IO) {
                runCatching {
                    val vpcId = catalogRepository.getVpcId(token, session.provider.streamingServiceUrl)
                    subscriptionRepository.fetchSubscription(token, session.user.userId, vpcId)
                }.getOrNull()
            }
            val enrichedSession = persistSubscriptionTier(session, sub)
            _state.update { current ->
                current.copy(
                    authSession = current.authSession
                        ?.takeIf { it.user.userId == enrichedSession.user.userId }
                        ?.let { enrichedSession }
                        ?: current.authSession,
                    savedAccounts = savedAccountsSnapshot(),
                    subscriptionInfo = sub,
                )
            }
        }
        activeSubscriptionJob = subscriptionJob
        val accountConnectorsJob = viewModelScope.launch {
            _state.update { it.copy(loadingAccountConnectors = true) }
            val connectors = withContext(Dispatchers.IO) {
                runCatching { accountConnectorRepository.fetchConnectors(token) }.getOrDefault(emptyList())
            }
            _state.update { it.copy(accountConnectors = connectors, loadingAccountConnectors = false) }
        }
        val regionsJob = viewModelScope.launch {
            val regions = withContext(Dispatchers.IO) {
                runCatching { fetchDynamicRegions(http, token, session.provider.streamingServiceUrl).first }.getOrDefault(emptyList())
            }
            _state.update { it.copy(regions = regions) }
        }
        gamesJob?.cancel()
        gamesJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val includeSupplementalPublicVariants = !androidTvProfile
                    val catalog = catalogRepository.browseCatalog(
                        token = token,
                        providerStreamingBaseUrl = baseUrl,
                        searchQuery = initialCatalogSearch,
                        sortId = initialCatalogSortId,
                        filterIds = initialCatalogFilterIds,
                        maxPages = if (androidTvProfile) TV_INITIAL_CATALOG_PAGE_COUNT else 3,
                        includeSupplementalPublicVariants = includeSupplementalPublicVariants,
                    )
                    // MainV2 is a ~600KB personalized panel response on this TV and then
                    // triggers additional metadata batches. The bounded catalog already
                    // contains the launchable TV store data; retain MainV2 on mobile.
                    val main = if (androidTvProfile) {
                        catalog.games
                    } else {
                        catalogRepository.fetchMainGames(token, baseUrl, includeSupplementalPublicVariants)
                    }
                    val library = catalogRepository.fetchLibraryGames(token, baseUrl, includeSupplementalPublicVariants)
                    val mergedLibrary = mergeKnownLibraryGames(library, main, catalog.games)
                    catalogCacheStore.saveMainGames(session.user.userId, baseUrl, main)
                    catalogCacheStore.saveLibraryGames(session.user.userId, baseUrl, mergedLibrary)
                    catalogCacheStore.saveCatalog(
                        userId = session.user.userId,
                        providerStreamingBaseUrl = baseUrl,
                        searchQuery = initialCatalogSearch,
                        sortId = initialCatalogSortId,
                        filterIds = initialCatalogFilterIds,
                        result = catalog,
                    )
                    Triple(main, mergedLibrary, catalog)
                }
            }.onSuccess { (main, library, catalog) ->
                _state.update {
                    it.copy(
                        games = main,
                        libraryGames = library,
                        catalogResult = catalog,
                        loadingGames = false,
                        error = null,
                    )
                }
                refreshActiveSession()
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                val hasUsableCache = cachedMain != null || cachedLibrary != null || cachedCatalog != null
                _state.update { it.copy(loadingGames = false, error = if (hasUsableCache) null else error.message ?: "Failed to load games") }
            }
        }
        subscriptionJob.join()
        accountConnectorsJob.join()
        regionsJob.join()
    }

    private fun refreshCatalogDebounced() {
        gamesJob?.cancel()
        gamesJob = viewModelScope.launch {
            val auth = state.value.authSession ?: return@launch
            val baseUrl = effectiveStreamingBaseUrl(auth)
            val searchQuery = state.value.catalogSearch
            val sortId = state.value.catalogSortId
            val filterIds = state.value.catalogFilterIds
            val unboundedCachedCatalog = withContext(Dispatchers.IO) {
                catalogCacheStore.loadCatalog(
                    userId = auth.user.userId,
                    providerStreamingBaseUrl = baseUrl,
                    searchQuery = searchQuery,
                    sortId = sortId,
                    filterIds = filterIds,
                )
            }
            val cachedCatalog = if (androidTvProfile) {
                unboundedCachedCatalog?.copy(games = unboundedCachedCatalog.games.take(TV_INITIAL_CATALOG_GAME_LIMIT))
            } else {
                unboundedCachedCatalog
            }
            _state.update {
                it.copy(
                    loadingGames = cachedCatalog == null,
                    catalogResult = cachedCatalog ?: it.catalogResult,
                    games = cachedCatalog?.games ?: it.games,
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    catalogRepository.browseCatalog(
                        token = auth.tokens.idToken ?: auth.tokens.accessToken,
                        providerStreamingBaseUrl = baseUrl,
                        searchQuery = searchQuery,
                        sortId = sortId,
                        filterIds = filterIds,
                        maxPages = if (androidTvProfile) TV_INITIAL_CATALOG_PAGE_COUNT else 3,
                        includeSupplementalPublicVariants = !androidTvProfile,
                    )
                }
            }.onSuccess { result ->
                val mergedLibrary = withContext(Dispatchers.Default) {
                    mergeKnownLibraryGames(state.value.libraryGames, result.games)
                }
                withContext(Dispatchers.IO) {
                    catalogCacheStore.saveCatalog(
                        userId = auth.user.userId,
                        providerStreamingBaseUrl = baseUrl,
                        searchQuery = searchQuery,
                        sortId = sortId,
                        filterIds = filterIds,
                        result = result,
                    )
                }
                _state.update {
                    it.copy(
                        catalogResult = result,
                        loadingGames = false,
                        games = result.games,
                        libraryGames = mergedLibrary.ifEmpty { it.libraryGames },
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _state.update { it.copy(error = if (cachedCatalog != null) null else error.message ?: "Catalog refresh failed", loadingGames = false) }
            }
        }
    }

    private suspend fun showPrintedWasteSelector(game: GameInfo) {
        recordDebugEvent("queue", "Loading PrintedWaste selector game=${game.title}")
        _state.update {
            it.copy(
                pendingStoreChoiceGame = null,
                pendingPrintedWasteGame = game,
                printedWasteLoading = true,
                printedWasteError = null,
                printedWasteQueue = emptyMap(),
                printedWasteMapping = emptyMap(),
                printedWastePings = emptyMap(),
            )
        }
        loadPrintedWasteQueue(game)
    }

    private suspend fun loadPrintedWasteQueue(game: GameInfo) {
        recordDebugEvent("queue", "Fetching PrintedWaste queue data game=${game.title}")
        _state.update {
            it.copy(
                pendingStoreChoiceGame = null,
                pendingPrintedWasteGame = game,
                printedWasteLoading = true,
                printedWasteError = null,
            )
        }
        runCatching {
            coroutineScope {
                val queue = async { printedWasteRepository.fetchQueue() }
                val mapping = async { printedWasteRepository.fetchServerMapping() }
                val queueData = queue.await()
                val mappingData = mapping.await()
                val regions = queueData
                    .filter { (zoneId, _) -> isStandardPrintedWasteZoneId(zoneId) && mappingData[zoneId]?.nuked != true }
                    .map { (zoneId, _) ->
                        StreamRegion(name = zoneId, url = printedWasteZoneUrlForId(zoneId), pingMs = null)
                    }
                val pings = printedWasteRepository.pingRegions(regions).associate { it.url to it.pingMs }
                Triple(queueData, mappingData, pings)
            }
        }.onSuccess { (queue, mapping, pings) ->
            val usableZones = queue
                .filter { (zoneId, _) -> isStandardPrintedWasteZoneId(zoneId) && mapping[zoneId]?.nuked != true }
                .keys
            val bestZone = usableZones
                .mapNotNull { zoneId ->
                    val zone = queue[zoneId] ?: return@mapNotNull null
                    val url = printedWasteZoneUrlForId(zoneId)
                    Triple(zoneId, zone.QueuePosition, pings[url])
                }
                .minWithOrNull(
                    compareBy<Triple<String, Int, Long?>>(
                        { it.third ?: Long.MAX_VALUE },
                        { it.second },
                    ),
                )
            recordDebugEvent(
                "queue",
                "PrintedWaste queue loaded zones=${queue.size} usable=${usableZones.size} best=${bestZone?.first.orEmpty()} bestQueue=${bestZone?.second ?: 0} bestPing=${bestZone?.third ?: -1}",
            )
            _state.update {
                it.copy(
                    printedWasteQueue = queue,
                    printedWasteMapping = mapping,
                    printedWastePings = pings,
                    printedWasteLoading = false,
                    printedWasteError = null,
                )
            }
        }.onFailure { error ->
            recordDebugEvent("queue", "PrintedWaste queue load failed error=${error.debugMessage()}")
            _state.update {
                it.copy(
                    printedWasteLoading = false,
                    printedWasteError = error.message ?: "PrintedWaste queue data unavailable",
                )
            }
        }
    }

    private suspend fun refreshActiveSession() {
        val auth = state.value.authSession ?: return
        val settings = effectiveStreamSettings()
        val token = auth.tokens.idToken ?: auth.tokens.accessToken
        val active = runCatching { sessionRepository.getActiveSessions(token, effectiveStreamingBaseUrl(auth), settings) }
            .getOrDefault(emptyList())
            .firstOrNull { it.status in setOf(1, 2, 3) && it.matchesStreamSettings(settings) }
        _state.update { it.copy(activeSession = active) }
    }

    private suspend fun resumeKnownActiveSession(
        token: String,
        active: ActiveSessionInfo,
        settings: StreamSettings,
        baseUrl: String,
    ): SessionInfo {
        if (!active.matchesStreamSettings(settings)) {
            recordDebugEvent(
                "queue",
                "Explicit resume is using active session settings active=${active.debugSummary()} requested=${settings.debugSummary()}",
            )
        }
        if (active.isReadyForClaim()) {
            recordDebugEvent("queue", "Active session already ready for claim ${active.debugSummary()}")
            _state.update { it.copy(launchPhase = "Resuming session") }
            return claimActiveSessionOrContinuePolling(token, active, settings)
        }

        val pending = active.toPendingSession(zone = "prod")
        recordDebugEvent("queue", "Hydrating active session before resume ${pending.debugSummary()}")
        val hydrated = runCatching {
            sessionRepository.pollSession(
                token = token,
                streamingBaseUrl = active.streamingBaseUrl ?: baseUrl,
                serverIp = active.serverIp,
                zone = "prod",
                sessionId = active.sessionId,
                clientId = null,
                deviceId = null,
                settings = settings,
            )
        }.getOrElse { error ->
            recordDebugEvent("queue", "Resume hydrate failed session=${pending.shortDebugId()} error=${error.debugMessage()}")
            pending
        }
        val latest = mergeQueueSessionState(pending, hydrated)
        recordDebugEvent("queue", "Resume hydrate result ${latest.debugSummary()}")
        _state.update {
            it.copy(
                streamSession = latest,
                launchPhase = loadingPhaseFor(latest),
                queuePosition = queueDisplayPosition(latest),
                queueAdActiveId = chooseQueueAdActiveId(it.queueAdActiveId, latest),
            )
        }
        if (latest.isReadyForStream()) {
            val hydratedActive = active.copy(
                status = latest.status,
                queuePosition = latest.queuePosition,
                seatSetupStep = latest.seatSetupStep,
                streamingBaseUrl = latest.streamingBaseUrl ?: active.streamingBaseUrl,
                serverIp = latest.serverIp,
                signalingUrl = latest.signalingUrl,
            )
            recordDebugEvent("queue", "Resume session became ready ${latest.debugSummary()}")
            _state.update { it.copy(launchPhase = "Resuming session") }
            return claimActiveSessionOrContinuePolling(token, hydratedActive, settings)
        }
        return pollUntilReady(token, latest, settings)
    }

    private suspend fun claimActiveSessionOrContinuePolling(
        token: String,
        active: ActiveSessionInfo,
        settings: StreamSettings,
    ): SessionInfo {
        return try {
            sessionRepository.claimSession(token, active, settings)
        } catch (error: SessionClaimNotReadyException) {
            val fallback = active.toPendingSession(zone = "prod")
            val latest = error.latestSession?.let { mergeQueueSessionState(fallback, it) } ?: fallback
            recordDebugEvent("queue", "Claim stayed pending; continuing queue polling ${latest.debugSummary()}")
            _state.update {
                it.copy(
                    streamSession = latest,
                    activeStreamSettings = settings,
                    launchPhase = loadingPhaseFor(latest),
                    queuePosition = queueDisplayPosition(latest),
                    queueAdActiveId = chooseQueueAdActiveId(it.queueAdActiveId, latest),
                )
            }
            pollUntilReady(token, latest, settings)
        }
    }

    private suspend fun pollUntilReady(token: String, created: SessionInfo, settings: StreamSettings): SessionInfo {
        var latest = created
        var pollCount = 0
        recordDebugEvent("queue", "Begin polling ${latest.debugSummary()}")
        _state.update {
            it.copy(
                streamSession = latest,
                launchPhase = loadingPhaseFor(latest),
                queuePosition = queueDisplayPosition(latest),
                queueAdActiveId = chooseQueueAdActiveId(it.queueAdActiveId, latest),
            )
        }
        while (!latest.isReadyForStream()) {
            val waitMs = if (shouldWaitForQueueAdPlayback(latest.adState)) 30_000L else 2_000L
            if (waitMs > 2_000L) {
                recordDebugEvent(
                    "queue",
                    "Waiting for queue ad playback session=${latest.shortDebugId()} ads=${sessionAdItems(latest.adState).size} paused=${latest.adState?.isQueuePaused} message=${latest.adState?.message.orEmpty()}",
                )
            }
            if (waitMs > 2_000L) {
                var elapsedMs = 0L
                while (elapsedMs < waitMs) {
                    kotlinx.coroutines.delay(500L)
                    elapsedMs += 500L
                    state.value.streamSession
                        ?.takeIf { it.sessionId == latest.sessionId }
                        ?.let { latest = mergeQueueSessionState(latest, it) }
                    if (!shouldWaitForQueueAdPlayback(latest.adState) || latest.isReadyForStream()) {
                        break
                    }
                }
            } else {
                kotlinx.coroutines.delay(waitMs)
            }
            if (latest.isReadyForStream()) {
                break
            }
            pollCount += 1
            val polled = try {
                sessionRepository.pollSession(
                    token = token,
                    streamingBaseUrl = latest.streamingBaseUrl ?: effectiveStreamingBaseUrl(),
                    serverIp = latest.serverIp,
                    zone = latest.zone,
                    sessionId = latest.sessionId,
                    clientId = latest.clientId,
                    deviceId = latest.deviceId,
                    settings = settings,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                recordDebugEvent("queue", "Poll #$pollCount failed due to network error: ${e.message}. Retrying in 2 seconds...")
                kotlinx.coroutines.delay(2_000L)
                continue
            }
            latest = mergeQueueSessionState(latest, polled)
            recordDebugEvent("queue", "Poll #$pollCount result ${latest.debugSummary()}")
            _state.update {
                it.copy(
                    streamSession = latest,
                    launchPhase = loadingPhaseFor(latest),
                    queuePosition = queueDisplayPosition(latest),
                    queueAdActiveId = chooseQueueAdActiveId(it.queueAdActiveId, latest),
                )
            }
        }
        recordDebugEvent("queue", "Polling complete after $pollCount polls ${latest.debugSummary()}")
        return latest
    }

    private fun effectiveStreamingBaseUrl(sessionOverride: AuthSession? = null): String {
        val settings = state.value.settings
        val auth = sessionOverride ?: state.value.authSession
        return settings.stream.region.trim().ifBlank { auth?.provider?.streamingServiceUrl ?: state.value.selectedProvider.streamingServiceUrl }
    }

    private fun shouldUsePrintedWasteQueue(auth: AuthSession): Boolean {
        if (state.value.settings.hideServerSelector) return false
        if (!auth.provider.code.equals("NVIDIA", ignoreCase = true)) return false
        if (!isFreeTier()) return false
        return !isAllianceStreamingBaseUrl(effectiveStreamingBaseUrl(auth))
    }

    private fun isFreeTier(): Boolean {
        val tier = state.value.subscriptionInfo?.membershipTier ?: state.value.authSession?.user?.membershipTier
        return tier.isNullOrBlank() || tier.equals("FREE", ignoreCase = true)
    }

    private fun isAllianceStreamingBaseUrl(streamingBaseUrl: String): Boolean {
        val host = runCatching { Uri.parse(streamingBaseUrl).host.orEmpty() }.getOrDefault("")
        return host.isNotBlank() && !host.endsWith(".nvidiagrid.net", ignoreCase = true)
    }

    private fun isStandardPrintedWasteZoneId(zoneId: String): Boolean =
        zoneId.startsWith("NP-") && !zoneId.startsWith("NPA-")

    private fun printedWasteZoneUrlForId(zoneId: String): String =
        "https://${zoneId.lowercase()}.cloudmatchbeta.nvidiagrid.net/"

    private fun chooseQueueAdActiveId(currentId: String?, session: SessionInfo?): String? {
        val ads = sessionAdItems(session?.adState)
        if (!isSessionAdsRequired(session?.adState) || ads.isEmpty()) return null
        return ads.firstOrNull { it.adId == currentId }?.adId ?: ads.first().adId
    }

    private fun loadingPhaseFor(session: SessionInfo): String =
        when {
            queueDisplayPosition(session) != null || session.seatSetupStep == 1 -> "Queue"
            session.status == 0 || session.status == 1 -> "Checking queue"
            else -> "Setting up rig"
        }

    private fun ActiveSessionInfo.toPendingSession(zone: String): SessionInfo {
        val host = serverIp.orEmpty()
        val signalingServer = when {
            host.isBlank() -> ""
            host.contains(":") -> host
            else -> "$host:443"
        }
        return SessionInfo(
            sessionId = sessionId,
            status = status,
            queuePosition = queuePosition,
            seatSetupStep = seatSetupStep,
            zone = zone,
            streamingBaseUrl = streamingBaseUrl,
            serverIp = host,
            signalingServer = signalingServer,
            signalingUrl = signalingUrl ?: host.takeIf { it.isNotBlank() }?.let { "wss://$it:443/nvst/" }.orEmpty(),
            gpuType = gpuType,
            deviceId = authStore.stableDeviceId(),
        )
    }

    private fun SessionInfo.toRecoveryActiveSession(appId: Int, fallbackActive: ActiveSessionInfo?): ActiveSessionInfo? {
        if (serverIp.isBlank() || appId <= 0) return null
        return ActiveSessionInfo(
            sessionId = sessionId,
            appId = appId,
            gpuType = gpuType ?: fallbackActive?.gpuType,
            status = status.takeIf { it in setOf(2, 3) } ?: 2,
            queuePosition = queuePosition,
            seatSetupStep = seatSetupStep,
            streamingBaseUrl = streamingBaseUrl ?: fallbackActive?.streamingBaseUrl,
            serverIp = serverIp,
            signalingUrl = signalingUrl.takeIf { it.isNotBlank() } ?: fallbackActive?.signalingUrl,
            resolution = fallbackActive?.resolution,
            fps = fallbackActive?.fps,
            settingsSignature = fallbackActive?.settingsSignature,
        )
    }

    private fun SessionInfo.toActiveRecoverySession(fallbackActive: ActiveSessionInfo?): ActiveSessionInfo? {
        val appId = fallbackActive?.takeIf { it.sessionId == sessionId }?.appId ?: fallbackActive?.appId ?: return null
        return toRecoveryActiveSession(appId, fallbackActive)
    }

    private fun shouldSendAccountLinked(game: GameInfo, variant: GameVariant?): Boolean {
        return shouldLaunchWithAccountLinked(game, variant)
    }

    private fun normalizeLaunchError(error: Throwable, gameTitle: String? = null): String =
        normalizeLaunchErrorMessage(error, gameTitle)

    private fun AuthSession.toSavedAccount(): SavedAccount =
        SavedAccount(
            userId = user.userId,
            displayName = user.displayName,
            email = user.email,
            avatarUrl = user.avatarUrl,
            membershipTier = user.membershipTier,
            providerCode = provider.code,
        )

    private fun AuthSession.withSubscriptionTier(subscription: SubscriptionInfo?): AuthSession {
        val tier = subscription?.membershipTier?.takeIf { it.isNotBlank() } ?: return this
        return if (user.membershipTier == tier) this else copy(user = user.copy(membershipTier = tier))
    }

    private fun persistSubscriptionTier(session: AuthSession, subscription: SubscriptionInfo?): AuthSession {
        val enriched = session.withSubscriptionTier(subscription)
        if (enriched != session) authStore.upsertSession(enriched)
        return enriched
    }

    private fun savedAccountsSnapshot(): List<SavedAccount> =
        authStore.state.value.sessions.map { session -> session.toSavedAccount() }
}

internal fun externalLaunchIdFromParts(
    extras: List<String?>,
    scheme: String?,
    host: String?,
    pathSegments: List<String>,
    schemeSpecificPart: String?,
    queryParameters: Map<String, String?>,
): String? {
    val normalizedScheme = scheme.orEmpty().lowercase(Locale.US)
    val uriCandidates = if (normalizedScheme == "opennow") {
        buildList {
            add(queryParameters["id"])
            add(queryParameters["appId"])
            add(queryParameters["launchAppId"])
            val routeHost = host.orEmpty()
            if (routeHost.equals("launch", ignoreCase = true)) {
                add(pathSegments.firstOrNull())
            } else if (routeHost.isNotBlank()) {
                add(routeHost)
            }
            if (pathSegments.firstOrNull()?.equals("launch", ignoreCase = true) == true) {
                add(pathSegments.getOrNull(1))
            }
            add(pathSegments.lastOrNull())
            add(schemeSpecificPart)
        }
    } else {
        emptyList()
    }
    return (extras + uriCandidates).firstNotNullOfOrNull { it.normalizedExternalLaunchId() }
}

private fun String?.normalizedExternalLaunchId(): String? {
    val trimmed = this?.trim()?.trim('/', '?', '#') ?: return null
    if (trimmed.isBlank() || trimmed.equals("launch", ignoreCase = true)) return null
    val cleaned = trimmed
        .removePrefix("//")
        .substringBefore('#')
        .substringBefore('?')
        .trim('/')
    if (cleaned.isBlank() || cleaned.equals("launch", ignoreCase = true)) return null
    return cleaned.split('/').lastOrNull { it.isNotBlank() && !it.equals("launch", ignoreCase = true) }
}

private fun shortDebugId(value: String?): String {
    val text = value.orEmpty()
    if (text.length <= 12) return text
    return "${text.take(6)}...${text.takeLast(4)}"
}

private fun hostForDebug(url: String?): String =
    runCatching { Uri.parse(url.orEmpty()).host.orEmpty() }
        .getOrDefault("")
        .ifBlank { url.orEmpty().take(80) }

private fun Throwable.debugMessage(): String {
    val type = javaClass.simpleName.ifBlank { "Throwable" }
    val text = message.orEmpty()
        .lineSequence()
        .joinToString(" ") { it.trim() }
        .take(DEBUG_EVENT_MESSAGE_LIMIT)
    return if (text.isBlank()) type else "$type: $text"
}

private fun StreamSettings.debugSummary(): String =
    "res=$resolution aspect=$aspectRatio fps=$fps bitrate=$maxBitrateMbps codec=$codec color=${colorQuality.name} hdr=$hdrEnabled l4s=$enableL4S gsync=$enableCloudGsync sharp=$streamSharpeningEnabled"

private fun StreamRuntimeStats.hasDebugValues(): Boolean =
    bitrateKbps != null ||
        pingMs != null ||
        fps != null ||
        !resolution.isNullOrBlank() ||
        !codec.isNullOrBlank()

private fun StreamRuntimeStats.debugSummary(): String =
    "bitrateKbps=${bitrateKbps ?: 0} pingMs=${pingMs ?: -1} fps=${fps ?: 0} resolution=${resolution.orEmpty()} codec=${codec.orEmpty()}"

private fun TimedStreamRuntimeStats.debugSummary(nowMs: Long): String {
    val formatter = DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.US)
    val ageMs = (nowMs - capturedAtMs).coerceAtLeast(0L)
    return "capturedAt=${formatter.format(Date(capturedAtMs))} ageMs=$ageMs session=${shortDebugId(sessionId)} ${stats.debugSummary()}"
}

private fun SessionInfo.shortDebugId(): String = shortDebugId(sessionId)

private fun SessionInfo.debugSummary(): String =
    "id=${shortDebugId()} status=$status ready=${isReadyForStream()} queue=${queuePosition ?: "-"} seat=${seatSetupStep ?: "-"} adsRequired=${isSessionAdsRequired(adState)} ads=${sessionAdItems(adState).size} paused=${adState?.isQueuePaused} base=${hostForDebug(streamingBaseUrl)} server=${serverIp.take(80)}"

private fun ActiveSessionInfo.shortDebugId(): String = shortDebugId(sessionId)

private fun ActiveSessionInfo.debugSummary(): String =
    "id=${shortDebugId()} app=$appId status=$status queue=${queuePosition ?: "-"} seat=${seatSetupStep ?: "-"} base=${hostForDebug(streamingBaseUrl)} server=${serverIp.orEmpty().take(80)} res=${resolution.orEmpty()} fps=${fps ?: 0}"

private fun NegotiatedStreamProfile.debugSummary(): String =
    "res=${resolution.orEmpty()} fps=${fps ?: 0} codec=${codec?.name.orEmpty()} color=${colorQuality?.name.orEmpty()} l4s=$enableL4S gsync=$enableCloudGsync reflex=$enableReflex"

private fun StreamingFeatures.debugSummary(): String =
    "reflex=$reflex bitDepth=$bitDepth gsync=$cloudGsync chroma=$chromaFormat l4s=$enabledL4S hdr=$trueHdr"
