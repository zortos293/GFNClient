import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    focus: true
    Accessible.role: Accessible.Pane
    Accessible.name: qsTr("Live session")

    signal stopRequested()
    signal retryRequested()
    signal menuRequested()

    readonly property var game: ShellStore.selectedGame || ({})
    readonly property var session: ShellStore.activeSession || ({})
    readonly property var streamer: ShellStore.streamer || ({})
    readonly property string status: String(root.streamer.status || "starting")
    readonly property bool streaming: root.status === "streaming"
    readonly property bool failed: root.status === "error"
    // While the native runtime presents the picture the shell stays out of the way:
    // the window is hidden by Main.qml, and any overlay must composite over the stream.
    readonly property bool handoffVisible: !root.streaming
    readonly property string artwork: DesktopTokens.decodeArtworkUrl(
        String(root.game.heroImageUrl || root.game.imageUrl || ""))

    function statusLabel() {
        if (root.failed) return qsTr("MEDIA RUNTIME FAILED")
        if (root.status === "negotiating") return qsTr("NEGOTIATING TRANSPORT")
        if (root.status === "connecting") return qsTr("CONNECTING TO RIG")
        if (root.status === "reconnecting") return qsTr("RECONNECTING")
        if (root.status === "stopped") return qsTr("SESSION CLOSED")
        return qsTr("HANDING OVER THE STREAM")
    }

    ArtworkSource {
        id: streamArtwork
        sourceUrl: root.artwork
        active: root.handoffVisible && root.visible
    }

    Rectangle {
        anchors.fill: parent
        visible: root.handoffVisible
        color: "#04060A"

        Image {
            anchors.fill: parent
            source: streamArtwork.resolvedUrl
            fillMode: Image.PreserveAspectCrop
            asynchronous: true
            cache: true
            opacity: status === Image.Ready ? 0.28 : 0
            Behavior on opacity { NumberAnimation { duration: DesktopTokens.revealDuration; easing.type: Easing.OutCubic } }
        }
        Rectangle {
            anchors.fill: parent
            gradient: Gradient {
                GradientStop { position: 0; color: "#D604060A" }
                GradientStop { position: 0.5; color: "#BC04060A" }
                GradientStop { position: 1; color: "#F204060A" }
            }
        }
    }

    Column {
        id: handoff
        visible: root.handoffVisible
        width: Math.min(560, root.width - 64)
        x: Math.round((root.width - width) / 2)
        y: Math.max(72, Math.round((root.height - height) / 2))
        spacing: 0

        Text {
            text: root.statusLabel()
            color: root.failed ? DesktopTokens.danger : DesktopTokens.focus
            font.family: DesktopTokens.monoFont
            font.pixelSize: 11
            font.weight: Font.Bold
            font.letterSpacing: 1.8
        }
        Text {
            width: parent.width
            topPadding: 5
            text: String(root.game.title || qsTr("GeForce NOW"))
            color: DesktopTokens.text
            font.family: DesktopTokens.displayFont
            font.pixelSize: 40
            font.weight: Font.Black
            font.letterSpacing: -1.1
            elide: Text.ElideRight
        }
        Text {
            width: parent.width
            topPadding: 6
            text: String(root.streamer.message || ShellStore.streamMessage
                || qsTr("Your rig is live. OpenNOW is starting the native media runtime."))
            color: DesktopTokens.textBody
            font.family: DesktopTokens.bodyFont
            font.pixelSize: 14
            font.weight: Font.Medium
            wrapMode: Text.WordWrap
        }

        Item { width: 1; height: 26 }
        Row {
            spacing: 10
            DesktopButton {
                id: primaryAction
                text: root.failed ? qsTr("Retry media") : qsTr("Session menu")
                shortcutText: root.failed ? "" : "F1"
                primary: true
                onClicked: root.failed ? root.retryRequested() : root.menuRequested()
            }
            DesktopButton {
                text: qsTr("End session")
                shortcutText: "Esc"
                danger: true
                onClicked: root.stopRequested()
            }
        }

        Item { width: 1; height: 24 }
        Row {
            spacing: 22
            DesktopKeyHint { keyText: "F1"; label: qsTr("Session menu") }
            DesktopKeyHint { keyText: "F3"; label: qsTr("Stream stats") }
            DesktopKeyHint { keyText: "F10"; label: qsTr("Console mode") }
        }
    }

    Text {
        visible: root.handoffVisible
        anchors.left: parent.left
        anchors.bottom: parent.bottom
        anchors.margins: 32
        text: root.session.sessionId
            ? qsTr("SESSION %1").arg(String(root.session.sessionId).slice(0, 9).toUpperCase())
            : qsTr("SESSION PENDING")
        color: DesktopTokens.textFaint
        font.family: DesktopTokens.monoFont
        font.pixelSize: 9
        font.weight: Font.Bold
        font.letterSpacing: 0.8
    }

    onVisibleChanged: if (visible) Qt.callLater(() => primaryAction.forceActiveFocus())

    Keys.onPressed: event => {
        if (event.isAutoRepeat)
            return
        if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
            root.stopRequested()
            event.accepted = true
        }
    }
}
