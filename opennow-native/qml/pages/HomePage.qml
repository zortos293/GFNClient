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
        width: 374
        height: 58
        radius: 16
        color: "#0d120f"
        border.width: 1
        border.color: "#202a24"
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 24
            anchors.rightMargin: 24
            Rectangle { width: 8; height: 8; radius: 4; color: Theme.accent }
            Text { text: "REGION AUTO"; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.Bold }
            Rectangle { width: 1; height: 24; color: Theme.divider }
            Text { text: catalogEngine.subscription.membershipName || "GeForce NOW"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 12 }
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

        Rectangle {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            height: 190
            color: Theme.surfaceBright
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
                Keys.onReturnPressed: page.startGame(page.primaryGame)
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
                subtitle: modelData.subtitle
                badge: modelData.badge
                variant: modelData.variant
                imageSource: modelData.imageUrl || ""
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
