import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    width: 1440
    height: 900
    focus: true
    property bool commandOpen: false
    property string searchText: ""
    property string settingsSubtitle: qsTr("Profile")
    readonly property string route: AppController.route
    readonly property bool signInVisible: !ShellStore.signedIn || route === "sign-in"
    readonly property bool sessionStartingVisible: !signInVisible && route === "inserting"

    function titleForRoute(value) {
        if (value === "library" || value === "game-detail") return qsTr("Library")
        if (value === "store") return qsTr("Store")
        if (value === "friends") return qsTr("Friends")
        if (value.indexOf("settings") === 0 || value === "controllers") return qsTr("Settings")
        return qsTr("Home")
    }
    function subtitleForRoute(value) {
        if (value === "library" || value === "game-detail") return qsTr("%1 games").arg(ShellStore.catalogTotalCount || ShellStore.catalogGames.length)
        if (value === "store") return qsTr("4 free this week")
        if (value === "friends") return qsTr("3 of 24 online")
        if (value.indexOf("settings") === 0 || value === "controllers") return settingsSubtitle
        return qsTr("3 friends online")
    }
    function settingsSection(value) {
        if (value === "settings-streaming" || value === "settings-video" || value === "settings-video-dropdown") return 3
        if (value === "controllers" || value === "settings-input") return 5
        if (value === "settings-network") return 6
        if (value === "settings-themes") return 8
        if (value === "settings-advanced" || value === "settings-advanced-dropdown") return 11
        return 0
    }
    function contentForRoute(value) {
        if (value === "store") return storeComponent
        if (value === "friends") return friendsComponent
        if (value.indexOf("settings") === 0 || value === "controllers") return settingsComponent
        if (value === "library" || value === "game-detail") return libraryComponent
        return homeComponent
    }
    function enterConsoleMode() {
        ShellStore.setSetting("launchInConsoleMode", true)
        if (!ShellStore.signedIn)
            AppController.navigate("sign-in")
    }

    DesktopSignInScreen {
        id: desktopSignIn
        anchors.fill: parent
        visible: root.signInVisible
        z: 50
        // Restoring an existing session must not discard a requested deep link
        // (including screenshot/smoke-test routes). A completed interactive
        // sign-in still returns to Home as expected.
        onSignedIn: if (root.route === "sign-in") AppController.navigate("home")
    }

    DesktopShell {
        id: shell
        anchors.fill: parent
        visible: !root.signInVisible && !root.sessionStartingVisible
        route: root.route
        title: root.titleForRoute(root.route)
        subtitle: root.subtitleForRoute(root.route)
        searchText: root.searchText
        onSearchTextChanged: root.searchText = searchText
        onRouteRequested: route => AppController.navigate(route)
        onConsoleModeRequested: root.enterConsoleMode()
        onCommandPaletteRequested: root.commandOpen = true

        Loader {
            id: pageLoader
            anchors.fill: parent
            sourceComponent: root.contentForRoute(root.route)
            opacity: 1
            onLoaded: {
                if (shell.visible && item && item.forceActiveFocus)
                    Qt.callLater(() => item.forceActiveFocus())
            }
            Behavior on opacity { NumberAnimation { duration: DesktopTokens.quickDuration } }
        }
    }

    DesktopSessionStarting {
        anchors.fill: parent
        visible: root.sessionStartingVisible
        focus: visible
        z: 80
        onCancelRequested: ShellStore.stopStreamingSession()
    }

    DesktopGameModal {
        anchors.fill: parent
        visible: !root.signInVisible && !root.sessionStartingVisible && root.route === "game-detail"
        z: 100
        onCloseRequested: AppController.goBack()
        onPlayRequested: ShellStore.launchSelectedGame(false)
    }
    DesktopCommandPalette {
        visible: root.commandOpen && !root.signInVisible && !root.sessionStartingVisible
        z: 120
        onCloseRequested: root.commandOpen = false
        onRouteRequested: route => AppController.navigate(route)
    }

    Component {
        id: homeComponent
        DesktopHomeScreen {
            active: root.route === "home"
            onRouteRequested: route => AppController.navigate(route)
            onGameRequested: game => { ShellStore.selectedGame = game; AppController.navigate("game-detail") }
        }
    }
    Component {
        id: libraryComponent
        DesktopLibraryScreen {
            searchQuery: root.searchText
            onDetailsRequested: game => { ShellStore.selectedGame = game; AppController.navigate("game-detail") }
            onPlayRequested: game => { ShellStore.selectedGame = game; ShellStore.launchSelectedGame(false) }
        }
    }
    Component {
        id: storeComponent
        DesktopStoreContent {
            searchText: root.searchText
            onGameSelected: game => { ShellStore.selectedGame = game; AppController.navigate("game-detail") }
            onRouteRequested: route => AppController.navigate(route)
            onClaimRequested: games => ShellStore.accessibilityMessage = qsTr("Claim request prepared for %1 games").arg(games.length)
            onSearchRequested: shell.forceActiveFocus()
            onMessageRequested: message => ShellStore.accessibilityMessage = message
        }
    }
    Component { id: friendsComponent; DesktopFriendsScreen {} }
    Component {
        id: settingsComponent
        DesktopSettingsScreen {
            selectedSection: root.settingsSection(root.route)
            onSelectedSectionChanged: root.settingsSubtitle = pageTitles[selectedSection]
            onRequestConsoleMode: enabled => {
                if (enabled) root.enterConsoleMode()
                else ShellStore.setSetting("launchInConsoleMode", false)
            }
            Component.onCompleted: root.settingsSubtitle = pageTitles[selectedSection]
        }
    }

    Connections {
        target: AppController
        function onRouteChanged() {
            root.commandOpen = false
            root.searchText = ""
        }
    }
    onSignInVisibleChanged: Qt.callLater(() => {
        if (root.signInVisible)
            desktopSignIn.forceActiveFocus()
        else if (pageLoader.item)
            pageLoader.item.forceActiveFocus()
    })
    Component.onCompleted: Qt.callLater(() => {
        if (root.signInVisible)
            desktopSignIn.forceActiveFocus()
    })
    Keys.onPressed: event => {
        if ((event.modifiers & Qt.ControlModifier) && event.key === Qt.Key_K) {
            root.commandOpen = !root.commandOpen
            event.accepted = true
        } else if (event.key === Qt.Key_Escape && root.commandOpen) {
            root.commandOpen = false
            event.accepted = true
        }
    }
}
