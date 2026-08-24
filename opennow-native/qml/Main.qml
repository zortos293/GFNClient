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
    property bool showingDetails: false
    property bool showingStream: false
    property bool reducedMotion: false
    property bool signedIn: false
    readonly property var games: [
        { title: "Cyber Drift 2088", subtitle: "Last played 2 h ago", badge: "CONTINUE", variant: 0, progress: 0.64, genre: "Open-world racing", store: "Steam", description: "Return to the neon megacity with your latest cloud save and a low-latency performance profile." },
        { title: "Starfall Frontier", subtitle: "Steam · 84 h played", badge: "RTX ON", variant: 1, progress: 0, genre: "Space adventure", store: "Steam", description: "Explore the frontier with ray-traced lighting and synchronized progress." },
        { title: "Nightfall Protocol", subtitle: "Epic · 12 h played", badge: "", variant: 2, progress: 0, genre: "Tactical action", store: "Epic", description: "Coordinate your squad through a responsive cloud session." },
        { title: "Iron Harvest 2", subtitle: "GOG · 37 h played", badge: "120 FPS", variant: 3, progress: 0, genre: "Strategy", store: "GOG", description: "Command mechanized armies with a high-refresh input profile." },
        { title: "Voidrunner", subtitle: "Steam · 3 h played", badge: "", variant: 4, progress: 0, genre: "Action", store: "Steam", description: "Resume your run from the latest synchronized checkpoint." },
        { title: "Ashen Kingdom", subtitle: "Ubisoft · 51 h played", badge: "NEW", variant: 5, progress: 0, genre: "Role-playing", store: "Ubisoft", description: "Return to the kingdom with every save synchronized." }
    ]

    function openGame(game) {
        selectedGame = game
        showingDetails = true
        showingStream = false
    }

    function startStream(quality) {
        showingDetails = false
        showingStream = true
        streamEngine.startDemo(quality || "720p60")
    }

    function closeOverlay() {
        if (showingStream) {
            streamEngine.stop()
            showingStream = false
        } else if (showingDetails) {
            showingDetails = false
        }
    }

    function focusCurrentView() {
        if (!signedIn)
            signInPage.forceActiveFocus()
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
        else
            settingsPage.forceActiveFocus()
    }

    function switchSection(delta) {
        if (signedIn && !showingDetails && !showingStream)
            currentPage = (currentPage + delta + 4) % 4
    }

    Component.onCompleted: Qt.callLater(focusCurrentView)
    onCurrentPageChanged: Qt.callLater(focusCurrentView)
    onShowingDetailsChanged: Qt.callLater(focusCurrentView)
    onShowingStreamChanged: Qt.callLater(focusCurrentView)
    onSignedInChanged: Qt.callLater(focusCurrentView)

    Shortcut { sequence: "Escape"; onActivated: window.closeOverlay() }
    Shortcut { sequence: "Alt+Left"; onActivated: window.closeOverlay() }
    Shortcut { sequence: "Ctrl+Tab"; onActivated: window.switchSection(1) }
    Shortcut { sequence: "Ctrl+Shift+Tab"; onActivated: window.switchSection(-1) }
    Connections {
        target: controllerInput
        function onSectionRequested(delta) { window.switchSection(delta) }
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
                onStartGame: function(game) { window.selectedGame = game; window.startStream("720p60") }
            }
            LibraryPage {
                id: libraryPage
                anchors.fill: parent
                visible: window.currentPage === 1 && !window.showingDetails && !window.showingStream
                focus: visible
                games: window.games
                onOpenGame: function(game) { window.openGame(game) }
            }
            SearchPage {
                id: searchPage
                anchors.fill: parent
                visible: window.currentPage === 2 && !window.showingDetails && !window.showingStream
                focus: visible
                games: window.games
                onOpenGame: function(game) { window.openGame(game) }
            }
            SettingsPage {
                id: settingsPage
                anchors.fill: parent
                visible: window.currentPage === 3 && !window.showingDetails && !window.showingStream
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
                onPlay: function(quality) { window.startStream(quality) }
            }
            StreamPage {
                id: streamPage
                anchors.fill: parent
                visible: window.showingStream
                focus: visible
                game: window.selectedGame
                onExit: window.closeOverlay()
            }
        }
        }
    }
}
