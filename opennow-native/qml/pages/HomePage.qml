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

    function browseRows(delta) {
        if (delta > 0)
            libraryRow.forceActiveFocus()
        else
            playButton.forceActiveFocus()
    }

    Shortcut { sequence: "X"; enabled: page.visible; onActivated: page.openGame(page.games[0]) }

    Text {
        x: Theme.pageMargin
        y: 35
        text: "FRI 21:47"
        color: Theme.accent
        font.family: Theme.monoFont.family
        font.pixelSize: 13
        font.weight: Font.Bold
        font.letterSpacing: 1.8
    }

    Text {
        x: Theme.pageMargin
        y: 60
        text: "Good evening, " + appState.profileName
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
            Text { text: appState.serverRegion; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.Bold }
            Rectangle { width: 1; height: 24; color: Theme.divider }
            Text { text: "RTX 4080 rig"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 12 }
            Rectangle { width: 1; height: 24; color: Theme.divider }
            Text { text: appState.serverLatency + " ms"; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.Bold }
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
                text: page.games[0].title
                color: Theme.ink
                font.family: Theme.displayFont.family
                font.pixelSize: 64
                font.weight: Font.DemiBold
            }
            Text {
                text: "Last played 2 h ago   ·   Steam   ·   1440p   ·   120 FPS"
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
                onClicked: page.startGame(page.games[0])
                Keys.onReturnPressed: page.startGame(page.games[0])
                KeyNavigation.right: detailsButton
                KeyNavigation.down: libraryRow
            }
            ActionButton {
                id: detailsButton
                width: 172
                height: 78
                y: 5
                text: "Details   ⓧ"
                onClicked: page.openGame(page.games[0])
                KeyNavigation.left: playButton
                KeyNavigation.down: libraryRow
            }
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
            text: "library: 214 titles · synced 4 min ago"
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
