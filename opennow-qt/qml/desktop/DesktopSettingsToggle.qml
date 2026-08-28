import QtQuick
import QtQuick.Controls
import OpenNOW

AbstractButton {
    id: control
    signal valueChangedByUser(bool value)

    implicitWidth: 40
    implicitHeight: 22
    hoverEnabled: true
    onClicked: valueChangedByUser(!checked)

    background: Rectangle {
        radius: height / 2
        color: control.checked ? Qt.rgba(0.46, 0.65, 1.0, control.down ? 0.50 : 0.42)
                               : Qt.rgba(1, 1, 1, control.down ? 0.17 : 0.12)
        border.width: control.activeFocus ? 2 : 0
        border.color: DesktopTokens.focus
        Behavior on color { ColorAnimation { duration: Theme.focusDuration } }
    }

    Rectangle {
        width: 16
        height: 16
        radius: 8
        y: 3
        x: control.checked ? 21 : 3
        color: control.checked ? "#FFFFFF" : Qt.rgba(1, 1, 1, 0.58)
        Behavior on x {
            NumberAnimation {
                duration: AppController.reducedMotion ? 0 : 150
                easing.type: Easing.OutCubic
            }
        }
    }
}
