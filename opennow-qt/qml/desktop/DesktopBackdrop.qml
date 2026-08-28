import QtQuick
import OpenNOW

Item {
    id: root
    property string artwork: ShellStore.catalogGames.length
        ? String(ShellStore.catalogGames[0].heroImageUrl || ShellStore.catalogGames[0].imageUrl || "") : ""

    Rectangle { anchors.fill: parent; color: DesktopTokens.shell }
    Image {
        anchors.fill: parent
        source: root.artwork
        asynchronous: true
        cache: true
        fillMode: Image.PreserveAspectCrop
        sourceSize: Qt.size(1440, 900)
        opacity: status === Image.Ready ? 0.14 : 0
        Behavior on opacity { NumberAnimation { duration: DesktopTokens.revealDuration } }
    }
    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: "#F2070A11" }
            GradientStop { position: 0.34; color: "#DB09101D" }
            GradientStop { position: 1; color: "#F2070B14" }
        }
    }
    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0; color: "#00000000" }
            GradientStop { position: 1; color: "#B802040A" }
        }
    }
}
