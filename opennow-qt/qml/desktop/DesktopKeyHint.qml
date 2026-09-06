import QtQuick
import OpenNOW

Row {
    id: root
    property string keyText: "Enter"
    property string label: "Play"
    property bool compact: false
    spacing: compact ? 6 : 8

    Rectangle {
        width: keyLabel.implicitWidth + (root.compact ? 12 : 16)
        height: root.compact ? 18 : 26
        radius: root.compact ? 5 : 7
        color: "#1AFFFFFF"
        border.width: 1
        border.color: DesktopTokens.seam
        Text {
            id: keyLabel
            anchors.centerIn: parent
            text: root.keyText
            color: DesktopTokens.textBody
            font.family: DesktopTokens.monoFont
            font.pixelSize: root.compact ? 9 : 12
            font.weight: Font.DemiBold
        }
    }
    Text {
        anchors.verticalCenter: parent.verticalCenter
        text: root.label
        color: DesktopTokens.textMuted
        font.family: DesktopTokens.bodyFont
        font.pixelSize: root.compact ? 11 : 14
        font.weight: Font.DemiBold
    }
}
