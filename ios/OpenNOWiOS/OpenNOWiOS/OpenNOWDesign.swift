import SwiftUI

#if canImport(UIKit)
import UIKit
#endif

// MARK: - Colour primitives

/// Hex helper so the token table below reads the same as the Android palette it mirrors
/// (`android/app/src/main/java/com/opencloudgaming/opennow/ui/theme/Color.kt`).
extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

#if canImport(UIKit)
private func dynamicColor(dark: UInt32, light: UInt32, opacity: Double = 1) -> Color {
    Color(uiColor: UIColor { traits in
        let hex = traits.userInterfaceStyle == .light ? light : dark
        return UIColor(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: CGFloat(opacity)
        )
    })
}

private func dynamicColor(dark: Color, light: Color) -> Color {
    Color(uiColor: UIColor { traits in
        UIColor(traits.userInterfaceStyle == .light ? light : dark)
    })
}
#else
private func dynamicColor(dark: UInt32, light: UInt32, opacity: Double = 1) -> Color {
    Color(hex: dark, opacity: opacity)
}

private func dynamicColor(dark: Color, light: Color) -> Color { dark }
#endif

// MARK: - Accent

/// The six accents Android ships (`UiAccent` in `Models.kt`), plus the olive the iOS build has
/// used since its first release so nobody's existing look is taken away.
///
/// Each case carries a separate light-appearance value. The dark values are tuned for a near-black
/// ground and every one of them fails 4.5 : 1 against white, so a light-mode variant is not
/// optional — it is the difference between a readable tint and an invisible one.
enum UIAccent: String, Codable, CaseIterable, Identifiable {
    case openNow
    case pixel
    case hotPink
    case lime
    case coral
    case violet
    case classic

    var id: String { rawValue }

    var label: String {
        switch self {
        case .openNow: return "OpenNOW"
        case .pixel: return "Pixel"
        case .hotPink: return "Hot Pink"
        case .lime: return "Lime"
        case .coral: return "Coral"
        case .violet: return "Violet"
        case .classic: return "Classic"
        }
    }

    /// Value used on dark surfaces. Matches `OpenNowPalette` on Android one-for-one.
    var darkHex: UInt32 {
        switch self {
        case .openNow: return 0x6AF0A0
        case .pixel: return 0x8AB4F8
        case .hotPink: return 0xFF4FB8
        case .lime: return 0xC7EF6B
        case .coral: return 0xFF8D7A
        case .violet: return 0xC7A4FF
        case .classic: return 0x75B800
        }
    }

    /// Darkened for light appearance. Every value below measures at least 5 : 1 on white.
    var lightHex: UInt32 {
        switch self {
        case .openNow: return 0x0E7A57
        case .pixel: return 0x1A5FBF
        case .hotPink: return 0xB4147C
        case .lime: return 0x4F6B0C
        case .coral: return 0xB03A22
        case .violet: return 0x5B45A6
        case .classic: return 0x4A7300
        }
    }

    /// Resolves per appearance.
    var color: Color { dynamicColor(dark: darkHex, light: lightHex) }

    /// Fixed dark-surface value, for chrome that stays dark in both appearances
    /// (the catalog backdrop, anything over live video).
    var onDarkColor: Color { Color(hex: darkHex) }

    /// Legible label colour on a filled accent. Near-black over the bright dark-mode accents,
    /// white over the darkened light-mode ones.
    var onAccent: Color { dynamicColor(dark: Color(hex: 0x08090C), light: .white) }
}

// MARK: - Palette

/// Single source of truth for colour. Mirrors `OpenNowPalette` on Android; anything that appears
/// more than once belongs here rather than inline at a call site.
enum OpenNOWPalette {

