import QtQuick
import QtQuick.Controls
import OpenNOW

Rectangle {
    id: toast
    property string message: ""
    property string detail: ""
    property string tone: "success"
    signal dismissed()

    width: Math.max(260, textColumn.implicitWidth + 78)
    height: detail.length > 0 ? 64 : 48
    radius: 10
    color: "#111814"
    border.color: tone === "error" ? "#5c2424" : (tone === "warning" ? "#5e4b1d" : "#28432f")
    visible: message.length > 0

    Rectangle {
        anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; anchors.leftMargin: 15
        width: 24; height: 24; radius: 12
        color: toast.tone === "error" ? "#321718" : (toast.tone === "warning" ? "#302815" : "#163321")
        Text { anchors.centerIn: parent; text: toast.tone === "error" ? "!" : (toast.tone === "warning" ? "△" : "✓"); color: toast.tone === "error" ? Theme.error : (toast.tone === "warning" ? Theme.warning : Theme.accent); font.pixelSize: 13; font.weight: Font.Bold }
    }
    Column {
        id: textColumn
        anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; anchors.leftMargin: 50
        spacing: 3
        Text { text: toast.message; color: Theme.ink; font.family: Theme.bodyFont.family; font.pixelSize: 12; font.weight: Font.Bold }
        Text { visible: toast.detail.length > 0; text: toast.detail; color: Theme.inkMuted; font.family: Theme.bodyFont.family; font.pixelSize: 9 }
    }
    MouseArea { anchors.fill: parent; onClicked: toast.dismissed() }
}
