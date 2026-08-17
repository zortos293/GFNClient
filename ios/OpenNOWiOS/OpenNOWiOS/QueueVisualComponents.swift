import SwiftUI

/// The ambient wash behind the queue screen.
///
/// Rewritten twice, both times for cost. The first version put a 52-point `.blur` on two
/// full-screen circles and took `queuePosition` as a parameter, so it invalidated on every poll and
/// restarted its own drift animation mid-flight. The second replaced that with a full-screen
/// `Canvas`, which fixed the invalidation but still re-rasterised four full-screen fills — two of
/// them radial gradients — on the main thread twenty times a second, while the same thread was
/// holding a WebRTC connection open. That was the remaining jank.
///
/// This version draws the gradients once and only moves them. A `LinearGradient` view is
/// rasterised by Core Animation and an `.offset` is a layer transform, so drift costs a
/// recomposite rather than a redraw — the render server's job, not the main thread's. The phase
/// still comes from the clock rather than `@State`, so a parent re-render cannot interrupt it.
struct QueueAmbientBackdrop: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let accent: Color

    /// Slow enough to be felt rather than watched. At this period a 10 Hz tick is already finer
    /// than the eye can resolve, and the offsets it produces move under a point per frame.
    private let driftPeriod: TimeInterval = 26

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            let travel = min(size.width, size.height) * 0.1

            ZStack {
                LinearGradient(
                    colors: [
                        .black,
                        Color(red: 0.02, green: 0.035, blue: 0.045),
                        .black
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )

                TimelineView(.animation(minimumInterval: 1.0 / 10.0, paused: reduceMotion)) { context in
                    let drift = driftPhase(at: context.date)

                    ZStack {
                        orb(color: accent, diameter: min(size.width, size.height) * 1.5, peak: 0.30)
                            .position(x: size.width * 0.26, y: size.height * 0.30)
                            .offset(x: travel * drift, y: travel * 1.2 * drift)

                        orb(
                            color: Color(red: 0.17, green: 0.86, blue: 1),
                            diameter: min(size.width, size.height) * 1.1,
                            peak: 0.20
                        )
                        .position(x: size.width * 0.72, y: size.height * 0.68)
                        .offset(x: -travel * 1.2 * drift, y: -travel * drift)
                    }
                }
                // Decoration whose position already comes from the clock. Without this, an
                // implicit animation anywhere up the tree — the queue numeral's spring, say —
                // re-interpolates every offset the timeline produces and the drift stutters.
                .transaction { $0.animation = nil }

                Color.black.opacity(0.22)
            }
            .frame(width: size.width, height: size.height)
            .clipped()
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }

    /// A soft disc drawn as a multi-stop radial gradient. Same look as a blurred circle, without
    /// the offscreen pass a `.blur` needs.
    private func orb(color: Color, diameter: CGFloat, peak: Double) -> some View {
        Circle()
            .fill(
                RadialGradient(
                    gradient: Gradient(stops: [
                        .init(color: color.opacity(peak), location: 0),
                        .init(color: color.opacity(peak * 0.45), location: 0.35),
                        .init(color: color.opacity(peak * 0.12), location: 0.65),
                        .init(color: .clear, location: 1)
                    ]),
                    center: .center,
                    startRadius: 0,
                    endRadius: diameter / 2
                )
            )
            .frame(width: diameter, height: diameter)
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
/// Two things had to be true before this stopped stuttering, and only the first was fixed the last
/// time round:
///
/// 1. **Position comes from the clock, not from `@State`.** The queue screen observes the whole
///    store, so it re-renders on every poll, every trend sample and every ad-state update, and each
///    of those re-evaluated an implicit `repeatForever` animation and restarted it mid-flight.
/// 2. **No ancestor may re-interpolate that position.** The screen animates the queue numeral with
///    a spring, and an implicit animation applies to the whole subtree — so every offset the
///    timeline produced was being spring-interpolated towards the next one, which is exactly the
///    lag a clock-driven animation is supposed to make impossible. `.transaction` clears it.
///
/// The traveller is a plain `Capsule` moved with `.offset`, so a tick is a layer transform on the
/// render server rather than a `Canvas` redraw on the main thread — which matters on the one screen
/// that is also holding a WebRTC connection open.
struct OscillatingQueueProgressView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let cycle: TimeInterval = 2.0
    private let barHeight: CGFloat = 8
    private let travellerFraction: CGFloat = 0.32

    var body: some View {
        GeometryReader { proxy in
            let travellerWidth = proxy.size.width * travellerFraction
            let travelSpan = max(0, proxy.size.width - travellerWidth)

            ZStack(alignment: .leading) {
                Capsule(style: .continuous)
                    .fill(Color.secondary.opacity(0.28))

                TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reduceMotion)) { context in
                    Capsule(style: .continuous)
                        .fill(brandAccent)
                        .frame(width: travellerWidth)
                        .offset(x: travelSpan * offsetFraction(at: context.date))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .transaction { $0.animation = nil }
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
