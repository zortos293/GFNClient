import QtQuick
import QtQuick.Controls
import OpenNOW

AbstractButton {
    id: control
    signal valueChangedByUser(bool value)

    implicitWidth: DesktopTokens.px(52)
    implicitHeight: DesktopTokens.px(30)
    hoverEnabled: true
    onClicked: valueChangedByUser(!checked)

    background: Rectangle {
        radius: height / 2
        color: control.checked ? Qt.rgba(Theme.focus.r, Theme.focus.g, Theme.focus.b, control.down ? 0.65 : 0.45)
                               : DesktopTokens.raisedStrong
        border.width: control.activeFocus ? 2 : 0
        border.color: DesktopTokens.focus
        Behavior on color { ColorAnimation { duration: Theme.focusDuration } }
    }

    Rectangle {
        width: DesktopTokens.px(24)
        height: DesktopTokens.px(24)
        radius: width / 2
        y: DesktopTokens.px(3)
        x: control.checked ? control.width - width - DesktopTokens.px(3) : DesktopTokens.px(3)
        color: control.checked ? "#FFFFFF" : Qt.rgba(1, 1, 1, 0.58)
        Behavior on x {
            NumberAnimation {
                duration: AppController.reducedMotion ? 0 : 150
                easing.type: Easing.OutCubic
            }
        }
    }
}
