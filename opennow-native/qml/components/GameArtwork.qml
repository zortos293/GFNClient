import QtQuick
import OpenNOW

Rectangle {
    id: root
    property int variant: 0
    property string kicker: ""
    property url source: ""
    clip: true
    radius: 14
    color: "#171d1a"

    Image {
        anchors.fill: parent
        source: root.source
        visible: root.source.toString().length > 0
        fillMode: Image.PreserveAspectCrop
        asynchronous: true
        cache: true
    }

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
