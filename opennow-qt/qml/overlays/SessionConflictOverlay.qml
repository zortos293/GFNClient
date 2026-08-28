import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    anchors.fill: parent
    focus: true
    Accessible.name: qsTr("Existing GeForce NOW session")
    Accessible.role: Accessible.Dialog

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(0, 0, 0, 0.58)
    }

    GlassPanel {
        id: card
        anchors.centerIn: parent
        width: Math.min(parent.width - 64, 660)
        height: 390
        scale: root.visible ? 1 : 0.94
        Behavior on scale {
            NumberAnimation { duration: Theme.panelDuration; easing.type: Easing.OutBack }
        }

        Column {
            anchors.fill: parent
            anchors.margins: 42
            spacing: 18

            Text {
                text: qsTr("SESSION ALREADY ACTIVE")
                color: Theme.mint
                font.family: Theme.monoFont
                font.pixelSize: 13
                font.weight: Font.Bold
                font.letterSpacing: 1.5
            }
            Text {
                width: parent.width
                text: ShellStore.pendingLaunchParams
                      ? qsTr("Pick up where you left off?")
                      : qsTr("A cloud session is still running")
                color: Theme.label
                font.family: Theme.displayFont
                font.pixelSize: 34
                font.weight: Font.Black
                wrapMode: Text.WordWrap
            }
            Text {
                width: parent.width
                text: ShellStore.conflictSession
                      ? qsTr("App %1 · %2 · %3 FPS")
                            .arg(String(ShellStore.conflictSession.appId || "—"))
                            .arg(String(ShellStore.conflictSession.resolution || qsTr("Cloud rig")))
                            .arg(Number(ShellStore.conflictSession.fps || 60))
                      : qsTr("OpenNOW found another session on your NVIDIA account.")
                color: Theme.textMuted
                font.family: Theme.bodyFont
                font.pixelSize: 18
            }

            Item { width: 1; height: 8 }

            Row {
                spacing: 14
                GlassButton {
                    id: resumeButton
                    text: qsTr("Resume")
                    glyph: "A"
                    primary: true
                    focus: true
                    onClicked: ShellStore.resolveSessionConflict("resume")
                    KeyNavigation.right: newButton
                }
                GlassButton {
                    id: newButton
                    text: ShellStore.pendingLaunchParams ? qsTr("Start new") : qsTr("End session")
                    glyph: "X"
                    danger: true
                    onClicked: ShellStore.resolveSessionConflict("new")
                    KeyNavigation.left: resumeButton
                    KeyNavigation.right: cancelButton
                }
                GlassButton {
                    id: cancelButton
                    text: qsTr("Cancel")
                    glyph: "B"
                    onClicked: ShellStore.resolveSessionConflict("cancel")
                    KeyNavigation.left: newButton
                }
            }
        }
    }

    Component.onCompleted: resumeButton.forceActiveFocus()

    Behavior on opacity {
        NumberAnimation { duration: Theme.overlayDuration; easing.type: Easing.OutCubic }
    }
}
