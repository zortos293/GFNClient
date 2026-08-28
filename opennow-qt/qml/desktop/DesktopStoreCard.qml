import QtQuick
import QtQuick.Controls
import OpenNOW

Item {
    id: root

    property var game: ({})
    property bool selected: false
    property string price: ""
    property string discount: ""
    property bool owned: false
    property bool freeToPlay: false
    property color fallbackColor: Theme.cartSteam

    signal activated(var game)
    signal pointed()

    width: 132
    height: 250
    scale: selected ? 1.025 : 1
    transformOrigin: Item.Center
    z: selected ? 20 : 0
    Accessible.role: Accessible.Button
    Accessible.name: String(game && game.title || qsTr("Game"))

    Behavior on scale {
        NumberAnimation { duration: Theme.focusDuration; easing.type: Easing.OutCubic }
    }

    RoundedArtwork {
        id: cover
        x: 0
        y: 0
        width: 132
        height: 198
        cornerRadius: 12
        scrimStart: 1
        artwork: root.game ? String(root.game.imageUrl || root.game.heroImageUrl || "") : ""
        fallbackColor: root.fallbackColor
    }

    Rectangle {
        x: -2
        y: -2
        width: 136
        height: 202
        radius: 14
        color: "transparent"
        border.width: root.selected ? 2 : 1
        border.color: root.selected ? DesktopTokens.focus : Qt.rgba(1, 1, 1, 0.16)

        Behavior on border.color {
            ColorAnimation { duration: Theme.focusDuration }
        }
    }

    Text {
        x: 0
        y: 206
        width: 132
        height: 15
        text: root.game ? String(root.game.title || qsTr("Untitled game")) : qsTr("Untitled game")
        color: Qt.rgba(1, 1, 1, 0.88)
        font.family: Theme.bodyFont
        font.pixelSize: 12
        font.weight: Font.Bold
        elide: Text.ElideRight
        verticalAlignment: Text.AlignVCenter
    }

    Row {
        x: 0
        y: 229
        width: 132
        height: 17
        spacing: 7

        Rectangle {
            anchors.verticalCenter: parent.verticalCenter
            visible: root.discount.length > 0 && !root.owned
            width: discountLabel.implicitWidth + 10
            height: 17
            radius: 5
            color: Qt.rgba(0.431, 0.906, 0.718, 0.18)

            Text {
                id: discountLabel
                anchors.centerIn: parent
                text: root.discount
                color: DesktopTokens.green
                font.family: Theme.monoFont
                font.pixelSize: 9
                font.weight: Font.Bold
            }
        }

        Text {
            anchors.verticalCenter: parent.verticalCenter
            visible: root.owned
            text: "✓"
            color: Qt.rgba(1, 1, 1, 0.40)
            font.family: Theme.bodyFont
            font.pixelSize: 11
            font.weight: Font.Bold
        }

        Text {
            anchors.verticalCenter: parent.verticalCenter
            width: Math.max(0, parent.width - x)
            text: root.owned ? qsTr("In library")
                             : root.freeToPlay ? qsTr("Free to play")
                                               : root.price
            color: root.freeToPlay ? DesktopTokens.green
                                   : root.owned ? Qt.rgba(1, 1, 1, 0.50)
                                                : "#FFFFFF"
            font.family: Theme.monoFont
            font.pixelSize: 11
            font.weight: root.owned ? Font.DemiBold : Font.Bold
            elide: Text.ElideRight
        }
    }

    MouseArea {
        id: pointer
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        onEntered: root.pointed()
        onClicked: root.activated(root.game)
    }
}
