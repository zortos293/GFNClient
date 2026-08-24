import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    signal profileSelected(string name)
    signal addAccount()
    signal manageProfiles()
    signal back()
    property int currentIndex: 0
    property bool pinVisible: false
    property string pinError: ""
    readonly property var profiles: [
        { kind: "profile", badge: "PLAYER 1", initial: appState.profileInitial, name: appState.profileName, tier: "ULTIMATE", detail: "SIGNED IN", locked: true },
        { kind: "profile", badge: "PLAYER 2", initial: "M", name: "Mika", tier: "FREE", detail: "PIN REQUIRED", locked: true },
        { kind: "add", badge: "", initial: "+", name: "Add account", tier: "", detail: "SIGN IN WITH NVIDIA", locked: false },
        { kind: "manage", badge: "", initial: "⚙", name: "Manage profiles", tier: "", detail: "PINS · ACCOUNTS", locked: false }
    ]

    function activate(index) {
        var entry = profiles[index]
        if (entry.kind === "profile" && index === 1) {
            pinVisible = true
            pinError = ""
            Qt.callLater(function() { pinField.forceActiveFocus() })
        } else if (entry.kind === "profile")
            profileSelected(entry.name)
        else if (entry.kind === "add")
            addAccount()
        else
            manageProfiles()
    }

    Keys.onLeftPressed: currentIndex = (currentIndex + profiles.length - 1) % profiles.length
    Keys.onRightPressed: currentIndex = (currentIndex + 1) % profiles.length
    Keys.onReturnPressed: activate(currentIndex)
    Keys.onEscapePressed: {
        if (pinVisible) {
            pinVisible = false
            forceActiveFocus()
        } else {
            back()
        }
    }
    Component.onCompleted: forceActiveFocus()

    Rectangle { anchors.fill: parent; color: Theme.canvas }

    Column {
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.leftMargin: 112
        anchors.topMargin: 72
        spacing: 4
        Text { text: "CONTROLLER MODE"; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 11; font.weight: Font.Bold; font.letterSpacing: 1.8 }
        Text { text: (controllerInput.connected ? controllerInput.controllerName : "Keyboard") + " · Player 1"; color: Theme.inkSoft; font.family: Theme.bodyFont.family; font.pixelSize: 13 }
    }

    Rectangle {
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.rightMargin: 112
        anchors.topMargin: 76
        width: connectedLabel.implicitWidth + 42
        height: 38
        radius: 19
        color: Theme.surfaceRaised
        border.color: Theme.divider
        Row {
            anchors.centerIn: parent
            spacing: 10
            Rectangle { anchors.verticalCenter: parent.verticalCenter; width: 8; height: 8; radius: 4; color: Theme.accent }
            Text { id: connectedLabel; text: (controllerInput.connected ? controllerInput.controllerCount + (controllerInput.controllerCount === 1 ? " CONTROLLER" : " CONTROLLERS") : "KEYBOARD") + " CONNECTED"; color: Theme.inkSoft; font.family: Theme.bodyFont.family; font.pixelSize: 11; font.weight: Font.Bold }
        }
    }

    Column {
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.leftMargin: 215
        anchors.topMargin: 168
        spacing: 8
        Text { text: "Who’s playing?"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 52; font.weight: Font.Light }
        Text { text: "Choose a profile to continue. Focus follows the active controller."; color: Theme.inkSoft; font.family: Theme.bodyFont.family; font.pixelSize: 16 }
    }

    Text {
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.rightMargin: 215
        anchors.topMargin: 246
        text: "PROFILE GATE · TV SAFE AREA"
        color: Theme.inkMuted
        font.family: Theme.monoFont.family
        font.pixelSize: 10
        font.letterSpacing: 1.5
    }

    Row {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.leftMargin: 382
        anchors.rightMargin: 382
        anchors.topMargin: 296
        spacing: 30

        Repeater {
            model: page.profiles
            Column {
                required property var modelData
                required property int index
                width: 264
                spacing: 12
                Button {
                    id: card
                    width: parent.width
                    height: 264
                    hoverEnabled: true
                    focusPolicy: Qt.StrongFocus
                    onClicked: { page.currentIndex = index; page.activate(index) }
                    onActiveFocusChanged: { if (activeFocus) page.currentIndex = index }
                    Keys.onLeftPressed: page.currentIndex = (index + page.profiles.length - 1) % page.profiles.length
                    Keys.onRightPressed: page.currentIndex = (index + 1) % page.profiles.length
                    Keys.onReturnPressed: page.activate(index)
                    contentItem: Item {
                        Rectangle {
                            visible: modelData.badge.length > 0
                            x: 16; y: 16
                            width: badgeText.implicitWidth + 18; height: 26; radius: 13
                            color: index === 0 ? Theme.accent : "#273029"
                            Text { id: badgeText; anchors.centerIn: parent; text: modelData.badge; color: index === 0 ? Theme.accentInk : Theme.inkSoft; font.family: Theme.monoFont.family; font.pixelSize: 9; font.weight: Font.Bold }
                        }
                        Text {
                            anchors.centerIn: parent
                            text: modelData.initial
                            color: index === 1 ? Theme.inkSoft : Theme.ink
                            font.family: Theme.displayFont.family
                            font.pixelSize: modelData.kind === "manage" ? 48 : (modelData.kind === "add" ? 42 : 82)
                            font.weight: Font.Light
                        }
                        Rectangle {
                            visible: modelData.locked
                            anchors.right: parent.right; anchors.bottom: parent.bottom
                            anchors.margins: 16
                            width: 32; height: 32; radius: 16
                            color: Theme.canvas; border.color: "#39443d"
                            Text { anchors.centerIn: parent; text: "▣"; color: Theme.inkSoft; font.pixelSize: 12 }
                        }
                    }
                    background: Rectangle {
                        radius: 18
                        color: modelData.kind === "profile" ? (index === 0 ? "#17211b" : "#1a1d1b") : Theme.surfaceRaised
                        border.width: page.currentIndex === index || card.activeFocus ? 3 : 1
                        border.color: page.currentIndex === index || card.activeFocus ? Theme.accent : (modelData.kind === "add" ? "#3a493f" : "#313b34")
                        Rectangle { visible: page.currentIndex === index || card.activeFocus; anchors.fill: parent; anchors.margins: -7; radius: 24; color: "transparent"; border.width: 6; border.color: "#273f2f"; z: -1 }
                    }
                }
                Text { anchors.horizontalCenter: parent.horizontalCenter; text: modelData.name; color: Theme.ink; font.family: Theme.bodyFont.family; font.pixelSize: 19; font.weight: Font.Bold }
                Row {
                    anchors.horizontalCenter: parent.horizontalCenter
                    spacing: 6
                    Text { text: modelData.tier; color: modelData.tier === "ULTIMATE" ? Theme.accent : Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10; font.weight: Font.Bold }
                    Text { visible: modelData.tier.length > 0; text: "·"; color: Theme.inkMuted; font.pixelSize: 10 }
                    Text { text: modelData.detail; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10; font.weight: Font.Bold }
                }
            }
        }
    }

    Rectangle {
        anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
        anchors.leftMargin: 215; anchors.rightMargin: 215; anchors.bottomMargin: 122
        height: 1; color: Theme.divider
    }
    Row {
        anchors.left: parent.left; anchors.bottom: parent.bottom
        anchors.leftMargin: 215; anchors.bottomMargin: 76; spacing: 28
        KeyHint { keyText: "A"; label: "Select" }
        KeyHint { keyText: "B"; label: "Back" }
        KeyHint { keyText: "☰"; label: "Controller settings" }
    }
    Text {
        anchors.right: parent.right; anchors.bottom: parent.bottom
        anchors.rightMargin: 215; anchors.bottomMargin: 82
        text: "LEFT STICK / D-PAD TO MOVE"
        color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10; font.letterSpacing: 1.3
    }

    Rectangle {
        anchors.fill: parent
        visible: page.pinVisible
        color: "#c4050806"
        z: 20
        MouseArea { anchors.fill: parent }
        Rectangle {
            anchors.centerIn: parent
            width: 480; height: 262; radius: 16
            color: Theme.surfaceRaised; border.color: page.pinError.length > 0 ? Theme.error : "#2b3730"
            Column {
                anchors.fill: parent; anchors.margins: 28; spacing: 15
                Text { text: "Unlock Mika"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 24; font.weight: Font.DemiBold }
                Text { text: "Enter the four-digit profile PIN."; color: Theme.inkSoft; font.family: Theme.bodyFont.family; font.pixelSize: 13 }
                TextField {
                    id: pinField
                    width: parent.width; height: 54
                    echoMode: TextInput.Password
                    inputMethodHints: Qt.ImhDigitsOnly
                    maximumLength: 4
                    color: Theme.ink
                    font.family: Theme.monoFont.family; font.pixelSize: 22; font.letterSpacing: 10
                    horizontalAlignment: Text.AlignHCenter
                    Keys.onReturnPressed: unlockButton.clicked()
                    background: Rectangle { radius: 9; color: Theme.canvas; border.width: pinField.activeFocus ? 2 : 1; border.color: pinField.activeFocus ? Theme.accent : Theme.divider }
                }
                Text { visible: page.pinError.length > 0; text: page.pinError; color: Theme.error; font.family: Theme.bodyFont.family; font.pixelSize: 11 }
                Row {
                    anchors.right: parent.right; spacing: 10
                    ActionButton { text: "Cancel"; onClicked: { page.pinVisible = false; pinField.text = ""; page.forceActiveFocus() } }
                    ActionButton {
                        id: unlockButton
                        text: "Unlock"
                        primary: true
                        onClicked: {
                            if (pinField.text === "2468") {
                                page.pinVisible = false
                                pinField.text = ""
                                page.profileSelected("Mika")
                            } else {
                                page.pinError = "Incorrect PIN. Try 2468 in this prototype."
                                pinField.selectAll()
                            }
                        }
                    }
                }
            }
        }
    }
}
