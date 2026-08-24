package com.opencloudgaming.opennow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.opencloudgaming.opennow.ui.controls.ControlRow
import com.opencloudgaming.opennow.ui.controls.ControlRowStyle
import com.opencloudgaming.opennow.ui.controls.ControlRowLabels
import com.opencloudgaming.opennow.ui.controls.ControlSection
import com.opencloudgaming.opennow.ui.controls.LocalControlRowStyle
import com.opencloudgaming.opennow.ui.controls.controlRowStyle
import com.opencloudgaming.opennow.ui.theme.OpenNowRadius
import com.opencloudgaming.opennow.ui.theme.OpenNowSpacing
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * First-run setup.
 *
 * Runs after sign-in, over the whole app, and writes every choice straight through to
 * [AppSettings] as it is made. Applying live is what makes the appearance step a real preview
 * rather than a mock-up of one: the screen behind the content *is* [CatalogWallpaperBackdrop] with
 * the user's current pick, the box art on it comes from their own catalog, and the accent
 * recolours this flow's chrome as they choose it. Waiting until after sign-in is what buys that
 * artwork — before it there is no catalog to preview against.
 *
 * Step order, gating, and the settings written on exit live in `AndroidSetupFlow.kt`.
 */
@Composable
internal fun SetupFlowScreen(state: OpenNowUiState, viewModel: OpenNowViewModel) {
    val settings = state.settings
    val tvProfile = state.androidTvProfile
    val controllerNavigationEnabled = tvProfile || rememberPhysicalControllerConnected(enabled = true)
    val focusManager = LocalFocusManager.current
    val primaryFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var step by rememberSaveable { mutableStateOf(SetupStep.Welcome) }
    var furthestStepOrdinal by rememberSaveable { mutableStateOf(SetupStep.Welcome.ordinal) }
    val furthestStep = SetupStep.entries[furthestStepOrdinal]

    fun finish(skipped: Boolean) {
        OpenNowAnalytics.capture(
            event = "setup_flow_finished",
            properties = mapOf(
                "skipped" to skipped,
                "last_step" to step.name,
                "furthest_step" to furthestStep.name,
            ),
        )
        viewModel.updateSettings(settings.completingSetupFlow(furthestStep))
    }

    BackHandler(enabled = step != SetupStep.Welcome) {
        setupStepBefore(step)?.let { step = it }
    }
    // Land on the primary action, not on "Skip" — which is what a D-pad's first key press would
    // otherwise reach, since it comes first in the footer. The requester is not attached until the
    // step has been laid out, so retry rather than betting on a single delay.
    LaunchedEffect(step) {
        scrollState.scrollTo(0)
        repeat(6) { attempt ->
            if (runCatching { primaryFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            if (attempt < 5) delay(80)
        }
    }

    val edgePadding = if (tvProfile) {
        OpenNowSpacing.xl + settings.tvSafeAreaPaddingDp.coerceIn(0f, 120f).dp
    } else {
        OpenNowSpacing.lg
    }

    CompositionLocalProvider(
        LocalSettingsControllerNavigationEnabled provides controllerNavigationEnabled,
        // Settings rows are translucent because in Settings they sit on a flat background. Here
        // they sit on the user's wallpaper, where 76% opacity puts box art behind the labels.
        // Opaque rows keep the picture vivid everywhere it is not covering text.
        LocalControlRowStyle provides ControlRowStyle.settings().let { style ->
            style.copy(
                containerRest = MaterialTheme.colorScheme.surfaceVariant,
                containerFocused = MaterialTheme.colorScheme.surfaceVariant,
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wideLayout = maxWidth >= 720.dp
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background))
            if (settings.nerdCatalogBackground) {
                CatalogWallpaperBackdrop(
                    settings = settings,
                    tvProfile = tvProfile,
                    width = maxWidth,
                    height = maxHeight,
                )
                // The wallpaper's own scrim is cut for artwork sitting on it, not for paragraphs.
                // Weight this one towards the bottom so the controls stay readable while the top of
                // the picture — the part being chosen — comes through close to untouched.
                val background = MaterialTheme.colorScheme.background
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    background.copy(alpha = 0.25f),
                                    background.copy(alpha = 0.6f),
                                    background.copy(alpha = 0.88f),
                                ),
                            ),
                        ),
                )
            } else if (settings.ambientBackgroundEnabled) {
                AmbientBackground()
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = edgePadding, vertical = OpenNowSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .widthIn(max = SetupContentMaxWidth)
                        .fillMaxSize()
                        // On the whole flow, not just the scrolling part: a D-pad has to be able to
                        // cross between the step's controls and the footer, and Compose's default
                        // vertical search does not reliably step into and out of the animated
                        // content container on its own.
                        .onPreviewKeyEvent { handleVerticalDpadFocusMove(it, focusManager) },
                    verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.md),
                ) {
                    SetupProgressBar(step)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = step,
                            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                            label = "setup-step",
                        ) { currentStep ->
                            // The hero centres itself in the viewport, so it must not be inside a
                            // scroll container — that would measure it against an infinite height
                            // and vertical centring would resolve to "hug the top".
                            if (currentStep == SetupStep.Welcome) {
                                SetupWelcomeStep(wideLayout = wideLayout)
                            } else {
                                Column(
                                    Modifier.fillMaxSize().verticalScroll(scrollState),
                                    verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.md),
                                ) {
                                    SetupStepHeading(currentStep)
                                    when (currentStep) {
                                        SetupStep.Appearance -> SetupAppearanceStep(
                                            state = state,
                                            onSettingsChange = viewModel::updateSettings,
                                        )
                                        SetupStep.Streaming -> SetupStreamingStep(
                                            state = state,
                                            viewModel = viewModel,
                                        )
                                        SetupStep.Play -> SetupPlayStep(
                                            settings = settings,
                                            tvProfile = tvProfile,
                                            onSettingsChange = viewModel::updateSettings,
                                        )
                                        SetupStep.Feedback -> SetupFeedbackStep(
                                            settings = settings,
                                            onSettingsChange = viewModel::updateSettings,
                                        )
                                        else -> SetupReadyStep(settings = settings, tvProfile = tvProfile)
                                    }
                                    Spacer(Modifier.height(OpenNowSpacing.sm))
                                }
                            }
                        }
                    }
                    SetupStepFooter(
                        step = step,
                        primaryFocusRequester = primaryFocusRequester,
                        onBack = { setupStepBefore(step)?.let { step = it } },
                        onSkip = { finish(skipped = true) },
                        onNext = {
                            val next = setupStepAfter(step)
                            if (next == null) {
                                finish(skipped = false)
                            } else {
                                step = next
                                if (next.ordinal > furthestStepOrdinal) furthestStepOrdinal = next.ordinal
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupProgressBar(step: SetupStep) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        setupSteps().forEach { candidate ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        if (candidate.ordinal <= step.ordinal) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun SetupStepHeading(step: SetupStep) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(step.titleRes),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(step.subtitleRes),
            // Brighter than the usual muted body: this line sits directly on the user's wallpaper
            // rather than on a panel, and onSurfaceVariant loses against busy artwork.
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SetupStepFooter(
    step: SetupStep,
    primaryFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step != SetupStep.Welcome) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.setup_action_back))
            }
        }
        Spacer(Modifier.weight(1f))
        if (!isFinalSetupStep(step)) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.setup_action_skip), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Button(onClick = onNext, modifier = Modifier.focusRequester(primaryFocusRequester)) {
            Text(
                stringResource(
                    when {
                        step == SetupStep.Welcome -> R.string.setup_action_start
                        isFinalSetupStep(step) -> R.string.setup_action_finish
                        else -> R.string.setup_action_next
                    },
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SetupWelcomeStep(wideLayout: Boolean) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OpenNowMark(size = if (wideLayout) 88.dp else 64.dp)
        Spacer(Modifier.height(OpenNowSpacing.lg))
        Text(
            stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(OpenNowSpacing.xs))
        Text(
            stringResource(R.string.setup_welcome_tagline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SetupAppearanceStep(state: OpenNowUiState, onSettingsChange: (AppSettings) -> Unit) {
    val settings = state.settings
    val context = LocalContext.current
    val launchImagePicker = rememberCatalogBackgroundImagePicker(settings, onSettingsChange)
    val customUri = settings.nerdCatalogBackgroundUri?.takeIf { it.isNotBlank() }
    val backgroundName = when {
        appBackgroundChoiceFor(settings) == AppBackgroundChoice.Default ->
            stringResource(R.string.setup_background_default)
        appBackgroundChoiceFor(settings) == AppBackgroundChoice.Nothing ->
            stringResource(R.string.setup_background_nothing)
        customUri != null -> stringResource(R.string.settings_catalog_background_image_custom)
        else -> catalogBackgroundPresetLabel(settings.catalogBackgroundPreset)
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.md),
    ) {
        SetupAppearancePreview(state)

        Column(verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm)) {
            SetupSectionLabel(stringResource(R.string.setup_background), backgroundName)
            SetupPeekRow { tileWidth ->
                SetupTile(
                    width = tileWidth,
                    selected = appBackgroundChoiceFor(settings) == AppBackgroundChoice.Default,
                    onClick = { onSettingsChange(settings.withAppBackgroundChoice(AppBackgroundChoice.Default)) },
                ) {
                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background))
                    AmbientBackground(Modifier.matchParentSize())
                    Text(
                        stringResource(R.string.setup_background_default),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                SetupTile(
                    width = tileWidth,
                    selected = appBackgroundChoiceFor(settings) == AppBackgroundChoice.Nothing,
                    onClick = { onSettingsChange(settings.withAppBackgroundChoice(AppBackgroundChoice.Nothing)) },
                ) {
                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background))
                    Text(
                        stringResource(R.string.setup_background_nothing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                CatalogBackgroundPreset.entries.forEach { preset ->
                    SetupTile(
                        width = tileWidth,
                        selected = settings.nerdCatalogBackground &&
                            customUri == null &&
                            settings.catalogBackgroundPreset == preset,
                        onClick = { applyCatalogBackgroundPreset(context, settings, preset, onSettingsChange) },
                    ) {
                        Image(
                            painter = painterResource(preset.drawableRes),
                            contentDescription = catalogBackgroundPresetLabel(preset),
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                SetupTile(
                    width = tileWidth,
                    selected = settings.nerdCatalogBackground && customUri != null,
                    onClick = launchImagePicker,
                ) {
                    if (customUri == null) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        )
                        Text(
                            stringResource(R.string.setup_background_your_image),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    } else {
                        AsyncImage(
                            model = imageDataForSource(customUri),
                            contentDescription = stringResource(R.string.settings_catalog_background_image_custom),
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm)) {
            SetupSectionLabel(
                stringResource(R.string.setup_appearance_accent),
                uiAccentLabel(settings.uiAccent),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
            ) {
                UiAccent.entries.forEach { accent ->
                    SetupAccentSwatch(
                        accent = accent,
                        selected = settings.uiAccent == accent,
                        onClick = { onSettingsChange(settings.copy(uiAccent = accent)) },
                    )
                }
            }
        }

        // Everything below changes the preview above. That is the point of putting them here
        // rather than leaving them to be discovered in Settings > Interface much later.
        Column(verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm)) {
            SetupSectionLabel(
                stringResource(R.string.setup_appearance_layout),
                stringResource(R.string.setup_appearance_layout_hint),
            )
            SettingSwitch(
                label = stringResource(R.string.setup_appearance_titles),
                checked = settings.showCardTitles,
                description = stringResource(R.string.setup_appearance_titles_desc),
            ) {
                onSettingsChange(settings.copy(showCardTitles = it))
            }
            SettingSwitch(
                label = stringResource(R.string.setup_appearance_compact),
                checked = settings.compactGameCards,
                description = stringResource(R.string.setup_appearance_compact_desc),
            ) {
                onSettingsChange(settings.copy(compactGameCards = it))
            }
            SettingSwitch(
                label = stringResource(R.string.setup_appearance_favorite_icon),
                checked = settings.showFavoriteIconOnGameCards,
                description = stringResource(R.string.setup_appearance_favorite_icon_desc),
            ) {
                onSettingsChange(settings.copy(showFavoriteIconOnGameCards = it))
            }
            SettingSwitch(
                label = stringResource(R.string.setup_appearance_expressive),
                checked = settings.expressiveUi,
                description = stringResource(R.string.setup_appearance_expressive_desc),
            ) {
                onSettingsChange(settings.copy(expressiveUi = it))
            }
            SettingSwitch(
                label = stringResource(R.string.setup_appearance_cinema),
                checked = settings.absoluteCinemaEffects,
                description = stringResource(R.string.setup_appearance_cinema_desc),
            ) { enabled ->
                onSettingsChange(
                    settings.copy(
                        absoluteCinemaEffects = enabled,
                        absoluteCinemaEverywhere = settings.absoluteCinemaEverywhere && enabled,
                    ),
                )
            }
            SettingSwitch(
                label = stringResource(R.string.settings_im_crazy),
                checked = settings.absoluteCinemaEverywhere,
                enabled = settings.absoluteCinemaEffects,
                description = stringResource(R.string.settings_im_crazy_desc),
                indentLevel = 1,
            ) {
                onSettingsChange(settings.copy(absoluteCinemaEverywhere = it))
            }
            SettingSwitch(
                label = stringResource(R.string.setup_appearance_animations),
                checked = settings.controllerBackgroundAnimations,
                description = stringResource(R.string.setup_appearance_animations_desc),
            ) {
                onSettingsChange(settings.copy(controllerBackgroundAnimations = it))
            }
        }

        // Feedback belongs on this step rather than buried in Settings > Interface: both fire on
        // the very next tap, so setup is the one moment where turning them off costs nothing to
        // find out about.
        Column(verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm)) {
            SetupSectionLabel(
                stringResource(R.string.setup_appearance_feedback),
                stringResource(R.string.setup_appearance_feedback_hint),
            )
            SettingSwitch(
                label = stringResource(R.string.setup_appearance_haptics),
                checked = settings.vibrationEnabled,
                description = stringResource(R.string.setup_appearance_haptics_desc),
            ) {
                onSettingsChange(settings.copy(vibrationEnabled = it))
            }
            SettingSwitch(
                label = stringResource(R.string.setup_appearance_sounds),
                checked = settings.controllerUiSounds,
                description = stringResource(R.string.setup_appearance_sounds_desc),
            ) {
                onSettingsChange(settings.copy(controllerUiSounds = it))
            }
        }
    }
}

/**
 * A working miniature of the app, on the user's own backdrop and box art.
 *
 * This replaces a flat strip of posters that showed the backdrop and nothing else. It is drawn as
 * the app rather than as a sample of one — its own window with the Store's top bar, a section
 * heading, the grid, and the tab bar underneath — because the choices on this step apply to the
 * chrome as much as to the cards, and a bare row of posters could not show that. Every switch below
 * redraws it: corner radius, captions, card shape, the favourite badge, and the frame on the
 * selected card.
 *
 * Non-interactive by design: it is the sample, and the controls below it are the step.
 *
 * Renders nothing until the catalogue has artwork. Placeholder blocks would preview nothing and
 * read as an unfinished screen.
 */
@Composable
private fun SetupAppearancePreview(state: OpenNowUiState) {
    val settings = state.settings
    val tvProfile = state.androidTvProfile
    val games = remember(state.games, state.libraryGames, tvProfile) {
        (state.libraryGames + state.games)
            .distinctBy { it.id }
            .filter { !catalogCardImageUrl(it, tvProfile).isNullOrBlank() }
            .take(SETUP_PREVIEW_CARD_COUNT)
    }
    if (games.isEmpty()) return
    val cardShape = RoundedCornerShape(if (settings.expressiveUi) OpenNowRadius.md else OpenNowRadius.sm)
    val windowShape = RoundedCornerShape(if (settings.expressiveUi) OpenNowRadius.lg else OpenNowRadius.sm)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.xs),
    ) {
        Text(
            stringResource(R.string.setup_appearance_preview_label),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = windowShape,
            // Opaque, and outlined: this is meant to read as a screenshot of the app sitting on the
            // wallpaper, not as another translucent panel belonging to setup.
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                SetupPreviewTopBar()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OpenNowSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.xs),
                ) {
                    Text(
                        stringResource(R.string.store_results),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
                    ) {
                        games.forEachIndexed { index, game ->
                            // The first card stands in for the focused one, so the accent and the
                            // Absolute Cinema frame have somewhere to show.
                            val selected = index == 0
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.xs),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(
                                            if (settings.compactGameCards) 1f else GAME_BOX_ART_ASPECT_RATIO,
                                        ),
                                ) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .clip(cardShape)
                                            .then(
                                                if (selected && !LocalAbsoluteCinemaEffects.current) {
                                                    Modifier.border(
                                                        2.dp,
                                                        LocalSelectionTintColor.current,
                                                        cardShape,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    ) {
                                        UrlImage(
                                            catalogCardImageUrl(game, tvProfile),
                                            Modifier.matchParentSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                        if (settings.showFavoriteIconOnGameCards) {
                                            SetupPreviewFavoriteBadge(
                                                Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp),
                                            )
                                        }
                                    }
                                    ControllerFocusFrame(
                                        visible = selected && LocalAbsoluteCinemaEffects.current,
                                        cornerRadius = if (settings.expressiveUi) OpenNowRadius.md else OpenNowRadius.sm,
                                        tint = LocalActiveSelectionColor.current,
                                        secondaryTint = LocalActiveSelectionSecondaryColor.current,
                                    )
                                }
                                if (settings.showCardTitles) {
                                    Text(
                                        game.title,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                SetupPreviewTabBar()
            }
        }
    }
}

/** The Store's top bar, reduced to the shapes that read at this size. */
@Composable
private fun SetupPreviewTopBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OpenNowSpacing.sm, vertical = OpenNowSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OpenNowMark(size = 14.dp)
        Text(
            stringResource(R.string.nav_store),
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        SetupPreviewChromeDot()
        SetupPreviewChromeDot()
    }
}

@Composable
private fun SetupPreviewChromeDot() {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)),
    )
}

/**
 * The tab bar, with Store selected.
 *
 * Worth drawing even though the accent no longer reaches it: showing the fixed navigation tint
 * beside an accent-coloured card is exactly how the two read in the real app, and a preview that
 * left the chrome out would imply the accent covers everything.
 */
@Composable
private fun SetupPreviewTabBar() {
    val labels = listOf(
        stringResource(R.string.nav_store) to true,
        stringResource(R.string.nav_library) to false,
        stringResource(R.string.nav_settings) to false,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = OpenNowSpacing.sm)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEach { (label, selected) ->
            val tint = if (selected) {
                NavigationSelectionColor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 20.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = if (selected) 1f else 0.45f)),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    color = tint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SetupPreviewFavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_save),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(11.dp),
        )
    }
}

