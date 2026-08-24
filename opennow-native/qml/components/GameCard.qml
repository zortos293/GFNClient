import QtQuick
import QtQuick.Controls
import OpenNOW

FocusCard {
    id: card
    property string title: "Game"
    property string subtitle: "Ready"
    property string badge: ""
    property int variant: 0
    property url imageSource: ""
    property real progress: 0
    property bool selected: false

    implicitWidth: 260
    implicitHeight: 248

    background: Rectangle { color: "transparent" }

    contentItem: Item {
        anchors.fill: parent

        GameArtwork {
            id: art
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            height: 180
            variant: card.variant
            kicker: card.badge
            source: card.imageSource
        }

        Rectangle {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            height: 180
            radius: 14
            color: "transparent"
            border.width: card.selected || card.activeFocus ? 3 : 0
            border.color: Theme.accent
        }

        Column {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: art.bottom
            anchors.topMargin: 12
            spacing: 4

            Text {
                width: parent.width
                text: card.title
                color: Theme.ink
                elide: Text.ElideRight
                font.family: Theme.bodyFont.family
                font.pixelSize: 16
                font.weight: Font.Bold
            }
            Text {
                width: parent.width
                text: card.subtitle
                color: Theme.inkMuted
                elide: Text.ElideRight
                font.family: Theme.bodyFont.family
                font.pixelSize: 12
            }
        }

        Rectangle {
            visible: card.progress > 0
            anchors.left: parent.left
            anchors.bottom: parent.bottom
            width: parent.width * card.progress
            height: 2
            color: Theme.accent
        }
    }
}
