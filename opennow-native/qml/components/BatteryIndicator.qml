import QtQuick
import OpenNOW

Item {
    id: battery

    property int percent: -1
    property string status: "Unknown"
    property bool charging: false
    property bool compact: false

    readonly property bool hasPercentage: percent >= 0
    readonly property bool wired: status === "Wired"
    readonly property color levelColor: hasPercentage && percent <= 20 ? Theme.warning : Theme.accent
    readonly property string displayText: hasPercentage ? percent + "%" : status.toUpperCase()

    implicitWidth: compact ? (hasPercentage ? 70 : 88) : (hasPercentage ? 82 : 102)
    implicitHeight: compact ? 30 : 36

    Rectangle {
        anchors.fill: parent
        radius: battery.compact ? 9 : 11
        color: "#111914"
        border.color: battery.hasPercentage ? "#2c3c32" : "#273029"

        Row {
            anchors.centerIn: parent
            spacing: battery.compact ? 6 : 8

            Item {
                width: battery.compact ? 25 : 28
                height: 14

                Rectangle {
                    visible: !battery.wired
                    x: 0
                    anchors.verticalCenter: parent.verticalCenter
                    width: parent.width - 3
                    height: 12
                    radius: 3
                    color: "transparent"
                    border.width: 1
                    border.color: battery.hasPercentage ? "#708078" : Theme.inkMuted

                    Rectangle {
                        anchors.left: parent.left
                        anchors.top: parent.top
                        anchors.bottom: parent.bottom
                        anchors.margins: 2
                        width: battery.hasPercentage
                               ? Math.max(2, (parent.width - 4) * battery.percent / 100)
                               : 0
                        radius: 1
                        color: battery.levelColor

                        Behavior on width { NumberAnimation { duration: 260; easing.type: Easing.OutCubic } }
                    }
                }

                Rectangle {
                    visible: !battery.wired
                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    width: 2
                    height: 6
                    radius: 1
                    color: battery.hasPercentage ? "#708078" : Theme.inkMuted
                }

                Canvas {
                    id: usbGlyph
                    visible: battery.wired
                    anchors.centerIn: parent
                    width: parent.width
                    height: 14

                    onPaint: {
                        var ctx = getContext("2d")
                        ctx.reset()
                        ctx.strokeStyle = Theme.inkSoft
                        ctx.lineWidth = 1.5
                        ctx.lineJoin = "round"
                        ctx.lineCap = "round"
                        ctx.strokeRect(6, 3, 13, 8)
                        ctx.beginPath()
                        ctx.moveTo(1, 7)
                        ctx.lineTo(6, 7)
                        ctx.moveTo(19, 5)
                        ctx.lineTo(24, 5)
                        ctx.moveTo(19, 9)
                        ctx.lineTo(24, 9)
                        ctx.stroke()
                    }
                }
            }

            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: (battery.charging ? "+ " : "") + battery.displayText
                color: battery.hasPercentage ? battery.levelColor : Theme.inkMuted
                font.family: Theme.monoFont.family
                font.pixelSize: battery.compact ? 10 : 11
                font.weight: Font.Bold
            }
        }
    }
}
