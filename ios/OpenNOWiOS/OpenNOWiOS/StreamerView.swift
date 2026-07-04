import Foundation
import SwiftUI

#if os(iOS)
import UIKit
#endif

#if os(iOS) && canImport(WebRTC)
import AVFoundation
import AVKit
import MetalKit
@preconcurrency import WebRTC
import os
#endif

struct StreamerView: View {
    #if os(iOS) && canImport(WebRTC)
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var coordinator: NativeStreamCoordinator

    init(
        session: ActiveSession,
        settings: AppSettings,
        nativeStreamerEnabled: Bool = true,
        onTouchLayoutChange: @escaping (String, TouchControlLayout) -> Void,
        onStreamerPreferencesChange: @escaping (StreamerPreferences) -> Void,
        onStatsOverlayChange: @escaping (Bool) -> Void = { _ in },
        onSafeVideoFallbackRequired: @escaping (String) -> Void,
        onNativeFallbackRequiresFreshEndpoint: @escaping (String) -> Void,
        onClose: @escaping () -> Void,
        onRetry: (() -> Void)? = nil
    ) {
        _coordinator = StateObject(
            wrappedValue: NativeStreamCoordinator(
                session: session,
                settings: settings,
                onTouchLayoutChange: onTouchLayoutChange,
                onStreamerPreferencesChange: onStreamerPreferencesChange,
                onStatsOverlayChange: onStatsOverlayChange,
                onSafeVideoFallbackRequired: onSafeVideoFallbackRequired,
                onClose: onClose,
                onRetry: onRetry
            )
        )
        _ = nativeStreamerEnabled
        _ = onNativeFallbackRequiresFreshEndpoint
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Color.black
                    .ignoresSafeArea()

                NativeStreamVideoView(coordinator: coordinator)
                    .ignoresSafeArea()

                NativeStreamTouchCaptureView(inputBridge: coordinator.inputBridge)
                    .ignoresSafeArea()

                VStack {
                    HStack(alignment: .top, spacing: 10) {
                        if coordinator.showStatsOverlay {
                            NativeStreamStatsPill(
                                gameTitle: coordinator.gameTitle,
                                status: coordinator.statusText,
                                snapshot: coordinator.statsSnapshot,
                                preferences: coordinator.streamerPreferences,
                                deviceStatus: coordinator.deviceStatus,
                                style: coordinator.statsDisplayStyle
                            )
                            .transition(.opacity.combined(with: .move(edge: .top)))
                        }

                        Spacer(minLength: 10)

                        if coordinator.pictureInPictureAvailable {
                            NativeStreamOverlayButton(
                                systemImage: coordinator.isPictureInPictureActive ? "pip.exit" : "pip.enter",
                                label: coordinator.isPictureInPictureActive ? "Stop Picture in Picture" : "Start Picture in Picture"
                            ) {
                                coordinator.togglePictureInPicture()
                            }
                        }

                        NativeStreamOverlayButton(systemImage: "slider.horizontal.3", label: "Stream controls") {
                            coordinator.toggleControlsPanel()
                        }
                    }
                    .padding(.top, max(22, proxy.safeAreaInsets.top + 8))
                    .padding(.horizontal, max(12, proxy.safeAreaInsets.leading + 12))
                    Spacer()
                }
                .frame(maxWidth: .infinity, alignment: .top)

                if coordinator.showStatusOverlay {
                    NativeStreamStatusOverlay(
                        status: coordinator.statusText,
                        detail: coordinator.detailText
                    )
                    .frame(maxWidth: min(proxy.size.width - 48, 420))
                    .allowsHitTesting(false)
                }

                if coordinator.controlsPanelVisible {
                    Color.black.opacity(0.24)
                        .ignoresSafeArea()
                        .onTapGesture { coordinator.setControlsPanelVisible(false) }

                    VStack {
                        Spacer()
                        NativeStreamControlsPanel(coordinator: coordinator)
                            .frame(maxWidth: min(proxy.size.width - 28, 390))
                            .frame(maxHeight: min(proxy.size.height * 0.72, 560))
                            .padding(.horizontal, 14)
                            .padding(.bottom, max(14, proxy.safeAreaInsets.bottom + 10))
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.easeInOut(duration: 0.18), value: coordinator.controlsPanelVisible)
            .animation(.easeInOut(duration: 0.18), value: coordinator.showStatsOverlay)
            .task(id: coordinator.sessionID) {
                coordinator.handleScenePhase(scenePhase)
                coordinator.start(viewportSize: proxy.size)
            }
            .onChangeCompat(of: scenePhase) { newPhase in
                coordinator.handleScenePhase(newPhase)
            }
            .onChangeCompat(of: proxy.size) { newSize in
                coordinator.updateViewportSize(newSize)
            }
            .onDisappear {
                coordinator.handleViewDisappear(scenePhase: scenePhase)
            }
            .statusBarHidden(true)
        }
    }
    #else
    private let session: ActiveSession
    private let onClose: () -> Void

    init(
        session: ActiveSession,
        settings: AppSettings,
        nativeStreamerEnabled: Bool = true,
        onTouchLayoutChange: @escaping (String, TouchControlLayout) -> Void,
        onStreamerPreferencesChange: @escaping (StreamerPreferences) -> Void,
        onStatsOverlayChange: @escaping (Bool) -> Void = { _ in },
        onSafeVideoFallbackRequired: @escaping (String) -> Void,
        onNativeFallbackRequiresFreshEndpoint: @escaping (String) -> Void,
        onClose: @escaping () -> Void,
        onRetry: (() -> Void)? = nil
    ) {
        self.session = session
        self.onClose = onClose
        _ = settings
        _ = nativeStreamerEnabled
        _ = onTouchLayoutChange
        _ = onStreamerPreferencesChange
        _ = onStatsOverlayChange
        _ = onSafeVideoFallbackRequired
        _ = onNativeFallbackRequiresFreshEndpoint
        _ = onRetry
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "display.trianglebadge.exclamationmark")
                    .font(.largeTitle)
                Text("Streaming Unavailable")
                    .font(.headline)
                Text("\(session.game.title) is ready, but this platform build does not include the iOS WebRTC runtime.")
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                Button("Close") {
                    onClose()
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
            .foregroundStyle(.white)
        }
    }
    #endif
}

#if os(iOS) && canImport(WebRTC)
private enum NativeStreamStatsDisplayStyle: String {
    case compact
    case detail

    var label: String {
        switch self {
        case .compact: return "Compact"
        case .detail: return "Detail"
        }
    }

    var next: NativeStreamStatsDisplayStyle {
        self == .compact ? .detail : .compact
    }
}

private struct NativeStreamStatsSnapshot: Equatable {
    var codec = "--"
    var resolution = "--"
    var fps: Int?
    var bitrateKbps: Int?
    var pingMs: Int?
    var decoded = 0
    var rendered = 0
    var dropped = 0
    var lossPercent = 0.0
    var jitterMs: Int?
    var inputSummary = "r0/p0"
    var detail = ""

    static let empty = NativeStreamStatsSnapshot()

    var fpsText: String {
        fps.map(String.init) ?? "--"
    }

    var bitrateText: String {
        guard let bitrateKbps else { return "--" }
        if bitrateKbps >= 1000 {
            return String(format: "%.1f Mbps", Double(bitrateKbps) / 1000.0)
        }
        return "\(bitrateKbps) Kbps"
    }

    var compactBitrateText: String {
        guard let bitrateKbps else { return "--" }
        if bitrateKbps >= 1000 {
            return String(format: "%.1fM", Double(bitrateKbps) / 1000.0)
        }
        return "\(bitrateKbps)K"
    }

    var pingText: String {
        pingMs.map { "\($0)ms" } ?? "--"
    }
}

private struct NativeStreamDeviceStatus: Equatable {
    var timeText: String = "--:--"
    var batteryPercent: Int?
    var batteryState: UIDevice.BatteryState = .unknown

    static func current() -> NativeStreamDeviceStatus {
        UIDevice.current.isBatteryMonitoringEnabled = true
        let level = UIDevice.current.batteryLevel
        let percent = level >= 0 ? Int((level * 100).rounded()) : nil
        return NativeStreamDeviceStatus(
            timeText: timeFormatter.string(from: Date()),
            batteryPercent: percent,
            batteryState: UIDevice.current.batteryState
        )
    }

    var batterySymbol: String {
        if batteryState == .charging || batteryState == .full {
            return "battery.100.bolt"
        }
        guard let batteryPercent else { return "battery.100" }
        switch batteryPercent {
        case 76...100: return "battery.100"
        case 51...75: return "battery.75"
        case 26...50: return "battery.50"
        case 1...25: return "battery.25"
        default: return "battery.0"
        }
    }

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter
    }()
}

private struct NativeStreamStatusOverlay: View {
    let status: String
    let detail: String

    var body: some View {
        VStack(spacing: 8) {
            ProgressView()
                .tint(.white)
            Text(status)
                .font(.headline)
            if !detail.isEmpty {
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.72))
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
            }
        }
        .foregroundStyle(.white)
        .padding(16)
        .background(.black.opacity(0.58), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private struct NativeStreamOverlayButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 42, height: 42)
        }
        .buttonStyle(.plain)
        .foregroundStyle(.white)
        .background(.black.opacity(0.52), in: Circle())
        .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
        .accessibilityLabel(label)
    }
}

private struct NativeStreamStatsPill: View {
    let gameTitle: String
    let status: String
    let snapshot: NativeStreamStatsSnapshot
    let preferences: StreamerPreferences
    let deviceStatus: NativeStreamDeviceStatus
    let style: NativeStreamStatsDisplayStyle

    var body: some View {
        if style == .compact {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 8) {
                    statusIndicators(showBatteryPercent: false)
                    Text("FPS \(snapshot.fpsText)")
                    Text(snapshot.pingText)
                    Text(snapshot.compactBitrateText)
                }
                HStack(spacing: 7) {
                    statusIndicators(showBatteryPercent: false)
                    Text("\(snapshot.fpsText) fps")
                    Text(snapshot.pingText)
                }
                HStack(spacing: 6) {
                    statusIndicators(showBatteryPercent: false)
                    Text("\(snapshot.fpsText) fps")
                }
            }
            .font(.caption2.weight(.semibold))
            .foregroundStyle(.white)
            .lineLimit(1)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(.black.opacity(0.50), in: Capsule())
            .overlay(Capsule().stroke(Color.white.opacity(0.10), lineWidth: 1))
        } else {
            VStack(alignment: .leading, spacing: 3) {
                Text(gameTitle)
                    .font(.caption.weight(.bold))
                    .lineLimit(1)
                Text("FPS \(snapshot.fpsText)  Bitrate \(snapshot.bitrateText)  Ping \(snapshot.pingText)  Codec \(snapshot.codec)")
                    .font(.caption2.monospaced())
                    .foregroundStyle(.white.opacity(0.76))
                    .lineLimit(1)
                Text("\(snapshot.resolution)  decoded \(snapshot.decoded) rendered \(snapshot.rendered) drop \(snapshot.dropped) loss \(String(format: "%.1f%%", snapshot.lossPercent))")
                    .font(.caption2.monospaced())
                    .foregroundStyle(.white.opacity(0.76))
                    .lineLimit(1)
                HStack(spacing: 8) {
                    statusIndicators(showBatteryPercent: true)
                    Text([status == "Streaming" ? nil : status, preferences.audioMuted ? "audio muted" : "audio on"].compactMap { $0 }.joined(separator: "  "))
                        .lineLimit(1)
                }
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.66))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .frame(maxWidth: 460, alignment: .leading)
            .background(.black.opacity(0.70), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Color.white.opacity(0.10), lineWidth: 1))
        }
    }

    @ViewBuilder
    private func statusIndicators(showBatteryPercent: Bool) -> some View {
        if preferences.showStatsClock {
            Text(deviceStatus.timeText)
        }
        if preferences.showStatsCellular {
            Image(systemName: "cellularbars")
                .accessibilityLabel("Cellular bars")
        }
        if preferences.showStatsBattery {
            HStack(spacing: 3) {
                Image(systemName: deviceStatus.batterySymbol)
                if showBatteryPercent, let batteryPercent = deviceStatus.batteryPercent {
                    Text("\(batteryPercent)%")
                }
            }
        }
    }
}

