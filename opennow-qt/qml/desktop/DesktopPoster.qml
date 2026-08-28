import QtQuick
import QtQuick.Controls
import OpenNOW

ItemDelegate {
    id: root
    property var game: null
    property bool showTitle: false
    property bool showPlay: false
    property bool selected: activeFocus
    signal contextRequested(real sceneX, real sceneY)
    // Keep focus geometry inside the delegate so GridView clipping never cuts
    // off the top/left ring while the tile scales up.
    width: 144
    height: showTitle ? 248 : 210
    padding: 0
    hoverEnabled: true
    focusPolicy: Qt.StrongFocus
    scale: down ? 0.985 : (activeFocus ? 1.025 : 1)
    z: activeFocus || hovered ? 5 : 1
    Behavior on scale { NumberAnimation { duration: DesktopTokens.quickDuration; easing.type: Easing.OutCubic } }
    background: Item {}
    contentItem: Item {
        RoundedArtwork {
            id: art
            x: 6; y: 6; width: 132; height: 198
            artwork: root.game ? String(root.game.imageUrl || root.game.heroImageUrl || "") : ""
            cornerRadius: 12
            scrimStart: root.hovered || root.showPlay ? 0.48 : 1
            fallbackColor: "#1A2232"
        }
        Rectangle {
            x: 6; y: 6; width: 132; height: 198; radius: 12
            color: "transparent"
            border.width: root.activeFocus ? 2 : 1
            border.color: root.activeFocus ? "#FFFFFF" : "#29FFFFFF"
            layer.enabled: true
        }
        Rectangle {
            x: 3; y: 3; width: 138; height: 204; radius: 15
            visible: root.activeFocus
            color: "transparent"; border.width: 2; border.color: DesktopTokens.focus
        }
        Column {
            x: 15; y: 153; width: 114; spacing: 7
            visible: (root.hovered || root.activeFocus) && !root.showTitle
            Text { width: parent.width; text: root.game ? String(root.game.title || qsTr("Game")) : qsTr("Game"); color: DesktopTokens.text; elide: Text.ElideRight; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.Bold }
            Rectangle {
                width: parent.width; height: 26; radius: 8; color: "#F2FFFFFF"
                Text { anchors.centerIn: parent; text: "▶  " + qsTr("Play"); color: DesktopTokens.shell; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; font.weight: Font.Bold }
            }
        }
        Column {
            x: 6; y: 210; width: 132; spacing: 5
            visible: root.showTitle
            Text { width: parent.width; text: root.game ? String(root.game.title || qsTr("Game")) : qsTr("Game"); color: DesktopTokens.textHigh; elide: Text.ElideRight; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.Bold }
            Text { width: parent.width; text: root.game && root.game.price ? String(root.game.price) : qsTr("Available"); color: DesktopTokens.textMuted; elide: Text.ElideRight; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.DemiBold }
        }
    }
    TapHandler {
        acceptedButtons: Qt.RightButton
        onTapped: point => {
            const scene = root.mapToItem(null, point.position.x, point.position.y)
            root.contextRequested(scene.x, scene.y)
        }
    }
}
