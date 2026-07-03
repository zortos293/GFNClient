import SwiftUI

struct LibraryView: View {
    @EnvironmentObject private var store: OpenNOWStore
    @State private var pendingLaunchRequest: GameLaunchRequest?
    @State private var selectedGameForDetails: CloudGame?
    @State private var searchText = ""
    @State private var selectedGenre: String?
    @State private var selectedPlatform: String?
    @State private var selectedStore: String?
    @State private var favoritesOnly = false
    @State private var sortMode: CatalogSortMode = .title
    @State private var selectedGameForLauncher: CloudGame?

    var body: some View {
        NavigationStack {
            Group {
                if store.user == nil {
                    OpenNOWUnavailableView("Signed Out", systemImage: "person.crop.circle.badge.exclamationmark")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    GameCatalogGridView(
                        games: filteredGames,
                        isLoading: store.libraryGames.isEmpty && store.isLoadingGames,
                        emptyTitle: store.libraryGames.isEmpty && !hasActiveFilters ? "Library Empty" : "No Matches",
                        emptySystemImage: store.libraryGames.isEmpty && !hasActiveFilters ? "books.vertical" : "magnifyingglass",
                        subtitle: { gameCatalogSubtitle(for: $0) },
                        badgeSystemImage: { _ in nil },
                        onOpenDetails: { selectedGameForDetails = $0 },
                        onPlay: launchFromCard
                    ) {
                        libraryHeader
                    } emptyActions: {
                        if hasActiveFilters {
                            Button("Clear Filters") {
                                clearFilters()
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(brandAccent)
                        }
                    }
                }
            }
            .navigationTitle("Library")
            .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .automatic), prompt: "Search library")
            .refreshable { await store.refreshCatalog() }
        }
        .presentGameDetailsUIKit(selectedGame: $selectedGameForDetails) { game, option in
            pendingLaunchRequest = GameLaunchRequest(game: game, launchOption: option)
        }
        .opennowBottomSheet(item: $selectedGameForLauncher, heightFraction: 0.58, maxHeight: 560) { game in
            GameLauncherSelectionSheet(game: game) { option in
                selectedGameForLauncher = nil
                DispatchQueue.main.async {
                    pendingLaunchRequest = GameLaunchRequest(game: game, launchOption: option)
                }
            }
            .environmentObject(store)
        }
        .printedWasteLaunchSheet(pendingLaunchRequest: $pendingLaunchRequest)
    }

    private var libraryHeader: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let active = store.activeSession {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Current Session")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)

                    GameBannerRowView(
                        games: [active.game],
                        subtitle: { _ in active.status == 3 ? "Streaming" : "Queued" },
                        badgeSystemImage: { _ in active.status == 3 ? "play.circle.fill" : "hourglass" }
                    ) { _ in
                        store.jumpBackToSession()
                    }
                }
            }

            CatalogControlsHeader(
                title: libraryCountTitle,
                subtitle: "Synced library",
                chips: activeFilterChips,
                onClear: hasActiveFilters ? clearFilters : nil
            ) {
                HStack(spacing: 8) {
                    filterMenu
                    sortMenu
                }
            }
        }
    }

    private var filterMenu: some View {
        Menu {
            Toggle("Favorites", isOn: $favoritesOnly)

            Picker("Platform", selection: binding(for: $selectedPlatform)) {
                Text("Any Platform").tag("")
                ForEach(platforms, id: \.self) { platform in
                    Text(platform).tag(platform)
                }
            }

            Picker("Genre", selection: binding(for: $selectedGenre)) {
                Text("Any Genre").tag("")
                ForEach(genres, id: \.self) { genre in
                    Text(genre).tag(genre)
                }
            }

            Picker("Launcher", selection: binding(for: $selectedStore)) {
                Text("Any Launcher").tag("")
                ForEach(stores, id: \.self) { storeName in
                    Text(storeDisplayName(storeName)).tag(storeName)
                }
            }

            if hasActiveFilters {
                Divider()
                Button("Clear Filters", role: .destructive) {
                    clearFilters()
                }
            }
        } label: {
            Image(systemName: hasActiveFilters ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
        }
        .accessibilityLabel("Filters")
    }

    private var sortMenu: some View {
        Menu {
            Picker("Sort", selection: $sortMode) {
                ForEach(CatalogSortMode.allCases) { mode in
                    Label(mode.title, systemImage: mode.icon).tag(mode)
                }
            }
        } label: {
            Image(systemName: "arrow.up.arrow.down")
        }
        .accessibilityLabel("Sort")
    }

    private var filteredGames: [CloudGame] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        let filtered = store.libraryGames.filter { game in
            let matchesQuery = query.isEmpty ||
                game.title.localizedCaseInsensitiveContains(query) ||
                game.genre.localizedCaseInsensitiveContains(query) ||
                game.platform.localizedCaseInsensitiveContains(query) ||
                (game.publisher?.localizedCaseInsensitiveContains(query) ?? false) ||
                (game.developer?.localizedCaseInsensitiveContains(query) ?? false) ||
                gameResolvedStores(game: game).contains { storeDisplayName($0).localizedCaseInsensitiveContains(query) }
            let matchesGenre = selectedGenre == nil || game.genre == selectedGenre
            let matchesPlatform = selectedPlatform == nil || game.platform == selectedPlatform
            let matchesStore = selectedStore.map { gameResolvedStores(game: game).contains($0) } ?? true
            let matchesFavorite = !favoritesOnly || store.isFavorite(game)
            return matchesQuery && matchesGenre && matchesPlatform && matchesStore && matchesFavorite
        }

        switch sortMode {
        case .title:
            return filtered.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        case .genre:
            return filtered.sorted {
                $0.genre == $1.genre
                    ? $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
                    : $0.genre.localizedCaseInsensitiveCompare($1.genre) == .orderedAscending
            }
        case .platform:
            return filtered.sorted {
                $0.platform == $1.platform
                    ? $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
                    : $0.platform.localizedCaseInsensitiveCompare($1.platform) == .orderedAscending
            }
        }
    }

    private var genres: [String] {
        Array(Set(store.libraryGames.map(\.genre).filter { !$0.isEmpty })).sorted()
    }

    private var platforms: [String] {
        Array(Set(store.libraryGames.map(\.platform).filter { !$0.isEmpty })).sorted()
    }

    private var stores: [String] {
        Array(Set(store.libraryGames.flatMap { gameResolvedStores(game: $0) })).sorted()
    }

    private var libraryCountTitle: String {
        if filteredGames.count == store.libraryGames.count {
            return store.libraryGames.count == 1 ? "1 Game" : "\(store.libraryGames.count) Games"
        }
        return "\(filteredGames.count) / \(store.libraryGames.count) Games"
    }

    private var activeFilterChips: [CatalogFilterChip] {
        var chips: [CatalogFilterChip] = []
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        if !query.isEmpty {
            chips.append(CatalogFilterChip(label: "Search: \(query)") {
                searchText = ""
            })
        }
        if favoritesOnly {
            chips.append(CatalogFilterChip(label: "Favorites") {
                favoritesOnly = false
            })
        }
        if let selectedStore {
            chips.append(CatalogFilterChip(label: storeDisplayName(selectedStore)) {
                self.selectedStore = nil
            })
        }
        if let selectedPlatform {
            chips.append(CatalogFilterChip(label: selectedPlatform) {
                self.selectedPlatform = nil
            })
        }
        if let selectedGenre {
            chips.append(CatalogFilterChip(label: selectedGenre) {
                self.selectedGenre = nil
            })
        }
        if sortMode != .title {
            chips.append(CatalogFilterChip(label: "Sort: \(sortMode.title)") {
                sortMode = .title
            })
        }
        return chips
    }

    private var hasActiveFilters: Bool {
        !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            selectedGenre != nil ||
            selectedPlatform != nil ||
            selectedStore != nil ||
            favoritesOnly ||
            sortMode != .title
    }

    private func binding(for optional: Binding<String?>) -> Binding<String> {
        Binding(
            get: { optional.wrappedValue ?? "" },
            set: { optional.wrappedValue = $0.isEmpty ? nil : $0 }
        )
    }

    private func clearFilters() {
        searchText = ""
        selectedGenre = nil
        selectedPlatform = nil
        selectedStore = nil
        favoritesOnly = false
        sortMode = .title
    }

    private func launchFromCard(_ game: CloudGame) {
        let options = store.launchOptions(for: game)
        if options.count > 1, store.defaultLaunchOption(for: game) == nil {
            selectedGameForLauncher = game
            return
        }
        pendingLaunchRequest = GameLaunchRequest(game: game, launchOption: store.defaultLaunchOption(for: game) ?? options.first)
    }

}