private struct NativeStreamControlsPanel: View {
    @ObservedObject var coordinator: NativeStreamCoordinator

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Stream Controls")
                            .font(.subheadline.weight(.bold))
                        Text(coordinator.gameTitle)
                            .font(.caption)
                            .foregroundStyle(Color.primary.opacity(0.72))
                            .lineLimit(1)
                    }

                    Spacer()

                    NativeStreamPanelIconButton(systemImage: "rectangle.portrait.and.arrow.right", label: "Exit stream") {
                        coordinator.close()
                    }

                    NativeStreamPanelIconButton(systemImage: "checkmark", label: "Done") {
                        coordinator.setControlsPanelVisible(false)
                    }
                }

                NativeStreamPanelSection(title: "Display") {
                    NativeStreamToggleRow(
                        title: "Audio",
                        value: coordinator.streamerPreferences.audioMuted ? "Muted" : "On",
                        isOn: Binding(
                            get: { !coordinator.streamerPreferences.audioMuted },
                            set: { coordinator.setAudioEnabled($0) }
                        )
                    )
                    NativeStreamToggleRow(
                        title: "Stream stats",
                        value: coordinator.showStatsOverlay ? "On" : "Off",
                        isOn: Binding(
                            get: { coordinator.showStatsOverlay },
                            set: { coordinator.setStatsOverlayVisible($0) }
                        )
                    )
                    NativeStreamActionRow(title: "Stats style", value: coordinator.statsDisplayStyle.label) {
                        coordinator.cycleStatsStyle()
                    }
                    if coordinator.pictureInPictureAvailable {
                        NativeStreamActionRow(
                            title: "Picture in Picture",
                            value: coordinator.isPictureInPictureActive ? "Active" : "Ready",
                            actionLabel: coordinator.isPictureInPictureActive ? "Stop" : "Start"
                        ) {
                            coordinator.togglePictureInPicture()
                        }
                    }
                    NativeStreamInfoRow(title: "Codec", value: coordinator.selectedCodecLabel)
                    NativeStreamInfoRow(title: "Resolution", value: coordinator.profileLabel)
                }

                NativeStreamPanelSection(title: "Status") {
                    NativeStreamToggleRow(
                        title: "Time",
                        value: coordinator.streamerPreferences.showStatsClock ? "Shown" : "Hidden",
                        isOn: Binding(
                            get: { coordinator.streamerPreferences.showStatsClock },
                            set: { coordinator.setStatsClockVisible($0) }
                        )
                    )
                    NativeStreamToggleRow(
                        title: "Battery",
                        value: coordinator.streamerPreferences.showStatsBattery ? "Shown" : "Hidden",
                        isOn: Binding(
                            get: { coordinator.streamerPreferences.showStatsBattery },
                            set: { coordinator.setStatsBatteryVisible($0) }
                        )
                    )
                    NativeStreamToggleRow(
                        title: "Cellular",
                        value: coordinator.streamerPreferences.showStatsCellular ? "Shown" : "Hidden",
                        isOn: Binding(
                            get: { coordinator.streamerPreferences.showStatsCellular },
                            set: { coordinator.setStatsCellularVisible($0) }
                        )
                    )
                }

                NativeStreamPanelSection(title: "Input") {
                    NativeStreamToggleRow(
                        title: "Touch controller",
                        value: coordinator.streamerPreferences.touchControllerVisible ? "Shown" : "Hidden",
                        isOn: Binding(
                            get: { coordinator.streamerPreferences.touchControllerVisible },
                            set: { coordinator.setTouchControllerVisible($0) }
                        )
                    )
                    NativeStreamToggleRow(
                        title: "Touchscreen mode",
                        value: coordinator.streamerPreferences.touchscreenModeEnabled ? "Direct touch" : "Mouse",
                        isOn: Binding(
                            get: { coordinator.streamerPreferences.touchscreenModeEnabled },
                            set: { coordinator.setTouchscreenModeEnabled($0) }
                        )
                    )
                    NativeStreamToggleRow(
                        title: "Controller passthrough",
                        value: coordinator.streamerPreferences.physicalControllerPassthrough ? "On" : "Off",
                        isOn: Binding(
                            get: { coordinator.streamerPreferences.physicalControllerPassthrough },
                            set: { coordinator.setPhysicalControllerPassthrough($0) }
                        )
                    )
                    HStack(spacing: 8) {
                        NativeStreamKeyButton(title: "Esc") {
                            coordinator.sendVirtualKey(.escape)
                        }
                        NativeStreamKeyButton(title: "Enter") {
                            coordinator.sendVirtualKey(.enter)
                        }
                        NativeStreamKeyButton(title: "Delete") {
                            coordinator.sendVirtualKey(.backspace)
                        }
                    }
                }

                NativeStreamPanelSection(title: "Touch Layout") {
                    NativeStreamSliderRow(
                        title: "Layout scale",
                        value: Binding(
                            get: { coordinator.touchLayout.scale },
                            set: { coordinator.setTouchLayoutScale($0) }
                        ),
                        range: 0.6...1.4
                    )
                    NativeStreamSliderRow(
                        title: "Button size",
                        value: Binding(
                            get: { coordinator.touchLayout.buttonScale },
                            set: { coordinator.setTouchButtonScale($0) }
                        ),
                        range: 0.65...1.5
                    )
                    NativeStreamSliderRow(
                        title: "Stick size",
                        value: Binding(
                            get: { coordinator.touchLayout.stickScale },
                            set: { coordinator.setTouchStickScale($0) }
                        ),
                        range: 0.65...1.5
                    )
                    NativeStreamSliderRow(
                        title: "Opacity",
                        value: Binding(
                            get: { coordinator.touchLayout.opacity },
                            set: { coordinator.setTouchOpacity($0) }
                        ),
                        range: 0.15...1.0
                    )
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 14)
        }
        .foregroundStyle(.primary)
        .background(Color(uiColor: .secondarySystemGroupedBackground).opacity(0.98), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.primary.opacity(0.12), lineWidth: 1))
        .scrollIndicators(.visible)
        .shadow(color: .black.opacity(0.24), radius: 16, y: 8)
    }
}

private struct NativeStreamPanelSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.primary.opacity(0.62))
            content()
        }
    }
}

private struct NativeStreamPanelIconButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 14, weight: .semibold))
                .frame(width: 34, height: 34)
        }
        .buttonStyle(.plain)
        .foregroundStyle(.primary)
        .background(Color.primary.opacity(0.10), in: Circle())
        .accessibilityLabel(label)
    }
}

private struct NativeStreamToggleRow: View {
    let title: String
    let value: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Text(value)
                    .font(.caption)
                    .foregroundStyle(Color.primary.opacity(0.72))
            }
            Spacer(minLength: 10)
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .fixedSize()
        }
        .font(.subheadline)
        .frame(minHeight: 44)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private struct NativeStreamSliderRow: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let step: Double

    init(
        title: String,
        value: Binding<Double>,
        range: ClosedRange<Double>,
        step: Double = 0.05
    ) {
        self.title = title
        self._value = value
        self.range = range
        self.step = step
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(alignment: .firstTextBaseline) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Spacer(minLength: 10)
                Text(percentText)
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(Color.primary.opacity(0.72))
            }
            Slider(value: $value, in: range, step: step)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 9)
        .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var percentText: String {
        "\(Int((value * 100).rounded()))%"
    }
}

private struct NativeStreamActionRow: View {
    let title: String
    let value: String
    let actionLabel: String
    let action: () -> Void

    init(
        title: String,
        value: String,
        actionLabel: String = "Change",
        action: @escaping () -> Void
    ) {
        self.title = title
        self.value = value
        self.actionLabel = actionLabel
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                    Text(value)
                        .font(.caption)
                        .foregroundStyle(Color.primary.opacity(0.72))
                }
                Spacer()
                Text(actionLabel)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tint)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct NativeStreamInfoRow: View {
    let title: String
    let value: String

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.subheadline.weight(.semibold))
            Spacer(minLength: 12)
            Text(value)
                .font(.caption)
                .foregroundStyle(Color.primary.opacity(0.72))
                .multilineTextAlignment(.trailing)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private struct NativeStreamKeyButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
        }
        .buttonStyle(.bordered)
    }
}

private enum NativeStreamVirtualKey {
    case escape
    case enter
    case backspace

    var mapping: NativeStreamKeyboardMapping {
        switch self {
        case .escape:
            return .init(virtualKey: 0x1b, scanCode: 0x0001)
        case .enter:
            return .init(virtualKey: 0x0d, scanCode: 0x001c)
        case .backspace:
            return .init(virtualKey: 0x08, scanCode: 0x000e)
        }
    }
}

@MainActor
final class NativeStreamCoordinator: NSObject, ObservableObject {
    @Published var statusText = "Preparing stream"
    @Published var detailText = ""
    @Published var statsText = ""
    @Published fileprivate var statsSnapshot = NativeStreamStatsSnapshot.empty
    @Published var showStatusOverlay = true
    @Published var retryAvailable = false
    @Published var showStatsOverlay = false
    @Published var controlsPanelVisible = false
    @Published fileprivate var statsDisplayStyle: NativeStreamStatsDisplayStyle = .compact
    @Published var streamerPreferences: StreamerPreferences
    @Published fileprivate var deviceStatus = NativeStreamDeviceStatus.current()
    @Published var touchLayout: TouchControlLayout
    @Published fileprivate var pictureInPictureAvailable = false
    @Published fileprivate var isPictureInPictureActive = false

    let sessionID: String
    let inputBridge = NativeStreamInputBridge()

    private let session: ActiveSession
    private let settings: AppSettings
    private let onTouchLayoutChange: (String, TouchControlLayout) -> Void
    private let onStreamerPreferencesChange: (StreamerPreferences) -> Void
    private let onStatsOverlayChange: (Bool) -> Void
    private let onSafeVideoFallbackRequired: (String) -> Void
    private let onClose: () -> Void
    private let onRetry: (() -> Void)?
    private let logger = Logger(subsystem: "OpenNOWiOS", category: "NativeStreamer")
    private let workQueue = DispatchQueue(label: "OpenNOW.NativeStreamer")
    private let peerName = "peer-\(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(12))"
    private var peerId = 0
    private var remotePeerId = 1
    private var ackCounter = 0
    private var started = false
    private var stopped = false
    private var selectedCodec: NativeStreamVideoCodec = .h264
    private var streamProfile: StreamVideoProfile
    private var viewportSize: CGSize = .zero
    private var signalingSession: URLSession?
    private var webSocket: URLSessionWebSocketTask?
    private var signalingHeartbeat: DispatchSourceTimer?
    private var offerTimeoutWorkItem: DispatchWorkItem?
    private var statsTimer: DispatchSourceTimer?
    private var factory: RTCPeerConnectionFactory?
    private var peerConnection: RTCPeerConnection?
    private var reliableInputChannel: RTCDataChannel?
    private var partiallyReliableInputChannel: RTCDataChannel?
    private var controlChannel: RTCDataChannel?
    private let pictureInPictureBridge = NativeStreamPictureInPictureBridge()
    private weak var renderer: NativeStreamRenderView?
    private let videoSink = NativeStreamVideoSink()
    private var videoTrack: RTCVideoTrack?
    private var videoSinkAttached = false
    private var renderedFrameCount = 0
    private var renderedVideoSize: CGSize = .zero
    private var sampledLuma: Int?
    private var decodedWithoutRenderStartedAt: TimeInterval?
    private var lastRenderKeyframeRequestAt: TimeInterval?
    private var renderKeyframeAttempts = 0
    private var codecReport = NativeStreamCodecProbe.report()
    private var reliableInputPackets = 0
    private var partiallyReliableInputPackets = 0
    private var negotiationDebugText = ""
    private var localCodecDebugText = ""
    private var answerSent = false
    private var queuedLocalIceCandidates: [[String: Any]] = []
    private var lastStatsSampleAt: TimeInterval?
    private var lastStatsFramesDecoded: Int?
    private var lastStatsBytesReceived: Int?
    private var autoRetryScheduled = false
    private var webRTCAudioSessionConfigured = false
    private var mutedAudioDevice: NativeStreamMutedAudioDevice?
    private var latestScenePhase: ScenePhase = .active
    private var backgroundPictureInPictureStartPending = false
    private var needsForegroundReconnect = false

    init(
        session: ActiveSession,
        settings: AppSettings,
        onTouchLayoutChange: @escaping (String, TouchControlLayout) -> Void,
        onStreamerPreferencesChange: @escaping (StreamerPreferences) -> Void,
        onStatsOverlayChange: @escaping (Bool) -> Void,
        onSafeVideoFallbackRequired: @escaping (String) -> Void,
        onClose: @escaping () -> Void,
        onRetry: (() -> Void)?
    ) {
        self.session = session
        self.settings = settings
        self.sessionID = session.id
        self.streamerPreferences = settings.streamerPreferences
        self.touchLayout = settings.touchLayout(for: "default")
        self.onTouchLayoutChange = onTouchLayoutChange
        self.onStreamerPreferencesChange = onStreamerPreferencesChange
        self.onStatsOverlayChange = onStatsOverlayChange
        self.onSafeVideoFallbackRequired = onSafeVideoFallbackRequired
        self.onClose = onClose
        self.onRetry = onRetry
        self.streamProfile = Self.effectiveProfile(for: session, settings: settings)
        self.showStatsOverlay = settings.showStatsOverlay
        super.init()
        inputBridge.sink = self
        videoSink.onFrame = { [weak self] count, size, luma in
            Task { @MainActor in
                self?.noteRenderedFrame(count: count, size: size, luma: luma)
            }
        }
        videoSink.onPictureInPictureFrame = { [weak pictureInPictureBridge] frame in
            pictureInPictureBridge?.enqueue(frame: frame)
        }
        pictureInPictureBridge.onAvailabilityChanged = { [weak self] available in
            Task { @MainActor in
                self?.pictureInPictureAvailable = available
            }
        }
        pictureInPictureBridge.onActiveChanged = { [weak self] active in
            Task { @MainActor in
                self?.handlePictureInPictureActiveChanged(active)
            }
        }
        pictureInPictureBridge.onLog = { [weak self] message in
            Task { @MainActor in
                self?.log(message)
            }
        }
    }

    func start(viewportSize: CGSize) {
        guard !started else { return }
        started = true
        stopped = false
        self.viewportSize = viewportSize
        refreshDeviceStatus()
        retryAvailable = onRetry != nil
        updateStatus("Checking codecs", detail: codecReport.summary)

        let requested = NativeStreamVideoCodec.normalized(settings.preferredCodec)
        let resolved = codecReport.launchSafeCodec(preferred: settings.preferredCodec)
        if let requested, requested != resolved {
            if allowsUnsafeCodecDiagnostics,
               codecReport.capability(for: requested)?.webRTCSupported == true {
                selectedCodec = requested
                updateStatus("Checking codecs", detail: "Diagnostic unsafe codec override: \(requested.rawValue)")
            } else {
                updateStatus("Switching to safe video", detail: "\(requested.rawValue) is not available through iOS hardware WebRTC on this runtime.")
                onSafeVideoFallbackRequired("\(requested.rawValue) is not hardware-safe on this iOS runtime")
                return
            }
        } else {
            selectedCodec = resolved
        }

        configureWebRTCAudioSession()
        inputBridge.attach()
        setIdleTimerDisabled(true)
        connectSignaling()
    }

