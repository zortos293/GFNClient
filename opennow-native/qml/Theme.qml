pragma Singleton
import QtQuick

QtObject {
    readonly property color canvas: "#070908"
    readonly property color surface: "#101310"
    readonly property color surfaceRaised: "#171b17"
    readonly property color surfaceBright: "#202620"
    readonly property color ink: "#f3f7f3"
    readonly property color inkSoft: "#b5bdb6"
    readonly property color inkMuted: "#747d76"
    readonly property color accent: "#78e89b"
    readonly property color accentStrong: "#45c978"
    readonly property color accentInk: "#062211"
    readonly property color warning: "#f3c969"
    readonly property color error: "#ff7b72"
    readonly property color divider: "#2a302b"

    readonly property int railWidth: 104
    readonly property int pageMargin: 42
    readonly property int radiusSmall: 10
    readonly property int radius: 16
    readonly property int radiusLarge: 24
    readonly property int motionFast: 120
    readonly property int motion: 220

    readonly property font displayFont: Qt.font({ family: "DejaVu Sans", weight: Font.DemiBold })
    readonly property font bodyFont: Qt.font({ family: "DejaVu Sans", weight: Font.Normal })
}
