import SwiftUI
#if canImport(Charts)
import Charts
#endif

/// S9 — the post-session report.
///
/// Its job is to turn "it felt laggy" into something a person can act on. That means three things
/// in order: a single score they can react to, the measurements behind it, and named suggestions.
/// Everything is derived — nothing here is invented to fill space.
struct SessionReportView: View {
    let report: SessionReport
    var onReportProblem: (() -> Void)? = nil
    let onDismiss: (Bool) -> Void

    @Environment(\.openNowAccent) private var accent
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dontShowAgain = false
    @State private var ringProgress: Double = 0

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: OpenNOWSpacing.xl) {
                    scoreHeader
                    if report.limitedData { limitedDataNotice }
                    if report.showsTrendChart { trendSection }
                    measurementsSection
                    if !report.downgrades.isEmpty { findingsSection("What happened", report.downgrades) }
                    findingsSection("Suggestions", report.recommendations)
                    footerControls
                }
                .padding(.horizontal, OpenNOWSpacing.lg)
                .padding(.vertical, OpenNOWSpacing.lg)
            }
            .navigationTitle("Session Report")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { onDismiss(dontShowAgain) }
                }
            }
        }
        .task {
            guard !reduceMotion else { ringProgress = Double(report.score) / 100; return }
            withAnimation(.easeOut(duration: 0.8)) {
                ringProgress = Double(report.score) / 100
            }
        }
    }

    // MARK: Score

    private var scoreHeader: some View {
        VStack(spacing: OpenNOWSpacing.md) {
            ZStack {
                Circle()
                    .stroke(OpenNOWPalette.surfaceInset, lineWidth: 8)
                Circle()
                    .trim(from: 0, to: ringProgress)
                    .stroke(ringTint, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                VStack(spacing: 0) {
                    Text("\(report.score)")
                        .font(.system(size: 40, weight: .heavy, design: .rounded))
                        .monospacedDigit()
                    Text(report.rating.label)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: 116, height: 116)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Session quality")
            .accessibilityValue("\(report.score) out of 100, \(report.rating.label)")

            VStack(spacing: 2) {
                Text(report.gameTitle)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                Text("\(durationLabel) · \(report.networkKind.label)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var ringTint: Color {
        report.rating.qualityLevel.tint ?? accent.color
    }

    private var durationLabel: String {
        let hours = report.durationSeconds / 3_600
        let minutes = (report.durationSeconds % 3_600) / 60
        if hours > 0 { return "\(hours) hr \(minutes) min" }
        if minutes > 0 { return "\(minutes) min" }
        return "\(report.durationSeconds) sec"
    }

    private var limitedDataNotice: some View {
        Label(
            "This session was too short to measure properly, so treat the score as a rough reading.",
            systemImage: "info.circle"
        )
        .font(.footnote)
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(OpenNOWSpacing.md)
        .openNowCard(fill: OpenNOWPalette.surfaceInset, radius: OpenNOWRadius.sm)
    }

    // MARK: Trend

    private var trendSection: some View {
        VStack(alignment: .leading, spacing: OpenNOWSpacing.sm) {
            sectionHeader("Latency over time")
            #if canImport(Charts)
            if #available(iOS 16.0, *) {
                Chart(report.trend) { point in
                    AreaMark(
                        x: .value("Time", point.offsetSeconds),
                        y: .value("Latency", point.pingMs)
                    )
                    .foregroundStyle(
                        LinearGradient(
                            colors: [accent.color.opacity(0.28), accent.color.opacity(0.02)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    LineMark(
                        x: .value("Time", point.offsetSeconds),
                        y: .value("Latency", point.pingMs)
                    )
                    .foregroundStyle(accent.color)
                    .lineStyle(StrokeStyle(lineWidth: 1.5))
                    .interpolationMethod(.monotone)
                }
                .chartYAxis {
                    AxisMarks(position: .leading, values: .automatic(desiredCount: 3)) { value in
                        AxisGridLine().foregroundStyle(OpenNOWPalette.hairline)
                        AxisValueLabel {
                            if let ms = value.as(Int.self) {
                                Text("\(ms)").font(.caption2).monospacedDigit()
                            }
                        }
                    }
                }
                .chartXAxis {
                    AxisMarks(values: .automatic(desiredCount: 3)) { value in
                        AxisValueLabel {
                            if let seconds = value.as(Int.self) {
                                Text("\(seconds / 60)m").font(.caption2).monospacedDigit()
                            }
                        }
                    }
                }
                .frame(height: 132)
                .accessibilityLabel("Latency over the session, in milliseconds")
                .accessibilityValue(trendAccessibilitySummary)
            }
            #endif
        }
    }

    private var trendAccessibilitySummary: String {
        guard let first = report.trend.first, let last = report.trend.last else { return "No data" }
        let peak = report.trend.max { $0.pingMs < $1.pingMs }?.pingMs ?? last.pingMs
        return "Started at \(first.pingMs) milliseconds, peaked at \(peak), ended at \(last.pingMs)."
    }

    // MARK: Measurements

    private var measurementsSection: some View {
        VStack(alignment: .leading, spacing: OpenNOWSpacing.sm) {
            sectionHeader("Measurements")
            VStack(spacing: 0) {
                if let avg = report.averagePingMs {
                    measurementRow(
                        "Latency",
                        primary: "\(avg) ms",
                        secondary: report.peakPingMs.map { "peak \($0) ms" },
                        level: StreamQuality.latency(avg)
                    )
                }
                if let loss = report.packetLossPercent {
                    measurementRow(
                        "Packet loss",
                        primary: String(format: "%.1f%%", loss),
                        secondary: report.peakPacketLossPercent.map { String(format: "peak %.1f%%", $0) },
                        level: StreamQuality.packetLoss(loss)
                    )
                }
                if let jitter = report.averageJitterMs {
                    measurementRow(
                        "Jitter",
                        primary: String(format: "%.1f ms", jitter),
                        secondary: nil,
                        level: StreamQuality.jitter(jitter)
                    )
                }
                if let fps = report.averageFps {
                    measurementRow(
                        "Frame rate",
                        primary: String(format: "%.0f fps", fps),
                        secondary: report.lowestFps.map { "low \($0) fps" },
                        level: StreamQuality.frameRate(fps, targetFps: report.targetFps)
                    )
                }
                if let decode = report.averageDecodeMs {
                    measurementRow(
                        "Decode",
                        primary: String(format: "%.1f ms", decode),
                        secondary: report.peakDecodeMs.map { String(format: "peak %.1f ms", $0) },
                        level: StreamQuality.decode(decode, targetFps: report.targetFps, actualFps: report.averageFps)
                    )
                }
                if let bitrate = report.averageBitrateKbps {
                    measurementRow(
                        "Bitrate",
                        primary: String(format: "%.1f Mb/s", Double(bitrate) / 1_000),
                        secondary: report.peakBitrateKbps.map { String(format: "peak %.1f Mb/s", Double($0) / 1_000) },
                        level: .good
                    )
                }
                measurementRow(
                    "Resolution",
                    primary: report.deliveredResolution ?? report.requestedResolution,
                    secondary: deliveredDiffersFromRequest ? "asked for \(report.requestedResolution)" : nil,
                    level: deliveredDiffersFromRequest ? .fair : .good,
                    showsDivider: false
                )
            }
            .openNowCard()
        }
    }

    private var deliveredDiffersFromRequest: Bool {
        guard let delivered = report.deliveredResolution else { return false }
        return StreamSessionReportAccumulator.normalizedResolutionLabel(delivered) != report.requestedResolution
    }

    @ViewBuilder
    private func measurementRow(
        _ title: String,
        primary: String,
        secondary: String?,
        level: StreamQualityLevel,
        showsDivider: Bool = true
    ) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: OpenNOWSpacing.sm) {
                Text(title)
                Spacer(minLength: OpenNOWSpacing.md)
                if let secondary {
                    Text(secondary)
                        .font(.footnote)
                        .foregroundStyle(.tertiary)
                        .monospacedDigit()
                }
                HStack(spacing: 4) {
                    // Colour is never the only signal — a glyph carries the same reading for
                    // anyone using Differentiate Without Color.
                    if let glyph = level.glyph {
                        Image(systemName: glyph)
                            .font(.caption2)
                    }
                    Text(primary)
                        .monospacedDigit()
                        .fontWeight(.semibold)
                }
                .foregroundStyle(level.tint ?? Color.primary)
            }
            .padding(.horizontal, OpenNOWSpacing.lg)
            .padding(.vertical, 11)
            .accessibilityElement(children: .combine)
            .accessibilityLabel(title)
            .accessibilityValue([primary, secondary, level == .good ? nil : level.label]
                .compactMap { $0 }
                .joined(separator: ", "))

            if showsDivider {
                Divider().padding(.leading, OpenNOWSpacing.lg)
            }
        }
    }

    // MARK: Findings

    private func findingsSection(_ title: String, _ findings: [SessionReportFinding]) -> some View {
        VStack(alignment: .leading, spacing: OpenNOWSpacing.sm) {
            sectionHeader(title)
            VStack(alignment: .leading, spacing: OpenNOWSpacing.md) {
                ForEach(findings) { finding in
                    HStack(alignment: .top, spacing: OpenNOWSpacing.md) {
                        Image(systemName: finding.kind == .warning ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                            .font(.footnote)
                            .foregroundStyle(finding.kind == .warning ? OpenNOWPalette.statusFair : accent.color)
                            .padding(.top, 2)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(finding.title).font(.subheadline.weight(.semibold))
                            Text(finding.detail)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(OpenNOWSpacing.lg)
            .openNowCard()
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title.uppercased())
            .font(OpenNOWFont.overline)
            .kerning(0.6)
            .foregroundStyle(.secondary)
    }

    // MARK: Footer

    private var footerControls: some View {
        VStack(spacing: OpenNOWSpacing.md) {
            if let onReportProblem {
                Button {
                    onReportProblem()
                } label: {
                    Label("Report a Problem", systemImage: "ladybug")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
            }

            Toggle("Don't show this after every session", isOn: $dontShowAgain)
                .font(.footnote)
                .tint(accent.color)
        }
    }
}