    func updateViewportSize(_ size: CGSize) {
        viewportSize = size
    }

    func handleScenePhase(_ phase: ScenePhase) {
        latestScenePhase = phase
        switch phase {
        case .active:
            backgroundPictureInPictureStartPending = false
            reconnectAfterBackgroundIfNeeded()
        case .inactive:
            startPictureInPictureForBackground(reason: "scene inactive")
        case .background:
            startPictureInPictureForBackground(reason: "scene background")
        @unknown default:
            break
        }
    }

    func handleViewDisappear(scenePhase: ScenePhase) {
        latestScenePhase = scenePhase
        guard scenePhase == .active else {
            log("Preserving native stream during \(String(describing: scenePhase)) scene transition")
            startPictureInPictureForBackground(reason: "view disappeared while app was not active")
            return
        }
        stop()
    }

    var gameTitle: String {
        session.game.title
    }

    var selectedCodecLabel: String {
        selectedCodec.rawValue.uppercased()
    }

    var profileLabel: String {
        "\(streamProfile.resolutionString) @ \(streamProfile.fps) fps"
    }

    func toggleControlsPanel() {
        setControlsPanelVisible(!controlsPanelVisible)
    }

    func setControlsPanelVisible(_ visible: Bool) {
        withAnimation(.easeInOut(duration: 0.18)) {
            controlsPanelVisible = visible
        }
    }

    func setStatsOverlayVisible(_ visible: Bool) {
        showStatsOverlay = visible
        onStatsOverlayChange(visible)
    }

    func cycleStatsStyle() {
        statsDisplayStyle = statsDisplayStyle.next
    }

    func togglePictureInPicture() {
        if pictureInPictureBridge.isPictureInPictureActive {
            pictureInPictureBridge.stop()
        } else if !pictureInPictureBridge.start() {
            updateStatus("Picture in Picture unavailable", detail: "Waiting for a hardware-decoded video frame")
        }
    }

    func setAudioEnabled(_ enabled: Bool) {
        var preferences = streamerPreferences
        preferences.audioMuted = !enabled
        setStreamerPreferences(preferences)
        applyLiveAudioPreference()
    }

    func setStatsClockVisible(_ visible: Bool) {
        var preferences = streamerPreferences
        preferences.showStatsClock = visible
        setStreamerPreferences(preferences)
        refreshDeviceStatus()
    }

    func setStatsBatteryVisible(_ visible: Bool) {
        var preferences = streamerPreferences
        preferences.showStatsBattery = visible
        setStreamerPreferences(preferences)
        refreshDeviceStatus()
    }

    func setStatsCellularVisible(_ visible: Bool) {
        var preferences = streamerPreferences
        preferences.showStatsCellular = visible
        setStreamerPreferences(preferences)
    }

    func setTouchControllerVisible(_ visible: Bool) {
        var preferences = streamerPreferences
        preferences.touchControllerVisible = visible
        setStreamerPreferences(preferences)
    }

    func setTouchscreenModeEnabled(_ enabled: Bool) {
        var preferences = streamerPreferences
        preferences.touchscreenModeEnabled = enabled
        setStreamerPreferences(preferences)
    }

    func setPhysicalControllerPassthrough(_ enabled: Bool) {
        var preferences = streamerPreferences
        preferences.physicalControllerPassthrough = enabled
        setStreamerPreferences(preferences)
    }

    func setTouchLayoutScale(_ value: Double) {
        updateTouchLayout { $0.scale = min(max(value, 0.6), 1.4) }
    }

    func setTouchButtonScale(_ value: Double) {
        updateTouchLayout { $0.buttonScale = min(max(value, 0.65), 1.5) }
    }

    func setTouchStickScale(_ value: Double) {
        updateTouchLayout { $0.stickScale = min(max(value, 0.65), 1.5) }
    }

    func setTouchOpacity(_ value: Double) {
        updateTouchLayout { $0.opacity = min(max(value, 0.15), 1.0) }
    }

