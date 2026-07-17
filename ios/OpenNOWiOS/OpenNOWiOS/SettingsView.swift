import Foundation
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
#if os(iOS)
import PhotosUI
import UniformTypeIdentifiers
#endif

private enum SettingsCategory: String, CaseIterable, Hashable, Identifiable {
    case general
    case stream
    case input
    case interface
    case advanced
    case account

    var id: String { rawValue }

    var title: String {
        switch self {
        case .general: return "General"
        case .stream: return "Stream"
        case .input: return "Input"
        case .interface: return "Interface"
        case .advanced: return "Advanced"
        case .account: return "Account"
        }
    }

    var summary: String {
        switch self {
        case .general: return "Updates, privacy, cache, and reset"
        case .stream: return "Resolution, FPS, codec, HDR, proxy"
        case .input: return "Mouse, keyboard, touch controls, rumble"
        case .interface: return "Color, cards, stats, controller UI"
        case .advanced: return "Diagnostics, debug logs, nerd tools"
        case .account: return "Sign-in, storage, connected stores"
        }
    }

    var symbolName: String {
        switch self {
        case .general: return "gearshape"
        case .stream: return "play.rectangle"
        case .input: return "keyboard"
        case .interface: return "rectangle.grid.2x2"
        case .advanced: return "stethoscope"
        case .account: return "person.crop.circle"
        }
    }

    var keywords: [String] {
        switch self {
        case .general:
            return ["general", "updates", "privacy", "cache", "reset", "data"]
        case .stream:
            return ["stream", "preset", "resolution", "aspect ratio", "fps", "bitrate", "codec", "color", "hdr", "sharpening", "sharpness", "region", "session proxy", "proxy", "l4s", "cloud g-sync"]
        case .input:
            return ["input", "mouse", "sensitivity", "acceleration", "keyboard", "layout", "language", "touch", "controller", "rumble", "tutorial", "guide", "replay"]
        case .interface:
            return ["interface", "ui", "cards", "launch page", "stats", "server selector", "queue", "live activities", "catalog", "wallpaper", "background", "photo"]
        case .advanced:
            return ["advanced", "diagnostics", "debug", "logs", "codec", "native", "h264", "h265", "hevc", "av1"]
        case .account:
            return ["account", "login", "logout", "sign in", "saved", "provider", "membership", "subscription", "storage", "stores"]
        }
    }

    func matches(_ query: String) -> Bool {
        let tokens = query
            .lowercased()
            .split(whereSeparator: \.isWhitespace)
            .map(String.init)
        guard !tokens.isEmpty else { return true }
        let haystack = ([title, summary] + keywords).joined(separator: " ").lowercased()
        return tokens.allSatisfy { haystack.contains($0) }
    }
}

struct SettingsView: View {
    @EnvironmentObject private var store: OpenNOWStore
    @Environment(\.openURL) private var openURL
    @State private var searchText = ""
    @State private var showingResetConfirmation = false
    @State private var showingResetAppConfirmation = false
    @State private var showingSignOutAllConfirmation = false
    @State private var connectorPendingDisconnect: AccountConnector?
    #if os(iOS)
    @State private var selectedCatalogWallpaperItem: PhotosPickerItem?
    @State private var catalogWallpaperImportInProgress = false
    @State private var catalogWallpaperImportError: String?
    @State private var diagnosticsDocument = DiagnosticsTextDocument(text: "")
    @State private var diagnosticsExportInProgress = false
    @State private var showingDiagnosticsExporter = false
    @State private var diagnosticsExportError: String?
    @State private var diagnosticsExportStatus: String?
    #endif

    private let qualityValues = ["Balanced", "Data Saver", "Quality"]

    private var codecValues: [String] {
        let report = NativeStreamCodecProbe.report()
        var values = ["Auto", "H264"]
        if report.capability(for: .h265)?.launchSafe == true {
            values.append("H265")
        }
        if report.capability(for: .av1)?.launchSafe == true {
            values.append("AV1")
        }
        return values
    }

