import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: root
    objectName: "desktopSettingsScreen"
    anchors.fill: parent
    clip: true

    property int selectedSection: 3
    property string searchQuery: ""
    property bool advancedOpen: false
    // Isolated visual acceptance only; never selects alternate production UI.
    property string acceptancePanel: ""
    readonly property var acceptancePanels: ({stats:statsSettingsPage, audio:audioPage,
        interface:interfacePage, console:consolePage, shortcuts:shortcutsPage,
        controllers:controllersPage, subscription:subscriptionPage})
    readonly property bool compactNavigation: width < 1050
    readonly property int selectedGroup: sections.findIndex(section => section.page ===
        ([0,1,2].indexOf(selectedSection) >= 0 ? 0 : selectedSection === 10 ? 5 : selectedSection === 7 ? 8 : selectedSection))
    onSelectedSectionChanged: { advancedOpen = false }
    signal requestConsoleMode(bool enabled)

    readonly property var sections: [
        {label: qsTr("Stream"), detail: qsTr("Picture, codec, bitrate"), icon: "monitor", page: 3, keywords: "resolution fps hdr color audio stats overlay bitrate codec reflex backend gpu directx vulkan"},
        {label: qsTr("Audio"), detail: qsTr("Output and stream audio"), icon: "wave", page: 4, keywords: "sound audio volume output microphone"},
        {label: qsTr("Controls"), detail: qsTr("Pads, mouse, shortcuts"), icon: "controller", page: 5, keywords: "controller gyroscope steam sensitivity keyboard language shortcuts"},
        {label: qsTr("Look"), detail: qsTr("Theme, accent, layout"), icon: "palette", page: 8, keywords: "theme accent interface language scale motion console sidebar tiles"},
        {label: qsTr("Console mode"), detail: qsTr("Gamepad-first interface"), icon: "controller", page: 9, keywords: "console fullscreen gamepad startup"},
        {label: qsTr("Network"), detail: qsTr("Region, ping, proxy"), icon: "globe", page: 6, keywords: "server region ping proxy bandwidth l4s"},
        {label: qsTr("Account"), detail: qsTr("NVIDIA, stores, privacy"), icon: "person", page: 0, keywords: "profile subscription stores steam epic xbox ubisoft battle gaijin privacy"},
        {label: qsTr("About"), detail: qsTr("Updates, diagnostics"), icon: "info", page: 11, keywords: "version release update diagnostics"}
    ]
    readonly property var pageTitles: [qsTr("Account"), qsTr("Account"), qsTr("Account"), qsTr("Stream"), qsTr("Audio"), qsTr("Controls"), qsTr("Network"), qsTr("Look"), qsTr("Look"), qsTr("Console mode"), qsTr("Controls"), qsTr("About")]
    readonly property var pageComponents: [accountGroup, subscriptionPage, accountGroup, streamPage, audioPage, controlsGroup, networkPage, lookGroup, lookGroup, consolePage, controlsGroup, aboutPage]

    function matchesSection(section) {
        const query = searchQuery.trim().toLowerCase()
        return !query || (section.label + " " + section.detail + " " + section.keywords).toLowerCase().indexOf(query) >= 0
    }

    Component.onCompleted: {
        ShellStore.refreshRegions()
        ShellStore.refreshGameAccounts()
    }

    function boolSetting(key, fallbackValue) {
        return ShellStore.settings[key] === undefined ? fallbackValue : Boolean(ShellStore.settings[key])
    }

    function valueSetting(key, fallbackValue) {
        const value = ShellStore.settings[key]
        return value === undefined || value === null || value === "" ? fallbackValue : value
    }

    function setSetting(key, value) {
        ShellStore.applySetting(key, value)
        ShellStore.setSetting(key, value)
        if (key === "resolution")
            Qt.callLater(root.clampFpsToEntitlement)
    }

    function setChoice(key, value) {
        if (key === "appAccentColor")
            root.setSetting("themeAccentOverride", true)
        if (key === "themePack")
            root.setSetting("themeAccentOverride", false)
        const normalized = String(value || "").toLowerCase()
        const codec = String(root.valueSetting("codec", "auto")).toLowerCase()
        if (key === "colorQuality") {
            if (codec === "h264" && normalized !== "8bit_420")
                return
            if (codec === "av1" && normalized.indexOf("444") >= 0)
                return
        }
        const currentQuality = String(root.valueSetting("colorQuality", "8bit_420"))
        root.setSetting(key, value)
        if (key === "codec") {
            if (normalized === "h264" && currentQuality !== "8bit_420")
                root.setSetting("colorQuality", "8bit_420")
            else if (normalized === "av1" && currentQuality.indexOf("444") >= 0)
                root.setSetting("colorQuality", currentQuality.replace("444", "420"))
        }
    }

    function choices(values) {
        return values.map(value => typeof value === "object" ? value : ({ kind: "choice", label: String(value), value: value }))
    }

    function colorQualityItems() {
        const codec = String(root.valueSetting("codec", "auto")).toLowerCase()
        const h264 = codec === "h264"
        const chroma444Unavailable = h264 || codec === "av1"
        return [
            {kind:"choice", label:qsTr("8-bit, YUV 4:2:0"), detail:qsTr("All codecs"), value:"8bit_420"},
            {kind:"choice", label:qsTr("8-bit, YUV 4:4:4"), detail:chroma444Unavailable ? qsTr("H.265 required") : qsTr("Sharper color"), value:"8bit_444", disabled:chroma444Unavailable},
            {kind:"choice", label:qsTr("10-bit, YUV 4:2:0"), detail:h264 ? qsTr("H.265 / AV1 required") : qsTr("Smoother gradients"), value:"10bit_420", disabled:h264},
            {kind:"choice", label:qsTr("10-bit, YUV 4:4:4"), detail:chroma444Unavailable ? qsTr("H.265 required") : qsTr("Highest color quality"), value:"10bit_444", disabled:chroma444Unavailable}
        ]
    }

    function colorQualityFooter() {
        const codec = String(root.valueSetting("codec", "auto")).toLowerCase()
        if (codec === "h264")
            return qsTr("H.264 supports 8-bit YUV 4:2:0 only")
        if (codec === "av1")
            return qsTr("AV1 supports YUV 4:2:0; use H.265 for 4:4:4")
        return qsTr("4:4:4 profiles use H.265")
    }

    function colorQualityLabel() {
        const labels = {
            "8bit_420": qsTr("8-bit, YUV 4:2:0"),
            "8bit_444": qsTr("8-bit, YUV 4:4:4"),
            "10bit_420": qsTr("10-bit, YUV 4:2:0"),
            "10bit_444": qsTr("10-bit, YUV 4:4:4")
        }
        const value = String(root.valueSetting("colorQuality", "8bit_420"))
        return labels[value] || labels["8bit_420"]
    }

    function liveTierBadge() {
        const tier = ShellStore.subscription ? String(ShellStore.subscription.membershipTier || "") : ""
        return tier === "" ? "" : tier.toUpperCase()
    }

    function profileUser() {
        return ShellStore.authSession && ShellStore.authSession.user ? ShellStore.authSession.user : ({})
    }

    function profileName() {
        return String(root.profileUser().displayName || qsTr("Guest"))
    }

    function profileInitial() {
        const name = root.profileName()
        return name.length ? name.charAt(0).toUpperCase() : "?"
    }

    function profileEmail() {
        return String(root.profileUser().email || "")
    }

    function profileSubtitle() {
        if (!ShellStore.signedIn)
            return qsTr("Not signed in")
        const email = root.profileEmail()
        if (email !== "")
            return qsTr("%1 · NVIDIA account linked").arg(email)
        return qsTr("NVIDIA account linked")
    }

    function maskedEmail() {
        const email = root.profileEmail()
        const at = email.indexOf("@")
        if (at <= 0)
            return ShellStore.signedIn ? qsTr("NVIDIA account") : qsTr("Not signed in")
        return email.charAt(0) + "•••••" + email.slice(at)
    }

    function regionGroup(name) {
        const n = String(name || "").toLowerCase()
        if (n.indexOf("us ") === 0 || n.indexOf("us-") === 0 || n.indexOf("ca ") === 0 || n.indexOf("canada") >= 0 || n.indexOf("north america") >= 0)
            return qsTr("NORTH AMERICA")
        if (n.indexOf("eu ") === 0 || n.indexOf("eu-") === 0 || n.indexOf("uk") === 0 || n.indexOf("europe") >= 0 || n.indexOf("london") >= 0 || n.indexOf("frankfurt") >= 0 || n.indexOf("amsterdam") >= 0)
            return qsTr("EUROPE")
        if (n.indexOf("jp") === 0 || n.indexOf("kr") === 0 || n.indexOf("sg") === 0 || n.indexOf("au") === 0 || n.indexOf("asia") >= 0 || n.indexOf("tokyo") >= 0 || n.indexOf("seoul") >= 0 || n.indexOf("singapore") >= 0 || n.indexOf("sydney") >= 0 || n.indexOf("india") >= 0)
            return qsTr("ASIA PACIFIC")
        if (n.indexOf("br") === 0 || n.indexOf("south america") >= 0 || n.indexOf("brazil") >= 0 || n.indexOf("sao") >= 0)
            return qsTr("SOUTH AMERICA")
        if (n.indexOf("me ") === 0 || n.indexOf("middle") >= 0 || n.indexOf("uae") >= 0)
            return qsTr("MIDDLE EAST")
        return qsTr("OTHER")
    }

    function regionChoiceItems() {
        const items = [{ kind: "choice", label: qsTr("Automatic"), detail: qsTr("Lowest latency"), value: "" }]
        const regions = (ShellStore.regions || []).slice()
        let lastGroup = ""
        for (let i = 0; i < regions.length; ++i) {
            const region = regions[i]
            const group = root.regionGroup(region.name)
            if (group !== lastGroup) {
                items.push({ kind: "heading", label: group })
                lastGroup = group
            }
            const ping = root.regionPingMs(region.url)
            items.push({
                kind: "choice",
                label: region.name,
                detail: ShellStore.regionPingBusy ? qsTr("Measuring…") : ping !== null ? (ping + " ms")
                    : ShellStore.regionPingResults[region.url] === null ? qsTr("No response") : qsTr("Not measured"),
                detailColor: ping === null ? "" : root.pingRankColor(ping),
                value: region.url
            })
        }
        return items
    }

    function currentRegionLabel() {
        const selected = String(valueSetting("region", ""))
        if (selected === "")
            return qsTr("Automatic")
        const regions = ShellStore.regions || []
        for (let i = 0; i < regions.length; ++i) {
            if (regions[i].name === selected || regions[i].url === selected) {
                const ping = ShellStore.regionPingResults ? ShellStore.regionPingResults[regions[i].url] : undefined
                const name = regions[i].name
                return ping === undefined || ping === null || ping === "" ? name : (name + " · " + ping + " ms")
            }
        }
        return selected
    }

    function regionPingMs(url) {
        const raw = ShellStore.regionPingResults ? ShellStore.regionPingResults[url] : undefined
        const n = Number(raw)
        return raw === undefined || raw === null || raw === "" || !Number.isFinite(n) ? null : n
    }

    function currentRegionPing() {
        const selected = String(root.valueSetting("region", ""))
        if (selected === "") {
            const measured = (ShellStore.regions || []).map(region => root.regionPingMs(region.url))
                .filter(ping => ping !== null)
            return measured.length ? Math.min.apply(null, measured) : null
        }
        const regions = ShellStore.regions || []
        for (let i = 0; i < regions.length; ++i) {
            if (regions[i].name === selected || regions[i].url === selected)
                return root.regionPingMs(regions[i].url)
        }
        return null
    }

    // Ping quality ramp mirrors Electron's region picker: green <30,
    // lime <80, amber <150, red beyond. Unmeasured regions stay muted.
    function pingRankColor(ms) {
        if (ms === null || ms === undefined)
            return Theme.textMuted
        if (ms < 30)
            return "#58d98a"
        if (ms < 80)
            return "#84cc16"
        if (ms < 150)
            return "#eab308"
        return "#ef4444"
    }

    function pingBarsLit(ms) {
        if (ms === null || ms === undefined)
            return 0
        if (ms < 80)
            return 3
        if (ms < 150)
            return 2
        return 1
    }

    function themeMeta(id) {
        const packs = {
            aurora: { name: qsTr("Aurora"), blurb: qsTr("Cool teal shell with a mint focus ring."), accent: "#56E6A5" },
            nocturne: { name: qsTr("Nocturne"), blurb: qsTr("Near-black nocturne shell with a sky focus ring."), accent: "#7FD4FF" },
            kraft: { name: qsTr("Kraft"), blurb: qsTr("Warm brown shell with a brass focus ring."), accent: "#C6A46A" },
            phosphor: { name: qsTr("Mint"), blurb: qsTr("Deep green shell with a phosphor focus ring."), accent: "#56E6A5" },
            hibiscus: { name: qsTr("Sunset"), blurb: qsTr("Wine shell with a rose focus ring."), accent: "#FF8A80" },
            chapel: { name: qsTr("Chapel"), blurb: qsTr("Violet night shell with a gold focus ring."), accent: "#FFD166" },
            bone: { name: qsTr("Bone"), blurb: qsTr("Light paper shell for daytime use."), accent: "#C6A46A" },
            cobalt: { name: qsTr("Cobalt"), blurb: qsTr("Light blue-white shell with a cobalt focus ring."), accent: "#7FD4FF" }
        }
        return packs[id] || { name: String(id || qsTr("Theme")), blurb: qsTr("Installed theme pack.") }
    }

    function planChips() {
        const sub = ShellStore.subscription
        if (!sub)
            return [ShellStore.signedIn ? qsTr("Entitlements unavailable") : qsTr("Sign in to load entitlements")]
        const chips = []
        const resolutions = sub.entitledResolutions || []
        for (let i = 0; i < Math.min(4, resolutions.length); ++i) {
            const item = resolutions[i]
            const width = Number(item.width || 0)
            const height = Number(item.height || 0)
            const fps = Number(item.fps || 0)
            if (width >= 3840)
                chips.push("4K" + (fps ? " · " + fps + " FPS" : ""))
            else if (width >= 2560)
                chips.push("1440P" + (fps ? " · " + fps + " FPS" : ""))
            else if (width >= 1920)
                chips.push("1080P" + (fps ? " · " + fps + " FPS" : ""))
            else if (height > 0)
                chips.push(height + "P" + (fps ? " · " + fps + " FPS" : ""))
        }
        if (sub.isUnlimited)
            chips.push(qsTr("UNLIMITED"))
        else if (sub.remainingHours !== undefined && sub.remainingHours !== null)
            chips.push(qsTr("%1 h left").arg(Math.max(0, Math.round(Number(sub.remainingHours)))))
        if (chips.length === 0)
            chips.push(String(sub.membershipTier || qsTr("Unavailable")).toUpperCase())
        return chips
    }

    // Frame-rate helpers share ShellStore's entitlement logic (mirroring
    // Electron's getFpsForResolution); these are display wrappers only.
    function fpsEntitlementKnown() {
        return Boolean(ShellStore.subscription)
    }

    function currentResolutionValue() {
        return String(root.valueSetting("resolution", "1920x1080"))
    }

    function unentitledFpsValues() {
        return ShellStore.unentitledFpsValues(root.currentResolutionValue())
    }

    function fpsEntitlementNote() {
        if (!root.fpsEntitlementKnown())
            return ShellStore.signedIn
                ? qsTr("Loading your membership entitlements…")
                : qsTr("Sign in to unlock rates beyond 60 FPS")
        const entitled = ShellStore.entitledFpsForResolution(root.currentResolutionValue())
        const tier = root.liveTierBadge() || qsTr("Membership")
        if (entitled.length === 0)
            return qsTr("%1 · no exact entitlement for this resolution").arg(tier)
        const max = entitled[entitled.length - 1]
        return qsTr("%1 · up to %2 FPS at %3").arg(tier).arg(max)
            .arg(root.currentResolutionValue().replace("x", "×"))
    }

    function fpsLockedHint() {
        const tier = root.liveTierBadge()
        return tier
            ? qsTr("Not entitled on %1 — upgrade on NVIDIA to unlock").arg(tier)
            : qsTr("Not entitled on your current membership")
    }

    function clampFpsToEntitlement() {
        ShellStore.clampFpsToEntitlement()
    }

    function storeLetter(account) {
        const provider = String(account.provider || account.label || "").trim()
        return provider ? provider.charAt(0).toUpperCase() : ""
    }

    function storeIcon(account) {
        return DesktopTokens.storeIconUrl(account.provider || account.label)
            || "qrc:/qt/qml/OpenNOW/res/icons/desktop-nav-store.svg"
    }

    function storeAccent(account) {
        const provider = String(account.provider || account.label || "").toLowerCase()
        if (provider.indexOf("steam") >= 0) return Theme.cartSteam
        if (provider.indexOf("epic") >= 0) return Theme.cartEpic
        if (provider.indexOf("ubisoft") >= 0 || provider.indexOf("uplay") >= 0) return Theme.cartUbisoft
        if (provider.indexOf("xbox") >= 0) return Theme.cartXbox
        if (provider.indexOf("gog") >= 0) return Theme.cartGog
        if (provider.indexOf("battle") >= 0) return Theme.cartBattlenet
        return Qt.rgba(1, 1, 1, 0.08)
    }

    function storeStatus(account) {
        if (account.status === "expired")
            return { text: qsTr("EXPIRED"), color: Theme.yellow, action: qsTr("Reconnect"), connected: false, primary: true }
        if (account.status === "sync_error")
            return { text: qsTr("SYNC ISSUE"), color: Theme.coral, action: qsTr("Resync"), connected: true }
        if (account.isConnected || account.status === "connected")
            return { text: qsTr("LINKED"), color: DesktopTokens.green, action: account.supportsSync ? qsTr("Resync") : qsTr("Unlink"), connected: true }
        return { text: qsTr("NOT LINKED"), color: Theme.textMuted, action: qsTr("Link"), connected: false }
    }

    function storeDescription(account) {
        if (account.displayName)
            return account.displayName
        if (account.isConnected && account.syncedGames !== undefined && account.syncedGames !== null)
            return qsTr("%1 cloud-ready games synced").arg(account.syncedGames)
        if (account.isConnected)
            return qsTr("Connected through your NVIDIA account")
        return qsTr("Link this store on NVIDIA to add its games to your library")
    }

    function runStoreAction(account) {
        const status = storeStatus(account)
        if (status.action === qsTr("Resync"))
            ShellStore.syncGameAccount(account.provider)
        else if (status.connected)
            ShellStore.unlinkGameAccount(account.provider)
        else
            ShellStore.startAccountLink(account.provider)
    }

    function projectLinks() {
        return [
            {id: "source", label: qsTr("Source on GitHub"), hint: "↗"},
            {id: "issues", label: qsTr("Report an issue"), hint: "↗"},
            {id: "diagnostics", label: qsTr("Copy diagnostics"), hint: "NO PERSONAL DATA"},
            {id: "captures", label: qsTr("Reveal captures folder"), hint: ShellStore.mediaRootPath ? "↗" : "…"}
        ]
    }

    function runProjectLink(link) {
        if (!link)
            return
        if (link.id === "source")
            AppController.openExternalUrl("https://github.com/OpenCloudGaming/OpenNOW")
        else if (link.id === "issues")
            AppController.openExternalUrl("https://github.com/OpenCloudGaming/OpenNOW/issues")
        else if (link.id === "diagnostics")
            ShellStore.exportDiagnostics()
        else if (link.id === "captures") {
            if (ShellStore.mediaRootPath)
                AppController.openLocalPath(ShellStore.mediaRootPath, false)
            else
                ShellStore.refreshMedia()
        }
    }

    function resolutionItems() {
        const groups = [
            ["16:9 STANDARD", [["720p","1280x720"],["900p","1600x900"],["1080p","1920x1080"],["1440p","2560x1440"],["1800p","3200x1800"],["4K","3840x2160"],["5K","5120x2880"],["8K","7680x4320"]]],
            ["16:10 WIDESCREEN", [["800p","1280x800"],["900p","1440x900"],["1050p","1680x1050"],["1200p","1920x1200"],["1600p","2560x1600"],["2400p","3840x2400"]]],
            ["21:9 ULTRAWIDE", [["UW 1080p","2560x1080"],["UW 1440p","3440x1440"],["UW 1600p","3840x1600"],["UW 1800p","3840x1800"],["UW 2160p","5120x2160"]]],
            ["32:9 SUPER ULTRAWIDE", [["Dual 1080p","3840x1080"],["Dual 1440p","5120x1440"]]]
        ]
        const items = []
        for (const group of groups) {
            items.push({kind:"heading",label:group[0]})
            for (const option of group[1])
                items.push({kind:"choice",label:option[0],detail:option[1].replace("x","×"),value:option[1],
                    disabled: root.fpsEntitlementKnown() && ShellStore.entitledFpsForResolution(option[1]).length === 0})
        }
        return items
    }

    Column {
        id: settingsRail
        x: DesktopTokens.px(24)
        y: DesktopTokens.px(18)
        width: root.compactNavigation ? root.width - x * 2 : DesktopTokens.px(272)
        spacing: DesktopTokens.px(14)

        TextField {
            id: settingsSearch
            objectName: "settingsSearch"
            width: parent.width
            height: DesktopTokens.px(44)
            placeholderText: qsTr("Search settings")
            text: root.searchQuery
            onTextEdited: root.searchQuery = text
            color: Theme.label
            placeholderTextColor: Theme.textMuted
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.bodySize
            leftPadding: 42
            DesktopGlyph { x: 16; anchors.verticalCenter: parent.verticalCenter; width: 16; height: 16; icon: "desktop-search.svg" }
            background: Rectangle { radius: 10; color: Theme.glass; border.width: 1; border.color: settingsSearch.activeFocus ? Theme.focus : Theme.seam }
            onAccepted: {
                for (let i = 0; i < root.sections.length; ++i) {
                    if (root.matchesSection(root.sections[i])) {
                        root.selectedSection = root.sections[i].page
                        break
                    }
                }
            }
        }
        Flickable {
            width: parent.width
            height: root.compactNavigation ? DesktopTokens.px(52) : Math.max(0, root.height - settingsRail.y - settingsSearch.height - 36)
            contentWidth: root.compactNavigation ? navigation.implicitWidth : width
            contentHeight: navigation.implicitHeight
            clip: true
            boundsBehavior: Flickable.StopAtBounds
            Flow {
                id: navigation
                width: root.compactNavigation ? implicitWidth : parent.width
                flow: root.compactNavigation ? Flow.TopToBottom : Flow.LeftToRight
                height: root.compactNavigation ? DesktopTokens.px(52) : implicitHeight
                spacing: DesktopTokens.px(6)
                Repeater {
                    model: root.sections
                    delegate: Button {
                        required property var modelData
                        required property int index
                        visible: root.matchesSection(modelData)
                        width: root.compactNavigation ? DesktopTokens.px(130) : settingsRail.width
                        height: DesktopTokens.px(root.compactNavigation ? 48 : 64)
                        padding: 12
                        hoverEnabled: true
                        onClicked: root.selectedSection = modelData.page
                        background: Rectangle {
                            radius: 16
                            color: root.selectedGroup === index ? DesktopTokens.raisedStrong : parent.hovered ? DesktopTokens.raised : "transparent"
                            border.width: parent.activeFocus ? 2 : 0
                            border.color: Theme.focus
                        }
                        contentItem: RowLayout {
                            spacing: 14
                            Rectangle {
                                visible: !root.compactNavigation
                                Layout.preferredWidth: 40; Layout.preferredHeight: 40
                                radius: 12; color: root.selectedGroup === index ? Theme.face : DesktopTokens.raised
                                DesktopSettingsIcon { anchors.centerIn: parent; width: 20; height: 20; glyph: modelData.icon; ink: root.selectedGroup === index ? Theme.faceText : Theme.label }
                            }
                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2
                                Text { Layout.fillWidth: true; text: modelData.label; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.bodySize; font.weight: root.selectedGroup === index ? Font.ExtraBold : Font.Bold; elide: Text.ElideRight }
                                Text { visible: !root.compactNavigation; Layout.fillWidth: true; text: modelData.detail; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.captionSize; elide: Text.ElideRight }
                            }
                        }
                    }
                }
            }
        }
    }

    Item {
        id: contentLane
        x: root.compactNavigation ? settingsRail.x : settingsRail.x + settingsRail.width + DesktopTokens.px(20)
        y: root.compactNavigation ? settingsRail.y + settingsRail.height + DesktopTokens.px(16) : settingsRail.y
        width: root.width - x - DesktopTokens.px(24)
        height: root.height - y - DesktopTokens.px(18)
        Flickable {
            id: contentFlick
            anchors.fill: parent
            contentWidth: width
            contentHeight: pageLoader.height
            clip: true
            boundsBehavior: Flickable.StopAtBounds
            ScrollBar.vertical: ScrollBar { policy: ScrollBar.AsNeeded }
            Loader {
                id: pageLoader
                objectName: "settingsPageLoader"
                width: contentFlick.width
                sourceComponent: SmokeTestMode && root.acceptancePanel !== ""
                    ? root.acceptancePanels[root.acceptancePanel] : root.pageComponents[root.selectedSection]
                opacity: sectionEntrance.pageOpacity
                transform: Translate { y: sectionEntrance.offset }
                PageEntrance { id: sectionEntrance; objectName: "settingsPageEntrance" }
                onLoaded: { contentFlick.contentY = 0; sectionEntrance.restart() }
            }
        }
    }



    Connections {
        target: ShellStore
        function onSubscriptionChanged() { root.clampFpsToEntitlement() }
    }

    Component {
        id: accountGroup
        Column {
            width: contentFlick.width; spacing: 20
            Loader { width: parent.width; sourceComponent: profilePage }
            Loader { width: parent.width; sourceComponent: storesPage }
            DesktopSettingsAdvanced { detail: qsTr("Subscription · Profiles"); expanded: root.advancedOpen; onClicked: root.advancedOpen = !root.advancedOpen }
            DesktopSettingsDisclosure { objectName: "accountAdvancedDisclosure"; width: parent.width; expanded: root.advancedOpen; sourceComponent: subscriptionPage }
        }
    }
    Component {
        id: controlsGroup
        Column {
            id: controlsRoot
            property bool shortcutsOpen: root.selectedSection === 10
            width: contentFlick.width; spacing: 20
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
                    DesktopSettingsToggle { checked: root.boolSetting("enableGyroscopeControls",false); onValueChangedByUser: value => root.setSetting("enableGyroscopeControls",value) }
                }
                DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("Steam Input compatibility"); description: qsTr("Use the compatibility path for Steam-managed controllers"); showDivider: false
                    DesktopSettingsToggle { checked: root.boolSetting("steamControllerCompatibilityMode",false); onValueChangedByUser: value => root.setSetting("steamControllerCompatibilityMode",value) }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; paperStyle: true
                DesktopSettingsSection { text: qsTr("MOUSE & KEYBOARD") }
                DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "mouse"; title: qsTr("Mouse capture"); description: qsTr("Follows the remote cursor · F8 toggles capture")
                    DesktopSettingsSegmented { options: [qsTr("Automatic")]; optionWidth: 112; selectedIndex: 0 }
                }
                DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "arrows"; title: qsTr("Mouse sensitivity"); description: qsTr("Applied to native relative mouse input")
                    DesktopSettingsSlider { trackWidth: 320; from: 0.1; to: 3; stepSize: 0.05; decimals: 2; suffix: "×"; value: Number(root.valueSetting("mouseSensitivity",1)); onCommitted: value => root.setSetting("mouseSensitivity",value) }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "keyboard"; title: qsTr("Shortcuts")
                    objectName: "renewShortcutsDisclosure"
                    description: qsTr("Local shortcuts are consumed before gameplay input"); showDivider: false; expandable: true
                    expanded: controlsRoot.shortcutsOpen
                    onExpansionRequested: controlsRoot.shortcutsOpen = !controlsRoot.shortcutsOpen
                    Row { spacing: 10
                        DesktopKeyHint { keyText: String(root.valueSetting("shortcutToggleStats","Ctrl+N")); label: qsTr("stats") }
                        DesktopKeyHint { keyText: "Ctrl G"; label: qsTr("menu") }
                        DesktopKeyHint { keyText: "F11"; label: qsTr("fullscreen") }
                    }
                }
            }
            DesktopSettingsDisclosure { objectName: "renewInlineShortcuts"; width: parent.width; expanded: controlsRoot.shortcutsOpen; sourceComponent: shortcutsPage }
            DesktopSettingsDisclosure {
                width: parent.width; expanded: controlsRoot.shortcutsOpen
                sourceComponent: DesktopSettingsPanel {
                    width: contentFlick.width; paperStyle: true
                    DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "keyboard"; title: qsTr("Statistics shortcut"); showDivider: false
                        DesktopSettingsField { width: DesktopTokens.px(200); text: String(root.valueSetting("shortcutToggleStats","Ctrl+N")); Accessible.name: qsTr("Statistics shortcut"); onEditingFinished: root.setSetting("shortcutToggleStats",text) }
                    }
                }
            }
            DesktopSettingsAdvanced { detail: qsTr("Controller behavior · Cursor"); expanded: root.advancedOpen; onClicked: root.advancedOpen = !root.advancedOpen }
            DesktopSettingsDisclosure { width: parent.width; expanded: root.advancedOpen; sourceComponent: controllersPage }
            DesktopSettingsDisclosure {
                width: parent.width; expanded: root.advancedOpen
                sourceComponent: DesktopSettingsPanel {
                    width: contentFlick.width; paperStyle: true
                    DesktopSettingsSection { text: qsTr("KEYBOARD & CURSOR") }
                    DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "mouse"; title: qsTr("Cursor overlay"); showDivider: false
                        DesktopSettingsToggle { checked: root.boolSetting("nativeCursorOverlay",true); onValueChangedByUser: value => root.setSetting("nativeCursorOverlay",value) }
                    }
                }
            }
        }
    }

    Component {
        id: lookGroup
        Column {
            width: contentFlick.width; spacing: 20
            property bool allThemes: false
            DesktopSettingsPanel {
                width: parent.width; paperStyle: true
                DesktopSettingsSection { text: qsTr("THEME") }
                DesktopSettingsChoice {
                    objectName: "renewThemeChoice"
                    width: parent.width; glyph: "moon"; title: qsTr("Theme")
                    description: qsTr("Uses the existing OpenNOW theme collection")
                    items: ["aurora","nocturne","kraft","phosphor","hibiscus","chapel","bone","cobalt"].map(id => ({label:root.themeMeta(id).name,detail:root.themeMeta(id).blurb,value:id}))
                    value: root.valueSetting("themePack","nocturne")
                    onSelected: value => root.setChoice("themePack",value)
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "moon"; title: qsTr("Appearance")
                    DesktopSettingsSegmented { options: ["auto","light","dark"]; selectedIndex: options.indexOf(root.valueSetting("appTheme","auto")); onSelected: (index,value) => root.setSetting("appTheme",value) }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "palette"; title: qsTr("Accent"); description: qsTr("Selection, toggles and keyboard focus")
                    Row {
                        spacing: 10
                        Repeater {
                            model: [{value:"green",color:"#6EE7B7"},{value:"blue",color:"#7FD4FF"},{value:"violet",color:"#A78BFA"},{value:"rose",color:"#FF8A9A"},{value:"coral",color:"#FF8A80"},{value:"amber",color:"#FFD166"},{value:"white",color:"#FFFFFF"}]
                            delegate: AbstractButton {
                                required property var modelData
                                width: 28; height: 28; Accessible.name: modelData.value
                                onClicked: root.setChoice("appAccentColor",modelData.value)
                                background: Rectangle {
                                    radius: 14; color: parent.modelData.color
                                    border.width: parent.activeFocus || root.valueSetting("appAccentColor","green") === parent.modelData.value ? 3 : 0
                                    border.color: Theme.lightMode ? "#111827" : "#FFFFFF"
                                }
                            }
                        }
                    }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "image"; title: qsTr("Background"); description: qsTr("Artwork, gradient or a solid theme color"); showDivider: false
                    DesktopSettingsSegmented {
                        options: [{label:qsTr("Game art"),value:"art"},{label:qsTr("Gradient"),value:"gradient"},{label:qsTr("Solid"),value:"solid"}]; optionWidth: 80
                        selectedIndex: options.findIndex(item => item.value === root.valueSetting("desktopBackground","art"))
                        onSelected: (index,item) => root.setSetting("desktopBackground",item.value)
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
                        selectedIndex: Number(root.valueSetting("posterSizeScale",1.05)) < 1 ? 0 : Number(root.valueSetting("posterSizeScale",1.05)) > 1.1 ? 2 : 1
                        onSelected: index => root.setSetting("posterSizeScale",[0.9,1.05,1.25][index])
                    }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "sidebar"; title: qsTr("Sidebar opens on hover"); description: qsTr("Expands over the page without moving it")
                    DesktopSettingsToggle { checked: root.boolSetting("desktopSidebarHover",true); onValueChangedByUser: value => root.setSetting("desktopSidebarHover",value) }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "clock"; title: qsTr("Session clock in stream"); description: qsTr("Small timer while playing"); showDivider: false
                    DesktopSettingsToggle { checked: root.boolSetting("sessionCounterEnabled",false); onValueChangedByUser: value => root.setSetting("sessionCounterEnabled",value) }
                }
            }
            Loader { width: parent.width; sourceComponent: statsSettingsPage }
            DesktopSettingsAdvanced { detail: qsTr("Language · Interface scale · Motion"); expanded: root.advancedOpen; onClicked: root.advancedOpen = !root.advancedOpen }
            DesktopSettingsDisclosure {
                width: parent.width; expanded: root.advancedOpen
                sourceComponent: DesktopSettingsPanel {
                    width: contentFlick.width; paperStyle: true
                    DesktopSettingsRow {
                        width: parent.width; paperStyle: true; glyph: "grid"; title: qsTr("Interface scale"); showDivider: false
                        DesktopSettingsSlider { from: 0.85; to: 1.25; stepSize: 0.05; decimals: 2; suffix: "×"; value: Number(root.valueSetting("desktopUiScale",1)); onCommitted: value => root.setSetting("desktopUiScale",value) }
                    }
                }
            }
            DesktopSettingsDisclosure { width: parent.width; expanded: root.advancedOpen; sourceComponent: interfacePage }
        }
    }

    Component {
        id: statsSettingsPage
        Column {
            width: contentFlick.width; spacing: 20
            DesktopSettingsPanel {
                width: parent.width; paperStyle: true
                DesktopSettingsSection { text: qsTr("FRAME-RATE VIEW") }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "speed"; title: qsTr("Show on stream launch")
                    description: qsTr("Cycle compact bar, extended panel and off with your statistics shortcut")
                    DesktopSettingsToggle { checked: root.boolSetting("showStatsOnLaunch",false); onValueChangedByUser: value => { root.setSetting("showStatsOnLaunch",value); root.setSetting("showNativeStreamerStats",value) } }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "grid"; title: qsTr("Position")
                    DesktopSettingsSegmented {
                        options: [{label:qsTr("Top left"),value:"top-left"},{label:qsTr("Top right"),value:"top-right"},{label:qsTr("Bottom left"),value:"bottom-left"},{label:qsTr("Bottom right"),value:"bottom-right"}]
                        optionWidth: 102; selectedIndex: options.findIndex(item => item.value === root.valueSetting("statsOverlayPosition","top-right"))
                        onSelected: (index,item) => root.setSetting("statsOverlayPosition",item.value)
                    }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "arrows"; title: qsTr("Overlay scale")
                    DesktopSettingsSlider { from: 0.85; to: 1.5; stepSize: 0.05; decimals: 2; suffix: "×"; value: Number(root.valueSetting("statsOverlayScale",1)); onCommitted: value => root.setSetting("statsOverlayScale",value) }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "sun"; title: qsTr("Background opacity"); showDivider: false
                    DesktopSettingsSlider { from: 40; to: 100; stepSize: 5; value: Number(root.valueSetting("statsOverlayOpacity",85)); onCommitted: value => root.setSetting("statsOverlayOpacity",Math.round(value)) }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; paperStyle: true
                DesktopSettingsSection { text: qsTr("VISIBLE METRICS") }
                GridLayout {
                    width: parent.width; columns: width >= DesktopTokens.px(820) ? 2 : 1; columnSpacing: 0; rowSpacing: 0
                    Repeater {
                        model: [
                            {key:"statsShowFps",label:qsTr("Stream FPS"),glyph:"speed"},
                            {key:"statsShowRegion",label:qsTr("Stream region and rig"),glyph:"globe"},
                            {key:"statsShowPing",label:qsTr("Ping"),glyph:"wave"},
                            {key:"statsShowBitrate",label:qsTr("Bitrate"),glyph:"wave"},
                            {key:"statsShowJitter",label:qsTr("Jitter"),glyph:"wave"},
                            {key:"statsShowDrops",label:qsTr("Frame drops"),glyph:"monitor"},
                            {key:"statsShowPacketLoss",label:qsTr("Packet loss"),glyph:"arrows"},
                            {key:"statsShowDecode",label:qsTr("Decode time"),glyph:"chip"},
                            {key:"statsShowLatency",label:qsTr("Latency"),glyph:"clock"},
                            {key:"statsShowVideo",label:qsTr("Codec and video format"),glyph:"image"},
                            {key:"statsShowClock",label:qsTr("Session clock"),glyph:"clock"},
                            {key:"statsShowGraphs",label:qsTr("Live graphs"),glyph:"wave"}
                        ]
                        delegate: DesktopSettingsRow {
                            required property var modelData
                            Layout.fillWidth: true; Layout.preferredWidth: 1; paperStyle: true; glyph: modelData.glyph; title: modelData.label
                            DesktopSettingsToggle { objectName: "renew-" + modelData.key; checked: root.boolSetting(modelData.key,true); onValueChangedByUser: value => root.setSetting(modelData.key,value) }
                        }
                    }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "bolt"; title: qsTr("L4S")
                    description: qsTr("Request scalable low-latency transport for the next session"); showDivider: false
                    DesktopSettingsToggle { checked: root.boolSetting("enableL4S",false); onValueChangedByUser: value => root.setSetting("enableL4S",value) }
                }
            }
        }
    }

    Component {
        id: profilePage
        DesktopSettingsPanel {
            id: profilePanel
            width: contentFlick.width; paperStyle: true
            DesktopSettingsSection { text: qsTr("NVIDIA ACCOUNT") }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; title: root.profileName(); description: root.maskedEmail()
                leadingLetter: root.profileInitial(); leadingColor: Theme.focus; rowHeight: 76
                DesktopSettingsButton { text: ShellStore.signedIn ? qsTr("Sign out") : qsTr("Sign in"); onClicked: ShellStore.signedIn ? ShellStore.logout() : AppController.navigate("sign-in") }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "crown"; title: root.liveTierBadge() || qsTr("Membership unavailable")
                description: root.planChips().join(" · "); expandable: true; expanded: root.advancedOpen
                onExpansionRequested: root.advancedOpen = !root.advancedOpen
                Text { visible: ShellStore.subscription && ShellStore.subscription.remainingHours !== undefined; text: qsTr("%1 h left").arg(ShellStore.subscription ? ShellStore.subscription.remainingHours : ""); color: Theme.label; font.family: Theme.monoFont; font.pixelSize: 12; font.weight: Font.Bold }
                DesktopSettingsButton { text: qsTr("Manage"); onClicked: Qt.openUrlExternally("https://www.nvidia.com/en-us/account/") }
            }
            DesktopSettingsRow { objectName: "accountActivitySharing"; width: parent.width; paperStyle: true; glyph: "person"; title: qsTr("Show what I am playing"); description: qsTr("Discord activity sharing")
                DesktopSettingsToggle { checked: root.boolSetting("discordRichPresence",false); onValueChangedByUser: value => root.setSetting("discordRichPresence",value) }
            }
            DesktopSettingsRow { objectName: "accountCrashReports"; width: parent.width; paperStyle: true; glyph: "info"; title: qsTr("Crash reports"); description: qsTr("Optional error reporting"); showDivider: false
                DesktopSettingsToggle { checked: ShellStore.settings.errorReportingConsent === "granted"; onValueChangedByUser: value => root.setSetting("errorReportingConsent",value ? "granted" : "denied") }
            }
        }
    }

    Component {
        id: subscriptionPage
        DesktopSettingsPanel {
            width: contentFlick.width; paperStyle: true
            DesktopSettingsSection { text: qsTr("SUBSCRIPTION") }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "crown"
                title: root.liveTierBadge() || qsTr("Membership unavailable")
                description: qsTr("Plans and billing are managed by NVIDIA, not OpenNOW")
                DesktopSettingsButton { text: qsTr("Manage on NVIDIA"); onClicked: AppController.openExternalUrl("https://www.nvidia.com/en-us/account/") }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "monitor"; title: qsTr("Entitlements reported by NVIDIA")
                description: root.planChips().join(" · ")
                DesktopSettingsButton { text: qsTr("Refresh entitlements"); onClicked: ShellStore.refreshAccountServices() }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "person"; title: qsTr("Profiles")
                description: qsTr("Manage saved account profiles"); showDivider: false
                DesktopSettingsButton { text: qsTr("Manage"); onClicked: AppController.navigate("accounts") }
            }
        }
    }

    Component {
        id: storesPage
        DesktopSettingsPanel {
            width: contentFlick.width; paperStyle: true
            Item {
                width: parent.width; height: 56
                Column {
                    x: 20; y: 10; spacing: 2; width: parent.width-220
                    Text { text: qsTr("GAME STORES"); color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 12; font.weight: Font.ExtraBold; font.letterSpacing: 1.2 }
                    Text { width: parent.width; text: qsTr("%1 stores from your NVIDIA account").arg(ShellStore.gameAccounts.length); elide: Text.ElideRight; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(12.5) }
                }
                DesktopSettingsButton { anchors.right: parent.right; anchors.rightMargin: 16; anchors.verticalCenter: parent.verticalCenter; text: ShellStore.gameAccountsState === "loading" ? qsTr("Syncing…") : qsTr("Sync now"); enabled: ShellStore.gameAccountsState !== "loading"; onClicked: ShellStore.refreshGameAccounts() }
            }
            Repeater {
                model: ShellStore.gameAccounts
                delegate: DesktopSettingsRow {
                    required property int index
                    required property var modelData
                    readonly property var status: root.storeStatus(modelData)
                    width: parent.width; paperStyle: true; rowHeight: 56
                    leadingLetter: root.storeLetter(modelData); leadingIcon: root.storeIcon(modelData); leadingColor: root.storeAccent(modelData)
                    title: modelData.label || modelData.provider; description: root.storeDescription(modelData)
                    showDivider: index < ShellStore.gameAccounts.length-1
                    Item {
                        width: 112; height: 28
                        Rectangle { width: 6; height: 6; radius: 3; anchors.verticalCenter: parent.verticalCenter; color: status.color }
                        Text { x: 12; width: 100; anchors.verticalCenter: parent.verticalCenter; text: status.text; color: status.color; font.family: Theme.monoFont; font.pixelSize: 10; font.weight: Font.Bold; elide: Text.ElideRight }
                    }
                    DesktopSettingsButton { width: 92; text: status.action; compact: true; primary: Boolean(status.primary); enabled: ShellStore.gameAccountsState !== "loading"; onClicked: root.runStoreAction(modelData) }
                }
            }
            Text {
                x: 20; width: parent.width-40; topPadding: 12; bottomPadding: 16; wrapMode: Text.WordWrap
                text: ShellStore.gameAccountMessage || qsTr("Linking happens on NVIDIA's side.")
                color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 12
            }
        }
    }

    Component {
        id: streamPage
        Column {
            width: contentFlick.width; spacing: 20
            DesktopSettingsPanel {
                width: parent.width; paperStyle: true
                DesktopSettingsSection { text: qsTr("PICTURE") }
                DesktopSettingsResolution {
                    width: parent.width; items: root.resolutionItems()
                    value: root.currentResolutionValue()
                    onSelected: value => root.setSetting("resolution", value)
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "speed"; title: qsTr("Frame rate"); description: root.fpsEntitlementNote()
                    DesktopSettingsSegmented {
                        readonly property string current: Number(root.valueSetting("fps",60)) === 0 ? "AUTO" : String(root.valueSetting("fps",60))
                        options: ["60","90","120","144","240"].indexOf(current) >= 0 ? ["60","90","120","144","240"] : [current,"60","90","120","144","240"]
                        optionWidth: 50; selectedIndex: options.indexOf(current)
                        disabledValues: root.unentitledFpsValues(); disabledHint: root.fpsLockedHint()
                        onSelected: (index,value) => root.setSetting("fps",value === "AUTO" ? 0 : Number(value))
                    }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "sun"; title: qsTr("HDR")
                    description: qsTr("HDR cannot currently be selected by the native session API")
                    DesktopSettingsToggle { checked: false; enabled: false; opacity: 0.45; Accessible.name: qsTr("HDR unavailable") }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "drop"; title: qsTr("Color depth")
                    description: root.colorQualityFooter(); showDivider: false
                    DesktopSettingsSegmented {
                        options: root.colorQualityItems().filter(item => item.value !== "8bit_444" || root.valueSetting("colorQuality","8bit_420") === "8bit_444").map(item => ({label:item.value === "8bit_420" ? "8-bit" : item.value === "10bit_420" ? "10-bit" : item.value === "8bit_444" ? "8-bit 4:4:4" : "10-bit 4:4:4", value:item.value, enabled:!item.disabled}))
                        optionWidth: 85; selectedIndex: options.findIndex(item => item.value === root.valueSetting("colorQuality","8bit_420"))
                        onSelected: (index,item) => root.setChoice("colorQuality",item.value)
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; paperStyle: true
                DesktopSettingsSection { text: qsTr("CONNECTION") }
                DesktopSettingsChoice {
                    objectName: "streamBackendChoice"
                    width: parent.width; glyph: "chip"; title: qsTr("Video backend")
                    description: Qt.platform.os === "windows"
                        ? qsTr("Auto uses DX11 hardware decoding. DX12 and Vulkan texture sharing are not supported by the Windows stream view yet. Applies to the next stream.")
                        : qsTr("Choose a supported native backend. Applies to the next stream.")
                    items: ShellStore.videoBackendItems()
                    value: root.valueSetting("nativeVideoBackend", "auto")
                    onSelected: value => root.setSetting("nativeVideoBackend", value)
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "chip"; title: qsTr("Codec")
                    description: ShellStore.streamerDetectionMessage
                    DesktopSettingsSegmented {
                        options: [{label:qsTr("Auto"),value:"auto"},{label:"AV1",value:"av1",enabled:ShellStore.codecAvailable("av1")},{label:"H.265",value:"h265",enabled:ShellStore.codecAvailable("h265")},{label:"H.264",value:"h264",enabled:ShellStore.codecAvailable("h264")}]
                        disabledHint: qsTr("Not supported by the detected native decoder")
                        optionWidth: 64; selectedIndex: options.findIndex(item => item.value === root.valueSetting("codec","auto"))
                        onSelected: (index,item) => root.setChoice("codec",item.value)
                    }
                }
                DesktopSettingsRow {
                    id: bitrateRow
                    width: parent.width; paperStyle: true; glyph: "wave"; title: qsTr("Bitrate"); description: qsTr("Maximum requested bitrate")
                    DesktopSettingsSlider {
                        trackWidth: Math.max(DesktopTokens.px(160), bitrateRow.width - DesktopTokens.px(460)); from: 10; to: 200; stepSize: 5
                        value: Number(root.valueSetting("maxBitrateMbps",75)); suffix: " Mbps"
                        onCommitted: value => root.setSetting("maxBitrateMbps",Math.round(value))
                    }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "bolt"; title: qsTr("Reflex low latency")
                    description: qsTr("When the game supports it"); showDivider: false
                    DesktopSettingsToggle { checked: root.boolSetting("enableCloudGsync",false); onValueChangedByUser: value => root.setSetting("enableCloudGsync",value) }
                }
            }
            DesktopSettingsAdvanced { detail: qsTr("Steam Deck identity"); expanded: root.advancedOpen; onClicked: root.advancedOpen = !root.advancedOpen }
            DesktopSettingsDisclosure {
                width: parent.width; expanded: root.advancedOpen
                sourceComponent: DesktopSettingsPanel {
                    width: contentFlick.width; paperStyle: true
                    DesktopSettingsRow {
                        width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("Steam Deck identity"); description: qsTr("Unlock Deck resolutions and 90 FPS · refreshes entitlements")
                        DesktopSettingsToggle { checked: root.boolSetting("identifyAsSteamDeck",false); onValueChangedByUser: value => root.setSetting("identifyAsSteamDeck",value) }
                    }
                }
            }
        }
    }

    Component {
        id: audioPage
        DesktopSettingsPanel {
            width: contentFlick.width; paperStyle: true
            DesktopSettingsSection { text: qsTr("AUDIO") }
            DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "wave"; title: qsTr("Output device"); description: qsTr("The native streamer follows your operating system output"); value: qsTr("SYSTEM DEFAULT") }
            DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "sliders"; title: qsTr("Game volume"); description: qsTr("Use the system mixer or the game's own audio settings"); value: qsTr("SYSTEM MIXER") }
            DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "wave"; title: qsTr("Microphone upstream"); description: qsTr("Microphone upstream is unavailable for NVST sessions"); value: qsTr("UNAVAILABLE") }
            DesktopSettingsRow { width: parent.width; paperStyle: true; glyph: "info"; title: qsTr("Audio format"); description: qsTr("Audio format and channel count are negotiated with the active GeForce NOW session."); showDivider: false }
        }
    }

    Component {
        id: controllersPage
        DesktopSettingsPanel {
            width: contentFlick.width; paperStyle: true
            DesktopSettingsSection { text: qsTr("CONTROLLER BEHAVIOR") }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("Controller input")
                description: qsTr("%1 CONNECTED").arg(AppController.controllerCount)
                DesktopSettingsToggle { checked: root.boolSetting("controllerMode",true); onValueChangedByUser: value => root.setSetting("controllerMode",value) }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("Switch to console mode when a pad wakes up")
                description: qsTr("Open the gamepad-first shell when a controller becomes active")
                DesktopSettingsToggle { checked: root.boolSetting("switchToConsoleOnPad",false); onValueChangedByUser: value => root.setSetting("switchToConsoleOnPad",value) }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "mouse"; title: qsTr("Return to desktop on pointer input")
                description: qsTr("Mouse movement leaves console mode after the current input hold"); showDivider: false
                DesktopSettingsToggle { checked: root.boolSetting("leaveConsoleOnPointer",true); onValueChangedByUser: value => root.setSetting("leaveConsoleOnPointer",value) }
            }
        }
    }

    Component {
        id: networkPage
        Column {
            width: contentFlick.width; spacing: 20
            Component.onCompleted: ShellStore.refreshRegions()
            DesktopSettingsPanel {
                width: parent.width; paperStyle: true
                DesktopSettingsSection { text: qsTr("CONNECTION") }
                DesktopSettingsChoice {
                    objectName: "renewNetworkRegion"
                    width: parent.width; glyph: "globe"; title: qsTr("Server region")
                    description: ShellStore.regions.length ? qsTr("%1 streaming regions from your account").arg(ShellStore.regions.length) : qsTr("Sign in to discover available regions")
                    items: root.regionChoiceItems()
                    value: {
                        const selected = String(root.valueSetting("region",""))
                        const region = ShellStore.regions.find(item => item.url === selected || item.name === selected)
                        return region ? region.url : selected
                    }
                    valueLabel: root.currentRegionLabel()
                    onSelected: value => root.setSetting("region",value)
                }
                DesktopSettingsRow {
                    objectName: "renewRegionLatency"
                    width: parent.width; paperStyle: true; glyph: "speed"; title: qsTr("Region latency")
                    description: ShellStore.regionPingMessage || qsTr("Measure available regions before your next session")
                    value: ShellStore.regionPingBusy || root.currentRegionPing() === null ? ""
                        : root.valueSetting("region", "") === "" ? qsTr("Best: %1 ms").arg(root.currentRegionPing())
                        : root.currentRegionPing() + " ms"
                    DesktopSettingsButton {
                        objectName: "renewRegionPingButton"
                        text: ShellStore.regionPingPending ? qsTr("Loading…") : ShellStore.regionPingBusy ? qsTr("Pinging…") : qsTr("Ping regions")
                        enabled: !ShellStore.regionPingBusy
                        onClicked: ShellStore.pingRegions()
                    }
                }
                DesktopSettingsRow {
                    id: bandwidthRow
                    width: parent.width; paperStyle: true; glyph: "wave"; title: qsTr("Bandwidth ceiling")
                    description: qsTr("Maximum requested bitrate"); showDivider: false
                    DesktopSettingsSlider {
                        trackWidth: Math.max(DesktopTokens.px(160), bandwidthRow.width-DesktopTokens.px(460))
                        from: 10; to: 200; stepSize: 5; suffix: " Mbps"
                        value: Number(root.valueSetting("maxBitrateMbps",75))
                        onCommitted: value => root.setSetting("maxBitrateMbps",Math.round(value))
                    }
                }
            }
            DesktopSettingsPanel {
                width: parent.width; paperStyle: true
                DesktopSettingsSection { text: qsTr("API PROXY") }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "globe"; title: qsTr("Use proxy")
                    description: qsTr("Applies to API calls only · the stream always goes direct")
                    DesktopSettingsToggle { objectName: "renewProxyEnabled"; checked: root.boolSetting("sessionProxyEnabled",false); onValueChangedByUser: value => root.setSetting("sessionProxyEnabled",value) }
                }
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "arrows"; title: qsTr("Proxy address")
                    description: qsTr("Leave empty to use a direct connection"); showDivider: false
                    DesktopSettingsField {
                        objectName: "renewProxyAddress"
                        width: DesktopTokens.px(320)
                        text: String(root.valueSetting("sessionProxyUrl",""))
                        placeholderText: qsTr("http://proxy.example:8080")
                        Accessible.name: qsTr("Proxy address")
                        onEditingFinished: root.setSetting("sessionProxyUrl",text)
                    }
                }
            }
            DesktopSettingsAdvanced {
                detail: qsTr("Low-latency transport")
                expanded: root.advancedOpen; onClicked: root.advancedOpen = !root.advancedOpen
            }
            DesktopSettingsDisclosure {
                width: parent.width; expanded: root.advancedOpen
                sourceComponent: DesktopSettingsPanel {
                    width: contentFlick.width; paperStyle: true
                    DesktopSettingsSection { text: qsTr("TRANSPORT") }
                    DesktopSettingsRow {
                        width: parent.width; paperStyle: true; glyph: "bolt"; title: qsTr("L4S")
                        description: qsTr("Request scalable low-latency transport for the next session"); showDivider: false
                        DesktopSettingsToggle { checked: root.boolSetting("enableL4S",false); onValueChangedByUser: value => root.setSetting("enableL4S",value) }
                    }
                }
            }
        }
    }

    Component {
        id: interfacePage
        DesktopSettingsPanel {
            width: contentFlick.width; paperStyle: true
            DesktopSettingsSection { text: qsTr("INTERFACE") }
            DesktopSettingsChoice {
                objectName: "renewLanguageChoice"
                width: parent.width; glyph: "globe"; title: qsTr("Language"); description: qsTr("Community translated through Crowdin")
                items: [{label:qsTr("System"),value:"system"},{label:"Deutsch",value:"de"},{label:"English",value:"en"},{label:"Español",value:"es"},{label:"Français",value:"fr"},{label:qsTr("Japanese"),value:"ja"},{label:qsTr("Korean"),value:"ko"},{label:"Nederlands",value:"nl"},{label:"Polski",value:"pl"},{label:"Română",value:"ro"},{label:"Русский",value:"ru"},{label:"Türkçe",value:"tr"},{label:qsTr("Chinese"),value:"zh"}]
                value: root.valueSetting("appLanguage","en")
                onSelected: value => root.setChoice("appLanguage",value)
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "sidebar"; title: qsTr("Collapsed sidebar")
                description: qsTr("Show icons only · Ctrl B toggles")
                DesktopSettingsToggle { checked: root.boolSetting("desktopRailCollapsed",true); onValueChangedByUser: value => root.setSetting("desktopRailCollapsed",value) }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "wave"; title: qsTr("Reduce motion")
                description: qsTr("Cuts parallax and cover animations · follows your OS by default")
                DesktopSettingsToggle { checked: root.boolSetting("reducedMotion",false); onValueChangedByUser: value => root.setSetting("reducedMotion",value) }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "sun"; title: qsTr("Translucent interface")
                description: qsTr("Use translucent shell surfaces when supported"); showDivider: false
                DesktopSettingsToggle { checked: root.boolSetting("translucentUI",false); onValueChangedByUser: value => root.setSetting("translucentUI",value) }
            }
        }
    }


    Component {
        id: consolePage
        DesktopSettingsPanel {
            width: contentFlick.width; paperStyle: true
            DesktopSettingsSection { text: qsTr("CONSOLE MODE") }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("One app, two shells")
                description: qsTr("Same session, settings and themes · switching does not restart the stream")
                DesktopSettingsButton { text: ShellStore.consoleSurfaceRequestId === "" ? qsTr("Switch now") : qsTr("Switching…"); suffix: "F10"; primary: true; enabled: ShellStore.consoleSurfaceRequestId === ""; onClicked: ShellStore.requestConsoleSurface(true) }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("Start in console mode")
                description: qsTr("Remember this choice for the next time OpenNOW launches")
                DesktopSettingsToggle { checked: root.boolSetting("launchInConsoleMode",false); onValueChangedByUser: value => root.setSetting("launchInConsoleMode",value) }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("Enter console mode when a gamepad is the only input")
                description: qsTr("Ignored while a mouse has moved in the last 30 seconds")
                DesktopSettingsToggle { checked: root.boolSetting("switchToConsoleOnPad",false); onValueChangedByUser: value => root.setSetting("switchToConsoleOnPad",value) }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "mouse"; title: qsTr("Leave console mode on keyboard or mouse input")
                description: qsTr("Keeps your place in the grid when the shell swaps")
                DesktopSettingsToggle { checked: root.boolSetting("leaveConsoleOnPointer",true); onValueChangedByUser: value => root.setSetting("leaveConsoleOnPointer",value) }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "monitor"; title: qsTr("Go fullscreen in console mode")
                description: qsTr("Recommended on a TV · hides the window chrome entirely")
                DesktopSettingsToggle { checked: root.boolSetting("autoFullScreen",false); onValueChangedByUser: value => root.setSetting("autoFullScreen",value) }
            }
            DesktopSettingsRow {
                width: parent.width; paperStyle: true; glyph: "person"; title: qsTr("Controller profile picker")
                description: qsTr("Choose a saved profile when console mode starts"); showDivider: false
                DesktopSettingsToggle { checked: root.boolSetting("consoleProfilePickerOnLaunch",true); onValueChangedByUser: value => root.setSetting("consoleProfilePickerOnLaunch",value) }
            }
        }
    }

    Component {
        id: shortcutsPage
        Column {
            id: shortcutsPageRoot
            width: contentFlick.width; spacing: 14
            property string shortcutQuery: ""
            property bool confirmReset: false

            function allShortcutGroups() {
                return [
                    {h:"APP", rows:[{l:"Command palette",k:"Ctrl  K"},{l:"Search this page",k:"/"},{l:"Collapse or expand the sidebar",k:"Ctrl  B"},{l:"Switch to console mode",k:"F10"},{l:"Settings",k:"Ctrl  ,"},{l:"Quit OpenNOW",k:"Ctrl  Q"}]},
                    {h:"IN STREAM", rows:[{l:"Session menu",k:"Esc"},{l:"Stats overlay",k:String(root.valueSetting("shortcutToggleStats","Ctrl+N"))},{l:"Toggle fullscreen",k:String(root.valueSetting("shortcutToggleFullscreen","F11"))},{l:"Grab or release the mouse",k:String(root.valueSetting("shortcutTogglePointerLock","F8"))},{l:"Screenshot the stream",k:String(root.valueSetting("shortcutScreenshot","Ctrl+F11"))},{l:"End the session",k:String(root.valueSetting("shortcutStopStream","Ctrl+Shift+Q"))}]},
                    {h:"LIBRARY AND STORE", rows:[{l:"Move through covers",k:"Arrows"},{l:"Play or resume",k:"Enter"},{l:"Game details",k:"Space"},{l:"Toggle favourite",k:"F"},{l:"Context menu",k:"Shift  F10"}]},
                    {h:"GAMEPAD · CONSOLE MODE", rows:[{l:"Select · back",k:"A · B"},{l:"Details · favourite",k:"X · Y"},{l:"Switch tab",k:"LB · RB"},{l:"Stats overlay",k:"Guide"}]}
                ]
            }

            function shortcutGroups() {
                const query = shortcutsPageRoot.shortcutQuery.trim().toLocaleLowerCase()
                const groups = shortcutsPageRoot.allShortcutGroups()
                if (query === "")
                    return groups
                const result = []
                for (let i = 0; i < groups.length; ++i) {
                    const rows = []
                    for (let j = 0; j < groups[i].rows.length; ++j) {
                        const row = groups[i].rows[j]
                        if (String(row.l).toLocaleLowerCase().indexOf(query) >= 0
                                || String(row.k).toLocaleLowerCase().indexOf(query) >= 0
                                || String(groups[i].h).toLocaleLowerCase().indexOf(query) >= 0)
                            rows.push(row)
                    }
                    if (rows.length > 0)
                        result.push({h: groups[i].h, rows: rows})
                }
                return result
            }

            DesktopSettingsPanel {
                width: parent.width; paperStyle: true
                DesktopSettingsSection { text: qsTr("SHORTCUTS") }
                Item {
                    width: parent.width; height: DesktopTokens.px(64)
                    DesktopSettingsField {
                        x: 20; anchors.verticalCenter: parent.verticalCenter; width: parent.width-40
                        placeholderText: qsTr("Search commands or bindings…")
                        Accessible.name: qsTr("Search shortcuts")
                        onTextChanged: shortcutsPageRoot.shortcutQuery = text
                    }
                }
            }
            Column {
                width: parent.width; spacing: 20
                Repeater {
                    model: shortcutsPageRoot.shortcutGroups()
                    delegate: DesktopSettingsPanel {
                        required property var modelData
                        width: parent.width; paperStyle: true
                        DesktopSettingsSection { text: modelData.h }
                        Repeater {
                            model: modelData.rows
                            delegate: DesktopSettingsRow {
                                required property var modelData
                                required property int index
                                width: parent.width; paperStyle: true; glyph: "keyboard"; title: modelData.l
                                rowHeight: DesktopTokens.px(56)
                                Rectangle {
                                    width: Math.max(DesktopTokens.px(72), keyCapText.implicitWidth+28); height: DesktopTokens.px(32)
                                    radius: 10; color: DesktopTokens.raised; border.width: 1; border.color: Theme.seam
                                    Text { id: keyCapText; anchors.centerIn: parent; text: modelData.k; color: Theme.label; font.family: Theme.monoFont; font.pixelSize: DesktopTokens.px(12); font.weight: Font.DemiBold }
                                }
                            }
                        }
                    }
                }
            }
            Text {
                visible: shortcutsPageRoot.shortcutQuery !== "" && shortcutsPageRoot.shortcutGroups().length === 0
                width: parent.width; horizontalAlignment: Text.AlignHCenter
                text: qsTr("No bindings match “%1”.").arg(shortcutsPageRoot.shortcutQuery)
                color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.captionSize
            }
            DesktopSettingsPanel {
                width: parent.width; paperStyle: true
                DesktopSettingsRow {
                    width: parent.width; paperStyle: true; glyph: "sliders"; title: qsTr("Reset all settings")
                    description: shortcutsPageRoot.confirmReset ? qsTr("This resets all preferences, not only shortcuts. Continue?") : qsTr("Restore OpenNOW preferences to their defaults")
                    showDivider: false
                    DesktopSettingsButton { visible: shortcutsPageRoot.confirmReset; text: qsTr("Cancel"); onClicked: shortcutsPageRoot.confirmReset = false }
                    DesktopSettingsButton { text: shortcutsPageRoot.confirmReset ? qsTr("Confirm reset") : qsTr("Reset"); danger: true; onClicked: { if (shortcutsPageRoot.confirmReset) { ShellStore.resetSettings(); shortcutsPageRoot.confirmReset = false } else shortcutsPageRoot.confirmReset = true } }
                }
            }
        }
    }

    Component {
        id: aboutPage
        Column {
            width: contentFlick.width; spacing: 20
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
                        optionWidth: 96; selectedIndex: options.findIndex(item => item.value === root.valueSetting("updateChannel","stable"))
                        onSelected: (index,item) => root.setChoice("updateChannel",item.value)
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
                    model: root.projectLinks()
                    delegate: DesktopSettingsRow {
                        required property var modelData
                        required property int index
                        width: parent.width; paperStyle: true; glyph: modelData.id === "diagnostics" ? "wave" : modelData.id === "captures" ? "image" : "globe"
                        title: modelData.label
                        description: modelData.id === "diagnostics" ? qsTr("Generate a diagnostic report") : ""
                        showDivider: index < 3
                        DesktopSettingsButton { text: modelData.id === "diagnostics" ? qsTr("Export") : qsTr("Open"); onClicked: root.runProjectLink(modelData) }
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
    }

}
