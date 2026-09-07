import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

Column {
    id: page
    required property real availableWidth
    required property var settingsScreen
    required property Component statsSettingsPageComponent
    required property Component interfacePageComponent

    width: page.availableWidth; spacing: 20
    property bool allThemes: false
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("THEME") }
        DesktopSettingsChoice {
            objectName: "renewThemeChoice"
            width: parent.width; glyph: "moon"; title: qsTr("Theme")
            description: qsTr("Applies the pack's appearance, accent and surfaces")
            items: ["aurora","nocturne","kraft","phosphor","hibiscus","chapel","bone","cobalt"].map(id => ({label:page.settingsScreen.themeMeta(id).name,detail:page.settingsScreen.themeMeta(id).blurb,value:id}))
            value: page.settingsScreen.valueSetting("themePack","nocturne")
            onSelected: value => page.settingsScreen.setChoice("themePack",value)
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "moon"; title: qsTr("Appearance")
            DesktopSettingsSegmented {
                options: [{label:qsTr("Auto"),value:"auto"},{label:qsTr("Light"),value:"light"},{label:qsTr("Dark"),value:"dark"}]
                selectedIndex: options.findIndex(item => item.value === page.settingsScreen.valueSetting("appTheme","auto"))
                onSelected: (index,item) => page.settingsScreen.setSetting("appTheme",item.value)
            }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "palette"; title: qsTr("Accent"); description: qsTr("Selection, toggles and keyboard focus")
            Row {
                spacing: 10
                DesktopSettingsButton {
                    objectName: "themePackAccent"
                    text: qsTr("Theme")
                    compact: true
                    primary: !Theme.accentOverridden
                    onClicked: page.settingsScreen.setSetting("themeAccentOverride", false)
                }
                Repeater {
                    model: Theme.accentChoices
                    delegate: AbstractButton {
                        required property string modelData
                        objectName: "themeAccent-" + modelData
                        width: 28; height: 28; Accessible.name: modelData
                        checked: Theme.accentOverridden && Theme.accent === modelData
                        onClicked: page.settingsScreen.setChoice("appAccentColor",modelData)
                        background: Rectangle {
                            radius: 14; color: Theme.accentColor(parent.modelData)
                            border.width: parent.activeFocus || parent.checked ? 3 : 0
                            border.color: Theme.label
                        }
                    }
                }
            }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "image"; title: qsTr("Background"); description: qsTr("Artwork, gradient or a solid theme color"); showDivider: false
            DesktopSettingsSegmented {
                options: [{label:qsTr("Game art"),value:"art"},{label:qsTr("Gradient"),value:"gradient"},{label:qsTr("Solid"),value:"solid"}]; optionWidth: 80
                selectedIndex: options.findIndex(item => item.value === page.settingsScreen.valueSetting("desktopBackground","art"))
                onSelected: (index,item) => page.settingsScreen.setSetting("desktopBackground",item.value)
            }
        }
    }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("LAYOUT") }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "grid"; title: qsTr("Library tiles"); description: qsTr("How much art you see per row")
            DesktopSettingsSegmented {
                options: [qsTr("Compact"),qsTr("Cozy"),qsTr("Large")]; optionWidth: 72
                selectedIndex: Number(page.settingsScreen.valueSetting("posterSizeScale",1.05)) < 1 ? 0 : Number(page.settingsScreen.valueSetting("posterSizeScale",1.05)) > 1.1 ? 2 : 1
                onSelected: index => page.settingsScreen.setSetting("posterSizeScale",[0.9,1.05,1.25][index])
            }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "sidebar"; title: qsTr("Sidebar opens on hover"); description: qsTr("Expands over the page without moving it")
            DesktopSettingsToggle { checked: page.settingsScreen.boolSetting("desktopSidebarHover",true); onValueChangedByUser: value => page.settingsScreen.setSetting("desktopSidebarHover",value) }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "clock"; title: qsTr("Session clock in stream"); description: qsTr("Small timer while playing"); showDivider: false
            DesktopSettingsToggle { checked: page.settingsScreen.boolSetting("sessionCounterEnabled",false); onValueChangedByUser: value => page.settingsScreen.setSetting("sessionCounterEnabled",value) }
        }
    }
    Loader { width: parent.width; sourceComponent: page.statsSettingsPageComponent }
    DesktopSettingsAdvanced { detail: qsTr("Language · Interface scale · Motion"); expanded: page.settingsScreen.advancedOpen; onClicked: page.settingsScreen.advancedOpen = !page.settingsScreen.advancedOpen }
    DesktopSettingsDisclosure {
        width: parent.width; expanded: page.settingsScreen.advancedOpen
        sourceComponent: DesktopSettingsPanel {
            width: page.availableWidth; paperStyle: true
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "grid"; title: qsTr("Interface scale"); showDivider: false
                DesktopSettingsSlider { from: 0.85; to: 1.25; stepSize: 0.05; decimals: 2; suffix: "×"; value: Number(page.settingsScreen.valueSetting("desktopUiScale",1)); onCommitted: value => page.settingsScreen.setSetting("desktopUiScale",value) }
            }
        }
    }
    DesktopSettingsDisclosure { width: parent.width; expanded: page.settingsScreen.advancedOpen; sourceComponent: page.interfacePageComponent }
}
