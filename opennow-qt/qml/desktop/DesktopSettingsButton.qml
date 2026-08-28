import QtQuick
import QtQuick.Controls
import OpenNOW

Button {
    id: control
    property bool primary: false
    property bool danger: false
    property bool compact: false
    property string suffix: ""

    implicitHeight: compact ? 30 : 34
    implicitWidth: Math.max(compact ? 68 : 84, label.implicitWidth + (suffix === "" ? 28 : 54))
    hoverEnabled: true

    background: Rectangle {
        radius: compact ? 9 : 10
        color: control.primary ? Theme.face
             : control.danger ? Qt.rgba(1, 0.32, 0.32, control.down ? 0.18 : 0.09)
             : Qt.rgba(1, 1, 1, control.down ? 0.16 : control.hovered ? 0.12 : 0.08)
        border.width: control.activeFocus ? 2 : 1
        border.color: control.activeFocus ? DesktopTokens.focus
                    : control.danger ? Qt.rgba(1, 0.48, 0.48, 0.28)
                    : Qt.rgba(1, 1, 1, 0.13)
        Behavior on color { ColorAnimation { duration: Theme.focusDuration } }
    }

    contentItem: Row {
        spacing: 9
        anchors.centerIn: parent
        Text {
            id: label
            text: control.text
            color: control.primary ? Theme.faceText : control.danger ? "#FFC2C2" : Theme.label
            font.family: Theme.bodyFont
            font.pixelSize: 12
            font.weight: Font.Bold
            anchors.verticalCenter: parent.verticalCenter
        }
        Rectangle {
            visible: control.suffix !== ""
            width: suffixText.implicitWidth + 10
            height: 18
            radius: 5
            color: control.primary ? Qt.rgba(0.05, 0.07, 0.11, 0.08) : Qt.rgba(1, 1, 1, 0.07)
            anchors.verticalCenter: parent.verticalCenter
            Text {
                id: suffixText
                anchors.centerIn: parent
                text: control.suffix
                color: control.primary ? Qt.rgba(0.05, 0.07, 0.11, 0.52) : Theme.textMuted
                font.family: Theme.monoFont
                font.pixelSize: 9
                font.weight: Font.Bold
            }
        }
    }
}
