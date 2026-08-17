import SwiftUI

/// The ambient wash behind the queue screen.
///
/// Rewritten for cost. The previous version put a 52-point `.blur` on two full-screen circles —
/// two offscreen render passes per frame — and took `queuePosition` as a parameter, so the whole
/// thing invalidated on every poll and restarted its own drift animation mid-flight. On a phone
/// holding a WebRTC connection open, that was most of the jank.
///
/// Now: no blur (a radial gradient is already soft, so the blur was redundant), no dependency on
/// anything that changes while queueing, and the drift comes from the clock rather than `@State`
/// so a parent re-render cannot interrupt it.
struct QueueAmbientBackdrop: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let accent: Color

    /// Slow enough to be felt rather than watched.
    private let driftPeriod: TimeInterval = 26

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 20.0, paused: reduceMotion)) { context in
            Canvas { canvas, size in
                canvas.fill(
                    Path(CGRect(origin: .zero, size: size)),
                    with: .linearGradient(
                        Gradient(colors: [
                            .black,
                            Color(red: 0.02, green: 0.035, blue: 0.045),
                            .black
                        ]),
                        startPoint: .zero,
                        endPoint: CGPoint(x: 0, y: size.height)
                    )
                )

                let drift = driftPhase(at: context.date)
                orb(
                    in: canvas,
                    color: accent,
                    diameter: min(size.width, size.height) * 1.5,
                    centre: CGPoint(
                        x: size.width * (0.26 + 0.08 * drift),
                        y: size.height * (0.30 + 0.10 * drift)
                    ),
                    peak: 0.30
                )
                orb(
                    in: canvas,
                    color: Color(red: 0.17, green: 0.86, blue: 1),
                    diameter: min(size.width, size.height) * 1.1,
                    centre: CGPoint(
                        x: size.width * (0.72 - 0.10 * drift),
                        y: size.height * (0.68 - 0.08 * drift)
                    ),
                    peak: 0.20
                )

                canvas.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.black.opacity(0.22)))
            }
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }

    /// A soft disc drawn as a multi-stop radial gradient. Same look as a blurred circle, one pass
    /// instead of two.
    private func orb(in canvas: GraphicsContext, color: Color, diameter: CGFloat, centre: CGPoint, peak: Double) {
        let rect = CGRect(
            x: centre.x - diameter / 2,
            y: centre.y - diameter / 2,
            width: diameter,
            height: diameter
        )
        canvas.fill(
            Path(ellipseIn: rect),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: color.opacity(peak), location: 0),
                    .init(color: color.opacity(peak * 0.45), location: 0.35),
                    .init(color: color.opacity(peak * 0.12), location: 0.65),
                    .init(color: .clear, location: 1)
                ]),
                center: centre,
                startRadius: 0,
                endRadius: diameter / 2
            )
        )
    }

    private func driftPhase(at date: Date) -> CGFloat {
        guard !reduceMotion else { return 0.5 }
        let phase = date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: driftPeriod) / driftPeriod
        let triangle = phase < 0.5 ? phase * 2 : (1 - phase) * 2
        return CGFloat(triangle * triangle * (3 - 2 * triangle))
    }
}

struct QueuePositionDisplay: View {
    let position: Int
    var compact = false

