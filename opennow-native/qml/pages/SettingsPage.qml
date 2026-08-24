import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    property bool reducedMotion: false
    signal reducedMotionChangedByUser(bool value)
    property int qualityIndex: 1

    Flickable {
        anchors.fill: parent
        contentHeight: content.height + 60
        clip: true
        boundsBehavior: Flickable.StopAtBounds

        Column {
            id: content
            x: Theme.pageMargin
            y: 34
            width: parent.width - Theme.pageMargin * 2
            spacing: 24

            Column {
                spacing: 4
                Text {
                    text: "TUNE THE EXPERIENCE"
                    color: Theme.accent
                    font.pixelSize: 10
                    font.weight: Font.Bold
                    font.letterSpacing: 2.3
                }
                Text {
                    text: "Settings"
                    color: Theme.ink
                    font.pixelSize: 34
                    font.weight: Font.DemiBold
                }
            }

            RowLayout {
                width: parent.width
                spacing: 16
                Repeater {
                    model: [
                        { title: "Balanced", detail: "1080p · 60 FPS", icon: "◐" },
                        { title: "Performance", detail: "1080p · 120 FPS", icon: "↯" },
                        { title: "Cinematic", detail: "4K · 60 FPS · HDR", icon: "◇" }
                    ]
                    FocusCard {
                        required property var modelData
                        required property int index
                        Layout.fillWidth: true
                        Layout.preferredHeight: 120
                        focus: page.visible && index === 0
                        checked: page.qualityIndex === index
                        onClicked: page.qualityIndex = index
                        contentItem: Row {
                            anchors.fill: parent
                            anchors.margins: 19
                            spacing: 15
                            Rectangle {
                                width: 44
                                height: 44
                                radius: 14
                                color: page.qualityIndex === index ? "#263f2d" : Theme.surfaceBright
                                Text {
                                    anchors.centerIn: parent
                                    text: modelData.icon
                                    color: page.qualityIndex === index ? Theme.accent : Theme.inkSoft
                                    font.pixelSize: 22
                                }
                            }
                            Column {
                                anchors.verticalCenter: parent.verticalCenter
                                spacing: 5
                                Text { text: modelData.title; color: Theme.ink; font.pixelSize: 15; font.weight: Font.DemiBold }
                                Text { text: modelData.detail; color: Theme.inkMuted; font.pixelSize: 11 }
                            }
                        }
                    }
                }
            }

            Text {
                text: "STREAM"
                color: Theme.inkMuted
                font.pixelSize: 10
                font.weight: Font.Bold
                font.letterSpacing: 1.8
            }
            SettingRow {
                width: parent.width
                title: "Prefer hardware decoding"
                description: "Use D3D11, VideoToolbox, or VA-API and keep decoded frames on the GPU."
                checked: true
            }
            SettingRow {
                width: parent.width
                title: "Adaptive bitrate"
                description: "React to network changes while protecting the low-latency buffer target."
                checked: true
            }
            SettingRow {
                width: parent.width
                title: "Variable refresh rate"
                description: "Match presentation to the active display when the platform supports VRR."
                checked: true
            }

            Text {
                text: "INTERFACE"
                color: Theme.inkMuted
                font.pixelSize: 10
                font.weight: Font.Bold
                font.letterSpacing: 1.8
                topPadding: 8
            }
            SettingRow {
                width: parent.width
                title: "Reduced motion"
                description: "Replace spatial transitions with simple fades while preserving focus feedback."
                checked: page.reducedMotion
                onToggled: function(value) { page.reducedMotionChangedByUser(value) }
            }

            Rectangle {
                width: parent.width
                height: 68
                radius: Theme.radiusSmall
                color: "#0f1912"
                border.color: "#294532"
                RowLayout {
                    anchors.fill: parent
                    anchors.margins: 17
                    Rectangle { width: 9; height: 9; radius: 5; color: streamEngine.available ? Theme.accent : Theme.warning }
                    Column {
                        spacing: 3
                        Text { text: "Native WebRTC runtime"; color: Theme.ink; font.pixelSize: 13; font.weight: Font.DemiBold }
                        Text { text: streamEngine.statusText; color: Theme.inkMuted; font.pixelSize: 10 }
                    }
                    Item { Layout.fillWidth: true }
                    Text { text: "GSTREAMER 1.24"; color: Theme.accent; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1.2 }
                }
            }
        }
    }
}
