import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page

    property bool reducedMotion: false
    signal reducedMotionChangedByUser(bool value)
    property int sectionIndex: Number(appState.preference("settings.section", 0))
    readonly property var sectionNames: [
        "Account",
        "Streaming",
        "Video & display",
        "Input & controllers",
        "Network",
        "Advanced"
    ]

    function selectSection(index) {
        sectionIndex = Math.max(0, Math.min(sectionNames.length - 1, index))
        appState.setPreference("settings.section", sectionIndex)
        Qt.callLater(function() {
            var currentSection = sectionStack.children[page.sectionIndex]
            var first = currentSection ? currentSection.firstControl : null
            if (first)
                first.forceActiveFocus()
        })
    }

    function moveSection(delta) {
        selectSection((sectionIndex + delta + sectionNames.length) % sectionNames.length)
    }

    function cycleSection(delta) {
        moveSection(delta)
    }

    function moveFocus(forward) {
        var window = page.Window.window
        if (!window || !window.activeFocusItem)
            return
        var next = window.activeFocusItem.nextItemInFocusChain(forward)
        if (next)
            next.forceActiveFocus()
    }

    onVisibleChanged: {
        if (visible)
            Qt.callLater(function() { sectionRepeater.itemAt(sectionIndex).forceActiveFocus() })
    }

    Keys.onPressed: function(event) {
        if (event.key === Qt.Key_PageUp) {
            moveSection(-1)
            event.accepted = true
        } else if (event.key === Qt.Key_PageDown) {
            moveSection(1)
            event.accepted = true
        } else if (event.key === Qt.Key_Up) {
            moveFocus(false)
            event.accepted = true
        } else if (event.key === Qt.Key_Down) {
            moveFocus(true)
            event.accepted = true
        }
    }

    component SurfaceCard: Rectangle {
        color: "#0d120f"
        radius: 14
        border.width: 1
        border.color: "#202a23"
    }

    component TinyCaps: Text {
        color: Theme.inkMuted
        font.family: Theme.monoFont.family
        font.pixelSize: 10
        font.weight: Font.Bold
        font.letterSpacing: 1.7
    }

    component SectionHeader: Item {
        id: headerControl
        property string title: ""
        property string description: ""
        property string status: ""
        implicitHeight: 74
        height: implicitHeight

        Column {
            anchors.left: parent.left
            anchors.top: parent.top
            spacing: 5
            Text {
                text: headerControl.title
                color: Theme.ink
                font.family: Theme.displayFont.family
                font.pixelSize: 29
                font.weight: Font.DemiBold
            }
            Text {
                text: headerControl.description
                color: Theme.inkMuted
                font.pixelSize: 13
            }
        }
        TinyCaps {
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.topMargin: 39
            text: headerControl.status
            color: Theme.accent
        }
    }

    component PersistToggle: AbstractButton {
        id: toggleControl
        property string title: ""
        property string description: ""
        property string preferenceKey: ""
        property bool defaultValue: false
        property bool storedChecked: !!appState.preference(preferenceKey, defaultValue)
        property bool compact: false
        signal changed(bool value)

        implicitHeight: compact ? 50 : 62
        activeFocusOnTab: true
        hoverEnabled: true
        onClicked: {
            storedChecked = !storedChecked
            appState.setPreference(preferenceKey, storedChecked)
            changed(storedChecked)
        }

        Connections {
            target: appState
            function onPreferenceChanged(key, value) {
                if (key === toggleControl.preferenceKey)
                    toggleControl.storedChecked = !!value
            }
            function onPreferencesReset() {
                toggleControl.storedChecked = toggleControl.defaultValue
            }
        }

        background: Rectangle {
            color: toggleControl.activeFocus ? "#121d16" : toggleControl.hovered ? "#111813" : "transparent"
            radius: 9
            border.width: toggleControl.activeFocus ? 1 : 0
            border.color: Theme.accent
        }
        contentItem: Item {
            Column {
                anchors.left: parent.left
                anchors.right: switchTrack.left
                anchors.leftMargin: 2
                anchors.rightMargin: 18
                anchors.verticalCenter: parent.verticalCenter
                spacing: 3
                Text {
                    text: toggleControl.title
                    color: toggleControl.enabled ? Theme.ink : Theme.inkMuted
                    font.pixelSize: 14
                    font.weight: Font.DemiBold
                }
                Text {
                    visible: text.length > 0
                    width: parent.width
                    text: toggleControl.description
                    color: Theme.inkMuted
                    font.pixelSize: 11
                    elide: Text.ElideRight
                }
            }
            Rectangle {
                id: switchTrack
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                width: 48
                height: 28
                radius: 14
                color: toggleControl.storedChecked ? Theme.accentStrong : "#202923"
                border.width: 1
                border.color: toggleControl.storedChecked ? Theme.accent : "#26302a"
                Rectangle {
                    y: 4
                    x: toggleControl.storedChecked ? parent.width - width - 4 : 4
                    width: 20
                    height: 20
                    radius: 10
                    color: toggleControl.storedChecked ? "#08150c" : "#6e7a72"
                    Behavior on x { NumberAnimation { duration: Theme.motionFast } }
                }
            }
        }
    }

    component PersistSegments: FocusScope {
        id: segmentControl
        property var options: []
        property var disabledOptions: []
        property string preferenceKey: ""
        property string defaultValue: ""
        property string storedValue: String(appState.preference(preferenceKey, defaultValue))
        property color selectedColor: Theme.accent
        signal changed(string value)
        implicitHeight: 42

        function choose(value) {
            if (disabledOptions.indexOf(value) !== -1)
                return
            storedValue = value
            appState.setPreference(preferenceKey, value)
            changed(value)
        }

        Connections {
            target: appState
            function onPreferenceChanged(key, value) {
                if (key === segmentControl.preferenceKey)
                    segmentControl.storedValue = String(value)
            }
            function onPreferencesReset() {
                segmentControl.storedValue = segmentControl.defaultValue
            }
        }

        RowLayout {
            anchors.fill: parent
            spacing: 7
            Repeater {
                model: segmentControl.options
                AbstractButton {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    activeFocusOnTab: true
                    hoverEnabled: true
                    enabled: segmentControl.disabledOptions.indexOf(String(modelData)) === -1
                    onClicked: segmentControl.choose(String(modelData))
                    Keys.onLeftPressed: function(event) {
                        var previous = nextItemInFocusChain(false)
                        if (previous) previous.forceActiveFocus()
                        event.accepted = true
                    }
                    Keys.onRightPressed: function(event) {
                        var next = nextItemInFocusChain(true)
                        if (next) next.forceActiveFocus()
                        event.accepted = true
                    }
                    background: Rectangle {
                        radius: 9
                        color: segmentControl.storedValue === String(modelData) ? segmentControl.selectedColor : parent.hovered ? "#151d18" : "#0c110e"
                        border.width: parent.activeFocus ? 2 : 1
                        border.color: parent.activeFocus ? Theme.ink : segmentControl.storedValue === String(modelData) ? Theme.accent : "#222b25"
                        opacity: parent.enabled ? 1 : 0.36
                    }
                    contentItem: Text {
                        text: String(modelData)
                        color: segmentControl.storedValue === String(modelData) ? "#07130a" : Theme.inkSoft
                        font.pixelSize: 12
                        font.weight: Font.DemiBold
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                    }
                }
            }
        }
    }

    component PersistSlider: FocusScope {
        id: sliderControl
        property string title: ""
        property string preferenceKey: ""
        property real defaultValue: 0
        property real from: 0
        property real to: 100
        property real stepSize: 1
        property int decimals: 0
        property string suffix: ""
        property real storedValue: Number(appState.preference(preferenceKey, defaultValue))
        signal changed(real value)
        implicitHeight: 48

        function formattedValue() {
            return storedValue.toFixed(decimals) + suffix
        }

        Connections {
            target: appState
            function onPreferenceChanged(key, value) {
                if (key === sliderControl.preferenceKey)
                    sliderControl.storedValue = Number(value)
            }
            function onPreferencesReset() {
                sliderControl.storedValue = sliderControl.defaultValue
            }
        }

        Text {
            anchors.left: parent.left
            anchors.verticalCenter: parent.verticalCenter
            width: 116
            text: sliderControl.title
            color: Theme.inkMuted
            font.pixelSize: 12
        }
        Slider {
            id: slider
            anchors.left: parent.left
            anchors.leftMargin: 116
            anchors.right: valueText.left
            anchors.rightMargin: 14
            anchors.verticalCenter: parent.verticalCenter
            from: sliderControl.from
            to: sliderControl.to
            stepSize: sliderControl.stepSize
            value: sliderControl.storedValue
            activeFocusOnTab: true
            onMoved: {
                sliderControl.storedValue = value
                appState.setPreference(sliderControl.preferenceKey, value)
                sliderControl.changed(value)
            }
            background: Rectangle {
                x: slider.leftPadding
                y: slider.topPadding + slider.availableHeight / 2 - height / 2
                width: slider.availableWidth
                height: 5
                radius: 3
                color: "#202a23"
                Rectangle {
                    width: slider.visualPosition * parent.width
                    height: parent.height
                    radius: 3
                    color: Theme.accent
                }
            }
            handle: Rectangle {
                x: slider.leftPadding + slider.visualPosition * (slider.availableWidth - width)
                y: slider.topPadding + slider.availableHeight / 2 - height / 2
                width: 18
                height: 18
                radius: 9
                color: Theme.ink
                border.width: slider.activeFocus ? 2 : 0
                border.color: Theme.accent
            }
        }
        Text {
            id: valueText
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            width: 66
            text: sliderControl.formattedValue()
            color: Theme.ink
            font.family: Theme.monoFont.family
            font.pixelSize: 11
            font.weight: Font.Bold
            horizontalAlignment: Text.AlignRight
        }
    }

    component ChoiceRow: FocusScope {
        id: choiceControl
        property string title: ""
        property var options: []
        property string preferenceKey: ""
        property string defaultValue: ""
        property string storedValue: String(appState.preference(preferenceKey, defaultValue))
        implicitHeight: 48

        function cycle(delta) {
            var index = options.indexOf(storedValue)
            if (index < 0) index = 0
            storedValue = String(options[(index + delta + options.length) % options.length])
            appState.setPreference(preferenceKey, storedValue)
        }

        Connections {
            target: appState
            function onPreferenceChanged(key, value) {
                if (key === choiceControl.preferenceKey)
                    choiceControl.storedValue = String(value)
            }
            function onPreferencesReset() {
                choiceControl.storedValue = choiceControl.defaultValue
            }
        }

        Text {
            anchors.left: parent.left
            anchors.verticalCenter: parent.verticalCenter
            width: 116
            text: choiceControl.title
            color: Theme.inkMuted
            font.pixelSize: 12
        }
        Rectangle {
            anchors.left: parent.left
            anchors.leftMargin: 116
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            height: 42
            radius: 9
            color: "#0a0f0c"
            border.color: "#202a23"
            AbstractButton {
                anchors.left: parent.left
                anchors.verticalCenter: parent.verticalCenter
                width: 40
                height: 36
                activeFocusOnTab: true
                onClicked: choiceControl.cycle(-1)
                background: Rectangle { radius: 7; color: parent.activeFocus ? "#183021" : "#121914" }
                contentItem: Text { text: "‹"; color: Theme.inkSoft; font.pixelSize: 22; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
            }
            Text {
                anchors.centerIn: parent
                text: choiceControl.storedValue
                color: Theme.ink
                font.family: Theme.monoFont.family
                font.pixelSize: 12
                font.weight: Font.Bold
            }
            AbstractButton {
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                width: 40
                height: 36
                activeFocusOnTab: true
                onClicked: choiceControl.cycle(1)
                background: Rectangle { radius: 7; color: parent.activeFocus ? "#183021" : "#121914" }
                contentItem: Text { text: "›"; color: Theme.inkSoft; font.pixelSize: 22; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
            }
        }
    }

    component SmallButton: AbstractButton {
        id: smallButton
        property bool danger: false
        property bool primary: false
        implicitWidth: Math.max(82, label.implicitWidth + 28)
        implicitHeight: 38
        activeFocusOnTab: true
        hoverEnabled: true
        background: Rectangle {
            radius: 9
            color: smallButton.primary ? Theme.accent : smallButton.hovered ? "#182019" : "#0b100d"
            border.width: smallButton.activeFocus ? 2 : 1
            border.color: smallButton.danger ? "#8b2727" : smallButton.activeFocus ? Theme.accent : "#334038"
        }
        contentItem: Text {
            id: label
            text: smallButton.text
            color: smallButton.primary ? "#07130a" : smallButton.danger ? "#ff6464" : Theme.ink
            font.pixelSize: 12
            font.weight: Font.DemiBold
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
        }
    }

    component KeybindRow: AbstractButton {
        id: keybind
        property string title: ""
        property string preferenceKey: ""
        property string defaultSequence: ""
        property string sequence: String(appState.preference(preferenceKey, defaultSequence))
        property bool capturing: false
        implicitHeight: 51
        activeFocusOnTab: true
        hoverEnabled: true
        onClicked: capturing = true

        Connections {
            target: appState
            function onPreferenceChanged(key, value) {
                if (key === keybind.preferenceKey)
                    keybind.sequence = String(value)
            }
            function onPreferencesReset() {
                keybind.sequence = keybind.defaultSequence
                keybind.capturing = false
            }
        }

        function keyName(event) {
            var parts = []
            if (event.modifiers & Qt.ControlModifier) parts.push("Ctrl")
            if (event.modifiers & Qt.ShiftModifier) parts.push("Shift")
            if (event.modifiers & Qt.AltModifier) parts.push("Alt")
            var name = event.text ? event.text.toUpperCase() : ""
            if (event.key >= Qt.Key_F1 && event.key <= Qt.Key_F12)
                name = "F" + (event.key - Qt.Key_F1 + 1)
            else if (event.key === Qt.Key_Space) name = "Space"
            else if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter) name = "Enter"
            if (!name.length) return ""
            parts.push(name)
            return parts.join("+")
        }

        Keys.onPressed: function(event) {
            if (!capturing)
                return
            if (event.key === Qt.Key_Escape) {
                capturing = false
                event.accepted = true
                return
            }
            var next = keyName(event)
            if (next.length) {
                sequence = next
                appState.setPreference(preferenceKey, sequence)
                capturing = false
                event.accepted = true
            }
        }

        background: Rectangle {
            radius: 8
            color: keybind.activeFocus || keybind.capturing ? "#101a13" : keybind.hovered ? "#111713" : "transparent"
            border.width: keybind.activeFocus || keybind.capturing ? 1 : 0
            border.color: Theme.accent
        }
        contentItem: Item {
            Text {
                anchors.left: parent.left
                anchors.verticalCenter: parent.verticalCenter
                text: keybind.title
                color: Theme.inkSoft
                font.pixelSize: 12
            }
            Rectangle {
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                width: sequenceText.implicitWidth + 18
                height: 29
                radius: 7
                color: keybind.capturing ? "#174828" : "#101612"
                border.color: keybind.capturing ? Theme.accent : "#344038"
                Text {
                    id: sequenceText
                    anchors.centerIn: parent
                    text: keybind.capturing ? "press keys…" : keybind.sequence
                    color: keybind.capturing ? Theme.accent : Theme.ink
                    font.family: Theme.monoFont.family
                    font.pixelSize: 11
                    font.weight: Font.Bold
                }
            }
        }
    }

    component AccountSection: Item {
        id: accountSection
        property Item firstControl: signOutButton
        function storeModel() {
            return (catalogEngine.connectedAccounts || []).map(function(account) {
                var detail = String(account.status || "unknown").replace(/_/g, " ").toUpperCase()
                if (account.displayName)
                    detail += " · " + account.displayName
                if (Number(account.syncedGames || 0) > 0)
                    detail += " · " + account.syncedGames + " games"
                return {
                    name: account.label || account.provider,
                    state: detail,
                    tone: account.status === "connected" ? "ok" : (account.status === "not_connected" ? "off" : "warn"),
                    connected: account.isConnected
                }
            })
        }

        SectionHeader {
            id: header
            width: parent.width
            title: "Account"
            description: "Your NVIDIA link, membership and connected game stores."
            status: "TIER: " + (catalogEngine.subscription.membershipTier || "—")
        }
        RowLayout {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: header.bottom
            anchors.bottom: parent.bottom
            spacing: 20

            ColumnLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 112
                    Rectangle {
                        anchors.left: parent.left
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.leftMargin: 22
                        width: 62; height: 62; radius: 31
                        color: "#1a241e"; border.color: "#35433a"
                        Text { anchors.centerIn: parent; text: (authEngine.accountName || "?").charAt(0).toUpperCase(); color: Theme.ink; font.pixelSize: 24; font.weight: Font.Bold }
                    }
                    Column {
                        anchors.left: parent.left; anchors.leftMargin: 104
                        anchors.verticalCenter: parent.verticalCenter; spacing: 3
                        Text { text: authEngine.accountName.length > 0 ? authEngine.accountName : "NVIDIA account"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                        Text { text: authEngine.accountEmail.length > 0 ? authEngine.accountEmail : "Signed in with NVIDIA"; color: Theme.inkMuted; font.pixelSize: 11 }
                        TinyCaps { text: "●  NVIDIA ACCOUNT LINKED"; color: Theme.accent; font.pixelSize: 9 }
                    }
                    SmallButton {
                        id: signOutButton
                        anchors.right: parent.right; anchors.rightMargin: 22; anchors.verticalCenter: parent.verticalCenter
                        text: "Sign out"
                        onClicked: authEngine.signOut()
                    }
                }
                SurfaceCard {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 162
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Membership"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: catalogEngine.subscription.membershipTier || "—"; color: Theme.accent }
                    GridLayout {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top
                        anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 58
                        columns: 2; rowSpacing: 12
                        Text { text: "Subscription"; color: Theme.inkMuted; font.pixelSize: 12 }
                        Text { Layout.fillWidth: true; text: catalogEngine.subscription.subscriptionType || "—"; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.Bold; horizontalAlignment: Text.AlignRight }
                        Text { text: "Remaining time"; color: Theme.inkMuted; font.pixelSize: 12 }
                        Text { Layout.fillWidth: true; text: catalogEngine.subscription.isUnlimited ? "Unlimited" : Number(catalogEngine.subscription.remainingHours || 0).toFixed(1) + " h"; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.Bold; horizontalAlignment: Text.AlignRight }
                        Text { text: "State"; color: Theme.inkMuted; font.pixelSize: 12 }
                        Text { Layout.fillWidth: true; text: catalogEngine.subscription.state || "—"; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.Bold; horizontalAlignment: Text.AlignRight }
                    }
                }
                SurfaceCard {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 162
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Cloud storage"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    Text { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: catalogEngine.subscription.storageAddon ? Number(catalogEngine.subscription.storageAddon.usedGb || 0).toFixed(1) + " GB used" : "Not included"; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 13; font.weight: Font.Bold }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 49; text: catalogEngine.subscription.storageAddon ? "Permanent storage · " + (catalogEngine.subscription.storageAddon.regionName || "region managed by GeForce NOW") : "No storage add-on was returned for this membership."; color: Theme.inkMuted; font.pixelSize: 11 }
                    Text { anchors.left: parent.left; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.bottomMargin: 24; width: parent.width - 44; text: catalogEngine.subscription.storageAddon ? Number(catalogEngine.subscription.storageAddon.sizeGb || 0).toFixed(1) + " GB total" : ""; wrapMode: Text.WordWrap; color: Theme.inkMuted; font.pixelSize: 10 }
                }
                Item { Layout.fillHeight: true }
            }

            ColumnLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 390
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Connected stores"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    SmallButton { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 16; text: "Refresh"; onClicked: catalogEngine.refreshConnectedAccounts() }
                    Column {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top
                        anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 58; spacing: 10
                        Repeater {
                            model: accountSection.storeModel()
                            Rectangle {
                                width: parent.width; height: 65; radius: 9
                                color: "#090d0b"
                                border.color: modelData.tone === "ok" ? Theme.accent : modelData.tone === "warn" ? "#5c470b" : "#202a23"
                                Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 14; anchors.topMargin: 11; text: modelData.name; color: modelData.tone === "off" ? Theme.inkMuted : Theme.ink; font.pixelSize: 13; font.weight: Font.DemiBold }
                                Text { anchors.left: parent.left; anchors.bottom: parent.bottom; anchors.leftMargin: 14; anchors.bottomMargin: 10; text: modelData.state; color: modelData.tone === "ok" ? Theme.accent : modelData.tone === "warn" ? "#eab308" : Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 9; font.weight: Font.Bold }
                                Row {
                                    anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; anchors.rightMargin: 10; spacing: 8
                                    Text { text: "Manage in GeForce NOW"; color: Theme.inkMuted; font.pixelSize: 10 }
                                }
                            }
                        }
                        Text { width: parent.width; text: accountSection.storeModel().length === 0 ? "No supported linked-store data was returned." : "Linking and disconnect actions remain owned by GeForce NOW."; color: Theme.inkMuted; wrapMode: Text.WordWrap; font.pixelSize: 10 }
                    }
                }
                SurfaceCard {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 220
                    Column {
                        anchors.fill: parent; anchors.margins: 22; spacing: 2
                        Text { text: "Account controls"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold; bottomPadding: 8 }
                        Text { width: parent.width; text: "Store linking, profile management and membership changes open in GeForce NOW. OpenNOW only displays data returned by your account."; wrapMode: Text.WordWrap; color: Theme.inkMuted; font.pixelSize: 12 }
                    }
                }
                Item { Layout.fillHeight: true }
            }
        }
    }

    component StreamingSection: Item {
        id: streamingSection
        property Item firstControl: aspectOptions
        property string resolution: String(appState.preference("streaming.resolution", "1440p (QHD)"))
        SectionHeader { id: header; width: parent.width; title: "Streaming"; description: "Quality, latency and the pipeline between the rig and this screen."; status: "PROFILE: BALANCED" }
        RowLayout {
            anchors.left: parent.left; anchors.right: parent.right; anchors.top: header.bottom; anchors.bottom: parent.bottom; spacing: 20
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 368
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Target format"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "FROM RIG ENTITLEMENT" }
                    PersistSegments { id: aspectOptions; anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 58; options: ["16:9 Standard  4", "16:10 Widescreen  5", "21:9 Ultrawide  2"]; preferenceKey: "streaming.aspect"; defaultValue: "16:9 Standard  4" }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 107; options: ["32:9 Super UW  1", "4:3 Legacy  1", "Other  3"]; preferenceKey: "streaming.aspect"; defaultValue: "16:9 Standard  4" }
                    Column {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 160; spacing: 7
                        Repeater {
                            model: [
                                { label: "1080p (FHD)", value: "1920 × 1080", enabled: true },
                                { label: "1440p (QHD)", value: "2560 × 1440", enabled: true },
                                { label: "4K (UHD)", value: "3840 × 2160", enabled: false }
                            ]
                            AbstractButton {
                                id: resolutionButton
                                width: parent.width; height: 40; enabled: modelData.enabled; activeFocusOnTab: true; hoverEnabled: true
                                onClicked: { streamingSection.resolution = modelData.label; appState.setPreference("streaming.resolution", modelData.label) }
                                background: Rectangle { radius: 8; color: "#0a0f0c"; border.width: resolutionButton.activeFocus ? 2 : 1; border.color: streamingSection.resolution === modelData.label ? Theme.accent : "#222b25"; opacity: resolutionButton.enabled ? 1 : .35 }
                                contentItem: Item {
                                    Text { anchors.left: parent.left; anchors.leftMargin: 13; anchors.verticalCenter: parent.verticalCenter; text: modelData.label; color: Theme.ink; font.pixelSize: 12; font.weight: Font.DemiBold }
                                    TinyCaps { visible: modelData.label === "1440p (QHD)"; anchors.left: parent.left; anchors.leftMargin: 125; anchors.verticalCenter: parent.verticalCenter; text: "CURRENT"; color: Theme.accent; font.pixelSize: 8 }
                                    Text { anchors.right: parent.right; anchors.rightMargin: 13; anchors.verticalCenter: parent.verticalCenter; text: modelData.value; color: streamingSection.resolution === modelData.label ? Theme.accent : Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10 }
                                }
                            }
                        }
                    }
                    Text { anchors.left: parent.left; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.bottomMargin: 37; text: "Frame rate"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 116; anchors.rightMargin: 22; anchors.bottomMargin: 27; options: ["30", "60", "120", "144", "165", "240"]; disabledOptions: ["165", "240"]; preferenceKey: "streaming.frameRate"; defaultValue: "120" }
                    Text { anchors.left: parent.left; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.bottomMargin: 8; text: "Greyed rates aren't offered at 1440p on this rig — drop to 1080p for 240."; color: Theme.inkMuted; font.pixelSize: 9 }
                }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 142
                    enabled: false
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Upscaling"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "UNAVAILABLE"; color: Theme.inkMuted }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 56; options: ["Off", "Spatial", "AI Temporal"]; preferenceKey: "streaming.upscaling"; defaultValue: "AI Temporal" }
                    PersistSlider { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; title: "Sharpness"; preferenceKey: "streaming.sharpness"; defaultValue: 62; from: 0; to: 100 }
                }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 160
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Codec & color"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "FALLBACK: AUTO" }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 70; text: "Codec"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 116; anchors.rightMargin: 22; anchors.topMargin: 56; options: ["Auto", "AV1", "H265", "H264"]; preferenceKey: "streaming.codec"; defaultValue: "Auto" }
                    Text { anchors.left: parent.left; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.bottomMargin: 34; text: "Color"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 116; anchors.rightMargin: 22; anchors.bottomMargin: 14; options: ["8-bit 4:2:0", "8-bit 4:4:4", "10-bit 4:2:0", "10-bit 4:4:4"]; preferenceKey: "streaming.color"; defaultValue: "8-bit 4:2:0" }
                }
                Item { Layout.fillHeight: true }
            }
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 150
                    enabled: false
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Frame generation"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "UNAVAILABLE"; color: Theme.inkMuted }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 58; options: ["Off", "2×", "3×"]; preferenceKey: "streaming.frameGeneration"; defaultValue: "2×" }
                    Text { anchors.left: parent.left; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.bottomMargin: 14; text: "Interpolates on this device after decode — 60 stream FPS presents at 120."; color: Theme.inkMuted; font.pixelSize: 10 }
                }
                SurfaceCard { enabled: false; Layout.fillWidth: true; Layout.preferredHeight: 82; PersistToggle { anchors.fill: parent; anchors.margins: 18; title: "NTFK · unavailable"; description: "The native client currently uses GeForce NOW WebRTC"; preferenceKey: "streaming.ntfk"; defaultValue: false; compact: true } }
                SurfaceCard { enabled: false; Layout.fillWidth: true; Layout.preferredHeight: 82; PersistToggle { anchors.fill: parent; anchors.margins: 18; title: "Variable refresh sync · unavailable"; description: "The production surface does not expose VRR control"; preferenceKey: "streaming.vrr"; defaultValue: false; compact: true } }
                SurfaceCard { Layout.fillWidth: true; Layout.preferredHeight: 86; PersistSlider { anchors.fill: parent; anchors.margins: 18; title: "Bitrate cap"; preferenceKey: "streaming.bitrate"; defaultValue: 75; from: 5; to: 100; suffix: " Mbps" } }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 112; color: "#080d0a"
                    TinyCaps { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 20; text: "LIVE STREAM TELEMETRY" }
                    Text { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 20; text: streamEngine.latencyMs > 0 ? streamEngine.latencyMs + " ms RTT" : "No active stream"; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.Bold }
                    RowLayout {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 20; anchors.rightMargin: 20; anchors.bottomMargin: 20; spacing: 6
                        Repeater {
                            model: [{t:streamEngine.resolution,c:"#315f43"},{t:streamEngine.codec,c:"#4ce87f"},{t:streamEngine.fps + " fps",c:"#a5f3c0"},{t:Math.round(streamEngine.bitrateKbps / 1000) + " Mbps",c:"#68736c"}]
                            ColumnLayout { Layout.fillWidth: true; spacing: 6; Rectangle { Layout.fillWidth: true; height: 7; radius: 4; color: modelData.c } Text { text: modelData.t; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 8 } }
                        }
                    }
                }
                Item { Layout.fillHeight: true }
            }
        }
    }

    component VideoSection: Item {
        id: videoSection
        property Item firstControl: decoder
        property string accentName: String(appState.preference("appearance.accent", "green"))
        SectionHeader { id: header; width: parent.width; title: "Video & display"; description: "How the decoded stream is presented, recorded and themed."; status: "OUTPUT: 2560×1440" }
        RowLayout {
            anchors.left: parent.left; anchors.right: parent.right; anchors.top: header.bottom; anchors.bottom: parent.bottom; spacing: 20
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 268
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Presentation"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 81; text: "Decoder"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistSegments { id: decoder; enabled: false; anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 150; anchors.rightMargin: 22; anchors.topMargin: 60; options: ["Auto", "Hardware", "Software"]; preferenceKey: "video.decoder"; defaultValue: "Auto" }
                    Text { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 116; text: "Hardware decode picks the OS API automatically — D3D11VA on Windows, VideoToolbox on macOS, VAAPI on Linux."; wrapMode: Text.WordWrap; color: Theme.inkMuted; font.pixelSize: 10 }
                    PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 151; title: "Enter fullscreen on launch"; description: "F10 toggles at any time"; preferenceKey: "video.fullscreenOnLaunch"; defaultValue: true; compact: true }
                    PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.bottomMargin: 12; title: "Stats overlay on launch"; description: "Position: bottom-left · toggle with Ctrl+N"; preferenceKey: "video.statsOnLaunch"; defaultValue: false; compact: true }
                }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 202
                    enabled: false
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Video shaders"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "GPU POST-PROCESS" }
                    PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 53; title: "Adaptive sharpen"; description: "Counteracts encoder softness"; preferenceKey: "video.sharpen.enabled"; defaultValue: true; compact: true }
                    PersistSlider { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 105; title: "Strength"; preferenceKey: "video.sharpen.strength"; defaultValue: .4; from: 0; to: 1; stepSize: .05; decimals: 2 }
                    PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.bottomMargin: 8; title: "Film grain"; description: "Masks banding on dark scenes"; preferenceKey: "video.filmGrain"; defaultValue: false; compact: true }
                }
                Item { Layout.fillHeight: true }
            }
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 275
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Appearance"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 81; text: "Theme"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 150; anchors.rightMargin: 22; anchors.topMargin: 60; options: ["Light", "Dark", "Auto"]; preferenceKey: "appearance.theme"; defaultValue: "Dark" }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 132; text: "Accent"; color: Theme.inkMuted; font.pixelSize: 12 }
                    Row {
                        anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 150; anchors.topMargin: 113; spacing: 14
                        Repeater {
                            model: [{n:"green",c:"#4ce87f"},{n:"blue",c:"#4899e8"},{n:"purple",c:"#9066ef"},{n:"orange",c:"#ffac16"},{n:"pink",c:"#ef6688"}]
                            AbstractButton {
                                id: accentButton
                                width: 42; height: 42; activeFocusOnTab: true
                                onClicked: { videoSection.accentName = modelData.n; appState.setPreference("appearance.accent", modelData.n) }
                                background: Rectangle { radius: 21; color: modelData.c; border.width: accentButton.activeFocus || videoSection.accentName === modelData.n ? 3 : 0; border.color: accentButton.activeFocus ? Theme.ink : "#d9ffe4" }
                            }
                        }
                    }
                    PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 166; title: "Console UI (big screen)"; description: "10-foot shell — controller, keyboard and mouse all work"; preferenceKey: "appearance.consoleUi"; defaultValue: true; compact: true }
                    PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.bottomMargin: 10; title: "Translucent overlays"; description: "Frosted settings and navigation panels"; preferenceKey: "appearance.translucent"; defaultValue: false; compact: true }
                }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 210
                    enabled: false
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Recording"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "UNAVAILABLE" }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 83; text: "Resolution"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 150; anchors.rightMargin: 22; anchors.topMargin: 62; options: ["720p", "1080p", "1440p"]; preferenceKey: "recording.resolution"; defaultValue: "720p" }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 137; text: "Frame rate"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 150; anchors.rightMargin: 22; anchors.topMargin: 116; options: ["30", "60"]; preferenceKey: "recording.frameRate"; defaultValue: "30" }
                    PersistSlider { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; title: "Bitrate"; preferenceKey: "recording.bitrate"; defaultValue: 8; from: 2; to: 30; suffix: " Mbps" }
                }
                Item { Layout.fillHeight: true }
            }
        }
    }

    component InputSection: Item {
        property Item firstControl: gyro
        SectionHeader { id: header; width: parent.width; title: "Input & controllers"; description: "Controllers, mouse, keyboard layout and in-stream shortcuts."; status: controllerInput.controllerCount + (controllerInput.controllerCount === 1 ? " CONTROLLER" : " CONTROLLERS") }
        RowLayout {
            anchors.left: parent.left; anchors.right: parent.right; anchors.top: header.bottom; anchors.bottom: parent.bottom; spacing: 20
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 292
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Controllers"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    Column {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 58; spacing: 10
                        Repeater {
                            model: controllerInput.connected ? [{p:"P1",n:controllerInput.controllerName,s:"ACTIVE",active:true}] : []
                            Rectangle {
                                width: parent.width; height: 53; radius: 9; color: "#090d0b"; border.color: modelData.active ? "#2f4638" : "#202a23"
                                TinyCaps { anchors.left: parent.left; anchors.leftMargin: 14; anchors.verticalCenter: parent.verticalCenter; text: modelData.p; color: modelData.active ? Theme.accent : Theme.inkMuted }
                                Text { anchors.left: parent.left; anchors.leftMargin: 44; anchors.verticalCenter: parent.verticalCenter; text: modelData.n; color: Theme.ink; font.pixelSize: 12; font.weight: Font.DemiBold }
                                TinyCaps { anchors.right: parent.right; anchors.rightMargin: 14; anchors.verticalCenter: parent.verticalCenter; text: modelData.s; color: modelData.active ? Theme.accent : Theme.inkMuted }
                            }
                        }
                    }
                    PersistToggle { id: gyro; enabled: false; anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 176; title: "Gyroscope controls · unavailable"; description: "Motion input is not encoded yet"; preferenceKey: "input.gyroscope"; defaultValue: false; compact: true }
                    PersistToggle { enabled: false; anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.bottomMargin: 12; title: "Steam Controller compatibility · unavailable"; description: "No compatibility shim is active"; preferenceKey: "input.steamControllerCompatibility"; defaultValue: false; compact: true }
                }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 298
                    enabled: false
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Mouse & keyboard"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    PersistSlider { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 52; title: "Sensitivity"; preferenceKey: "input.mouse.sensitivity"; defaultValue: 1; from: .1; to: 2; stepSize: .1; decimals: 1 }
                    PersistSlider { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 93; title: "Acceleration"; preferenceKey: "input.mouse.acceleration"; defaultValue: 1; from: 0; to: 2; stepSize: .1; decimals: 1 }
                    ChoiceRow { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 143; title: "Layout"; options: ["QWERTY · US Intl", "QWERTY · US", "AZERTY · French", "QWERTZ · German"]; preferenceKey: "input.keyboard.layout"; defaultValue: "QWERTY · US Intl" }
                    ChoiceRow { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 195; title: "Game language"; options: ["English (US)", "English (UK)", "Deutsch", "Français"]; preferenceKey: "input.gameLanguage"; defaultValue: "English (US)" }
                    PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.bottomMargin: 4; title: "Clipboard paste"; description: "Forward Ctrl+V into the session as keystrokes"; preferenceKey: "input.clipboardPaste"; defaultValue: true; compact: true }
                }
                Item { Layout.fillHeight: true }
            }
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 120
                    enabled: false
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Microphone · unavailable"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.bottomMargin: 21; options: ["Disabled", "Push-to-talk", "Voice activity"]; preferenceKey: "input.microphone"; defaultValue: "Disabled" }
                }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 480
                    enabled: false
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Shortcuts"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "REBINDING UNAVAILABLE" }
                    Column {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 58; spacing: 0
                        KeybindRow { width: parent.width; title: "Toggle stats overlay"; preferenceKey: "shortcut.stats"; defaultSequence: "Ctrl+N" }
                        KeybindRow { width: parent.width; title: "Toggle pointer lock"; preferenceKey: "shortcut.pointerLock"; defaultSequence: "Ctrl+L" }
                        KeybindRow { width: parent.width; title: "Toggle fullscreen"; preferenceKey: "shortcut.fullscreen"; defaultSequence: "F10" }
                        KeybindRow { width: parent.width; title: "Stop stream"; preferenceKey: "shortcut.stop"; defaultSequence: "Ctrl+Shift+Q" }
                        KeybindRow { width: parent.width; title: "Toggle anti-AFK"; preferenceKey: "shortcut.antiAfk"; defaultSequence: "Ctrl+Shift+K" }
                        KeybindRow { width: parent.width; title: "Toggle microphone"; preferenceKey: "shortcut.microphone"; defaultSequence: "Ctrl+Shift+M" }
                        KeybindRow { width: parent.width; title: "Screenshot"; preferenceKey: "shortcut.screenshot"; defaultSequence: "F11" }
                        KeybindRow { width: parent.width; title: "Toggle recording"; preferenceKey: "shortcut.recording"; defaultSequence: "F12" }
                    }
                }
                Item { Layout.fillHeight: true }
            }
        }
    }

    component NetworkSection: Item {
        id: networkSection
        property Item firstControl: autoRegion
        property string filterText: ""
        property string selectedServer: "Automatic"
        property bool communityProxy: false
        SectionHeader { id: header; width: parent.width; title: "Network"; description: "Region, routing, transport and connection resilience."; status: "REGION: AUTO" + (catalogEngine.vpcId ? " · " + catalogEngine.vpcId : "") }
        Shortcut { sequence: "Y"; enabled: page.visible && page.sectionIndex === 4; onActivated: serverFilter.forceActiveFocus() }
        RowLayout {
            anchors.left: parent.left; anchors.right: parent.right; anchors.top: header.bottom; anchors.bottom: parent.bottom; spacing: 20
            SurfaceCard {
                Layout.fillWidth: true; Layout.fillHeight: true
                Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Region"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 84; anchors.topMargin: 25; text: catalogEngine.regions.length + " API regions"; color: Theme.inkMuted; font.pixelSize: 10 }
                SmallButton { id: autoRegion; anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 20; text: "REFRESH"; primary: true; onClicked: catalogEngine.refreshServerInfo() }
                TextField {
                    id: serverFilter
                    anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 60
                    height: 42; placeholderText: "⌕  Filter servers — or press Y"; color: Theme.ink; placeholderTextColor: Theme.inkMuted; font.pixelSize: 12; activeFocusOnTab: true
                    background: Rectangle { radius: 9; color: "#080d0a"; border.width: serverFilter.activeFocus ? 2 : 1; border.color: serverFilter.activeFocus ? Theme.accent : "#202a23" }
                    onTextChanged: appState.setPreference("network.serverFilter", text)
                    Component.onCompleted: text = String(appState.preference("network.serverFilter", ""))
                }
                Column {
                    anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 120; spacing: 8
                    TinyCaps { text: "GEFORCE NOW REGIONS" }
                    Repeater {
                        model: catalogEngine.regions.slice(0, 6)
                        AbstractButton {
                            id: europeServerButton
                            width: parent.width; height: 49; activeFocusOnTab: true; hoverEnabled: true
                            onClicked: {}
                            background: Rectangle { radius: 9; color: europeServerButton.hovered ? "#111713" : "#090d0b"; border.width: europeServerButton.activeFocus ? 2 : 1; border.color: "#202a23" }
                            contentItem: Item {
                                Text { anchors.left: parent.left; anchors.leftMargin: 14; anchors.verticalCenter: parent.verticalCenter; text: modelData.name; color: Theme.ink; font.pixelSize: 13; font.weight: Font.DemiBold }
                                Text { id: latency; anchors.right: parent.right; anchors.rightMargin: 14; anchors.verticalCenter: parent.verticalCenter; text: "managed automatically"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10 }
                            }
                        }
                    }
                    TinyCaps { visible: false; text: "" }
                    Repeater {
                        model: []
                        AbstractButton {
                            id: americanServerButton
                            width: parent.width; height: 49; activeFocusOnTab: true; hoverEnabled: true
                            onClicked: { appState.setPreference("network.server", modelData.n); appState.setPreference("network.regionMode", "manual") }
                            background: Rectangle { radius: 9; color: americanServerButton.hovered ? "#111713" : "#090d0b"; border.width: americanServerButton.activeFocus ? 2 : 1; border.color: americanServerButton.activeFocus ? Theme.accent : "#202a23" }
                            contentItem: Item { Text { anchors.left: parent.left; anchors.leftMargin: 14; anchors.verticalCenter: parent.verticalCenter; text: modelData.n; color: Theme.inkSoft; font.pixelSize: 13; font.weight: Font.DemiBold } Text { anchors.right: parent.right; anchors.rightMargin: 14; anchors.verticalCenter: parent.verticalCenter; text: "▂  " + modelData.m; color: "#eab308"; font.family: Theme.monoFont.family; font.pixelSize: 10; font.weight: Font.Bold } }
                        }
                    }
                    RowLayout { width: parent.width; Text { Layout.fillWidth: true; text: "CloudMatch chooses the serving region during launch."; color: Theme.inkMuted; font.pixelSize: 10 } }
                }
            }
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 188
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Protocol"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 59; options: ["WebRTC", "NVST · COMING SOON"]; disabledOptions: ["NVST · COMING SOON"]; preferenceKey: "network.protocol"; defaultValue: "WebRTC" }
                    PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.bottomMargin: 12; title: "L4S congestion signalling"; description: "Low Latency, Low Loss, Scalable throughput — experimental"; preferenceKey: "network.l4s"; defaultValue: true; compact: true }
                }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 345
                    enabled: false
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Session proxy"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "COMMUNITY ACTIVE"; color: Theme.accent }
                    Rectangle {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 60; height: 113; radius: 10; color: "#0a100c"; border.color: Theme.accent
                        PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 14; anchors.rightMargin: 14; anchors.topMargin: 7; title: "Proxy unavailable"; description: "The native client uses direct NVIDIA endpoints"; preferenceKey: "network.communityProxy"; defaultValue: false; compact: true }
                        Rectangle { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.margins: 14; height: 36; radius: 7; color: "#070b08"; border.color: "#253028" }
                    }
                    PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 36; anchors.rightMargin: 36; anchors.topMargin: 187; title: "Custom proxy URL"; description: "Route session traffic through your own proxy"; preferenceKey: "network.customProxyEnabled"; defaultValue: false; compact: true; enabled: !networkSection.communityProxy }
                    TextField {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 36; anchors.rightMargin: 36; anchors.topMargin: 243; height: 36
                        text: String(appState.preference("network.customProxyUrl", "socks5://127.0.0.1:9050")); enabled: !networkSection.communityProxy; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 10; activeFocusOnTab: true
                        onEditingFinished: appState.setPreference("network.customProxyUrl", text)
                        background: Rectangle { radius: 7; color: "#070b08"; border.color: "#253028"; opacity: parent.enabled ? 1 : .45 }
                    }
                    Text { anchors.left: parent.left; anchors.bottom: parent.bottom; anchors.leftMargin: 36; anchors.bottomMargin: 18; text: "▣  Locked — the community proxy writes this URL. Turn it off to use your own."; color: "#eab308"; font.pixelSize: 10 }
                    Rectangle {
                        anchors.fill: parent; z: 20; radius: 12; color: "#0c110e"; border.color: Theme.divider
                        Column { anchors.centerIn: parent; width: parent.width - 60; spacing: 10
                            Text { anchors.horizontalCenter: parent.horizontalCenter; text: "Direct NVIDIA transport"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                            Text { width: parent.width; horizontalAlignment: Text.AlignHCenter; wrapMode: Text.WordWrap; text: "Proxy routing is not implemented in the native client, so no proxy credentials or fallback endpoints are configured."; color: Theme.inkMuted; font.pixelSize: 12 }
                        }
                    }
                }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 116; color: "#080d0a"
                    TinyCaps { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "LAST CONNECTION TEST" }
                    SmallButton { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 17; text: "Run again →"; onClicked: appState.setPreference("network.connectionTestRequested", Date.now()) }
                    RowLayout {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.bottomMargin: 18; spacing: 24
                        Repeater { model: [{v:streamEngine.bitrateKbps > 0 ? Math.round(streamEngine.bitrateKbps / 1000) : "—",l:"Mbps stream"},{v:streamEngine.latencyMs > 0 ? streamEngine.latencyMs : "—",l:"ms RTT"},{v:streamEngine.packetLoss.toFixed(2) + "%",l:"packet loss"},{v:streamEngine.phase.toUpperCase(),l:"runtime state"}]; Column { Text { text: modelData.v; color: index > 1 ? Theme.accent : Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 18; font.weight: Font.Bold } Text { text: modelData.l; color: Theme.inkMuted; font.pixelSize: 9 } } }
                    }
                }
                Item { Layout.fillHeight: true }
            }
        }
    }

    component AdvancedSection: Item {
        id: advancedSection
        property Item firstControl: protocol
        property string videoBackend: String(appState.preference("advanced.videoBackend", "Auto"))
        SectionHeader { id: header; width: parent.width; title: "Advanced"; description: "Native streamer, experiments, updates and diagnostics."; status: "PROTOCOL: WEBRTC · NVST SOON" }
        RowLayout {
            anchors.left: parent.left; anchors.right: parent.right; anchors.top: header.bottom; anchors.bottom: parent.bottom; spacing: 20
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 338
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Streamer engine"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "RUNNING · WIN · MAC · LINUX"; color: Theme.accent }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 90; text: "Protocol"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistSegments { id: protocol; anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 150; anchors.rightMargin: 22; anchors.topMargin: 67; options: ["WebRTC", "NVST · COMING SOON"]; disabledOptions: ["NVST · COMING SOON"]; preferenceKey: "network.protocol"; defaultValue: "WebRTC" }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 139; text: "Video backend"; color: Theme.inkMuted; font.pixelSize: 12 }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.rightMargin: 22; anchors.topMargin: 139; text: "DETECTED: WINDOWS 11" }
                    Flow {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 158; spacing: 7
                        Repeater {
                            model: ["Auto", "D3D11  WIN", "D3D12  WIN", "NVDEC  NV GPU", "Vulkan  ALL", "Software"]
                            SmallButton {
                                text: modelData; primary: advancedSection.videoBackend === modelData
                                onClicked: { advancedSection.videoBackend = modelData; appState.setPreference("advanced.videoBackend", modelData) }
                            }
                        }
                    }
                    Text { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 244; text: "Only this device's backends are listed — Linux shows VAAPI/V4L2, macOS shows VideoToolbox."; color: Theme.inkMuted; font.pixelSize: 10 }
                    Rectangle { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.margins: 22; height: 42; radius: 8; color: "#080c09"; border.color: "#202a23"; Text { anchors.left: parent.left; anchors.leftMargin: 14; anchors.verticalCenter: parent.verticalCenter; text: "streamer path: auto (bundled opennow-streamer)"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10 } }
                }
                AbstractButton {
                    Layout.fillWidth: true; Layout.preferredHeight: 82; activeFocusOnTab: true
                    property real held: 0
                    onPressed: holdTimer.start()
                    onReleased: { holdTimer.stop(); held = 0 }
                    Timer { id: holdTimer; interval: 900; onTriggered: { appState.resetPreferences(); parent.held = 1 } }
                    background: Rectangle { radius: 12; color: "#0c0d0c"; border.width: parent.activeFocus ? 2 : 1; border.color: "#9b2c2c" }
                    contentItem: Item {
                        Column { anchors.left: parent.left; anchors.leftMargin: 22; anchors.verticalCenter: parent.verticalCenter; spacing: 4; Text { text: "Reset all settings"; color: "#ff4f4f"; font.pixelSize: 15; font.weight: Font.DemiBold } Text { text: "Restores defaults — your account stays linked"; color: Theme.inkMuted; font.pixelSize: 10 } }
                        TinyCaps { anchors.right: parent.right; anchors.rightMargin: 22; anchors.verticalCenter: parent.verticalCenter; text: "HOLD A"; color: "#ff4f4f" }
                    }
                }
                Item { Layout.fillHeight: true }
            }
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 220
                    Column { anchors.fill: parent; anchors.margins: 22; spacing: 3
                        Text { text: "Identity & experiments"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold; bottomPadding: 8 }
                        PersistToggle { width: parent.width; title: "Identify as Steam Deck"; description: "Advertise the official Deck client via nv-device headers"; preferenceKey: "advanced.identifySteamDeck"; defaultValue: true; compact: true }
                        PersistToggle { width: parent.width; title: "Persist in-game settings"; description: "NVIDIA per-game graphics persistence between sessions"; preferenceKey: "advanced.persistGameSettings"; defaultValue: false; compact: true }
                        PersistToggle { width: parent.width; title: "Anti-AFK reminder"; description: "Every 15 min · shown for 5 s · Ctrl+Shift+K"; preferenceKey: "advanced.antiAfkReminder"; defaultValue: true; compact: true }
                    }
                }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 168
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Updates"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "v3.0.0-beta · up to date" }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 84; text: "Channel"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 150; anchors.rightMargin: 22; anchors.topMargin: 62; options: ["Stable", "Beta"]; preferenceKey: "updates.channel"; defaultValue: "Beta" }
                    PersistToggle { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.bottomMargin: 6; title: "Check automatically"; description: "Watches GitHub Releases in the background"; preferenceKey: "updates.autoCheck"; defaultValue: true; compact: true }
                }
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 168
                    Column { anchors.fill: parent; anchors.margins: 22; spacing: 3
                        Text { text: "Privacy"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold; bottomPadding: 8 }
                        PersistToggle { width: parent.width; title: "Anonymous error reporting"; description: "Crash traces only — never gameplay or account data"; preferenceKey: "privacy.errorReporting"; defaultValue: false; compact: true }
                        PersistToggle { width: parent.width; title: "Session counter"; description: "Track sessions locally for the Stats page"; preferenceKey: "privacy.sessionCounter"; defaultValue: true; compact: true }
                    }
                }
                Item { Layout.fillHeight: true }
            }
        }
    }

    Rectangle {
        id: settingsNav
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        width: 400
        color: "#080c09"
        border.color: "#1b241e"

        Text {
            anchors.left: parent.left
            anchors.top: parent.top
            anchors.leftMargin: 26
            anchors.topMargin: 38
            text: "Settings"
            color: Theme.ink
            font.family: Theme.displayFont.family
            font.pixelSize: 29
            font.weight: Font.DemiBold
        }

        Column {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.leftMargin: 26
            anchors.rightMargin: 22
            anchors.topMargin: 102
            spacing: 8

            Repeater {
                id: sectionRepeater
                model: page.sectionNames
                AbstractButton {
                    width: parent.width
                    height: 50
                    activeFocusOnTab: true
                    hoverEnabled: true
                    onClicked: page.selectSection(index)
                    Keys.onRightPressed: function(event) {
                        var currentSection = sectionStack.children[page.sectionIndex]
                        var first = currentSection ? currentSection.firstControl : null
                        if (first) first.forceActiveFocus()
                        event.accepted = true
                    }
                    background: Rectangle {
                        radius: 10
                        color: page.sectionIndex === index ? "#111914" : parent.hovered ? "#0e1510" : "transparent"
                        border.width: parent.activeFocus || page.sectionIndex === index ? 1 : 0
                        border.color: page.sectionIndex === index ? Theme.accent : "#39473e"
                    }
                    contentItem: Item {
                        Text {
                            anchors.left: parent.left
                            anchors.leftMargin: 18
                            anchors.verticalCenter: parent.verticalCenter
                            text: modelData
                            color: page.sectionIndex === index ? Theme.ink : Theme.inkMuted
                            font.pixelSize: 14
                            font.weight: Font.DemiBold
                        }
                        Rectangle {
                            visible: page.sectionIndex === index
                            anchors.right: parent.right
                            anchors.rightMargin: 18
                            anchors.verticalCenter: parent.verticalCenter
                            width: 7; height: 7; radius: 4; color: Theme.accent
                        }
                    }
                }
            }
        }
    }

    StackLayout {
        id: sectionStack
        anchors.left: settingsNav.right
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.bottom: footer.top
        anchors.leftMargin: 52
        anchors.rightMargin: 56
        anchors.topMargin: 31
        currentIndex: page.sectionIndex

        AccountSection {}
        StreamingSection {}
        VideoSection {}
        InputSection {}
        NetworkSection {}
        AdvancedSection {}
    }

    Item {
        id: footer
        anchors.left: settingsNav.right
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.leftMargin: 52
        anchors.rightMargin: 56
        height: 70
        Rectangle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; height: 1; color: "#1c251f" }
        TinyCaps { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; text: "changes apply live  ·  no stream restart" }
        Row {
            anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; spacing: 22
            Text { text: "Ⓐ  Change"; color: Theme.inkMuted; font.pixelSize: 11 }
            Text { text: "Ⓑ  Back"; color: Theme.inkMuted; font.pixelSize: 11 }
            Text { text: "LB  RB   Section"; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 10 }
        }
    }
}
