import QtQuick
import QtQuick.Controls
import QtQuick.Effects
import OpenNOW

Item {
    id: root

    property var games: []
    property int selectedAction: -1

    signal claimRequested()
    signal includedRequested()
    signal actionPointed(int index)

    width: 1160
    height: 224

    readonly property int artCardCount: Math.max(0, Math.min(root.games.length, 4, Math.floor((root.width - 420) / 130)))
    readonly property int artRowWidth: root.artCardCount * 130 - 12

    Rectangle {
        id: heroMask
        anchors.fill: parent
        radius: 16
        color: "white"
        visible: false
        layer.enabled: true
    }

    Item {
        anchors.fill: parent
        layer.enabled: true
        layer.smooth: true
        layer.effect: MultiEffect {
            maskEnabled: true
            maskSource: heroMask
            maskThresholdMin: 0.25
            maskSpreadAtMin: 0.2
        }

        Rectangle {
            anchors.fill: parent
            color: Qt.rgba(0.043, 0.059, 0.102, 0.72)
        }

        Rectangle {
            anchors.centerIn: parent
            width: parent.height
            height: parent.width
            rotation: -90
            opacity: 0.42
            gradient: Gradient {
                GradientStop { position: 0; color: "#60368D" }
                GradientStop { position: 0.48; color: "#173B5B" }
                GradientStop { position: 1; color: "transparent" }
            }
        }
    }

    Rectangle {
        anchors.fill: parent
        radius: 16
        color: "transparent"
        border.width: 1
        border.color: Qt.rgba(1, 1, 1, 0.12)
    }

    Column {
        x: 22
        y: 22
        width: Math.min(530, Math.max(300, root.width - root.artRowWidth - 60))
        spacing: 12

        Row {
            height: 22
            spacing: 8

            Rectangle {
                width: badgeText.implicitWidth + 18
                height: 22
                radius: 6
                color: Qt.rgba(0.431, 0.906, 0.718, 0.16)
                border.width: 1
                border.color: Qt.rgba(0.431, 0.906, 0.718, 0.36)
                Text {
                    id: badgeText
                    anchors.centerIn: parent
                    text: qsTr("GEFORCE NOW CATALOG")
                    color: DesktopTokens.green
                    font.family: Theme.monoFont
                    font.pixelSize: 9
                    font.weight: Font.Bold
                    font.letterSpacing: 0.72
                }
            }

            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: root.games.length ? qsTr("%1 FEATURED GAMES").arg(root.games.length) : qsTr("LIVE CATALOG")
                color: Qt.rgba(1, 1, 1, 0.60)
                font.family: Theme.monoFont
                font.pixelSize: 10
                font.weight: Font.DemiBold
                font.letterSpacing: 0.6
            }
        }

        Text {
            width: parent.width
            text: qsTr("Featured on GeForce NOW")
            color: "#FFFFFF"
            font.family: Theme.displayFont
            font.pixelSize: 30
            font.weight: Font.Black
            font.letterSpacing: -0.9
        }

        Text {
            width: parent.width
            text: qsTr("Browse games returned by NVIDIA for your region. Link supported stores to see which titles are ready in your account library.")
            color: Qt.rgba(1, 1, 1, 0.72)
            font.family: Theme.bodyFont
            font.pixelSize: 13
            font.weight: Font.Medium
            lineHeightMode: Text.FixedHeight
            lineHeight: 19
            wrapMode: Text.WordWrap
        }
    }

    Row {
        x: 22
        y: 158
        height: 36
        spacing: 9

        Button {
            id: claimButton
            width: 121
            height: 36
            padding: 0
            focusPolicy: Qt.NoFocus
            hoverEnabled: true
            Accessible.name: text
            text: qsTr("View featured")
            onHoveredChanged: if (hovered) root.actionPointed(0)
            onClicked: root.claimRequested()
            background: Rectangle {
                radius: 10
                color: claimButton.down ? Qt.rgba(1, 1, 1, 0.82) : Qt.rgba(1, 1, 1, 0.95)
                border.width: root.selectedAction === 0 ? 2 : 0
                border.color: DesktopTokens.focus
            }
            contentItem: Text {
                text: claimButton.text
                color: "#0B0F1A"
                font.family: Theme.bodyFont
                font.pixelSize: 13
                font.weight: Font.ExtraBold
                horizontalAlignment: Text.AlignHCenter
                verticalAlignment: Text.AlignVCenter
            }
        }

        Button {
            id: includedButton
            width: 153
            height: 36
            padding: 0
            focusPolicy: Qt.NoFocus
            hoverEnabled: true
            Accessible.name: text
            text: qsTr("Browse catalog")
            onHoveredChanged: if (hovered) root.actionPointed(1)
            onClicked: root.includedRequested()
            background: Rectangle {
                radius: 10
                color: includedButton.down ? Qt.rgba(1, 1, 1, 0.16) : Qt.rgba(1, 1, 1, 0.10)
                border.width: root.selectedAction === 1 ? 2 : 1
                border.color: root.selectedAction === 1 ? DesktopTokens.focus : Qt.rgba(1, 1, 1, 0.18)
            }
            contentItem: Text {
                text: includedButton.text
                color: Qt.rgba(1, 1, 1, 0.88)
                font.family: Theme.bodyFont
                font.pixelSize: 13
                font.weight: Font.Bold
                horizontalAlignment: Text.AlignHCenter
                verticalAlignment: Text.AlignVCenter
            }
        }
    }

    Row {
        anchors.right: parent.right
        anchors.rightMargin: 22
        y: 23
        spacing: 12

        Repeater {
            model: root.artCardCount

            Item {
                required property int index
                readonly property var itemGame: root.games[index]
                width: 118
                height: 177

                RoundedArtwork {
                    anchors.fill: parent
                    artwork: DesktopTokens.artworkUrl(parent.itemGame, false)
                    fallbackColor: index % 2 ? Theme.cartSteam : Theme.glassStrong
                    cornerRadius: 11
                    scrimStart: 1
                }
                Rectangle {
                    anchors.fill: parent
                    radius: 11
                    color: "transparent"
                    border.width: 1
                    border.color: Qt.rgba(1, 1, 1, 0.24)
                }
            }
        }
    }
}
