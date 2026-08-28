import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    property string query: ""
    signal closeRequested()
    signal routeRequested(string route)
    anchors.fill: parent

    readonly property var actions: [
        { icon: "desktop-nav-home.svg", name: qsTr("Go to Home"), detail: qsTr("Open your desktop"), route: "home", key: "1" },
        { icon: "desktop-nav-library.svg", name: qsTr("Open Library"), detail: qsTr("Browse all games"), route: "library", key: "2" },
        { icon: "desktop-nav-store.svg", name: qsTr("Open Store"), detail: qsTr("Discover games"), route: "store", key: "3" },
        { icon: "desktop-nav-friends.svg", name: qsTr("Friends and party"), detail: qsTr("See who is online"), route: "friends", key: "4" },
        { icon: "desktop-nav-settings.svg", name: qsTr("Settings"), detail: qsTr("Configure OpenNOW"), route: "settings", key: "," },
        { icon: "desktop-play-stroke.svg", name: qsTr("Start last game"), detail: qsTr("Resume your previous session"), route: "game-detail", key: "Enter" }
    ]
    readonly property var visibleActions: actions.filter(item =>
        root.query === "" || String(item.name + " " + item.detail).toLocaleLowerCase().indexOf(root.query.toLocaleLowerCase()) >= 0)

    Rectangle { anchors.fill: parent; color: "#A8000000"; TapHandler { onTapped: root.closeRequested() } }
    Rectangle {
        x: (parent.width - 640) / 2
        y: 132
        width: 640
        height: 476
        radius: 18
        color: "#FA0A0E15"
        border.width: 1
        border.color: "#2EFFFFFF"
        TapHandler { }

        Item {
            x: 0; y: 0; width: parent.width; height: 58
            DesktopGlyph { x: 18; anchors.verticalCenter: parent.verticalCenter; width: 17; height: 17; icon: "desktop-search.svg" }
            TextField {
                id: field
                x: 47; y: 12; width: 520; height: 34
                leftPadding: 0; rightPadding: 0
                placeholderText: qsTr("Search commands, games, and settings…")
                color: DesktopTokens.text
                placeholderTextColor: DesktopTokens.textMuted
                font.family: DesktopTokens.bodyFont
                font.pixelSize: 16
                font.weight: Font.DemiBold
                background: Item {}
                onTextChanged: root.query = text
                Component.onCompleted: forceActiveFocus()
            }
            Rectangle {
                anchors.right: parent.right; anchors.rightMargin: 18; anchors.verticalCenter: parent.verticalCenter
                width: 52; height: 22; radius: 7; color: "#14FFFFFF"
                Text { anchors.centerIn: parent; text: qsTr("Ctrl K"); color: DesktopTokens.textMuted; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.DemiBold }
            }
            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: "#14FFFFFF" }
        }

        Column {
            x: 10; y: 68; width: 620; spacing: 2
            Text {
                height: 26; leftPadding: 8
                text: qsTr("ACTIONS")
                color: "#66FFFFFF"
                font.family: DesktopTokens.monoFont
                font.pixelSize: 9
                font.weight: Font.DemiBold
                font.letterSpacing: 1
                verticalAlignment: Text.AlignVCenter
            }
            Repeater {
                model: root.visibleActions
                delegate: ItemDelegate {
                    id: command
                    required property var modelData
                    width: parent.width
                    height: 40
                    padding: 0
                    background: Rectangle {
                        radius: 11
                        color: command.hovered || command.activeFocus ? "#17FFFFFF" : "transparent"
                        border.width: command.activeFocus ? 1 : 0
                        border.color: "#24FFFFFF"
                    }
                    contentItem: Item {
                        Rectangle {
                            x: 8; anchors.verticalCenter: parent.verticalCenter
                            width: 20; height: 20; radius: 7; color: "#0FFFFFFF"
                            DesktopGlyph { anchors.centerIn: parent; width: 13; height: 13; icon: command.modelData.icon }
                        }
                        Text {
                            x: 40; anchors.verticalCenter: parent.verticalCenter
                            text: command.modelData.name
                            color: DesktopTokens.textHigh
                            font.family: DesktopTokens.bodyFont
                            font.pixelSize: 13
                            font.weight: Font.DemiBold
                        }
                        Rectangle {
                            visible: command.modelData.key !== ""
                            anchors.right: parent.right; anchors.rightMargin: 10; anchors.verticalCenter: parent.verticalCenter
                            width: keyText.implicitWidth + 14; height: 20; radius: 6; color: "#14FFFFFF"
                            Text {
                                id: keyText
                                anchors.centerIn: parent
                                text: command.modelData.key
                                color: DesktopTokens.textMuted
                                font.family: DesktopTokens.monoFont
                                font.pixelSize: 9
                            }
                        }
                    }
                    onClicked: { root.routeRequested(modelData.route); root.closeRequested() }
                }
            }
        }

        Rectangle {
            x: 0; y: 434; width: parent.width; height: 42
            color: "#8A04060A"
            Rectangle { width: parent.width; height: 1; color: "#14FFFFFF" }
            Row {
                x: 16; anchors.verticalCenter: parent.verticalCenter; spacing: 15
                DesktopKeyHint { keyText: qsTr("↑ ↓"); label: qsTr("Move") }
                DesktopKeyHint { keyText: qsTr("Tab"); label: qsTr("Filter type") }
                DesktopKeyHint { keyText: "Esc"; label: qsTr("Close") }
            }
            Text {
                anchors.right: parent.right; anchors.rightMargin: 16; anchors.verticalCenter: parent.verticalCenter
                text: qsTr("%1 RESULTS").arg(root.visibleActions.length)
                color: DesktopTokens.textFaint
                font.family: DesktopTokens.monoFont
                font.pixelSize: 10
                font.weight: Font.DemiBold
                font.letterSpacing: 0.6
            }
        }
    }
    Keys.onEscapePressed: root.closeRequested()
}
