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
    readonly property int streamFps: Math.round(Number(live.framesPerSecond || live.fps || 118))
    readonly property int gameFps: Math.round(Number(live.gameFramesPerSecond || profile.fps || 120))
    readonly property int latency: Math.round(Number(live.latencyMs || live.roundTripLatencyMs || 18))
    readonly property int latencyP99: Math.round(Number(live.latencyP99Ms || 24))
    readonly property int ping: Math.round(Number(live.pingMs || session.latencyMs || 9))
    readonly property real jitter: Number(live.jitterMs || 1.2)
    readonly property int bitrate: Math.round(Number(live.bitrateMbps || live.receiveBitrateMbps || 72))
    readonly property int peakBitrate: Math.round(Number(live.peakBitrateMbps || 94))
    readonly property real loss: Number(live.packetLossPercent || 0)
    readonly property int dropped: Math.round(Number(live.droppedFrames || live.queueDropCount || 3))
    readonly property string region: String(session.regionName || session.region || session.serverRegionId || ShellStore.settings.region || "").toUpperCase()
    readonly property string rig: String(session.rigName || session.gpuName || "").toUpperCase()
    readonly property string codec: String(live.codec || profile.codec || ShellStore.settings.codec || "AV1").toUpperCase()
    readonly property int outputHeight: Number(profile.height || ShellStore.settings.resolutionHeight || 1440)
    readonly property int refreshRate: Number(profile.fps || profile.frameRate || ShellStore.settings.frameRate || 120)
    readonly property bool hdr: Boolean(profile.hdr || ShellStore.settings.hdr)
    readonly property var frameBars: [8,10,7,11,9,12,8,9,13,10,8,12,9,11,8,14,9,10,8,11,9,12,10,9]
    readonly property var latencyBars: [7,8,7,9,8,10,9,8,11,9,8,10,12,9,8,11,10,9,13,10,9,11,10,8]

    function elapsedText() {
        const elapsed = ShellStore.streamStartedAtMs > 0 ? Math.max(0, nowMs - ShellStore.streamStartedAtMs) : 6130000
        const total = Math.floor(elapsed / 1000)
        const h = Math.floor(total / 3600)
        const m = Math.floor((total % 3600) / 60)
        const s = total % 60
        return h + ":" + String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0")
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
                Text { anchors.baseline: parent.children[0].baseline; text: "ms"; color: DesktopTokens.textMuted; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold }
            }
            Rectangle { width: 1; height: 14; anchors.verticalCenter: parent.verticalCenter; color: "#24FFFFFF" }
            Text { anchors.verticalCenter: parent.verticalCenter; text: root.region; color: DesktopTokens.textHigh; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Bold; font.letterSpacing: 0.5 }
            Rectangle { width: 1; height: 14; anchors.verticalCenter: parent.verticalCenter; color: "#24FFFFFF" }
            Text { anchors.verticalCenter: parent.verticalCenter; text: root.codec; color: DesktopTokens.focus; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Bold }
            Rectangle { width: 1; height: 14; anchors.verticalCenter: parent.verticalCenter; color: "#24FFFFFF" }
            Text { anchors.verticalCenter: parent.verticalCenter; text: root.bitrate + " Mbps"; color: DesktopTokens.textHigh; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Bold }
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
                text: root.region + " · " + root.rig
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
                model: [
                    { label: "FPS", main: root.streamFps, secondary: root.gameFps + " game" },
                    { label: qsTr("LATENCY"), main: root.latency + " ms", secondary: root.latencyP99 + " ms p99" },
                    { label: qsTr("PING"), main: root.ping + " ms", secondary: root.jitter.toFixed(1) + " ms jitter" },
                    { label: qsTr("BITRATE"), main: root.bitrate + " Mbps", secondary: root.peakBitrate + " Mbps peak" },
                    { label: qsTr("LOSS"), main: root.loss.toFixed(2) + "%", secondary: root.dropped + " dropped" }
                ]
                delegate: Item {
                    id: streamRow
                    required property var modelData
                    width: parent.width
                    height: 24
                    Text { width: 64; anchors.verticalCenter: parent.verticalCenter; text: streamRow.modelData.label; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 0.6 }
                    Text { x: 66; width: 100; anchors.verticalCenter: parent.verticalCenter; horizontalAlignment: Text.AlignRight; text: streamRow.modelData.main; color: DesktopTokens.textHigh; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Bold }
                    Text { anchors.right: parent.right; width: 88; anchors.verticalCenter: parent.verticalCenter; horizontalAlignment: Text.AlignRight; text: streamRow.modelData.secondary; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.DemiBold }
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
                    { label: qsTr("CODEC"), main: root.codec + " 10-bit", secondary: qsTr("NVDEC hw") },
                    { label: qsTr("OUTPUT"), main: root.outputHeight + "p", secondary: root.refreshRate + " Hz" + (root.hdr ? " · HDR" : "") }
                ]
                delegate: Item {
                    id: videoRow
                    required property var modelData
                    width: parent.width
                    height: 24
                    Text { width: 64; anchors.verticalCenter: parent.verticalCenter; text: videoRow.modelData.label; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 0.6 }
                    Text { x: 66; width: 100; anchors.verticalCenter: parent.verticalCenter; horizontalAlignment: Text.AlignRight; text: videoRow.modelData.main; color: DesktopTokens.focus; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Bold }
                    Text { anchors.right: parent.right; width: 88; anchors.verticalCenter: parent.verticalCenter; horizontalAlignment: Text.AlignRight; text: videoRow.modelData.secondary; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.DemiBold }
                }
            }
        }

        Item {
            x: 12
            y: 240
            width: parent.width - 24
            height: 55
            Text { text: qsTr("FRAME TIME"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 0.6 }
            Row {
                y: 15
                height: 24
                spacing: 3
                Repeater {
                    model: root.frameBars
                    delegate: Rectangle {
                        required property int modelData
                        anchors.bottom: parent.bottom
                        width: 8
                        height: Math.max(3, modelData)
                        radius: 1.5
                        color: modelData > 12 ? DesktopTokens.amber : "#667FD4FF"
                    }
                }
            }
            Text { x: 0; y: 43; text: qsTr("min 6.9"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 7 }
            Text { anchors.right: parent.right; y: 43; text: qsTr("max 21.4 ms"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 7 }
        }
        Item {
            x: 12
            y: 302
            width: parent.width - 24
            height: 45
            Text { text: qsTr("LATENCY"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 0.6 }
            Row {
                y: 15
                height: 19
                spacing: 3
                Repeater {
                    model: root.latencyBars
                    delegate: Rectangle {
                        required property int modelData
                        anchors.bottom: parent.bottom
                        width: 8
                        height: Math.max(3, modelData)
                        radius: 1.5
                        color: "#6656E6A5"
                    }
                }
            }
            Text { x: 0; y: 36; text: qsTr("min 15"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 7 }
            Text { anchors.right: parent.right; y: 36; text: qsTr("max 24 ms"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 7 }
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
