package com.opencloudgaming.opennow

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import com.opencloudgaming.opennow.ui.theme.LocalReduceMotion
import com.opencloudgaming.opennow.ui.theme.OpenNowMotion
import com.opencloudgaming.opennow.ui.theme.OpenNowPalette
import com.opencloudgaming.opennow.ui.theme.OpenNowRadius
import com.opencloudgaming.opennow.ui.theme.OpenNowSpacing
import com.opencloudgaming.opennow.ui.theme.tint
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeScreen(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    tvProfile: Boolean,
    hideChromeWhenScrolled: Boolean,
    controlsInTopBar: Boolean,
    topBarFocusRequester: FocusRequester?,
    searchRequested: Boolean,
    onSearchDismissed: () -> Unit,
    onScrollChromeHiddenChange: (Boolean) -> Unit,
) {
    val catalogGames = state.games.ifEmpty { state.catalogResult.games }
    val visibleGames = remember(catalogGames, state.catalogFilterIds) {
        filterCatalogGamesForLocalControls(catalogGames, state.catalogFilterIds)
    }
    val searchingCatalog = state.loadingGames && state.catalogSearch.isNotBlank()
    val gridState = rememberLazyGridState()
    val searchFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val selectGameWithHaptic: (GameInfo) -> Unit = { game ->
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        viewModel.selectGame(game)
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val showSearch = searchRequested || state.catalogSearch.isNotBlank()
    val physicalControllerConnected = rememberPhysicalControllerConnected(
        enabled = hideChromeWhenScrolled && !tvProfile,
    )
    val showScrollActions = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 80
    val scrolledAwayFromTop = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
    val hideScrollChrome = shouldHideStoreChromeOnScroll(
        hideChromeWhenScrolled = hideChromeWhenScrolled,
        scrolledAwayFromTop = scrolledAwayFromTop,
        physicalControllerConnected = physicalControllerConnected,
    )
    LaunchedEffect(hideScrollChrome) {
        onScrollChromeHiddenChange(hideScrollChrome)
    }
    DisposableEffect(Unit) {
        onDispose { onScrollChromeHiddenChange(false) }
    }
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            delay(90)
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }
    SwipeToRefreshContainer(
        refreshing = state.loadingGames,
        enabled = !tvProfile,
        showRefreshIndicator = !searchingCatalog,
        onRefresh = viewModel::refreshGames,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        top = if (controlsInTopBar) 4.dp else 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimatedVisibility(visible = showSearch) {
                    NativeSearchField(
                        modifier = Modifier.fillMaxWidth(),
                        query = state.catalogSearch,
                        onQueryChange = { next ->
                            viewModel.setCatalogSearch(next)
                            if (next.isBlank()) onSearchDismissed()
                        },
                        placeholder = stringResource(R.string.search_games),
                        searching = searchingCatalog,
                        focusRequester = searchFocusRequester,
                        onOpen = {
                            if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
                                scope.launch { gridState.animateScrollToItem(0) }
                            }
                        },
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        },
                ) {
                    if (state.loadingGames && visibleGames.isEmpty()) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            StoreScrollableControls(
                                state = state,
                                onSortChange = viewModel::setCatalogSort,
                                onFilterToggle = viewModel::toggleCatalogFilter,
                                showToolbar = !controlsInTopBar,
                            )
                            if (showSearch) {
                                SectionHeader(
                                    title = stringResource(R.string.store_results),
                                    modifier = Modifier.padding(top = OpenNowSpacing.lg, bottom = OpenNowSpacing.sm),
                                )
                            }
                            RefreshingGamesPlaceholder(
                                settings = state.settings,
                                tvProfile = tvProfile,
                                storeLayout = !showSearch,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        StoreGameGrid(
                            games = visibleGames,
                            favoriteIds = state.settings.favoriteGameIds,
                            settings = state.settings,
                            tvProfile = tvProfile,
                            state = state,
                            onSelect = selectGameWithHaptic,
                            onFavorite = viewModel::updateFavorites,
                            onPlay = viewModel::play,
                            onChooseStore = viewModel::chooseStore,
                            onSortChange = viewModel::setCatalogSort,
                            onFilterToggle = viewModel::toggleCatalogFilter,
                            onClearSearch = {
                                viewModel.setCatalogSearch("")
                                onSearchDismissed()
                            },
                            onClearFilters = viewModel::clearCatalogFilters,
                            gridState = gridState,
                            showToolbar = !controlsInTopBar,
                            topFocusRequester = topBarFocusRequester,
                            searchActive = showSearch,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (showScrollActions) {
                        Box(Modifier.align(Alignment.BottomEnd).padding(2.dp)) {
                            StoreScrollActionButton(
                                iconRes = R.drawable.ic_arrow_up,
                                contentDescription = stringResource(R.string.action_scroll_top),
                            ) {
                                scope.launch { gridState.animateScrollToItem(0) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreScrollableControls(
    state: OpenNowUiState,
    onSortChange: (String) -> Unit,
    onFilterToggle: (String) -> Unit,
    showToolbar: Boolean = true,
) {
    val filterGroups = catalogVisibleFilterGroups(state.catalogResult.filterGroups)
    val filterOptions = catalogFilterOptions(filterGroups)
    val hasSelectedFilters = state.catalogFilterIds.isNotEmpty()
    val hasError = !state.error.isNullOrBlank()
    if (!showToolbar && !hasSelectedFilters && !hasError) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showToolbar) {
            StoreCatalogToolbar(
                state = state,
                onSortChange = onSortChange,
                onFilterToggle = onFilterToggle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SelectedFilterChips(options = filterOptions, selectedIds = state.catalogFilterIds, onToggle = onFilterToggle)
        InlineErrorNotice(error = state.error)
    }
}

@Composable
internal fun StoreCatalogToolbar(
    state: OpenNowUiState,
    onSortChange: (String) -> Unit,
    onFilterToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val filterGroups = catalogVisibleFilterGroups(state.catalogResult.filterGroups)
    val filterOptions = catalogFilterOptions(filterGroups)
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortPicker(
            options = state.catalogResult.sortOptions,
            selected = state.catalogSortId,
            onSelect = onSortChange,
            modifier = Modifier.width(if (compact) 118.dp else 172.dp),
            compact = compact,
        )
        if (filterOptions.isNotEmpty()) {
            FilterMenu(options = filterOptions, selectedIds = state.catalogFilterIds, onToggle = onFilterToggle, compact = compact)
        }
    }
}

@Composable
private fun InlineErrorNotice(error: String?) {
    if (error.isNullOrBlank()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = OpenNowPalette.ErrorContainer,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                compactErrorTitle(error),
                color = OpenNowPalette.OnErrorContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                compactErrorBody(error),
                color = OpenNowPalette.OnErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun compactErrorTitle(error: String): String =
    when {
        error.contains("DNS lookup failed", ignoreCase = true) -> "Network lookup failed"
        error.contains("Unable to resolve host", ignoreCase = true) -> "Network lookup failed"
        else -> "Something went wrong"
    }

private fun compactErrorBody(error: String): String =
    error
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .let { if (it.length > 180) "${it.take(177)}..." else it }

@Composable
private fun StoreScrollActionButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = PanelAlt.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun LibraryScreen(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    tvProfile: Boolean,
    hideChromeWhenScrolled: Boolean,
    controlsInTopBar: Boolean,
    topBarFocusRequester: FocusRequester?,
    searchRequested: Boolean,
    onSearchDismissed: () -> Unit,
    onScrollChromeHiddenChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val selectGameWithHaptic: (GameInfo) -> Unit = { game ->
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        viewModel.selectGame(game)
    }
    val orderedGames = remember(state.libraryGames, state.settings.favoriteGameIds, state.librarySortId) {
        sortLibraryGames(
            favoriteOrderedGames(state.libraryGames, state.settings.favoriteGameIds),
            state.librarySortId,
        )
    }
    val touchFilterLabel = stringResource(R.string.catalog_filter_touch_controls)
    val filterOptions = remember(orderedGames, touchFilterLabel) {
        libraryStoreFilterOptions(orderedGames, touchFilterLabel)
    }
    val games = remember(orderedGames, state.librarySearch, state.libraryFilterIds) {
        orderedGames.filter { game ->
            gameMatchesSearch(game, state.librarySearch) && gameMatchesLibraryFilters(game, state.libraryFilterIds)
        }
    }
    val gridState = rememberLazyGridState()
    val searchFocusRequester = remember { FocusRequester() }
    val localAppsFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val showSearch = searchRequested || state.librarySearch.isNotBlank()
    val scrolledAwayFromTop = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
    val hideScrollChrome = hideChromeWhenScrolled && scrolledAwayFromTop
    LaunchedEffect(hideScrollChrome) {
        onScrollChromeHiddenChange(hideScrollChrome)
    }
    DisposableEffect(Unit) {
        onDispose { onScrollChromeHiddenChange(false) }
    }
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            delay(90)
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }
    SwipeToRefreshContainer(
        refreshing = state.loadingGames,
        enabled = !tvProfile,
        onRefresh = viewModel::refreshGames,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        top = if (controlsInTopBar) 4.dp else 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(visible = showSearch) {
                    NativeSearchField(
                        modifier = Modifier.fillMaxWidth(),
                        query = state.librarySearch,
                        onQueryChange = { next ->
                            viewModel.setLibrarySearch(next)
                            if (next.isBlank()) onSearchDismissed()
                        },
                        placeholder = "Search library",
                        focusRequester = searchFocusRequester,
                    )
                }
                if (BuildConfig.LOCAL_APP_LAUNCHER_SUPPORTED && state.settings.localAppsEnabled) {
                    LocalAppsShelf(
                        packageNames = state.settings.localAppPackageNames,
                        onAddPackage = viewModel::addLocalApp,
                        onRemovePackage = viewModel::removeLocalApp,
                        focusRequester = localAppsFocusRequester,
                        topFocusRequester = topBarFocusRequester,
                    )
                }
                LibraryFilterControls(
                    gameCount = games.size,
                    totalCount = state.libraryGames.size,
                    options = filterOptions,
                    selectedIds = state.libraryFilterIds,
                    onToggle = viewModel::toggleLibraryFilter,
                    showToolbar = !controlsInTopBar,
                )
                if (state.loadingGames && state.libraryGames.isEmpty()) {
                    RefreshingGamesPlaceholder(
                        settings = state.settings,
                        tvProfile = tvProfile,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    GameGrid(
                        games,
                        state.settings.favoriteGameIds,
                        state.settings,
                        tvProfile,
                        selectGameWithHaptic,
                        viewModel::updateFavorites,
                        viewModel::play,
                        viewModel::chooseStore,
                        topFocusRequester = if (BuildConfig.LOCAL_APP_LAUNCHER_SUPPORTED && state.settings.localAppsEnabled) {
                            localAppsFocusRequester
                        } else {
                            topBarFocusRequester
                        },
                        modifier = Modifier.weight(1f),
                        gridState = gridState,
                        emptyContent = {
                            val hasSearch = state.librarySearch.isNotBlank()
                            val hasFilters = state.libraryFilterIds.isNotEmpty()
                            if ((hasSearch || hasFilters) && state.libraryGames.isNotEmpty()) {
                                SearchEmptyState(
                                    title = stringResource(R.string.library_empty_search_title),
                                    message = when {
                                        hasSearch && hasFilters -> stringResource(R.string.library_empty_search_filters_body)
                                        hasSearch -> stringResource(R.string.library_empty_search_body)
                                        else -> stringResource(R.string.library_empty_filters_body)
                                    },
                                    onClearSearch = if (hasSearch) {
                                        {
                                            viewModel.setLibrarySearch("")
                                            onSearchDismissed()
                                        }
                                    } else {
                                        null
                                    },
                                    onClearFilters = if (hasFilters) {
                                        { viewModel.clearLibraryFilters() }
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                Text(stringResource(R.string.no_games_loaded), color = TextMuted)
                            }
                        },
                    )
                }
            }
        }
    }
}

internal const val LIBRARY_SORT_DEFAULT = "library"
internal const val LIBRARY_SORT_RECENT = "recent"
internal const val LIBRARY_SORT_TITLE = "title"

@Composable
internal fun librarySortOptions(): List<CatalogSortOption> = listOf(
    CatalogSortOption(LIBRARY_SORT_DEFAULT, stringResource(R.string.library_sort_default), ""),
    CatalogSortOption(LIBRARY_SORT_RECENT, stringResource(R.string.library_sort_recent), ""),
    CatalogSortOption(LIBRARY_SORT_TITLE, stringResource(R.string.library_sort_title), ""),
)

internal fun sortLibraryGames(games: List<GameInfo>, sortId: String): List<GameInfo> =
    when (sortId) {
        LIBRARY_SORT_TITLE -> games.sortedBy { it.title.lowercase(Locale.US) }
        LIBRARY_SORT_RECENT -> games.sortedWith(
            compareByDescending<GameInfo> { it.recentPlaySortKey() != null }
                .thenByDescending { it.recentPlaySortKey() }
                .thenBy { it.title.lowercase(Locale.US) },
        )
        else -> games
    }

@Composable
internal fun LibraryFilterControls(
    gameCount: Int,
    totalCount: Int,
    options: List<CatalogFilterOption>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showToolbar: Boolean = true,
    showSelectedChips: Boolean = true,
) {
    if (!showToolbar && (!showSelectedChips || selectedIds.isEmpty())) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showToolbar) {
            Row(
                if (compact) Modifier else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val countModifier = if (compact) Modifier else Modifier.weight(1f)
                Text(
                    text = if (gameCount == totalCount) {
                        stringResource(R.string.library_count, totalCount)
                    } else {
                        "$gameCount / ${stringResource(R.string.library_count, totalCount)}"
                    },
                    color = TextMuted,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = countModifier,
                )
                if (options.isNotEmpty()) {
                    FilterMenu(options = options, selectedIds = selectedIds, onToggle = onToggle, compact = compact)
                }
            }
        }
        if (showSelectedChips) {
            SelectedFilterChips(options = options, selectedIds = selectedIds, onToggle = onToggle)
        }
    }
}

internal fun libraryStoreFilterOptions(
    games: List<GameInfo>,
    touchFilterLabel: String = "Touch controls",
): List<CatalogFilterOption> {
    val labelsById = linkedMapOf<String, String>()
    games.forEach { game ->
        libraryStoreFilterIds(game).forEach { (id, label) ->
            labelsById.putIfAbsent(id, label)
        }
    }
    val storeOptions = labelsById.entries
        .sortedBy { it.value.lowercase(Locale.US) }
        .map { (id, label) ->
            CatalogFilterOption(
                id = id,
                rawId = id.removePrefix(LIBRARY_STORE_FILTER_PREFIX),
                label = label,
                groupId = "library_store",
                groupLabel = "Launcher",
            )
        }
    val touchOption = if (games.any(::catalogClaimsTouchSupport)) {
        listOf(
            CatalogFilterOption(
                id = CATALOG_FILTER_TOUCHSCREEN,
                rawId = SUPPORTED_CONTROL_TOUCHSCREEN,
                label = touchFilterLabel,
                groupId = "supported_controls",
                groupLabel = "Controls",
            ),
        )
    } else {
        emptyList()
    }
    return touchOption + storeOptions
}

internal fun gameMatchesLibraryFilters(game: GameInfo, selectedIds: List<String>): Boolean {
    if (selectedIds.isEmpty()) return true
    if (CATALOG_FILTER_TOUCHSCREEN in selectedIds && !catalogClaimsTouchSupport(game)) return false
    val selectedStoreIds = selectedIds.filter { it.startsWith(LIBRARY_STORE_FILTER_PREFIX) }
    if (selectedStoreIds.isEmpty()) return true
    val gameFilterIds = libraryStoreFilterIds(game).map { it.first }.toSet()
    return selectedStoreIds.any { it in gameFilterIds }
}

internal fun filterCatalogGamesForLocalControls(games: List<GameInfo>, selectedIds: List<String>): List<GameInfo> =
    if (CATALOG_FILTER_TOUCHSCREEN in selectedIds) games.filter(::catalogClaimsTouchSupport) else games

private fun libraryStoreFilterIds(game: GameInfo): List<Pair<String, String>> {
    val labels = libraryStoreDisplayNames(game)
    return labels
        .mapNotNull { label ->
            val normalized = normalizeGameStore(label)
            if (normalized.isBlank()) return@mapNotNull null
            LIBRARY_STORE_FILTER_PREFIX + normalized to label
        }
        .distinctBy { it.first }
}

private const val LIBRARY_STORE_FILTER_PREFIX = "library_store:"

@Composable
private fun ActiveSessionResumeCard(
    state: OpenNowUiState,
    onResumeActiveSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.activeSession ?: return
    val game = activeSessionGame(state, active)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PanelAlt.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UrlImage(
                game?.imageUrl,
                Modifier
                    .width(44.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.catalog_resume_cloud_session), color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    game?.title ?: stringResource(R.string.catalog_app_id, active.appId),
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    activeSessionSummary(active),
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onResumeActiveSession, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.action_resume), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun activeSessionGame(state: OpenNowUiState, active: ActiveSessionInfo): GameInfo? =
    (state.games + state.libraryGames).firstOrNull { game ->
        game.launchAppId == active.appId.toString() ||
            game.variants.any { variant -> variant.id == active.appId.toString() }
    }

@Composable
internal fun activeSessionSummary(active: ActiveSessionInfo): String =
    listOfNotNull(
        when (active.status) {
            1 -> active.queuePosition?.takeIf { it > 0 }?.let { stringResource(R.string.queue_short_position, it) }
                ?: stringResource(R.string.common_starting)
            2, 3 -> stringResource(R.string.common_ready)
            else -> stringResource(R.string.common_active)
        },
        active.resolution,
        active.fps?.let { "${it} FPS" },
        active.gpuType,
        active.sessionId.take(8).takeIf { it.isNotBlank() }?.let { "Session $it" },
    ).joinToString(" - ")

@Composable
private fun SearchEmptyState(
    title: String,
    message: String,
    onClearSearch: (() -> Unit)? = null,
    onClearFilters: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onClearSearch?.let { clearSearch ->
                OutlinedButton(onClick = clearSearch) {
                    Text(stringResource(R.string.search_clear), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            onClearFilters?.let { clearFilters ->
                OutlinedButton(onClick = clearFilters) {
                    Text(stringResource(R.string.action_clear_filters), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RefreshingGamesPlaceholder(
    settings: AppSettings,
    tvProfile: Boolean,
    storeLayout: Boolean = false,
    modifier: Modifier = Modifier,
) {
    GameGridSkeleton(
        settings = settings,
        tvProfile = tvProfile,
        storeLayout = storeLayout,
        modifier = modifier,
    )
}

internal val LocalShimmerOffset = staticCompositionLocalOf<State<Float>?> { null }
internal val LocalTvLoadingPulse = staticCompositionLocalOf<State<Float>?> { null }
internal val LocalTvLoadingProfile = staticCompositionLocalOf { false }
internal val LocalTouchControllerStyle = staticCompositionLocalOf { TouchControllerStyle.V1 }
internal val LocalSelectedCatalogGameId = staticCompositionLocalOf<String?> { null }
internal const val SHIMMER_CYCLE_DURATION_MS = 760

@Composable
private fun GameGridSkeleton(
    settings: AppSettings,
    tvProfile: Boolean,
    storeLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = settings.posterSizeScale.coerceIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE)
    val compact = settings.compactGameCards
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val physicalControllerConnected = rememberPhysicalControllerConnected(enabled = landscapeLayout && !tvProfile)
    val controllerActionMode = landscapeLayout && !tvProfile && physicalControllerConnected
    val artworkOnly = shouldUseArtworkOnlyCatalogCards(
        tvProfile = tvProfile,
        controllerActionMode = controllerActionMode,
    )

    val shimmerOffset: State<Float>?
    val tvPulse: State<Float>?
    // Under reduced motion the skeletons still show — they just stop animating. A never-ending
    // sweep is exactly the kind of movement the setting exists to stop.
    if (LocalReduceMotion.current) {
        shimmerOffset = null
        tvPulse = null
    } else if (tvProfile) {
        val transition = rememberInfiniteTransition(label = "loading-pulse-global")
        val pulse = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loading-pulse-global",
        )
        shimmerOffset = null
        tvPulse = pulse
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer-global")
        val shimmer = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = SHIMMER_CYCLE_DURATION_MS, easing = LinearEasing),
            ),
            label = "shimmer-offset-global",
        )
        shimmerOffset = shimmer
        tvPulse = null
    }

    CompositionLocalProvider(
        LocalShimmerOffset provides shimmerOffset,
        LocalTvLoadingPulse provides tvPulse,
    ) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val gridSpec = gameGridSpec(maxWidth, compact, landscapeLayout, settings, handheldLayout = !tvProfile)
            val placeholderItems = remember(gridSpec.estimatedColumns, storeLayout) {
                List(gridSpec.estimatedColumns * if (storeLayout) 4 else 3) { it }
            }
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = gridSpec.cells,
                contentPadding = gridSpec.contentPadding,
                horizontalArrangement = Arrangement.spacedBy(gridSpec.horizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpec.verticalSpacing),
                userScrollEnabled = false,
            ) {
                if (storeLayout) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StoreStartRailsSkeleton(
                            settings = settings,
                            tvProfile = tvProfile,
                        )
                    }
                }
                gridItems(placeholderItems, key = { it }) {
                    GameCardSkeleton(
                        squareCard = gridSpec.squareCards,
                        thumbnailFavoriteOverlay = shouldShowCatalogFavoriteIcon(settings),
                        showStoreLabels = !artworkOnly && shouldShowGameStoreLabels(
                            tvProfile = tvProfile,
                            enabled = settings.showGameStoreLabels,
                        ),
                        showCardTitles = !artworkOnly && shouldShowCatalogCardTitles(
                            tvProfile = tvProfile,
                            enabled = settings.showCardTitles,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreStartRailsSkeleton(
    settings: AppSettings,
    tvProfile: Boolean,
) {
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            StoreRailSectionSkeleton(
                expressiveUi = settings.expressiveUi,
                tvProfile = tvProfile,
                landscapeLayout = landscapeLayout,
                cardScale = settings.posterSizeScale,
                showFavoriteIcon = shouldShowCatalogFavoriteIcon(settings),
            )
        }
    }
}

@Composable
private fun StoreRailSectionSkeleton(
    expressiveUi: Boolean,
    tvProfile: Boolean,
    landscapeLayout: Boolean,
    cardScale: Float,
    showFavoriteIcon: Boolean,
) {
    val spacing = 10.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonLine(widthFraction = 0.34f, height = 15.dp)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            val baseCardWidth = storeRailCardWidth(tvProfile, landscapeLayout)
            val visibleCount = storeRailVisibleCardCount(
                availableWidthDp = maxWidth.value,
                baseCardWidthDp = baseCardWidth.value,
                spacingDp = spacing.value,
                cardScale = cardScale,
            )
            val fittedCardWidth = ((maxWidth.value - spacing.value * (visibleCount - 1)) / visibleCount)
                .coerceAtLeast(1f)
                .dp
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                repeat(visibleCount) {
                    StoreRailGameCardSkeleton(
                        width = fittedCardWidth,
                        expressiveUi = expressiveUi,
                        portraitCard = !tvProfile,
                        showFavoriteIcon = showFavoriteIcon,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreRailGameCardSkeleton(
    width: Dp,
    expressiveUi: Boolean,
    portraitCard: Boolean,
    showFavoriteIcon: Boolean,
) {
    val shape = RoundedCornerShape(if (expressiveUi) 12.dp else 8.dp)
    Surface(
        modifier = Modifier
            .width(width)
            .aspectRatio(if (portraitCard) GAME_BOX_ART_ASPECT_RATIO else 1f)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
        shape = shape,
        color = Color.Black,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            LoadingShimmer(Modifier.fillMaxSize())
            if (showFavoriteIcon) {
                SkeletonCircle(
                    size = 34.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
        }
    }
}

/** Mirrors [GameCard]'s layout exactly, so nothing shifts when real content replaces it. */
@Composable
private fun GameCardSkeleton(
    squareCard: Boolean,
    thumbnailFavoriteOverlay: Boolean,
    showStoreLabels: Boolean,
    showCardTitles: Boolean,
) {
    val cardShape = RoundedCornerShape(OpenNowRadius.md)
    Column(Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (squareCard) Modifier.aspectRatio(1f)
                    else Modifier.aspectRatio(GAME_BOX_ART_ASPECT_RATIO),
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
            shape = cardShape,
        ) {
            Box(Modifier.fillMaxSize()) {
                LoadingShimmer(Modifier.fillMaxSize())
                if (thumbnailFavoriteOverlay) {
                    SkeletonCircle(
                        size = 34.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    )
                }
            }
        }
        if (showCardTitles || showStoreLabels) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = OpenNowSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showCardTitles) {
                    SkeletonLine(widthFraction = 0.86f)
                    SkeletonLine(widthFraction = 0.52f)
                }
                if (showStoreLabels) {
                    SkeletonLine(widthFraction = 0.4f)
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: Dp = 9.dp) {
    LoadingShimmer(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(999.dp)),
    )
}

@Composable
private fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    LoadingShimmer(
        modifier
            .size(size)
            .clip(CircleShape),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToRefreshContainer(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showRefreshIndicator: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) {
            content()
        }
        return
    }
    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pullRefreshState,
        indicator = {
            if (showRefreshIndicator) {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
    ) {
        content()
    }
}

@Composable
private fun GameGrid(
    games: List<GameInfo>,
    favoriteIds: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    topFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    emptyContent: (@Composable () -> Unit)? = null,
) {
    if (games.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (emptyContent != null) {
                emptyContent()
            } else {
                Text(stringResource(R.string.no_games_loaded), color = TextMuted)
            }
        }
        return
    }
    val scale = settings.posterSizeScale.coerceIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE)
    val compact = settings.compactGameCards
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val physicalControllerConnected = rememberPhysicalControllerConnected(enabled = landscapeLayout && !tvProfile)
    val controllerActionMode = landscapeLayout && !tvProfile && physicalControllerConnected
    val artworkOnly = shouldUseArtworkOnlyCatalogCards(tvProfile, controllerActionMode)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gridSpec = gameGridSpec(maxWidth, compact, landscapeLayout, settings, handheldLayout = !tvProfile)
        val firstRowGameIds = remember(games, gridSpec.estimatedColumns) {
            games.take(gridSpec.estimatedColumns).mapTo(mutableSetOf()) { it.id }
        }
        CatalogFocusScope {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                state = gridState,
                columns = gridSpec.cells,
                contentPadding = gridSpec.contentPadding,
                horizontalArrangement = Arrangement.spacedBy(gridSpec.horizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpec.verticalSpacing),
            ) {
                gridItems(games, key = { it.id }) { game ->
                    GameCard(
                        game = game,
                        favorite = game.id in favoriteIds,
                        tvProfile = tvProfile,
                        expressiveUi = settings.expressiveUi,
                        liveSelectedOutlines = LocalActiveSelectionEnabled.current,
                        showGameStoreLabels = !artworkOnly && shouldShowGameStoreLabels(
                            tvProfile = tvProfile,
                            enabled = settings.showGameStoreLabels,
                        ),
                        showCardTitles = !artworkOnly && shouldShowCatalogCardTitles(
                            tvProfile = tvProfile,
                            enabled = settings.showCardTitles,
                        ),
                        squareCard = gridSpec.squareCards,
                        thumbnailFavoriteOverlay = shouldShowCatalogFavoriteIcon(settings),
                        controllerActionMode = controllerActionMode,
                        upFocusRequester = topFocusRequester.takeIf { game.id in firstRowGameIds },
                        onSelect = onSelect,
                        onFavorite = onFavorite,
                        onPlay = onPlay,
                        onChooseStore = onChooseStore,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreGameGrid(
    games: List<GameInfo>,
    favoriteIds: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    state: OpenNowUiState,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    onSortChange: (String) -> Unit,
    onFilterToggle: (String) -> Unit,
    onClearSearch: () -> Unit,
    onClearFilters: () -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    showToolbar: Boolean = true,
    topFocusRequester: FocusRequester? = null,
    searchActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StoreScrollableControls(state, onSortChange, onFilterToggle, showToolbar = showToolbar)
            if (searchActive) {
                SectionHeader(
                    title = stringResource(R.string.store_results),
                    modifier = Modifier.padding(top = OpenNowSpacing.lg, bottom = OpenNowSpacing.sm),
                )
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val hasSearch = state.catalogSearch.isNotBlank()
                val hasFilters = state.catalogFilterIds.isNotEmpty()
                if (hasSearch || hasFilters) {
                    SearchEmptyState(
                        title = stringResource(R.string.store_empty_search_title),
                        message = when {
                            hasSearch && hasFilters -> stringResource(R.string.store_empty_search_filters_body)
                            hasSearch -> stringResource(R.string.store_empty_search_body)
                            else -> stringResource(R.string.store_empty_filters_body)
                        },
                        onClearSearch = if (hasSearch) onClearSearch else null,
                        onClearFilters = if (hasFilters) onClearFilters else null,
                    )
                } else {
                    Text(stringResource(R.string.no_games_loaded), color = TextMuted)
                }
            }
        }
        return
    }
    val scale = settings.posterSizeScale.coerceIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE)
    val compact = settings.compactGameCards
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val physicalControllerConnected = rememberPhysicalControllerConnected(enabled = landscapeLayout && !tvProfile)
    val controllerActionMode = landscapeLayout && !tvProfile && physicalControllerConnected
    val artworkOnly = shouldUseArtworkOnlyCatalogCards(tvProfile, controllerActionMode)
    val showControlsHeader = showToolbar || state.catalogFilterIds.isNotEmpty() || !state.error.isNullOrBlank()
    val showDiscoverySections = shouldShowStoreDiscoverySections(searchActive)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gridSpec = gameGridSpec(maxWidth, compact, landscapeLayout, settings, handheldLayout = !tvProfile)
        val firstRowGameIds = remember(games, gridSpec.estimatedColumns) {
            games.take(gridSpec.estimatedColumns).mapTo(mutableSetOf()) { it.id }
        }
        CatalogFocusScope {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                state = gridState,
                columns = gridSpec.cells,
                contentPadding = gridSpec.contentPadding,
                horizontalArrangement = Arrangement.spacedBy(gridSpec.horizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpec.verticalSpacing),
            ) {
                if (showControlsHeader) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StoreScrollableControls(state, onSortChange, onFilterToggle, showToolbar = showToolbar)
                    }
                }
                if (showDiscoverySections) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StoreStartRails(
                            games = games,
                            libraryGames = state.libraryGames,
                            favoriteIds = favoriteIds,
                            queuedGameKeys = state.queuedGameKeys,
                            settings = settings,
                            tvProfile = tvProfile,
                            controllerActionMode = controllerActionMode,
                            topFocusRequester = topFocusRequester,
                            onSelect = onSelect,
                            onFavorite = onFavorite,
                            onPlay = onPlay,
                            onChooseStore = onChooseStore,
                        )
                    }
                }
                if (games.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = stringResource(
                                if (showDiscoverySections) R.string.store_recommendations else R.string.store_results,
                            ),
                            modifier = Modifier.padding(top = OpenNowSpacing.lg, bottom = OpenNowSpacing.sm),
                        )
                    }
                }
                gridItems(games, key = { it.id }) { game ->
                    GameCard(
                        game = game,
                        favorite = game.id in favoriteIds,
                        tvProfile = tvProfile,
                        expressiveUi = settings.expressiveUi,
                        liveSelectedOutlines = LocalActiveSelectionEnabled.current,
                        showGameStoreLabels = !artworkOnly && shouldShowGameStoreLabels(
                            tvProfile = tvProfile,
                            enabled = settings.showGameStoreLabels,
                        ),
                        showCardTitles = !artworkOnly && shouldShowCatalogCardTitles(
                            tvProfile = tvProfile,
                            enabled = settings.showCardTitles,
                        ),
                        squareCard = gridSpec.squareCards,
                        thumbnailFavoriteOverlay = shouldShowCatalogFavoriteIcon(settings),
                        controllerActionMode = controllerActionMode,
                        upFocusRequester = topFocusRequester.takeIf {
                            !showDiscoverySections && game.id in firstRowGameIds
                        },
                        onSelect = onSelect,
                        onFavorite = onFavorite,
                        onPlay = onPlay,
                        onChooseStore = onChooseStore,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreStartRails(
    games: List<GameInfo>,
    libraryGames: List<GameInfo>,
    favoriteIds: List<String>,
    queuedGameKeys: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    controllerActionMode: Boolean,
    topFocusRequester: FocusRequester?,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    val startRails = remember(games, libraryGames, favoriteIds, queuedGameKeys) {
        storeStartRailGroups(games, libraryGames, favoriteIds, queuedGameKeys)
    }
    val featured = remember(games, startRails) {
        comingNextStoreGames(games = games, excludedGames = startRails.allGames)
            .take(HERO_CAROUSEL_PAGE_LIMIT)
    }
    if (startRails.isEmpty && featured.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.lg),
    ) {
        // The hero leads, then the rails — the catalog opens on one thing worth looking at rather
        // than on three equally-weighted horizontal strips.
        if (featured.isNotEmpty()) {
            StoreComingNextCarousel(
                title = stringResource(R.string.store_coming_next),
                games = featured,
                favoriteIds = favoriteIds,
                settings = settings,
                tvProfile = tvProfile,
                controllerActionMode = controllerActionMode,
                upFocusRequester = topFocusRequester,
                onSelect = onSelect,
                onFavorite = onFavorite,
                onPlay = onPlay,
                onChooseStore = onChooseStore,
            )
        }
        StoreStartRail(
            R.string.store_continue_playing,
            startRails.continuePlaying,
            favoriteIds,
            settings,
            tvProfile,
            controllerActionMode,
            topFocusRequester,
            onSelect,
            onFavorite,
            onPlay,
            onChooseStore,
        )
        StoreStartRail(
            R.string.store_in_queue,
            startRails.inQueue,
            favoriteIds,
            settings,
            tvProfile,
            controllerActionMode,
            topFocusRequester.takeIf { featured.isEmpty() && startRails.continuePlaying.isEmpty() },
            onSelect,
            onFavorite,
            onPlay,
            onChooseStore,
        )
        StoreStartRail(
            R.string.store_favorites,
            startRails.favorites,
            favoriteIds,
            settings,
            tvProfile,
            controllerActionMode,
            topFocusRequester.takeIf {
                featured.isEmpty() && startRails.continuePlaying.isEmpty() && startRails.inQueue.isEmpty()
            },
            onSelect,
            onFavorite,
            onPlay,
            onChooseStore,
        )
    }
}

internal fun shouldShowStoreDiscoverySections(searchActive: Boolean): Boolean = !searchActive

internal fun shouldHideStoreChromeOnScroll(
    hideChromeWhenScrolled: Boolean,
    scrolledAwayFromTop: Boolean,
    physicalControllerConnected: Boolean,
): Boolean = hideChromeWhenScrolled && scrolledAwayFromTop && !physicalControllerConnected

/** Small wrapper so the three start rails don't repeat an eleven-argument call three times. */
@Composable
private fun StoreStartRail(
    @StringRes titleRes: Int,
    games: List<GameInfo>,
    favoriteIds: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    controllerActionMode: Boolean,
    upFocusRequester: FocusRequester?,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    if (games.isEmpty()) return
    StoreRailSection(
        title = stringResource(titleRes),
        games = games,
        favoriteIds = favoriteIds,
        settings = settings,
        tvProfile = tvProfile,
        controllerActionMode = controllerActionMode,
        upFocusRequester = upFocusRequester,
        onSelect = onSelect,
        onFavorite = onFavorite,
        onPlay = onPlay,
        onChooseStore = onChooseStore,
    )
}

/**
 * The one heading treatment used by every catalog section — rails, the hero, and the
 * recommendations grid — so a section title looks the same wherever it appears. Previously each
 * of those sites styled its own `Text` and they had drifted apart.
 */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreComingNextCarousel(
    title: String,
    games: List<GameInfo>,
    favoriteIds: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    controllerActionMode: Boolean,
    upFocusRequester: FocusRequester?,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    if (games.isEmpty()) return
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var page by remember(games) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val enhancedControllerFocus = shouldShowEnhancedControllerFocus(
        focused = focused,
        tvProfile = tvProfile,
        controllerActionMode = controllerActionMode,
    )
    val selectedGameId = LocalSelectedCatalogGameId.current
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(games, page, focused, reduceMotion) {
        // Never auto-advance under the reader's hands: not while focused, and not at all when the
        // user has asked for reduced motion.
        if (games.size > 1 && !focused && !reduceMotion) {
            delay(HERO_CAROUSEL_ADVANCE_MS)
            page = (page + 1) % games.size
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.md),
    ) {
        SectionHeader(
            title = title,
            subtitle = stringResource(R.string.store_coming_next_subtitle),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                games.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .width(if (index == page) 22.dp else 7.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(if (index == page) MaterialTheme.colorScheme.primary else TextMuted.copy(alpha = 0.32f)),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                fadeIn(tween(if (reduceMotion) 0 else OpenNowMotion.DurationStandard)) togetherWith
                    fadeOut(tween(if (reduceMotion) 0 else OpenNowMotion.DurationFast))
            },
            label = "coming-next-carousel",
        ) { targetPage ->
            val featured = games[targetPage.coerceIn(games.indices)]
            val selected = featured.id == selectedGameId
            val selectedOutline = shouldShowActiveSelectionOutline(selected, LocalActiveSelectionEnabled.current)
            val shape = RoundedCornerShape(if (settings.expressiveUi) 24.dp else 16.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    // Aspect ratio rather than a fixed height, so the hero scales with the screen
                    // instead of dominating a small phone and looking stunted on a tablet.
                    .aspectRatio(heroAspectRatio(tvProfile, landscape)),
            ) {
                Surface(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            upFocusRequester?.let { requester ->
                                Modifier.focusProperties { up = requester }
                            } ?: Modifier,
                        )
                        .onFocusChanged { focused = it.isFocused || it.hasFocus }
                        .border(
                            width = if (focused) 3.dp else 2.dp,
                            color = when {
                                enhancedControllerFocus -> Color.Transparent
                                focused -> Color.White
                                selected -> LocalActiveSelectionColor.current
                                else -> Color.White.copy(alpha = 0.9f)
                            },
                            shape = shape,
                        )
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                            when {
                                controllerActionMode && event.key == Key.DirectionLeft && games.size > 1 -> {
                                    page = (page - 1 + games.size) % games.size
                                    true
                                }
                                controllerActionMode && event.key == Key.DirectionRight && games.size > 1 -> {
                                    page = (page + 1) % games.size
                                    true
                                }
                                !tvProfile && controllerActionMode && handleCatalogControllerAction(
                                    event = event,
                                    onFavorite = { onFavorite(featured.id) },
                                    onPlay = { onPlay(featured) },
                                ) -> true
                                isTvActivateKey(event) -> {
                                    onSelect(featured)
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable()
                        .combinedClickable(
                            onClick = { onSelect(featured) },
                            onLongClick = { onChooseStore(featured) },
                            onLongClickLabel = stringResource(R.string.store_selector_play_long_press),
                        ),
                    shape = shape,
                    color = Panel,
                    tonalElevation = if (focused) 5.dp else 0.dp,
                    shadowElevation = if (focused) 9.dp else 1.dp,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        UrlImage(gameHeroImageUrl(context, featured), Modifier.fillMaxSize())
                        // Horizontal scrim carries the title block; the vertical one settles the art
                        // into the surface below so the hero reads as part of the page, not a sticker.
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.3f), Color.Transparent),
                                    ),
                                ),
                        )
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.45f to Color.Transparent,
                                        1f to Background.copy(alpha = 0.85f),
                                    ),
                                ),
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(0.66f)
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                featured.title,
                                color = Color.White,
                                style = when {
                                    // Across a room the hero title is the only thing readable at a
                                    // glance, so TV gets the display scale.
                                    tvProfile -> MaterialTheme.typography.displaySmall
                                    landscape -> MaterialTheme.typography.headlineSmall
                                    else -> MaterialTheme.typography.headlineMedium
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(featured.publisherName, displayStoresForGame(featured).takeIf { it.isNotBlank() })
                                    .distinct()
                                    .joinToString("  •  "),
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (shouldShowCatalogFavoriteIcon(settings)) {
                            FavoriteIconButton(
                                favorite = featured.id in favoriteIds,
                                onClick = { onFavorite(featured.id) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                                size = 38.dp,
                            )
                        }
                    }
                }
                ControllerFocusFrame(
                    visible = enhancedControllerFocus || selectedOutline || (focused && LocalAbsoluteCinemaEffects.current),
                    cornerRadius = if (settings.expressiveUi) 24.dp else 16.dp,
                    tint = if (selectedOutline || LocalAbsoluteCinemaEffects.current) LocalActiveSelectionColor.current else Color.White,
                    secondaryTint = if (selectedOutline || LocalAbsoluteCinemaEffects.current) LocalActiveSelectionSecondaryColor.current else Color.White,
                )
            }
        }
    }
}

@Composable
private fun StoreRailSection(
    title: String,
    games: List<GameInfo>,
    favoriteIds: List<String>,
    settings: AppSettings,
    tvProfile: Boolean,
    controllerActionMode: Boolean,
    upFocusRequester: FocusRequester?,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    val landscapeLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm)) {
        SectionHeader(title = title)
        // The row breaks out of the grid's edge padding and re-applies it as content padding, so
        // cards scroll all the way under the screen edge instead of stopping short of it. The
        // header stays aligned to the content because the bleed is only on the row.
        BoxWithConstraints(Modifier.horizontalBleed(OpenNowSpacing.ScreenEdge)) {
            val spacing = OpenNowSpacing.md
            val baseCardWidth = storeRailCardWidth(tvProfile, landscapeLayout)
            val contentInset = OpenNowSpacing.ScreenEdge
            val visibleCount = storeRailVisibleCardCount(
                availableWidthDp = maxWidth.value - contentInset.value * 2f,
                baseCardWidthDp = baseCardWidth.value,
                spacingDp = spacing.value,
                cardScale = settings.posterSizeScale,
            )
            // Leave a sliver of the next card showing — the standard cue that a row keeps going.
            val cardWidth = ((maxWidth.value - contentInset.value * 2f - spacing.value * visibleCount) /
                (visibleCount + PEEK_CARD_FRACTION))
                .coerceAtLeast(1f)
                .dp
            CatalogFocusScope {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    contentPadding = PaddingValues(horizontal = contentInset),
                ) {
                    items(games, key = { storeRailGameKey(it) }) { game ->
                        StoreRailGameCard(
                            game = game,
                            favorite = game.id in favoriteIds,
                            tvProfile = tvProfile,
                            expressiveUi = settings.expressiveUi,
                            liveSelectedOutlines = LocalActiveSelectionEnabled.current,
                            showFavoriteIcon = shouldShowCatalogFavoriteIcon(settings),
                            width = cardWidth,
                            controllerActionMode = controllerActionMode,
                            upFocusRequester = upFocusRequester,
                            onSelect = onSelect,
                            onFavorite = onFavorite,
                            onPlay = onPlay,
                            onChooseStore = onChooseStore,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreRailGameCard(
    game: GameInfo,
    favorite: Boolean,
    tvProfile: Boolean,
    expressiveUi: Boolean,
    liveSelectedOutlines: Boolean,
    showFavoriteIcon: Boolean,
    width: Dp,
    controllerActionMode: Boolean,
    upFocusRequester: FocusRequester?,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(if (expressiveUi) OpenNowRadius.md else OpenNowRadius.sm)
    val actionButtonSize = 34.dp
    val enhancedControllerFocus = shouldShowEnhancedControllerFocus(
        focused = focused,
        tvProfile = tvProfile,
        controllerActionMode = controllerActionMode,
    )
    val selected = LocalSelectedCatalogGameId.current == game.id
    val selectedOutline = shouldShowActiveSelectionOutline(selected, liveSelectedOutlines)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val reduceMotion = LocalReduceMotion.current
    val cardScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.965f
            focused || hovered -> when {
                tvProfile -> 1.08f
                controllerActionMode -> 1f
                else -> 1.035f
            }
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else OpenNowMotion.DurationStandard,
            easing = OpenNowMotion.EasingStandard,
        ),
        label = "rail-card-scale",
    )
    val dimAlpha = rememberCatalogCardAlpha(focused = focused, tvProfile = tvProfile)
    Box(
        Modifier
            .width(width)
            .padding(vertical = if (tvProfile) CATALOG_CONTROLLER_FOCUS_INSET else 0.dp)
            .aspectRatio(if (tvProfile) 1f else GAME_BOX_ART_ASPECT_RATIO)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                alpha = dimAlpha
            }
            .semantics(mergeDescendants = true) {
                contentDescription = game.title
                role = Role.Button
            },
    ) {
        Surface(
            modifier = Modifier
                .matchParentSize()
                .then(
                    upFocusRequester?.let { requester ->
                        Modifier.focusProperties { up = requester }
                    } ?: Modifier,
                )
                .onFocusChanged { focused = it.isFocused || it.hasFocus }
                .border(
                    width = if (focused) 3.dp else 2.dp,
                    color = when {
                        enhancedControllerFocus -> Color.Transparent
                        focused -> Color.White
                        selected -> LocalActiveSelectionColor.current
                        else -> Color.White.copy(alpha = 0.9f)
                    },
                    shape = shape,
                )
                .onPreviewKeyEvent { event ->
                    when {
                        !tvProfile && controllerActionMode && handleCatalogControllerAction(
                            event = event,
                            onFavorite = { onFavorite(game.id) },
                            onPlay = { onPlay(game) },
                        ) -> true
                        isTvActivateKey(event) -> {
                            onSelect(game)
                            true
                        }
                        else -> handleDpadFocusMove(event, focusManager)
                    }
                }
                .focusable(interactionSource = interaction)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { onSelect(game) },
                    onLongClick = { onChooseStore(game) },
                    onLongClickLabel = stringResource(R.string.store_selector_play_long_press),
                ),
            shape = shape,
            color = OpenNowPalette.ImagePlaceholder,
            tonalElevation = if (focused) 4.dp else 0.dp,
            shadowElevation = if (focused) 8.dp else 1.dp,
        ) {
            Box(Modifier.fillMaxSize().clip(shape)) {
                UrlImage(
                    catalogCardImageUrl(game, tvProfile),
                    Modifier.fillMaxSize(),
                    // Crop everywhere — see the note in GameCard.
                    contentScale = ContentScale.Crop,
                )
                if (shouldOverlayCatalogCardTitle(tvProfile)) {
                    GameCardTitleOverlay(game.title)
                }
                if (showFavoriteIcon) {
                    FavoriteIconButton(
                        favorite = favorite,
                        onClick = { onFavorite(game.id) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        size = actionButtonSize,
                    )
                }
            }
        }
        ControllerFocusFrame(
            visible = enhancedControllerFocus || selectedOutline || ((focused || hovered) && LocalAbsoluteCinemaEffects.current),
            cornerRadius = if (expressiveUi) 12.dp else 8.dp,
            tint = if (selectedOutline || LocalAbsoluteCinemaEffects.current) LocalActiveSelectionColor.current else Color.White,
            secondaryTint = if (selectedOutline || LocalAbsoluteCinemaEffects.current) LocalActiveSelectionSecondaryColor.current else Color.White,
        )
    }
}

/**
 * The three rails that open the store, kept distinct.
 *
 * These used to be flattened into one "Jump back in" rail of `queued + favorites + recent + owned`
 * capped at 14 items — which meant genuinely recently-played games sat third in priority and were
 * routinely pushed off-screen by favourites, and the tail was padded with owned games the user had
 * never launched. Owned games are the Library tab's job, so they are dropped here entirely.
 */
internal data class StoreStartRailGroups(
    val continuePlaying: List<GameInfo>,
    val inQueue: List<GameInfo>,
    val favorites: List<GameInfo>,
) {
    val allGames: List<GameInfo> get() = continuePlaying + inQueue + favorites
    val isEmpty: Boolean get() = continuePlaying.isEmpty() && inQueue.isEmpty() && favorites.isEmpty()
}

internal fun storeStartRailGroups(
    games: List<GameInfo>,
    libraryGames: List<GameInfo>,
    favoriteIds: List<String>,
    queuedGameKeys: List<String>,
): StoreStartRailGroups {
    val favoriteSet = favoriteIds.toSet()
    val combined = distinctStoreGames(libraryGames + games)
    val byKey = combined.associateBy(::storeRailGameKey)

    val continuePlaying = combined
        .filter { it.recentPlaySortKey() != null }
        .sortedByDescending { it.recentPlaySortKey() }
        .take(CONTINUE_PLAYING_RAIL_LIMIT)
    val continueKeys = continuePlaying.map(::storeRailGameKey).toSet()

    val inQueue = queuedGameKeys
        .mapNotNull(byKey::get)
        .filterNot { storeRailGameKey(it) in continueKeys }
        .take(STORE_RAIL_GAME_LIMIT)
    val shownKeys = continueKeys + inQueue.map(::storeRailGameKey)

    // Favourites already visible above would just be a second sighting of the same card.
    val favorites = combined
        .filter { it.id in favoriteSet }
        .filterNot { storeRailGameKey(it) in shownKeys }
        .take(STORE_RAIL_GAME_LIMIT)

    return StoreStartRailGroups(continuePlaying, inQueue, favorites)
}

internal fun comingNextStoreGames(
    games: List<GameInfo>,
    excludedGames: List<GameInfo>,
): List<GameInfo> {
    val excludedKeys = excludedGames.map(::storeRailGameKey).toSet()
    return distinctStoreGames(games)
        .filterNot { storeRailGameKey(it) in excludedKeys }
        .filter(GameInfo::isNewOrUpdatedCatalogSection)
        .take(STORE_RAIL_GAME_LIMIT)
}

private fun GameInfo.isNewOrUpdatedCatalogSection(): Boolean {
    val section = catalogSectionTitle?.lowercase(Locale.US)?.trim().orEmpty()
    return section.contains("new") ||
        section.contains("recent") ||
        section.contains("updated") ||
        section.contains("just added")
}

private fun GameInfo.recentPlaySortKey(): String? =
    listOfNotNull(
        lastPlayed?.takeIf { it.isNotBlank() },
        variants.mapNotNull { it.lastPlayedDate?.takeIf(String::isNotBlank) }.maxOrNull(),
    ).maxOrNull()

private fun distinctStoreGames(games: List<GameInfo>): List<GameInfo> {
    val byKey = linkedMapOf<String, GameInfo>()
    games.forEach { game ->
        byKey.putIfAbsent(storeRailGameKey(game), game)
    }
    return byKey.values.toList()
}

private fun storeRailGameKey(game: GameInfo): String =
    gameTrackingKey(game)

private const val STORE_RAIL_GAME_LIMIT = 14

/** Recently-played is a short list by nature — padding it out defeats the point of the rail. */
private const val CONTINUE_PLAYING_RAIL_LIMIT = 12

/** Five hero pages, five indicator pills. Fourteen was a rash of dots. */
private const val HERO_CAROUSEL_PAGE_LIMIT = 5

private const val HERO_CAROUSEL_ADVANCE_MS = 6_000L

/**
 * Wider on surfaces that are already wide, so the hero stays a banner rather than becoming a wall.
 */
private fun heroAspectRatio(tvProfile: Boolean, landscape: Boolean): Float = when {
    tvProfile -> 16f / 6f
    landscape -> 16f / 5f
    else -> 16f / 7f
}
private const val GAME_BOX_ART_ASPECT_RATIO = 628f / 888f

internal fun shouldInitiallyFocusGameDetailsPlay(tvProfile: Boolean): Boolean = tvProfile

private data class GameGridSpec(
    val cells: GridCells,
    /** Only used to size skeleton placeholder runs; the real column count is the grid's to decide. */
    val estimatedColumns: Int,
    val horizontalSpacing: Dp,
    val verticalSpacing: Dp,
    val contentPadding: PaddingValues,
    val squareCards: Boolean,
)

/** How much of the next card stays visible past the last fully-visible one. */
private const val PEEK_CARD_FRACTION = 0.28f

/**
 * Number of catalog cards currently holding focus inside the surrounding grid or rail. A count
 * rather than a flag so that handing focus from one card to its neighbour — where the old card
 * reports losing focus in the same frame the new one reports gaining it — never dips to "nothing
 * is focused" and flickers the dim.
 */
private val LocalCatalogFocusCount = compositionLocalOf<MutableIntState?> { null }

/**
 * Scopes the focus count to one grid or one rail, so focusing a card in the grid doesn't dim the
 * rails above it.
 */
@Composable
private fun CatalogFocusScope(content: @Composable () -> Unit) {
    val count = remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalCatalogFocusCount provides count, content = content)
}

/** Alpha applied to unfocused cards while a sibling is focused. TV only. */
private const val TV_UNFOCUSED_CARD_ALPHA = 0.55f

/**
 * Registers this card's focus in the surrounding [CatalogFocusScope] and returns the alpha it
 * should draw at. Dimming the neighbours is what makes the focus cursor readable from across a
 * room — on TV a border and a scale change alone still leave a wall of equally bright artwork.
 */
@Composable
private fun rememberCatalogCardAlpha(focused: Boolean, tvProfile: Boolean): Float {
    val count = LocalCatalogFocusCount.current
    DisposableEffect(focused, count) {
        if (focused) count?.intValue = (count?.intValue ?: 0) + 1
        onDispose {
            if (focused) count?.intValue = ((count?.intValue ?: 1) - 1).coerceAtLeast(0)
        }
    }
    if (!tvProfile) return 1f
    val anyFocused = (count?.intValue ?: 0) > 0
    val target = if (anyFocused && !focused) TV_UNFOCUSED_CARD_ALPHA else 1f
    val reduceMotion = LocalReduceMotion.current
    val alpha by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else OpenNowMotion.DurationStandard,
            easing = OpenNowMotion.EasingStandard,
        ),
        label = "catalog-card-dim",
    )
    return alpha
}

/**
 * Lets a child extend [bleed] past its parent's bounds on both sides without reporting the extra
 * width upward — the standard way to make a horizontally scrolling row run edge to edge inside a
 * padded container.
 */
private fun Modifier.horizontalBleed(bleed: Dp): Modifier = this.layout { measurable, constraints ->
    val extra = bleed.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    val reportedWidth = (placeable.width - extra).coerceAtLeast(0)
    layout(reportedWidth, placeable.height) {
        placeable.place(-bleed.roundToPx(), 0)
    }
}

private fun storeRailCardWidth(tvProfile: Boolean, landscapeLayout: Boolean): Dp =
    when {
        tvProfile -> 158.dp
        landscapeLayout -> 146.dp
        else -> 142.dp
    }

/**
 * Cell widths the grid aims for at `posterSizeScale == 1`. These are minimums fed to
 * [GridCells.Adaptive], not column counts: the grid fits as many as will hold and shares the
 * remainder out evenly.
 *
 * The previous implementation picked a column count from a table of hardcoded dp breakpoints, so a
 * 360dp budget phone and a 411dp Pixel both got exactly 3 columns — cards ended up 15% wider on one
 * than the other, and gutters never adapted at all. Foldables, tablets, DeX and split-screen were
 * all served by the same four buckets.
 */
private val GRID_CELL_WIDTH_PORTRAIT = 96.dp
private val GRID_CELL_WIDTH_LANDSCAPE = 112.dp
private val GRID_CELL_WIDTH_TV = 158.dp

/** Compact mode shrinks the target cell rather than switching to a separate size table. */
private const val COMPACT_CELL_WIDTH_FACTOR = 0.88f
private val CATALOG_CONTROLLER_FOCUS_INSET = 8.dp

private fun gameGridSpec(
    maxWidth: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    landscapeLayout: Boolean,
    settings: AppSettings,
    handheldLayout: Boolean,
): GameGridSpec {
    val horizontalSpacing = if (compact) OpenNowSpacing.sm else OpenNowSpacing.GridGutter
    val verticalSpacing = if (compact) OpenNowSpacing.md else OpenNowSpacing.GridRowGap
    val horizontalPadding = OpenNowSpacing.ScreenEdge

    val baseCellWidth = when {
        !handheldLayout -> GRID_CELL_WIDTH_TV
        landscapeLayout -> GRID_CELL_WIDTH_LANDSCAPE
        else -> GRID_CELL_WIDTH_PORTRAIT
    }
    // posterSizeScale is persisted user state and keeps its existing meaning: larger scale means
    // larger cards, which now falls out of a wider target cell instead of a divided column count.
    val scale = settings.posterSizeScale.coerceIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE)
    val cellWidth = (baseCellWidth * scale * if (compact) COMPACT_CELL_WIDTH_FACTOR else 1f)
        .coerceIn(64.dp, 240.dp)

    val available = (maxWidth - horizontalPadding * 2).coerceAtLeast(cellWidth)
    val estimatedColumns = ((available + horizontalSpacing) / (cellWidth + horizontalSpacing))
        .toInt()
        .coerceIn(1, 12)

    return GameGridSpec(
        cells = GridCells.Adaptive(minSize = cellWidth),
        estimatedColumns = estimatedColumns,
        horizontalSpacing = horizontalSpacing,
        verticalSpacing = verticalSpacing,
        contentPadding = PaddingValues(
            start = horizontalPadding,
            top = OpenNowSpacing.md,
            end = horizontalPadding,
            bottom = AppScrollEndSpacing,
        ),
        // TV grid cards match the TV rail cards, which have always been square — this is the shape
        // NVIDIA's tvCardImageUrl assets are cut for.
        squareCards = !handheldLayout,
    )
}

internal fun appContentEdgePaddingDp(
    settings: AppSettings,
    inStream: Boolean,
    tvProfile: Boolean,
): Float = if (inStream || !tvProfile) 0f else settings.tvSafeAreaPaddingDp.coerceIn(0f, 120f)

internal fun storeRailVisibleCardCount(
    availableWidthDp: Float,
    baseCardWidthDp: Float,
    spacingDp: Float,
    cardScale: Float,
): Int {
    val scaledCardWidth = baseCardWidthDp * cardScale.coerceIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE)
    return ((availableWidthDp + spacingDp) / (scaledCardWidth + spacingDp))
        .toInt()
        .coerceAtLeast(1)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(
    game: GameInfo,
    favorite: Boolean,
    tvProfile: Boolean,
    expressiveUi: Boolean,
    liveSelectedOutlines: Boolean,
    showGameStoreLabels: Boolean,
    showCardTitles: Boolean,
    squareCard: Boolean,
    thumbnailFavoriteOverlay: Boolean,
    controllerActionMode: Boolean,
    upFocusRequester: FocusRequester? = null,
    onSelect: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val cardShape = RoundedCornerShape(if (expressiveUi) OpenNowRadius.md else OpenNowRadius.sm)
    val handheldPosterCard = !tvProfile
    val launcherTile = handheldPosterCard && thumbnailFavoriteOverlay
    val overlayActionSize = if (launcherTile) 34.dp else 44.dp
    val overlayActionPadding = if (launcherTile) 6.dp else 8.dp
    val enhancedControllerFocus = shouldShowEnhancedControllerFocus(
        focused = focused,
        tvProfile = tvProfile,
        controllerActionMode = controllerActionMode,
    )
    val selected = LocalSelectedCatalogGameId.current == game.id
    val selectedOutline = shouldShowActiveSelectionOutline(selected, liveSelectedOutlines)
    // Touch-handheld captions live outside the poster, so the artwork stays visually clean.
    val showCaption = handheldPosterCard && (showCardTitles || showGameStoreLabels)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val reduceMotion = LocalReduceMotion.current
    val cardScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.965f
            // A bigger lift on TV: from three metres a border change is nearly invisible, but a
            // card growing out of the grid is unmistakable.
            focused || hovered -> when {
                tvProfile -> 1.08f
                controllerActionMode -> 1f
                else -> 1.035f
            }
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else OpenNowMotion.DurationStandard,
            easing = OpenNowMotion.EasingStandard,
        ),
        label = "game-card-scale",
    )
    val dimAlpha = rememberCatalogCardAlpha(focused = focused, tvProfile = tvProfile)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = if (tvProfile) CATALOG_CONTROLLER_FOCUS_INSET else 0.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                alpha = dimAlpha
            }
            // One merged node per card. Without this TalkBack reads nothing at all here: UrlImage
            // passes a null contentDescription and phone cards carry no title text of their own.
            .semantics(mergeDescendants = true) {
                contentDescription = game.title
                role = Role.Button
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (squareCard) Modifier.aspectRatio(1f)
                    else Modifier.aspectRatio(GAME_BOX_ART_ASPECT_RATIO),
                ),
        ) {
            Card(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        upFocusRequester?.let { requester ->
                            Modifier.focusProperties { up = requester }
                        } ?: Modifier,
                    )
                    .onFocusChanged { focused = it.isFocused || it.hasFocus }
                    .border(
                        width = if (focused) 3.dp else 2.dp,
                        color = when {
                            enhancedControllerFocus -> Color.Transparent
                            focused -> Color.White
                            selected -> LocalActiveSelectionColor.current
                            else -> Color.White.copy(alpha = 0.9f)
                        },
                        shape = cardShape,
                    )
                    .onPreviewKeyEvent { event ->
                        when {
                            !tvProfile && controllerActionMode && handleCatalogControllerAction(
                                event = event,
                                onFavorite = { onFavorite(game.id) },
                                onPlay = { onPlay(game) },
                            ) -> true
                            isTvActivateKey(event) -> {
                                onSelect(game)
                                true
                            }
                            else -> handleDpadFocusMove(event, focusManager)
                        }
                    }
                    .focusable(interactionSource = interaction),
                colors = CardDefaults.cardColors(
                    containerColor = if (expressiveUi) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f) else Panel,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (focused) 8.dp else 0.dp),
                shape = cardShape,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(game) },
                            onLongClick = { onChooseStore(game) },
                            onLongClickLabel = stringResource(R.string.store_selector_play_long_press),
                        ),
                ) {
                    UrlImage(
                        catalogCardImageUrl(game, tvProfile),
                        Modifier.fillMaxSize(),
                        // Always Crop. The card is already locked to NVIDIA's box-art ratio, so for
                        // correctly-cut art this is identical to Fit; when the CDN returns something
                        // off-ratio, Fit pillarboxed it against a flat swatch and Crop simply trims.
                        contentScale = ContentScale.Crop,
                    )
                    if (shouldOverlayCatalogCardTitle(tvProfile)) {
                        GameCardTitleOverlay(game.title)
                    }
                    if (thumbnailFavoriteOverlay) {
                        FavoriteIconButton(
                            favorite = favorite,
                            onClick = { onFavorite(game.id) },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(overlayActionPadding),
                            size = overlayActionSize,
                        )
                    }
                }
            }
            ControllerFocusFrame(
                visible = enhancedControllerFocus || selectedOutline || ((focused || hovered) && LocalAbsoluteCinemaEffects.current),
                cornerRadius = if (expressiveUi) OpenNowRadius.md else OpenNowRadius.sm,
                tint = if (selectedOutline || LocalAbsoluteCinemaEffects.current) LocalActiveSelectionColor.current else Color.White,
                secondaryTint = if (selectedOutline || LocalAbsoluteCinemaEffects.current) LocalActiveSelectionSecondaryColor.current else Color.White,
            )
        }
        if (showCaption) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = OpenNowSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (showCardTitles) {
                    Text(
                        game.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        // minLines keeps every row in the grid aligned regardless of title length.
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showGameStoreLabels) {
                    Text(
                        displayStoresForGame(game),
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

internal fun catalogCardImageUrl(game: GameInfo, tvProfile: Boolean): String? {
    val source = if (tvProfile) {
        game.tvCardImageUrl?.takeIf { it.isNotBlank() }
            ?: game.imageUrl?.takeIf { it.isNotBlank() }
    } else {
        game.imageUrl
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { !it.contains("img.nvidiagrid.net") || it.contains("/GAME_BOX_ART_") }
    } ?: return null
    return if (tvProfile) optimizedNvidiaImageUrl(source, 272) else source
}

@Suppress("UNUSED_PARAMETER")
internal fun shouldOverlayCatalogCardTitle(tvProfile: Boolean): Boolean = false

internal fun shouldUseArtworkOnlyCatalogCards(tvProfile: Boolean, controllerActionMode: Boolean): Boolean =
    tvProfile || controllerActionMode

internal fun shouldShowCatalogFavoriteIcon(settings: AppSettings): Boolean =
    settings.showFavoriteIconOnGameCards

internal fun shouldShowGameStoreLabels(tvProfile: Boolean, enabled: Boolean): Boolean =
    enabled && !tvProfile

/** Titles may be captioned on touch handhelds; controller-first layouts suppress them upstream. */
internal fun shouldShowCatalogCardTitles(tvProfile: Boolean, enabled: Boolean): Boolean =
    enabled && !tvProfile

@Composable
private fun GameCardTitleOverlay(title: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GameCardOverlayGradient),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

private fun handleCatalogControllerAction(
    event: androidx.compose.ui.input.key.KeyEvent,
    onFavorite: () -> Unit,
    onPlay: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyUp) return false
    return when (event.key) {
        Key.ButtonX -> {
            onFavorite()
            true
        }
        Key.ButtonY -> {
            onPlay()
            true
        }
        else -> false
    }
}

@Composable
internal fun ControllerCatalogRailActionHints(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 3.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.8f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ControllerCatalogActionHint(
                button = "X",
                label = stringResource(R.string.action_save),
                buttonColor = Color(0xff4aa3ff),
            )
            ControllerCatalogActionHint(
                button = "Y",
                label = stringResource(R.string.action_play),
                buttonColor = Color(0xffffcf40),
            )
        }
    }
}

