import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: "#273149" }
            GradientStop { position: 0.5; color: "#17192A" }
            GradientStop { position: 1; color: "#302238" }
        }
    }

    Column {
        x: 64; y: 64
        width: parent.width - 128
        spacing: 40

        Column {
            spacing: 10
            Text {
                text: qsTr("OPENNOW V3 · COMPONENTS")
                color: Theme.focus
                font.family: Theme.bodyFont
                font.pixelSize: 14
                font.weight: Font.Black
                font.letterSpacing: 1.12
            }
            Text {
                text: qsTr("Tiles, pills & glass")
                color: Theme.mediaForeground
                font.family: Theme.displayFont
                font.pixelSize: 52
                font.weight: Font.Black
            }
            Text {
                width: 820
                wrapMode: Text.WordWrap
                text: qsTr("Every game is a rounded icon tile with a thin white edge over a dimmed scene of its own key art. Chrome lives in frosted pills. Focus scales the tile and rings it in sky.")
                color: Qt.rgba(1, 1, 1, 0.68)
                font.family: Theme.bodyFont
                font.pixelSize: 18
                font.weight: Font.DemiBold
                lineHeight: 1.44
            }
        }

        Row {
            spacing: 40
            GameTile {
                title: qsTr("Control")
                artwork: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/62SFJEAVHQ410MNWZCNPVDYT09.jpg"
                storeGlyph: "E"
                storeColor: Theme.cartEpic
                KeyNavigation.right: focusedTile
            }
            GameTile {
                id: focusedTile
                title: qsTr("Baldur's Gate 3")
                artwork: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/0KQRAJ13TA66P3QHJXS3BQ4YEM.jpg"
                KeyNavigation.left: parent.children[0]
                KeyNavigation.right: wideTile
                Component.onCompleted: forceActiveFocus()
            }
            GameTile {
                id: wideTile
                wide: true
                title: qsTr("Cyberpunk 2077")
                eyebrow: qsTr("STEAM · 2 H AGO")
                artwork: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/27G0GQ44XWDM79Z64SSA5Z89F6.jpg"
                KeyNavigation.left: focusedTile
            }
        }

        Flow {
            width: parent.width
            spacing: 24
            GlassPanel {
                width: 234; height: 56; panelRadius: 28; strong: true
                Text {
                    anchors.centerIn: parent
                    text: qsTr("Recently played")
                    color: Theme.label
                    font.family: Theme.displayFont
                    font.pixelSize: 20
                    font.weight: Font.Black
                }
            }
            GlassPanel {
                width: 437; height: 56; panelRadius: 28; strong: true
                Row {
                    anchors.centerIn: parent
                    spacing: 18
                    Rectangle { width: 10; height: 10; radius: 5; color: Theme.mint }
                    Text { text: qsTr("EU-West · 9 ms"); color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 16; font.weight: Font.Bold }
                    Rectangle { width: 1; height: 20; color: Theme.seam }
                    Text { text: qsTr("16:19 | 08/27"); color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 16; font.weight: Font.Bold }
                    Rectangle { width: 1; height: 20; color: Theme.seam }
                    Text { text: qsTr("Ultimate"); color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 16; font.weight: Font.Bold }
                }
            }
            HintBar {
                hints: [
                    { glyph: "A", label: qsTr("Select") },
                    { glyph: "B", label: qsTr("Back") },
                    { glyph: "−", label: qsTr("Details") },
                    { glyph: "+", label: qsTr("Menu") }
                ]
            }
            NavPill { currentRoute: "home" }
        }
    }
}
