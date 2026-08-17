#if os(tvOS)
enum Haptics {
    static func light() {}
    static func medium() {}
    static func selection() {}
    static func success() {}
    static func warning() {}
    static func error() {}
}
#else
import UIKit

/// Taptic feedback only on iPhone (skips iPad and Mac Catalyst).
enum Haptics {
    static var isIPhone: Bool {
        UIDevice.current.userInterfaceIdiom == .phone
    }

    static func light() {
        guard isIPhone else { return }
        let gen = UIImpactFeedbackGenerator(style: .light)
        gen.prepare()
        gen.impactOccurred()
    }

    static func medium() {
        guard isIPhone else { return }
        let gen = UIImpactFeedbackGenerator(style: .medium)
        gen.prepare()
        gen.impactOccurred()
    }

    static func selection() {
        guard isIPhone else { return }
        let gen = UISelectionFeedbackGenerator()
        gen.prepare()
        gen.selectionChanged()
    }

    static func notify(_ type: UINotificationFeedbackGenerator.FeedbackType) {
        guard isIPhone else { return }
        let gen = UINotificationFeedbackGenerator()
        gen.prepare()
        gen.notificationOccurred(type)
    }

    // Named outcomes rather than raw generator types, so a call site says what happened rather
    // than which haptic to play. Nothing here fires during a stream except session warnings and
    // transport loss — a vibration mid-game is indistinguishable from controller rumble.
    static func success() { notify(.success) }
    static func warning() { notify(.warning) }
    static func error() { notify(.error) }
}
#endif
