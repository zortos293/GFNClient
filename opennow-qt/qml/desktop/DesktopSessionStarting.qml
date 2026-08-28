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
    Accessible.role: Accessible.Pane
    Accessible.name: qsTr("Starting session")

    signal cancelRequested()

    readonly property var game: ShellStore.selectedGame || ({
        title: "Cyberpunk 2077",
        heroImageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/27G0GQ44XWDM79Z64SSA5Z89F6.jpg"
    })
    readonly property var session: ShellStore.activeSession || ({})
    readonly property var profile: session.negotiatedStreamProfile || session.streamProfile || ({})
    readonly property string phase: String(ShellStore.streamState || "preparing")
    readonly property bool failed: phase === "failed" || phase === "error"
    readonly property int activeStep: phase === "checking" ? 0
        : phase === "requesting" || phase === "resuming" ? 1
        : phase === "ready" || phase === "streaming" ? 3 : 2
    readonly property real progressValue: phase === "checking" ? 0.16
        : phase === "requesting" ? 0.34
        : phase === "resuming" ? 0.48
        : phase === "ready" || phase === "streaming" ? 1.0 : 0.665
    readonly property string region: String(session.regionName || session.region || session.serverRegionId || "EU-WEST").toUpperCase()
    readonly property int latency: Math.max(1, Number(session.latencyMs || session.pingMs || 9))
    readonly property int widthPx: Number(profile.width || ShellStore.settings.resolutionWidth || 3840)
    readonly property int heightPx: Number(profile.height || ShellStore.settings.resolutionHeight || 2160)
    readonly property int fps: Number(profile.fps || profile.frameRate || ShellStore.settings.frameRate || 120)
    readonly property string codec: String(profile.codec || ShellStore.settings.codec || "AV1").toUpperCase()
    readonly property int bitrate: Math.round(Number(profile.bitrateMbps || ShellStore.settings.maxBitrate || 75))

    function resolutionLabel() {
        if (heightPx >= 2160) return "2160P"
        if (heightPx >= 1440) return "1440P"
        if (heightPx >= 1080) return "1080P"
        return heightPx + "P"
    }

    Rectangle { anchors.fill: parent; color: "#04060A" }

    Image {
        anchors.fill: parent
        source: String(root.game.heroImageUrl || root.game.imageUrl || "")
        fillMode: Image.PreserveAspectCrop
        asynchronous: true
        cache: true
        opacity: status === Image.Ready ? 0.32 : 0
        Behavior on opacity { NumberAnimation { duration: DesktopTokens.revealDuration; easing.type: Easing.OutCubic } }
    }
    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0; color: "#CF04060A" }
            GradientStop { position: 0.48; color: "#B804060A" }
            GradientStop { position: 1; color: "#F204060A" }
        }
    }
    Rectangle {
        anchors.fill: parent
        color: "transparent"
        border.width: 1
        border.color: "#0AFFFFFF"
    }

    Row {
        x: 32
        y: 28
        spacing: 10
        Rectangle {
            anchors.verticalCenter: parent.verticalCenter
            width: 25
            height: 14
            radius: 7
            color: "#68E341"
            Text { anchors.centerIn: parent; text: "☁"; color: "#11320C"; font.pixelSize: 10; font.weight: Font.Black }
        }
        Text {
            text: "OpenNOW"
            color: DesktopTokens.text
            font.family: DesktopTokens.displayFont
            font.pixelSize: 16
            font.weight: Font.Black
            font.letterSpacing: -0.3
        }
    }

    Column {
        id: launchContent
        x: Math.round((root.width - width) / 2)
        y: 204
        width: 620
        spacing: 0
        opacity: 1
        transform: Translate { id: revealTranslate; y: 0 }

        Text {
            text: root.failed ? qsTr("SESSION INTERRUPTED") : qsTr("STARTING SESSION")
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
            font.pixelSize: 44
            font.weight: Font.Black
            font.letterSpacing: -1.2
            elide: Text.ElideRight
        }
        Text {
            width: parent.width
            topPadding: 4
            text: root.failed
                ? (ShellStore.streamMessage || qsTr("The cloud rig could not finish preparing. Your library is safe."))
                : qsTr("No queue. Your rig is being handed over now.")
            color: DesktopTokens.textBody
            font.family: DesktopTokens.bodyFont
            font.pixelSize: 14
            font.weight: Font.Medium
            wrapMode: Text.WordWrap
        }

        Item { width: 1; height: 29 }
        Rectangle {
            width: parent.width
            height: 5
            radius: 2.5
            color: "#21FFFFFF"
            clip: true
            Rectangle {
                width: parent.width * root.progressValue
                height: parent.height
                radius: parent.radius
                color: root.failed ? DesktopTokens.danger : DesktopTokens.focus
                Behavior on width { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic } }
            }
        }
        Item {
            width: parent.width
            height: 31
            Text {
                anchors.left: parent.left
                anchors.top: parent.top
                anchors.topMargin: 8
                text: root.failed ? qsTr("LAUNCH PAUSED")
                    : root.activeStep < 2 ? qsTr("RESERVING YOUR RIG")
                    : root.activeStep === 2 ? qsTr("WARMING UP SHADERS") : qsTr("OPENING STREAM")
                color: DesktopTokens.textMuted
                font.family: DesktopTokens.monoFont
                font.pixelSize: 9
                font.weight: Font.Bold
                font.letterSpacing: 1.2
            }
            Text {
                anchors.right: parent.right
                anchors.top: parent.top
                anchors.topMargin: 8
                text: root.failed ? qsTr("ACTION NEEDED") : root.activeStep >= 3 ? qsTr("ALMOST THERE") : qsTr("ABOUT 6 SECONDS LEFT")
                color: DesktopTokens.textFaint
                font.family: DesktopTokens.monoFont
                font.pixelSize: 9
                font.weight: Font.DemiBold
                font.letterSpacing: 0.9
            }
        }

        Rectangle {
            width: parent.width
            height: 174
            radius: 16
            color: "#B804060A"
            border.width: 1
            border.color: "#17FFFFFF"

            Column {
                x: 16
                y: 14
                width: parent.width - 32
                spacing: 0
                Repeater {
                    model: [
                        { label: qsTr("Reserved an RTX 5080 in %1").arg(root.region), meta: "0.8 s" },
                        { label: qsTr("Restored your cloud save and Steam link"), meta: "1.4 s" },
                        { label: qsTr("Warming up shaders and applying your overrides"), meta: qsTr("RUNNING") },
                        { label: qsTr("Opening the NVST stream transport"), meta: qsTr("QUEUED") }
                    ]
                    delegate: Item {
                        id: stepRow
                        required property var modelData
                        required property int index
                        width: parent.width
                        height: 36
                        readonly property bool done: index < root.activeStep
                        readonly property bool active: index === root.activeStep && !root.failed

                        Rectangle {
                            x: 0
                            anchors.verticalCenter: parent.verticalCenter
                            width: 18
                            height: 18
                            radius: 9
                            color: stepRow.done ? "#2856E6A5" : stepRow.active ? "#237FD4FF" : "#0FFFFFFF"
                            border.width: 1
                            border.color: stepRow.done ? DesktopTokens.green : stepRow.active ? DesktopTokens.focus : "#29FFFFFF"
                            Text {
                                anchors.centerIn: parent
                                text: stepRow.done ? "✓" : stepRow.active ? "•" : ""
                                color: stepRow.done ? DesktopTokens.green : DesktopTokens.focus
                                font.pixelSize: stepRow.active ? 18 : 11
                                font.weight: Font.Bold
                            }
                        }
                        Text {
                            x: 30
                            anchors.verticalCenter: parent.verticalCenter
                            width: parent.width - 118
                            text: stepRow.modelData.label
                            color: stepRow.done || stepRow.active ? DesktopTokens.textHigh : DesktopTokens.textFaint
                            font.family: DesktopTokens.bodyFont
                            font.pixelSize: 12
                            font.weight: stepRow.active ? Font.Bold : Font.DemiBold
                            elide: Text.ElideRight
                        }
                        Text {
                            anchors.right: parent.right
                            anchors.verticalCenter: parent.verticalCenter
                            text: stepRow.index === 2 && stepRow.active ? qsTr("RUNNING")
                                : stepRow.index === 3 && stepRow.active ? qsTr("RUNNING")
                                : stepRow.modelData.meta
                            color: stepRow.active ? DesktopTokens.focus : stepRow.done ? DesktopTokens.textMuted : DesktopTokens.textFaint
                            font.family: DesktopTokens.monoFont
                            font.pixelSize: 9
                            font.weight: Font.Bold
                            font.letterSpacing: 0.7
                        }
                    }
                }
            }
        }

        Item { width: 1; height: 14 }
        Row {
            width: parent.width
            spacing: 8
            Repeater {
                model: [
                    root.region + " · " + root.latency + " MS",
                    root.resolutionLabel() + " · " + root.fps + " FPS",
                    root.codec + " · " + root.bitrate + " MBPS",
                    "RT OVERDRIVE"
                ]
                delegate: Rectangle {
                    id: metaChip
                    required property string modelData
                    required property int index
                    width: metaText.implicitWidth + 18
                    height: 25
                    radius: 12.5
                    color: index === 3 ? "#279F7AEA" : "#10FFFFFF"
                    border.width: 1
                    border.color: index === 3 ? "#669F7AEA" : "#20FFFFFF"
                    Text {
                        id: metaText
                        anchors.centerIn: parent
                        text: metaChip.modelData
                        color: metaChip.index === 3 ? "#CDBDFF" : DesktopTokens.textMuted
                        font.family: DesktopTokens.monoFont
                        font.pixelSize: 9
                        font.weight: Font.Bold
                        font.letterSpacing: 0.6
                    }
                }
            }
        }

        Item { width: 1; height: 16 }
        DesktopButton {
            id: cancelButton
            width: parent.width
            height: 36
            text: root.failed ? qsTr("Back to game details") : qsTr("Cancel launch")
            shortcutText: "Esc"
            danger: root.failed
            onClicked: root.cancelRequested()
        }
    }

    Item {
        x: 32
        y: 804
        width: 340
        height: 64
        Text {
            text: qsTr("WHILE YOU WAIT")
            color: DesktopTokens.textFaint
            font.family: DesktopTokens.monoFont
            font.pixelSize: 9
            font.weight: Font.Bold
            font.letterSpacing: 1.2
        }
        Text {
            y: 22
            width: parent.width
            text: qsTr("Press F3 any time to cycle compact and detailed stream stats.")
            color: DesktopTokens.textMuted
            font.family: DesktopTokens.bodyFont
            font.pixelSize: 11
            wrapMode: Text.WordWrap
        }
    }

    Column {
        anchors.right: parent.right
        anchors.rightMargin: 32
        y: 812
        spacing: 5
        Text {
            anchors.right: parent.right
            text: qsTr("SESSION %1").arg(String(root.session.sessionId || "8D2A-7F19").slice(0, 9).toUpperCase())
            color: DesktopTokens.textFaint
            font.family: DesktopTokens.monoFont
            font.pixelSize: 9
            font.weight: Font.Bold
            font.letterSpacing: 0.8
        }
        Text {
            anchors.right: parent.right
            text: qsTr("ULTIMATE · PRIORITY RIG")
            color: DesktopTokens.textMuted
            font.family: DesktopTokens.monoFont
            font.pixelSize: 9
            font.weight: Font.DemiBold
            font.letterSpacing: 0.6
        }
    }

    SequentialAnimation {
        running: true
        NumberAnimation { target: launchContent; property: "opacity"; from: 0; to: 1; duration: AppController.reducedMotion ? 0 : 200; easing.type: Easing.OutCubic }
        NumberAnimation { target: revealTranslate; property: "y"; from: AppController.reducedMotion ? 0 : 10; to: 0; duration: AppController.reducedMotion ? 0 : 180; easing.type: Easing.OutCubic }
    }

    Keys.onPressed: event => {
        if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
            root.cancelRequested()
            event.accepted = true
        }
    }
}
