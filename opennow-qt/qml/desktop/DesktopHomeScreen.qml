pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Effects
import OpenNOW

FocusScope {
    id: root

    readonly property string pageTitle: qsTr("Home")
    readonly property string pageSubtitle: qsTr("3 friends online")
    property int focusZone: 0
    property int focusIndex: 0
    property bool active: true

    signal routeRequested(string route)
    signal gameRequested(var game)

    width: 1208
    height: 804
    focus: active
    clip: true
    Accessible.role: Accessible.Pane
    Accessible.name: pageTitle

    readonly property var fallbackGames: [
        { id: "desktop-cyberpunk", title: "Cyberpunk 2077", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/62SFJEAVHQ410MNWZCNPVDYT09.jpg", heroImageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/27G0GQ44XWDM79Z64SSA5Z89F6.jpg", availableStores: ["STEAM"] },
        { id: "desktop-elden-ring", title: "Elden Ring", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/0SVG3SN59REC38313F48EY0YSE.jpg", availableStores: ["STEAM"] },
        { id: "desktop-control", title: "Control Ultimate Edition", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/62SFJEAVHQ410MNWZCNPVDYT09.jpg", availableStores: ["STEAM"] },
        { id: "desktop-helldivers", title: "HELLDIVERS 2", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/03HVWW89V2FPMAP5G1DJ7TM1CG.jpg", availableStores: ["STEAM"] },
        { id: "desktop-baldurs-gate", title: "Baldur's Gate 3", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/0KQRAJ13TA66P3QHJXS3BQ4YEM.jpg", availableStores: ["STEAM"] },
        { id: "desktop-doom-eternal", title: "DOOM Eternal", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/3MPF7BT46FVAMKRYXPCNW1E0QH.jpg", availableStores: ["STEAM"] },
        { id: "desktop-sea-of-thieves", title: "Sea of Thieves", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/0B9HCRMKXGDM796X71PBBVH5CP.jpg", availableStores: ["STEAM"] },
        { id: "desktop-dead-cells", title: "Dead Cells", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/3WB7N0FW4DXSDM88CJXYC9BNM3.jpg", availableStores: ["STEAM"] },
        { id: "desktop-destiny", title: "Destiny 2", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/01KAKZH3C60FT0JRJF41ARWASW.jpg", availableStores: ["STEAM"] },
        { id: "desktop-hogwarts", title: "Hogwarts Legacy", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/1CTYH6TE5K9SMM9KTTRP80P1M1.jpg", availableStores: ["STEAM"] },
        { id: "desktop-satisfactory", title: "Satisfactory", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/230KEWXQY0V7X2PV75YSKMG0A2.jpg", availableStores: ["STEAM"] },
        { id: "desktop-marvel-rivals", title: "Marvel Rivals", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/7CKD11KP1QKR2J9MWTT588ZXPX.jpg", availableStores: ["STEAM"] },
        { id: "desktop-palworld", title: "Palworld", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/1PAQY26TGSP3BSWZ941DS22P0Z.jpg", availableStores: ["STEAM"] },
        { id: "desktop-diablo", title: "Diablo IV", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/2MY41CFE0NMVNWR28TFW8CVFJ3.jpg", availableStores: ["BATTLE.NET"] },
        { id: "desktop-terraria", title: "Terraria", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/5F1KTMMEZ8W4V5ED1XY9HV12CY.jpg", availableStores: ["STEAM"] },
        { id: "desktop-hollow-knight", title: "Hollow Knight", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/5VY2YWMSRW97346Y25TAQT9CCE.jpg", availableStores: ["STEAM"] },
        { id: "desktop-celeste", title: "Celeste", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/3WP7HBET5XXSSH82CTDX01A9PB.jpg", availableStores: ["STEAM"] },
        { id: "desktop-stardew", title: "Stardew Valley", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/5QYBWM86X6VPW3YYM1HJHMXS80.jpg", availableStores: ["STEAM"] },
        { id: "desktop-new-2", title: "No Man's Sky", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/040Q987G519SZNRQAA3FC1J1JQ.jpg", availableStores: ["STEAM"] },
        { id: "desktop-new-3", title: "The Witcher 3", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/2M5WFA3P0KMAJMS1YP35REP6PX.jpg", availableStores: ["STEAM"] },
        { id: "desktop-new-4", title: "Warframe", imageUrl: "https://app.paper.design/file-assets/01M11SPTRPMYQB9S9AX948A6WH/18ZXXEK97VYSTZ0HZ2J4P87QKX.jpg", availableStores: ["STEAM"] }
    ]

    readonly property var games: ShellStore.catalogGames && ShellStore.catalogGames.length > 0
        ? ShellStore.catalogGames : fallbackGames
    readonly property var heroGame: findGame("Cyberpunk 2077", 0)
    readonly property var jumpGames: preferredGames([
        "Elden Ring", "Control", "HELLDIVERS 2", "Baldur's Gate 3", "DOOM Eternal",
        "Sea of Thieves", "Dead Cells", "Destiny 2", "Hogwarts Legacy"
    ], 1)
    readonly property var friendsGames: preferredGames([
        "HELLDIVERS 2", "Elden Ring", "Satisfactory", "Marvel Rivals", "Palworld",
        "Diablo IV", "Terraria", "Hollow Knight", "Celeste"
    ], 3)
    readonly property var newGames: preferredGames([
        "Stardew Valley", "No Man's Sky", "The Witcher 3", "Warframe", "Sea of Thieves",
        "DOOM", "Baldur's Gate 3", "Dead Cells", "Hogwarts Legacy"
    ], 17)
    readonly property var friends: [
        { initial: "M", name: "Mika", status: "In Elden Ring · 1 h", action: "Join" },
        { initial: "S", name: "Sam", status: "In party · idle", action: "Invite" },
        { initial: "N", name: "Nova", status: "In Helldivers II · 22 m", action: "Join" }
    ]

    function findGame(title, fallbackIndex) {
        const wanted = String(title || "").toLocaleLowerCase()
        for (let index = 0; index < root.games.length; ++index) {
            const candidate = root.games[index]
            const candidateTitle = String(candidate.title || "").toLocaleLowerCase()
            if (candidateTitle === wanted || candidateTitle.indexOf(wanted) >= 0)
                return candidate
        }
        return root.games.length > 0 ? root.games[fallbackIndex % root.games.length] : null
    }

    function preferredGames(titles, fallbackOffset) {
        const result = []
        const identities = []
        for (let titleIndex = 0; titleIndex < titles.length; ++titleIndex) {
            const game = root.findGame(titles[titleIndex], fallbackOffset + titleIndex)
            if (!game)
                continue
            const identity = ShellStore.gameIdentity(game) || String(game.title || titleIndex)
            if (identities.indexOf(identity) >= 0) {
                const replacement = root.games[(fallbackOffset + titleIndex) % root.games.length]
                const replacementIdentity = ShellStore.gameIdentity(replacement) || String(replacement.title || titleIndex)
                if (identities.indexOf(replacementIdentity) < 0) {
                    result.push(replacement)
                    identities.push(replacementIdentity)
                }
            } else {
                result.push(game)
                identities.push(identity)
            }
        }
        let scanIndex = 0
        while (result.length < 9 && result.length < root.games.length && scanIndex < root.games.length) {
            const candidate = root.games[(fallbackOffset + scanIndex) % root.games.length]
            const identity = ShellStore.gameIdentity(candidate) || String(candidate.title || result.length)
            if (identities.indexOf(identity) < 0) {
                result.push(candidate)
                identities.push(identity)
            }
            ++scanIndex
        }
        return result
    }

    function zoneGames(zone) {
        if (zone === 1) return root.jumpGames
        if (zone === 2) return root.friendsGames
        return root.newGames
    }

    function setSelection(zone, index) {
        root.focusZone = Math.max(0, Math.min(3, zone))
        const count = root.focusZone === 0 ? 6 : root.zoneGames(root.focusZone).length
        root.focusIndex = Math.max(0, Math.min(Math.max(0, count - 1), index))
        root.ensureSelectionVisible()
    }

    function ensureSelectionVisible() {
        if (root.focusZone <= 1) {
            if (contentFlick.contentY > 32)
                contentFlick.contentY = 0
            return
        }
        const zoneTop = root.focusZone === 2 ? 510 : 724
        const zoneBottom = zoneTop + 198
        if (zoneBottom > contentFlick.contentY + contentFlick.height - 12)
            contentFlick.contentY = Math.min(contentFlick.contentHeight - contentFlick.height,
                                             zoneBottom - contentFlick.height + 12)
        else if (zoneTop < contentFlick.contentY + 12)
            contentFlick.contentY = Math.max(0, zoneTop - 12)
    }

    function moveHorizontal(delta) {
        const count = root.focusZone === 0 ? 6 : root.zoneGames(root.focusZone).length
        if (count <= 0)
            return
        root.setSelection(root.focusZone, Math.max(0, Math.min(count - 1, root.focusIndex + delta)))
    }

    function moveVertical(delta) {
        const nextZone = Math.max(0, Math.min(3, root.focusZone + delta))
        if (nextZone === root.focusZone)
            return
        let nextIndex = root.focusIndex
        if (nextZone === 0)
            nextIndex = Math.min(1, Math.round(root.focusIndex / 4))
        else if (root.focusZone === 0)
            nextIndex = Math.min(8, root.focusIndex * 2)
        root.setSelection(nextZone, nextIndex)
    }

    function openGame(game) {
        if (!game)
            return
        root.gameRequested(game)
        ShellStore.openGame(game)
    }

    function resumeHero() {
        if (!root.heroGame)
            return
        ShellStore.selectedGame = root.heroGame
        if (ShellStore.signedIn)
            ShellStore.launchSelectedGame(false)
        else
            AppController.navigate("sign-in")
    }

    function openFriends() {
        AppController.showOverlay("friends")
    }

    function activateSelection() {
        if (root.focusZone === 0) {
            if (root.focusIndex === 0)
                root.resumeHero()
            else if (root.focusIndex === 1)
                root.openGame(root.heroGame)
            else
                root.openFriends()
            return
        }
        const selectedGames = root.zoneGames(root.focusZone)
        if (selectedGames.length > root.focusIndex)
            root.openGame(selectedGames[root.focusIndex])
    }

    Keys.onPressed: event => {
        if (event.key === Qt.Key_Left) {
            root.moveHorizontal(-1)
        } else if (event.key === Qt.Key_Right) {
            root.moveHorizontal(1)
        } else if (event.key === Qt.Key_Up) {
            root.moveVertical(-1)
        } else if (event.key === Qt.Key_Down) {
            root.moveVertical(1)
        } else if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
            root.activateSelection()
        } else {
            return
        }
        event.accepted = true
    }

    Flickable {
        id: contentFlick
        anchors.fill: parent
        contentWidth: width
        contentHeight: 922
        clip: true
        interactive: true
        boundsBehavior: Flickable.StopAtBounds
        flickDeceleration: 5200
        maximumFlickVelocity: 2200
        Accessible.role: Accessible.Pane

        Item {
            id: content
            width: contentFlick.width
            height: contentFlick.contentHeight

            Item {
                id: heroRow
                x: 24
                y: 18
                width: 1160
                height: 262

                Rectangle {
                    id: heroMask
                    anchors.fill: heroCard
                    radius: 16
                    color: "white"
                    visible: false
                    layer.enabled: true
                }

                Item {
                    id: heroCard
                    x: 0
                    y: 0
                    width: 826
                    height: 262
                    layer.enabled: true
                    layer.smooth: true
                    layer.effect: MultiEffect {
                        maskEnabled: true
                        maskSource: heroMask
                        maskThresholdMin: 0.25
                        maskSpreadAtMin: 0.2
                        shadowEnabled: true
                        shadowColor: "#73000000"
                        shadowBlur: 0.55
                        shadowVerticalOffset: 10
                    }

                    Rectangle { anchors.fill: parent; color: "#171B27" }
                    Image {
                        anchors.fill: parent
                        source: root.heroGame
                            ? String(root.heroGame.heroImageUrl || root.heroGame.imageUrl || "") : ""
                        fillMode: Image.PreserveAspectCrop
                        sourceSize: Qt.size(Math.ceil(width), Math.ceil(height))
                        asynchronous: true
                        cache: true
                    }
                    Rectangle {
                        anchors.fill: parent
                        gradient: Gradient {
                            orientation: Gradient.Horizontal
                            GradientStop { position: 0; color: "#EB060912" }
                            GradientStop { position: 0.62; color: "#4D060912" }
                            GradientStop { position: 1; color: "#1A060912" }
                        }
                    }
                    Rectangle {
                        anchors.fill: parent
                        color: "transparent"
                        border.width: 1
                        border.color: "#29FFFFFF"
                        radius: 16
                    }

                    Column {
                        x: 22
                        anchors.bottom: parent.bottom
                        anchors.bottomMargin: 22
                        spacing: 14

                        Text {
                            text: qsTr("CONTINUE PLAYING")
                            color: DesktopTokens.focus
                            font.family: Theme.monoFont
                            font.pixelSize: 10
                            font.weight: Font.DemiBold
                            font.letterSpacing: 1
                        }

                        Column {
                            spacing: 6
                            Text {
                                text: root.heroGame ? String(root.heroGame.title || qsTr("Cyberpunk 2077")) : qsTr("Cyberpunk 2077")
                                color: "#FFFFFF"
                                font.family: Theme.displayFont
                                font.pixelSize: 34
                                font.weight: Font.Black
                                font.letterSpacing: -1
                            }
                            Text {
                                text: qsTr("Left off 2 hours ago · 14 h played · Phantom Liberty")
                                color: "#B8FFFFFF"
                                font.family: Theme.bodyFont
                                font.pixelSize: 13
                                font.weight: Font.DemiBold
                            }
                        }

                        Row {
                            spacing: 9

                            Rectangle {
                                id: resumeButton
                                width: 158
                                height: 38
                                radius: 10
                                color: resumeTap.pressed ? "#D9FFFFFF" : "#F2FFFFFF"
                                border.width: root.focusZone === 0 && root.focusIndex === 0 && AppController.inputMode !== "pointer" ? 2 : 0
                                border.color: "#FFFFFF"

                                Row {
                                    anchors.centerIn: parent
                                    spacing: 8
                                    Text { text: "▶"; color: "#0B0F1A"; font.pixelSize: 11 }
                                    Text { text: qsTr("Resume"); color: "#0B0F1A"; font.family: Theme.bodyFont; font.pixelSize: 14; font.weight: Font.ExtraBold }
                                    Rectangle {
                                        width: 41; height: 19; radius: 5; color: "#1A0B0F1A"
                                        Text { anchors.centerIn: parent; text: qsTr("ENTER"); color: "#B80B0F1A"; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.Bold }
                                    }
                                }
                                HoverHandler { id: resumeHover; cursorShape: Qt.PointingHandCursor; onHoveredChanged: if (hovered) root.setSelection(0, 0) }
                                TapHandler { id: resumeTap; onTapped: root.resumeHero() }
                            }

                            Rectangle {
                                id: detailsButton
                                width: 81
                                height: 38
                                radius: 10
                                color: detailsHover.hovered ? "#2EFFFFFF" : "#1FFFFFFF"
                                border.width: root.focusZone === 0 && root.focusIndex === 1 && AppController.inputMode !== "pointer" ? 2 : 1
                                border.color: root.focusZone === 0 && root.focusIndex === 1 && AppController.inputMode !== "pointer" ? "#FFFFFF" : "#33FFFFFF"
                                Text { anchors.centerIn: parent; text: qsTr("Details"); color: "#FFFFFF"; font.family: Theme.bodyFont; font.pixelSize: 14; font.weight: Font.Bold }
                                HoverHandler { id: detailsHover; cursorShape: Qt.PointingHandCursor; onHoveredChanged: if (hovered) root.setSelection(0, 1) }
                                TapHandler { onTapped: root.openGame(root.heroGame) }
                                Behavior on color { ColorAnimation { duration: AppController.reducedMotion ? 0 : 90 } }
                            }

                            Rectangle {
                                width: 182
                                height: 38
                                radius: 10
                                color: "#59000000"
                                border.width: 1
                                border.color: "#1FFFFFFF"
                                Row {
                                    anchors.centerIn: parent
                                    spacing: 8
                                    Rectangle { width: 6; height: 6; radius: 3; color: "#1DB954" }
                                    Text { text: qsTr("RTX 5080 · 1440p · AV1"); color: "#CCFFFFFF"; font.family: Theme.monoFont; font.pixelSize: 10; font.weight: Font.DemiBold; font.letterSpacing: 0.4 }
                                }
                            }
                        }
                    }
                }

                Rectangle {
                    id: friendsPanel
                    x: 842
                    y: 0
                    width: 318
                    height: 262
                    radius: 16
                    color: "#C70B0F1A"
                    border.width: 1
                    border.color: "#17FFFFFF"

                    Text {
                        x: 16; y: 16
                        text: qsTr("Friends")
                        color: "#FFFFFF"
                        font.family: Theme.bodyFont
                        font.pixelSize: 14
                        font.weight: Font.ExtraBold
                    }
                    Text {
                        anchors.right: parent.right; anchors.rightMargin: 16; y: 17
                        text: qsTr("3 ONLINE")
                        color: "#80FFFFFF"
                        font.family: Theme.monoFont
                        font.pixelSize: 10
                        font.weight: Font.DemiBold
                        font.letterSpacing: 0.4
                    }

                    Repeater {
                        model: root.friends
                        Item {
                            id: friendRow
                            required property var modelData
                            required property int index
                            x: 16
                            y: 44 + index * 54
                            width: 286
                            height: 44

                            Rectangle {
                                anchors.fill: parent
                                radius: 11
                                color: friendRow.index === 0 ? "#0FFFFFFF" : "transparent"
                                border.width: root.focusZone === 0 && root.focusIndex === friendRow.index + 2 && AppController.inputMode !== "pointer" ? 2 : 0
                                border.color: "#FFFFFF"
                            }
                            Rectangle {
                                x: 10; anchors.verticalCenter: parent.verticalCenter
                                width: 28; height: 28; radius: 14
                                color: friendRow.index === 0 ? "#1FFFFFFF" : "#14FFFFFF"
                                border.width: 1
                                border.color: friendRow.index === 0 ? "#29FFFFFF" : "#1FFFFFFF"
                                Text { anchors.centerIn: parent; text: friendRow.modelData.initial; color: friendRow.index === 0 ? "#E0FFFFFF" : "#B8FFFFFF"; font.family: Theme.displayFont; font.pixelSize: 12; font.weight: Font.Black }
                            }
                            Column {
                                x: 48; anchors.verticalCenter: parent.verticalCenter; spacing: 1
                                Text { text: friendRow.modelData.name; color: friendRow.index === 0 ? "#E0FFFFFF" : "#CCFFFFFF"; font.family: Theme.bodyFont; font.pixelSize: 13; font.weight: Font.Bold }
                                Text { text: friendRow.modelData.status; color: "#80FFFFFF"; font.family: Theme.bodyFont; font.pixelSize: 11; font.weight: Font.Medium }
                            }
                            Rectangle {
                                anchors.right: parent.right; anchors.rightMargin: 10; anchors.verticalCenter: parent.verticalCenter
                                width: friendRow.modelData.action === "Invite" ? 51 : 44
                                height: 26
                                radius: 8
                                color: friendHover.hovered ? "#26FFFFFF" : (friendRow.index === 0 ? "#1FFFFFFF" : "#14FFFFFF")
                                border.width: friendRow.index === 0 ? 1 : 0
                                border.color: "#2EFFFFFF"
                                Text { anchors.centerIn: parent; text: friendRow.modelData.action; color: friendRow.index === 0 ? "#FFFFFF" : "#B8FFFFFF"; font.family: Theme.bodyFont; font.pixelSize: 11; font.weight: friendRow.index === 0 ? Font.Bold : Font.DemiBold }
                            }
                            HoverHandler { id: friendHover; cursorShape: Qt.PointingHandCursor; onHoveredChanged: if (hovered) root.setSelection(0, friendRow.index + 2) }
                            TapHandler { onTapped: root.openFriends() }
                        }
                    }

                    Rectangle {
                        id: partyButton
                        x: 16; y: 216
                        width: 286; height: 34; radius: 10
                        color: partyHover.hovered ? "#17FFFFFF" : "#0FFFFFFF"
                        border.width: root.focusZone === 0 && root.focusIndex === 5 && AppController.inputMode !== "pointer" ? 2 : 1
                        border.color: root.focusZone === 0 && root.focusIndex === 5 && AppController.inputMode !== "pointer" ? "#FFFFFF" : "#1FFFFFFF"
                        Row {
                            anchors.centerIn: parent; spacing: 8
                            Text { text: qsTr("Start a co-op party"); color: "#CCFFFFFF"; font.family: Theme.bodyFont; font.pixelSize: 12; font.weight: Font.Bold }
                            Rectangle {
                                width: 43; height: 18; radius: 5; color: "#17FFFFFF"
                                Text { anchors.centerIn: parent; text: qsTr("Ctrl P"); color: "#99FFFFFF"; font.family: Theme.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold }
                            }
                        }
                        HoverHandler { id: partyHover; cursorShape: Qt.PointingHandCursor; onHoveredChanged: if (hovered) root.setSelection(0, 5) }
                        TapHandler { onTapped: root.openFriends() }
                        Behavior on color { ColorAnimation { duration: AppController.reducedMotion ? 0 : 90 } }
                    }
                }
            }

            Item {
                id: jumpRail
                x: 24; y: 296; width: 1160; height: 198
                Text { text: qsTr("Jump back in"); color: "#FFFFFF"; font.family: Theme.displayFont; font.pixelSize: 16; font.weight: Font.Black; font.letterSpacing: -0.2 }
                Text {
                    anchors.right: parent.right; y: 1
                    text: qsTr("See all 9  ›")
                    color: seeJump.hovered ? "#B8FFFFFF" : "#80FFFFFF"
                    font.family: Theme.bodyFont; font.pixelSize: 11; font.weight: Font.Bold
                    HoverHandler { id: seeJump; cursorShape: Qt.PointingHandCursor }
                    TapHandler { onTapped: { root.routeRequested("library"); AppController.navigate("library") } }
                }
                Row {
                    y: 30; spacing: 14
                    Repeater {
                        model: root.jumpGames
                        DesktopHomePoster {
                            required property var modelData
                            required property int index
                            game: modelData
                            current: root.focusZone === 1 && root.focusIndex === index
                            onPointed: root.setSelection(1, index)
                            onActivated: root.openGame(modelData)
                        }
                    }
                }
            }

            Item {
                id: playingRail
                x: 24; y: 510; width: 1160; height: 198
                Row {
                    spacing: 9
                    Text { text: qsTr("Friends are playing"); color: "#FFFFFF"; font.family: Theme.displayFont; font.pixelSize: 16; font.weight: Font.Black; font.letterSpacing: -0.2 }
                    Text { anchors.baseline: parent.children[0].baseline; text: qsTr("CO-OP READY"); color: "#66FFFFFF"; font.family: Theme.monoFont; font.pixelSize: 10; font.weight: Font.DemiBold; font.letterSpacing: 0.4 }
                }
                Text {
                    anchors.right: parent.right; y: 1
                    text: qsTr("See all  ›")
                    color: seeFriends.hovered ? "#B8FFFFFF" : "#80FFFFFF"
                    font.family: Theme.bodyFont; font.pixelSize: 11; font.weight: Font.Bold
                    HoverHandler { id: seeFriends; cursorShape: Qt.PointingHandCursor }
                    TapHandler { onTapped: root.openFriends() }
                }
                Row {
                    y: 30; spacing: 14
                    Repeater {
                        model: root.friendsGames
                        DesktopHomePoster {
                            required property var modelData
                            required property int index
                            game: modelData
                            current: root.focusZone === 2 && root.focusIndex === index
                            onPointed: root.setSelection(2, index)
                            onActivated: root.openGame(modelData)
                        }
                    }
                }
            }

            Item {
                id: newRail
                x: 24; y: 724; width: 1160; height: 198
                Text { text: qsTr("New in your library"); color: "#FFFFFF"; font.family: Theme.displayFont; font.pixelSize: 16; font.weight: Font.Black; font.letterSpacing: -0.2 }
                Text {
                    anchors.right: parent.right; y: 1
                    text: qsTr("See all  ›")
                    color: seeNew.hovered ? "#B8FFFFFF" : "#80FFFFFF"
                    font.family: Theme.bodyFont; font.pixelSize: 11; font.weight: Font.Bold
                    HoverHandler { id: seeNew; cursorShape: Qt.PointingHandCursor }
                    TapHandler { onTapped: { root.routeRequested("library"); AppController.navigate("library") } }
                }
                Row {
                    y: 30; spacing: 14
                    Repeater {
                        model: root.newGames
                        DesktopHomePoster {
                            required property var modelData
                            required property int index
                            game: modelData
                            current: root.focusZone === 3 && root.focusIndex === index
                            onPointed: root.setSelection(3, index)
                            onActivated: root.openGame(modelData)
                        }
                    }
                }
            }
        }
    }

    Component.onCompleted: {
        root.focusZone = 0
        root.focusIndex = Math.max(0, Math.min(5, ShellStore.focusIndex("desktop-home")))
        Qt.callLater(root.forceActiveFocus)
    }
    onFocusIndexChanged: ShellStore.rememberFocus("desktop-home", focusIndex)
}