    fileprivate func sendVirtualKey(_ key: NativeStreamVirtualKey) {
        let mapping = key.mapping
        inputBridge.sendKey(mapping: mapping, pressed: true, modifiers: 0)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.045) { [weak self] in
            self?.inputBridge.sendKey(mapping: mapping, pressed: false, modifiers: 0)
        }
    }

    private func setStreamerPreferences(_ preferences: StreamerPreferences) {
        streamerPreferences = preferences
        onStreamerPreferencesChange(preferences)
    }

    private func refreshDeviceStatus() {
        deviceStatus = NativeStreamDeviceStatus.current()
    }

    private func updateTouchLayout(_ update: (inout TouchControlLayout) -> Void) {
        var next = touchLayout
        update(&next)
        touchLayout = next
        onTouchLayoutChange("default", next)
    }

    fileprivate func attachRenderer(_ renderer: NativeStreamRenderView) {
        if self.renderer !== renderer {
            self.renderer?.metalDelegate = nil
            renderer.metalDelegate = self
            videoSink.attach(renderView: renderer)
            pictureInPictureBridge.attach(displayLayer: renderer.pictureInPictureDisplayLayer)
        }
        self.renderer = renderer
        attachCurrentVideoSinkIfNeeded()
    }

    func close() {
        stop()
        onClose()
    }

    func retry() {
        stop()
        onRetry?()
    }

    func stop() {
        guard !stopped else { return }
        stopped = true
        inputBridge.detach()
        setIdleTimerDisabled(false)
        teardownWebRTCAudioSession()
        offerTimeoutWorkItem?.cancel()
        offerTimeoutWorkItem = nil
        signalingHeartbeat?.cancel()
        signalingHeartbeat = nil
        statsTimer?.cancel()
        statsTimer = nil
        pictureInPictureBridge.detach()
        pictureInPictureAvailable = false
        isPictureInPictureActive = false
        webSocket?.cancel(with: .goingAway, reason: nil)
        webSocket = nil
        signalingSession?.invalidateAndCancel()
        signalingSession = nil
        reliableInputChannel?.delegate = nil
        partiallyReliableInputChannel?.delegate = nil
        controlChannel?.delegate = nil
        reliableInputChannel?.close()
        partiallyReliableInputChannel?.close()
        controlChannel?.close()
        reliableInputChannel = nil
        partiallyReliableInputChannel = nil
        controlChannel = nil
        if let videoTrack, videoSinkAttached {
            videoTrack.remove(videoSink)
        }
        videoSink.detach()
        renderer?.metalDelegate = nil
        videoTrack = nil
        videoSinkAttached = false
        peerConnection?.delegate = nil
        peerConnection?.close()
        peerConnection = nil
        factory = nil
        mutedAudioDevice = nil
        answerSent = false
        queuedLocalIceCandidates.removeAll(keepingCapacity: true)
    }

    private func setVideoTrack(_ track: RTCVideoTrack) {
        if let existingTrack = videoTrack, existingTrack !== track, videoSinkAttached {
            existingTrack.remove(videoSink)
            videoSinkAttached = false
        }
        videoTrack = track
        attachCurrentVideoSinkIfNeeded()
        updateStatus("Video track attached", detail: "Waiting for rendered frames")
    }

    private func attachCurrentVideoSinkIfNeeded() {
        guard !videoSinkAttached, let videoTrack, renderer != nil else { return }
        videoTrack.add(videoSink)
        videoSinkAttached = true
        log("Attached native Metal video sink")
    }

    private func connectSignaling() {
        guard let url = signalingURL() else {
            fail("Missing signaling endpoint")
            return
        }
        updateStatus("Connecting", detail: url.host ?? "signaling")
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 60
        let urlSession = URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
        signalingSession = urlSession
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("x-nv-sessionid.\(session.id)", forHTTPHeaderField: "Sec-WebSocket-Protocol")
        request.setValue("https://play.geforcenow.com", forHTTPHeaderField: "Origin")
        request.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
        if let host = url.host {
            let hostHeader = url.port.map { "\(host):\($0)" } ?? host
            request.setValue(hostHeader, forHTTPHeaderField: "Host")
        }
        let task = urlSession.webSocketTask(with: request)
        webSocket = task
        scheduleOfferTimeout()
        task.resume()
        receiveSignaling()
    }

    private func signalingURL() -> URL? {
        let rawBase = session.signalingUrl?.trimmingCharacters(in: .whitespacesAndNewlines)
        let fallbackHost = session.signalingServer?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? session.serverIp?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? ""
        let base: String
        if let rawBase, !rawBase.isEmpty {
            base = rawBase
        } else if !fallbackHost.isEmpty {
            let host = fallbackHost.contains(":") ? fallbackHost : "\(fallbackHost):443"
            base = "wss://\(host)/nvst/"
        } else {
            return nil
        }
        let withoutScheme = base
            .replacingOccurrences(of: "wss://", with: "", options: [.caseInsensitive])
            .replacingOccurrences(of: "ws://", with: "", options: [.caseInsensitive])
            .replacingOccurrences(of: "https://", with: "", options: [.caseInsensitive])
            .replacingOccurrences(of: "http://", with: "", options: [.caseInsensitive])
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return URL(string: "wss://\(withoutScheme)/sign_in?peer_id=\(peerName)&version=2&peer_role=1&pairing_id=\(session.id)")
    }

    private func receiveSignaling() {
        webSocket?.receive { [weak self] result in
            guard let self else { return }
            Task { @MainActor in
                guard !self.stopped else { return }
                switch result {
                case .success(.string(let text)):
                    self.handleSignalingText(text)
                    self.receiveSignaling()
                case .success(.data(let data)):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleSignalingText(text)
                    }
                    self.receiveSignaling()
                case .failure(let error):
                    self.fail("Signaling failed: \(error.localizedDescription)")
                @unknown default:
                    self.receiveSignaling()
                }
            }
        }
    }

    private func handleSignalingText(_ text: String) {
        guard let data = text.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            log("Ignoring non-JSON signaling packet")
            return
        }

        if let info = object["peer_info"] as? [String: Any],
           let name = info["name"] as? String,
           name == peerName {
            peerId = info["id"] as? Int ?? peerId
        }

        if let ack = object["ackid"] as? Int {
            let sourceId = (object["peer_info"] as? [String: Any])?["id"] as? Int
            if sourceId != peerId {
                sendJSON(["ack": ack])
            }
        }

        if object["hb"] != nil {
            sendJSON(["hb": 1])
            return
        }

        guard let peerMessage = object["peer_msg"] as? [String: Any] else { return }
        remotePeerId = peerMessage["from"] as? Int ?? remotePeerId
        guard let message = peerMessage["msg"] as? String,
              let messageData = message.data(using: .utf8),
              let payload = try? JSONSerialization.jsonObject(with: messageData) as? [String: Any] else {
            return
        }

        if payload["type"] as? String == "offer", let sdp = payload["sdp"] as? String {
            handleOffer(sdp)
        } else if let candidate = payload["candidate"] as? String {
            addRemoteCandidate(
                candidate,
                sdpMid: payload["sdpMid"] as? String,
                sdpMLineIndex: payload["sdpMLineIndex"] as? Int
            )
        }
    }

    private func sendPeerInfo() {
        sendJSON([
            "ackid": nextAckId(),
            "peer_info": [
                "browser": "Chrome",
                "browserVersion": "131",
                "connected": true,
                "id": peerId,
                "name": peerName,
                "peerRole": 0,
                "resolution": "1920x1080",
                "version": 2
            ]
        ])
    }

    private func handleOffer(_ offerSDP: String) {
        offerTimeoutWorkItem?.cancel()
        updateStatus("Negotiating video", detail: "\(selectedCodec.rawValue) \(streamProfile.resolutionString) @ \(streamProfile.fps) fps")
        inputBridge.configure(
            protocolVersion: NativeStreamSDP.parseInputProtocolVersion(from: offerSDP),
            partiallyReliableGamepadMask: NativeStreamSDP.parsePartiallyReliableGamepadMask(from: offerSDP)
        )

        workQueue.async { [weak self] in
            guard let self else { return }
            Task { @MainActor in
                self.createPeerConnection(with: offerSDP)
            }
        }
    }

    private func createPeerConnection(with offerSDP: String) {
        if factory == nil {
            _ = RTCInitializeSSL()
            let audioDevice = shouldNegotiateAudio ? nil : NativeStreamMutedAudioDevice()
            mutedAudioDevice = audioDevice
            factory = RTCPeerConnectionFactory(
                encoderFactory: RTCDefaultVideoEncoderFactory(),
                decoderFactory: NativeStreamVideoDecoderFactory(),
                audioDevice: audioDevice
            )
            if audioDevice != nil {
                log("Using muted WebRTC audio device")
            }
        }
        guard let factory else {
            fail("Could not create WebRTC factory")
            return
        }
        answerSent = false
        queuedLocalIceCandidates.removeAll(keepingCapacity: true)

        let configuration = RTCConfiguration()
        configuration.iceServers = session.iceServers.map {
            RTCIceServer(urlStrings: $0.urls, username: $0.username, credential: $0.credential)
        }
        configuration.sdpSemantics = .unifiedPlan
        configuration.bundlePolicy = .maxBundle
        configuration.rtcpMuxPolicy = .require
        configuration.tcpCandidatePolicy = .disabled
        configuration.continualGatheringPolicy = .gatherContinually

        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        guard let peerConnection = factory.peerConnection(
            with: configuration,
            constraints: constraints,
            delegate: self
        ) else {
            fail("Could not create WebRTC peer connection")
            return
        }
        self.peerConnection = peerConnection

        let reliableConfig = RTCDataChannelConfiguration()
        reliableConfig.isOrdered = true
        reliableInputChannel = peerConnection.dataChannel(forLabel: "input_channel_v1", configuration: reliableConfig)
        reliableInputChannel?.delegate = self

        let partialConfig = RTCDataChannelConfiguration()
        partialConfig.isOrdered = false
        partialConfig.maxPacketLifeTime = Int32(NativeStreamSDP.parsePartialReliableThresholdMs(from: offerSDP))
        partiallyReliableInputChannel = peerConnection.dataChannel(
            forLabel: "input_channel_partially_reliable",
            configuration: partialConfig
        )
        partiallyReliableInputChannel?.delegate = self

        var processedOffer = prepareRemoteOffer(offerSDP)
        let didRewriteCandidate = processedOffer != offerSDP
        negotiationDebugText = [
            "media \(session.mediaIp ?? "nil"):\(session.mediaPort)",
            "sdpFix \(didRewriteCandidate ? "yes" : "no")"
        ].joined(separator: " ")
        let preferTenBit = settings.hdrEnabled || settings.preferredColorQuality.hasPrefix("10bit")
        processedOffer = NativeStreamSDP.preferCodec(in: processedOffer, codec: selectedCodec, preferTenBit: preferTenBit)
        negotiationDebugText = [
            negotiationDebugText,
            NativeStreamSDP.describeOfferedVideoCodecs(from: processedOffer)
        ].filter { !$0.isEmpty }.joined(separator: " ")
        let remoteDescription = RTCSessionDescription(type: .offer, sdp: processedOffer)
        peerConnection.setRemoteDescription(remoteDescription) { [weak self] error in
            Task { @MainActor in
                guard let self else { return }
                if let error {
                    self.fail("Remote SDP failed: \(error.localizedDescription)")
                    return
                }
                self.applyCodecPreferences()
                self.createAnswer(for: processedOffer)
            }
        }
    }

    private func prepareRemoteOffer(_ offerSDP: String) -> String {
        var prepared = NativeStreamSDP.fixServerIP(in: offerSDP, serverIP: session.mediaIp)
        guard selectedCodec == .h265 else { return prepared }

        let maxLevels = h265ReceiverMaxLevelsByProfile()
        if !maxLevels.isEmpty {
            let rewritten = NativeStreamSDP.rewriteH265LevelIdByProfile(in: prepared, maxLevelByProfile: maxLevels)
            if rewritten.replacements > 0 {
                log("H265 level-id clamped replacements=\(rewritten.replacements) maxLevels=\(maxLevels)")
                prepared = rewritten.sdp
            }
        }

        if !supportsH265TierFlagOne() {
            let rewritten = NativeStreamSDP.rewriteH265TierFlag(in: prepared, tierFlag: 0)
            if rewritten.replacements > 0 {
                log("H265 tier-flag rewritten replacements=\(rewritten.replacements)")
                prepared = rewritten.sdp
            }
        }

        return prepared
    }

    private func applyCodecPreferences() {
        guard let peerConnection, let factory else { return }
        guard let videoTransceiver = peerConnection.transceivers.first(where: { $0.mediaType == .video }) else {
            log("Codec preferences skipped: no video transceiver")
            return
        }

        let receiverCapabilities = factory.rtpReceiverCapabilities(forKind: kRTCMediaStreamTrackKindVideo)
        var preferred = receiverCapabilities.codecs.filter { capabilityMatches($0, codec: selectedCodec) }
        let auxiliary = receiverCapabilities.codecs.filter { capability in
            let name = capability.name.uppercased()
            let mime = capability.mimeType.uppercased()
            return name.contains("RTX") || name.contains("FLEXFEC") || mime.contains("RTX") || mime.contains("FLEXFEC")
        }

        guard !preferred.isEmpty else {
            log("Codec preferences skipped: \(selectedCodec.rawValue) not in receiver capabilities")
            return
        }

        if selectedCodec == .h265 {
            preferred.sort { hevcCapabilityPriority($0) < hevcCapabilityPriority($1) }
        }

        let codecList = preferred + auxiliary
        do {
            try videoTransceiver.setCodecPreferences(codecList, error: ())
            log("Codec preferences set: \(selectedCodec.rawValue) preferred=\(preferred.count) auxiliary=\(auxiliary.count)")
        } catch {
            log("Codec preferences failed: \(error.localizedDescription)")
        }
    }

    private func capabilityMatches(_ capability: RTCRtpCodecCapability, codec: NativeStreamVideoCodec) -> Bool {
        let name = capability.name.uppercased()
        let mime = capability.mimeType.uppercased()
        switch codec {
        case .h264:
            return name == "H264" || name == "AVC" || mime == "VIDEO/H264" || mime == "VIDEO/AVC"
        case .h265:
            return name == "H265" || name == "HEVC" || mime == "VIDEO/H265" || mime == "VIDEO/HEVC"
        case .av1:
            return name == "AV1" || name == "AV01" || mime == "VIDEO/AV1" || mime == "VIDEO/AV01"
        }
    }

    private func hevcCapabilityPriority(_ capability: RTCRtpCodecCapability) -> Int {
        let profileID = capability.parameters["profile-id"] ?? capability.parameters["profile_id"]
        if profileID == "1" { return 0 }
        if profileID == nil { return 1 }
        return 2
    }

    private func h265ReceiverMaxLevelsByProfile() -> [Int: Int] {
        receiverH265Capabilities()
            .compactMap { capability -> (Int, Int)? in
                guard let profile = capability.codecParameterInt("profile-id"),
                      let level = capability.codecParameterInt("level-id") else {
                    return nil
                }
                return (profile, level)
            }
            .reduce(into: [Int: Int]()) { partial, pair in
                partial[pair.0] = max(partial[pair.0] ?? 0, pair.1)
            }
            .filter { $0.value > 0 }
    }

    private func supportsH265TierFlagOne() -> Bool {
        receiverH265Capabilities().contains { $0.codecParameterInt("tier-flag") == 1 }
    }

    private func receiverH265Capabilities() -> [RTCRtpCodecCapability] {
        guard let factory else { return [] }
        return factory.rtpReceiverCapabilities(forKind: kRTCMediaStreamTrackKindVideo).codecs.filter {
            capabilityMatches($0, codec: .h265)
        }
    }

    private func createAnswer(for processedOffer: String) {
        guard let peerConnection else { return }
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        peerConnection.answer(for: constraints) { [weak self] answer, error in
            Task { @MainActor in
                guard let self else { return }
                if let error {
                    self.fail("Answer creation failed: \(error.localizedDescription)")
                    return
                }
                guard let answer else {
                    self.fail("Answer creation returned no SDP")
                    return
                }
                let mungedSDP = NativeStreamSDP.mungeAnswerSDP(answer.sdp, maxBitrateKbps: self.streamProfile.maxBitrateKbps)
                self.localCodecDebugText = NativeStreamSDP.describeNegotiatedVideo(from: mungedSDP)
                self.log("Local video SDP \(self.localCodecDebugText)")
                if self.selectedCodec != .h264,
                   !NativeStreamSDP.negotiatesCodec(mungedSDP, codec: self.selectedCodec) {
                    self.log("Local answer did not negotiate requested codec \(self.selectedCodec.rawValue); requesting safe fallback")
                    self.onSafeVideoFallbackRequired("\(self.selectedCodec.rawValue) was requested but WebRTC did not negotiate it; restarting with safe H264 profile")
                    return
                }
                let localDescription = RTCSessionDescription(type: .answer, sdp: mungedSDP)
                guard let connection = self.peerConnection else {
                    self.fail("Peer connection closed before local SDP")
                    return
                }
                do {
                    try await connection.setLocalDescription(localDescription)
                } catch {
                    self.fail("Local SDP failed: \(error.localizedDescription)")
                    return
                }
                self.sendAnswer(offerSDP: processedOffer)
                self.startStatsTimer()
            }
        }
    }

    private func sendAnswer(offerSDP: String) {
        guard let localSDP = peerConnection?.localDescription?.sdp else {
            fail("Local answer SDP was unavailable")
            return
        }
        let nvst = NativeStreamSDP.buildNvstSDP(
            offerSDP: offerSDP,
            localAnswerSDP: localSDP,
            profile: streamProfile,
            settings: settings,
            codec: selectedCodec
        )
        sendPeerMessage([
            "type": "answer",
            "sdp": localSDP,
            "nvstSdp": nvst
        ])
        answerSent = true
        flushQueuedLocalIceCandidates()
        updateStatus("Connecting media", detail: "Waiting for first frame")
    }

    private func sendLocalIceCandidate(_ candidate: RTCIceCandidate) {
        guard candidate.sdp.range(of: " tcp ", options: .caseInsensitive) == nil else { return }
        let payload: [String: Any] = [
            "candidate": candidate.sdp,
            "sdpMid": candidate.sdpMid ?? "",
            "sdpMLineIndex": Int(candidate.sdpMLineIndex)
        ]
        if answerSent {
            sendPeerMessage(payload)
        } else {
            queuedLocalIceCandidates.append(payload)
        }
    }

    private func flushQueuedLocalIceCandidates() {
        guard answerSent, !queuedLocalIceCandidates.isEmpty else { return }
        let queued = queuedLocalIceCandidates
        queuedLocalIceCandidates.removeAll(keepingCapacity: true)
        for candidate in queued {
            sendPeerMessage(candidate)
        }
    }

    private func addRemoteCandidate(_ candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        guard let peerConnection else { return }
        let ice = RTCIceCandidate(
            sdp: candidate,
            sdpMLineIndex: Int32(sdpMLineIndex ?? 0),
            sdpMid: sdpMid
        )
        peerConnection.add(ice) { [weak self] error in
            Task { @MainActor in
                guard let self else { return }
                if let error {
                    self.log("Remote ICE rejected: \(error.localizedDescription)")
                }
            }
        }
    }

    private func sendPeerMessage(_ payload: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              let message = String(data: data, encoding: .utf8) else {
            return
        }
        sendJSON([
            "peer_msg": [
                "from": peerId,
                "to": remotePeerId,
                "msg": message
            ],
            "ackid": nextAckId()
        ])
    }

    private func sendJSON(_ object: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: object),
              let text = String(data: data, encoding: .utf8) else {
            return
        }
        webSocket?.send(.string(text)) { [weak self] error in
            if let error {
                Task { @MainActor in
                    self?.log("Signaling send failed: \(error.localizedDescription)")
                }
            }
        }
    }

    private func nextAckId() -> Int {
        ackCounter += 1
        return ackCounter
    }

    private func scheduleOfferTimeout() {
        offerTimeoutWorkItem?.cancel()
        let item = DispatchWorkItem { [weak self] in
            Task { @MainActor in
                guard let self, !self.stopped, self.peerConnection == nil else { return }
                if self.selectedCodec != .h264 {
                    self.onSafeVideoFallbackRequired("Waiting for offer timed out while using \(self.selectedCodec.rawValue)")
                } else {
                    self.fail("Timed out waiting for WebRTC offer")
                }
            }
        }
        offerTimeoutWorkItem = item
        workQueue.asyncAfter(deadline: .now() + 24, execute: item)
    }

    private func startSignalingHeartbeat() {
        signalingHeartbeat?.cancel()
        let timer = DispatchSource.makeTimerSource(queue: workQueue)
        timer.schedule(deadline: .now() + 5, repeating: 5)
        timer.setEventHandler { [weak self] in
            Task { @MainActor in
                self?.sendJSON(["hb": 1])
            }
        }
        signalingHeartbeat = timer
        timer.resume()
    }

    private func startStatsTimer() {
        statsTimer?.cancel()
        let timer = DispatchSource.makeTimerSource(queue: workQueue)
        timer.schedule(deadline: .now() + 1, repeating: 1)
        timer.setEventHandler { [weak self] in
            Task { @MainActor in
                self?.collectStats()
            }
        }
        statsTimer = timer
        timer.resume()
    }

    private func collectStats() {
        guard let peerConnection else { return }
        peerConnection.statistics { [weak self] report in
            Task { @MainActor in
                self?.updateStats(from: report)
            }
        }
    }

    private func updateStats(from report: RTCStatisticsReport) {
        refreshDeviceStatus()
        var framesDecoded: Int?
        var framesPerSecond: Int?
        var framesDropped: Int?
        var width: Int?
        var height: Int?
        var bytesReceived: Int?
        var packetsLost: Int?
        var packetsReceived: Int?
        var jitterMs: Double?
        var rttMs: Double?
        var selectedPairValues: [String: NSObject]?
        var candidateStats: [String: [String: NSObject]] = [:]

        for stat in report.statistics.values {
            guard stat.type == "inbound-rtp"
                    || stat.type == "local-candidate"
                    || stat.type == "remote-candidate"
                    || stat.type == "candidate-pair" else { continue }
            if stat.type == "inbound-rtp" {
                if let kind = stat.values["kind"] as? String, kind != "video" { continue }
                framesDecoded = (stat.values["framesDecoded"] as? NSNumber)?.intValue ?? framesDecoded
                framesPerSecond = (stat.values["framesPerSecond"] as? NSNumber)?.intValue ?? framesPerSecond
                framesDropped = (stat.values["framesDropped"] as? NSNumber)?.intValue ?? framesDropped
                width = (stat.values["frameWidth"] as? NSNumber)?.intValue ?? width
                height = (stat.values["frameHeight"] as? NSNumber)?.intValue ?? height
                bytesReceived = (stat.values["bytesReceived"] as? NSNumber)?.intValue ?? bytesReceived
                packetsLost = (stat.values["packetsLost"] as? NSNumber)?.intValue ?? packetsLost
                packetsReceived = (stat.values["packetsReceived"] as? NSNumber)?.intValue ?? packetsReceived
                if let jitter = stat.values["jitter"] as? NSNumber {
                    jitterMs = jitter.doubleValue * 1000
                }
            } else if stat.type == "local-candidate" || stat.type == "remote-candidate" {
                candidateStats[stat.id] = stat.values
            } else if stat.type == "candidate-pair",
                      (stat.values["state"] as? String) == "succeeded" {
                let nominated = (stat.values["nominated"] as? NSNumber)?.boolValue ?? false
                let selected = (stat.values["selected"] as? NSNumber)?.boolValue ?? false
                if selectedPairValues == nil || nominated || selected {
                    selectedPairValues = stat.values
                }
                if let rtt = stat.values["currentRoundTripTime"] as? NSNumber {
                    rttMs = rtt.doubleValue * 1000
                }
            }
        }

        let packetLossPercent: Double = {
            let lost = packetsLost ?? 0
            let received = packetsReceived ?? 0
            let total = lost + received
            guard total > 0 else { return 0 }
            return (Double(lost) / Double(total)) * 100
        }()
        let icePath = selectedIcePath(pair: selectedPairValues, candidates: candidateStats)
        let resolution = width.flatMap { w in height.map { "\(w)x\($0)" } } ?? "\(streamProfile.width)x\(streamProfile.height)"
        if (framesDecoded ?? 0) > 0 {
            handleDecodedVideoProgress(framesDecoded: framesDecoded ?? 0)
        }
        let now = ProcessInfo.processInfo.systemUptime
        let derivedFPS = framesPerSecond ?? estimatedFramesPerSecond(framesDecoded: framesDecoded, now: now)
        let derivedBitrate = estimatedBitrateKbps(bytesReceived: bytesReceived, now: now)
        statsText = [
            selectedCodec.rawValue,
            resolution,
            "decoded \(framesDecoded ?? 0)",
            "rendered \(renderedFrameCount)",
            sampledLuma.map { "luma \($0)" },
            localCodecDebugText.isEmpty ? nil : localCodecDebugText,
            "drop \(framesDropped ?? 0)",
            String(format: "loss %.1f%%", packetLossPercent),
            jitterMs.map { String(format: "jitter %.0fms", $0) },
            rttMs.map { String(format: "rtt %.0fms", $0) },
            "input r\(reliableInputPackets)/p\(partiallyReliableInputPackets)",
            negotiationDebugText,
            icePath
        ].compactMap { $0 }.joined(separator: "  ")
        statsSnapshot = NativeStreamStatsSnapshot(
            codec: selectedCodec.rawValue.uppercased(),
            resolution: resolution,
            fps: derivedFPS,
            bitrateKbps: derivedBitrate,
            pingMs: rttMs.map { Int($0.rounded()) },
            decoded: framesDecoded ?? 0,
            rendered: renderedFrameCount,
            dropped: framesDropped ?? 0,
            lossPercent: packetLossPercent,
            jitterMs: jitterMs.map { Int($0.rounded()) },
            inputSummary: "r\(reliableInputPackets)/p\(partiallyReliableInputPackets)",
            detail: statsText
        )
        lastStatsSampleAt = now
        lastStatsFramesDecoded = framesDecoded
        lastStatsBytesReceived = bytesReceived
    }

    private func estimatedFramesPerSecond(framesDecoded: Int?, now: TimeInterval) -> Int? {
        guard let framesDecoded,
              let lastFramesDecoded = lastStatsFramesDecoded,
              let lastStatsSampleAt,
              now > lastStatsSampleAt else {
            return nil
        }
        let delta = framesDecoded - lastFramesDecoded
        guard delta >= 0 else { return nil }
        return Int((Double(delta) / (now - lastStatsSampleAt)).rounded())
    }

    private func estimatedBitrateKbps(bytesReceived: Int?, now: TimeInterval) -> Int? {
        guard let bytesReceived,
              let lastStatsBytesReceived,
              let lastStatsSampleAt,
              now > lastStatsSampleAt else {
            return nil
        }
        let delta = bytesReceived - lastStatsBytesReceived
        guard delta >= 0 else { return nil }
        return Int((Double(delta) * 8.0 / (now - lastStatsSampleAt) / 1000.0).rounded())
    }

    private func selectedIcePath(pair: [String: NSObject]?, candidates: [String: [String: NSObject]]) -> String? {
        guard let pair else { return nil }
        let localID = pair["localCandidateId"] as? String
        let remoteID = pair["remoteCandidateId"] as? String
        let local = localID.flatMap { candidates[$0] }.flatMap(candidateEndpoint)
        let remote = remoteID.flatMap { candidates[$0] }.flatMap(candidateEndpoint)
        guard local != nil || remote != nil else { return nil }
        return "ice \(local ?? "?")->\(remote ?? "?")"
    }

    private func candidateEndpoint(_ values: [String: NSObject]) -> String {
        let address = (values["address"] as? String)
            ?? (values["ip"] as? String)
            ?? (values["networkAdapterType"] as? String)
            ?? "?"
        let port = (values["port"] as? NSNumber)?.intValue
            ?? Int(values["port"] as? String ?? "")
        let proto = (values["protocol"] as? String)?.lowercased()
        let type = (values["candidateType"] as? String)?.lowercased()
        return [
            port.map { "\(address):\($0)" } ?? address,
            proto,
            type
        ].compactMap { $0 }.joined(separator: "/")
    }

    private func updateStatus(_ status: String, detail: String = "") {
        statusText = status
        detailText = detail
        showStatusOverlay = true
        logger.info("\(status, privacy: .public) \(detail, privacy: .public)")
    }

    private func markVideoActive() {
        statusText = "Streaming"
        detailText = ""
        withAnimation(.easeOut(duration: 0.25)) {
            showStatusOverlay = false
        }
    }

    private func noteRenderedFrame(count: Int, size: CGSize, luma: Int?) {
        renderedFrameCount = count
        renderedVideoSize = size
        sampledLuma = luma ?? sampledLuma
        decodedWithoutRenderStartedAt = nil
        lastRenderKeyframeRequestAt = nil
        renderKeyframeAttempts = 0
        if !showStatusOverlay || count == 0 { return }
        markVideoActive()
    }

    private func handleDecodedVideoProgress(framesDecoded: Int) {
        guard renderedFrameCount == 0 else {
            markVideoActive()
            return
        }

        let now = ProcessInfo.processInfo.systemUptime
        if decodedWithoutRenderStartedAt == nil {
            decodedWithoutRenderStartedAt = now
            updateStatus("Recovering video", detail: "Decoded \(framesDecoded) frames; waiting for iOS renderer")
            return
        }

        let stalledFor = now - (decodedWithoutRenderStartedAt ?? now)
        if stalledFor >= 14 {
            if selectedCodec != .h264 {
                onSafeVideoFallbackRequired("Decoded video did not render with \(selectedCodec.rawValue); restarting with safe H264 profile")
            } else {
                fail("Video decoded but iOS renderer did not paint frames")
            }
            return
        }

        let keyframeDue = lastRenderKeyframeRequestAt.map { now - $0 >= 3 } ?? true
        if stalledFor >= 2, keyframeDue {
            renderKeyframeAttempts += 1
            lastRenderKeyframeRequestAt = now
            requestKeyframe(reason: "ios_renderer_startup", attempt: renderKeyframeAttempts)
        }

        updateStatus(
            "Recovering video",
            detail: "Decoded \(framesDecoded) frames; waiting for rendered frame \(renderKeyframeAttempts > 0 ? "(keyframe \(renderKeyframeAttempts))" : "")"
        )
    }

    private func requestKeyframe(reason: String, attempt: Int) {
        sendPeerMessage([
            "type": "request_keyframe",
            "reason": reason,
            "backlogFrames": 0,
            "attempt": attempt
        ])
        log("Requested keyframe reason=\(reason) attempt=\(attempt)")
    }

    private func fail(_ message: String) {
        if latestScenePhase != .active, isTransientStartupFailure(message) {
            needsForegroundReconnect = true
            statusText = "Reconnecting"
            detailText = "Connection paused while OpenNOW was in the background"
            showStatusOverlay = true
            logger.warning("Deferring transient stream failure until foreground: \(message, privacy: .public)")
            return
        }
        if scheduleAutoRetryIfNeeded(for: message) {
            return
        }
        statusText = "Stream failed"
        detailText = message
        showStatusOverlay = true
        retryAvailable = false
        logger.error("\(message, privacy: .public)")
    }

    private func scheduleAutoRetryIfNeeded(for message: String) -> Bool {
        guard let onRetry,
              !autoRetryScheduled,
              isTransientStartupFailure(message) else {
            return false
        }

        autoRetryScheduled = true
        retryAvailable = false
        updateStatus("Reconnecting", detail: message)
        logger.warning("Auto-retrying stream startup: \(message, privacy: .public)")

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.85) { [weak self] in
            guard let self, !self.stopped else { return }
            self.stop()
            onRetry()
        }
        return true
    }

    private func isTransientStartupFailure(_ message: String) -> Bool {
        let value = message.lowercased()
        return value.contains("bad response")
            || value.contains("timed out waiting for webrtc offer")
            || value.contains("waiting for offer timed out")
            || value.contains("signaling failed")
            || value.contains("signaling closed")
            || value.contains("ice connection failed")
            || value.contains("peer connection failed")
    }

    private func log(_ message: String) {
        logger.info("\(message, privacy: .public)")
    }

    private func handlePictureInPictureActiveChanged(_ active: Bool) {
        isPictureInPictureActive = active
        if active || latestScenePhase == .active {
            backgroundPictureInPictureStartPending = false
        }
    }

    private func startPictureInPictureForBackground(reason: String) {
        guard !stopped else { return }
        guard !pictureInPictureBridge.isPictureInPictureActive else { return }
        guard !backgroundPictureInPictureStartPending else { return }
        guard pictureInPictureBridge.start() else {
            log("Picture in Picture background start skipped for \(reason); waiting for PiP availability")
            return
        }
        backgroundPictureInPictureStartPending = true
        log("Requested Picture in Picture for \(reason)")
    }

    private func reconnectAfterBackgroundIfNeeded() {
        guard needsForegroundReconnect else { return }
        needsForegroundReconnect = false
        guard let onRetry else { return }
        updateStatus("Reconnecting", detail: "App returned from background")
        logger.warning("Restarting native streamer after background interruption")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
            guard let self, !self.stopped else { return }
            self.stop()
            onRetry()
        }
    }

    private var allowsUnsafeCodecDiagnostics: Bool {
        ProcessInfo.processInfo.arguments.contains("--opennow-allow-unsafe-codecs")
            || ProcessInfo.processInfo.environment["OPENNOW_ALLOW_UNSAFE_CODECS"] == "1"
    }

    private static func effectiveProfile(for session: ActiveSession, settings: AppSettings) -> StreamVideoProfile {
        var profile = StreamSettingsResolver.profile(for: settings)
        if let resolution = session.negotiatedStreamProfile?.resolution,
           let parsed = parseResolution(resolution) {
            profile = StreamVideoProfile(
                width: parsed.width,
                height: parsed.height,
                fps: session.negotiatedStreamProfile?.fps ?? profile.fps,
                maxBitrateKbps: profile.maxBitrateKbps
            )
        }
        return profile
    }

    private static func parseResolution(_ value: String) -> (width: Int, height: Int)? {
        let parts = value.split(separator: "x", maxSplits: 1).map(String.init)
        guard parts.count == 2,
              let width = Int(parts[0]),
              let height = Int(parts[1]),
              width > 0,
              height > 0 else {
            return nil
        }
        return (width, height)
    }

    private func setIdleTimerDisabled(_ disabled: Bool) {
        UIApplication.shared.isIdleTimerDisabled = disabled
    }

    private func parseHapticsMessage(_ data: Data) {
        guard let command = NativeStreamHapticsParser.parse(data) else { return }
        inputBridge.applyRumble(
            controllerId: command.controllerId,
            weakMagnitude: command.weakMagnitude,
            strongMagnitude: command.strongMagnitude
        )
    }

    private var shouldNegotiateAudio: Bool {
        #if targetEnvironment(simulator)
        false
        #else
        !streamerPreferences.audioMuted
        #endif
    }

    private func configureWebRTCAudioSession() {
        guard shouldNegotiateAudio else {
            log("WebRTC audio held disabled")
            return
        }

        let audioSession = RTCAudioSession.sharedInstance()
        let enableMic = settings.keepMicEnabled

        audioSession.useManualAudio = true
        audioSession.ignoresPreferredAttributeConfigurationErrors = true
        audioSession.lockForConfiguration()
        defer { audioSession.unlockForConfiguration() }

        configureAudioCategory(audioSession, enableMic: enableMic)
        audioSession.isAudioEnabled = true
        do {
            try audioSession.setPreferredSampleRate(48_000)
        } catch {
            log("Audio session sample rate failed: \(error.localizedDescription)")
        }
        do {
            try audioSession.setPreferredIOBufferDuration(0.01)
        } catch {
            log("Audio session buffer duration failed: \(error.localizedDescription)")
        }
        activateWebRTCAudioSession(audioSession)
        webRTCAudioSessionConfigured = true
        log("WebRTC audio playback enabled mic=\(enableMic)")
    }

    private func applyLiveAudioPreference() {
        if mutedAudioDevice != nil {
            log("WebRTC audio held disabled until stream restart")
            return
        }

        guard shouldNegotiateAudio else {
            teardownWebRTCAudioSession()
            log("WebRTC audio held disabled")
            return
        }

        configureWebRTCAudioSession()
    }

    private func configureAudioCategory(_ audioSession: RTCAudioSession, enableMic: Bool) {
        let category: AVAudioSession.Category = enableMic ? .playAndRecord : .playback
        let mode: AVAudioSession.Mode = enableMic ? .voiceChat : .moviePlayback
        let options: AVAudioSession.CategoryOptions = enableMic
            ? [.allowBluetoothHFP, .allowBluetoothA2DP, .defaultToSpeaker]
            : [.allowBluetoothA2DP]

        do {
            try audioSession.setCategory(category, mode: mode, options: options)
        } catch {
            log("Audio session category failed: \(error.localizedDescription)")
        }
    }

    private func activateWebRTCAudioSession(_ audioSession: RTCAudioSession) {
        #if !targetEnvironment(simulator)
        do {
            try audioSession.setActive(true)
        } catch {
            log("Audio session activation failed: \(error.localizedDescription)")
        }
        #endif
    }

    private func teardownWebRTCAudioSession() {
        guard webRTCAudioSessionConfigured else { return }
        let audioSession = RTCAudioSession.sharedInstance()
        audioSession.useManualAudio = true
        audioSession.isAudioEnabled = false
        #if !targetEnvironment(simulator)
        do {
            try audioSession.setActive(false)
        } catch {
            log("Audio session deactivation failed: \(error.localizedDescription)")
        }
        #endif
        webRTCAudioSessionConfigured = false
    }
}

