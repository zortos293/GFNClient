#if os(tvOS)
import Foundation

actor NotificationManager {
    static let shared = NotificationManager()

    func requestPermission() async {
    }

    func sendQueueReadyNotification(gameTitle: String) async {
    }

    func sendQueueSetupNotification(gameTitle: String) async {
    }

    func cancelSessionNotifications() {
    }
}
#else
import Foundation
import UserNotifications

actor NotificationManager {
    static let shared = NotificationManager()

    private let readyNotificationId = "com.opencloudgaming.opennow.sessionReady"
    private let setupNotificationId = "com.opencloudgaming.opennow.seatSetup"

    func requestPermission() async {
        _ = await ensureAuthorization()
    }

    func sendQueueReadyNotification(gameTitle: String) async {
        guard await ensureAuthorization() else { return }

        let content = UNMutableNotificationContent()
        content.title = "Session Ready!"
        content.body = "\(gameTitle) is ready to stream. Tap to play."
        content.sound = .default
        content.interruptionLevel = .active
        let request = UNNotificationRequest(identifier: readyNotificationId, content: content, trigger: nil)
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            notificationCenter.add(request) { _ in
                continuation.resume()
            }
        }
    }

    func sendQueueSetupNotification(gameTitle: String) async {
        guard await ensureAuthorization() else { return }

        let content = UNMutableNotificationContent()
        // "Seat allocated" is NVIDIA's internal vocabulary for its own allocator, not something a
        // player asked about. What they want to know is how much longer, so say that instead.
        content.title = "Almost ready!"
        content.body = "\(gameTitle) is starting up — we'll let you know the moment it's ready."
        content.sound = .default
        content.interruptionLevel = .active
        let request = UNNotificationRequest(identifier: setupNotificationId, content: content, trigger: nil)
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            notificationCenter.add(request) { _ in
                continuation.resume()
            }
        }
    }

    func cancelSessionNotifications() {
        notificationCenter.removeDeliveredNotifications(withIdentifiers: [readyNotificationId, setupNotificationId])
        notificationCenter.removePendingNotificationRequests(withIdentifiers: [readyNotificationId, setupNotificationId])
    }

    private var notificationCenter: UNUserNotificationCenter {
        UNUserNotificationCenter.current()
    }

    private func ensureAuthorization() async -> Bool {
        let settings = await notificationCenter.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined:
            return (try? await notificationCenter.requestAuthorization(options: [.alert, .sound])) == true
        case .denied:
            return false
        @unknown default:
            return false
        }
    }
}
#endif
