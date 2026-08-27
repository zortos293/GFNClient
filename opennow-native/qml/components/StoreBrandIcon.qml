import QtQuick
import OpenNOW

Item {
    id: root
    property string provider: ""
    property url source: ""
    property color foreground: Theme.ink
    readonly property string normalizedProvider: provider.toUpperCase().replace(/[^A-Z0-9]/g, "")
    readonly property url fallbackSource: normalizedProvider.indexOf("STEAM") >= 0
                                          ? "qrc:/OpenNOW/assets/store-icons/steam.svg"
                                          : normalizedProvider.indexOf("EPIC") >= 0
                                            ? "qrc:/OpenNOW/assets/store-icons/epic.svg"
                                            : normalizedProvider.indexOf("UBI") >= 0 || normalizedProvider.indexOf("UPLAY") >= 0
                                              ? "qrc:/OpenNOW/assets/store-icons/ubisoft.svg"
                                              : normalizedProvider.indexOf("BATTLE") >= 0
                                                ? "qrc:/OpenNOW/assets/store-icons/battlenet.svg" : ""

    Image {
        id: fallbackImage
        anchors.fill: parent
        anchors.margins: 2
        source: root.fallbackSource
        fillMode: Image.PreserveAspectFit
        asynchronous: true
        smooth: true
        visible: brandImage.status !== Image.Ready
    }

    Image {
        id: brandImage
        anchors.fill: parent
        anchors.margins: 1
        source: root.source
        fillMode: Image.PreserveAspectFit
        asynchronous: true
        smooth: true
        opacity: status === Image.Ready ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: Theme.motionFast } }
    }

    Text {
        anchors.centerIn: parent
        visible: brandImage.status !== Image.Ready && fallbackImage.status !== Image.Ready
        text: root.provider.charAt(0).toUpperCase()
        color: root.foreground
        font.family: Theme.displayFont.family
        font.pixelSize: Math.max(12, root.height * 0.46)
        font.weight: Font.Bold
    }
}