extension NativeStreamCoordinator: NativeStreamInputSink {
    nonisolated func sendReliableInput(_ data: Data) {
        Task { @MainActor in
            guard self.reliableInputChannel?.readyState == .open else { return }
            if self.reliableInputChannel?.sendData(RTCDataBuffer(data: data, isBinary: true)) == true {
                self.reliableInputPackets += 1
            }
        }
    }

    nonisolated func sendPartiallyReliableInput(_ data: Data) {
        Task { @MainActor in
            if self.partiallyReliableInputChannel?.readyState == .open {
                if self.partiallyReliableInputChannel?.sendData(RTCDataBuffer(data: data, isBinary: true)) == true {
                    self.partiallyReliableInputPackets += 1
                }
            } else if self.reliableInputChannel?.readyState == .open {
                if self.reliableInputChannel?.sendData(RTCDataBuffer(data: data, isBinary: true)) == true {
                    self.reliableInputPackets += 1
                }
            }
        }
    }

    nonisolated func logInputEvent(_ message: String) {
        Task { @MainActor in
            self.log(message)
        }
    }
}

extension NativeStreamCoordinator: URLSessionWebSocketDelegate {
    nonisolated func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {
        Task { @MainActor in
            self.updateStatus("Signaling connected", detail: "Waiting for offer")
            self.sendPeerInfo()
            self.startSignalingHeartbeat()
        }
    }

