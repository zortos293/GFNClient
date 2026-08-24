import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

ApplicationWindow {
    id: window
    width: 1920
    height: 1080
    minimumWidth: 1000
    minimumHeight: 650
    visible: true
    visibility: Window.FullScreen
    color: Theme.canvas
    title: "OpenNOW Native Prototype"

    property int currentPage: 0
    property var selectedGame: games[0]
    property string pendingQuality: "720p60"
    property bool showingDetails: false
    property bool showingStream: false
    property bool showingProfilePicker: false
    property bool showingServerSelector: false
    property bool launchAfterProfile: false
    property bool reducedMotion: false
    property string toastMessage: ""
    property string toastDetail: ""
    property string toastTone: "success"
    property bool signedIn: Qt.application.arguments.indexOf("--demo-signed-in") >= 0
    readonly property var games: [
        { title: "Cyber Drift 2088", subtitle: "Last played 2 h ago", badge: "CONTINUE", variant: 0, progress: 0.64, genre: "Open-world racing", store: "Steam", description: "Return to the neon megacity with your latest cloud save and a low-latency performance profile." },
        { title: "Starfall Frontier", subtitle: "Steam · 84 h played", badge: "RTX ON", variant: 1, progress: 0, genre: "Space adventure", store: "Steam", description: "Explore the frontier with ray-traced lighting and synchronized progress." },
        { title: "Nightfall Protocol", subtitle: "Epic · 12 h played", badge: "", variant: 2, progress: 0, genre: "Tactical action", store: "Epic", description: "Coordinate your squad through a responsive cloud session." },
        { title: "Iron Harvest 2", subtitle: "GOG · 37 h played", badge: "120 FPS", variant: 3, progress: 0, genre: "Strategy", store: "GOG", description: "Command mechanized armies with a high-refresh input profile." },
        { title: "Voidrunner", subtitle: "Steam · 3 h played", badge: "", variant: 4, progress: 0, genre: "Action", store: "Steam", description: "Resume your run from the latest synchronized checkpoint." },
        { title: "Ashen Kingdom", subtitle: "Ubisoft · 51 h played", badge: "NEW", variant: 5, progress: 0, genre: "Role-playing", store: "Ubisoft", description: "Return to the kingdom with every save synchronized." }
    ]

    function showToast(message, detail, tone) {
        toastMessage = message
        toastDetail = detail || ""
        toastTone = tone || "success"
        toastTimer.restart()
    }

    function openGame(game) {
        selectedGame = game
        showingDetails = true
        showingStream = false
    }

    function startStream(quality) {
        showingDetails = false
        showingServerSelector = false
        showingStream = true
        streamEngine.startDemo(quality || "720p60")
        streamEngine.setBitrate(Number(appState.preference("streaming.bitrate", 75)) * 1000)
    }

    function configuredQuality() {
        var resolution = String(appState.preference("streaming.resolution", "1440p (QHD)"))
        var fps = Number(appState.preference("streaming.frameRate", "120"))
        if (resolution.indexOf("4K") >= 0)
            return "4k60"
        if (resolution.indexOf("1440p") >= 0)
            return "1440p120"
        if (resolution.indexOf("1080p") >= 0 && fps >= 120)
            return "1080p120"
        if (resolution.indexOf("1080p") >= 0)
            return "1080p60"
        return "720p60"
    }

    function requestLaunch(game, quality) {
        selectedGame = game
        var requestedQuality = quality || configuredQuality()
        if (!Boolean(appState.preference("streaming.enableStressProfiles", false))
                && (requestedQuality === "1440p120" || requestedQuality === "1080p120" || requestedQuality === "4k60"))
            pendingQuality = "720p60"
        else
            pendingQuality = requestedQuality
        showingDetails = false
        if (appState.preference("account.console.profilePicker", true)) {
            launchAfterProfile = true
            showingProfilePicker = true
        } else {
            showingServerSelector = true
        }
    }

    function closeOverlay() {
        if (showingStream) {
            streamEngine.stop()
            showingStream = false
        } else if (showingServerSelector) {
            showingServerSelector = false
        } else if (showingDetails) {
            showingDetails = false
        } else if (showingProfilePicker) {
            showingProfilePicker = false
        }
    }

    function focusCurrentView() {
        if (!signedIn)
            signInPage.forceActiveFocus()
        else if (showingProfilePicker)
            profilePicker.forceActiveFocus()
        else if (showingServerSelector)
            serverSelector.forceActiveFocus()
        else if (showingStream)
            streamPage.forceActiveFocus()
        else if (showingDetails)
            detailPage.forceActiveFocus()
        else if (currentPage === 0)
            homePage.forceActiveFocus()
        else if (currentPage === 1)
            libraryPage.forceActiveFocus()
        else if (currentPage === 2)
            searchPage.forceActiveFocus()
        else if (currentPage === 3)
            sessionsPage.forceActiveFocus()
        else
            settingsPage.forceActiveFocus()
    }

    function switchSection(delta) {
        if (signedIn && !showingProfilePicker && !showingServerSelector && !showingDetails && !showingStream)
            currentPage = (currentPage + delta + 5) % 5
    }

    Component.onCompleted: {
        var appArgs = Qt.application.arguments
        if (!Boolean(appState.preference("video.fullscreenOnLaunch", true)))
            visibility = Window.Windowed
        if (appArgs.indexOf("--demo-profile-picker") >= 0) {
            signedIn = true
            showingProfilePicker = true
        }
        if (appArgs.indexOf("--demo-server-selector") >= 0) {
            signedIn = true
            showingServerSelector = true
        }
        for (var i = 0; i < appArgs.length; ++i) {
            if (appArgs[i].indexOf("--demo-page=") !== 0)
                continue
            signedIn = true
            var pageName = appArgs[i].substring(12)
            currentPage = pageName === "library" ? 1 : (pageName === "search" ? 2 : (pageName === "sessions" ? 3 : (pageName === "settings" ? 4 : 0)))
        }
        Qt.callLater(focusCurrentView)
    }
    onCurrentPageChanged: Qt.callLater(focusCurrentView)
    onShowingDetailsChanged: Qt.callLater(focusCurrentView)
    onShowingStreamChanged: Qt.callLater(focusCurrentView)
    onShowingProfilePickerChanged: Qt.callLater(focusCurrentView)
    onShowingServerSelectorChanged: Qt.callLater(focusCurrentView)
    onSignedInChanged: Qt.callLater(focusCurrentView)

    Shortcut { sequence: "Escape"; enabled: !window.showingStream; onActivated: window.closeOverlay() }
    Shortcut { sequence: "Alt+Left"; enabled: !window.showingStream; onActivated: window.closeOverlay() }
    Shortcut { sequence: "Ctrl+Tab"; onActivated: window.switchSection(1) }
    Shortcut { sequence: "Ctrl+Shift+Tab"; onActivated: window.switchSection(-1) }
    Shortcut { sequence: "F10"; onActivated: window.visibility = window.visibility === Window.FullScreen ? Window.Windowed : Window.FullScreen }
    Shortcut { sequence: "X"; enabled: window.signedIn && window.currentPage === 3 && !window.showingProfilePicker && !window.showingServerSelector && !window.showingDetails && !window.showingStream; onActivated: sessionsPage.exportSessions() }
    Shortcut { sequence: "Y"; enabled: window.signedIn && window.currentPage <= 1 && !window.showingProfilePicker && !window.showingServerSelector && !window.showingDetails && !window.showingStream; onActivated: window.currentPage = 2 }
    Connections {
        target: controllerInput
        function onConnectedChanged() {
            if (controllerInput.connected)
                window.showToast(controllerInput.controllerName + " connected", "Player 1 · Controller mode", "success")
            else
                window.showToast("Controller disconnected", "Keyboard controls remain active", "warning")
        }
        function onSectionRequested(delta) {
            if (window.showingProfilePicker || window.showingServerSelector || window.showingDetails || window.showingStream)
                return
            if (homePage.visible)
                homePage.browseRows(delta)
            else if (libraryPage.visible)
                libraryPage.cycleFilter(delta)
            else if (sessionsPage.visible)
                sessionsPage.cycleRange(delta)
            else if (settingsPage.visible)
                settingsPage.cycleSection(delta)
            else if (window.signedIn)
                window.switchSection(delta)
            else
                signInPage.cycleProvider(delta)
        }
    }
    Connections {
        target: appState
        function onExportCompleted(path) {
            if (path.length > 0)
                window.showToast("Session report exported", path, "success")
            else
                window.showToast("Session report could not be exported", "Check Downloads folder permissions", "error")
        }
    }
    Connections {
        target: streamEngine
        function onRuntimeEvent(type, payload) {
            if (type === "error")
                window.showToast("Stream runtime error", payload.message || "The session stopped", "error")
            else if (type === "state" && payload.phase === "streaming")
                window.showToast("Session connected", appState.serverName + " · " + appState.serverLatency + " ms", "success")
        }
    }
    Timer { id: toastTimer; interval: 4200; onTriggered: window.toastMessage = "" }
    Connections {
        target: authEngine
        function onAuthorized() {
            window.signedIn = true
            window.showingProfilePicker = appState.preference("account.console.profilePicker", true)
        }
        function onSignedInChanged() {
            if (!authEngine.signedIn && Qt.application.arguments.indexOf("--demo-signed-in") < 0) {
                window.signedIn = false
                window.showingProfilePicker = false
                window.showingDetails = false
                window.showingStream = false
            }
        }
    }

    Rectangle {
        anchors.fill: parent
        color: Theme.canvas

        SignInPage {
            id: signInPage
            anchors.fill: parent
            visible: !window.signedIn
            focus: visible
            onSignedIn: window.signedIn = true
        }

        Item {
            id: signedInShell
            anchors.fill: parent
            visible: window.signedIn

        NavigationRail {
            id: rail
            anchors.left: parent.left
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            currentIndex: window.currentPage
            visible: !window.showingStream
            onNavigate: function(index) {
                window.currentPage = index
                window.showingDetails = false
            }
            onProfileRequested: window.showingProfilePicker = true
        }

        Item {
            id: pageHost
            anchors.left: rail.visible ? rail.right : parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.bottom: parent.bottom

            HomePage {
                id: homePage
                anchors.fill: parent
                visible: window.currentPage === 0 && !window.showingDetails && !window.showingStream
                focus: visible
                games: window.games
                onOpenGame: function(game) { window.openGame(game) }
                onStartGame: function(game) { window.requestLaunch(game, window.configuredQuality()) }
                onChooseServer: { window.selectedGame = window.games[0]; window.showingServerSelector = true }
            }
            LibraryPage {
                id: libraryPage
                anchors.fill: parent
                visible: window.currentPage === 1 && !window.showingDetails && !window.showingStream
                focus: visible
                games: window.games
                onOpenGame: function(game) { window.openGame(game) }
                onStartGame: function(game) { window.requestLaunch(game, window.configuredQuality()) }
            }
            SearchPage {
                id: searchPage
                anchors.fill: parent
                visible: window.currentPage === 2 && !window.showingDetails && !window.showingStream
                focus: visible
                games: window.games
                onOpenGame: function(game) { window.openGame(game) }
            }
            SessionsPage {
                id: sessionsPage
                anchors.fill: parent
                visible: window.currentPage === 3 && !window.showingDetails && !window.showingStream
                focus: visible
            }
            SettingsPage {
                id: settingsPage
                anchors.fill: parent
                visible: window.currentPage === 4 && !window.showingDetails && !window.showingStream
                focus: visible
                reducedMotion: window.reducedMotion
                onReducedMotionChangedByUser: function(value) { window.reducedMotion = value }
            }
            GameDetailPage {
                id: detailPage
                anchors.fill: parent
                visible: window.showingDetails && !window.showingStream
                focus: visible
                game: window.selectedGame
                onBack: window.showingDetails = false
                onPlay: function(quality) { window.requestLaunch(window.selectedGame, quality) }
            }
            StreamPage {
                id: streamPage
                anchors.fill: parent
                visible: window.showingStream
                focus: visible
                game: window.selectedGame
                qualityId: window.pendingQuality
                onExit: window.closeOverlay()
                onScreenshotRequested: {
                    var path = appState.nextScreenshotPath()
                    signedInShell.grabToImage(function(result) {
                        if (!result.saveToFile(path))
                            window.showToast("Screenshot could not be saved", path, "error")
                    })
                }
            }
        }

        ProfilePickerPage {
            id: profilePicker
            anchors.fill: parent
            visible: window.showingProfilePicker
            focus: visible
            z: 80
            onProfileSelected: function(name) {
                appState.selectProfile(name)
                window.showingProfilePicker = false
                if (window.launchAfterProfile) {
                    window.launchAfterProfile = false
                    window.showingServerSelector = true
                } else {
                    window.currentPage = 0
                }
            }
            onAddAccount: authEngine.signOut()
            onManageProfiles: {
                window.showingProfilePicker = false
                window.currentPage = 4
            }
            onBack: {
                window.showingProfilePicker = false
                window.launchAfterProfile = false
            }
        }

        ServerSelectorDialog {
            id: serverSelector
            anchors.fill: parent
            visible: window.showingServerSelector
            focus: visible
            z: 90
            game: window.selectedGame
            onLaunch: function(serverId) {
                window.startStream(window.pendingQuality)
            }
            onCancelled: window.showingServerSelector = false
        }

        ToastBanner {
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: parent.top
            anchors.topMargin: 20
            message: window.toastMessage
            detail: window.toastDetail
            tone: window.toastTone
            z: 120
            onDismissed: window.toastMessage = ""
        }
        }
    }
}
