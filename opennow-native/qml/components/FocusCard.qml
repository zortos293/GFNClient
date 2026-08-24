import QtQuick
import QtQuick.Controls
import OpenNOW

ItemDelegate {
    id: control
    hoverEnabled: true
    focusPolicy: Qt.StrongFocus
    padding: 0

    background: Rectangle {
        radius: Theme.radius
        color: control.hovered || control.activeFocus ? Theme.surfaceBright : Theme.surfaceRaised
        border.width: control.activeFocus ? 3 : 1
        border.color: control.activeFocus ? Theme.accent : Theme.divider
        scale: control.down ? 0.985 : (control.activeFocus ? 1.025 : 1.0)

        Behavior on color { ColorAnimation { duration: Theme.motionFast } }
        Behavior on scale { NumberAnimation { duration: Theme.motionFast; easing.type: Easing.OutCubic } }
    }
}
