package com.opencloudgaming.opennow

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

internal val SettingsBackground = Color(0xff090b0d)
internal val SettingsPanel = Color(0xff11161a)
internal val SettingsPanelAlt = Color(0xff171d22)
internal val SettingsText = Color(0xffeef3f5)
internal val SettingsTextMuted = Color(0xff98a4aa)
internal const val DONATE_URL = "https://printedwaste.com/donate"
internal val PHONE_NAV_RAIL_MAX_SMALLEST_WIDTH = 600.dp
internal val APP_NAV_RAIL_WIDTH = 80.dp
internal const val PHONE_ULTRAWIDE_MIN_STREAM_ASPECT = 2.2f
internal const val PHONE_ULTRAWIDE_MIN_VIEWPORT_ASPECT = 2.0f
internal const val CATALOG_BACKGROUND_IMAGE_FILE_PREFIX = "catalog_background_image"
internal val LocalSettingsControllerNavigationEnabled = androidx.compose.runtime.staticCompositionLocalOf { false }

internal data class SettingsChoiceOption(val value: String, val label: String)
internal data class ChoiceMenuOption(
    val value: String,
    val label: String,
    val enabled: Boolean = true,
    val badge: String? = null,
)

internal enum class SearchTarget {
    Store,
    Library,
    Settings,
}

private enum class SettingsCategory(
    val title: String,
    val summary: String,
    val iconRes: Int,
) {
    General("General", "Updates, privacy, advanced options, reset", R.drawable.ic_tab_settings),
    Stream("Stream", "Resolution, FPS, codec, HDR, proxy", R.drawable.ic_tab_stream),
    Input("Input", "Mouse, keyboard, touch controls, rumble", R.drawable.ic_tab_library),
    Interface("Interface", "Color, cards, stats, controller UI", R.drawable.ic_tab_store),
    Account("Account", "Sign-in, storage, connected stores", R.drawable.ic_tab_store),
    Advanced("Advanced", "Diagnostics, debug logs, advanced tools", R.drawable.ic_search),
    About("About", "Version, credits, and support", R.drawable.ic_tab_settings),
}

internal data class LauncherBadge(
    val iconRes: Int,
    val name: String,
    val background: Color,
    val foreground: Color = SettingsText,
)

private val keyboardLayoutOptions = listOf(
    SettingsChoiceOption("en-US", "English (US)"),
    SettingsChoiceOption("en-GB", "English (UK)"),
    SettingsChoiceOption("tr-TR", "Turkish Q"),
    SettingsChoiceOption("de-DE", "German"),
    SettingsChoiceOption("fr-FR", "French"),
    SettingsChoiceOption("es-ES", "Spanish"),
    SettingsChoiceOption("es-MX", "Spanish (Latin America)"),
    SettingsChoiceOption("it-IT", "Italian"),
    SettingsChoiceOption("pt-PT", "Portuguese (Portugal)"),
    SettingsChoiceOption("pt-BR", "Portuguese (Brazil)"),
    SettingsChoiceOption("pl-PL", "Polish"),
    SettingsChoiceOption("ru-RU", "Russian"),
    SettingsChoiceOption("ja-JP", "Japanese"),
    SettingsChoiceOption("ko-KR", "Korean"),
    SettingsChoiceOption("zh-CN", "Chinese (Simplified)"),
    SettingsChoiceOption("zh-TW", "Chinese (Traditional)"),
)

private val gameLanguageOptions = listOf(
    SettingsChoiceOption("en_US", "English (US)"),
    SettingsChoiceOption("en_GB", "English (UK)"),
    SettingsChoiceOption("de_DE", "Deutsch"),
    SettingsChoiceOption("fr_FR", "Francais"),
    SettingsChoiceOption("es_ES", "Espanol (ES)"),
    SettingsChoiceOption("es_MX", "Espanol (MX)"),
    SettingsChoiceOption("it_IT", "Italiano"),
    SettingsChoiceOption("pt_PT", "Portugues (PT)"),
    SettingsChoiceOption("pt_BR", "Portugues (BR)"),
    SettingsChoiceOption("ru_RU", "Russian"),
    SettingsChoiceOption("pl_PL", "Polish"),
    SettingsChoiceOption("tr_TR", "Turkish"),
    SettingsChoiceOption("ar_SA", "Arabic"),
    SettingsChoiceOption("ja_JP", "Japanese"),
    SettingsChoiceOption("ko_KR", "Korean"),
    SettingsChoiceOption("zh_CN", "Chinese (Simplified)"),
    SettingsChoiceOption("zh_TW", "Chinese (Traditional)"),
    SettingsChoiceOption("th_TH", "Thai"),
    SettingsChoiceOption("vi_VN", "Vietnamese"),
    SettingsChoiceOption("id_ID", "Indonesian"),
    SettingsChoiceOption("cs_CZ", "Czech"),
    SettingsChoiceOption("el_GR", "Greek"),
    SettingsChoiceOption("hu_HU", "Hungarian"),
    SettingsChoiceOption("ro_RO", "Romanian"),
    SettingsChoiceOption("uk_UA", "Ukrainian"),
    SettingsChoiceOption("nl_NL", "Dutch"),
    SettingsChoiceOption("sv_SE", "Swedish"),
    SettingsChoiceOption("da_DK", "Danish"),
    SettingsChoiceOption("fi_FI", "Finnish"),
    SettingsChoiceOption("no_NO", "Norwegian"),
)

