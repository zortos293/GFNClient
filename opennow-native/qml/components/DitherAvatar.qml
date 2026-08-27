import QtQuick

// Native Qt port of Dither Kit's deterministic avatar concept:
// https://www.tripwire.sh/dither-kit
Item {
    id: root
    property string name: "Player"
    property real hue: -1
    property string mirror: "auto"
    property bool animate: true
    property int animationDuration: 600
    property real reveal: 1
    readonly property var bayer4: [
        [0.03125, 0.53125, 0.15625, 0.65625],
        [0.78125, 0.28125, 0.90625, 0.40625],
        [0.21875, 0.71875, 0.09375, 0.59375],
        [0.96875, 0.46875, 0.84375, 0.34375]
    ]
    readonly property var avatarModel: makeModel(name, hue, mirror)
    implicitWidth: 48
    implicitHeight: 48

    function fnv1a(value) {
        var hash = 0x811c9dc5
        var input = String(value || "Player")
        for (var i = 0; i < input.length; ++i) {
            hash ^= input.charCodeAt(i)
            hash = (hash + (hash << 1) + (hash << 4) + (hash << 7)
                    + (hash << 8) + (hash << 24)) >>> 0
        }
        return hash >>> 0
    }

    function hueRgb(value) {
        var h = ((value % 360) + 360) % 360
        var saturation = 0.85
        var lightness = 0.58
        var chroma = (1 - Math.abs(2 * lightness - 1)) * saturation
        var x = chroma * (1 - Math.abs((h / 60) % 2 - 1))
        var offset = lightness - chroma / 2
        var channels = h < 60 ? [chroma, x, 0]
                     : h < 120 ? [x, chroma, 0]
                     : h < 180 ? [0, chroma, x]
                     : h < 240 ? [0, x, chroma]
                     : h < 300 ? [x, 0, chroma]
                               : [chroma, 0, x]
        return [Math.round((channels[0] + offset) * 255),
                Math.round((channels[1] + offset) * 255),
                Math.round((channels[2] + offset) * 255)]
    }

    function makeModel(seedName, requestedHue, requestedMirror) {
        var state = fnv1a(seedName) || 0x9e3779b9
        function random() {
            state ^= state << 13
            state >>>= 0
            state ^= state >>> 17
            state ^= state << 5
            state >>>= 0
            return state / 0x100000000
        }
        var bits = []
        for (var bitIndex = 0; bitIndex < 32; ++bitIndex)
            bits.push(random() < 0.5)
        var drawnVertical = random() < 0.5
        var drawnHue = Math.floor(random() * 180) * 2
        var halfDensity = []
        for (var densityIndex = 0; densityIndex < 32; ++densityIndex)
            halfDensity.push(0.55 + random() * 0.45)
        var vertical = requestedMirror === "auto" ? drawnVertical : requestedMirror === "vertical"
        var on = []
        var density = []
        for (var row = 0; row < 8; ++row) {
            for (var column = 0; column < 8; ++column) {
                var index = vertical
                        ? Math.min(row, 7 - row) * 8 + column
                        : row * 4 + Math.min(column, 7 - column)
                on.push(bits[index])
                density.push(halfDensity[index])
            }
        }
        return { on: on, density: density,
                 rgb: hueRgb(requestedHue >= 0 ? requestedHue : drawnHue) }
    }

    function replay() {
        entrance.stop()
        if (!animate) {
            reveal = 1
            avatarCanvas.requestPaint()
            return
        }
        reveal = 0
        entrance.start()
    }

    onAvatarModelChanged: {
        avatarCanvas.requestPaint()
        Qt.callLater(replay)
    }
    onRevealChanged: avatarCanvas.requestPaint()
    Component.onCompleted: replay()

    NumberAnimation {
        id: entrance
        target: root
        property: "reveal"
        from: 0
        to: 1
        duration: root.animationDuration
        easing.type: Easing.OutCubic
    }

    Canvas {
        id: avatarCanvas
        anchors.fill: parent
        canvasSize: Qt.size(32, 32)
        antialiasing: false
        renderStrategy: Canvas.Threaded
        layer.enabled: true
        layer.smooth: false
        onPaint: {
            var context = getContext("2d")
            context.clearRect(0, 0, 32, 32)
            var model = root.avatarModel
            var progress = root.reveal
            for (var row = 0; row < 8; ++row) {
                for (var column = 0; column < 8; ++column) {
                    var cellIndex = row * 8 + column
                    if (!model.on[cellIndex])
                        continue
                    var start = root.bayer4[row % 4][column % 4] * 0.7
                    var cellAlpha = Math.max(0, Math.min(1, (progress - start) / 0.3))
                    if (cellAlpha <= 0)
                        continue
                    var density = model.density[cellIndex]
                    var base = 0.35 + 0.65 * density
                    for (var pixelY = 0; pixelY < 4; ++pixelY) {
                        for (var pixelX = 0; pixelX < 4; ++pixelX) {
                            var globalX = column * 4 + pixelX
                            var globalY = row * 4 + pixelY
                            var lit = density > root.bayer4[globalY & 3][globalX & 3]
                            var alpha = (lit ? base : base * 0.35) * cellAlpha
                            context.fillStyle = "rgba(" + model.rgb[0] + "," + model.rgb[1]
                                    + "," + model.rgb[2] + "," + alpha + ")"
                            context.fillRect(globalX, globalY, 1, 1)
                        }
                    }
                }
            }
        }
    }
}
