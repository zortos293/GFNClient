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
    function liveNumber(value) {
        return value === undefined || value === null || isNaN(Number(value)) ? null : Number(value)
    }
    function firstAvailable(primary, fallback) {
        return primary === undefined || primary === null ? fallback : primary
    }
    function liveValue(live, value) {
        return ["starting", "negotiating", "connecting", "streaming", "error"]
            .indexOf(String(live.status || "")) >= 0 ? value : undefined
    }
    function liveText(value) {
        const number = root.liveNumber(value)
        return number === null ? "—" : String(Math.round(number))
    }
    function copyStatsSummary() {
        const live = ShellStore.streamer || ({})
        const session = ShellStore.activeSession || ({})
        const region = String(session.regionName || session.region || session.serverRegionId || "").toUpperCase() || "—"
        const ping = root.liveText(root.firstAvailable(root.liveValue(live, live.pingMs), session.latencyMs))
        const bitrate = root.liveText(root.liveValue(live,
            root.firstAvailable(live.bitrateMbps, live.receiveBitrateMbps)))
        return qsTr("Stream stats: region %1, ping %2, bitrate %3")
            .arg(region)
            .arg(ping === "—" ? ping : ping + " ms")
            .arg(bitrate === "—" ? bitrate : bitrate + " Mbps")
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
        onInviteRequested: if (ShellStore.socialCapabilities
                && ShellStore.socialCapabilities.invitesAvailable)
            AppController.showOverlay("friends")
        onConsoleModeRequested: enabled => {
            ShellStore.requestConsoleSurface(enabled)
            ShellStore.accessibilityMessage = enabled
                ? qsTr("Switching to console mode") : qsTr("Switching to computer mode")
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
        onCopyRequested: ShellStore.accessibilityMessage = root.copyStatsSummary()
    }
}
