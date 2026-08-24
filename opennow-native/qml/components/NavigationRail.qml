import QtQuick
import QtQuick.Controls
import OpenNOW

Rectangle {
    id: rail
    property int currentIndex: 0
    signal navigate(int index)

    width: Theme.railWidth
    color: "#dd090b09"
    border.color: Theme.divider
    border.width: 1

    Image {
        id: logo
        anchors.top: parent.top
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.topMargin: 27
        source: "qrc:/OpenNOW/assets/opennow-mark.png"
        sourceSize.width: 60
        sourceSize.height: 34
        fillMode: Image.PreserveAspectFit
    }

    Column {
        id: nav
        anchors.centerIn: parent
        spacing: 14

        Repeater {
            model: [
                { icon: "⌂", label: "Home" },
                { icon: "▦", label: "Library" },
                { icon: "⌕", label: "Search" },
                { icon: "⚙", label: "Settings" }
            ]

            Button {
                id: navButton
                required property var modelData
                required property int index
                width: 76
                height: 66
                hoverEnabled: true
                focusPolicy: Qt.StrongFocus
                onClicked: rail.navigate(index)

                contentItem: Column {
                    anchors.centerIn: parent
                    spacing: 3
                    Text {
                        anchors.horizontalCenter: parent.horizontalCenter
                        text: navButton.modelData.icon
                        color: rail.currentIndex === navButton.index ? Theme.accent : Theme.inkSoft
                        font.pixelSize: 25
                        font.weight: Font.Light
                    }
                    Text {
                        anchors.horizontalCenter: parent.horizontalCenter
                        text: navButton.modelData.label
                        color: rail.currentIndex === navButton.index ? Theme.ink : Theme.inkMuted
                        font.pixelSize: 10
                        font.weight: Font.DemiBold
                    }
                }

                background: Rectangle {
                    radius: 15
                    color: rail.currentIndex === navButton.index
                           ? "#1b2b20"
                           : (navButton.hovered || navButton.activeFocus ? Theme.surfaceRaised : "transparent")
                    border.width: navButton.activeFocus ? 2 : 0
                    border.color: Theme.accent
                }
            }
        }
    }

    Column {
        anchors.bottom: parent.bottom
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottomMargin: 24
        spacing: 8
        Rectangle {
            anchors.horizontalCenter: parent.horizontalCenter
            width: 42
            height: 42
            radius: 21
            color: "#26392d"
            border.color: "#44624c"
            Text {
                anchors.centerIn: parent
                text: "Z"
                color: Theme.accent
                font.pixelSize: 16
                font.weight: Font.Bold
            }
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: controllerInput.connected ? "GAMEPAD" : "ONLINE"
            color: Theme.accent
            font.pixelSize: 8
            font.letterSpacing: 1.4
        }
    }
}
