pragma Singleton

import QtQuick

QtObject {
    // Exact official GFN web/Steam Deck modes, mirrored from the Electron app.
    // CloudMatch can reject invented aspect-ratio dimensions.
    readonly property var officialModes: [
        { width: 3840, height: 2160, aspect: "16:9", fps: [120, 60, 30] },
        { width: 3456, height: 2160, aspect: "16:10", fps: [120, 60, 30] },
        { width: 3840, height: 1600, aspect: "21:9", fps: [120, 60, 30] },
        { width: 3440, height: 1440, aspect: "21:9", fps: [120, 60, 30] },
        { width: 3840, height: 1080, aspect: "32:9", fps: [120, 60, 30] },
        { width: 2560, height: 1600, aspect: "16:10", fps: [120, 60, 30] },
        { width: 2560, height: 1440, aspect: "16:9", fps: [120, 60, 30] },
        { width: 2560, height: 1080, aspect: "21:9", fps: [120, 60, 30] },
        { width: 1920, height: 1200, aspect: "16:10", fps: [240, 120, 60, 30] },
        { width: 1920, height: 1080, aspect: "16:9", fps: [240, 120, 60, 30] },
        { width: 1600, height: 1200, aspect: "4:3", fps: [120, 60, 30] },
        { width: 1680, height: 1050, aspect: "16:10", fps: [120, 60, 30] },
        { width: 1600, height: 900, aspect: "16:9", fps: [120, 60, 30] },
        { width: 1280, height: 1024, aspect: "Other", fps: [120, 60, 30] },
        { width: 1440, height: 900, aspect: "16:10", fps: [120, 60, 30] },
        { width: 1680, height: 720, aspect: "21:9", fps: [120, 60, 30] },
        { width: 1366, height: 768, aspect: "16:9", fps: [120, 60, 30] },
        { width: 1280, height: 800, aspect: "16:10", fps: [120, 60, 30] },
        { width: 1112, height: 834, aspect: "4:3", fps: [120, 60, 30] },
        { width: 1280, height: 720, aspect: "16:9", fps: [120, 60, 30] },
        { width: 1376, height: 640, aspect: "21:9", fps: [120, 60, 30] },
        { width: 1024, height: 768, aspect: "4:3", fps: [120, 60, 30] }
    ]

    function aspectKey(aspect) {
        var value = String(aspect)
        if (value.indexOf("16:10") === 0) return "16:10"
        if (value.indexOf("21:9") === 0) return "21:9"
        if (value.indexOf("32:9") === 0) return "32:9"
        if (value.indexOf("4:3") === 0) return "4:3"
        if (value.indexOf("16:9") === 0) return "16:9"
        return "Other"
    }

    function rawEntitlements(entitlements) {
        var result = []
        var source = entitlements || []
        for (var i = 0; i < source.length; ++i) {
            var width = Number(source[i].width)
            var height = Number(source[i].height)
            var fps = Number(source[i].fps)
            if (width > 0 && height > 0 && fps > 0)
                result.push({ width: width, height: height, fps: fps })
        }
        return result
    }

    function expandedProfiles(entitlements) {
        var envelopes = rawEntitlements(entitlements)
        if (envelopes.length === 0)
            return [{ width: 1920, height: 1080, fps: 60, aspect: "16:9" }]
        var result = []
        var seen = ({})
        function add(width, height, fps, aspect) {
            var key = width + "x" + height + "@" + fps
            if (!seen[key]) {
                seen[key] = true
                result.push({ width: width, height: height, fps: fps, aspect: aspect })
            }
        }
        for (var rawIndex = 0; rawIndex < envelopes.length; ++rawIndex) {
            var raw = envelopes[rawIndex]
            var rawAspect = "Other"
            var ratio = raw.width / raw.height
            if (Math.abs(ratio - 16 / 9) < 0.04) rawAspect = "16:9"
            else if (Math.abs(ratio - 16 / 10) < 0.04) rawAspect = "16:10"
            else if (ratio >= 3.2) rawAspect = "32:9"
            else if (ratio >= 2.1) rawAspect = "21:9"
            else if (Math.abs(ratio - 4 / 3) < 0.04) rawAspect = "4:3"
            add(raw.width, raw.height, raw.fps, rawAspect)
        }
        for (var modeIndex = 0; modeIndex < officialModes.length; ++modeIndex) {
            var mode = officialModes[modeIndex]
            for (var fpsIndex = 0; fpsIndex < mode.fps.length; ++fpsIndex) {
                var modeFps = mode.fps[fpsIndex]
                for (var entitlementIndex = 0; entitlementIndex < envelopes.length; ++entitlementIndex) {
                    var entitlement = envelopes[entitlementIndex]
                    if (entitlement.width >= mode.width && entitlement.height >= mode.height
                            && entitlement.fps >= modeFps) {
                        add(mode.width, mode.height, modeFps, mode.aspect)
                        break
                    }
                }
            }
        }
        result.sort(function(left, right) {
            var pixelDelta = right.width * right.height - left.width * left.height
            if (pixelDelta !== 0) return pixelDelta
            if (right.width !== left.width) return right.width - left.width
            if (right.height !== left.height) return right.height - left.height
            return right.fps - left.fps
        })
        return result
    }

    function targetDimensions(resolution, aspect) {
        var tier = String(resolution)
        var key = aspectKey(aspect)
        var is4k = tier.indexOf("4K") >= 0 || tier.indexOf("3840x2160") >= 0
        var isQhd = tier.indexOf("1440") >= 0 || tier.indexOf("2560x1440") >= 0
        if (key === "16:10")
            return is4k ? ({ width: 3456, height: 2160 }) : isQhd ? ({ width: 2560, height: 1600 }) : ({ width: 1920, height: 1200 })
        if (key === "21:9")
            return is4k ? ({ width: 3840, height: 1600 }) : isQhd ? ({ width: 3440, height: 1440 }) : ({ width: 2560, height: 1080 })
        if (key === "32:9")
            return { width: 3840, height: 1080 }
        if (key === "4:3")
            return { width: 1600, height: 1200 }
        if (key === "Other")
            return { width: 1280, height: 1024 }
        return is4k ? ({ width: 3840, height: 2160 }) : isQhd ? ({ width: 2560, height: 1440 }) : ({ width: 1920, height: 1080 })
    }

    function resolveProfile(entitlements, resolution, aspect, requestedFps) {
        var profiles = expandedProfiles(entitlements)
        var target = targetDimensions(resolution, aspect)
        var targetAspect = aspectKey(aspect)
        var matching = []
        for (var i = 0; i < profiles.length; ++i) {
            if (profiles[i].width === target.width && profiles[i].height === target.height)
                matching.push(profiles[i])
        }
        if (matching.length === 0) {
            for (var aspectIndex = 0; aspectIndex < profiles.length; ++aspectIndex) {
                if (profiles[aspectIndex].aspect === targetAspect)
                    matching.push(profiles[aspectIndex])
            }
        }
        if (matching.length === 0)
            matching = profiles
        var requested = Number(requestedFps)
        var best = matching[0]
        for (var matchIndex = 0; matchIndex < matching.length; ++matchIndex) {
            var candidate = matching[matchIndex]
            if (candidate.width === best.width && candidate.height === best.height) {
                if (candidate.fps === requested)
                    best = candidate
                else if (best.fps > requested && candidate.fps <= requested)
                    best = candidate
                else if (candidate.fps <= requested && candidate.fps > best.fps)
                    best = candidate
            }
        }
        return best
    }

    function supportsTarget(entitlements, resolution, aspect) {
        var target = targetDimensions(resolution, aspect)
        var profiles = expandedProfiles(entitlements)
        for (var i = 0; i < profiles.length; ++i) {
            if (profiles[i].width === target.width && profiles[i].height === target.height)
                return true
        }
        return false
    }

    function availableFps(entitlements, resolution, aspect) {
        var resolved = resolveProfile(entitlements, resolution, aspect, 240)
        var profiles = expandedProfiles(entitlements)
        var result = []
        for (var i = 0; i < profiles.length; ++i) {
            if (profiles[i].width === resolved.width && profiles[i].height === resolved.height
                    && result.indexOf(String(profiles[i].fps)) === -1)
                result.push(String(profiles[i].fps))
        }
        return result
    }

    function dimensions(resolution, aspect, entitlements, fps) {
        var profile = resolveProfile(entitlements, resolution, aspect, fps === undefined ? 120 : fps)
        return { width: profile.width, height: profile.height }
    }

    function label(resolution, aspect, entitlements, fps) {
        var size = dimensions(resolution, aspect, entitlements, fps)
        return size.width + "×" + size.height
    }
}
