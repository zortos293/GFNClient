import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    required property var games
    signal openGame(var game)
    property string query: "cyber"
    property bool keyboardVisible: true
    property int selectedRow: 3
    property int selectedColumn: 2
    readonly property var keyRows: [
        ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
        ["Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"],
        ["A", "S", "D", "F", "G", "H", "J", "K", "L"],
        ["Z", "X", "C", "V", "B", "N", "M", "⌫"],
        ["SPACE", "DONE"]
    ]
    readonly property var supplementalGames: [
        { title: "Cyberline: Rogue", subtitle: "Epic · not installed", badge: "", variant: 2, progress: 0, genre: "Action", store: "Epic", description: "Break the line in a tactical cyber-thriller." },
        { title: "Neon Cyber Racer", subtitle: "GOG · 2 h played", badge: "", variant: 4, progress: 0, genre: "Racing", store: "GOG", description: "Race the neon grid at impossible speed." }
    ]
    readonly property var searchCatalog: {
        var catalog = page.games ? page.games.slice(0) : []
        var known = {}
        for (var i = 0; i < catalog.length; ++i)
            known[catalog[i].title] = true
        for (var j = 0; j < page.supplementalGames.length; ++j) {
            if (!known[page.supplementalGames[j].title])
                catalog.push(page.supplementalGames[j])
        }
        return catalog
    }
    readonly property var filteredGames: {
        var needle = page.query.trim().toLowerCase()
        if (needle.length === 0)
            return page.searchCatalog
        return page.searchCatalog.filter(function(game) {
            return game.title.toLowerCase().indexOf(needle) >= 0
                || (game.store || "").toLowerCase().indexOf(needle) >= 0
                || (game.genre || "").toLowerCase().indexOf(needle) >= 0
        })
    }

    function appendText(text) {
        page.query += text
    }

    function backspace() {
        if (page.query.length > 0)
            page.query = page.query.slice(0, -1)
    }

    function moveHorizontal(delta) {
        var count = page.keyRows[page.selectedRow].length
        page.selectedColumn = Math.max(0, Math.min(count - 1, page.selectedColumn + delta))
    }

    function moveVertical(delta) {
        var nextRow = Math.max(0, Math.min(page.keyRows.length - 1, page.selectedRow + delta))
        if (nextRow === page.selectedRow)
            return
        var oldCount = page.keyRows[page.selectedRow].length
        var nextCount = page.keyRows[nextRow].length
        var center = (page.selectedColumn + 0.5) / oldCount
        page.selectedRow = nextRow
        page.selectedColumn = Math.max(0, Math.min(nextCount - 1, Math.floor(center * nextCount)))
    }

    function activateKey(row, column) {
        var key = page.keyRows[row][column]
        if (key === "⌫") {
            page.backspace()
        } else if (key === "SPACE") {
            page.appendText(" ")
        } else if (key === "DONE") {
            page.keyboardVisible = false
            if (results.count > 0)
                results.forceActiveFocus()
            else
                searchInput.forceActiveFocus()
        } else {
            page.appendText(key.toLowerCase())
        }
    }

    onVisibleChanged: {
        if (visible) {
            keyboardVisible = true
            Qt.callLater(function() { keyboard.forceActiveFocus() })
        }
    }

    Rectangle {
        id: searchBar
        x: Theme.pageMargin
        y: 32
        width: parent.width - Theme.pageMargin * 2
        height: 76
        radius: 16
        color: Theme.surfaceRaised
        border.width: searchInput.activeFocus || keyboard.activeFocus ? 3 : 1
        border.color: searchInput.activeFocus || keyboard.activeFocus ? Theme.accent : Theme.divider

        Text {
            anchors.left: parent.left
            anchors.leftMargin: 28
            anchors.verticalCenter: parent.verticalCenter
            text: "⌕"
            color: Theme.accent
            font.pixelSize: 26
        }

        TextInput {
            id: searchInput
            anchors.left: parent.left
            anchors.leftMargin: 66
            anchors.right: resultCount.left
            anchors.rightMargin: 24
            anchors.verticalCenter: parent.verticalCenter
            text: page.query
            color: Theme.ink
            selectionColor: Theme.accent
            selectedTextColor: Theme.accentInk
            font.family: Theme.bodyFont.family
            font.pixelSize: 22
            font.weight: Font.DemiBold
            onTextEdited: page.query = text
            onAccepted: {
                page.keyboardVisible = false
                if (results.count > 0)
                    results.forceActiveFocus()
            }
            Keys.onDownPressed: {
                page.keyboardVisible = true
                keyboard.forceActiveFocus()
            }
        }

        Text {
            id: resultCount
            anchors.right: parent.right
            anchors.rightMargin: 28
            anchors.verticalCenter: parent.verticalCenter
            text: page.filteredGames.length + (page.filteredGames.length === 1 ? " result" : " results")
            color: Theme.inkMuted
            font.family: Theme.monoFont.family
            font.pixelSize: 12
        }
    }

    GridView {
        id: results
        x: Theme.pageMargin
        y: 132
        width: parent.width - Theme.pageMargin * 2
        height: 236
        clip: true
        model: page.filteredGames
        cellWidth: width / 5
        cellHeight: 230
        onCountChanged: {
            if (count === 0)
                currentIndex = -1
            else if (currentIndex < 0 || currentIndex >= count)
                currentIndex = 0
        }
        focus: page.visible && !page.keyboardVisible
        Keys.onReturnPressed: {
            if (currentIndex >= 0)
                page.openGame(page.filteredGames[currentIndex])
        }
        Keys.onDownPressed: {
            page.keyboardVisible = true
            keyboard.forceActiveFocus()
        }
        Keys.onUpPressed: searchInput.forceActiveFocus()

        delegate: Item {
            required property var modelData
            required property int index
            width: results.cellWidth
            height: results.cellHeight
            GameCard {
                anchors.fill: parent
                anchors.rightMargin: index % 5 === 4 ? 0 : 20
                anchors.bottomMargin: 14
                title: modelData.title
                subtitle: modelData.subtitle || ((modelData.store || "") + " · " + (modelData.genre || ""))
                badge: modelData.badge
                variant: modelData.variant
                selected: index === results.currentIndex
                onClicked: {
                    results.currentIndex = index
                    page.openGame(modelData)
                }
            }
        }
    }

    FocusScope {
        id: keyboard
        visible: page.keyboardVisible
        width: 676
        height: 342
        anchors.horizontalCenter: parent.horizontalCenter
        y: 584
        focus: visible
        Keys.onLeftPressed: page.moveHorizontal(-1)
        Keys.onRightPressed: page.moveHorizontal(1)
        Keys.onUpPressed: page.moveVertical(-1)
        Keys.onDownPressed: page.moveVertical(1)
        Keys.onReturnPressed: page.activateKey(page.selectedRow, page.selectedColumn)
        Keys.onEscapePressed: {
            page.keyboardVisible = false
            searchInput.forceActiveFocus()
        }
        Keys.onPressed: function(event) {
            if (event.key === Qt.Key_Backspace || (event.key === Qt.Key_X && (!event.text || event.text.length === 0))) {
                page.backspace()
                event.accepted = true
            } else if (event.text && event.text.length === 1 && event.text >= " " && event.key !== Qt.Key_Return) {
                page.appendText(event.text)
                event.accepted = true
            }
        }

        Rectangle {
            anchors.fill: parent
            radius: 16
            color: "#0c110e"
            border.color: Theme.divider
        }

        Column {
            anchors.fill: parent
            anchors.margins: 24
            spacing: 10

            Repeater {
                model: page.keyRows
                Row {
                    id: keyRow
                    required property var modelData
                    required property int index
                    property int rowIndex: index
                    width: parent.width
                    height: rowIndex === 4 ? 52 : 50
                    spacing: 8

                    Repeater {
                        model: keyRow.modelData
                        Rectangle {
                            id: keyCap
                            required property string modelData
                            required property int index
                            property bool selected: page.selectedRow === keyRow.rowIndex && page.selectedColumn === index
                            width: keyRow.rowIndex === 4
                                   ? (index === 0 ? keyRow.width * 0.69 : keyRow.width * 0.31 - keyRow.spacing)
                                   : (keyRow.width - (keyRow.modelData.length - 1) * keyRow.spacing) / keyRow.modelData.length
                            height: keyRow.height
                            radius: 9
                            color: selected ? Theme.accent : (keyRow.rowIndex === 4 && index === 1 ? "#14341f" : Theme.surfaceRaised)
                            border.width: selected ? 3 : 1
                            border.color: selected ? Theme.accentInk : Theme.divider

                            Text {
                                anchors.centerIn: parent
                                text: keyCap.modelData === "⌫" ? "⌫  X" : (keyCap.modelData === "SPACE" ? "SPACE  Y" : (keyCap.modelData === "DONE" ? "DONE  ≡" : keyCap.modelData))
                                color: keyCap.selected ? Theme.accentInk : (keyCap.modelData === "DONE" ? Theme.accent : Theme.inkSoft)
                                font.family: Theme.monoFont.family
                                font.pixelSize: keyCap.modelData.length > 1 ? 12 : 16
                                font.weight: Font.Bold
                            }

                            MouseArea {
                                anchors.fill: parent
                                hoverEnabled: true
                                onEntered: {
                                    page.selectedRow = keyRow.rowIndex
                                    page.selectedColumn = keyCap.index
                                }
                                onClicked: {
                                    keyboard.forceActiveFocus()
                                    page.activateKey(keyRow.rowIndex, keyCap.index)
                                }
                            }
                        }
                    }
                }
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
            text: "searching across steam · epic · gog · ubisoft"
            color: "#455048"
            font.family: Theme.monoFont.family
            font.pixelSize: 11
            font.letterSpacing: 1.1
        }
        Row {
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            spacing: 22
            KeyHint { keyText: "A"; label: "Type" }
            KeyHint { keyText: "X"; label: "Backspace" }
            KeyHint { keyText: "ENTER"; label: "Physical keyboard OK" }
        }
    }
}
