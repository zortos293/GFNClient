import QtQuick
import QtQuick.Controls
import OpenNOW

Row {
    id: root
    property alias from: slider.from
    property alias to: slider.to
    property alias stepSize: slider.stepSize
    property alias value: slider.value
    property string suffix: "%"
    property int decimals: 0
    signal moved(real value)
    spacing: 14

    Slider {
        id: slider
        width: 200
        height: 28
        live: true
        onMoved: root.moved(value)
        background: Rectangle {
            x: slider.leftPadding
            y: slider.topPadding + slider.availableHeight / 2 - height / 2
            width: slider.availableWidth
            height: 6
            radius: 3
            color: Qt.rgba(1, 1, 1, 0.10)
            Rectangle {
                width: slider.visualPosition * parent.width
                height: parent.height
                radius: parent.radius
                color: slider.activeFocus ? DesktopTokens.focus : Qt.rgba(1, 1, 1, 0.76)
            }
        }
        handle: Rectangle {
            x: slider.leftPadding + slider.visualPosition * (slider.availableWidth - width)
            y: slider.topPadding + slider.availableHeight / 2 - height / 2
            width: 14
            height: 14
            radius: 7
            color: "#FFFFFF"
            border.width: slider.activeFocus ? 2 : 0
            border.color: DesktopTokens.focus
        }
    }
    Text {
        width: 58
        height: 28
        text: Number(slider.value).toFixed(root.decimals) + root.suffix
        color: Theme.label
        font.family: Theme.monoFont
        font.pixelSize: 10
        font.weight: Font.Bold
        horizontalAlignment: Text.AlignRight
        verticalAlignment: Text.AlignVCenter
    }
}
