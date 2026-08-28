pragma Singleton
import QtQuick
import OpenNOW

QtObject {
    readonly property color shell: "#0B0F1A"
    readonly property color rail: "#E6080B12"
    readonly property color topBar: "#A80B0F1A"
    readonly property color statusBar: "#C70B0F1A"
    readonly property color surface: "#E8101521"
    readonly property color raised: "#14FFFFFF"
    readonly property color raisedStrong: "#1FFFFFFF"
    readonly property color seam: "#24FFFFFF"
    readonly property color seamSoft: "#0FFFFFFF"
    readonly property color text: "#FFFFFF"
    readonly property color textHigh: "#E0FFFFFF"
    readonly property color textBody: "#B8FFFFFF"
    readonly property color textMuted: "#80FFFFFF"
    readonly property color textFaint: "#52FFFFFF"
    readonly property color focus: "#7FD4FF"
    readonly property color green: "#56E6A5"
    readonly property color amber: "#FFD166"
    readonly property color danger: "#FF8A80"
    readonly property string displayFont: Theme.displayFont
    readonly property string bodyFont: Theme.bodyFont
    readonly property string monoFont: Theme.monoFont
    readonly property int quickDuration: AppController.reducedMotion ? 0 : 120
    readonly property int motionDuration: AppController.reducedMotion ? 0 : 220
    readonly property int revealDuration: AppController.reducedMotion ? 0 : 320
}
