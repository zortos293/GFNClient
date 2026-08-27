import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    required property var game
    signal back()
    signal play(string quality)
    signal favoriteToggled(bool favorite)
    signal streamSettingsRequested()
    signal reportRequested()

    property bool favorite: false
    property bool settingsOpen: false
    property bool reportOpen: false
    property real revealProgress: visible ? 1 : 0
    property string selectedQuality: "1440p120"
    property int settingsIndex: 2
    readonly property var qualityOptions: [
        { title: "Balanced", detail: "720p · 60 FPS", quality: "720p60" },
        { title: "Quality", detail: "1080p · 60 FPS", quality: "1080p60" },
        { title: "Performance", detail: "1440p · 120 FPS", quality: "1440p120" },
        { title: "Cinematic", detail: "4K · 60 FPS", quality: "4k60" }
    ]
    readonly property var latestReport: {
        var sessions = appState.sessions || []
        var title = page.field("title", "")
        for (var i = 0; i < sessions.length; ++i) {
            if (String(sessions[i].title || "") === title)
                return sessions[i]
        }
        return null
    }

    function field(name, fallback) {
        var value = page.game ? page.game[name] : undefined
        return value === undefined || value === null || value === "" ? fallback : value
    }

    function loadFavorite() {
        if (page.game)
            page.favorite = Boolean(appState.preference("favorite/" + page.field("title", "Game"), false))
    }

    function toggleFavorite() {
        page.favorite = !page.favorite
        appState.setPreference("favorite/" + page.field("title", "Game"), page.favorite)
        page.favoriteToggled(page.favorite)
    }

    function openSettings() {
        page.settingsOpen = true
        page.reportOpen = false
        page.streamSettingsRequested()
        Qt.callLater(function() { settingsPanel.forceActiveFocus() })
    }

    function openReport() {
        page.reportOpen = true
        page.settingsOpen = false
        page.reportRequested()
        Qt.callLater(function() { reportPanel.forceActiveFocus() })
    }

    onGameChanged: loadFavorite()
    Component.onCompleted: loadFavorite()
    onVisibleChanged: {
        if (visible)
            Qt.callLater(function() { playButton.forceActiveFocus() })
        else {
            settingsOpen = false
            reportOpen = false
        }
    }
    opacity: revealProgress
    transform: Translate { y: (1 - page.revealProgress) * 14 }
    Behavior on revealProgress { NumberAnimation { duration: Theme.motion; easing.type: Easing.OutCubic } }

    Shortcut {
        sequence: "Y"
        enabled: page.visible && !page.settingsOpen && !page.reportOpen
        onActivated: page.toggleFavorite()
    }
    Shortcut {
        sequence: "X"
        enabled: page.visible && !page.settingsOpen && !page.reportOpen
        onActivated: page.openSettings()
    }

    Item {
        id: canvas
        x: -Theme.railWidth
        width: page.width + Theme.railWidth
        height: page.height

        Rectangle {
            anchors.fill: parent
            color: Theme.canvas
        }

        GameArtwork {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            height: 260
            variant: page.field("variant", 1)
            source: page.field("heroImageUrl", page.field("imageUrl", ""))
            game: page.game
        }

        Rectangle {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            height: 260
            color: "#a30a130e"
        }

        Button {
            id: backButton
            x: 52
            y: 30
            width: 170
            height: 44
            focusPolicy: Qt.StrongFocus
            onClicked: page.back()
            contentItem: Row {
                spacing: 10
                anchors.verticalCenter: parent.verticalCenter
                Rectangle {
                    width: 28
                    height: 28
                    radius: 14
                    color: "transparent"
                    border.color: backButton.activeFocus ? Theme.accent : "#3b4740"
                    Text { anchors.centerIn: parent; text: "B"; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 11; font.weight: Font.Bold }
                }
                Text { anchors.verticalCenter: parent.verticalCenter; text: "Back to library"; color: Theme.inkSoft; font.pixelSize: 14; font.weight: Font.DemiBold }
            }
            background: Rectangle { color: "transparent"; border.width: backButton.activeFocus ? 1 : 0; border.color: Theme.accent; radius: 8 }
        }

        Rectangle {
            id: titleBand
            anchors.left: parent.left
            anchors.right: parent.right
            y: 260
            height: 300
            color: "#080c09"

            Row {
                x: 52
                y: 94
                spacing: 10
                Repeater {
                model: (page.game.nvidiaTech || []).concat(page.game.supportedControls || []).slice(0, 4)
                    Rectangle {
                        required property string modelData
                        width: tagText.implicitWidth + 22
                        height: 28
                        radius: 7
                        color: "transparent"
                        border.color: Theme.divider
                        Text {
                            id: tagText
                            anchors.centerIn: parent
                            text: modelData
                            color: modelData === "RTX ON" ? Theme.accent : Theme.inkSoft
                            font.family: Theme.monoFont.family
                            font.pixelSize: 10
                            font.weight: Font.Bold
                            font.letterSpacing: 1
                        }
                    }
                }
            }

            Text {
                x: 52
                y: 136
                width: parent.width * 0.62
                text: page.field("title", "Untitled game")
                color: Theme.ink
                font.family: Theme.displayFont.family
                font.pixelSize: 56
                font.weight: Font.DemiBold
                elide: Text.ElideRight
            }

            Text {
                x: 52
                y: 218
                text: page.field("developerName", page.field("publisherName", ""))
                      + "   ·   " + (page.game.genres || []).join(", ")
                      + "   ·   " + (page.game.availableStores || []).join(", ")
                color: Theme.inkSoft
                font.family: Theme.bodyFont.family
                font.pixelSize: 15
            }

            ActionButton {
                id: playButton
                anchors.right: favoriteButton.left
                anchors.rightMargin: 14
                y: 174
                width: 196
                height: 76
                text: "PLAY   Ⓐ"
                glyph: "▶"
                primary: true
                focus: page.visible && !page.settingsOpen && !page.reportOpen
                onClicked: page.play(page.selectedQuality)
                KeyNavigation.right: favoriteButton
            }

            ActionButton {
                id: favoriteButton
                anchors.right: parent.right
                anchors.rightMargin: 52
                y: 180
                width: 190
                height: 66
                text: (page.favorite ? "★" : "☆") + "  Favorite   Ⓨ"
                onClicked: page.toggleFavorite()
                KeyNavigation.left: playButton
            }
        }

        ScrollView {
            id: aboutScroll
            x: 52
            y: 596
            width: canvas.width - activityCard.width - 52 - 52 - 28
            height: canvas.height - y - 78 - 18
            clip: true
            contentWidth: availableWidth
            ScrollBar.horizontal.policy: ScrollBar.AlwaysOff
            ScrollBar.vertical.policy: contentHeight > height ? ScrollBar.AsNeeded : ScrollBar.AlwaysOff

            Column {
                id: aboutContent
                width: aboutScroll.availableWidth - 18
                spacing: 16

                Text {
                    width: parent.width
                    text: "About"
                    color: Theme.ink
                    font.family: Theme.bodyFont.family
                    font.pixelSize: 18
                    font.weight: Font.Bold
                }

                Text {
                    width: parent.width
                    text: page.field("longDescription", page.field("description", "No description is available."))
                    color: Theme.inkSoft
                    font.family: Theme.bodyFont.family
                    font.pixelSize: 17
                    lineHeightMode: Text.FixedHeight
                    lineHeight: 28
                    wrapMode: Text.WordWrap
                }

                Flow {
                    width: parent.width
                    height: childrenRect.height
                    spacing: 10
                    Repeater {
                        model: page.game.featureLabels || []
                        Rectangle {
                            required property string modelData
                            width: capabilityText.implicitWidth + 24
                            height: 34
                            radius: 8
                            color: "transparent"
                            border.color: Theme.divider
                            Text { id: capabilityText; anchors.centerIn: parent; text: modelData; color: Theme.inkMuted; font.pixelSize: 12 }
                        }
                    }
                }

                Item { width: 1; height: 8 }
            }
        }

        Rectangle {
            id: activityCard
            anchors.right: parent.right
            anchors.rightMargin: 52
            y: 596
            width: 556
            height: 318
            radius: 16
            color: Theme.surfaceRaised
            border.color: Theme.divider

            Text { x: 24; y: 22; text: "Your activity"; color: Theme.ink; font.pixelSize: 18; font.weight: Font.Bold }
            Column {
                x: 24
                y: 64
                width: parent.width - 48
                spacing: 18
                Repeater {
                    model: [
                        { label: "Available on", value: (page.game.availableStores || []).join(", ") || "—", accent: false },
                        { label: "Last played", value: page.field("lastPlayed", "—"), accent: false },
                        { label: "Required tier", value: page.field("membershipTierLabel", "—"), accent: false },
                        { label: "Controls", value: (page.game.supportedControls || []).join(", ") || "—", accent: false }
                    ]
                    RowLayout {
                        required property var modelData
                        width: parent.width
                        Text { text: modelData.label; color: Theme.inkMuted; font.pixelSize: 13 }
                        Item { Layout.fillWidth: true }
                        Text { Layout.preferredWidth: parent.width * 0.58; text: modelData.value; horizontalAlignment: Text.AlignRight; elide: Text.ElideRight; color: modelData.accent ? Theme.accent : Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 13; font.weight: Font.Bold }
                    }
                }
            }
            Rectangle { x: 24; y: 226; width: parent.width - 48; height: 1; color: Theme.divider }
            Button {
                id: reportButton
                enabled: page.latestReport !== null
                x: 16
                y: 238
                width: parent.width - 32
                height: 56
                onClicked: page.openReport()
                contentItem: RowLayout {
                    Text { text: page.latestReport ? "Session report" : "No session report"; color: Theme.ink; font.pixelSize: 13; font.weight: Font.Bold }
                    Item { Layout.fillWidth: true }
                    Text { text: page.latestReport ? "View last →" : ""; color: Theme.accent; font.pixelSize: 13; font.weight: Font.Bold }
                }
                background: Rectangle { radius: 8; color: reportButton.down ? Theme.surfaceBright : "transparent"; border.width: reportButton.activeFocus ? 1 : 0; border.color: Theme.accent }
            }
        }

        Rectangle {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            height: 78
            color: Theme.canvas
            Rectangle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; height: 1; color: Theme.divider }
            Text {
                anchors.left: parent.left
                anchors.leftMargin: 52
                anchors.verticalCenter: parent.verticalCenter
                text: "GeForce NOW selects the streaming region when you launch"
                color: "#455048"
                font.family: Theme.monoFont.family
                font.pixelSize: 11
                font.letterSpacing: 1.1
            }
            Row {
                anchors.right: parent.right
                anchors.rightMargin: 52
                anchors.verticalCenter: parent.verticalCenter
                spacing: 22
                KeyHint { keyText: "A"; label: "Play" }
                KeyHint { keyText: "Y"; label: "Favorite" }
                KeyHint { keyText: "X"; label: "Stream settings" }
            }
        }

        Rectangle {
            visible: page.settingsOpen || page.reportOpen
            anchors.fill: parent
            z: 30
            color: "#a6000000"
            MouseArea { anchors.fill: parent; onClicked: { page.settingsOpen = false; page.reportOpen = false; playButton.forceActiveFocus() } }
        }

        FocusScope {
            id: settingsPanel
            visible: page.settingsOpen
            z: 31
            width: 560
            height: 430
            anchors.centerIn: parent
            focus: visible
            Keys.onEscapePressed: {
                page.settingsOpen = false
                playButton.forceActiveFocus()
            }
            Keys.onDownPressed: page.settingsIndex = (page.settingsIndex + 1) % page.qualityOptions.length
            Keys.onUpPressed: page.settingsIndex = (page.settingsIndex + page.qualityOptions.length - 1) % page.qualityOptions.length
            Keys.onReturnPressed: {
                page.selectedQuality = page.qualityOptions[page.settingsIndex].quality
                page.settingsOpen = false
                playButton.forceActiveFocus()
            }
            Rectangle { anchors.fill: parent; radius: 18; color: Theme.surfaceRaised; border.color: Theme.divider }
            Text { x: 28; y: 24; text: "Stream settings"; color: Theme.ink; font.pixelSize: 24; font.weight: Font.Bold }
            Text { x: 28; y: 58; text: "Choose the preset used for the next launch"; color: Theme.inkMuted; font.pixelSize: 13 }
            Column {
                x: 24
                y: 100
                width: parent.width - 48
                spacing: 8
                Repeater {
                    model: page.qualityOptions
                    Button {
                        id: presetButton
                        required property var modelData
                        required property int index
                        width: parent.width
                        height: 62
                        onHoveredChanged: { if (hovered) page.settingsIndex = index }
                        onClicked: {
                            page.settingsIndex = index
                            page.selectedQuality = modelData.quality
                            page.settingsOpen = false
                            playButton.forceActiveFocus()
                        }
                        contentItem: RowLayout {
                            anchors.leftMargin: 16
                            anchors.rightMargin: 16
                            Text { text: presetButton.modelData.title; color: Theme.ink; font.pixelSize: 15; font.weight: Font.Bold }
                            Item { Layout.fillWidth: true }
                            Text { text: presetButton.modelData.detail; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 12 }
                            Text { text: presetButton.index === page.settingsIndex ? "✓" : ""; color: Theme.accent; font.pixelSize: 16 }
                        }
                        background: Rectangle {
                            radius: 10
                            color: presetButton.index === page.settingsIndex ? "#173120" : Theme.surface
                            border.width: presetButton.index === page.settingsIndex ? 2 : 1
                            border.color: presetButton.index === page.settingsIndex ? Theme.accent : Theme.divider
                        }
                    }
                }
            }
        }

        FocusScope {
            id: reportPanel
            visible: page.reportOpen
            z: 31
            width: 600
            height: 360
            anchors.centerIn: parent
            focus: visible
            Keys.onEscapePressed: {
                page.reportOpen = false
                reportButton.forceActiveFocus()
            }
            Keys.onReturnPressed: {
                page.reportOpen = false
                reportButton.forceActiveFocus()
            }
            Rectangle { anchors.fill: parent; radius: 18; color: Theme.surfaceRaised; border.color: Theme.divider }
            Text { x: 28; y: 24; text: "Last session report"; color: Theme.ink; font.pixelSize: 24; font.weight: Font.Bold }
            Text { x: 28; y: 62; text: page.field("title", "Game") + " · " + (page.latestReport ? String(page.latestReport.startedAt || "") : ""); color: Theme.inkMuted; font.pixelSize: 13 }
            Column {
                x: 28
                y: 112
                width: parent.width - 56
                spacing: 18
                Repeater {
                    model: [
                        { label: "Average frame rate", value: page.latestReport ? Number(page.latestReport.averageFps || 0) + " FPS" : "—" },
                        { label: "Round-trip latency", value: page.latestReport ? Number(page.latestReport.latencyMs || 0) + " ms" : "—" },
                        { label: "Packet loss", value: page.latestReport ? Number(page.latestReport.packetLoss || 0).toFixed(2) + "%" : "—" },
                        { label: "Connection", value: page.latestReport ? String(page.latestReport.rating || "—") : "—" }
                    ]
                    RowLayout {
                        required property var modelData
                        width: parent.width
                        Text { text: modelData.label; color: Theme.inkMuted; font.pixelSize: 14 }
                        Item { Layout.fillWidth: true }
                        Text { text: modelData.value; color: modelData.value === "Smooth" ? Theme.accent : Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 14; font.weight: Font.Bold }
                    }
                }
            }
            ActionButton {
                anchors.right: parent.right
                anchors.bottom: parent.bottom
                anchors.margins: 24
                width: 120
                height: 52
                text: "Close"
                onClicked: {
                    page.reportOpen = false
                    reportButton.forceActiveFocus()
                }
            }
        }
    }
}
