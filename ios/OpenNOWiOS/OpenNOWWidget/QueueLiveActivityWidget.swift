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
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(alignment: .center, spacing: 8) {
                        LiveActivityAppIcon(size: 28)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(context.attributes.gameTitle)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                                .minimumScaleFactor(0.72)
                            Text(storeQueueLabel(for: context))
                                .font(.caption.weight(.medium))
                                .foregroundStyle(.white.opacity(0.78))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                    }
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
                    HStack(spacing: 8) {
                        Text(context.state.detail)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.white.opacity(0.92))
                            .lineLimit(1)
                            .minimumScaleFactor(0.78)
                        Spacer(minLength: 0)
                    }
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
            .contentMargins(.all, 10, for: .expanded)
            .contentMargins([.leading, .top, .bottom], 6, for: .compactLeading)
            .contentMargins([.trailing, .top, .bottom], 6, for: .compactTrailing)
            .contentMargins(.all, 6, for: .minimal)
            .keylineTint(color(for: context.state.phase))
        }
    }

    private func backgroundTint(for phase: QueueActivityAttributes.ContentState.Phase) -> Color {
        switch phase {
        case .queued:
            return Color(red: 0.14, green: 0.2, blue: 0.27)
        case .waiting:
            return Color(red: 0.22, green: 0.2, blue: 0.12)
        case .ready:
            return Color(red: 0.08, green: 0.29, blue: 0.19)
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
        HStack(alignment: .center, spacing: 14) {
            VStack(alignment: .leading, spacing: 6) {
                Text(context.attributes.gameTitle)
                    .font(.headline)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                Text(context.state.headline)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.88))
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)
                Text(context.state.detail)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.72))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Spacer(minLength: 12)
            QueueValueText(
                label: lockScreenValueLabel(for: context.state),
                phase: context.state.phase,
                size: .lockScreen
            )
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 18)
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

private struct LiveActivityAppIcon: View {
    let size: CGFloat

    var body: some View {
        Image("LiveActivityAppIcon")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.22, style: .continuous))
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
