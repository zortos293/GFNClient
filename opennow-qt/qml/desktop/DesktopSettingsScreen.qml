import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: root
    implicitWidth: 1208
    implicitHeight: 804
    clip: true

    property int selectedSection: 0
    property string dropdownKey: ""
    signal requestConsoleMode(bool enabled)

    readonly property var sections: [
        { kind: "header", label: "ACCOUNT" },
        { kind: "item", label: "Profile", page: 0 },
        { kind: "item", label: "Subscription", badge: "ULTIMATE", page: 1 },
        { kind: "item", label: "Linked stores", page: 2 },
        { kind: "spacer" },
        { kind: "header", label: "STREAMING" },
        { kind: "item", label: "Stream", page: 3 },
        { kind: "item", label: "Audio", page: 4 },
        { kind: "item", label: "Controllers", page: 5 },
        { kind: "item", label: "Network", page: 6 },
        { kind: "spacer" },
        { kind: "header", label: "APP" },
        { kind: "item", label: "Interface", page: 7 },
        { kind: "item", label: "Themes", badge: String(ShellStore.settings.themePack || "AURORA").toUpperCase(), page: 8 },
        { kind: "item", label: "Console mode", page: 9 },
        { kind: "item", label: "Shortcuts", page: 10 },
        { kind: "item", label: "About", page: 11 },
        { kind: "version", label: String(ShellStore.updaterState.currentVersion || "1.0.0") }
    ]
    readonly property var pageTitles: ["Profile", "Subscription", "Linked stores", "Stream", "Audio", "Controllers", "Network", "Interface", "Themes", "Console mode", "Shortcuts", "About"]
    readonly property var pageSubtitles: [
        "Who you are inside OpenNOW and what your friends can see.",
        "Your GeForce NOW plan decides resolution, frame rate and session length.",
        "Link a store once and its cloud-ready games show up in your library.",
        "Defaults for every session. Any game can override these from its details page.",
        "Output, microphone and party voice for every session.",
        "Pads, glyphs and what happens when one wakes up.",
        "Region, transport and how OpenNOW behaves when the link gets rough.",
        "How the desktop shell looks, starts and notifies you.",
        "Installed themes, custom backgrounds and the theme store.",
        "The gamepad-first shell inside the same app — for the TV or the Deck.",
        "Every binding in the desktop shell and in-stream. Click a key to rebind.",
        "Build, updates and the fine print."
    ]
    readonly property var pageComponents: [profilePage, subscriptionPage, storesPage, streamPage, audioPage, controllersPage, networkPage, interfacePage, themesPage, consolePage, shortcutsPage, aboutPage]

    function boolSetting(key, fallbackValue) {
        return ShellStore.settings[key] === undefined ? fallbackValue : Boolean(ShellStore.settings[key])
    }

    function valueSetting(key, fallbackValue) {
        const value = ShellStore.settings[key]
        return value === undefined || value === null || value === "" ? fallbackValue : value
    }

    function setSetting(key, value) {
        ShellStore.setSetting(key, value)
    }

    function choices(values) {
        return values.map(value => typeof value === "object" ? value : ({ kind: "choice", label: String(value), value: value }))
    }

    function openChoices(anchor, key, items, footer) {
        dropdownKey = key
        choiceOverlay.showFor(anchor, items, ShellStore.settings[key], footer || "")
    }

    function resolutionItems() {
        return [
            {kind:"heading", label:"16:9 STANDARD"},
            {kind:"choice", label:"720p", detail:"1280×720", value:"1280x720"},
            {kind:"choice", label:"1080p", detail:"1920×1080 · up to 240", value:"1920x1080"},
            {kind:"choice", label:"1440p", detail:"2560×1440 · up to 240", value:"2560x1440"},
            {kind:"choice", label:"4K", detail:"3840×2160 · up to 120", value:"3840x2160"},
            {kind:"heading", label:"16:10 WIDESCREEN"},
            {kind:"choice", label:"720p · WXGA · WSXGA", detail:"1280×800 → 1680×1050", value:"1680x1050"},
            {kind:"choice", label:"1200p · 1600p · 4K", detail:"1920×1200 → 3840×2400", value:"2560x1600"},
            {kind:"heading", label:"21:9 ULTRAWIDE"},
            {kind:"choice", label:"UW 1080p · UW 1440p", detail:"2560×1080 · 3440×1440", value:"3440x1440"},
            {kind:"heading", label:"32:9 SUPER ULTRAWIDE"},
            {kind:"choice", label:"Super ultrawide", detail:"5120×1440 · up to 120", value:"5120x1440"}
        ]
    }

    Rectangle {
        id: settingsRail
        x: 24
        y: 18
        width: 212
        height: 768
        color: "transparent"

        Column {
            width: parent.width
            Repeater {
                model: root.sections
                delegate: Item {
                    required property var modelData
                    width: settingsRail.width
                    height: modelData.kind === "header" ? 28 : modelData.kind === "spacer" ? 12 : modelData.kind === "version" ? 32 : 36

                    Text {
                        visible: modelData.kind === "header"
                        anchors.left: parent.left
                        anchors.leftMargin: 10
                        anchors.verticalCenter: parent.verticalCenter
                        text: modelData.label || ""
                        color: Qt.rgba(1,1,1,0.32)
                        font.family: Theme.monoFont
                        font.pixelSize: 9
                        font.weight: Font.Bold
                        font.letterSpacing: 1.1
                    }

                    Button {
                        id: railButton
                        visible: modelData.kind === "item"
                        anchors.fill: parent
                        hoverEnabled: true
                        onClicked: root.selectedSection = modelData.page
                        background: Rectangle {
                            radius: 10
                            color: modelData.page === root.selectedSection ? Qt.rgba(1,1,1,0.10)
                                 : railButton.hovered ? Qt.rgba(1,1,1,0.045) : "transparent"
                            Behavior on color { ColorAnimation { duration: Theme.focusDuration } }
                        }
                        contentItem: Item {
                            Text {
                                anchors.left: parent.left
                                anchors.leftMargin: 10
                                anchors.verticalCenter: parent.verticalCenter
                                text: modelData.label || ""
                                color: modelData.page === root.selectedSection ? Theme.label : Qt.rgba(1,1,1,0.64)
                                font.family: Theme.bodyFont
                                font.pixelSize: 13
                                font.weight: modelData.page === root.selectedSection ? Font.Bold : Font.Medium
                            }
                            Text {
                                visible: Boolean(modelData.badge)
                                anchors.right: parent.right
                                anchors.rightMargin: modelData.page === root.selectedSection ? 22 : 10
                                anchors.verticalCenter: parent.verticalCenter
                                text: modelData.badge || ""
                                color: modelData.label === "Subscription" ? Theme.violet : Qt.rgba(1,1,1,0.34)
                                font.family: Theme.monoFont
                                font.pixelSize: 9
                                font.weight: Font.Bold
                            }
                            Rectangle {
                                visible: modelData.page === root.selectedSection
                                width: 4
                                height: 16
                                radius: 2
                                color: DesktopTokens.focus
                                anchors.right: parent.right
                                anchors.rightMargin: 9
                                anchors.verticalCenter: parent.verticalCenter
                            }
                        }
                    }

                    Text {
                        visible: modelData.kind === "version"
                        anchors.left: parent.left
                        anchors.leftMargin: 164
                        anchors.verticalCenter: parent.verticalCenter
                        text: modelData.label || ""
                        color: Qt.rgba(1,1,1,0.26)
                        font.family: Theme.monoFont
                        font.pixelSize: 9
                    }
                }
            }
        }
    }

    Item {
        id: contentLane
        x: 256
        y: 18
        width: 928
        height: 768

        Text {
            id: pageTitle
            text: root.pageTitles[root.selectedSection]
            color: Theme.label
            font.family: Theme.displayFont
            font.pixelSize: 20
            font.weight: Font.Black
        }
        Text {
            anchors.left: parent.left
            anchors.top: pageTitle.bottom
            anchors.topMargin: 3
            text: root.pageSubtitles[root.selectedSection]
            color: Qt.rgba(1,1,1,0.50)
            font.family: Theme.bodyFont
            font.pixelSize: 13
            font.weight: Font.Medium
        }

        Flickable {
            id: contentFlick
            x: 0
            y: 61
            width: parent.width
            height: parent.height - 61
            contentWidth: width
            contentHeight: pageLoader.childrenRect.height
            clip: true
            boundsBehavior: Flickable.StopAtBounds
            ScrollBar.vertical: ScrollBar { policy: ScrollBar.AsNeeded }

            Loader {
                id: pageLoader
                width: contentFlick.width
                sourceComponent: root.pageComponents[root.selectedSection]
                onLoaded: contentFlick.contentY = 0
            }
        }
    }

    DesktopSettingsDropdown {
        id: choiceOverlay
        onChosen: value => root.setSetting(root.dropdownKey, value)
    }

    Component {
        id: profilePage
        Column {
            width: contentFlick.width
            spacing: 14
            DesktopSettingsPanel {
                width: parent.width
                padding: 18
                Item {
                    width: parent.width
                    height: 64
                    Rectangle {
                        width: 56; height: 56; radius: 28
                        color: Qt.rgba(1,1,1,0.16)
                        border.width: 1; border.color: Qt.rgba(1,1,1,0.24)
                        Text { anchors.centerIn: parent; text: "Z"; color: Theme.label; font.family: Theme.displayFont; font.pixelSize: 22; font.weight: Font.Black }
                    }
                    Column {
                        x: 82; anchors.verticalCenter: parent.verticalCenter; spacing: 3
                        Row {
                            spacing: 10
                            Text { text: ShellStore.authSession && ShellStore.authSession.user ? ShellStore.authSession.user.displayName : "Zortos"; color: Theme.label; font.family: Theme.displayFont; font.pixelSize: 20; font.weight: Font.Black }
                            Rectangle {
                                width: tier.implicitWidth + 12; height: 20; radius: 5; color: Qt.rgba(0.55,0.40,1,0.20)
                                Text { id: tier; anchors.centerIn: parent; text: ShellStore.subscription ? String(ShellStore.subscription.membershipTier || "ULTIMATE").toUpperCase() : "ULTIMATE"; color: "#C7B5FF"; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold }
                            }
                        }
                        Text { text: "zortos@proton.me · NVIDIA account linked · member since Aug 2025"; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11 }
                    }
                    Row {
                        anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; spacing: 10
                        DesktopSettingsButton { text: "Change avatar"; compact: true }
                        DesktopSettingsButton { text: "Rename"; compact: true }
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; title: "NVIDIA account"; description: "Bring your own account · OpenNOW never stores your password"; value: "z•••••@proton.me"; DesktopSettingsButton { text: "Manage"; compact: true; onClicked: AppController.navigate("accounts") } }
                DesktopSettingsRow { width: parent.width; title: "This device"; description: "Name shown in session history and to your party"; value: "ZORTOS-DESK · LINUX"; DesktopSettingsButton { text: "Edit"; compact: true } }
                DesktopSettingsRow { width: parent.width; title: "Signed-in devices"; description: "Desktop, Steam Deck and one TV box"; value: String(ShellStore.savedAccounts.length || 3) + " ACTIVE"; showDivider: false; DesktopSettingsButton { text: "Review"; compact: true; onClicked: AppController.navigate("accounts") } }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 58; title: "Show what I am playing"; description: "Friends see the game and how long you have been in it"; DesktopSettingsToggle { checked: root.boolSetting("discordRichPresence", false); onValueChangedByUser: value => root.setSetting("discordRichPresence", value) } }
                DesktopSettingsRow { width: parent.width; rowHeight: 58; title: "Let friends ask to join"; description: "Requests appear as an overlay you can dismiss with Esc"; DesktopSettingsToggle { checked: true } }
                DesktopSettingsRow { width: parent.width; rowHeight: 58; title: "Crash reports"; description: "Off by default · OpenNOW ships no analytics or trackers"; showDivider: false; DesktopSettingsToggle { checked: ShellStore.settings.errorReportingConsent === "granted"; onValueChangedByUser: value => root.setSetting("errorReportingConsent", value ? "granted" : "denied") } }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Item {
                    width: parent.width; height: 36
                    Column { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; spacing: 2
                        Text { text: "Local data"; color: Qt.rgba(1,1,1,0.88); font.family: Theme.bodyFont; font.pixelSize: 13; font.weight: Font.Bold }
                        Text { text: "Auth tokens, library cache and themes live in ~/.opennow · nothing leaves this device"; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11 }
                    }
                    Row { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; spacing: 10
                        DesktopSettingsButton { text: "Open folder" }
                        DesktopSettingsButton { text: "Sign out"; danger: true; onClicked: ShellStore.logout() }
                    }
                }
            }
        }
    }

    Component {
        id: subscriptionPage
        Column {
            width: contentFlick.width; spacing: 14
            DesktopSettingsPanel {
                width: parent.width; padding: 20
                Item {
                    width: parent.width; height: 86
                    Column { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; spacing: 7
                        Text { text: "CURRENT PLAN"; color: "#BBA2FF"; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1.1 }
                        Row { spacing: 12
                            Text { text: ShellStore.subscription ? String(ShellStore.subscription.membershipTier || "Ultimate") : "Ultimate"; color: Theme.label; font.family: Theme.displayFont; font.pixelSize: 28; font.weight: Font.Black }
                            Text { text: "€21.99 / month"; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 13; anchors.baseline: parent.children[0].baseline }
                        }
                        Row { spacing: 7
                            Repeater { model: ["RTX 5080 RIG","4K · 120 FPS","1080P · 240 FPS","RT OVERDRIVE","8 H SESSIONS"]
                                delegate: Rectangle { required property var modelData; width: chip.implicitWidth + 14; height: 22; radius: 6; color: Qt.rgba(1,1,1,0.07); border.width: 1; border.color: Qt.rgba(1,1,1,0.10); Text { id: chip; anchors.centerIn: parent; text: modelData; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold } }
                            }
                        }
                    }
                    Column { anchors.right: parent.right; spacing: 2
                        Text { anchors.right: parent.right; text: "RENEWS"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold }
                        Text { anchors.right: parent.right; text: "12 Sep 2026"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 14; font.weight: Font.Black }
                        Row { spacing: 8; topPadding: 8
                            DesktopSettingsButton { text: "Billing history" }
                            DesktopSettingsButton { text: "Manage on NVIDIA"; primary: true }
                        }
                    }
                }
            }
            Row {
                width: parent.width; spacing: 12
                Repeater { model: [{l:"SESSIONS THIS MONTH",v:"42"},{l:"TIME IN THE CLOUD",v:"61 h"},{l:"MEDIAN LATENCY",v:"9 ms",green:true},{l:"QUEUE WAIT",v:"None"}]
                    delegate: DesktopSettingsPanel { required property var modelData; width: (contentFlick.width - 36) / 4; height: 72; padding: 16
                        Text { text: modelData.l; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1 }
                        Text { text: modelData.v; color: modelData.green ? DesktopTokens.green : Theme.label; font.family: Theme.displayFont; font.pixelSize: 20; font.weight: Font.Black }
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Column { width: parent.width; spacing: 12
                    Row { width: parent.width
                        Column { width: parent.width - 220; spacing: 2
                            Text { text: "What each tier unlocks in OpenNOW"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 14; font.weight: Font.Bold }
                            Text { text: "Stream settings adapt automatically — locked options are dimmed, never hidden"; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11 }
                        }
                        Text { width: 220; text: "READ FROM YOUR ACCOUNT"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold; horizontalAlignment: Text.AlignRight }
                    }
                    Row { width: parent.width; spacing: 10
                        Repeater { model: [{n:"Free", lines:"1080p · 60 fps\nBasic rig\n1 h sessions\nStandard queue"},{n:"Performance", lines:"1440p · 60 fps\nRTX rig · RT on\n6 h sessions\nPriority queue"},{n:"Ultimate", lines:"4K · 120 fps\n1080p · 240 fps\n8 h sessions\nUltrawide · HDR", yours:true}]
                            delegate: Rectangle { required property var modelData; width: (parent.width - 20) / 3; height: 130; radius: 12; color: modelData.yours ? Qt.rgba(0.55,0.42,0.86,0.15) : Qt.rgba(1,1,1,0.035); border.width: 1; border.color: modelData.yours ? Qt.rgba(0.65,0.52,0.94,0.34) : Qt.rgba(1,1,1,0.10)
                                Text { x: 14; y: 12; text: modelData.n; color: modelData.yours ? Theme.label : Theme.textMuted; font.family: Theme.displayFont; font.pixelSize: 16; font.weight: Font.Black }
                                Text { visible: modelData.yours; anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 14; text: "YOURS"; color: "#C7B5FF"; font.family: Theme.monoFont; font.pixelSize: 8; font.weight: Font.Bold }
                                Text { x: 14; y: 43; text: modelData.lines; color: modelData.yours ? Qt.rgba(1,1,1,0.88) : Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 10; font.weight: Font.Medium; lineHeight: 1.85 }
                            }
                        }
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Item { width: parent.width; height: 30
                    Text { anchors.left: parent.left; anchors.right: refresh.left; anchors.rightMargin: 16; anchors.verticalCenter: parent.verticalCenter; text: "●   OpenNOW is a client, not a reseller. Plans, billing and rig availability are handled by NVIDIA — we only read your entitlements to decide which stream options to offer."; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; elide: Text.ElideRight }
                    DesktopSettingsButton { id: refresh; anchors.right: parent.right; text: "Refresh entitlements"; compact: true; onClicked: ShellStore.refreshAccountServices() }
                }
            }
        }
    }

    Component {
        id: storesPage
        Column {
            width: contentFlick.width; spacing: 14
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Item { width: parent.width; height: 38
                    Column { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; spacing: 2
                        Row { spacing: 8
                            Text { text: "Library sync"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 13; font.weight: Font.Bold }
                            Text { text: "● UP TO DATE"; color: DesktopTokens.green; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold }
                        }
                        Text { text: "184 cloud-ready games across 3 stores · last synced 4 minutes ago"; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11 }
                    }
                    Row { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; spacing: 14
                        Text { text: "Hide games without cloud support"; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; anchors.verticalCenter: parent.verticalCenter }
                        DesktopSettingsToggle { checked: true }
                        DesktopSettingsButton { text: "Sync now"; onClicked: ShellStore.refreshGameAccounts() }
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Repeater {
                    model: [
                        {icon:"S",name:"Steam",desc:"142 cloud-ready of 611 owned · synced 4 min ago",state:"● LINKED",color:DesktopTokens.green,action:"Unlink",provider:"steam",linked:true},
                        {icon:"E",name:"Epic Games",desc:"38 cloud-ready of 96 owned · synced 4 min ago",state:"● LINKED",color:DesktopTokens.green,action:"Unlink",provider:"epic",linked:true},
                        {icon:"U",name:"Ubisoft Connect",desc:"4 cloud-ready · needs a re-auth to keep syncing",state:"● EXPIRED",color:Theme.yellow,action:"Reconnect",provider:"ubisoft",primary:true},
                        {icon:"X",name:"Xbox / Microsoft Store",desc:"PC Game Pass titles appear once linked",state:"● NOT LINKED",color:Theme.textMuted,action:"Link",provider:"xbox"},
                        {icon:"G",name:"GOG",desc:"Small catalogue on GeForce NOW, but Cyberpunk is there",state:"● NOT LINKED",color:Theme.textMuted,action:"Link",provider:"gog",last:true}
                    ]
                    delegate: DesktopSettingsRow {
                        required property var modelData
                        width: parent.width; rowHeight: 62; title: modelData.name; description: modelData.desc; showDivider: !modelData.last
                        Rectangle { width: 36; height: 36; radius: 9; color: Qt.rgba(1,1,1,0.07); border.width: 1; border.color: Qt.rgba(1,1,1,0.11); Text { anchors.centerIn: parent; text: modelData.icon; color: Theme.textMuted; font.family: Theme.displayFont; font.pixelSize: 14; font.weight: Font.Black } }
                        Text { width: 110; text: modelData.state; color: modelData.color; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold; anchors.verticalCenter: parent.verticalCenter }
                        DesktopSettingsButton { text: modelData.action; compact: true; primary: Boolean(modelData.primary); onClicked: modelData.linked ? ShellStore.unlinkGameAccount(modelData.provider) : ShellStore.startAccountLink(modelData.provider) }
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Text { width: parent.width; height: 24; text: "●   Linking happens on NVIDIA's side. OpenNOW opens the store login in your browser and only keeps the resulting session token, encrypted, on this device."; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; verticalAlignment: Text.AlignVCenter }
            }
        }
    }

    Component {
        id: streamPage
        Column {
            id: streamPageRoot
            width: contentFlick.width; spacing: 14
            property string qualityMode: "custom"
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 44; title: "Display"; description: "Where OpenNOW opens the stream window"; value: "MONITOR 1 · 2560×1440 · 144 HZ" }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Quality mode"; description: "Auto reads your line and display · Custom keeps what you pick below"
                    DesktopSettingsSegmented { options: [{label:"AUTO",value:"auto"},{label:"BALANCED",value:"balanced"},{label:"PERFORMANCE",value:"performance"},{label:"CUSTOM",value:"custom"}]; optionWidth: 74; selectedIndex: ["auto","balanced","performance","custom"].indexOf(streamPageRoot.qualityMode); onSelected: (index, value) => streamPageRoot.qualityMode = value.value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 64; title: "Resolution"; description: "Exact stream size, grouped by aspect ratio"
                    DesktopSettingsButton { id: resolutionButton; text: root.valueSetting("resolution", "2560x1440") === "2560x1440" ? "1440p (16:9) · 2560×1440" : String(root.valueSetting("resolution", "1920x1080")).replace("x", "×"); compact: true; onClicked: root.openChoices(resolutionButton, "resolution", root.resolutionItems(), "MATCHES MONITOR 1 · 144 HZ") }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 64; title: "Frame rate"; description: "Only rates your plan and resolution allow · 4K caps at 120"
                    DesktopSettingsSegmented { options: ["AUTO","30","60","90","120","144","165","240"]; optionWidth: 44; selectedIndex: Math.max(0, [0,30,60,90,120,144,165,240].indexOf(Number(root.valueSetting("fps", 120)))); onSelected: (index, value) => root.setSetting("fps", value === "AUTO" ? 0 : Number(value)) }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 56; title: "HDR"; description: "Display reports HDR10 support"; showDivider: false
                    DesktopSettingsToggle { checked: String(root.valueSetting("colorQuality", "8bit_420")).indexOf("10bit") === 0; onValueChangedByUser: value => root.setSetting("colorQuality", value ? "10bit_420" : "8bit_420") }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 64; title: "Bitrate ceiling"; description: "Measured line speed 187 Mbps"; showDivider: true
                    DesktopSettingsSlider { from: 10; to: 200; stepSize: 5; value: Number(root.valueSetting("maxBitrateMbps", 75)); suffix: " Mbps"; onMoved: value => root.setSetting("maxBitrateMbps", Math.round(value)) }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 64; title: "Codec"; description: "AV1 saves about 30% bandwidth at the same quality"
                    DesktopSettingsButton { id: codecButton; text: String(root.valueSetting("codec", "auto")).toUpperCase() + (String(root.valueSetting("codec", "auto")).toLowerCase() === "av1" ? " · RECOMMENDED" : ""); compact: true; onClicked: root.openChoices(codecButton, "codec", root.choices([{kind:"choice",label:"Automatic",detail:"Best available",value:"auto"},{kind:"choice",label:"AV1",detail:"Recommended",value:"av1"},{kind:"choice",label:"H.265",detail:"Efficient",value:"h265"},{kind:"choice",label:"H.264",detail:"Compatible",value:"h264"}])) }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 56; title: "Reflex low latency"; description: "Requested per session when the game supports it"; showDivider: false
                    DesktopSettingsToggle { checked: root.boolSetting("enableCloudGsync", true); onValueChangedByUser: value => root.setSetting("enableCloudGsync", value) }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Item { width: parent.width; height: 36
                    Row { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; spacing: 22
                        Column { spacing: 2; Text { text: "SERVER REGION"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 8; font.weight: Font.Bold } Text { text: "●  EU-West · Amsterdam"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 14; font.weight: Font.Bold } }
                        Rectangle { width: 1; height: 34; color: Qt.rgba(1,1,1,0.08) }
                        Column { spacing: 2; Text { text: "PING"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 8; font.weight: Font.Bold } Text { text: "9 ms"; color: DesktopTokens.green; font.family: Theme.monoFont; font.pixelSize: 14; font.weight: Font.Bold } }
                        Column { spacing: 2; Text { text: "RIG"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 8; font.weight: Font.Bold } Text { text: "RTX 5080"; color: Theme.label; font.family: Theme.monoFont; font.pixelSize: 14; font.weight: Font.Bold } }
                    }
                    Row { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; spacing: 10
                        DesktopSettingsButton { text: ShellStore.regionPingRequestId === "" ? "Test connection" : "Testing…"; onClicked: ShellStore.pingRegions() }
                        DesktopSettingsButton { id: streamRegionButton; text: "Change region"; onClicked: root.openChoices(streamRegionButton, "region", root.choices([{kind:"choice",label:"Automatic",detail:"Lowest latency",value:""},{kind:"choice",label:"EU-West",detail:"9 ms",value:"EU-West"},{kind:"choice",label:"EU-Central",detail:"14 ms",value:"EU-Central"},{kind:"choice",label:"EU-North",detail:"26 ms",value:"EU-North"}])) }
                    }
                }
            }
        }
    }

    Component {
        id: audioPage
        Column {
            id: audioPageRoot
            width: contentFlick.width; spacing: 14
            property real gameVolume: 78
            property bool nightMode: false
            property string channels: "5.1"
            property bool pushToTalk: true
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Output device"; description: "Follows your system default unless you pin one here"; value: "SYSTEM DEFAULT · HD 560S" }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Channels"; description: "Surround needs Ultimate and a game that supports it"
                    DesktopSettingsSegmented { options: ["STEREO","5.1","7.1"]; optionWidth: 54; selectedIndex: ["STEREO","5.1","7.1"].indexOf(audioPageRoot.channels); onSelected: (index, value) => audioPageRoot.channels = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Game volume"; description: "Applied to the stream, independent of your system mixer"
                    DesktopSettingsSlider { from: 0; to: 100; value: audioPageRoot.gameVolume; onMoved: value => audioPageRoot.gameVolume = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 56; title: "Night mode compression"; description: "Tames explosions so dialogue stays audible at low volume"; showDivider: false
                    DesktopSettingsToggle { checked: audioPageRoot.nightMode; onValueChangedByUser: value => audioPageRoot.nightMode = value }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Microphone"; description: "Sent to the game so in-game voice chat works"
                    DesktopSettingsButton { id: micButton; text: root.valueSetting("microphoneDeviceId", "SHURE MV7 · USB"); compact: true; onClicked: root.openChoices(micButton, "microphoneDeviceId", root.choices([{kind:"choice",label:"System default",value:""},{kind:"choice",label:"Shure MV7 · USB",value:"SHURE MV7 · USB"}])) }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Input level"; description: "Say something — the meter should sit in the middle"
                    Row { spacing: 4; Repeater { model: [7,12,18,24,19,13,8,4]; delegate: Rectangle { required property int index; width: 4; height: modelData; color: index < 5 ? DesktopTokens.green : Qt.rgba(1,1,1,0.15); radius: 2; anchors.bottom: parent.bottom } } }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 56; title: "Push to talk"; description: "Held keys are never forwarded to the game"; showDivider: false; value: "MOUSE 4"
                    DesktopSettingsToggle { checked: audioPageRoot.pushToTalk; onValueChangedByUser: value => { audioPageRoot.pushToTalk = value; root.setSetting("microphoneMode", value ? "voice-activity" : "disabled") } }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Item { width: parent.width; height: 30
                    Text { anchors.left: parent.left; anchors.right: testAudio.left; anchors.rightMargin: 16; anchors.verticalCenter: parent.verticalCenter; text: "●   Audio rides the same NVST transport as video, so it inherits your bitrate ceiling. Stereo at 128 kbps is the safe pick on flaky Wi-Fi."; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; elide: Text.ElideRight }
                    DesktopSettingsButton { id: testAudio; anchors.right: parent.right; text: "Test audio"; compact: true }
                }
            }
        }
    }

    Component {
        id: controllersPage
        Column {
            id: controllersPageRoot
            width: contentFlick.width; spacing: 14
            property string glyphs: "AUTO"
            property real rumble: 100
            property real deadzone: 12
            property bool guideStats: true
            property bool switchOnPadWake: false
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Row { width: parent.width; height: 24
                    Text { text: "Detected pads"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 13; font.weight: Font.Bold }
                    Text { width: parent.width - 100; text: "HOT-PLUG · NO RESTART NEEDED"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 8; font.weight: Font.Bold; horizontalAlignment: Text.AlignRight }
                }
                Repeater { model: [{name:"Xbox Wireless Controller",desc:"Player 1 · Bluetooth · battery 74% · firmware 5.17",glyph:"XBOX GLYPHS",state:"● ACTIVE",active:true},{name:"DualSense Edge",desc:"Player 2 · USB-C · idle for 12 minutes",glyph:"PS GLYPHS"}]
                    delegate: Rectangle { required property var modelData; width: parent.width; height: 62; radius: 11; color: modelData.active ? Qt.rgba(0.25,0.38,0.68,0.15) : Qt.rgba(1,1,1,0.025); border.width: 1; border.color: modelData.active ? Qt.rgba(0.35,0.56,0.95,0.28) : Qt.rgba(1,1,1,0.08)
                        Rectangle { x: 14; anchors.verticalCenter: parent.verticalCenter; width: 34; height: 34; radius: 9; color: Qt.rgba(1,1,1,0.07); Text { anchors.centerIn: parent; text: "◉"; color: Theme.textMuted; font.pixelSize: 15 } }
                        Column { x: 62; anchors.verticalCenter: parent.verticalCenter; spacing: 2; Text { text: modelData.name; color: modelData.active ? Theme.label : Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 14; font.weight: Font.Bold } Text { text: modelData.desc; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11 } }
                        Row { anchors.right: parent.right; anchors.rightMargin: 14; anchors.verticalCenter: parent.verticalCenter; spacing: 8
                            Text { text: modelData.glyph; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold; anchors.verticalCenter: parent.verticalCenter }
                            Text { visible: Boolean(modelData.state); text: modelData.state || ""; color: DesktopTokens.green; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold; anchors.verticalCenter: parent.verticalCenter }
                            DesktopSettingsButton { text: "Test"; compact: true; primary: Boolean(modelData.active) }
                        }
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Switch to console mode when a pad wakes up"; description: "Same app, gamepad-first shell · a keypress switches back"
                    DesktopSettingsToggle { checked: controllersPageRoot.switchOnPadWake; onValueChangedByUser: value => controllersPageRoot.switchOnPadWake = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Button glyphs"; description: "Used in hints, the overlay and the console shell"
                    DesktopSettingsSegmented { options: ["AUTO","XBOX","PLAYSTATION","STEAM"]; optionWidth: 64; selectedIndex: ["AUTO","XBOX","PLAYSTATION","STEAM"].indexOf(controllersPageRoot.glyphs); onSelected: (index, value) => controllersPageRoot.glyphs = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Rumble strength"; description: "Forwarded over NVST · adaptive triggers pass through untouched"
                    DesktopSettingsSlider { from: 0; to: 100; value: controllersPageRoot.rumble; onMoved: value => controllersPageRoot.rumble = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Stick deadzone"; description: "Only affects OpenNOW menus — games get raw input"
                    DesktopSettingsSlider { from: 0; to: 40; value: controllersPageRoot.deadzone; onMoved: value => controllersPageRoot.deadzone = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 56; title: "Guide button opens the stats overlay"; description: "Xbox or PlayStation button · same panel as F3 on keyboard"; showDivider: false; value: "GUIDE"
                    DesktopSettingsToggle { checked: controllersPageRoot.guideStats; onValueChangedByUser: value => controllersPageRoot.guideStats = value }
                }
            }
            DesktopSettingsPanel { width: parent.width; padding: 18
                Item { width: parent.width; height: 30
                    Text { anchors.left: parent.left; anchors.right: inputTest.left; anchors.rightMargin: 16; anchors.verticalCenter: parent.verticalCenter; text: "●   Remapping happens in the game, not here. OpenNOW forwards raw pad state so anti-cheat and in-game bindings keep working."; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; elide: Text.ElideRight }
                    DesktopSettingsButton { id: inputTest; anchors.right: parent.right; text: "Open input tester"; compact: true; onClicked: AppController.navigate("joining") }
                }
            }
        }
    }

    Component {
        id: networkPage
        Column {
            id: networkPageRoot
            width: contentFlick.width; spacing: 14
            property bool reconnect: true
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Row { width: parent.width; height: 126; spacing: 16
                    Column { width: 250; spacing: 6
                        Text { text: "LAST CONNECTION TEST"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold }
                        Text { text: "● Excellent"; color: Theme.label; font.family: Theme.displayFont; font.pixelSize: 20; font.weight: Font.Black }
                        Text { width: parent.width; text: "4K at 120 fps is safe on this line. Tested 6 minutes ago over Ethernet."; wrapMode: Text.WordWrap; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11 }
                        DesktopSettingsButton { width: parent.width; text: ShellStore.regionPingRequestId === "" ? "Run test again" : "Testing…"; primary: true; onClicked: ShellStore.pingRegions() }
                    }
                    Rectangle { width: 1; height: parent.height; color: Qt.rgba(1,1,1,0.08) }
                    Column { width: parent.width - 267; spacing: 16
                        Row { width: parent.width; spacing: 48
                            Repeater { model: [{l:"DOWNLINK",v:"412 Mbps"},{l:"PING",v:"8 ms",c:DesktopTokens.green},{l:"JITTER",v:"0.4 ms"},{l:"PACKET LOSS",v:"0.00%"}]
                                delegate: Column { required property var modelData; width: 105; spacing: 2; Text { text: modelData.l; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold } Text { text: modelData.v; color: modelData.c || Theme.label; font.family: Theme.displayFont; font.pixelSize: 18; font.weight: Font.Black } }
                            }
                        }
                        Row { spacing: 4; Repeater { model: [18,24,17,20,28,17,20,42,25,17,21,29,17,22,20,27,18,21,33,16]; delegate: Rectangle { required property var modelData; width: 26; height: modelData; color: index === 7 ? Theme.yellow : Qt.rgba(0.45,0.62,0.92,0.55); anchors.bottom: parent.bottom } } }
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Server region"; description: "Auto picks the lowest ping rig with your tier available"; value: "EU-CENTRAL  14 MS    EU-NORTH  26 MS"
                    DesktopSettingsButton { id: networkRegion; text: String(root.valueSetting("region", "EU-WEST · 8 MS")); compact: true; onClicked: root.openChoices(networkRegion, "region", root.choices([{kind:"choice",label:"Automatic",detail:"Lowest latency",value:""},{kind:"choice",label:"EU-West",detail:"8 ms",value:"EU-West"},{kind:"choice",label:"EU-Central",detail:"14 ms",value:"EU-Central"},{kind:"choice",label:"EU-North",detail:"26 ms",value:"EU-North"}])) }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Bandwidth ceiling"; description: "Hard cap for the stream · useful on shared or metered lines"
                    DesktopSettingsSlider { from: 10; to: 200; stepSize: 5; value: Number(root.valueSetting("maxBitrateMbps", 75)); suffix: " Mbps"; onMoved: value => root.setSetting("maxBitrateMbps", Math.round(value)) }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Reconnect automatically"; description: "Keeps the rig warm for 90 s and resumes where you left off"
                    DesktopSettingsToggle { checked: networkPageRoot.reconnect; onValueChangedByUser: value => networkPageRoot.reconnect = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Proxy"; description: "Applies to API calls only · the stream always goes direct"; showDivider: false
                    TextField { width: 280; height: 34; text: String(root.valueSetting("sessionProxyUrl", "socks5://127.0.0.1:1080")); color: Theme.label; font.family: Theme.monoFont; font.pixelSize: 10; onEditingFinished: root.setSetting("sessionProxyUrl", text); background: Rectangle { radius: 9; color: Qt.rgba(1,1,1,0.04); border.width: 1; border.color: parent.activeFocus ? DesktopTokens.focus : Qt.rgba(1,1,1,0.12) } }
                    DesktopSettingsToggle { checked: root.boolSetting("sessionProxyEnabled", false); onValueChangedByUser: value => root.setSetting("sessionProxyEnabled", value) }
                }
            }
            DesktopSettingsPanel { width: parent.width; padding: 18
                Item { width: parent.width; height: 34
                    Column { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; spacing: 2
                        Row { spacing: 8; Text { text: "Transport"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 13; font.weight: Font.Bold } Text { text: "NVST · UDP 49000–49200"; color: DesktopTokens.green; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold } }
                        Text { text: "Native NVST is the only path OpenNOW uses. If UDP is blocked the session fails fast instead of silently degrading."; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11 }
                    }
                    DesktopSettingsButton { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; text: "Check ports"; compact: true }
                }
            }
        }
    }

    Component {
        id: interfacePage
        Column {
            id: interfacePageRoot
            width: contentFlick.width; spacing: 14
            property bool startAtLogin: false
            property string openOn: "HOME"
            property bool keepInTray: true
            property string sidebarMode: "EXPANDED"
            property string density: "COMFORTABLE"
            property bool hardwareAcceleration: true
            property bool notifyQueue: true
            property bool notifyFriends: false
            property bool notifyBuilds: true
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Start OpenNOW when I log in"; description: "Starts minimized in the tray, ready in about a second"
                    DesktopSettingsToggle { checked: interfacePageRoot.startAtLogin; onValueChangedByUser: value => interfacePageRoot.startAtLogin = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Open on"; description: "Where the window lands after launch"
                    DesktopSettingsSegmented { options: ["HOME","LIBRARY","LAST PAGE"]; optionWidth: 72; selectedIndex: ["HOME","LIBRARY","LAST PAGE"].indexOf(interfacePageRoot.openOn); onSelected: (index,value) => interfacePageRoot.openOn = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Closing the window keeps OpenNOW in the tray"; description: "A running session is never killed by closing the window"
                    DesktopSettingsToggle { checked: interfacePageRoot.keepInTray; onValueChangedByUser: value => interfacePageRoot.keepInTray = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Language"; description: "Community translated through Crowdin · 14 languages"; showDivider: false
                    DesktopSettingsButton { id: languageButton; text: String(root.valueSetting("appLanguage", "en")).toLowerCase() === "en" ? "ENGLISH (UK)" : String(root.valueSetting("appLanguage", "system")).toUpperCase(); compact: true; onClicked: root.openChoices(languageButton, "appLanguage", root.choices([{kind:"choice",label:"System",value:"system"},{kind:"choice",label:"English (UK)",value:"en"},{kind:"choice",label:"Nederlands",value:"nl"},{kind:"choice",label:"Deutsch",value:"de"},{kind:"choice",label:"Français",value:"fr"},{kind:"choice",label:"Türkçe",value:"tr"}])) }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Sidebar"; description: "Collapsed shows icons only and expands on hover · Ctrl B toggles"
                    DesktopSettingsSegmented { options: ["EXPANDED","COLLAPSED","REMEMBER"]; optionWidth: 83; selectedIndex: ["EXPANDED","COLLAPSED","REMEMBER"].indexOf(interfacePageRoot.sidebarMode); onSelected: (index,value) => interfacePageRoot.sidebarMode = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Grid density"; description: "Cover size in Library and Store"
                    DesktopSettingsSegmented { options: ["DENSE","COMFORTABLE","LARGE"]; optionWidth: 74; selectedIndex: ["DENSE","COMFORTABLE","LARGE"].indexOf(interfacePageRoot.density); onSelected: (index,value) => { interfacePageRoot.density = value; root.setSetting("posterSizeScale", value === "DENSE" ? 0.9 : value === "LARGE" ? 1.25 : 1.05) } }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Reduce motion"; description: "Cuts parallax and cover animations · follows your OS by default"
                    DesktopSettingsToggle { checked: root.boolSetting("reducedMotion", false); onValueChangedByUser: value => root.setSetting("reducedMotion", value) }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Hardware acceleration"; description: "Needs a restart · turn off only if the shell flickers"; showDivider: false
                    DesktopSettingsToggle { checked: interfacePageRoot.hardwareAcceleration; onValueChangedByUser: value => interfacePageRoot.hardwareAcceleration = value }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 56; title: "Tell me when a queued session is ready"
                    DesktopSettingsToggle { checked: interfacePageRoot.notifyQueue; onValueChangedByUser: value => interfacePageRoot.notifyQueue = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 56; title: "Tell me when a friend starts playing"
                    DesktopSettingsToggle { checked: interfacePageRoot.notifyFriends; onValueChangedByUser: value => interfacePageRoot.notifyFriends = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 56; title: "Tell me when a new OpenNOW build lands"; showDivider: false
                    DesktopSettingsToggle { checked: interfacePageRoot.notifyBuilds; onValueChangedByUser: value => interfacePageRoot.notifyBuilds = value }
                }
            }
        }
    }

    Component {
        id: themesPage
        Column {
            width: contentFlick.width; spacing: 14
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Item { width: parent.width; height: 114
                    Rectangle { width: 232; height: 100; radius: 12; color: "#17162A"; border.width: 1; border.color: Qt.rgba(0.7,0.5,1,0.35)
                        Row { x: 50; y: 34; spacing: 7
                            Repeater { model: ["#304265","#594779","#406A65"]; delegate: Rectangle { required property var modelData; width: 44; height: 56; radius: 6; gradient: Gradient { GradientStop { position: 0; color: Qt.lighter(modelData,1.3) } GradientStop { position: 1; color: Qt.darker(modelData,1.35) } } } }
                        }
                        Column { x: 7; y: 9; spacing: 6; Repeater { model: [22,31,28]; delegate: Rectangle { required property var modelData; width: modelData; height: 6; radius: 3; color: Qt.rgba(1,1,1,0.28) } } }
                    }
                    Column { x: 250; y: 8; width: 470; spacing: 7
                        Text { text: "ACTIVE THEME"; color: DesktopTokens.focus; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1 }
                        Row { spacing: 12; Text { text: "Aurora"; color: Theme.label; font.family: Theme.displayFont; font.pixelSize: 24; font.weight: Font.Black } Text { text: "by opennow · v1.4"; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; anchors.baseline: parent.children[0].baseline } }
                        Text { width: parent.width; wrapMode: Text.WordWrap; text: "Cool violet gradient with a soft scrim over cover art. Custom background, accent and scrim opacity all come from a single theme.json."; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11 }
                        Row { spacing: 7; Repeater { model: ["GRADIENT BACKGROUND","SCRIM 62%","WORKS IN CONSOLE MODE"]; delegate: Rectangle { required property var modelData; width: badge.implicitWidth + 14; height: 22; radius: 5; color: Qt.rgba(1,1,1,0.07); Text { id: badge; anchors.centerIn: parent; text: modelData; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 8; font.weight: Font.Bold } } } }
                    }
                    Column { anchors.right: parent.right; y: 28; spacing: 8
                        DesktopSettingsButton { width: 144; text: "Customise"; primary: true }
                        DesktopSettingsButton { width: 144; text: "Change background" }
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Row { width: parent.width; height: 30
                    Text { text: "Installed"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 14; font.weight: Font.Bold }
                    Text { width: parent.width - 180; text: "5 THEMES · 1 UPDATE"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 8; font.weight: Font.Bold; horizontalAlignment: Text.AlignRight; anchors.verticalCenter: parent.verticalCenter }
                    DesktopSettingsButton { width: 126; text: "Open theme store"; compact: true; onClicked: AppController.navigate("theme-store") }
                }
                Row { width: parent.width; height: 124; spacing: 10
                    Repeater { model: [{n:"Aurora",c:"#3D315C",s:"IN USE",id:"aurora"},{n:"Carbon",c:"#222329",s:"USE",id:"nocturne"},{n:"Sunset",c:"#6A3939",s:"UPDATE",id:"hibiscus"},{n:"Mint",c:"#164B3C",s:"USE",id:"phosphor"},{n:"+\nNew theme",c:"transparent",s:"FROM CURRENT",id:""}]
                        delegate: Button { required property var modelData; width: (parent.width - 40) / 5; height: 116; hoverEnabled: true; onClicked: { if (modelData.id !== "") root.setSetting("themePack", modelData.id) }
                            background: Rectangle { radius: 11; color: Qt.rgba(1,1,1,0.025); border.width: 1; border.color: modelData.id === String(root.valueSetting("themePack","aurora")) ? DesktopTokens.focus : Qt.rgba(1,1,1,0.13) }
                            contentItem: Item { Rectangle { visible: modelData.id !== ""; x: 8; y: 8; width: parent.width - 16; height: 76; radius: 7; color: modelData.c; gradient: Gradient { GradientStop { position: 0; color: Qt.lighter(modelData.c,1.25) } GradientStop { position: 1; color: Qt.darker(modelData.c,1.2) } } }
                                Text { anchors.left: parent.left; anchors.leftMargin: 10; anchors.bottom: parent.bottom; anchors.bottomMargin: 8; width: parent.width - 20; text: modelData.n; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 12; font.weight: Font.Bold; horizontalAlignment: modelData.id === "" ? Text.AlignHCenter : Text.AlignLeft }
                                Text { visible: modelData.id !== ""; anchors.right: parent.right; anchors.rightMargin: 10; anchors.bottom: parent.bottom; anchors.bottomMargin: 9; text: modelData.s; color: modelData.s === "UPDATE" ? Theme.yellow : modelData.s === "IN USE" ? DesktopTokens.focus : Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 7; font.weight: Font.Bold }
                            }
                        }
                    }
                }
            }
            DesktopSettingsPanel { width: parent.width; padding: 18
                Item { width: parent.width; height: 36
                    Column { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; spacing: 2; Text { text: "Theme folder"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 13; font.weight: Font.Bold } Text { text: "~/.opennow/themes · drop a folder with theme.json and it appears here instantly"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 10 } }
                    Row { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; spacing: 10; DesktopSettingsButton { text: "Open folder" } DesktopSettingsButton { text: "Import theme" } }
                }
            }
        }
    }

    Component {
        id: consolePage
        Column {
            id: consolePageRoot
            width: contentFlick.width; spacing: 14
            property bool leaveOnInput: true
            property real safeArea: 3
            property string uiScale: "COUCH"
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                Item { width: parent.width; height: 120
                    Rectangle { width: 212; height: 120; radius: 12; color: "#151526"; border.width: 1; border.color: Qt.rgba(0.6,0.5,1,0.25)
                        Row { x: 12; y: 34; spacing: 7; Repeater { model: ["#35527D","#684A77","#38675E"]; delegate: Rectangle { required property var modelData; width: 50; height: 56; radius: 7; gradient: Gradient { GradientStop { position: 0; color: Qt.lighter(modelData,1.3) } GradientStop { position: 1; color: Qt.darker(modelData,1.4) } } } }
                        Text { x: 12; y: 96; text: "A  PLAY   B  BACK"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 7; font.weight: Font.Bold }
                    }
                    Column { x: 230; y: 18; width: 500; spacing: 9
                        Row { spacing: 10; Text { text: "One app, two shells"; color: Theme.label; font.family: Theme.displayFont; font.pixelSize: 20; font.weight: Font.Black } Text { text: "● GAMEPAD READY"; color: DesktopTokens.green; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold; anchors.verticalCenter: parent.verticalCenter } }
                        Text { width: parent.width; wrapMode: Text.WordWrap; text: "Console mode swaps the desktop chrome for big art, focus rings and glyph hints. Same session, same settings, same themes — nothing restarts and a running stream keeps going."; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; lineHeight: 1.45 }
                    }
                    Column { anchors.right: parent.right; y: 20; spacing: 8
                        DesktopSettingsButton { width: 158; text: "Switch now"; suffix: "F10"; primary: true; onClicked: { root.setSetting("launchInConsoleMode", true); root.requestConsoleMode(true) } }
                        DesktopSettingsButton { width: 158; text: "Preview on this screen" }
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; padding: 18
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Start in console mode"; description: "Remember this choice for the next time OpenNOW launches"
                    DesktopSettingsToggle { checked: root.boolSetting("launchInConsoleMode", true); onValueChangedByUser: value => root.setSetting("launchInConsoleMode", value) }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Enter console mode when a gamepad is the only input"; description: "Ignored while a mouse has moved in the last 30 seconds"
                    DesktopSettingsToggle { checked: root.boolSetting("controllerMode", true); onValueChangedByUser: value => root.setSetting("controllerMode", value) }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Leave console mode on keyboard or mouse input"; description: "Keeps your place in the grid when the shell swaps"
                    DesktopSettingsToggle { checked: consolePageRoot.leaveOnInput; onValueChangedByUser: value => consolePageRoot.leaveOnInput = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "Go fullscreen in console mode"; description: "Recommended on a TV · hides the window chrome entirely"
                    DesktopSettingsToggle { checked: root.boolSetting("autoFullScreen", true); onValueChangedByUser: value => root.setSetting("autoFullScreen", value) }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "TV safe area"; description: "Pulls the UI inwards on sets that overscan"
                    DesktopSettingsSlider { from: 0; to: 10; stepSize: 1; value: consolePageRoot.safeArea; onMoved: value => consolePageRoot.safeArea = value }
                }
                DesktopSettingsRow { width: parent.width; rowHeight: 62; title: "UI scale"; description: "Deck defaults to compact, a 55\" TV wants couch"; showDivider: false
                    DesktopSettingsSegmented { options: ["COMPACT","COUCH","LARGE"]; optionWidth: 66; selectedIndex: ["COMPACT","COUCH","LARGE"].indexOf(consolePageRoot.uiScale); onSelected: (index,value) => consolePageRoot.uiScale = value }
                }
            }
            DesktopSettingsPanel { width: parent.width; padding: 18
                Item { width: parent.width; height: 30
                    Text { anchors.left: parent.left; anchors.right: cli.left; anchors.rightMargin: 16; anchors.verticalCenter: parent.verticalCenter; text: "●   Launch OpenNOW with --console to boot straight into the gamepad shell. That is the flag Steam Deck and TV boxes should use in their launcher entry."; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; elide: Text.ElideRight }
                    Rectangle { id: cli; anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; width: 132; height: 28; radius: 8; color: Qt.rgba(1,1,1,0.07); border.width: 1; border.color: Qt.rgba(1,1,1,0.12); Text { anchors.centerIn: parent; text: "opennow --console"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold } }
                }
            }
        }
    }
    }

    Component {
        id: shortcutsPage
        Column {
            width: contentFlick.width; spacing: 14
            DesktopSettingsPanel {
                width: parent.width; padding: 16
                Row { width: parent.width; height: 34; spacing: 12
                    TextField { width: parent.width - 262; height: 34; placeholderText: "⌕  Search a command or press a key to find its binding"; color: Theme.label; placeholderTextColor: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; background: Rectangle { radius: 9; color: Qt.rgba(1,1,1,0.04); border.width: 1; border.color: parent.activeFocus ? DesktopTokens.focus : Qt.rgba(1,1,1,0.10) } }
                    Text { width: 148; height: 34; text: "3 CHANGED FROM DEFAULT"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 8; font.weight: Font.Bold; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                    DesktopSettingsButton { width: 90; text: "Reset all"; compact: true; onClicked: ShellStore.resetSettings() }
                }
            }
            GridLayout {
                width: parent.width; columns: 2; columnSpacing: 14; rowSpacing: 14
                Repeater {
                    model: [
                        {h:"APP", rows:[{l:"Command palette",k:"Ctrl  K"},{l:"Search this page",k:"/"},{l:"Collapse or expand the sidebar",k:"Ctrl  B"},{l:"Switch to console mode",k:"F10"},{l:"Settings",k:"Ctrl  ,"},{l:"Quit OpenNOW",k:"Ctrl  Q"}]},
                        {h:"IN STREAM", rows:[{l:"Session menu",k:"Esc"},{l:"Stats overlay · minimal then expanded",k:"F3"},{l:"Toggle fullscreen",k:"F11"},{l:"Grab or release the mouse",k:"Ctrl  G"},{l:"Screenshot the stream",k:"F12"},{l:"End the session",k:"Ctrl  Shift  Q"}]},
                        {h:"LIBRARY AND STORE", rows:[{l:"Move through covers",k:"Arrows"},{l:"Play or resume",k:"Enter"},{l:"Game details",k:"Space"},{l:"Toggle favourite",k:"F"},{l:"Context menu",k:"Shift  F10"}]},
                        {h:"GAMEPAD · CONSOLE MODE", rows:[{l:"Select · back",k:"A · B"},{l:"Details · favourite",k:"X · Y"},{l:"Switch tab",k:"LB · RB"},{l:"Stats overlay",k:"Guide"}]}
                    ]
                    delegate: DesktopSettingsPanel {
                        required property var modelData
                        Layout.fillWidth: true; Layout.preferredHeight: modelData.rows.length * 40 + 48; padding: 16
                        Text { text: modelData.h; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold; font.letterSpacing: 1 }
                        Repeater { model: modelData.rows
                            delegate: Item { required property var modelData; width: parent.width; height: 40
                                Text { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; text: modelData.l; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 12 }
                                DesktopSettingsButton { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; text: modelData.k; compact: true }
                            }
                        }
                    }
                }
            }
        }
    }

    Component {
        id: aboutPage
        Column {
            width: contentFlick.width; spacing: 14
            DesktopSettingsPanel { width: parent.width; padding: 18
                Item { width: parent.width; height: 54
                    Row { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; spacing: 16
                        Text { text: "☁"; color: "#65F02E"; font.pixelSize: 42; font.weight: Font.Black }
                        Column { spacing: 3; Text { text: "OpenNOW " + String(ShellStore.updaterState.currentVersion || "1.0.0"); color: Theme.label; font.family: Theme.displayFont; font.pixelSize: 24; font.weight: Font.Black } Text { text: "Build 2026.08.28 · commit 4f9c1ab · Qt Quick · NVST native transport"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 10; font.weight: Font.Bold } }
                        Text { text: "UP TO DATE"; color: DesktopTokens.green; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold }
                    }
                    Row { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; spacing: 10
                        DesktopSettingsButton { id: updateChannelButton; text: String(root.valueSetting("updateChannel","stable")).toUpperCase() + " CHANNEL"; onClicked: root.openChoices(updateChannelButton,"updateChannel",root.choices([{kind:"choice",label:"Stable",value:"stable"},{kind:"choice",label:"Nightly",value:"nightly"}])) }
                        DesktopSettingsButton { text: "Check for updates"; primary: true; onClicked: ShellStore.checkForUpdates() }
                    }
                }
            }
            Row { width: parent.width; spacing: 14
                DesktopSettingsPanel { width: parent.width - 340; padding: 18
                    Row { width: parent.width; Text { text: "What changed in 1.0.0"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 14; font.weight: Font.Bold } Text { width: parent.width - 160; text: "28 AUG 2026"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: 8; font.weight: Font.Bold; horizontalAlignment: Text.AlignRight } }
                    Repeater { model: ["Native NVST transport is now the only stream path. Reverse-engineered handshake shaves 18 ms off the old embedded route.","Collapsible sidebar, command palette and a full desktop shell that shares state with console mode.","Theme store with custom gradient backgrounds and live previews.","F3 stats overlay rebuilt: minimal bar plus an expanded panel with frametime and latency graphs."]
                        delegate: Text { required property int index; required property var modelData; width: parent.width; height: 46; text: ["●","●","●","●"][index] + "  " + modelData; color: index === 0 ? DesktopTokens.green : index === 1 ? DesktopTokens.focus : index === 2 ? Theme.violet : Theme.yellow; font.family: Theme.bodyFont; font.pixelSize: 11; wrapMode: Text.WordWrap }
                    }
                    Text { text: "Read the full release notes  ↗"; color: DesktopTokens.focus; font.family: Theme.bodyFont; font.pixelSize: 12; font.weight: Font.Bold }
                }
                DesktopSettingsPanel { width: 326; padding: 18
                    Text { text: "Project"; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: 14; font.weight: Font.Bold }
                    Repeater { model: ["Source on GitHub","Open source licences","Translate OpenNOW on Crowdin","Copy diagnostics","Open log folder"]
                        delegate: Item { required property var modelData; width: parent.width; height: 54; Text { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; text: modelData; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 12 } Text { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; text: modelData === "Copy diagnostics" ? "NO PERSONAL DATA" : "↗"; color: Theme.textMuted; font.family: Theme.monoFont; font.pixelSize: modelData === "Copy diagnostics" ? 8 : 14; font.weight: Font.Bold } Rectangle { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; height: 1; color: Qt.rgba(1,1,1,0.06) } }
                    }
                }
            }
            DesktopSettingsPanel { width: parent.width; padding: 18
                Text { width: parent.width; height: 30; verticalAlignment: Text.AlignVCenter; text: "●   OpenNOW is an independent client. Not affiliated with, endorsed by or supported by NVIDIA. GeForce NOW is a trademark of NVIDIA Corporation. You bring your own account and your own subscription."; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 11; wrapMode: Text.WordWrap }
            }
        }
    }
}
