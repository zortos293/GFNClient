import Foundation

#if canImport(AudioToolbox)
import AudioToolbox
#endif

/// Tells you the rig is ready when you are not looking at the screen.
///
/// This is the whole reason the setting exists: on the free tier a queue can run past twenty
/// minutes, and the only sensible thing to do is put the phone down. Without a cue you either
/// stare at it or miss the seat.
///
/// Uses a system sound rather than a bundled track. That is a deliberate limit — the equivalent
/// Android feature plays its own audio, but shipping a music file here would mean authoring one,
/// and a system chime does the actual job. It also respects the ringer switch, which a bundled
/// track played through the media session would not.
enum QueueReadyAlert {

    /// Rises through the ready transition once per session. Repeating it would train people to
    /// ignore it, and a session only becomes ready once.
    private static var announcedSessionIds = Set<String>()

    static func announceIfNeeded(sessionId: String, isReady: Bool, enabled: Bool) {
        guard enabled, isReady else { return }
        guard announcedSessionIds.insert(sessionId).inserted else { return }
        play()
    }

    /// Clears the record for a session that has ended, so a relaunch can chime again.
    static func forget(sessionId: String) {
        announcedSessionIds.remove(sessionId)
    }

    static func reset() {
        announcedSessionIds.removeAll()
    }

    private static func play() {
        #if canImport(AudioToolbox)
        // 1054 is the standard "alert" tone: short, unmistakable, and already familiar. Paired
        // with a success haptic so it lands even with the ringer off.
        AudioServicesPlaySystemSound(1054)
        #endif
        Haptics.success()
    }

    /// Exposed for tests: whether this session has already been announced.
    static func hasAnnounced(sessionId: String) -> Bool {
        announcedSessionIds.contains(sessionId)
    }
}
