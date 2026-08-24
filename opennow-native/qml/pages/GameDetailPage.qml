import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    required property var game
    signal back()
    signal play(string quality)

    GameArtwork {
        anchors.fill: parent
        variant: page.game.variant
    }
    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: "#fc070908" }
            GradientStop { position: 0.54; color: "#d5070908" }
            GradientStop { position: 1; color: "#3a070908" }
        }
    }
    Rectangle {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: parent.height * 0.34
        gradient: Gradient {
            GradientStop { position: 0; color: "#00070908" }
            GradientStop { position: 1; color: Theme.canvas }
        }
    }

    ActionButton {
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.margins: 32
        text: "Back"
        glyph: "←"
        onClicked: page.back()
    }

    Column {
        anchors.left: parent.left
        anchors.bottom: parent.bottom
        anchors.leftMargin: 52
        anchors.bottomMargin: 62
        width: Math.min(600, parent.width * 0.53)
        spacing: 16

        Row {
            spacing: 9
            Repeater {
                model: [page.game.store, page.game.genre, page.game.badge]
                Rectangle {
                    required property string modelData
                    width: detail.implicitWidth + 20
                    height: 29
                    radius: 14
                    color: "#c2171b18"
                    border.color: "#43504845"
                    Text { id: detail; anchors.centerIn: parent; text: modelData.toUpperCase(); color: Theme.inkSoft; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 0.8 }
                }
            }
        }
        Text {
            text: page.game.title
            color: Theme.ink
            font.pixelSize: Math.min(52, page.width * 0.045)
            font.weight: Font.Bold
        }
        Text {
            width: parent.width
            text: page.game.description
            color: Theme.inkSoft
            font.pixelSize: 15
            lineHeight: 1.35
            wrapMode: Text.WordWrap
        }
        Row {
            spacing: 12
            topPadding: 8
            ActionButton {
                id: playButton
                text: page.game.progress > 0 ? "Resume now" : "Play now"
                glyph: "▶"
                primary: true
                focus: page.visible
                onClicked: page.play("720p60")
                KeyNavigation.right: qualityButton
            }
            ActionButton {
                id: qualityButton
                text: "DEMO · 720p 60"
                glyph: "↯"
                onClicked: qualityMenu.open()
                Menu {
                    id: qualityMenu
                    MenuItem { text: "Demo · 720p · 60 FPS"; onTriggered: page.play("720p60") }
                    MenuItem { text: "1080p · 60 FPS"; onTriggered: page.play("1080p60") }
                    MenuItem { text: "1080p · 120 FPS"; onTriggered: page.play("1080p120") }
                    MenuItem { text: "4K · 60 FPS"; onTriggered: page.play("4k60") }
                }
            }
        }
        Row {
            spacing: 24
            topPadding: 7
            Text { text: "●  CLOUD SAVE SYNCED"; color: Theme.accent; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 0.8 }
            Text { text: "◉  EU NORTHEAST · 18 MS"; color: Theme.inkMuted; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 0.8 }
        }
    }
}
