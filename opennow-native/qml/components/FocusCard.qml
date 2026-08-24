import QtQuick
import QtQuick.Controls
import OpenNOW

ItemDelegate {
    id: control
    hoverEnabled: true
    focusPolicy: Qt.StrongFocus
    padding: 0

    background: Rectangle {
        radius: 15
        color: Theme.surfaceBright
        border.width: control.activeFocus ? 3 : 1
        border.color: control.activeFocus ? Theme.accent : Theme.divider
        scale: control.down ? 0.99 : 1.0

        Behavior on color { ColorAnimation { duration: Theme.motionFast } }
        Behavior on scale { NumberAnimation { duration: Theme.motionFast; easing.type: Easing.OutCubic } }
    }
}
