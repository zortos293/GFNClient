import QtQuick
import OpenNOW

Row {
    id: root
    property string keyText: "Enter"
    property string label: "Play"
    spacing: 6

    Rectangle {
        width: keyLabel.implicitWidth + 12
        height: 18
        radius: 5
        color: "#1AFFFFFF"
        border.width: 1
        border.color: DesktopTokens.seam
        Text {
            id: keyLabel
            anchors.centerIn: parent
            text: root.keyText
            color: DesktopTokens.textBody
            font.family: DesktopTokens.monoFont
            font.pixelSize: 9
            font.weight: Font.DemiBold
        }
    }
    Text {
        anchors.verticalCenter: parent.verticalCenter
        text: root.label
        color: DesktopTokens.textMuted
        font.family: DesktopTokens.bodyFont
        font.pixelSize: 11
        font.weight: Font.DemiBold
    }
}
