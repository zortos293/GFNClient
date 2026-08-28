pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    property string selectedTab: "online"
    readonly property var friends: [
        {initial:"M", name:"Mika", status:qsTr("Playing for 1 h 12 m"), game:"Elden Ring", action:qsTr("Join game"), online:true},
        {initial:"N", name:"Nova", status:qsTr("Playing for 22 m"), game:"Helldivers II", action:qsTr("Join game"), online:true},
        {initial:"S", name:"Sam", status:qsTr("In your party · browsing"), game:qsTr("Not in a game"), action:qsTr("Invite to game"), online:true},
        {initial:"J", name:"Jules", status:qsTr("Last online 2 h ago"), game:"Cyberpunk 2077", action:"", online:false},
        {initial:"T", name:"Tomas", status:qsTr("Last online yesterday"), game:"Satisfactory", action:"", online:false},
        {initial:"A", name:"Ash", status:qsTr("Last online 3 days ago"), game:"Dead Cells", action:"", online:false}
    ]
    readonly property var displayedFriends: selectedTab === "online"
        ? friends.filter(friend => friend.online) : friends

    Row {
        x: 24; y: 18; spacing: 8
        Repeater {
            model: [{key:"online",name:qsTr("Online"),count:3},{key:"all",name:qsTr("All friends"),count:24},{key:"requests",name:qsTr("Requests"),count:2}]
            delegate: Button {
                id: tab
                required property var modelData
                width: Math.max(86, tabRow.implicitWidth + 28); height: 30; padding: 0
                background: Rectangle { radius: 9; color: root.selectedTab === tab.modelData.key ? "#1AFFFFFF" : (tab.hovered ? "#0CFFFFFF" : "transparent"); border.width: root.selectedTab === tab.modelData.key ? 1 : 0; border.color: "#3DFFFFFF" }
                contentItem: Row { id: tabRow; anchors.centerIn: parent; spacing: 7; Text { text: tab.modelData.name; color: root.selectedTab === tab.modelData.key ? DesktopTokens.text : DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; font.weight: Font.Bold } Text { text: tab.modelData.count; color: tab.modelData.key === "requests" ? DesktopTokens.amber : DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold } }
                onClicked: root.selectedTab = modelData.key
            }
        }
    }
    DesktopButton { anchors.right: parent.right; anchors.rightMargin: 384; y: 18; width: 102; height: 30; primary: true; text: qsTr("＋  Add friend") }

    Rectangle {
        x: 24; y: 64; width: parent.width - 392; height: parent.height - 66
        radius: 14; color: "#A6080C15"; border.width: 1; border.color: DesktopTokens.seamSoft
        ListView {
            id: list
            anchors.fill: parent; anchors.margins: 16; spacing: 10; clip: true
            model: root.selectedTab === "requests" ? [] : root.displayedFriends
            section.property: "online"
            section.criteria: ViewSection.FullString
            section.delegate: Item {
                required property string section
                width: list.width; height: 31
                Text { y: 2; text: section === "true" ? qsTr("IN A GAME") : qsTr("OFFLINE · 21"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: 1 }
                Rectangle { y: 25; width: parent.width; height: 1; color: DesktopTokens.seamSoft }
            }
            delegate: ItemDelegate {
                id: friendRow
                required property var modelData
                width: list.width; height: 64; padding: 0
                background: Rectangle { radius: 11; color: friendRow.hovered || friendRow.activeFocus ? "#0FFFFFFF" : (modelData.online ? "#08FFFFFF" : "transparent"); border.width: modelData.online ? 1 : 0; border.color: DesktopTokens.seamSoft }
                contentItem: Item {
                    Rectangle { x: 12; anchors.verticalCenter: parent.verticalCenter; width: 36; height: 36; radius: 18; color: "#14FFFFFF"; border.width: 1; border.color: DesktopTokens.seam; Text { anchors.centerIn: parent; text: friendRow.modelData.initial; color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.Bold } }
                    Column { x: 61; anchors.verticalCenter: parent.verticalCenter; spacing: 3
                        Text { text: friendRow.modelData.name; color: friendRow.modelData.online ? DesktopTokens.textHigh : DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 13; font.weight: Font.Bold }
                        Row { spacing: 6; Rectangle { visible: friendRow.modelData.online; anchors.verticalCenter: parent.verticalCenter; width: 6; height: 6; radius: 3; color: friendRow.modelData.name === "Sam" ? DesktopTokens.focus : DesktopTokens.green } Text { text: friendRow.modelData.status; color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 11 } }
                    }
                    Text { x: parent.width * .40; anchors.verticalCenter: parent.verticalCenter; text: friendRow.modelData.game; color: friendRow.modelData.online ? DesktopTokens.textBody : DesktopTokens.textFaint; font.family: friendRow.modelData.online ? DesktopTokens.bodyFont : DesktopTokens.monoFont; font.pixelSize: friendRow.modelData.online ? 12 : 9; font.letterSpacing: friendRow.modelData.online ? 0 : .6 }
                    DesktopButton { visible: friendRow.modelData.action !== ""; anchors.right: parent.right; anchors.rightMargin: 52; anchors.verticalCenter: parent.verticalCenter; width: 105; height: 32; text: friendRow.modelData.action }
                    DesktopButton { visible: friendRow.modelData.online; anchors.right: parent.right; anchors.rightMargin: 12; anchors.verticalCenter: parent.verticalCenter; width: 32; height: 32; text: "▢" }
                }
            }
        }
        Column {
            anchors.centerIn: parent; visible: root.selectedTab === "requests"; spacing: 12
            Text { anchors.horizontalCenter: parent.horizontalCenter; text: qsTr("Friend requests are shown on the right"); color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 16; font.weight: Font.Bold }
            Text { anchors.horizontalCenter: parent.horizontalCenter; text: qsTr("Accept or dismiss each request without leaving this screen."); color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 12 }
        }
    }

    Column {
        anchors.right: parent.right; anchors.rightMargin: 24; y: 18; width: 352; spacing: 16
        Rectangle {
            width: parent.width; height: 278; radius: 14; color: "#C7080C15"; border.width: 1; border.color: DesktopTokens.seamSoft
            Text { x: 16; y: 17; text: qsTr("Your party"); color: DesktopTokens.text; font.family: DesktopTokens.bodyFont; font.pixelSize: 13; font.weight: Font.Black }
            Text { anchors.right: parent.right; anchors.rightMargin: 16; y: 18; text: qsTr("2 / 4 · EU-WEST"); color: DesktopTokens.focus; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: .6 }
            Column { x: 16; y: 48; width: parent.width - 32; spacing: 10
                Repeater { model: [{i:"Z",n:"Zortos",s:qsTr("HOST · ULTIMATE"),m:"●"},{i:"S",n:"Sam",s:qsTr("READY · 12 MS"),m:"○"}]
                    delegate: Rectangle { required property var modelData; width: parent.width; height: 50; radius: 10; color: "#0AFFFFFF"; Rectangle { x: 10; anchors.verticalCenter: parent.verticalCenter; width: 32; height: 32; radius: 16; color: "#14FFFFFF"; Text { anchors.centerIn: parent; text: modelData.i; color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; font.weight: Font.Bold } } Column { x: 52; anchors.verticalCenter: parent.verticalCenter; spacing: 2; Text { text: modelData.n; color: DesktopTokens.textHigh; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.Bold } Text { text: modelData.s; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold } } Text { anchors.right: parent.right; anchors.rightMargin: 13; anchors.verticalCenter: parent.verticalCenter; text: modelData.m; color: modelData.i === "Z" ? DesktopTokens.green : DesktopTokens.textFaint; font.pixelSize: 14 } }
                }
                Rectangle { width: parent.width; height: 43; radius: 10; color: "transparent"; border.width: 1; border.color: DesktopTokens.seam; Text { anchors.centerIn: parent; text: qsTr("＋  Invite a friend"); color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 12 } }
                DesktopButton { width: parent.width; height: 40; primary: true; text: qsTr("Start a session together") }
            }
        }
        Rectangle {
            width: parent.width; height: 168; radius: 14; color: "#C7080C15"; border.width: 1; border.color: DesktopTokens.seamSoft
            Text { x: 16; y: 17; text: qsTr("Requests"); color: DesktopTokens.text; font.family: DesktopTokens.bodyFont; font.pixelSize: 13; font.weight: Font.Black }
            Text { anchors.right: parent.right; anchors.rightMargin: 16; y: 18; text: qsTr("2 PENDING"); color: DesktopTokens.amber; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: .6 }
            Column { x: 16; y: 50; width: parent.width - 32; spacing: 10
                Repeater { model: [{i:"K",n:"Kai",s:qsTr("4 games in common")},{i:"I",n:"Iris",s:qsTr("Friend of Mika")}]
                    delegate: Item { required property var modelData; width: parent.width; height: 40; Rectangle { width: 30; height: 30; radius: 15; anchors.verticalCenter: parent.verticalCenter; color: "#12FFFFFF"; Text { anchors.centerIn: parent; text: modelData.i; color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 10; font.weight: Font.Bold } } Column { x: 40; anchors.verticalCenter: parent.verticalCenter; spacing: 2; Text { text: modelData.n; color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.Bold } Text { text: modelData.s; color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 10 } } DesktopButton { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; width: 30; height: 30; text: "×" } DesktopButton { anchors.right: parent.right; anchors.rightMargin: 40; anchors.verticalCenter: parent.verticalCenter; width: 30; height: 30; text: "✓" } }
                }
            }
        }
        Rectangle { width: parent.width; height: 138; radius: 14; color: "#A6080C15"; border.width: 1; border.color: DesktopTokens.seamSoft
            Column { anchors.fill: parent; anchors.margins: 16; spacing: 10
                Text { text: qsTr("Friends come from your account"); color: DesktopTokens.textHigh; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.Bold }
                Text { width: parent.width; wrapMode: Text.WordWrap; text: qsTr("OpenNOW reads your provider friends list and adds local invites on top, so a party works even when a friend uses the official client."); color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; lineHeight: 1.45 }
                Text { text: qsTr("Manage linked accounts  ›"); color: DesktopTokens.focus; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; font.weight: Font.Bold }
            }
        }
    }
}
