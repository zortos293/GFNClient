import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    default property alias contentData: contentHost.data
    property string route: "home"
    property string title: qsTr("Home")
    property string subtitle: qsTr("3 friends online")
    property bool searchVisible: route !== "settings" && route.indexOf("settings-") !== 0 && route !== "friends"
    property string searchText: ""
    property bool railCollapsed: false
    signal routeRequested(string route)
    signal consoleModeRequested()
    signal commandPaletteRequested()

    width: 1440
    height: 900
    focus: true

    DesktopBackdrop { anchors.fill: parent }

    DesktopSidebar {
        id: sidebar
        currentRoute: root.route
        collapsed: root.railCollapsed
        onRouteRequested: route => root.routeRequested(route)
        onConsoleModeRequested: root.consoleModeRequested()
        onCollapseRequested: collapsed => root.railCollapsed = collapsed
    }

    Item {
        id: main
        x: root.railCollapsed ? 72 : 232
        width: root.width - x
        height: root.height
        Behavior on x { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic } }
        Behavior on width { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic } }

        Rectangle {
            width: parent.width; height: 60
            color: DesktopTokens.topBar
            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: DesktopTokens.seam }
            Row {
                x: 24; anchors.verticalCenter: parent.verticalCenter; spacing: 10
                Text { text: root.title; color: DesktopTokens.text; font.family: DesktopTokens.displayFont; font.pixelSize: 22; font.weight: Font.Black; font.letterSpacing: -0.4 }
                Text { anchors.baseline: parent.children[0].baseline; text: root.subtitle; color: DesktopTokens.textMuted; font.family: DesktopTokens.monoFont; font.pixelSize: 11; font.weight: Font.DemiBold; font.letterSpacing: 0.45 }
            }
            TextField {
                id: search
                visible: root.searchVisible
                anchors.right: parent.right
                anchors.rightMargin: 24
                anchors.verticalCenter: parent.verticalCenter
                width: 300; height: 34
                leftPadding: 34; rightPadding: 34; topPadding: 0; bottomPadding: 0
                color: DesktopTokens.textBody
                placeholderText: qsTr("Search your library")
                placeholderTextColor: "#66FFFFFF"
                font.family: DesktopTokens.bodyFont; font.pixelSize: 13; font.weight: Font.DemiBold
                text: root.searchText
                selectByMouse: true
                background: Rectangle { radius: 10; color: "#59000000"; border.width: 1; border.color: DesktopTokens.seam }
                onTextChanged: root.searchText = text
                Text { x: 11; anchors.verticalCenter: parent.verticalCenter; text: "⌕"; color: "#8AFFFFFF"; font.pixelSize: 18 }
                Rectangle { anchors.right: parent.right; anchors.rightMargin: 8; anchors.verticalCenter: parent.verticalCenter; width: 20; height: 20; radius: 6; color: "#1AFFFFFF"; border.width: 1; border.color: DesktopTokens.seam; Text { anchors.centerIn: parent; text: "/"; color: DesktopTokens.textBody; font.family: DesktopTokens.monoFont; font.pixelSize: 10 } }
            }
        }

        Item {
            id: contentHost
            x: 0; y: 60
            width: parent.width; height: 804
            clip: true
        }

        Rectangle {
            y: 864; width: parent.width; height: 36
            color: DesktopTokens.statusBar
            Rectangle { width: parent.width; height: 1; color: DesktopTokens.seam }
            Row { x: 24; anchors.verticalCenter: parent.verticalCenter; spacing: 16
                DesktopKeyHint { keyText: qsTr("Arrows"); label: qsTr("Move") }
                DesktopKeyHint { keyText: qsTr("Enter"); label: qsTr("Play") }
                DesktopKeyHint { keyText: "/"; label: qsTr("Search") }
                DesktopKeyHint { keyText: qsTr("Ctrl K"); label: qsTr("Commands") }
                DesktopKeyHint { keyText: "?"; label: qsTr("All shortcuts") }
            }
            Row { anchors.right: parent.right; anchors.rightMargin: 24; anchors.verticalCenter: parent.verticalCenter; spacing: 10
                Rectangle { width: 6; height: 6; radius: 3; color: DesktopTokens.green }
                Text { text: qsTr("EU-WEST · 9 ms"); color: DesktopTokens.textBody; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.DemiBold; font.letterSpacing: 0.4 }
                Rectangle { width: 1; height: 14; color: DesktopTokens.seam }
                Text { text: String(ShellStore.settings.themePack || "AURORA").toUpperCase() + qsTr(" THEME"); color: DesktopTokens.textMuted; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.DemiBold; font.letterSpacing: 0.4 }
            }
        }
    }

    Keys.onPressed: event => {
        if ((event.modifiers & Qt.ControlModifier) && event.key === Qt.Key_K) {
            root.commandPaletteRequested()
            event.accepted = true
        } else if ((event.modifiers & Qt.ControlModifier) && event.key === Qt.Key_B) {
            root.railCollapsed = !root.railCollapsed
            event.accepted = true
        } else if ((event.modifiers & Qt.ControlModifier) && event.key === Qt.Key_Comma) {
            root.routeRequested("settings")
            event.accepted = true
        } else if (event.key === Qt.Key_Slash && root.searchVisible) {
            search.forceActiveFocus()
            event.accepted = true
        }
    }
}
