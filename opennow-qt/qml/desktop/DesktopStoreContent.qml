import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root

    anchors.fill: parent
    focus: true
    clip: true

    property var catalogGames: ShellStore.catalogGames
    property string searchText: ""
    property string activeCategory: qsTr("Featured")
    property string activeCategoryId: "featured"
    property string activeGenre: qsTr("All")
    property string activeStore: qsTr("All")
    property string focusZone: "hero"
    property int focusIndex: 0
    property string openMenu: ""
    property int menuIndex: 0

    signal gameSelected(var game)
    signal routeRequested(string route)
    signal claimRequested(var games)
    signal searchRequested()
    signal messageRequested(string message)

    readonly property int railInnerWidth: Math.max(200, content.width - 48)
    readonly property int railCount: Math.max(1, Math.floor((root.railInnerWidth + 14) / (132 + 14)))
    readonly property var filteredCatalog: root.buildFiltered(root.catalogGames, root.searchText, root.activeGenre, root.activeStore, root.activeCategoryId)
    readonly property var heroGames: root.buildHero(root.filteredCatalog)
    readonly property var newGames: root.buildNew(root.filteredCatalog, root.heroGames)
    readonly property var popularGames: root.buildPopular(root.filteredCatalog, root.heroGames, root.newGames)
    readonly property var categories: root.buildCategories(root.catalogGames)
    readonly property var genreOptions: root.optionsFor("genre", root.catalogGames)
    readonly property var storeOptions: root.optionsFor("store", root.catalogGames)
    readonly property var currentMenuOptions: openMenu === "genre" ? genreOptions : storeOptions
    readonly property int catalogCount: Number(ShellStore.catalogTotalCount || (root.catalogGames || []).length)
    readonly property string catalogState: ShellStore.catalogState
    readonly property string catalogError: ShellStore.lastError

    function catalogList() {
        return root.catalogGames || []
    }

    function isOwned(game) {
        if (Boolean(game && (game.storeOwned || game.isInLibrary)))
            return true
        const variants = game && game.variants || []
        for (let index = 0; index < variants.length; ++index) {
            if (Boolean(variants[index].inLibrary))
                return true
        }
        return false
    }

    function looksFree(game) {
        return Boolean(game && (game.storeFree === true || game.freeToPlay === true || game.isFree === true))
    }

    function catalogHasFreeFlag(games) {
        const source = games || []
        for (let index = 0; index < source.length; ++index) {
            if (root.looksFree(source[index]))
                return true
        }
        return false
    }

    function isRtx(game) {
        const tags = game && game.storeCategories || []
        for (let index = 0; index < tags.length; ++index) {
            if (String(tags[index]).toLowerCase() === "rtx")
                return true
        }
        const title = String(game && game.title || "").toLowerCase()
        if (title.indexOf("rtx") >= 0)
            return true
        const genres = game && game.genres || []
        for (let index = 0; index < genres.length; ++index) {
            if (String(genres[index]).toLowerCase().indexOf("rtx") >= 0)
                return true
        }
        const tech = game && game.nvidiaTech || []
        for (let index = 0; index < tech.length; ++index) {
            if (String(tech[index]).toLowerCase().indexOf("rtx") >= 0)
                return true
        }
        return false
    }

    function looksNew(game) {
        const tags = game && game.storeCategories || []
        for (let index = 0; index < tags.length; ++index) {
            if (String(tags[index]).toLowerCase() === "new")
                return true
        }
        return String(game && game.lastPlayed || "").length === 0
    }

    function looksComing(game) {
        const tags = game && game.storeCategories || []
        for (let index = 0; index < tags.length; ++index) {
            if (String(tags[index]).toLowerCase() === "coming")
                return true
        }
        const state = String(game && game.playabilityState || "").toLowerCase()
        return state.indexOf("coming") >= 0 || state.indexOf("soon") >= 0 || state.indexOf("unreleased") >= 0
    }

    function catalogPrice(game) {
        if (game && game.storePrice !== undefined && game.storePrice !== null && String(game.storePrice).length)
            return String(game.storePrice)
        if (game && game.price !== undefined && game.price !== null && String(game.price).length)
            return String(game.price)
        return qsTr("Available")
    }

    function catalogDiscount(game) {
        if (game && game.storeDiscount !== undefined && game.storeDiscount !== null)
            return String(game.storeDiscount)
        if (game && game.discount !== undefined && game.discount !== null)
            return String(game.discount)
        return ""
    }

    function categoriesFor(game) {
        const values = []
        const tags = game && game.storeCategories || []
        for (let index = 0; index < tags.length; ++index)
            values.push(String(tags[index]))
        if (root.isRtx(game) && values.indexOf("rtx") < 0)
            values.push("rtx")
        if (root.looksFree(game) && values.indexOf("free") < 0)
            values.push("free")
        if (root.looksNew(game) && values.indexOf("new") < 0)
            values.push("new")
        if (root.looksComing(game) && values.indexOf("coming") < 0)
            values.push("coming")
        return values
    }

    function decorate(game) {
        if (!game)
            return game
        return Object.assign({}, game, {
            storePrice: root.catalogPrice(game),
            storeDiscount: root.catalogDiscount(game),
            storeFree: root.looksFree(game),
            storeOwned: root.isOwned(game),
            storeCategories: root.categoriesFor(game)
        })
    }

    function decorateList(games) {
        const result = []
        const source = games || []
        for (let index = 0; index < source.length; ++index)
            result.push(root.decorate(source[index]))
        return result
    }

    function containsIdentity(games, candidate) {
        const identity = String(candidate && (candidate.uuid || candidate.id || candidate.title) || "")
        for (let index = 0; index < games.length; ++index) {
            if (String(games[index] && (games[index].uuid || games[index].id || games[index].title) || "") === identity)
                return true
        }
        return false
    }

    function storesFor(game) {
        const values = []
        const stores = game && game.availableStores || []
        for (let index = 0; index < stores.length; ++index)
            values.push(String(stores[index]))
        const variants = game && game.variants || []
        for (let index = 0; index < variants.length; ++index) {
            const store = String(variants[index].store || "")
            if (store.length && values.indexOf(store) < 0)
                values.push(store)
        }
        return values
    }

    function matchesSearchAndFacets(game, query, genre, store) {
        if (query.length && String(game && game.title || "").toLowerCase().indexOf(query) < 0)
            return false
        if (genre !== qsTr("All")) {
            const genres = game && game.genres || []
            let genreFound = false
            for (let index = 0; index < genres.length; ++index)
                genreFound = genreFound || String(genres[index]).toLowerCase() === genre.toLowerCase()
            if (!genreFound)
                return false
        }
        if (store !== qsTr("All")) {
            const stores = root.storesFor(game)
            let storeFound = false
            for (let index = 0; index < stores.length; ++index)
                storeFound = storeFound || stores[index].toLowerCase() === store.toLowerCase()
            if (!storeFound)
                return false
        }
        return true
    }

    function matchesCategory(game, categoryId) {
        if (categoryId === "featured")
            return true
        if (categoryId === "rtx")
            return root.isRtx(game)
        if (categoryId === "free")
            return root.looksFree(game)
        if (categoryId === "new")
            return root.looksNew(game)
        if (categoryId === "coming")
            return root.looksComing(game)
        return true
    }

    function buildFiltered(games, searchText, genre, store, categoryId) {
        const query = String(searchText || "").trim().toLowerCase()
        const source = games || []
        const result = []
        for (let index = 0; index < source.length; ++index) {
            const game = source[index]
            if (!root.matchesSearchAndFacets(game, query, genre, store))
                continue
            if (!root.matchesCategory(game, categoryId))
                continue
            result.push(game)
        }
        return result
    }

    function takeSlice(source, start, count) {
        const result = []
        const list = source || []
        for (let index = start; index < list.length && result.length < count; ++index)
            result.push(list[index])
        return result
    }

    function takeWithout(source, excluded, count) {
        const result = []
        const list = source || []
        for (let index = 0; index < list.length && result.length < count; ++index) {
            if (root.containsIdentity(excluded, list[index]))
                continue
            result.push(list[index])
        }
        return result
    }

    function buildHero(filtered) {
        return root.decorateList(root.takeSlice(filtered, 0, 4))
    }

    function buildNew(filtered, heroGames) {
        const unplayed = []
        const list = filtered || []
        for (let index = 0; index < list.length; ++index) {
            if (String(list[index] && list[index].lastPlayed || "").length === 0)
                unplayed.push(list[index])
        }
        const lastAdded = root.takeSlice(list, Math.max(0, list.length - root.railCount), root.railCount)
        const pool = unplayed.length ? unplayed : lastAdded
        let result = root.takeWithout(pool, heroGames, root.railCount)
        if (!result.length)
            result = root.takeSlice(list, 4, root.railCount)
        if (!result.length)
            result = root.takeSlice(list, 0, root.railCount)
        return root.decorateList(result)
    }

    function buildPopular(filtered, heroGames, newGames) {
        const excluded = (heroGames || []).concat(newGames || [])
        const played = []
        const list = filtered || []
        for (let index = 0; index < list.length; ++index) {
            if (String(list[index] && list[index].lastPlayed || "").length > 0)
                played.push(list[index])
        }
        let result = root.takeWithout(played.length ? played : list, excluded, root.railCount)
        if (!result.length)
            result = root.takeSlice(list, 4 + root.railCount, root.railCount)
        if (!result.length)
            result = root.takeWithout(list, excluded, root.railCount)
        if (!result.length)
            result = root.takeSlice(list, 0, root.railCount)
        return root.decorateList(result)
    }

    function buildCategories(games) {
        const list = [
            {id: "featured", label: qsTr("Featured")},
            {id: "new", label: qsTr("New releases")}
        ]
        if (root.catalogHasFreeFlag(games))
            list.push({id: "free", label: qsTr("Free to play")})
        list.push({id: "rtx", label: qsTr("RTX")})
        list.push({id: "coming", label: qsTr("Coming soon")})
        return list
    }

    function optionsFor(kind, games) {
        const values = [qsTr("All")]
        const source = games || []
        for (let gameIndex = 0; gameIndex < source.length; ++gameIndex) {
            const game = source[gameIndex]
            const candidates = kind === "genre" ? (game.genres || []) : root.storesFor(game)
            for (let index = 0; index < candidates.length; ++index) {
                const value = String(candidates[index]).trim()
                if (value.length && values.indexOf(value) < 0)
                    values.push(value)
            }
        }
        return values.slice(0, 7)
    }

    function selectZone(zone, index) {
        focusZone = zone
        focusIndex = Math.max(0, index)
        forceActiveFocus()
        if (zone === "popular")
            scrollTo(88)
        else if (zone === "hero" || zone === "chips")
            scrollTo(0)
    }

    function scrollTo(value) {
        scrollAnimation.stop()
        scrollAnimation.from = content.contentY
        scrollAnimation.to = Math.max(0, Math.min(value, content.contentHeight - content.height))
        scrollAnimation.duration = AppController.reducedMotion ? 0 : 180
        scrollAnimation.start()
    }

    function activateCurrent() {
        if (focusZone === "hero") {
            if (focusIndex === 0 && heroGames.length) gameSelected(heroGames[0])
            else if (heroGames.length) gameSelected(heroGames[0])
            return
        }
        if (focusZone === "chips") {
            if (focusIndex < categories.length) {
                activeCategoryId = categories[focusIndex].id
                activeCategory = categories[focusIndex].label
            }
            else {
                openMenu = focusIndex === categories.length ? (openMenu === "genre" ? "" : "genre")
                                                            : (openMenu === "store" ? "" : "store")
                menuIndex = 0
            }
            return
        }
        const games = focusZone === "new" ? newGames : popularGames
        if (games.length)
            gameSelected(games[Math.min(focusIndex, games.length - 1)])
    }

    function syncFreeCategory() {
        if (root.activeCategoryId === "free" && !root.catalogHasFreeFlag(root.catalogGames)) {
            root.activeCategoryId = "featured"
            root.activeCategory = qsTr("Featured")
        }
    }

    Keys.onPressed: event => {
        if (root.openMenu.length && (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space)) {
            const option = root.currentMenuOptions[Math.min(root.menuIndex, Math.max(0, root.currentMenuOptions.length - 1))]
            if (root.openMenu === "genre") root.activeGenre = option
            else root.activeStore = option
            root.openMenu = ""
        } else if (root.openMenu.length && event.key === Qt.Key_Up) {
            root.menuIndex = Math.max(0, root.menuIndex - 1)
        } else if (root.openMenu.length && event.key === Qt.Key_Down) {
            root.menuIndex = Math.min(root.currentMenuOptions.length - 1, root.menuIndex + 1)
        } else if (event.key === Qt.Key_Slash) {
            root.searchRequested()
        } else if (event.key === Qt.Key_Escape && root.openMenu.length) {
            root.openMenu = ""
        } else if (event.key === Qt.Key_Left) {
            root.focusIndex = Math.max(0, root.focusIndex - 1)
        } else if (event.key === Qt.Key_Right) {
            const count = root.focusZone === "hero" ? 2
                        : root.focusZone === "chips" ? root.categories.length + 2
                        : root.focusZone === "new" ? root.newGames.length
                        : root.popularGames.length
            root.focusIndex = Math.min(Math.max(0, count - 1), root.focusIndex + 1)
        } else if (event.key === Qt.Key_Up) {
            if (root.focusZone === "popular") root.selectZone("new", root.focusIndex)
            else if (root.focusZone === "new") root.selectZone("chips", Math.min(root.focusIndex, root.categories.length + 1))
            else if (root.focusZone === "chips") root.selectZone("hero", Math.min(root.focusIndex, 1))
            else return
        } else if (event.key === Qt.Key_Down) {
            if (root.focusZone === "hero") root.selectZone("chips", 0)
            else if (root.focusZone === "chips") root.selectZone("new", Math.min(root.focusIndex, Math.max(0, root.newGames.length - 1)))
            else if (root.focusZone === "new") root.selectZone("popular", Math.min(root.focusIndex, Math.max(0, root.popularGames.length - 1)))
            else return
        } else if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
            root.activateCurrent()
        } else {
            return
        }
        event.accepted = true
    }

    Flickable {
        id: content
        anchors.fill: parent
        contentWidth: width
        contentHeight: Math.max(height, popularShelf.y + popularShelf.height + 24)
        clip: true
        boundsBehavior: Flickable.StopAtBounds
        flickableDirection: Flickable.VerticalFlick

        DesktopStoreHero {
            id: hero
            x: 24
            y: 18
            width: root.railInnerWidth
            games: root.heroGames
            selectedAction: root.focusZone === "hero" ? root.focusIndex : -1
            onActionPointed: index => root.selectZone("hero", index)
            onClaimRequested: if (root.heroGames.length) root.gameSelected(root.heroGames[0])
            onIncludedRequested: if (root.heroGames.length) root.gameSelected(root.heroGames[0])
        }

        Row {
            id: chips
            x: 24
            y: 258
            width: root.railInnerWidth
            height: 30
            spacing: 8

            Repeater {
                model: root.categories
                DesktopStoreChip {
                    required property int index
                    required property var modelData
                    text: modelData.label
                    selected: root.activeCategoryId === modelData.id
                              || (root.focusZone === "chips" && root.focusIndex === index)
                    onHoveredChanged: if (hovered) root.selectZone("chips", index)
                    onClicked: {
                        root.activeCategoryId = modelData.id
                        root.activeCategory = modelData.label
                        root.openMenu = ""
                    }
                }
            }

            Rectangle {
                anchors.verticalCenter: parent.verticalCenter
                width: 1
                height: 20
                color: Qt.rgba(1, 1, 1, 0.09)
            }

            DesktopStoreChip {
                id: genreChip
                text: root.activeGenre === qsTr("All") ? qsTr("Genres") : root.activeGenre
                hasMenu: true
                selected: root.openMenu === "genre"
                          || (root.focusZone === "chips" && root.focusIndex === root.categories.length)
                onHoveredChanged: if (hovered) root.selectZone("chips", root.categories.length)
                onClicked: {
                    root.openMenu = root.openMenu === "genre" ? "" : "genre"
                    root.menuIndex = 0
                }
            }

            DesktopStoreChip {
                id: storeChip
                text: root.activeStore === qsTr("All") ? qsTr("Stores") : root.activeStore
                hasMenu: true
                selected: root.openMenu === "store"
                          || (root.focusZone === "chips" && root.focusIndex === root.categories.length + 1)
                onHoveredChanged: if (hovered) root.selectZone("chips", root.categories.length + 1)
                onClicked: {
                    root.openMenu = root.openMenu === "store" ? "" : "store"
                    root.menuIndex = 0
                }
            }
        }

        Text {
            anchors.right: parent.right
            anchors.rightMargin: 24
            y: 267
            text: root.catalogCount > 0 ? qsTr("%1 games in catalog").arg(root.catalogCount)
                                        : (root.catalogState === "error" ? qsTr("Catalog unavailable")
                                                                        : qsTr("Live catalog"))
            color: Qt.rgba(1, 1, 1, 0.32)
            font.family: Theme.monoFont
            font.pixelSize: 10
            font.weight: Font.DemiBold
            font.letterSpacing: 0.4
        }

        DesktopStoreShelf {
            id: newShelf
            x: 24
            y: 304
            width: root.railInnerWidth
            title: qsTr("Recently added")
            seeAllText: root.filteredCatalog.length ? qsTr("See all %1").arg(root.filteredCatalog.length) : qsTr("See all")
            games: root.newGames
            active: root.focusZone === "new"
            selectedIndex: root.focusIndex
            onGamePointed: index => root.selectZone("new", index)
            onGameActivated: game => root.gameSelected(game)
            onSeeAllRequested: root.routeRequested("library")
        }

        DesktopStoreShelf {
            id: popularShelf
            x: 24
            y: newShelf.y + newShelf.height + 16
            width: root.railInnerWidth
            title: qsTr("More from the catalog")
            games: root.popularGames
            active: root.focusZone === "popular"
            selectedIndex: root.focusIndex
            onGamePointed: index => root.selectZone("popular", index)
            onGameActivated: game => root.gameSelected(game)
            onSeeAllRequested: root.routeRequested("library")
        }
    }

    Rectangle {
        id: filterMenu
        z: 400
        visible: true
        enabled: root.openMenu.length > 0
        x: chips.x + (root.openMenu === "genre" ? genreChip.x : storeChip.x)
        y: chips.y + chips.height + 6 - content.contentY
        width: 178
        height: menuColumn.height + 12
        radius: 11
        color: Qt.rgba(0.043, 0.059, 0.102, 0.98)
        border.width: 1
        border.color: Qt.rgba(1, 1, 1, 0.18)
        opacity: enabled ? 1 : 0
        scale: enabled ? 1 : 0.96
        transformOrigin: Item.TopLeft

        Behavior on opacity { NumberAnimation { duration: Theme.overlayDuration } }
        Behavior on scale { NumberAnimation { duration: Theme.overlayDuration; easing.type: Easing.OutCubic } }

        Column {
            id: menuColumn
            x: 6
            y: 6
            width: parent.width - 12

            Repeater {
                model: root.openMenu === "genre" ? root.genreOptions : root.storeOptions

                Button {
                    id: menuButton
                    required property string modelData
                    required property int index
                    width: menuColumn.width
                    height: 32
                    padding: 0
                    focusPolicy: Qt.NoFocus
                    hoverEnabled: true
                    background: Rectangle {
                        radius: 7
                        color: menuButton.hovered || root.menuIndex === menuButton.index
                               ? Qt.rgba(1, 1, 1, 0.10) : "transparent"
                    }
                    contentItem: Text {
                        leftPadding: 9
                        text: modelData
                        color: "#FFFFFF"
                        font.family: Theme.bodyFont
                        font.pixelSize: 12
                        font.weight: Font.DemiBold
                        verticalAlignment: Text.AlignVCenter
                        elide: Text.ElideRight
                    }
                    onClicked: {
                        if (root.openMenu === "genre") root.activeGenre = modelData
                        else root.activeStore = modelData
                        root.openMenu = ""
                        root.forceActiveFocus()
                    }
                    onHoveredChanged: if (hovered) root.menuIndex = index
                }
            }
        }
    }

    Column {
        anchors.centerIn: parent
        spacing: 9
        visible: root.newGames.length === 0 && root.popularGames.length === 0
        z: 200

        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: root.catalogList().length === 0
                  ? (root.catalogState === "error" ? qsTr("Catalog unavailable") : qsTr("Loading the live catalog…"))
                  : qsTr("No games match these filters")
            color: "#FFFFFF"
            font.family: Theme.displayFont
            font.pixelSize: 22
            font.weight: Font.Black
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: root.catalogList().length === 0
                  ? (root.catalogState === "error" ? (root.catalogError || qsTr("Try again in a moment.")) : qsTr("NVIDIA’s public list is loading."))
                  : qsTr("Try another category, genre, store, or search.")
            color: Qt.rgba(1, 1, 1, 0.58)
            font.family: Theme.bodyFont
            font.pixelSize: 13
        }
    }

    NumberAnimation {
        id: scrollAnimation
        target: content
        property: "contentY"
        easing.type: Easing.OutCubic
    }

    onCatalogGamesChanged: root.syncFreeCategory()
    Component.onCompleted: {
        root.syncFreeCategory()
        root.forceActiveFocus()
    }
}
