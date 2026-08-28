import QtQuick
import OpenNOW

FocusScope {
    id: root
    readonly property var game: ShellStore.selectedGame || ({ title: qsTr("GeForce NOW") })
    readonly property var session: ShellStore.activeSession || ({})
    readonly property var profile: session.negotiatedStreamProfile || ({})
    readonly property var streamer: ShellStore.streamer || ({ status: "starting", message: qsTr("Launching the native media runtime…") })
    readonly property bool streaming: streamer.status === "streaming"
    readonly property bool failed: streamer.status === "error"
    property double nowMs: Date.now()
    readonly property int elapsedSeconds: ShellStore.streamStartedAtMs > 0
        ? Math.max(0, Math.floor((nowMs - ShellStore.streamStartedAtMs) / 1000)) : 0
    readonly property int clockDuration: Math.max(1, Number(ShellStore.settings.sessionClockShowDurationSeconds || 30))
    readonly property int clockInterval: Math.max(0, Number(ShellStore.settings.sessionClockShowEveryMinutes || 0) * 60)
    readonly property bool sessionClockVisible: Boolean(ShellStore.settings.sessionCounterEnabled) && streaming
        && (elapsedSeconds < clockDuration
            || (clockInterval > 0 && elapsedSeconds % clockInterval < clockDuration))
    readonly property int antiAfkReminderDuration: Math.max(1, Number(ShellStore.settings.antiAfkReminderDurationSeconds || 5))
    readonly property int antiAfkReminderInterval: Math.max(0, Number(ShellStore.settings.antiAfkReminderEveryMinutes || 0) * 60)
    readonly property bool antiAfkReminderVisible: ShellStore.antiAfkEnabled && streaming
        && !Boolean(ShellStore.settings.showAntiAfkIndicator) && antiAfkReminderInterval > 0
        && elapsedSeconds % antiAfkReminderInterval < antiAfkReminderDuration

    function elapsed(value) {
        const hours = Math.floor(value / 3600)
        const minutes = Math.floor((value % 3600) / 60)
        const seconds = value % 60
        return (hours > 0 ? String(hours).padStart(2, "0") + ":" : "")
            + String(minutes).padStart(2, "0") + ":" + String(seconds).padStart(2, "0")
    }

    Timer { interval: 1000; repeat: true; running: root.streaming; onTriggered: root.nowMs = Date.now() }

    ScreenBackground {
        visible: !root.streaming
        artwork: root.game.heroImageUrl || root.game.imageUrl || ""
        tint: "#101B2A"
    }

    Rectangle {
        visible: !root.streaming
        anchors.fill: parent
        color: Qt.rgba(0.02, 0.04, 0.08, 0.38)
    }

    GlassPanel {
        visible: !root.streaming
        anchors.centerIn: parent
        width: Math.min(980, parent.width - 160)
        height: 500
        panelRadius: 44
        strong: true

        Column {
            anchors.fill: parent
            anchors.margins: 44
            spacing: 22

            Row {
                width: parent.width
                spacing: 18
                Rectangle {
                    width: 62; height: 62; radius: 31
                    color: root.failed ? Theme.coral : (root.streaming ? Theme.mint : Theme.focus)
                    Rectangle { anchors.centerIn: parent; width: 22; height: 22; radius: 11; color: Theme.shell }
                    SequentialAnimation on scale {
                        running: !AppController.reducedMotion
                        loops: Animation.Infinite
                        NumberAnimation { to: 1.08; duration: 700; easing.type: Easing.InOutSine }
                        NumberAnimation { to: 1.0; duration: 700; easing.type: Easing.InOutSine }
                    }
                }
                Column {
                    anchors.verticalCenter: parent.verticalCenter
                    spacing: 3
                    Text { text: root.failed ? qsTr("MEDIA STARTUP FAILED") : (root.streaming ? qsTr("STREAMING LIVE") : qsTr("CLOUD SEAT READY")); color: root.failed ? Theme.coral : (root.streaming ? Theme.mint : Theme.focus); font.family: Theme.bodyFont; font.pixelSize: 14; font.weight: Font.Black; font.letterSpacing: 1.4 }
                    Text { text: root.game.title; color: Theme.label; font.family: Theme.displayFont; font.pixelSize: 34; font.weight: Font.Black }
                }
            }

            Text {
                width: parent.width
                text: root.streamer.message || qsTr("GeForce NOW prepared the remote machine. OpenNOW is validating the negotiated media transport.")
                wrapMode: Text.WordWrap
                color: Theme.textMuted
                font.family: Theme.bodyFont
                font.pixelSize: 18
                lineHeight: 1.25
            }

            Row {
                spacing: 12
                Repeater {
                    model: [
                        root.profile.resolution || (ShellStore.settings.resolution || "1920x1080"),
                        String(root.profile.fps || ShellStore.settings.fps || 60) + " FPS",
                        root.profile.codec || String(ShellStore.settings.codec || "Auto").toUpperCase(),
                        root.session.gpuType || "GFN GPU"
                    ]
                    GlassPanel {
                        required property string modelData
                        width: 190; height: 58; panelRadius: 20; strong: true
                        Text { anchors.centerIn: parent; text: modelData; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 15; font.weight: Font.Bold }
                    }
                }
            }

            GlassPanel {
                width: parent.width; height: 82; panelRadius: 22
                Row {
                    anchors.fill: parent; anchors.margins: 18; spacing: 22
                    Column {
                        width: parent.width * 0.42
                        Text { text: qsTr("SESSION"); color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; font.weight: Font.Black; font.letterSpacing: 1.2 }
                        Text { width: parent.width; elide: Text.ElideMiddle; text: root.session.sessionId || "—"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 14 }
                    }
                    Column {
                        width: parent.width * 0.42
                        Text { text: qsTr("SERVER"); color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; font.weight: Font.Black; font.letterSpacing: 1.2 }
                        Text { width: parent.width; elide: Text.ElideMiddle; text: root.session.serverLocation || root.session.zone || qsTr("Automatic"); color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 14 }
                    }
                }
            }

            Row {
                anchors.horizontalCenter: parent.horizontalCenter
                spacing: 12
                GlassButton { width: 260; text: qsTr("Stop session"); glyph: "B"; onClicked: ShellStore.stopStreamingSession(); Component.onCompleted: forceActiveFocus() }
                GlassButton { width: 260; text: root.failed ? qsTr("Retry media") : qsTr("Session guide"); glyph: root.failed ? "↻" : "≡"; primary: true; onClicked: root.failed ? ShellStore.retryNativeStreamer() : AppController.showOverlay("guide-session") }
            }
        }
    }

    GlassPanel {
        visible: root.sessionClockVisible
        z: 12
        x: 34; y: 34; width: 190; height: 58; panelRadius: 22; strong: true
        Row {
            anchors.centerIn: parent; spacing: 10
            Text { text: "◷"; color: Theme.focus; font.pixelSize: 19; font.weight: Font.Black }
            Text { text: root.elapsed(root.elapsedSeconds); color: Theme.label; font.family: Theme.monoFont; font.pixelSize: 17; font.weight: Font.Bold }
        }
        opacity: visible ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: Theme.overlayDuration; easing.type: Easing.OutCubic } }
    }

    GlassPanel {
        visible: root.streaming && ShellStore.antiAfkEnabled
            && (Boolean(ShellStore.settings.showAntiAfkIndicator) || root.antiAfkReminderVisible)
        z: 12
        x: root.width - width - 34; y: 34; width: 180; height: 58; panelRadius: 22; strong: true
        Row {
            anchors.centerIn: parent; spacing: 10
            Rectangle { width: 10; height: 10; radius: 5; color: Theme.mint }
            Text { text: qsTr("ANTI-AFK ON"); color: Theme.label; font.family: Theme.monoFont; font.pixelSize: 13; font.weight: Font.Black; font.letterSpacing: 0.8 }
        }
        opacity: visible ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: Theme.overlayDuration; easing.type: Easing.OutCubic } }
    }

    Keys.onPressed: event => {
        if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
            event.accepted = true
            ShellStore.stopStreamingSession()
        }
    }

    AppChrome { visible: !root.streaming; anchors.fill: parent; title: qsTr("Live session"); currentRoute: "home"; bottomVisible: false }
}