    var body: some View {
        VStack(spacing: compact ? 2 : 5) {
            Text(position == 1 ? "NEXT IN QUEUE" : "QUEUE POSITION")
                .font((compact ? Font.caption2 : .caption).weight(.bold))
                .tracking(1.2)
                .foregroundStyle(.white.opacity(0.62))
            Text(position == 1 ? "NEXT" : String(position))
                .font(compact ? .title2.weight(.black) : .system(size: 54, weight: .black, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(urgencyColor)
                .numericQueueTransition(value: position)
                .shadow(color: urgencyColor.opacity(0.42), radius: compact ? 8 : 18)
        }
        .padding(.horizontal, compact ? 14 : 24)
        .padding(.vertical, compact ? 9 : 15)
        .frame(maxWidth: compact ? 220 : 360)
        .glassCard(cornerRadius: compact ? 15 : 22)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(position == 1 ? "Next in queue" : "Queue position \(position)")
    }

    private var urgencyColor: Color {
        switch position {
        case 1...9: return Color(red: 1, green: 0.54, blue: 0.30)
        case 10...29: return Color(red: 1, green: 0.80, blue: 0.33)
        default: return Color(red: 0.45, green: 0.82, blue: 1)
        }
    }
}

struct AndroidQueueStatusText: View {
    let text: String
    let position: Int?
    var compact = false

    var body: some View {
        Group {
            if let position {
                HStack(spacing: 0) {
                    Text(prefix(for: position))
                        .foregroundStyle(.secondary)
                    Text(String(position))
                        .foregroundStyle(urgencyColor(for: position))
                        .monospacedDigit()
                        .numericQueueTransition(value: position)
                        .shadow(
                            color: urgencyColor(for: position).opacity(position < 10 ? 0.55 : 0),
                            radius: position < 10 ? 14 : 0
                        )
                    Text(suffix(for: position))
                        .foregroundStyle(.secondary)
                }
            } else {
                Text(text)
                    .foregroundStyle(text.localizedCaseInsensitiveCompare("Starting session") == .orderedSame ? brandAccent : .secondary)
            }
        }
        .font((compact ? Font.body : .title3).weight(.regular))
        .multilineTextAlignment(.center)
        .lineLimit(2)
        .minimumScaleFactor(0.8)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(text)
    }

    private func prefix(for position: Int) -> String {
        guard let range = text.range(of: String(position)) else { return "\(text) " }
        return String(text[..<range.lowerBound])
    }

    private func suffix(for position: Int) -> String {
        guard let range = text.range(of: String(position)) else { return "" }
        return String(text[range.upperBound...])
    }

    private func urgencyColor(for position: Int) -> Color {
        guard position < 10 else { return .secondary }
        let heat = Double(10 - max(1, position)) / 9
        return Color(
            red: 1,
            green: max(0.06, 0.57 - 0.49 * heat),
            blue: max(0.08, 0.25 - 0.17 * heat)
        )
    }
}

/// The indeterminate bar shown while waiting for a rig.
///
/// Driven by `TimelineView` rather than a `@State` toggle with `repeatForever`. The queue screen
/// observes the whole store, so it re-renders on every poll, every trend sample and every ad-state
/// update — and each of those re-evaluated the implicit animation and restarted it mid-flight,
/// which is what made the bar stutter. A timeline computes position from the clock instead, so
/// nothing the parent does can interrupt it.
///
/// It also draws in a single `Canvas` pass: no `GeometryReader`, no per-frame layout.
struct OscillatingQueueProgressView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let cycle: TimeInterval = 2.0
    private let barHeight: CGFloat = 8
    private let travellerFraction: CGFloat = 0.32

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reduceMotion)) { context in
            Canvas { canvas, size in
                let radius = barHeight / 2
                let track = CGRect(
                    x: 0,
                    y: (size.height - barHeight) / 2,
                    width: size.width,
                    height: barHeight
                )
                canvas.fill(
                    Path(roundedRect: track, cornerRadius: radius),
                    with: .color(.secondary.opacity(0.28))
                )

                let travellerWidth = size.width * travellerFraction
                let travelSpan = max(0, size.width - travellerWidth)
                let x = travelSpan * offsetFraction(at: context.date)

                canvas.fill(
                    Path(roundedRect: CGRect(x: x, y: track.minY, width: travellerWidth, height: barHeight),
                         cornerRadius: radius),
                    with: .color(brandAccent)
                )
            }
        }
        .frame(height: barHeight)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Queue progress")
        .accessibilityValue("Waiting for a gaming rig")
    }

    /// Ping-pongs 0…1…0 across one cycle, eased so the turn at each end is not abrupt.
    private func offsetFraction(at date: Date) -> CGFloat {
        // Parked mid-track under Reduce Motion: still reads as "busy", never moves.
        guard !reduceMotion else { return 0.5 }
        let phase = date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: cycle) / cycle
        let triangle = phase < 0.5 ? phase * 2 : (1 - phase) * 2
        // Smoothstep, which is the cheap equivalent of easeInOut without an Animation object.
        return CGFloat(triangle * triangle * (3 - 2 * triangle))
    }
}
