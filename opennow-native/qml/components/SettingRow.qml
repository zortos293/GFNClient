import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    property string title: "Setting"
    property string description: ""
    property alias checked: toggle.checked
    signal toggled(bool checked)
    implicitHeight: 78

    Rectangle {
        anchors.fill: parent
        radius: Theme.radiusSmall
        color: root.activeFocus ? Theme.surfaceBright : Theme.surfaceRaised
        border.width: root.activeFocus ? 2 : 1
        border.color: root.activeFocus ? Theme.accent : Theme.divider
    }

    Column {
        anchors.left: parent.left
        anchors.verticalCenter: parent.verticalCenter
        anchors.leftMargin: 18
        anchors.right: toggle.left
        anchors.rightMargin: 16
        spacing: 5
        Text {
            text: root.title
            color: Theme.ink
            font.pixelSize: 15
            font.weight: Font.DemiBold
        }
        Text {
            width: parent.width
            text: root.description
            color: Theme.inkMuted
            font.pixelSize: 12
            wrapMode: Text.WordWrap
        }
    }

    AbstractButton {
        id: toggle
        anchors.right: parent.right
        anchors.verticalCenter: parent.verticalCenter
        anchors.rightMargin: 18
        width: 48
        height: 28
        focus: true
        checkable: true
        onToggled: root.toggled(checked)
        background: Rectangle {
            radius: height / 2
            color: toggle.checked ? Theme.accentStrong : Theme.surfaceBright
            border.width: toggle.activeFocus ? 2 : 1
            border.color: toggle.activeFocus ? Theme.ink : Theme.divider
            Rectangle {
                width: 20
                height: 20
                radius: 10
                y: 4
                x: toggle.checked ? parent.width - width - 4 : 4
                color: toggle.checked ? Theme.accentInk : Theme.inkSoft
                Behavior on x { NumberAnimation { duration: Theme.motionFast; easing.type: Easing.OutCubic } }
            }
        }
    }
}
