import QtQuick
import OpenNOW

Item {
    id: root
    property string artwork: ShellStore.catalogGames.length
        ? DesktopTokens.artworkUrl(ShellStore.catalogGames[0], true) : ""
    property bool signIn: false
    readonly property string normalizedArtwork: root.artwork !== ""
        ? DesktopTokens.decodeArtworkUrl(root.artwork)
        : root.signIn ? "qrc:/qt/qml/OpenNOW/res/brand/signin-hero.jpg" : "qrc:/qt/qml/OpenNOW/res/brand/desktop-renew.jpg"

    ArtworkSource {
        id: artworkSource
        sourceUrl: root.normalizedArtwork
        active: root.visible
    }

    Rectangle { anchors.fill: parent; color: root.signIn ? Theme.shell : DesktopTokens.shell }

    Item {
        anchors.fill: parent
        clip: true
        Image {
            id: art
            width: parent.width
            height: parent.height * (root.signIn ? 1.18 : 1)
            y: root.signIn ? -parent.height * 0.12 : 0
            source: artworkSource.resolvedUrl
            asynchronous: true
            cache: true
            fillMode: Image.PreserveAspectCrop
            sourceSize: Qt.size(1440, 900)
            opacity: status === Image.Ready && (root.signIn || String(ShellStore.settings.desktopBackground || "art") === "art") ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: DesktopTokens.revealDuration } }
        }
    }

    Rectangle {
        visible: root.signIn
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0; color: "#B80B0F1A" }
            GradientStop { position: 0.4; color: "#990B0F1A" }
            GradientStop { position: 1; color: "#D60B0F1A" }
        }
    }
    Rectangle {
        visible: root.signIn
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: "#F2070A11" }
            GradientStop { position: 0.5; color: "#00000000" }
            GradientStop { position: 1; color: "#F2070A11" }
        }
    }

    Rectangle {
        anchors.fill: parent
        visible: !root.signIn && String(ShellStore.settings.desktopBackground || "art") !== "solid"
        // A gradient-only background must remain visible above the darkening
        // layers used for artwork. Solid mode has neither artwork nor tint.
        z: String(ShellStore.settings.desktopBackground || "art") === "gradient" ? 1 : 0
        gradient: Gradient {
            GradientStop { position: 0; color: Qt.rgba(Theme.focus.r,Theme.focus.g,Theme.focus.b,
                String(ShellStore.settings.desktopBackground || "art") === "gradient" ? 0.22 : 0.07) }
            GradientStop { position: 0.4; color: "transparent" }
        }
    }
    Rectangle {
        visible: !root.signIn
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, 0.92) }
            GradientStop { position: 0.34; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, 0.78) }
            GradientStop { position: 1; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, 0.94) }
        }
    }
    Rectangle {
        visible: !root.signIn
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0; color: "#00000000" }
            GradientStop { position: 1; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, 0.55) }
        }
    }
}
