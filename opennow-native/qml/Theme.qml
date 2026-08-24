pragma Singleton
import QtQuick

QtObject {
    readonly property color canvas: "#070a08"
    readonly property color surface: "#0a0e0c"
    readonly property color surfaceRaised: "#0e120f"
    readonly property color surfaceBright: "#17201b"
    readonly property color ink: "#e8f2ea"
    readonly property color inkSoft: "#a0aaa3"
    readonly property color inkMuted: "#667169"
    readonly property color accent: "#4ce87f"
    readonly property color accentStrong: "#36d86c"
    readonly property color accentInk: "#041309"
    readonly property color warning: "#f3c969"
    readonly property color error: "#ff7b72"
    readonly property color divider: "#1c231e"

    readonly property int railWidth: 104
    readonly property int pageMargin: 56
    readonly property int radiusSmall: 10
    readonly property int radius: 16
    readonly property int radiusLarge: 24
    readonly property int motionFast: 120
    readonly property int motion: 220

    readonly property font displayFont: Qt.font({ family: "Inter Display", weight: Font.DemiBold })
    readonly property font bodyFont: Qt.font({ family: "Inter", weight: Font.Normal })
    readonly property font monoFont: Qt.font({ family: "GeistMono Nerd Font", weight: Font.Medium })
}
