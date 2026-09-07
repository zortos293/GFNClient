import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

Column {
    id: controlsRoot
    required property real availableWidth
    required property var settingsScreen
    required property Component controllersPageComponent
    required property Component shortcutsPageComponent

    property bool shortcutsOpen: controlsRoot.settingsScreen.selectedSection === 10
    width: controlsRoot.availableWidth; spacing: 20
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("CONTROLLERS") }
        Repeater {
            model: ControllerInput.controllers
            delegate: DesktopSettingsRow {
                required property var modelData
                width: parent.width; paperStyle: true; glyph: "controller"; title: modelData.name
                description: qsTr("Player %1").arg(modelData.slot) + (modelData.batteryPercent >= 0 ? " · " + modelData.batteryPercent + "%" : "")
                DesktopSettingsButton { text: "P" + modelData.slot; onClicked: AppController.navigate("joining") }
            }
        }
        DesktopSettingsRow {
            visible: ControllerInput.controllers.length === 0; width: parent.width; paperStyle: true; glyph: "controller"
            title: qsTr("No controllers connected"); description: qsTr("Connect a controller to assign a player")
            DesktopSettingsButton { text: qsTr("Controller order"); onClicked: AppController.navigate("joining") }
        }
        DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "globe"; title: qsTr("Gyroscope"); description: qsTr("Motion aiming on supported pads")
            DesktopSettingsToggle { checked: controlsRoot.settingsScreen.boolSetting("enableGyroscopeControls",false); onValueChangedByUser: value => controlsRoot.settingsScreen.setSetting("enableGyroscopeControls",value) }
        }
        DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("Steam Input compatibility"); description: qsTr("Use the compatibility path for Steam-managed controllers"); showDivider: false
            DesktopSettingsToggle { checked: controlsRoot.settingsScreen.boolSetting("steamControllerCompatibilityMode",false); onValueChangedByUser: value => controlsRoot.settingsScreen.setSetting("steamControllerCompatibilityMode",value) }
        }
    }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("MOUSE & KEYBOARD") }
        DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "mouse"; title: qsTr("Mouse capture"); description: qsTr("Follows the remote cursor · F8 toggles capture")
            DesktopSettingsSegmented { options: [qsTr("Automatic")]; optionWidth: 112; selectedIndex: 0 }
        }
        DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "arrows"; title: qsTr("Mouse sensitivity"); description: qsTr("Applied to native relative mouse input")
            DesktopSettingsSlider { trackWidth: 320; from: 0.1; to: 3; stepSize: 0.05; decimals: 2; suffix: "×"; value: Number(controlsRoot.settingsScreen.valueSetting("mouseSensitivity",1)); onCommitted: value => controlsRoot.settingsScreen.setSetting("mouseSensitivity",value) }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "keyboard"; title: qsTr("Shortcuts")
            objectName: "renewShortcutsDisclosure"
            description: qsTr("Local shortcuts are consumed before gameplay input"); showDivider: false; expandable: true
            expanded: controlsRoot.shortcutsOpen
            onExpansionRequested: controlsRoot.shortcutsOpen = !controlsRoot.shortcutsOpen
            Row { spacing: 10
                DesktopKeyHint { keyText: String(controlsRoot.settingsScreen.valueSetting("shortcutToggleStats","Ctrl+N")); label: qsTr("stats") }
                DesktopKeyHint { keyText: "Ctrl G"; label: qsTr("menu") }
                DesktopKeyHint { keyText: "F11"; label: qsTr("fullscreen") }
            }
        }
    }
    DesktopSettingsDisclosure { objectName: "renewInlineShortcuts"; width: parent.width; expanded: controlsRoot.shortcutsOpen; sourceComponent: controlsRoot.shortcutsPageComponent }
    DesktopSettingsDisclosure {
        width: parent.width; expanded: controlsRoot.shortcutsOpen
        sourceComponent: DesktopSettingsPanel {
            width: controlsRoot.availableWidth; paperStyle: true
            DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "keyboard"; title: qsTr("Statistics shortcut"); showDivider: false
                DesktopSettingsField { width: DesktopTokens.px(200); text: String(controlsRoot.settingsScreen.valueSetting("shortcutToggleStats","Ctrl+N")); Accessible.name: qsTr("Statistics shortcut"); onEditingFinished: controlsRoot.settingsScreen.setSetting("shortcutToggleStats",text) }
            }
        }
    }
    DesktopSettingsAdvanced { detail: qsTr("Controller behavior · Cursor"); expanded: controlsRoot.settingsScreen.advancedOpen; onClicked: controlsRoot.settingsScreen.advancedOpen = !controlsRoot.settingsScreen.advancedOpen }
    DesktopSettingsDisclosure { width: parent.width; expanded: controlsRoot.settingsScreen.advancedOpen; sourceComponent: controlsRoot.controllersPageComponent }
    DesktopSettingsDisclosure {
        width: parent.width; expanded: controlsRoot.settingsScreen.advancedOpen
        sourceComponent: DesktopSettingsPanel {
            width: controlsRoot.availableWidth; paperStyle: true
            DesktopSettingsSection { text: qsTr("KEYBOARD & CURSOR") }
            DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "mouse"; title: qsTr("Cursor overlay"); showDivider: false
                DesktopSettingsToggle { checked: controlsRoot.settingsScreen.boolSetting("nativeCursorOverlay",true); onValueChangedByUser: value => controlsRoot.settingsScreen.setSetting("nativeCursorOverlay",value) }
            }
        }
    }
}
