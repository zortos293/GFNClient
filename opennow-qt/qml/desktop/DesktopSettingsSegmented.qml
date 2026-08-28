import QtQuick
import QtQuick.Controls
import OpenNOW

Rectangle {
    id: root
    property var options: []
    property int selectedIndex: 0
    property int optionWidth: 72
    signal selected(int index, var value)

    implicitWidth: options.length * optionWidth + 4
    implicitHeight: 34
    radius: 10
    color: Qt.rgba(1, 1, 1, 0.035)
    border.width: 1
    border.color: Qt.rgba(1, 1, 1, 0.10)

    Row {
        anchors.fill: parent
        anchors.margins: 2
        Repeater {
            model: root.options
            delegate: Button {
                required property int index
                required property var modelData
                width: root.optionWidth
                height: 30
                hoverEnabled: true
                onClicked: root.selected(index, modelData)
                background: Rectangle {
                    radius: 8
                    color: index === root.selectedIndex ? Theme.face
                         : choice.hovered ? Qt.rgba(1, 1, 1, 0.05) : "transparent"
                    border.width: parent.activeFocus ? 2 : 0
                    border.color: DesktopTokens.focus
                    Behavior on color { ColorAnimation { duration: Theme.focusDuration } }
                }
                contentItem: Text {
                    text: typeof modelData === "object" ? modelData.label : String(modelData)
                    color: index === root.selectedIndex ? Theme.faceText : Theme.textMuted
                    font.family: Theme.monoFont
                    font.pixelSize: 9
                    font.weight: Font.Bold
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                    elide: Text.ElideRight
                }
            }
        }
    }
}
