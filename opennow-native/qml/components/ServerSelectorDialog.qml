import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: dialog
    required property var game
    signal launch(string serverId)
    signal cancelled()
    property int selectedIndex: 0
    readonly property var servers: [
        { id: "EU-NORTHWEST-01", region: "EUROPE", ping: 24, queue: 12, wait: "~4 min", badge: "AUTO" },
        { id: "EU-WEST-02", region: "EUROPE", ping: 18, queue: 41, wait: "~15 min", badge: "NEAREST" },
        { id: "EU-SOUTHEAST-03", region: "EUROPE", ping: 46, queue: 7, wait: "~3 min", badge: "" },
        { id: "NP-SJC6-01", region: "NORTH AMERICA", ping: 132, queue: 5, wait: "~2 min", badge: "" },
        { id: "NP-CHI-03", region: "NORTH AMERICA", ping: 151, queue: 23, wait: "~9 min", badge: "" }
    ]

    function confirmLaunch() {
        var server = servers[selectedIndex]
        appState.selectServer(server.id, server.region === "EUROPE" ? "EU-WEST" : "US", server.ping)
        launch(server.id)
    }

    Keys.onEscapePressed: cancelled()
    Component.onCompleted: serverList.forceActiveFocus()

    Rectangle { anchors.fill: parent; color: "#b8050907" }

    Rectangle {
        anchors.centerIn: parent
        width: 860
        height: 756
        radius: 18
        color: "#0d120f"
        border.color: "#273129"

        Column {
            anchors.fill: parent

            Item {
                width: parent.width; height: 92
                Column {
                    anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; anchors.leftMargin: 25; spacing: 7
                    Row {
                        spacing: 12
                        Text { text: "Select server"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 26; font.weight: Font.DemiBold }
                        Rectangle { anchors.verticalCenter: parent.verticalCenter; width: 74; height: 26; radius: 7; color: "transparent"; border.color: "#4b5750"; Text { anchors.centerIn: parent; text: "FREE TIER"; color: Theme.inkSoft; font.family: Theme.monoFont.family; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1.1 } }
                    }
                    Text { text: dialog.game.title + " · Live queue and latency measurements"; color: Theme.inkMuted; font.family: Theme.bodyFont.family; font.pixelSize: 12 }
                }
                Button {
                    anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22
                    width: 34; height: 34; text: "×"; onClicked: dialog.cancelled()
                    contentItem: Text { text: "×"; color: Theme.inkSoft; font.pixelSize: 19; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                    background: Rectangle { radius: 8; color: Theme.surfaceRaised; border.color: Theme.divider }
                }
                Rectangle { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; height: 1; color: Theme.divider }
            }

            Item {
                width: parent.width; height: 590
                Text { x: 25; y: 18; text: "RECOMMENDED"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1.4 }
                Row {
                    x: 25; y: 42; spacing: 12
                    Repeater {
                        model: [
                            { label: "AUTO SELECTED", note: "Best ping + queue balance", server: "EU-NORTHWEST-01", ping: "24 ms", queue: "Queue 12", wait: "~4 min" },
                            { label: "CLOSEST SERVER", note: "Fastest route from your network", server: "EU-WEST-02", ping: "18 ms", queue: "Queue 41", wait: "~15 min" }
                        ]
                        Button {
                            id: recommendation
                            required property var modelData
                            required property int index
                            width: 394; height: 144
                            onClicked: { dialog.selectedIndex = index; serverList.currentIndex = index }
                            contentItem: Column {
                                anchors.fill: parent; anchors.margins: 16; spacing: 9
                                RowLayout {
                                    width: parent.width
                                    Text { text: modelData.label; color: index === 0 ? Theme.accent : Theme.inkSoft; font.family: Theme.monoFont.family; font.pixelSize: 10; font.weight: Font.Bold; font.letterSpacing: 1 }
                                    Item { Layout.fillWidth: true }
                                    Text { text: index === 0 ? "✓" : "LOWEST LATENCY"; color: index === 0 ? Theme.accent : Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: index === 0 ? 18 : 9 }
                                }
                                Text { text: modelData.note; color: Theme.inkMuted; font.family: Theme.bodyFont.family; font.pixelSize: 11 }
                                Text { text: modelData.server; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 15; font.weight: Font.Bold }
                                Row { spacing: 8; Repeater { model: [modelData.ping, modelData.queue, modelData.wait]; Rectangle { required property string modelData; width: chip.implicitWidth + 14; height: 24; radius: 5; color: "#152219"; Text { id: chip; anchors.centerIn: parent; text: modelData; color: modelData.indexOf("Queue 41") >= 0 ? Theme.warning : Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 10; font.weight: Font.Bold } } } }
                            }
                            background: Rectangle { radius: 12; color: index === dialog.selectedIndex ? "#122018" : Theme.surfaceRaised; border.width: index === dialog.selectedIndex ? 2 : 1; border.color: index === dialog.selectedIndex ? Theme.accent : "#2c352f" }
                        }
                    }
                }

                Row {
                    x: 25; y: 214; width: 810
                    Text { width: 600; text: "ALL SERVERS"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1.4 }
                    Text { width: 78; text: "PING"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 1.2 }
                    Text { width: 82; text: "QUEUE"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 1.2 }
                    Text { text: "WAIT"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 1.2 }
                }
                ListView {
                    id: serverList
                    x: 25; y: 240; width: 810; height: 322
                    model: dialog.servers
                    currentIndex: dialog.selectedIndex
                    spacing: 6
                    keyNavigationWraps: true
                    Keys.onReturnPressed: { dialog.selectedIndex = currentIndex; dialog.confirmLaunch() }
                    onCurrentIndexChanged: dialog.selectedIndex = currentIndex
                    delegate: Item {
                        required property var modelData
                        required property int index
                        width: serverList.width; height: index === 0 || index === 3 ? 68 : 42
                        Text { visible: index === 0 || index === 3; anchors.top: parent.top; text: modelData.region; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 9; font.weight: Font.Bold }
                        Button {
                            anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
                            height: 42; hoverEnabled: true
                            onClicked: { dialog.selectedIndex = index; serverList.currentIndex = index }
                            contentItem: RowLayout {
                                anchors.fill: parent; anchors.leftMargin: 12; anchors.rightMargin: 12
                                Text { text: modelData.id; color: Theme.inkSoft; font.family: Theme.monoFont.family; font.pixelSize: 11; font.weight: Font.Bold }
                                Rectangle { visible: modelData.badge.length > 0; width: badge.implicitWidth + 12; height: 20; radius: 4; color: "#163322"; Text { id: badge; anchors.centerIn: parent; text: modelData.badge; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 8; font.weight: Font.Bold } }
                                Item { Layout.fillWidth: true }
                                Text { Layout.preferredWidth: 74; text: modelData.ping + " ms"; color: modelData.ping < 50 ? Theme.accent : (modelData.ping < 145 ? Theme.warning : Theme.error); font.family: Theme.monoFont.family; font.pixelSize: 11; font.weight: Font.Bold }
                                Text { Layout.preferredWidth: 70; text: "Q:" + modelData.queue; color: modelData.queue < 20 ? Theme.accent : Theme.warning; font.family: Theme.monoFont.family; font.pixelSize: 11; font.weight: Font.Bold }
                                Text { text: modelData.wait; color: Theme.inkSoft; font.family: Theme.monoFont.family; font.pixelSize: 10 }
                            }
                            background: Rectangle { radius: 8; color: index === dialog.selectedIndex ? "#132219" : Theme.surfaceRaised; border.width: index === dialog.selectedIndex ? 1 : 1; border.color: index === dialog.selectedIndex ? Theme.accent : "#263029" }
                        }
                    }
                }
            }

            Item {
                width: parent.width; height: 74
                Rectangle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; height: 1; color: Theme.divider }
                Column {
                    anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; anchors.leftMargin: 25; spacing: 4
                    Text { text: "Live queue data refreshes every 2 minutes"; color: Theme.inkMuted; font.family: Theme.bodyFont.family; font.pixelSize: 10 }
                    Text { text: "POWERED BY PRINTEDWASTE"; color: "#3f4842"; font.family: Theme.monoFont.family; font.pixelSize: 8; font.letterSpacing: 1.1 }
                }
                Row {
                    anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; anchors.rightMargin: 25; spacing: 10
                    ActionButton { text: "Cancel"; onClicked: dialog.cancelled() }
                    ActionButton { text: "Launch on " + (dialog.selectedIndex === 0 ? "Auto" : dialog.servers[dialog.selectedIndex].id); glyph: "A"; primary: true; onClicked: dialog.confirmLaunch() }
                }
            }
        }
    }
}