/**
 * A horizontally scrolling row of picture tiles that always cuts one off at the trailing edge.
 *
 * The tiles previously used a fixed 116dp width, which on a typical phone left the row *just*
 * filled: nothing was clipped, no scrollbar showed, and the options past the fold were invisible
 * unless the user happened to try dragging. Sizing the tiles from the available width instead
 * guarantees a partial one at the edge, which is the affordance — a cut-off tile is read as "there
 * is more" without any extra chrome. The fade reinforces it and disappears at the end of the row.
 */
@Composable
private fun SetupPeekRow(content: @Composable RowScope.(tileWidth: Dp) -> Unit) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tileWidth = ((maxWidth - OpenNowSpacing.sm * SETUP_TILE_PEEK_COUNT) / SETUP_TILE_PEEK_COUNT)
            .coerceIn(SetupTileMinWidth, SetupTileMaxWidth)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
        ) {
            content(tileWidth)
        }
        // Edge shadows on whichever side has more to reach. Drawn rather than composed: reading the
        // scroll position inside drawBehind keeps a drag in the draw phase instead of recomposing
        // the whole row — and every tile in it — on every frame of the gesture. Black rather than a
        // surface colour because this row sits directly on the user's wallpaper, where a flat
        // theme-coloured band would read as a smear and a shadow reads as depth.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val fade = SetupPeekFadeWidth.toPx().coerceAtMost(size.width)
                    if (scrollState.canScrollForward) {
                        drawRect(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, SetupPeekFadeColor),
                                startX = size.width - fade,
                                endX = size.width,
                            ),
                        )
                    }
                    if (scrollState.canScrollBackward) {
                        drawRect(
                            Brush.horizontalGradient(
                                colors = listOf(SetupPeekFadeColor, Color.Transparent),
                                startX = 0f,
                                endX = fade,
                            ),
                        )
                    }
                },
        )
    }
}

