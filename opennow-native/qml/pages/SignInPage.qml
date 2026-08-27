import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    signal signedIn()
    property int providerIndex: 0
    readonly property var providers: authEngine.providers
    readonly property var displayCode: {
        if (authEngine.userCode.length === 0)
            return ["", "", "", "—", "", "", ""]
        var result = []
        var midpoint = Math.ceil(authEngine.userCode.length / 2)
        for (var i = 0; i < authEngine.userCode.length; ++i) {
            if (i === midpoint)
                result.push("—")
            result.push(authEngine.userCode.charAt(i))
        }
        return result
    }

    function cycleProvider(delta) {
        providerIndex = (providerIndex + delta + providers.length) % providers.length
        authEngine.selectProvider(providerIndex)
    }

    function refreshCode() {
        authEngine.startLogin()
    }

    function signInWithBrowser() {
        authEngine.startBrowserLogin()
    }

    function verificationHost() {
        if (authEngine.verificationUrl.length === 0)
            return providers[providerIndex].url
        return authEngine.verificationUrl.replace(/^https?:\/\//, "").split(/[\/?#]/)[0]
    }

    Shortcut { sequence: "Y"; enabled: page.visible; onActivated: page.refreshCode() }
    Shortcut { sequence: "Ctrl+Left"; enabled: page.visible; onActivated: page.cycleProvider(-1) }
    Shortcut { sequence: "Ctrl+Right"; enabled: page.visible; onActivated: page.cycleProvider(1) }
    Connections {
        target: authEngine
        function onSelectedProviderChanged() { page.providerIndex = authEngine.selectedProviderIndex }
    }

    Rectangle {
        anchors.fill: parent
        color: Theme.canvas
    }

    Row {
        x: 48
        y: 28
        spacing: 16
        BrandMark { width: 36; height: 36 }
        Text {
            anchors.verticalCenter: parent.verticalCenter
            text: "OPENNOW"
            color: Theme.ink
            font.family: Theme.bodyFont.family
            font.pixelSize: 18
            font.weight: Font.Bold
        }
    }

    Text {
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.rightMargin: 48
        anchors.topMargin: 42
        text: "OpenNOW " + Qt.application.version
        color: Theme.inkMuted
        font.family: Theme.monoFont.family
        font.pixelSize: 13
        font.letterSpacing: 1.4
    }

    Column {
        id: content
        width: 720
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.top: parent.top
        anchors.topMargin: 134
        spacing: 0

        Text {
            text: "Sign in"
            color: Theme.ink
            font.family: Theme.displayFont.family
            font.pixelSize: 38
            font.weight: Font.DemiBold
        }
        Text {
            topPadding: 12
            text: "Pick your GeForce NOW provider, then pair this device."
            color: Theme.inkSoft
            font.family: Theme.bodyFont.family
            font.pixelSize: 16
        }

        Item { width: 1; height: 38 }

        RowLayout {
            width: parent.width
            Text {
                text: "SERVICE PROVIDER"
                color: Theme.inkMuted
                font.family: Theme.monoFont.family
                font.pixelSize: 11
                font.weight: Font.Bold
                font.letterSpacing: 2.2
            }
            Item { Layout.fillWidth: true }
            KeyHint { keyText: "LB"; label: ""; scale: 0.78 }
            KeyHint { keyText: "RB"; label: "Switch"; scale: 0.78 }
        }

        Item { width: 1; height: 9 }

        Button {
            id: providerButton
            width: parent.width
            height: 64
            focusPolicy: Qt.StrongFocus
            onClicked: providerPopup.open()
            KeyNavigation.down: browserButton

            contentItem: RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 18
                anchors.rightMargin: 18
                spacing: 15
                Rectangle {
                    width: 38
                    height: 38
                    radius: 9
                    color: Theme.accent
                    Text { anchors.centerIn: parent; text: page.providers[page.providerIndex].mark; color: Theme.accentInk; font.family: Theme.monoFont.family; font.pixelSize: 13; font.weight: Font.Bold }
                }
                Column {
                    spacing: 3
                    Text { text: page.providers[page.providerIndex].name; color: Theme.ink; font.family: Theme.bodyFont.family; font.pixelSize: 15; font.weight: Font.Bold }
                    Text { text: page.providers[page.providerIndex].detail; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10 }
                }
                Item { Layout.fillWidth: true }
                Text { text: Math.max(0, page.providers.length - 1) + " more ⌄"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10 }
            }
            background: Rectangle {
                radius: 12
                color: "#0e120f"
                border.width: providerButton.activeFocus || providerPopup.opened ? 2 : 1
                border.color: providerButton.activeFocus || providerPopup.opened ? Theme.accent : "#233028"
            }

            Popup {
                id: providerPopup
                parent: providerButton
                z: 1000
                scale: page.Window.window ? page.Window.window.presentationScale : 1
                transformOrigin: Item.TopLeft
                x: 0
                y: providerButton.height + 8
                width: providerButton.width
                height: 320
                focus: true
                modal: true
                padding: 6
                closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
                background: Rectangle { radius: 12; color: "#0e120f"; border.width: 1; border.color: "#233028" }
                contentItem: ListView {
                    id: providerList
                    clip: true
                    focus: true
                    model: page.providers
                    currentIndex: page.providerIndex
                    Keys.onUpPressed: currentIndex = (currentIndex + count - 1) % count
                    Keys.onDownPressed: currentIndex = (currentIndex + 1) % count
                    Keys.onReturnPressed: {
                        page.providerIndex = currentIndex
                        authEngine.selectProvider(currentIndex)
                        providerPopup.close()
                        providerButton.forceActiveFocus()
                    }
                    delegate: ItemDelegate {
                        id: providerDelegate
                        required property var modelData
                        required property int index
                        width: ListView.view.width
                        height: 48
                        text: modelData.name
                        font.family: Theme.bodyFont.family
                        font.pixelSize: 13
                        highlighted: index === page.providerIndex
                        onClicked: {
                            page.providerIndex = index
                            authEngine.selectProvider(index)
                            providerPopup.close()
                            providerButton.forceActiveFocus()
                        }
                        contentItem: Text { text: providerDelegate.text; color: providerDelegate.highlighted ? Theme.accent : Theme.ink; font: providerDelegate.font; verticalAlignment: Text.AlignVCenter; leftPadding: 12 }
                        background: Rectangle { radius: 8; color: providerDelegate.highlighted ? "#15241a" : (providerDelegate.hovered ? Theme.surfaceBright : "transparent") }
                    }
                }
                onOpened: providerList.forceActiveFocus()
            }
        }
        Text {
            topPadding: 10
            text: "A opens the list — alliance partners like LG U+, Taiwan Mobile and bro.game run their own rigs."
            color: "#4d5750"
            font.family: Theme.bodyFont.family
            font.pixelSize: 12
        }

        Item { width: 1; height: 39 }

        Rectangle {
            width: parent.width
            height: 304
            radius: 16
            color: "#0e120f"
            border.width: 1
            border.color: "#202a24"

            Column {
                anchors.fill: parent
                anchors.margins: 40
                spacing: 31
                RowLayout {
                    width: parent.width
                    Text {
                        text: page.providers[page.providerIndex].login + " DEVICE LOGIN"
                        color: Theme.accent
                        font.family: Theme.monoFont.family
                        font.pixelSize: 12
                        font.weight: Font.Bold
                        font.letterSpacing: 2.4
                    }
                    Item { Layout.fillWidth: true }
                    Rectangle { width: 8; height: 8; radius: 4; color: Theme.accent }
                    Text { text: authEngine.statusText; color: Theme.inkSoft; font.family: Theme.bodyFont.family; font.pixelSize: 12 }
                }
                Row {
                    spacing: 14
                    Repeater {
                        model: page.displayCode
                        Item {
                            required property string modelData
                            width: modelData === "—" ? 22 : (page.displayCode.length > 7 ? 58 : 76)
                            height: 96
                            Rectangle {
                                visible: modelData !== "—"
                                anchors.fill: parent
                                radius: 12
                                color: Theme.canvas
                                border.width: 1
                                border.color: "#25332b"
                            }
                            Text {
                                anchors.centerIn: parent
                                text: modelData
                                color: modelData === "—" ? "#465149" : Theme.accent
                                font.family: Theme.monoFont.family
                                font.pixelSize: modelData === "—" ? 28 : 45
                                font.weight: Font.Bold
                            }
                        }
                    }
                }
                Text {
                    width: parent.width
                    text: "On any phone or PC, open " + page.verificationHost() + " and enter this code. Switching\nprovider issues a new code."
                    color: Theme.inkSoft
                    font.family: Theme.bodyFont.family
                    font.pixelSize: 14
                    lineHeight: 1.45
                }
            }
        }

        Item { width: 1; height: 32 }

        RowLayout {
            width: parent.width
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.divider }
            Text { text: "OR ON THIS DEVICE"; color: Theme.inkMuted; font.family: Theme.bodyFont.family; font.pixelSize: 12; font.weight: Font.Bold; leftPadding: 18; rightPadding: 18; font.letterSpacing: 1.2 }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.divider }
        }

        Item { width: 1; height: 38 }

        ActionButton {
            id: browserButton
            x: -6
            width: parent.width + 12
            height: 84
            text: "Sign in with browser"
            glyph: "⊕"
            primary: true
            focus: page.visible
            KeyNavigation.up: providerButton
            onClicked: page.signInWithBrowser()
        }

        Item { width: 1; height: 24 }

        Text {
            width: parent.width
            text: "Sign-in happens on NVIDIA's or your alliance partner's own page — OpenNOW never sees your password.\nNVIDIA and GeForce NOW are trademarks of NVIDIA Corporation."
            color: "#59635c"
            font.family: Theme.bodyFont.family
            font.pixelSize: 12
            lineHeight: 1.5
        }
    }

    Rectangle {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: 80
        color: Theme.canvas
        border.width: 0
        Rectangle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; height: 1; color: Theme.divider }
        Text {
            anchors.left: parent.left
            anchors.verticalCenter: parent.verticalCenter
            anchors.leftMargin: 48
            text: "nvst: idle · session: none"
            color: "#455048"
            font.family: Theme.monoFont.family
            font.pixelSize: 11
            font.letterSpacing: 1.2
        }
        Row {
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            anchors.rightMargin: 48
            spacing: 24
            KeyHint { keyText: "A"; label: "Select" }
            KeyHint { keyText: "Y"; label: "Refresh code" }
            KeyHint { keyText: "ENTER"; label: "Keyboard OK" }
        }
    }
}
