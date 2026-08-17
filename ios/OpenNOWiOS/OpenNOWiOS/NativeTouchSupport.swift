import Foundation

/// Which games get native touch.
///
/// GeForce NOW ships no per-game touch layouts. The games that "support touch" are simply the ones
/// whose Windows build already reacts to a Windows digitizer — invariably because they also ship on
/// phones or tablets — and they switch to their own mobile UI the moment one appears. So this file
/// decides nothing about *how* touch works, only *where* it gets turned on.
///
/// The catalog carries the answer: a launch option's `supportedControls` includes `TOUCHSCREEN`,
/// the same capability signal the official client uses. Reading that signal replaces the
/// title-matched "fortnite" check the iOS build shipped with, which missed every other touch title
/// and broke for localized names.
///
/// Ported from `NativeTouchGames.kt` in the Android build.
enum NativeTouchSupport {

    /// The value the catalog uses to mark a touch-capable variant.
    static let supportedControlTouchscreen = "TOUCHSCREEN"

    /// Whether the catalog itself claims this game takes touch, across any of its launch options.
    static func catalogClaimsTouchSupport(_ game: CloudGame?) -> Bool {
        guard let game else { return false }
        return game.launchOptions.contains { option in
            option.supportedControls?.contains {
                $0.caseInsensitiveCompare(supportedControlTouchscreen) == .orderedSame
            } ?? false
        }
    }

    static func shouldUseNativeTouch(mode: NativeTouchMode, game: CloudGame?) -> Bool {
        switch mode {
        case .never: return false
        case .always: return true
        case .automatic: return catalogClaimsTouchSupport(game)
        }
    }

    /// Resolves native touch for a live stream after the player has made a session-level choice.
    /// A catalog capability is useful guidance, but it must not lock the player out of the
    /// on-screen controller when they have deliberately asked for it.
    static func shouldUseNativeTouchForStream(
        mode: NativeTouchMode,
        game: CloudGame?,
        preferVirtualController: Bool
    ) -> Bool {
        !preferVirtualController && shouldUseNativeTouch(mode: mode, game: game)
    }

    /// Which stored touch-control layout the on-screen controller should load.
    ///
    /// Fortnite is the one title that still needs its own preset, because its mobile UI puts the
    /// build and edit controls where a standard gamepad layout puts the face buttons. Everything
    /// else uses the default preset.
    static func touchLayoutProfile(gameTitle: String, settings: AppSettings) -> String {
        guard settings.touch.nativeTouchMode != .never else { return "default" }
        return gameTitle.localizedCaseInsensitiveContains("fortnite") ? "fortnite-mobile" : "default"
    }

    /// Whether a session should be requested with the mobile CloudMatch identity.
    ///
    /// This is not a free choice: the mobile identity narrows the allocation matrix, so a game
    /// requested at 1440p or above 60 fps would be quietly downgraded. Under `.automatic` the
    /// user's quality request wins — touch is optional, the resolution they picked is not.
    /// Mirrors `StreamSettings.requiresNativeDesktopCloudMatchMode()` on Android.
    static func prefersMobileIdentity(
        mode: NativeTouchMode,
        game: CloudGame?,
        profile: StreamVideoProfile,
        hdrEnabled: Bool
    ) -> Bool {
        switch mode {
        case .never:
            return false
        case .always:
            return true
        case .automatic:
            guard catalogClaimsTouchSupport(game) else { return false }
            return !exceedsMobileAllocationEnvelope(profile: profile, hdrEnabled: hdrEnabled)
        }
    }

    /// The envelope CloudMatch's mobile allocation will actually honour.
    static func exceedsMobileAllocationEnvelope(profile: StreamVideoProfile, hdrEnabled: Bool) -> Bool {
        hdrEnabled || profile.fps > 60 || profile.width > 1920 || profile.height > 1200
    }

    /// One line per session recording the catalog signal and the decision it produced, so a
    /// "touch did not work" report can be answered without guessing.
    static func diagnostics(game: CloudGame?, enabled: Bool) -> String {
        guard let game else { return "native touch enabled=\(enabled) game=none" }
        let controls = game.launchOptions
            .compactMap(\.supportedControls)
            .flatMap { $0 }
            .reduce(into: [String]()) { acc, value in
                if !acc.contains(value) { acc.append(value) }
            }
            .joined(separator: "|")
        return "native touch enabled=\(enabled) id=\(game.id) title=\(game.title) "
            + "catalogTouch=\(catalogClaimsTouchSupport(game)) "
            + "supportedControls=\(controls.isEmpty ? "none" : controls)"
    }
}
