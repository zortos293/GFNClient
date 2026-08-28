import QtQuick
import QtQuick.Controls
import OpenNOW

Item {
    id: root
    property string title: ""
    property string description: ""
    property string value: ""
    property int rowHeight: 62
    property bool showDivider: true
    default property alias trailing: trailingSlot.data

    implicitHeight: rowHeight

    Column {
        anchors.left: parent.left
        anchors.right: trailingSlot.left
        anchors.rightMargin: 20
        anchors.verticalCenter: parent.verticalCenter
        spacing: 2
        Text {
            width: parent.width
            text: root.title
            color: Qt.rgba(1, 1, 1, 0.88)
            font.family: Theme.bodyFont
            font.pixelSize: 13
            font.weight: Font.Bold
            elide: Text.ElideRight
        }
        Text {
            width: parent.width
            visible: root.description !== ""
            text: root.description
            color: Qt.rgba(1, 1, 1, 0.50)
            font.family: Theme.bodyFont
            font.pixelSize: 11
            font.weight: Font.Medium
            elide: Text.ElideRight
        }
    }

    Row {
        id: trailingSlot
        anchors.right: parent.right
        anchors.verticalCenter: parent.verticalCenter
        spacing: 10
        Text {
            visible: root.value !== ""
            text: root.value
            color: Qt.rgba(1, 1, 1, 0.80)
            font.family: Theme.monoFont
            font.pixelSize: 10
            font.weight: Font.Bold
            anchors.verticalCenter: parent.verticalCenter
        }
    }

    Rectangle {
        visible: root.showDivider
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: 1
        color: Qt.rgba(1, 1, 1, 0.06)
    }
}
