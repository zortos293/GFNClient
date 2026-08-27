import QtQuick
import QtQuick.Effects
import OpenNOW

Rectangle {
    id: root
    property int variant: 0
    property string kicker: ""
    property url source: ""
    property var sources: []
    property var game: null
    property bool preferBanner: true
    property int candidateIndex: 0
    readonly property var candidates: buildCandidates()
    readonly property url currentSource: candidates.length > candidateIndex ? candidates[candidateIndex] : ""
    clip: true
    radius: 14
    color: "#171d1a"

    function appendCandidate(result, value) {
        var candidate = String(value || "").trim()
        if (candidate.length > 0 && result.indexOf(candidate) === -1)
            result.push(candidate)
    }

    function appendType(result, imageMap, key) {
        var values = imageMap && imageMap[key] ? imageMap[key] : []
        for (var i = 0; i < values.length; ++i)
            appendCandidate(result, values[i])
    }

    function buildCandidates() {
        var result = []
        for (var sourceIndex = 0; sourceIndex < root.sources.length; ++sourceIndex)
            appendCandidate(result, root.sources[sourceIndex])

        var imageMap = root.game && root.game.imageUrlsByType ? root.game.imageUrlsByType : ({})
        var bannerTypes = ["MARQUEE_HERO_IMAGE", "FEATURE_IMAGE", "HERO_IMAGE", "TV_BANNER", "KEY_ART", "KEY_IMAGE"]
        var posterTypes = ["GAME_BOX_ART", "KEY_IMAGE", "KEY_ART", "TV_BANNER", "HERO_IMAGE"]
        var types = root.preferBanner ? bannerTypes : posterTypes
        for (var typeIndex = 0; typeIndex < types.length; ++typeIndex)
            appendType(result, imageMap, types[typeIndex])

        if (root.game) {
            appendCandidate(result, root.game.heroImageUrl)
            appendCandidate(result, root.game.imageUrl)
        }
        appendCandidate(result, root.source)

        if (root.game) {
            var screenshots = root.game.screenshotUrls || []
            for (var screenshotIndex = 0; screenshotIndex < screenshots.length; ++screenshotIndex)
                appendCandidate(result, screenshots[screenshotIndex])
            appendCandidate(result, root.game.screenshotUrl)

            var variants = root.game.variants || []
            for (var variantIndex = 0; variantIndex < variants.length; ++variantIndex) {
                var variant = variants[variantIndex]
                var id = String(variant.id || "")
                if (String(variant.store || "").toUpperCase().indexOf("STEAM") >= 0 && /^\d+$/.test(id)) {
                    appendCandidate(result, root.preferBanner
                                    ? "https://cdn.cloudflare.steamstatic.com/steam/apps/" + id + "/header.jpg"
                                    : "https://cdn.cloudflare.steamstatic.com/steam/apps/" + id + "/library_600x900.jpg")
                    break
                }
            }
        }
        return result
    }

    onCandidatesChanged: candidateIndex = 0

    Image {
        id: artworkImage
        anchors.fill: parent
        source: root.currentSource
        visible: false
        fillMode: Image.PreserveAspectCrop
        asynchronous: true
        cache: true
        layer.enabled: true
        onStatusChanged: {
            if (status === Image.Error && root.candidateIndex + 1 < root.candidates.length)
                root.candidateIndex += 1
        }
    }

    MultiEffect {
        anchors.fill: parent
        source: artworkImage
        visible: root.currentSource.toString().length > 0
        opacity: artworkImage.status === Image.Ready ? 1 : 0
        maskEnabled: true
        maskSource: artworkMask
        Behavior on opacity { NumberAnimation { duration: Theme.motion } }
    }

    Rectangle {
        id: artworkMask
        anchors.fill: parent
        radius: root.radius
        color: "white"
        visible: false
        layer.enabled: true
    }

    Text {
        visible: root.kicker.length > 0
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.leftMargin: 16
        anchors.topMargin: 16
        text: root.kicker.toUpperCase()
        color: Theme.accent
        font.family: Theme.monoFont.family
        font.pixelSize: 10
        font.weight: Font.Bold
        font.letterSpacing: 1.5
    }
}