@Composable
internal fun SettingsScreen(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    tvProfile: Boolean,
    searchRequested: Boolean,
    searchQuery: String,
    backRequestToken: Int,
    onSearchQueryChange: (String) -> Unit,
    onDetailRouteChange: (Boolean) -> Unit,
) {
    var showSessionProxyWarning by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    val scrollState = rememberScrollState()
    val searchFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val controllerNavigationEnabled =
        tvProfile || rememberPhysicalControllerConnected(enabled = !tvProfile)
    val showSearch = searchRequested || searchQuery.isNotBlank()
    val categories = remember { settingsCategories() }
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            delay(90)
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }
    LaunchedEffect(categories) {
        if (selectedCategory != null && selectedCategory !in settingsDetailCategories()) {
            selectedCategory = null
        }
    }
    LaunchedEffect(state.settingsRouteTarget) {
        if (state.settingsRouteTarget == SettingsRouteTarget.General) {
            onSearchQueryChange("")
            selectedCategory = SettingsCategory.General
            viewModel.consumeSettingsRouteTarget(SettingsRouteTarget.General)
        }
    }
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }
    LaunchedEffect(selectedCategory, controllerNavigationEnabled) {
        val detailOpen = selectedCategory != null
        onDetailRouteChange(detailOpen)
        if (detailOpen && controllerNavigationEnabled) {
            delay(90)
            runCatching { detailFocusRequester.requestFocus() }
        }
    }
    LaunchedEffect(backRequestToken) {
        if (backRequestToken > 0 && selectedCategory != null) {
            selectedCategory = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { onDetailRouteChange(false) }
    }
    CompositionLocalProvider(LocalSettingsControllerNavigationEnabled provides controllerNavigationEnabled) {
        if (showSessionProxyWarning) {
            SessionProxyWarningDialog(
                onCancel = { showSessionProxyWarning = false },
                onEnable = {
                    viewModel.updateStreamSettings { s -> s.copy(sessionProxyEnabled = true) }
                    showSessionProxyWarning = false
                },
            )
        }
        if (tvProfile) {
            SwipeToRefreshContainer(
                refreshing = state.settingsRefreshing,
                onRefresh = viewModel::refreshSettings,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(SettingsBackground)
                        .onPreviewKeyEvent { handleVerticalDpadFocusMove(it, focusManager) }
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AnimatedVisibility(visible = showSearch) {
                        NativeSearchField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            placeholder = stringResource(R.string.search_settings),
                            focusRequester = searchFocusRequester,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AnimatedContent(targetState = selectedCategory, label = "settings-route") { category ->
                        SettingsBody(
                            state = state,
                            viewModel = viewModel,
                            searchQuery = searchQuery,
                            selectedCategory = category,
                            categories = categories,
                            detailFocusRequester = detailFocusRequester,
                            onSelectCategory = { selectedCategory = it },
                            onBack = { selectedCategory = null },
                            showSessionProxyWarning = { showSessionProxyWarning = true },
                        )
                    }
                }
            }
        } else {
            SwipeToRefreshContainer(
                refreshing = state.settingsRefreshing,
                onRefresh = viewModel::refreshSettings,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .background(SettingsBackground),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        AnimatedVisibility(visible = showSearch) {
                            NativeSearchField(
                                query = searchQuery,
                                onQueryChange = onSearchQueryChange,
                                placeholder = stringResource(R.string.search_settings),
                                focusRequester = searchFocusRequester,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    item {
                        AnimatedContent(targetState = selectedCategory, label = "settings-route") { category ->
                            SettingsBody(
                                state = state,
                                viewModel = viewModel,
                                searchQuery = searchQuery,
                                selectedCategory = category,
                                categories = categories,
                                detailFocusRequester = detailFocusRequester,
                                onSelectCategory = { selectedCategory = it },
                                onBack = { selectedCategory = null },
                                showSessionProxyWarning = { showSessionProxyWarning = true },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsBody(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    searchQuery: String,
    selectedCategory: SettingsCategory?,
    categories: List<SettingsCategory>,
    detailFocusRequester: FocusRequester,
    onSelectCategory: (SettingsCategory) -> Unit,
    onBack: () -> Unit,
    showSessionProxyWarning: () -> Unit,
) {
    when {
        searchQuery.isNotBlank() -> {
            SettingsContent(
                state = state,
                viewModel = viewModel,
                searchQuery = searchQuery,
                selectedCategory = null,
                showSessionProxyWarning = showSessionProxyWarning,
            )
        }
        selectedCategory == null -> {
            SettingsCategoryLanding(
                state = state,
                viewModel = viewModel,
                categories = categories,
                onSelectCategory = onSelectCategory,
            )
        }
        else -> {
            Column(
                Modifier
                    .fillMaxWidth()
                    .lockedFocusGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsDetailHeader(category = selectedCategory, onBack = onBack)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(detailFocusRequester)
                        .focusGroup(),
                ) {
                    SettingsContent(
                        state = state,
                        viewModel = viewModel,
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        showSessionProxyWarning = showSessionProxyWarning,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    searchQuery: String,
    selectedCategory: SettingsCategory?,
    showSessionProxyWarning: () -> Unit,
) {
    val settings = state.settings
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    CategorySettingsSection(selectedCategory, SettingsCategory.General, searchQuery, "App updates", "update", "updates", "disable update checking", "checking", "check", "download", "install", "apk") {
                if (state.androidUpdate.apkUpdatesAllowed) {
                    SettingSwitch(stringResource(R.string.settings_disable_update_checking), !settings.autoCheckForUpdates) { disabled ->
                        viewModel.updateSettings(settings.copy(autoCheckForUpdates = !disabled))
                    }
                }
                AndroidUpdatePanel(state = state, viewModel = viewModel)
            }
    CategorySettingsSection(selectedCategory, SettingsCategory.General, searchQuery, stringResource(R.string.settings_nerd_mode), "advanced", "advanced options", "nerd", "experimental", "diagnostics", "catalog", "cave", "background", "wallpaper", "image", "custom") {
                AdvancedOptionsSettings(settings = settings, viewModel = viewModel)
            }
    CategorySettingsSection(selectedCategory, SettingsCategory.General, searchQuery, "Privacy", "privacy", "analytics", "telemetry", "posthog", "usage", "tracking", "opt out") {
                SettingSwitch("Share usage analytics", settings.analyticsSharingEnabled) { enabled ->
                    viewModel.updateSettings(
                        settings.copy(
                            analyticsConsentAsked = true,
                            analyticsOptOut = !enabled,
                        ),
                    )
                }
            }
    CategorySettingsSection(selectedCategory, SettingsCategory.Stream, searchQuery, stringResource(R.string.settings_section_stream), "stream", "preset", "data saver", "low", "medium", "high", "custom", "resolution", "aspect ratio", "fps", "bitrate", "codec", "color", "hdr", "sharpening", "region", "session proxy", "proxy", "l4s", "cloud g-sync", "vrr") {
                val fallbackMembershipTier = state.authSession?.user?.membershipTier
                ChoiceMenuRow(
                    label = stringResource(R.string.settings_stream_preset),
                    options = StreamPreset.entries.map { preset ->
                        ChoiceMenuOption(
                            value = preset.name,
                            label = streamPresetLabel(preset),
                        )
                    },
                    selectedLabel = streamPresetLabel(settings.streamPreset),
                ) { value ->
                    viewModel.applyStreamPreset(StreamPreset.valueOf(value))
                }
                val resolutionChoices = streamResolutionChoicesForAspect(settings.stream.aspectRatio).ifEmpty {
                    streamResolutionChoicesForAspect("16:9")
                }
                val selectedResolution = normalizeStreamResolutionForAspectAndPlan(
                    settings.stream.resolution,
                    settings.stream.aspectRatio,
                    state.subscriptionInfo,
                    fallbackMembershipTier,
                )
                ChoiceMenuRow(
                    label = stringResource(R.string.settings_resolution),
                    options = resolutionChoices.map { choice ->
                        val available = choice.isAvailableFor(state.subscriptionInfo, fallbackMembershipTier)
                        ChoiceMenuOption(
                            value = choice.value,
                            label = choice.label,
                            enabled = available,
                            badge = if (available) null else choice.requiredPlanLabel,
                        )
                    },
                    selectedLabel = resolutionChoices.firstOrNull { it.value == selectedResolution }?.label ?: selectedResolution,
                ) {
                    viewModel.updateStreamSettings { s -> s.copy(resolution = it) }
                }
                ChoiceMenuRow(
                    label = stringResource(R.string.settings_aspect_ratio),
                    options = streamAspectRatioOptions().map { aspectRatio ->
                        val choices = streamResolutionChoicesForAspect(aspectRatio)
                        val available = choices.any { it.isAvailableFor(state.subscriptionInfo, fallbackMembershipTier) }
                        ChoiceMenuOption(
                            value = aspectRatio,
                            label = aspectRatio,
                            enabled = available,
                            badge = if (available) null else choices.firstNotNullOfOrNull { it.requiredPlanLabel },
                        )
                    },
                    selectedLabel = settings.stream.aspectRatio,
                ) {
                    viewModel.updateStreamSettings { s ->
                        s.copy(
                            aspectRatio = it,
                            resolution = normalizeStreamResolutionForAspectAndPlan(
                                s.resolution,
                                it,
                                state.subscriptionInfo,
                                fallbackMembershipTier,
                            ),
                        )
                    }
                }
                SettingSwitch(stringResource(R.string.settings_stretch_stream_to_fill), settings.stretchStreamToZoom) { enabled ->
                    viewModel.updateSettings(
                        settings.copy(
                            stretchStreamToZoom = enabled,
                            stretchStreamToFill = if (enabled) false else settings.stretchStreamToFill
                        )
                    )
                }
                SettingSwitch(stringResource(R.string.settings_stretch_stream_to_zoom), settings.stretchStreamToFill) { enabled ->
                    viewModel.updateSettings(
                        settings.copy(
                            stretchStreamToFill = enabled,
                            stretchStreamToZoom = if (enabled) false else settings.stretchStreamToZoom
                        )
                    )
                }
                val maxFps = maxStreamFpsFor(state.subscriptionInfo, fallbackMembershipTier)
                NumberSlider(stringResource(R.string.settings_fps), settings.stream.fps.coerceAtMost(maxFps).toFloat(), 30f, maxFps.toFloat(), 30f) {
                    val fps = it.roundToInt().coerceIn(30, maxFps)
                    viewModel.updateStreamSettings { s -> s.copy(fps = fps) }
                }
                NumberSlider(
                    label = stringResource(R.string.settings_bitrate),
                    value = settings.stream.maxBitrateMbps.toFloat(),
                    min = 1f,
                    max = 150f,
                    step = 1f,
                    descriptionProvider = { mbps ->
                        "Est. data usage: %.1f GB/hour".format((mbps * 3600f) / (8f * 1000f))
                    }
                ) {
                    viewModel.updateStreamSettings { s -> s.copy(maxBitrateMbps = it.roundToInt()) }
                }
                val comingSoonLabel = stringResource(R.string.option_coming_soon)
                val unavailableLabel = "Unavailable"
                val settingsAvailableStream = settings.stream.withAndroidSettingsAvailability()
                val effectiveCodec = settingsAvailableStream.adjustedForDevice(state.codecReport).codec
                ChoiceMenuRow(
                    label = stringResource(R.string.settings_codec),
                    options = VideoCodec.entries.map { codec ->
                        val launchUsable = state.codecReport
                            ?.capabilities
                            ?.firstOrNull { it.codec == codec }
                            ?.streamingDecoderUsableForLaunch()
                            ?: true
                        val settingsAvailable = codec.availableForAndroidSettings()
                        val available = settingsAvailable && launchUsable
                        ChoiceMenuOption(
                            value = codec.name,
                            label = codec.name,
                            enabled = available,
                            badge = when {
                                available -> null
                                !settingsAvailable -> comingSoonLabel
                                else -> unavailableLabel
                            },
                        )
                    },
                    selectedLabel = if (effectiveCodec == settings.stream.codec) {
                        settings.stream.codec.name
                    } else {
                        "${settings.stream.codec.name} -> ${effectiveCodec.name}"
                    },
                ) {
                    viewModel.updateStreamSettings { s ->
                        s.copy(codec = VideoCodec.valueOf(it)).withCodecColorCompatibility()
                    }
                }
                val effectiveColorQuality = settingsAvailableStream.withCodecColorCompatibility().colorQuality
                ChoiceMenuRow(
                    label = stringResource(R.string.settings_color),
                    options = ColorQuality.entries.map { quality ->
                        val available = quality.availableForCodec(settingsAvailableStream.codec)
                        ChoiceMenuOption(
                            value = quality.name,
                            label = quality.label,
                            enabled = available,
                            badge = if (available) null else comingSoonLabel,
                        )
                    },
                    selectedLabel = if (effectiveColorQuality == settings.stream.colorQuality) {
                        settings.stream.colorQuality.label
                    } else {
                        "${settings.stream.colorQuality.label} -> ${effectiveColorQuality.label}"
                    },
                ) { value ->
                    viewModel.updateStreamSettings { s ->
                        s.copy(colorQuality = ColorQuality.valueOf(value)).withCodecColorCompatibility()
                    }
                }
                val hdrAvailable = hasUltimateStreamingPlan(state.subscriptionInfo, fallbackMembershipTier)
                SettingSwitch(
                    stringResource(R.string.settings_hdr),
                    settings.stream.hdrEnabled && hdrAvailable,
                    enabled = hdrAvailable,
                ) { enabled ->
                    viewModel.updateStreamSettings { s ->
                        s.copy(
                            hdrEnabled = enabled,
                            colorQuality = if (enabled && !s.colorQuality.name.startsWith("TenBit")) ColorQuality.TenBit420 else s.colorQuality,
                        ).withCodecColorCompatibility()
                    }
                }
                SettingSwitch("Stream sharpening", settings.stream.streamSharpeningEnabled) {
                    viewModel.updateStreamSettings { s -> s.copy(streamSharpeningEnabled = it) }
                }
                if (settings.stream.streamSharpeningEnabled) {
                    NumberSlider("Sharpness amount", settings.stream.streamSharpeningAmount, 0f, 1f, 0.05f) {
                        viewModel.updateStreamSettings { s -> s.copy(streamSharpeningAmount = it) }
                    }
                }
                ChoiceRow(stringResource(R.string.settings_region), listOf(stringResource(R.string.option_auto)) + state.regions.map { it.name }, state.regions.firstOrNull { it.url == settings.stream.region }?.name ?: stringResource(R.string.option_auto)) { label ->
                    val url = state.regions.firstOrNull { it.name == label }?.url.orEmpty()
                    viewModel.updateStreamSettings { s -> s.copy(region = url) }
                }
                SettingSwitch(stringResource(R.string.settings_session_proxy), settings.stream.sessionProxyEnabled) { enabled ->
                    if (enabled) {
                        showSessionProxyWarning()
                    } else {
                        viewModel.updateStreamSettings { s -> s.copy(sessionProxyEnabled = false) }
                    }
                }
                Text(
                    stringResource(R.string.settings_session_proxy_hint),
                    color = SettingsTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (settings.stream.sessionProxyEnabled) {
                    OutlinedTextField(
                        value = settings.stream.sessionProxyUrl,
                        onValueChange = { value -> viewModel.updateStreamSettings { s -> s.copy(sessionProxyUrl = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_session_proxy_url)) },
                        placeholder = { Text("http://127.0.0.1:8080") },
                    )
                }
                SettingSwitch(
                    label = stringResource(R.string.settings_l4s),
                    checked = settings.stream.enableL4S,
                    description = stringResource(R.string.settings_l4s_desc),
                ) {
                    viewModel.updateStreamSettings { s -> s.copy(enableL4S = it) }
                }
                SettingSwitch(
                    label = stringResource(R.string.settings_cloud_gsync),
                    checked = settings.stream.enableCloudGsync,
                    description = stringResource(R.string.settings_cloud_gsync_desc),
                ) {
                    viewModel.updateStreamSettings { s -> s.copy(enableCloudGsync = it) }
                }
            }
    CategorySettingsSection(selectedCategory, SettingsCategory.Input, searchQuery, "Input", "input", "mouse", "sensitivity", "acceleration", "keyboard", "layout", "language", "clipboard", "paste", "rumble", "touch", "finger", "opacity", "edge", "padding", "offset", "controls", "stick", "button") {
                NumberSlider("Mouse sensitivity", settings.stream.mouseSensitivity, 0.25f, 3f, 0.05f) {
                    viewModel.updateStreamSettings { s -> s.copy(mouseSensitivity = it) }
                }
                NumberSlider("Mouse acceleration", settings.stream.mouseAcceleration.toFloat(), 1f, 150f, 1f) {
                    viewModel.updateStreamSettings { s -> s.copy(mouseAcceleration = it.roundToInt()) }
                }
                ChoiceOptionRow("Keyboard layout", keyboardLayoutOptions, settings.stream.keyboardLayout) {
                    viewModel.updateStreamSettings { s -> s.copy(keyboardLayout = it) }
                }
                ChoiceOptionRow("Game language", gameLanguageOptions, settings.stream.gameLanguage) {
                    viewModel.updateStreamSettings { s -> s.copy(gameLanguage = it) }
                }
                SettingSwitch("Clipboard paste", settings.clipboardPaste) { enabled -> viewModel.updateSettings(settings.copy(clipboardPaste = enabled)) }
                SettingSwitch("Phone rumble fallback", settings.phoneRumbleFallback) { enabled -> viewModel.updateSettings(settings.copy(phoneRumbleFallback = enabled)) }
                SettingSwitch(
                    label = "Mouse mode (Left stick)",
                    checked = settings.controllerMouseEmulation,
                    description = "Toggle in Stream Controls per session. Left stick moves the cursor, A button clicks, B button right-clicks.",
                ) { enabled ->
                    viewModel.updateSettings(settings.copy(controllerMouseEmulation = enabled))
                }
                SettingSwitch("Touch controls", settings.androidTouch.enabled) { enabled -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(enabled = enabled))) }
                val touchStyleOptions = listOf(
                    SettingsChoiceOption(TouchControllerStyle.V1.name, "V1 (Solid)"),
                    SettingsChoiceOption(TouchControllerStyle.V2.name, "V2 (Clean Outline)")
                )
                ChoiceOptionRow("Touch controller style", touchStyleOptions, settings.androidTouch.touchControllerStyle.name) { styleName ->
                    val style = TouchControllerStyle.valueOf(styleName)
                    viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(touchControllerStyle = style)))
                }
                SettingSwitch("Finger mouse", settings.androidTouch.mousePad) { enabled -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(mousePad = enabled))) }
                if (settings.androidTouch.mousePad) {
                    Box(Modifier.padding(start = 24.dp)) {
                        SettingSwitch("Direct click", settings.androidTouch.mouseDirectClick) { enabled -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(mouseDirectClick = enabled))) }
                    }
                }
                NumberSlider("Touch layout scale", settings.androidTouch.scale, 0.6f, 1.4f, 0.05f) { value -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(scale = value))) }
                NumberSlider("Touch button size", settings.androidTouch.buttonScale, 0.65f, 1.5f, 0.05f) { value -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(buttonScale = value))) }
                NumberSlider("Touch stick size", settings.androidTouch.stickScale, 0.65f, 1.5f, 0.05f) { value -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(stickScale = value))) }
                NumberSlider("Touch opacity", settings.androidTouch.opacity, 0.15f, 1f, 0.05f) { value -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(opacity = value))) }
                NumberSlider("Touch edge padding", settings.androidTouch.edgePaddingDp, 0f, 72f, 1f) { value -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(edgePaddingDp = value))) }
                NumberSlider("Touch bottom padding", settings.androidTouch.bottomPaddingDp, 0f, 120f, 1f) { value -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(bottomPaddingDp = value))) }
                NumberSlider("Left controls horizontal offset", settings.androidTouch.leftOffsetXDp, -220f, 220f, 2f) { value -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(leftOffsetXDp = value))) }
                NumberSlider("Left controls vertical offset", settings.androidTouch.leftOffsetYDp, -160f, 160f, 2f) { value -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(leftOffsetYDp = value))) }
                NumberSlider("Right controls horizontal offset", settings.androidTouch.rightOffsetXDp, -220f, 220f, 2f) { value -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(rightOffsetXDp = value))) }
                NumberSlider("Right controls vertical offset", settings.androidTouch.rightOffsetYDp, -160f, 160f, 2f) { value -> viewModel.updateSettings(settings.copy(androidTouch = settings.androidTouch.copy(rightOffsetYDp = value))) }
            }
    CategorySettingsSection(selectedCategory, SettingsCategory.Interface, searchQuery, stringResource(R.string.settings_section_interface), "interface", "ui", "system colors", "accent", "launch page", "default page", "store", "library", "nerd", "expressive", "compact", "cards", "store labels", "game card size", "stats", "position", "server selector", "controller", "sounds", "button", "tone", "tv", "safe area", "screen padding", "overscan", "session counter", "intro", "music", "queue", "stretch", "fill") {
                val accentOptions = UiAccent.entries.map { it to uiAccentLabel(it) }
                SettingSwitch(stringResource(R.string.settings_dynamic_color), settings.dynamicColor) { viewModel.updateSettings(settings.copy(dynamicColor = it)) }
                ChoiceRow(stringResource(R.string.settings_accent), accentOptions.map { it.second }, accentOptions.firstOrNull { it.first == settings.uiAccent }?.second ?: accentOptions.first().second) { label ->
                    accentOptions.firstOrNull { it.second == label }?.first?.let { accent ->
                        viewModel.updateSettings(settings.copy(uiAccent = accent))
                    }
                }
                val launchPageOptions = AppLaunchPage.entries.map { page -> page to appLaunchPageLabel(page) }
                ChoiceRow(
                    stringResource(R.string.settings_launch_page),
                    launchPageOptions.map { it.second },
                    launchPageOptions.firstOrNull { it.first == settings.launchPage }?.second
                        ?: launchPageOptions.first().second,
                ) { label ->
                    launchPageOptions.firstOrNull { it.second == label }?.first?.let { page ->
                        viewModel.updateSettings(settings.copy(launchPage = page))
                    }
                }
                SettingSwitch(
                    label = stringResource(R.string.settings_expressive_ui),
                    checked = settings.expressiveUi,
                    description = stringResource(R.string.settings_expressive_ui_desc),
                ) {
                    viewModel.updateSettings(settings.copy(expressiveUi = it))
                }
                SettingSwitch(stringResource(R.string.settings_compact_cards), settings.compactGameCards) { viewModel.updateSettings(settings.copy(compactGameCards = it)) }
                SettingSwitch(stringResource(R.string.settings_show_store_labels), settings.showGameStoreLabels) { viewModel.updateSettings(settings.copy(showGameStoreLabels = it)) }
                NumberSlider(stringResource(R.string.settings_card_size), settings.posterSizeScale, MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE, 0.05f) { value ->
                    viewModel.updateSettings(settings.copy(posterSizeScale = value))
                }
                NumberSlider(stringResource(R.string.settings_tv_safe_area), settings.tvSafeAreaPaddingDp, 0f, 72f, 2f) { value ->
                    viewModel.updateSettings(settings.copy(tvSafeAreaPaddingDp = value))
                }
                SettingSwitch(stringResource(R.string.settings_show_stats), settings.showStatsOnLaunch) { viewModel.updateSettings(settings.copy(showStatsOnLaunch = it)) }
                ChoiceRow("Status bar appearance", StreamStatsStyle.entries.map { it.label }, settings.streamStatsStyle.label) { label ->
                    StreamStatsStyle.entries.firstOrNull { it.label == label }?.let { style ->
                        viewModel.updateSettings(settings.copy(streamStatsStyle = style))
                    }
                }
                ChoiceRow(stringResource(R.string.settings_stats_position), StreamStatsPosition.entries.map { it.label }, settings.streamStatsPosition.label) { label ->
                    StreamStatsPosition.entries.firstOrNull { it.label == label }?.let { position ->
                        viewModel.updateSettings(settings.copy(streamStatsPosition = position))
                    }
                }
                SettingSwitch(stringResource(R.string.settings_hide_server_selector), settings.hideServerSelector) { viewModel.updateSettings(settings.copy(hideServerSelector = it)) }
                SettingSwitch(
                    label = stringResource(R.string.settings_button_press_tones),
                    checked = settings.controllerUiSounds,
                    description = stringResource(R.string.settings_button_press_tones_desc),
                ) { enabled ->
                    viewModel.updateSettings(settings.copy(controllerUiSounds = enabled))
                }
                SettingSwitch(stringResource(R.string.settings_session_counter), settings.sessionCounterEnabled) { viewModel.updateSettings(settings.copy(sessionCounterEnabled = it)) }
                SettingSwitch(stringResource(R.string.settings_stream_intro_music), settings.streamIntroMusic) { enabled ->
                    viewModel.updateSettings(settings.copy(streamIntroMusic = enabled))
                }
                if (settings.streamIntroMusic) {
                    val introStartOptions = IntroMusicStartMode.entries.map { mode ->
                        mode to introMusicStartModeLabel(mode)
                    }
                    ChoiceRow(
                        stringResource(R.string.settings_stream_intro_music_start),
                        introStartOptions.map { it.second },
                        introStartOptions.firstOrNull { it.first == settings.streamIntroStartMode }?.second
                            ?: introStartOptions.first().second,
                    ) { label ->
                        introStartOptions.firstOrNull { it.second == label }?.first?.let { mode ->
                            viewModel.updateSettings(settings.copy(streamIntroStartMode = mode))
                        }
                    }
                }
                SettingSwitch(stringResource(R.string.settings_queue_ready_music), settings.queueReadyMusic) { enabled ->
                    viewModel.updateSettings(settings.copy(queueReadyMusic = enabled))
                }
            }
    CategorySettingsSection(selectedCategory, SettingsCategory.General, searchQuery, "App Data", "app data", "data", "cache", "clear", "reset", "settings", "tutorial", "guide", "wipe", "relaunch", "fresh install") {
                AppDataSettingsPanel(viewModel = viewModel)
            }
    CategorySettingsSection(selectedCategory, SettingsCategory.Account, searchQuery, "Account", "account", "login", "logout", "sign in", "saved", "provider", "membership", "subscription") {
                AccountSettingsPanel(state = state, viewModel = viewModel)
            }
    CategorySettingsSection(selectedCategory, SettingsCategory.Advanced, searchQuery, "Codec Diagnostics", "codec", "diagnostics", "probe", "av1", "h264", "h265", "hevc", "decode") {
                    CodecDiagnosticsPanel(state.codecReport)
                }
    CategorySettingsSection(selectedCategory, SettingsCategory.Advanced, searchQuery, "Debug Logs", "debug", "logs", "logcat", "events", "export", "json", "cloudmatch", "queue", "stream") {
                    DebugLogsPanel(state = state, viewModel = viewModel)
                }
    CategorySettingsSection(selectedCategory, SettingsCategory.Advanced, searchQuery, "Battery Optimization", "battery", "optimization", "background", "activity", "ignore", "allow", "run") {
                    BatteryOptimizationPanel()
                }
    CategorySettingsSection(selectedCategory, SettingsCategory.About, searchQuery, "About", "about", "version", "build", "app", "github", "developer", "kiefer", "zortos", "opennow", "repository") {
                AppVersionPanel()
                OpenNowGitHubPanel()
                DeveloperPanel()
            }
    CategorySettingsSection(selectedCategory, SettingsCategory.About, searchQuery, stringResource(R.string.settings_section_thanks), "thanks", "credits", "contributors", "darkevilpt", "donate", "paypal", "printedwaste") {
                ThanksPanel()
            }
    }
}

