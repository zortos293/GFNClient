import SwiftUI

/// One-time consent prompt, shown after the first successful sign-in.
///
/// Two things make this honest rather than a dark pattern: neither button is styled as the
/// obvious one, and the list below says exactly what is and is not collected. Declining is a
/// first-class outcome — the app is fully functional either way, and the copy says so.
struct AnalyticsConsentView: View {
    let onDecide: (Bool) -> Void

    @Environment(\.openNowAccent) private var accent

    var body: some View {
        VStack(alignment: .leading, spacing: OpenNOWSpacing.xl) {
            VStack(alignment: .leading, spacing: OpenNOWSpacing.sm) {
                Image(systemName: "chart.bar.xaxis")
                    .font(.system(size: 34, weight: .semibold))
                    .foregroundStyle(accent.color)
                    .accessibilityHidden(true)

                Text("Help improve OpenNOW?")
                    .font(.title2.bold())

                Text("OpenNOW is built by a couple of people in their spare time. Anonymous usage numbers tell us which settings actually get used, so we know what to fix first.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            VStack(alignment: .leading, spacing: OpenNOWSpacing.md) {
                row(
                    included: true,
                    "Which screens and stream settings get used",
                    detail: "Counts only — resolution, codec, how often the queue is cancelled."
                )
                row(
                    included: true,
                    "Crashes and stream failures",
                    detail: "So a bug that hits many people gets fixed before one that hits nobody."
                )
                row(
                    included: false,
                    "What you play",
                    detail: "Game titles, your library and your play history are never sent."
                )
                row(
                    included: false,
                    "Anything that identifies you",
                    detail: "No account details, no email, no IP address, no advertising ID."
                )
            }
            .padding(OpenNOWSpacing.lg)
            .openNowCard(fill: OpenNOWPalette.surfaceInset)

            Spacer(minLength: 0)

            VStack(spacing: OpenNOWSpacing.sm) {
                // Deliberately the same weight as each other. Consent that is visually
                // pre-selected is not consent.
                Button("Share Usage Data") { onDecide(true) }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                    .frame(maxWidth: .infinity)

                Button("Don't Share") { onDecide(false) }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                    .frame(maxWidth: .infinity)

                Text("You can change this any time in Settings → General → Privacy.")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 2)
            }
        }
        .padding(OpenNOWSpacing.xl)
        .presentationDetents([.large])
        // Choosing is the whole point of the screen, so there is no way to swipe past it —
        // but both outcomes are one tap away, which is what keeps that acceptable.
        .interactiveDismissDisabled()
    }

    private func row(included: Bool, _ title: String, detail: String) -> some View {
        HStack(alignment: .top, spacing: OpenNOWSpacing.md) {
            Image(systemName: included ? "checkmark.circle.fill" : "xmark.circle.fill")
                .foregroundStyle(included ? accent.color : OpenNOWPalette.textMuted)
                .font(.subheadline)
                .padding(.top, 1)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(included ? "Collected" : "Not collected"): \(title). \(detail)")
    }
}