@Composable
private fun ControllerCatalogActionHint(
    button: String,
    label: String,
    buttonColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            modifier = Modifier.size(18.dp),
            shape = CircleShape,
            color = buttonColor,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    button,
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun launcherBadgeForStoreKey(storeKey: String?): LauncherBadge =
    when (storeKey) {
        "STEAM" -> LauncherBadge(R.drawable.ic_store_steam, "Steam", Color(0xff17324d))
        "EPIC", "EGS", "EPIC_GAMES_STORE" -> LauncherBadge(R.drawable.ic_store_epic, "Epic", Color(0xff111111))
        "HOYO", "HOYOVERSE", "HOYOPLAY", "HOYO_PLAY", "MIHOYO" -> LauncherBadge(R.drawable.ic_store_hoyo, "HoYo", Color(0xff2b62d9))
        "XBOX", "XBOX_GAME_PASS", "GAME_PASS" -> LauncherBadge(R.drawable.ic_store_xbox, "Xbox", Color(0xff107c10))
        "MICROSOFT", "MICROSOFT_STORE" -> LauncherBadge(R.drawable.ic_store_microsoft, "Microsoft Store", Color(0xff0067b8))
        "UBISOFT", "UBISOFT_CONNECT" -> LauncherBadge(R.drawable.ic_store_ubisoft, "Ubisoft Connect", Color(0xff006efc))
        "EA", "EA_APP", "ORIGIN" -> LauncherBadge(R.drawable.ic_store_ea, "EA app", Color(0xffff4747))
        "GOG", "GOG.COM", "GOG_COM" -> LauncherBadge(R.drawable.ic_store_gog, "GOG", Color(0xff6a35a8))
        "BATTLENET", "BATTLE.NET", "BATTLE_NET", "BLIZZARD" -> LauncherBadge(R.drawable.ic_store_battlenet, "Battle.net", Color(0xff148eff))
        "RIOT", "RIOT_CLIENT", "RIOT_GAMES" -> LauncherBadge(R.drawable.ic_store_riot, "Riot", Color(0xffd13639))
        "ROCKSTAR", "ROCKSTAR_GAMES", "ROCKSTAR_GAMES_LAUNCHER" -> LauncherBadge(R.drawable.ic_store_rockstar, "Rockstar", Color(0xffffc400), Color(0xff111111))
        "NCSOFT", "NC_SOFT", "PURPLE" -> LauncherBadge(R.drawable.ic_tab_store, "NCSOFT", Color(0xffb4822d), Color(0xff111111))
        "GOOGLE_PLAY", "PLAY_STORE", "ANDROID" -> LauncherBadge(R.drawable.ic_store_google_play, "Google Play", Color(0xff0f9d58))
        "AMAZON", "AMAZON_GAMES" -> LauncherBadge(R.drawable.ic_store_amazon, "Amazon Games", Color(0xffff9900), Color(0xff111111))
        else -> LauncherBadge(R.drawable.ic_tab_store, "GeForce NOW", Color.Black.copy(alpha = 0.72f))
    }

private fun displayStoresForGame(game: GameInfo): String {
    val stores = displayStoresForVariants(game.variants).ifEmpty {
        game.availableStores.map(::gameStoreDisplayName)
    }.distinctBy { normalizeGameStore(it) }
    return stores.joinToString(", ").ifBlank { "GeForce NOW" }
}

@Composable
private fun ZortosPlayMark(
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    playColor: Color = ringColor,
) {
    Canvas(modifier) {
        val play = Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.25f)
            lineTo(size.width * 0.35f, size.height * 0.75f)
            lineTo(size.width * 0.75f, size.height * 0.5f)
            close()
        }
        drawPath(play, playColor)
    }
}

