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
    readonly property var summaries: [
        [
            { label: "Time played", value: "18.7 h", detail: "last 7 days" },
            { label: "Sessions", value: "14", detail: "0 disconnects" },
            { label: "Average FPS", value: "118", detail: "target 120" },
            { label: "Average RTT", value: "11 ms", detail: appState.serverRegion }
        ],
        [
            { label: "Time played", value: "42.5 h", detail: "last 14 days" },
            { label: "Sessions", value: "31", detail: "0 disconnects" },
            { label: "Average FPS", value: "116", detail: "target 120" },
            { label: "Average RTT", value: "12 ms", detail: appState.serverRegion }
        ],
        [
            { label: "Time played", value: "286.4 h", detail: "last 90 days" },
            { label: "Sessions", value: "214", detail: "3 disconnects" },
            { label: "Average FPS", value: "114", detail: "target 120" },
            { label: "Average RTT", value: "14 ms", detail: appState.serverRegion }
        ]
    ]
    readonly property var chartSets: [
        [54, 82, 45, 96, 61, 110, 89],
        [56, 38, 76, 24, 10, 101, 126, 48, 64, 31, 86, 42, 70, 94],
        [31, 54, 42, 76, 24, 60, 83, 49, 95, 62, 37, 72, 55, 88, 45, 101, 63, 78]
    ]
    readonly property var reports: [
        { title: "Cyber Drift 2088", when: "today · 2 h 10 m", performance: "117 FPS · 14 ms RTT", status: "Smooth", warning: false },
        { title: "Starfall Frontier", when: "yesterday · 3 h 40 m", performance: "118 FPS · 12 ms RTT", status: "Smooth", warning: false },
        { title: "Iron Harvest 2", when: "Aug 22 · 1 h 05 m", performance: "96 FPS · 31 ms RTT", status: "Wi-Fi jitter", warning: true }
    ]

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
            model: page.summaries[page.rangeIndex]
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
                model: page.chartSets[page.rangeIndex]
                Item {
                    required property int modelData
                    required property int index
                    width: (bars.width - (page.chartSets[page.rangeIndex].length - 1) * bars.spacing) / page.chartSets[page.rangeIndex].length
                    height: bars.height
                    Rectangle {
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.bottom: parent.bottom
                        height: Math.max(8, modelData)
                        radius: 3
                        color: index === page.chartSets[page.rangeIndex].length - 1 ? Theme.accent : "#376c4a"
                    }
                    Text {
                        visible: index === page.chartSets[page.rangeIndex].length - 1
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
                        visible: page.rangeIndex === 1 && index === 6
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

        Text { x: 24; anchors.bottom: parent.bottom; anchors.bottomMargin: 20; text: page.rangeIndex === 0 ? "AUG 18" : (page.rangeIndex === 1 ? "AUG 11" : "MAY 26"); color: "#455048"; font.family: Theme.monoFont.family; font.pixelSize: 9 }
        Text { anchors.horizontalCenter: parent.horizontalCenter; anchors.bottom: parent.bottom; anchors.bottomMargin: 20; text: page.rangeIndex === 1 ? "AUG 17" : ""; color: "#455048"; font.family: Theme.monoFont.family; font.pixelSize: 9 }
        Text { anchors.right: parent.right; anchors.rightMargin: 24; anchors.bottom: parent.bottom; anchors.bottomMargin: 20; text: "AUG 24"; color: "#455048"; font.family: Theme.monoFont.family; font.pixelSize: 9 }
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
        Keys.onReturnPressed: page.openReport(page.reports[currentIndex])
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
            text: "31 sessions recorded locally · export CSV with X"
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
