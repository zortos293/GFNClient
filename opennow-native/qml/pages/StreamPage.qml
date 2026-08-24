import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page

    required property var game
    signal exit()
    signal screenshotRequested()
    signal recordingToggled(bool active)
    signal microphoneChanged(bool enabled)
    signal antiAfkChanged(bool enabled)
    signal reconnectRequested(int attempt)
    signal reportExported(string path)

    property bool hudVisible: statsMode > 0
    property bool menuVisible: false
    property string lifecycle: "preparing"
    property string stateBeforeConfirmation: "live"
    property bool hasStreamed: false
    property bool endingSession: false
    property bool qualityChangePending: false
    property bool sessionRecorded: false

    property int preparingSeconds: 0
    property int sessionElapsedSeconds: 0
    property double sessionStartedAt: 0
    property int sessionLimitSeconds: Number(appState.preference("streamSessionLimitSeconds", 28800))
    property int statsMode: Math.max(0, Math.min(2, Number(appState.preference("streamStatsMode", 1))))
    property int recoveryAttempt: 0
    property int recoveryElapsedSeconds: 0
    property int recoveryCount: 0
    property int holdProgressPercent: 0

    property string qualityId: String(appState.preference("streamQuality", "1080p120"))
    property string upscalingMode: String(appState.preference("streamUpscaling", "temporal"))
    property int upscalingSharpness: Number(appState.preference("streamUpscalingSharpness", 62))
    property string frameGenerationMode: String(appState.preference("streamFrameGeneration", "2x"))
    property int bitrateCapMbps: Number(appState.preference("streamBitrateCapMbps", 75))
    property bool antiAfkEnabled: Boolean(appState.preference("streamAntiAfk", true))
    property bool microphoneEnabled: Boolean(appState.preference("streamMicrophone", true))
    property bool recording: false
    property int recordingSeconds: 0

    property int statsSamples: 0
    property real totalFps: 0
    property real totalLatency: 0
    property real totalPacketLoss: 0
    property bool oneHourToastShown: false
    property bool fifteenMinuteToastShown: false
    property bool weakNetworkToastShown: false
    property bool updateToastShown: false
    property bool previousControllerConnected: controllerInput.connected

    property string toastTitle: ""
    property string toastDetail: ""
    property string toastTone: "success"
    property string toastIcon: "✓"
    property string toastAction: ""
    property bool toastVisible: false
    property string exportStatus: ""

    readonly property bool liveSurfaceVisible: lifecycle === "live"
                                              || lifecycle === "end-confirm"
                                              || lifecycle === "recovery"
    readonly property int remainingSeconds: Math.max(0, sessionLimitSeconds - sessionElapsedSeconds)
    readonly property string elapsedText: formatClock(sessionElapsedSeconds)
    readonly property string remainingText: formatRemaining(remainingSeconds)
    readonly property string displayResolution: streamEngine.resolution && streamEngine.resolution.length > 0
                                                ? streamEngine.resolution.replace(" × ", "x")
                                                : "—"
    readonly property string displayCodec: streamEngine.codec && streamEngine.codec.length > 0
                                           ? streamEngine.codec.toUpperCase()
                                           : "—"
    readonly property int averageFps: statsSamples > 0 ? Math.round(totalFps / statsSamples) : streamEngine.fps
    readonly property int averageLatency: statsSamples > 0 ? Math.round(totalLatency / statsSamples) : streamEngine.latencyMs
    readonly property real averagePacketLoss: statsSamples > 0 ? totalPacketLoss / statsSamples : streamEngine.packetLoss
    readonly property string reportRating: recoveryCount === 0 && averagePacketLoss < 1.0
                                           ? "Smooth"
                                           : recoveryCount <= 1 && averagePacketLoss < 2.5
                                             ? "Good"
                                             : "Unstable"

    function formatClock(totalSeconds) {
        var hours = Math.floor(totalSeconds / 3600)
        var minutes = Math.floor((totalSeconds % 3600) / 60)
        var seconds = totalSeconds % 60
        return (hours > 0 ? String(hours).padStart(2, "0") + ":" : "")
               + String(minutes).padStart(2, "0") + ":"
               + String(seconds).padStart(2, "0")
    }

    function formatDuration(totalSeconds) {
        var hours = Math.floor(totalSeconds / 3600)
        var minutes = Math.floor((totalSeconds % 3600) / 60)
        if (hours > 0)
            return hours + " h " + minutes + " m"
        return Math.max(1, minutes) + " min"
    }

    function formatRemaining(totalSeconds) {
        var hours = Math.floor(totalSeconds / 3600)
        var minutes = Math.floor((totalSeconds % 3600) / 60)
        return hours > 0 ? hours + " h " + minutes + " m left" : minutes + " min left"
    }

    function qualityLabel() {
        if (qualityId === "4k60")
            return "3840x2160@60"
        if (qualityId === "1440p120")
            return "2560x1440@120"
        if (qualityId === "1080p120")
            return "1920x1080@120"
        if (qualityId === "1080p60")
            return "1920x1080@60"
        return "1280x720@60"
    }

    function showToast(title, detail, tone, icon, action) {
        toastTitle = title
        toastDetail = detail || ""
        toastTone = tone || "success"
        toastIcon = icon || "✓"
        toastAction = action || ""
        toastVisible = true
        toastTimer.restart()
    }

    function resetSessionUi() {
        lifecycle = streamEngine.phase === "streaming" ? "live" : "preparing"
        hasStreamed = streamEngine.phase === "streaming"
        endingSession = false
        sessionRecorded = false
        qualityChangePending = false
        menuVisible = false
        preparingSeconds = 0
        sessionElapsedSeconds = 0
        sessionStartedAt = Date.now()
        recoveryAttempt = 0
        recoveryElapsedSeconds = 0
        recoveryCount = 0
        holdProgressPercent = 0
        recording = false
        recordingSeconds = 0
        statsSamples = 0
        totalFps = 0
        totalLatency = 0
        totalPacketLoss = 0
        oneHourToastShown = false
        fifteenMinuteToastShown = false
        weakNetworkToastShown = false
        updateToastShown = false
        exportStatus = ""
        qualitySamples.clear()
        qualitySamples.append({ value: Math.max(20, streamEngine.fps), tone: "good" })
        forceActiveFocus()
    }

    function cycleStats() {
        statsMode = (statsMode + 1) % 3
        appState.setPreference("streamStatsMode", statsMode)
    }

    function captureScreenshot() {
        screenshotRequested()
        var count = Number(appState.preference("streamScreenshotCount", 0)) + 1
        appState.setPreference("streamScreenshotCount", count)
        showToast("Screenshot saved", "Pictures › OpenNOW", "success", "▣", "")
    }

    function toggleRecording() {
        recording = !recording
        if (recording)
            recordingSeconds = 0
        recordingToggled(recording)
        showToast(recording ? "Recording started" : "Recording saved",
                  recording ? "Press F12 to stop" : "Videos › OpenNOW",
                  recording ? "recording" : "success", "●", "")
    }

    function toggleMicrophone() {
        microphoneEnabled = !microphoneEnabled
        appState.setPreference("streamMicrophone", microphoneEnabled)
        microphoneChanged(microphoneEnabled)
        showToast(microphoneEnabled ? "Microphone live" : "Microphone muted",
                  microphoneEnabled ? "Voice is being sent to the rig" : "Voice input is paused",
                  microphoneEnabled ? "success" : "neutral", microphoneEnabled ? "♩" : "×", "")
    }

    function toggleAntiAfk() {
        antiAfkEnabled = !antiAfkEnabled
        appState.setPreference("streamAntiAfk", antiAfkEnabled)
        antiAfkChanged(antiAfkEnabled)
        showToast(antiAfkEnabled ? "Anti-AFK enabled" : "Anti-AFK disabled",
                  antiAfkEnabled ? "Simulates input every 15 minutes" : "No synthetic input will be sent",
                  antiAfkEnabled ? "success" : "neutral", "•", "")
    }

    function applyBitrateCap(value) {
        bitrateCapMbps = Math.round(value)
        appState.setPreference("streamBitrateCapMbps", bitrateCapMbps)
        streamEngine.setBitrate(bitrateCapMbps * 1000)
    }

    function openEndConfirmation() {
        if (lifecycle === "report")
            return
        menuVisible = false
        stateBeforeConfirmation = lifecycle === "recovery" ? "recovery" : "live"
        lifecycle = "end-confirm"
        holdProgressPercent = 0
        Qt.callLater(function() { keepPlayingButton.forceActiveFocus() })
    }

    function cancelEndConfirmation() {
        holdTimer.stop()
        holdProgressPercent = 0
        lifecycle = streamEngine.phase === "streaming" ? "live" : stateBeforeConfirmation
        forceActiveFocus()
    }

    function finishSession(reason) {
        holdTimer.stop()
        endingSession = true
        menuVisible = false
        recording = false
        if (hasStreamed && !sessionRecorded) {
            sessionRecorded = true
            appState.recordSession({
                title: String(game.title || "Unknown game"),
                startedAt: new Date(sessionStartedAt).toISOString(),
                durationMinutes: Math.max(1, Math.ceil(sessionElapsedSeconds / 60)),
                region: appState.serverRegion,
                latencyMs: averageLatency,
                averageFps: averageFps,
                packetLoss: averagePacketLoss,
                disconnects: recoveryCount,
                rating: reportRating
            })
        }
        streamEngine.stop()
        lifecycle = "report"
        exportStatus = reason || ""
        if (qualitySamples.count < 2)
            qualitySamples.append({ value: Math.max(20, averageFps), tone: "good" })
        Qt.callLater(function() { doneButton.forceActiveFocus() })
    }

    function beginRecovery() {
        if (endingSession || lifecycle === "report" || lifecycle === "recovery")
            return
        menuVisible = false
        lifecycle = "recovery"
        recoveryAttempt = 0
        recoveryElapsedSeconds = 0
        recoveryCount += 1
        attemptRecovery()
    }

    function attemptRecovery() {
        if (lifecycle !== "recovery" || recoveryAttempt >= 5)
            return
        recoveryAttempt += 1
        reconnectRequested(recoveryAttempt)
        streamEngine.startDemo(qualityId)
    }

    function exportReport() {
        var path = appState.exportSessions()
        exportStatus = path && path.length > 0 ? "Exported to " + path : "The report could not be exported"
        reportExported(path)
    }

    onVisibleChanged: {
        if (visible)
            resetSessionUi()
        else {
            toastVisible = false
            menuVisible = false
            holdTimer.stop()
        }
    }

    Component.onCompleted: {
        if (visible)
            resetSessionUi()
    }

    Keys.priority: Keys.BeforeItem
    Keys.onPressed: function(event) {
        if (!page.visible)
            return
        if (event.key === Qt.Key_Escape) {
            event.accepted = true
            if (menuVisible) {
                menuVisible = false
                forceActiveFocus()
            } else if (lifecycle === "end-confirm") {
                cancelEndConfirmation()
            } else if (lifecycle === "recovery") {
                finishSession("Connection lost")
            } else if (lifecycle === "report") {
                page.exit()
            } else if (hasStreamed) {
                openEndConfirmation()
            } else {
                finishSession("Launch cancelled")
            }
        }
    }

    ListModel { id: qualitySamples }

    Timer {
        id: toastTimer
        interval: 4300
        onTriggered: page.toastVisible = false
    }

    Timer {
        id: bitrateApplyTimer
        interval: 350
        onTriggered: page.applyBitrateCap(page.bitrateCapMbps)
    }

    Timer {
        id: holdTimer
        interval: 40
        repeat: true
        onTriggered: {
            page.holdProgressPercent = Math.min(100, page.holdProgressPercent + 4)
            if (page.holdProgressPercent >= 100)
                page.finishSession("")
        }
    }

    Timer {
        id: secondTimer
        interval: 1000
        repeat: true
        running: page.visible
        onTriggered: {
            if (page.lifecycle === "preparing") {
                page.preparingSeconds += 1
                progressCanvas.requestPaint()
            }

            if (page.hasStreamed && !page.endingSession && page.lifecycle !== "report") {
                page.sessionElapsedSeconds += 1
                if (page.recording)
                    page.recordingSeconds += 1

                if (page.lifecycle === "live") {
                    page.statsSamples += 1
                    page.totalFps += streamEngine.fps
                    page.totalLatency += streamEngine.latencyMs
                    page.totalPacketLoss += streamEngine.packetLoss
                    if (page.sessionElapsedSeconds % 12 === 0) {
                        var sampleTone = streamEngine.packetLoss >= 1.0 ? "warn" : "good"
                        qualitySamples.append({ value: Math.max(4, streamEngine.fps), tone: sampleTone })
                        if (qualitySamples.count > 10)
                            qualitySamples.remove(0)
                    }
                }

                if (!page.oneHourToastShown && page.sessionElapsedSeconds >= 3600) {
                    page.oneHourToastShown = true
                    page.showToast("One hour played", page.remainingText + " on this session", "success", "◷", "")
                }
                if (!page.fifteenMinuteToastShown && page.remainingSeconds <= 900) {
                    page.fifteenMinuteToastShown = true
                    page.showToast("15 minutes left", "Save your game — the session ends at "
                                   + Math.round(page.sessionLimitSeconds / 3600) + " hours", "warning", "◷", "")
                }
                if (!page.weakNetworkToastShown && streamEngine.packetLoss >= 1.0) {
                    page.weakNetworkToastShown = true
                    page.showToast("Weak network", "Adaptive bitrate is protecting stream stability", "warning", "▥", "")
                }
            }

            if (page.lifecycle === "recovery") {
                page.recoveryElapsedSeconds += 1
                if (page.recoveryElapsedSeconds >= 90) {
                    page.finishSession("Connection could not be restored")
                } else if (page.recoveryElapsedSeconds % 18 === 0 && page.recoveryAttempt < 5) {
                    page.attemptRecovery()
                }
            }

            if (!page.updateToastShown
                    && page.lifecycle === "live"
                    && Boolean(appState.preference("updateReady", false))) {
                page.updateToastShown = true
                page.showToast("Update ready",
                               "OpenNOW " + String(appState.preference("updateVersion", "next")) + " installs on restart",
                               "success", "↓", "Restart")
            }
        }
    }

    Connections {
        target: streamEngine

        function onPhaseChanged() {
            var phase = streamEngine.phase
            if (phase === "streaming") {
                var recovered = page.lifecycle === "recovery"
                page.hasStreamed = true
                page.lifecycle = "live"
                page.qualityChangePending = false
                if (recovered)
                    page.showToast("Picture recovered", "Decoder reset — " + page.recoveryElapsedSeconds + " s freeze", "success", "✓", "")
                if (page.sessionStartedAt === 0)
                    page.sessionStartedAt = Date.now()
                page.forceActiveFocus()
            } else if (phase === "error") {
                if (!page.endingSession)
                    page.beginRecovery()
            } else if (phase === "idle") {
                if (page.hasStreamed && !page.endingSession && !page.qualityChangePending)
                    page.beginRecovery()
            } else if (phase === "connecting" && !page.hasStreamed) {
                page.lifecycle = "preparing"
            }
        }

        function onRuntimeEvent(type, payload) {
            if (type === "pong" && page.lifecycle === "live")
                return
        }
    }

    Connections {
        target: controllerInput
        function onConnectedChanged() {
            if (!page.visible)
                return
            if (controllerInput.connected) {
                page.showToast("Controller connected", controllerInput.controllerName + " · Player 1", "success", "⌁", "")
            } else if (page.previousControllerConnected) {
                page.showToast("Controller disconnected", "Input paused — reconnect a controller to continue", "warning", "!", "")
            }
            page.previousControllerConnected = controllerInput.connected
        }
    }

    Connections {
        target: appState
        function onPreferenceChanged(key, value) {
            if (key === "streamAntiAfk")
                page.antiAfkEnabled = Boolean(value)
            else if (key === "streamMicrophone")
                page.microphoneEnabled = Boolean(value)
            else if (key === "streamStatsMode")
                page.statsMode = Number(value)
        }
    }

    Rectangle {
        anchors.fill: parent
        color: "#050907"
    }

    GameArtwork {
        anchors.fill: parent
        variant: page.game.variant
        opacity: page.liveSurfaceVisible ? 0.14 : 0.08
    }

    Rectangle {
        anchors.fill: parent
        color: page.liveSurfaceVisible ? "#d9050a08" : "#eb050907"
    }

    Column {
        anchors.centerIn: parent
        spacing: 8
        visible: page.lifecycle === "live"
        opacity: 0.36

        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "NATIVE WEBRTC SURFACE"
            color: Theme.accent
            font.family: Theme.monoFont.family
            font.pixelSize: 11
            font.weight: Font.Bold
            font.letterSpacing: 2.2
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: streamEngine.statusText
            color: Theme.inkMuted
            font.pixelSize: 11
        }
    }

    Item {
        id: preparingView
        anchors.fill: parent
        visible: page.lifecycle === "preparing"

        Column {
            anchors.centerIn: parent
            width: 530
            spacing: 0

            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: String(page.game.title).toUpperCase()
                color: Theme.accent
                font.family: Theme.monoFont.family
                font.pixelSize: 13
                font.weight: Font.Bold
                font.letterSpacing: 3.2
            }
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                topPadding: 24
                text: "Preparing your rig"
                color: Theme.ink
                font.family: Theme.displayFont.family
                font.pixelSize: 48
                font.weight: Font.DemiBold
            }
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                topPadding: 12
                text: appState.serverName + " · RTX 4080 · " + qualityLabel() + " · " + page.displayCodec
                color: Theme.inkMuted
                font.pixelSize: 16
            }

            Item {
                anchors.horizontalCenter: parent.horizontalCenter
                width: 174
                height: 174
                anchors.topMargin: 42

                Canvas {
                    id: progressCanvas
                    anchors.fill: parent
                    onPaint: {
                        var ctx = getContext("2d")
                        ctx.clearRect(0, 0, width, height)
                        var center = width / 2
                        ctx.lineWidth = 3
                        ctx.strokeStyle = "#1b271f"
                        ctx.beginPath()
                        ctx.arc(center, center, center - 5, 0, Math.PI * 2)
                        ctx.stroke()
                        ctx.strokeStyle = Theme.accent
                        ctx.lineCap = "round"
                        ctx.beginPath()
                        ctx.arc(center, center, center - 5, -Math.PI / 2,
                                -Math.PI / 2 + Math.PI * 2 * Math.min(0.92, 0.18 + page.preparingSeconds / 18))
                        ctx.stroke()
                    }
                }
                Column {
                    anchors.centerIn: parent
                    spacing: 2
                    Text {
                        anchors.horizontalCenter: parent.horizontalCenter
                        text: page.preparingSeconds + "s"
                        color: Theme.accent
                        font.family: Theme.monoFont.family
                        font.pixelSize: 38
                        font.weight: Font.Bold
                    }
                    Text {
                        anchors.horizontalCenter: parent.horizontalCenter
                        text: "elapsed"
                        color: Theme.inkMuted
                        font.pixelSize: 12
                    }
                }
            }

            Column {
                anchors.horizontalCenter: parent.horizontalCenter
                width: 390
                topPadding: 32
                spacing: 17

                StageRow { complete: true; active: false; text: "Queue skipped — Ultimate tier" }
                StageRow { complete: page.preparingSeconds >= 1; active: page.preparingSeconds < 1; text: "Rig allocated in " + appState.serverRegion + "-03" }
                StageRow { complete: page.preparingSeconds >= 3; active: page.preparingSeconds >= 1 && page.preparingSeconds < 3; text: "Starting Steam in console mode…" }
                StageRow { complete: false; active: page.preparingSeconds >= 3; text: "NVST handshake & first frame" }
            }
        }

        Rectangle {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            height: 70
            color: "#9a070a08"
            border.color: Theme.divider
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 42
                anchors.rightMargin: 42
                Text {
                    text: "tip: press F6 in-stream for quick settings"
                    color: Theme.inkMuted
                    font.family: Theme.monoFont.family
                    font.pixelSize: 11
                    font.letterSpacing: 0.8
                }
                Item { Layout.fillWidth: true }
                FlatButton {
                    text: "Cancel"
                    keyHint: "B"
                    compact: true
                    onClicked: page.finishSession("Launch cancelled")
                }
            }
        }
    }

    Rectangle {
        id: minimalStats
        visible: page.lifecycle === "live" && page.statsMode > 0 && !page.menuVisible
        anchors.top: parent.top
        anchors.right: parent.right
        anchors.topMargin: 28
        anchors.rightMargin: 28
        width: 150
        height: 40
        radius: 11
        color: "#d0090d0a"
        border.color: "#294032"

        Row {
            anchors.centerIn: parent
            spacing: 9
            Text { text: streamEngine.fps; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 19; font.weight: Font.Bold }
            Text { text: "FPS"; color: Theme.inkMuted; font.pixelSize: 10; anchors.baseline: parent.children[0].baseline }
            Rectangle { width: 1; height: 18; color: Theme.divider; anchors.verticalCenter: parent.verticalCenter }
            Text { text: streamEngine.latencyMs + " ms"; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.Bold; anchors.verticalCenter: parent.verticalCenter }
        }
    }

    Rectangle {
        id: detailedStats
        visible: page.lifecycle === "live" && page.statsMode === 2 && !page.menuVisible
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.leftMargin: 28
        anchors.topMargin: 28
        width: 500
        height: 520
        radius: 14
        color: "#ed070b08"
        border.color: "#294032"

        Column {
            anchors.fill: parent
            anchors.margins: 24
            spacing: 10

            RowLayout {
                width: parent.width
                Text { text: "NVST DEBUG"; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 13; font.weight: Font.Bold; font.letterSpacing: 2.2 }
                Item { Layout.fillWidth: true }
                Rectangle {
                    width: 72; height: 24; radius: 6; color: "#12321d"
                    Text { anchors.centerIn: parent; text: "NVENC ON"; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 9; font.weight: Font.Bold }
                }
                FlatButton { text: "Close"; keyHint: "F3"; compact: true; onClicked: page.statsMode = 0 }
            }

            Row {
                spacing: 22
                Column {
                    Text { text: streamEngine.fps; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 62; font.weight: Font.Bold }
                    Text { text: (streamEngine.fps > 0 ? (1000 / streamEngine.fps).toFixed(1) : "—") + " ms avg"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 11 }
                }
                Row {
                    anchors.bottom: parent.bottom
                    anchors.bottomMargin: 8
                    spacing: 4
                    Repeater {
                        model: [0.52, 0.68, 0.61, 0.72, 0.66, 0.91, 0.58, 0.64, 0.71, 0.67, 0.73, 0.76]
                        Rectangle {
                            required property real modelData
                            required property int index
                            width: 10
                            height: 54 * modelData
                            anchors.bottom: parent.bottom
                            color: index === 5 ? Theme.warning : (index === 11 ? Theme.accent : "#386d4a")
                        }
                    }
                }
            }

            Text { text: "VIDEO"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1.8 }
            MetricLine { label: "codec"; value: page.displayCodec + " 10-bit" }
            MetricLine { label: "decoder"; value: "GStreamer native" }
            MetricLine { label: "stream"; value: page.displayResolution + " @ " + streamEngine.fps }
            MetricLine { label: "decode / present"; value: (streamEngine.fps > 0 ? (500 / streamEngine.fps).toFixed(1) : "—") + " ms / " + (streamEngine.fps > 0 ? (1000 / streamEngine.fps).toFixed(1) : "—") + " ms"; accentValue: true }
            MetricLine { label: "queue / dropped"; value: "1 frame / —" }
            MetricLine { label: "upscale / framegen"; value: page.upscalingMode + " → 4K / " + page.frameGenerationMode }

            Rectangle { width: parent.width; height: 1; color: Theme.divider }
            Text { text: "NETWORK"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1.8 }
            MetricLine { label: "rtt / jitter"; value: streamEngine.latencyMs + " ms / —"; accentValue: streamEngine.latencyMs < 40 }
            MetricLine { label: "packet loss"; value: Number(streamEngine.packetLoss).toFixed(1) + "%"; warningValue: streamEngine.packetLoss >= 1 }
            MetricLine { label: "bitrate rx"; value: (streamEngine.bitrateKbps / 1000).toFixed(1) + " Mbps" }
            MetricLine { label: "server"; value: appState.serverName + " · WebRTC" }
            MetricLine { label: "recovery"; value: page.recoveryCount + " resets" }
        }
    }

    Rectangle {
        visible: page.lifecycle === "live" && page.recording
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.leftMargin: 28
        anchors.topMargin: 28
        width: 70
        height: 34
        radius: 17
        color: "#d0090d0a"
        border.color: "#2c3930"
        Row {
            anchors.centerIn: parent
            spacing: 7
            Rectangle { width: 8; height: 8; radius: 4; color: Theme.error }
            Text { text: page.formatClock(page.recordingSeconds); color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 10; font.weight: Font.Bold }
        }
    }

    Row {
        visible: page.lifecycle === "live"
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.rightMargin: 28
        anchors.topMargin: page.statsMode > 0 && !page.menuVisible ? 82 : 28
        spacing: 9

        Rectangle {
            width: 42; height: 34; radius: 17; color: "#d0090d0a"; border.color: "#2c3930"
            Text { anchors.centerIn: parent; text: page.microphoneEnabled ? "♩" : "×"; color: page.microphoneEnabled ? Theme.accent : Theme.inkMuted; font.pixelSize: 15 }
            MouseArea { anchors.fill: parent; onClicked: page.toggleMicrophone() }
        }
        Rectangle {
            visible: page.antiAfkEnabled
            width: 93; height: 34; radius: 17; color: "#d0090d0a"; border.color: "#2c3930"
            Row {
                anchors.centerIn: parent; spacing: 7
                Rectangle { width: 7; height: 7; radius: 4; color: Theme.accent }
                Text { text: "Anti-AFK"; color: Theme.inkSoft; font.pixelSize: 10 }
            }
        }
    }

    Rectangle {
        id: menuShade
        visible: page.menuVisible && page.lifecycle === "live"
        anchors.fill: parent
        color: "#65000000"
        MouseArea {
            anchors.fill: parent
            onClicked: {
                page.menuVisible = false
                page.forceActiveFocus()
            }
        }
    }

    Rectangle {
        id: quickMenu
        visible: page.menuVisible && page.lifecycle === "live"
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        anchors.right: parent.right
        width: 500
        color: "#f2070b08"
        border.color: Theme.divider

        Flickable {
            anchors.fill: parent
            anchors.margins: 24
            contentHeight: menuContent.height
            clip: true
            boundsBehavior: Flickable.StopAtBounds

            Column {
                id: menuContent
                width: parent.width
                spacing: 16

                RowLayout {
                    width: parent.width
                    Text { text: "Quick settings"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 27; font.weight: Font.DemiBold }
                    Item { Layout.fillWidth: true }
                    FlatButton {
                        id: resumeButton
                        text: "Resume"
                        keyHint: "B"
                        compact: true
                        onClicked: {
                            page.menuVisible = false
                            page.forceActiveFocus()
                        }
                    }
                }

                RowLayout {
                    width: parent.width
                    Text { text: page.game.title + "  ·  " + page.elapsedText; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 11 }
                    Item { Layout.fillWidth: true }
                    Text { text: page.remainingText; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 11; font.weight: Font.Bold }
                }
                ProgressBar {
                    width: parent.width
                    from: 0
                    to: Math.max(1, page.sessionLimitSeconds)
                    value: page.sessionElapsedSeconds
                    background: Rectangle { implicitHeight: 4; radius: 2; color: Theme.divider }
                    contentItem: Item {
                        implicitHeight: 4
                        Rectangle { width: parent.width * quickProgress.position; height: 4; radius: 2; color: Theme.accent }
                    }
                    id: quickProgress
                }

                RowLayout {
                    width: parent.width
                    spacing: 9
                    QuickAction {
                        id: screenshotButton
                        Layout.fillWidth: true
                        glyph: "▣"
                        text: "SHOT · F11"
                        onClicked: page.captureScreenshot()
                    }
                    QuickAction {
                        id: recordingButton
                        Layout.fillWidth: true
                        glyph: "●"
                        text: page.recording ? "STOP · F12" : "REC · F12"
                        active: page.recording
                        danger: page.recording
                        onClicked: page.toggleRecording()
                    }
                    QuickAction {
                        id: microphoneButton
                        Layout.fillWidth: true
                        glyph: "♩"
                        text: page.microphoneEnabled ? "MIC LIVE" : "MIC OFF"
                        active: page.microphoneEnabled
                        onClicked: page.toggleMicrophone()
                    }
                    QuickAction {
                        id: hudButton
                        Layout.fillWidth: true
                        glyph: "▥"
                        text: "HUD · F3"
                        active: page.statsMode > 0
                        onClicked: page.cycleStats()
                    }
                }

                SettingsCard {
                    title: "Upscaling"
                    value: upscalingMode === "off" ? "Native" : "1440p → 4K"
                    Column {
                        width: parent.width
                        spacing: 12
                        RowLayout {
                            width: parent.width
                            spacing: 8
                            SegmentButton { Layout.fillWidth: true; text: "Off"; checked: page.upscalingMode === "off"; onClicked: { page.upscalingMode = "off"; appState.setPreference("streamUpscaling", "off") } }
                            SegmentButton { Layout.fillWidth: true; text: "Spatial"; checked: page.upscalingMode === "spatial"; onClicked: { page.upscalingMode = "spatial"; appState.setPreference("streamUpscaling", "spatial") } }
                            SegmentButton { Layout.fillWidth: true; text: "AI Temporal"; checked: page.upscalingMode === "temporal"; onClicked: { page.upscalingMode = "temporal"; appState.setPreference("streamUpscaling", "temporal") } }
                        }
                        RowLayout {
                            width: parent.width
                            Text { text: "Sharpness"; color: Theme.inkMuted; font.pixelSize: 11 }
                            Slider {
                                Layout.fillWidth: true
                                from: 0; to: 100; stepSize: 1; value: page.upscalingSharpness
                                onMoved: {
                                    page.upscalingSharpness = Math.round(value)
                                    appState.setPreference("streamUpscalingSharpness", page.upscalingSharpness)
                                }
                            }
                            Text { text: page.upscalingSharpness; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 11; font.weight: Font.Bold }
                        }
                    }
                }

                SettingsCard {
                    title: "Frame generation"
                    value: page.frameGenerationMode === "off" ? "" : "+4 MS LATENCY"
                    valueWarning: page.frameGenerationMode !== "off"
                    RowLayout {
                        width: parent.width
                        spacing: 8
                        SegmentButton { Layout.fillWidth: true; text: "Off"; checked: page.frameGenerationMode === "off"; onClicked: { page.frameGenerationMode = "off"; appState.setPreference("streamFrameGeneration", "off") } }
                        SegmentButton { Layout.fillWidth: true; text: "2×"; checked: page.frameGenerationMode === "2x"; onClicked: { page.frameGenerationMode = "2x"; appState.setPreference("streamFrameGeneration", "2x") } }
                        SegmentButton { Layout.fillWidth: true; text: "3×"; checked: page.frameGenerationMode === "3x"; onClicked: { page.frameGenerationMode = "3x"; appState.setPreference("streamFrameGeneration", "3x") } }
                    }
                }

                SettingsCard {
                    title: "Bitrate cap"
                    value: page.bitrateCapMbps + " Mbps"
                    Slider {
                        width: parent.width
                        from: 10
                        to: 75
                        stepSize: 1
                        value: page.bitrateCapMbps
                        onMoved: {
                            page.bitrateCapMbps = Math.round(value)
                            bitrateApplyTimer.restart()
                        }
                    }
                }

                Rectangle {
                    width: parent.width
                    height: 62
                    radius: 12
                    color: Theme.surfaceRaised
                    border.color: Theme.divider
                    RowLayout {
                        anchors.fill: parent
                        anchors.margins: 14
                        Column {
                            Text { text: "Anti-AFK"; color: Theme.ink; font.pixelSize: 14; font.weight: Font.DemiBold }
                            Text { text: "Simulates input every 15 min · Ctrl+Shift+K"; color: Theme.inkMuted; font.pixelSize: 10 }
                        }
                        Item { Layout.fillWidth: true }
                        Switch {
                            checked: page.antiAfkEnabled
                            onToggled: page.toggleAntiAfk()
                        }
                    }
                }

                Item { width: 1; height: 6 }
                Rectangle {
                    width: parent.width
                    height: 54
                    radius: 10
                    color: "transparent"
                    border.color: Theme.divider
                    border.width: 1
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 14
                        anchors.rightMargin: 14
                        Text { text: page.displayCodec + " · " + page.qualityLabel() + " · Vsync off"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10 }
                        Item { Layout.fillWidth: true }
                        FlatButton { text: "Debug HUD"; keyHint: "F3"; compact: true; onClicked: page.statsMode = 2 }
                    }
                }
                FlatButton {
                    id: menuEndButton
                    width: parent.width
                    text: "End session"
                    keyHint: "HOLD ="
                    danger: true
                    onClicked: page.openEndConfirmation()
                }
            }
        }
    }

    Rectangle {
        id: toast
        visible: page.toastVisible && page.lifecycle !== "report" && page.lifecycle !== "end-confirm" && page.lifecycle !== "recovery"
        anchors.top: parent.top
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.topMargin: 24
        width: Math.min(420, toastRow.implicitWidth + 38)
        height: 70
        radius: 13
        color: "#f10a0e0b"
        border.color: page.toastTone === "warning" ? "#584918"
                      : page.toastTone === "recording" ? "#542023" : "#25372b"
        z: 40

        RowLayout {
            id: toastRow
            anchors.fill: parent
            anchors.margins: 12
            spacing: 12
            Rectangle {
                width: 42; height: 42; radius: 10
                color: page.toastTone === "warning" ? "#2a2410"
                     : page.toastTone === "recording" ? "#2b1114" : "#102018"
                Text {
                    anchors.centerIn: parent
                    text: page.toastIcon
                    color: page.toastTone === "warning" ? Theme.warning
                         : page.toastTone === "recording" ? Theme.error : Theme.accent
                    font.pixelSize: 18
                    font.weight: Font.Bold
                }
            }
            Column {
                Layout.preferredWidth: Math.max(toastTitleText.implicitWidth, toastDetailText.implicitWidth)
                Text { id: toastTitleText; text: page.toastTitle; color: Theme.ink; font.pixelSize: 14; font.weight: Font.DemiBold }
                Text { id: toastDetailText; visible: page.toastDetail.length > 0; text: page.toastDetail; color: Theme.inkMuted; font.pixelSize: 11 }
            }
            FlatButton {
                visible: page.toastAction.length > 0
                text: page.toastAction
                compact: true
                onClicked: {
                    page.toastVisible = false
                    page.openEndConfirmation()
                }
            }
        }
    }

    Rectangle {
        visible: page.lifecycle === "recovery"
        anchors.fill: parent
        color: "#e9000000"
        z: 60

        Rectangle {
            anchors.centerIn: parent
            width: 500
            height: 270
            radius: 16
            color: "#f2090d0a"
            border.color: "#26352b"

            Column {
                anchors.fill: parent
                anchors.margins: 30
                spacing: 10
                Item {
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: 58; height: 58
                    Rectangle {
                        anchors.centerIn: parent
                        width: 52; height: 52; radius: 26
                        color: "transparent"; border.width: 3; border.color: "#1b271f"
                    }
                    Rectangle {
                        width: 12; height: 12; radius: 6; color: Theme.accent
                        anchors.top: parent.top; anchors.horizontalCenter: parent.horizontalCenter
                        RotationAnimation on rotation { from: 0; to: 360; duration: 1100; loops: Animation.Infinite }
                        transformOrigin: Item.Bottom
                    }
                }
                Text { anchors.horizontalCenter: parent.horizontalCenter; text: "Connection lost"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 25; font.weight: Font.DemiBold }
                Text { anchors.horizontalCenter: parent.horizontalCenter; text: "Resuming your stream — attempt " + page.recoveryAttempt + " of 5"; color: Theme.inkSoft; font.pixelSize: 13 }
                Text { anchors.horizontalCenter: parent.horizontalCenter; text: "game keeps running on the rig for " + Math.max(0, 90 - page.recoveryElapsedSeconds) + " s"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10 }
                ProgressBar {
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: 300
                    from: 0; to: 90; value: page.recoveryElapsedSeconds
                }
                FlatButton {
                    anchors.horizontalCenter: parent.horizontalCenter
                    text: "Give up and end session"
                    keyHint: "B"
                    compact: true
                    onClicked: page.finishSession("Connection lost")
                }
            }
        }
    }

    Rectangle {
        visible: page.lifecycle === "end-confirm"
        anchors.fill: parent
        color: "#dd000000"
        z: 70

        Rectangle {
            anchors.centerIn: parent
            width: 500
            height: 184
            radius: 16
            color: "#f2090d0a"
            border.color: "#542326"

            Column {
                anchors.fill: parent
                anchors.margins: 28
                spacing: 8
                Text { text: "End this session?"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 22; font.weight: Font.DemiBold }
                Text {
                    width: parent.width
                    text: "Unsaved progress in " + page.game.title + " is lost. The rig is released immediately."
                    color: Theme.inkMuted
                    font.pixelSize: 12
                    wrapMode: Text.WordWrap
                }
                RowLayout {
                    width: parent.width
                    spacing: 10
                    FlatButton {
                        id: keepPlayingButton
                        Layout.fillWidth: true
                        text: "Keep playing"
                        keyHint: "B"
                        primary: true
                        onClicked: page.cancelEndConfirmation()
                    }
                    Button {
                        id: holdEndButton
                        Layout.fillWidth: true
                        implicitHeight: 48
                        focusPolicy: Qt.StrongFocus
                        onPressed: holdTimer.start()
                        onReleased: {
                            if (page.holdProgressPercent < 100) {
                                holdTimer.stop()
                                page.holdProgressPercent = 0
                            }
                        }
                        contentItem: Text {
                            text: "Hold A to end"
                            color: Theme.error
                            horizontalAlignment: Text.AlignHCenter
                            verticalAlignment: Text.AlignVCenter
                            font.pixelSize: 13
                            font.weight: Font.Bold
                        }
                        background: Rectangle {
                            radius: 10
                            color: "transparent"
                            border.color: "#b83a40"
                            clip: true
                            Rectangle {
                                width: parent.width * page.holdProgressPercent / 100
                                height: parent.height
                                color: "#6a2024"
                            }
                        }
                    }
                }
            }
        }
    }

    Rectangle {
        visible: page.lifecycle === "report"
        anchors.fill: parent
        color: "#f0000000"
        z: 80

        Rectangle {
            anchors.centerIn: parent
            width: 570
            height: 280
            radius: 16
            color: "#f2090d0a"
            border.color: "#26352b"

            Column {
                anchors.fill: parent
                anchors.margins: 28
                spacing: 10
                RowLayout {
                    width: parent.width
                    Column {
                        Text { text: "Session report"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 23; font.weight: Font.DemiBold }
                        Text { text: page.game.title + " · " + page.formatDuration(page.sessionElapsedSeconds); color: Theme.inkMuted; font.pixelSize: 12 }
                    }
                    Item { Layout.fillWidth: true }
                    Rectangle {
                        width: ratingText.implicitWidth + 30; height: 30; radius: 15
                        color: page.reportRating === "Unstable" ? "#2b1513" : "#12321d"
                        Row {
                            anchors.centerIn: parent; spacing: 7
                            Rectangle { width: 7; height: 7; radius: 4; color: page.reportRating === "Unstable" ? Theme.error : Theme.accent }
                            Text { id: ratingText; text: page.reportRating; color: page.reportRating === "Unstable" ? Theme.error : Theme.accent; font.pixelSize: 11; font.weight: Font.Bold }
                        }
                    }
                }

                RowLayout {
                    width: parent.width
                    ReportMetric { Layout.fillWidth: true; value: page.averageFps; label: "avg FPS" }
                    ReportMetric { Layout.fillWidth: true; value: page.averageLatency + " ms"; label: "avg RTT" }
                    ReportMetric { Layout.fillWidth: true; value: page.recoveryCount; label: "stalls" }
                    ReportMetric { Layout.fillWidth: true; value: Number(page.averagePacketLoss).toFixed(1) + "%"; label: "pkt loss"; warning: page.averagePacketLoss >= 1 }
                }

                Row {
                    width: parent.width
                    height: 38
                    spacing: 2
                    Repeater {
                        model: qualitySamples
                        Rectangle {
                            required property real value
                            required property string tone
                            width: (parent.width - 18) / Math.max(1, qualitySamples.count)
                            height: Math.max(18, Math.min(38, value / Math.max(1, page.averageFps) * 30))
                            anchors.bottom: parent.bottom
                            color: tone === "warn" ? Theme.warning : "#386d4a"
                        }
                    }
                }

                Text {
                    visible: page.exportStatus.length > 0
                    width: parent.width
                    text: page.exportStatus
                    color: Theme.inkMuted
                    font.family: Theme.monoFont.family
                    font.pixelSize: 9
                    elide: Text.ElideMiddle
                }

                RowLayout {
                    width: parent.width
                    spacing: 10
                    FlatButton {
                        Layout.fillWidth: true
                        text: "Export"
                        keyHint: "X"
                        onClicked: page.exportReport()
                    }
                    FlatButton {
                        id: doneButton
                        Layout.fillWidth: true
                        text: "Done"
                        keyHint: "A"
                        primary: true
                        onClicked: page.exit()
                    }
                }
            }
        }
    }

    Shortcut { sequence: "F3"; enabled: page.visible && page.lifecycle === "live"; onActivated: page.cycleStats() }
    Shortcut { sequence: "Ctrl+N"; enabled: page.visible && page.lifecycle === "live"; onActivated: page.cycleStats() }
    Shortcut { sequence: "F6"; enabled: page.visible && page.lifecycle === "live"; onActivated: { page.menuVisible = !page.menuVisible; if (page.menuVisible) Qt.callLater(function() { resumeButton.forceActiveFocus() }); else page.forceActiveFocus() } }
    Shortcut { sequence: "Ctrl+G"; enabled: page.visible && page.lifecycle === "live"; onActivated: { page.menuVisible = !page.menuVisible; if (page.menuVisible) Qt.callLater(function() { resumeButton.forceActiveFocus() }); else page.forceActiveFocus() } }
    Shortcut { sequence: "F11"; enabled: page.visible && page.lifecycle === "live"; onActivated: page.captureScreenshot() }
    Shortcut { sequence: "F12"; enabled: page.visible && page.lifecycle === "live"; onActivated: page.toggleRecording() }
    Shortcut { sequence: "Ctrl+Shift+K"; enabled: page.visible && page.lifecycle === "live"; onActivated: page.toggleAntiAfk() }
    Shortcut { sequence: "Ctrl+Shift+Q"; enabled: page.visible && page.lifecycle !== "report"; onActivated: page.openEndConfirmation() }
    Shortcut { sequence: "X"; enabled: page.visible && page.lifecycle === "report"; onActivated: page.exportReport() }

    component StageRow: Row {
        property bool complete: false
        property bool active: false
        property string text: ""
        spacing: 13
        Rectangle {
            width: 23; height: 23; radius: 12
            color: parent.complete ? Theme.accent : "transparent"
            border.width: parent.complete ? 0 : 2
            border.color: parent.active ? Theme.accent : "#263129"
            Text { anchors.centerIn: parent; text: parent.parent.complete ? "✓" : ""; color: Theme.accentInk; font.pixelSize: 13; font.weight: Font.Bold }
        }
        Text {
            anchors.verticalCenter: parent.verticalCenter
            text: parent.text
            color: parent.active ? Theme.ink : (parent.complete ? Theme.inkSoft : Theme.inkMuted)
            font.pixelSize: 14
            font.weight: parent.active ? Font.DemiBold : Font.Normal
        }
    }

    component FlatButton: Button {
        id: control
        property bool primary: false
        property bool danger: false
        property bool compact: false
        property string keyHint: ""
        implicitHeight: compact ? 38 : 50
        implicitWidth: Math.max(compact ? 90 : 150, buttonRow.implicitWidth + 30)
        focusPolicy: Qt.StrongFocus
        contentItem: Row {
            id: buttonRow
            anchors.centerIn: parent
            spacing: 8
            Text { text: control.text; color: control.danger ? Theme.error : (control.primary ? Theme.accentInk : Theme.ink); font.pixelSize: control.compact ? 11 : 13; font.weight: Font.Bold }
            Rectangle {
                visible: control.keyHint.length > 0
                width: keyText.implicitWidth + 10; height: 22; radius: 6
                color: control.primary ? "#22000000" : "transparent"
                border.color: control.primary ? "#55000000" : (control.danger ? "#8e3237" : "#39453d")
                Text { id: keyText; anchors.centerIn: parent; text: control.keyHint; color: control.danger ? Theme.error : (control.primary ? Theme.accentInk : Theme.inkSoft); font.family: Theme.monoFont.family; font.pixelSize: 9; font.weight: Font.Bold }
            }
        }
        background: Rectangle {
            radius: 10
            color: control.primary ? (control.down ? Theme.accentStrong : Theme.accent)
                 : control.danger ? "#15000000"
                 : control.down ? Theme.surfaceBright : Theme.surfaceRaised
            border.width: control.activeFocus ? 2 : 1
            border.color: control.danger ? "#b83a40" : (control.activeFocus ? Theme.accent : Theme.divider)
        }
    }

    component QuickAction: Button {
        id: control
        property string glyph: ""
        property bool active: false
        property bool danger: false
        implicitHeight: 66
        focusPolicy: Qt.StrongFocus
        contentItem: Column {
            anchors.centerIn: parent
            spacing: 6
            Text { anchors.horizontalCenter: parent.horizontalCenter; text: control.glyph; color: control.danger ? Theme.error : (control.active ? Theme.accent : Theme.inkSoft); font.pixelSize: 18; font.weight: Font.Bold }
            Text { anchors.horizontalCenter: parent.horizontalCenter; text: control.text; color: control.danger ? Theme.error : (control.active ? Theme.accent : Theme.inkMuted); font.family: Theme.monoFont.family; font.pixelSize: 8; font.weight: Font.Bold; font.letterSpacing: 0.7 }
        }
        background: Rectangle {
            radius: 11
            color: Theme.surfaceRaised
            border.width: control.activeFocus || control.active ? 2 : 1
            border.color: control.danger ? "#9f3036" : (control.activeFocus || control.active ? Theme.accent : Theme.divider)
        }
    }

    component SegmentButton: Button {
        id: control
        implicitHeight: 43
        focusPolicy: Qt.StrongFocus
        contentItem: Text {
            text: control.text
            color: control.checked ? Theme.accentInk : Theme.inkSoft
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            font.pixelSize: 12
            font.weight: Font.DemiBold
        }
        background: Rectangle {
            radius: 9
            color: control.checked ? Theme.accent : (control.down ? Theme.surfaceBright : "transparent")
            border.width: control.activeFocus ? 2 : 1
            border.color: control.activeFocus ? Theme.accent : Theme.divider
        }
    }

    component SettingsCard: Rectangle {
        id: card
        property string title: ""
        property string value: ""
        property bool valueWarning: false
        default property alias body: bodyColumn.data
        width: parent ? parent.width : 452
        height: cardColumn.implicitHeight + 30
        radius: 12
        color: Theme.surfaceRaised
        border.color: Theme.divider
        Column {
            id: cardColumn
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 15
            spacing: 12
            RowLayout {
                width: parent.width
                Text { text: card.title; color: Theme.ink; font.pixelSize: 16; font.weight: Font.DemiBold }
                Item { Layout.fillWidth: true }
                Text { text: card.value; color: card.valueWarning ? Theme.warning : Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 10; font.weight: Font.Bold; font.letterSpacing: 0.8 }
            }
            Column {
                id: bodyColumn
                width: parent.width
                spacing: 10
            }
        }
    }

    component MetricLine: RowLayout {
        property string label: ""
        property string value: ""
        property bool accentValue: false
        property bool warningValue: false
        width: parent ? parent.width : 450
        Text { text: parent.label; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 11 }
        Item { Layout.fillWidth: true }
        Text { text: parent.value; color: parent.warningValue ? Theme.warning : (parent.accentValue ? Theme.accent : Theme.ink); font.family: Theme.monoFont.family; font.pixelSize: 11; font.weight: Font.Bold }
    }

    component ReportMetric: Column {
        property string value: ""
        property string label: ""
        property bool warning: false
        spacing: 2
        Text { text: parent.value; color: parent.warning ? Theme.warning : Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 18; font.weight: Font.Bold }
        Text { text: parent.label; color: Theme.inkMuted; font.pixelSize: 10 }
    }
}