@Composable
internal fun AnimatedLaunchOverlay(
    modifier: Modifier = Modifier,
    enterFromTop: Boolean = false,
    content: @Composable () -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn() +
            slideInVertically(initialOffsetY = { if (enterFromTop) -it / 4 else it / 4 }) +
            scaleIn(initialScale = 0.94f),
        exit = fadeOut() +
            slideOutVertically(targetOffsetY = { if (enterFromTop) -it / 4 else it / 4 }) +
            scaleOut(targetScale = 0.94f),
        modifier = modifier,
    ) {
        content()
    }
}

internal suspend fun requestFocusWithRetry(
    focusRequester: FocusRequester,
    initialDelayMs: Long = 80L,
    retryDelayMs: Long = 70L,
    attempts: Int = 4,
): Boolean {
    if (initialDelayMs > 0L) delay(initialDelayMs)
    repeat(attempts.coerceAtLeast(1)) { attempt ->
        if (runCatching { focusRequester.requestFocus() }.getOrDefault(false)) return true
        if (attempt + 1 < attempts) delay(retryDelayMs.coerceAtLeast(0L))
    }
    return false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameDetailsSheet(
    game: GameInfo,
    favorite: Boolean,
    defaultVariantId: String?,
    fullScreen: Boolean,
    safeAreaPadding: Dp,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    connectedTvName: String?,
    onPlayOnTv: (GameInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val gameFocusRequester = remember(game.id) { FocusRequester() }
    val playFocusRequester = remember(game.id) { FocusRequester() }
    LaunchedEffect(game.id, fullScreen) {
        val initialRequester = if (shouldInitiallyFocusGameDetailsPlay(tvProfile = fullScreen)) {
            playFocusRequester
        } else {
            gameFocusRequester
        }
        requestFocusWithRetry(initialRequester)
    }
    BackHandler(onBack = onDismiss)
    // Drag-to-dismiss for the phone sheet. Everyone reaches for this gesture on a bottom sheet and
    // previously nothing happened — there was no handle and no drag response at all. Implemented
    // here rather than by switching to ModalBottomSheet so the sheet keeps its lockedFocusGroup and
    // focus requesters, which the controller and TV navigation depend on.
    val density = LocalDensity.current
    var dragOffset by remember(game.id) { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(density) { SHEET_DISMISS_DRAG_THRESHOLD.toPx() }
    val dragState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .lockedFocusGroup()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .then(
                    if (fullScreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                            .offset { IntOffset(0, dragOffset.roundToInt()) }
                    },
                )
                .clickable(onClick = {}),
            shape = if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = OpenNowRadius.xl, topEnd = OpenNowRadius.xl),
            color = Panel,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!fullScreen) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .draggable(
                                state = dragState,
                                orientation = Orientation.Vertical,
                                onDragStopped = { velocity ->
                                    if (dragOffset > dismissThresholdPx || velocity > SHEET_DISMISS_FLING_VELOCITY) {
                                        onDismiss()
                                    } else {
                                        dragOffset = 0f
                                    }
                                },
                            )
                            .padding(vertical = OpenNowSpacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 34.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(TextMuted.copy(alpha = 0.45f)),
                        )
                    }
                }
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(if (fullScreen) safeAreaPadding else 0.dp),
            ) {
                val aspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
                val landscapeTvLayout = maxWidth >= 720.dp && aspect >= 1.35f
                val phoneLandscapeLayout = landscapeTvLayout && minOf(maxWidth, maxHeight) < PHONE_NAV_RAIL_MAX_SMALLEST_WIDTH
                if (landscapeTvLayout) {
                    GameDetailsLandscapeContent(
                        game = game,
                        favorite = favorite,
                        defaultVariantId = defaultVariantId,
                        onPlay = onPlay,
                        onChooseStore = onChooseStore,
                        onFavorite = onFavorite,
                        connectedTvName = connectedTvName,
                        onPlayOnTv = onPlayOnTv,
                        onDismiss = onDismiss,
                        gameFocusRequester = gameFocusRequester,
                        playFocusRequester = playFocusRequester,
                        shortHeight = maxHeight <= 620.dp,
                        imageActionsOverlay = phoneLandscapeLayout,
                    )
                } else {
                    GameDetailsScrollableContent(
                        game = game,
                        favorite = favorite,
                        defaultVariantId = defaultVariantId,
                        onPlay = onPlay,
                        onChooseStore = onChooseStore,
                        onFavorite = onFavorite,
                        connectedTvName = connectedTvName,
                        onPlayOnTv = onPlayOnTv,
                        onDismiss = onDismiss,
                        gameFocusRequester = gameFocusRequester,
                        playFocusRequester = playFocusRequester,
                    )
                }
            }
            }
        }
    }
}

