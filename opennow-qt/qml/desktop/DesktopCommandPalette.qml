import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    property string query: ""
    signal closeRequested()
    signal routeRequested(string route)
    anchors.fill: parent
    Rectangle { anchors.fill: parent; color: "#A8000000"; TapHandler { onTapped: root.closeRequested() } }
    Rectangle {
        x: (parent.width - 560) / 2; y: 132; width: 560; height: 420; radius: 16
        color: "#F70D121D"; border.width: 1; border.color: "#29FFFFFF"
        TapHandler { }
        TextField { id: field; x: 16; y: 16; width: 528; height: 46; leftPadding: 42; rightPadding: 16; placeholderText: qsTr("Search commands, games, and settings…"); color: DesktopTokens.textHigh; placeholderTextColor: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 14; background: Rectangle { radius: 11; color: "#52000000"; border.width: 1; border.color: field.activeFocus ? DesktopTokens.focus : DesktopTokens.seam } Text { x: 13; anchors.verticalCenter: parent.verticalCenter; text: "⌕"; color: DesktopTokens.textMuted; font.pixelSize: 19 } onTextChanged: root.query = text; Component.onCompleted: forceActiveFocus() }
        Text { x: 18; y: 79; text: qsTr("QUICK ACTIONS"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: .9 }
        Column { x: 9; y: 99; width: 542; spacing: 3
            Repeater { model: [{i:"⌂",n:qsTr("Go to Home"),d:qsTr("Open your desktop"),r:"home",k:"1"},{i:"▦",n:qsTr("Open Library"),d:qsTr("Browse all games"),r:"library",k:"2"},{i:"▣",n:qsTr("Open Store"),d:qsTr("Discover games"),r:"store",k:"3"},{i:"♧",n:qsTr("Friends and party"),d:qsTr("See who is online"),r:"friends",k:"4"},{i:"☷",n:qsTr("Settings"),d:qsTr("Configure OpenNOW"),r:"settings",k:","},{i:"◉",n:qsTr("Start last game"),d:qsTr("Resume your previous session"),r:"game-detail",k:"Enter"}]
                delegate: ItemDelegate { id: command; required property var modelData; width: parent.width; height: 48; visible: root.query === "" || String(modelData.n + " " + modelData.d).toLocaleLowerCase().indexOf(root.query.toLocaleLowerCase()) >= 0; padding: 0; background: Rectangle { radius: 9; color: command.hovered || command.activeFocus ? "#14FFFFFF" : "transparent" } contentItem: Item { Text { x: 12; anchors.verticalCenter: parent.verticalCenter; text: command.modelData.i; color: command.activeFocus ? DesktopTokens.focus : DesktopTokens.textMuted; font.pixelSize: 17 } Column { x: 45; anchors.verticalCenter: parent.verticalCenter; spacing: 2; Text { text: command.modelData.n; color: DesktopTokens.textHigh; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.Bold } Text { text: command.modelData.d; color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 10 } } Rectangle { anchors.right: parent.right; anchors.rightMargin: 12; anchors.verticalCenter: parent.verticalCenter; width: keyText.implicitWidth + 12; height: 20; radius: 5; color: "#12FFFFFF"; Text { id: keyText; anchors.centerIn: parent; text: command.modelData.k; color: DesktopTokens.textMuted; font.family: DesktopTokens.monoFont; font.pixelSize: 9 } } } onClicked: { root.routeRequested(modelData.r); root.closeRequested() } }
            }
        }
        Rectangle { x: 0; y: 381; width: parent.width; height: 39; color: "#99070A11"; Rectangle { width: parent.width; height: 1; color: DesktopTokens.seamSoft } Row { x: 16; anchors.verticalCenter: parent.verticalCenter; spacing: 16; DesktopKeyHint { keyText:qsTr("Arrows"); label:qsTr("Navigate") } DesktopKeyHint { keyText:qsTr("Enter"); label:qsTr("Open") } DesktopKeyHint { keyText:"Esc"; label:qsTr("Close") } } }
    }
    Keys.onEscapePressed: root.closeRequested()
}
