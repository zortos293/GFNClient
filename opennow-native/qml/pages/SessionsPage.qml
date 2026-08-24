import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    signal reportOpened(var report)
    signal sessionsExported(string path)

    property int rangeIndex: 1
    property bool reportOpen: false
    property var activeReport: ({})
    property string exportMessage: ""
    readonly property var rangeNames: ["7 days", "14 days", "90 days"]
    readonly property var rangeDays: [7, 14, 90]
    readonly property var reports: buildReports(filteredSessions())

    function filteredSessions() {
        var source = appState.sessions || []
        var cutoff = Date.now() - rangeDays[rangeIndex] * 86400000
        return source.filter(function(session) {
            var started = Date.parse(String(session.startedAt || ""))
            return !isNaN(started) && started >= cutoff
        })
    }

    function summaryForRange() {
        var sessions = filteredSessions()
        var minutes = 0
        var fps = 0
        var latency = 0
        var disconnects = 0
        for (var i = 0; i < sessions.length; ++i) {
            minutes += Number(sessions[i].durationMinutes || 0)
            fps += Number(sessions[i].averageFps || 0)
            latency += Number(sessions[i].latencyMs || 0)
            disconnects += Number(sessions[i].disconnects || 0)
        }
        var count = sessions.length
        return [
            { label: "Time played", value: (minutes / 60).toFixed(1) + " h", detail: "last " + rangeDays[rangeIndex] + " days" },
            { label: "Sessions", value: String(count), detail: disconnects + (disconnects === 1 ? " disconnect" : " disconnects") },
            { label: "Average FPS", value: count > 0 ? String(Math.round(fps / count)) : "—", detail: "decoded stream FPS" },
            { label: "Average RTT", value: count > 0 ? Math.round(latency / count) + " ms" : "—", detail: appState.serverRegion }
        ]
    }

    function chartValues() {
        var days = rangeDays[rangeIndex]
        var bucketCount = days <= 14 ? days : 18
        var buckets = []
        var i
        for (i = 0; i < bucketCount; ++i)
            buckets.push(0)
        var sessions = filteredSessions()
        var bucketSpan = days / bucketCount
        var now = Date.now()
        for (i = 0; i < sessions.length; ++i) {
            var ageDays = Math.max(0, (now - Date.parse(String(sessions[i].startedAt))) / 86400000)
            var reverseIndex = Math.min(bucketCount - 1, Math.floor(ageDays / bucketSpan))
            buckets[bucketCount - 1 - reverseIndex] += Number(sessions[i].durationMinutes || 0)
        }
        var maximum = Math.max.apply(Math, buckets.concat([1]))
        return buckets.map(function(minutes) { return Math.max(4, Math.round(minutes / maximum * 126)) })
    }

    function durationText(minutes) {
        var hours = Math.floor(minutes / 60)
        var remaining = Math.floor(minutes % 60)
        return hours > 0 ? hours + " h " + String(remaining).padStart(2, "0") + " m" : Math.max(1, remaining) + " min"
    }

    function whenText(isoDate) {
        var value = new Date(String(isoDate))
        if (isNaN(value.getTime()))
            return "unknown time"
        var age = Math.floor((Date.now() - value.getTime()) / 86400000)
        if (age === 0)
            return "today"
        if (age === 1)
            return "yesterday"
        return value.toLocaleDateString(Qt.locale(), "MMM d")
    }

    function buildReports(sessions) {
        return sessions.slice(0, 20).map(function(session) {
            var rating = String(session.rating || (Number(session.disconnects || 0) > 0 ? "Recovered" : "Smooth"))
            return {
                title: String(session.title || "Unknown game"),
                when: whenText(session.startedAt) + " · " + durationText(Number(session.durationMinutes || 0)),
                performance: Number(session.averageFps || 0) + " FPS · " + Number(session.latencyMs || 0) + " ms RTT",
                status: rating,
                warning: rating === "Unstable" || Number(session.packetLoss || 0) >= 1,
                duration: durationText(Number(session.durationMinutes || 0)),
                region: String(session.region || "—"),
                packetLoss: Number(session.packetLoss || 0).toFixed(2) + "%",
                disconnects: String(Number(session.disconnects || 0))
            }
        })
    }

    function focusRange() {
        var item = rangeButtons.itemAt(page.rangeIndex)
        if (item)
            item.forceActiveFocus()
    }

    function setRange(index) {
        page.rangeIndex = Math.max(0, Math.min(page.rangeNames.length - 1, index))
    }

    function cycleRange(delta) {
        page.rangeIndex = (page.rangeIndex + delta + page.rangeNames.length) % page.rangeNames.length
        if (page.visible)
            page.focusRange()
    }

    function openReport(report) {
        page.activeReport = report
        page.reportOpen = true
        page.reportOpened(report)
        Qt.callLater(function() { reportPanel.forceActiveFocus() })
    }

    function exportSessions() {
        var path = appState.exportSessions()
        page.exportMessage = path && path.length > 0 ? "Exported to " + path : "Export failed"
        page.sessionsExported(path || "")
        exportToast.restart()
    }

    onVisibleChanged: {
        if (visible)
            Qt.callLater(page.focusRange)
        else
            reportOpen = false
    }

    Shortcut {
        sequence: "X"
        enabled: page.visible && !page.reportOpen
        onActivated: page.exportSessions()
    }

    Text {
        x: Theme.pageMargin
        y: 36
        text: "Sessions"
        color: Theme.ink
        font.family: Theme.displayFont.family
        font.pixelSize: 42
        font.weight: Font.DemiBold
    }

    Row {
        anchors.right: parent.right
        anchors.rightMargin: Theme.pageMargin
        y: 45
        spacing: 8
        Repeater {
            id: rangeButtons
            model: page.rangeNames
            Button {
                id: rangeButton
                required property string modelData
                required property int index
                width: 76
                height: 38
                focusPolicy: Qt.StrongFocus
                onClicked: page.setRange(index)
                Keys.onLeftPressed: {
                    page.setRange(index - 1)
                    page.focusRange()
                }
                Keys.onRightPressed: {
                    page.setRange(index + 1)
                    page.focusRange()
                }
                Keys.onDownPressed: reportsList.forceActiveFocus()
                contentItem: Text {
                    text: rangeButton.modelData
                    color: rangeButton.index === page.rangeIndex ? Theme.accentInk : Theme.inkMuted
                    font.pixelSize: 12
                    font.weight: Font.DemiBold
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                background: Rectangle {
                    radius: 9
                    color: rangeButton.index === page.rangeIndex ? Theme.accent : "transparent"
                    border.width: rangeButton.activeFocus && rangeButton.index !== page.rangeIndex ? 2 : 1
                    border.color: rangeButton.activeFocus ? Theme.accent : Theme.divider
                }
            }
        }
    }

    RowLayout {
        id: summaryRow
        x: Theme.pageMargin
        y: 104
        width: parent.width - Theme.pageMargin * 2
        spacing: 18
        Repeater {
            model: page.summaryForRange()
            Rectangle {
                required property var modelData
                Layout.fillWidth: true
                Layout.preferredHeight: 122
                radius: 15
                color: Theme.surfaceRaised
                border.color: Theme.divider
                Column {
                    anchors.left: parent.left
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.leftMargin: 24
                    spacing: 7
                    Text { text: modelData.label; color: Theme.inkMuted; font.pixelSize: 13 }
                    Text { text: modelData.value; color: Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 36; font.weight: Font.Bold }
                    Text { text: modelData.detail; color: "#4f5a53"; font.pixelSize: 12 }
                }
            }
        }
    }

    Rectangle {
        id: chart
        x: Theme.pageMargin
        y: 246
        width: parent.width - Theme.pageMargin * 2
        height: 260
        radius: 15
        color: Theme.surfaceRaised
        border.color: Theme.divider

        Text { x: 24; y: 22; text: "Hours per day"; color: Theme.ink; font.pixelSize: 17; font.weight: Font.Bold }
        Text { anchors.right: parent.right; anchors.rightMargin: 24; y: 25; text: "last " + page.rangeNames[page.rangeIndex]; color: "#4f5a53"; font.pixelSize: 11 }

        Row {
            id: bars
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.leftMargin: 24
            anchors.rightMargin: 24
            anchors.bottomMargin: 52
            height: 145
            spacing: page.rangeIndex === 2 ? 12 : 10
            Repeater {
                model: page.chartValues()
                Item {
                    required property int modelData
                    required property int index
                    width: (bars.width - (page.chartValues().length - 1) * bars.spacing) / page.chartValues().length
                    height: bars.height
                    Rectangle {
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.bottom: parent.bottom
                        height: Math.max(8, modelData)
                        radius: 3
                        color: index === page.chartValues().length - 1 ? Theme.accent : "#376c4a"
                    }
                    Text {
                        visible: index === page.chartValues().length - 1
                        anchors.bottom: parent.bottom
                        anchors.bottomMargin: modelData + 8
                        anchors.horizontalCenter: parent.horizontalCenter
                        text: "today"
                        color: Theme.accent
                        font.family: Theme.monoFont.family
                        font.pixelSize: 10
                        font.weight: Font.Bold
                    }
                    Text {
                        visible: false
                        anchors.bottom: parent.bottom
                        anchors.bottomMargin: modelData + 8
                        anchors.horizontalCenter: parent.horizontalCenter
                        text: "6.2 h"
                        color: Theme.inkSoft
                        font.family: Theme.monoFont.family
                        font.pixelSize: 10
                        font.weight: Font.Bold
                    }
                }
            }
        }

        Text { x: 24; anchors.bottom: parent.bottom; anchors.bottomMargin: 20; text: new Date(Date.now() - page.rangeDays[page.rangeIndex] * 86400000).toLocaleDateString(Qt.locale(), "MMM d").toUpperCase(); color: "#455048"; font.family: Theme.monoFont.family; font.pixelSize: 9 }
        Text { anchors.right: parent.right; anchors.rightMargin: 24; anchors.bottom: parent.bottom; anchors.bottomMargin: 20; text: new Date().toLocaleDateString(Qt.locale(), "MMM d").toUpperCase(); color: "#455048"; font.family: Theme.monoFont.family; font.pixelSize: 9 }
    }

    Text {
        x: Theme.pageMargin
        y: 536
        text: "Recent session reports"
        color: Theme.ink
        font.pixelSize: 17
        font.weight: Font.Bold
    }

    ListView {
        id: reportsList
        x: Theme.pageMargin
        y: 572
        width: parent.width - Theme.pageMargin * 2
        height: 192
        spacing: 10
        model: page.reports
        clip: true
        focus: false
        currentIndex: 0
        Keys.onReturnPressed: {
            if (currentIndex >= 0 && currentIndex < page.reports.length)
                page.openReport(page.reports[currentIndex])
        }
        Keys.onUpPressed: {
            if (currentIndex === 0)
                page.focusRange()
            else
                decrementCurrentIndex()
        }

        delegate: Button {
            id: reportRow
            required property var modelData
            required property int index
            width: reportsList.width
            height: 54
            focusPolicy: Qt.NoFocus
            onClicked: {
                reportsList.currentIndex = index
                page.openReport(modelData)
            }
            contentItem: RowLayout {
                anchors.leftMargin: 18
                anchors.rightMargin: 18
                Text { text: reportRow.modelData.title; color: Theme.ink; font.pixelSize: 14; font.weight: Font.Bold; Layout.preferredWidth: 260 }
                Text { text: reportRow.modelData.when; color: Theme.inkMuted; font.family: Theme.monoFont.family; font.pixelSize: 11; Layout.preferredWidth: 190 }
                Text { text: reportRow.modelData.performance; color: Theme.inkSoft; font.family: Theme.monoFont.family; font.pixelSize: 11 }
                Item { Layout.fillWidth: true }
                Text { text: reportRow.modelData.warning ? "⚠  " + reportRow.modelData.status : "●  " + reportRow.modelData.status; color: reportRow.modelData.warning ? Theme.warning : Theme.accent; font.pixelSize: 12; font.weight: Font.Bold }
            }
            background: Rectangle {
                radius: 10
                color: reportRow.index === reportsList.currentIndex && reportsList.activeFocus ? "#132119" : Theme.surfaceRaised
                border.width: reportRow.index === reportsList.currentIndex && reportsList.activeFocus ? 2 : 1
                border.color: reportRow.index === reportsList.currentIndex && reportsList.activeFocus ? Theme.accent : Theme.divider
            }
        }
    }

    Rectangle {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.leftMargin: Theme.pageMargin
        anchors.rightMargin: Theme.pageMargin
        height: 78
        color: Theme.canvas
        Rectangle { anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; height: 1; color: Theme.divider }
        Text {
            anchors.left: parent.left
            anchors.verticalCenter: parent.verticalCenter
            text: appState.sessions.length + " sessions recorded locally · export CSV with X"
            color: "#455048"
            font.family: Theme.monoFont.family
            font.pixelSize: 11
            font.letterSpacing: 1.1
        }
        Row {
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            spacing: 22
            KeyHint { keyText: "A"; label: "Open report" }
            KeyHint { keyText: "X"; label: "Export" }
            KeyHint { keyText: "LB RB"; label: "Time range" }
        }
    }

    Timer {
        id: exportToast
        interval: 3600
        onTriggered: page.exportMessage = ""
    }

    Rectangle {
        visible: page.exportMessage.length > 0
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 92
        z: 15
        width: Math.min(720, exportText.implicitWidth + 44)
        height: 48
        radius: 12
        color: Theme.surfaceBright
        border.color: page.exportMessage === "Export failed" ? Theme.error : Theme.accent
        Text { id: exportText; anchors.centerIn: parent; text: page.exportMessage; color: Theme.ink; font.pixelSize: 12; elide: Text.ElideMiddle; width: parent.width - 32; horizontalAlignment: Text.AlignHCenter }
    }

    Rectangle {
        visible: page.reportOpen
        anchors.fill: parent
        z: 30
        color: "#a6000000"
        MouseArea { anchors.fill: parent; onClicked: { page.reportOpen = false; reportsList.forceActiveFocus() } }
    }

    FocusScope {
        id: reportPanel
        visible: page.reportOpen
        z: 31
        width: 620
        height: 390
        anchors.centerIn: parent
        focus: visible
        Keys.onEscapePressed: {
            page.reportOpen = false
            reportsList.forceActiveFocus()
        }
        Keys.onReturnPressed: {
            page.reportOpen = false
            reportsList.forceActiveFocus()
        }
        Rectangle { anchors.fill: parent; radius: 18; color: Theme.surfaceRaised; border.color: Theme.divider }
        Text { x: 28; y: 24; text: page.activeReport.title || "Session report"; color: Theme.ink; font.pixelSize: 24; font.weight: Font.Bold }
        Text { x: 28; y: 62; text: page.activeReport.when || ""; color: Theme.inkMuted; font.pixelSize: 13 }
        Rectangle { x: 28; y: 96; width: parent.width - 56; height: 1; color: Theme.divider }
        Column {
            x: 28
            y: 122
            width: parent.width - 56
            spacing: 20
            Repeater {
                model: [
                    { label: "Performance", value: page.activeReport.performance || "" },
                    { label: "Connection health", value: page.activeReport.status || "" },
                    { label: "Region", value: appState.serverRegion },
                    { label: "Server", value: appState.serverName }
                ]
                RowLayout {
                    required property var modelData
                    width: parent.width
                    Text { text: modelData.label; color: Theme.inkMuted; font.pixelSize: 14 }
                    Item { Layout.fillWidth: true }
                    Text { text: modelData.value; color: modelData.value === "Smooth" ? Theme.accent : Theme.ink; font.family: Theme.monoFont.family; font.pixelSize: 13; font.weight: Font.Bold }
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
                reportsList.forceActiveFocus()
            }
        }
    }
}