    // Surfaces that follow the system appearance.
    static let surfaceBase = dynamicColor(dark: Color(hex: 0x090B0D), light: Color(uiColor: .systemBackground))
    static let surfaceRaised = dynamicColor(dark: Color(hex: 0x11161A), light: Color(uiColor: .secondarySystemGroupedBackground))
    static let surfaceInset = dynamicColor(dark: Color(hex: 0x171D22), light: Color(uiColor: .tertiarySystemFill))
    static let surfaceGrouped = dynamicColor(dark: Color(hex: 0x090B0D), light: Color(uiColor: .systemGroupedBackground))

    // Surfaces that stay dark in both appearances. Box art and live video need a dark surround,
    // and a white catalog behind a dark game reads as two different applications.
    static let catalogBackdrop = Color(hex: 0x090B0D)
    static let wallpaperBackdrop = Color(hex: 0x07100B)
    static let imagePlaceholder = Color(hex: 0x0E1317)
    static let shimmerBase = Color(hex: 0x0D1216)

    // Text
    static let textPrimary = dynamicColor(dark: Color(hex: 0xEEF3F5), light: Color(uiColor: .label))
    static let textMuted = dynamicColor(dark: Color(hex: 0x98A4AA), light: Color(uiColor: .secondaryLabel))
    static let textFaint = dynamicColor(dark: Color(hex: 0x74838A), light: Color(uiColor: .tertiaryLabel))

    /// Fixed light text for chrome that stays dark regardless of appearance.
    static let textOnDark = Color(hex: 0xEEF3F5)
    static let textMutedOnDark = Color(hex: 0x98A4AA)

    // Feedback
    static let errorContainer = dynamicColor(dark: Color(hex: 0x33181C), light: Color(hex: 0xFDECEC))
    static let onErrorContainer = dynamicColor(dark: Color(hex: 0xFFB8BF), light: Color(hex: 0x8C1D2A))

    /// The quality ladder, shared by the in-stream stats HUD and the post-session report so the
    /// two never disagree about what "bad" looks like.
    ///
    /// `statusGood` is deliberately absent: a good reading renders in `textPrimary`. Tinting the
    /// normal case is what makes the abnormal one hard to spot.
    static let statusFair = dynamicColor(dark: Color(hex: 0xFFC95A), light: Color(hex: 0x8A5A08))
    static let statusPoor = dynamicColor(dark: Color(hex: 0xFF8D7A), light: Color(hex: 0xA93A26))

    /// Advisory notices that are neither an error nor a quality reading — privacy disclosures,
    /// plan gates, "this applies next session".
    static let statusNotice = dynamicColor(dark: Color(hex: 0xFFC266), light: Color(hex: 0x8A5308))

    // Chrome over live video. All fixed — there is no light appearance on top of a game.

    /// Panel fill. Deliberately not opaque; the point of the control panel is to be usable without
    /// leaving the game. But not below 96 % either — `textMutedOnDark` drops under 4.5 : 1 over a
    /// bright frame at the 93 % the first draft used.
    static let panelOverVideo = Color(hex: 0x11161A, opacity: 0.96)

    /// Row fills inside a panel over video. Opaque tones rather than translucent white, which
    /// composites differently against every frame of the game behind it.
    static let rowOverVideoRest = Color(hex: 0x1B2228)
    static let rowOverVideoFocused = Color(hex: 0x28323A)

    /// Hairline that keeps an overlay's edge visible against a bright frame.
    static let hairlineOverVideo = Color.white.opacity(0.08)

    /// Full-screen wash behind a stream overlay.
    static let streamScrim = Color.black.opacity(0.55)

    /// Heavier wash for Reduce Transparency.
    static let streamScrimOpaque = Color.black.opacity(0.8)

    static let hairline = dynamicColor(dark: Color(hex: 0x222B31), light: Color(uiColor: .separator))
}

// MARK: - Quality ladder

/// Shared by the stats HUD, the session report and the pre-flight deck.
enum StreamQualityLevel: Equatable {
    case good
    case fair
    case poor

    /// `nil` when the metric is fine and should render in the normal text colour.
    var tint: Color? {
        switch self {
        case .good: return nil
        case .fair: return OpenNOWPalette.statusFair
        case .poor: return OpenNOWPalette.statusPoor
        }
    }

