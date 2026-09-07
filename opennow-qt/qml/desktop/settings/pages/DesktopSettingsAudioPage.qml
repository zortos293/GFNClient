import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

DesktopSettingsPanel {
    id: page
    required property real availableWidth

    width: page.availableWidth; paperStyle: true
    DesktopSettingsSection { text: qsTr("AUDIO") }
    DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "wave"; title: qsTr("Output device"); description: qsTr("The native streamer follows your operating system output"); value: qsTr("SYSTEM DEFAULT") }
    DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "sliders"; title: qsTr("Game volume"); description: qsTr("Use the system mixer or the game's own audio settings"); value: qsTr("SYSTEM MIXER") }
    DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "wave"; title: qsTr("Microphone upstream"); description: qsTr("Microphone upstream is unavailable for NVST sessions"); value: qsTr("UNAVAILABLE") }
    DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "info"; title: qsTr("Audio format"); description: qsTr("Audio format and channel count are negotiated with the active GeForce NOW session."); showDivider: false }
}