    nonisolated func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        Task { @MainActor in
            guard !self.stopped else { return }
            self.fail("Signaling closed: \(closeCode.rawValue)")
        }
    }
}

extension NativeStreamCoordinator: RTCPeerConnectionDelegate {
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        Task { @MainActor in
            if let track = stream.videoTracks.first {
                self.setVideoTrack(track)
            }
        }
    }
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    nonisolated func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        Task { @MainActor in
            switch newState {
            case .connected, .completed:
                self.updateStatus("Media connected", detail: "Waiting for video")
            case .failed:
                if self.selectedCodec != .h264 {
                    self.onSafeVideoFallbackRequired("ICE failed while using \(self.selectedCodec.rawValue)")
                } else {
                    self.fail("ICE connection failed")
                }
            case .disconnected:
                self.updateStatus("Reconnecting", detail: "ICE disconnected")
            default:
                break
            }
        }
    }
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        Task { @MainActor in
            self.sendLocalIceCandidate(candidate)
        }
    }
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {
        Task { @MainActor in
            dataChannel.delegate = self
            if dataChannel.label == "control_channel" {
                self.controlChannel = dataChannel
            }
        }
    }
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCPeerConnectionState) {
        Task { @MainActor in
            switch stateChanged {
            case .connected:
                self.updateStatus("Peer connected", detail: "Waiting for video")
            case .failed:
                if self.selectedCodec != .h264 {
                    self.onSafeVideoFallbackRequired("Peer connection failed while using \(self.selectedCodec.rawValue)")
                } else {
                    self.fail("Peer connection failed")
                }
            case .disconnected:
                self.updateStatus("Reconnecting", detail: "Peer connection disconnected")
            case .closed:
                break
            default:
                break
            }
        }
    }
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didAdd receiver: RTCRtpReceiver, streams mediaStreams: [RTCMediaStream]) {
        Task { @MainActor in
            if let track = receiver.track as? RTCVideoTrack {
                self.setVideoTrack(track)
            }
        }
    }
}

extension NativeStreamCoordinator: RTCVideoViewDelegate {
    nonisolated func videoView(_ videoView: any RTCVideoRenderer, didChangeVideoSize size: CGSize) {
        Task { @MainActor in
            self.renderedVideoSize = size
            self.log("Native video size changed \(Int(size.width))x\(Int(size.height))")
        }
    }
}

extension NativeStreamCoordinator: RTCDataChannelDelegate {
    nonisolated func dataChannelDidChangeState(_ dataChannel: RTCDataChannel) {
        Task { @MainActor in
            if dataChannel.readyState == .open {
                if dataChannel === self.reliableInputChannel {
                    self.inputBridge.advertiseHaptics()
                    self.log("Reliable input channel open")
                } else if dataChannel === self.partiallyReliableInputChannel {
                    self.log("Partially reliable input channel open")
                } else if dataChannel.label == "control_channel" {
                    self.log("Control channel open")
                }
            }
        }
    }

    nonisolated func dataChannel(_ dataChannel: RTCDataChannel, didReceiveMessageWith buffer: RTCDataBuffer) {
        Task { @MainActor in
            if buffer.isBinary {
                self.parseHapticsMessage(buffer.data)
            } else if let text = String(data: buffer.data, encoding: .utf8),
                      text.contains("request_keyframe") {
                self.log("Control channel requested keyframe")
            }
        }
    }
}

private final class NativeStreamPictureInPictureBridge: NSObject {
    var onAvailabilityChanged: ((Bool) -> Void)?
    var onActiveChanged: ((Bool) -> Void)?
    var onLog: ((String) -> Void)?

    private let frameQueue = DispatchQueue(label: "OpenNOW.NativeStreamer.PiPFrames")
    private weak var displayLayer: AVSampleBufferDisplayLayer?
    private var contentSource: AVPictureInPictureController.ContentSource?
    private var pictureInPictureController: AVPictureInPictureController?
    private var availabilityPublished = false
    private var playbackPaused = false

    private var frameSequence: Int64 = 0
    private var hasFrameForPlayback = false
    private var activeForFrameQueue = false
    private var lastAcceptedFrameAt: TimeInterval = 0
    private var cachedFormatDescription: CMVideoFormatDescription?
    private var cachedPixelFormat: OSType = 0
    private var cachedWidth = 0
    private var cachedHeight = 0

    private static let activeFrameInterval: TimeInterval = 1.0 / 30.0
    private static let inactiveFrameInterval: TimeInterval = 0.5
    private static let sampleTimescale: CMTimeScale = 60

    var isPictureInPictureActive: Bool {
        pictureInPictureController?.isPictureInPictureActive == true
    }

    func attach(displayLayer: AVSampleBufferDisplayLayer) {
        self.displayLayer = displayLayer
        hasFrameForPlayback = false
        playbackPaused = false
        displayLayer.videoGravity = .resizeAspect
        displayLayer.backgroundColor = UIColor.black.cgColor
        displayLayer.flush()

        guard AVPictureInPictureController.isPictureInPictureSupported() else {
            publishAvailability(false)
            onLog?("Picture in Picture is not supported on this device")
            return
        }

        let contentSource = AVPictureInPictureController.ContentSource(
            sampleBufferDisplayLayer: displayLayer,
            playbackDelegate: self
        )
        let controller = AVPictureInPictureController(contentSource: contentSource)
        controller.delegate = self
        controller.requiresLinearPlayback = true
        controller.canStartPictureInPictureAutomaticallyFromInline = true
        self.contentSource = contentSource
        self.pictureInPictureController = controller
        publishAvailability(false)
    }

    @discardableResult
    func start() -> Bool {
        guard let controller = pictureInPictureController,
              AVPictureInPictureController.isPictureInPictureSupported() else {
            return false
        }
        if controller.isPictureInPictureActive {
            return true
        }
        guard controller.isPictureInPicturePossible else {
            return false
        }
        controller.startPictureInPicture()
        return true
    }

    func stop() {
        guard pictureInPictureController?.isPictureInPictureActive == true else { return }
        pictureInPictureController?.stopPictureInPicture()
    }

    func detach() {
        stop()
        publishActive(false)
        publishAvailability(false)
        hasFrameForPlayback = false
        playbackPaused = false
        pictureInPictureController?.delegate = nil
        pictureInPictureController = nil
        contentSource = nil
        displayLayer?.flush()
        displayLayer = nil
        frameQueue.async { [weak self] in
            self?.resetFrameState()
        }
    }

    func enqueue(frame: RTCVideoFrame) {
        guard let cvBuffer = frame.buffer as? RTCCVPixelBuffer else { return }
        let pixelBuffer = cvBuffer.pixelBuffer
        frameQueue.async { [weak self] in
            self?.enqueue(pixelBuffer: pixelBuffer)
        }
    }

    private func enqueue(pixelBuffer: CVPixelBuffer) {
        guard shouldAcceptFrame() else { return }
        guard let sampleBuffer = makeSampleBuffer(pixelBuffer: pixelBuffer) else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self, let displayLayer = self.displayLayer else { return }
            if displayLayer.status == .failed || displayLayer.requiresFlushToResumeDecoding {
                displayLayer.flush()
            }
            guard displayLayer.isReadyForMoreMediaData else { return }
            displayLayer.enqueue(sampleBuffer)
            self.hasFrameForPlayback = true
            self.updateAvailabilityFromController()
            self.pictureInPictureController?.invalidatePlaybackState()
        }
    }

    private func shouldAcceptFrame() -> Bool {
        let now = ProcessInfo.processInfo.systemUptime
        let interval = activeForFrameQueue ? Self.activeFrameInterval : Self.inactiveFrameInterval
        guard lastAcceptedFrameAt == 0 || now - lastAcceptedFrameAt >= interval else {
            return false
        }
        lastAcceptedFrameAt = now
        return true
    }

    private func makeSampleBuffer(pixelBuffer: CVPixelBuffer) -> CMSampleBuffer? {
        guard let formatDescription = formatDescription(for: pixelBuffer) else { return nil }
        frameSequence += 1
        var timing = CMSampleTimingInfo(
            duration: CMTime(value: 1, timescale: Self.sampleTimescale),
            presentationTimeStamp: CMTime(value: frameSequence, timescale: Self.sampleTimescale),
            decodeTimeStamp: .invalid
        )
        var sampleBuffer: CMSampleBuffer?
        let status = CMSampleBufferCreateReadyWithImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: pixelBuffer,
            formatDescription: formatDescription,
            sampleTiming: &timing,
            sampleBufferOut: &sampleBuffer
        )
        guard status == noErr, let sampleBuffer else { return nil }
        markDisplayImmediately(sampleBuffer)
        return sampleBuffer
    }

    private func formatDescription(for pixelBuffer: CVPixelBuffer) -> CMVideoFormatDescription? {
        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        let pixelFormat = CVPixelBufferGetPixelFormatType(pixelBuffer)
        if let cachedFormatDescription,
           cachedWidth == width,
           cachedHeight == height,
           cachedPixelFormat == pixelFormat {
            return cachedFormatDescription
        }

        var formatDescription: CMVideoFormatDescription?
        let status = CMVideoFormatDescriptionCreateForImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: pixelBuffer,
            formatDescriptionOut: &formatDescription
        )
        guard status == noErr, let formatDescription else { return nil }
        cachedFormatDescription = formatDescription
        cachedWidth = width
        cachedHeight = height
        cachedPixelFormat = pixelFormat
        return formatDescription
    }

    private func markDisplayImmediately(_ sampleBuffer: CMSampleBuffer) {
        guard let attachments = CMSampleBufferGetSampleAttachmentsArray(
            sampleBuffer,
            createIfNecessary: true
        ), CFArrayGetCount(attachments) > 0 else {
            return
        }
        let rawDictionary = CFArrayGetValueAtIndex(attachments, 0)
        let attachmentsDictionary = unsafeBitCast(rawDictionary, to: CFMutableDictionary.self)
        CFDictionarySetValue(
            attachmentsDictionary,
            Unmanaged.passUnretained(kCMSampleAttachmentKey_DisplayImmediately).toOpaque(),
            Unmanaged.passUnretained(kCFBooleanTrue).toOpaque()
        )
    }

    private func updateAvailabilityFromController() {
        publishAvailability(pictureInPictureController?.isPictureInPicturePossible == true)
    }

    private func publishAvailability(_ available: Bool) {
        guard availabilityPublished != available else { return }
        availabilityPublished = available
        onAvailabilityChanged?(available)
    }

    private func publishActive(_ active: Bool) {
        playbackPaused = false
        frameQueue.async { [weak self] in
            self?.activeForFrameQueue = active
            if active {
                self?.lastAcceptedFrameAt = 0
            }
        }
        onActiveChanged?(active)
        pictureInPictureController?.invalidatePlaybackState()
    }

    private func resetFrameState() {
        frameSequence = 0
        activeForFrameQueue = false
        lastAcceptedFrameAt = 0
        cachedFormatDescription = nil
        cachedPixelFormat = 0
        cachedWidth = 0
        cachedHeight = 0
    }
}