    var body: some View {
        NavigationStack {
            List {
                accountLandingSection
                settingsCategorySection
                aboutSection
                thanksSection
            }
            .navigationTitle("Settings")
            .searchable(text: $searchText, prompt: "Search settings")
            .navigationDestination(for: SettingsCategory.self) { category in
                Form {
                    settingsDetailContent(for: category)
                }
                .navigationTitle(category.title)
                .navigationBarTitleDisplayMode(.inline)
            }
            .onChangeCompat(of: store.settings) { _ in
                store.persistSettings()
            }
            .onChangeCompat(of: store.settings.queueLiveActivitiesEnabled) { _ in
                store.refreshTrackedSessionSurface()
            }
            .onAppear {
                enforceAvailableResolution()
                enforceAvailableFPS()
                enforceAvailableHDR()
                enforceAvailableCodec()
            }
            .onChangeCompat(of: store.settings.preferredAspectRatio) { _ in
                enforceAvailableResolution()
            }
            .onChangeCompat(of: currentMembershipTier ?? "") { _ in
                enforceAvailableResolution()
                enforceAvailableFPS()
                enforceAvailableHDR()
            }
            .onChangeCompat(of: store.settings.hdrEnabled) { enabled in
                guard enabled else { return }
                store.settings.preferredColorQuality = StreamColorQuality.tenBit420.rawValue
                if NativeStreamCodecProbe.report().capability(for: .h265)?.launchSafe == true {
                    store.settings.preferredCodec = "H265"
                }
            }
            .confirmationDialog("Reset settings?", isPresented: $showingResetConfirmation, titleVisibility: .visible) {
                Button("Reset Settings", role: .destructive) {
                    store.resetSettings()
                }
                Button("Cancel", role: .cancel) {}
            }
            .confirmationDialog("Reset app?", isPresented: $showingResetAppConfirmation, titleVisibility: .visible) {
                Button("Reset App", role: .destructive) {
                    store.resetApp()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This clears local settings, saved accounts, launch choices, favorites, session state, and cached images on this device.")
            }
            .confirmationDialog("Sign out all accounts?", isPresented: $showingSignOutAllConfirmation, titleVisibility: .visible) {
                Button("Sign Out All Accounts", role: .destructive) {
                    store.signOutAll()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This removes every saved GeForce NOW account from this device.")
            }
            .alert(
                "Disconnect account?",
                isPresented: Binding(
                    get: { connectorPendingDisconnect != nil },
                    set: { isPresented in
                        if !isPresented {
                            connectorPendingDisconnect = nil
                        }
                    }
                )
            ) {
                Button("Disconnect", role: .destructive) {
                    if let connector = connectorPendingDisconnect {
                        Task { await store.disconnectAccountConnector(connector) }
                    }
                    connectorPendingDisconnect = nil
                }
                Button("Cancel", role: .cancel) {
                    connectorPendingDisconnect = nil
                }
            } message: {
                Text("This removes the linked account from GeForce NOW. You can connect it again later.")
            }
            .task {
                await store.refreshAccountConnectors()
            }
        }
        .tint(brandAccent)
    }

    @ViewBuilder
    private var accountLandingSection: some View {
        if accountLandingVisible {
            Section {
                NavigationLink(value: SettingsCategory.account) {
                    HStack(spacing: 14) {
                        SettingsAccountAvatar(
                            initial: accountLandingInitial,
                            size: 52,
                            selected: true
                        )

                        VStack(alignment: .leading, spacing: 3) {
                            Text(accountLandingTitle)
                                .font(.body.weight(.semibold))
                                .lineLimit(1)
                            Text(accountLandingDetail)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    private var settingsCategorySection: some View {
        Section {
            ForEach(visibleLandingCategories) { category in
                NavigationLink(value: category) {
                    SettingsCategoryLabel(category: category)
                }
            }
        }
    }

    @ViewBuilder
    private func settingsDetailContent(for category: SettingsCategory) -> some View {
        switch category {
        case .general:
            appUpdatesSection
            privacySection
            dataSection
        case .stream:
            streamingSection
        case .input:
            inputSection
        case .interface:
            interfaceSection
        case .advanced:
            advancedSection
        case .account:
            savedAccountsSection
            accountActionsSection
            playTimeSection
            storageAddonSection
            accountConnectorsSection
        }
    }

    private var visibleLandingCategories: [SettingsCategory] {
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return landingCategories }
        return landingCategories.filter { $0.matches(trimmed) }
    }

    private var accountLandingVisible: Bool {
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty || SettingsCategory.account.matches(trimmed)
    }

    private var savedAccountsSection: some View {
        Section {
            if accountRows.isEmpty {
                Text("No saved accounts on this device.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(accountRows) { account in
                    savedAccountRow(account)
                }
            }
        } header: {
            Text("Saved Accounts")
        } footer: {
            Text("Switching accounts updates launches, library sync, cloud storage, and store connections.")
        }
    }

    private var accountActionsSection: some View {
        Section("Account Actions") {
            HStack {
                Button {
                    Task { await store.signIn(forceAccountSelection: true) }
                } label: {
                    Label("Add Account", systemImage: "person.badge.plus")
                }
                .disabled(store.isAuthenticating)

                Spacer()

                if store.isAuthenticating {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("Waiting for NVIDIA")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            if store.isAuthenticating {
                Text("Take your time completing sign-in. OpenNOW will wait up to five minutes for NVIDIA to return to the app.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            if let error = store.lastError, !error.isEmpty {
                Label(error, systemImage: "exclamationmark.triangle.fill")
                    .font(.footnote)
                    .foregroundStyle(.red)
            }

            if store.user != nil {
                Button(role: .destructive) {
                    store.signOut()
                } label: {
                    Label("Sign Out Current Account", systemImage: "rectangle.portrait.and.arrow.right")
                }
            }

            if store.savedAccounts.count > 1 {
                Button(role: .destructive) {
                    showingSignOutAllConfirmation = true
                } label: {
                    Label("Sign Out All Accounts", systemImage: "person.2.slash")
                }
            }
        }
    }

    private var storageAddonSection: some View {
        Section("Cloud Storage") {
            if let storage = store.subscription?.storageAddon {
                if let size = storage.sizeGb {
                    LabeledContent("Total", value: formatStorageGb(size))
                }
                if let used = storage.usedGb {
                    LabeledContent("Used", value: formatStorageGb(used))
                }
                if let size = storage.sizeGb, let used = storage.usedGb {
                    LabeledContent("Available", value: formatStorageGb(max(size - used, 0)))
                }
                if let usage = storage.usageFraction {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("Storage usage")
                            Spacer()
                            Text(formatStoragePercent(usage))
                                .foregroundStyle(.secondary)
                        }
                        ProgressView(value: usage)
                            .tint(storageUsageTint(usage))
                    }
                }
                if let region = storage.regionName, !region.isEmpty {
                    LabeledContent("Location", value: region)
                }
                Text("Storage purchases and changes are managed outside OpenNOW in your GeForce NOW account.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } else {
                Text("No persistent storage add-on is active for this account.")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var playTimeSection: some View {
        Section("Play Time") {
            LabeledContent("Membership", value: currentMembershipTier ?? "Unknown")
            LabeledContent("Session Limit", value: sessionLimitDescription)
            if let subscription = store.subscription, subscription.totalHours > 0 {
                LabeledContent("Monthly Allowance", value: hoursLabel(subscription.totalHours))
                LabeledContent("Monthly Remaining", value: hoursLabel(subscription.remainingHours))
                ProgressView(
                    value: min(max(subscription.totalHours - subscription.remainingHours, 0), subscription.totalHours),
                    total: subscription.totalHours
                )
            }
            if store.activeSession != nil {
                LabeledContent("Current Session", value: elapsedLabel(store.sessionElapsedSeconds))
            }
        }
    }

    private var accountConnectorsSection: some View {
        Section("Game Store Connections") {
            if store.loadingAccountConnectors && store.accountConnectors.isEmpty {
                HStack {
                    ProgressView()
                    Text("Loading connections")
                }
            } else if store.accountConnectors.isEmpty {
                OpenNOWUnavailableView(
                    "No Connections Found",
                    systemImage: "link.badge.plus"
                ) {
                    Text("Refresh to load supported store connections for this account.")
                }
                .frame(maxWidth: .infinity)
            } else {
                ForEach(store.accountConnectors.prefix(8)) { connector in
                    HStack(spacing: 12) {
                        StoreGlyph(store: connector.store)
                            .frame(width: 30, height: 30)
                            .accessibilityHidden(true)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(connector.label)
                                .font(.body.weight(.semibold))
                            Text(connectorStatusText(connector))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                                .truncationMode(.middle)
                        }

                        Spacer(minLength: 8)

                        if store.connectorActionStore == connector.store {
                            ProgressView()
                        } else if connector.isLinked {
                            Button("Disconnect", role: .destructive) {
                                connectorPendingDisconnect = connector
                            }
                            .buttonStyle(.bordered)
                        } else if connector.supported {
                            Button("Connect") {
                                Task {
                                    await store.connectAccountConnector(connector) { url in
                                        openURL(url)
                                    }
                                }
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    }
                    .padding(.vertical, 3)
                }
            }

            Button {
                Task { await store.refreshAccountConnectors() }
            } label: {
                Label(store.loadingAccountConnectors ? "Refreshing" : "Refresh Connections", systemImage: "arrow.clockwise")
            }
            .disabled(store.loadingAccountConnectors)

            Link(destination: store.accountHelpURL) {
                Label("Account Help", systemImage: "questionmark.circle")
            }
        }
    }

    private var streamingSection: some View {
        Section {
            Picker("Preset", selection: streamPresetBinding) {
                ForEach(StreamPreset.allCases) { preset in
                    Text(preset.label).tag(preset)
                }
            }

            Picker("Region", selection: $store.settings.preferredRegion) {
                Text("Automatic").tag("")
                if !store.settings.preferredRegion.isEmpty,
                   !store.availableRegions.contains(where: { $0.url == store.settings.preferredRegion }) {
                    Text("Saved Region").tag(store.settings.preferredRegion)
                }
                ForEach(store.availableRegions) { region in
                    Text(region.name).tag(region.url)
                }
            }

            Picker("Aspect Ratio", selection: customStreamBinding(\.preferredAspectRatio)) {
                ForEach(StreamSettingsResolver.aspectRatioOptions, id: \.self) { value in
                    Text(value).tag(value)
                }
            }

            Picker("Resolution", selection: customStreamBinding(\.preferredResolution)) {
                Text("Auto").tag("Auto")
                ForEach(StreamSettingsResolver.choices(forAspectRatio: store.settings.preferredAspectRatio)) { choice in
                    let available = resolutionAvailable(choice)
                    Text(resolutionLabel(for: choice, available: available))
                        .foregroundStyle(available ? .primary : .secondary)
                        .tag(choice.value)
                        .disabled(!available)
                }
            }

            Picker("Target FPS", selection: customStreamBinding(\.preferredFPS)) {
                ForEach(fpsValues, id: \.self) { Text("\($0) fps").tag($0) }
            }

            Picker("Quality", selection: customStreamBinding(\.preferredQuality)) {
                ForEach(qualityValues, id: \.self) { Text($0).tag($0) }
            }

            Picker("Codec", selection: codecSelectionBinding) {
                ForEach(codecValues, id: \.self) { Text($0).tag($0) }
            }

            Picker("Color", selection: customStreamBinding(\.preferredColorQuality)) {
                ForEach([StreamColorQuality.eightBit420, .tenBit420]) { color in
                    Text(color.label).tag(color.rawValue)
                }
            }

            Picker("Max Bitrate", selection: customStreamBinding(\.maxBitrateMbps)) {
                ForEach(StreamSettingsResolver.bitrateOptionsMbps, id: \.self) { bitrate in
                    Text(bitrateLabel(for: bitrate)).tag(bitrate)
                }
            }

            Toggle("HDR", isOn: $store.settings.hdrEnabled)
                .disabled(!hdrAvailable)

            Toggle("Cloud G-Sync", isOn: $store.settings.enableCloudGsync)

            Toggle("L4S Low Latency", isOn: $store.settings.enableL4S)

            Toggle("Stream Sharpening", isOn: $store.settings.streamSharpeningEnabled)

            if store.settings.streamSharpeningEnabled {
                VStack(alignment: .leading, spacing: 8) {
                    LabeledContent(
                        "Sharpness",
                        value: "\(Int((store.settings.streamSharpeningAmount * 100).rounded()))%"
                    )
                    Slider(
                        value: $store.settings.streamSharpeningAmount,
                        in: 0...1,
                        step: 0.05
                    )
                    .accessibilityLabel("Sharpness amount")
                }
            }

            Toggle("Stretch to Fill", isOn: $store.settings.streamerPreferences.stretchStreamToFill)

            Toggle("Session Proxy", isOn: $store.settings.sessionProxyEnabled)

            if store.settings.sessionProxyEnabled {
                TextField("Proxy URL", text: $store.settings.sessionProxyUrl)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
            }
        } header: {
            Text("Stream")
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                Text("Resolutions above your current plan are shown but unavailable.")
                if !hdrAvailable {
                    Text("HDR requires an Ultimate-capable account.")
                }
            }
        }
    }

    private var inputSection: some View {
        Section("Input") {
            LabeledContent {
                Slider(value: $store.settings.mouseSensitivity, in: 0.25...3, step: 0.05)
                    .frame(maxWidth: 180)
            } label: {
                Text("Mouse Sensitivity")
            }

            Picker("Mouse Acceleration", selection: $store.settings.mouseAcceleration) {
                Text("Off").tag(0)
                Text("Standard").tag(1)
                Text("High").tag(2)
            }

            Picker("Keyboard Layout", selection: $store.settings.keyboardLayout) {
                ForEach(StreamSettingsResolver.keyboardLayoutOptions, id: \.value) { option in
                    Text(option.label).tag(option.value)
                }
            }

            Picker("Game Language", selection: $store.settings.gameLanguage) {
                ForEach(StreamSettingsResolver.gameLanguageOptions, id: \.value) { option in
                    Text(option.label).tag(option.value)
                }
            }

            #if !os(tvOS)
            Toggle("Finger Mouse", isOn: $store.settings.fingerMouseEnabled)
            Toggle("Fortnite Mobile Touch", isOn: $store.settings.fortnitePrefersNativeTouch)
            Toggle("Touch Controller", isOn: $store.settings.streamerPreferences.touchControllerVisible)
            Toggle("Phone Rumble Fallback", isOn: $store.settings.phoneRumbleFallback)
            touchLayoutSlider("Touch Layout Scale", keyPath: \.scale, range: 0.6...1.4, step: 0.05)
            touchLayoutSlider("Touch Button Size", keyPath: \.buttonScale, range: 0.65...1.5, step: 0.05)
            touchLayoutSlider("Touch Stick Size", keyPath: \.stickScale, range: 0.65...1.5, step: 0.05)
            touchLayoutSlider("Touch Opacity", keyPath: \.opacity, range: 0.15...1, step: 0.05)
            #endif

            LabeledContent("Physical Controller", value: "Detected Automatically")
            LabeledContent(
                "Stream Tutorial",
                value: store.settings.streamTutorialCompleted ? "Completed" : "Shows on Next Stream"
            )
            Button {
                store.setStreamTutorialCompleted(false)
            } label: {
                Label("Replay Stream Tutorial", systemImage: "questionmark.circle")
            }
            .disabled(!store.settings.streamTutorialCompleted)
        }
    }

    private var interfaceSection: some View {
        let hasCustomCatalogWallpaper = store.settings.catalogWallpaperFilename != nil
        return Section("Interface") {
            Toggle("Nerd Mode", isOn: $store.settings.nerdMode)
            Toggle("Stats Overlay", isOn: $store.settings.showStatsOverlay)
            Picker("Stats Style", selection: $store.settings.streamerPreferences.statsStyle) {
                ForEach(StreamStatsStyle.allCases) { style in
                    Text(style.label).tag(style)
                }
            }
            Picker("Stats Position", selection: $store.settings.streamerPreferences.statsPosition) {
                ForEach(StreamStatsPosition.allCases) { position in
                    Text(position.label).tag(position)
                }
            }
            Toggle("Session Counter", isOn: $store.settings.sessionCounterEnabled)
            Picker("Open App To", selection: $store.settings.launchPage) {
                ForEach(AppLaunchPage.allCases) { page in
                    Text(page.label).tag(page)
                }
            }
            Toggle("Compact Game Cards", isOn: $store.settings.compactGameCards)
            Toggle("Show Store Labels", isOn: $store.settings.showGameStoreLabels)
            #if os(iOS)
            Toggle("Catalog Wallpaper", isOn: $store.settings.catalogWallpaperEnabled)
            if store.settings.catalogWallpaperEnabled {
                LabeledContent("Wallpaper", value: catalogWallpaperDescription)

                PhotosPicker(
                    selection: $selectedCatalogWallpaperItem,
                    matching: .images
                ) {
                    Label(
                        hasCustomCatalogWallpaper ? "Replace Image" : "Choose Image",
                        systemImage: "photo.on.rectangle"
                    )
                }
                .disabled(catalogWallpaperImportInProgress)
                .onChangeCompat(of: selectedCatalogWallpaperItem) { item in
                    guard let item else { return }
                    Task { await importCatalogWallpaper(item) }
                }

                if catalogWallpaperImportInProgress {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("Preparing wallpaper…")
                            .foregroundStyle(.secondary)
                    }
                }

                if hasCustomCatalogWallpaper {
                    Button("Use OpenNOW Gradient") {
                        resetCatalogWallpaperImage()
                    }
                }

                if let catalogWallpaperImportError {
                    Text(catalogWallpaperImportError)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }
            #endif
            LabeledContent {
                Slider(value: $store.settings.posterSizeScale, in: 0.75...1.4, step: 0.05)
                    .frame(maxWidth: 180)
            } label: {
                Text("Game Card Size")
            }
            #if !os(tvOS)
            Toggle("Queue Live Activities", isOn: $store.settings.queueLiveActivitiesEnabled)
            #endif
            Toggle("Skip Server Selector", isOn: $store.settings.hideServerSelector)
        }
    }

    private var landingCategories: [SettingsCategory] {
        var categories: [SettingsCategory] = [.general, .stream, .input, .interface]
        if store.settings.nerdMode {
            categories.append(.advanced)
        }
        return categories
    }

    private var appUpdatesSection: some View {
        Section("App Updates") {
            LabeledContent("Version", value: appVersion)
            LabeledContent("Build", value: buildNumber)
        }
    }

    private var privacySection: some View {
        Section("Privacy") {
            LabeledContent("Usage Analytics", value: "Not Collected")
            NavigationLink {
                OpenNOWPrivacyPolicyView()
            } label: {
                Label("Privacy Policy", systemImage: "hand.raised")
            }
        }
    }

    private var advancedSection: some View {
        Section {
            LabeledContent("Preferred Codec", value: store.settings.preferredCodec)
            LabeledContent("Stream Profile", value: headerSummary)
            LabeledContent("Color", value: selectedColorQualityLabel)
            LabeledContent("HDR", value: hdrAvailable ? (store.settings.hdrEnabled ? "On" : "Off") : "Unavailable")
            #if os(iOS)
            Button {
                diagnosticsExportInProgress = true
                diagnosticsExportError = nil
                diagnosticsExportStatus = nil
                Task {
                    defer { diagnosticsExportInProgress = false }
                    do {
                        let pasteURL = try await store.uploadDiagnosticsPaste()
                        UIPasteboard.general.string = store.diagnosticsClipboardSummary(pasteURL: pasteURL)
                        diagnosticsExportStatus = "Copied a compact build and tier summary with the full log paste link at the end."
                    } catch is CancellationError {
                        return
                    } catch {
                        diagnosticsExportError = error.localizedDescription
                    }
                }
            } label: {
                Label("Upload Redacted Logs & Copy Summary", systemImage: "doc.on.clipboard")
            }
            .disabled(diagnosticsExportInProgress)

            Button {
                diagnosticsExportInProgress = true
                diagnosticsExportError = nil
                diagnosticsExportStatus = nil
                Task {
                    diagnosticsDocument = DiagnosticsTextDocument(
                        text: await store.makeDiagnosticsExport()
                    )
                    diagnosticsExportInProgress = false
                    showingDiagnosticsExporter = true
                }
            } label: {
                Label("Export Extensive Logs", systemImage: "square.and.arrow.up")
            }
            .disabled(diagnosticsExportInProgress)
            .fileExporter(
                isPresented: $showingDiagnosticsExporter,
                document: diagnosticsDocument,
                contentType: .plainText,
                defaultFilename: store.diagnosticsExportFileName
            ) { result in
                if case .failure(let error) = result {
                    diagnosticsExportError = error.localizedDescription
                }
            }

            if diagnosticsExportInProgress {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("Sanitizing app, API, queue, and stream logs…")
                        .foregroundStyle(.secondary)
                }
            }
            if let diagnosticsExportStatus {
                Text(diagnosticsExportStatus)
                    .font(.footnote)
                    .foregroundStyle(.green)
            }
            if let diagnosticsExportError {
                Text(diagnosticsExportError)
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
            #else
            ShareLink(item: store.diagnosticsReport) {
                Label("Share Redacted Diagnostics", systemImage: "square.and.arrow.up")
            }
            #endif
        } header: {
            Text("Codec Diagnostics")
        } footer: {
            Text("Includes detailed redacted API requests and responses, launch, queue, stream, codec, device, settings, telemetry, and recent OpenNOW logs. Uploads use an unlisted public paste that expires after 7 days. Credentials, tokens, cookies, OAuth values, email addresses, local user paths, identifiers, IP addresses, and long opaque values are removed or fingerprinted before upload. Only the compact summary and paste link are copied.")
        }
    }

    private var dataSection: some View {
        Section("Data") {
            Button {
                Task { await store.refreshCatalog() }
            } label: {
                Label(store.isLoadingGames ? "Reloading Catalog" : "Reload Catalog", systemImage: "arrow.clockwise")
            }
            .disabled(store.isLoadingGames)

            Button {
                store.clearImageCache()
            } label: {
                Label("Clear Image Cache", systemImage: "trash")
            }

            if !store.settings.defaultGameVariantIds.isEmpty {
                Button(role: .destructive) {
                    store.clearDefaultGameVariants()
                } label: {
                    Label("Clear Default Launchers", systemImage: "star.slash")
                }
            }

            if !store.settings.favoriteGameIds.isEmpty {
                Button(role: .destructive) {
                    store.clearFavorites()
                } label: {
                    Label("Clear Favorites", systemImage: "heart.slash")
                }
            }

            Button(role: .destructive) {
                showingResetConfirmation = true
            } label: {
                Label("Reset Settings", systemImage: "arrow.counterclockwise")
            }

            Button(role: .destructive) {
                showingResetAppConfirmation = true
            } label: {
                Label("Reset App", systemImage: "arrow.triangle.2.circlepath")
            }
        }
    }

    private var aboutSection: some View {
        Section("About") {
            LabeledContent("OpenNOW iOS", value: "Version \(appVersion)")
            LabeledContent("Build", value: buildNumber)
            LabeledContent("Platform", value: OpenNOWPlatform.displayName)
            Link(destination: URL(string: "https://github.com/OpenCloudGaming/OpenNOW")!) {
                Label("OpenNOW Repository", systemImage: "chevron.left.forwardslash.chevron.right")
            }
            Link(destination: URL(string: "https://github.com/Kief5555")!) {
                Label("Kiefer", systemImage: "person.crop.circle")
            }
            Link(destination: URL(string: "https://github.com/zortos293")!) {
                Label("Zortos", systemImage: "person.crop.circle")
            }
        }
    }

    private var thanksSection: some View {
        Section("Thanks") {
            Text("Thanks to the people helping improve OpenNOW for everyone.")
                .foregroundStyle(.secondary)
            LabeledContent("DarkevilPT", value: "Community support")
        }
    }

    private func savedAccountRow(_ account: SavedAccount) -> some View {
        let selected = account.userId == store.user?.userId

        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 12) {
                SettingsAccountAvatar(
                    initial: accountInitial(account),
                    size: 38,
                    selected: selected
                )

                VStack(alignment: .leading, spacing: 2) {
                    Text(account.displayName.isEmpty ? "NVIDIA Account" : account.displayName)
                        .font(.body.weight(.semibold))
                        .lineLimit(1)
                    Text(accountSubtitle(account))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer(minLength: 8)

                if store.switchingAccountUserId == account.userId {
                    ProgressView()
                        .accessibilityLabel("Switching account")
                } else if selected {
                    Text("Active")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.green)
                } else {
                    Button("Switch") {
                        Task { await store.switchAccount(to: account.userId) }
                    }
                    .buttonStyle(.bordered)
                    .disabled(store.switchingAccountUserId != nil || store.isAuthenticating)
                }
            }
        }
        .padding(.vertical, 3)
    }

    private func touchLayoutSlider(
        _ title: String,
        keyPath: WritableKeyPath<TouchControlLayout, Double>,
        range: ClosedRange<Double>,
        step: Double
    ) -> some View {
        let value = store.settings.touchLayout(for: "default")[keyPath: keyPath]

        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(title)
                Spacer()
                Text(percentLabel(value))
                    .foregroundStyle(.secondary)
            }
            Slider(value: touchLayoutBinding(keyPath), in: range, step: step)
        }
    }

    private func touchLayoutBinding(
        _ keyPath: WritableKeyPath<TouchControlLayout, Double>,
        profile: String = "default"
    ) -> Binding<Double> {
        Binding(
            get: {
                store.settings.touchLayout(for: profile)[keyPath: keyPath]
            },
            set: { newValue in
                var settings = store.settings
                var layout = settings.touchLayout(for: profile)
                layout[keyPath: keyPath] = newValue
                settings.touchControlLayouts[profile] = layout
                store.settings = settings
            }
        )
    }

    private var accountRows: [SavedAccount] {
        if !store.savedAccounts.isEmpty {
            return store.savedAccounts
        }
        guard let user = store.user else { return [] }
        let fallbackProvider = store.authProviderCode ?? "NVIDIA"
        return [
            SavedAccount(
                userId: user.userId,
                displayName: user.displayName,
                email: user.email,
                membershipTier: store.subscription?.membershipTier ?? user.membershipTier,
                providerCode: fallbackProvider
            )
        ]
    }

    private var landingAccount: SavedAccount? {
        accountRows.first { $0.userId == store.user?.userId } ?? accountRows.first
    }

    private var accountLandingTitle: String {
        landingAccount?.displayName.nonEmpty
            ?? store.user?.displayName.nonEmpty
            ?? "NVIDIA Account"
    }

    private var accountLandingDetail: String {
        let email = landingAccount?.email?.nonEmpty ?? store.user?.email?.nonEmpty
        let tier = store.subscription?.membershipTier.nonEmpty
            ?? landingAccount?.membershipTier.nonEmpty
            ?? store.user?.membershipTier.nonEmpty
        let detail = [email, tier].compactMap { $0 }.joined(separator: " - ")
        if !detail.isEmpty {
            return detail
        }
        return store.user == nil && landingAccount == nil ? "Sign in to sync your GeForce NOW account" : "Manage account"
    }

    private var accountLandingInitial: String {
        if let first = accountLandingTitle.first {
            return String(first).uppercased()
        }
        return "N"
    }

    private func accountInitial(_ account: SavedAccount) -> String {
        if let first = account.displayName.first ?? account.email?.first {
            return String(first).uppercased()
        }
        return "N"
    }

    private func accountSubtitle(_ account: SavedAccount) -> String {
        [account.email, account.providerCode, account.membershipTier]
            .compactMap { value in
                let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                return trimmed.isEmpty ? nil : trimmed
            }
            .joined(separator: " - ")
    }

    private func formatStorageGb(_ value: Double) -> String {
        value == floor(value) ? "\(Int(value)) GB" : String(format: "%.1f GB", value)
    }

    private func formatStoragePercent(_ value: Double) -> String {
        "\(Int((value * 100).rounded()))% used"
    }

    private func percentLabel(_ value: Double) -> String {
        "\(Int((value * 100).rounded()))%"
    }

    private func storageUsageTint(_ value: Double) -> Color {
        if value >= 0.9 { return .red }
        if value >= 0.75 { return .orange }
        return .accentColor
    }

    private func connectorStatusText(_ connector: AccountConnector) -> String {
        if connector.isLinked {
            let identity = cleanConnectorIdentity(connector.userDisplayName)
                ?? cleanConnectorIdentity(connector.userIdentifier)
            let sync = connector.syncedGameCount.map { "\($0) synced games" }
                ?? cleanConnectorState(connector.syncState)
            let summary = [identity, sync]
                .compactMap(\.self)
                .joined(separator: " - ")
            return summary.isEmpty ? "Connected" : summary
        }
        if connector.required {
            return "Required for some launches"
        }
        return connector.supported ? "Not connected" : "Unsupported"
    }

    private func cleanConnectorIdentity(_ value: String?) -> String? {
        guard var cleaned = value?.trimmingCharacters(in: .whitespacesAndNewlines), !cleaned.isEmpty else {
            return nil
        }
        cleaned = cleaned
            .replacingOccurrences(of: "[N/A]", with: "")
            .replacingOccurrences(of: "(N/A)", with: "")
            .replacingOccurrences(of: "N/A", with: "")
            .trimmingCharacters(in: CharacterSet(charactersIn: " -_/[]()").union(.whitespacesAndNewlines))
        guard !cleaned.isEmpty, cleaned.lowercased() != "null" else {
            return nil
        }
        return cleaned.contains("@") ? nil : cleaned
    }

    private func cleanConnectorState(_ value: String?) -> String? {
        guard let state = value?.trimmingCharacters(in: .whitespacesAndNewlines), !state.isEmpty else {
            return nil
        }
        switch state.uppercased() {
        case "SYNC_SUCCESS", "SUCCESS", "CONNECTED":
            return "Synced"
        case "SYNC_FAILED", "FAILED":
            return "Sync failed"
        case "SYNCING", "IN_PROGRESS":
            return "Syncing"
        default:
            return state
                .replacingOccurrences(of: "_", with: " ")
                .lowercased()
                .capitalized
        }
    }

    private var hdrAvailable: Bool {
        let tierAllowsHDR = StreamSettingsResolver.isHDRAvailable(
            subscription: store.subscription,
            fallbackMembershipTier: store.user?.membershipTier
        )
        #if canImport(UIKit)
        let displayAllowsHDR = UIScreen.main.potentialEDRHeadroom > 1
        #else
        let displayAllowsHDR = false
        #endif
        let codecAllowsHDR = NativeStreamCodecProbe.report().capability(for: .h265)?.launchSafe == true
        return tierAllowsHDR && displayAllowsHDR && codecAllowsHDR
    }

    private var currentMembershipTier: String? {
        store.subscription?.membershipTier ?? store.user?.membershipTier
    }

    private var fpsValues: [Int] {
        StreamSettingsResolver.plan(for: currentMembershipTier) >= .ultimate
            ? [30, 60, 90, 120]
            : [30, 60]
    }

    private var streamPresetBinding: Binding<StreamPreset> {
        Binding(
            get: { store.settings.streamPreset },
            set: { preset in
                store.settings = StreamSettingsResolver.settings(
                    store.settings,
                    applying: preset,
                    membershipTier: currentMembershipTier
                )
                enforceAvailableResolution()
                enforceAvailableFPS()
            }
        )
    }

    private func customStreamBinding<Value>(
        _ keyPath: WritableKeyPath<AppSettings, Value>
    ) -> Binding<Value> {
        Binding(
            get: { store.settings[keyPath: keyPath] },
            set: { value in
                var settings = store.settings
                settings[keyPath: keyPath] = value
                settings.streamPreset = .custom
                store.settings = settings
            }
        )
    }

    #if os(iOS)
    private var catalogWallpaperDescription: String {
        store.settings.catalogWallpaperFilename == nil ? "OpenNOW Gradient" : "Custom Image"
    }

    @MainActor
    private func importCatalogWallpaper(_ item: PhotosPickerItem) async {
        catalogWallpaperImportInProgress = true
        catalogWallpaperImportError = nil
        defer {
            catalogWallpaperImportInProgress = false
            selectedCatalogWallpaperItem = nil
        }

        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                throw CatalogWallpaperStorageError.invalidImage
            }
            let filename = try await Task.detached(priority: .userInitiated) {
                try CatalogWallpaperStorage.storeSelectedImageData(data)
            }.value
            store.settings.catalogWallpaperFilename = filename
            store.settings.catalogWallpaperEnabled = true
            store.persistSettings()
            CatalogWallpaperStorage.pruneManagedWallpapers(keeping: filename)
        } catch is CancellationError {
            return
        } catch {
            catalogWallpaperImportError = error.localizedDescription
        }
    }

    private func resetCatalogWallpaperImage() {
        store.settings.catalogWallpaperFilename = nil
        store.settings.catalogWallpaperEnabled = true
        store.persistSettings()
        CatalogWallpaperStorage.pruneManagedWallpapers(keeping: nil)
        catalogWallpaperImportError = nil
    }
    #endif

    private var sessionLimitDescription: String {
        let tier = (currentMembershipTier ?? "").uppercased()
        if tier.contains("ULTIMATE") || tier.contains("RTX3080") {
            return "8 hours"
        }
        if tier.contains("PERFORMANCE") || tier.contains("PRIORITY") || tier.contains("PREMIUM") || tier.contains("FOUNDERS") {
            return "6 hours"
        }
        return "1 hour"
    }

    private func hoursLabel(_ value: Double) -> String {
        if value == floor(value) {
            return "\(Int(value)) hr"
        }
        return String(format: "%.1f hr", value)
    }

    private func elapsedLabel(_ seconds: Int) -> String {
        let hours = seconds / 3_600
        let minutes = (seconds % 3_600) / 60
        let remainingSeconds = seconds % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, remainingSeconds)
        }
        return String(format: "%02d:%02d", minutes, remainingSeconds)
    }

    private var headerSummary: String {
        let profile = StreamSettingsResolver.profile(for: store.settings, membershipTier: currentMembershipTier)
        return "\(profile.width)x\(profile.height) @ \(profile.fps) fps"
    }

    private var selectedColorQualityLabel: String {
        StreamColorQuality(rawValue: store.settings.preferredColorQuality)?.label ?? store.settings.preferredColorQuality
    }

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
    }

    private var buildNumber: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
    }

    private func bitrateLabel(for value: Int) -> String {
        value == 0 ? "Auto" : "\(value) Mbps"
    }

    private func resolutionAvailable(_ choice: StreamSettingsResolver.StreamResolutionChoice) -> Bool {
        StreamSettingsResolver.isResolutionAvailable(choice, membershipTier: currentMembershipTier)
    }

    private func resolutionLabel(
        for choice: StreamSettingsResolver.StreamResolutionChoice,
        available: Bool
    ) -> String {
        guard !available, let plan = choice.requiredPlan.label else {
            return choice.label
        }
        return "\(choice.label) - \(plan)"
    }

    private func enforceAvailableResolution() {
        guard store.settings.preferredResolution != "Auto" else { return }
        if let selected = StreamSettingsResolver.resolutionChoice(
                value: store.settings.preferredResolution,
                aspectRatio: store.settings.preferredAspectRatio
              ) {
            if !resolutionAvailable(selected) {
                store.settings.preferredResolution = "Auto"
            }
            return
        }
        store.settings.preferredResolution = "Auto"
    }

    private func enforceAvailableFPS() {
        if !fpsValues.contains(store.settings.preferredFPS) {
            store.settings.preferredFPS = fpsValues.last ?? 60
            store.settings.streamPreset = .custom
        }
    }

    private func enforceAvailableHDR() {
        if store.settings.hdrEnabled && !hdrAvailable {
            store.settings.hdrEnabled = false
        }
    }

    private func enforceAvailableCodec() {
        if !codecValues.contains(store.settings.preferredCodec) {
            store.settings.preferredCodec = "H264"
            store.settings.streamPreset = .custom
        }
    }

    private var codecSelectionBinding: Binding<String> {
        Binding(
            get: {
                codecValues.contains(store.settings.preferredCodec)
                    ? store.settings.preferredCodec
                    : "H264"
            },
            set: { value in
                guard codecValues.contains(value) else { return }
                var settings = store.settings
                settings.preferredCodec = value
                settings.streamPreset = .custom
                store.settings = settings
            }
        )
    }
}

private struct OpenNOWPrivacyPolicyView: View {
    var body: some View {
        List {
            Section("Overview") {
                Text("OpenNOW is a client for a GeForce NOW account you provide. OpenNOW does not sell personal information, serve its own advertising, or run usage analytics.")
            }

            Section("Information on This Device") {
                Text("Account authorization tokens are stored in Apple Keychain. App preferences, a selected catalog wallpaper, a random device identifier, catalog cache, favorites, launcher choices, and resumable-session state are stored locally so the app can work reliably across launches.")
            }

            Section("Services You Contact") {
                Text("When you sign in, browse games, link stores, or start a stream, the app communicates directly with NVIDIA and the selected GeForce NOW alliance provider. Those services receive the account, device, network, and session information needed to provide their service under their own privacy terms.")
                Text("Free-tier server selection can contact PrintedWaste queue endpoints. OpenNOW does not send OAuth passwords to PrintedWaste.")
                Text("If you explicitly choose Upload Redacted Logs, OpenNOW sends a strictly sanitized diagnostic artifact to paste.rtech.support. The unlisted paste expires after seven days. The app does not upload diagnostics automatically.")
            }

            Section("Apple Features") {
                Text("If enabled, queue status can be shown using local notifications and Live Activities. These features are processed by the app and Apple system services on your device.")
            }

            Section("Your Choices") {
                Text("You can sign out one or all accounts, clear cached images and catalog data, reset settings, or reset the entire app from Settings. Resetting removes locally stored OpenNOW data and Keychain credentials.")
            }

            Section("Contact") {
                Link("Open an OpenNOW privacy issue", destination: URL(string: "https://github.com/OpenCloudGaming/OpenNOW/issues")!)
            }
        }
        .navigationTitle("Privacy Policy")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct SettingsCategoryLabel: View {
    let category: SettingsCategory

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(category.title)
                    .font(.body.weight(.medium))
                Text(category.summary)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        } icon: {
            Image(systemName: category.symbolName)
                .frame(width: 28)
        }
        .padding(.vertical, 4)
    }
}

private struct SettingsAccountAvatar: View {
    let initial: String
    let size: CGFloat
    let selected: Bool

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [
                            Color(red: 0.08, green: 0.14, blue: 0.10),
                            Color(red: 0.035, green: 0.07, blue: 0.055),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Circle()
                .stroke(
                    selected ? brandAccent.opacity(0.90) : Color.white.opacity(0.18),
                    lineWidth: selected ? 2 : 1
                )
            Text(initial)
                .font(.system(size: size * 0.38, weight: .bold, design: .rounded))
                .foregroundStyle(Color.white)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

#if os(iOS)
private struct DiagnosticsTextDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.plainText] }

    var text: String

    init(text: String) {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let text = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.text = text
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
#endif

private extension String {
    var nonEmpty: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
