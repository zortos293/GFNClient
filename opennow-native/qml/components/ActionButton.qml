import QtQuick
import QtQuick.Controls
import OpenNOW

Button {
    id: control
    property bool primary: false
    property string glyph: ""

    implicitHeight: 58
    implicitWidth: Math.max(154, label.implicitWidth + 64)
    hoverEnabled: true
    focusPolicy: Qt.StrongFocus

    contentItem: Item {
        Row {
            anchors.centerIn: parent
            spacing: 10

            Text {
                visible: control.glyph.length > 0
                text: control.glyph
                color: control.primary ? Theme.accentInk : Theme.ink
                font.family: Theme.bodyFont.family
                font.pixelSize: 15
                font.weight: Font.Bold
            }
            Text {
                id: label
                text: control.text
                color: control.primary ? Theme.accentInk : Theme.ink
                font.family: Theme.bodyFont.family
                font.pixelSize: 16
                font.weight: Font.Bold
            }
        }
    }

    background: Rectangle {
        radius: 16
        color: control.primary && control.activeFocus ? Theme.accent : "transparent"
        border.width: control.activeFocus ? 3 : 0
        border.color: Theme.accent
        scale: control.down ? 0.985 : 1.0

        Rectangle {
            anchors.fill: parent
            anchors.margins: control.activeFocus ? 5 : 0
            radius: control.activeFocus ? 11 : 13
            color: control.primary
                   ? (control.down ? Theme.accentStrong : Theme.accent)
                   : (control.down ? Theme.surfaceBright : Theme.surface)
            border.width: control.activeFocus ? 2 : 1
            border.color: control.activeFocus ? Theme.accentInk : Theme.divider
        }

        Behavior on color { ColorAnimation { duration: Theme.motionFast } }
        Behavior on scale { NumberAnimation { duration: Theme.motionFast } }
    }
}
