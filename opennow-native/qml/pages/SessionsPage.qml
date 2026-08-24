import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page

    Column {
        anchors.fill: parent
        anchors.margins: Theme.pageMargin
        spacing: 24

        RowLayout {
            width: parent.width
            Column {
                spacing: 5
                Text { text: "SESSION HISTORY"; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 11; font.weight: Font.Bold; font.letterSpacing: 2 }
                Text { text: "Sessions"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 36; font.weight: Font.DemiBold }
            }
            Item { Layout.fillWidth: true }
            ActionButton { text: "Export"; focus: page.visible }
        }

        RowLayout {
            width: parent.width
            spacing: 16
            Repeater {
                model: [
                    { value: "42.5 h", label: "TOTAL PLAY TIME" },
                    { value: "31", label: "SESSIONS" },
                    { value: "116", label: "AVERAGE FPS" },
                    { value: "12 ms", label: "MEDIAN LATENCY" }
                ]
                Rectangle {
                    required property var modelData
                    Layout.fillWidth: true
                    Layout.preferredHeight: 104
                    radius: 14
                    color: Theme.surfaceRaised
                    border.color: Theme.divider
                    Column {
                        anchors.left: parent.left
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.leftMargin: 22
                        spacing: 7
                        Text { text: modelData.value; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 26; font.weight: Font.Bold }
                        Text { text: modelData.label; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 9; font.letterSpacing: 1.3 }
                    }
                }
            }
        }

        Rectangle {
            width: parent.width
            height: 300
            radius: 14
            color: Theme.surfaceRaised
            border.color: Theme.divider
            Text { x: 22; y: 18; text: "Hours per day"; color: Theme.ink; font.family: Theme.bodyFont.family; font.pixelSize: 15; font.weight: Font.Bold }
            Row {
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.bottom: parent.bottom
                anchors.leftMargin: 28
                anchors.rightMargin: 28
                anchors.bottomMargin: 32
                height: 200
                spacing: 18
                Repeater {
                    model: [42, 66, 38, 54, 91, 72, 58, 76, 45, 83, 64, 112, 78, 96]
                    Rectangle {
                        required property int modelData
                        width: (parent.width - 13 * parent.spacing) / 14
                        height: modelData
                        anchors.bottom: parent.bottom
                        color: index === 13 ? Theme.accent : "#376c4a"
                    }
                }
            }
        }

        Rectangle {
            width: parent.width
            height: 300
            radius: 14
            color: Theme.surfaceRaised
            border.color: Theme.divider
            Column {
                anchors.fill: parent
                anchors.margins: 22
                spacing: 0
                Text { text: "Recent sessions"; color: Theme.ink; font.family: Theme.bodyFont.family; font.pixelSize: 15; font.weight: Font.Bold; height: 42 }
                Repeater {
                    model: ["Cyber Drift 2088", "Starfall Frontier", "Iron Harvest 2", "Nightfall Protocol"]
                    Rectangle {
                        required property string modelData
                        width: parent.width
                        height: 48
                        color: "transparent"
                        border.color: Theme.divider
                        RowLayout {
                            anchors.fill: parent
                            Text { text: modelData; color: Theme.ink; font.family: Theme.bodyFont.family; font.pixelSize: 13 }
                            Item { Layout.fillWidth: true }
                            Text { text: "EU-WEST  ·  9 ms  ·  120 FPS"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10 }
                        }
                    }
                }
            }
        }
    }
}
