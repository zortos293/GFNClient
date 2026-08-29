import QtQuick
import OpenNOW

Rectangle {
    id: root
    property string artwork: ""
    property color tint: "#111827"
    anchors.fill: parent
    color: Theme.shell

    ArtworkSource {
        id: artworkSource
        sourceUrl: DesktopTokens.decodeArtworkUrl(root.artwork)
        active: root.visible
    }

    Image {
        anchors.fill: parent
        source: artworkSource.resolvedUrl
        fillMode: Image.PreserveAspectCrop
        sourceSize: Qt.size(Math.ceil(width), Math.ceil(height))
        asynchronous: true
        opacity: status === Image.Ready ? 0.58 : 0
        Behavior on opacity { NumberAnimation { duration: Theme.enterDuration } }
    }
    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Qt.darker(root.tint, 1.5) }
            GradientStop { position: 0.48; color: Qt.rgba(0.03, 0.05, 0.09, 0.72) }
            GradientStop { position: 1; color: Qt.rgba(0.03, 0.04, 0.08, 0.94) }
        }
    }
}
