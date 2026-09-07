import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

Column {
    id: page
    required property real availableWidth
    required property var settingsScreen

    width: page.availableWidth; spacing: 20
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("OPENNOW") }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true
            leadingIcon: "qrc:/qt/qml/OpenNOW/res/brand/opennow-mark.png"
            title: "OpenNOW " + String(ShellStore.updaterState.currentVersion || qsTr("unknown"))
            description: qsTr("Your games, anywhere.")
            DesktopSettingsButton { text: ShellStore.updaterState.status === "checking" ? qsTr("Checking…") : qsTr("Check for updates"); primary: true; enabled: ShellStore.updaterState.status !== "checking"; onClicked: ShellStore.checkForUpdates() }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "arrows"; title: qsTr("Update channel")
            description: qsTr("Choose which releases OpenNOW checks")
            DesktopSettingsSegmented {
                options: [{label:qsTr("Stable"),value:"stable"},{label:qsTr("Nightly"),value:"nightly"}]
                objectName: "renewUpdateChannel"
                optionWidth: 96; selectedIndex: options.findIndex(item => item.value === page.settingsScreen.valueSetting("updateChannel","stable"))
                onSelected: (index,item) => page.settingsScreen.setChoice("updateChannel",item.value)
            }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "info"; title: qsTr("Update status")
            description: String(ShellStore.updaterState.message || ShellStore.updaterState.status || qsTr("idle"))
            showDivider: false
            DesktopSettingsButton { text: qsTr("Updates"); onClicked: AppController.navigate("updates") }
        }
    }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("RELEASE NOTES") }
        Column {
            x: DesktopTokens.px(20); width: parent.width-DesktopTokens.px(40); spacing: 8
            Text { width: parent.width; text: ShellStore.releaseHighlights.title || qsTr("Release notes"); color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(15); font.weight: Font.ExtraBold; wrapMode: Text.WordWrap; textFormat: Text.PlainText }
            ReleaseNotes { width: parent.width; bottomPadding: 20; text: ShellStore.releaseHighlights.bodyMarkdown || qsTr("Check for updates to load verified release information from GitHub."); font.pixelSize: DesktopTokens.px(13) }
        }
    }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("PROJECT & DIAGNOSTICS") }
        Repeater {
            model: page.settingsScreen.projectLinks()
            delegate: DesktopSettingsRow {
                required property var modelData
                required property int index
                width: parent.width; paperStyle: true; glyph: modelData.id === "diagnostics" ? "wave" : modelData.id === "captures" ? "image" : "globe"
                title: modelData.label
                description: modelData.id === "diagnostics" ? qsTr("Generate a diagnostic report") : ""
                showDivider: index < 3
                DesktopSettingsButton { text: modelData.id === "diagnostics" ? qsTr("Export") : qsTr("Open"); onClicked: page.settingsScreen.runProjectLink(modelData) }
            }
        }
    }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "info"; title: qsTr("Independent client")
            description: qsTr("OpenNOW is not affiliated with, endorsed by or supported by NVIDIA. GeForce NOW is a trademark of NVIDIA Corporation. You bring your own account and subscription.")
            showDivider: false
        }
    }
}