extension NativeStreamPictureInPictureBridge: AVPictureInPictureControllerDelegate {
    func pictureInPictureControllerDidStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        publishActive(true)
        onLog?("Picture in Picture started")
    }

    func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        publishActive(false)
        onLog?("Picture in Picture stopped")
    }

    func pictureInPictureController(
        _ pictureInPictureController: AVPictureInPictureController,
        failedToStartPictureInPictureWithError error: Error
    ) {
        publishActive(false)
        onLog?("Picture in Picture failed: \(error.localizedDescription)")
    }

    func pictureInPictureController(
        _ pictureInPictureController: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(true)
    }
}

extension NativeStreamPictureInPictureBridge: AVPictureInPictureSampleBufferPlaybackDelegate {
    func pictureInPictureController(
        _ pictureInPictureController: AVPictureInPictureController,
        setPlaying playing: Bool
    ) {
        playbackPaused = false
        pictureInPictureController.invalidatePlaybackState()
    }

    func pictureInPictureControllerTimeRangeForPlayback(
        _ pictureInPictureController: AVPictureInPictureController
    ) -> CMTimeRange {
        hasFrameForPlayback
            ? CMTimeRange(start: .zero, duration: .positiveInfinity)
            : .invalid
    }

    func pictureInPictureControllerIsPlaybackPaused(
        _ pictureInPictureController: AVPictureInPictureController
    ) -> Bool {
        playbackPaused
    }

    func pictureInPictureController(
        _ pictureInPictureController: AVPictureInPictureController,
        didTransitionToRenderSize newRenderSize: CMVideoDimensions
    ) {
        onLog?("Picture in Picture render size \(newRenderSize.width)x\(newRenderSize.height)")
    }

    func pictureInPictureController(
        _ pictureInPictureController: AVPictureInPictureController,
        skipByInterval skipInterval: CMTime,
        completion completionHandler: @escaping () -> Void
    ) {
        completionHandler()
    }

    func pictureInPictureControllerShouldProhibitBackgroundAudioPlayback(
        _ pictureInPictureController: AVPictureInPictureController
    ) -> Bool {
        false
    }
}

private final class NativeStreamVideoSink: NSObject, RTCVideoRenderer {
    var onFrame: ((Int, CGSize, Int?) -> Void)?
    var onPictureInPictureFrame: ((RTCVideoFrame) -> Void)?

    private weak var renderView: NativeStreamRenderView?
    private let lock = NSLock()
    private var frameCount = 0
    private var lastSize: CGSize = .zero

    func attach(renderView: NativeStreamRenderView) {
        lock.lock()
        self.renderView = renderView
        frameCount = 0
        lastSize = .zero
        lock.unlock()
    }

    func detach() {
        lock.lock()
        renderView = nil
        frameCount = 0
        lastSize = .zero
        lock.unlock()
    }

    func setSize(_ size: CGSize) {
        lock.lock()
        lastSize = size
        let target = renderView
        lock.unlock()
        target?.setSize(size)
    }

    func renderFrame(_ frame: RTCVideoFrame?) {
        lock.lock()
        frameCount += 1
        let count = frameCount
        let size = CGSize(
            width: frame.map { CGFloat($0.width) } ?? lastSize.width,
            height: frame.map { CGFloat($0.height) } ?? lastSize.height
        )
        let target = renderView
        lock.unlock()

        target?.renderFrame(frame)
        if let frame {
            onPictureInPictureFrame?(frame)
        }
        if count <= 3 || count.isMultiple(of: 60) {
            onFrame?(count, size, Self.sampleLuma(from: frame))
        }
    }

    private static func sampleLuma(from frame: RTCVideoFrame?) -> Int? {
        if let cvBuffer = frame?.buffer as? RTCCVPixelBuffer,
           let luma = sampleNativeLuma(from: cvBuffer.pixelBuffer) {
            return luma
        }
        guard let i420 = frame?.newI420().buffer.toI420() else { return nil }
        let width = max(1, Int(i420.width))
        let height = max(1, Int(i420.height))
        let stride = max(1, Int(i420.strideY))
        let dataY = i420.dataY
        let samplesX = min(8, width)
        let samplesY = min(8, height)
        var total = 0
        var count = 0
        for yIndex in 0..<samplesY {
            let y = min(height - 1, yIndex * height / samplesY)
            for xIndex in 0..<samplesX {
                let x = min(width - 1, xIndex * width / samplesX)
                total += Int(dataY[y * stride + x])
                count += 1
            }
        }
        return count == 0 ? nil : total / count
    }

    private static func sampleNativeLuma(from pixelBuffer: CVPixelBuffer) -> Int? {
        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }

        if CVPixelBufferIsPlanar(pixelBuffer), CVPixelBufferGetPlaneCount(pixelBuffer) > 0 {
            guard let base = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0) else { return nil }
            let width = max(1, CVPixelBufferGetWidthOfPlane(pixelBuffer, 0))
            let height = max(1, CVPixelBufferGetHeightOfPlane(pixelBuffer, 0))
            let stride = max(1, CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0))
            return averagePlane(base: base, width: width, height: height, stride: stride)
        }

        guard let base = CVPixelBufferGetBaseAddress(pixelBuffer) else { return nil }
        let width = max(1, CVPixelBufferGetWidth(pixelBuffer))
        let height = max(1, CVPixelBufferGetHeight(pixelBuffer))
        let stride = max(1, CVPixelBufferGetBytesPerRow(pixelBuffer))
        return averageBGRA(base: base, width: width, height: height, stride: stride)
    }

    private static func averagePlane(base: UnsafeMutableRawPointer, width: Int, height: Int, stride: Int) -> Int? {
        let bytes = base.assumingMemoryBound(to: UInt8.self)
        let samplesX = min(8, width)
        let samplesY = min(8, height)
        var total = 0
        var count = 0
        for yIndex in 0..<samplesY {
            let y = min(height - 1, yIndex * height / samplesY)
            for xIndex in 0..<samplesX {
                let x = min(width - 1, xIndex * width / samplesX)
                total += Int(bytes[y * stride + x])
                count += 1
            }
        }
        return count == 0 ? nil : total / count
    }

    private static func averageBGRA(base: UnsafeMutableRawPointer, width: Int, height: Int, stride: Int) -> Int? {
        let bytes = base.assumingMemoryBound(to: UInt8.self)
        let samplesX = min(8, width)
        let samplesY = min(8, height)
        var total = 0
        var count = 0
        for yIndex in 0..<samplesY {
            let y = min(height - 1, yIndex * height / samplesY)
            for xIndex in 0..<samplesX {
                let x = min(width - 1, xIndex * width / samplesX)
                let offset = y * stride + x * 4
                let b = Int(bytes[offset])
                let g = Int(bytes[offset + 1])
                let r = Int(bytes[offset + 2])
                total += (r * 54 + g * 183 + b * 19) >> 8
                count += 1
            }
        }
        return count == 0 ? nil : total / count
    }
}

private struct NativeStreamVideoView: UIViewRepresentable {
    @ObservedObject var coordinator: NativeStreamCoordinator

    func makeUIView(context: Context) -> NativeStreamRenderView {
        let view = NativeStreamRenderView(frame: .zero)
        coordinator.attachRenderer(view)
        return view
    }

    func updateUIView(_ uiView: NativeStreamRenderView, context: Context) {
        coordinator.attachRenderer(uiView)
    }
}

private final class NativeStreamRenderView: UIView {
    let pictureInPictureDisplayLayer = AVSampleBufferDisplayLayer()

    private let metalVideoView = RTCMTLVideoView(frame: .zero)
    private let frameMetalView = NativeStreamFrameMetalView.make()

    var metalDelegate: RTCVideoViewDelegate? {
        get { metalVideoView.delegate }
        set { metalVideoView.delegate = newValue }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        isOpaque = true
        pictureInPictureDisplayLayer.videoGravity = .resizeAspect
        pictureInPictureDisplayLayer.backgroundColor = UIColor.black.cgColor
        layer.addSublayer(pictureInPictureDisplayLayer)
        metalVideoView.videoContentMode = .scaleAspectFit
        metalVideoView.backgroundColor = .black
        metalVideoView.isEnabled = true
        addSubview(metalVideoView)
        if let frameMetalView {
            addSubview(frameMetalView)
        }
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        pictureInPictureDisplayLayer.frame = bounds
        metalVideoView.frame = bounds
        frameMetalView?.frame = bounds
    }

    func setSize(_ size: CGSize) {
        metalVideoView.setSize(size)
        frameMetalView?.videoSize = size
    }

    func renderFrame(_ frame: RTCVideoFrame?) {
        metalVideoView.renderFrame(frame)
        frameMetalView?.display(frame: frame)
    }
}

private final class NativeStreamFrameMetalView: UIView, MTKViewDelegate {
    var videoSize: CGSize = .zero

    private let device: MTLDevice
    private let commandQueue: MTLCommandQueue
    private let ciContext: CIContext
    private let colorSpace = CGColorSpaceCreateDeviceRGB()
    private let mtkView: MTKView
    private let lock = NSLock()
    private var latestPixelBuffer: CVPixelBuffer?
    private var latestFrameSize: CGSize = .zero
    private var renderScheduled = false

    static func make() -> NativeStreamFrameMetalView? {
        guard let device = MTLCreateSystemDefaultDevice(),
              let queue = device.makeCommandQueue() else {
            return nil
        }
        return NativeStreamFrameMetalView(device: device, commandQueue: queue)
    }

    private init(device: MTLDevice, commandQueue: MTLCommandQueue) {
        self.device = device
        self.commandQueue = commandQueue
        self.ciContext = CIContext(mtlDevice: device)
        self.mtkView = MTKView(frame: .zero, device: device)
        super.init(frame: .zero)
        isOpaque = true
        backgroundColor = .black
        mtkView.framebufferOnly = false
        mtkView.isPaused = true
        mtkView.enableSetNeedsDisplay = false
        mtkView.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
        mtkView.backgroundColor = .black
        mtkView.delegate = self
        addSubview(mtkView)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        mtkView.frame = bounds
    }

    func display(frame: RTCVideoFrame?) {
        guard let cvBuffer = frame?.buffer as? RTCCVPixelBuffer else { return }
        let pixelBuffer = cvBuffer.pixelBuffer
        let frameSize = CGSize(
            width: frame.map { CGFloat($0.width) } ?? CGFloat(CVPixelBufferGetWidth(pixelBuffer)),
            height: frame.map { CGFloat($0.height) } ?? CGFloat(CVPixelBufferGetHeight(pixelBuffer))
        )

        lock.lock()
        latestPixelBuffer = pixelBuffer
        latestFrameSize = frameSize
        guard !renderScheduled else {
            lock.unlock()
            return
        }
        renderScheduled = true
        lock.unlock()

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.lock.lock()
            self.renderScheduled = false
            self.lock.unlock()
            self.mtkView.draw()
        }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        lock.lock()
        let pixelBuffer = latestPixelBuffer
        let frameSize = latestFrameSize
        lock.unlock()

        guard let pixelBuffer,
              let drawable = view.currentDrawable,
              let commandBuffer = commandQueue.makeCommandBuffer() else {
            return
        }

        if let descriptor = view.currentRenderPassDescriptor,
           let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: descriptor) {
            encoder.endEncoding()
        }

        let sourceImage = CIImage(cvPixelBuffer: pixelBuffer)
        let sourceExtent = sourceImage.extent
        let targetBounds = CGRect(origin: .zero, size: view.drawableSize)
        let fitted = Self.aspectFitRect(
            source: frameSize == .zero ? sourceExtent.size : frameSize,
            target: targetBounds.size
        )
        let scaleX = fitted.width / max(sourceExtent.width, 1)
        let scaleY = fitted.height / max(sourceExtent.height, 1)
        let transform = CGAffineTransform(translationX: fitted.minX, y: fitted.minY)
            .scaledBy(x: scaleX, y: scaleY)
        let outputImage = sourceImage.transformed(by: transform)

        ciContext.render(
            outputImage,
            to: drawable.texture,
            commandBuffer: commandBuffer,
            bounds: targetBounds,
            colorSpace: colorSpace
        )
        commandBuffer.present(drawable)
        commandBuffer.commit()
    }

    private static func aspectFitRect(source: CGSize, target: CGSize) -> CGRect {
        guard source.width > 0, source.height > 0, target.width > 0, target.height > 0 else {
            return CGRect(origin: .zero, size: target)
        }
        let scale = min(target.width / source.width, target.height / source.height)
        let width = source.width * scale
        let height = source.height * scale
        return CGRect(
            x: (target.width - width) / 2,
            y: (target.height - height) / 2,
            width: width,
            height: height
        )
    }
}

private struct NativeStreamTouchCaptureView: UIViewRepresentable {
    let inputBridge: NativeStreamInputBridge

    func makeUIView(context: Context) -> NativeStreamTouchView {
        let view = NativeStreamTouchView()
        view.inputBridge = inputBridge
        return view
    }

    func updateUIView(_ uiView: NativeStreamTouchView, context: Context) {
        uiView.inputBridge = inputBridge
    }
}

private extension CGPoint {
    func distance(to other: CGPoint) -> CGFloat {
        hypot(x - other.x, y - other.y)
    }
}

private final class NativeStreamTouchView: UIView {
    weak var inputBridge: NativeStreamInputBridge?
    private static let tapMovementThreshold: CGFloat = 8
    private static let clickReleaseDelay: TimeInterval = 0.045

