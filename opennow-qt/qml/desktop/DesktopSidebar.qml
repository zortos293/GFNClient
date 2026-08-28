import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    property string currentRoute: "home"
    property bool collapsed: false
    property bool hoverExpanded: false
    readonly property bool compact: collapsed && !hoverExpanded
    signal routeRequested(string route)
    signal consoleModeRequested()
    signal collapseRequested(bool collapsed)

    width: compact ? 72 : (hoverExpanded ? 248 : 232)
    height: 900
    z: hoverExpanded ? 30 : 3

    Behavior on width {
        NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic }
    }

    Rectangle {
        anchors.fill: parent
        color: DesktopTokens.rail
        border.width: 0
        Rectangle { anchors.right: parent.right; width: 1; height: parent.height; color: DesktopTokens.seamSoft }
    }

    HoverHandler {
        id: railHover
        acceptedDevices: PointerDevice.Mouse
        onHoveredChanged: {
            if (root.collapsed)
                root.hoverExpanded = hovered
        }
    }

    Rectangle {
        x: root.compact ? 23 : 22
        y: 28
        width: root.compact ? 26 : 22
        height: root.compact ? 14 : 12
        radius: 7
        color: "#68E341"
        layer.enabled: true
        Text { anchors.centerIn: parent; text: "☁"; color: "#11320C"; font.pixelSize: 10; font.weight: Font.Black }
    }
    Text {
        x: 76; y: 27
        visible: !root.compact
        text: "OpenNOW"
        color: DesktopTokens.text
        font.family: DesktopTokens.displayFont
        font.pixelSize: 16
        font.weight: Font.Black
        font.letterSpacing: -0.3
    }
    ToolButton {
        id: collapseButton
        x: root.compact ? 16 : root.width - 41
        y: root.compact ? 56 : 20
        width: root.compact ? 40 : 28
        height: 28
        focusPolicy: Qt.StrongFocus
        background: Rectangle {
            radius: 9
            color: collapseButton.hovered || collapseButton.activeFocus ? "#17FFFFFF" : "#0FFFFFFF"
            border.width: 1
            border.color: DesktopTokens.seamSoft
        }
        contentItem: Text {
            text: root.collapsed ? "›" : "‹"
            color: DesktopTokens.textBody
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            font.pixelSize: 18
        }
        onClicked: {
            root.hoverExpanded = false
            root.collapseRequested(!root.collapsed)
        }
    }

    Column {
        id: navigation
        x: root.compact ? 14 : 12
        y: root.compact ? 108 : 70
        width: root.compact ? 44 : root.width - 25
        spacing: root.compact ? 3 : 3

        Repeater {
            model: [
                { route: "home", icon: "⌂", name: qsTr("Home") },
                { route: "library", icon: "▦", name: qsTr("Library") },
                { route: "store", icon: "▣", name: qsTr("Store") },
                { route: "friends", icon: "♧", name: qsTr("Friends") },
                { route: "settings", icon: "☷", name: qsTr("Settings") }
            ]
            delegate: ItemDelegate {
                id: navButton
                required property var modelData
                width: navigation.width
                height: root.compact ? 44 : 38
                padding: 0
                readonly property bool selected: root.currentRoute === modelData.route
                    || (modelData.route === "settings" && root.currentRoute.indexOf("settings") === 0)
                    || (modelData.route === "library" && root.currentRoute === "game-detail")
                Accessible.name: modelData.name
                background: Rectangle {
                    radius: 10
                    color: navButton.selected ? "#14FFFFFF"
                        : (navButton.hovered || navButton.activeFocus ? "#0CFFFFFF" : "transparent")
                }
                contentItem: Item {
                    Text {
                        x: root.compact ? 0 : 10
                        width: root.compact ? parent.width : 18
                        anchors.verticalCenter: parent.verticalCenter
                        text: navButton.modelData.icon
                        color: navButton.selected ? DesktopTokens.focus : "#8AFFFFFF"
                        horizontalAlignment: root.compact ? Text.AlignHCenter : Text.AlignLeft
                        font.family: DesktopTokens.bodyFont
                        font.pixelSize: 18
                        font.weight: Font.Bold
                    }
                    Text {
                        x: 39
                        anchors.verticalCenter: parent.verticalCenter
                        visible: !root.compact
                        text: navButton.modelData.name
                        color: navButton.selected ? DesktopTokens.text : DesktopTokens.textBody
                        font.family: DesktopTokens.bodyFont
                        font.pixelSize: 14
                        font.weight: navButton.selected ? Font.Bold : Font.DemiBold
                    }
                    Row {
                        anchors.right: parent.right
                        anchors.rightMargin: 10
                        anchors.verticalCenter: parent.verticalCenter
                        visible: navButton.modelData.route === "friends"
                        spacing: 8
                        Text { visible: !root.compact; text: "3"; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 10 }
                        Rectangle { width: 6; height: 6; radius: 3; color: "#1DB954" }
                    }
                    Rectangle {
                        anchors.right: parent.right
                        anchors.rightMargin: root.compact ? 0 : 9
                        anchors.verticalCenter: parent.verticalCenter
                        visible: navButton.selected
                        width: 4; height: 16; radius: 2; color: DesktopTokens.focus
                    }
                }
                onClicked: root.routeRequested(modelData.route)
            }
        }
    }

    Item {
        x: 12; y: 294
        width: root.width - 25; height: 180
        visible: !root.compact
        Rectangle { width: parent.width; height: 1; color: DesktopTokens.seamSoft }
        Text { x: 10; y: 27; text: qsTr("COLLECTIONS"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: 0.9 }
        Text { anchors.right: parent.right; anchors.rightMargin: 8; y: 20; text: "+"; color: DesktopTokens.textMuted; font.pixelSize: 16 }
        Column {
            y: 48; width: parent.width; spacing: 0
            Repeater {
                model: [
                    {icon:"☆", name:qsTr("Favourites"), count:"14"},
                    {icon:"◷", name:qsTr("Recently played"), count:"9"},
                    {icon:"∞", name:qsTr("Co-op with friends"), count:"21"},
                    {icon:"ϟ", name:qsTr("RTX ready"), count:"63"}
                ]
                delegate: Item {
                    required property var modelData
                    width: parent.width; height: 32
                    Text { x: 10; anchors.verticalCenter: parent.verticalCenter; text: modelData.icon; color: "#66FFFFFF"; font.pixelSize: 14 }
                    Text { x: 35; anchors.verticalCenter: parent.verticalCenter; text: modelData.name; color: "#A3FFFFFF"; font.family: DesktopTokens.bodyFont; font.pixelSize: 13; font.weight: Font.DemiBold }
                    Text { anchors.right: parent.right; anchors.rightMargin: 10; anchors.verticalCenter: parent.verticalCenter; text: modelData.count; color: "#4DFFFFFF"; font.family: DesktopTokens.monoFont; font.pixelSize: 10 }
                }
            }
        }
    }

    Column {
        x: root.compact ? 14 : 12
        y: root.compact ? 795 : 781
        width: root.compact ? 44 : root.width - 25
        spacing: 4
        Rectangle { width: parent.width; height: 1; color: DesktopTokens.seamSoft }
        ItemDelegate {
            id: consoleModeButton
            width: parent.width; height: root.compact ? 44 : 54; padding: 0
            background: Rectangle { radius: 11; color: consoleModeButton.hovered || consoleModeButton.activeFocus ? "#0CFFFFFF" : "transparent" }
            contentItem: Item {
                Text { x: root.compact ? 0 : 10; width: root.compact ? parent.width : 17; anchors.verticalCenter: parent.verticalCenter; text: "🎮"; horizontalAlignment: root.compact ? Text.AlignHCenter : Text.AlignLeft; color: "#8AFFFFFF"; font.pixelSize: 14 }
                Column { x: 38; anchors.verticalCenter: parent.verticalCenter; visible: !root.compact; spacing: 2
                    Text { text: qsTr("Console mode"); color: DesktopTokens.textHigh; font.family: DesktopTokens.bodyFont; font.pixelSize: 13; font.weight: Font.Bold }
                    Row { spacing: 5; Rectangle { width: 5; height: 5; radius: 3; color: DesktopTokens.amber } Text { text: qsTr("GAMEPAD READY"); color: DesktopTokens.textMuted; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: 0.4 } }
                }
                Rectangle { anchors.right: parent.right; anchors.rightMargin: 10; anchors.verticalCenter: parent.verticalCenter; visible: !root.compact; width: 32; height: 19; radius: 10; color: "#1FFFFFFF"; border.width: 1; border.color: "#1AFFFFFF"; Rectangle { x: 3; y: 3; width: 13; height: 13; radius: 7; color: "#CCFFFFFF" } }
                Rectangle { anchors.right: parent.right; anchors.top: parent.top; width: 7; height: 7; radius: 4; visible: root.compact; color: DesktopTokens.amber }
            }
            onClicked: root.consoleModeRequested()
        }
        ItemDelegate {
            id: profileButton
            width: parent.width; height: 44; padding: 0
            background: Rectangle { radius: 11; color: profileButton.hovered || profileButton.activeFocus ? "#0CFFFFFF" : "transparent" }
            contentItem: Item {
                Rectangle { x: root.compact ? 4 : 10; anchors.verticalCenter: parent.verticalCenter; width: root.compact ? 36 : 28; height: root.compact ? 36 : 28; radius: 20; color: "#17FFFFFF"; border.width: 1; border.color: "#29FFFFFF"; Text { anchors.centerIn: parent; text: ShellStore.signedIn && ShellStore.authSession.user ? String(ShellStore.authSession.user.displayName || "Z").charAt(0).toUpperCase() : "Z"; color: DesktopTokens.textHigh; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.Black } }
                Column { x: 48; anchors.verticalCenter: parent.verticalCenter; visible: !root.compact; spacing: 2
                    Text { text: ShellStore.signedIn && ShellStore.authSession.user ? ShellStore.authSession.user.displayName : qsTr("Guest"); color: DesktopTokens.textHigh; font.family: DesktopTokens.bodyFont; font.pixelSize: 13; font.weight: Font.Bold }
                    Text { text: ShellStore.signedIn ? String((ShellStore.authSession.user.membershipTier || "MEMBER") + " · 82%").toUpperCase() : qsTr("NOT SIGNED IN"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: 0.4 }
                }
                Text { anchors.right: parent.right; anchors.rightMargin: 11; anchors.verticalCenter: parent.verticalCenter; visible: !root.compact; text: "⌃"; color: DesktopTokens.textFaint; font.pixelSize: 12 }
            }
            onClicked: root.routeRequested("settings-account")
        }
    }
}
