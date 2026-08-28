import QtQuick
import QtQuick.Controls
import OpenNOW

Button {
    id: root
    property bool primary: false
    property bool danger: false
    property string shortcutText: ""
    height: 40
    leftPadding: 14
    rightPadding: 14
    focusPolicy: Qt.StrongFocus
    font.family: DesktopTokens.bodyFont
    font.pixelSize: 12
    font.weight: Font.Bold
    background: Rectangle {
        radius: 9
        color: root.primary ? (root.down ? "#D9D9D9" : "#F2FFFFFF")
             : root.danger ? (root.hovered || root.activeFocus ? "#29FF8A80" : "#14FF8A80")
             : (root.hovered || root.activeFocus ? "#1FFFFFFF" : "#12FFFFFF")
        border.width: root.primary ? 0 : 1
        border.color: root.danger ? "#52FF8A80" : DesktopTokens.seam
        scale: root.down ? 0.985 : 1
        Behavior on color { ColorAnimation { duration: DesktopTokens.quickDuration } }
        Behavior on scale { NumberAnimation { duration: DesktopTokens.quickDuration; easing.type: Easing.OutCubic } }
        Rectangle {
            anchors.fill: parent
            anchors.margins: -2
            radius: parent.radius + 2
            color: "transparent"
            border.width: 2
            border.color: DesktopTokens.focus
            visible: root.activeFocus
        }
    }
    contentItem: Row {
        spacing: 8
        Text {
            anchors.verticalCenter: parent.verticalCenter
            text: root.text
            color: root.primary ? DesktopTokens.shell : root.danger ? "#FFB4AE" : DesktopTokens.textHigh
            font: root.font
        }
        Rectangle {
            visible: root.shortcutText !== ""
            anchors.verticalCenter: parent.verticalCenter
            width: shortcut.implicitWidth + 10
            height: 20
            radius: 5
            color: root.primary ? "#120B0F1A" : "#12FFFFFF"
            Text { id: shortcut; anchors.centerIn: parent; text: root.shortcutText; color: root.primary ? "#990B0F1A" : DesktopTokens.textMuted; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold }
        }
    }
}