    private var activeTouch: UITouch?
    private var touchStartPoint: CGPoint?
    private var lastPoint: CGPoint?
    private var movedBeyondTapThreshold = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = true
        backgroundColor = .clear
        isAccessibilityElement = true
        accessibilityLabel = "Stream input surface"
        accessibilityTraits = [.button, .allowsDirectInteraction]
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var canBecomeFirstResponder: Bool { true }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            becomeFirstResponder()
        }
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard activeTouch == nil, let touch = touches.first else { return }
        becomeFirstResponder()
        activeTouch = touch
        let point = touch.location(in: self)
        touchStartPoint = point
        lastPoint = point
        movedBeyondTapThreshold = false
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let activeTouch, touches.contains(activeTouch), let lastPoint else { return }
        let point = activeTouch.location(in: self)
        if let touchStartPoint, point.distance(to: touchStartPoint) > Self.tapMovementThreshold {
            movedBeyondTapThreshold = true
        }
        inputBridge?.sendTouchMouseMove(dx: point.x - lastPoint.x, dy: point.y - lastPoint.y)
        self.lastPoint = point
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let activeTouch, touches.contains(activeTouch) else { return }
        let shouldClick = !movedBeyondTapThreshold
        resetActiveTouch()
        if shouldClick {
            sendPrimaryClick()
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let activeTouch, touches.contains(activeTouch) else { return }
        resetActiveTouch()
    }

    private func resetActiveTouch() {
        activeTouch = nil
        touchStartPoint = nil
        lastPoint = nil
        movedBeyondTapThreshold = false
    }

    override func accessibilityActivate() -> Bool {
        sendPrimaryClick()
        return true
    }

    private func sendPrimaryClick() {
        inputBridge?.sendMouseButton(1, pressed: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.clickReleaseDelay) { [weak self] in
            self?.inputBridge?.sendMouseButton(1, pressed: false)
        }
    }

    override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        guard forwardPresses(presses, pressed: true) else {
            super.pressesBegan(presses, with: event)
            return
        }
    }

    override func pressesEnded(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        guard forwardPresses(presses, pressed: false) else {
            super.pressesEnded(presses, with: event)
            return
        }
    }

    override func pressesCancelled(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        _ = forwardPresses(presses, pressed: false)
        super.pressesCancelled(presses, with: event)
    }

    private func forwardPresses(_ presses: Set<UIPress>, pressed: Bool) -> Bool {
        var handled = false
        for press in presses {
            guard let key = press.key,
                  let mapping = NativeStreamUIKitKeyboardMapper.mapping(for: key.keyCode) else { continue }
            inputBridge?.sendKey(
                mapping: mapping,
                pressed: pressed,
                modifiers: NativeStreamUIKitKeyboardMapper.modifiers(for: key.modifierFlags)
            )
            handled = true
        }
        return handled
    }
}

private enum NativeStreamUIKitKeyboardMapper {
    private static let mappingByHID: [Int: NativeStreamKeyboardMapping] = [
        0x04: .init(virtualKey: 0x41, scanCode: 0x001e),
        0x05: .init(virtualKey: 0x42, scanCode: 0x0030),
        0x06: .init(virtualKey: 0x43, scanCode: 0x002e),
        0x07: .init(virtualKey: 0x44, scanCode: 0x0020),
        0x08: .init(virtualKey: 0x45, scanCode: 0x0012),
        0x09: .init(virtualKey: 0x46, scanCode: 0x0021),
        0x0a: .init(virtualKey: 0x47, scanCode: 0x0022),
        0x0b: .init(virtualKey: 0x48, scanCode: 0x0023),
        0x0c: .init(virtualKey: 0x49, scanCode: 0x0017),
        0x0d: .init(virtualKey: 0x4a, scanCode: 0x0024),
        0x0e: .init(virtualKey: 0x4b, scanCode: 0x0025),
        0x0f: .init(virtualKey: 0x4c, scanCode: 0x0026),
        0x10: .init(virtualKey: 0x4d, scanCode: 0x0032),
        0x11: .init(virtualKey: 0x4e, scanCode: 0x0031),
        0x12: .init(virtualKey: 0x4f, scanCode: 0x0018),
        0x13: .init(virtualKey: 0x50, scanCode: 0x0019),
        0x14: .init(virtualKey: 0x51, scanCode: 0x0010),
        0x15: .init(virtualKey: 0x52, scanCode: 0x0013),
        0x16: .init(virtualKey: 0x53, scanCode: 0x001f),
        0x17: .init(virtualKey: 0x54, scanCode: 0x0014),
        0x18: .init(virtualKey: 0x55, scanCode: 0x0016),
        0x19: .init(virtualKey: 0x56, scanCode: 0x002f),
        0x1a: .init(virtualKey: 0x57, scanCode: 0x0011),
        0x1b: .init(virtualKey: 0x58, scanCode: 0x002d),
        0x1c: .init(virtualKey: 0x59, scanCode: 0x0015),
        0x1d: .init(virtualKey: 0x5a, scanCode: 0x002c),
        0x1e: .init(virtualKey: 0x31, scanCode: 0x0002),
        0x1f: .init(virtualKey: 0x32, scanCode: 0x0003),
        0x20: .init(virtualKey: 0x33, scanCode: 0x0004),
        0x21: .init(virtualKey: 0x34, scanCode: 0x0005),
        0x22: .init(virtualKey: 0x35, scanCode: 0x0006),
        0x23: .init(virtualKey: 0x36, scanCode: 0x0007),
        0x24: .init(virtualKey: 0x37, scanCode: 0x0008),
        0x25: .init(virtualKey: 0x38, scanCode: 0x0009),
        0x26: .init(virtualKey: 0x39, scanCode: 0x000a),
        0x27: .init(virtualKey: 0x30, scanCode: 0x000b),
        0x28: .init(virtualKey: 0x0d, scanCode: 0x001c),
        0x29: .init(virtualKey: 0x1b, scanCode: 0x0001),
        0x2a: .init(virtualKey: 0x08, scanCode: 0x000e),
        0x2b: .init(virtualKey: 0x09, scanCode: 0x000f),
        0x2c: .init(virtualKey: 0x20, scanCode: 0x0039),
        0x2d: .init(virtualKey: 0xbd, scanCode: 0x000c),
        0x2e: .init(virtualKey: 0xbb, scanCode: 0x000d),
        0x2f: .init(virtualKey: 0xdb, scanCode: 0x001a),
        0x30: .init(virtualKey: 0xdd, scanCode: 0x001b),
        0x31: .init(virtualKey: 0xdc, scanCode: 0x002b),
        0x33: .init(virtualKey: 0xba, scanCode: 0x0027),
        0x34: .init(virtualKey: 0xde, scanCode: 0x0028),
        0x35: .init(virtualKey: 0xc0, scanCode: 0x0029),
        0x36: .init(virtualKey: 0xbc, scanCode: 0x0033),
        0x37: .init(virtualKey: 0xbe, scanCode: 0x0034),
        0x38: .init(virtualKey: 0xbf, scanCode: 0x0035),
        0x39: .init(virtualKey: 0x14, scanCode: 0x003a),
        0x3a: .init(virtualKey: 0x70, scanCode: 0x003b),
        0x3b: .init(virtualKey: 0x71, scanCode: 0x003c),
        0x3c: .init(virtualKey: 0x72, scanCode: 0x003d),
        0x3d: .init(virtualKey: 0x73, scanCode: 0x003e),
        0x3e: .init(virtualKey: 0x74, scanCode: 0x003f),
        0x3f: .init(virtualKey: 0x75, scanCode: 0x0040),
        0x40: .init(virtualKey: 0x76, scanCode: 0x0041),
        0x41: .init(virtualKey: 0x77, scanCode: 0x0042),
        0x42: .init(virtualKey: 0x78, scanCode: 0x0043),
        0x43: .init(virtualKey: 0x79, scanCode: 0x0044),
        0x44: .init(virtualKey: 0x7a, scanCode: 0x0057),
        0x45: .init(virtualKey: 0x7b, scanCode: 0x0058),
        0x46: .init(virtualKey: 0x2c, scanCode: 0xe037),
        0x47: .init(virtualKey: 0x91, scanCode: 0x0046),
        0x48: .init(virtualKey: 0x13, scanCode: 0x0045),
        0x49: .init(virtualKey: 0x2d, scanCode: 0xe052),
        0x4a: .init(virtualKey: 0x24, scanCode: 0xe047),
        0x4b: .init(virtualKey: 0x21, scanCode: 0xe049),
        0x4c: .init(virtualKey: 0x2e, scanCode: 0xe053),
        0x4d: .init(virtualKey: 0x23, scanCode: 0xe04f),
        0x4e: .init(virtualKey: 0x22, scanCode: 0xe051),
        0x4f: .init(virtualKey: 0x27, scanCode: 0xe04d),
        0x50: .init(virtualKey: 0x25, scanCode: 0xe04b),
        0x51: .init(virtualKey: 0x28, scanCode: 0xe050),
        0x52: .init(virtualKey: 0x26, scanCode: 0xe048),
        0x58: .init(virtualKey: 0x0d, scanCode: 0xe01c),
        0x65: .init(virtualKey: 0x5d, scanCode: 0xe05d),
        0xe0: .init(virtualKey: 0xa2, scanCode: 0x001d),
        0xe1: .init(virtualKey: 0xa0, scanCode: 0x002a),
        0xe2: .init(virtualKey: 0xa4, scanCode: 0x0038),
        0xe3: .init(virtualKey: 0x5b, scanCode: 0xe05b),
        0xe4: .init(virtualKey: 0xa3, scanCode: 0xe01d),
        0xe5: .init(virtualKey: 0xa1, scanCode: 0x0036),
        0xe6: .init(virtualKey: 0xa5, scanCode: 0xe038),
        0xe7: .init(virtualKey: 0x5c, scanCode: 0xe05c)
    ]

    static func mapping(for keyCode: UIKeyboardHIDUsage) -> NativeStreamKeyboardMapping? {
        mappingByHID[Int(keyCode.rawValue)]
    }

    static func modifiers(for flags: UIKeyModifierFlags) -> UInt16 {
        var value: UInt16 = 0
        if flags.contains(.shift) { value |= 0x01 }
        if flags.contains(.control) { value |= 0x02 }
        if flags.contains(.alternate) { value |= 0x04 }
        if flags.contains(.command) { value |= 0x08 }
        if flags.contains(.alphaShift) { value |= 0x10 }
        return value
    }
}

private struct NativeStreamRumbleCommand {
    let controllerId: Int
    let weakMagnitude: Int
    let strongMagnitude: Int
}

private enum NativeStreamHapticsParser {
    static func parse(_ data: Data) -> NativeStreamRumbleCommand? {
        let bytes = [UInt8](data)
        guard bytes.count >= 2 else { return nil }
        let firstWord = uint16LE(bytes, 0)
        if firstWord == 267 {
            return parseLegacy(bytes, offset: 2)
        }
        switch firstWord & 0xff {
        case 34:
            return parseSubMessage(bytes, offset: 1)
        case 32, 33, 35, 36, 255:
            return nil
        default:
            return parseLegacy(bytes, offset: 0)
        }
    }

    private static func parseSubMessage(_ bytes: [UInt8], offset: Int) -> NativeStreamRumbleCommand? {
        guard offset >= 0, offset + 4 <= bytes.count else { return nil }
        let type = uint32LE(bytes, offset)
        if type == 267 {
            return parseLegacy(bytes, offset: offset + 4)
        }
        if type == 17 {
            return parseOc(bytes, offset: offset + 4)
        }
        return nil
    }

    private static func parseLegacy(_ bytes: [UInt8], offset: Int) -> NativeStreamRumbleCommand? {
        guard offset >= 0, offset + 10 <= bytes.count else { return nil }
        guard uint16LE(bytes, offset) == 1 else { return nil }
        guard uint16LE(bytes, offset + 2) >= 6 else { return nil }
        return NativeStreamRumbleCommand(
            controllerId: Int(uint16LE(bytes, offset + 4)),
            weakMagnitude: Int(uint16LE(bytes, offset + 6)),
            strongMagnitude: Int(uint16LE(bytes, offset + 8))
        )
    }

    private static func parseOc(_ bytes: [UInt8], offset: Int) -> NativeStreamRumbleCommand? {
        guard offset >= 0, offset + 9 <= bytes.count else { return nil }
        let controllerByte = Int(bytes[offset])
        guard controllerByte >= 6, controllerByte < 10 else { return nil }
        let reportKind = bytes[offset + 3]
        let flags = bytes[offset + 4]
        guard reportKind == 5, (flags & 0xfe) == 0 else { return nil }
        return NativeStreamRumbleCommand(
            controllerId: controllerByte - 6,
            weakMagnitude: Int(bytes[offset + 7]) << 8,
            strongMagnitude: Int(bytes[offset + 8]) << 8
        )
    }

    private static func uint16LE(_ bytes: [UInt8], _ offset: Int) -> UInt16 {
        UInt16(bytes[offset]) | (UInt16(bytes[offset + 1]) << 8)
    }

    private static func uint32LE(_ bytes: [UInt8], _ offset: Int) -> UInt32 {
        UInt32(bytes[offset])
            | (UInt32(bytes[offset + 1]) << 8)
            | (UInt32(bytes[offset + 2]) << 16)
            | (UInt32(bytes[offset + 3]) << 24)
    }
}

private extension RTCRtpCodecCapability {
    func codecParameterInt(_ name: String) -> Int? {
        parameters[name].flatMap(Int.init)
    }
}
#endif
