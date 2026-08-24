import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

ApplicationWindow {
    id: window
    width: 1440
    height: 900
    minimumWidth: 1000
    minimumHeight: 650
    visible: true
    color: Theme.canvas
    title: "OpenNOW Native Prototype"

    property int currentPage: 0
    property var selectedGame: games[0]
    property bool showingDetails: false
    property bool showingStream: false
    property bool reducedMotion: false
    readonly property var games: [
        { title: "Cyberpunk 2077", subtitle: "Played 2 hours ago", badge: "CONTINUE", variant: 0, progress: 0.64, genre: "Open-world action RPG", store: "Steam", description: "Night City waits. Resume instantly on a nearby GeForce NOW rig with your preferred performance profile." },
        { title: "Alan Wake 2", subtitle: "Ready to stream", badge: "RTX", variant: 1, progress: 0, genre: "Survival horror", store: "Epic Games", description: "A cinematic mystery rendered with full ray tracing. Your cloud save is synchronized and ready." },
        { title: "Forza Horizon 5", subtitle: "Played yesterday", badge: "120 FPS", variant: 2, progress: 0.31, genre: "Open-world racing", store: "Xbox", description: "Drive across a living Mexico with low-latency controller input and a high-refresh stream." },
        { title: "Baldur's Gate 3", subtitle: "Cloud save synced", badge: "SYNCED", variant: 3, progress: 0.82, genre: "Role-playing", store: "Steam", description: "Return to your party with every save synchronized across devices." },
        { title: "Control", subtitle: "Ready to stream", badge: "ULTIMATE", variant: 4, progress: 0, genre: "Action adventure", store: "Steam", description: "Explore the Oldest House with high-fidelity ray tracing and responsive mouse input." },
        { title: "The Witcher 3", subtitle: "Last played 4 days ago", badge: "COMPLETE", variant: 5, progress: 0.47, genre: "Open-world RPG", store: "GOG", description: "Continue Geralt's journey from your latest synchronized cloud save." }
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
        if (showingStream)
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
        if (!showingDetails && !showingStream)
            currentPage = (currentPage + delta + 4) % 4
    }

    Component.onCompleted: Qt.callLater(focusCurrentView)
    onCurrentPageChanged: Qt.callLater(focusCurrentView)
    onShowingDetailsChanged: Qt.callLater(focusCurrentView)
    onShowingStreamChanged: Qt.callLater(focusCurrentView)

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

        Rectangle {
            width: parent.width * 0.68
            height: parent.height * 0.7
            x: parent.width * 0.32
            y: -height * 0.45
            radius: width / 2
            color: "#0b2817"
            opacity: 0.24
        }

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
