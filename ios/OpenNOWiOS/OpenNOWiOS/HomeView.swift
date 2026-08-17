import SwiftUI
import UIKit
import ImageIO
import GameController
import Combine
import CryptoKit

func catalogStableGameKey(_ game: CloudGame) -> String {
    if let uuid = game.uuid?.trimmingCharacters(in: .whitespacesAndNewlines), !uuid.isEmpty {
        return uuid.lowercased()
    }
    return game.id.lowercased()
}

@MainActor
final class CatalogControllerShortcutCoordinator: ObservableObject {
    @Published private(set) var isEnabled = false
    @Published private(set) var controllerConnected = false

    private struct FocusedActions {
        let owner: UUID
        let favorite: () -> Void
        let play: () -> Void
    }

    private var focusedActions: FocusedActions?
    private var attachedControllers: [GCController] = []
    private var notificationObservers: [NSObjectProtocol] = []

    init() {
        let center = NotificationCenter.default
        notificationObservers = [
            center.addObserver(
                forName: NSNotification.Name.GCControllerDidConnect,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                Task { @MainActor in self?.refreshControllers() }
            },
            center.addObserver(
                forName: NSNotification.Name.GCControllerDidDisconnect,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                Task { @MainActor in self?.refreshControllers() }
            }
        ]
    }

    deinit {
        notificationObservers.forEach(NotificationCenter.default.removeObserver)
    }

    func setEnabled(_ enabled: Bool) {
        guard isEnabled != enabled else { return }
        isEnabled = enabled
        if enabled {
            refreshControllers()
        } else {
            focusedActions = nil
            detachControllerHandlers()
            controllerConnected = false
        }
    }

    func updateFocusedActions(
        owner: UUID,
        isFocused: Bool,
        favorite: @escaping () -> Void,
        play: @escaping () -> Void
    ) {
        if isFocused {
            focusedActions = FocusedActions(owner: owner, favorite: favorite, play: play)
        } else if focusedActions?.owner == owner {
            focusedActions = nil
        }
    }

    func clearFocusedActions(owner: UUID) {
        guard focusedActions?.owner == owner else { return }
        focusedActions = nil
    }

    private func refreshControllers() {
        guard isEnabled else { return }
        detachControllerHandlers()
        attachedControllers = GCController.controllers().filter { $0.extendedGamepad != nil }
        controllerConnected = !attachedControllers.isEmpty

        for controller in attachedControllers {
            controller.extendedGamepad?.buttonX.pressedChangedHandler = { [weak self] _, _, pressed in
                guard !pressed else { return }
                Task { @MainActor in
                    guard let self, self.isEnabled else { return }
                    self.focusedActions?.favorite()
                }
            }
            controller.extendedGamepad?.buttonY.pressedChangedHandler = { [weak self] _, _, pressed in
                guard !pressed else { return }
                Task { @MainActor in
                    guard let self, self.isEnabled else { return }
                    self.focusedActions?.play()
                }
            }
        }
    }

    private func detachControllerHandlers() {
        for controller in attachedControllers {
            controller.extendedGamepad?.buttonX.pressedChangedHandler = nil
            controller.extendedGamepad?.buttonY.pressedChangedHandler = nil
        }
        attachedControllers.removeAll()
    }
}

final class OpenNOWImageCache {
    static let shared = OpenNOWImageCache()

    private let cache = NSCache<NSString, UIImage>()

    private init() {
        cache.countLimit = 240
        cache.totalCostLimit = 96 * 1024 * 1024
    }

    static func configureURLCache() {
        URLCache.shared = URLCache(
            memoryCapacity: 64 * 1024 * 1024,
            diskCapacity: 256 * 1024 * 1024,
            diskPath: "OpenNOWURLCache"
        )
        Task(priority: .utility) {
            await OpenNOWImageDiskCache.shared.prepare()
        }
    }

    func image(for url: URL, targetPixelSize: Int) -> UIImage? {
        cache.object(forKey: cacheKey(url: url, targetPixelSize: targetPixelSize))
    }

    func insert(_ image: UIImage, for url: URL, targetPixelSize: Int, cost: Int) {
        cache.setObject(image, forKey: cacheKey(url: url, targetPixelSize: targetPixelSize), cost: cost)
    }

    func removeAll() {
        cache.removeAllObjects()
    }

    static func removeAllPersistentImages() {
        Task(priority: .utility) {
            await OpenNOWImageDiskCache.shared.removeAll()
        }
    }

    private func cacheKey(url: URL, targetPixelSize: Int) -> NSString {
        "\(url.absoluteString)#\(normalizedImageTargetPixelSize(targetPixelSize))" as NSString
    }
}

private actor OpenNOWImageDiskCache {
    static let shared = OpenNOWImageDiskCache()

    private let maximumAge: TimeInterval = 30 * 24 * 60 * 60
    private let maximumBytes = 512 * 1024 * 1024
    private let directoryURL: URL?
    private var lastPruneAt = Date.distantPast

    private init() {
        directoryURL = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("OpenNOWArtwork", isDirectory: true)
    }

    func prepare() {
        ensureDirectoryExists()
        pruneIfNeeded(force: true)
    }

    func data(for url: URL) -> Data? {
        guard let fileURL = fileURL(for: url),
              let values = try? fileURL.resourceValues(forKeys: [.contentModificationDateKey]),
              Date().timeIntervalSince(values.contentModificationDate ?? .distantPast) <= maximumAge,
              let data = try? Data(contentsOf: fileURL, options: .mappedIfSafe),
              !data.isEmpty else {
            return nil
        }
        return data
    }

    func store(_ data: Data, for url: URL) {
        guard !data.isEmpty, let fileURL = fileURL(for: url) else { return }
        ensureDirectoryExists()
        do {
            try data.write(to: fileURL, options: .atomic)
            pruneIfNeeded(force: false)
        } catch {
            // Artwork can always be fetched again; cache writes must not block rendering.
        }
    }

    func remove(for url: URL) {
        guard let fileURL = fileURL(for: url) else { return }
        try? FileManager.default.removeItem(at: fileURL)
    }

    func removeAll() {
        guard let directoryURL else { return }
        try? FileManager.default.removeItem(at: directoryURL)
        ensureDirectoryExists()
        lastPruneAt = .distantPast
    }

    private func ensureDirectoryExists() {
        guard let directoryURL else { return }
        try? FileManager.default.createDirectory(
            at: directoryURL,
            withIntermediateDirectories: true
        )
    }

    private func fileURL(for url: URL) -> URL? {
        guard let directoryURL else { return nil }
        let digest = SHA256.hash(data: Data(url.absoluteString.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
        return directoryURL.appendingPathComponent(digest).appendingPathExtension("image")
    }

    private func pruneIfNeeded(force: Bool) {
        let now = Date()
        guard force || now.timeIntervalSince(lastPruneAt) >= 60 * 60,
              let directoryURL,
              let files = try? FileManager.default.contentsOfDirectory(
                at: directoryURL,
                includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey],
                options: [.skipsHiddenFiles]
              ) else {
            return
        }
        lastPruneAt = now

        var entries = files.compactMap { fileURL -> (url: URL, date: Date, bytes: Int)? in
            guard let values = try? fileURL.resourceValues(
                forKeys: [.contentModificationDateKey, .fileSizeKey]
            ) else {
                return nil
            }
            return (fileURL, values.contentModificationDate ?? .distantPast, values.fileSize ?? 0)
        }

        for entry in entries where now.timeIntervalSince(entry.date) > maximumAge {
            try? FileManager.default.removeItem(at: entry.url)
        }
        entries.removeAll { now.timeIntervalSince($0.date) > maximumAge }

        var totalBytes = entries.reduce(0) { $0 + $1.bytes }
        guard totalBytes > maximumBytes else { return }
        for entry in entries.sorted(by: { $0.date < $1.date }) where totalBytes > maximumBytes {
            try? FileManager.default.removeItem(at: entry.url)
            totalBytes -= entry.bytes
        }
    }
}

private actor OpenNOWImageLoadGate {
    static let shared = OpenNOWImageLoadGate(limit: 6)

    private let limit: Int
    private var available: Int
    private var waiters: [(
        id: UUID,
        priority: TaskPriority,
        continuation: CheckedContinuation<Void, Error>
    )] = []

    init(limit: Int) {
        self.limit = limit
        self.available = limit
    }

    func acquire() async throws {
        if available > 0 {
            available -= 1
            return
        }

        let id = UUID()
        let priority = Task.currentPriority
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                waiters.append((id, priority, continuation))
            }
        } onCancel: {
            Task {
                await self.cancelWaiter(id)
            }
        }
    }

    func release() {
        guard !waiters.isEmpty else {
            available = min(available + 1, limit)
            return
        }

        let nextIndex = waiters.indices.max {
            waiters[$0].priority.rawValue < waiters[$1].priority.rawValue
        } ?? waiters.startIndex
        let next = waiters.remove(at: nextIndex)
        next.continuation.resume()
    }

    private func cancelWaiter(_ id: UUID) {
        guard let index = waiters.firstIndex(where: { $0.id == id }) else { return }
        let waiter = waiters.remove(at: index)
        waiter.continuation.resume(throwing: CancellationError())
    }
}

private struct OpenNOWImageLoadRequest: Hashable {
    let url: URL
    let targetPixelSize: Int
}

private struct OpenNOWLoadedImage {
    let image: UIImage
    let cost: Int
    let data: Data
}

private enum OpenNOWImageDecoder {
    static func downsample(data: Data, targetPixelSize: Int) -> UIImage? {
        let options = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, options) else {
            return UIImage(data: data)
        }
        return downsample(source: source, targetPixelSize: targetPixelSize)
            ?? UIImage(data: data)
    }

    static func downsample(fileURL: URL, targetPixelSize: Int) -> UIImage? {
        let options = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithURL(fileURL as CFURL, options) else {
            return UIImage(contentsOfFile: fileURL.path)
        }
        return downsample(source: source, targetPixelSize: targetPixelSize)
            ?? UIImage(contentsOfFile: fileURL.path)
    }

    static func downsampledImage(data: Data, targetPixelSize: Int) async throws -> UIImage {
        try await Task.detached(priority: .utility) {
            try Task.checkCancellation()
            guard let decoded = downsample(data: data, targetPixelSize: targetPixelSize) else {
                throw URLError(.cannotDecodeContentData)
            }
            return decoded
        }.value
    }

    private static func downsample(source: CGImageSource, targetPixelSize: Int) -> UIImage? {
        let maxPixelSize = max(160, targetPixelSize)
        let downsampleOptions = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize
        ] as CFDictionary
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, downsampleOptions) else {
            return nil
        }
        return UIImage(cgImage: cgImage, scale: UIScreen.main.scale, orientation: .up)
    }
}

private enum OpenNOWRemoteImageFetcher {
    static func load(url: URL, targetPixelSize: Int) async throws -> OpenNOWLoadedImage {
        try await OpenNOWImageLoadGate.shared.acquire()
        defer {
            Task {
                await OpenNOWImageLoadGate.shared.release()
            }
        }

        try Task.checkCancellation()

        var request = URLRequest(url: url)
        request.cachePolicy = .returnCacheDataElseLoad
        request.timeoutInterval = 15

        let (data, response) = try await URLSession.shared.data(for: request)
        try Task.checkCancellation()

        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw URLError(.badServerResponse)
        }

        let image = try await OpenNOWImageDecoder.downsampledImage(
            data: data,
            targetPixelSize: targetPixelSize
        )

        let cost = image.cgImage.map { $0.bytesPerRow * $0.height } ?? data.count
        return OpenNOWLoadedImage(image: image, cost: cost, data: data)
    }
}

