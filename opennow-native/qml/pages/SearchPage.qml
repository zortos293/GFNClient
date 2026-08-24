import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    required property var games
    signal openGame(var game)
    property string query: ""

    Column {
        anchors.fill: parent
        anchors.margins: Theme.pageMargin
        spacing: 24

        Column {
            spacing: 4
            Text {
                text: "FIND YOUR NEXT SESSION"
                color: Theme.accent
                font.pixelSize: 10
                font.weight: Font.Bold
                font.letterSpacing: 2.3
            }
            Text {
                text: "Search"
                color: Theme.ink
                font.pixelSize: 34
                font.weight: Font.DemiBold
            }
        }

        TextField {
            id: search
            width: Math.min(720, parent.width)
            height: 58
            focus: page.visible
            placeholderText: "Search your games"
            color: Theme.ink
            placeholderTextColor: Theme.inkMuted
            font.pixelSize: 16
            leftPadding: 52
            onTextChanged: page.query = text
            background: Rectangle {
                radius: Theme.radius
                color: Theme.surfaceRaised
                border.width: search.activeFocus ? 2 : 1
                border.color: search.activeFocus ? Theme.accent : Theme.divider
                Text {
                    anchors.left: parent.left
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.leftMargin: 19
                    text: "⌕"
                    color: Theme.inkMuted
                    font.pixelSize: 25
                }
            }
            KeyNavigation.down: results
        }

        Text {
            text: page.query.length === 0 ? "Recently played" : "Results"
            color: Theme.inkSoft
            font.pixelSize: 13
            font.weight: Font.DemiBold
        }

        GridView {
            id: results
            width: parent.width
            height: parent.height - y
            clip: true
            focus: false
            cellWidth: width / 3
            cellHeight: 246
            keyNavigationWraps: true
            model: page.games.filter(function(game) {
                return page.query.length === 0 || game.title.toLowerCase().indexOf(page.query.toLowerCase()) >= 0
            })

            delegate: Item {
                required property var modelData
                required property int index
                width: results.cellWidth
                height: results.cellHeight
                GameCard {
                    anchors.fill: parent
                    anchors.rightMargin: index % 3 === 2 ? 0 : 16
                    anchors.bottomMargin: 16
                    title: modelData.title
                    subtitle: modelData.store + " · " + modelData.genre
                    badge: modelData.badge
                    variant: modelData.variant
                    onClicked: page.openGame(modelData)
                }
            }
        }
    }
}
