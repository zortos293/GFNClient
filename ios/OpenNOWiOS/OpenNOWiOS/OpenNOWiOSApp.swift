import SwiftUI
import UIKit

final class OpenNOWAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        OpenNOWImageCache.configureURLCache()
        if ProcessInfo.processInfo.arguments.contains("--opennow-streamer-self-test") {
            NativeStreamSelfTest.run()
        }
        return true
    }

    func applicationDidReceiveMemoryWarning(_ application: UIApplication) {
        OpenNOWImageCache.shared.removeAll()
    }

}

@main
struct OpenNOWiOSApp: App {
    @UIApplicationDelegateAdaptor(OpenNOWAppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var store = OpenNOWStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .task {
                    store.handleScenePhase(scenePhase)
                }
                .onChangeCompat(of: scenePhase) { newPhase in
                    store.handleScenePhase(newPhase)
                }
                .onOpenURL { url in
                    store.handleIncomingURL(url)
                }
        }
    }
}
