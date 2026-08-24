import QtQuick
import OpenNOW

Rectangle {
    id: root
    property int variant: 0
    property string kicker: ""
    clip: true
    radius: 14
    color: Theme.surfaceBright

    Text {
        visible: root.kicker.length > 0
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.leftMargin: 16
        anchors.topMargin: 16
        text: root.kicker.toUpperCase()
        color: Theme.accent
        font.family: Theme.monoFont.family
        font.pixelSize: 10
        font.weight: Font.Bold
        font.letterSpacing: 1.5
    }
}
