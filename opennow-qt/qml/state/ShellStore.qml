pragma Singleton
import QtQuick

QtObject {
    id: root
    property var settings: ({})
    property string previewThemePack: ""
    property string accessibilityMessage: ""
    property string settingsRequestId: ""
    property string lastError: ""
    property var focusPositions: ({})
    property var providers: []
    property var authSession: null
    property var authChallenge: null
    property string authState: "idle"
    property string authMessage: ""
    property var catalogGames: []
    property var selectedGame: null
    property int catalogTotalCount: 0
    property string catalogState: "idle"
    property string catalogSource: "public"
    property string sessionPersistence: "none"
    property bool authRestorePending: true
    property bool pendingStaySignedIn: true
    signal consoleSurfaceRequested(bool enabled)
    property string consoleSurfaceRequestId: ""
    property bool consoleSurfaceConfirmedValue: false
    property bool consoleSurfaceDesiredValue: false
    property bool consoleSurfaceRequestValue: false
    property bool consoleSurfaceInitialized: false
    property string consoleSurfaceError: ""
    property bool desktopUiActive: false
    property var subscription: null
    property var regions: []
    property var regionPingResults: ({})
    property string regionPingMessage: ""
    property var savedAccounts: []
    property var gameAccounts: []
    property string gameAccountsState: "idle"
    property string gameAccountMessage: ""
    property var accountLinkAttempt: null
    property var storageLocations: []
    property string storageMessage: ""
    property var mediaItems: []
    property string mediaRootPath: ""
    property string mediaState: "idle"
    property string mediaMessage: ""
    property var diagnostics: ({entries: []})
    property string diagnosticsMessage: ""
    property var updaterState: ({status: "idle", currentVersion: "0.5.4", canCheck: true})
    property var releaseHighlights: ({})
    property var socialCapabilities: ({
        friendsAvailable: false,
        presenceAvailable: false,
        invitesAvailable: false,
        localControllerJoin: true,
        reason: qsTr("Checking provider social capabilities…")
    })
    property string reportingMessage: ""
    property string reportingState: "idle"
    property string pinMode: "unlock"
    property string pinTargetUserId: ""
    property string pinTargetName: qsTr("Profile")
    property string pinMessage: ""
    property var activeSession: null
    property var remoteSessions: []
    property var pendingLaunchParams: null
    property var pendingDirectLaunch: null
    property var conflictSession: null
    property bool forceNewAfterStop: false
    property var streamer: null
    property var streamerDetection: ({available: false, availableCodecs: [], capabilities: ({})})
    property string streamerDetectionMessage: qsTr("Checking native codec support…")
    property string streamState: "idle"
    property string streamMessage: ""
    property int streamerRestartAttempts: 0
    property int sessionReconnectAttempts: 0
    property int streamerRestartRecoveryCount: 0
    property int sessionRecoveryCount: 0
    property var guidePagesVisited: []
    property string regionsVpcId: ""
    property string providersRequestId: ""
    property string authSessionRequestId: ""
    property string activeSessionRequestId: ""
    property string remoteSessionDiscoveryRequestId: ""
    property string remoteSessionsRequestId: ""
    property string sessionClaimRequestId: ""
    property bool sessionClaimIsRecovery: false
    property string streamCreateRequestId: ""
    property string streamPollRequestId: ""
    property string streamStopRequestId: ""
    property string streamerStartRequestId: ""
    property string streamerDetectRequestId: ""
    property string streamerStatusRequestId: ""
    property string streamerStopRequestId: ""
    property bool streamerStopExpected: false
    property var artworkUrls: ({})
    property var artworkPending: ({})
    property var artworkRequestSources: ({})
    property var artworkRetrySources: ({})
    property string catalogRequestId: ""
    property string deviceStartRequestId: ""
    property string devicePollRequestId: ""
    property string deviceCompleteRequestId: ""
    property string logoutRequestId: ""
    property string logoutAllRequestId: ""
    property string subscriptionRequestId: ""
    property string regionsRequestId: ""
    property string regionPingRequestId: ""
    property string accountsRequestId: ""
    property string accountSwitchRequestId: ""
    property string accountRemoveRequestId: ""
    property string pinRequestId: ""
    property string gameAccountsRequestId: ""
    property string gameAccountActionRequestId: ""
    property string accountLinkStartRequestId: ""
    property string accountLinkPollRequestId: ""
    property string storageLocationsRequestId: ""
    property string storageResetRequestId: ""
    property string sessionAdRequestId: ""
    property string mediaRequestId: ""
    property string mediaDeleteRequestId: ""
    property string mediaRecordingTargetRequestId: ""
    property string streamRecordingStartRequestId: ""
    property string streamRecordingStopRequestId: ""
    property string pendingRecordingPath: ""
    property string pendingRecordingThumbnailPath: ""
    property bool streamRecordingActive: false
    property double streamRecordingElapsedMs: 0
    property double streamRecordingStartedAtMs: 0
    property string diagnosticsRequestId: ""
    property string diagnosticsExportRequestId: ""
    property string acceptanceExportRequestId: ""
    property string updaterStateRequestId: ""
    property string updaterCheckRequestId: ""
    property string updaterHighlightsRequestId: ""
    property string updaterDownloadRequestId: ""
    property string updaterInstallRequestId: ""
    property string socialCapabilitiesRequestId: ""
    property string discordRequestId: ""
    property double streamStartedAtMs: 0
    property string telemetryRequestId: ""
    property string feedbackRequestId: ""
    property string bugReportRequestId: ""
    property string streamInputPauseRequestId: ""
    property string streamControlRequestId: ""
    property string streamControlAction: ""
    property string streamControlMessage: ""
    readonly property var streamSurface: StreamSurfaceController.surface
    property int overlayRequestGeneration: 0
    property int screenshotRequestGeneration: 0
    property int recordingToggleRequestGeneration: 0
    property int shortcutActionGeneration: 0
    property bool antiAfkEnabled: false
    property var lastSessionReport: null
    property bool desiredStreamInputPaused: false
    property bool currentStreamInputPaused: false
    property bool streamInputStateKnown: false
    readonly property bool ready: CoreClient.state === "ready"
    readonly property bool signedIn: authSession !== null
    readonly property string sessionStatus: activeSession
        ? String(activeSession.phase || activeSession.status || "requesting") : "idle"
    readonly property string streamerStatus: streamer ? String(streamer.status || "unknown") : "stopped"
    readonly property var negotiatedStreamProfile: activeSession && activeSession.negotiatedStreamProfile
        ? activeSession.negotiatedStreamProfile : ({})
    readonly property int streamerRecoveryCount: streamerRestartRecoveryCount
        + Number(streamer && streamer.deviceRecoveryCount || 0)
    readonly property string sessionPersistenceMessage: {
        if (sessionPersistence === "unavailable")
            return qsTr("A saved NVIDIA session could not be opened. Sign in once more to store it on this PC.")
        if (sessionPersistence === "memory-only")
            return qsTr("This session is memory-only and will not last after you quit.")
        return ""
    }
    readonly property bool streamBusy: streamCreateRequestId !== "" || streamStopRequestId !== ""

    signal fullscreenToggleRequested()
    readonly property var resumableSession: {
        if (root.activeSession) {
            const localStatus = Number(root.activeSession.status || 0)
            const localPhase = String(root.activeSession.phase || "").toLowerCase()
            if (localStatus === 2 || localStatus === 3
                    || localPhase === "ready" || localPhase === "streaming")
                return root.activeSession
        }
        const sessions = root.remoteSessions || []
        for (let index = 0; index < sessions.length; ++index) {
            const status = Number(sessions[index] && sessions[index].status || 0)
            if (status === 2 || status === 3)
                return sessions[index]
        }
        return null
    }
    readonly property string microphoneState: "unavailable"
    readonly property bool microphoneEnabled: false
    readonly property string microphoneLabel: qsTr("Unavailable")
    readonly property string microphoneDescription: qsTr("Microphone upstream is unavailable for NVST sessions")

    property Timer devicePollTimer: Timer {
        interval: root.authChallenge ? Math.max(1000, Number(root.authChallenge.intervalSeconds || 5) * 1000) : 5000
        repeat: true
        running: false
        onTriggered: root.pollDeviceLogin()
    }

    property Timer streamPollTimer: Timer {
        interval: 1500
        repeat: true
        running: false
        onTriggered: root.pollStreamingSession()
    }

    property Timer remoteSessionRefreshTimer: Timer {
        interval: 30000
        repeat: true
        running: root.signedIn && AppController.route !== "stream"
        onTriggered: root.refreshRemoteSessions()
    }

    property Timer antiAfkPulseTimer: Timer {
        interval: 240000
        repeat: true
        running: root.antiAfkEnabled && root.activeSession && root.streamer
            && root.streamer.status === "streaming"
        onTriggered: root.controlStream("anti-afk-pulse")
    }

    property Timer streamerStatusTimer: Timer {
        interval: 1000
        repeat: true
        running: false
        onTriggered: root.refreshStreamerStatus()
    }

    property Timer artworkRetryTimer: Timer {
        interval: 30000
        repeat: true
        running: Object.keys(root.artworkRetrySources).length > 0
        onTriggered: {
            const sources = Object.keys(root.artworkRetrySources)
            for (let index = 0; index < sources.length; ++index)
                root.requestArtwork(sources[index])
        }
    }

    property Timer streamRecordingTimer: Timer {
        interval: 250
        repeat: true
        running: root.streamRecordingActive
        onTriggered: root.streamRecordingElapsedMs = Math.max(0, Date.now() - root.streamRecordingStartedAtMs)
    }

    property Timer accountLinkPollTimer: Timer {
        interval: 1200
        repeat: true
        running: false
        onTriggered: root.pollAccountLink()
    }

    property Timer streamerRestartTimer: Timer {
        interval: Math.min(4000, 500 * Math.pow(2, root.streamerRestartAttempts))
        repeat: false
        onTriggered: root.startNativeStreamer()
    }

    property Timer autoUpdateCheckTimer: Timer {
        interval: 15000
        repeat: false
        onTriggered: root.checkForUpdates()
    }

    function refreshSettings() {
        if (!ready)
            return
        settingsRequestId = CoreClient.request("settings.get", {})
    }

    function initializeServices() {
        if (!ready)
            return
        if (!signedIn)
            authRestorePending = true
        refreshSettings()
        providersRequestId = CoreClient.request("auth.providers.list", {}, 25000)
        authSessionRequestId = CoreClient.request("auth.session.get", {})
        activeSessionRequestId = CoreClient.request("session.active.get", {})
        if (streamerStatusRequestId === "")
            streamerStatusRequestId = CoreClient.request("streamer.status.get", {})
        updaterStateRequestId = CoreClient.request("updater.state.get", {})
        socialCapabilitiesRequestId = CoreClient.request("social.capabilities.get", {})
        refreshCatalog()
    }

    function refreshStreamerDetection() {
        if (!ready || streamerDetectRequestId !== "")
            return
        if (streamer && streamer.status && streamer.status !== "stopped" && streamer.status !== "error") {
            if (streamer.capabilities) {
                streamerDetection = Object.assign({}, streamerDetection, {
                    available: true,
                    capabilities: streamer.capabilities,
                    availableCodecs: codecNamesFromCapabilities(streamer.capabilities)
                })
            }
            return
        }
        streamerDetectionMessage = qsTr("Checking native codec support…")
        streamerDetectRequestId = CoreClient.request("streamer.detect", {}, 15000)
    }

    function refreshRemoteSessions() {
        if (!ready || !signedIn || activeSession || remoteSessionDiscoveryRequestId !== ""
                || remoteSessionsRequestId !== "")
            return
        remoteSessionDiscoveryRequestId = CoreClient.request("session.remote.list", {}, 30000)
    }

    function sessionGameTitle(session) {
        if (!session)
            return ""
        const appId = String(session.appId || "")
        const games = catalogGames || []
        for (let gameIndex = 0; gameIndex < games.length; ++gameIndex) {
            const game = games[gameIndex]
            if (String(game.launchAppId || "") === appId)
                return String(game.title || "")
            const variants = game.variants || []
            for (let variantIndex = 0; variantIndex < variants.length; ++variantIndex) {
                if (String(variants[variantIndex] && variants[variantIndex].id || "") === appId)
                    return String(game.title || "")
            }
        }
        return ""
    }

    function selectGameForSession(session) {
        if (!session)
            return
        const title = sessionGameTitle(session)
        if (!title)
            return
        const games = catalogGames || []
        for (let index = 0; index < games.length; ++index) {
            if (String(games[index].title || "") === title) {
                selectedGame = games[index]
                return
            }
        }
    }

    function resumeActiveSession() {
        const session = resumableSession
        if (!session || sessionClaimRequestId !== "")
            return
        selectGameForSession(session)
        if (activeSession && String(activeSession.sessionId || "") === String(session.sessionId || "")) {
            if (AppController.route !== "stream")
                AppController.navigate("stream")
            startNativeStreamer()
            return
        }
        conflictSession = session
        streamState = "resuming"
        streamMessage = qsTr("Resuming your active GeForce NOW session…")
        sessionClaimIsRecovery = false
        sessionClaimRequestId = CoreClient.request("session.claim", {
            sessionId: session.sessionId,
            streamingBaseUrl: session.streamingBaseUrl,
            appId: String(session.appId || "0"),
            recoveryMode: true
        }, 35000)
        AppController.navigate("inserting")
    }

    function codecNamesFromCapabilities(capabilities) {
        const result = []
        const backends = capabilities && capabilities.videoBackends
            ? capabilities.videoBackends : []
        for (let backendIndex = 0; backendIndex < backends.length; ++backendIndex) {
            const backend = backends[backendIndex]
            if (!backend.available)
                continue
            const codecs = backend.codecs || []
            for (let codecIndex = 0; codecIndex < codecs.length; ++codecIndex) {
                const codec = codecs[codecIndex]
                const name = String(codec.codec || "").toLowerCase()
                if (codec.available && ["h264", "h265", "av1"].indexOf(name) >= 0
                        && result.indexOf(name) < 0)
                    result.push(name)
            }
        }
        return result
    }

    function codecAvailable(codec) {
        const name = String(codec || "h264").toLowerCase()
        const codecs = streamerDetection && streamerDetection.availableCodecs
            ? streamerDetection.availableCodecs : ["h264"]
        return codecs.indexOf(name) >= 0
    }

    function availableCodecValues() {
        const result = []
        if (codecAvailable("h264")) {
            result.push("auto")
            result.push("h264")
        }
        if (codecAvailable("h265"))
            result.push("h265")
        if (codecAvailable("av1"))
            result.push("av1")
        // Keep the persisted choice visible when the child is missing or the selected decoder
        // policy has no compatible codec. Prelaunch validation still rejects it before CloudMatch.
        return result.length ? result : [String(settings.codec || "auto").toLowerCase()]
    }

    function availableCodecLabels() {
        const values = availableCodecValues()
        const result = []
        for (let index = 0; index < values.length; ++index) {
            result.push(values[index] === "auto" ? "Auto"
                : values[index] === "h264" ? "H.264"
                : values[index] === "h265" ? "H.265" : "AV1")
        }
        return result
    }

    function refreshCatalog(searchQuery) {
        if (!ready || catalogRequestId !== "")
            return
        catalogState = catalogGames.length > 0 ? "refreshing" : "loading"
        catalogSource = signedIn ? "account-library" : "public"
        catalogRequestId = CoreClient.request(signedIn ? "catalog.library.list" : "catalog.public.list", {
            limit: signedIn ? 1000 : 360,
            searchQuery: searchQuery || ""
        }, 30000)
    }

    function reloadCatalogForSession() {
        if (catalogRequestId !== "") {
            CoreClient.cancel(catalogRequestId)
            catalogRequestId = ""
        }
        catalogGames = []
        catalogState = "idle"
        refreshCatalog("")
    }

    function refreshAccountServices() {
        if (!ready || !signedIn)
            return
        if (subscriptionRequestId === "")
            subscriptionRequestId = CoreClient.request("account.subscription.get", {}, 30000)
        if (regionsRequestId === "")
            regionsRequestId = CoreClient.request("network.regions.list", {}, 30000)
        if (accountsRequestId === "")
            accountsRequestId = CoreClient.request("auth.accounts.list", {})
        if (gameAccountsRequestId === "") {
            gameAccountsState = gameAccounts.length ? "refreshing" : "loading"
            gameAccountsRequestId = CoreClient.request("account.connections.list", {}, 30000)
        }
        if (remoteSessionsRequestId === "" && !activeSession && !pendingLaunchParams)
            remoteSessionsRequestId = CoreClient.request("session.remote.list", {}, 30000)
    }

    function refreshRegions() {
        if (!ready || !signedIn || regionsRequestId !== "")
            return
        regionsRequestId = CoreClient.request("network.regions.list", {}, 30000)
    }

    function pingRegions() {
        if (!ready || regions.length === 0 || regionPingRequestId !== "")
            return
        regionPingMessage = qsTr("Measuring region latency…")
        regionPingRequestId = CoreClient.request("network.regions.ping", {
            regions: regions
        }, 20000)
    }

    function refreshGameAccounts() {
        if (!ready || !signedIn || gameAccountsRequestId !== "")
            return
        gameAccountsState = gameAccounts.length ? "refreshing" : "loading"
        gameAccountsRequestId = CoreClient.request("account.connections.list", {}, 30000)
    }

    function startAccountLink(provider) {
        if (!ready || accountLinkStartRequestId !== "")
            return
        gameAccountMessage = qsTr("Opening %1 sign-in…").arg(provider)
        accountLinkStartRequestId = CoreClient.request("account.connections.link.start", { provider: provider }, 30000)
    }

    function pollAccountLink() {
        if (!ready || !accountLinkAttempt || accountLinkPollRequestId !== "")
            return
        accountLinkPollRequestId = CoreClient.request("account.connections.link.poll", {
            attemptId: accountLinkAttempt.attemptId
        }, 10000)
    }

    function syncGameAccount(provider) {
        if (!ready || gameAccountActionRequestId !== "")
            return
        gameAccountMessage = qsTr("Starting library sync…")
        gameAccountActionRequestId = CoreClient.request("account.connections.sync", { provider: provider }, 30000)
    }

    function unlinkGameAccount(provider) {
        if (!ready || gameAccountActionRequestId !== "")
            return
        gameAccountMessage = qsTr("Disconnecting account…")
        gameAccountActionRequestId = CoreClient.request("account.connections.unlink", { provider: provider }, 30000)
    }

    function refreshStorageLocations() {
        if (!ready || !signedIn || storageLocationsRequestId !== "")
            return
        const addon = subscription && subscription.storageAddon ? subscription.storageAddon : ({})
        storageMessage = qsTr("Loading storage regions…")
        storageLocationsRequestId = CoreClient.request("account.storage.locations", {
            serverRegionId: subscription ? subscription.serverRegionId : "",
            currentRegionCode: addon.regionCode || "",
            currentRegionName: addon.regionName || "",
            locale: "en_US"
        }, 30000)
    }

    function resetPersistentStorage(regionCode) {
        if (!ready || !signedIn || storageResetRequestId !== "")
            return
        storageMessage = qsTr("Resetting persistent storage…")
        storageResetRequestId = CoreClient.request("account.storage.reset", {
            storageRegion: regionCode || null,
            confirmed: true
        }, 30000)
    }

    function refreshMedia() {
        if (!ready || mediaRequestId !== "")
            return
        mediaState = mediaItems.length ? "refreshing" : "loading"
        mediaRequestId = CoreClient.request("media.list", {}, 15000)
    }

    function deleteMedia(item) {
        if (!ready || !item || mediaDeleteRequestId !== "")
            return
        mediaMessage = qsTr("Deleting ") + item.fileName + "…"
        mediaDeleteRequestId = CoreClient.request("media.delete", {
            kind: item.kind,
            id: item.id,
            confirmed: true
        }, 15000)
    }

    function refreshDiagnostics() {
        if (!ready || diagnosticsRequestId !== "")
            return
        diagnosticsMessage = qsTr("Loading redacted diagnostics…")
        diagnosticsRequestId = CoreClient.request("diagnostics.snapshot", {})
    }

    function exportDiagnostics() {
        if (!ready || diagnosticsExportRequestId !== "")
            return
        diagnosticsMessage = qsTr("Creating redacted diagnostic export…")
        diagnosticsExportRequestId = CoreClient.request("diagnostics.export", {}, 15000)
    }

    function recordGuidePage(page) {
        const allowed = ["guide-session", "guide-controls", "guide-media", "guide-shortcuts"]
        if (allowed.indexOf(page) < 0 || guidePagesVisited.indexOf(page) >= 0)
            return
        guidePagesVisited = guidePagesVisited.concat([page])
    }

    function exportAcceptanceEvidence() {
        if (!ready || acceptanceExportRequestId !== "")
            return
        diagnosticsMessage = qsTr("Creating machine-verifiable live evidence…")
        acceptanceExportRequestId = CoreClient.request("acceptance.export", {
            windowSystem: Qt.platform.pluginName,
            shell: {
                streamerRecoveryCount: streamerRecoveryCount,
                sessionRecoveryCount: sessionRecoveryCount,
                guidePagesVisited: guidePagesVisited
            }
        }, 120000)
    }

    function checkForUpdates() {
        if (!ready || updaterCheckRequestId !== "")
            return
        updaterState = Object.assign({}, updaterState, {
            status: "checking",
            message: qsTr("Checking GitHub Releases…"),
            canCheck: false
        })
        updaterCheckRequestId = CoreClient.request("updater.check", {
            channel: settings.updateChannel || "stable"
        }, 30000)
    }

    function downloadUpdate() {
        if (!ready || updaterDownloadRequestId !== "" || !updaterState.canDownload)
            return
        updaterDownloadRequestId = CoreClient.request("updater.download", {}, 300000)
        updaterState = Object.assign({}, updaterState, {
            status: "downloading",
            message: qsTr("Downloading and verifying the signed update…"),
            canDownload: false,
            canCheck: false
        })
    }

    function installUpdate() {
        if (!ready || updaterInstallRequestId !== "" || !updaterState.canInstall)
            return
        updaterInstallRequestId = CoreClient.request("updater.install", {confirmed: true}, 30000)
    }

    function syncTelemetry() {
        if (!ready || telemetryRequestId !== "")
            return
        telemetryRequestId = CoreClient.request("telemetry.sync", {}, 30000)
    }

    function submitFeedback(category, message) {
        if (!ready || feedbackRequestId !== "")
            return
        reportingState = "submitting"
        reportingMessage = qsTr("Sending feedback…")
        feedbackRequestId = CoreClient.request("feedback.submit", {
            category: category,
            message: message,
            includeSystemInfo: true
        }, 30000)
    }

    function submitBugReport(title, description, includeDiagnostics) {
        if (!ready || bugReportRequestId !== "")
            return
        reportingState = "submitting"
        reportingMessage = qsTr("Uploading bug report…")
        bugReportRequestId = CoreClient.request("bug_report.submit", {
            title: title,
            description: description,
            includeDiagnostics: includeDiagnostics
        }, 60000)
    }

    function switchAccount(userId, pin) {
        if (ready && accountSwitchRequestId === "")
            accountSwitchRequestId = CoreClient.request("auth.accounts.switch", {
                userId: userId,
                pin: pin || ""
            }, 30000)
    }

    function removeAccount(userId) {
        if (ready && accountRemoveRequestId === "")
            accountRemoveRequestId = CoreClient.request("auth.accounts.remove", { userId: userId })
    }

    function openPin(mode, account) {
        pinMode = mode
        pinTargetUserId = account && account.userId ? account.userId : (authSession ? authSession.user.userId : "")
        pinTargetName = account && account.displayName ? account.displayName : (authSession ? authSession.user.displayName : "Profile")
        pinMessage = qsTr("")
        AppController.navigate("profile-pin")
    }

    function submitPin(pin) {
        if (!ready || pinRequestId !== "" || !pinTargetUserId)
            return
        pinMessage = qsTr("Checking PIN…")
        if (pinMode === "unlock") {
            switchAccount(pinTargetUserId, pin)
            return
        }
        const method = pinMode === "clear" ? "auth.pin.clear" : "auth.pin.set"
        const params = pinMode === "clear"
            ? { userId: pinTargetUserId, currentPin: pin }
            : { userId: pinTargetUserId, pin: pin }
        pinRequestId = CoreClient.request(method, params, 30000)
    }

    function openGame(game) {
        selectedGame = game
        AppController.navigateFromLastPrimary("game-detail")
    }

    function selectGameVariant(index) {
        if (!selectedGame)
            return
        const variants = selectedGame.variants || []
        if (!variants.length)
            return
        const boundedIndex = Math.max(0, Math.min(variants.length - 1, Number(index)))
        const nextGame = Object.assign({}, selectedGame)
        nextGame.selectedVariantIndex = boundedIndex
        selectedGame = nextGame
        const variant = variants[boundedIndex]
        accessibilityMessage = qsTr("%1 platform selected").arg(String(variant.store || qsTr("Unknown")))
            + (Boolean(variant.inLibrary) ? qsTr(" · owned") : qsTr(" · not owned"))
    }

    function gameIdentity(game) {
        if (!game)
            return ""
        return String(game.uuid || game.id || game.launchAppId || "")
    }

    function isFavorite(game) {
        const id = gameIdentity(game)
        return id !== "" && (settings.favoriteGameIds || []).indexOf(id) >= 0
    }

    function toggleFavorite(game) {
        const id = gameIdentity(game)
        if (!id)
            return
        const favorites = (settings.favoriteGameIds || []).slice(0)
        const index = favorites.indexOf(id)
        if (index >= 0) {
            removeFromHome(game)
            return
        }
        addToHome(game)
    }

    function addToHome(game) {
        const id = gameIdentity(game)
        if (!id)
            return
        const favorites = (settings.favoriteGameIds || []).slice(0)
        if (favorites.indexOf(id) >= 0)
            return
        const firstTile = favorites.length === 0
        favorites.push(id)
        if (firstTile) {
            const sizes = Object.assign({}, settings.homeTileSizes || ({}))
            if (!sizes[id])
                sizes[id] = "wide"
            setSetting("homeTileSizes", sizes)
        }
        setSetting("favoriteGameIds", favorites)
        accessibilityMessage = qsTr("Added to Home")
    }

    function removeFromHome(game) {
        const id = gameIdentity(game)
        if (!id)
            return
        const favorites = (settings.favoriteGameIds || []).slice(0)
        const index = favorites.indexOf(id)
        if (index < 0)
            return
        favorites.splice(index, 1)
        const sizes = Object.assign({}, settings.homeTileSizes || ({}))
        delete sizes[id]
        setSetting("homeTileSizes", sizes)
        setSetting("favoriteGameIds", favorites)
        accessibilityMessage = qsTr("Removed from Home")
    }

    function setHomeOrder(ids) {
        const known = settings.favoriteGameIds || []
        const normalized = []
        for (let index = 0; index < ids.length; ++index) {
            const id = String(ids[index] || "")
            if (id && known.indexOf(id) >= 0 && normalized.indexOf(id) < 0)
                normalized.push(id)
        }
        for (let index = 0; index < known.length; ++index) {
            if (normalized.indexOf(known[index]) < 0)
                normalized.push(known[index])
        }
        setSetting("favoriteGameIds", normalized)
        accessibilityMessage = qsTr("Home layout saved")
    }

    function homeTileSize(game) {
        const id = gameIdentity(game)
        return id && settings.homeTileSizes && settings.homeTileSizes[id] === "wide"
            ? "wide" : "square"
    }

    function setHomeTileSize(game, size) {
        const id = gameIdentity(game)
        if (!id)
            return
        const sizes = Object.assign({}, settings.homeTileSizes || ({}))
        sizes[id] = size === "wide" ? "wide" : "square"
        setSetting("homeTileSizes", sizes)
        accessibilityMessage = size === "wide" ? qsTr("Wide Home tile") : qsTr("Square Home tile")
    }

    function acceptDirectLaunch(appId, title) {
        pendingDirectLaunch = {
            appId: String(appId || ""),
            title: String(title || "").trim()
        }
        resolveDirectLaunch()
    }

    function gameMatchesDirectLaunch(game, request) {
        const requestedId = String(request.appId || "")
        if (requestedId !== "") {
            if (String(game.launchAppId || "") === requestedId
                    || String(game.appId || "") === requestedId
                    || String(game.id || "") === requestedId)
                return true
            const variants = game.variants || []
            for (let index = 0; index < variants.length; ++index) {
                if (String(variants[index].id || "") === requestedId)
                    return true
            }
        }
        return request.title !== ""
            && String(game.title || "").toLocaleLowerCase() === request.title.toLocaleLowerCase()
    }

    function resolveDirectLaunch() {
        if (!pendingDirectLaunch)
            return
        if (catalogState !== "ready") {
            if (ready && catalogRequestId === "")
                refreshCatalog(pendingDirectLaunch.title)
            return
        }
        let match = null
        for (let index = 0; index < catalogGames.length; ++index) {
            if (gameMatchesDirectLaunch(catalogGames[index], pendingDirectLaunch)) {
                match = catalogGames[index]
                break
            }
        }
        if (!match) {
            lastError = qsTr("The requested GeForce NOW game was not found in the current catalog.")
            pendingDirectLaunch = null
            AppController.navigate("library")
            return
        }
        selectedGame = match
        if (!signedIn) {
            AppController.navigate("sign-in")
            return
        }
        pendingDirectLaunch = null
        launchSelectedGame(true)
    }

    function selectedLaunchAppId() {
        if (!selectedGame)
            return ""
        const variants = selectedGame.variants || []
        const index = Math.max(0, Number(selectedGame.selectedVariantIndex || 0))
        const variantId = variants.length > index ? String(variants[index].id || "") : ""
        if (/^\d+$/.test(variantId))
            return variantId
        const launchId = String(selectedGame.launchAppId || "")
        return /^\d+$/.test(launchId) ? launchId : ""
    }

    function launchSelectedGame(directConsoleMode) {
        if (!signedIn) {
            AppController.navigate("sign-in")
            return
        }
        const appId = selectedLaunchAppId()
        if (!appId) {
            streamState = "error"
            streamMessage = qsTr("This game does not expose a numeric GeForce NOW launch ID.")
            lastError = streamMessage
            return
        }
        if (!ready || streamBusy)
            return
        streamerRestartAttempts = 0
        sessionReconnectAttempts = 0
        streamerRestartRecoveryCount = 0
        sessionRecoveryCount = 0
        guidePagesVisited = []
        antiAfkEnabled = false
        const variants = selectedGame.variants || []
        const selectedVariantIndex = Math.max(0, Number(selectedGame.selectedVariantIndex || 0))
        const selectedVariant = variants.length > selectedVariantIndex ? variants[selectedVariantIndex] : null
        const params = {
            appId: appId,
            title: selectedGame.title || "GeForce NOW game",
            supportsInGameSettingsPersistence: Boolean(selectedVariant && selectedVariant.supportsInGameSettingsPersistence),
            accountLinked: Boolean(selectedVariant && selectedVariant.inLibrary),
            appLaunchMode: Boolean(settings.controllerMode || settings.launchInConsoleMode || directConsoleMode)
                ? "gamepadFriendly" : "default"
        }
        const configuredRegion = String(settings.region || "")
        for (let index = 0; index < regions.length; ++index) {
            const region = regions[index]
            if (configuredRegion === region.name || configuredRegion === region.url) {
                params.zone = region.name
                params.streamingBaseUrl = region.url
                break
            }
        }
        pendingLaunchParams = params
        streamState = "checking"
        streamMessage = qsTr("Checking for an active GeForce NOW session…")
        lastError = qsTr("")
        activeSession = null
        remoteSessionsRequestId = CoreClient.request("session.remote.list", params, 30000)
        AppController.navigate("inserting")
    }

    function createPendingSession() {
        if (!pendingLaunchParams || !ready || streamCreateRequestId !== "")
            return
        streamState = "requesting"
        streamMessage = qsTr("Requesting a cloud gaming seat…")
        streamCreateRequestId = CoreClient.request("session.create", pendingLaunchParams, 35000)
    }

    function resolveSessionConflict(choice) {
        AppController.showOverlay("")
        if (choice === "cancel") {
            pendingLaunchParams = null
            conflictSession = null
            streamState = "idle"
            streamMessage = qsTr("")
            AppController.navigateFromLastPrimary("game-detail")
            return
        }
        if (choice === "resume") {
            if (!conflictSession)
                return
            streamState = "resuming"
            streamMessage = qsTr("Resuming your existing cloud session…")
            sessionClaimIsRecovery = false
            sessionClaimRequestId = CoreClient.request("session.claim", {
                sessionId: conflictSession.sessionId,
                streamingBaseUrl: conflictSession.streamingBaseUrl,
                appId: String(conflictSession.appId || "0"),
                recoveryMode: true
            }, 35000)
            return
        }
        if (choice === "new") {
            if (!conflictSession) {
                createPendingSession()
                return
            }
            forceNewAfterStop = true
            streamState = "stopping"
            streamMessage = qsTr("Closing the previous cloud session…")
            streamStopRequestId = CoreClient.request("session.stop", {
                sessionId: conflictSession.sessionId,
                streamingBaseUrl: conflictSession.streamingBaseUrl,
                serverIp: conflictSession.serverIp || ""
            }, 35000)
        }
    }

    function inspectRemoteSessions(result) {
        remoteSessions = result.sessions || []
        if (remoteSessions.length === 0) {
            createPendingSession()
            return
        }
        const wantedAppId = pendingLaunchParams ? Number(pendingLaunchParams.appId || 0) : 0
        conflictSession = remoteSessions[0]
        for (let index = 0; index < remoteSessions.length; ++index) {
            if (Number(remoteSessions[index].appId || 0) === wantedAppId) {
                conflictSession = remoteSessions[index]
                break
            }
        }
        streamState = "conflict"
        streamMessage = qsTr("You already have a GeForce NOW session running.")
        AppController.showOverlay("session-conflict")
    }

    function normalizedStreamingSession(session) {
        if (!session)
            return null
        const normalized = Object.assign({}, session)
        if (session.negotiatedStreamProfile) {
            const profile = Object.assign({}, session.negotiatedStreamProfile)
            const resolution = String(profile.resolution || "").split("x")
            if (resolution.length === 2) {
                const width = Number(resolution[0])
                const height = Number(resolution[1])
                if (Number.isFinite(width) && width > 0)
                    profile.width = width
                if (Number.isFinite(height) && height > 0)
                    profile.height = height
            }
            normalized.negotiatedStreamProfile = profile
        }
        return normalized
    }

    function acceptStreamingSession(session) {
        const previousSession = activeSession
        activeSession = normalizedStreamingSession(session)
        if (!activeSession) {
            if (previousSession && streamer && streamer.status !== "stopped")
                stopNativeStreamer("The remote session ended")
            streamRecordingActive = false
            streamRecordingElapsedMs = 0
            pendingRecordingPath = ""
            pendingRecordingThumbnailPath = ""
            streamState = "idle"
            streamMessage = qsTr("")
            streamPollTimer.stop()
            streamStartedAtMs = 0
            syncDiscordPresence()
            return
        }
        streamState = activeSession.phase || (Number(activeSession.status) >= 2 ? "ready" : "preparing")
        const adState = activeSession.adState || ({})
        const ads = adState.sessionAds || adState.ads || []
        if ((adState.sessionAdsRequired || adState.isAdsRequired) && ads.length > 0
                && AppController.overlay !== "queue-ad")
            AppController.showOverlay("queue-ad")
        if (streamState === "ready" || streamState === "streaming") {
            if (streamStartedAtMs === 0)
                streamStartedAtMs = Date.now()
            if (sessionReconnectAttempts > 0)
                sessionRecoveryCount += 1
            sessionReconnectAttempts = 0
            streamMessage = qsTr("Your GeForce NOW seat is ready.")
            streamPollTimer.stop()
            if (AppController.route !== "stream")
                AppController.navigate("stream")
            startNativeStreamer()
        } else if (streamState === "failed") {
            streamMessage = qsTr("GeForce NOW could not prepare this session.")
            streamPollTimer.stop()
        } else {
            const position = Number(activeSession.queuePosition || 0)
            streamMessage = position > 0
                ? qsTr("Queue position %1").arg(position)
                : qsTr("Preparing your cloud gaming seat…")
            streamPollTimer.restart()
        }
        syncDiscordPresence()
    }

    function syncDiscordPresence() {
        if (!ready || discordRequestId !== "")
            return
        if (!Boolean(settings.discordRichPresence) || !activeSession) {
            discordRequestId = CoreClient.request("discord.activity.clear", {}, 5000)
            return
        }
        const status = Number(activeSession.status || 0)
        const kind = status >= 3 ? "streaming"
            : Number(activeSession.queuePosition || 0) > 0 ? "queued" : "starting"
        discordRequestId = CoreClient.request("discord.activity.sync", {
            enabled: true,
            gameName: selectedGame ? selectedGame.title : String(activeSession.appId || "GeForce NOW"),
            gameImageUrl: selectedGame ? (selectedGame.boxArtUrl || selectedGame.imageUrl || "") : "",
            kind: kind,
            queuePosition: Number(activeSession.queuePosition || 0),
            startTimestampMs: streamStartedAtMs
        }, 5000)
    }

    function reportSessionAd(action, ad, watchedTimeMs, cancelReason) {
        if (!ready || !activeSession || !ad || sessionAdRequestId !== "")
            return
        sessionAdRequestId = CoreClient.request("session.ad.report", {
            sessionId: activeSession.sessionId,
            adId: ad.adId,
            action: action,
            watchedTimeInMs: Math.max(0, Math.round(watchedTimeMs || 0)),
            pausedTimeInMs: 0,
            cancelReason: cancelReason || undefined
        }, 30000)
    }

    function startNativeStreamer() {
        if (!ready || !activeSession || streamerStartRequestId !== "")
            return
        if (streamer && streamer.sessionId === activeSession.sessionId
                && streamer.status !== "stopped" && streamer.status !== "error")
            return
        streamer = {
            status: "starting",
            message: qsTr("Launching the native media runtime…"),
            sessionId: activeSession.sessionId
        }
        streamInputStateKnown = false
        streamerStopExpected = false
        streamerStartRequestId = CoreClient.request("streamer.start", {
            session: activeSession,
            surface: streamSurface
        }, 50000)
        streamerStatusTimer.restart()
    }

    function retryNativeStreamer() {
        streamerRestartAttempts = 0
        streamer = null
        startNativeStreamer()
    }

    function acceptStreamerSnapshot(snapshot) {
        streamer = snapshot || null
        if (!streamer) {
            streamerStatusTimer.stop()
            return
        }
        inspectStreamerOverlayRequest(streamer)
        inspectStreamerScreenshotRequest(streamer)
        inspectStreamerRecordingRequest(streamer)
        inspectStreamerShortcutAction(streamer)

        const startedAt = Number(streamer.sessionStartedAtMs || 0)
        if (Number.isFinite(startedAt) && startedAt > 0)
            streamStartedAtMs = startedAt

        const status = String(streamer.status || "unknown")
        if (activeSession && status !== "stopped" && status !== "error") {
            streamState = status
            streamMessage = streamer.message || streamMessage
            streamerStatusTimer.restart()
            setStreamInputPaused(desiredStreamInputPaused)
        }
        if (status === "streaming") {
            if (streamerRestartAttempts > 0) {
                streamerRestartRecoveryCount += streamerRestartAttempts
                streamerRestartAttempts = 0
            }
            return
        }
        if (status !== "error" && status !== "stopped")
            return

        streamerStatusTimer.stop()
        streamInputStateKnown = false
        if (streamRecordingActive) {
            streamRecordingActive = false
            streamRecordingElapsedMs = 0
            pendingRecordingPath = ""
            pendingRecordingThumbnailPath = ""
            refreshMedia()
        }
        if (status === "stopped" && (streamerStopExpected || !activeSession)) {
            streamerStopExpected = false
            streamInputStateKnown = false
            return
        }
        streamMessage = streamer.message || (status === "error"
            ? qsTr("Native media startup failed") : qsTr("The native media runtime stopped unexpectedly"))
        if (!activeSession)
            return
        if (streamerRestartAttempts < 2) {
            streamerRestartAttempts += 1
            streamerRestartTimer.restart()
            return
        }
        recoverStreamingSession(streamMessage)
    }

    function recoverStreamingSession(reason) {
        if (!ready || !activeSession || sessionClaimRequestId !== "")
            return
        if (sessionReconnectAttempts >= 2) {
            streamState = "error"
            streamMessage = reason || qsTr("The streaming session could not be recovered")
            lastError = streamMessage
            return
        }
        sessionReconnectAttempts += 1
        streamState = "reconnecting"
        streamMessage = qsTr("Reconnecting to the GeForce NOW session…")
        sessionClaimIsRecovery = true
        sessionClaimRequestId = CoreClient.request("session.claim", {
            sessionId: activeSession.sessionId,
            streamingBaseUrl: activeSession.streamingBaseUrl,
            appId: String(activeSession.appId || "0"),
            recoveryMode: true
        }, 35000)
    }

    function refreshStreamerStatus() {
        if (!ready || streamerStatusRequestId !== "")
            return
        streamerStatusRequestId = CoreClient.request("streamer.status.get", {})
    }

    function artworkUrl(sourceUrl) {
        const source = String(sourceUrl || "")
        if (source === "")
            return ""
        if (!/^https?:\/\//i.test(source))
            return source
        const resolved = artworkUrls[source]
        if (resolved !== undefined)
            return String(resolved)
        return ""
    }

    function requestArtwork(sourceUrl) {
        const source = String(sourceUrl || "")
        const resolved = artworkUrls[source]
        if (!ready || !/^https?:\/\//i.test(source) || artworkPending[source]
                || (resolved !== undefined && String(resolved) !== source))
            return ""
        const requestId = CoreClient.request("artwork.resolve", { sourceUrl: source }, 2000)
        if (requestId === "")
            return ""
        const pending = Object.assign({}, artworkPending)
        const requests = Object.assign({}, artworkRequestSources)
        pending[source] = true
        requests[requestId] = source
        artworkPending = pending
        artworkRequestSources = requests
        return requestId
    }

    function finishArtworkRequest(requestId, result, failed) {
        const source = artworkRequestSources[requestId]
        if (source === undefined)
            return false
        const requests = Object.assign({}, artworkRequestSources)
        const pending = Object.assign({}, artworkPending)
        delete requests[requestId]
        delete pending[source]
        artworkRequestSources = requests
        artworkPending = pending
        const resolved = result && result.cached === true && result.artworkUrl
            ? String(result.artworkUrl) : ""
        if (resolved !== "") {
            const urls = Object.assign({}, artworkUrls)
            urls[source] = resolved
            artworkUrls = urls
        }
        if (failed || (result && result.cached === false)) {
            const retries = Object.assign({}, artworkRetrySources)
            retries[source] = true
            artworkRetrySources = retries
        }
        return true
    }

    function acceptArtworkResult(payload) {
        const source = String(payload && payload.sourceUrl || "")
        const resolved = payload && payload.cached === true
            ? String(payload.artworkUrl || "") : ""
        if (source === "")
            return
        const pending = Object.assign({}, artworkPending)
        delete pending[source]
        artworkPending = pending
        if (resolved !== "") {
            const urls = Object.assign({}, artworkUrls)
            urls[source] = resolved
            artworkUrls = urls
        }
        const retries = Object.assign({}, artworkRetrySources)
        if (payload.cached === false)
            retries[source] = true
        else
            delete retries[source]
        artworkRetrySources = retries
    }

    function stopNativeStreamer(reason) {
        streamerStatusTimer.stop()
        if (!ready || streamerStopRequestId !== "")
            return
        // A runtime that never started, or that already reported "stopped", has
        // nothing left to reap. Without this an Escape held down during a failing
        // session issues one streamer.stop per key repeat.
        const status = streamer ? String(streamer.status || "") : ""
        if (status === "" || status === "stopped")
            return
        streamerStopExpected = true
        streamerStopRequestId = CoreClient.request("streamer.stop", {
            reason: reason || "User stopped the session"
        }, 10000)
    }

    function setStreamInputPaused(paused) {
        desiredStreamInputPaused = Boolean(paused)
        if (!ready || streamInputPauseRequestId !== "" || !streamer
                || streamer.status === "stopped" || streamer.status === "error")
            return
        if (streamInputStateKnown && currentStreamInputPaused === desiredStreamInputPaused)
            return
        streamInputPauseRequestId = CoreClient.request("streamer.input.pause", {
            paused: desiredStreamInputPaused
        }, 5000)
    }

    function controlStream(action) {
        if (!ready || streamControlRequestId !== "")
            return
        if (!activeSession || !streamer || streamer.status !== "streaming") {
            streamControlMessage = qsTr("Stream controls are available once the session is live.")
            return
        }
        streamControlMessage = qsTr("")
        streamControlAction = action
        streamControlRequestId = CoreClient.request("streamer.control", {
            action: action
        }, 5000)
    }

    function inspectStreamerOverlayRequest(value) {
        const generation = Number(value && value.overlayRequestGeneration || 0)
        if (generation <= overlayRequestGeneration)
            return
        overlayRequestGeneration = generation
        AppController.activateWindow()
        AppController.showOverlay(desktopUiActive ? "desktop-stream-menu" : "guide-session")
    }

    function inspectStreamerScreenshotRequest(value) {
        const generation = Number(value && value.screenshotRequestGeneration || 0)
        if (generation <= screenshotRequestGeneration)
            return
        screenshotRequestGeneration = generation
        captureStreamScreenshot()
    }

    function captureStreamScreenshot() {
        const rect = streamSurface && streamSurface.logicalScreenRect
            ? streamSurface.logicalScreenRect : ({x: 0, y: 0, width: 0, height: 0})
        const title = selectedGame && selectedGame.title ? selectedGame.title : "OpenNOW"
        const path = AppController.captureScreenRegion(
            Number(rect.x || 0), Number(rect.y || 0),
            Number(rect.width || 0), Number(rect.height || 0), title)
        if (path) {
            mediaMessage = qsTr("Screenshot saved")
            accessibilityMessage = qsTr("Screenshot saved to %1").arg(path)
            refreshMedia()
        } else {
            mediaMessage = qsTr("Screenshot capture failed")
            lastError = qsTr("The desktop compositor did not allow OpenNOW to capture the stream.")
            accessibilityMessage = lastError
        }
    }

    function inspectStreamerRecordingRequest(value) {
        const generation = Number(value && value.recordingToggleRequestGeneration || 0)
        if (generation <= recordingToggleRequestGeneration)
            return
        recordingToggleRequestGeneration = generation
        toggleStreamRecording()
    }

    function inspectStreamerShortcutAction(value) {
        const generation = Number(value && value.shortcutActionGeneration || 0)
        if (generation <= shortcutActionGeneration)
            return
        shortcutActionGeneration = generation
        const action = String(value.shortcutAction || "")
        if (action === "stop-stream") {
            stopStreamingSession()
        } else if (action === "toggle-anti-afk") {
            antiAfkEnabled = !antiAfkEnabled
            streamControlMessage = antiAfkEnabled ? qsTr("Anti-AFK on") : qsTr("Anti-AFK off")
            accessibilityMessage = streamControlMessage
        } else if (action === "toggle-microphone") {
            toggleMicrophoneMode()
        } else if (action === "toggle-stats") {
            AppController.activateWindow()
            const compact = desktopUiActive ? "desktop-stream-stats" : "stream-stats"
            const expanded = desktopUiActive
                ? "desktop-stream-stats-expanded" : "stream-stats-expanded"
            if (AppController.overlay === compact || AppController.overlay === expanded)
                AppController.showOverlay("")
            else
                AppController.showOverlay(compact)
        } else if (action === "toggle-fullscreen") {
            AppController.activateWindow()
            fullscreenToggleRequested()
        } else if (action === "screenshot") {
            captureStreamScreenshot()
        } else if (action === "toggle-recording") {
            toggleStreamRecording()
        }
    }

    function toggleStreamRecording() {
        if (streamRecordingStartRequestId !== "" || streamRecordingStopRequestId !== ""
                || mediaRecordingTargetRequestId !== "")
            return
        if (streamRecordingActive) {
            streamRecordingStopRequestId = CoreClient.request("streamer.recording.stop", {}, 15000)
            mediaMessage = qsTr("Finalizing recording…")
            accessibilityMessage = mediaMessage
            return
        }
        if (!activeSession || !streamer || streamer.status !== "streaming") {
            mediaMessage = qsTr("Start a native stream before recording")
            accessibilityMessage = mediaMessage
            return
        }
        const title = selectedGame && selectedGame.title ? selectedGame.title : "OpenNOW"
        AppController.showOverlay("")
        mediaRecordingTargetRequestId = CoreClient.request("media.recording.target", {
            gameTitle: title
        }, 5000)
        mediaMessage = qsTr("Preparing source-quality recording…")
        accessibilityMessage = mediaMessage
    }

    function toggleMicrophoneMode() {
        if (settings.microphoneMode !== "disabled") {
            applySetting("microphoneMode", "disabled")
            setSetting("microphoneMode", "disabled")
        }
        streamControlMessage = qsTr("Microphone upstream is unavailable for NVST sessions")
        accessibilityMessage = streamControlMessage
    }

    function pollStreamingSession() {
        if (!ready || !activeSession || streamPollRequestId !== "" || streamStopRequestId !== "")
            return
        streamPollRequestId = CoreClient.request("session.poll", {
            sessionId: activeSession.sessionId,
            streamingBaseUrl: activeSession.streamingBaseUrl
        }, 35000)
    }

    function stopStreamingSession() {
        if (activeSession && streamStartedAtMs > 0) {
            const snapshot = streamer || ({})
            lastSessionReport = {
                gameTitle: selectedGame && selectedGame.title ? selectedGame.title : "GeForce NOW",
                durationMs: Math.max(0, Date.now() - streamStartedAtMs),
                transport: snapshot.transport ? String(snapshot.transport).toUpperCase() : "",
                mediaBackend: snapshot.mediaBackend ? String(snapshot.mediaBackend) : "",
                firstFrameLatencyMs: snapshot.firstFrameLatencyMs !== undefined
                    && snapshot.firstFrameLatencyMs !== null
                    ? Number(snapshot.firstFrameLatencyMs) : null,
                recoveries: Number(streamerRecoveryCount || 0) + Number(sessionRecoveryCount || 0),
                decoderErrors: snapshot.decoderErrorCount !== undefined
                    && snapshot.decoderErrorCount !== null
                    ? Number(snapshot.decoderErrorCount) : null,
                outputErrors: snapshot.outputErrorCount !== undefined
                    && snapshot.outputErrorCount !== null
                    ? Number(snapshot.outputErrorCount) : null,
                queueDrops: snapshot.queueDropCount !== undefined
                    && snapshot.queueDropCount !== null
                    ? Number(snapshot.queueDropCount) : null,
                recordingCount: snapshot.recordingStopCount !== undefined
                    && snapshot.recordingStopCount !== null
                    ? Number(snapshot.recordingStopCount) : null
            }
        }
        streamPollTimer.stop()
        stopNativeStreamer("User stopped the session")
        if (streamPollRequestId !== "") {
            CoreClient.cancel(streamPollRequestId)
            streamPollRequestId = ""
        }
        if (!activeSession) {
            streamState = "idle"
            AppController.navigateFromLastPrimary("game-detail")
            return
        }
        if (!ready || streamStopRequestId !== "")
            return
        streamState = "stopping"
        streamMessage = qsTr("Closing the remote session…")
        streamStopRequestId = CoreClient.request("session.stop", {
            sessionId: activeSession.sessionId,
            streamingBaseUrl: activeSession.streamingBaseUrl
        }, 35000)
    }

    function startDeviceLogin(providerIdpId, staySignedIn) {
        if (!ready || deviceStartRequestId !== "")
            return
        pendingStaySignedIn = staySignedIn !== false
        cancelDeviceLogin()
        lastError = qsTr("")
        authMessage = qsTr("Contacting NVIDIA…")
        authState = "starting"
        const params = providerIdpId ? { providerIdpId: providerIdpId } : {}
        deviceStartRequestId = CoreClient.request("auth.device.start", params, 30000)
    }

    function pollDeviceLogin() {
        if (!ready || !authChallenge || devicePollRequestId !== "")
            return
        devicePollRequestId = CoreClient.request("auth.device.poll", {
            attemptId: authChallenge.attemptId,
            deviceCode: authChallenge.deviceCode
        }, 30000)
    }

    function cancelDeviceLogin() {
        devicePollTimer.stop()
        if (devicePollRequestId !== "") {
            CoreClient.cancel(devicePollRequestId)
            devicePollRequestId = ""
        }
        if (authChallenge && ready)
            CoreClient.request("auth.device.cancel", { attemptId: authChallenge.attemptId })
        authChallenge = null
        if (!signedIn)
            authState = "idle"
    }

    function logout() {
        if (ready && logoutRequestId === "")
            logoutRequestId = CoreClient.request("auth.logout", {})
    }

    function logoutAll() {
        if (ready && logoutAllRequestId === "")
            logoutAllRequestId = CoreClient.request("auth.accounts.logoutAll", {})
    }

    function requestConsoleSurface(enabled) {
        const requested = Boolean(enabled)
        const previous = consoleSurfaceInitialized
            ? consoleSurfaceConfirmedValue : Boolean(settings.launchInConsoleMode)
        consoleSurfaceError = ""
        consoleSurfaceDesiredValue = requested
        applySetting("launchInConsoleMode", requested)
        root.consoleSurfaceRequested(requested)
        if (!ready) {
            consoleSurfaceDesiredValue = previous
            applySetting("launchInConsoleMode", previous)
            root.consoleSurfaceRequested(previous)
            consoleSurfaceError = qsTr("Console mode could not be saved because the OpenNOW core is not ready. The previous mode was restored.")
            lastError = consoleSurfaceError
            accessibilityMessage = lastError
            return ""
        }
        if (consoleSurfaceRequestId === "")
            beginConsoleSurfacePersistence()
        return consoleSurfaceRequestId
    }

    function beginConsoleSurfacePersistence() {
        consoleSurfaceRequestValue = consoleSurfaceDesiredValue
        consoleSurfaceRequestId = CoreClient.request("settings.set", {
            key: "launchInConsoleMode",
            value: consoleSurfaceRequestValue
        })
        if (consoleSurfaceRequestId !== "")
            return
        const restored = consoleSurfaceConfirmedValue
        consoleSurfaceDesiredValue = restored
        applySetting("launchInConsoleMode", restored)
        root.consoleSurfaceRequested(restored)
        consoleSurfaceError = qsTr("Console mode could not be saved. The previous mode was restored.")
        lastError = consoleSurfaceError
        accessibilityMessage = lastError
    }

    function setSetting(key, value) {
        if (key === "launchInConsoleMode")
            return requestConsoleSurface(Boolean(value))
        if (!ready) {
            lastError = qsTr("The OpenNOW core is not ready")
            return ""
        }
        return CoreClient.request("settings.set", { key: key, value: value })
    }

    function resetSettings() {
        if (!ready) {
            lastError = qsTr("The OpenNOW core is not ready")
            return
        }
        CoreClient.request("settings.reset", {})
    }

    function applySetting(key, value) {
        const updated = Object.assign({}, settings)
        updated[key] = value
        settings = updated
        if (["nativeStreamerExecutablePath", "nativeVideoBackend", "decoderPreference"].indexOf(key) >= 0)
            Qt.callLater(root.refreshStreamerDetection)
        accessibilityMessage = qsTr("%1 updated").arg(String(key).split(/(?=[A-Z])/).join(" "))
        if (key === "reducedMotion")
            AppController.reducedMotion = Boolean(value)
        if (key === "appLanguage")
            I18n.setLocale(String(value || "system"))
        if (key === "discordRichPresence")
            syncDiscordPresence()
        if (key === "errorReportingConsent")
            syncTelemetry()
    }

    function focusIndex(route) {
        return focusPositions[route] === undefined ? 0 : focusPositions[route]
    }

    function rememberFocus(route, index) {
        const updated = Object.assign({}, focusPositions)
        updated[route] = index
        focusPositions = updated
    }

    property Connections coreConnections: Connections {
        target: CoreClient
        function onStateChanged() {
            if (CoreClient.state === "ready")
                root.initializeServices()
            else if (CoreClient.state === "failed") {
                root.lastError = CoreClient.lastError
                root.authRestorePending = false
            }
        }
        function onResponseReceived(requestId, result) {
            if (root.finishArtworkRequest(requestId, result, false)) {
                return
            } else if (requestId === root.settingsRequestId && result.settings) {
                root.settings = Object.assign({}, result.settings, {microphoneMode: "disabled"})
                if (result.settings.microphoneMode !== "disabled")
                    root.setSetting("microphoneMode", "disabled")
                root.consoleSurfaceConfirmedValue = Boolean(result.settings.launchInConsoleMode)
                root.consoleSurfaceDesiredValue = root.consoleSurfaceConfirmedValue
                root.consoleSurfaceInitialized = true
                AppController.reducedMotion = Boolean(result.settings.reducedMotion)
                I18n.setLocale(String(result.settings.appLanguage || "system"))
                root.settingsRequestId = ""
                root.syncTelemetry()
                root.syncDiscordPresence()
                root.refreshStreamerDetection()
                if (result.settings.autoCheckForUpdates && root.updaterCheckRequestId === "")
                    root.autoUpdateCheckTimer.restart()
            } else if (requestId === root.consoleSurfaceRequestId) {
                root.consoleSurfaceRequestId = ""
                root.consoleSurfaceConfirmedValue = Boolean(result.value)
                root.consoleSurfaceInitialized = true
                if (root.consoleSurfaceDesiredValue === root.consoleSurfaceConfirmedValue) {
                    root.applySetting("launchInConsoleMode", root.consoleSurfaceConfirmedValue)
                    root.consoleSurfaceError = ""
                    root.accessibilityMessage = root.consoleSurfaceConfirmedValue
                        ? qsTr("Console mode saved") : qsTr("Computer mode saved")
                } else {
                    root.beginConsoleSurfacePersistence()
                }
            } else if (requestId === root.streamerDetectRequestId) {
                root.streamerDetectRequestId = ""
                root.streamerDetection = result
                const codecs = result.availableCodecs || []
                root.streamerDetectionMessage = codecs.length
                    ? qsTr("Available: ") + codecs.map(codec => String(codec).toUpperCase()).join(", ")
                    : qsTr("No native video decoder is available")
            } else if (requestId === root.providersRequestId) {
                root.providers = result.providers || []
                root.providersRequestId = ""
            } else if (requestId === root.authSessionRequestId) {
                root.authSession = result.session || null
                root.sessionPersistence = result.persistence || "none"
                root.authState = root.authSession ? "signed-in" : "idle"
                root.authSessionRequestId = ""
                root.authRestorePending = false
                if (root.sessionPersistenceMessage !== "")
                    root.accessibilityMessage = root.sessionPersistenceMessage
                if (root.authSession && root.catalogSource !== "account-library")
                    root.reloadCatalogForSession()
                if (root.authSession)
                    root.refreshAccountServices()
                if (root.authSession)
                    root.refreshRemoteSessions()
                else
                    root.remoteSessions = []
                root.resolveDirectLaunch()
            } else if (requestId === root.catalogRequestId) {
                root.catalogGames = result.games || []
                root.catalogTotalCount = Number(result.totalCount || root.catalogGames.length)
                if (!root.selectedGame && root.catalogGames.length > 0)
                    root.selectedGame = root.catalogGames[0]
                root.catalogState = "ready"
                root.catalogRequestId = ""
                root.resolveDirectLaunch()
            } else if (requestId === root.deviceStartRequestId) {
                root.authChallenge = result
                root.authState = "waiting"
                root.authMessage = qsTr("Scan the QR code or enter %1").arg(result.userCode)
                root.deviceStartRequestId = ""
                root.devicePollTimer.restart()
            } else if (requestId === root.devicePollRequestId) {
                root.devicePollRequestId = ""
                const status = result.status || "error"
                if (status === "authorized") {
                    root.devicePollTimer.stop()
                    root.authState = "completing"
                    root.authMessage = qsTr("Signed in. Loading your profile…")
                    root.deviceCompleteRequestId = CoreClient.request("auth.device.complete", {
                        attemptId: root.authChallenge.attemptId,
                        staySignedIn: root.pendingStaySignedIn
                    }, 30000)
                } else if (status === "pending") {
                    root.authState = "waiting"
                } else if (status === "slow_down") {
                    root.authChallenge.intervalSeconds = Number(result.intervalSeconds || 10)
                    root.devicePollTimer.restart()
                } else {
                    root.devicePollTimer.stop()
                    root.authState = "error"
                    root.authMessage = result.error || qsTr("Device sign-in failed")
                }
            } else if (requestId === root.deviceCompleteRequestId) {
                root.authSession = result.session || null
                root.sessionPersistence = result.persistence || "memory-only"
                root.authChallenge = null
                root.authState = root.authSession ? "signed-in" : "error"
                root.authMessage = root.authSession
                    ? (root.sessionPersistenceMessage !== ""
                        ? qsTr("Welcome, %1. %2").arg(root.authSession.user.displayName).arg(root.sessionPersistenceMessage)
                        : qsTr("Welcome, %1").arg(root.authSession.user.displayName))
                    : qsTr("Sign-in did not return a session")
                root.deviceCompleteRequestId = ""
                if (root.sessionPersistenceMessage !== "")
                    root.accessibilityMessage = root.sessionPersistenceMessage
                if (root.authSession)
                    root.reloadCatalogForSession()
                if (root.authSession)
                    root.refreshAccountServices()
                root.resolveDirectLaunch()
            } else if (requestId === root.logoutRequestId) {
                root.authSession = result.session || null
                if (!root.authSession)
                    root.sessionPersistence = "none"
                root.authState = root.authSession ? "signed-in" : "idle"
                root.authMessage = qsTr("")
                root.logoutRequestId = ""
                root.subscription = null
                root.regions = []
                root.reloadCatalogForSession()
                if (root.authSession)
                    root.refreshAccountServices()
            } else if (requestId === root.logoutAllRequestId) {
                root.logoutAllRequestId = ""
                root.authSession = null
                root.sessionPersistence = "none"
                root.authState = "idle"
                root.authMessage = qsTr("")
                root.savedAccounts = []
                root.subscription = null
                root.regions = []
                root.reloadCatalogForSession()
                root.accessibilityMessage = qsTr("All saved accounts signed out")
            } else if (requestId === root.subscriptionRequestId) {
                root.subscription = result.subscription || null
                root.subscriptionRequestId = ""
            } else if (requestId === root.regionsRequestId) {
                root.regions = result.regions || []
                root.regionsVpcId = result.vpcId || ""
                root.regionsRequestId = ""
            } else if (requestId === root.regionPingRequestId) {
                root.regionPingRequestId = ""
                const values = {}
                const results = result.results || []
                let bestName = ""
                let bestPing = Number.MAX_VALUE
                for (let index = 0; index < results.length; ++index) {
                    const item = results[index]
                    values[String(item.url || "")] = item.pingMs === null || item.pingMs === undefined
                        ? null : Number(item.pingMs)
                    if (values[String(item.url || "")] !== null && Number(item.pingMs) < bestPing) {
                        bestPing = Number(item.pingMs)
                        for (let regionIndex = 0; regionIndex < root.regions.length; ++regionIndex) {
                            if (root.regions[regionIndex].url === item.url) {
                                bestName = root.regions[regionIndex].name
                                break
                            }
                        }
                    }
                }
                root.regionPingResults = values
                root.regionPingMessage = bestName
                    ? qsTr("Best: %1 · %2 ms").arg(bestName).arg(bestPing)
                    : qsTr("No region responded")
            } else if (requestId === root.accountsRequestId) {
                root.savedAccounts = result.accounts || []
                root.accountsRequestId = ""
            } else if (requestId === root.accountSwitchRequestId) {
                root.authSession = result.session || null
                root.sessionPersistence = result.persistence || "os-credential-store"
                root.authState = root.authSession ? "signed-in" : "error"
                root.accountSwitchRequestId = ""
                if (root.sessionPersistenceMessage !== "")
                    root.accessibilityMessage = root.sessionPersistenceMessage
                root.reloadCatalogForSession()
                root.refreshAccountServices()
                root.pinMessage = qsTr("")
                if (AppController.route === "profile-pin")
                    AppController.navigate("accounts")
            } else if (requestId === root.accountRemoveRequestId) {
                root.accountRemoveRequestId = ""
                if (root.accountsRequestId === "")
                    root.accountsRequestId = CoreClient.request("auth.accounts.list", {})
                root.authSessionRequestId = CoreClient.request("auth.session.get", {})
            } else if (requestId === root.pinRequestId) {
                root.pinRequestId = ""
                if (result.ok) {
                    root.pinMessage = qsTr("")
                    if (root.accountsRequestId === "")
                        root.accountsRequestId = CoreClient.request("auth.accounts.list", {})
                    AppController.navigate("accounts")
                } else {
                    root.pinMessage = result.reason === "locked_out"
                        ? qsTr("Too many attempts. This profile is temporarily locked.")
                        : result.reason === "invalid_pin"
                            ? qsTr("That PIN is incorrect.")
                            : qsTr("Enter exactly four digits.")
                }
            } else if (requestId === root.gameAccountsRequestId) {
                root.gameAccountsRequestId = ""
                root.gameAccounts = result.accounts || []
                root.gameAccountsState = "ready"
                root.gameAccountMessage = qsTr("")
            } else if (requestId === root.gameAccountActionRequestId) {
                root.gameAccountActionRequestId = ""
                root.gameAccountMessage = result.message || qsTr("Account updated")
                root.refreshGameAccounts()
                root.reloadCatalogForSession()
            } else if (requestId === root.accountLinkStartRequestId) {
                root.accountLinkStartRequestId = ""
                root.accountLinkAttempt = result
                if (!AppController.openExternalUrl(result.loginUrl || "")) {
                    root.gameAccountMessage = qsTr("Open the provider sign-in URL in your browser.")
                } else {
                    root.gameAccountMessage = qsTr("Finish signing in in your browser…")
                }
                root.accountLinkPollTimer.restart()
            } else if (requestId === root.accountLinkPollRequestId) {
                root.accountLinkPollRequestId = ""
                const status = result.status || "error"
                if (status === "pending") {
                    return
                }
                root.accountLinkPollTimer.stop()
                root.accountLinkAttempt = null
                if (status === "complete") {
                    root.gameAccountMessage = qsTr("Account connected")
                    root.refreshGameAccounts()
                    root.reloadCatalogForSession()
                } else {
                    root.gameAccountMessage = result.message || qsTr("Account linking expired")
                }
            } else if (requestId === root.storageLocationsRequestId) {
                root.storageLocationsRequestId = ""
                root.storageLocations = result.locations || []
                root.storageMessage = qsTr("")
            } else if (requestId === root.storageResetRequestId) {
                root.storageResetRequestId = ""
                root.storageMessage = result.message || qsTr("Persistent storage was reset successfully.")
                root.refreshAccountServices()
            } else if (requestId === root.mediaRequestId) {
                root.mediaRequestId = ""
                root.mediaItems = result.items || []
                root.mediaRootPath = result.rootPath || ""
                root.mediaState = "ready"
                root.mediaMessage = root.mediaItems.length
                    ? qsTr("%1 screenshots · %2 recordings").arg(result.screenshots).arg(result.recordings)
                    : qsTr("No captures yet")
            } else if (requestId === root.mediaDeleteRequestId) {
                root.mediaDeleteRequestId = ""
                root.mediaMessage = qsTr("Capture deleted")
                root.refreshMedia()
            } else if (requestId === root.mediaRecordingTargetRequestId) {
                root.mediaRecordingTargetRequestId = ""
                root.pendingRecordingPath = String(result.path || "")
                root.pendingRecordingThumbnailPath = String(result.thumbnailPath || "")
                root.streamRecordingStartRequestId = CoreClient.request("streamer.recording.start", {
                    outputPath: root.pendingRecordingPath
                }, 15000)
            } else if (requestId === root.streamRecordingStartRequestId) {
                root.streamRecordingStartRequestId = ""
                root.streamRecordingActive = true
                root.streamRecordingStartedAtMs = Date.now()
                root.streamRecordingElapsedMs = 0
                const rect = root.streamSurface && root.streamSurface.logicalScreenRect
                    ? root.streamSurface.logicalScreenRect : ({x: 0, y: 0, width: 0, height: 0})
                if (root.pendingRecordingThumbnailPath) {
                    AppController.captureScreenRegionTo(
                        Number(rect.x || 0), Number(rect.y || 0),
                        Number(rect.width || 0), Number(rect.height || 0),
                        root.pendingRecordingThumbnailPath)
                }
                root.mediaMessage = qsTr("Recording source video + stream audio")
                root.accessibilityMessage = qsTr("Recording started")
            } else if (requestId === root.streamRecordingStopRequestId) {
                root.streamRecordingStopRequestId = ""
                root.streamRecordingActive = false
                root.streamRecordingElapsedMs = 0
                root.pendingRecordingPath = ""
                root.pendingRecordingThumbnailPath = ""
                root.mediaMessage = result.path ? qsTr("Recording saved") : qsTr("Recording stopped")
                root.accessibilityMessage = result.path
                    ? qsTr("Recording saved to %1").arg(result.path)
                    : root.mediaMessage
                root.refreshMedia()
            } else if (requestId === root.diagnosticsRequestId) {
                root.diagnosticsRequestId = ""
                root.diagnostics = result
                root.diagnosticsMessage = result.entries && result.entries.length
                    ? qsTr("%1 recent redacted events").arg(result.entries.length)
                    : qsTr("No diagnostic events recorded")
            } else if (requestId === root.diagnosticsExportRequestId) {
                root.diagnosticsExportRequestId = ""
                root.diagnosticsMessage = qsTr("Saved redacted report to %1").arg(result.path)
                AppController.openLocalPath(result.path, true)
            } else if (requestId === root.acceptanceExportRequestId) {
                root.acceptanceExportRequestId = ""
                root.diagnosticsMessage = qsTr("Saved live acceptance evidence to %1").arg(result.path)
                AppController.openLocalPath(result.path, true)
            } else if (requestId === root.updaterStateRequestId) {
                root.updaterStateRequestId = ""
                root.updaterState = result
            } else if (requestId === root.updaterCheckRequestId) {
                root.updaterCheckRequestId = ""
                root.updaterState = result
                root.updaterHighlightsRequestId = CoreClient.request("updater.highlights.get", {})
            } else if (requestId === root.updaterHighlightsRequestId) {
                root.updaterHighlightsRequestId = ""
                root.releaseHighlights = result
            } else if (requestId === root.updaterDownloadRequestId) {
                root.updaterDownloadRequestId = ""
                root.updaterState = result
                root.accessibilityMessage = qsTr("Signed update downloaded and verified")
            } else if (requestId === root.updaterInstallRequestId) {
                root.updaterInstallRequestId = ""
                root.updaterState = result
                root.accessibilityMessage = qsTr("Verified update installer launched")
                Qt.callLater(() => AppController.quitApplication())
            } else if (requestId === root.socialCapabilitiesRequestId) {
                root.socialCapabilitiesRequestId = ""
                root.socialCapabilities = result
            } else if (requestId === root.discordRequestId) {
                root.discordRequestId = ""
            } else if (requestId === root.telemetryRequestId) {
                root.telemetryRequestId = ""
            } else if (requestId === root.feedbackRequestId) {
                root.feedbackRequestId = ""
                root.reportingState = "sent"
                root.reportingMessage = result.message || qsTr("Thanks — your feedback was sent.")
            } else if (requestId === root.bugReportRequestId) {
                root.bugReportRequestId = ""
                root.reportingState = "sent"
                root.reportingMessage = result.reference
                    ? qsTr("Bug report sent · reference %1").arg(result.reference)
                    : qsTr("Bug report sent successfully.")
            } else if (requestId === root.sessionAdRequestId) {
                root.sessionAdRequestId = ""
                root.acceptStreamingSession(result.session || root.activeSession)
                const state = root.activeSession ? (root.activeSession.adState || ({})) : ({})
                const ads = state.sessionAds || state.ads || []
                if (!(state.sessionAdsRequired || state.isAdsRequired) || ads.length === 0)
                    AppController.showOverlay("")
            } else if (requestId === root.activeSessionRequestId) {
                root.activeSessionRequestId = ""
                root.acceptStreamingSession(result.session || null)
            } else if (requestId === root.remoteSessionDiscoveryRequestId) {
                root.remoteSessionDiscoveryRequestId = ""
                root.remoteSessions = result.sessions || []
            } else if (requestId === root.remoteSessionsRequestId) {
                root.remoteSessionsRequestId = ""
                root.inspectRemoteSessions(result)
            } else if (requestId === root.sessionClaimRequestId) {
                root.sessionClaimRequestId = ""
                root.sessionClaimIsRecovery = false
                root.pendingLaunchParams = null
                root.conflictSession = null
                root.remoteSessions = []
                root.acceptStreamingSession(result.session || null)
            } else if (requestId === root.streamCreateRequestId) {
                root.streamCreateRequestId = ""
                root.acceptStreamingSession(result.session || null)
            } else if (requestId === root.streamPollRequestId) {
                root.streamPollRequestId = ""
                root.acceptStreamingSession(result.session || null)
            } else if (requestId === root.streamStopRequestId) {
                root.streamStopRequestId = ""
                const wasForceNewAfterStop = root.forceNewAfterStop
                root.remoteSessions = []
                root.acceptStreamingSession(null)
                if (root.forceNewAfterStop) {
                    root.forceNewAfterStop = false
                    root.conflictSession = null
                    if (root.pendingLaunchParams)
                        root.createPendingSession()
                    else if (AppController.route === "inserting")
                        AppController.navigate("home")
                } else if (AppController.route !== "game-detail")
                    AppController.navigateFromLastPrimary("game-detail")
                if (!wasForceNewAfterStop && Boolean(root.settings.showSessionReport)
                        && root.lastSessionReport)
                    AppController.showOverlay("session-report")
            } else if (requestId === root.streamerStartRequestId) {
                root.streamerStartRequestId = ""
                root.acceptStreamerSnapshot(result.streamer || result || null)
            } else if (requestId === root.streamerStatusRequestId) {
                root.streamerStatusRequestId = ""
                root.acceptStreamerSnapshot(result.streamer || result || root.streamer)
            } else if (requestId === root.streamerStopRequestId) {
                root.streamerStopRequestId = ""
                root.acceptStreamerSnapshot(result.streamer || result || null)
            } else if (requestId === root.streamInputPauseRequestId) {
                root.streamInputPauseRequestId = ""
                if (result.streamerRunning)
                    root.currentStreamInputPaused = Boolean(result.paused)
                root.streamInputStateKnown = Boolean(result.streamerRunning)
                if (result.streamerRunning
                        && root.currentStreamInputPaused !== root.desiredStreamInputPaused)
                    Qt.callLater(() => root.setStreamInputPaused(root.desiredStreamInputPaused))
            } else if (requestId === root.streamControlRequestId) {
                root.streamControlRequestId = ""
                root.streamControlAction = ""
                root.streamControlMessage = qsTr("Stream control applied")
                root.refreshStreamerStatus()
            }
        }
        function onRequestFailed(requestId, code, message) {
            if (root.finishArtworkRequest(requestId, null, true))
                return
            if (requestId === root.remoteSessionDiscoveryRequestId) {
                root.remoteSessionDiscoveryRequestId = ""
                root.remoteSessions = []
                return
            }
            root.lastError = message
            if (requestId === root.consoleSurfaceRequestId) {
                root.consoleSurfaceRequestId = ""
                root.consoleSurfaceDesiredValue = root.consoleSurfaceConfirmedValue
                root.applySetting("launchInConsoleMode", root.consoleSurfaceConfirmedValue)
                root.consoleSurfaceRequested(root.consoleSurfaceConfirmedValue)
                root.consoleSurfaceError = qsTr("Console mode could not be saved. The previous mode was restored. %1").arg(message)
                root.lastError = root.consoleSurfaceError
                root.accessibilityMessage = root.lastError
            } else if (requestId === root.catalogRequestId) {
                root.catalogState = "error"
                root.catalogRequestId = ""
            } else if (requestId === root.streamerDetectRequestId) {
                root.streamerDetectRequestId = ""
                root.streamerDetection = {available: false, availableCodecs: [], capabilities: ({})}
                root.streamerDetectionMessage = message
            } else if (requestId === root.providersRequestId) {
                root.providersRequestId = ""
            } else if (requestId === root.authSessionRequestId) {
                root.authSessionRequestId = ""
                root.authRestorePending = false
                root.authState = root.authSession ? "signed-in" : "idle"
                if (code !== "cancelled")
                    root.authMessage = message
            } else if (requestId === root.deviceStartRequestId
                       || requestId === root.devicePollRequestId
                       || requestId === root.deviceCompleteRequestId) {
                root.devicePollTimer.stop()
                root.authState = code === "cancelled" ? "idle" : "error"
                root.authMessage = code === "cancelled" ? "" : message
                root.deviceStartRequestId = ""
                root.devicePollRequestId = ""
                root.deviceCompleteRequestId = ""
            } else if (requestId === root.logoutRequestId) {
                root.logoutRequestId = ""
                root.authMessage = message
            } else if (requestId === root.logoutAllRequestId) {
                root.logoutAllRequestId = ""
                root.authMessage = message
            } else if (requestId === root.subscriptionRequestId) {
                root.subscriptionRequestId = ""
            } else if (requestId === root.regionsRequestId) {
                root.regionsRequestId = ""
            } else if (requestId === root.regionPingRequestId) {
                root.regionPingRequestId = ""
                root.regionPingMessage = message
            } else if (requestId === root.accountsRequestId) {
                root.accountsRequestId = ""
            } else if (requestId === root.accountSwitchRequestId) {
                root.accountSwitchRequestId = ""
                root.pinMessage = message
            } else if (requestId === root.accountRemoveRequestId) {
                root.accountRemoveRequestId = ""
            } else if (requestId === root.pinRequestId) {
                root.pinRequestId = ""
                root.pinMessage = message
            } else if (requestId === root.gameAccountsRequestId) {
                root.gameAccountsRequestId = ""
                root.gameAccountsState = "error"
                root.gameAccountMessage = message
            } else if (requestId === root.gameAccountActionRequestId) {
                root.gameAccountActionRequestId = ""
                root.gameAccountMessage = message
            } else if (requestId === root.accountLinkStartRequestId) {
                root.accountLinkStartRequestId = ""
                root.gameAccountMessage = message
            } else if (requestId === root.accountLinkPollRequestId) {
                root.accountLinkPollRequestId = ""
                root.accountLinkPollTimer.stop()
                root.gameAccountMessage = message
            } else if (requestId === root.storageLocationsRequestId) {
                root.storageLocationsRequestId = ""
                root.storageMessage = message
            } else if (requestId === root.storageResetRequestId) {
                root.storageResetRequestId = ""
                root.storageMessage = message
            } else if (requestId === root.mediaRequestId) {
                root.mediaRequestId = ""
                root.mediaState = "error"
                root.mediaMessage = message
            } else if (requestId === root.mediaDeleteRequestId) {
                root.mediaDeleteRequestId = ""
                root.mediaMessage = message
            } else if (requestId === root.mediaRecordingTargetRequestId) {
                root.mediaRecordingTargetRequestId = ""
                root.pendingRecordingPath = ""
                root.pendingRecordingThumbnailPath = ""
                root.mediaMessage = message
            } else if (requestId === root.streamRecordingStartRequestId) {
                root.streamRecordingStartRequestId = ""
                root.pendingRecordingPath = ""
                root.pendingRecordingThumbnailPath = ""
                root.streamRecordingActive = false
                root.mediaMessage = message
            } else if (requestId === root.streamRecordingStopRequestId) {
                root.streamRecordingStopRequestId = ""
                root.streamRecordingActive = false
                root.streamRecordingElapsedMs = 0
                root.pendingRecordingPath = ""
                root.pendingRecordingThumbnailPath = ""
                root.mediaMessage = message
                root.refreshMedia()
            } else if (requestId === root.diagnosticsRequestId) {
                root.diagnosticsRequestId = ""
                root.diagnosticsMessage = message
            } else if (requestId === root.diagnosticsExportRequestId) {
                root.diagnosticsExportRequestId = ""
                root.diagnosticsMessage = message
            } else if (requestId === root.acceptanceExportRequestId) {
                root.acceptanceExportRequestId = ""
                root.diagnosticsMessage = message
            } else if (requestId === root.updaterStateRequestId) {
                root.updaterStateRequestId = ""
            } else if (requestId === root.updaterCheckRequestId) {
                root.updaterCheckRequestId = ""
                root.updaterState = Object.assign({}, root.updaterState, {
                    status: "error",
                    message: message,
                    canCheck: true
                })
            } else if (requestId === root.updaterHighlightsRequestId) {
                root.updaterHighlightsRequestId = ""
            } else if (requestId === root.updaterDownloadRequestId) {
                root.updaterDownloadRequestId = ""
                root.updaterState = Object.assign({}, root.updaterState, {
                    status: "available", message: message, canCheck: true
                })
            } else if (requestId === root.updaterInstallRequestId) {
                root.updaterInstallRequestId = ""
                root.updaterState = Object.assign({}, root.updaterState, {
                    status: "downloaded", message: message, canInstall: true, canCheck: true
                })
            } else if (requestId === root.socialCapabilitiesRequestId) {
                root.socialCapabilitiesRequestId = ""
                root.socialCapabilities = Object.assign({}, root.socialCapabilities, {
                    reason: qsTr("Provider social capabilities could not be checked.")
                })
            } else if (requestId === root.discordRequestId) {
                root.discordRequestId = ""
            } else if (requestId === root.telemetryRequestId) {
                root.telemetryRequestId = ""
            } else if (requestId === root.feedbackRequestId) {
                root.feedbackRequestId = ""
                root.reportingState = "error"
                root.reportingMessage = message
            } else if (requestId === root.bugReportRequestId) {
                root.bugReportRequestId = ""
                root.reportingState = "error"
                root.reportingMessage = message
            } else if (requestId === root.sessionAdRequestId) {
                root.sessionAdRequestId = ""
                root.streamMessage = message
            } else if (requestId === root.activeSessionRequestId) {
                root.activeSessionRequestId = ""
            } else if (requestId === root.remoteSessionsRequestId) {
                root.remoteSessionsRequestId = ""
                root.streamMessage = qsTr("Could not check existing sessions; starting a new one.")
                root.createPendingSession()
            } else if (requestId === root.sessionClaimRequestId) {
                const recovering = root.sessionClaimIsRecovery
                root.sessionClaimRequestId = ""
                root.sessionClaimIsRecovery = false
                if (recovering && root.sessionReconnectAttempts < 2) {
                    root.streamState = "reconnecting"
                    root.streamMessage = qsTr("Session recovery was interrupted. Retrying…")
                    Qt.callLater(() => root.recoverStreamingSession(message))
                } else {
                    root.streamState = "error"
                    root.streamMessage = message
                }
            } else if (requestId === root.streamCreateRequestId) {
                root.streamCreateRequestId = ""
                root.streamState = "error"
                root.streamMessage = message
                root.streamPollTimer.stop()
            } else if (requestId === root.streamPollRequestId) {
                root.streamPollRequestId = ""
                root.sessionReconnectAttempts += 1
                root.streamState = root.activeSession && root.sessionReconnectAttempts <= 8 ? "reconnecting" : "error"
                root.streamMessage = root.streamState === "reconnecting"
                    ? qsTr("Connection interrupted. Retrying…")
                    : message
                if (root.streamState === "reconnecting")
                    root.streamPollTimer.restart()
            } else if (requestId === root.streamStopRequestId) {
                root.streamStopRequestId = ""
                root.streamState = "error"
                root.streamMessage = message
            } else if (requestId === root.streamerStartRequestId) {
                root.streamerStartRequestId = ""
                root.streamerStatusTimer.stop()
                root.acceptStreamerSnapshot({ status: "error", message: message, errorCode: code })
            } else if (requestId === root.streamerStatusRequestId) {
                root.streamerStatusRequestId = ""
            } else if (requestId === root.streamerStopRequestId) {
                root.streamerStopRequestId = ""
                root.lastError = message
            } else if (requestId === root.streamInputPauseRequestId) {
                root.streamInputPauseRequestId = ""
            } else if (requestId === root.streamControlRequestId) {
                root.streamControlRequestId = ""
                root.streamControlAction = ""
                root.streamControlMessage = message
            }
        }
        function onEventReceived(name, payload) {
            if (name === "settings.changed") {
                if (payload.key === "launchInConsoleMode") {
                    const persisted = Boolean(payload.value)
                    root.consoleSurfaceConfirmedValue = persisted
                    root.consoleSurfaceInitialized = true
                    if (root.consoleSurfaceRequestId !== ""
                            && root.consoleSurfaceDesiredValue !== persisted)
                        return
                    root.consoleSurfaceDesiredValue = persisted
                    root.applySetting(payload.key, persisted)
                    root.consoleSurfaceRequested(persisted)
                } else {
                    root.applySetting(payload.key, payload.value)
                }
            } else if (name === "settings.reset")
                root.refreshSettings()
            else if (name === "auth.session.changed") {
                root.authSession = payload.session || null
                root.authState = root.authSession ? "signed-in" : "idle"
                if (root.authSession)
                    root.refreshRemoteSessions()
                else
                    root.remoteSessions = []
            } else if (name === "session.changed")
                root.acceptStreamingSession(payload.session || null)
            else if (name === "streamer.changed")
                root.acceptStreamerSnapshot(payload.streamer || payload || null)
            else if (name === "artwork.ready")
                root.acceptArtworkResult(payload)
            else if (name === "updater.changed")
                root.updaterState = payload
            else if (name === "updater.highlights.show") {
                root.releaseHighlights = payload
                AppController.navigate("updates")
                CoreClient.request("updater.highlights.ack", {version: payload.version || ""})
            }
        }
    }
}
