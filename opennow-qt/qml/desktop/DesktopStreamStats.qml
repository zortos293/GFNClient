pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Controls
import QtQuick.Effects
import OpenNOW

FocusScope {
    id: root
    width: 1440
    height: 900
    focus: true
    property bool expanded: false
    property double nowMs: Date.now()
    signal cycleRequested()
    signal copyRequested()
    signal closeRequested()

    readonly property var live: ShellStore.streamer || ({})
    readonly property var session: ShellStore.activeSession || ({})
    readonly property var profile: session.negotiatedStreamProfile || session.streamProfile || ({})
    readonly property bool liveTelemetryAvailable: ["starting", "negotiating", "connecting", "streaming", "error"]
        .indexOf(String(live.status || "")) >= 0

    function liveNumber(value) {
        return value === undefined || value === null || isNaN(Number(value)) ? null : Number(value)
    }
    function firstAvailable(primary, fallback) {
        return primary === undefined || primary === null ? fallback : primary
    }
    function liveValue(value) {
        return root.liveTelemetryAvailable ? value : undefined
    }
    function liveText(value) {
        const number = root.liveNumber(value)
        return number === null ? "—" : String(Math.round(number))
    }
    function liveFixed(value, decimals) {
        const number = root.liveNumber(value)
        return number === null ? "—" : number.toFixed(decimals)
    }
    function withUnit(text, unit) {
        return text === "—" ? "—" : text + unit
    }
    function secondaryText(value, suffix) {
        return value === "—" ? "" : value + suffix
    }

    readonly property string streamFps: root.liveText(root.liveValue(root.firstAvailable(live.framesPerSecond, live.fps)))
    readonly property string gameFps: root.liveText(root.firstAvailable(root.liveValue(live.gameFramesPerSecond), profile.fps))
    readonly property string latency: root.liveText(root.liveValue(root.firstAvailable(live.latencyMs, live.roundTripLatencyMs)))
    readonly property string latencyP99: root.liveText(root.liveValue(live.latencyP99Ms))
    readonly property string ping: root.liveText(root.firstAvailable(root.liveValue(live.pingMs), session.latencyMs))
    readonly property string jitter: root.liveFixed(root.liveValue(live.jitterMs), 1)
    readonly property string bitrate: root.liveText(root.liveValue(root.firstAvailable(live.bitrateMbps, live.receiveBitrateMbps)))
    readonly property string peakBitrate: root.liveText(root.liveValue(live.peakBitrateMbps))
    readonly property string loss: root.liveFixed(root.liveValue(live.packetLossPercent), 2)
    readonly property string dropped: root.liveText(root.liveValue(root.firstAvailable(live.droppedFrames, live.queueDropCount)))
    readonly property string firstFrame: root.liveText(root.liveValue(live.firstFrameLatencyMs))
    readonly property string decoderErrors: root.liveText(root.liveValue(live.decoderErrorCount))
    readonly property string outputErrors: root.liveText(root.liveValue(live.outputErrorCount))

    readonly property string region: String(session.regionName || session.region || session.serverRegionId || "").toUpperCase()
    readonly property string rig: String(session.rigName || session.gpuName || "").toUpperCase()
    readonly property string codec: String(root.liveValue(live.codec) || profile.codec || "").toUpperCase()
    readonly property string transport: String(root.liveValue(live.transport) || "").toUpperCase()
    readonly property string mediaBackend: String(root.liveValue(live.mediaBackend) || "").toUpperCase()
    readonly property int outputHeight: Number(profile.height || 0)
    readonly property int refreshRate: Number(profile.fps || profile.frameRate || 0)
    readonly property bool hdr: Boolean(profile.hdr)

    readonly property string outputText: (root.outputHeight > 0 ? root.outputHeight + "p" : "—")
        + " · " + (root.refreshRate > 0 ? root.refreshRate + " Hz" : "—")
        + (root.hdr ? " · HDR" : "")

    function elapsedText() {
        if (ShellStore.streamStartedAtMs <= 0)
            return "—"
        const total = Math.floor(Math.max(0, nowMs - ShellStore.streamStartedAtMs) / 1000)
        const h = Math.floor(total / 3600)
        const m = Math.floor((total % 3600) / 60)
        const s = total % 60
        return h + ":" + String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0")
    }

    function metricRows() {
        return [
            { label: "FPS", main: root.streamFps, secondary: root.secondaryText(root.gameFps, " game") },
            { label: qsTr("LATENCY"), main: root.withUnit(root.latency, " ms"), secondary: root.secondaryText(root.latencyP99, " ms p99") },
            { label: qsTr("PING"), main: root.withUnit(root.ping, " ms"), secondary: root.secondaryText(root.jitter, " ms jitter") },
            { label: qsTr("BITRATE"), main: root.withUnit(root.bitrate, " Mbps"), secondary: root.secondaryText(root.peakBitrate, " Mbps peak") },
            { label: qsTr("LOSS"), main: root.withUnit(root.loss, "%"), secondary: root.secondaryText(root.dropped, " dropped") }
        ]
    }

    Rectangle {
        id: compactPill
        anchors.top: parent.top
        anchors.topMargin: 24
        anchors.right: parent.right
        anchors.rightMargin: 24
        width: compactRow.implicitWidth + 24
        height: 34
        radius: 17
        visible: !root.expanded
        color: "#BF0B0F1A"
        border.width: 1
        border.color: DesktopTokens.seam
        opacity: root.expanded ? 0 : 1
        scale: compactTap.pressed && !AppController.reducedMotion ? 0.985 : 1
        Behavior on opacity { NumberAnimation { duration: DesktopTokens.quickDuration } }
        Behavior on scale { NumberAnimation { duration: DesktopTokens.quickDuration; easing.type: Easing.OutCubic } }
        Row {
            id: compactRow
            anchors.centerIn: parent
            spacing: 10
            Rectangle { width: 6; height: 6; radius: 3; anchors.verticalCenter: parent.verticalCenter; color: DesktopTokens.green }
            Row {
                spacing: 3
                Text { text: root.ping; color: DesktopTokens.green; font.family: DesktopTokens.monoFont; font.pixelSize: 13; font.weight: Font.Bold }
                Text { anchors.baseline: parent.children[0].baseline; visible: root.ping !== "—"; text: "ms"; color: DesktopTokens.textMuted; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold }
            }
            Rectangle { width: 1; height: 14; anchors.verticalCenter: parent.verticalCenter; color: "#24FFFFFF" }
            Text { anchors.verticalCenter: parent.verticalCenter; text: root.region.length ? root.region : "—"; color: DesktopTokens.textHigh; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Bold; font.letterSpacing: 0.5 }
            Rectangle { width: 1; height: 14; anchors.verticalCenter: parent.verticalCenter; color: "#24FFFFFF" }
            Text { anchors.verticalCenter: parent.verticalCenter; text: root.codec.length ? root.codec : "—"; color: DesktopTokens.focus; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Bold }
            Rectangle { width: 1; height: 14; anchors.verticalCenter: parent.verticalCenter; color: "#24FFFFFF" }
            Text { anchors.verticalCenter: parent.verticalCenter; text: root.withUnit(root.bitrate, " Mbps"); color: DesktopTokens.textHigh; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Bold }
        }
        HoverHandler { cursorShape: Qt.PointingHandCursor }
        TapHandler { id: compactTap; onTapped: root.cycleRequested() }
    }

    MultiEffect {
        anchors.fill: expandedPanel
        source: expandedPanel
        visible: false
        shadowEnabled: true
        shadowColor: "#CC000000"
        shadowOpacity: 0.8
        shadowBlur: 1
        shadowVerticalOffset: 12
    }

    Rectangle {
        id: expandedPanel
        anchors.top: parent.top
        anchors.topMargin: 24
        anchors.right: parent.right
        anchors.rightMargin: 24
        width: 292
        height: 390
        radius: 18
        visible: root.expanded
        color: "#F00B0F1A"
        border.width: 1
        border.color: DesktopTokens.seam
        opacity: root.expanded ? 1 : 0
        scale: root.expanded || AppController.reducedMotion ? 1 : 0.98
        Behavior on opacity { NumberAnimation { duration: AppController.reducedMotion ? 0 : 140; easing.type: Easing.OutCubic } }
        Behavior on scale { NumberAnimation { duration: AppController.reducedMotion ? 0 : 140; easing.type: Easing.OutCubic } }

        Item {
            x: 12
            y: 12
            width: parent.width - 24
            height: 25
            Rectangle { x: 0; anchors.verticalCenter: parent.verticalCenter; width: 6; height: 6; radius: 3; color: DesktopTokens.green }
            Text {
                x: 13
                anchors.verticalCenter: parent.verticalCenter
                width: 165
                text: (root.region.length ? root.region : "—") + " · " + (root.rig.length ? root.rig : "—")
                color: DesktopTokens.textHigh
                font.family: DesktopTokens.monoFont
                font.pixelSize: 10
                font.weight: Font.Bold
                font.letterSpacing: 0.35
                elide: Text.ElideRight
            }
            Text {
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                text: root.elapsedText()
                color: DesktopTokens.textMuted
                font.family: DesktopTokens.monoFont
                font.pixelSize: 9
                font.weight: Font.DemiBold
            }
        }
        Rectangle { x: 12; y: 43; width: parent.width - 24; height: 1; color: "#17FFFFFF" }

        Column {
            x: 12
            y: 51
            width: parent.width - 24
            spacing: 0
            Repeater {
                model: root.metricRows()
                delegate: Item {
                    id: streamRow
                    required property var modelData
                    width: parent.width
                    height: 24
                    Text { width: 64; anchors.verticalCenter: parent.verticalCenter; text: streamRow.modelData.label; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 0.6 }
                    Text { x: 66; width: 100; anchors.verticalCenter: parent.verticalCenter; horizontalAlignment: Text.AlignRight; text: streamRow.modelData.main; color: DesktopTokens.textHigh; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Bold }
                    Text { anchors.right: parent.right; width: 88; anchors.verticalCenter: parent.verticalCenter; horizontalAlignment: Text.AlignRight; visible: streamRow.modelData.secondary.length > 0; text: streamRow.modelData.secondary; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.DemiBold }
                }
            }
        }

        Rectangle { x: 12; y: 174; width: parent.width - 24; height: 1; color: "#17FFFFFF" }
        Column {
            x: 12
            y: 182
            width: parent.width - 24
            spacing: 0
            Repeater {
                model: [
                    { label: qsTr("CODEC"), main: root.codec.length ? root.codec : "—" },
                    { label: qsTr("OUTPUT"), main: root.outputText }
                ]
                delegate: Item {
                    id: videoRow
                    required property var modelData
                    width: parent.width
                    height: 24
                    Text { width: 64; anchors.verticalCenter: parent.verticalCenter; text: videoRow.modelData.label; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 0.6 }
                    Text { x: 66; width: 180; anchors.verticalCenter: parent.verticalCenter; horizontalAlignment: Text.AlignRight; text: videoRow.modelData.main; color: DesktopTokens.focus; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Bold; elide: Text.ElideRight }
                }
            }
        }

        Rectangle { x: 12; y: 240; width: parent.width - 24; height: 1; color: "#17FFFFFF" }
        Item {
            x: 12
            y: 250
            width: parent.width - 24
            height: 40
            Text { text: qsTr("TRANSPORT"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 0.6 }
            Text {
                y: 14
                width: parent.width
                text: (root.transport.length ? root.transport : "—")
                    + (root.mediaBackend.length ? " · " + root.mediaBackend : "")
                color: DesktopTokens.textHigh
                font.family: DesktopTokens.monoFont
                font.pixelSize: 10
                font.weight: Font.Bold
                elide: Text.ElideRight
            }
        }
        Item {
            x: 12
            y: 296
            width: parent.width - 24
            height: 40
            Text { text: qsTr("DIAGNOSTICS"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 0.6 }
            Text {
                y: 14
                width: parent.width
                text: qsTr("First frame %1 · decoder errors %2 · output errors %3")
                    .arg(root.withUnit(root.firstFrame, " ms")).arg(root.decoderErrors).arg(root.outputErrors)
                color: DesktopTokens.textHigh
                font.family: DesktopTokens.monoFont
                font.pixelSize: 10
                font.weight: Font.Bold
                elide: Text.ElideRight
            }
        }
        Rectangle { x: 12; y: 356; width: parent.width - 24; height: 1; color: "#17FFFFFF" }
        Text { x: 12; y: 369; text: qsTr("F3 · PS · XB — CYCLE"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 7; font.weight: Font.Bold; font.letterSpacing: 0.35 }
        Text { anchors.right: parent.right; anchors.rightMargin: 12; y: 369; text: qsTr("SHIFT+F3 COPY"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 7; font.weight: Font.Bold; font.letterSpacing: 0.35 }
        HoverHandler { cursorShape: Qt.PointingHandCursor }
        TapHandler { onTapped: root.cycleRequested() }
    }

    Timer { interval: 1000; repeat: true; running: root.visible; onTriggered: root.nowMs = Date.now() }
    Keys.onPressed: event => {
        if (event.key === Qt.Key_F3 && (event.modifiers & Qt.ShiftModifier)) {
            root.copyRequested()
            event.accepted = true
        } else if (event.key === Qt.Key_F3) {
            root.cycleRequested()
            event.accepted = true
        } else if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
            root.closeRequested()
            event.accepted = true
        }
    }
}
