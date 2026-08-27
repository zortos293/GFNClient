import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page

    property bool reducedMotion: false
    property real sectionReveal: 1
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
        sectionRevealAnimation.restart()
        Qt.callLater(function() {
            var currentSection = sectionStack.children[page.sectionIndex]
            var first = currentSection ? currentSection.firstControl : null
            if (first)
                first.forceActiveFocus()
        })
    }

    NumberAnimation {
        id: sectionRevealAnimation
        target: page
        property: "sectionReveal"
        from: 0
        to: 1
        duration: page.reducedMotion ? 0 : Theme.motion
        easing.type: Easing.OutCubic
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
        radius: 16
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

    component SearchGlyph: Item {
        width: 18
        height: 18
        property color strokeColor: Theme.inkMuted

        Rectangle {
            width: 10
            height: 10
            radius: 5
            color: "transparent"
            border.width: 2
            border.color: parent.strokeColor
            anchors.left: parent.left
            anchors.top: parent.top
        }
        Rectangle {
            width: 7
            height: 2
            radius: 1
            color: parent.strokeColor
            rotation: 45
            transformOrigin: Item.Left
            x: 10
            y: 11
        }
    }

    component SignalBars: Item {
        id: signalBars
        property int latency: -1
        readonly property int activeBars: latency < 0 ? 0
                                                  : latency < 35 ? 4
                                                  : latency < 60 ? 3
                                                  : latency < 100 ? 2 : 1
        readonly property color signalColor: latency < 0 ? Theme.inkMuted
                                                         : latency < 60 ? Theme.accent
                                                         : latency < 150 ? Theme.warning : Theme.error
        width: 22
        height: 16

        Row {
            anchors.bottom: parent.bottom
            spacing: 2
            Repeater {
                model: [6, 10, 13, 16]
                Rectangle {
                    required property int index
                    required property int modelData
                    width: 4
                    height: modelData
                    radius: 1
                    color: index < signalBars.activeBars ? signalBars.signalColor : "#2a342c"
                    anchors.bottom: parent.bottom

                    Behavior on color { ColorAnimation { duration: page.reducedMotion ? 0 : Theme.motionFast } }
                    Behavior on height { NumberAnimation { duration: page.reducedMotion ? 0 : Theme.motionFast; easing.type: Easing.OutCubic } }
                }
            }
        }
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
            anchors.right: statusLabel.left
            anchors.rightMargin: 24
            anchors.top: parent.top
            spacing: 5
            Text {
                width: parent.width
                text: headerControl.title
                color: Theme.ink
                font.family: Theme.displayFont.family
                font.pixelSize: 29
                font.weight: Font.DemiBold
            }
            Text {
                width: parent.width
                text: headerControl.description
                color: Theme.inkMuted
                font.pixelSize: 13
                elide: Text.ElideRight
            }
        }
        TinyCaps {
            id: statusLabel
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

    component PersistDropdown: FocusScope {
        id: dropdownControl
        property var options: []
        property var optionLabels: ({})
        property var optionDetails: ({})
        property var disabledOptions: []
        property string preferenceKey: ""
        property string defaultValue: ""
        property string badgeText: ""
        property string storedValue: String(appState.preference(preferenceKey, defaultValue))
        signal changed(string value)
        implicitHeight: 64

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
                if (key === dropdownControl.preferenceKey)
                    dropdownControl.storedValue = String(value)
            }
            function onPreferencesReset() {
                dropdownControl.storedValue = dropdownControl.defaultValue
            }
        }

        Component.onCompleted: {
            if (options.indexOf(storedValue) < 0) {
                storedValue = defaultValue
                appState.setPreference(preferenceKey, defaultValue)
            }
        }

        Button {
            id: dropdownButton
            anchors.fill: parent
            focusPolicy: Qt.StrongFocus
            focus: true
            onClicked: dropdownPopup.open()

            contentItem: RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 14
                anchors.rightMargin: 16
                spacing: 13
                Rectangle {
                    width: 38
                    height: 38
                    radius: 9
                    color: Theme.accent
                    Text {
                        anchors.centerIn: parent
                        text: dropdownControl.badgeText
                        color: Theme.accentInk
                        font.family: Theme.monoFont.family
                        font.pixelSize: 11
                        font.weight: Font.Bold
                    }
                }
                Column {
                    Layout.fillWidth: true
                    spacing: 3
                    Text {
                        width: parent.width
                        text: String(dropdownControl.optionLabels[dropdownControl.storedValue]
                                     || dropdownControl.storedValue)
                        color: Theme.ink
                        font.family: Theme.bodyFont.family
                        font.pixelSize: 15
                        font.weight: Font.DemiBold
                        elide: Text.ElideRight
                    }
                    Text {
                        width: parent.width
                        text: String(dropdownControl.optionDetails[dropdownControl.storedValue] || "")
                        color: Theme.inkMuted
                        font.family: Theme.monoFont.family
                        font.pixelSize: 10
                        elide: Text.ElideRight
                    }
                }
                Text {
                    text: "Change  ⌄"
                    color: Theme.inkMuted
                    font.family: Theme.monoFont.family
                    font.pixelSize: 10
                }
            }
            background: Rectangle {
                radius: 12
                color: "#0e120f"
                border.width: dropdownButton.activeFocus || dropdownPopup.opened ? 2 : 1
                border.color: dropdownButton.activeFocus || dropdownPopup.opened ? Theme.accent : "#233028"
            }

            Popup {
                id: dropdownPopup
                parent: dropdownButton
                z: 1000
                scale: page.Window.window ? page.Window.window.presentationScale : 1
                transformOrigin: Item.TopLeft
                x: 0
                y: dropdownButton.height + 8
                width: dropdownButton.width
                height: Math.min(320, dropdownControl.options.length * 48 + 12)
                focus: true
                modal: true
                padding: 6
                closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
                background: Rectangle { radius: 12; color: "#0e120f"; border.width: 1; border.color: "#233028" }
                contentItem: ListView {
                    id: dropdownList
                    clip: true
                    focus: true
                    model: dropdownControl.options
                    currentIndex: Math.max(0, dropdownControl.options.indexOf(dropdownControl.storedValue))
                    Keys.onUpPressed: currentIndex = (currentIndex + count - 1) % count
                    Keys.onDownPressed: currentIndex = (currentIndex + 1) % count
                    Keys.onReturnPressed: {
                        var value = String(dropdownControl.options[currentIndex])
                        if (dropdownControl.disabledOptions.indexOf(value) === -1) {
                            dropdownControl.choose(value)
                            dropdownPopup.close()
                            dropdownButton.forceActiveFocus()
                        }
                    }
                    delegate: ItemDelegate {
                        id: dropdownDelegate
                        required property var modelData
                        required property int index
                        width: ListView.view.width
                        height: 48
                        text: String(dropdownControl.optionLabels[String(modelData)] || modelData)
                        enabled: dropdownControl.disabledOptions.indexOf(String(modelData)) === -1
                        highlighted: String(modelData) === dropdownControl.storedValue
                        font.family: Theme.bodyFont.family
                        font.pixelSize: 13
                        onClicked: {
                            dropdownControl.choose(String(modelData))
                            dropdownPopup.close()
                            dropdownButton.forceActiveFocus()
                        }
                        contentItem: Text {
                            text: dropdownDelegate.text
                            color: dropdownDelegate.highlighted ? Theme.accent : Theme.ink
                            opacity: dropdownDelegate.enabled ? 1 : 0.36
                            font: dropdownDelegate.font
                            verticalAlignment: Text.AlignVCenter
                            leftPadding: 12
                        }
                        background: Rectangle { radius: 8; color: dropdownDelegate.highlighted ? "#15241a" : (dropdownDelegate.hovered ? Theme.surfaceBright : "transparent") }
                    }
                }
                onOpened: dropdownList.forceActiveFocus()
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
        property bool warning: false
        implicitWidth: Math.max(82, label.implicitWidth + 28)
        implicitHeight: 38
        activeFocusOnTab: true
        hoverEnabled: true
        background: Rectangle {
            radius: 9
            color: smallButton.primary ? Theme.accent : smallButton.warning ? "#2a2109" : smallButton.hovered ? "#182019" : "#0b100d"
            border.width: smallButton.activeFocus ? 2 : 1
            border.color: smallButton.danger ? "#8b2727" : smallButton.warning ? "#806215" : smallButton.activeFocus ? Theme.accent : "#334038"
        }
        contentItem: Text {
            id: label
            text: smallButton.text
            color: smallButton.primary ? "#07130a" : smallButton.danger ? "#ff6464" : smallButton.warning ? Theme.warning : Theme.ink
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
        readonly property var membership: catalogEngine.subscription || ({})
        readonly property var storage: membership.storageAddon || ({})

        function compactNumber(value) {
            var number = Number(value || 0)
            return Math.abs(number - Math.round(number)) < 0.05
                    ? String(Math.round(number)) : number.toFixed(1)
        }

        function rigClassText() {
            var resolutions = membership.entitledResolutions || []
            if (resolutions.length === 0)
                return membership.membershipTier ? membership.membershipTier + " performance rig" : "—"
            var highest = resolutions[0]
            return Number(highest.width) + "×" + Number(highest.height) + " · up to " + Number(highest.fps) + " FPS"
        }

        function sessionLengthText() {
            if (membership.isUnlimited)
                return "Unlimited · queue managed by GFN"
            var remaining = Number(membership.remainingHours || 0)
            return remaining > 0 ? compactNumber(remaining) + " h remaining" : (membership.state || "—")
        }

        function renewalText() {
            var raw = String(membership.currentSpanEndDateTime || "")
            if (!raw.length)
                return membership.state || "—"
            var date = new Date(raw)
            return isNaN(date.getTime()) ? raw : Qt.formatDate(date, "d MMM yyyy")
        }

        function storeSyncText(account) {
            var raw = String(account.syncDate || "")
            if (!raw.length)
                return ""
            var date = new Date(raw)
            if (isNaN(date.getTime()))
                return ""
            var minutes = Math.max(0, Math.floor((Date.now() - date.getTime()) / 60000))
            if (minutes < 60) return " · " + Math.max(1, minutes) + " min ago"
            if (minutes < 1440) return " · " + Math.floor(minutes / 60) + " h ago"
            return " · " + Math.floor(minutes / 1440) + " d ago"
        }

        function storeModel() {
            return (catalogEngine.connectedAccounts || []).map(function(account) {
                var detail = String(account.status || "unknown").replace(/_/g, " ").toUpperCase()
                if (account.displayName)
                    detail += " · " + account.displayName
                if (Number(account.syncedGames || 0) > 0)
                    detail += " · " + account.syncedGames + " games"
                detail += accountSection.storeSyncText(account)
                return {
                    account: account,
                    provider: account.provider || account.label || "",
                    name: account.label || account.provider,
                    state: detail,
                    tone: account.status === "connected" ? "ok" : (account.status === "not_connected" ? "off" : "warn"),
                    connected: account.isConnected,
                    iconUrl: account.iconUrl || "",
                    primaryAction: account.status === "not_connected" ? "Connect" : (account.status === "expired" ? "Re-link" : (account.status === "sync_error" ? "Retry sync" : (account.supportsSync ? "Sync now" : "Re-link"))),
                    secondaryAction: account.isConnected ? "Disconnect" : ""
                }
            })
        }

        function manageStore(action) {
            if (action === "Sync now" || action === "Retry sync")
                catalogEngine.refreshConnectedAccounts()
            else
                Qt.openUrlExternally("https://play.geforcenow.com/mall/#/account")
        }

        function manageInstalledGames() {
            Qt.openUrlExternally("https://play.geforcenow.com/")
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
            spacing: 24

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
                        DitherAvatar { anchors.fill: parent; anchors.margins: 7; name: authEngine.accountName.length > 0 ? authEngine.accountName : "NVIDIA account" }
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
                    Layout.preferredHeight: 198
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 26; anchors.topMargin: 24; text: "Membership"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 21; font.weight: Font.DemiBold }
                    Rectangle {
                        anchors.right: parent.right; anchors.top: parent.top; anchors.rightMargin: 26; anchors.topMargin: 23
                        width: membershipBadge.implicitWidth + 24; height: 28; radius: 8
                        color: Qt.rgba(76 / 255, 232 / 255, 127 / 255, 0.14)
                        Text { id: membershipBadge; anchors.centerIn: parent; text: accountSection.membership.membershipTier || "—"; color: Theme.accent; font.family: Theme.monoFont.family; font.pixelSize: 12; font.weight: Font.DemiBold; font.letterSpacing: 1.4 }
                    }
                    Column {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top
                        anchors.leftMargin: 26; anchors.rightMargin: 26; anchors.topMargin: 72
                        spacing: 16
                        Repeater {
                            model: [
                                { label: "Rig class", value: accountSection.rigClassText() },
                                { label: accountSection.membership.isUnlimited ? "Session length" : "Play time left", value: accountSection.sessionLengthText() },
                                { label: "Renews", value: accountSection.renewalText() }
                            ]
                            Item {
                                width: parent.width; height: 18
                                Text { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; text: modelData.label; color: "#7c877e"; font.family: Theme.bodyFont.family; font.pixelSize: 15 }
                                Text { anchors.left: parent.left; anchors.leftMargin: 150; anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; horizontalAlignment: Text.AlignRight; text: modelData.value; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 15; font.weight: Font.Medium; elide: Text.ElideRight }
                            }
                        }
                    }
                }
                SurfaceCard {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 210
                    Column {
                        anchors.left: parent.left; anchors.right: storageMetrics.left; anchors.top: parent.top
                        anchors.leftMargin: 26; anchors.rightMargin: 18; anchors.topMargin: 24; spacing: 3
                        Text { text: "Cloud storage"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 21; font.weight: Font.DemiBold }
                        Text { width: parent.width; text: accountSection.membership.storageAddon ? "Install-to-Play — games stay installed between sessions" : "Permanent game storage is not included with this membership"; color: "#7c877e"; font.family: Theme.bodyFont.family; font.pixelSize: 13; elide: Text.ElideRight }
                    }
                    Column {
                        id: storageMetrics
                        anchors.right: parent.right; anchors.top: parent.top; anchors.rightMargin: 26; anchors.topMargin: 25; spacing: 2
                        Text { anchors.right: parent.right; text: accountSection.membership.storageAddon ? accountSection.compactNumber(accountSection.storage.usedGb) + " GB used" : "Not included"; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 16; font.weight: Font.DemiBold }
                        Text { anchors.right: parent.right; text: accountSection.membership.storageAddon ? accountSection.compactNumber(Math.max(0, Number(accountSection.storage.sizeGb || 0) - Number(accountSection.storage.usedGb || 0))) + " GB free of " + accountSection.compactNumber(accountSection.storage.sizeGb) + " GB" : "0 GB allocated"; color: "#7c877e"; font.family: Theme.monoFont.family; font.pixelSize: 12 }
                    }
                    Rectangle {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top
                        anchors.leftMargin: 26; anchors.rightMargin: 26; anchors.topMargin: 88
                        height: 10; radius: 5; color: Theme.divider; clip: true
                        Rectangle {
                            width: parent.width * Math.max(0, Math.min(1, Number(accountSection.storage.usedGb || 0) / Math.max(1, Number(accountSection.storage.sizeGb || 0))))
                            height: parent.height; radius: 5; color: Theme.accent
                            Behavior on width { NumberAnimation { duration: page.reducedMotion ? 0 : Theme.motion; easing.type: Easing.OutCubic } }
                        }
                    }
                    Text {
                        anchors.left: parent.left; anchors.right: manageStorage.left; anchors.bottom: parent.bottom
                        anchors.leftMargin: 26; anchors.rightMargin: 16; anchors.bottomMargin: 34
                        text: accountSection.membership.storageAddon ? "Per-game usage lives in Steam — GFN only reports the total." : "Add permanent storage from your GeForce NOW membership settings."
                        color: "#57615a"; font.family: Theme.bodyFont.family; font.pixelSize: 13; elide: Text.ElideRight
                    }
                    SmallButton {
                        id: manageStorage
                        anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.rightMargin: 26; anchors.bottomMargin: 24
                        height: 42; text: accountSection.membership.storageAddon ? "Manage installed games" : "View storage options"
                        onClicked: accountSection.manageInstalledGames()
                    }
                }
                Item { Layout.fillHeight: true }
            }

            ColumnLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 464
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 26; anchors.topMargin: 24; text: "Connected stores"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 21; font.weight: Font.DemiBold }
                    Row {
                        anchors.right: parent.right; anchors.top: parent.top; anchors.rightMargin: 26; anchors.topMargin: 21; spacing: 14
                        AbstractButton {
                            width: 74; height: 30; activeFocusOnTab: true; enabled: !catalogEngine.loading
                            onClicked: catalogEngine.refreshConnectedAccounts()
                            contentItem: Row { spacing: 7
                                Rectangle { width: 26; height: 26; radius: 13; color: "transparent"; border.width: 1; border.color: parent.parent.activeFocus ? Theme.accent : "#3a453d"; Text { anchors.centerIn: parent; text: "X"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 12; font.weight: Font.Bold } }
                                Text { anchors.verticalCenter: parent.verticalCenter; text: catalogEngine.loading ? "Syncing" : "Sync"; color: "#7c877e"; font.family: Theme.bodyFont.family; font.pixelSize: 13; font.weight: Font.Medium }
                            }
                            background: Rectangle { color: "transparent" }
                        }
                        AbstractButton {
                            width: 112; height: 30; activeFocusOnTab: true
                            onClicked: accountSection.manageStore("Disconnect")
                            contentItem: Row { spacing: 7
                                Rectangle { width: 26; height: 26; radius: 13; color: "transparent"; border.width: 1; border.color: parent.parent.activeFocus ? Theme.accent : "#3a453d"; Text { anchors.centerIn: parent; text: "Y"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 12; font.weight: Font.Bold } }
                                Text { anchors.verticalCenter: parent.verticalCenter; text: "Disconnect"; color: "#7c877e"; font.family: Theme.bodyFont.family; font.pixelSize: 13; font.weight: Font.Medium }
                            }
                            background: Rectangle { color: "transparent" }
                        }
                    }
                    Flickable {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.bottom: parent.bottom
                        anchors.leftMargin: 26; anchors.rightMargin: 26; anchors.topMargin: 70; anchors.bottomMargin: 56
                        clip: true
                        contentHeight: storeRows.height
                        boundsBehavior: Flickable.StopAtBounds
                        Column {
                            id: storeRows
                            width: parent.width
                            height: childrenRect.height
                            spacing: 12
                        Repeater {
                            model: accountSection.storeModel()
                            Rectangle {
                                width: parent.width; height: 72; radius: 12
                                color: modelData.tone === "ok" ? "#101512" : "#070a08"
                                border.width: modelData.tone === "ok" ? 2 : 1
                                border.color: modelData.tone === "ok" ? Theme.accent : modelData.tone === "warn" ? "#2e2712" : Theme.divider
                                StoreBrandIcon { id: storeIcon; anchors.left: parent.left; anchors.leftMargin: 16; anchors.verticalCenter: parent.verticalCenter; width: 30; height: 30; provider: modelData.provider; source: modelData.iconUrl }
                                Text { anchors.left: storeIcon.right; anchors.right: storeActions.left; anchors.top: parent.top; anchors.leftMargin: 14; anchors.rightMargin: 14; anchors.topMargin: 13; text: modelData.name; color: modelData.tone === "off" ? "#7c877e" : Theme.ink; elide: Text.ElideRight; font.family: Theme.bodyFont.family; font.pixelSize: 16; font.weight: Font.DemiBold }
                                Text { anchors.left: storeIcon.right; anchors.right: storeActions.left; anchors.bottom: parent.bottom; anchors.leftMargin: 14; anchors.rightMargin: 14; anchors.bottomMargin: 12; text: modelData.state; elide: Text.ElideRight; color: modelData.tone === "ok" ? Theme.accent : modelData.tone === "warn" ? Theme.warning : "#57615a"; font.family: Theme.monoFont.family; font.pixelSize: 12 }
                                Row {
                                    id: storeActions
                                    anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; anchors.rightMargin: 16; spacing: 8
                                    SmallButton { height: 40; text: modelData.primaryAction; primary: modelData.primaryAction === "Re-link"; warning: modelData.primaryAction === "Retry sync"; onClicked: accountSection.manageStore(modelData.primaryAction) }
                                    SmallButton { height: 40; visible: modelData.secondaryAction.length > 0; text: modelData.secondaryAction; danger: true; onClicked: accountSection.manageStore(modelData.secondaryAction) }
                                }
                            }
                        }
                        Text { visible: accountSection.storeModel().length === 0; width: parent.width; text: "No supported linked-store data was returned."; color: Theme.inkMuted; wrapMode: Text.WordWrap; font.pixelSize: 11 }
                        }
                    }
                    Text { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 26; anchors.rightMargin: 26; anchors.bottomMargin: 15; height: 34; text: "Disconnecting removes the link on GeForce NOW — synced games leave your library on the next refresh."; color: "#57615a"; wrapMode: Text.WordWrap; font.family: Theme.bodyFont.family; font.pixelSize: 13 }
                }
                SurfaceCard {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 220
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 26; anchors.topMargin: 24; text: "Console mode"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 21; font.weight: Font.DemiBold }
                    Column {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top
                        anchors.leftMargin: 26; anchors.rightMargin: 26; anchors.topMargin: 59; spacing: 1
                        PersistToggle { width: parent.width; title: "Launch games gamepad-friendly"; description: "Steam starts in Big Picture inside the session"; preferenceKey: "account.console.gamepadFriendly"; defaultValue: true; compact: true }
                        PersistToggle { width: parent.width; title: "‘Who's playing?’ on launch"; description: "Show the profile picker when console mode starts"; preferenceKey: "account.console.profilePicker"; defaultValue: true; compact: true }
                        PersistToggle { width: parent.width; title: "Discord Rich Presence"; description: "Show the streaming game as your Discord activity"; preferenceKey: "account.discordRichPresence"; defaultValue: false; compact: true }
                    }
                }
                Item { Layout.fillHeight: true }
            }
        }
    }

    component StreamingSection: Item {
        id: streamingSection
        property Item firstControl: aspectDropdown
        property string resolution: String(appState.preference("streaming.resolution", "1440p (QHD)"))
        SectionHeader { id: header; width: parent.width; title: "Streaming"; description: "Quality, latency and the pipeline between the rig and this screen."; status: "PROFILE: BALANCED" }
        RowLayout {
            anchors.left: parent.left; anchors.right: parent.right; anchors.top: header.bottom; anchors.bottom: parent.bottom; spacing: 20
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
                SurfaceCard {
                    Layout.fillWidth: true; Layout.preferredHeight: 342
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.margins: 22; text: "Target format"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.DemiBold }
                    TinyCaps { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 22; text: "FROM RIG ENTITLEMENT" }
                    PersistDropdown {
                        id: aspectDropdown
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top
                        anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 56
                        badgeText: "AR"
                        options: ["16:9 Standard", "16:10 Widescreen", "21:9 Ultrawide", "32:9 Super ultrawide", "4:3 Legacy", "Other"]
                        disabledOptions: options.filter(function(value) {
                            var tiers = ["1080p (FHD)", "1440p (QHD)", "4K (UHD)"]
                            for (var i = 0; i < tiers.length; ++i) {
                                if (StreamFormat.supportsTarget(catalogEngine.subscription.entitledResolutions || [], tiers[i], value))
                                    return false
                            }
                            return true
                        })
                        optionDetails: ({
                            "16:9 Standard": "Most televisions and gaming displays",
                            "16:10 Widescreen": "Taller laptop and handheld displays",
                            "21:9 Ultrawide": "Ultrawide desktop displays",
                            "32:9 Super ultrawide": "Dual-wide desktop displays",
                            "4:3 Legacy": "Classic display format",
                            "Other": "Use the closest format offered by the rig"
                        })
                        preferenceKey: "streaming.aspect"
                        defaultValue: "16:9 Standard"
                    }
                    Text { anchors.left: parent.left; anchors.top: parent.top; anchors.leftMargin: 22; anchors.topMargin: 136; text: "Resolution"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistDropdown {
                        id: resolutionDropdown
                        anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top
                        anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.topMargin: 157
                        badgeText: "PX"
                        options: ["1080p (FHD)", "1440p (QHD)", "4K (UHD)"]
                        optionLabels: ({
                            "1080p (FHD)": StreamFormat.label("1080p (FHD)", aspectDropdown.storedValue, catalogEngine.subscription.entitledResolutions || [], Number(appState.preference("streaming.frameRate", "120"))),
                            "1440p (QHD)": StreamFormat.label("1440p (QHD)", aspectDropdown.storedValue, catalogEngine.subscription.entitledResolutions || [], Number(appState.preference("streaming.frameRate", "120"))),
                            "4K (UHD)": StreamFormat.label("4K (UHD)", aspectDropdown.storedValue, catalogEngine.subscription.entitledResolutions || [], Number(appState.preference("streaming.frameRate", "120")))
                        })
                        optionDetails: ({
                            "1080p (FHD)": "Exact stream size for " + aspectDropdown.storedValue,
                            "1440p (QHD)": "Exact stream size for " + aspectDropdown.storedValue,
                            "4K (UHD)": "Exact stream size for " + aspectDropdown.storedValue
                        })
                        disabledOptions: options.filter(function(value) {
                            return !StreamFormat.supportsTarget(catalogEngine.subscription.entitledResolutions || [], value, aspectDropdown.storedValue)
                        })
                        preferenceKey: "streaming.resolution"
                        defaultValue: "1440p (QHD)"
                        onChanged: function(value) { streamingSection.resolution = value }
                    }
                    Text { anchors.left: parent.left; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.bottomMargin: 37; text: "Frame rate"; color: Theme.inkMuted; font.pixelSize: 12 }
                    PersistSegments { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 116; anchors.rightMargin: 22; anchors.bottomMargin: 27; options: ["30", "60", "120", "240"]; disabledOptions: ["30", "60", "120", "240"].filter(function(value) { return StreamFormat.availableFps(catalogEngine.subscription.entitledResolutions || [], resolutionDropdown.storedValue, aspectDropdown.storedValue).indexOf(String(value)) === -1 }); preferenceKey: "streaming.frameRate"; defaultValue: "120" }
                    Text { anchors.left: parent.left; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.bottomMargin: 8; text: "Only exact frame rates returned by your membership entitlement are selectable."; color: Theme.inkMuted; font.pixelSize: 9 }
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
                                Text {
                                    anchors.left: parent.left
                                    anchors.leftMargin: 44
                                    anchors.right: controllerStatus.left
                                    anchors.rightMargin: 12
                                    anchors.verticalCenter: parent.verticalCenter
                                    text: modelData.n
                                    color: Theme.ink
                                    elide: Text.ElideRight
                                    font.pixelSize: 12
                                    font.weight: Font.DemiBold
                                }
                                Row {
                                    id: controllerStatus
                                    anchors.right: parent.right
                                    anchors.rightMargin: 12
                                    anchors.verticalCenter: parent.verticalCenter
                                    spacing: 10

                                    TinyCaps {
                                        anchors.verticalCenter: parent.verticalCenter
                                        text: modelData.s
                                        color: modelData.active ? Theme.accent : Theme.inkMuted
                                    }
                                    BatteryIndicator {
                                        anchors.verticalCenter: parent.verticalCenter
                                        compact: true
                                        percent: controllerInput.batteryPercent
                                        status: controllerInput.batteryStatus
                                        charging: controllerInput.batteryCharging
                                    }
                                }
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
        property Item firstControl: serverFilter
        property string filterText: ""
        property string selectedServer: String(appState.preference("network.serverName", "Automatic"))
        property string regionMode: String(appState.preference("network.regionMode", "automatic"))
        property bool communityProxy: false
        readonly property var filteredRegions: (catalogEngine.regions || []).filter(function(region) {
            var needle = networkSection.filterText.trim().toLowerCase()
            return needle.length === 0 || String(region.name || "").toLowerCase().indexOf(needle) >= 0
        })
        readonly property var regionGroups: {
            var groups = {}
            var pings = catalogEngine.regionPings
            var order = ["EUROPE", "NORTH AMERICA", "SOUTH AMERICA", "ASIA", "OCEANIA", "MIDDLE EAST & AFRICA", "OTHER"]
            for (var i = 0; i < order.length; ++i)
                groups[order[i]] = []
            for (var j = 0; j < networkSection.filteredRegions.length; ++j) {
                var region = networkSection.filteredRegions[j]
                groups[networkSection.categoryForRegion(region)].push(region)
            }
            function sortableLatency(region) {
                var raw = pings[String(region.url)]
                if (raw === undefined)
                    return Number.MAX_VALUE
                var latency = Number(raw)
                return isFinite(latency) && latency >= 0 ? latency : Number.MAX_VALUE
            }
            var result = []
            for (var k = 0; k < order.length; ++k) {
                if (groups[order[k]].length > 0) {
                    groups[order[k]].sort(function(left, right) {
                        var latencyDifference = sortableLatency(left) - sortableLatency(right)
                        return latencyDifference !== 0
                                ? latencyDifference
                                : String(left.name || "").localeCompare(String(right.name || ""))
                    })
                    result.push({ name: order[k], items: groups[order[k]] })
                }
            }
            return result
        }
        readonly property var bestRegion: {
            var best = null
            var bestPing = Number.MAX_VALUE
            var values = catalogEngine.regions || []
            for (var i = 0; i < values.length; ++i) {
                var measured = Number(catalogEngine.regionPings[String(values[i].url)])
                if (isFinite(measured) && measured >= 0 && measured < bestPing) {
                    bestPing = measured
                    best = values[i]
                }
            }
            return best
        }
        readonly property string testTarget: String(appState.preference("network.regionMode", "automatic")) === "manual"
                                             ? String(appState.preference("network.serverUrl", ""))
                                             : (networkSection.bestRegion ? String(networkSection.bestRegion.url || "")
                                                                          : (catalogEngine.regions.length > 0 ? String(catalogEngine.regions[0].url || "") : ""))
        function categoryForRegion(region) {
            var name = String(region.name || "").toLowerCase()
            var northAmerica = ["usa", "united states", "canada", "mexico", "arizona", "california", "florida", "georgia", "illinois", "indiana", "newark", "new york", "oregon", "texas", "virginia", "washington", "ashburn", "atlanta", "chicago", "dallas", "miami", "montreal", "phoenix", "san jose", "seattle"]
            var southAmerica = ["argentina", "brazil", "chile", "colombia", "peru", "uruguay", "sao paulo", "são paulo"]
            var oceania = ["australia", "new zealand", "sydney", "melbourne"]
            var asia = ["china", "hong kong", "india", "indonesia", "japan", "korea", "malaysia", "philippines", "singapore", "taiwan", "thailand", "tokyo"]
            var middleEastAfrica = ["bahrain", "egypt", "israel", "saudi", "south africa", "turkey", "uae", "united arab", "dubai"]
            function includesAny(words) {
                for (var i = 0; i < words.length; ++i) {
                    if (name.indexOf(words[i]) >= 0)
                        return true
                }
                return false
            }
            if (includesAny(northAmerica)) return "NORTH AMERICA"
            if (includesAny(southAmerica)) return "SOUTH AMERICA"
            if (includesAny(oceania)) return "OCEANIA"
            if (includesAny(asia)) return "ASIA"
            if (includesAny(middleEastAfrica)) return "MIDDLE EAST & AFRICA"
            return "EUROPE"
        }
        function pingValue(url) {
            var value = catalogEngine.regionPings[String(url)]
            return value === undefined ? -1 : Number(value)
        }
        function pingText(url) {
            var value = catalogEngine.regionPings[String(url)]
            if (catalogEngine.probingRegions && value === undefined)
                return "testing…"
            if (value === undefined)
                return "not tested"
            return Number(value) >= 0 ? Number(value) + " ms" : "unreachable"
        }
        function selectRegion(region) {
            networkSection.selectedServer = String(region.name)
            networkSection.regionMode = "manual"
            appState.setPreference("network.regionMode", "manual")
            appState.setPreference("network.serverName", region.name)
            appState.setPreference("network.serverUrl", region.url)
            appState.selectServer(region.name, region.name,
                                  Math.max(0, Number(catalogEngine.regionPings[String(region.url)] || 0)))
        }
        function selectAutomatic() {
            networkSection.selectedServer = "Automatic"
            networkSection.regionMode = "automatic"
            appState.setPreference("network.regionMode", "automatic")
            appState.setPreference("network.serverName", "Automatic")
            appState.setPreference("network.serverUrl", "")
            appState.selectServer("Automatic", "AUTO",
                                  networkSection.bestRegion
                                  ? Math.max(0, networkSection.pingValue(networkSection.bestRegion.url)) : 0)
        }
        function isSelectedRegion(region) {
            if (networkSection.regionMode === "manual")
                return networkSection.selectedServer === String(region.name)
            return networkSection.bestRegion && String(networkSection.bestRegion.url) === String(region.url)
        }
        onVisibleChanged: {
            if (visible && catalogEngine.regions.length > 0 && Object.keys(catalogEngine.regionPings).length === 0)
                catalogEngine.probeRegions()
        }
        Connections {
            target: catalogEngine
            function onServerInfoChanged() {
                if (networkSection.visible && catalogEngine.regions.length > 0)
                    catalogEngine.probeRegions()
            }
        }
        SectionHeader { id: header; width: parent.width; title: "Network"; description: "Region, routing, transport and connection resilience."; status: "REGION: " + (networkSection.regionMode === "automatic" ? "AUTO" + (networkSection.bestRegion ? " · " + String(networkSection.bestRegion.name).toUpperCase() : "") : networkSection.selectedServer.toUpperCase()) }
        Shortcut { sequence: "Y"; enabled: page.visible && page.sectionIndex === 4; onActivated: serverFilter.forceActiveFocus() }
        RowLayout {
            anchors.left: parent.left; anchors.right: parent.right; anchors.top: header.bottom; anchors.bottom: parent.bottom; spacing: 24
            SurfaceCard {
                Layout.fillWidth: true; Layout.fillHeight: true
                Row {
                    anchors.left: parent.left
                    anchors.top: parent.top
                    anchors.leftMargin: 26
                    anchors.topMargin: 24
                    spacing: 12
                    Text { text: "Region"; color: Theme.ink; font.family: Theme.displayFont.family; font.pixelSize: 21; font.weight: Font.DemiBold }
                    Text { anchors.verticalCenter: parent.verticalCenter; text: catalogEngine.regions.length + " servers"; color: "#57615a"; font.family: Theme.monoFont.family; font.pixelSize: 13; font.weight: Font.Medium }
                }
                AbstractButton {
                    anchors.right: parent.right
                    anchors.top: parent.top
                    anchors.rightMargin: 26
                    anchors.topMargin: 23
                    width: autoLabel.implicitWidth + 24
                    height: 28
                    activeFocusOnTab: true
                    onClicked: networkSection.selectAutomatic()
                    background: Rectangle {
                        radius: 8
                        color: Qt.rgba(76 / 255, 232 / 255, 127 / 255, parent.activeFocus ? 0.24 : 0.14)
                        border.width: parent.activeFocus ? 1 : 0
                        border.color: Theme.accent
                    }
                    contentItem: Text {
                        id: autoLabel
                        text: networkSection.regionMode === "automatic" ? "AUTO · LOWEST PING" : "USE AUTO ROUTING"
                        color: Theme.accent
                        font.family: Theme.monoFont.family
                        font.pixelSize: 12
                        font.weight: Font.DemiBold
                        font.letterSpacing: 1.4
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                    }
                }
                TextField {
                    id: serverFilter
                    anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.leftMargin: 26; anchors.rightMargin: 26; anchors.topMargin: 68
                    height: 46; leftPadding: 44; rightPadding: 16
                    placeholderText: "Filter servers — or press Y"; color: Theme.ink; placeholderTextColor: "#57615a"; font.family: Theme.bodyFont.family; font.pixelSize: 15; activeFocusOnTab: true
                    background: Rectangle { radius: 11; color: "#070a08"; border.width: serverFilter.activeFocus ? 2 : 1; border.color: serverFilter.activeFocus ? Theme.accent : Theme.divider }
                    onTextChanged: { networkSection.filterText = text; appState.setPreference("network.serverFilter", text) }
                    Component.onCompleted: text = String(appState.preference("network.serverFilter", ""))
                }
                SearchGlyph {
                    anchors.left: serverFilter.left
                    anchors.leftMargin: 16
                    anchors.verticalCenter: serverFilter.verticalCenter
                    strokeColor: serverFilter.activeFocus ? Theme.accent : "#57615a"
                }
                Flickable {
                    id: regionScroll
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.top: serverFilter.bottom
                    anchors.bottom: regionFooter.top
                    anchors.leftMargin: 26
                    anchors.rightMargin: 22
                    anchors.topMargin: 14
                    anchors.bottomMargin: 14
                    clip: true
                    contentWidth: width
                    contentHeight: regionGroupsColumn.implicitHeight
                    boundsBehavior: Flickable.StopAtBounds
                    flickDeceleration: 2400
                    ScrollBar.vertical: ScrollBar {
                        width: 4
                        policy: regionScroll.contentHeight > regionScroll.height ? ScrollBar.AlwaysOn : ScrollBar.AlwaysOff
                        contentItem: Rectangle { radius: 2; color: "#3a453d" }
                        background: Rectangle { radius: 2; color: "#141a15" }
                    }
                    Column {
                        id: regionGroupsColumn
                        width: regionScroll.width - (regionScroll.contentHeight > regionScroll.height ? 14 : 0)
                        spacing: 8
                        Repeater {
                            model: networkSection.regionGroups
                            delegate: Column {
                                required property var modelData
                                readonly property var group: modelData
                                width: regionGroupsColumn.width
                                spacing: 8
                                Item {
                                    width: parent.width
                                    height: 22
                                    TinyCaps {
                                        anchors.left: parent.left
                                        anchors.verticalCenter: parent.verticalCenter
                                        text: group.name + " · " + group.items.length
                                    }
                                }
                                Repeater {
                                    model: group.items
                                    delegate: AbstractButton {
                                        id: regionButton
                                        required property var modelData
                                        readonly property int measuredPing: networkSection.pingValue(modelData.url)
                                        readonly property bool routeSelected: networkSection.isSelectedRegion(modelData)
                                        width: regionGroupsColumn.width
                                        height: 54
                                        activeFocusOnTab: true
                                        hoverEnabled: true
                                        scale: pressed ? 0.985 : 1
                                        onClicked: networkSection.selectRegion(modelData)
                                        background: Rectangle {
                                            radius: 12
                                            color: regionButton.routeSelected || regionButton.hovered ? "#101512" : "#070a08"
                                            border.width: regionButton.routeSelected || regionButton.activeFocus ? 2 : 1
                                            border.color: regionButton.routeSelected || regionButton.activeFocus ? Theme.accent : Theme.divider
                                            Behavior on color { ColorAnimation { duration: page.reducedMotion ? 0 : Theme.motionFast } }
                                            Behavior on border.color { ColorAnimation { duration: page.reducedMotion ? 0 : Theme.motionFast } }
                                        }
                                        contentItem: Item {
                                            Text {
                                                anchors.left: parent.left
                                                anchors.leftMargin: 16
                                                anchors.right: strength.left
                                                anchors.rightMargin: 16
                                                anchors.verticalCenter: parent.verticalCenter
                                                text: modelData.name
                                                color: regionButton.routeSelected ? Theme.ink : Theme.inkSoft
                                                font.family: Theme.bodyFont.family
                                                font.pixelSize: 16
                                                font.weight: regionButton.routeSelected ? Font.DemiBold : Font.Medium
                                                elide: Text.ElideRight
                                            }
                                            SignalBars {
                                                id: strength
                                                anchors.right: pingLabel.left
                                                anchors.rightMargin: 12
                                                anchors.verticalCenter: parent.verticalCenter
                                                latency: regionButton.measuredPing
                                            }
                                            Text {
                                                id: pingLabel
                                                anchors.right: parent.right
                                                anchors.rightMargin: 16
                                                anchors.verticalCenter: parent.verticalCenter
                                                width: 78
                                                horizontalAlignment: Text.AlignRight
                                                text: networkSection.pingText(modelData.url)
                                                color: regionButton.measuredPing < 0 ? Theme.inkMuted : strength.signalColor
                                                font.family: Theme.monoFont.family
                                                font.pixelSize: 14
                                                font.weight: regionButton.routeSelected ? Font.DemiBold : Font.Medium
                                                elide: Text.ElideRight
                                            }
                                        }
                                        Behavior on scale { NumberAnimation { duration: page.reducedMotion ? 0 : Theme.motionFast; easing.type: Easing.OutCubic } }
                                    }
                                }
                            }
                        }
                        Text {
                            visible: networkSection.filteredRegions.length === 0
                            width: parent.width
                            topPadding: 20
                            horizontalAlignment: Text.AlignHCenter
                            text: "No regions match “" + networkSection.filterText + "”."
                            color: Theme.inkMuted
                            font.family: Theme.bodyFont.family
                            font.pixelSize: 13
                        }
                    }
                }
                Item {
                    id: regionFooter
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.bottom: parent.bottom
                    anchors.leftMargin: 26
                    anchors.rightMargin: 26
                    anchors.bottomMargin: 22
                    height: 20
                    Text {
                        anchors.left: parent.left
                        anchors.right: pingAgain.left
                        anchors.rightMargin: 18
                        anchors.verticalCenter: parent.verticalCenter
                        text: networkSection.regionGroups.length + " infrastructure groups · scroll for all " + networkSection.filteredRegions.length
                        color: "#57615a"
                        font.family: Theme.bodyFont.family
                        font.pixelSize: 13
                        elide: Text.ElideRight
                    }
                    AbstractButton {
                        id: pingAgain
                        anchors.right: parent.right
                        anchors.verticalCenter: parent.verticalCenter
                        width: pingAgainLabel.implicitWidth
                        height: 30
                        enabled: !catalogEngine.probingRegions
                        activeFocusOnTab: true
                        onClicked: catalogEngine.probeRegions()
                        contentItem: Text {
                            id: pingAgainLabel
                            text: catalogEngine.probingRegions ? "Pinging…" : "Ping all again →"
                            color: pingAgain.activeFocus ? Theme.ink : Theme.accent
                            font.family: Theme.bodyFont.family
                            font.pixelSize: 13
                            font.weight: Font.Medium
                            horizontalAlignment: Text.AlignRight
                            verticalAlignment: Text.AlignVCenter
                        }
                        background: Rectangle { color: "transparent"; border.width: pingAgain.activeFocus ? 1 : 0; border.color: Theme.accent; radius: 6 }
                    }
                }
            }
            ColumnLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; spacing: 16
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
                    SmallButton { anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 17; text: catalogEngine.networkTest.status === "testing" ? "Testing…" : "Run again →"; enabled: catalogEngine.networkTest.status !== "testing"; onClicked: catalogEngine.testConnection(networkSection.testTarget) }
                    RowLayout {
                        anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.leftMargin: 22; anchors.rightMargin: 22; anchors.bottomMargin: 18; spacing: 24
                        Repeater { model: [{v:Number(catalogEngine.networkTest.latencyMs) >= 0 ? catalogEngine.networkTest.latencyMs : "—",l:"ms RTT"},{v:catalogEngine.networkTest.jitterMs === undefined ? "—" : catalogEngine.networkTest.jitterMs,l:"ms jitter"},{v:catalogEngine.networkTest.packetLoss === undefined ? "—" : catalogEngine.networkTest.packetLoss + "%",l:"packet loss"},{v:String(catalogEngine.networkTest.status || "not run").toUpperCase(),l:"route state"}]; Column { Text { text: modelData.v; color: index === 3 && String(modelData.v) === "PASS" ? Theme.accent : Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 18; font.weight: Font.Bold } Text { text: modelData.l; color: Theme.inkMuted; font.pixelSize: 9 } } }
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
        width: 340
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
        opacity: page.sectionReveal
        transform: Translate { x: (1 - page.sectionReveal) * 14 }

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
