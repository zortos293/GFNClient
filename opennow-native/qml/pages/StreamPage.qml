import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    required property var game
    signal exit()
    property bool hudVisible: true
    property bool menuVisible: false

    Rectangle {
        anchors.fill: parent
        color: "#020302"
    }

    GameArtwork {
        anchors.fill: parent
        variant: page.game.variant
        opacity: streamEngine.phase === "streaming" ? 0.42 : 0.15
    }

    Rectangle {
        anchors.fill: parent
        color: "#52000000"
    }

    Column {
        anchors.centerIn: parent
        spacing: 16
        visible: streamEngine.phase !== "streaming"

        Item {
            anchors.horizontalCenter: parent.horizontalCenter
            width: 64
            height: 64
            Rectangle {
                anchors.centerIn: parent
                width: 54
                height: 54
                radius: 27
                color: "#14271b"
                border.width: 2
                border.color: Theme.accent
            }
            Rectangle {
                anchors.centerIn: parent
                width: 10
                height: 10
                radius: 5
                color: Theme.accent
                SequentialAnimation on opacity {
                    loops: Animation.Infinite
                    NumberAnimation { to: 0.25; duration: 700 }
                    NumberAnimation { to: 1; duration: 700 }
                }
            }
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: streamEngine.phase === "error" ? "Stream unavailable" : "Connecting to WebRTC session"
            color: Theme.ink
            font.pixelSize: 24
            font.weight: Font.DemiBold
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: streamEngine.statusText
            color: streamEngine.phase === "error" ? Theme.error : Theme.inkSoft
            font.pixelSize: 13
        }
        ActionButton {
            visible: streamEngine.phase === "error"
            anchors.horizontalCenter: parent.horizontalCenter
            text: "Return to library"
            onClicked: page.exit()
        }
    }

    Column {
        anchors.centerIn: parent
        spacing: 10
        visible: streamEngine.phase === "streaming"
        opacity: 0.8
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "WEBRTC VIDEO IS PLAYING IN THE NATIVE SURFACE"
            color: Theme.accent
            font.pixelSize: 11
            font.weight: Font.Bold
            font.letterSpacing: 2
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "The production renderer replaces this handoff with a shared GPU texture."
            color: Theme.inkSoft
            font.pixelSize: 12
        }
    }

    Rectangle {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        height: 86
        color: "#c6070908"
        visible: page.hudVisible

        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 28
            anchors.rightMargin: 28
            spacing: 16

            Column {
                spacing: 4
                Text { text: page.game.title; color: Theme.ink; font.pixelSize: 18; font.weight: Font.DemiBold }
                Text { text: streamEngine.statusText; color: Theme.accent; font.pixelSize: 10; font.weight: Font.Bold; font.letterSpacing: 0.9 }
            }
            Item { Layout.fillWidth: true }
            Repeater {
                model: [
                    { label: "CODEC", value: streamEngine.codec },
                    { label: "STREAM", value: streamEngine.resolution + "  " + streamEngine.fps + " FPS" },
                    { label: "BITRATE", value: (streamEngine.bitrateKbps / 1000).toFixed(1) + " Mbps" },
                    { label: "LATENCY", value: streamEngine.latencyMs + " ms" }
                ]
                Column {
                    required property var modelData
                    spacing: 4
                    Text { anchors.right: parent.right; text: modelData.label; color: Theme.inkMuted; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 1.1 }
                    Text { anchors.right: parent.right; text: modelData.value; color: Theme.ink; font.pixelSize: 11; font.weight: Font.DemiBold }
                }
            }
            ActionButton {
                id: exitButton
                text: "End session"
                focus: page.visible
                onClicked: page.exit()
            }
        }
    }

    Rectangle {
        anchors.left: parent.left
        anchors.bottom: parent.bottom
        anchors.leftMargin: 28
        anchors.bottomMargin: 24
        width: shortcuts.implicitWidth + 28
        height: 38
        radius: 19
        color: "#c40c0f0d"
        border.color: Theme.divider
        Row {
            id: shortcuts
            anchors.centerIn: parent
            spacing: 17
            Text { text: "ESC  End session"; color: Theme.inkSoft; font.pixelSize: 10 }
            Text { text: "F6  Stats"; color: Theme.inkSoft; font.pixelSize: 10 }
            Text { text: "F11  Fullscreen"; color: Theme.inkSoft; font.pixelSize: 10 }
        }
    }

    Shortcut { sequence: "F6"; onActivated: page.hudVisible = !page.hudVisible }
}
