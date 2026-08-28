import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    focus: true
    property int currentIndex: Math.max(0, Math.min(32, ShellStore.focusIndex("store")))
    readonly property var games: ShellStore.catalogGames
    readonly property var selectedGame: gameAt(currentIndex)
    readonly property var rows: [
        { title: qsTr("Free to play"), count: 142 },
        { title: qsTr("RTX 5080 ready"), count: 240 },
        { title: qsTr("New this week"), count: 18 }
    ]

    function gameAt(index) {
        if (!games.length || index < 0)
            return null
        return games[index % games.length]
    }

    function moveSelection(delta) {
        if (!games.length)
            return
        if ((delta === -1 && currentIndex % 11 === 0)
                || (delta === 1 && currentIndex % 11 === 10))
            return
        currentIndex = Math.max(0, Math.min(32, currentIndex + delta))
        ShellStore.rememberFocus("store", currentIndex)
    }

    function openSelected() {
        const game = gameAt(currentIndex)
        if (game)
            ShellStore.openGame(game)
    }

    Keys.onPressed: event => {
        if (event.key === Qt.Key_Left) root.moveSelection(-1)
        else if (event.key === Qt.Key_Right) root.moveSelection(1)
        else if (event.key === Qt.Key_Up) root.moveSelection(-11)
        else if (event.key === Qt.Key_Down) root.moveSelection(11)
        else if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space)
            root.openSelected()
        else return
        event.accepted = true
    }

    ScreenBackground {
        artwork: root.selectedGame ? (root.selectedGame.heroImageUrl || root.selectedGame.imageUrl || "") : ""
        tint: "#18230F"
    }

    GlassPanel {
        x: 40
        y: 108
        width: 1840
        height: 848
        panelRadius: 44

        Item {
            anchors.fill: parent
            anchors.margins: 28

            Repeater {
                model: root.rows
                Item {
                    required property var modelData
                    required property int index
                    x: 0
                    y: index * 270
                    width: parent.width
                    height: 250

                    Text {
                        x: 0
                        y: 0
                        text: modelData.title
                        color: Theme.label
                        font.family: Theme.displayFont
                        font.pixelSize: 24
                        font.weight: Font.Black
                        font.letterSpacing: -0.48
                    }

                    Row {
                        anchors.right: parent.right
                        y: 4
                        spacing: 8
                        Text {
                            text: qsTr("See all %1").arg(modelData.count)
                            color: Theme.textMuted
                            font.family: Theme.bodyFont
                            font.pixelSize: 14
                            font.weight: Font.Bold
                        }
                        Text {
                            text: "›"
                            color: Theme.label
                            font.family: Theme.bodyFont
                            font.pixelSize: 22
                            font.weight: Font.Black
                        }
                    }

                    Repeater {
                        model: 11
                        ItemDelegate {
                            required property int index
                            readonly property int gameIndex: parent.index * 11 + index
                            readonly property var game: root.gameAt(gameIndex)
                            x: index * 164
                            y: 40
                            width: 140
                            height: 210
                            padding: 0
                            focusPolicy: Qt.NoFocus
                            Accessible.name: game ? game.title : qsTr("Game")
                            Accessible.role: Accessible.Button
                            onClicked: {
                                root.currentIndex = gameIndex
                                root.forceActiveFocus()
                                root.openSelected()
                            }
                            background: RoundedArtwork {
                                artwork: parent.game ? (parent.game.imageUrl || parent.game.heroImageUrl || "") : ""
                                fallbackColor: Theme.glassStrong
                                cornerRadius: 18
                                scrimStart: 1
                            }
                            contentItem: Item {}
                            FocusFrame {
                                focused: root.currentIndex === parent.gameIndex
                                frameRadius: 21
                            }
                            scale: root.currentIndex === gameIndex ? 1.045 : 1
                            z: root.currentIndex === gameIndex ? 20 : 0
                            Behavior on scale {
                                NumberAnimation {
                                    duration: Theme.focusDuration
                                    easing.type: Easing.OutCubic
                                }
                            }
                        }
                    }
                }
            }

            Column {
                anchors.centerIn: parent
                spacing: 12
                visible: root.games.length === 0
                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    text: ShellStore.catalogState === "error" ? qsTr("Catalog unavailable") : qsTr("Loading the live catalog…")
                    color: Theme.label
                    font.family: Theme.displayFont
                    font.pixelSize: 25
                    font.weight: Font.Black
                }
                GlassButton {
                    anchors.horizontalCenter: parent.horizontalCenter
                    visible: ShellStore.catalogState === "error"
                    text: qsTr("Try again")
                    glyph: "A"
                    primary: true
                    onClicked: ShellStore.refreshCatalog("")
                }
            }
        }
    }

    AppChrome {
        anchors.fill: parent
        title: qsTr("Store")
        currentRoute: "store"
        onRouteRequested: route => AppController.navigate(route)
    }
}
