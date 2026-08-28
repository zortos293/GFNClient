import QtQuick
import OpenNOW

GlassPanel {
    id: root
    width: 520; height: 770; panelRadius: 34; strong: true
    focus: visible
    readonly property var bitrates: [25, 50, 75, 100, 150, 200]
    onVisibleChanged: if (visible) regionRow.forceActiveFocus()
    Component.onCompleted: if (visible) Qt.callLater(regionRow.forceActiveFocus)

    function nextBitrate() {
        const current = Number(ShellStore.settings.maxBitrateMbps || 75)
        const index = root.bitrates.indexOf(current)
        ShellStore.setSetting("maxBitrateMbps", root.bitrates[(index + 1) % root.bitrates.length])
    }

    Column {
        anchors.fill: parent; anchors.margins: 23; spacing: 4
        Row {
            width: parent.width; height: 48
            Text { text: qsTr("Quick settings"); color: Theme.label; font.family: Theme.displayFont; font.pixelSize: 24; font.weight: Font.Black }
            Item { width: parent.width - 260; height: 1 }
            GlassPanel {
                width: 84; height: 32; panelRadius: 16
                Text { anchors.centerIn: parent; text: ShellStore.activeSession ? (ShellStore.activeSession.gpuType || "GFN") : qsTr("READY"); color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 12; font.weight: Font.Bold }
            }
        }
        SettingRow {
            id: regionRow; width: parent.width; title: qsTr("Region")
            value: ShellStore.settings.region || qsTr("Automatic")
            description: qsTr("Open the complete network region selector")
            KeyNavigation.down: bitrateRow
            onClicked: { AppController.showOverlay(""); AppController.navigate("settings-network") }
        }
        SettingRow {
            id: bitrateRow; width: parent.width; title: qsTr("Max bitrate")
            value: (ShellStore.settings.maxBitrateMbps || 75) + " Mbps"
            description: qsTr("Cycles through safe bandwidth caps")
            KeyNavigation.up: regionRow; KeyNavigation.down: statsRow
            onClicked: root.nextBitrate()
        }
        SettingRow {
            id: statsRow; width: parent.width; title: ShellStore.activeSession ? qsTr("Stats overlay") : qsTr("Stats on launch")
            value: ShellStore.activeSession ? qsTr("Toggle") : (ShellStore.settings.showNativeStreamerStats ? qsTr("On") : qsTr("Off"))
            description: ShellStore.activeSession ? qsTr("Toggles live native stream statistics") : qsTr("Starts the next native session with compact stats")
            KeyNavigation.up: bitrateRow; KeyNavigation.down: recordingRow
            onClicked: {
                if (ShellStore.activeSession)
                    ShellStore.controlStream("toggle-stats")
                else
                    ShellStore.setSetting("showNativeStreamerStats", !Boolean(ShellStore.settings.showNativeStreamerStats))
            }
        }
        SettingRow {
            id: recordingRow; width: parent.width; title: qsTr("Recording")
            value: ShellStore.streamRecordingActive ? qsTr("Recording") : qsTr("Off")
            description: ShellStore.streamRecordingActive
                ? qsTr("%1 seconds · source video + audio").arg(Math.floor(ShellStore.streamRecordingElapsedMs / 1000))
                : qsTr("Source-quality video + stream audio · %1").arg(ShellStore.settings.shortcutToggleRecording || "F12")
            KeyNavigation.up: statsRow; KeyNavigation.down: syncRow
            onClicked: ShellStore.toggleStreamRecording()
        }
        SettingRow {
            id: syncRow; width: parent.width; title: qsTr("Cloud G-Sync")
            value: ShellStore.settings.enableCloudGsync ? qsTr("On") : qsTr("Off")
            KeyNavigation.up: recordingRow; KeyNavigation.down: micRow
            onClicked: ShellStore.setSetting("enableCloudGsync", !Boolean(ShellStore.settings.enableCloudGsync))
        }
        SettingRow {
            id: micRow; width: parent.width; title: qsTr("Microphone")
            value: ShellStore.microphoneLabel
            description: ShellStore.microphoneDescription
            KeyNavigation.up: syncRow; KeyNavigation.down: controllerButton
            onClicked: ShellStore.toggleMicrophoneMode()
        }
        Text { text: qsTr("CONTROLLERS · %1 CONNECTED").arg(AppController.controllerCount); color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 12; font.letterSpacing: 1; topPadding: 12; bottomPadding: 8 }
        GlassButton {
            id: controllerButton
            width: parent.width
            text: AppController.controllerCount >= 2 ? qsTr("Manage local players") : qsTr("Connect player two")
            glyph: "A"
            KeyNavigation.up: micRow
            onClicked: { AppController.showOverlay(""); AppController.navigate("joining") }
        }
        HintBar { anchors.horizontalCenter: parent.horizontalCenter; hints: [{glyph:"A",label:qsTr("Change")},{glyph:"B",label:qsTr("Close")}] }
    }
}
