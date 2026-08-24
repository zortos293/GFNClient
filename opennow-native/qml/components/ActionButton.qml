import QtQuick
import QtQuick.Controls
import OpenNOW

Button {
    id: control
    property bool primary: false
    property string glyph: ""

    implicitHeight: 50
    implicitWidth: Math.max(154, label.implicitWidth + 54)
    hoverEnabled: true
    focusPolicy: Qt.StrongFocus

    contentItem: Row {
        anchors.centerIn: parent
        spacing: 10

        Text {
            visible: control.glyph.length > 0
            text: control.glyph
            color: control.primary ? Theme.accentInk : Theme.ink
            font.pixelSize: 16
            font.weight: Font.Bold
        }
        Text {
            id: label
            text: control.text
            color: control.primary ? Theme.accentInk : Theme.ink
            font.pixelSize: 15
            font.weight: Font.DemiBold
        }
    }

    background: Rectangle {
        radius: Theme.radiusSmall
        color: control.primary
               ? (control.down ? Theme.accentStrong : Theme.accent)
               : (control.down ? Theme.surfaceBright : Theme.surfaceRaised)
        border.width: control.activeFocus ? 2 : 1
        border.color: control.activeFocus ? Theme.ink : (control.primary ? Theme.accent : Theme.divider)
        scale: control.down ? 0.98 : 1.0

        Behavior on color { ColorAnimation { duration: Theme.motionFast } }
        Behavior on scale { NumberAnimation { duration: Theme.motionFast } }
    }
}