private actor OpenNOWRemoteImagePipeline {
    static let shared = OpenNOWRemoteImagePipeline()

    private var inFlight: [OpenNOWImageLoadRequest: Task<UIImage, Error>] = [:]

    func load(_ request: OpenNOWImageLoadRequest) async throws -> UIImage {
        if let cached = OpenNOWImageCache.shared.image(
            for: request.url,
            targetPixelSize: request.targetPixelSize
        ) {
            return cached
        }
        if let existing = inFlight[request] {
            return try await existing.value
        }

        let task = Task(priority: Task.currentPriority) {
            if let diskData = await OpenNOWImageDiskCache.shared.data(for: request.url) {
                do {
                    let diskImage = try await OpenNOWImageDecoder.downsampledImage(
                        data: diskData,
                        targetPixelSize: request.targetPixelSize
                    )
                    let cost = diskImage.cgImage.map { $0.bytesPerRow * $0.height } ?? diskData.count
                    OpenNOWImageCache.shared.insert(
                        diskImage,
                        for: request.url,
                        targetPixelSize: request.targetPixelSize,
                        cost: cost
                    )
                    return diskImage
                } catch {
                    await OpenNOWImageDiskCache.shared.remove(for: request.url)
                }
            }

            let loaded = try await OpenNOWRemoteImageFetcher.load(
                url: request.url,
                targetPixelSize: request.targetPixelSize
            )
            OpenNOWImageCache.shared.insert(
                loaded.image,
                for: request.url,
                targetPixelSize: request.targetPixelSize,
                cost: loaded.cost
            )
            await OpenNOWImageDiskCache.shared.store(loaded.data, for: request.url)
            return loaded.image
        }
        inFlight[request] = task
        do {
            let image = try await task.value
            inFlight[request] = nil
            return image
        } catch {
            inFlight[request] = nil
            throw error
        }
    }
}

@MainActor
private final class CachedRemoteImageLoader: ObservableObject {
    @Published private(set) var image: UIImage?
    @Published private(set) var didFail = false

    private var loadedRequest: OpenNOWImageLoadRequest?

    func load(_ request: OpenNOWImageLoadRequest) async {
        if loadedRequest == request && image != nil { return }

        let previousRequest = loadedRequest
        loadedRequest = request
        didFail = false

        if let cached = OpenNOWImageCache.shared.image(for: request.url, targetPixelSize: request.targetPixelSize) {
            image = cached
            return
        }

        if previousRequest?.url != request.url {
            image = nil
        }

        do {
            let loaded = try await OpenNOWRemoteImagePipeline.shared.load(request)
            guard !Task.isCancelled, loadedRequest == request else { return }
            image = loaded
        } catch is CancellationError {
            if loadedRequest == request {
                didFail = false
            }
        } catch {
            if loadedRequest == request, image == nil {
                didFail = true
            }
        }
    }
}

struct CachedRemoteImage<Content: View, Placeholder: View, Failure: View>: View {
    let url: URL
    let targetPixelSize: Int
    var priority: TaskPriority = .utility
    let content: (Image) -> Content
    let placeholder: () -> Placeholder
    let failure: () -> Failure

    @StateObject private var loader = CachedRemoteImageLoader()

    private var request: OpenNOWImageLoadRequest {
        OpenNOWImageLoadRequest(url: url, targetPixelSize: targetPixelSize)
    }

    var body: some View {
        Group {
            if let image = loader.image {
                content(Image(uiImage: image))
            } else if loader.didFail {
                failure()
            } else {
                placeholder()
            }
        }
        .task(id: request, priority: priority) {
            await loader.load(request)
        }
    }
}

struct CatalogWallpaperBackdrop: View {
    let isEnabled: Bool
    let managedFilename: String?

    @State private var image: UIImage?

    var body: some View {
        GeometryReader { proxy in
            let targetPixelSize = imageTargetPixelSize(for: proxy.size)
            let loadID = "\(isEnabled)-\(managedFilename ?? "gradient")-\((targetPixelSize + 159) / 160)"
            Group {
                if isEnabled {
                    ZStack {
                        if let image {
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFill()
                                .transition(.opacity)
                        } else {
                            brandGradient
                        }

                        LinearGradient(
                            colors: [
                                Color(uiColor: .systemBackground).opacity(0.34),
                                Color(uiColor: .systemBackground).opacity(0.72)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    }
                    .clipped()
                } else {
                    Color(uiColor: .systemBackground)
                }
            }
            .task(id: loadID) {
                await loadManagedWallpaper(targetPixelSize: targetPixelSize)
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }

    @MainActor
    private func loadManagedWallpaper(targetPixelSize: Int) async {
        guard isEnabled,
              let url = CatalogWallpaperStorage.wallpaperURL(for: managedFilename) else {
            image = nil
            return
        }
        let loaded = await Task.detached(priority: .utility) {
            OpenNOWImageDecoder.downsample(
                fileURL: url,
                targetPixelSize: targetPixelSize
            )
        }.value
        guard !Task.isCancelled else { return }
        withAnimation(.easeInOut(duration: 0.2)) {
            image = loaded
        }
    }
}

struct HomeView: View {
    @EnvironmentObject private var store: OpenNOWStore
    @State private var pendingLaunchRequest: GameLaunchRequest?
    @State private var selectedGameForDetails: CloudGame?
    @State private var selectedGameForLauncher: CloudGame?
    @State private var isSearchPresented = false

    private var continueCardWidth: CGFloat {
        let baseWidth: CGFloat = store.settings.compactGameCards ? 140 : 160
        let scale = CGFloat(min(max(store.settings.posterSizeScale, 0.75), 1.4))
        return baseWidth * scale
    }

    var body: some View {
        NavigationStack {
            GameCatalogGridView(
                games: homeGridGames,
                isLoading: store.isLoadingGames && store.allGames.isEmpty,
                emptyTitle: isHomeSearchActive ? "No Matches" : "No Games",
                emptySystemImage: isHomeSearchActive ? "magnifyingglass" : "square.grid.2x2",
                subtitle: { gameCatalogSubtitle(for: $0) },
                badgeSystemImage: { _ in nil },
                onOpenDetails: { selectedGameForDetails = $0 },
                onPlay: launchFromCard
            ) {
                homeHeader
            } emptyActions: {
                if isHomeSearchActive {
                    Button("Clear Search") {
                        store.searchText = ""
                        isSearchPresented = false
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(brandAccent)
                }
            }
            .searchableCompat(
                text: $store.searchText,
                isPresented: $isSearchPresented,
                placement: .navigationBarDrawer(displayMode: .automatic),
                prompt: "Search games"
            )
            .refreshable { await store.refreshCatalog() }
            .navigationTitle("OpenNOW")
            .background {
                CatalogWallpaperBackdrop(
                    isEnabled: store.settings.catalogWallpaperEnabled,
                    managedFilename: store.settings.catalogWallpaperFilename
                )
            }
        }
        .presentGameDetailsSheet(selectedGame: $selectedGameForDetails, store: store) { game, option in
            pendingLaunchRequest = GameLaunchRequest(game: game, launchOption: option)
        }
        .launcherSelectionModalSheet(selectedGame: $selectedGameForLauncher, store: store) { game, option in
            pendingLaunchRequest = GameLaunchRequest(game: game, launchOption: option)
        }
        .printedWasteLaunchSheet(pendingLaunchRequest: $pendingLaunchRequest)
    }

    private var homeHeader: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let error = store.lastError {
                ErrorBannerView(message: error)
            }

            if !isHomeSearchActive && jumpBackInHasContent {
                continueSection
            }

            if !isHomeSearchActive && !comingNextGames.isEmpty {
                comingNextSection
            }

            if !isHomeSearchActive {
                ForEach(storeSectionRails) { section in
                    CatalogPosterRail(
                        title: section.title,
                        games: section.games,
                        onOpenDetails: { selectedGameForDetails = $0 },
                        onPlay: launchFromCard
                    )
                }
            }

            CatalogControlsHeader(
                title: homeHeaderTitle,
                subtitle: isHomeSearchActive ? "Search results" : "Store catalog",
                chips: homeActiveFilterChips,
                onClear: isHomeSearchActive ? {
                    store.searchText = ""
                    isSearchPresented = false
                } : nil
            ) {
                EmptyView()
            }
        }
    }

    private var continueSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Continue")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(continueGameItems) { item in
                        GameBannerButton(
                            game: item.game,
                            subtitle: item.subtitle,
                            badgeSystemImage: item.badgeSystemImage
                        ) {
                            item.onSelect()
                        }
                        .frame(width: continueCardWidth)
                    }

                    ForEach(unknownResumableSessions) { candidate in
                        Button {
                            Haptics.light()
                            store.scheduleResume(candidate: candidate)
                        } label: {
                            Label("Cloud Session", systemImage: "arrow.clockwise.circle")
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.bordered)
                        .frame(width: continueCardWidth)
                    }
                }
                .padding(.horizontal, 2)
                .padding(.vertical, 8)
            }
            .accessibilityLabel("Continue games and sessions")
        }
    }

    private var comingNextSection: some View {
        CatalogPosterRail(
            title: "Coming Next",
            symbol: "sparkles",
            caption: "Recently added or updated",
            games: comingNextGames,
            onOpenDetails: { selectedGameForDetails = $0 },
            onPlay: launchFromCard
        )
    }

    /// The catalog groups games into named sections; Android surfaces those as rails while iOS
    /// was flattening all of them into one grid. A section is only worth a rail when it is long
    /// enough to scroll — anything shorter reads as a rendering bug, so it stays in the grid.
    private var storeSectionRails: [CatalogSectionGroup] {
        let excluded = comingNextExcludedGameKeys.union(comingNextGames.map(catalogStableGameKey))
        var order: [String] = []
        var grouped: [String: [CloudGame]] = [:]
        var seen = Set<String>()

        for game in store.allGames {
            let key = catalogStableGameKey(game)
            guard !excluded.contains(key), seen.insert(key).inserted else { continue }
            guard let title = game.catalogSectionTitle?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !title.isEmpty,
                  !title.localizedCaseInsensitiveContains("jump back in") else { continue }
            if grouped[title] == nil { order.append(title) }
            grouped[title, default: []].append(game)
        }

        return order.compactMap { title -> CatalogSectionGroup? in
            guard let games = grouped[title], games.count >= 4 else { return nil }
            return CatalogSectionGroup(title: title, games: Array(games.prefix(20)))
        }
        .prefix(6)
        .map { $0 }
    }

    private var comingNextGames: [CloudGame] {
        let excludedGameKeys = comingNextExcludedGameKeys
        var seenGameKeys = Set<String>()
        return Array(
            store.allGames.lazy
                .filter { !excludedGameKeys.contains(catalogStableGameKey($0)) }
                .filter { game in
                    guard let sectionTitle = game.catalogSectionTitle?
                        .trimmingCharacters(in: .whitespacesAndNewlines),
                        !sectionTitle.isEmpty else {
                        return false
                    }

                    let normalizedTitle = sectionTitle.lowercased()
                    guard !normalizedTitle.contains("jump back in") else { return false }
                    return normalizedTitle.contains("new") ||
                        normalizedTitle.contains("recent") ||
                        normalizedTitle.contains("updated") ||
                        normalizedTitle.contains("just added")
                }
                .filter { seenGameKeys.insert(catalogStableGameKey($0)).inserted }
                .prefix(14)
        )
    }

    private var comingNextExcludedGameKeys: Set<String> {
        var keys = Set(continueGameItems.map { catalogStableGameKey($0.game) })
        if let activeGame = store.activeSession?.game {
            keys.insert(catalogStableGameKey(activeGame))
        }
        for candidate in store.resumableSessions {
            if let game = store.gameForRemoteSession(candidate) {
                keys.insert(catalogStableGameKey(game))
            }
        }
        return keys
    }

    private var homeGridGames: [CloudGame] {
        let games = isHomeSearchActive ? homeSearchResults : store.allGames
        guard !store.settings.favoriteGameIds.isEmpty else { return games }
        let favoriteIds = Set(store.settings.favoriteGameIds)
        return games.sorted {
            let leftFavorite = favoriteIds.contains($0.id)
            let rightFavorite = favoriteIds.contains($1.id)
            if leftFavorite != rightFavorite { return leftFavorite }
            return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
        }
    }

    private var homeHeaderTitle: String {
        let count = homeGridGames.count
        if isHomeSearchActive {
            return count == 1 ? "1 Match" : "\(count) Matches"
        }
        return count == 1 ? "1 Game" : "\(count) Games"
    }

    private var homeActiveFilterChips: [CatalogFilterChip] {
        let query = store.searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return [] }
        return [
            CatalogFilterChip(label: "Search: \(query)") {
                store.searchText = ""
                isSearchPresented = false
            }
        ]
    }

    private var jumpBackInHasContent: Bool {
        !continueGameItems.isEmpty || !unknownResumableSessions.isEmpty
    }

    private var isHomeSearchActive: Bool {
        !store.searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var homeSearchResults: [CloudGame] {
        store.filteredCatalogGames
    }

    private func launchFromCard(_ game: CloudGame) {
        let options = store.launchOptions(for: game)
        if options.count > 1 {
            selectedGameForLauncher = game
            return
        }
        pendingLaunchRequest = GameLaunchRequest(game: game, launchOption: store.defaultLaunchOption(for: game) ?? options.first)
    }

    private var resumableSessionsExcludingActive: [RemoteSessionCandidate] {
        let activeId = store.activeSession?.id
        return store.resumableSessions.filter { $0.id != activeId }
    }

    private var continueGameItems: [GameBannerActionItem] {
        var items: [GameBannerActionItem] = []
        var seenGameKeys = Set<String>()
        if let active = store.activeSession {
            seenGameKeys.insert(catalogStableGameKey(active.game))
            items.append(
                GameBannerActionItem(
                    id: "active-\(active.id)",
                    game: active.game,
                    subtitle: jumpBackInSubtitleActive(active),
                    badgeSystemImage: active.status == 3 ? "play.circle.fill" : "hourglass"
                ) {
                    store.jumpBackToSession()
                }
            )
        }

        for candidate in resumableSessionsExcludingActive.prefix(6) {
            guard let game = store.gameForRemoteSession(candidate) else { continue }
            guard seenGameKeys.insert(catalogStableGameKey(game)).inserted else { continue }
            items.append(
                GameBannerActionItem(
                    id: "remote-\(candidate.id)",
                    game: game,
                    subtitle: "Resume session",
                    badgeSystemImage: "arrow.clockwise.circle"
                ) {
                    store.scheduleResume(candidate: candidate)
                }
            )
        }

        let favoriteIDs = Set(store.settings.favoriteGameIds)
        let catalogGamesByID = Dictionary(
            (store.allGames + store.libraryGames).map { ($0.id, $0) },
            uniquingKeysWith: { current, _ in current }
        )
        let favoriteGames = favoriteIDs.compactMap { catalogGamesByID[$0] }
            .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        let ownedGames = store.libraryGames.sorted {
            $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
        }

        for game in favoriteGames + ownedGames where items.count < 6 {
            guard seenGameKeys.insert(catalogStableGameKey(game)).inserted else { continue }
            let isFavorite = favoriteIDs.contains(game.id)
            items.append(
                GameBannerActionItem(
                    id: "catalog-\(game.id)",
                    game: game,
                    subtitle: isFavorite ? "Favorite" : "In your library",
                    badgeSystemImage: isFavorite ? "heart.fill" : "books.vertical.fill"
                ) {
                    launchFromCard(game)
                }
            )
        }
        return items
    }

    private var unknownResumableSessions: [RemoteSessionCandidate] {
        resumableSessionsExcludingActive
            .prefix(6)
            .filter { store.gameForRemoteSession($0) == nil }
    }

    private func jumpBackInSubtitleActive(_ session: ActiveSession) -> String {
        switch session.status {
        case 3:
            guard store.supportsEmbeddedStreamer else { return "Ready on another platform" }
            return store.streamSession == nil ? "Ready to return" : "Streaming"
        case 2:
            return "Connecting"
        default:
            if let queue = session.queuePosition {
                return queue == 1 ? "Next in queue" : "Queue #\(queue)"
            }
            return "Queued"
        }
    }
}

