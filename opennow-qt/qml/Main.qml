import QtQuick
import QtQuick.Controls
import OpenNOW

ApplicationWindow {
    id: window
    width: 1600
    height: 900
    minimumWidth: 960
    minimumHeight: 540
    visible: true
    visibility: ApplicationWindow.Windowed
    color: activeRoute === "stream" ? "transparent" : Theme.shell
    title: qsTr("OpenNOW")

    property string activeRoute: AppController.route
    property bool geometryRestored: false
    readonly property bool settingsLoaded: Object.keys(ShellStore.settings || {}).length > 0
    property bool consoleHeldByPad: false
    property bool pointerRecentlyActive: false
    property bool forceConsole: false
    readonly property bool switchToConsoleOnPad: !settingsLoaded
        || ShellStore.settings.switchToConsoleOnPad !== false
    readonly property bool leaveConsoleOnPointer: !settingsLoaded
        || ShellStore.settings.leaveConsoleOnPointer !== false
    readonly property bool pointerHoldsDesktop: leaveConsoleOnPointer && pointerRecentlyActive
        && LaunchModeOverride !== "console"
        && !forceConsole
    readonly property bool consolePreferred: LaunchModeOverride === "console"
        || forceConsole
        || (!pointerHoldsDesktop && (
            (settingsLoaded && ShellStore.settings.launchInConsoleMode === true)
            || (consoleHeldByPad && switchToConsoleOnPad)))
    readonly property bool desktopRequested: LaunchModeOverride === "desktop"
        || (LaunchModeOverride !== "console" && !consolePreferred)
    readonly property bool desktopEligibleRoute: ["joining",
        "accounts", "profile-pin", "game-accounts", "persistent-storage", "media",
        "diagnostics", "updates", "feedback", "theme-store"].indexOf(activeRoute) < 0
    readonly property bool targetDesktopSurface: desktopRequested && desktopEligibleRoute
    readonly property bool desktopStreamOverlayActive: desktopSurfaceActive && activeRoute === "stream"
        && AppController.overlay.startsWith("desktop-stream-")
    // The desktop shell owns only its own stream overlays, so anything else keeps
    // rendering through the console overlay host instead of leaving a blank window.
    readonly property bool consoleOverlayFallbackActive: desktopSurfaceActive
        && AppController.overlay !== "" && !AppController.overlay.startsWith("desktop-stream-")
    readonly property bool desktopStatsOverlayActive: AppController.overlay === "desktop-stream-stats"
        || AppController.overlay === "desktop-stream-stats-expanded"
    property bool desktopSurfaceActive: targetDesktopSurface
    property bool modeInitialized: false
    readonly property bool settingsLoadedForSmokeTest: settingsLoaded
    readonly property bool consoleModePersistedForSmokeTest:
        ShellStore.settings.launchInConsoleMode === true
    readonly property bool modePersistenceBusyForSmokeTest:
        ShellStore.consoleSurfaceRequestId !== ""
    readonly property string modePersistenceErrorForSmokeTest: ShellStore.lastError
    readonly property var streamerSnapshotForSmokeTest: ShellStore.streamer
    readonly property real designWidth: desktopSurfaceActive ? 1440 : 1920
    readonly property real designHeight: desktopSurfaceActive ? 900 : 1080
    readonly property real designScale: Math.min(width / designWidth, height / designHeight)

    Timer {
        id: geometrySaveTimer
        interval: 450
        repeat: false
        onTriggered: {
            if (!window.geometryRestored || window.visibility !== ApplicationWindow.Windowed)
                return
            const savedWidth = Number(ShellStore.settings.windowWidth || 0)
            const savedHeight = Number(ShellStore.settings.windowHeight || 0)
            if (Math.round(window.width) !== savedWidth)
                ShellStore.setSetting("windowWidth", Math.round(window.width))
            if (Math.round(window.height) !== savedHeight)
                ShellStore.setSetting("windowHeight", Math.round(window.height))
        }
    }

    function updateStreamSurface() {
        ShellStore.setStreamSurface(
            window.x,
            window.y,
            window.width,
            window.height,
            window.screen ? window.screen.devicePixelRatio : 1
        )
    }

    function syncStreamWindowVisibility() {
        const nativePresentationOwnsWindow = AppController.route === "stream"
            && ShellStore.streamer
            && ShellStore.streamer.status === "streaming"
            && AppController.overlay === ""
        if (nativePresentationOwnsWindow) {
            if (window.visible)
                window.hide()
        } else if (!window.visible) {
            window.show()
            window.raise()
            window.requestActivate()
        }
    }

    onXChanged: updateStreamSurface()
    onYChanged: updateStreamSurface()
    onWidthChanged: {
        updateStreamSurface()
        if (geometryRestored)
            geometrySaveTimer.restart()
    }
    onHeightChanged: {
        updateStreamSurface()
        if (geometryRestored)
            geometrySaveTimer.restart()
    }
    onScreenChanged: updateStreamSurface()

    function syncInputOwnership() {
        const shellOwnsInput = AppController.route !== "stream"
            || (AppController.overlay !== "" && !window.desktopStatsOverlayActive)
        ControllerInput.shellCaptureEnabled = shellOwnsInput
            && ShellStore.settings.controllerMode !== false
        if (AppController.route === "stream")
            ShellStore.setStreamInputPaused(shellOwnsInput)
    }
    Timer {
        id: pointerGrace
        interval: 30000
        repeat: false
        onTriggered: window.pointerRecentlyActive = false
    }

    function applyConsoleSurface(enabled) {
        window.pointerRecentlyActive = false
        window.forceConsole = enabled
        if (!enabled)
            window.consoleHeldByPad = false
        if (ShellStore.signedIn && AppController.route === "sign-in")
            AppController.navigate("home")
        window.synchronizeRenderedSurface()
    }

    function requestConsoleSurface(enabled) {
        ShellStore.requestConsoleSurface(enabled)
    }

    function synchronizeRenderedSurface() {
        if (window.desktopSurfaceActive === window.targetDesktopSurface)
            return
        window.desktopSurfaceActive = window.targetDesktopSurface
        if (modeInitialized)
            modeTransition.restart()
    }

    function notePointerInput() {
        window.pointerRecentlyActive = true
        pointerGrace.restart()
        if (window.leaveConsoleOnPointer && LaunchModeOverride !== "console" && !window.forceConsole)
            window.consoleHeldByPad = false
    }

    function syncPadHold() {
        if (AppController.controllerCount > 0
                && window.switchToConsoleOnPad
                && !window.pointerRecentlyActive)
            window.consoleHeldByPad = true
    }

    Component.onCompleted: {
        syncPadHold()
        modeInitialized = true
        window.synchronizeRenderedSurface()
        syncInputOwnership()
        updateStreamSurface()
    }

    HoverHandler {
        acceptedDevices: PointerDevice.Mouse
        onPointChanged: {
            AppController.inputMode = "pointer"
            window.notePointerInput()
        }
    }

    function componentForRoute(route) {
        if (route === "library")
            return libraryScreen
        if (route === "store")
            return storeScreen
        if (route === "theme-store")
            return themeStoreScreen
        if (route === "settings" || route === "settings-streaming")
            return settingsStreamingScreen
        if (route === "controllers" || route === "settings-input")
            return settingsInputScreen
        if (route === "settings-account")
            return settingsAccountScreen
        if (route === "settings-video")
            return settingsVideoScreen
        if (route === "settings-video-dropdown")
            return settingsVideoDropdownScreen
        if (route === "settings-network")
            return settingsNetworkScreen
        if (route === "settings-themes")
            return settingsThemesScreen
        if (route === "settings-advanced")
            return settingsAdvancedScreen
        if (route === "settings-advanced-dropdown")
            return settingsAdvancedDropdownScreen
        if (route === "game-detail")
            return gameDetailScreen
        if (route === "game-detail-platform-dropdown")
            return gameDetailPlatformDropdownScreen
        if (route === "sign-in")
            return signInScreen
        if (route === "joining")
            return joiningScreen
        if (route === "inserting")
            return insertingScreen
        if (route === "stream")
            return streamScreen
        if (route === "accounts")
            return accountsScreen
        if (route === "profile-pin")
            return profilePinScreen
        if (route === "game-accounts")
            return gameAccountsScreen
        if (route === "persistent-storage")
            return persistentStorageScreen
        if (route === "media")
            return mediaScreen
        if (route === "diagnostics")
            return diagnosticsScreen
        if (route === "updates")
            return updateScreen
        if (route === "feedback")
            return feedbackScreen
        return homeScreen
    }

    FocusScope {
        x: window.desktopSurfaceActive ? 0 : Math.round((window.width - width * scale) / 2)
        y: window.desktopSurfaceActive ? 0 : Math.round((window.height - height * scale) / 2)
        width: window.desktopSurfaceActive ? window.width : window.designWidth
        height: window.desktopSurfaceActive ? window.height : window.designHeight
        scale: window.desktopSurfaceActive ? 1 : window.designScale
        transformOrigin: Item.TopLeft
        focus: true

        Loader {
            id: routeLoader
            anchors.fill: parent
            sourceComponent: window.desktopSurfaceActive
                ? desktopAppScreen : window.componentForRoute(window.activeRoute)
            opacity: 1

            onLoaded: {
                if (item) {
                    item.forceActiveFocus()
                    if (!window.desktopSurfaceActive
                            && (window.activeRoute === "settings-video-dropdown"
                            || window.activeRoute === "settings-advanced-dropdown")) {
                        item.initialDropdownOpen = true
                        Qt.callLater(item.openInitialDropdown)
                    }
                }
            }

            Behavior on opacity {
                NumberAnimation { duration: Theme.enterDuration; easing.type: Easing.OutCubic }
            }
        }

        Connections {
            target: AppController
            function onRouteChanged() {
                routeLoader.opacity = 0
                routeEnter.restart()
                window.syncInputOwnership()
                window.syncStreamWindowVisibility()
            }
            function onOverlayChanged() {
                if (window.desktopSurfaceActive && window.activeRoute === "stream"
                        && AppController.overlay === "guide-session") {
                    Qt.callLater(() => AppController.showOverlay("desktop-stream-menu"))
                    return
                }
                window.syncInputOwnership()
                window.syncStreamWindowVisibility()
                if (AppController.overlay === "" && routeLoader.item)
                    Qt.callLater(() => routeLoader.item.forceActiveFocus())
            }
            function onDirectLaunchRequested(appId, title) {
                if (ShellStore.settings.autoFullScreen)
                    window.showFullScreen()
                ShellStore.acceptDirectLaunch(appId, title)
            }
        }
        Connections {
            target: ShellStore
            function onStreamerChanged() { window.syncStreamWindowVisibility() }
            function onConsoleSurfaceRequested(enabled) { window.applyConsoleSurface(enabled) }
            function onSettingsChanged() {
                window.syncInputOwnership()
                if (window.geometryRestored || !ShellStore.settings.windowWidth)
                    return
                window.width = Number(ShellStore.settings.windowWidth)
                window.height = Number(ShellStore.settings.windowHeight)
                window.geometryRestored = true
                window.updateStreamSurface()
            }
        }
        Timer {
            id: routeEnter
            interval: AppController.reducedMotion ? 0 : 45
            onTriggered: routeLoader.opacity = 1
        }

        Keys.onPressed: event => {
            if (event.key === Qt.Key_F10) {
                window.requestConsoleSurface(window.desktopSurfaceActive)
                event.accepted = true
            } else if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
                event.accepted = AppController.goBack()
            } else if (event.key === Qt.Key_PageUp) {
                event.accepted = AppController.overlay.startsWith("guide-")
                    ? AppController.cycleGuidePage(-1)
                    : AppController.cyclePrimaryRoute(-1)
            } else if (event.key === Qt.Key_PageDown) {
                event.accepted = AppController.overlay.startsWith("guide-")
                    ? AppController.cycleGuidePage(1)
                    : AppController.cyclePrimaryRoute(1)
            } else if (event.key === Qt.Key_F1) {
                event.accepted = AppController.showOverlay(window.desktopSurfaceActive
                    && window.activeRoute === "stream" ? "desktop-stream-menu" : "guide-session")
            } else if (event.key === Qt.Key_F3 && window.desktopSurfaceActive
                       && window.activeRoute === "stream") {
                event.accepted = AppController.showOverlay("desktop-stream-stats")
            } else if (event.key === Qt.Key_Menu) {
                event.accepted = AppController.showOverlay("quick-settings")
            } else if (event.key === Qt.Key_Y) {
                event.accepted = AppController.showOverlay("friends")
            } else if (event.key === Qt.Key_X
                       && (AppController.route === "home" || AppController.route === "library")) {
                event.accepted = AppController.navigate("game-detail")
            }
        }
    }

    OverlayHost {
        readonly property real consoleScale: Math.min(window.width / 1920, window.height / 1080)
        x: Math.round((window.width - width * consoleScale) / 2)
        y: Math.round((window.height - height * consoleScale) / 2)
        width: 1920
        height: 1080
        scale: consoleScale
        transformOrigin: Item.TopLeft
        overlay: AppController.overlay
        visible: !window.desktopSurfaceActive || window.consoleOverlayFallbackActive
        z: 1000
    }

    DesktopStreamOverlayHost {
        anchors.fill: parent
        overlay: AppController.overlay
        visible: window.desktopStreamOverlayActive
        focus: visible
        z: 1100
        onVisibleChanged: if (visible) forceActiveFocus()
    }

    Rectangle {
        id: modeCurtain
        anchors.fill: parent
        color: Theme.shell
        opacity: 0
        visible: opacity > 0
        z: 2000
        Column {
            anchors.centerIn: parent
            spacing: 10
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: window.targetDesktopSurface ? qsTr("COMPUTER MODE") : qsTr("CONSOLE MODE")
                color: Theme.label
                font.family: Theme.displayFont
                font.pixelSize: 28
                font.weight: Font.Black
                font.letterSpacing: 1.2
            }
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: ShellStore.signedIn
                    ? qsTr("Keeping you signed in")
                    : qsTr("Switching shell")
                color: Theme.textMuted
                font.family: Theme.bodyFont
                font.pixelSize: 14
                font.weight: Font.DemiBold
            }
        }
    }
    SequentialAnimation {
        id: modeTransition
        NumberAnimation { target: modeCurtain; property: "opacity"; to: 1; duration: AppController.reducedMotion ? 0 : 240; easing.type: Easing.OutCubic }
        PauseAnimation { duration: AppController.reducedMotion ? 0 : 160 }
        ScriptAction {
            script: {
                if (ShellStore.signedIn && AppController.route === "sign-in")
                    AppController.navigate("home")
            }
        }
        NumberAnimation { target: modeCurtain; property: "opacity"; to: 0; duration: AppController.reducedMotion ? 0 : 380; easing.type: Easing.OutCubic }
    }
    onTargetDesktopSurfaceChanged: {
        window.synchronizeRenderedSurface()
    }

    Item {
        id: accessibilityAnnouncer
        x: -10
        width: 1
        height: 1
        opacity: 0.01
        Accessible.name: qsTr("OpenNOW status")
        Accessible.role: Accessible.StaticText

        function routeName(route) {
            const names = {
                "home": qsTr("Home"),
                "library": qsTr("Library"),
                "store": qsTr("Store"),
                "theme-store": qsTr("Theme store"),
                "controllers": qsTr("Controllers"),
                "settings": qsTr("Settings"),
                "settings-account": qsTr("Account settings"),
                "settings-streaming": qsTr("Streaming settings"),
                "settings-video": qsTr("Video settings"),
                "settings-video-dropdown": qsTr("Video setting choices"),
                "game-detail-platform-dropdown": qsTr("Platform choices"),
                "settings-input": qsTr("Input settings"),
                "settings-network": qsTr("Network settings"),
                "settings-themes": qsTr("Theme settings"),
                "settings-advanced": qsTr("Advanced settings"),
                "settings-advanced-dropdown": qsTr("Advanced setting choices"),
                "game-detail": qsTr("Game details"),
                "sign-in": qsTr("Sign in"),
                "joining": qsTr("Controller order"),
                "inserting": qsTr("Preparing session"),
                "stream": qsTr("Stream"),
                "accounts": qsTr("Accounts"),
                "profile-pin": qsTr("Profile security"),
                "game-accounts": qsTr("Game accounts"),
                "persistent-storage": qsTr("Persistent storage"),
                "media": qsTr("Captures"),
                "diagnostics": qsTr("Diagnostics"),
                "updates": qsTr("Updates"),
                "feedback": qsTr("Feedback"),
                "friends": qsTr("Friends"),
                "friend-actions": qsTr("Friend actions"),
                "quick-settings": qsTr("Quick settings"),
                "session-conflict": qsTr("Session conflict"),
                "queue-ad": qsTr("Queue message"),
                "guide-session": qsTr("Session guide"),
                "guide-controls": qsTr("Controls guide"),
                "guide-media": qsTr("Media guide"),
                "guide-shortcuts": qsTr("Shortcuts guide")
            }
            return names[String(route)] || String(route)
        }
        function announce(message, assertive) {
            if (assertive)
                Accessible.announce(message, Accessible.Assertive)
            else
                Accessible.announce(message)
        }

        Connections {
            target: AppController
            function onRouteChanged() { accessibilityAnnouncer.announce(qsTr("Screen: %1").arg(accessibilityAnnouncer.routeName(AppController.route))) }
            function onOverlayChanged() {
                accessibilityAnnouncer.announce(AppController.overlay === ""
                    ? qsTr("Overlay closed")
                    : qsTr("%1 overlay opened").arg(accessibilityAnnouncer.routeName(AppController.overlay)))
            }
            function onControllerCountChanged() {
                window.syncPadHold()
                accessibilityAnnouncer.announce(AppController.controllerCount === 0
                    ? qsTr("All controllers disconnected")
                    : qsTr("%1 controllers connected").arg(AppController.controllerCount))
            }
            function onInputModeChanged() {
                if (AppController.inputMode === "pointer" || AppController.inputMode === "keyboard")
                    window.notePointerInput()
            }
        }
        Connections {
            target: ShellStore
            function onAccessibilityMessageChanged() {
                if (ShellStore.accessibilityMessage !== "")
                    accessibilityAnnouncer.announce(ShellStore.accessibilityMessage)
            }
            function onLastErrorChanged() {
                if (ShellStore.lastError !== "")
                    accessibilityAnnouncer.announce(qsTr("Error: %1").arg(ShellStore.lastError), true)
            }
        }
    }

    Component { id: desktopAppScreen; DesktopApp {} }
    Component { id: homeScreen; HomeScreen {} }
    Component { id: libraryScreen; LibraryScreen {} }
    Component { id: storeScreen; StoreScreen {} }
    Component { id: themeStoreScreen; ThemeStoreScreen {} }
    Component { id: settingsStreamingScreen; SettingsScreen { initialSection: 1 } }
    Component { id: settingsAccountScreen; SettingsScreen { initialSection: 0 } }
    Component { id: settingsVideoScreen; SettingsScreen { initialSection: 2 } }
    Component { id: settingsVideoDropdownScreen; SettingsScreen { initialSection: 2; initialDropdownOpen: true } }
    Component { id: settingsInputScreen; SettingsScreen { initialSection: 3 } }
    Component { id: settingsNetworkScreen; SettingsScreen { initialSection: 4 } }
    Component { id: settingsThemesScreen; SettingsScreen { initialSection: 5 } }
    Component { id: settingsAdvancedScreen; SettingsScreen { initialSection: 6 } }
    Component { id: settingsAdvancedDropdownScreen; SettingsScreen { initialSection: 6; initialDropdownOpen: true } }
    Component { id: gameDetailScreen; GameDetailScreen {} }
    Component {
        id: gameDetailPlatformDropdownScreen
        GameDetailScreen {
            initialPlatformOpen: SmokeTestMode
            previewGame: SmokeTestMode ? SmokeTestGame : null
        }
    }
    Component { id: signInScreen; SignInScreen {} }
    Component { id: joiningScreen; JoiningScreen {} }
    Component { id: insertingScreen; InsertingScreen {} }
    Component { id: streamScreen; StreamScreen {} }
    Component { id: accountsScreen; AccountsScreen {} }
    Component { id: profilePinScreen; ProfilePinScreen {} }
    Component { id: gameAccountsScreen; GameAccountsScreen {} }
    Component { id: persistentStorageScreen; PersistentStorageScreen {} }
    Component { id: mediaScreen; MediaScreen {} }
    Component { id: diagnosticsScreen; DiagnosticsScreen {} }
    Component { id: updateScreen; UpdateScreen {} }
    Component { id: feedbackScreen; FeedbackScreen {} }
}