@Composable
private fun SettingsCategoryLanding(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    categories: List<SettingsCategory>,
    onSelectCategory: (SettingsCategory) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsAccountCard(
            state = state,
            onClick = { onSelectCategory(SettingsCategory.Account) },
        )
        AndroidUpdateNoticeRow(
            update = state.androidUpdate,
            dismissedKey = state.dismissedAndroidUpdateNoticeKey,
            onOpenUpdates = { onSelectCategory(SettingsCategory.General) },
            onDismiss = viewModel::dismissAndroidUpdateNotice,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                categories.forEach { category ->
                    SettingsCategoryRow(
                        category = category,
                        onClick = { onSelectCategory(category) },
                    )
                }
            }
        }
        SearchableSettingsSection("", "About", "about", "version", "build", "app", "github", "developer", "kiefer", "zortos", "opennow", "repository") {
            AppVersionPanel()
            OpenNowGitHubPanel()
            DeveloperPanel()
        }
        SearchableSettingsSection("", stringResource(R.string.settings_section_thanks), "thanks", "credits", "contributors", "darkevilpt", "donate", "paypal", "printedwaste") {
            ThanksPanel()
        }
    }
}

@Composable
private fun AdvancedOptionsSettings(settings: AppSettings, viewModel: OpenNowViewModel) {
    SettingSwitch(
        label = stringResource(R.string.settings_nerd_mode),
        checked = settings.nerdMode,
        description = stringResource(R.string.settings_nerd_mode_desc),
    ) {
        viewModel.updateSettings(settings.copy(nerdMode = it))
    }
    SettingSwitch(
        label = stringResource(R.string.settings_nerd_catalog_background),
        checked = settings.nerdCatalogBackground,
        description = stringResource(R.string.settings_nerd_catalog_background_desc),
    ) {
        viewModel.updateSettings(settings.copy(nerdCatalogBackground = it))
    }
    if (settings.nerdCatalogBackground) {
        CatalogBackgroundImageSetting(settings = settings, viewModel = viewModel)
    }
}

