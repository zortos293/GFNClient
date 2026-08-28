pragma ComponentBehavior: Bound

import QtQuick
import OpenNOW

Item {
    id: root

    required property var game
    property bool current: false
    readonly property bool pointerHover: hoverHandler.hovered
    readonly property bool navigationFocus: current && AppController.inputMode !== "pointer"
    readonly property string artwork: game
        ? String(game.imageUrl || game.heroImageUrl || "") : ""

    signal activated()
    signal pointed()

    width: 112
    height: 168
    z: navigationFocus || pointerHover ? 10 : 0
    Accessible.role: Accessible.Button
    Accessible.name: game ? String(game.title || qsTr("Game")) : qsTr("Game")

    Rectangle {
        id: raisedShadow
        x: 3
        y: motionSurface.y + 7
        width: motionSurface.width - 6
        height: motionSurface.height
        radius: 10
        color: "#000000"
        opacity: root.pointerHover || root.navigationFocus ? 0.38 : 0.22

        Behavior on opacity {
            NumberAnimation { duration: AppController.reducedMotion ? 0 : 90; easing.type: Easing.BezierSpline; easing.bezierCurve: [0.2, 0, 0, 1, 1, 1] }
        }
    }

    Item {
        id: motionSurface
        x: 0
        y: !AppController.reducedMotion && root.pointerHover && !tapHandler.pressed ? -2 : 0
        width: root.width
        height: root.height
        scale: AppController.reducedMotion ? 1
               : tapHandler.pressed ? 0.98
               : root.navigationFocus ? 1.03 : 1
        transformOrigin: Item.Center

        RoundedArtwork {
            anchors.fill: parent
            artwork: root.artwork
            fallbackColor: "#171B27"
            cornerRadius: 10
            scrimStart: 1
        }

        Rectangle {
            anchors.fill: parent
            radius: 10
            color: "transparent"
            border.width: root.navigationFocus ? 2 : 1
            border.color: root.navigationFocus ? "#FFFFFF" : "#29FFFFFF"

            Behavior on border.color {
                ColorAnimation { duration: AppController.reducedMotion ? 0 : 90 }
            }
        }

        Behavior on y {
            NumberAnimation { duration: AppController.reducedMotion ? 0 : 90; easing.type: Easing.BezierSpline; easing.bezierCurve: [0.2, 0, 0, 1, 1, 1] }
        }
        Behavior on scale {
            NumberAnimation {
                duration: AppController.reducedMotion ? 0 : (tapHandler.pressed ? 70 : 90)
                easing.type: root.navigationFocus ? Easing.OutBack : Easing.BezierSpline
                easing.overshoot: 1.15
                easing.bezierCurve: [0.2, 0, 0, 1, 1, 1]
            }
        }
    }

    HoverHandler {
        id: hoverHandler
        acceptedDevices: PointerDevice.Mouse | PointerDevice.TouchPad | PointerDevice.Stylus
        cursorShape: Qt.PointingHandCursor
        onHoveredChanged: if (hovered) root.pointed()
    }

    TapHandler {
        id: tapHandler
        acceptedDevices: PointerDevice.Mouse | PointerDevice.TouchPad | PointerDevice.Stylus | PointerDevice.TouchScreen
        onTapped: root.activated()
    }
}