private let gameVerticalBannerAspectRatio: CGFloat = 2.0 / 3.0

/// One curated catalog section, rendered as a rail on Home.
struct CatalogSectionGroup: Identifiable {
    let title: String
    let games: [CloudGame]
    var id: String { title }
}

struct GameBannerRowGroup: Identifiable {
    let id: String
    let games: [CloudGame]
}

struct GameBannerActionItem: Identifiable {
    let id: String
    let game: CloudGame
    let subtitle: String?
    let badgeSystemImage: String?
    let onSelect: () -> Void
}

func gameBannerRows(for games: [CloudGame]) -> [GameBannerRowGroup] {
    guard !games.isEmpty else { return [] }
    var rows: [GameBannerRowGroup] = []
    rows.reserveCapacity((games.count + 1) / 2)

    var index = 0
    while index < games.count {
        let rowGames = Array(games[index..<min(index + 2, games.count)])
        rows.append(GameBannerRowGroup(id: rowGames.map(\.id).joined(separator: "|"), games: rowGames))
        index += 2
    }

    return rows
}

struct CatalogFilterChip: Identifiable {
    let label: String
    let onRemove: () -> Void

    var id: String { label }
}

struct CatalogControlsHeader<Controls: View>: View {
    let title: String
    let subtitle: String?
    let chips: [CatalogFilterChip]
    let onClear: (() -> Void)?
    @ViewBuilder let controls: () -> Controls

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center, spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline.weight(.semibold))
                    if let subtitle, !subtitle.isEmpty {
                        Text(subtitle)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 8)
                controls()
            }

            if !chips.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(chips) { chip in
                            CatalogFilterChipButton(chip: chip)
                        }
                        if let onClear {
                            Button("Clear") {
                                onClear()
                            }
                            .font(.caption.weight(.semibold))
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        }
                    }
                    .padding(.vertical, 1)
                }
            }
        }
    }
}

private struct CatalogFilterChipButton: View {
    let chip: CatalogFilterChip

    var body: some View {
        Button {
            chip.onRemove()
        } label: {
            HStack(spacing: 6) {
                Text(chip.label)
                    .lineLimit(1)
                Image(systemName: "xmark")
                    .font(.caption2.weight(.bold))
            }
        }
        .font(.caption.weight(.semibold))
        .buttonStyle(.bordered)
        .controlSize(.small)
        .tint(.secondary)
    }
}

struct GameCatalogGridView<Header: View, EmptyActions: View>: View {
    @EnvironmentObject private var store: OpenNOWStore
    let games: [CloudGame]
    let isLoading: Bool
    let emptyTitle: String
    let emptySystemImage: String
    /// One line under the empty-state title saying what actually happened. Optional because not
    /// every caller has a cause worth naming — but when there is one, it belongs here rather
    /// than being folded into the title.
    var emptyDescription: String? = nil
    let subtitle: (CloudGame) -> String
    let badgeSystemImage: (CloudGame) -> String?
    let onOpenDetails: (CloudGame) -> Void
    let onPlay: (CloudGame) -> Void
    @ViewBuilder let header: () -> Header
    @ViewBuilder let emptyActions: () -> EmptyActions

    private var columns: [GridItem] {
        let scale = CGFloat(min(max(store.settings.posterSizeScale, 0.75), 1.4))
        let baseMinimum: CGFloat = store.settings.compactGameCards ? 132 : 154
        let baseMaximum: CGFloat = store.settings.compactGameCards ? 196 : 230
        return [
            GridItem(
                .adaptive(minimum: baseMinimum * scale, maximum: baseMaximum * scale),
                spacing: 10,
                alignment: .top
            )
        ]
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                header()
                    .padding(.horizontal, 14)

                if isLoading && games.isEmpty {
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(0..<8, id: \.self) { _ in
                            GameCatalogGridSkeletonCard()
                        }
                    }
                    .shimmeringSkeleton()
                    .padding(.horizontal, 12)
                } else if games.isEmpty {
                    OpenNOWUnavailableView(emptyTitle, systemImage: emptySystemImage) {
                        if let emptyDescription {
                            Text(emptyDescription)
                        }
                    } actions: {
                        emptyActions()
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 18)
                    .padding(.top, 42)
                } else {
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(games) { game in
                            let favorite = store.isFavorite(game)
                            let canLaunch = OpenNOWPlatform.supportsEmbeddedStreamer
                                && !store.launchOptions(for: game).isEmpty
                            GameCatalogGridCard(
                                game: game,
                                subtitle: store.settings.showGameStoreLabels ? subtitle(game) : nil,
                                badgeSystemImage: badgeSystemImage(game),
                                compact: store.settings.compactGameCards,
                                favorite: favorite,
                                canLaunch: canLaunch,
                                onToggleFavorite: { store.toggleFavorite(game) },
                                onOpenDetails: { onOpenDetails(game) },
                                onPlay: { onPlay(game) }
                            )
                        }
                    }
                    .padding(.horizontal, 12)
                }
            }
            .padding(.vertical, 12)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

private struct GameCatalogGridCard: View {
    @EnvironmentObject private var controllerShortcuts: CatalogControllerShortcutCoordinator
    @FocusState private var isPosterFocused: Bool
    @State private var isLegacyPosterFocused = false
    @State private var controllerShortcutOwner = UUID()
    let game: CloudGame
    let subtitle: String?
    let badgeSystemImage: String?
    let compact: Bool
    let favorite: Bool
    let canLaunch: Bool
    let onToggleFavorite: () -> Void
    let onOpenDetails: () -> Void
    let onPlay: () -> Void

    private var controlSize: CGFloat {
        compact ? 36 : 42
    }

    private var isPosterVisuallyFocused: Bool {
        isPosterFocused || isLegacyPosterFocused
    }

    private func openDetails() {
        Haptics.light()
        onOpenDetails()
    }

    private func toggleFavorite() {
        Haptics.light()
        onToggleFavorite()
    }