@Composable
private fun CatalogBackgroundImageSetting(settings: AppSettings, viewModel: OpenNowViewModel) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val currentSettings by rememberUpdatedState(settings)
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        takePersistableImageReadPermission(context, uri)
        scope.launch {
            val newUri = withContext(Dispatchers.IO) {
                persistCatalogBackgroundImage(appContext, uri)
            }
            val previousUri = currentSettings.nerdCatalogBackgroundUri
            if (newUri != uri.toString()) {
                releasePersistableImageReadPermission(context, uri.toString())
            }
            viewModel.updateSettings(
                currentSettings.copy(
                    nerdCatalogBackground = true,
                    nerdCatalogBackgroundUri = newUri,
                ),
            )
            if (!previousUri.isNullOrBlank() && previousUri != newUri) {
                releasePersistableImageReadPermission(context, previousUri)
            }
            pruneStoredCatalogBackgroundImages(appContext, keepUri = newUri)
        }
    }
    val customBackgroundUri = settings.nerdCatalogBackgroundUri?.takeIf { it.isNotBlank() }
    val hasCustomBackground = customBackgroundUri != null
    val presetOptions = listOf(
        CatalogBackgroundPreset.ColorfulAbstract to stringResource(R.string.catalog_background_colorful_abstract),
        CatalogBackgroundPreset.Original to stringResource(R.string.catalog_background_original),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.settings_catalog_background_image),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (hasCustomBackground) {
                            stringResource(R.string.settings_catalog_background_image_custom)
                        } else {
                            presetOptions.first { it.first == settings.catalogBackgroundPreset }.second
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ChoiceRow(
                label = stringResource(R.string.settings_catalog_background_built_in),
                options = presetOptions.map { it.second },
                selected = presetOptions.first { it.first == settings.catalogBackgroundPreset }.second,
            ) { selectedLabel ->
                val selectedPreset = presetOptions.firstOrNull { it.second == selectedLabel }?.first
                    ?: return@ChoiceRow
                viewModel.updateSettings(
                    settings.copy(
                        catalogBackgroundPreset = selectedPreset,
                        nerdCatalogBackgroundUri = null,
                    ),
                )
                customBackgroundUri?.let { releasePersistableImageReadPermission(context, it) }
                pruneStoredCatalogBackgroundImages(appContext)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_choose_image), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (hasCustomBackground) {
                    OutlinedButton(
                        onClick = {
                            viewModel.updateSettings(settings.copy(nerdCatalogBackgroundUri = null))
                            releasePersistableImageReadPermission(context, customBackgroundUri)
                            pruneStoredCatalogBackgroundImages(appContext)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_use_default), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun takePersistableImageReadPermission(context: android.content.Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun persistCatalogBackgroundImage(context: android.content.Context, uri: Uri): String {
    val uniqueId = UUID.randomUUID().toString()
    val target = File(context.filesDir, "$CATALOG_BACKGROUND_IMAGE_FILE_PREFIX-$uniqueId")
    val temp = File(context.cacheDir, "$CATALOG_BACKGROUND_IMAGE_FILE_PREFIX-$uniqueId.tmp")
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return uri.toString()
        input.use {
            temp.outputStream().use { output ->
                it.copyTo(output)
            }
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
        }
        Uri.fromFile(target).toString()
    } catch (_: Exception) {
        target.delete()
        uri.toString()
    } finally {
        temp.delete()
    }
}

internal fun isManagedCatalogBackgroundImageFile(filesDir: File, candidate: File): Boolean {
    val normalizedFilesDir = runCatching { filesDir.canonicalFile }.getOrElse { filesDir.absoluteFile }
    val normalizedCandidate = runCatching { candidate.canonicalFile }.getOrElse { candidate.absoluteFile }
    val managedName = normalizedCandidate.name == CATALOG_BACKGROUND_IMAGE_FILE_PREFIX ||
        normalizedCandidate.name.startsWith("$CATALOG_BACKGROUND_IMAGE_FILE_PREFIX-")
    return normalizedCandidate.parentFile == normalizedFilesDir && managedName
}

private fun pruneStoredCatalogBackgroundImages(context: android.content.Context, keepUri: String? = null) {
    val keepFile = keepUri
        ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
        ?.path
        ?.let(::File)
        ?.let { file -> runCatching { file.canonicalFile }.getOrElse { file.absoluteFile } }
    runCatching {
        context.filesDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { isManagedCatalogBackgroundImageFile(context.filesDir, it) }
            .filterNot { candidate ->
                val normalized = runCatching { candidate.canonicalFile }.getOrElse { candidate.absoluteFile }
                normalized == keepFile
            }
            .forEach(File::delete)
        context.cacheDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter {
                it.name == "$CATALOG_BACKGROUND_IMAGE_FILE_PREFIX.tmp" ||
                    (it.name.startsWith("$CATALOG_BACKGROUND_IMAGE_FILE_PREFIX-") && it.name.endsWith(".tmp"))
            }
            .forEach(File::delete)
    }
}

private fun releasePersistableImageReadPermission(context: android.content.Context, uriString: String) {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
    runCatching {
        context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

@Composable
private fun streamPresetLabel(preset: StreamPreset): String =
    when (preset) {
        StreamPreset.Recommended -> stringResource(R.string.stream_preset_recommended)
        StreamPreset.Custom -> stringResource(R.string.stream_preset_custom)
        StreamPreset.LowDataSaver -> stringResource(R.string.stream_preset_low_data_saver)
        StreamPreset.Medium -> stringResource(R.string.stream_preset_medium)
        StreamPreset.High -> stringResource(R.string.stream_preset_high)
    }

@Composable
private fun introMusicStartModeLabel(mode: IntroMusicStartMode): String =
    when (mode) {
        IntroMusicStartMode.Muted -> stringResource(R.string.intro_music_start_muted)
        IntroMusicStartMode.Playing -> stringResource(R.string.intro_music_start_playing)
    }

@Composable
private fun appLaunchPageLabel(page: AppLaunchPage): String =
    when (page) {
        AppLaunchPage.Store -> stringResource(R.string.launch_page_store)
        AppLaunchPage.Library -> stringResource(R.string.launch_page_library)
    }

@Composable
private fun SettingsAccountCard(state: OpenNowUiState, onClick: () -> Unit) {
    val account = state.savedAccounts.firstOrNull { it.userId == state.authSession?.user?.userId }
        ?: state.savedAccounts.firstOrNull()
    val displayName = account?.displayName?.takeIf { it.isNotBlank() }
        ?: state.authSession?.user?.displayName?.takeIf { it.isNotBlank() }
        ?: "NVIDIA Account"
    val email = account?.email?.takeIf { it.isNotBlank() }
        ?: state.authSession?.user?.email?.takeIf { it.isNotBlank() }
    val tier = state.subscriptionInfo?.membershipTier?.takeIf { it.isNotBlank() }
        ?: state.authSession?.user?.membershipTier?.takeIf { it.isNotBlank() }
        ?: account?.membershipTier?.takeIf { it.isNotBlank() }
    val detail = listOfNotNull(email, tier).joinToString(" - ").ifBlank {
        if (state.authSession == null && account == null) "Sign in to sync your GeForce NOW account" else "Manage account"
    }
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(28.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "N",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    displayName,
                    color = SettingsText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    color = SettingsTextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = SettingsTextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SettingsCategoryRow(category: SettingsCategory, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(category.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                category.title,
                color = SettingsText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                category.summary,
                color = SettingsTextMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = SettingsTextMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SettingsDetailHeader(category: SettingsCategory, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint = SettingsText,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                category.title,
                color = SettingsText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                category.summary,
                color = SettingsTextMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategorySettingsSection(
    selectedCategory: SettingsCategory?,
    category: SettingsCategory,
    searchQuery: String,
    title: String,
    vararg keywords: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    if (searchQuery.isNotBlank() || selectedCategory == null || selectedCategory == category) {
        SearchableSettingsSection(searchQuery, title, *keywords, content = content)
    }
}

private fun settingsCategories(): List<SettingsCategory> =
    buildList {
        add(SettingsCategory.General)
        add(SettingsCategory.Stream)
        add(SettingsCategory.Input)
        add(SettingsCategory.Interface)
        add(SettingsCategory.Advanced)
    }

private fun settingsDetailCategories(): List<SettingsCategory> =
    settingsCategories() + SettingsCategory.Account