@Composable
private fun SetupStreamingStep(state: OpenNowUiState, viewModel: OpenNowViewModel) {
    val settings = state.settings
    val recommended = state.recommendedStreamSettings
    val selected = setupStreamingChoiceFor(settings)
    val fallbackMembershipTier = state.authSession?.user?.membershipTier
    val entitlements = remember(state.subscriptionInfo, fallbackMembershipTier) {
        streamPlanEntitlements(state.subscriptionInfo, fallbackMembershipTier)
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
    ) {
        SetupMembershipCard(entitlements)
        SetupChoiceRow(
            label = stringResource(R.string.setup_streaming_recommended),
            value = recommended?.recommendationSummary()
                ?: stringResource(R.string.setup_streaming_measuring),
            selected = selected == SetupStreamingChoice.Recommended,
        ) {
            viewModel.applyStreamPreset(setupStreamingPresetFor(SetupStreamingChoice.Recommended))
        }
        SetupChoiceRow(
            label = stringResource(R.string.setup_streaming_best),
            value = stringResource(
                R.string.setup_streaming_best_desc,
                entitlements.maxResolutionLabel,
                entitlements.maxFps,
            ),
            selected = selected == SetupStreamingChoice.Best,
        ) {
            viewModel.applyStreamPreset(setupStreamingPresetFor(SetupStreamingChoice.Best))
        }
        SetupChoiceRow(
            label = stringResource(R.string.setup_streaming_data_saver),
            value = stringResource(R.string.setup_streaming_data_saver_desc),
            selected = selected == SetupStreamingChoice.DataSaver,
        ) {
            viewModel.applyStreamPreset(setupStreamingPresetFor(SetupStreamingChoice.DataSaver))
        }
        SetupChoiceRow(
            label = stringResource(R.string.setup_streaming_custom),
            value = if (selected == SetupStreamingChoice.Custom) {
                settings.stream.recommendationSummary()
            } else {
                stringResource(R.string.setup_streaming_custom_desc)
            },
            selected = selected == SetupStreamingChoice.Custom,
        ) {
            viewModel.applyStreamPreset(setupStreamingPresetFor(SetupStreamingChoice.Custom))
        }
        // Only under Custom. Beside a preset these would edit values the next preset write
        // discards, which reads as the controls not working.
        AnimatedVisibility(visible = setupStreamingCustomControlsVisible(selected)) {
            SetupCustomStreamControls(
                state = state,
                viewModel = viewModel,
                entitlements = entitlements,
                fallbackMembershipTier = fallbackMembershipTier,
            )
        }
    }
}

