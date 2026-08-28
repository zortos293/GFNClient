pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    property string searchQuery: ""
    property string activeFilter: "all"
    property var contextGame: null
    property point contextPoint: Qt.point(0, 0)
    signal detailsRequested(var game)
    signal playRequested(var game)

    function filteredGames() {
        const query = searchQuery.trim().toLocaleLowerCase()
        const source = ShellStore.catalogGames || []
        const result = []
        for (let index = 0; index < source.length; ++index) {
            const game = source[index]
            if (query !== "" && String(game.title || "").toLocaleLowerCase().indexOf(query) < 0)
                continue
            const stores = (game.availableStores || []).join(" ").toLocaleLowerCase()
            if (activeFilter === "ready" && !game.isAvailable)
                continue
            if (activeFilter === "steam" && stores.indexOf("steam") < 0)
                continue
            if (activeFilter === "epic" && stores.indexOf("epic") < 0)
                continue
            if (activeFilter === "gog" && stores.indexOf("gog") < 0)
                continue
            result.push(game)
        }
        return result
    }
    readonly property var games: filteredGames()

    Row {
        x: 24; y: 18; spacing: 8
        Repeater {
            model: [
                {key:"all", label:qsTr("All"), count:ShellStore.catalogTotalCount || root.games.length},
                {key:"ready", label:qsTr("Ready to play"), count:Math.max(0, root.games.length - 8)},
                {key:"rtx", label:"RTX", count:63},
                {key:"controller", label:qsTr("Controller"), count:150},
                {key:"steam", label:"Steam", count:""},
                {key:"epic", label:"Epic", count:""},
                {key:"gog", label:"GOG", count:""}
            ]
            delegate: Button {
                id: filterButton
                required property var modelData
                height: 30
                width: Math.max(69, labelText.implicitWidth + countText.implicitWidth + 35)
                padding: 0
                focusPolicy: Qt.StrongFocus
                background: Rectangle {
                    radius: 9
                    color: root.activeFilter === filterButton.modelData.key ? "#1AFFFFFF" : (filterButton.hovered ? "#0FFFFFFF" : "transparent")
                    border.width: 1
                    border.color: root.activeFilter === filterButton.modelData.key ? "#3DFFFFFF" : DesktopTokens.seam
                }
                contentItem: Row {
                    anchors.centerIn: parent; spacing: 7
                    Rectangle { visible: ["steam","epic","gog"].indexOf(filterButton.modelData.key) >= 0; width: 7; height: 7; radius: filterButton.modelData.key === "gog" ? 2 : 4; color: filterButton.modelData.key === "gog" ? "#8D52FF" : "transparent"; border.width: filterButton.modelData.key === "gog" ? 0 : 1; border.color: "#52FFFFFF" }
                    Text { id: labelText; text: filterButton.modelData.label; color: root.activeFilter === filterButton.modelData.key ? DesktopTokens.text : DesktopTokens.textMuted; font.family: DesktopTokens.bodyFont; font.pixelSize: 11; font.weight: Font.Bold }
                    Text { id: countText; text: filterButton.modelData.count; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold }
                }
                onClicked: root.activeFilter = modelData.key
            }
        }
    }
    Text {
        anchors.right: parent.right; anchors.rightMargin: 24; y: 27
        text: qsTr("RIGHT-CLICK A GAME FOR ACTIONS")
        color: DesktopTokens.textFaint
        font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: 0.7
    }

    GridView {
        id: grid
        // The delegate keeps a six-pixel focus/scale gutter. Offset the view by
        // that gutter so the artwork remains on Paper's 24/64 alignment lane.
        x: 18; y: 58
        width: parent.width - 36
        height: parent.height - 58
        clip: true
        cellWidth: 146
        cellHeight: 214
        model: root.games
        focus: true
        boundsBehavior: Flickable.StopAtBounds
        ScrollBar.vertical: ScrollBar { policy: ScrollBar.AsNeeded }
        delegate: DesktopPoster {
            required property var modelData
            game: modelData
            onClicked: {
                ShellStore.selectedGame = modelData
                root.detailsRequested(modelData)
            }
            onDoubleClicked: {
                ShellStore.selectedGame = modelData
                root.playRequested(modelData)
            }
            onContextRequested: (sceneX, sceneY) => {
                root.contextGame = modelData
                const local = root.mapFromItem(null, sceneX, sceneY)
                root.contextPoint = Qt.point(Math.min(root.width - 230, Math.max(16, local.x)), Math.min(root.height - 250, Math.max(16, local.y)))
            }
        }
    }

    MouseArea {
        anchors.fill: parent; z: 40
        visible: root.contextGame !== null
        acceptedButtons: Qt.LeftButton | Qt.RightButton
        onClicked: root.contextGame = null
    }
    Rectangle {
        x: root.contextPoint.x; y: root.contextPoint.y
        width: 212; height: 237; radius: 12
        visible: root.contextGame !== null
        z: 41
        color: "#F710131D"
        border.width: 1; border.color: "#29FFFFFF"
        Column {
            x: 7; y: 7; width: 198; spacing: 0
            Text { width: parent.width; height: 28; leftPadding: 8; text: root.contextGame ? String(root.contextGame.title || "").toUpperCase() : ""; color: DesktopTokens.textFaint; elide: Text.ElideRight; verticalAlignment: Text.AlignVCenter; font.family: DesktopTokens.monoFont; font.pixelSize: 9; font.weight: Font.DemiBold; font.letterSpacing: 0.6 }
            Repeater {
                model: [
                    {label:qsTr("Play"), key:"Enter", action:"play"},
                    {label:qsTr("Details"), key:"Space", action:"details"},
                    {label:qsTr("Add to favourites"), key:"F", action:"favorite"},
                    {label:qsTr("Add to collection"), key:"›", action:"collection"},
                    {label:qsTr("Stream settings…"), key:"Ctrl ,", action:"settings"},
                    {label:qsTr("Hide from library"), key:"", action:"hide"}
                ]
                delegate: ItemDelegate {
                    required property var modelData
                    width: 198; height: 30; padding: 8
                    background: Rectangle { radius: 7; color: parent.hovered || parent.activeFocus ? "#14FFFFFF" : "transparent" }
                    contentItem: Item {
                        Text { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; text: modelData.label; color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: 12 }
                        Text { anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; text: modelData.key; color: DesktopTokens.textFaint; font.family: DesktopTokens.monoFont; font.pixelSize: 9 }
                    }
                    onClicked: {
                        const game = root.contextGame
                        root.contextGame = null
                        if (modelData.action === "play") { ShellStore.selectedGame = game; root.playRequested(game) }
                        else if (modelData.action === "details") { ShellStore.selectedGame = game; root.detailsRequested(game) }
                        else if (modelData.action === "favorite") ShellStore.toggleFavorite(game)
                        else if (modelData.action === "settings") AppController.navigate("settings-streaming")
                    }
                }
            }
        }
        opacity: visible ? 1 : 0
        scale: visible ? 1 : 0.96
        Behavior on opacity { NumberAnimation { duration: DesktopTokens.quickDuration } }
        Behavior on scale { NumberAnimation { duration: DesktopTokens.quickDuration; easing.type: Easing.OutCubic } }
    }
}
