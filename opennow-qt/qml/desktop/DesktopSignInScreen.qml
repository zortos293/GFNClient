pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    property bool providerOpen: false
    property bool qrRequested: false
    property bool staySignedIn: true
    property double clockMs: Date.now()
    readonly property var challenge: ShellStore.authChallenge
    readonly property var providers: ShellStore.providers && ShellStore.providers.length
        ? ShellStore.providers : [{displayName:"NVIDIA · GeForce NOW", idpId:"", region:"GLOBAL"}]
    readonly property bool waiting: ShellStore.authState === "starting" || ShellStore.authState === "waiting" || ShellStore.authState === "completing"
    readonly property bool failed: ShellStore.authState === "error"
    signal signedIn()
    signal offlineRequested()

    width: 1440; height: 900; focus: true
    Timer { interval: 1000; repeat: true; running: root.challenge !== null; onTriggered: root.clockMs = Date.now() }

    DesktopBackdrop { anchors.fill: parent; artwork: ShellStore.catalogGames.length ? String(ShellStore.catalogGames[0].heroImageUrl || ShellStore.catalogGames[0].imageUrl || "") : "" }
    Rectangle { anchors.fill: parent; color: "#4D020713" }

    Row {
        x: 626; y: 132; spacing: 10
        Rectangle { width: 26; height: 14; radius: 7; color: "#68E341"; Text { anchors.centerIn: parent; text: "☁"; color: "#11320C"; font.pixelSize: 11; font.weight: Font.Black } }
        Text { text: "OpenNOW"; color: DesktopTokens.text; font.family: DesktopTokens.displayFont; font.pixelSize: 18; font.weight: Font.Black }
        Rectangle { anchors.verticalCenter: parent.verticalCenter; width: 46; height: 20; radius: 7; color: "#12FFFFFF"; Text { anchors.centerIn: parent; text: Qt.application.version || "1.0.0"; color: DesktopTokens.textMuted; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold } }
    }

    Rectangle {
        id: card
        x: 484
        y: root.failed ? 196 : (root.waiting ? 174 : 178)
        width: 472
        height: root.failed ? 507 : (root.waiting && root.qrRequested ? 551 : root.waiting ? 560 : 533)
        radius: 16
        color: "#EB0D121D"
        border.width: 1
        border.color: "#1FFFFFFF"
        clip: false
        Behavior on y { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic } }
        Behavior on height { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic } }

        Column {
            x: 33; y: 32; width: 406; spacing: 0

            Rectangle {
                width: statusText.implicitWidth + 28; height: 24; radius: 8
                color: root.failed ? "#14FF8A80" : root.waiting ? "#147FD4FF" : "#14FFD166"
                border.width: 1; border.color: root.failed ? "#40FF8A80" : root.waiting ? "#407FD4FF" : "#40FFD166"
                Text { id: statusText; anchors.centerIn: parent; text: root.failed ? qsTr("SIGN IN FAILED") : root.waiting ? qsTr("WAITING FOR APPROVAL") : qsTr("NOT SIGNED IN"); color: root.failed ? DesktopTokens.danger : root.waiting ? DesktopTokens.focus : DesktopTokens.amber; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 0.8 }
            }
            Item { width: 1; height: 12 }
            Text {
                width: parent.width
                text: root.failed ? qsTr("We could not finish sign in")
                    : root.waiting ? (root.qrRequested ? qsTr("Scan to sign in") : qsTr("Finish in your browser"))
                    : qsTr("Sign in to continue")
                color: DesktopTokens.text
                font.family: DesktopTokens.displayFont; font.pixelSize: 28; font.weight: Font.Black; font.letterSpacing: -0.6
            }
            Item { width: 1; height: 7 }
            Text {
                width: parent.width; wrapMode: Text.WordWrap
                text: root.failed ? (ShellStore.authMessage || qsTr("Your provider returned without a usable session. Nothing was saved."))
                    : root.waiting ? (root.qrRequested ? qsTr("Scan with your phone and approve the request on your provider.") : qsTr("A secure provider page is open. Approve it there and return to OpenNOW."))
                    : qsTr("Nothing works until your provider tells us who you are.")
                color: DesktopTokens.textMuted
                font.family: DesktopTokens.bodyFont; font.pixelSize: 13; font.weight: Font.Medium; lineHeight: 1.45
            }

            Item { width: 1; height: root.failed ? 22 : 24 }

            Column {
                width: parent.width; spacing: 9; visible: !root.waiting && !root.failed
                Text { text: qsTr("PROVIDER"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1 }
                ItemDelegate {
                    id: providerButton
                    width: parent.width; height: 60; padding: 0
                    background: Rectangle { radius: 12; color: providerButton.hovered || providerButton.activeFocus ? "#14FFFFFF" : "#0DFFFFFF"; border.width: 1; border.color: "#29FFFFFF" }
                    contentItem: Item {
                        Rectangle { x: 12; anchors.verticalCenter: parent.verticalCenter; width: 36; height: 36; radius: 9; color: DesktopTokens.text; Text { anchors.centerIn: parent; text: "NV"; color: DesktopTokens.shell; font.family: DesktopTokens.monoFont; font.pixelSize: 10; font.weight: Font.Black } }
                        Column { x: 61; anchors.verticalCenter: parent.verticalCenter; spacing: 3
                            Text { text: String(root.providers[0].displayName || "NVIDIA · GeForce NOW"); color: DesktopTokens.textHigh; font.family: DesktopTokens.bodyFont; font.pixelSize: 13; font.weight: Font.Bold }
                            Text { text: String(root.providers[0].region || "GLOBAL").toUpperCase() + qsTr("  ·  DEFAULT PROVIDER"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: 0.6 }
                        }
                        Text { anchors.right: parent.right; anchors.rightMargin: 36; anchors.verticalCenter: parent.verticalCenter; text: root.providers.length > 1 ? qsTr("%1 MORE").arg(root.providers.length - 1) : ""; color: DesktopTokens.textMuted; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold }
                        Text { anchors.right: parent.right; anchors.rightMargin: 13; anchors.verticalCenter: parent.verticalCenter; text: root.providerOpen ? "⌃" : "⌄"; color: DesktopTokens.textMuted; font.pixelSize: 14 }
                    }
                    Component.onCompleted: forceActiveFocus()
                    onClicked: root.providerOpen = !root.providerOpen
                }
                Text { width: parent.width; wrapMode: Text.WordWrap; text: qsTr("Alliance partners run their own rigs. Choose yours before continuing."); color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 11 }
                Rectangle { width: parent.width; height: 1; color: DesktopTokens.seamSoft }
                ItemDelegate {
                    width: parent.width; height: 49; padding: 0
                    background: Item {}
                    contentItem: Item {
                        Rectangle { x: 0; anchors.verticalCenter: parent.verticalCenter; width: 17; height: 17; radius: 4; color: root.staySignedIn ? DesktopTokens.text : "transparent"; border.width: 1; border.color: root.staySignedIn ? DesktopTokens.text : DesktopTokens.textMuted; Text { anchors.centerIn: parent; visible: root.staySignedIn; text: "✓"; color: DesktopTokens.shell; font.pixelSize: 12; font.weight: Font.Black } }
                        Column { x: 29; anchors.verticalCenter: parent.verticalCenter; spacing: 2
                            Text { text: qsTr("Stay signed in on this PC"); color: DesktopTokens.textHigh; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.DemiBold }
                            Text { text: qsTr("The refresh token is encrypted with the OS keychain."); color: DesktopTokens.textFaint; font.family: DesktopTokens.bodyFont; font.pixelSize: 11 }
                        }
                    }
                    onClicked: root.staySignedIn = !root.staySignedIn
                }
                Item { width: 1; height: 5 }
                DesktopButton {
                    width: parent.width; height: 46; primary: true; text: qsTr("◉  Continue with NVIDIA")
                    enabled: ShellStore.ready
                    onClicked: ShellStore.startDeviceLogin(root.providers[0].idpId || "")
                }
                DesktopButton {
                    width: parent.width; height: 42; text: qsTr("⌗  Sign in with a QR code")
                    enabled: ShellStore.ready
                    onClicked: { root.qrRequested = true; ShellStore.startDeviceLogin(root.providers[0].idpId || "") }
                }
                Item { width: 1; height: 7 }
                Row { spacing: 10
                    Text { text: "ⓘ"; color: DesktopTokens.textMuted; font.pixelSize: 13 }
                    Text { width: 376; wrapMode: Text.WordWrap; text: qsTr("OpenNOW never sees your password. Sign-in happens on your provider's own page."); color: DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; lineHeight: 1.35 }
                }
            }

            Column {
                width: parent.width; spacing: 14; visible: root.waiting
                Rectangle {
                    visible: root.qrRequested
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: 148; height: 148; radius: 10; color: DesktopTokens.text
                    Grid {
                        id: qrGrid
                        anchors.centerIn: parent
                        columns: root.challenge && root.challenge.qrRows ? root.challenge.qrRows.length : 0
                        visible: columns > 0
                        property var qrRows: root.challenge && root.challenge.qrRows ? root.challenge.qrRows : []
                        property real cell: columns > 0 ? Math.floor(132 / columns) : 0
                        Repeater {
                            model: qrGrid.columns * qrGrid.columns
                            Rectangle { required property int index; width: qrGrid.cell; height: qrGrid.cell; color: qrGrid.qrRows[Math.floor(index / qrGrid.columns)].charAt(index % qrGrid.columns) === "1" ? "#050608" : "#FFFFFF" }
                        }
                    }
                    Text { anchors.centerIn: parent; visible: !qrGrid.visible; text: "QR"; color: DesktopTokens.shell; font.family: DesktopTokens.monoFont; font.pixelSize: 30; font.weight: Font.Black }
                }
                Rectangle {
                    width: parent.width; height: 82; radius: 12; color: "#0AFFFFFF"; border.width: 1; border.color: DesktopTokens.seamSoft
                    Column { x: 16; anchors.verticalCenter: parent.verticalCenter; spacing: 7
                        Text { text: root.challenge ? String(root.challenge.userCode || "").toUpperCase() : qsTr("CREATING SECURE CODE…"); color: DesktopTokens.text; font.family: DesktopTokens.monoFont; font.pixelSize: 20; font.weight: Font.Bold; font.letterSpacing: 2 }
                        Text { text: root.challenge ? String(root.challenge.verificationUri || "").replace(/^https?:\/\//, "") : qsTr("Contacting provider"); color: DesktopTokens.focus; font.family: DesktopTokens.monoFont; font.pixelSize: 10 }
                    }
                }
                Row { width: parent.width; spacing: 8; Rectangle { anchors.verticalCenter: parent.verticalCenter; width: 7; height: 7; radius: 4; color: DesktopTokens.green; SequentialAnimation on opacity { running: root.waiting; loops: Animation.Infinite; NumberAnimation { to: .35; duration: 700 } NumberAnimation { to: 1; duration: 700 } } } Text { text: ShellStore.authMessage || qsTr("Waiting for approval…"); color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 12 } }
                DesktopButton { width: parent.width; height: 42; primary: !root.qrRequested; text: root.qrRequested ? qsTr("Open sign-in page") : qsTr("Open provider page"); onClicked: if (root.challenge) Qt.openUrlExternally(root.challenge.verificationUriComplete || root.challenge.verificationUri) }
                DesktopButton { width: parent.width; height: 40; text: qsTr("Cancel"); onClicked: { ShellStore.cancelDeviceLogin(); root.qrRequested = false } }
            }

            Column {
                width: parent.width; spacing: 16; visible: root.failed
                Rectangle { width: parent.width; height: 79; radius: 12; color: "#08FF8A80"; border.width: 1; border.color: "#24FF8A80"; Rectangle { x: 14; y: 20; width: 3; height: 36; radius: 2; color: DesktopTokens.danger } Text { x: 29; y: 16; width: 280; text: qsTr("The authorization was cancelled or expired. Your previous account state is unchanged."); wrapMode: Text.WordWrap; color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; lineHeight: 1.35 } }
                Row { width: parent.width; spacing: 10
                    DesktopButton { width: 217; height: 44; primary: true; text: qsTr("Try again"); onClicked: { ShellStore.authState = "idle"; ShellStore.startDeviceLogin(root.providers[0].idpId || "") } }
                    DesktopButton { width: 179; height: 44; text: qsTr("Choose provider"); onClicked: { ShellStore.authState = "idle"; root.providerOpen = true } }
                }
                Rectangle { width: parent.width; height: 140; radius: 12; color: "#06FFFFFF"; border.width: 1; border.color: DesktopTokens.seamSoft
                    Column { anchors.fill: parent; anchors.margins: 12; spacing: 8
                        Text { text: qsTr("BEFORE YOU TRY AGAIN"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: .8 }
                        Text { text: qsTr("✓  Your internet connection is available"); color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 11 }
                        Text { text: qsTr("✓  Pop-ups are allowed in your browser"); color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 11 }
                        Text { text: qsTr("✓  You selected the correct alliance provider"); color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 11 }
                    }
                }
            }
        }

        Rectangle {
            id: providerMenu
            x: 33; y: 202; width: 406
            height: Math.min(260, root.providers.length * 52 + 16)
            visible: root.providerOpen && !root.waiting && !root.failed
            z: 20; radius: 12; color: "#FA111722"; border.width: 1; border.color: "#29FFFFFF"
            ListView {
                anchors.fill: parent; anchors.margins: 8; clip: true; model: root.providers
                delegate: ItemDelegate {
                    id: providerOption
                    required property var modelData
                    width: ListView.view.width; height: 52
                    background: Rectangle { radius: 8; color: providerOption.hovered || providerOption.activeFocus ? "#14FFFFFF" : "transparent" }
                    contentItem: Column { spacing: 2; Text { text: modelData.displayName || qsTr("Provider"); color: DesktopTokens.textHigh; font.family: DesktopTokens.bodyFont; font.pixelSize: 12; font.weight: Font.Bold } Text { text: String(modelData.region || "GLOBAL").toUpperCase(); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9 } }
                    onClicked: { root.providerOpen = false; ShellStore.startDeviceLogin(modelData.idpId || "") }
                }
            }
            opacity: visible ? 1 : 0; scale: visible ? 1 : .97
            Behavior on opacity { NumberAnimation { duration: DesktopTokens.quickDuration } }
            Behavior on scale { NumberAnimation { duration: DesktopTokens.quickDuration; easing.type: Easing.OutCubic } }
        }
    }

    Row {
        anchors.horizontalCenter: parent.horizontalCenter
        y: card.y + card.height + 43
        spacing: 15
        Text { text: qsTr("Why an account?"); color: DesktopTokens.focus; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; font.weight: Font.DemiBold }
        Text { text: "·"; color: DesktopTokens.textFaint }
        Text { text: qsTr("Source"); color: DesktopTokens.focus; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; font.weight: Font.DemiBold }
        Text { text: "·"; color: DesktopTokens.textFaint }
        Text { text: qsTr("Privacy"); color: DesktopTokens.focus; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; font.weight: Font.DemiBold }
        Text { text: "·"; color: DesktopTokens.textFaint }
        Text { text: root.waiting ? qsTr("WAITING") : root.failed ? qsTr("NOT SIGNED IN") : qsTr("OFFLINE MODE"); color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: .7 }
    }

    Connections {
        target: ShellStore
        function onSignedInChanged() { if (ShellStore.signedIn) root.signedIn() }
        function onAuthChallengeChanged() {
            if (ShellStore.authChallenge && !root.qrRequested)
                Qt.openUrlExternally(ShellStore.authChallenge.verificationUriComplete || ShellStore.authChallenge.verificationUri)
        }
    }
}
