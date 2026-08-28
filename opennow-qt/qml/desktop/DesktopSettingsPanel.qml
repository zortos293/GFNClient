import QtQuick
import OpenNOW

Rectangle {
    id: panel
    property int padding: 18
    default property alias content: body.data

    implicitHeight: body.implicitHeight + padding * 2
    radius: 14
    color: Qt.rgba(0.043, 0.059, 0.102, 0.72)
    border.width: 1
    border.color: Qt.rgba(1, 1, 1, 0.08)

    Column {
        id: body
        x: panel.padding
        y: panel.padding
        width: panel.width - panel.padding * 2
    }
}
