import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root

    width: 1208
    height: 804
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

    readonly property var fallbackCatalog: [
        { title:"Helldivers II", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/03HVWW89V2FPMAP5G1DJ7TM1CG.jpg" },
        { title:"Elden Ring", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/0SVG3SN59REC38313F48EY0YSE.jpg" },
        { title:"Palworld", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/1PAQY26TGSP3BSWZ941DS22P0Z.jpg" },
        { title:"Satisfactory", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/230KEWXQY0V7X2PV75YSKMG0A2.jpg" },
        { title:"Diablo IV", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/2MY41CFE0NMVNWR28TFW8CVFJ3.jpg" },
        { title:"Marvel Rivals", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/7CKD11KP1QKR2J9MWTT588ZXPX.jpg" },
        { title:"Baldur's Gate 3", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/0KQRAJ13TA66P3QHJXS3BQ4YEM.jpg" },
        { title:"Doom Eternal", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/3MPF7BT46FVAMKRYXPCNW1E0QH.jpg" },
        { title:"Hogwarts Legacy", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/1CTYH6TE5K9SMM9KTTRP80P1M1.jpg" },
        { title:"Control", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/62SFJEAVHQ410MNWZCNPVDYT09.jpg" },
        { title:"The Witcher 3", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/2M5WFA3P0KMAJMS1YP35REP6PX.jpg" },
        { title:"Sea of Thieves", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/0B9HCRMKXGDM796X71PBBVH5CP.jpg" },
        { title:"Celeste", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/3WP7HBET5XXSSH82CTDX01A9PB.jpg" },
        { title:"Destiny 2", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/01KAKZH3C60FT0JRJF41ARWASW.jpg" },
        { title:"Dead Cells", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/3WB7N0FW4DXSDM88CJXYC9BNM3.jpg" },
        { title:"Hades II", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/18ZXXEK97VYSTZ0HZ2J4P87QKX.jpg" },
        { title:"Stardew Valley", imageUrl:"https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/5QYBWM86X6VPW3YYM1HJHMXS80.jpg" }
    ]

    readonly property var newDescriptors: [
        {title:"Helldivers II", storePrice:"€39.99", storeCategories:["new", "rtx"]},
        {title:"Elden Ring", storePrice:"€29.99", storeDiscount:"-50%", storeCategories:["rtx"]},
        {title:"Palworld", storePrice:"€26.99", storeCategories:["new"]},
        {title:"Satisfactory", storePrice:"€34.99", storeCategories:["new"]},
        {title:"Diablo IV", storePrice:"€41.99", storeDiscount:"-40%", storeCategories:["rtx"]},
        {title:"Marvel Rivals", storeFree:true, storeCategories:["free"]},
        {title:"Baldur's Gate 3", storeOwned:true},
        {title:"Doom Eternal", storePrice:"€11.99", storeDiscount:"-70%"}
    ]
    readonly property var popularDescriptors: [
        {title:"Hogwarts Legacy", storePrice:"€49.99"},
        {title:"Control", storePrice:"€19.99"},
        {title:"The Witcher 3", storePrice:"€9.99"},
        {title:"Sea of Thieves", storePrice:"€39.99"},
        {title:"Celeste", storePrice:"€8.99"},
        {title:"Destiny 2", storeFree:true, storeCategories:["free"]},
        {title:"Dead Cells", storePrice:"€24.99"},
        {title:"Hades II", storePrice:"€29.99", storeCategories:["coming"]}
    ]
    readonly property var heroDescriptors: [
        {title:"Dead Cells"}, {title:"Celeste"}, {title:"Stardew Valley"}, {title:"Sea of Thieves"}
    ]
    readonly property var heroGames: gamesFor(heroDescriptors, false)
    readonly property var newGames: gamesFor(newDescriptors, true)
    readonly property var popularGames: gamesFor(popularDescriptors, true)
    readonly property var categories: [
        {id:"featured", label:qsTr("Featured")},
        {id:"new", label:qsTr("New releases")},
        {id:"free", label:qsTr("Free to play")},
        {id:"rtx", label:qsTr("RTX")},
        {id:"coming", label:qsTr("Coming soon")}
    ]
    readonly property var genreOptions: optionsFor("genre")
    readonly property var storeOptions: optionsFor("store")
    readonly property var currentMenuOptions: openMenu === "genre" ? genreOptions : storeOptions

    function fallbackByTitle(title) {
        const key = String(title || "").toLowerCase()
        for (let index = 0; index < fallbackCatalog.length; ++index) {
            if (String(fallbackCatalog[index].title).toLowerCase() === key)
                return fallbackCatalog[index]
        }
        return {title:title, imageUrl:""}
    }

    function catalogByTitle(title) {
        const key = String(title || "").toLowerCase()
        for (let index = 0; index < catalogGames.length; ++index) {
            const candidate = catalogGames[index]
            const candidateTitle = String(candidate && candidate.title || "").toLowerCase()
            if (candidateTitle === key || candidateTitle.indexOf(key) >= 0 || key.indexOf(candidateTitle) >= 0)
                return candidate
        }
        return null
    }

    function decorate(descriptor) {
        const fallback = fallbackByTitle(descriptor.title)
        const catalog = catalogByTitle(descriptor.title)
        return Object.assign({}, fallback, catalog || {}, descriptor)
    }

    function gamesFor(descriptors, applyFilters) {
        const result = []
        for (let index = 0; index < descriptors.length; ++index) {
            const game = decorate(descriptors[index])
            if (!applyFilters || matchesFilters(game))
                result.push(game)
        }

        if (applyFilters && filtersAreActive() && result.length < 8) {
            for (let index = 0; index < catalogGames.length && result.length < 8; ++index) {
                const candidate = catalogGames[index]
                if (!matchesFilters(candidate) || containsIdentity(result, candidate))
                    continue
                result.push(Object.assign({}, candidate, {
                    storePrice: qsTr("Available"),
                    storeOwned: isOwned(candidate)
                }))
            }
        }
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

    function isOwned(game) {
        const variants = game && game.variants || []
        for (let index = 0; index < variants.length; ++index) {
            if (Boolean(variants[index].inLibrary))
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

    function matchesFilters(game) {
        const query = searchText.trim().toLowerCase()
        if (query.length && String(game && game.title || "").toLowerCase().indexOf(query) < 0)
            return false
        if (activeGenre !== qsTr("All")) {
            const genres = game && game.genres || []
            let genreFound = false
            for (let index = 0; index < genres.length; ++index)
                genreFound = genreFound || String(genres[index]).toLowerCase() === activeGenre.toLowerCase()
            if (!genreFound)
                return false
        }
        if (activeStore !== qsTr("All")) {
            const stores = storesFor(game)
            let storeFound = false
            for (let index = 0; index < stores.length; ++index)
                storeFound = storeFound || stores[index].toLowerCase() === activeStore.toLowerCase()
            if (!storeFound)
                return false
        }
        if (activeCategoryId === "free" && !Boolean(game && game.storeFree))
            return false
        if (activeCategoryId !== "featured" && activeCategoryId !== "free") {
            const tags = game && game.storeCategories || []
            if (tags.length > 0 && tags.indexOf(activeCategoryId) < 0)
                return false
        }
        return true
    }

    function filtersAreActive() {
        return searchText.trim().length > 0 || activeCategoryId !== "featured"
                || activeGenre !== qsTr("All") || activeStore !== qsTr("All")
    }

    function optionsFor(kind) {
        const values = [qsTr("All")]
        for (let gameIndex = 0; gameIndex < catalogGames.length; ++gameIndex) {
            const game = catalogGames[gameIndex]
            const candidates = kind === "genre" ? (game.genres || []) : storesFor(game)
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
            if (focusIndex === 0) claimRequested(heroGames)
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
        contentHeight: 882
        clip: true
        boundsBehavior: Flickable.StopAtBounds
        flickableDirection: Flickable.VerticalFlick

        DesktopStoreHero {
            id: hero
            x: 24
            y: 18
            games: root.heroGames
            selectedAction: root.focusZone === "hero" ? root.focusIndex : -1
            onActionPointed: index => root.selectZone("hero", index)
            onClaimRequested: root.claimRequested(root.heroGames)
            onIncludedRequested: if (root.heroGames.length) root.gameSelected(root.heroGames[0])
        }

        Row {
            id: chips
            x: 24
            y: 258
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
            text: qsTr("PRICES FROM LINKED STORES")
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
            title: qsTr("New this week")
            seeAllText: qsTr("See all 24")
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
            y: 601
            title: qsTr("Popular in the cloud")
            eyebrow: qsTr("LAST 7 DAYS")
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
        x: root.openMenu === "genre" ? 481 : 574
        y: 294 - content.contentY
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
            text: qsTr("No games match these filters")
            color: "#FFFFFF"
            font.family: Theme.displayFont
            font.pixelSize: 22
            font.weight: Font.Black
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: qsTr("Try another category, genre, store, or search.")
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

    Component.onCompleted: forceActiveFocus()
}
