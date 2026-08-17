#if canImport(ActivityKit)
import ActivityKit
import Foundation

struct QueueActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        enum Phase: String, Codable, Hashable {
            case queued
            case waiting
            case ready
        }

        let phase: Phase
        let headline: String
        let detail: String
        let queueLabel: String
        let queuePosition: Int?

        /// 0…1 through the wait, or nil when there is nothing honest to draw.
        ///
        /// Computed in the app, not the widget: only the app has seen where the queue started, and
        /// a widget that guessed would draw a bar that jumps backwards when the estimate changes.
        var progress: Double?

        init(
            phase: Phase,
            headline: String,
            detail: String,
            queueLabel: String,
            queuePosition: Int?,
            progress: Double? = nil
        ) {
            self.phase = phase
            self.headline = headline
            self.detail = detail
            self.queueLabel = queueLabel
            self.queuePosition = queuePosition
            self.progress = progress
        }
    }

    let sessionId: String
    let gameTitle: String
    let storeName: String?
}
#else
import Foundation

struct QueueActivityAttributes {
    struct ContentState: Codable, Hashable {
        enum Phase: String, Codable, Hashable {
            case queued
            case waiting
            case ready
        }

        let phase: Phase
        let headline: String
        let detail: String
        let queueLabel: String
        let queuePosition: Int?
        var progress: Double?

        init(
            phase: Phase,
            headline: String,
            detail: String,
            queueLabel: String,
            queuePosition: Int?,
            progress: Double? = nil
        ) {
            self.phase = phase
            self.headline = headline
            self.detail = detail
            self.queueLabel = queueLabel
            self.queuePosition = queuePosition
            self.progress = progress
        }
    }

    let sessionId: String
    let gameTitle: String
    let storeName: String?
}
#endif