    /// Redundant glyph for Differentiate Without Color. Colour is never the only signal.
    var glyph: String? {
        switch self {
        case .good: return nil
        case .fair: return "triangle.fill"
        case .poor: return "exclamationmark.circle.fill"
        }
    }

    var label: String {
        switch self {
        case .good: return "Good"
        case .fair: return "Fair"
        case .poor: return "Poor"
        }
    }

    /// Bands live in `StreamQualityLadder` so the HUD, the session report and the server picker
    /// cannot drift into disagreeing about what "poor" means.
    static func from(score: Int) -> StreamQualityLevel {
        StreamQualityLadder.level(forScore: score)
    }
}

// MARK: - Metrics

/// One spacing scale, so gutters and padding stop being decided independently at each call site.
enum OpenNOWSpacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32

    /// Distance from content to the edge of the screen. Wider at regular width.
    static func screenEdge(_ sizeClass: UserInterfaceSizeClass?) -> CGFloat {
        sizeClass == .regular ? xl : lg
    }

    /// Horizontal gap between cards in the catalog grid.
    static let gridGutter: CGFloat = 12

    /// Vertical gap between rows — larger than the gutter, to separate captions from the art below.
    static let gridRowGap: CGFloat = 16

    /// Minimum interactive target. 48 in-stream, where a thumb is already gripping a controller.
    static let hitTarget: CGFloat = 44
    static let hitTargetInStream: CGFloat = 48
}

/// Six radii, matching Android's set exactly.
enum OpenNOWRadius {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24

    /// Cards, panels, the standard container radius.
    static let card: CGFloat = 12
}

/// Duration and easing tokens, replacing values picked independently across the UI.
enum OpenNOWMotion {
    /// Press, toggle, selection — anything that must feel instantaneous.
    static let fast = Animation.easeOut(duration: 0.12)

    /// Focus, hover, chip and tab changes, panel slide.
    static let standard = Animation.spring(response: 0.26, dampingFraction: 0.9)

    /// Sheets and page transitions, where the movement itself carries meaning.
    static let emphasized = Animation.spring(response: 0.42, dampingFraction: 0.86)

    /// Rolling integers — queue position, timers.
    static let numeric = Animation.spring(response: 0.32, dampingFraction: 0.82)

    static let shimmerDuration: Double = 1.1
    static let carouselAdvanceSeconds: Double = 6
    static let bannerDismissSeconds: Double = 3.2
}

// MARK: - Typography

/// Semantic styles only. Every one is a `Font.TextStyle` so Dynamic Type works without
/// intervention; the two fixed sizes in the app (queue numeral, score ring) use `@ScaledMetric`
/// with a cap instead.
enum OpenNOWFont {
    static let screenTitle = Font.largeTitle.bold()
    static let sectionHead = Font.title3.weight(.semibold)
    static let cardTitle = Font.subheadline.weight(.semibold)
    static let body = Font.body
    static let rowLabel = Font.body
    static let rowFootnote = Font.footnote
    static let overline = Font.caption2.weight(.semibold)

    /// Anything that ticks in place. Proportional digits make a 60 fps counter visibly jitter
    /// thirty times a second, which reads as instability in the stream itself.
    static let telemetry = Font.system(.footnote, design: .monospaced)
    static let telemetryCompact = Font.system(.caption2, design: .monospaced)
    static let telemetryLarge = Font.system(.callout, design: .monospaced)
}

extension View {
    /// Tabular figures for a value that updates in place.
    func openNowNumeric() -> some View {
        self.monospacedDigit()
    }
}

// MARK: - Environment

private struct OpenNOWAccentKey: EnvironmentKey {
    static let defaultValue: UIAccent = .openNow
}

private struct OpenNOWExpressiveKey: EnvironmentKey {
    static let defaultValue = true
}