    private func play() {
        guard canLaunch else { return }
        Haptics.medium()
        onPlay()
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Button(action: openDetails) {
                GameCatalogPosterContent(
                    game: game,
                    subtitle: subtitle,
                    badgeSystemImage: badgeSystemImage,
                    compact: compact,
                    isFocused: isPosterVisuallyFocused
                )
                .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .controllerFocusableCompat(
                fallbackActivation: openDetails,
                onLegacyFocusChange: { isLegacyPosterFocused = $0 }
            )
            .focused($isPosterFocused)
            .scaleEffect(isPosterVisuallyFocused ? 1.025 : 1)
            .animation(.easeOut(duration: 0.16), value: isPosterVisuallyFocused)
            .accessibilityLabel("Open details for \(game.title)")

            HStack(alignment: .bottom) {
                Button(action: toggleFavorite) {
                    Image(systemName: favorite ? "heart.fill" : "heart")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(favorite ? Color.red : Color.white)
                        .frame(width: controlSize, height: controlSize)
                        .background(.ultraThinMaterial, in: Circle())
                        .shadow(color: .black.opacity(0.24), radius: 6, y: 3)
                }
                .buttonStyle(.plain)
                .controllerFocusableCompat(fallbackActivation: toggleFavorite)
                .contentShape(Circle())
                .accessibilityLabel(favorite ? "Remove \(game.title) from favorites" : "Add \(game.title) to favorites")

                Spacer(minLength: 8)

                Button(action: play) {
                    Image(systemName: "play.fill")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(Color.white)
                        .frame(width: controlSize, height: controlSize)
                        .background(brandAccent.opacity(canLaunch ? 0.96 : 0.45), in: Circle())
                        .shadow(color: .black.opacity(0.24), radius: 6, y: 3)
                }
                .buttonStyle(.plain)
                .controllerFocusableCompat(fallbackActivation: play)
                .contentShape(Circle())
                .accessibilityLabel("Launch \(game.title)")
                .disabled(!canLaunch)
            }
            .padding(compact ? 6 : 8)
            .zIndex(1)
        }
        .overlay(alignment: .topTrailing) {
            if isPosterVisuallyFocused,
               controllerShortcuts.isEnabled,
               controllerShortcuts.controllerConnected {
                CatalogControllerShortcutHint(
                    favorite: favorite,
                    playEnabled: canLaunch
                )
                .padding(6)
                .transition(.opacity)
            }
        }
        .onAppear {
            updateControllerShortcutRegistration(isPosterVisuallyFocused)
        }
        .onChangeCompat(of: isPosterVisuallyFocused) { focused in
            updateControllerShortcutRegistration(focused)
        }
        .onChangeCompat(of: controllerShortcuts.isEnabled) { enabled in
            updateControllerShortcutRegistration(enabled && isPosterVisuallyFocused)
        }
        .onDisappear {
            controllerShortcuts.clearFocusedActions(owner: controllerShortcutOwner)
        }
        .zIndex(isPosterVisuallyFocused ? 2 : 0)
    }

    private func updateControllerShortcutRegistration(_ focused: Bool) {
        controllerShortcuts.updateFocusedActions(
            owner: controllerShortcutOwner,
            isFocused: focused,
            favorite: { toggleFavorite() },
            play: { play() }
        )
    }
}

private struct CatalogControllerShortcutHint: View {
    let favorite: Bool
    let playEnabled: Bool

    var body: some View {
        HStack(spacing: 5) {
            shortcut(button: "X", systemImage: favorite ? "heart.fill" : "heart")
            shortcut(button: "Y", systemImage: "play.fill")
                .opacity(playEnabled ? 1 : 0.45)
        }
        .padding(5)
        .background(.black.opacity(0.72), in: Capsule())
        .overlay(Capsule().stroke(Color.white.opacity(0.16), lineWidth: 1))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func shortcut(button: String, systemImage: String) -> some View {
        HStack(spacing: 3) {
            Text(button)
                .font(.caption2.bold())
                .foregroundStyle(.white)
                .frame(width: 17, height: 17)
                .background(Color.white.opacity(0.18), in: Circle())
            Image(systemName: systemImage)
                .font(.caption2.weight(.bold))
                .foregroundStyle(.white)
        }
    }
}

private struct GameCatalogPosterContent: View {
    let game: CloudGame
    let subtitle: String?
    let badgeSystemImage: String?
    let compact: Bool
    let isFocused: Bool

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Color.black

            GameArtworkView(game: game, iconSize: 42)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .aspectRatio(gameVerticalBannerAspectRatio, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(alignment: .topTrailing) {
            if let badgeSystemImage {
                Image(systemName: badgeSystemImage)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(badgeBackgroundColor.opacity(0.92), in: Circle())
                    .padding(8)
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(
                    isFocused ? brandAccent : Color.white.opacity(0.10),
                    lineWidth: isFocused ? 2 : 1
                )
        )
        .shadow(color: .black.opacity(0.14), radius: 8, y: 4)
        .accessibilityLabel(game.title)
        .accessibilityValue(subtitle ?? "")
    }

    private var badgeBackgroundColor: Color {
        badgeSystemImage == "heart.fill" ? .red : brandAccent
    }
}

private struct GameCatalogGridSkeletonCard: View {
    var body: some View {
        ZStack(alignment: .bottom) {
            Color.secondary.opacity(0.16)

            HStack {
                Circle()
                    .fill(Color.white.opacity(0.18))
                    .frame(width: 42, height: 42)
                Spacer()
                Circle()
                    .fill(Color.white.opacity(0.18))
                    .frame(width: 42, height: 42)
            }
            .padding(8)
        }
        .aspectRatio(gameVerticalBannerAspectRatio, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .accessibilityHidden(true)
    }
}

struct GameBannerRowView: View {
    let games: [CloudGame]
    let subtitle: (CloudGame) -> String
    var badgeSystemImage: (CloudGame) -> String? = { _ in nil }
    let onSelect: (CloudGame) -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ForEach(games) { game in
                GameBannerButton(
                    game: game,
                    subtitle: subtitle(game),
                    badgeSystemImage: badgeSystemImage(game)
                ) {
                    onSelect(game)
                }
                .frame(maxWidth: .infinity)
            }

