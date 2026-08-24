import QtQuick
import OpenNOW

Row {
    id: root
    property string keyText: "A"
    property string label: "Select"
    spacing: 10

    Rectangle {
        anchors.verticalCenter: parent.verticalCenter
        width: Math.max(30, keyLabel.implicitWidth + 16)
        height: 30
        radius: 15
        color: "transparent"
        border.width: 1
        border.color: "#344039"
        Text {
            id: keyLabel
            anchors.centerIn: parent
            text: root.keyText
            color: Theme.ink
            font.family: Theme.monoFont.family
            font.pixelSize: 12
            font.weight: Font.Bold
        }
    }

    Text {
        anchors.verticalCenter: parent.verticalCenter
        text: root.label
        color: Theme.inkMuted
        font.family: Theme.bodyFont.family
        font.pixelSize: 14
    }
}
