import QtQuick
import OpenNOW

Item {
    id: root
    property int variant: 0
    property string kicker: ""
    clip: true

    readonly property var palettes: [
        ["#071c17", "#28785a", "#f0a04b"],
        ["#160d23", "#6a3478", "#e25555"],
        ["#071522", "#165f78", "#63d0ce"],
        ["#21130a", "#9a4c28", "#e7c56f"],
        ["#111317", "#485466", "#cdd8e5"],
        ["#170809", "#692027", "#f16e58"]
    ]
    readonly property var palette: palettes[variant % palettes.length]

    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0.0; color: root.palette[0] }
            GradientStop { position: 0.62; color: root.palette[1] }
            GradientStop { position: 1.0; color: root.palette[2] }
        }
    }

    Rectangle {
        width: root.width * 0.54
        height: width
        radius: width / 2
        x: root.width * 0.58
        y: -height * 0.24
        color: "#22ffffff"
        rotation: 12
    }

    Repeater {
        model: 4
        Rectangle {
            width: root.width * (0.08 + index * 0.035)
            height: root.height * 1.45
            x: root.width * (0.16 + index * 0.12)
            y: -root.height * 0.22
            rotation: 24
            color: index % 2 ? "#0cffffff" : "#12ffffff"
        }
    }

    Text {
        visible: root.kicker.length > 0
        anchors.left: parent.left
        anchors.bottom: parent.bottom
        anchors.margins: Math.max(16, root.width * 0.04)
        text: root.kicker.toUpperCase()
        color: "#dfffe8"
        font.pixelSize: Math.max(12, root.width * 0.027)
        font.letterSpacing: 2.2
        opacity: 0.82
    }

    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0.0; color: "#00000000" }
            GradientStop { position: 0.68; color: "#18000000" }
            GradientStop { position: 1.0; color: "#9d000000" }
        }
    }
}