            if games.count == 1 {
                Color.clear
                    .aspectRatio(gameVerticalBannerAspectRatio, contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .accessibilityHidden(true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct GameBannerSkeletonRowView: View {
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            GameBannerSkeletonCard()
                .frame(maxWidth: .infinity)
            GameBannerSkeletonCard()
                .frame(maxWidth: .infinity)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct GameBannerSkeletonCard: View {
    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(
                colors: [
                    Color.secondary.opacity(0.22),
                    Color.secondary.opacity(0.12),
                    Color.black.opacity(0.16)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            VStack(alignment: .leading, spacing: 7) {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(Color.white.opacity(0.24))
                    .frame(maxWidth: .infinity)
                    .frame(height: 9)
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(Color.white.opacity(0.18))
                    .frame(width: 74, height: 8)
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(Color.white.opacity(0.14))
                    .frame(width: 46, height: 7)
            }
            .padding(10)
        }
        .aspectRatio(gameVerticalBannerAspectRatio, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .accessibilityHidden(true)
    }
}

struct GameBannerButton: View {
    @FocusState private var isFocused: Bool
    @State private var isLegacyFocused = false
    let game: CloudGame
    let subtitle: String?
    let badgeSystemImage: String?
    let onSelect: () -> Void

    private func select() {
        Haptics.light()
        onSelect()
    }

    private var isVisuallyFocused: Bool {
        isFocused || isLegacyFocused
    }

    var body: some View {
        Button(action: select) {
            GameVerticalBannerCard(
                game: game,
                subtitle: subtitle,
                badgeSystemImage: badgeSystemImage,
                isFocused: isVisuallyFocused
            )
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .controllerFocusableCompat(
            fallbackActivation: select,
            onLegacyFocusChange: { isLegacyFocused = $0 }
        )
        .focused($isFocused)
        .scaleEffect(isVisuallyFocused ? 1.025 : 1)
        .animation(.easeOut(duration: 0.16), value: isVisuallyFocused)
        .zIndex(isVisuallyFocused ? 2 : 0)
    }
}

struct GameVerticalBannerCard: View {
    let game: CloudGame
    let subtitle: String?
    let badgeSystemImage: String?
    var fitArtwork = false
    var isFocused = false

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            GameArtworkView(game: game, iconSize: 42, fit: fitArtwork)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            LinearGradient(
                colors: [
                    .clear,
                    .black.opacity(0.22),
                    .black.opacity(0.88)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .allowsHitTesting(false)

            VStack(alignment: .leading, spacing: 4) {
                Text(game.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.white)
                    .lineLimit(3)
                    .minimumScaleFactor(0.78)
                    .fixedSize(horizontal: false, vertical: true)

                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(Color.white.opacity(0.82))
                        .lineLimit(1)
                }
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .aspectRatio(gameVerticalBannerAspectRatio, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(alignment: .topTrailing) {
            if let badgeSystemImage {
                Image(systemName: badgeSystemImage)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(badgeBackgroundColor.opacity(0.92), in: Circle())
                    .padding(8)
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(
                    isFocused ? brandAccent : Color.white.opacity(0.10),
                    lineWidth: isFocused ? 2 : 1
                )
        )
        .shadow(color: .black.opacity(0.14), radius: 8, y: 4)
        .accessibilityElement(children: .combine)
    }

    private var badgeBackgroundColor: Color {
        badgeSystemImage == "heart.fill" ? .red : brandAccent
    }
}

private struct GameLaunchDetailsArtworkCard: View {
    let game: CloudGame
    let subtitle: String?
    let badgeSystemImage: String?

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            GameLaunchDetailsArtwork(game: game)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            LinearGradient(
                colors: [.clear, .black.opacity(0.20), .black.opacity(0.82)],
                startPoint: .top,
                endPoint: .bottom
            )
            .allowsHitTesting(false)

            VStack(alignment: .leading, spacing: 4) {
                Text(game.title)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)

                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.white.opacity(0.82))
                        .lineLimit(1)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .aspectRatio(16.0 / 9.0, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(alignment: .topTrailing) {
            if let badgeSystemImage {
                Image(systemName: badgeSystemImage)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(badgeBackgroundColor.opacity(0.92), in: Circle())
                    .padding(8)
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.white.opacity(0.10), lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
    }

    private var badgeBackgroundColor: Color {
        badgeSystemImage == "heart.fill" ? .red : brandAccent
    }
}

private struct GameLaunchDetailsArtwork: View {
    let game: CloudGame

    var body: some View {
        GeometryReader { proxy in
            let targetPixelSize = imageTargetPixelSize(for: proxy.size)
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(gameColor(for: game.title).opacity(0.18))

                GameArtworkView(game: game, iconSize: 42, role: .catalog)
                    .frame(width: proxy.size.width, height: proxy.size.height)

                if let imageUrl = game.detailsArtworkUrl,
                   let url = URL(
                    string: optimizedNvidiaArtworkURL(
                        imageUrl,
                        targetPixelSize: targetPixelSize
                    )
                   ) {
                    CachedRemoteImage(
                        url: url,
                        targetPixelSize: targetPixelSize,
                        priority: .userInitiated
                    ) { image in
                        image
                            .resizable()
                            .scaledToFill()
                            .frame(width: proxy.size.width, height: proxy.size.height)
                    } placeholder: {
                        Color.clear
                    } failure: {
                        Color.clear
                    }
                } else {
                    Color.clear
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
            .clipped()
        }
    }

}

private struct GameScreenshotGallery: View {
    let urls: [String]

    private var screenshotURLs: [URL] {
        var seen = Set<String>()
        return urls.compactMap { raw in
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty, seen.insert(trimmed).inserted else { return nil }
            return URL(string: optimizedNvidiaArtworkURL(trimmed, targetPixelSize: 960))
        }
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 10) {
                ForEach(screenshotURLs, id: \.absoluteString) { url in
                    CachedRemoteImage(
                        url: url,
                        targetPixelSize: 960,
                        priority: .userInitiated
                    ) { image in
                        image
                            .resizable()
                            .scaledToFit()
                    } placeholder: {
                        GameScreenshotPlaceholder()
                    } failure: {
                        GameScreenshotPlaceholder(isFailure: true)
                    }
                    .frame(width: 288)
                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
                    .background(Color.black)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(Color.white.opacity(0.10), lineWidth: 1)
                    )
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
        }
        .accessibilityLabel("Game screenshots")
    }
}

private struct GameScreenshotPlaceholder: View {
    var isFailure = false

    var body: some View {
        ZStack {
            Color.secondary.opacity(isFailure ? 0.10 : 0.16)
            Image(systemName: isFailure ? "photo.badge.exclamationmark" : "photo")
                .font(.title2.weight(.semibold))
                .foregroundStyle(.secondary)
        }
    }
}

struct GameListRowView: View {
    let game: CloudGame
    var subtitle: String?
    var trailingSystemImage: String?

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            GameArtworkView(game: game, iconSize: 30)
                .frame(maxWidth: .infinity)
                .frame(height: 96)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

            LinearGradient(
                colors: [.clear, .black.opacity(0.36), .black.opacity(0.86)],
                startPoint: .top,
                endPoint: .bottom
            )
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

            VStack(alignment: .leading, spacing: 3) {
                Text(game.title)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .minimumScaleFactor(0.86)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(Color.white.opacity(0.82))
                        .lineLimit(1)
                }
            }
            .padding(12)
            .padding(.trailing, trailingSystemImage == nil ? 0 : 42)

            if let trailingSystemImage {
                VStack {
                    HStack {
                        Spacer()
                        Image(systemName: trailingSystemImage)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(trailingSystemImage == "heart.fill" ? Color.red : Color.white)
                            .frame(width: 30, height: 30)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                    Spacer()
                }
                .padding(10)
            }
        }
        .frame(minHeight: 96)
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

private struct JumpBackInCard: View {
    let title: String
    let subtitle: String
    let game: CloudGame?
    let statusTint: Color
    let onTap: () -> Void

    var body: some View {
        Button(action: {
            Haptics.light()
            onTap()
        }) {
            VStack(alignment: .leading, spacing: 0) {
                Group {
                    if let game {
                        GameArtworkView(game: game, iconSize: 48)
                    } else {
                        ZStack {
                            Color.secondary.opacity(0.18)
                            Image(systemName: "arrow.counterclockwise.circle.fill")
                                .font(.system(size: 40))
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .frame(width: 160, height: 100)
                .clipShape(
                    UnevenRoundedRectangle(
                        topLeadingRadius: 14,
                        bottomLeadingRadius: 0,
                        bottomTrailingRadius: 0,
                        topTrailingRadius: 14
                    )
                )

                VStack(alignment: .leading, spacing: 6) {
                    Text(title)
                        .font(.caption.bold())
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .frame(height: 32, alignment: .top)
                        .foregroundStyle(.primary)
                    Text(subtitle)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Color.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(statusTint.opacity(0.92), in: Capsule())
                }
                .padding(10)
            }
            .frame(width: 160)
            .glassCard()
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

/// A horizontal row of poster cards under a titled header.
///
/// Used both for "Coming Next" and for the curated sections the catalog itself defines, so a rail
/// looks and behaves the same wherever it appears — which is the point of having rails at all.
private struct CatalogPosterRail: View {
    @EnvironmentObject private var store: OpenNOWStore

    let title: String
    var symbol: String? = nil
    var caption: String? = nil
    let games: [CloudGame]
    let onOpenDetails: (CloudGame) -> Void
    let onPlay: (CloudGame) -> Void

    private var cardWidth: CGFloat {
        let baseWidth: CGFloat = store.settings.compactGameCards ? 140 : 160
        let scale = CGFloat(min(max(store.settings.posterSizeScale, 0.75), 1.4))
        return baseWidth * scale
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Group {
                    if let symbol {
                        Label(title, systemImage: symbol)
                    } else {
                        Text(title)
                    }
                }
                .font(.subheadline.weight(.semibold))

                Spacer(minLength: 8)

                if let caption {
                    Text(caption)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 10) {
                    ForEach(games) { game in
                        let favorite = store.isFavorite(game)
                        let canLaunch = OpenNOWPlatform.supportsEmbeddedStreamer
                            && !store.launchOptions(for: game).isEmpty
                        GameCatalogGridCard(
                            game: game,
                            subtitle: gameCatalogSubtitle(for: game),
                            badgeSystemImage: nil,
                            compact: store.settings.compactGameCards,
                            favorite: favorite,
                            canLaunch: canLaunch,
                            onToggleFavorite: { store.toggleFavorite(game) },
                            onOpenDetails: { onOpenDetails(game) },
                            onPlay: { onPlay(game) }
                        )
                        .frame(width: cardWidth)
                    }
                }
                .padding(.horizontal, 2)
                .padding(.vertical, 4)
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel("\(title) games")
        }
    }
}

private struct ComingNextCarousel: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var selectedPage = 0
    @State private var focusedGameID: String?
    @FocusState private var focusedPageIndicator: Int?
    @State private var legacyFocusedPageIndicator: Int?
    @State private var voiceOverRunning = false

    let games: [CloudGame]
    let onOpenDetails: (CloudGame) -> Void
    let onPlay: (CloudGame) -> Void

    private var gameIDs: [String] {
        games.map(\.id)
    }

    private var shouldAutoAdvance: Bool {
        games.count > 1 &&
            focusedGameID == nil &&
            focusedPageIndicator == nil &&
            legacyFocusedPageIndicator == nil &&
            scenePhase == .active &&
            !reduceMotion &&
            !voiceOverRunning
    }

    private var autoAdvanceID: String {
        [
            gameIDs.joined(separator: "|"),
            String(selectedPage),
            scenePhase == .active ? "active" : "inactive",
            focusedGameID ?? "unfocused",
            focusedPageIndicator.map(String.init) ?? "no-indicator-focus",
            legacyFocusedPageIndicator.map(String.init) ?? "no-legacy-indicator-focus",
            reduceMotion ? "reduce" : "motion",
            voiceOverRunning ? "voiceover" : "standard"
        ].joined(separator: "#")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Label("Coming Next", systemImage: "sparkles")
                    .font(.subheadline.weight(.semibold))
                Spacer(minLength: 8)
                Text("Recently added or updated")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            TabView(selection: $selectedPage) {
                ForEach(Array(games.enumerated()), id: \.element.id) { index, game in
                    ComingNextHeroCard(
                        game: game,
                        onOpenDetails: { onOpenDetails(game) },
                        onPlay: { onPlay(game) },
                        onFocusChange: { focused in
                            if focused {
                                focusedGameID = game.id
                            } else if focusedGameID == game.id {
                                focusedGameID = nil
                            }
                        }
                    )
                    .padding(.horizontal, 2)
                    .padding(.vertical, 4)
                    .tag(index)
                }
            }
            .frame(height: 218)
            .tabViewStyle(.page(indexDisplayMode: .never))
            .accessibilityLabel("Coming Next games")

            HStack(spacing: 2) {
                ForEach(games.indices, id: \.self) { index in
                    Button {
                        selectPage(index)
                    } label: {
                        Capsule()
                            .fill(index == selectedPage ? brandAccent : Color.secondary.opacity(0.34))
                            .frame(width: index == selectedPage ? 14 : 6, height: 5)
                            .frame(width: 16, height: 22)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .controllerFocusableCompat(
                        fallbackActivation: { selectPage(index) },
                        onLegacyFocusChange: { focused in
                            if focused {
                                legacyFocusedPageIndicator = index
                            } else if legacyFocusedPageIndicator == index {
                                legacyFocusedPageIndicator = nil
                            }
                        }
                    )
                    .focused($focusedPageIndicator, equals: index)
                    .accessibilityLabel("Show \(games[index].title)")
                    .accessibilityAddTraits(index == selectedPage ? .isSelected : [])
                }
            }
            .frame(maxWidth: .infinity)
        }
        .onAppear {
            voiceOverRunning = UIAccessibility.isVoiceOverRunning
            normalizeSelectedPage()
        }
        .onChangeCompat(of: gameIDs) { _ in
            normalizeSelectedPage()
        }
        .onReceive(
            NotificationCenter.default.publisher(
                for: UIAccessibility.voiceOverStatusDidChangeNotification
            )
        ) { _ in
            voiceOverRunning = UIAccessibility.isVoiceOverRunning
        }
        .task(id: autoAdvanceID) {
            guard shouldAutoAdvance else { return }
            do {
                try await Task.sleep(nanoseconds: 6_000_000_000)
            } catch {
                return
            }
            guard !Task.isCancelled, shouldAutoAdvance, !games.isEmpty else { return }
            withAnimation(.easeInOut(duration: 0.32)) {
                selectedPage = (selectedPage + 1) % games.count
            }
        }
    }

    private func normalizeSelectedPage() {
        guard !games.isEmpty else {
            selectedPage = 0
            return
        }
        selectedPage = min(max(selectedPage, 0), games.count - 1)
    }

    private func selectPage(_ index: Int) {
        withAnimation(.easeInOut(duration: 0.28)) {
            selectedPage = index
        }
    }
}

private struct ComingNextHeroCard: View {
    @EnvironmentObject private var store: OpenNOWStore
    @EnvironmentObject private var controllerShortcuts: CatalogControllerShortcutCoordinator
    @FocusState private var isFocused: Bool
    @FocusState private var favoriteFocused: Bool
    @FocusState private var playFocused: Bool
    @State private var isLegacyFocused = false
    @State private var favoriteLegacyFocused = false
    @State private var playLegacyFocused = false
    @State private var controllerShortcutOwner = UUID()

    let game: CloudGame
    let onOpenDetails: () -> Void
    let onPlay: () -> Void
    let onFocusChange: (Bool) -> Void

    private var isVisuallyFocused: Bool {
        isFocused || favoriteFocused || playFocused ||
            isLegacyFocused || favoriteLegacyFocused || playLegacyFocused
    }

    private var canLaunch: Bool {
        OpenNOWPlatform.supportsEmbeddedStreamer && !store.launchOptions(for: game).isEmpty
    }

    private func openDetails() {
        Haptics.light()
        onOpenDetails()
    }

    private func toggleFavorite() {
        Haptics.light()
        store.toggleFavorite(game)
    }

    private func play() {
        guard canLaunch else { return }
        Haptics.medium()
        onPlay()
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        ZStack(alignment: .bottom) {
            Button(action: openDetails) {
                ZStack(alignment: .bottomLeading) {
                    GameArtworkView(game: game, iconSize: 54)

                    LinearGradient(
                        colors: [.clear, .black.opacity(0.30), .black.opacity(0.92)],
                        startPoint: .top,
                        endPoint: .bottom
                    )

                    VStack(alignment: .leading, spacing: 5) {
                        Text(game.title)
                            .font(.title3.bold())
                            .foregroundStyle(.white)
                            .lineLimit(2)
                        Text(gameCatalogSubtitle(for: game))
                            .font(.caption.weight(.medium))
                            .foregroundStyle(Color.white.opacity(0.78))
                            .lineLimit(1)
                    }
                    .padding(16)
                    .padding(.bottom, 44)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(shape)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .buttonStyle(.plain)
            .controllerFocusableCompat(
                fallbackActivation: openDetails,
                onLegacyFocusChange: { isLegacyFocused = $0 }
            )
            .focused($isFocused)

            HStack {
                Button(action: toggleFavorite) {
                    Image(systemName: store.isFavorite(game) ? "heart.fill" : "heart")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(store.isFavorite(game) ? Color.red : Color.white)
                        .frame(width: 42, height: 42)
                        .background(.ultraThinMaterial, in: Circle())
                }
                .buttonStyle(.plain)
                .controllerFocusableCompat(
                    fallbackActivation: toggleFavorite,
                    onLegacyFocusChange: { favoriteLegacyFocused = $0 }
                )
                .focused($favoriteFocused)
                .accessibilityLabel(store.isFavorite(game) ? "Remove \(game.title) from favorites" : "Add \(game.title) to favorites")

                Spacer(minLength: 8)

                Button(action: play) {
                    Image(systemName: "play.fill")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 46, height: 46)
                        .background(brandAccent.opacity(canLaunch ? 0.96 : 0.45), in: Circle())
                }
                .buttonStyle(.plain)
                .controllerFocusableCompat(
                    fallbackActivation: play,
                    onLegacyFocusChange: { playLegacyFocused = $0 }
                )
                .focused($playFocused)
                .disabled(!canLaunch)
                .accessibilityLabel("Launch \(game.title)")
            }
            .padding(12)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 210)
        .clipShape(shape)
        .overlay(
            shape.stroke(isVisuallyFocused ? brandAccent : Color.white.opacity(0.12), lineWidth: isVisuallyFocused ? 2 : 1)
        )
        .overlay(alignment: .topTrailing) {
            if isVisuallyFocused,
               controllerShortcuts.isEnabled,
               controllerShortcuts.controllerConnected {
                CatalogControllerShortcutHint(
                    favorite: store.isFavorite(game),
                    playEnabled: canLaunch
                )
                .padding(10)
            }
        }
        .scaleEffect(isVisuallyFocused ? 1.012 : 1)
        .animation(.easeOut(duration: 0.16), value: isVisuallyFocused)
        .onAppear {
            updateControllerShortcutRegistration(isVisuallyFocused)
        }
        .onChangeCompat(of: isVisuallyFocused) { focused in
            updateControllerShortcutRegistration(focused)
            onFocusChange(focused)
        }
        .onChangeCompat(of: controllerShortcuts.isEnabled) { enabled in
            updateControllerShortcutRegistration(enabled && isVisuallyFocused)
        }
        .onDisappear {
            controllerShortcuts.clearFocusedActions(owner: controllerShortcutOwner)
            onFocusChange(false)
        }
    }

    private func updateControllerShortcutRegistration(_ focused: Bool) {
        controllerShortcuts.updateFocusedActions(
            owner: controllerShortcutOwner,
            isFocused: focused,
            favorite: { toggleFavorite() },
            play: { play() }
        )
    }
}

struct FeaturedGameCard: View {
    @EnvironmentObject private var store: OpenNOWStore
    @FocusState private var isFocused: Bool
    @State private var isLegacyFocused = false
    let game: CloudGame
    let onOpenDetails: () -> Void

    private var cardWidth: CGFloat {
        let baseWidth: CGFloat = store.settings.compactGameCards ? 140 : 160
        let scale = CGFloat(min(max(store.settings.posterSizeScale, 0.75), 1.4))
        return baseWidth * scale
    }

    private func openDetails() {
        Haptics.light()
        onOpenDetails()
    }

    private var isVisuallyFocused: Bool {
        isFocused || isLegacyFocused
    }

    var body: some View {
        Button(action: openDetails) {
            GameVerticalBannerCard(
                game: game,
                subtitle: store.settings.showGameStoreLabels ? gameCatalogSubtitle(for: game) : nil,
                badgeSystemImage: nil,
                isFocused: isVisuallyFocused
            )
            .frame(width: cardWidth)
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .controllerFocusableCompat(
            fallbackActivation: openDetails,
            onLegacyFocusChange: { isLegacyFocused = $0 }
        )
        .focused($isFocused)
        .scaleEffect(isVisuallyFocused ? 1.025 : 1)
        .animation(.easeOut(duration: 0.16), value: isVisuallyFocused)
        .zIndex(isVisuallyFocused ? 2 : 0)
    }
}

private struct FeaturedGameCardSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            RoundedRectangle(cornerRadius: 14)
                .fill(.quaternary.opacity(0.4))
                .frame(width: 160, height: 100)
                .shimmeringSkeleton()
            VStack(alignment: .leading, spacing: 4) {
                RoundedRectangle(cornerRadius: 5)
                    .fill(.quaternary.opacity(0.4))
                    .frame(height: 32)
                RoundedRectangle(cornerRadius: 4)
                    .fill(.quaternary.opacity(0.3))
                    .frame(width: 70, height: 14)
            }
            .padding(10)
        }
        .frame(width: 160)
        .glassCard()
    }
}

struct ErrorBannerView: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.orange)
            Text(message)
                .font(.footnote)
                .foregroundStyle(.primary)
            Spacer()
        }
        .padding(12)
        .background(.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
    }
}

struct GameCardView: View {
    @EnvironmentObject private var store: OpenNOWStore
    @FocusState private var isFocused: Bool
    @State private var isLegacyFocused = false
    let game: CloudGame
    let onOpenDetails: () -> Void

    private func openDetails() {
        Haptics.light()
        onOpenDetails()
    }

    private var isVisuallyFocused: Bool {
        isFocused || isLegacyFocused
    }

    var body: some View {
        Button(action: openDetails) {
            GameVerticalBannerCard(
                game: game,
                subtitle: store.settings.showGameStoreLabels ? gameCatalogSubtitle(for: game) : nil,
                badgeSystemImage: nil,
                isFocused: isVisuallyFocused
            )
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .controllerFocusableCompat(
            fallbackActivation: openDetails,
            onLegacyFocusChange: { isLegacyFocused = $0 }
        )
        .focused($isFocused)
        .scaleEffect(isVisuallyFocused ? 1.025 : 1)
        .animation(.easeOut(duration: 0.16), value: isVisuallyFocused)
        .zIndex(isVisuallyFocused ? 2 : 0)
    }
}

struct GameLaunchDetailsSheet: View {
    let game: CloudGame
    let onLaunch: (GameLaunchOption?) -> Void
    @EnvironmentObject private var store: OpenNOWStore
    @Environment(\.dismiss) private var dismiss
    @State private var selectedOption: GameLaunchOption?
    @State private var launchAlertMessage: String?

    private var launcherOptions: [GameLaunchOption] {
        store.launchOptions(for: game)
    }

    private var launchUnavailableMessage: String? {
        if !OpenNOWPlatform.supportsEmbeddedStreamer {
            return OpenNOWPlatform.streamingUnavailableReason
        }
        if launcherOptions.isEmpty {
            return "This game doesn't expose launch targets yet."
        }
        return nil
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    GameLaunchDetailsArtworkCard(
                        game: game,
                        subtitle: gameSubtitle,
                        badgeSystemImage: store.isFavorite(game) ? "heart.fill" : nil
                    )
                    .frame(maxWidth: .infinity, alignment: .center)
                    .listRowInsets(EdgeInsets())

                }

                if let screenshots = game.screenshotUrls, !screenshots.isEmpty {
                    Section("Screenshots") {
                        GameScreenshotGallery(urls: screenshots)
                            .listRowInsets(EdgeInsets())
                    }
                }

                if !launcherOptions.isEmpty {
                    Section("Launch") {
                        Picker("Launcher", selection: selectedOptionBinding) {
                            ForEach(launcherOptions) { option in
                                Text(storeDisplayName(option.storefront)).tag(option.id)
                            }
                        }

                        if let selectedOption {
                            if !selectedControlLabels.isEmpty {
                                LabeledContent("Controls", value: selectedControlLabels.joined(separator: ", "))
                            }
                            Button {
                                store.setDefaultGameVariant(game: game, option: selectedOption)
                            } label: {
                                Label(
                                    store.defaultLaunchOption(for: game)?.id == selectedOption.id ? "Default Launcher" : "Set as Default",
                                    systemImage: store.defaultLaunchOption(for: game)?.id == selectedOption.id ? "star.fill" : "star"
                                )
                            }
                            if store.defaultLaunchOption(for: game) != nil {
                                Button(role: .destructive) {
                                    store.setDefaultGameVariant(game: game, option: nil)
                                } label: {
                                    Label("Clear Default", systemImage: "star.slash")
                                }
                            }
                        }
                    }
                }

                Section("Details") {
                    if let releaseDate = game.releaseDate {
                        LabeledContent("Release", value: releaseDate)
                    }
                    if let publisher = game.publisher {
                        LabeledContent("Publisher", value: publisher)
                    }
                    if let developer = game.developer {
                        LabeledContent("Developer", value: developer)
                    }
                    if let genre = displayMetadataLabel(game.genre) {
                        LabeledContent("Genre", value: genre)
                    }
                    if let platform = displayPlatform {
                        LabeledContent("Platform", value: platform)
                    }
                    if !resolvedStores.isEmpty {
                        LabeledContent("Stores", value: resolvedStores.map(storeDisplayName).joined(separator: ", "))
                    }
                    if let playType = displayMetadataLabel(game.playType) {
                        LabeledContent("Play Type", value: playType)
                    }
                    if let tier = displayMetadataLabel(game.membershipTierLabel) {
                        LabeledContent("Membership", value: tier)
                    }
                    LabeledContent(
                        "Age Rating",
                        value: GFNContentRatingParser.ageBadge(from: game.contentRatings) ?? "Not rated"
                    )
                }

                if !detailLabels.isEmpty {
                    Section("Features") {
                        ForEach(detailLabels.prefix(12), id: \.self) { label in
                            Text(label)
                        }
                    }
                }

                if let summary = summaryText {
                    Section("Description") {
                        Text(summary)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                if let launchUnavailableMessage {
                    Section {
                        Text(launchUnavailableMessage)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .navigationTitle(game.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        store.toggleFavorite(game)
                    } label: {
                        Image(systemName: store.isFavorite(game) ? "heart.fill" : "heart")
                    }
                    .tint(store.isFavorite(game) ? .red : nil)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 0) {
                    Button {
                        if let launchRestriction = store.launchRestrictionMessage(for: game) {
                            Haptics.medium()
                            launchAlertMessage = launchRestriction
                            return
                        }
                        Haptics.medium()
                        onLaunch(selectedOption ?? launcherOptions.first)
                        dismiss()
                    } label: {
                        Text(launchUnavailableMessage == nil ? "Launch" : "Launch Unavailable")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(brandAccent)
                    .disabled(launchUnavailableMessage != nil)
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 8)
                }
                .frame(maxWidth: .infinity)
                .bottomSheetFooterBackground()
            }
            .alert("Launch Unavailable", isPresented: launchAlertPresented) {
                Button("OK", role: .cancel) {
                    launchAlertMessage = nil
                }
            } message: {
                Text(launchAlertMessage ?? "")
            }
        }
        .onAppear {
            selectedOption = store.defaultLaunchOption(for: game) ?? launcherOptions.first
        }
    }

    private var selectedOptionBinding: Binding<String> {
        Binding(
            get: { selectedOption?.id ?? launcherOptions.first?.id ?? "" },
            set: { id in selectedOption = launcherOptions.first { $0.id == id } }
        )
    }

    private var launchAlertPresented: Binding<Bool> {
        Binding(
            get: { launchAlertMessage != nil },
            set: { isPresented in
                if !isPresented {
                    launchAlertMessage = nil
                }
            }
        )
    }

    private var selectedControlLabels: [String] {
        guard let controls = selectedOption?.supportedControls else { return [] }
        return gameMetadataDisplayLabels(controls)
    }

    private var resolvedStores: [String] {
        if let stores = game.stores, !stores.isEmpty {
            return stores
        }
        let derived = Array(Set(launcherOptions.map(\.storefront))).sorted()
        return derived
    }

    private var summaryText: String? {
        let long = game.longDescription?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !long.isEmpty {
            return long
        }
        let trimmed = game.summary?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmed.isEmpty {
            return trimmed
        }
        return nil
    }

    private var detailLabels: [String] {
        gameMetadataDisplayLabels((game.featureLabels ?? []) + (game.tags ?? [])).sorted()
    }

    private var gameSubtitle: String {
        let stores = resolvedStores.map(storeDisplayName).joined(separator: ", ")
        if !stores.isEmpty {
            return stores
        }
        return [displayMetadataLabel(game.genre), displayPlatform].compactMap { $0 }.joined(separator: " · ")
    }

    private var displayPlatform: String? {
        let trimmed = game.platform.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return storeDisplayName(trimmed)
    }

    private func displayMetadataLabel(_ value: String?) -> String? {
        guard let value else { return nil }
        let label = gameMetadataDisplayLabel(value)
        return label.isEmpty ? nil : label
    }
}

struct GameLauncherSelectionSheet: View {
    let game: CloudGame
    let onLaunch: (GameLaunchOption) -> Void

    @EnvironmentObject private var store: OpenNOWStore
    @Environment(\.dismiss) private var dismiss
    @State private var selectedOptionId = ""
    @State private var rememberDefault = false

    private var launcherOptions: [GameLaunchOption] {
        store.launchOptions(for: game)
    }

    private var selectedOption: GameLaunchOption? {
        launcherOptions.first { $0.id == selectedOptionId } ?? launcherOptions.first
    }

    private var defaultOption: GameLaunchOption? {
        store.defaultLaunchOption(for: game)
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 12) {
                        GameArtworkView(game: game, iconSize: 26, fit: true)
                            .frame(width: 58, height: 76)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                        VStack(alignment: .leading, spacing: 4) {
                            Text(game.title)
                                .font(.headline)
                                .lineLimit(2)
                            Text("Choose a launcher")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 2)
                }

                Section("Launcher") {
                    ForEach(launcherOptions) { option in
                        Button {
                            selectedOptionId = option.id
                        } label: {
                            LauncherOptionRow(
                                option: option,
                                selected: option.id == selectedOption?.id,
                                savedDefault: option.id == defaultOption?.id
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }

                if !launcherOptions.isEmpty {
                    Section {
                        Toggle("Remember for this game", isOn: $rememberDefault)
                    } footer: {
                        Text("When remembered, the play button launches this game with the selected launcher. Use the launcher badge to change it later.")
                    }
                }
            }
            .navigationTitle("Launch")
            .navigationBarTitleDisplayMode(.inline)
            .scrollContentBackground(.hidden)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 0) {
                    Button {
                        guard let selectedOption else { return }
                        if rememberDefault || defaultOption != nil {
                            store.setDefaultGameVariant(game: game, option: rememberDefault ? selectedOption : nil)
                        }
                        Haptics.medium()
                        onLaunch(selectedOption)
                        dismiss()
                    } label: {
                        Text("Continue")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(brandAccent)
                    .disabled(selectedOption == nil)
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 8)
                }
                .frame(maxWidth: .infinity)
                .bottomSheetFooterBackground()
            }
        }
        .onAppear {
            let defaultOption = defaultOption
            selectedOptionId = defaultOption?.id ?? launcherOptions.first?.id ?? ""
            rememberDefault = defaultOption != nil
        }
    }
}

private struct LauncherOptionRow: View {
    let option: GameLaunchOption
    let selected: Bool
    let savedDefault: Bool

    var body: some View {
        HStack(spacing: 12) {
            StoreGlyph(store: option.storefront)
                .frame(width: 24, height: 24)
                .frame(width: 42, height: 42)
                .background(launcherBadgeColor(for: option.storefront).opacity(0.94), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(alignment: .leading, spacing: 3) {
                Text(storeDisplayName(option.storefront))
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                if !detailText.isEmpty {
                    Text(detailText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 8)

            if selected {
                Image(systemName: "checkmark.circle.fill")
                    .font(.title3)
                    .foregroundStyle(brandAccent)
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }

    private var detailText: String {
        var parts: [String] = []
        if savedDefault {
            parts.append("Default")
        }
        if let controls = option.supportedControls {
            let labels = gameMetadataDisplayLabels(controls).prefix(3)
            if !labels.isEmpty {
                parts.append(labels.joined(separator: ", "))
            }
        }
        if parts.isEmpty {
            parts.append("App \(option.appId)")
        }
        return parts.joined(separator: " - ")
    }
}

private struct GameArtworkCard: View {
    @EnvironmentObject private var store: OpenNOWStore
    let game: CloudGame
    let artworkHeight: CGFloat
    let titleFont: Font
    let subtitleFont: Font
    let storeBadgeLimit: Int

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            GameArtworkView(game: game, iconSize: 36)
                .frame(maxWidth: .infinity)
                .frame(height: artworkHeight)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

            LinearGradient(
                colors: [.clear, .black.opacity(0.36), .black.opacity(0.88)],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: min(artworkHeight * 0.74, 170))
            .frame(maxHeight: .infinity, alignment: .bottom)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

            VStack(alignment: .leading, spacing: 8) {
                Text(game.title)
                    .font(titleFont)
                    .foregroundStyle(Color.white)
                    .lineLimit(3)
                    .minimumScaleFactor(0.86)
                    .fixedSize(horizontal: false, vertical: true)

                Text("\(game.genre) · \(game.platform)")
                    .font(subtitleFont)
                    .foregroundStyle(Color.white.opacity(0.82))
                    .lineLimit(1)

                if !displayStores.isEmpty {
                    HStack(spacing: 8) {
                        ForEach(displayStores, id: \.self) { store in
                            StorePill(store: store, prominent: false)
                        }
                    }
                }
            }
            .padding(14)
            .padding(.top, 26)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                Rectangle()
                    .fill(.ultraThinMaterial.opacity(0.86))
                    .mask(
                        LinearGradient(
                            colors: [.clear, Color.white.opacity(0.35), Color.white],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .allowsHitTesting(false)
            }
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .overlay(alignment: .topTrailing) {
            if store.isFavorite(game) {
                Image(systemName: "heart.fill")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(8)
                    .background(.red.opacity(0.88), in: Circle())
                    .padding(10)
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.18), radius: 12, y: 8)
    }

    private var displayStores: [String] {
        guard store.settings.showGameStoreLabels else { return [] }
        return Array(gameResolvedStores(game: game).prefix(storeBadgeLimit))
    }
}

private struct GameMetaCard: View {
    let label: String
    let value: String
    let icon: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(label, systemImage: icon)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, minHeight: 88, alignment: .topLeading)
        .padding(14)
        .glassCard()
    }
}

private struct StorePill: View {
    let store: String
    let prominent: Bool

    var body: some View {
        HStack(spacing: 8) {
            StoreGlyph(store: store)
                .frame(width: prominent ? 28 : 22, height: prominent ? 28 : 22)
            if prominent {
                Text(storeDisplayName(store))
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
            }
        }
        .foregroundColor(prominent ? .primary : .white)
        .padding(.horizontal, prominent ? 12 : 6)
        .padding(.vertical, prominent ? 10 : 6)
        .background(backgroundShape)
    }

    @ViewBuilder
    private var backgroundShape: some View {
        if prominent {
            Capsule()
                .fill(.regularMaterial)
                .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 1))
        } else {
            Capsule()
                .fill(Color.white.opacity(0.12))
                .overlay(Capsule().stroke(Color.white.opacity(0.14), lineWidth: 1))
        }
    }
}

struct StoreGlyph: View {
    let store: String

    var body: some View {
        ZStack {
            if showsGlyphBackground {
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(glyphBackground)
            }
            if let assetName {
                Image(assetName)
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .padding(imagePadding)
            } else {
                Image(systemName: "bag.fill")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.white)
            }
        }
    }

    private var normalizedStore: String {
        storeNormalizedKey(store)
    }

    private var glyphBackground: some ShapeStyle {
        switch normalizedStore {
        case "STEAM":
            return AnyShapeStyle(
                LinearGradient(
                    colors: [Color(red: 0.08, green: 0.16, blue: 0.24), Color(red: 0.17, green: 0.42, blue: 0.70)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
        case "EPIC", "EGS", "EPIC_GAMES_STORE":
            return AnyShapeStyle(Color.black)
        case "XBOX", "XBOX_GAME_PASS", "GAME_PASS":
            return AnyShapeStyle(
                LinearGradient(
                    colors: [Color(red: 0.31, green: 0.66, blue: 0.17), Color(red: 0.15, green: 0.48, blue: 0.12)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
        default:
            return AnyShapeStyle(Color.gray.opacity(0.8))
        }
    }

    private var showsGlyphBackground: Bool {
        normalizedStore != "STEAM"
    }

    private var assetName: String? {
        switch normalizedStore {
        case "STEAM":
            return "StoreSteam"
        case "EPIC", "EGS", "EPIC_GAMES_STORE":
            return "StoreEpic"
        case "XBOX", "XBOX_GAME_PASS", "GAME_PASS":
            return "StoreXbox"
        default:
            return nil
        }
    }

    private var imagePadding: CGFloat {
        switch normalizedStore {
        case "STEAM":
            return 0
        case "EPIC", "EGS", "EPIC_GAMES_STORE":
            return 3
        case "XBOX", "XBOX_GAME_PASS", "GAME_PASS":
            return 4
        default:
            return 2
        }
    }
}

struct GameCardSkeletonView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            RoundedRectangle(cornerRadius: 12)
                .fill(.quaternary.opacity(0.35))
                .aspectRatio(gameVerticalBannerAspectRatio, contentMode: .fit)
                .shimmeringSkeleton()
            RoundedRectangle(cornerRadius: 5)
                .fill(.quaternary.opacity(0.4))
                .frame(height: 12)
            RoundedRectangle(cornerRadius: 4)
                .fill(.quaternary.opacity(0.3))
                .frame(width: 100, height: 10)
            RoundedRectangle(cornerRadius: 7)
                .fill(.quaternary.opacity(0.35))
                .frame(height: 30)
        }
        .padding(10)
        .glassCard()
    }
}

struct GameArtworkView: View {
    enum Role {
        case catalog
        case details
        case queue
    }

    let game: CloudGame
    let iconSize: CGFloat
    var fit = false
    var role: Role = .catalog

    var body: some View {
        GeometryReader { proxy in
            let targetPixelSize = imageTargetPixelSize(for: proxy.size)
            ZStack {
                gameColor(for: game.title).opacity(0.2)
                if let imageUrl = artworkUrl,
                   let url = URL(string: requestArtworkURL(imageUrl, targetPixelSize: targetPixelSize)) {
                    CachedRemoteImage(url: url, targetPixelSize: targetPixelSize) { image in
                        fittedImage(image, size: proxy.size)
                    } placeholder: {
                        GameArtworkLoadingPlaceholder(game: game, iconSize: iconSize, isFailure: false)
                            .frame(width: proxy.size.width, height: proxy.size.height)
                    } failure: {
                        iconFallback
                            .frame(width: proxy.size.width, height: proxy.size.height)
                    }
                } else {
                    iconFallback
                        .frame(width: proxy.size.width, height: proxy.size.height)
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
            .clipped()
        }
    }

    @ViewBuilder
    private func fittedImage(_ image: Image, size: CGSize) -> some View {
        if fit {
            image
                .resizable()
                .scaledToFit()
                .frame(width: size.width, height: size.height)
        } else {
            image
                .resizable()
                .scaledToFill()
                .frame(width: size.width, height: size.height)
        }
    }

    private var iconFallback: some View {
        GameArtworkLoadingPlaceholder(game: game, iconSize: iconSize, isFailure: true)
    }

    private var artworkUrl: String? {
        switch role {
        case .catalog:
            return game.catalogArtworkUrl
        case .details:
            return game.detailsArtworkUrl
        case .queue:
            return game.queueArtworkUrl
        }
    }

    private func requestArtworkURL(_ source: String, targetPixelSize: Int) -> String {
        switch role {
        case .catalog:
            return source
        case .details, .queue:
            return optimizedNvidiaArtworkURL(source, targetPixelSize: targetPixelSize)
        }
    }
}

struct GameArtworkLoadingPlaceholder: View {
    let game: CloudGame
    let iconSize: CGFloat
    let isFailure: Bool

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    gameColor(for: game.title).opacity(isFailure ? 0.18 : 0.30),
                    Color.secondary.opacity(isFailure ? 0.12 : 0.18),
                    Color.black.opacity(isFailure ? 0.08 : 0.18)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            VStack(spacing: 10) {
                Image(systemName: isFailure ? game.icon : "photo")
                    .font(.system(size: iconSize, weight: .semibold))
                    .foregroundStyle(gameColor(for: game.title).opacity(isFailure ? 0.95 : 0.72))

                if !isFailure {
                    VStack(spacing: 5) {
                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                            .fill(Color.white.opacity(0.16))
                            .frame(width: 56, height: 6)
                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                            .fill(Color.white.opacity(0.11))
                            .frame(width: 34, height: 6)
                    }
                }
            }
        }
        .overlay {
            if !isFailure {
                LinearGradient(
                    colors: [.clear, Color.white.opacity(0.10), .clear],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .blendMode(.screen)
                .allowsHitTesting(false)
            }
        }
    }
}

func imageTargetPixelSize(for size: CGSize) -> Int {
    let points = max(size.width, size.height)
    let pixels = points * UIScreen.main.scale
    guard pixels.isFinite, pixels > 0 else { return 480 }
    return normalizedImageTargetPixelSize(Int(ceil(pixels)))
}

func normalizedImageTargetPixelSize(_ targetPixelSize: Int) -> Int {
    max(160, ((targetPixelSize + 159) / 160) * 160)
}

func optimizedNvidiaArtworkURL(_ raw: String, targetPixelSize: Int) -> String {
    guard raw.localizedCaseInsensitiveContains("img.nvidiagrid.net") else { return raw }
    let markers = [";f=", ";w=", ";h=", ";dpr="]
    let cutoff = markers.compactMap {
        raw.range(of: $0, options: .caseInsensitive)?.lowerBound
    }.min()
    let base = cutoff.map { String(raw[..<$0]) } ?? raw
    let width = min(max(targetPixelSize, 160), 1_920)
    return "\(base);f=webp;w=\(width)"
}

func gameColor(for title: String) -> Color {
    let palette: [Color] = [
        Color(red: 0.46, green: 0.72, blue: 0.0),
        Color(red: 0.0, green: 0.72, blue: 0.55),
        Color(red: 0.2, green: 0.5, blue: 1.0),
        Color(red: 0.8, green: 0.3, blue: 0.9),
        Color(red: 1.0, green: 0.6, blue: 0.0),
        Color(red: 0.9, green: 0.2, blue: 0.3),
    ]
    let hash = abs(title.hashValue)
    return palette[hash % palette.count]
}

private final class LegacyControllerFocusButton: UIButton {
    var onActivate: () -> Void = {}
    var onFocusChange: (Bool) -> Void = { _ in }

    private var reportedFocus = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isAccessibilityElement = false
        addTarget(self, action: #selector(activate), for: .primaryActionTriggered)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    override var canBecomeFocused: Bool {
        isEnabled
    }

    override func didUpdateFocus(
        in context: UIFocusUpdateContext,
        with coordinator: UIFocusAnimationCoordinator
    ) {
        super.didUpdateFocus(in: context, with: coordinator)
        reportFocusIfNeeded(isFocused)
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            reportFocusIfNeeded(false)
        }
    }

    @objc private func activate() {
        onActivate()
    }

    private func reportFocusIfNeeded(_ focused: Bool) {
        guard reportedFocus != focused else { return }
        reportedFocus = focused
        onFocusChange(focused)
    }
}

private struct LegacyControllerFocusProxy: UIViewRepresentable {
    let isEnabled: Bool
    let onActivate: () -> Void
    let onFocusChange: (Bool) -> Void

    func makeUIView(context: Context) -> LegacyControllerFocusButton {
        let button = LegacyControllerFocusButton(type: .custom)
        configure(button)
        return button
    }

    func updateUIView(_ button: LegacyControllerFocusButton, context: Context) {
        configure(button)
    }

    private func configure(_ button: LegacyControllerFocusButton) {
        button.isEnabled = isEnabled
        button.isUserInteractionEnabled = isEnabled
        button.onActivate = onActivate
        button.onFocusChange = onFocusChange
    }
}

private struct ControllerFocusableCompatModifier: ViewModifier {
    @Environment(\.isEnabled) private var isEnabled

    let fallbackActivation: () -> Void
    let onLegacyFocusChange: (Bool) -> Void

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.focusable()
        } else {
            content.overlay {
                LegacyControllerFocusProxy(
                    isEnabled: isEnabled,
                    onActivate: fallbackActivation,
                    onFocusChange: onLegacyFocusChange
                )
            }
        }
    }
}

extension View {
    func controllerFocusableCompat(
        fallbackActivation: @escaping () -> Void,
        onLegacyFocusChange: @escaping (Bool) -> Void = { _ in }
    ) -> some View {
        modifier(
            ControllerFocusableCompatModifier(
                fallbackActivation: fallbackActivation,
                onLegacyFocusChange: onLegacyFocusChange
            )
        )
    }

    func glassCard(cornerRadius: CGFloat = 16) -> some View {
        modifier(GlassCardModifier(cornerRadius: cornerRadius))
    }

    @ViewBuilder
    func gameBannerGridListRowStyle() -> some View {
        #if os(iOS)
        self
            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        #else
        self
            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
            .listRowBackground(Color.clear)
        #endif
    }

    func shimmeringSkeleton() -> some View {
        modifier(SkeletonShimmerModifier())
    }

    func bottomSheetFooterBackground() -> some View {
        background {
            Rectangle()
                .fill(.regularMaterial)
                .ignoresSafeArea(edges: .bottom)
        }
    }

    func presentGameDetailsSheet(
        selectedGame: Binding<CloudGame?>,
        store: OpenNOWStore,
        onLaunch: @escaping (CloudGame, GameLaunchOption?) -> Void
    ) -> some View {
        sheet(item: selectedGame) { game in
            GameLaunchDetailsSheet(game: game) { option in
                selectedGame.wrappedValue = nil
                DispatchQueue.main.async {
                    onLaunch(game, option)
                }
            }
            .environmentObject(store)
        }
    }

    func launcherSelectionModalSheet(
        selectedGame: Binding<CloudGame?>,
        store: OpenNOWStore,
        onLaunch: @escaping (CloudGame, GameLaunchOption) -> Void
    ) -> some View {
        sheet(item: selectedGame) { game in
            GameLauncherSelectionSheet(game: game) { option in
                selectedGame.wrappedValue = nil
                DispatchQueue.main.async {
                    onLaunch(game, option)
                }
            }
            .environmentObject(store)
        }
    }
}

extension View {
    func opennowBottomSheet<Item: Identifiable, Sheet: View>(
        item: Binding<Item?>,
        heightFraction: CGFloat,
        maxHeight: CGFloat,
        @ViewBuilder content: @escaping (Item) -> Sheet
    ) -> some View {
        fullScreenCover(item: item) { value in
            OpenNOWBottomSheetHost(heightFraction: heightFraction, maxHeight: maxHeight) {
                content(value)
            }
            .presentationBackground(.clear)
        }
    }

}

private struct OpenNOWBottomSheetHost<Content: View>: View {
    @Environment(\.dismiss) private var dismiss
    let heightFraction: CGFloat
    let maxHeight: CGFloat
    let content: Content

    init(heightFraction: CGFloat, maxHeight: CGFloat, @ViewBuilder content: () -> Content) {
        self.heightFraction = heightFraction
        self.maxHeight = maxHeight
        self.content = content()
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .bottom) {
                Color.black.opacity(0.28)
                    .ignoresSafeArea()
                    .onTapGesture { dismiss() }

                content
                    .frame(maxWidth: .infinity)
                    .frame(height: sheetFrameHeight(in: proxy))
                    .modifier(OpenNOWBottomSheetSurfaceModifier(cornerRadius: 28))
                    .shadow(color: .black.opacity(0.22), radius: 18, y: -4)
                    .ignoresSafeArea(edges: .bottom)
            }
        }
        .ignoresSafeArea()
        .background(Color.clear)
    }

    private func sheetFrameHeight(in proxy: GeometryProxy) -> CGFloat {
        min(proxy.size.height, sheetHeight(in: proxy) + proxy.safeAreaInsets.bottom)
    }

    private func sheetHeight(in proxy: GeometryProxy) -> CGFloat {
        min(maxHeight, max(360, proxy.size.height * heightFraction))
    }
}

private struct OpenNOWBottomSheetSurfaceModifier: ViewModifier {
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        let shape = OpenNOWBottomSheetShape(cornerRadius: cornerRadius)
        if #available(iOS 26, *) {
            content
                .background(.regularMaterial, in: shape)
                .glassEffect(in: shape)
                .clipShape(shape)
        } else {
            content
                .background(.regularMaterial, in: shape)
                .clipShape(shape)
        }
    }
}

private struct OpenNOWBottomSheetShape: Shape {
    let cornerRadius: CGFloat

    func path(in rect: CGRect) -> Path {
        let radius = min(cornerRadius, rect.width / 2, rect.height / 2)
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + radius))
        path.addQuadCurve(
            to: CGPoint(x: rect.minX + radius, y: rect.minY),
            control: CGPoint(x: rect.minX, y: rect.minY)
        )
        path.addLine(to: CGPoint(x: rect.maxX - radius, y: rect.minY))
        path.addQuadCurve(
            to: CGPoint(x: rect.maxX, y: rect.minY + radius),
            control: CGPoint(x: rect.maxX, y: rect.minY)
        )
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

private func storeNormalizedKey(_ store: String) -> String {
    normalizeGameStore(store)
}

func storeDisplayName(_ store: String) -> String {
    gameStoreDisplayName(store)
}

private func launcherBadgeColor(for store: String) -> Color {
    switch storeNormalizedKey(store) {
    case "STEAM":
        return Color(red: 0.09, green: 0.18, blue: 0.28)
    case "EPIC", "EGS", "EPIC_GAMES_STORE":
        return Color.black
    case "XBOX", "XBOX_GAME_PASS", "GAME_PASS":
        return Color(red: 0.06, green: 0.49, blue: 0.06)
    case "MICROSOFT", "MICROSOFT_STORE":
        return Color(red: 0.0, green: 0.40, blue: 0.72)
    case "UBISOFT", "UBISOFT_CONNECT":
        return Color(red: 0.0, green: 0.43, blue: 0.99)
    case "EA", "EA_APP", "ORIGIN":
        return Color(red: 1.0, green: 0.28, blue: 0.28)
    case "GOG", "GOG_COM":
        return Color(red: 0.42, green: 0.21, blue: 0.66)
    case "BATTLENET", "BATTLE_NET", "BLIZZARD":
        return Color(red: 0.08, green: 0.56, blue: 1.0)
    case "RIOT", "RIOT_CLIENT", "RIOT_GAMES":
        return Color(red: 0.82, green: 0.21, blue: 0.22)
    case "ROCKSTAR", "ROCKSTAR_GAMES", "ROCKSTAR_GAMES_LAUNCHER":
        return Color(red: 1.0, green: 0.77, blue: 0.0)
    case "GOOGLE_PLAY", "PLAY_STORE", "ANDROID":
        return Color(red: 0.06, green: 0.62, blue: 0.35)
    case "AMAZON", "AMAZON_GAMES":
        return Color(red: 1.0, green: 0.60, blue: 0.0)
    default:
        return Color.black.opacity(0.72)
    }
}

func gameResolvedStores(game: CloudGame) -> [String] {
    if let stores = game.stores, !stores.isEmpty {
        return stores
    }
    let derived = Array(Set(game.launchOptions.map(\.storefront))).sorted()
    return derived.isEmpty ? [game.platform] : derived
}

func gameCatalogSubtitle(for game: CloudGame, storeLimit: Int = 3) -> String {
    let stores = gameResolvedStores(game: game)
        .map(storeDisplayName)
        .prefix(storeLimit)
        .joined(separator: ", ")
    if !stores.isEmpty {
        return stores
    }
    return [game.genre, game.platform].filter { !$0.isEmpty }.joined(separator: " · ")
}