@Composable
private fun SetupPlayStep(
    settings: AppSettings,
    tvProfile: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val touchChoice = setupTouchMouseChoiceFor(settings)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
    ) {
        SetupSectionLabel(
            title = stringResource(R.string.setup_play_preview),
            value = if (tvProfile) {
                if (settings.showStatsOnLaunch) settings.streamStatsPosition.label
                else stringResource(R.string.setup_summary_off)
            } else {
                setupTouchMouseChoiceLabel(touchChoice)
            },
        )
        SetupStreamExperiencePreview(settings = settings, showTouchHint = !tvProfile)

        if (!tvProfile) {
            SetupSectionLabel(
                title = stringResource(R.string.setup_play_touch),
                value = setupTouchMouseChoiceLabel(touchChoice),
            )
            SetupChoiceRow(
                label = stringResource(R.string.setup_play_touch_direct),
                value = stringResource(R.string.setup_play_touch_direct_desc),
                selected = touchChoice == SetupTouchMouseChoice.Direct,
            ) {
                onSettingsChange(settings.withSetupTouchMouseChoice(SetupTouchMouseChoice.Direct))
            }
            SetupChoiceRow(
                label = stringResource(R.string.setup_play_touch_trackpad),
                value = stringResource(R.string.setup_play_touch_trackpad_desc),
                selected = touchChoice == SetupTouchMouseChoice.Trackpad,
            ) {
                onSettingsChange(settings.withSetupTouchMouseChoice(SetupTouchMouseChoice.Trackpad))
            }
            SetupChoiceRow(
                label = stringResource(R.string.setup_play_touch_off),
                value = stringResource(R.string.setup_play_touch_off_desc),
                selected = touchChoice == SetupTouchMouseChoice.Off,
            ) {
                onSettingsChange(settings.withSetupTouchMouseChoice(SetupTouchMouseChoice.Off))
            }
        }

        SettingSwitch(
            label = stringResource(R.string.setup_play_status),
            checked = settings.showStatsOnLaunch,
            description = stringResource(R.string.setup_play_status_desc),
        ) { enabled ->
            onSettingsChange(settings.copy(showStatsOnLaunch = enabled))
        }
        AnimatedVisibility(visible = settings.showStatsOnLaunch) {
            ChoiceMenuRow(
                label = stringResource(R.string.setup_play_status_position),
                options = StreamStatsPosition.entries.map { position ->
                    ChoiceMenuOption(value = position.name, label = position.label)
                },
                selectedLabel = settings.streamStatsPosition.label,
            ) { value ->
                StreamStatsPosition.entries.firstOrNull { it.name == value }?.let { position ->
                    onSettingsChange(settings.copy(streamStatsPosition = position))
                }
            }
        }
    }
}

