import QtQuick
import QtQuick.Controls
import OpenNOW

FocusCard {
    id: card
    property string title: "Game"
    property string subtitle: "Ready"
    property string badge: ""
    property int variant: 0
    property real progress: 0

    implicitWidth: 260
    implicitHeight: 226

    contentItem: Item {
        anchors.fill: parent

        GameArtwork {
            id: art
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            height: 156
            variant: card.variant
            kicker: card.badge
        }

        Column {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.margins: 14
            spacing: 4

            Text {
                width: parent.width
                text: card.title
                color: Theme.ink
                elide: Text.ElideRight
                font.pixelSize: 16
                font.weight: Font.DemiBold
            }
            Text {
                width: parent.width
                text: card.subtitle
                color: Theme.inkMuted
                elide: Text.ElideRight
                font.pixelSize: 12
            }
        }

        Rectangle {
            visible: card.progress > 0
            anchors.left: parent.left
            anchors.bottom: parent.bottom
            width: parent.width * card.progress
            height: 3
            color: Theme.accent
        }
    }
}
