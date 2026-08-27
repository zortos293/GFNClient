import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    required property var games
    signal openGame(var game)
    signal startGame(var game)
    signal chooseServer()
    readonly property var primaryGame: games.length > 0 ? games[0] : ({})
    property int settingsRevision: 0
    readonly property var activeRoute: {
        settingsRevision
        var regions = catalogEngine.regions || []
        var pings = catalogEngine.regionPings || ({})
        var mode = String(appState.preference("network.regionMode", "automatic"))
        var requestedUrl = String(appState.preference("network.serverUrl", ""))
        var requestedName = String(appState.preference("network.serverName", ""))
        if (mode === "manual") {
            for (var manualIndex = 0; manualIndex < regions.length; ++manualIndex) {
                if (String(regions[manualIndex].url) === requestedUrl || String(regions[manualIndex].name) === requestedName)
                    return regions[manualIndex]
            }
        }
        var best = null
        var bestPing = Number.MAX_VALUE
        for (var i = 0; i < regions.length; ++i) {
            var latency = Number(pings[String(regions[i].url)])
            if (isFinite(latency) && latency >= 0 && latency < bestPing) {
                bestPing = latency
                best = regions[i]
            }
        }
        return best
    }
    readonly property int activeRoutePing: activeRoute && catalogEngine.regionPings[String(activeRoute.url)] !== undefined
                                           ? Number(catalogEngine.regionPings[String(activeRoute.url)]) : -1

    function streamFormatText() {
        settingsRevision
        var resolution = String(appState.preference("streaming.resolution", "1440p (QHD)"))
        var aspect = String(appState.preference("streaming.aspect", "16:9 Standard"))
        var profile = StreamFormat.resolveProfile(
                    catalogEngine.subscription.entitledResolutions || [], resolution, aspect,
                    Number(appState.preference("streaming.frameRate", "120")))
        return profile.width + "×" + profile.height + " · " + profile.fps + " FPS"
    }

    Connections {
        target: appState
        function onPreferenceChanged(key, value) {
            if (key === "network.regionMode" || key === "network.serverUrl" || key === "network.serverName"
                    || key === "streaming.resolution" || key === "streaming.aspect"
                    || key === "streaming.frameRate")
                page.settingsRevision += 1
        }
        function onPreferencesReset() { page.settingsRevision += 1 }
    }

    Connections {
        target: catalogEngine
        function onSubscriptionChanged() { page.settingsRevision += 1 }
        function onServerInfoChanged() {
            if (page.visible && catalogEngine.regions.length > 0 && !catalogEngine.probingRegions)
                catalogEngine.probeRegions()
        }
    }

    onVisibleChanged: {
        if (visible && catalogEngine.regions.length > 0
                && Object.keys(catalogEngine.regionPings).length === 0 && !catalogEngine.probingRegions)
            catalogEngine.probeRegions()
    }

    function browseRows(delta) {
        if (delta > 0)
            libraryRow.forceActiveFocus()
        else
            playButton.forceActiveFocus()
    }

    Shortcut { sequence: "X"; enabled: page.visible && page.games.length > 0; onActivated: page.openGame(page.primaryGame) }

    Text {
        x: Theme.pageMargin
        y: 35
        text: new Date().toLocaleString(Qt.locale(), "ddd HH:mm").toUpperCase()
        color: Theme.accent
        font.family: Theme.monoFont.family
        font.pixelSize: 13
        font.weight: Font.Bold
        font.letterSpacing: 1.8
    }

    Text {
        x: Theme.pageMargin
        y: 60
        text: "Welcome, " + (authEngine.accountName || "player")
        color: Theme.ink
        font.family: Theme.displayFont.family
        font.pixelSize: 48
        font.weight: Font.DemiBold
    }

    Rectangle {
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.rightMargin: Theme.pageMargin
        anchors.topMargin: 46
        width: 500
        height: 58
        radius: 16
        color: "#0d120f"
        border.width: 1
        border.color: "#202a24"
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 18
            anchors.rightMargin: 18
            spacing: 12
            Rectangle { width: 8; height: 8; radius: 4; color: Theme.accent }
            Text { Layout.preferredWidth: 142; text: page.activeRoute ? String(page.activeRoute.name).toUpperCase() : (catalogEngine.probingRegions ? "MEASURING" : "REGION AUTO"); color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.Bold; elide: Text.ElideRight }
            Rectangle { width: 1; height: 24; color: Theme.divider }
            Text { Layout.fillWidth: true; text: page.streamFormatText(); color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 11; horizontalAlignment: Text.AlignHCenter; elide: Text.ElideRight }
            Rectangle { width: 1; height: 24; color: Theme.divider }
            Text { Layout.preferredWidth: 50; text: page.activeRoutePing >= 0 ? page.activeRoutePing + " ms" : "— ms"; color: page.activeRoutePing >= 0 ? Theme.accent : Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.DemiBold; horizontalAlignment: Text.AlignRight }
        }
        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: page.chooseServer() }
    }

    Rectangle {
        id: hero
        x: Theme.pageMargin
        y: 146
        width: parent.width - Theme.pageMargin * 2
        height: 430
        radius: 24
        color: Theme.surface
        border.width: 1
        border.color: "#202a24"
        clip: true
        visible: page.games.length > 0

        GameArtwork {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            height: 190
            radius: 24
            game: page.primaryGame
            source: page.primaryGame.heroImageUrl || page.primaryGame.imageUrl || ""
        }

        Column {
            anchors.left: parent.left
            anchors.bottom: parent.bottom
            anchors.leftMargin: 44
            anchors.bottomMargin: 43
            spacing: 12
            Text {
                text: "CONTINUE PLAYING"
                color: Theme.accent
                font.family: Theme.monoFont.family
                font.pixelSize: 12
                font.weight: Font.Bold
                font.letterSpacing: 2.4
            }
            Text {
                text: page.primaryGame.title || ""
                color: Theme.ink
                font.family: Theme.displayFont.family
                font.pixelSize: 64
                font.weight: Font.DemiBold
            }
            Text {
                text: page.primaryGame.lastPlayed || page.primaryGame.availableStores || "Ready to stream"
                color: Theme.inkSoft
                font.family: Theme.bodyFont.family
                font.pixelSize: 15
            }
        }

        Row {
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.rightMargin: 46
            anchors.bottomMargin: 35
            spacing: 14
            ActionButton {
                id: playButton
                width: 220
                height: 88
                text: "PLAY   Ⓐ"
                glyph: "▶"
                primary: true
                focus: page.visible
                onClicked: page.startGame(page.primaryGame)
                KeyNavigation.right: detailsButton
                KeyNavigation.down: libraryRow
            }
            ActionButton {
                id: detailsButton
                width: 172
                height: 78
                y: 5
                text: "Details   ⓧ"
                onClicked: page.openGame(page.primaryGame)
                KeyNavigation.left: playButton
                KeyNavigation.down: libraryRow
            }
        }
    }

    Column {
        visible: page.games.length === 0
        anchors.centerIn: hero
        spacing: 12
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: catalogEngine.loading ? "Loading your GeForce NOW library…" : "Your library is empty"
            color: Theme.ink
            font.pixelSize: 24
            font.weight: Font.DemiBold
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: catalogEngine.errorString || "Sync a supported store account in GeForce NOW, then refresh."
            color: Theme.inkMuted
            font.pixelSize: 14
        }
    }

    Text {
        x: Theme.pageMargin
        y: 606
        text: "Your library"
        color: Theme.ink
        font.family: Theme.displayFont.family
        font.pixelSize: 22
        font.weight: Font.Bold
    }

    Row {
        anchors.right: parent.right
        y: 610
        anchors.rightMargin: Theme.pageMargin
        spacing: 10
        KeyHint { keyText: "LB"; label: ""; scale: 0.8 }
        KeyHint { keyText: "RB"; label: "Browse rows"; scale: 0.8 }
    }

    GridView {
        id: libraryRow
        x: Theme.pageMargin
        y: 672
        width: parent.width - Theme.pageMargin * 2 + 16
        height: 250
        interactive: false
        model: page.games.slice(1, 6)
        cellWidth: 344
        cellHeight: 250
        focus: false
        keyNavigationWraps: true
        KeyNavigation.up: playButton
        Keys.onLeftPressed: currentIndex = (currentIndex + count - 1) % count
        Keys.onRightPressed: currentIndex = (currentIndex + 1) % count
        Keys.onReturnPressed: page.startGame(model[currentIndex])

        delegate: Item {
            required property var modelData
            required property int index
            width: libraryRow.cellWidth
            height: libraryRow.cellHeight
            GameCard {
                width: 320
                height: parent.height
                title: modelData.title
                subtitle: modelData.subtitle || ((modelData.availableStores || []).join(" · "))
                badge: modelData.badge || ""
                variant: modelData.variant === undefined ? index % 6 : Number(modelData.variant)
                imageSource: modelData.imageUrl || ""
                game: modelData
                selected: index === libraryRow.currentIndex
                onClicked: page.openGame(modelData)
            }
        }
    }

    Rectangle {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.leftMargin: Theme.pageMargin
        anchors.rightMargin: Theme.pageMargin
        height: 80
        color: Theme.canvas
        border.width: 0
        Rectangle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; height: 1; color: Theme.divider }
        Text {
            anchors.left: parent.left
            anchors.verticalCenter: parent.verticalCenter
            text: "library: " + page.games.length + (page.games.length === 1 ? " title" : " titles")
            color: "#455048"
            font.family: Theme.monoFont.family
            font.pixelSize: 11
            font.letterSpacing: 1.2
        }
        Row {
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            spacing: 22
            KeyHint { keyText: "A"; label: "Play" }
            KeyHint { keyText: "Y"; label: "Search" }
            KeyHint { keyText: "ENTER"; label: "Keyboard OK" }
        }
    }
}
