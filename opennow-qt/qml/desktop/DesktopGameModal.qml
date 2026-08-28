import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    property var game: ShellStore.selectedGame
    signal closeRequested()
    signal playRequested()
    width: 1440; height: 900; focus: visible

    Rectangle { anchors.fill: parent; color: "#C9000000"; TapHandler { onTapped: root.closeRequested() } }
    Rectangle {
        id: dialog
        x: 272; y: 133; width: 896; height: 666; radius: 18
        color: "#ED0B101A"; border.width: 1; border.color: "#29FFFFFF"; clip: true
        scale: root.visible ? 1 : .96; opacity: root.visible ? 1 : 0
        Behavior on scale { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutBack } }
        Behavior on opacity { NumberAnimation { duration: DesktopTokens.quickDuration } }
        TapHandler { }

        RoundedArtwork { x: 0; y: 0; width: parent.width; height: 250; artwork: root.game ? String(root.game.heroImageUrl || root.game.imageUrl || "") : ""; cornerRadius: 18; scrimStart: .08; fallbackColor: "#1B2435" }
        Rectangle { x: 0; y: 120; width: parent.width; height: 130; gradient: Gradient { GradientStop { position: 0; color: "transparent" } GradientStop { position: 1; color: "#FA080D16" } } }
        Rectangle { x: 21; y: 20; width: publisher.implicitWidth + 22; height: 26; radius: 8; color: "#CC080B12"; border.width: 1; border.color: DesktopTokens.seam; Text { id: publisher; anchors.centerIn: parent; text: root.game ? String(root.game.publisherName || qsTr("GEFORCE NOW")).toUpperCase() : qsTr("GEFORCE NOW"); color: DesktopTokens.textBody; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: .6 } }
        DesktopButton { x: 843; y: 20; width: 32; height: 32; text: "×"; onClicked: root.closeRequested() }
        Text { x: 21; y: 162; width: 760; text: root.game ? String(root.game.title || qsTr("Game")) : qsTr("Game"); color: DesktopTokens.text; elide: Text.ElideRight; font.family: DesktopTokens.displayFont; font.pixelSize: 30; font.weight: Font.Black; font.letterSpacing: -.8 }
        Row { x: 21; y: 207; spacing: 8
            Repeater { model: [qsTr("READY TO PLAY"),"RTX ON","4K HDR",qsTr("CONTROLLER"),qsTr("SINGLE PLAYER")]
                delegate: Rectangle { required property string modelData; width: badgeText.implicitWidth + 20; height: 24; radius: 8; color: modelData === qsTr("READY TO PLAY") ? "#1F56E6A5" : "#0FFFFFFF"; border.width: 1; border.color: modelData === qsTr("READY TO PLAY") ? "#5256E6A5" : DesktopTokens.seam; Text { id: badgeText; anchors.centerIn: parent; text: modelData; color: modelData === qsTr("READY TO PLAY") ? DesktopTokens.green : DesktopTokens.textBody; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: .5 } }
            }
        }
        Row { x: 21; y: 268; spacing: 10
            DesktopButton { width: 157; height: 40; primary: true; text: qsTr("▶  Resume"); shortcutText: qsTr("ENTER"); Component.onCompleted: forceActiveFocus(); onClicked: root.playRequested() }
            DesktopButton { width: 108; height: 40; text: qsTr("☆  Favourite"); onClicked: if (root.game) ShellStore.toggleFavorite(root.game) }
            DesktopButton { width: 158; height: 40; text: qsTr("▱  Add to collection") }
        }
        Rectangle { x: 687; y: 272; width: 188; height: 32; radius: 9; color: "#CC070A11"; border.width: 1; border.color: DesktopTokens.seam; Row { anchors.centerIn: parent; spacing: 8; Rectangle { width: 6; height: 6; radius: 3; color: DesktopTokens.green } Text { text: qsTr("OWNED ON STEAM · LINKED"); color: DesktopTokens.textBody; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: .5 } } }
        Rectangle { x: 0; y: 326; width: parent.width; height: 1; color: DesktopTokens.seamSoft }
        Text { x: 21; y: 352; width: 548; height: 44; wrapMode: Text.WordWrap; text: root.game && root.game.description ? String(root.game.description) : qsTr("Stream this game instantly from your linked library. Your settings and session preferences are applied before the remote machine starts."); color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; lineHeight: 1.45 }
        Rectangle { x: 21; y: 412; width: 546; height: 62; radius: 12; color: "#08FFFFFF"; border.width: 1; border.color: DesktopTokens.seamSoft
            Row { anchors.fill: parent; anchors.margins: 14; spacing: 0
                Repeater { model: [{l:qsTr("LAST PLAYED"),v:qsTr("2 hours ago")},{l:qsTr("TIME IN CLOUD"),v:"48 h 12 m"},{l:qsTr("LAST SESSION"),v:"1440p · 9 ms"},{l:qsTr("SAVE DATA"),v:qsTr("Synced to cloud")}]
                    delegate: Item { required property var modelData; width: 130; height: parent.height; Column { spacing: 5; Text { text: modelData.l; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.DemiBold; font.letterSpacing: .6 } Text { text: modelData.v; color: modelData.l === qsTr("SAVE DATA") ? DesktopTokens.green : DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; font.weight: Font.DemiBold } } Rectangle { anchors.right: parent.right; width: 1; height: parent.height; color: DesktopTokens.seamSoft } }
                }
            }
        }
        Text { x: 21; y: 492; text: qsTr("4 FRIENDS PLAY THIS"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: .8 }
        Row { x: 21; y: 516; spacing: -4; Repeater { model: ["J","M","S","N"]; delegate: Rectangle { required property string modelData; width: 27; height: 27; radius: 14; color: "#17FFFFFF"; border.width: 2; border.color: "#0B101A"; Text { anchors.centerIn: parent; text: modelData; color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 10; font.weight: Font.Bold } } } }
        Text { x: 135; y: 522; text: qsTr("Jules played 2 hours ago · Mika, Sam and Nova own it"); color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 10 }
        Rectangle { x: 587; y: 348; width: 288; height: 253; radius: 14; color: "#CC070A11"; border.width: 1; border.color: DesktopTokens.seamSoft
            Text { x: 16; y: 17; text: qsTr("Stream for this game"); color: DesktopTokens.text; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.Black }
            Text { anchors.right: parent.right; anchors.rightMargin: 16; y: 19; text: qsTr("OVERRIDDEN"); color: DesktopTokens.amber; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.DemiBold; font.letterSpacing: .6 }
            Column { x: 16; y: 51; width: parent.width - 32; spacing: 18
                Repeater { model: [{l:qsTr("Resolution"),v:String(ShellStore.settings.resolution || "3840x2160").split("x")[1] + "p"},{l:qsTr("Frame rate"),v:String(ShellStore.settings.fps || 120)+" fps"},{l:qsTr("Ray tracing"),v:"OVERDRIVE"},{l:qsTr("Bitrate"),v:String(ShellStore.settings.maxBitrateMbps || 75)+" Mbps"}]
                    delegate: Item { required property var modelData; width: parent.width; height: 22; Text { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; text: modelData.l; color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 11 } Text { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; text: modelData.v; color: modelData.l === qsTr("Ray tracing") ? DesktopTokens.green : DesktopTokens.textHigh; font.family: modelData.l === qsTr("Ray tracing") ? DesktopTokens.monoFont : DesktopTokens.bodyFont; font.pixelSize: 10; font.weight: Font.Bold } }
                }
                DesktopButton { width: parent.width; height: 34; text: qsTr("Edit overrides"); onClicked: AppController.navigate("settings-streaming") }
            }
        }
        Rectangle { x: 0; y: 622; width: parent.width; height: 44; color: "#E8070A11"; Rectangle { width: parent.width; height: 1; color: DesktopTokens.seamSoft } Row { x: 21; anchors.verticalCenter: parent.verticalCenter; spacing: 16; DesktopKeyHint { keyText:"Esc"; label:qsTr("Close") } DesktopKeyHint { keyText:qsTr("Enter"); label:qsTr("Play") } DesktopKeyHint { keyText:"F"; label:qsTr("Favourite") } } Text { anchors.right: parent.right; anchors.rightMargin: 21; anchors.verticalCenter: parent.verticalCenter; text: qsTr("SESSION WILL START IN EU-WEST · RTX 5080"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: .6 } }
    }
    Keys.onEscapePressed: root.closeRequested()
    Keys.onReturnPressed: root.playRequested()
}
