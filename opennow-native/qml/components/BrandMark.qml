import QtQuick
import OpenNOW

Rectangle {
    id: mark
    implicitWidth: 40
    implicitHeight: 40
    radius: 9
    color: Theme.accent

    Text {
        anchors.centerIn: parent
        anchors.horizontalCenterOffset: 1
        text: "▶"
        color: Theme.accentInk
        font.family: Theme.bodyFont.family
        font.pixelSize: Math.round(mark.width * 0.35)
        font.weight: Font.Bold
    }
}
