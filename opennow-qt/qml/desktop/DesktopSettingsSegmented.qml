import QtQuick
import OpenNOW

Item {
    id: root
    property var options: []
    property int selectedIndex: 0
    property int optionWidth: 72
    signal selected(int index, var value)

    implicitWidth: Math.max(0, options.length * optionWidth + Math.max(0, options.length - 1) * 6)
    implicitHeight: 32

    Row {
        spacing: 6
        Repeater {
            model: root.options
            delegate: Rectangle {
                id: chip
                required property int index
                required property var modelData
                readonly property bool on: index === root.selectedIndex
                readonly property string label: typeof modelData === "object"
                    ? String(modelData.label || "")
                    : String(modelData)
                width: root.optionWidth
                height: 32
                radius: 9
                color: chip.on ? "#F2FFFFFF" : (chipHover.hovered ? "#1AFFFFFF" : "#0FFFFFFF")
                border.width: chip.on ? 0 : 1
                border.color: "#1FFFFFFF"

                Text {
                    anchors.centerIn: parent
                    width: parent.width - 8
                    text: chip.label
                    color: chip.on ? "#0B0F1A" : DesktopTokens.textBody
                    font.family: DesktopTokens.monoFont
                    font.pixelSize: 9
                    font.weight: Font.Bold
                    horizontalAlignment: Text.AlignHCenter
                    elide: Text.ElideRight
                }
                HoverHandler { id: chipHover; cursorShape: Qt.PointingHandCursor }
                TapHandler { onTapped: root.selected(chip.index, chip.modelData) }
            }
        }
    }
}