/** How far the sheet must be dragged down before letting go dismisses it. */
private val SHEET_DISMISS_DRAG_THRESHOLD = 140.dp

/** A fast enough flick dismisses regardless of distance travelled. */
private const val SHEET_DISMISS_FLING_VELOCITY = 1_200f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameDetailsLandscapeContent(
    game: GameInfo,
    favorite: Boolean,
    defaultVariantId: String?,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    connectedTvName: String?,
    onPlayOnTv: (GameInfo) -> Unit,
    onDismiss: () -> Unit,
    gameFocusRequester: FocusRequester,
    playFocusRequester: FocusRequester,
    shortHeight: Boolean,
    imageActionsOverlay: Boolean,
) {
    val description = gameDescriptionForDetails(game)
    val context = LocalContext.current
    val sideScrollState = rememberScrollState()
    val detailsSpacing = if (shortHeight) 8.dp else 10.dp
    var gameFocused by remember(game.id) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = if (shortHeight) 18.dp else 24.dp, vertical = if (shortHeight) 16.dp else 22.dp),
        horizontalArrangement = Arrangement.spacedBy(if (shortHeight) 16.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val imageShape = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .weight(0.92f)
                .fillMaxHeight()
                .focusRequester(gameFocusRequester)
                .focusProperties { right = playFocusRequester }
                .onFocusChanged { gameFocused = it.isFocused }
                .clickable {
                    onDismiss()
                    onPlay(game)
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        width = if (gameFocused) 3.dp else 1.dp,
                        color = if (gameFocused) Color.White else Color.White.copy(alpha = 0.12f),
                        shape = imageShape,
                    )
                    .clip(imageShape),
            ) {
                UrlImage(gameHeroImageUrl(context, game), Modifier.fillMaxSize())
                GameImageTitleOverlay(
                    game = game,
                    compact = shortHeight,
                    reserveEndSpace = imageActionsOverlay,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
                if (imageActionsOverlay) {
                    ImageCloseButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                    )
                }
                if (imageActionsOverlay) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .width(150.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        connectedTvName?.let {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onPlayOnTv(game)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.action_play_on_tv_generic), maxLines = 1)
                            }
                        }
                        LongPressPlayButton(
                            onClick = {
                                onDismiss()
                                onPlay(game)
                            },
                            onLongClick = {
                                onDismiss()
                                onChooseStore(game)
                            },
                            modifier = Modifier
                                .fillMaxWidth(),
                            focusRequester = playFocusRequester,
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .weight(1.08f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(detailsSpacing),
        ) {
            if (imageActionsOverlay) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(sideScrollState),
                    verticalArrangement = Arrangement.spacedBy(detailsSpacing),
                ) {
                    GameDetailsCompactInfoContent(
                        game = game,
                        defaultVariantId = defaultVariantId,
                        description = description,
                    )
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(sideScrollState),
                    verticalArrangement = Arrangement.spacedBy(detailsSpacing),
                ) {
                    GameDetailsCompactInfoContent(
                        game = game,
                        defaultVariantId = defaultVariantId,
                        description = description,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var dismissFocused by remember { mutableStateOf(false) }
                    val accent = MaterialTheme.colorScheme.primary
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, if (dismissFocused) accent else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .onFocusChanged { dismissFocused = it.isFocused }
                    ) {
                        Text(
                            stringResource(R.string.action_dismiss),
                            color = if (dismissFocused) accent else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LongPressPlayButton(
                        onClick = {
                            onDismiss()
                            onPlay(game)
                        },
                        onLongClick = {
                            onDismiss()
                            onChooseStore(game)
                        },
                        modifier = Modifier.weight(1f),
                        focusRequester = playFocusRequester,
                    )
                    connectedTvName?.let {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onPlayOnTv(game)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(stringResource(R.string.action_play_on_tv_generic), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDetailsCompactInfoContent(
    game: GameInfo,
    defaultVariantId: String?,
    description: String?,
) {
    OwnershipStatusRow(game = game, compact = true)
    GameGenreChips(game = game, compact = true)
    GameScreenshotGallery(game = game, compact = true)
    GameDescriptionDisclosure(
        description = description,
        compact = true,
    )
    CompactDetailRows(game)
    LaunchOptionsList(
        game = game,
        defaultVariantId = defaultVariantId,
        compact = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameDetailsScrollableContent(
    game: GameInfo,
    favorite: Boolean,
    defaultVariantId: String?,
    onPlay: (GameInfo) -> Unit,
    onChooseStore: (GameInfo) -> Unit,
    onFavorite: (String) -> Unit,
    connectedTvName: String?,
    onPlayOnTv: (GameInfo) -> Unit,
    onDismiss: () -> Unit,
    gameFocusRequester: FocusRequester,
    playFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    var gameFocused by remember(game.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                val imageShape = RoundedCornerShape(OpenNowRadius.lg)
                Box(
                    Modifier
                        .fillMaxWidth()
                        // Scales with the screen instead of being pinned at 220dp, which was
                        // cramped on a tablet and oversized on a small phone.
                        .aspectRatio(16f / 9f)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .focusRequester(gameFocusRequester)
                        .focusProperties { down = playFocusRequester }
                        .onFocusChanged { gameFocused = it.isFocused }
                        .clickable {
                            onDismiss()
                            onPlay(game)
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(
                                width = if (gameFocused) 3.dp else 1.dp,
                                color = if (gameFocused) Color.White else Color.White.copy(alpha = 0.12f),
                                shape = imageShape,
                            )
                            .clip(imageShape),
                    ) {
                        UrlImage(
                            gameHeroImageUrl(context, game),
                            Modifier.fillMaxSize(),
                        )
                        // Guarantees the title overlay stays legible over bright key art.
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.4f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.75f),
                                    ),
                                ),
                        )
                        GameImageTitleOverlay(
                            game = game,
                            compact = false,
                            reserveEndSpace = false,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val description = gameDescriptionForDetails(game)
                    OwnershipStatusRow(game = game, compact = false)
                    GameGenreChips(game = game, compact = false)
                    GameScreenshotGallery(game = game, compact = false)
                    GameDescriptionDisclosure(
                        description = description,
                        compact = false,
                    )
                    DetailRows(game)
                    LaunchOptionsList(
                        game = game,
                        defaultVariantId = defaultVariantId,
                        compact = false,
                    )
                }
            }
        }
        Surface(color = Panel.copy(alpha = 0.98f), tonalElevation = 8.dp) {
            // Play is the point of the screen, so it takes the width. Dismiss and the secondary
            // actions become fixed-size icons rather than equal-weight buttons that squeezed Play
            // down to a third of the bar whenever a TV was connected.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var dismissFocused by remember { mutableStateOf(false) }
                val accent = MaterialTheme.colorScheme.primary
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .onFocusChanged { dismissFocused = it.isFocused },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clear),
                        contentDescription = stringResource(R.string.action_dismiss),
                        tint = if (dismissFocused) accent else TextMuted,
                    )
                }
                // favorite/onFavorite were already threaded into this composable but never used —
                // on phones the only way to favourite a game was from the grid.
                FavoriteIconButton(
                    favorite = favorite,
                    onClick = { onFavorite(game.id) },
                    size = 48.dp,
                )
                LongPressPlayButton(
                    onClick = {
                        onDismiss()
                        onPlay(game)
                    },
                    onLongClick = {
                        onDismiss()
                        onChooseStore(game)
                    },
                    modifier = Modifier.weight(1f),
                    focusRequester = playFocusRequester,
                )
                connectedTvName?.let { tvName ->
                    IconButton(
                        onClick = {
                            onDismiss()
                            onPlayOnTv(game)
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cast,
                            contentDescription = stringResource(R.string.action_play_on_tv, tvName),
                            tint = TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchOptionsList(
    game: GameInfo,
    defaultVariantId: String?,
    compact: Boolean,
) {
    val variants = launchableGameVariants(game.variants)
    if (variants.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        Text(
            stringResource(R.string.store_selector_launchers),
            color = TextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        variants.take(if (compact) 3 else variants.size).forEach { variant ->
            val isDefault = variant.id == defaultVariantId
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
                color = if (isDefault) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else PanelAlt,
                contentColor = TextPrimary,
            ) {
                Row(
                    Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ConnectorStoreIcon(
                        launcherBadgeForStoreKey(splitGameStoreKeys(variant.store).firstOrNull()),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(gameStoreDisplayName(variant.store), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val details = variantDetailsText(variant)
                        Text(
                            if (isDefault) {
                                listOf(stringResource(R.string.store_selector_default), details).filter { it.isNotBlank() }.joinToString(" - ")
                            } else {
                                details.ifBlank { stringResource(R.string.store_selector_available_launcher) }
                            },
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (compact) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LongPressPlayButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val controllerFocusEnabled = LocalControllerFocusEnabled.current
    var focused by remember { mutableStateOf(false) }
    val controllerFocused = focused && controllerFocusEnabled
    val shape = RoundedCornerShape(999.dp)
    val accent = MaterialTheme.colorScheme.primary
    val focusScale by animateFloatAsState(
        targetValue = gameDetailsPlayFocusScale(controllerFocused),
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "game-details-play-focus-scale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (controllerFocused) Color.White else accent,
        animationSpec = tween(durationMillis = 120),
        label = "game-details-play-focus-color",
    )
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = focusScale
            scaleY = focusScale
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(
                    focusRequester?.let { requester -> Modifier.focusRequester(requester) }
                        ?: Modifier,
                )
                .onFocusChanged { focusState -> focused = focusState.isFocused }
                .onPreviewKeyEvent { event ->
                    if (isTvActivateKey(event)) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
                .focusable()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = stringResource(R.string.store_selector_play_long_press),
                )
                .then(
                    if (controllerFocused) {
                        Modifier.border(
                            width = gameDetailsPlayFocusBorderWidthDp(controllerFocused).dp,
                            color = accent,
                            shape = shape,
                        )
                    } else {
                        Modifier
                    },
                ),
            shape = shape,
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = if (controllerFocused) 12.dp else 0.dp,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZortosPlayMark(
                    modifier = Modifier.size(20.dp),
                    ringColor = Color.Black,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_play),
                    color = Color.Black,
                    fontWeight = if (controllerFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ControllerFocusFrame(
            visible = controllerFocused && LocalAbsoluteCinemaEffects.current,
            cornerRadius = 24.dp,
            tint = LocalActiveSelectionColor.current,
            secondaryTint = LocalActiveSelectionSecondaryColor.current,
        )
    }
}

internal fun gameDetailsPlayFocusScale(focused: Boolean): Float = if (focused) 1.06f else 1f

internal fun gameDetailsPlayFocusBorderWidthDp(focused: Boolean): Float = if (focused) 4f else 0f

private fun variantDetailsText(variant: GameVariant): String =
    listOfNotNull(
        variant.libraryStatus?.takeIf { it.isNotBlank() }?.let(::formatGameMetadataLabel),
        variant.supportedControls.takeIf { it.isNotEmpty() }?.joinToString(", ") { formatGameMetadataLabel(it) },
        variant.lastPlayedDate?.takeIf { it.isNotBlank() }?.let { "Last played $it" },
    ).joinToString(" - ")

@Composable
private fun ImageCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .size(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (focused) accent else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.58f),
        tonalElevation = 3.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_clear),
                contentDescription = stringResource(R.string.action_cancel),
                tint = TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FavoriteIconButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val label = stringResource(if (favorite) R.string.action_saved else R.string.action_save)
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .focusable()
            .then(
                if (focused) Modifier.border(2.dp, accent, CircleShape) else Modifier
            ),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.35f),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, if (focused) accent else Color.White.copy(alpha = 0.2f)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(if (favorite) R.drawable.ic_save_filled else R.drawable.ic_save),
                contentDescription = null,
                tint = if (favorite) MaterialTheme.colorScheme.primary else TextPrimary,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

internal fun gameDescriptionForDetails(game: GameInfo): String? =
    game.description?.takeIf { it.isNotBlank() }
        ?: game.longDescription?.takeIf { it.isNotBlank() }

private fun gameHeroImageUrl(context: Context, game: GameInfo?): String? {
    val url = game?.screenshotUrl?.takeIf { it.isNotBlank() }
        ?: game?.tvBannerUrl?.takeIf { it.isNotBlank() }
        ?: game?.imageUrl?.takeIf { it.isNotBlank() }
        ?: return null
    return optimizedNvidiaImageUrl(url, wideImageRequestWidth(context))
}

internal fun gameTvBannerImageUrl(context: Context, game: GameInfo?): String? {
    val url = game?.tvBannerUrl?.takeIf { it.isNotBlank() }
        ?: game?.screenshotUrl?.takeIf { it.isNotBlank() }
        ?: game?.imageUrl?.takeIf { it.isNotBlank() }
        ?: return null
    return optimizedNvidiaImageUrl(url, wideImageRequestWidth(context))
}

private fun optimizedNvidiaImageUrl(url: String, width: Int): String {
    if (!url.contains("img.nvidiagrid.net")) return url
    val base = url
        .substringBefore(";f=")
        .substringBefore(";w=")
        .substringBefore(";h=")
        .substringBefore(";dpr=")
    return "$base;f=webp;w=$width"
}

private fun wideImageRequestWidth(context: Context): Int {
    val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
    val downstreamKbps = capabilities?.linkDownstreamBandwidthKbps ?: 0
    return when {
        downstreamKbps >= 25_000 -> 1920
        downstreamKbps in 10_000 until 25_000 -> 1600
        downstreamKbps in 3_000 until 10_000 -> 1280
        downstreamKbps in 1 until 3_000 -> 960
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true -> 1600
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> 960
        else -> 1280
    }
}

@Composable
private fun GameImageTitleOverlay(
    game: GameInfo,
    compact: Boolean,
    reserveEndSpace: Boolean,
    modifier: Modifier = Modifier,
) {
    val textShadow = Shadow(
        color = Color.Black,
        offset = Offset(0f, 3f),
        blurRadius = 14f,
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(
                start = if (compact) 12.dp else 16.dp,
                top = if (compact) 9.dp else 12.dp,
                end = if (reserveEndSpace) 154.dp else if (compact) 12.dp else 16.dp,
                bottom = if (compact) 10.dp else 14.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            game.title,
            color = TextPrimary,
            style = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall).copy(
                shadow = textShadow,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = if (compact) 2 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            game.publisherName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.catalog_unknown_publisher),
            color = TextPrimary.copy(alpha = 0.88f),
            style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GameTitleBlock(game: GameInfo, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
        Text(
            game.title,
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            game.publisherName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.catalog_unknown_publisher),
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OwnershipStatusRow(game: GameInfo, compact: Boolean) {
    val ownedStores = ownedStoreLabels(game)
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    if (ownedStores.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = Color(0xff4a1216),
            tonalElevation = 0.dp,
        ) {
            Text(
                stringResource(R.string.catalog_not_owned),
                color = OpenNowPalette.OnErrorContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ownedStores.forEach { store ->
            val badge = launcherBadgeForStoreKey(normalizeGameStore(store))
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                tonalElevation = 0.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConnectorStoreIcon(badge)
                    Text(
                        "Owned on $store",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun ownedStoreLabels(game: GameInfo): List<String> =
    libraryStoreDisplayNames(game).ifEmpty {
        if (isGameInLibrary(game)) listOf("GeForce NOW") else emptyList()
    }

@Composable
private fun GameGenreChips(game: GameInfo, compact: Boolean) {
    val genres = game.genres
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map(::formatGameMetadataLabel)
        .filterNot(::isNoisyGameTag)
        .distinctBy { it.lowercase(Locale.US) }
        .take(if (compact) 12 else 20)
    if (genres.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp),
        contentPadding = PaddingValues(end = if (compact) 6.dp else 8.dp),
    ) {
        items(genres, key = { it }) { label ->
            AssistChip(onClick = {}, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

@Composable
private fun GameScreenshotGallery(game: GameInfo, compact: Boolean) {
    val screenshots = game.screenshotUrls
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    if (screenshots.isEmpty()) return
    val context = LocalContext.current
    val requestWidth = remember(context) { wideImageRequestWidth(context).coerceAtLeast(960) }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
    ) {
        Text(
            "Screenshots",
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(screenshots, key = { it }) { screenshot ->
                Surface(
                    modifier = Modifier
                        .width(if (compact) 224.dp else 288.dp)
                        .aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
                    color = Color.Black,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                ) {
                    UrlImage(
                        url = optimizedNvidiaImageUrl(screenshot, requestWidth),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameDescriptionDisclosure(
    description: String?,
    compact: Boolean,
) {
    var expanded by remember(description) { mutableStateOf(true) }
    val text = description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.catalog_no_description)
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clickable { expanded = !expanded },
            shape = shape,
            color = if (focused) PanelAlt.copy(alpha = 0.85f) else PanelAlt,
            border = if (focused) BorderStroke(1.dp, Color.White) else null,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Description",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = if (expanded) {
                                stringResource(R.string.control_hide_description)
                            } else {
                                stringResource(R.string.control_show_description)
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer(rotationZ = if (expanded) 90f else 0f),
                        )
                    }
                }
                if (expanded) {
                    Text(
                        text,
                        color = if (description == null) TextMuted else TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (compact) 8 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun formatGameMetadataLabel(raw: String): String {
    val compact = raw.trim()
        .removePrefix("GFN_")
        .removePrefix("GAME_")
        .replace(Regex("[_-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (compact.isBlank()) return ""
    val lower = compact.lowercase(Locale.US)
    return when (lower) {
        "full game" -> "Full game"
        "single player" -> "Single-player"
        "multi player", "multiplayer" -> "Multiplayer"
        "controller", "gamepad" -> "Controller"
        "keyboard mouse", "mouse keyboard" -> "Mouse and keyboard"
        else -> compact.split(" ").joinToString(" ") { word ->
            if (word.length <= 3 && word.all { it.isUpperCase() || it.isDigit() }) {
                word
            } else {
                word.lowercase(Locale.US).replaceFirstChar { char -> char.titlecase(Locale.US) }
            }
        }
    }
}

private fun isNoisyGameTag(label: String): Boolean {
    val normalized = label.trim().lowercase(Locale.US)
    return normalized.isBlank() ||
        normalized == "unknown" ||
        normalized == "gfn" ||
        normalized == "nvidia" ||
        normalized.contains("sku based tag") ||
        normalized.contains("catalog")
}

@Composable
private fun CompactDetailRows(game: GameInfo) {
    val rows = gameDetailRows(game).take(4)
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            DetailRow(row = row, compact = true)
        }
    }
}

@Composable
private fun DetailRows(game: GameInfo) {
    val rows = gameDetailRows(game)
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            DetailRow(row = row, compact = false)
        }
    }
}

private data class GameDetailRow(
    val label: String,
    val value: String,
    val copyValue: String? = null,
)

private fun gameDetailRows(game: GameInfo): List<GameDetailRow> =
    listOfNotNull(
        game.playabilityState?.takeIf { it.isNotBlank() }?.let { GameDetailRow("Status", formatGameMetadataLabel(it)) },
        game.publisherName?.takeIf { it.isNotBlank() }?.let { GameDetailRow("Publisher", it) },
        game.playType?.takeIf { it.isNotBlank() }?.let { GameDetailRow("Play type", formatGameMetadataLabel(it)) },
        supportedControlLabels(game).takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { GameDetailRow("Controls", it) },
        game.featureLabels
            .map(::formatGameMetadataLabel)
            .filterNot(::isNoisyGameTag)
            .filterNot { feature -> game.genres.any { genre -> feature.equals(formatGameMetadataLabel(genre), ignoreCase = true) } }
            .distinctBy { it.lowercase(Locale.US) }
            .take(8)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?.let { GameDetailRow("Features", it) },
        game.membershipTierLabel?.takeIf { it.isNotBlank() }?.let { GameDetailRow("Membership", formatGameMetadataLabel(it)) },
        game.contentRatings.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { GameDetailRow("Rating", it) },
        game.lastPlayed?.takeIf { it.isNotBlank() }?.let { GameDetailRow("Last played", it) },
        game.availableStores.takeIf { it.isNotEmpty() }?.map(::gameStoreDisplayName)?.distinct()?.joinToString(", ")?.let { GameDetailRow("Stores", it) },
        gameAppIdForDetails(game)?.let { GameDetailRow("App ID", it, copyValue = it) },
    )

internal fun supportedControlLabels(game: GameInfo): List<String> =
    game.variants
        .flatMap { it.supportedControls }
        .map(::formatGameMetadataLabel)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(Locale.US) }

private fun gameAppIdForDetails(game: GameInfo): String? =
    game.launchAppId?.takeIf { it.isNotBlank() }
        ?: game.variants.firstNotNullOfOrNull { variant -> variant.id.takeIf { it.isNotBlank() && it.all(Char::isDigit) } }
        ?: game.uuid?.takeIf { it.isNotBlank() }
        ?: game.id.takeIf { it.isNotBlank() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailRow(row: GameDetailRow, compact: Boolean) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PanelAlt)
            .combinedClickable(
                onClick = {},
                onLongClick = row.copyValue?.let { value ->
                    {
                        clipboard.setText(AnnotatedString(value))
                        Toast.makeText(context, "App ID copied", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 7.dp else 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
    ) {
        Text(
            row.label,
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(if (compact) 82.dp else 92.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (row.copyValue != null) "${row.value}" else row.value,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun gameMatchesSearch(game: GameInfo, query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return true
    val haystack = buildString {
        append(game.title).append(' ')
        append(game.description.orEmpty()).append(' ')
        append(game.longDescription.orEmpty()).append(' ')
        append(game.publisherName.orEmpty()).append(' ')
        append(game.genres.joinToString(" ")).append(' ')
        append(game.featureLabels.joinToString(" ")).append(' ')
        append(displayStoresForGame(game))
    }.lowercase()
    return normalized.split(Regex("\\s+")).all { it in haystack }
}

internal fun favoriteOrderedGames(games: List<GameInfo>, favoriteIds: List<String>): List<GameInfo> {
    val favorites = games.filter { it.id in favoriteIds }
    return if (favorites.isNotEmpty()) favorites + games.filterNot { it.id in favoriteIds } else games
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun StoreLaunchSelector(
    game: GameInfo,
    defaultVariantId: String?,
    onLaunch: (GameInfo, GameVariant) -> Unit,
    onSetDefaultStore: (String, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val variants = remember(game) { launchableGameVariants(game.variants) }
    val context = LocalContext.current
    val initialVariantId = remember(game.id, defaultVariantId, variants) {
        defaultVariantId?.takeIf { savedId -> variants.any { it.id == savedId } }
            ?: variants.firstOrNull()?.id
    }
    var selectedVariantId by remember(game.id, initialVariantId) { mutableStateOf(initialVariantId) }
    var rememberDefaultStore by remember(game.id, defaultVariantId) { mutableStateOf(defaultVariantId != null) }
    val selectedVariant = variants.firstOrNull { it.id == selectedVariantId }
    val continueFocusRequester = remember(game.id) { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(game.id, variants.size) {
        if (variants.isNotEmpty()) {
            requestFocusWithRetry(continueFocusRequester)
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .lockedFocusGroup()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false) {},
    ) {
        val phoneLandscape = isPhoneLandscape(maxWidth, maxHeight)
        val landscape = maxWidth > maxHeight
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = if (phoneLandscape) Alignment.CenterEnd else Alignment.Center,
        ) {
            Card(
                modifier = modifier
                    .then(
                        if (phoneLandscape) {
                            Modifier
                                .padding(end = 12.dp)
                                .fillMaxWidth(0.9f)
                                .fillMaxHeight(0.9f)
                        } else {
                            Modifier
                                .fillMaxWidth(if (landscape) 0.78f else 0.92f)
                                .fillMaxHeight(if (landscape) 0.86f else 0.64f)
                        },
                    ),
                colors = CardDefaults.cardColors(containerColor = Panel, contentColor = TextPrimary),
                shape = RoundedCornerShape(22.dp),
            ) {
                if (phoneLandscape) {
                    Row(
                        Modifier.fillMaxSize().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LaunchGameSummary(
                            game = game,
                            subtitle = stringResource(R.string.store_selector_choose_launcher),
                            modifier = Modifier
                                .width(190.dp)
                                .fillMaxHeight(),
                        )
                        StoreLaunchOptionsColumn(
                            variants = variants,
                            selectedVariantId = selectedVariantId,
                            defaultVariantId = defaultVariantId,
                            rememberDefaultStore = rememberDefaultStore,
                            selectedVariant = selectedVariant,
                            continueFocusRequester = continueFocusRequester,
                            onSelectVariant = { selectedVariantId = it },
                            onRememberDefaultStoreChange = { rememberDefaultStore = it },
                            onDismiss = onDismiss,
                            onContinue = { variant ->
                                if (rememberDefaultStore || defaultVariantId != null) {
                                    onSetDefaultStore(game.id, if (rememberDefaultStore) variant.id else null)
                                }
                                if (rememberDefaultStore) {
                                    Toast.makeText(context, context.getString(R.string.store_selector_long_press_tip), Toast.LENGTH_LONG).show()
                                }
                                onLaunch(game, variant)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UrlImage(
                                game.imageUrl,
                                Modifier
                                    .width(58.dp)
                                    .height(76.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(game.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.store_selector_choose_launcher), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        StoreLaunchOptionsColumn(
                            variants = variants,
                            selectedVariantId = selectedVariantId,
                            defaultVariantId = defaultVariantId,
                            rememberDefaultStore = rememberDefaultStore,
                            selectedVariant = selectedVariant,
                            continueFocusRequester = continueFocusRequester,
                            onSelectVariant = { selectedVariantId = it },
                            onRememberDefaultStoreChange = { rememberDefaultStore = it },
                            onDismiss = onDismiss,
                            onContinue = { variant ->
                                if (rememberDefaultStore || defaultVariantId != null) {
                                    onSetDefaultStore(game.id, if (rememberDefaultStore) variant.id else null)
                                }
                                if (rememberDefaultStore) {
                                    Toast.makeText(context, context.getString(R.string.store_selector_long_press_tip), Toast.LENGTH_LONG).show()
                                }
                                onLaunch(game, variant)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchGameSummary(game: GameInfo, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UrlImage(
            game.imageUrl,
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(game.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StoreLaunchOptionsColumn(
    variants: List<GameVariant>,
    selectedVariantId: String?,
    defaultVariantId: String?,
    rememberDefaultStore: Boolean,
    selectedVariant: GameVariant?,
    continueFocusRequester: FocusRequester,
    onSelectVariant: (String) -> Unit,
    onRememberDefaultStoreChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onContinue: (GameVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(variants, key = { it.id }) { variant ->
                StoreLaunchVariantRow(
                    variant = variant,
                    selected = variant.id == selectedVariantId,
                    savedDefault = variant.id == defaultVariantId,
                    onClick = { onSelectVariant(variant.id) },
                )
            }
        }
        var checkFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .onFocusChanged { checkFocused = it.isFocused }
                .background(if (checkFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (checkFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onRememberDefaultStoreChange(!rememberDefaultStore) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = rememberDefaultStore,
                onCheckedChange = onRememberDefaultStoreChange,
            )
            Text(
                stringResource(R.string.store_selector_default_checkbox),
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = {
                    val variant = selectedVariant ?: return@Button
                    onContinue(variant)
                },
                enabled = selectedVariant != null,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(continueFocusRequester),
            ) {
                Text(stringResource(R.string.action_continue), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StoreLaunchVariantRow(
    variant: GameVariant,
    selected: Boolean,
    savedDefault: Boolean,
    onClick: () -> Unit,
) {
    val badge = launcherBadgeForStoreKey(splitGameStoreKeys(variant.store).firstOrNull())
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = if (focused || selected) 2.dp else 1.dp,
                    color = when {
                        focused -> Color.White
                        selected -> LocalActiveSelectionColor.current
                        else -> Color.White.copy(alpha = 0.1f)
                    },
                    shape = shape,
                )
                .clickable { onClick() },
            shape = shape,
            color = if (focused) Color.White.copy(alpha = 0.12f) else if (selected) LocalActiveSelectionColor.current.copy(alpha = 0.18f) else PanelAlt,
            contentColor = TextPrimary,
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectorStoreIcon(badge)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        gameStoreDisplayName(variant.store),
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val details = listOf(
                        if (savedDefault) stringResource(R.string.store_selector_default) else "",
                        variantDetailsText(variant),
                    ).filter { it.isNotBlank() }.joinToString(" - ")
                    if (details.isNotBlank()) {
                        Text(details, color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (selected) {
                    Text(
                        stringResource(R.string.store_selector_selected),
                        color = LocalActiveSelectionColor.current,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
            }
        }
        ControllerFocusFrame(
            visible = selected && LocalActiveSelectionEnabled.current,
            cornerRadius = 14.dp,
            tint = LocalActiveSelectionColor.current,
            secondaryTint = LocalActiveSelectionSecondaryColor.current,
        )
    }
}
