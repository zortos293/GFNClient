import QtQuick
import OpenNOW

FocusScope {
    id: root
    width: 1440
    height: 900
    property string overlay: ""
    focus: visible

    readonly property bool menuVisible: overlay === "desktop-stream-menu"
    readonly property bool statsVisible: overlay === "desktop-stream-stats"
        || overlay === "desktop-stream-stats-expanded"

    function closeOverlay() {
        AppController.showOverlay("")
    }
    function cycleStats() {
        if (overlay === "desktop-stream-stats")
            AppController.showOverlay("desktop-stream-stats-expanded")
        else if (overlay === "desktop-stream-stats-expanded")
            AppController.showOverlay("")
        else
            AppController.showOverlay("desktop-stream-stats")
    }

    DesktopInStreamMenu {
        anchors.fill: parent
        visible: root.menuVisible
        focus: visible
        onResumeRequested: root.closeOverlay()
        onQualityRequested: {
            ShellStore.accessibilityMessage = qsTr("Opening live stream quality controls")
            AppController.showOverlay("quick-settings")
        }
        onInviteRequested: AppController.showOverlay("friends")
        onConsoleModeRequested: {
            ShellStore.setSetting("launchInConsoleMode", true)
            ShellStore.accessibilityMessage = qsTr("Console mode will be used at the next launch")
            root.closeOverlay()
        }
        onEndSessionRequested: {
            root.closeOverlay()
            ShellStore.stopStreamingSession()
        }
        onStatsRequested: AppController.showOverlay("desktop-stream-stats-expanded")
    }

    DesktopStreamStats {
        anchors.fill: parent
        visible: root.statsVisible
        focus: visible
        expanded: root.overlay === "desktop-stream-stats-expanded"
        onCycleRequested: root.cycleStats()
        onCloseRequested: root.closeOverlay()
        onCopyRequested: {
            ShellStore.accessibilityMessage = qsTr("Stream stats: %1, %2 ms ping, %3 Mbps")
                .arg(region).arg(ping).arg(bitrate)
        }
    }
}
