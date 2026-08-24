import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    required property var games
    signal openGame(var game)

    Column {
        anchors.fill: parent
        anchors.margins: Theme.pageMargin
        spacing: 22

        RowLayout {
            width: parent.width
            Column {
                spacing: 4
                Text {
                    text: "YOUR GAMES"
                    color: Theme.accent
                    font.pixelSize: 10
                    font.weight: Font.Bold
                    font.letterSpacing: 2.3
                }
                Text {
                    text: "Library"
                    color: Theme.ink
                    font.pixelSize: 34
                    font.weight: Font.DemiBold
                }
            }
            Item { Layout.fillWidth: true }
            Rectangle {
                Layout.preferredWidth: 230
                Layout.preferredHeight: 44
                radius: 22
                color: Theme.surfaceRaised
                border.color: Theme.divider
                Text {
                    anchors.centerIn: parent
                    text: "6 games  ·  Recently played"
                    color: Theme.inkSoft
                    font.pixelSize: 11
                }
            }
        }

        GridView {
            id: grid
            width: parent.width
            height: parent.height - y
            clip: true
            cellWidth: width / 3
            cellHeight: 246
            model: page.games
            keyNavigationWraps: true
            focus: page.visible
            Keys.onReturnPressed: page.openGame(page.games[currentIndex])

            delegate: Item {
                required property var modelData
                required property int index
                width: grid.cellWidth
                height: grid.cellHeight
                GameCard {
                    anchors.fill: parent
                    anchors.rightMargin: index % 3 === 2 ? 0 : 16
                    anchors.bottomMargin: 16
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