/** A deliberately quiet mock stream: every setup choice changes the chrome in this frame live. */
@Composable
private fun SetupStreamExperiencePreview(settings: AppSettings, showTouchHint: Boolean) {
    val touchChoice = setupTouchMouseChoiceFor(settings)
    val statusAlignment = when (settings.streamStatsPosition) {
        StreamStatsPosition.Left -> Alignment.TopStart
        StreamStatsPosition.Center -> Alignment.TopCenter
        StreamStatsPosition.Right -> Alignment.TopEnd
    }
    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = RoundedCornerShape(OpenNowRadius.lg),
        color = Color(0xFF08141C),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF17364A), Color(0xFF0D2630), Color(0xFF091117)),
                    ),
                ),
        ) {
            // A small fake game objective gives the cursor treatment a meaningful destination.
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(OpenNowRadius.full),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            ) {
                Text(
                    text = stringResource(R.string.setup_play_preview_target),
                    modifier = Modifier.padding(horizontal = OpenNowSpacing.lg, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            AnimatedVisibility(
                visible = settings.showStatsOnLaunch,
                modifier = Modifier.align(statusAlignment),
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(90)),
            ) {
                Surface(
                    modifier = Modifier.padding(10.dp),
                    shape = RoundedCornerShape(OpenNowRadius.full),
                    color = Color.Black.copy(alpha = 0.58f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    Text(
                        text = stringResource(R.string.setup_play_status_preview),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }

            if (showTouchHint) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                    shape = RoundedCornerShape(OpenNowRadius.full),
                    color = Color.Black.copy(alpha = 0.48f),
                ) {
                    Text(
                        text = stringResource(
                            when (touchChoice) {
                                SetupTouchMouseChoice.Direct -> R.string.setup_play_preview_direct
                                SetupTouchMouseChoice.Trackpad -> R.string.setup_play_preview_trackpad
                                SetupTouchMouseChoice.Off -> R.string.setup_play_preview_off
                            },
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun setupTouchMouseChoiceLabel(choice: SetupTouchMouseChoice): String =
    stringResource(
        when (choice) {
            SetupTouchMouseChoice.Direct -> R.string.setup_play_touch_direct
            SetupTouchMouseChoice.Trackpad -> R.string.setup_play_touch_trackpad
            SetupTouchMouseChoice.Off -> R.string.setup_play_touch_off
        },
    )

/**
 * The membership tier, stated plainly, above the quality choices.
 *
 * Without it a Free account sees 1080p as the top option and reads it as OpenNOW deciding the
 * device cannot do better. Naming the plan and its ceiling makes the cap attributable.
 */
@Composable
private fun SetupMembershipCard(entitlements: StreamPlanEntitlements) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OpenNowRadius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(OpenNowSpacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.setup_streaming_membership, entitlements.planLabel),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    R.string.setup_streaming_membership_ceiling,
                    entitlements.maxResolutionLabel,
                    entitlements.maxFps,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (entitlements.cappedBelowTopTier) {
                Text(
                    stringResource(R.string.setup_streaming_membership_upgrade),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Resolution, frame rate, and bitrate, edited here rather than in Settings > Stream.
 *
 * Options above the plan stay listed and disabled with the tier that unlocks them, matching how
 * Settings presents them — hiding them would leave the ceiling unexplained all over again.
 */
@Composable
private fun SetupCustomStreamControls(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    entitlements: StreamPlanEntitlements,
    fallbackMembershipTier: String?,
) {
    val stream = state.settings.stream
    val resolutionChoices = streamResolutionChoicesForAspect(stream.aspectRatio).ifEmpty {
        streamResolutionChoicesForAspect("16:9")
    }
    val selectedResolution = normalizeStreamResolutionForAspectAndPlan(
        stream.resolution,
        stream.aspectRatio,
        state.subscriptionInfo,
        fallbackMembershipTier,
    )
    Column(verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm)) {
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
            selectedLabel = resolutionChoices.firstOrNull { it.value == selectedResolution }?.label
                ?: selectedResolution,
        ) { value ->
            viewModel.updateStreamSettings { it.copy(resolution = value) }
        }
        NumberSlider(
            label = stringResource(R.string.settings_fps),
            value = stream.fps.coerceAtMost(entitlements.maxFps).toFloat(),
            min = 30f,
            max = entitlements.maxFps.toFloat(),
            step = 30f,
            unit = "FPS",
        ) { value ->
            val fps = value.roundToInt().coerceIn(30, entitlements.maxFps)
            viewModel.updateStreamSettings { it.copy(fps = fps) }
        }
        NumberSlider(
            label = stringResource(R.string.settings_bitrate),
            value = stream.maxBitrateMbps.toFloat(),
            min = 1f,
            max = 150f,
            step = 1f,
            descriptionProvider = { mbps -> streamBitrateUsageEstimate(mbps) },
        ) { value ->
            viewModel.updateStreamSettings { it.copy(maxBitrateMbps = value.roundToInt()) }
        }
    }
}

@Composable
private fun SetupFeedbackStep(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
    ) {
        SetupPointsCard(
            title = stringResource(R.string.setup_feedback_reporter_title),
            points = listOf(
                stringResource(R.string.setup_feedback_reporter_where),
                stringResource(R.string.setup_feedback_reporter_preflight),
                stringResource(R.string.setup_feedback_reporter_contents),
            ),
        )
        SettingSwitch(
            label = stringResource(R.string.setup_feedback_session_report),
            checked = settings.showSessionReportAfterStream,
            description = stringResource(R.string.setup_feedback_session_report_desc),
        ) {
            onSettingsChange(settings.copy(showSessionReportAfterStream = it))
        }
        SettingSwitch(
            label = stringResource(R.string.setup_feedback_analytics),
            checked = settings.analyticsSharingEnabled,
            description = stringResource(R.string.setup_feedback_analytics_desc),
        ) { enabled ->
            onSettingsChange(settings.copy(analyticsConsentAsked = true, analyticsOptOut = !enabled))
        }
        DiscordCommunityLink(
            summary = stringResource(R.string.discord_community_bug_report_summary),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun SetupReadyStep(settings: AppSettings, tvProfile: Boolean) {
    val backgroundValue = when {
        appBackgroundChoiceFor(settings) == AppBackgroundChoice.Default ->
            stringResource(R.string.setup_background_default)
        appBackgroundChoiceFor(settings) == AppBackgroundChoice.Nothing ->
            stringResource(R.string.setup_background_nothing)
        !settings.nerdCatalogBackgroundUri.isNullOrBlank() ->
            stringResource(R.string.settings_catalog_background_image_custom)
        else -> catalogBackgroundPresetLabel(settings.catalogBackgroundPreset)
    }
    ControlSection(stringResource(R.string.setup_summary_title)) {
        SetupSummaryRow(stringResource(R.string.setup_background), backgroundValue)
        SetupSummaryRow(stringResource(R.string.setup_appearance_accent), uiAccentLabel(settings.uiAccent))
        SetupSummaryRow(
            stringResource(R.string.setup_summary_streaming),
            settings.stream.recommendationSummary(),
        )
        if (!tvProfile) {
            SetupSummaryRow(
                stringResource(R.string.setup_summary_touch),
                setupTouchMouseChoiceLabel(setupTouchMouseChoiceFor(settings)),
            )
        }
        SetupSummaryRow(
            stringResource(R.string.setup_summary_status),
            if (settings.showStatsOnLaunch) settings.streamStatsPosition.label
            else stringResource(R.string.setup_summary_off),
        )
        SetupSummaryRow(
            stringResource(R.string.setup_feedback_analytics),
            stringResource(
                if (settings.analyticsSharingEnabled) R.string.setup_summary_on
                else R.string.setup_summary_off,
            ),
        )
    }
}

@Composable
private fun SetupSectionLabel(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A picture-sized, D-pad focusable option. Selection is the accent ring; focus is the white one. */
@Composable
private fun SetupTile(
    selected: Boolean,
    onClick: () -> Unit,
    width: Dp = SetupTileMaxWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    val controllerFocusEnabled = LocalControllerFocusEnabled.current
    var focused by remember { mutableStateOf(false) }
    val showFocusRing = focused && controllerFocusEnabled
    val shape = RoundedCornerShape(OpenNowRadius.md)
    Box(
        Modifier
            .size(width = width, height = SetupTileHeight)
            .border(
                width = when {
                    showFocusRing -> 3.dp
                    selected -> 2.dp
                    else -> 1.dp
                },
                color = when {
                    showFocusRing -> Color.White
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                },
                shape = shape,
            )
            .clip(shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun SetupAccentSwatch(accent: UiAccent, selected: Boolean, onClick: () -> Unit) {
    val controllerFocusEnabled = LocalControllerFocusEnabled.current
    var focused by remember { mutableStateOf(false) }
    val showFocusRing = focused && controllerFocusEnabled
    val ringColor = when {
        showFocusRing -> Color.White
        selected -> MaterialTheme.colorScheme.onBackground
        else -> Color.Transparent
    }
    Box(
        Modifier
            .size(SetupSwatchTarget)
            .border(if (ringColor == Color.Transparent) 0.dp else 2.dp, ringColor, CircleShape)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(SetupSwatchDot).clip(CircleShape).background(accent.color))
    }
}

/** A settings-style row whose trailing slot is a radio button — one of several exclusive options. */
@Composable
private fun SetupChoiceRow(label: String, value: String, selected: Boolean, onSelect: () -> Unit) {
    val style = controlRowStyle()
    ControlRow(onClick = onSelect) {
        ControlRowLabels(
            label = label,
            value = value,
            expandedDescription = null,
            enabled = true,
            style = style,
        )
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun SetupSummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            value,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SetupPointsCard(title: String, points: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OpenNowRadius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(OpenNowSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            points.forEach { point ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OpenNowSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        point,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private val SetupContentMaxWidth = 720.dp
private val SetupTileHeight = 68.dp
private val SetupTileMinWidth = 96.dp
private val SetupTileMaxWidth = 148.dp
private val SetupSwatchTarget = 40.dp
private val SetupSwatchDot = 28.dp

/**
 * Tiles per screen width. Fractional on purpose: 2.6 leaves most of a third tile showing, which is
 * what tells the user the row scrolls.
 */
private const val SETUP_TILE_PEEK_COUNT = 2.6f
private val SetupPeekFadeWidth = 36.dp
private val SetupPeekFadeColor = Color.Black.copy(alpha = 0.55f)
private const val SETUP_PREVIEW_CARD_COUNT = 3

private val SetupStep.titleRes: Int
    get() = when (this) {
        SetupStep.Welcome -> R.string.app_name
        SetupStep.Appearance -> R.string.setup_appearance_title
        SetupStep.Streaming -> R.string.setup_streaming_title
        SetupStep.Play -> R.string.setup_play_title
        SetupStep.Feedback -> R.string.setup_feedback_title
        SetupStep.Ready -> R.string.setup_ready_title
    }

private val SetupStep.subtitleRes: Int
    get() = when (this) {
        SetupStep.Welcome -> R.string.setup_welcome_tagline
        SetupStep.Appearance -> R.string.setup_appearance_subtitle
        SetupStep.Streaming -> R.string.setup_streaming_subtitle
        SetupStep.Play -> R.string.setup_play_subtitle
        SetupStep.Feedback -> R.string.setup_feedback_subtitle
        SetupStep.Ready -> R.string.setup_ready_subtitle
    }
