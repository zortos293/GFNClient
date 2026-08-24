pragma Singleton
import QtQuick

QtObject {
    id: theme
    property int settingsRevision: 0
    readonly property string themeName: {
        settingsRevision
        return String(appState.preference("appearance.theme", "Dark"))
    }
    readonly property string accentName: {
        settingsRevision
        return String(appState.preference("appearance.accent", "green"))
    }
    readonly property bool light: themeName === "Light"
    readonly property color canvas: light ? "#edf3ef" : "#070a08"
    readonly property color surface: light ? "#e5ece7" : "#0a0e0c"
    readonly property color surfaceRaised: light ? "#f8fbf9" : "#0e120f"
    readonly property color surfaceBright: light ? "#d8e3dc" : "#17201b"
    readonly property color ink: light ? "#102017" : "#e8f2ea"
    readonly property color inkSoft: light ? "#435449" : "#a0aaa3"
    readonly property color inkMuted: light ? "#68776d" : "#667169"
    readonly property color accent: accentName === "blue" ? "#4899e8" : accentName === "purple" ? "#9066ef" : accentName === "orange" ? "#ffac16" : accentName === "pink" ? "#ef6688" : "#4ce87f"
    readonly property color accentStrong: accentName === "blue" ? "#3388dc" : accentName === "purple" ? "#7f51e4" : accentName === "orange" ? "#ec9900" : accentName === "pink" ? "#e04f75" : "#36d86c"
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

    property Connections preferenceConnection: Connections {
        target: appState
        function onPreferenceChanged(key, value) {
            if (key === "appearance.theme" || key === "appearance.accent")
                theme.settingsRevision += 1
        }
        function onPreferencesReset() { theme.settingsRevision += 1 }
    }
}
