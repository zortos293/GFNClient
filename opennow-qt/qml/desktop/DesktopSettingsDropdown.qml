import QtQuick
import QtQuick.Controls
import OpenNOW

Item {
    id: root
    anchors.fill: parent
    visible: presented
    z: 10000
    property bool opened: false
    property bool presented: false
    property Item anchorItem: null
    property var items: []
    property var selectedValue: ""
    property string footer: ""
    signal chosen(var value)

    function showFor(anchor, choices, current, footerText) {
        closeTimer.stop()
        anchorItem = anchor
        items = choices || []
        selectedValue = current
        footer = footerText || ""
        const point = anchor.mapToItem(root, 0, anchor.height + 6)
        menu.x = Math.max(12, Math.min(root.width - menu.width - 12, point.x + anchor.width - menu.width))
        menu.y = Math.max(12, Math.min(root.height - menu.height - 12, point.y))
        presented = true
        opened = true
        Qt.callLater(choiceList.forceActiveFocus)
    }

    function dismiss() {
        opened = false
        closeTimer.restart()
    }

    Timer {
        id: closeTimer
        interval: AppController.reducedMotion ? 0 : 150
        onTriggered: root.presented = false
    }

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(0, 0, 0, root.opened ? 0.12 : 0)
        Behavior on color { ColorAnimation { duration: AppController.reducedMotion ? 0 : 150 } }
        MouseArea { anchors.fill: parent; onClicked: root.dismiss() }
    }

    Rectangle {
        id: menu
        width: 400
        height: Math.min(420, choiceColumn.implicitHeight + 20)
        radius: 14
        color: Qt.rgba(0.055, 0.063, 0.094, 0.98)
        border.width: 1
        border.color: Qt.rgba(1, 1, 1, 0.18)
        opacity: root.opened ? 1 : 0
        scale: root.opened ? 1 : 0.97
        transformOrigin: Item.TopRight
        Behavior on opacity { NumberAnimation { duration: AppController.reducedMotion ? 0 : 150; easing.type: Easing.OutCubic } }
        Behavior on scale { NumberAnimation { duration: AppController.reducedMotion ? 0 : 170; easing.type: Easing.OutBack; easing.overshoot: 0.6 } }

        Flickable {
            id: choiceList
            anchors.fill: parent
            anchors.margins: 10
            contentWidth: width
            contentHeight: choiceColumn.implicitHeight
            clip: true
            boundsBehavior: Flickable.StopAtBounds

            Column {
                id: choiceColumn
                width: parent.width
                Repeater {
                    model: root.items
                    delegate: Item {
                        required property int index
                        required property var modelData
                        width: choiceColumn.width
                        height: modelData.kind === "heading" ? 28 : 40
                        Text {
                            visible: modelData.kind === "heading"
                            anchors.left: parent.left
                            anchors.leftMargin: 10
                            anchors.verticalCenter: parent.verticalCenter
                            text: modelData.label
                            color: Qt.rgba(1, 1, 1, 0.42)
                            font.family: Theme.monoFont
                            font.pixelSize: 9
                            font.weight: Font.Bold
                            font.letterSpacing: 1
                        }
                        Button {
                            id: choice
                            visible: modelData.kind !== "heading"
                            anchors.fill: parent
                            hoverEnabled: true
                            onClicked: { root.chosen(modelData.value); root.dismiss() }
                            background: Rectangle {
                                radius: 8
                                color: choice.hovered ? Qt.rgba(1, 1, 1, 0.06) : "transparent"
                                border.width: modelData.value === root.selectedValue ? 1 : 0
                                border.color: DesktopTokens.focus
                            }
                            contentItem: Row {
                                spacing: 10
                                Text {
                                    width: 16
                                    text: modelData.value === root.selectedValue ? "✓" : ""
                                    color: DesktopTokens.focus
                                    font.pixelSize: 12
                                    anchors.verticalCenter: parent.verticalCenter
                                }
                                Text {
                                    width: 152
                                    text: modelData.label
                                    color: Theme.label
                                    font.family: Theme.bodyFont
                                    font.pixelSize: 12
                                    font.weight: Font.Bold
                                    anchors.verticalCenter: parent.verticalCenter
                                }
                                Text {
                                    width: 168
                                    text: modelData.detail || ""
                                    color: Theme.textMuted
                                    font.family: Theme.monoFont
                                    font.pixelSize: 9
                                    horizontalAlignment: Text.AlignRight
                                    anchors.verticalCenter: parent.verticalCenter
                                }
                            }
                        }
                    }
                }
                Rectangle { width: parent.width; height: root.footer === "" ? 0 : 1; color: Qt.rgba(1,1,1,0.08) }
                Text {
                    visible: root.footer !== ""
                    width: parent.width
                    height: visible ? 32 : 0
                    leftPadding: 10
                    text: root.footer
                    color: Qt.rgba(1,1,1,0.46)
                    font.family: Theme.monoFont
                    font.pixelSize: 9
                    font.weight: Font.Bold
                    verticalAlignment: Text.AlignVCenter
                }
            }
        }
    }

    Keys.onEscapePressed: event => { root.dismiss(); event.accepted = true }
}
