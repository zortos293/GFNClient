import ActivityKit
import Foundation
import SwiftUI
import WidgetKit

struct QueueLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: QueueActivityAttributes.self) { context in
            LockScreenQueueLiveActivityView(context: context)
                .activityBackgroundTint(backgroundTint(for: context.state.phase))
                .activitySystemActionForegroundColor(.white)
                .widgetURL(URL(string: "opennow://resume"))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    LiveActivityAppIcon(size: 24)
                        .padding(.leading, 2)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 1) {
                        Text(context.attributes.gameTitle)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.72)
                        Text(storeQueueLabel(for: context))
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(.white.opacity(0.72))
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if let queueLabel = queueNumberLabel(for: context.state) {
                        QueueValueText(
                            label: queueLabel,
                            phase: context.state.phase,
                            size: .expanded
                        )
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(context.state.detail)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.white.opacity(0.92))
                            .lineLimit(1)
                            .minimumScaleFactor(0.78)
                        LiveActivityPhaseTrack(phase: context.state.phase)
                    }
                    .padding(.horizontal, 2)
                }
            } compactLeading: {
                LiveActivityAppIcon(size: 21)
            } compactTrailing: {
                if let queueLabel = queueNumberLabel(for: context.state) {
                    QueueValueText(
                        label: queueLabel,
                        phase: context.state.phase,
                        size: .compact
                    )
                }
            } minimal: {
                LiveActivityAppIcon(size: 19)
            }
            .contentMargins(.horizontal, 20, for: .expanded)
            .contentMargins(.vertical, 12, for: .expanded)
            .contentMargins([.leading, .top, .bottom], 8, for: .compactLeading)
            .contentMargins([.trailing, .top, .bottom], 8, for: .compactTrailing)
            .contentMargins(.all, 8, for: .minimal)
            .keylineTint(color(for: context.state.phase))
            .widgetURL(URL(string: "opennow://resume"))
        }
    }

    private func backgroundTint(for phase: QueueActivityAttributes.ContentState.Phase) -> Color {
        switch phase {
        case .queued:
            return Color(red: 0.035, green: 0.075, blue: 0.105)
        case .waiting:
            return Color(red: 0.12, green: 0.10, blue: 0.035)
        case .ready:
            return Color(red: 0.025, green: 0.15, blue: 0.09)
        }
    }

    private func color(for phase: QueueActivityAttributes.ContentState.Phase) -> Color {
        switch phase {
        case .queued:
            return Color(red: 0.39, green: 0.71, blue: 1.0)
        case .waiting:
            return Color(red: 1.0, green: 0.8, blue: 0.33)
        case .ready:
            return Color(red: 0.45, green: 0.91, blue: 0.62)
        }
    }

    private func storeQueueLabel(for context: ActivityViewContext<QueueActivityAttributes>) -> String {
        let store = context.attributes.storeName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let displayStore = store.isEmpty ? "Store" : store
        return context.state.phase == .ready ? "\(displayStore) ready" : "\(displayStore) queue"
    }

    private func queueNumberLabel(for state: QueueActivityAttributes.ContentState) -> String? {
        state.queuePosition.map(String.init)
    }
}

private struct LockScreenQueueLiveActivityView: View {
    let context: ActivityViewContext<QueueActivityAttributes>

    var body: some View {
        VStack(spacing: 12) {
            HStack(alignment: .center, spacing: 12) {
                LiveActivityAppIcon(size: 38)
                VStack(alignment: .leading, spacing: 4) {
                    Text(context.attributes.gameTitle)
                        .font(.headline)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                    Text(context.state.headline)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.88))
                        .lineLimit(1)
                    Text(context.state.detail)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.72))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                Spacer(minLength: 10)
                QueueValueText(
                    label: lockScreenValueLabel(for: context.state),
                    phase: context.state.phase,
                    size: .lockScreen
                )
            }
            LiveActivityPhaseTrack(phase: context.state.phase)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
    }

    private func lockScreenValueLabel(for state: QueueActivityAttributes.ContentState) -> String {
        switch state.phase {
        case .queued:
            if let queue = state.queuePosition {
                return "#\(queue)"
            }
            return "QUEUE"
        case .waiting:
            return "WAIT"
        case .ready:
            return "READY"
        }
    }
}

private struct LiveActivityPhaseTrack: View {
    let phase: QueueActivityAttributes.ContentState.Phase

    var body: some View {
        HStack(spacing: 5) {
            ForEach(0..<3, id: \.self) { index in
                Capsule()
                    .fill(index <= activeIndex ? activeColor : Color.white.opacity(0.14))
                    .frame(maxWidth: .infinity)
                    .frame(height: 4)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Queue progress")
        .accessibilityValue(accessibilityValue)
    }

    private var activeIndex: Int {
        switch phase {
        case .queued: return 0
        case .waiting: return 1
        case .ready: return 2
        }
    }

    private var activeColor: Color {
        switch phase {
        case .queued: return Color(red: 0.45, green: 0.82, blue: 1)
        case .waiting: return Color(red: 1, green: 0.82, blue: 0.36)
        case .ready: return Color(red: 0.48, green: 0.94, blue: 0.65)
        }
    }

    private var accessibilityValue: String {
        switch phase {
        case .queued: return "In queue"
        case .waiting: return "Preparing rig"
        case .ready: return "Ready"
        }
    }
}

private struct LiveActivityAppIcon: View {
    let size: CGFloat

    var body: some View {
        Image("LiveActivityAppIcon")
            .resizable()
            .scaledToFill()
            .frame(width: size, height: size)
            .clipShape(Circle())
            .contentShape(Circle())
    }
}

private struct QueueValueText: View {
    enum Size {
        case compact
        case expanded
        case lockScreen
    }

    let label: String
    let phase: QueueActivityAttributes.ContentState.Phase
    let size: Size

    var body: some View {
        Text(label)
            .font(font)
            .monospacedDigit()
            .foregroundStyle(color)
            .lineLimit(1)
            .minimumScaleFactor(0.72)
            .frame(minWidth: minWidth, alignment: .trailing)
    }

    private var color: Color {
        switch phase {
        case .queued:
            return Color(red: 0.55, green: 0.82, blue: 1.0)
        case .waiting:
            return Color(red: 1.0, green: 0.83, blue: 0.4)
        case .ready:
            return Color(red: 0.55, green: 0.95, blue: 0.68)
        }
    }

    private var font: Font {
        switch size {
        case .compact:
            return .caption.weight(.bold)
        case .expanded:
            return .title3.weight(.bold)
        case .lockScreen:
            return .title3.weight(.bold)
        }
    }

    private var minWidth: CGFloat {
        switch size {
        case .compact:
            return 16
        case .expanded:
            return 24
        case .lockScreen:
            return 56
        }
    }
}
