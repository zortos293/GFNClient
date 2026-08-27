import QtQuick
import QtQuick.Layouts
import QtQuick.Shapes
import OpenNOW

Item {
    id: banner

    property bool shown: false
    property bool connected: true
    property string controllerName: "DualSense"
    property int batteryPercent: -1
    property string batteryStatus: "Unknown"
    property bool batteryCharging: false
    signal dismissed()

    readonly property bool playStation: /dualsense|playstation|ps5/i.test(controllerName)
    readonly property string displayName: connected
                                                  ? (playStation ? "DualSense connected"
                                                                 : controllerName + " connected")
                                                  : "Controller disconnected"
    readonly property string connectionLabel: batteryStatus === "Wired" ? "USB" : "WIRELESS"

    width: 422
    height: 76
    opacity: shown ? 1 : 0
    scale: shown ? 1 : 0.97
    visible: opacity > 0.01

    transform: Translate {
        y: banner.shown ? 0 : -12
        Behavior on y { NumberAnimation { duration: 210; easing.type: Easing.OutCubic } }
    }
    Behavior on opacity { NumberAnimation { duration: 180; easing.type: Easing.OutCubic } }
    Behavior on scale { NumberAnimation { duration: 210; easing.type: Easing.OutBack; easing.overshoot: 0.6 } }

    Rectangle {
        anchors.fill: parent
        radius: 16
        color: "#f20b100d"
        border.width: 1
        border.color: banner.connected ? "#2a3830" : "#4a3525"

        RowLayout {
            anchors.fill: parent
            anchors.margins: 12
            spacing: 14

            Rectangle {
                Layout.preferredWidth: 48
                Layout.preferredHeight: 48
                radius: 13
                color: banner.connected ? "#151d18" : "#211914"
                border.color: banner.connected ? "#26352c" : "#4a3525"

                Shape {
                    visible: banner.playStation
                    anchors.centerIn: parent
                    width: 24
                    height: 24

                    ShapePath {
                        fillColor: banner.connected ? Theme.accent : Theme.warning
                        strokeColor: "transparent"
                        PathSvg {
                            path: "M8.984 2.596v17.547l3.915 1.261V6.688c0-.69.304-1.151.794-.991.636.18.76.814.76 1.505v5.875c2.441 1.193 4.362-.002 4.362-3.152 0-3.237-1.126-4.675-4.438-5.827-1.307-.448-3.728-1.186-5.39-1.502zm4.656 16.241 6.296-2.275c.715-.258.826-.625.246-.818-.586-.192-1.637-.139-2.357.123l-4.205 1.5V14.98l.24-.085s1.201-.42 2.913-.615c1.696-.18 3.785.03 5.437.661 1.848.601 2.04 1.472 1.576 2.072-.465.6-1.622 1.036-1.622 1.036l-8.544 3.107V18.86zM1.807 18.6c-1.9-.545-2.214-1.668-1.352-2.32.801-.586 2.16-1.052 2.16-1.052l5.615-2.013v2.313L4.205 17c-.705.271-.825.632-.239.826.586.195 1.637.15 2.343-.12L8.247 17v2.074c-.12.03-.256.044-.39.073-1.939.331-3.996.196-6.038-.479z"
                        }
                    }
                }

                Canvas {
                    id: controllerGlyph
                    visible: !banner.playStation
                    anchors.centerIn: parent
                    width: 31
                    height: 23

                    onPaint: {
                        var ctx = getContext("2d")
                        ctx.reset()
                        ctx.lineWidth = 1.7
                        ctx.lineJoin = "round"
                        ctx.lineCap = "round"
                        ctx.strokeStyle = banner.connected ? Theme.accent : Theme.warning
                        ctx.fillStyle = banner.connected ? Theme.accent : Theme.warning
                        ctx.beginPath()
                        ctx.moveTo(7, 4)
                        ctx.lineTo(24, 4)
                        ctx.bezierCurveTo(28, 4, 30, 8, 29, 13)
                        ctx.lineTo(28, 18)
                        ctx.bezierCurveTo(27.5, 21, 24.5, 22, 22, 20)
                        ctx.lineTo(18.5, 17)
                        ctx.lineTo(12.5, 17)
                        ctx.lineTo(9, 20)
                        ctx.bezierCurveTo(6.5, 22, 3.5, 21, 3, 18)
                        ctx.lineTo(2, 13)
                        ctx.bezierCurveTo(1, 8, 3, 4, 7, 4)
                        ctx.stroke()
                        ctx.beginPath()
                        ctx.moveTo(7, 8)
                        ctx.lineTo(7, 13)
                        ctx.moveTo(4.5, 10.5)
                        ctx.lineTo(9.5, 10.5)
                        ctx.stroke()
                        ctx.beginPath()
                        ctx.arc(23, 9, 1.4, 0, Math.PI * 2)
                        ctx.arc(20, 12, 1.4, 0, Math.PI * 2)
                        ctx.fill()
                    }

                    Connections {
                        target: Theme
                        function onAccentChanged() { controllerGlyph.requestPaint() }
                    }
                }
            }

            Column {
                Layout.fillWidth: true
                Layout.alignment: Qt.AlignVCenter
                spacing: 3

                Text {
                    width: parent.width
                    text: banner.displayName
                    color: Theme.ink
                    elide: Text.ElideRight
                    font.family: Theme.displayFont.family
                    font.pixelSize: 16
                    font.weight: Font.DemiBold
                }
                Text {
                    width: parent.width
                    text: banner.connected
                          ? (banner.playStation ? "PS5" : "CONTROLLER") + "  ·  PLAYER 1  ·  " + banner.connectionLabel
                          : "KEYBOARD INPUT REMAINS ACTIVE"
                    color: Theme.inkMuted
                    elide: Text.ElideRight
                    font.family: Theme.monoFont.family
                    font.pixelSize: 10
                    font.weight: Font.Medium
                    font.letterSpacing: 0.5
                }
            }

            BatteryIndicator {
                visible: banner.connected
                Layout.preferredWidth: implicitWidth
                Layout.preferredHeight: implicitHeight
                percent: banner.batteryPercent
                status: banner.batteryStatus
                charging: banner.batteryCharging
            }
        }

        MouseArea {
            anchors.fill: parent
            onClicked: banner.dismissed()
        }
    }
}
