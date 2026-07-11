import SwiftUI

struct QueueAmbientBackdrop: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drift = false

    let accent: Color
    let queuePosition: Int?

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                LinearGradient(
                    colors: [Color.black, Color(red: 0.02, green: 0.035, blue: 0.045), Color.black],
                    startPoint: .top,
                    endPoint: .bottom
                )
                ambientOrb(color: accent, size: min(proxy.size.width, proxy.size.height) * 0.92, opacity: 0.52)
                    .offset(
                        x: proxy.size.width * (drift ? -0.08 : -0.24),
                        y: proxy.size.height * (drift ? 0.12 : 0.02)
                    )
                ambientOrb(
                    color: Color(red: 0.17, green: 0.86, blue: 1),
                    size: min(proxy.size.width, proxy.size.height) * 0.68,
                    opacity: 0.34
                )
                .offset(
                    x: proxy.size.width * (drift ? 0.23 : 0.10),
                    y: proxy.size.height * (drift ? 0.16 : 0.29)
                )
                QueueSignalField(accent: accent, queuePosition: queuePosition)
                Color.black.opacity(0.30)
            }
            .clipped()
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.easeInOut(duration: 13).repeatForever(autoreverses: true)) {
                    drift.toggle()
                }
            }
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }

    private func ambientOrb(color: Color, size: CGFloat, opacity: Double) -> some View {
        Circle()
            .fill(
                RadialGradient(
                    colors: [color.opacity(0.58), color.opacity(0.14), .clear],
                    center: .center,
                    startRadius: 0,
                    endRadius: size * 0.5
                )
            )
            .frame(width: size, height: size)
            .blur(radius: 52)
            .opacity(opacity)
    }
}

private struct QueueSignalField: View {
    let accent: Color
    let queuePosition: Int?

    var body: some View {
        Canvas { context, size in
            let urgency = queuePosition.map { 1 - min(Double($0), 50) / 50 } ?? 0
            let spacing = size.height / 9
            for index in 0...10 {
                let y = CGFloat(index) * spacing
                var path = Path()
                path.move(to: CGPoint(x: -size.width * 0.12, y: y))
                path.addLine(to: CGPoint(x: size.width * 1.08, y: y - size.height * 0.10))
                context.stroke(path, with: .color(accent.opacity(0.035 + urgency * 0.035)), lineWidth: 1)
            }
            for index in 1...12 {
                let x = CGFloat((index * 173) % 997) / 997 * size.width
                let y = CGFloat((index * 291) % 991) / 991 * size.height
                let diameter = CGFloat(2 + index % 4)
                context.fill(
                    Path(ellipseIn: CGRect(x: x, y: y, width: diameter, height: diameter)),
                    with: .color(accent.opacity(0.05 + urgency * 0.04))
                )
            }
        }
        .opacity(0.9)
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

struct OscillatingQueueProgressView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isAnimating = false

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.secondary.opacity(0.3))
                    .frame(height: 8)

                RoundedRectangle(cornerRadius: 4)
                    .fill(brandAccent)
                    .frame(width: geometry.size.width * 0.3, height: 8)
                    .offset(
                        x: reduceMotion
                            ? geometry.size.width * 0.35
                            : (isAnimating ? geometry.size.width * 0.7 : 0)
                    )
                    .animation(
                        reduceMotion
                            ? nil
                            : .easeInOut(duration: 1.0).repeatForever(autoreverses: true),
                        value: isAnimating
                    )
            }
        }
        .frame(height: 8)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Queue progress")
        .accessibilityValue("Waiting for a gaming rig")
        .onAppear {
            isAnimating = !reduceMotion
        }
        .onChangeCompat(of: reduceMotion) { enabled in
            isAnimating = !enabled
        }
    }
}
