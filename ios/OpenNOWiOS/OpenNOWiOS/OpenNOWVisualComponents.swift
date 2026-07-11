import SwiftUI

struct SkeletonShimmerModifier: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .overlay {
                GeometryReader { proxy in
                    LinearGradient(
                        colors: [
                            .clear,
                            Color.white.opacity(0.08),
                            Color.white.opacity(0.34),
                            Color.white.opacity(0.08),
                            .clear
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .rotationEffect(.degrees(18))
                    .frame(width: max(80, proxy.size.width * 0.48))
                    .offset(x: phase * (proxy.size.width * 1.7))
                    .blendMode(.screen)
                }
                .allowsHitTesting(false)
            }
            .mask(content)
            .onAppear {
                guard !reduceMotion else {
                    phase = 0
                    return
                }
                phase = -1
                withAnimation(.linear(duration: 1.35).repeatForever(autoreverses: false)) {
                    phase = 1.4
                }
            }
    }
}

struct GlassCardModifier: ViewModifier {
    let cornerRadius: CGFloat

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content
                .glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
        } else {
            content
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .stroke(Color.white.opacity(0.10), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.10), radius: 8, y: 4)
        }
    }
}

private struct AdaptiveGlassButtonModifier: ViewModifier {
    let prominent: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            if prominent {
                content.buttonStyle(.glassProminent)
            } else {
                content.buttonStyle(.glass)
            }
        } else if prominent {
            content.buttonStyle(.borderedProminent)
        } else {
            content.buttonStyle(.bordered)
        }
    }
}

extension View {
    func adaptiveGlassButton(prominent: Bool = false) -> some View {
        modifier(AdaptiveGlassButtonModifier(prominent: prominent))
    }
}
