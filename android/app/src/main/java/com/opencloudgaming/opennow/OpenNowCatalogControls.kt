package com.opencloudgaming.opennow

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import com.opencloudgaming.opennow.ui.theme.LocalReduceMotion
import com.opencloudgaming.opennow.ui.theme.OpenNowPalette
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

@Composable
internal fun SortPicker(
    options: List<CatalogSortOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val labels = options.ifEmpty { listOf(CatalogSortOption("relevance", "Relevance", "")) }
    val selectedLabel = labels.firstOrNull { it.id == selected }?.label ?: labels.first().label
    var expanded by remember { mutableStateOf(false) }
    val controlShape = RoundedCornerShape(999.dp)
    val controlColor = Color.White.copy(alpha = 0.1f)
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(if (compact) TopBarCompactControlHeight else 40.dp),
            shape = controlShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = controlColor,
                contentColor = TextPrimary,
            ),
            contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 12.dp),
        ) {
            Text(
                "Sort: $selectedLabel",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (option.id == selected) "✓" else "", modifier = Modifier.width(24.dp))
                            Text(option.label)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.id)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectedFilterChips(options: List<CatalogFilterOption>, selectedIds: List<String>, onToggle: (String) -> Unit) {
    val selectedOptions = options.filter { it.id in selectedIds }
    if (selectedOptions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        selectedOptions.take(4).forEach { option ->
            AssistChip(onClick = { onToggle(option.id) }, label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
        if (selectedOptions.size > 4) {
            AssistChip(onClick = {}, label = { Text("+${selectedOptions.size - 4}") })
        }
    }
}

internal fun catalogVisibleFilterGroups(groups: List<CatalogFilterGroup>): List<CatalogFilterGroup> =
    groups.filter { it.id in setOf("digital_store", "genre", "subscriptions") }

internal fun catalogFilterOptions(groups: List<CatalogFilterGroup>): List<CatalogFilterOption> =
    groups.flatMap { group -> group.options.take(if (group.id == "genre") 10 else group.options.size) }

@Composable
internal fun FilterMenu(
    options: List<CatalogFilterOption>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    compact: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val filterControlShape = RoundedCornerShape(999.dp)
    val filterControlColor = Color.White.copy(alpha = 0.1f)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(if (compact) TopBarCompactControlHeight else 36.dp),
            shape = filterControlShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = filterControlColor,
                contentColor = TextPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text(if (selectedIds.isEmpty()) "Filters" else "Filters ${selectedIds.size}", maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                title = {
                    Text(
                        "Filters",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(0.6f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(options) { option ->
                            val isSelected = option.id in selectedIds
                            var rowFocused by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .onFocusChanged { rowFocused = it.isFocused }
                                    .background(if (rowFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (rowFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onToggle(option.id) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { expanded = false }) {
                        Text(stringResource(R.string.stream_panel_done))
                    }
                }
            )
        }
    }
}

@Composable
internal fun PrintedWasteSelector(
    state: OpenNowUiState,
    game: GameInfo,
    viewModel: OpenNowViewModel,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = viewModel::dismissPrintedWasteSelector)
    val zones = remember(state.printedWasteQueue, state.printedWasteMapping, state.printedWastePings) {
        state.printedWasteQueue
            .filter { (zoneId, _) -> isStandardPrintedWasteZone(zoneId) && state.printedWasteMapping[zoneId]?.nuked != true }
            .map { (zoneId, zone) ->
                val routingUrl = printedWasteZoneUrl(zoneId)
                PrintedWasteZoneOption(
                    zoneId = zoneId,
                    zone = zone,
                    routingUrl = routingUrl,
                    pingMs = state.printedWastePings[routingUrl],
                )
            }
    }
    val autoZone = remember(zones) { recommendedPrintedWasteZone(zones) }
    val sortedZones = remember(zones, autoZone) {
        val maxPing = zones.mapNotNull { it.pingMs }.maxOrNull()?.coerceAtLeast(1) ?: 1
        val maxQueue = zones.maxOfOrNull { it.zone.QueuePosition }?.coerceAtLeast(1) ?: 1
        zones.sortedWith(
            compareByDescending<PrintedWasteZoneOption> { it.zoneId == autoZone?.zoneId }
                .thenBy { printedWasteScore(it, maxPing, maxQueue) }
                .thenBy { it.zoneId },
        )
    }
    var selectedZoneId by remember(game.id, sortedZones) { mutableStateOf<String?>(autoZone?.zoneId) }
    val selectedZone = sortedZones.firstOrNull { it.zoneId == selectedZoneId } ?: autoZone
    val context = LocalContext.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .lockedFocusGroup()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false) {},
    ) {
        val phoneLandscape = isPhoneLandscape(maxWidth, maxHeight)
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
                                .fillMaxWidth(0.94f)
                                .fillMaxHeight(0.82f)
                        },
                    ),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(22.dp),
            ) {
                if (phoneLandscape) {
                    Row(
                        Modifier.fillMaxSize().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PrintedWasteGameSummary(
                            game = game,
                            modifier = Modifier
                                .width(190.dp)
                                .fillMaxHeight(),
                        )
                        PrintedWasteOptionsColumn(
                            state = state,
                            zones = sortedZones,
                            selectedZoneId = selectedZoneId,
                            selectedZone = selectedZone,
                            autoZone = autoZone,
                            showRecommendedCard = true,
                            onSelectZone = { selectedZoneId = it },
                            onRetry = viewModel::refreshPrintedWasteQueues,
                            onDismiss = viewModel::dismissPrintedWasteSelector,
                            onDefault = { viewModel.launchWithPrintedWaste(null) },
                            onLaunch = { viewModel.launchWithPrintedWaste(selectedZone?.routingUrl) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UrlImage(
                                gameTvBannerImageUrl(context, game),
                                Modifier
                                    .width(98.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(game.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Free tier queue routing", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        PrintedWasteOptionsColumn(
                            state = state,
                            zones = sortedZones,
                            selectedZoneId = selectedZoneId,
                            selectedZone = selectedZone,
                            autoZone = autoZone,
                            showRecommendedCard = true,
                            onSelectZone = { selectedZoneId = it },
                            onRetry = viewModel::refreshPrintedWasteQueues,
                            onDismiss = viewModel::dismissPrintedWasteSelector,
                            onDefault = { viewModel.launchWithPrintedWaste(null) },
                            onLaunch = { viewModel.launchWithPrintedWaste(selectedZone?.routingUrl) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintedWasteGameSummary(
    game: GameInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UrlImage(
            gameTvBannerImageUrl(context, game),
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(game.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Free tier queue routing", color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun PrintedWasteOptionsColumn(
    state: OpenNowUiState,
    zones: List<PrintedWasteZoneOption>,
    selectedZoneId: String?,
    selectedZone: PrintedWasteZoneOption?,
    autoZone: PrintedWasteZoneOption?,
    showRecommendedCard: Boolean,
    onSelectZone: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onDefault: () -> Unit,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneListState = rememberLazyListState()
    val zoneListFocusRequester = remember { FocusRequester() }
    val defaultFocusRequester = remember { FocusRequester() }
    val launchFocusRequester = remember { FocusRequester() }
    var zoneListFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun selectZoneAt(index: Int) {
        val next = zones.getOrNull(index) ?: return
        onSelectZone(next.zoneId)
        scope.launch {
            zoneListState.animateScrollToItem(index)
        }
    }
    LaunchedEffect(state.printedWasteLoading, state.printedWasteError, zones.size) {
        delay(80)
        if (!state.printedWasteLoading && state.printedWasteError == null && zones.isNotEmpty()) {
            runCatching { launchFocusRequester.requestFocus() }
        } else {
            runCatching { defaultFocusRequester.requestFocus() }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.printedWasteLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Checking PrintedWaste queues and latency", color = TextMuted)
                }
            }
        } else if (state.printedWasteError != null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.printedWasteError, color = Color(0xffff9f9f))
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        } else {
            if (showRecommendedCard) {
                autoZone?.let {
                    RecommendedPrintedWasteCard(it)
                }
            }
            var listFocused by remember { mutableStateOf(false) }
            LazyColumn(
                state = zoneListState,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(zoneListFocusRequester)
                    .onFocusChanged { listFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (isTvActivateKey(event)) {
                            if (selectedZone != null) {
                                onLaunch()
                                true
                            } else {
                                false
                            }
                        } else if (event.type == KeyEventType.KeyDown) {
                            val selectedIndex = zones.indexOfFirst { it.zoneId == selectedZoneId }.let { if (it >= 0) it else 0 }
                            when (event.key) {
                                Key.DirectionUp -> {
                                    if (selectedIndex > 0) {
                                        selectZoneAt(selectedIndex - 1)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (selectedIndex < zones.lastIndex) {
                                        selectZoneAt(selectedIndex + 1)
                                        true
                                    } else {
                                        runCatching { launchFocusRequester.requestFocus() }.isSuccess
                                    }
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusable(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(zones, key = { it.zoneId }) { zoneOption ->
                    val isCurrent = zoneOption.zoneId == selectedZoneId
                    PrintedWasteZoneRow(
                        zoneOption = zoneOption,
                        selected = isCurrent,
                        focused = isCurrent && listFocused,
                        listFocused = listFocused,
                        onClick = { onSelectZone(zoneOption.zoneId) },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            OutlinedButton(
                onClick = onDefault,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(defaultFocusRequester),
            ) {
                Text(stringResource(R.string.store_selector_default), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(
                onClick = onLaunch,
                enabled = !state.printedWasteLoading && selectedZone != null,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(launchFocusRequester)
                    .focusProperties { up = zoneListFocusRequester },
            ) {
                Text(stringResource(R.string.action_launch), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RecommendedPrintedWasteCard(zoneOption: PrintedWasteZoneOption) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.queue_best_route), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    "${zoneOption.zoneId} · ${regionLabel(zoneOption.zone.Region)}",
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            QueueMetricPill(stringResource(R.string.stream_statusbar_metric_ping), zoneOption.pingMs?.let { "$it ms" } ?: stringResource(R.string.queue_checking))
            QueueMetricPill(stringResource(R.string.queue_metric_ahead), zoneOption.zone.QueuePosition.toString(), queueColor(zoneOption.zone.QueuePosition))
        }
    }
}

@Composable
private fun PrintedWasteZoneRow(
    zoneOption: PrintedWasteZoneOption,
    selected: Boolean,
    focused: Boolean,
    listFocused: Boolean,
    onClick: () -> Unit,
) {
    val zone = zoneOption.zone
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else PanelAlt,
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = if (selected && listFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 520.dp
            if (compact) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(zoneOption.zoneId, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else TextPrimary)
                            Text(regionLabel(zone.Region), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected) {
                            Text(stringResource(R.string.store_selector_selected), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueMetricPill(stringResource(R.string.stream_statusbar_metric_ping), zoneOption.pingMs?.let { "$it ms" } ?: "--", zoneOption.pingMs?.let(::pingColor) ?: TextMuted)
                        QueueMetricPill(stringResource(R.string.queue_metric_ahead), zone.QueuePosition.toString(), queueColor(zone.QueuePosition))
                        zone.eta?.let { QueueMetricPill(stringResource(R.string.queue_metric_wait), formatPrintedWasteWait(it)) }
                    }
                }
            } else {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(zoneOption.zoneId, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else TextPrimary)
                        Text(regionLabel(zone.Region), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    QueueMetricPill(stringResource(R.string.stream_statusbar_metric_ping), zoneOption.pingMs?.let { "$it ms" } ?: "--", zoneOption.pingMs?.let(::pingColor) ?: TextMuted)
                    QueueMetricPill(stringResource(R.string.queue_metric_ahead), zone.QueuePosition.toString(), queueColor(zone.QueuePosition))
                    zone.eta?.let { QueueMetricPill(stringResource(R.string.queue_metric_wait), formatPrintedWasteWait(it)) }
                }
            }
        }
    }
}

@Composable
private fun QueueMetricPill(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun isStandardPrintedWasteZone(zoneId: String): Boolean =
    zoneId.startsWith("NP-") && !zoneId.startsWith("NPA-")

private data class PrintedWasteZoneOption(
    val zoneId: String,
    val zone: PrintedWasteZone,
    val routingUrl: String,
    val pingMs: Long?,
)

private fun recommendedPrintedWasteZone(zones: List<PrintedWasteZoneOption>): PrintedWasteZoneOption? {
    if (zones.isEmpty()) return null
    val pool = zones.filter { it.pingMs != null }.ifEmpty { zones }
    val maxPing = pool.mapNotNull { it.pingMs }.maxOrNull()?.coerceAtLeast(1) ?: 1
    val maxQueue = pool.maxOfOrNull { it.zone.QueuePosition }?.coerceAtLeast(1) ?: 1
    return pool.minWithOrNull(
        compareBy<PrintedWasteZoneOption> { printedWasteScore(it, maxPing, maxQueue) }
            .thenBy { it.pingMs ?: Long.MAX_VALUE }
            .thenBy { it.zone.QueuePosition },
    )
}

private fun printedWasteScore(zone: PrintedWasteZoneOption, maxPing: Long, maxQueue: Int): Double {
    val pingScore = ((zone.pingMs ?: maxPing).toDouble() / maxPing.toDouble()) * 0.75
    val queueScore = (zone.zone.QueuePosition.toDouble() / maxQueue.toDouble()) * 0.25
    return pingScore + queueScore
}

private fun printedWasteZoneUrl(zoneId: String): String =
    "https://${zoneId.lowercase()}.cloudmatchbeta.nvidiagrid.net/"

private fun formatPrintedWasteWait(etaMs: Long): String {
    val minutes = ((etaMs + 59_999L) / 60_000L).coerceAtLeast(1L)
    return if (minutes < 60L) "${minutes}m" else "${minutes / 60L}h ${minutes % 60L}m"
}

private fun queueColor(queue: Int): Color = when {
    queue <= 5 -> Green
    queue <= 20 -> Color(0xffc7ef6b)
    queue <= 45 -> Color(0xffffc95a)
    else -> Color(0xffff8d8d)
}

private fun pingColor(pingMs: Long): Color = when {
    pingMs <= 60L -> Green
    pingMs <= 120L -> Color(0xffc7ef6b)
    pingMs <= 180L -> Color(0xffffc95a)
    else -> Color(0xffff8d8d)
}

private fun regionLabel(region: String): String = when (region) {
    "US" -> "North America"
    "CA" -> "Canada"
    "EU" -> "Europe"
    "JP" -> "Japan"
    "KR" -> "South Korea"
    "THAI" -> "Southeast Asia"
    "MY" -> "Malaysia"
    else -> region
}

@Composable
internal fun ProviderPicker(providers: List<LoginProvider>, selected: LoginProvider, onSelect: (LoginProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(selected.displayName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(provider)
                    },
                )
            }
        }
    }
}

private sealed interface UrlImageState {
    data object Empty : UrlImageState
    data object Loading : UrlImageState
    data object Failed : UrlImageState
    data object Loaded : UrlImageState
}

internal fun imageDataForSource(source: String): Any? {
    val key = source.trim()
    if (key.isBlank()) return null
    val uri = runCatching { Uri.parse(key) }.getOrNull() ?: return null
    val scheme = uri.scheme.orEmpty().lowercase(Locale.US)
    return when {
        scheme == "http" || scheme == "https" -> key
        scheme == "content" || scheme == "android.resource" || scheme == "file" -> uri
        scheme.isBlank() && key.startsWith("/") -> File(key)
        else -> uri
    }
}

@Composable
internal fun UrlImage(
    url: String?,
    modifier: Modifier = Modifier,
    fallbackUrl: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val source = url?.trim().orEmpty()
    val fallbackSource = fallbackUrl?.trim()?.takeIf { it.isNotBlank() && it != source }
    var activeSource by remember(source, fallbackSource) {
        mutableStateOf(source.takeIf { it.isNotBlank() } ?: fallbackSource)
    }
    var imageState by remember(source, fallbackSource) {
        mutableStateOf(if (activeSource == null) UrlImageState.Empty else UrlImageState.Loading)
    }
    val imageData = remember(activeSource) { activeSource?.let(::imageDataForSource) }
    LaunchedEffect(activeSource, imageData, fallbackSource, source) {
        if (activeSource == null) {
            imageState = UrlImageState.Empty
        } else if (imageData == null) {
            if (activeSource == source && fallbackSource != null) {
                activeSource = fallbackSource
                imageState = UrlImageState.Loading
            } else {
                imageState = UrlImageState.Failed
            }
        }
    }
    Box(modifier.background(OpenNowPalette.ImagePlaceholder), contentAlignment = Alignment.Center) {
        if (imageData != null) {
            key(activeSource) {
                AsyncImage(
                    model = imageData,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    onLoading = { imageState = UrlImageState.Loading },
                    onSuccess = { imageState = UrlImageState.Loaded },
                    onError = {
                        if (activeSource == source && fallbackSource != null) {
                            activeSource = fallbackSource
                            imageState = UrlImageState.Loading
                        } else {
                            imageState = UrlImageState.Failed
                        }
                    },
                )
            }
        }
        when (imageState) {
            UrlImageState.Loading -> LoadingShimmer(Modifier.fillMaxSize())
            UrlImageState.Loaded -> Unit
            UrlImageState.Empty,
            UrlImageState.Failed,
            -> OpenNowMark(42.dp)
        }
    }
}

@Composable
internal fun LoadingShimmer(modifier: Modifier = Modifier) {
    // Use the shared grid animation when available; the local fallback only runs while an
    // individual image placeholder is actually composed.
    // Using nullable avoids treating 0f (a valid animation start value) as "not provided".
    val reduceMotion = LocalReduceMotion.current
    val sharedPulse = LocalTvLoadingPulse.current
    val localPulse = if (!reduceMotion && LocalTvLoadingProfile.current && sharedPulse == null) {
        val transition = rememberInfiniteTransition(label = "loading-pulse-local")
        val pulse = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loading-pulse-local",
        )
        pulse
    } else {
        null
    }
    val pulse = sharedPulse ?: localPulse
    // Same rule as the shared driver above: no perpetual sweep under reduced motion.
    val shimmer = LocalShimmerOffset.current ?: if (pulse == null && !reduceMotion) run {
        val transition = rememberInfiniteTransition(label = "shimmer-local")
        val localOffset = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = SHIMMER_CYCLE_DURATION_MS, easing = LinearEasing),
            ),
            label = "shimmer-offset-local",
        )
        localOffset
    } else null
    val baseColor = OpenNowPalette.ShimmerBase
    val highlightColor1 = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    val highlightColor2 = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    Spacer(
        modifier = modifier
            .background(baseColor)
            .drawBehind {
                if (pulse != null) {
                    drawRect(highlightColor1.copy(alpha = 0.08f + pulse.value * 0.18f))
                } else {
                    val width = size.width
                    val bandWidth = (width * 0.52f).coerceAtLeast(1f)
                    // Observe the transition for draw invalidations, but anchor the visible phase
                    // to device uptime so recreating an image loader cannot jump the band back.
                    val animationFrame = shimmer?.value
                    val bandStart = shimmerBandStartX(
                        progress = if (animationFrame == null) {
                            0f
                        } else {
                            shimmerProgressAtUptime(SystemClock.uptimeMillis())
                        },
                        containerWidth = width,
                        bandWidth = bandWidth,
                    )
                    // A horizontal shader gives the sweep exact bounds. At both repeat endpoints
                    // its transparent edge only touches the card, so Restart cannot paint a
                    // backward-moving frame on tall poster placeholders.
                    val brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            highlightColor1,
                            highlightColor2,
                            highlightColor1,
                            Color.Transparent,
                        ),
                        startX = bandStart,
                        endX = bandStart + bandWidth,
                    )
                    drawRect(brush)
                }
            }
    )
}

internal fun shimmerProgressAtUptime(uptimeMillis: Long): Float {
    val cycleDurationMs = SHIMMER_CYCLE_DURATION_MS.toLong()
    return Math.floorMod(uptimeMillis, cycleDurationMs).toFloat() / cycleDurationMs
}

internal fun shimmerBandStartX(progress: Float, containerWidth: Float, bandWidth: Float): Float {
    val safeWidth = containerWidth.coerceAtLeast(0f)
    val safeBandWidth = bandWidth.coerceAtLeast(1f)
    return -safeBandWidth +
        progress.coerceIn(0f, 1f) * (safeWidth + safeBandWidth)
}

@Composable
internal fun OpenNowMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.opennow_logo_mark),
        contentDescription = "OpenNOW",
        modifier = modifier
            .width(size * 1.85f)
            .height(size),
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun OpenNowAppIcon(
    size: androidx.compose.ui.unit.Dp,
    animate: Boolean = false,
) {
    val glide = if (animate) {
        val transition = rememberInfiniteTransition(label = "app-icon-glide")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2_600, easing = LinearEasing),
            ),
            label = "app-icon-glide-phase",
        )
    } else {
        null
    }
    val glideDistancePx = with(LocalDensity.current) { (size * 0.055f).toPx() }
    val motionModifier = if (glide == null) {
        Modifier
    } else {
        // Read the animation state in the layer block so each frame only updates this GPU layer;
        // it does not remeasure the navigation rail or recompose the surrounding chrome.
        Modifier.graphicsLayer {
            val radians = glide.value * (2f * PI.toFloat())
            val horizontalGlide = sin(radians)
            translationX = horizontalGlide * glideDistancePx
            translationY = sin(radians * 2f - PI.toFloat() / 2f) * glideDistancePx * 0.24f
            rotationZ = -horizontalGlide * 0.85f
            scaleX = 1f + max(0f, horizontalGlide) * 0.018f
        }
    }
    Image(
        painter = painterResource(R.drawable.opennow_icon),
        contentDescription = "OpenNOW",
        modifier = Modifier
            .size(size)
            .then(motionModifier),
        contentScale = ContentScale.Fit,
    )
}

internal val ColorQuality.label: String
    get() = when (this) {
        ColorQuality.EightBit420 -> "8-bit 4:2:0"
        ColorQuality.EightBit444 -> "8-bit 4:4:4"
        ColorQuality.TenBit420 -> "10-bit 4:2:0"
        ColorQuality.TenBit444 -> "10-bit 4:4:4"
    }

internal val GameCardOverlayGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.95f))
)
