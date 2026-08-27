import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: page
    required property var games
    signal openGame(var game)
    signal startGame(var game)
    signal favoriteChanged(var game, bool favorite)

    property int filterIndex: 0
    property int sortIndex: 0
    property bool sortOpen: false
    property int pendingSortIndex: sortIndex
    property var favorites: ({})
    property int favoritesRevision: 0
    readonly property var filterNames: ["All", "☆  Favorites", "Steam", "Epic", "GOG", "Ubisoft"]
    readonly property var sortNames: ["Recently played", "A → Z"]
    readonly property var libraryGames: page.games || []
    readonly property var visibleGames: {
        page.filterIndex
        page.sortIndex
        page.favoritesRevision
        return page.filteredAndSortedGames()
    }

    function isFavorite(game) {
        page.favoritesRevision
        return page.favorites[game.title] === true
    }

    function toggleFavorite(game) {
        var next = !page.isFavorite(game)
        page.favorites[game.title] = next
        page.favoritesRevision += 1
        appState.setPreference("favorite/" + game.title, next)
        page.favoriteChanged(game, next)
    }

    function cycleFilter(delta) {
        page.filterIndex = (page.filterIndex + delta + page.filterNames.length) % page.filterNames.length
        grid.currentIndex = grid.count > 0 ? 0 : -1
        if (page.visible)
            grid.forceActiveFocus()
    }

    function hoursPlayed(game) {
        var match = /([0-9]+)\s*h/.exec(game.subtitle || "")
        return match ? Number(match[1]) : 0
    }

    function filteredAndSortedGames() {
        var filter = page.filterNames[page.filterIndex]
        var output = page.libraryGames.filter(function(game) {
            if (page.filterIndex === 0)
                return true
            if (page.filterIndex === 1)
                return page.isFavorite(game)
            return (game.availableStores || []).some(function(store) {
                return String(store).toLowerCase() === filter.toLowerCase()
            })
        })
        output.sort(function(a, b) {
            if (page.sortIndex === 1)
                return a.title.localeCompare(b.title)
            return page.libraryGames.indexOf(a) - page.libraryGames.indexOf(b)
        })
        return output
    }

    function selectSort(index) {
        page.sortIndex = index
        page.pendingSortIndex = index
        page.sortOpen = false
        Qt.callLater(function() { sortButton.forceActiveFocus() })
    }

    Component.onCompleted: {
        for (var i = 0; i < page.libraryGames.length; ++i) {
            var game = page.libraryGames[i]
            page.favorites[game.title] = Boolean(appState.preference("favorite/" + game.title, false))
        }
        page.favoritesRevision += 1
    }

    onVisibleChanged: {
        if (visible)
            Qt.callLater(function() { grid.forceActiveFocus() })
        else
            sortOpen = false
    }

    Text {
        x: Theme.pageMargin
        y: 36
        text: "Library"
        color: Theme.ink
        font.family: Theme.displayFont.family
        font.pixelSize: 42
        font.weight: Font.DemiBold
    }

    Text {
        x: Theme.pageMargin + 142
        y: 55
        text: page.libraryGames.length + (page.libraryGames.length === 1 ? " title" : " titles")
        color: Theme.inkMuted
        font.family: Theme.monoFont.family
        font.pixelSize: 13
    }

    Row {
        id: filters
        x: Theme.pageMargin
        y: 104
        spacing: 10

        Repeater {
            model: page.filterNames
            Button {
                id: filterButton
                required property string modelData
                required property int index
                width: Math.max(54, filterLabel.implicitWidth + 36)
                height: 40
                focusPolicy: Qt.StrongFocus
                onClicked: {
                    page.filterIndex = index
                    grid.currentIndex = 0
                    grid.forceActiveFocus()
                }
                contentItem: Text {
                    id: filterLabel
                    text: filterButton.modelData
                    color: filterButton.index === page.filterIndex ? Theme.accentInk : Theme.inkSoft
                    font.family: Theme.bodyFont.family
                    font.pixelSize: 13
                    font.weight: Font.DemiBold
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                background: Rectangle {
                    radius: 20
                    color: filterButton.index === page.filterIndex ? Theme.accent : "transparent"
                    border.width: filterButton.activeFocus && filterButton.index !== page.filterIndex ? 2 : 1
                    border.color: filterButton.activeFocus ? Theme.accent : Theme.divider
                }
            }
        }
    }

    Button {
        id: sortButton
        anchors.right: parent.right
        anchors.rightMargin: Theme.pageMargin
        y: 42
        width: 196
        height: 42
        focusPolicy: Qt.StrongFocus
        onClicked: {
            page.pendingSortIndex = page.sortIndex
            page.sortOpen = !page.sortOpen
            if (page.sortOpen)
                Qt.callLater(function() { sortMenu.forceActiveFocus() })
        }
        contentItem: RowLayout {
            spacing: 9
            Text { text: "Sort"; color: Theme.inkMuted; font.pixelSize: 12 }
            Text { text: page.sortNames[page.sortIndex]; color: Theme.ink; font.pixelSize: 13; font.weight: Font.DemiBold; Layout.fillWidth: true }
            Text { text: page.sortOpen ? "⌃" : "⌄"; color: page.sortOpen ? Theme.accent : Theme.inkMuted; font.pixelSize: 15 }
        }
        background: Rectangle {
            radius: 9
            color: Theme.surfaceRaised
            border.width: sortButton.activeFocus || page.sortOpen ? 2 : 1
            border.color: sortButton.activeFocus || page.sortOpen ? Theme.accent : Theme.divider
        }
    }

    Row {
        anchors.right: parent.right
        anchors.rightMargin: Theme.pageMargin
        y: 108
        spacing: 8
        KeyHint { keyText: "LB"; label: ""; scale: 0.82 }
        KeyHint { keyText: "RB"; label: "Filter"; scale: 0.82 }
    }

    GridView {
        id: grid
        x: Theme.pageMargin
        y: 168
        width: parent.width - Theme.pageMargin * 2
        height: Math.max(cellHeight, Math.floor((parent.height - y - 112) / cellHeight) * cellHeight)
        clip: true
        model: page.visibleGames
        cellWidth: width / 5
        cellHeight: 250
        keyNavigationWraps: false
        focus: page.visible && !page.sortOpen
        onCountChanged: {
            if (count === 0)
                currentIndex = -1
            else if (currentIndex < 0 || currentIndex >= count)
                currentIndex = 0
        }
        Keys.onReturnPressed: {
            if (currentIndex >= 0)
                page.startGame(page.visibleGames[currentIndex])
        }
        Keys.onPressed: function(event) {
            if (currentIndex < 0)
                return
            if (event.key === Qt.Key_X) {
                page.openGame(page.visibleGames[currentIndex])
                event.accepted = true
            } else if (event.key === Qt.Key_Y) {
                page.toggleFavorite(page.visibleGames[currentIndex])
                event.accepted = true
            }
        }

        delegate: Item {
            required property var modelData
            required property int index
            width: grid.cellWidth
            height: grid.cellHeight
            GameCard {
                anchors.fill: parent
                anchors.rightMargin: index % 5 === 4 ? 0 : 20
                anchors.bottomMargin: 12
                title: modelData.title
                subtitle: modelData.subtitle || ((modelData.availableStores || []).join(" · "))
                badge: modelData.badge || ""
                variant: modelData.variant === undefined ? index % 6 : Number(modelData.variant)
                imageSource: modelData.imageUrl || ""
                game: modelData
                progress: modelData.progress === undefined ? 0 : Number(modelData.progress)
                selected: index === grid.currentIndex
                onClicked: {
                    grid.currentIndex = index
                    page.openGame(modelData)
                }
            }
            Text {
                visible: page.isFavorite(modelData)
                anchors.right: parent.right
                anchors.rightMargin: index % 5 === 4 ? 10 : 30
                anchors.top: parent.top
                anchors.topMargin: 10
                text: "★"
                color: Theme.accent
                font.pixelSize: 14
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
            text: page.visibleGames.length + (page.visibleGames.length === 1 ? " visible title" : " visible titles")
            color: "#455048"
            font.family: Theme.monoFont.family
            font.pixelSize: 11
            font.letterSpacing: 1.1
        }
        Row {
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            spacing: 22
            KeyHint { keyText: "A"; label: "Play" }
            KeyHint { keyText: "X"; label: "Details" }
            KeyHint { keyText: "Y"; label: "Favorite" }
        }
    }

    FocusScope {
        id: sortMenu
        visible: page.sortOpen
        z: 20
        anchors.right: sortButton.right
        anchors.top: sortButton.bottom
        anchors.topMargin: 8
        width: sortButton.width
        height: sortOptions.height + 12
        focus: visible
        Keys.onEscapePressed: {
            page.sortOpen = false
            sortButton.forceActiveFocus()
        }
        Keys.onDownPressed: page.pendingSortIndex = (page.pendingSortIndex + 1) % page.sortNames.length
        Keys.onUpPressed: page.pendingSortIndex = (page.pendingSortIndex + page.sortNames.length - 1) % page.sortNames.length
        Keys.onReturnPressed: page.selectSort(page.pendingSortIndex)

        Rectangle {
            anchors.fill: parent
            radius: 10
            color: Theme.surfaceRaised
            border.color: Theme.divider
        }
        Column {
            id: sortOptions
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.margins: 6
            spacing: 2
            Repeater {
                model: page.sortNames
                Button {
                    id: option
                    required property string modelData
                    required property int index
                    width: sortOptions.width
                    height: 42
                    onHoveredChanged: {
                        if (hovered)
                            page.pendingSortIndex = index
                    }
                    onClicked: page.selectSort(index)
                    contentItem: RowLayout {
                        anchors.leftMargin: 10
                        anchors.rightMargin: 10
                        Text { text: option.modelData; color: Theme.ink; font.pixelSize: 12; font.weight: Font.DemiBold; Layout.fillWidth: true }
                        Text { visible: option.index === page.sortIndex; text: "✓"; color: Theme.accent; font.pixelSize: 14 }
                    }
                    background: Rectangle {
                        radius: 6
                        color: option.index === page.sortIndex ? "#173120" : "transparent"
                        border.width: option.index === page.pendingSortIndex ? 1 : 0
                        border.color: Theme.accent
                    }
                }
            }
        }
    }
}
