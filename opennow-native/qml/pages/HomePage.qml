import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    required property var games
    signal openGame(var game)
    signal startGame(var game)

    Flickable {
        anchors.fill: parent
        contentHeight: content.height + 50
        clip: true
        boundsBehavior: Flickable.StopAtBounds

        Column {
            id: content
            x: Theme.pageMargin
            y: 30
            width: parent.width - Theme.pageMargin * 2
            spacing: 28

            RowLayout {
                width: parent.width
                ColumnLayout {
                    spacing: 3
                    Text {
                        text: "GOOD EVENING"
                        color: Theme.accent
                        font.pixelSize: 10
                        font.weight: Font.Bold
                        font.letterSpacing: 2.4
                    }
                    Text {
                        text: "Pick up where you left off"
                        color: Theme.ink
                        font.pixelSize: 30
                        font.weight: Font.DemiBold
                    }
                }
                Item { Layout.fillWidth: true }
                Rectangle {
                    Layout.preferredWidth: 178
                    Layout.preferredHeight: 42
                    radius: 21
                    color: Theme.surfaceRaised
                    border.color: Theme.divider
                    Row {
                        anchors.centerIn: parent
                        spacing: 9
                        Rectangle { width: 8; height: 8; radius: 4; color: Theme.accent }
                        Text {
                            text: "EU Northeast · 18 ms"
                            color: Theme.inkSoft
                            font.pixelSize: 11
                        }
                    }
                }
            }

            FocusCard {
                id: hero
                width: parent.width
                height: Math.min(390, page.height * 0.46)
                focus: page.visible
                onClicked: page.openGame(page.games[0])
                Keys.onReturnPressed: page.startGame(page.games[0])
                KeyNavigation.down: continueRow

                contentItem: Item {
                    GameArtwork {
                        anchors.fill: parent
                        variant: page.games[0].variant
                    }
                    Rectangle {
                        anchors.fill: parent
                        gradient: Gradient {
                            orientation: Gradient.Horizontal
                            GradientStop { position: 0; color: "#e9070a08" }
                            GradientStop { position: 0.52; color: "#78070a08" }
                            GradientStop { position: 1; color: "#08070a08" }
                        }
                    }
                    Column {
                        anchors.left: parent.left
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.leftMargin: 40
                        width: Math.min(510, parent.width * 0.48)
                        spacing: 12
                        Text {
                            text: "READY TO RESUME"
                            color: Theme.accent
                            font.pixelSize: 10
                            font.weight: Font.Bold
                            font.letterSpacing: 2.2
                        }
                        Text {
                            text: page.games[0].title
                            color: Theme.ink
                            font.pixelSize: Math.min(42, page.width * 0.035)
                            font.weight: Font.Bold
                        }
                        Text {
                            width: parent.width
                            text: "Your save is synchronized. RTX Ultimate is ready on a low-latency rig nearby."
                            color: Theme.inkSoft
                            font.pixelSize: 14
                            wrapMode: Text.WordWrap
                            lineHeight: 1.25
                        }
                        Row {
                            spacing: 10
                            topPadding: 7
                            ActionButton {
                                text: "Resume session"
                                glyph: "▶"
                                primary: true
                                onClicked: page.startGame(page.games[0])
                            }
                            ActionButton {
                                text: "View details"
                                onClicked: page.openGame(page.games[0])
                            }
                        }
                    }
                    Row {
                        anchors.right: parent.right
                        anchors.bottom: parent.bottom
                        anchors.margins: 22
                        spacing: 8
                        Repeater {
                            model: ["4K", "120 FPS", "HDR", "RTX"]
                            Rectangle {
                                required property string modelData
                                width: badgeText.implicitWidth + 18
                                height: 26
                                radius: 13
                                color: "#b8181c19"
                                border.color: "#3effffff"
                                Text {
                                    id: badgeText
                                    anchors.centerIn: parent
                                    text: modelData
                                    color: Theme.inkSoft
                                    font.pixelSize: 9
                                    font.weight: Font.Bold
                                }
                            }
                        }
                    }
                }
            }

            RowLayout {
                width: parent.width
                Text {
                    text: "Continue playing"
                    color: Theme.ink
                    font.pixelSize: 20
                    font.weight: Font.DemiBold
                }
                Item { Layout.fillWidth: true }
                Text {
                    text: "VIEW LIBRARY  →"
                    color: Theme.inkMuted
                    font.pixelSize: 10
                    font.weight: Font.Bold
                    font.letterSpacing: 1.3
                }
            }

            GridView {
                id: continueRow
                width: parent.width
                height: 232
                interactive: false
                cellWidth: width / 3
                cellHeight: 232
                model: page.games.slice(1, 4)
                focus: false
                keyNavigationWraps: true

                delegate: Item {
                    required property var modelData
                    required property int index
                    width: continueRow.cellWidth
                    height: continueRow.cellHeight
                    GameCard {
                        anchors.fill: parent
                        anchors.rightMargin: index === 2 ? 0 : 14
                        title: modelData.title
                        subtitle: modelData.subtitle
                        badge: modelData.badge
                        variant: modelData.variant
                        progress: modelData.progress
                        onClicked: page.openGame(modelData)
                    }
                }
            }
        }
    }
}