private struct OpenNOWLiveOutlinesKey: EnvironmentKey {
    static let defaultValue = true
}

extension EnvironmentValues {
    /// The user's chosen accent. Read this rather than the `brandAccent` global in new code.
    var openNowAccent: UIAccent {
        get { self[OpenNOWAccentKey.self] }
        set { self[OpenNOWAccentKey.self] = newValue }
    }

    /// Expressive UI: gradients, ambient washes, the softer card treatment. Off gives a flatter,
    /// cheaper-to-render surface.
    var openNowExpressive: Bool {
        get { self[OpenNOWExpressiveKey.self] }
        set { self[OpenNOWExpressiveKey.self] = newValue }
    }

    /// Animated active-state frames on navigation, game details, server choice and theme pickers.
    var openNowLiveOutlines: Bool {
        get { self[OpenNOWLiveOutlinesKey.self] }
        set { self[OpenNOWLiveOutlinesKey.self] = newValue }
    }
}

extension View {
    /// Applies the whole theme in one place: accent tint plus the two appearance switches.
    func openNowTheme(_ settings: AppSettings) -> some View {
        self
            .environment(\.openNowAccent, settings.uiAccent)
            .environment(\.openNowExpressive, settings.expressiveUI)
            .environment(\.openNowLiveOutlines, settings.liveSelectedOutlines)
            .tint(settings.uiAccent.color)
    }
}

// MARK: - Shared modifiers

extension View {
    /// Guarantees WCAG 2.5.8 / HIG minimum target size without every call site inventing its own
    /// padding. Applies to the hit region only — the drawn glyph keeps its own size.
    func minimumHitTarget(_ size: CGFloat = OpenNOWSpacing.hitTarget) -> some View {
        self
            .frame(minWidth: size, minHeight: size)
            .contentShape(Rectangle())
    }

    /// Standard card container: raised surface, 12 pt continuous radius, hairline edge.
    func openNowCard(
        fill: Color = OpenNOWPalette.surfaceRaised,
        radius: CGFloat = OpenNOWRadius.card,
        bordered: Bool = false
    ) -> some View {
        self
            .background(fill, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay {
                if bordered {
                    RoundedRectangle(cornerRadius: radius, style: .continuous)
                        .strokeBorder(OpenNOWPalette.hairline, lineWidth: 1)
                }
            }
    }

    /// Controller focus ring. Appears only after the first controller event; see
    /// `ControllerFocusCoordinator`. Two points at the element's own radius, plus a small scale
    /// so the ring reads even when the accent is close to the artwork behind it.
    func openNowFocusRing(
        _ focused: Bool,
        accent: Color,
        radius: CGFloat = OpenNOWRadius.card,
        scale: CGFloat = 1.04
    ) -> some View {
        self
            .overlay {
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .strokeBorder(accent, lineWidth: focused ? 2 : 0)
            }
            .scaleEffect(focused ? scale : 1)
            .animation(OpenNOWMotion.standard, value: focused)
    }

    /// Honours Reduce Transparency by swapping a material for an opaque surface. A blur of a
    /// moving 60 fps frame also costs GPU time the decoder needs, so anything over live video
    /// passes `overVideo: true` and never gets a material at all.
    @ViewBuilder
    func openNowPanelBackground(overVideo: Bool, reduceTransparency: Bool, radius: CGFloat = OpenNOWRadius.lg) -> some View {
        if overVideo || reduceTransparency {
            self.background(
                overVideo ? OpenNOWPalette.panelOverVideo : OpenNOWPalette.surfaceRaised,
                in: RoundedRectangle(cornerRadius: radius, style: .continuous)
            )
        } else {
            self.background(.regularMaterial, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
        }
    }
}

// Per-metric quality readings live in `StreamQuality` (SessionReport.swift), built from the same
// score ladders the session report uses. There is deliberately no second set of thresholds here:
// an earlier draft had one, and it let the in-stream HUD call a session bad while the report that
// followed it called the same session Good.
