package com.opencloudgaming.opennow

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
enum class VideoCodec {
    H264,
    H265,
    AV1,
}

@Serializable
enum class ColorQuality {
    @kotlinx.serialization.SerialName("8bit_420")
    EightBit420,

    @kotlinx.serialization.SerialName("8bit_444")
    EightBit444,

    @kotlinx.serialization.SerialName("10bit_420")
    TenBit420,

    @kotlinx.serialization.SerialName("10bit_444")
    TenBit444,
}

@Serializable
enum class StreamPreset {
    @kotlinx.serialization.SerialName("recommended")
    Recommended,

    @kotlinx.serialization.SerialName("custom")
    Custom,

    @kotlinx.serialization.SerialName("low_data_saver")
    LowDataSaver,

    @kotlinx.serialization.SerialName("medium")
    Medium,

    @kotlinx.serialization.SerialName("high")
    High,
}

@Serializable
enum class MicrophoneMode {
    @kotlinx.serialization.SerialName("disabled")
    Disabled,

    @kotlinx.serialization.SerialName("push-to-talk")
    PushToTalk,

    @kotlinx.serialization.SerialName("voice-activity")
    VoiceActivity,
}

@Serializable
enum class UiAccent {
    OpenNow,
    Pixel,
    HotPink,
    Lime,
    Coral,
    Violet,
}

@Serializable
enum class StreamStatsStyle {
    Compact,
    Detailed,
}

@Serializable
enum class StreamStatsPosition {
    Left,
    Center,
    Right,
}

@Serializable
enum class CatalogBackgroundPreset {
    @kotlinx.serialization.SerialName("colorful-abstract")
    ColorfulAbstract,

    @kotlinx.serialization.SerialName("original")
    Original,
}

@Serializable
data class StreamStatsMetrics(
    val fps: Boolean = false,
    val ping: Boolean = true,
    val bitrate: Boolean = false,
    val battery: Boolean = true,
    val connection: Boolean = true,
    val resolution: Boolean = false,
    val codec: Boolean = false,
    val location: Boolean = true,
    val latency: Boolean = true,
    val packetLoss: Boolean = true,
) {
    fun enabledCount(): Int = listOf(fps, ping, bitrate, battery, connection, resolution, codec, location, latency, packetLoss).count { it }
}

@Serializable
enum class IntroMusicStartMode {
    @kotlinx.serialization.SerialName("muted")
    Muted,

    @kotlinx.serialization.SerialName("playing")
    Playing,
}

@Serializable
enum class AppLaunchPage {
    @kotlinx.serialization.SerialName("store")
    Store,

    @kotlinx.serialization.SerialName("library")
    Library,
}

enum class SessionTimerMode {
    Countdown,
    Stopwatch,
}

data class SmartSessionLimit(
    val tierLabel: String,
    val limitHours: Int,
    val mode: SessionTimerMode,
)

internal val SESSION_WARNING_THRESHOLDS_SECONDS = listOf(
    30 * 60,
    10 * 60,
    5 * 60,
    3 * 60,
    60,
)

internal fun sessionElapsedSeconds(startedAtMs: Long, nowMs: Long): Int =
    ((nowMs - startedAtMs).coerceAtLeast(0L) / 1000L).toInt()

internal fun sessionRemainingSeconds(limit: SmartSessionLimit, startedAtMs: Long, nowMs: Long): Int {
    val limitSeconds = limit.limitHours * 60 * 60
    return (limitSeconds - sessionElapsedSeconds(startedAtMs, nowMs)).coerceAtLeast(0)
}

internal fun sessionWarningThresholdCrossed(previousRemainingSeconds: Int?, remainingSeconds: Int): Int? {
    val previous = previousRemainingSeconds ?: return null
    return SESSION_WARNING_THRESHOLDS_SECONDS
        .filter { threshold -> previous > threshold && remainingSeconds <= threshold }
        .minOrNull()
}

@Serializable
data class StreamSettings(
    val resolution: String = "1920x1080",
    val aspectRatio: String = "16:9",
    val fps: Int = 60,
    val maxBitrateMbps: Int = 75,
    val codec: VideoCodec = VideoCodec.H264,
    val colorQuality: ColorQuality = ColorQuality.TenBit420,
    val hdrEnabled: Boolean = false,
    val region: String = "",
    val keyboardLayout: String = "en-US",
    val gameLanguage: String = "en_US",
    val sessionProxyEnabled: Boolean = false,
    val sessionProxyUrl: String = "",
    val enableL4S: Boolean = false,
    val enableCloudGsync: Boolean = false,
    val mouseSensitivity: Float = 1f,
    val mouseAcceleration: Int = 1,
    val streamSharpeningEnabled: Boolean = false,
    val streamSharpeningAmount: Float = 0.25f,
    val microphoneMode: MicrophoneMode = MicrophoneMode.Disabled,
    val microphoneDeviceId: String = "",
)

@Serializable
enum class TouchControllerStyle {
    V1,
    V2
}

@Serializable
data class TouchOffset(val x: Float = 0f, val y: Float = 0f)

@Serializable
data class AndroidTouchSettings(
    val enabled: Boolean = true,
    val mousePad: Boolean = true,
    val opacity: Float = 0.82f,
    val scale: Float = 1f,
    val buttonScale: Float = 1.102468f,
    val stickScale: Float = 1f,
    val edgePaddingDp: Float = 14f,
    val bottomPaddingDp: Float = 10f,
    val leftOffsetXDp: Float = 0f,
    val leftOffsetYDp: Float = 0f,
    val rightOffsetXDp: Float = 0f,
    val rightOffsetYDp: Float = 0f,
    val mouseDirectClick: Boolean = false,
    val offsets: Map<String, TouchOffset> = mapOf(
        "lstick_landscape" to TouchOffset(-67.02336f, 1.4236208f),
        "l3_landscape" to TouchOffset(-159.65048f, 119.79623f),
        "lt_landscape" to TouchOffset(32.63997f, -52.50644f),
        "dpad_landscape" to TouchOffset(47.38254f, -131.70163f),
        "rb_landscape" to TouchOffset(-124.94213f, -107.18774f),
        "lb_landscape" to TouchOffset(119.051155f, -100.54266f),
        "face_landscape" to TouchOffset(-20.225464f, -132.01855f),
        "rstick_landscape" to TouchOffset(96.44574f, -7.9870353f),
        "r3_landscape" to TouchOffset(191.65938f, 125.07891f),
        "rt_landscape" to TouchOffset(-30.344517f, -57.420998f)
    ),
    val touchControllerStyle: TouchControllerStyle = TouchControllerStyle.V1,
) {
    fun getOffset(key: String): TouchOffset = offsets[key] ?: TouchOffset()

    fun withOffset(key: String, x: Float, y: Float): AndroidTouchSettings {
        val newOffsets = offsets.toMutableMap()
        newOffsets[key] = TouchOffset(x, y)
        return this.copy(offsets = newOffsets)
    }

    fun withResetOffsets(): AndroidTouchSettings {
        val defaultSettings = AndroidTouchSettings()
        return this.copy(
            opacity = defaultSettings.opacity,
            scale = defaultSettings.scale,
            buttonScale = defaultSettings.buttonScale,
            stickScale = defaultSettings.stickScale,
            edgePaddingDp = defaultSettings.edgePaddingDp,
            bottomPaddingDp = defaultSettings.bottomPaddingDp,
            leftOffsetXDp = defaultSettings.leftOffsetXDp,
            leftOffsetYDp = defaultSettings.leftOffsetYDp,
            rightOffsetXDp = defaultSettings.rightOffsetXDp,
            rightOffsetYDp = defaultSettings.rightOffsetYDp,
            offsets = defaultSettings.offsets
        )
    }
}

@Serializable
data class AppSettings(
    val stream: StreamSettings = StreamSettings(),
    val streamPreset: StreamPreset = StreamPreset.Recommended,
    val posterSizeScale: Float = 1f,
    val compactGameCards: Boolean = true,
    val handheldLandscapeFourColumnGrid: Boolean = false,
    val handheldLandscapeSquareCards: Boolean = false,
    val nerdCatalogBackground: Boolean = false,
    val catalogBackgroundPreset: CatalogBackgroundPreset = CatalogBackgroundPreset.ColorfulAbstract,
    val nerdCatalogBackgroundUri: String? = null,
    val tvSafeAreaPaddingDp: Float = 16f,
    val tvLayoutProfileVersion: Int = 0,
    val localTvRemoteEnabled: Boolean = false,
    val showGameStoreLabels: Boolean = true,
    val expressiveUi: Boolean = true,
    val dynamicColor: Boolean = false,
    val uiAccent: UiAccent = UiAccent.OpenNow,
    val launchPage: AppLaunchPage = AppLaunchPage.Store,
    val nerdMode: Boolean = false,
    val hideStreamButtons: Boolean = false,
    val showAntiAfkIndicator: Boolean = true,
    val showStatsOnLaunch: Boolean = false,
    val streamStatsStyle: StreamStatsStyle = StreamStatsStyle.Compact,
    val streamStatsPosition: StreamStatsPosition = StreamStatsPosition.Right,
    val streamStatsMetrics: StreamStatsMetrics = StreamStatsMetrics(),
    val phoneRumbleFallback: Boolean = true,
    val hideServerSelector: Boolean = false,
    val controllerMode: Boolean = false,
    val controllerUiSounds: Boolean = true,
    val controllerMouseEmulation: Boolean = false,
    val controllerBackgroundAnimations: Boolean = true,
    val controllerThemeStyle: String = "aurora",
    val controllerThemeColor: ControllerThemeRgb = ControllerThemeRgb(),
    val controllerLibraryGameBackdrop: Boolean = true,
    val autoLoadControllerLibrary: Boolean = false,
    val autoFullScreen: Boolean = true,
    val streamIntroMusic: Boolean = false,
    val streamIntroStartMode: IntroMusicStartMode = IntroMusicStartMode.Muted,
    val queueReadyMusic: Boolean = false,
    val stretchStreamToFill: Boolean = false,
    val stretchStreamToZoom: Boolean = false,
    val favoriteGameIds: List<String> = emptyList(),
    val defaultGameVariantIds: Map<String, String> = emptyMap(),
    val sessionCounterEnabled: Boolean = true,
    val sessionClockShowEveryMinutes: Int = 60,
    val sessionClockShowDurationSeconds: Int = 30,
    val clipboardPaste: Boolean = true,
    val androidTouch: AndroidTouchSettings = AndroidTouchSettings(),
    val androidStreamGuideDismissed: Boolean = false,
    val androidPhysicalControllerPromptDismissed: Boolean = false,
    val discordRichPresence: Boolean = false,
    val autoCheckForUpdates: Boolean = true,
    val analyticsOptOut: Boolean = true,
    val analyticsConsentAsked: Boolean = false,
    val allowEscapeToExitFullscreen: Boolean = false,
)

internal const val MIN_GAME_CARD_SCALE = 0.75f
internal const val MAX_GAME_CARD_SCALE = 1.4f

internal val AppSettings.analyticsSharingEnabled: Boolean
    get() = analyticsConsentAsked && !analyticsOptOut

internal fun streamResolutionPixels(settings: StreamSettings): Pair<Int, Int> {
    if (!isKnownStreamResolution(settings.resolution)) {
        parseResolutionPixelsOrNull(settings.resolution)?.let { return it }
    }
    return parseResolutionPixels(normalizeStreamResolutionForAspect(settings.resolution, settings.aspectRatio))
}

internal data class StreamResolutionMismatch(
    val actualResolution: String,
    val expectedResolution: String,
    val serverNegotiatedResolution: String? = null,
)

internal val StreamResolutionMismatch.isServerNegotiatedFallback: Boolean
    get() = serverNegotiatedResolution == actualResolution

internal fun streamRuntimeResolutionMismatch(
    settings: StreamSettings,
    actualResolution: String?,
    serverNegotiatedResolution: String? = null,
): StreamResolutionMismatch? {
    val actualPixels = parseResolutionPixelsOrNull(actualResolution) ?: return null
    val expectedPixels = streamResolutionPixels(settings)
    if (actualPixels == expectedPixels) return null
    val negotiatedPixels = parseResolutionPixelsOrNull(serverNegotiatedResolution)
    return StreamResolutionMismatch(
        actualResolution = "${actualPixels.first}x${actualPixels.second}",
        expectedResolution = "${expectedPixels.first}x${expectedPixels.second}",
        serverNegotiatedResolution = negotiatedPixels
            ?.takeIf { it == actualPixels }
            ?.let { "${it.first}x${it.second}" },
    )
}

internal fun streamResolutionOptionsForAspect(aspectRatio: String): List<String> =
    STREAM_RESOLUTION_OPTIONS.filter { it.aspectRatio == aspectRatio }.map { it.value }

internal fun streamResolutionChoicesForAspect(aspectRatio: String): List<StreamResolutionChoice> =
    STREAM_RESOLUTION_OPTIONS.filter { it.aspectRatio == aspectRatio }.map { it.toChoice() }

internal fun streamAspectRatioOptions(): List<String> =
    STREAM_RESOLUTION_OPTIONS.map { it.aspectRatio }.distinct()

internal fun streamAspectRatioForResolution(resolution: String): String? =
    STREAM_RESOLUTION_OPTIONS.firstOrNull { it.value == resolution }?.aspectRatio

internal fun normalizeStreamResolutionForAspect(resolution: String, aspectRatio: String): String {
    val normalizedAspect = aspectRatio.trim()
    val options = streamResolutionOptionsForAspect(normalizedAspect)
    if (options.isEmpty()) return resolution
    if (STREAM_RESOLUTION_OPTIONS.any { it.value == resolution && it.aspectRatio == normalizedAspect }) {
        return resolution
    }

    val tier = STREAM_RESOLUTION_OPTIONS.firstOrNull { it.value == resolution }?.tier
        ?: resolutionTierForHeight(parseResolutionPixels(resolution).second)
    PREFERRED_RESOLUTION_BY_TIER_AND_ASPECT[tier]?.get(normalizedAspect)?.let { preferred ->
        if (preferred in options) return preferred
    }

    val requestedPixels = parseResolutionPixels(resolution).let { it.first * it.second }
    return options.minWithOrNull(
        compareBy<String> { option ->
            val pixels = parseResolutionPixels(option).let { it.first * it.second }
            abs(pixels - requestedPixels)
        }.thenBy { option ->
            parseResolutionPixels(option).first * parseResolutionPixels(option).second
        },
    ) ?: options.first()
}

internal fun normalizeStreamResolutionForAspectAndPlan(
    resolution: String,
    aspectRatio: String,
    subscriptionInfo: SubscriptionInfo?,
    fallbackMembershipTier: String?,
): String {
    val customResolution = customStreamResolutionOrNull(resolution)
    if (customResolution != null && customResolutionAllowedForPlan(customResolution, subscriptionInfo, fallbackMembershipTier)) {
        return "${customResolution.first}x${customResolution.second}"
    }

    val normalized = normalizeStreamResolutionForAspect(resolution, aspectRatio)
    val choices = streamResolutionChoicesForAspect(aspectRatio)
    val current = choices.firstOrNull { it.value == normalized }
    if (current?.isAvailableFor(subscriptionInfo, fallbackMembershipTier) == true) return normalized

    val availableChoices = choices.filter { it.isAvailableFor(subscriptionInfo, fallbackMembershipTier) }
        .ifEmpty {
            streamResolutionChoicesForAspect("16:9").filter { it.isAvailableFor(subscriptionInfo, fallbackMembershipTier) }
        }
    if (availableChoices.isEmpty()) return normalized

    val requestedPixels = parseResolutionPixels(normalized).let { it.first * it.second }
    return availableChoices
        .filter { it.width * it.height <= requestedPixels }
        .maxByOrNull { it.width * it.height }
        ?.value
        ?: availableChoices.minByOrNull { it.width * it.height }?.value
        ?: normalized
}

internal fun parseResolutionPixels(value: String): Pair<Int, Int> {
    val parts = value.split("x")
    val width = parts.getOrNull(0)?.toIntOrNull()
    val height = parts.getOrNull(1)?.toIntOrNull()
    return if (width != null && height != null && width > 0 && height > 0) width to height else 1920 to 1080
}

internal fun streamSettingsSessionSignature(settings: StreamSettings): String {
    val compatible = settings.withCodecColorCompatibility()
    val (width, height) = streamResolutionPixels(compatible)
    return listOf(
        "opennow-android-stream-v1",
        "res=${width}x$height",
        "fps=${compatible.fps}",
        "bitrate=${compatible.maxBitrateMbps}",
        "codec=${compatible.codec.name}",
        "color=${compatible.colorQuality.name}",
        "hdr=${if (compatible.hdrEnabled) 1 else 0}",
        "l4s=${if (compatible.enableL4S) 1 else 0}",
        "gsync=${if (compatible.enableCloudGsync) 1 else 0}",
        "keyboard=${compatible.keyboardLayout.trim()}",
        "language=${compatible.gameLanguage.trim()}",
    ).joinToString(";")
}

internal data class StreamResolutionOption(
    val value: String,
    val aspectRatio: String,
    val tier: String,
    val requiredPlan: StreamResolutionPlan = StreamResolutionPlan.Free,
) {
    fun toChoice(): StreamResolutionChoice {
        val (width, height) = parseResolutionPixels(value)
        return StreamResolutionChoice(
            value = value,
            width = width,
            height = height,
            aspectRatio = aspectRatio,
            requiredPlan = requiredPlan,
        )
    }
}

internal enum class StreamResolutionPlan {
    Free,
    Priority,
    Ultimate,
}

internal data class StreamResolutionChoice(
    val value: String,
    val width: Int,
    val height: Int,
    val aspectRatio: String,
    val requiredPlan: StreamResolutionPlan,
) {
    val label: String
        get() = "$width x $height"

    val requiredPlanLabel: String?
        get() = when (requiredPlan) {
            StreamResolutionPlan.Free -> null
            StreamResolutionPlan.Priority -> "Priority"
            StreamResolutionPlan.Ultimate -> "Ultimate"
        }

    fun isAvailableFor(subscriptionInfo: SubscriptionInfo?, fallbackMembershipTier: String?): Boolean {
        if (requiredPlan == StreamResolutionPlan.Free) return true
        return streamResolutionPlanRank(effectiveStreamingPlan(subscriptionInfo, fallbackMembershipTier)) >= streamResolutionPlanRank(requiredPlan)
    }
}

internal fun hasUltimateStreamingPlan(subscriptionInfo: SubscriptionInfo?, fallbackMembershipTier: String?): Boolean =
    streamResolutionPlanRank(effectiveStreamingPlan(subscriptionInfo, fallbackMembershipTier)) >=
        streamResolutionPlanRank(StreamResolutionPlan.Ultimate)

internal fun maxStreamFpsFor(subscriptionInfo: SubscriptionInfo?, fallbackMembershipTier: String?): Int =
    if (hasUltimateStreamingPlan(subscriptionInfo, fallbackMembershipTier)) 360 else 60

internal fun StreamSettings.withFpsAllowed(subscriptionInfo: SubscriptionInfo?, fallbackMembershipTier: String?): StreamSettings {
    val maxFps = maxStreamFpsFor(subscriptionInfo, fallbackMembershipTier)
    val allowedFps = fps.coerceIn(30, maxFps)
    return if (allowedFps == fps) this else copy(fps = allowedFps)
}

internal fun smartSessionLimitFor(subscriptionInfo: SubscriptionInfo?, fallbackMembershipTier: String?): SmartSessionLimit {
    return when (effectiveStreamingPlan(subscriptionInfo, fallbackMembershipTier)) {
        StreamResolutionPlan.Ultimate -> SmartSessionLimit("Ultimate", 8, SessionTimerMode.Stopwatch)
        StreamResolutionPlan.Priority -> SmartSessionLimit("Performance", 6, SessionTimerMode.Stopwatch)
        StreamResolutionPlan.Free -> SmartSessionLimit("Free", 1, SessionTimerMode.Countdown)
    }
}

internal fun monthlyHourLimitFor(subscriptionInfo: SubscriptionInfo?, fallbackMembershipTier: String?): Double? {
    val reported = subscriptionInfo?.totalHours?.takeIf { it > 0.0 }
    if (reported != null) return reported
    return when (effectiveStreamingPlan(subscriptionInfo, fallbackMembershipTier)) {
        StreamResolutionPlan.Free -> null
        StreamResolutionPlan.Priority,
        StreamResolutionPlan.Ultimate,
        -> 100.0
    }
}

internal fun monthlyHoursRemainingFor(subscriptionInfo: SubscriptionInfo?, fallbackMembershipTier: String?): Double? {
    val reported = subscriptionInfo?.remainingHours?.takeIf { it > 0.0 }
    if (reported != null) return reported
    val limit = monthlyHourLimitFor(subscriptionInfo, fallbackMembershipTier) ?: return null
    return (limit - (subscriptionInfo?.usedHours ?: 0.0)).coerceAtLeast(0.0)
}

internal fun StreamSettings.withHdrAllowed(subscriptionInfo: SubscriptionInfo?, fallbackMembershipTier: String?): StreamSettings =
    if (hdrEnabled && !hasUltimateStreamingPlan(subscriptionInfo, fallbackMembershipTier)) copy(hdrEnabled = false).withCodecColorCompatibility() else withCodecColorCompatibility()

internal fun VideoCodec.availableForAndroidSettings(): Boolean =
    true

internal fun ColorQuality.availableForAndroidSettings(): Boolean =
    !isChroma444()

internal fun ColorQuality.availableForCodec(codec: VideoCodec): Boolean =
    availableForAndroidSettings() && codec.availableForAndroidSettings()

internal fun StreamSettings.withAndroidSettingsAvailability(): StreamSettings {
    val availableCodec = if (codec.availableForAndroidSettings()) codec else VideoCodec.H264
    val normalized = if (availableCodec == codec) this else copy(codec = availableCodec)
    return normalized.withCodecColorCompatibility()
}

internal fun StreamSettings.withCodecColorCompatibility(): StreamSettings {
    val compatibleColor = when {
        colorQuality.isChroma444() -> colorQuality.asChroma420()
        hdrEnabled && !colorQuality.isTenBit() -> ColorQuality.TenBit420
        else -> colorQuality
    }
    return if (compatibleColor == colorQuality) this else copy(colorQuality = compatibleColor)
}

internal fun StreamSettings.applyingStreamPreset(preset: StreamPreset): StreamSettings {
    if (preset == StreamPreset.Custom) return this
    val target = streamPresetTargetForAspect(preset, aspectRatio)
    return copy(
        resolution = target.resolution,
        aspectRatio = target.aspectRatio,
        fps = target.fps,
        maxBitrateMbps = target.maxBitrateMbps,
        colorQuality = ColorQuality.EightBit420,
        hdrEnabled = false,
        enableCloudGsync = false,
    ).withAndroidSettingsAvailability()
}

internal fun StreamSettings.withResolutionAllowed(subscriptionInfo: SubscriptionInfo?, fallbackMembershipTier: String?): StreamSettings {
    val customResolution = customStreamResolutionOrNull(resolution)
    if (customResolution != null && customResolutionAllowedForPlan(customResolution, subscriptionInfo, fallbackMembershipTier)) {
        val normalizedResolution = "${customResolution.first}x${customResolution.second}"
        return if (normalizedResolution == resolution) this else copy(resolution = normalizedResolution)
    }

    val allowedAspectRatio = if (streamResolutionChoicesForAspect(aspectRatio).any { it.isAvailableFor(subscriptionInfo, fallbackMembershipTier) }) {
        aspectRatio
    } else {
        "16:9"
    }
    val allowedResolution = normalizeStreamResolutionForAspectAndPlan(resolution, allowedAspectRatio, subscriptionInfo, fallbackMembershipTier)
    return if (allowedResolution == resolution && allowedAspectRatio == aspectRatio) this else copy(resolution = allowedResolution, aspectRatio = allowedAspectRatio)
}

private fun isKnownStreamResolution(resolution: String): Boolean =
    STREAM_RESOLUTION_OPTIONS.any { it.value == resolution }

private fun customStreamResolutionOrNull(resolution: String): Pair<Int, Int>? =
    parseResolutionPixelsOrNull(resolution)?.takeUnless {
        isKnownStreamResolution(resolution) || resolution in UNSUPPORTED_LEGACY_STREAM_RESOLUTIONS
    }

private val UNSUPPORTED_LEGACY_STREAM_RESOLUTIONS = setOf(
    "1600x720",
    "2400x1080",
    "3200x1440",
    "4800x2160",
)

private fun customResolutionAllowedForPlan(
    resolution: Pair<Int, Int>,
    subscriptionInfo: SubscriptionInfo?,
    fallbackMembershipTier: String?,
): Boolean {
    val availableChoices = STREAM_RESOLUTION_OPTIONS
        .map { it.toChoice() }
        .filter { it.isAvailableFor(subscriptionInfo, fallbackMembershipTier) }
    if (availableChoices.isEmpty()) return false

    val (width, height) = resolution
    val pixels = width * height
    return width <= availableChoices.maxOf { it.width } &&
        height <= availableChoices.maxOf { it.height } &&
        pixels <= availableChoices.maxOf { it.width * it.height }
}

internal val STREAM_RESOLUTION_OPTIONS = listOf(
    StreamResolutionOption("1280x720", "16:9", "720"),
    StreamResolutionOption("1366x768", "16:9", "768"),
    StreamResolutionOption("1600x900", "16:9", "900"),
    StreamResolutionOption("1280x800", "16:10", "720"),
    StreamResolutionOption("1440x900", "16:10", "900"),
    StreamResolutionOption("1680x1050", "16:10", "1050"),
    StreamResolutionOption("1920x1080", "16:9", "1080"),
    StreamResolutionOption("1920x1200", "16:10", "1080"),
    StreamResolutionOption("1024x768", "4:3", "768"),
    StreamResolutionOption("1112x834", "4:3", "834"),
    StreamResolutionOption("1600x1200", "4:3", "1080"),
    StreamResolutionOption("1280x1024", "5:4", "1050"),
    StreamResolutionOption("1680x720", "21:9", "720"),
    StreamResolutionOption("2560x1080", "21:9", "1080", StreamResolutionPlan.Priority),
    StreamResolutionOption("3840x1080", "32:9", "1080", StreamResolutionPlan.Priority),
    StreamResolutionOption("2560x1440", "16:9", "1440", StreamResolutionPlan.Priority),
    StreamResolutionOption("2560x1600", "16:10", "1440", StreamResolutionPlan.Priority),
    StreamResolutionOption("3440x1440", "21:9", "1440", StreamResolutionPlan.Priority),
    StreamResolutionOption("5120x1440", "32:9", "1440", StreamResolutionPlan.Priority),
    StreamResolutionOption("3840x1600", "24:10", "1440", StreamResolutionPlan.Priority),
    StreamResolutionOption("3840x2160", "16:9", "2160", StreamResolutionPlan.Ultimate),
    StreamResolutionOption("3456x2160", "16:10", "2160", StreamResolutionPlan.Ultimate),
    StreamResolutionOption("5120x2160", "21:9", "2160", StreamResolutionPlan.Ultimate),
    StreamResolutionOption("5120x2880", "16:9", "2880", StreamResolutionPlan.Ultimate),
)

private val PREFERRED_RESOLUTION_BY_TIER_AND_ASPECT = mapOf(
    "720" to mapOf("16:9" to "1280x720", "16:10" to "1280x800", "4:3" to "1024x768", "21:9" to "1680x720"),
    "768" to mapOf("16:9" to "1366x768", "4:3" to "1024x768"),
    "834" to mapOf("4:3" to "1112x834"),
    "900" to mapOf("16:9" to "1600x900", "16:10" to "1440x900"),
    "1050" to mapOf("16:10" to "1680x1050", "5:4" to "1280x1024"),
    "1080" to mapOf("16:9" to "1920x1080", "16:10" to "1920x1200", "4:3" to "1600x1200", "21:9" to "2560x1080", "32:9" to "3840x1080"),
    "1440" to mapOf("16:9" to "2560x1440", "16:10" to "2560x1600", "21:9" to "3440x1440", "24:10" to "3840x1600", "32:9" to "5120x1440"),
    "2160" to mapOf("16:9" to "3840x2160", "16:10" to "3456x2160", "21:9" to "5120x2160"),
    "2880" to mapOf("16:9" to "5120x2880"),
)

private data class StreamPresetTarget(
    val resolution: String,
    val aspectRatio: String,
    val fps: Int,
    val maxBitrateMbps: Int,
)

private fun streamPresetTargetForAspect(preset: StreamPreset, aspectRatio: String): StreamPresetTarget {
    val normalizedAspect = aspectRatio.takeIf { streamResolutionOptionsForAspect(it).isNotEmpty() } ?: "16:9"
    val maxHeight = when (preset) {
        StreamPreset.Custom -> Int.MAX_VALUE
        StreamPreset.Recommended -> 1200
        StreamPreset.LowDataSaver -> 800
        StreamPreset.Medium -> 1200
        StreamPreset.High -> 1600
    }
    val options = STREAM_RESOLUTION_OPTIONS
        .filter { it.aspectRatio == normalizedAspect }
        .sortedBy { it.pixelCount() }
    val resolution = options
        .filter { parseResolutionPixels(it.value).second <= maxHeight }
        .maxByOrNull { it.pixelCount() }
        ?: options.firstOrNull()
        ?: StreamResolutionOption("1280x720", "16:9", "720")

    return when (preset) {
        StreamPreset.Custom -> StreamPresetTarget(resolution.value, resolution.aspectRatio, 60, 75)
        StreamPreset.Recommended -> StreamPresetTarget(resolution.value, resolution.aspectRatio, 60, 35)
        StreamPreset.LowDataSaver -> StreamPresetTarget(resolution.value, resolution.aspectRatio, 30, 12)
        StreamPreset.Medium -> StreamPresetTarget(resolution.value, resolution.aspectRatio, 60, 35)
        StreamPreset.High -> StreamPresetTarget(resolution.value, resolution.aspectRatio, 360, 75)
    }
}

private fun ColorQuality.isChroma444(): Boolean =
    this == ColorQuality.EightBit444 || this == ColorQuality.TenBit444

private fun ColorQuality.isTenBit(): Boolean =
    this == ColorQuality.TenBit420 || this == ColorQuality.TenBit444

private fun ColorQuality.asChroma420(): ColorQuality =
    when (this) {
        ColorQuality.TenBit444 -> ColorQuality.TenBit420
        ColorQuality.EightBit444 -> ColorQuality.EightBit420
        else -> this
    }

private fun resolutionTierForHeight(height: Int): String =
    when {
        height >= 2600 -> "2880"
        height >= 2000 -> "2160"
        height >= 1320 -> "1440"
        height >= 1120 -> "1080"
        height >= 975 -> "1050"
        height >= 850 -> "900"
        height >= 800 -> "834"
        height >= 740 -> "768"
        else -> "720"
    }

private fun planForMembershipTier(membershipTier: String?): StreamResolutionPlan {
    val normalized = membershipTier.orEmpty().uppercase(Locale.US).replace(Regex("[^A-Z0-9]+"), "")
    return when {
        normalized.contains("ULTIMATE") || normalized.contains("RTX3080") -> StreamResolutionPlan.Ultimate
        normalized.contains("PRIORITY") || normalized.contains("PERFORMANCE") || normalized.contains("FOUNDERS") -> StreamResolutionPlan.Priority
        else -> StreamResolutionPlan.Free
    }
}

private fun effectiveStreamingPlan(
    subscriptionInfo: SubscriptionInfo?,
    fallbackMembershipTier: String?,
): StreamResolutionPlan =
    listOf(
        planForMembershipTier(subscriptionInfo?.membershipTier),
        planForMembershipTier(fallbackMembershipTier),
    ).maxBy { streamResolutionPlanRank(it) }

private fun streamResolutionPlanRank(plan: StreamResolutionPlan): Int =
    when (plan) {
        StreamResolutionPlan.Free -> 0
        StreamResolutionPlan.Priority -> 1
        StreamResolutionPlan.Ultimate -> 2
    }

@Serializable
data class ControllerThemeRgb(
    val r: Int = 124,
    val g: Int = 241,
    val b: Int = 177,
)

@Serializable
data class LoginProvider(
    val idpId: String,
    val code: String,
    val displayName: String,
    val streamingServiceUrl: String,
    val priority: Int = 0,
)

val LoginProvider.supportsDeviceCodeLogin: Boolean
    get() = code.equals("NVIDIA", ignoreCase = true)

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val expiresAt: Long,
    val clientToken: String? = null,
    val clientTokenExpiresAt: Long? = null,
    val authClientId: String? = null,
)

@Serializable
data class AuthUser(
    val userId: String,
    val displayName: String,
    val email: String? = null,
    val avatarUrl: String? = null,
    val membershipTier: String = "FREE",
)

@Serializable
data class AuthSession(
    val provider: LoginProvider,
    val tokens: AuthTokens,
    val user: AuthUser,
)

data class DeviceLoginPrompt(
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String? = null,
    val expiresAt: Long,
)

@Serializable
data class SavedAccount(
    val userId: String,
    val displayName: String,
    val email: String? = null,
    val avatarUrl: String? = null,
    val membershipTier: String = "FREE",
    val providerCode: String = "NVIDIA",
)

@Serializable
data class PersistedAuthState(
    val sessions: List<AuthSession> = emptyList(),
    val activeUserId: String? = null,
    val selectedProvider: LoginProvider? = null,
)

@Serializable
data class StreamRegion(
    val name: String,
    val url: String,
    val pingMs: Long? = null,
)

@Serializable
data class EntitledResolution(
    val width: Int,
    val height: Int,
    val fps: Int,
)

@Serializable
data class StorageAddon(
    val type: String = "PERMANENT_STORAGE",
    val sizeGb: Double? = null,
    val usedGb: Double? = null,
    val regionName: String? = null,
    val regionCode: String? = null,
    val status: String? = null,
    val subType: String? = null,
    val autoPayEnabled: Boolean? = null,
)

@Serializable
data class SubscriptionInfo(
    val membershipTier: String = "FREE",
    val subscriptionType: String? = null,
    val subscriptionSubType: String? = null,
    val allottedHours: Double = 0.0,
    val purchasedHours: Double = 0.0,
    val rolledOverHours: Double = 0.0,
    val usedHours: Double = 0.0,
    val remainingHours: Double = 0.0,
    val totalHours: Double = 0.0,
    val state: String? = null,
    val isGamePlayAllowed: Boolean? = null,
    val isUnlimited: Boolean = false,
    val storageAddon: StorageAddon? = null,
    val entitledResolutions: List<EntitledResolution> = emptyList(),
)

@Serializable
data class AccountConnector(
    val store: String,
    val label: String,
    val supported: Boolean = true,
    val required: Boolean = false,
    val userDisplayName: String? = null,
    val userIdentifier: String? = null,
    val expiresInSeconds: Long? = null,
    val syncedGameCount: Int? = null,
    val syncState: String? = null,
    val syncDate: String? = null,
)

val AccountConnector.isLinked: Boolean
    get() = !userDisplayName.isNullOrBlank() ||
        !userIdentifier.isNullOrBlank() ||
        expiresInSeconds != null ||
        syncedGameCount != null ||
        !syncState.isNullOrBlank() ||
        !syncDate.isNullOrBlank()

@Immutable
@Serializable
data class GameVariant(
    val id: String,
    val store: String,
    val supportedControls: List<String> = emptyList(),
    val librarySelected: Boolean? = null,
    val libraryStatus: String? = null,
    val lastPlayedDate: String? = null,
    val gfnStatus: String? = null,
)

@Immutable
@Serializable
data class GameInfo(
    val id: String,
    val uuid: String? = null,
    val launchAppId: String? = null,
    val title: String,
    val catalogSectionId: String? = null,
    val catalogSectionTitle: String? = null,
    val description: String? = null,
    val longDescription: String? = null,
    val featureLabels: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val imageUrl: String? = null,
    val tvCardImageUrl: String? = null,
    val screenshotUrl: String? = null,
    val screenshotUrls: List<String> = emptyList(),
    val tvBannerUrl: String? = null,
    val playType: String? = null,
    val membershipTierLabel: String? = null,
    val publisherName: String? = null,
    val contentRatings: List<String> = emptyList(),
    val playabilityState: String? = null,
    val availableStores: List<String> = emptyList(),
    val searchText: String? = null,
    val lastPlayed: String? = null,
    val isInLibrary: Boolean = false,
    val selectedVariantIndex: Int = 0,
    val variants: List<GameVariant> = emptyList(),
)

private val primaryCatalogStoreKeys = setOf(
    "STEAM",
    "EPIC",
    "EPIC_GAMES_STORE",
    "EGS",
    "XBOX",
    "XBOX_GAME_PASS",
    "MICROSOFT",
    "MICROSOFT_STORE",
)

private val ownedLibraryStatuses = setOf("MANUAL", "PLATFORM_SYNC", "IN_LIBRARY")

internal fun isOwnedLibraryStatus(status: String?): Boolean =
    status in ownedLibraryStatuses

internal fun isOwnedGameVariant(variant: GameVariant): Boolean =
    isOwnedLibraryStatus(variant.libraryStatus)

internal fun isGameInLibrary(game: GameInfo): Boolean =
    game.isInLibrary || game.variants.any(::isOwnedGameVariant)

internal fun gameTrackingKey(game: GameInfo): String =
    game.uuid?.takeIf { it.isNotBlank() }
        ?: game.launchAppId?.takeIf { it.isNotBlank() }
        ?: game.id

internal fun shouldLaunchWithAccountLinked(game: GameInfo, selectedVariant: GameVariant?): Boolean {
    if (game.playType == "INSTALL_TO_PLAY") return false
    if (selectedVariant?.let(::isOwnedGameVariant) == true) return true
    return isGameInLibrary(game)
}

internal fun mergeKnownLibraryGames(vararg groups: List<GameInfo>): List<GameInfo> {
    val byKey = linkedMapOf<String, GameInfo>()
    for (game in groups.flatMap { it }) {
        if (!isGameInLibrary(game)) continue
        val key = game.uuid ?: game.id
        val existing = byKey[key]
        byKey[key] = if (existing == null) game.copy(isInLibrary = true) else mergeGameInfo(existing, game).copy(isInLibrary = true)
    }
    return byKey.values.toList()
}

internal fun mergePanelGameWithMetadata(panelGame: GameInfo, metadataGame: GameInfo): GameInfo =
    mergeGameInfo(panelGame, metadataGame).copy(
        imageUrl = metadataGame.imageUrl ?: panelGame.imageUrl,
        tvCardImageUrl = metadataGame.tvCardImageUrl ?: panelGame.tvCardImageUrl,
        screenshotUrl = metadataGame.screenshotUrl ?: panelGame.screenshotUrl,
        screenshotUrls = (metadataGame.screenshotUrls + panelGame.screenshotUrls).distinct(),
        tvBannerUrl = metadataGame.tvBannerUrl ?: panelGame.tvBannerUrl,
    )

internal fun normalizeGameStore(store: String): String =
    store.uppercase(Locale.US).replace(Regex("[\\s-]+"), "_")

internal fun splitGameStoreKeys(store: String): List<String> =
    store.split(",")
        .map { normalizeGameStore(it.trim()) }
        .filter { it.isNotBlank() }

internal fun isPrimaryCatalogStoreValue(store: String): Boolean {
    val storeKeys = splitGameStoreKeys(store)
    return storeKeys.isNotEmpty() && storeKeys.all { it in primaryCatalogStoreKeys }
}

internal fun gameStoreDisplayName(store: String): String {
    val parts = store.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(store.trim()) }
    return parts.map { part ->
        when (normalizeGameStore(part)) {
            "EPIC", "EGS", "EPIC_GAMES_STORE" -> "Epic"
            "STEAM" -> "Steam"
            "XBOX", "XBOX_GAME_PASS" -> "Xbox"
            "MICROSOFT", "MICROSOFT_STORE" -> "Microsoft Store"
            else -> part.replace('_', ' ').lowercase(Locale.US)
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { word -> word.replaceFirstChar { char -> char.titlecase(Locale.US) } }
                .ifBlank { "Unknown" }
        }
    }.distinct().joinToString(" / ")
}

internal fun launchableGameVariants(variants: List<GameVariant>): List<GameVariant> {
    val uniqueVariants = variants.distinctBy { it.id }
    val individualPrimaryStores = uniqueVariants
        .map { splitGameStoreKeys(it.store) }
        .filter { it.size == 1 && it.first() in primaryCatalogStoreKeys }
        .flatten()
        .toSet()
    val filtered = uniqueVariants.filterNot { variant ->
        val storeKeys = splitGameStoreKeys(variant.store)
        storeKeys.size > 1 && storeKeys.all { it in individualPrimaryStores }
    }
    val byStore = linkedMapOf<String, GameVariant>()
    for (variant in filtered) {
        val storeKey = splitGameStoreKeys(variant.store).joinToString(",").ifBlank { normalizeGameStore(variant.store) }
        val existing = byStore[storeKey]
        if (existing == null || variantLaunchRank(variant) > variantLaunchRank(existing)) {
            byStore[storeKey] = variant
        }
    }
    return byStore.values.toList()
}

internal fun displayStoresForVariants(variants: List<GameVariant>): List<String> =
    launchableGameVariants(variants)
        .flatMap { variant -> gameStoreDisplayName(variant.store).split(" / ") }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalizeGameStore(it) }

internal fun libraryStoreDisplayNames(game: GameInfo): List<String> {
    val variants = when {
        game.variants.any(::isOwnedGameVariant) -> game.variants.filter(::isOwnedGameVariant)
        game.isInLibrary -> listOfNotNull(game.variants.firstOrNull { it.librarySelected == true })
            .ifEmpty { listOfNotNull(game.variants.getOrNull(game.selectedVariantIndex)) }
            .ifEmpty { game.variants.take(1) }
        else -> emptyList()
    }
    val variantStores = variants
        .flatMap { variant -> gameStoreDisplayName(variant.store).split(" / ") }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalizeGameStore(it) }
    if (variantStores.isNotEmpty()) return variantStores
    if (!game.isInLibrary) return emptyList()
    return game.availableStores
        .map(::gameStoreDisplayName)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { normalizeGameStore(it) }
}

private fun mergeGameInfo(left: GameInfo, right: GameInfo): GameInfo {
    val variants = (left.variants + right.variants).distinctBy { it.id }
    val selectedVariantId = left.variants.getOrNull(left.selectedVariantIndex)?.id
        ?: right.variants.getOrNull(right.selectedVariantIndex)?.id
    val selectedIndex = selectedVariantId?.let { id -> variants.indexOfFirst { it.id == id } } ?: -1
    return left.copy(
        uuid = left.uuid ?: right.uuid,
        launchAppId = left.launchAppId ?: right.launchAppId,
        description = left.description ?: right.description,
        longDescription = left.longDescription ?: right.longDescription,
        imageUrl = left.imageUrl ?: right.imageUrl,
        tvCardImageUrl = left.tvCardImageUrl ?: right.tvCardImageUrl,
        screenshotUrl = left.screenshotUrl ?: right.screenshotUrl,
        screenshotUrls = (left.screenshotUrls + right.screenshotUrls).distinct(),
        tvBannerUrl = left.tvBannerUrl ?: right.tvBannerUrl,
        playType = left.playType ?: right.playType,
        membershipTierLabel = left.membershipTierLabel ?: right.membershipTierLabel,
        publisherName = left.publisherName ?: right.publisherName,
        contentRatings = (left.contentRatings + right.contentRatings).distinct(),
        playabilityState = left.playabilityState ?: right.playabilityState,
        variants = variants,
        availableStores = displayStoresForVariants(variants),
        genres = (left.genres + right.genres).distinct(),
        featureLabels = (left.featureLabels + right.featureLabels).distinct(),
        searchText = listOfNotNull(left.searchText, right.searchText).joinToString(" ").ifBlank { null },
        lastPlayed = left.lastPlayed ?: right.lastPlayed,
        isInLibrary = left.isInLibrary || right.isInLibrary,
        selectedVariantIndex = if (selectedIndex >= 0) selectedIndex else left.selectedVariantIndex.coerceAtMost(max(variants.size - 1, 0)),
    )
}

private fun variantLaunchRank(variant: GameVariant): Int =
    when {
        variant.librarySelected == true && isOwnedGameVariant(variant) -> 4
        isOwnedGameVariant(variant) -> 3
        variant.id.all(Char::isDigit) -> 2
        else -> 1
    }

@Serializable
data class CatalogFilterOption(
    val id: String,
    val rawId: String,
    val label: String,
    val groupId: String,
    val groupLabel: String,
)

@Serializable
data class CatalogFilterGroup(
    val id: String,
    val label: String,
    val options: List<CatalogFilterOption>,
)

@Serializable
data class CatalogSortOption(
    val id: String,
    val label: String,
    val orderBy: String,
)

@Serializable
data class CatalogBrowseResult(
    val games: List<GameInfo>,
    val numberReturned: Int = games.size,
    val numberSupported: Int = games.size,
    val totalCount: Int = games.size,
    val hasNextPage: Boolean = false,
    val endCursor: String? = null,
    val searchQuery: String = "",
    val selectedSortId: String = "relevance",
    val selectedFilterIds: List<String> = emptyList(),
    val filterGroups: List<CatalogFilterGroup> = emptyList(),
    val sortOptions: List<CatalogSortOption> = emptyList(),
)

@Serializable
data class PrintedWasteZone(
    val QueuePosition: Int,
    val LastUpdated: Long = 0,
    val Region: String,
    val eta: Long? = null,
)

@Serializable
data class PrintedWasteServerMappingEntry(
    val title: String? = null,
    val region: String? = null,
    val is4080Server: Boolean? = null,
    val is5080Server: Boolean? = null,
    val nuked: Boolean? = null,
)

@Serializable
data class PingResult(
    val url: String,
    val pingMs: Long? = null,
    val error: String? = null,
)

@Serializable
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null,
)

@Serializable
data class MediaConnectionInfo(
    val ip: String,
    val port: Int,
)

@Serializable
data class NegotiatedStreamProfile(
    val resolution: String? = null,
    val fps: Int? = null,
    val codec: VideoCodec? = null,
    val colorQuality: ColorQuality? = null,
    val enableL4S: Boolean? = null,
    val enableCloudGsync: Boolean? = null,
    val enableReflex: Boolean? = null,
)

@Serializable
data class SessionAdMediaFile(
    val mediaFileUrl: String? = null,
    val encodingProfile: String? = null,
)

@Serializable
data class SessionAdInfo(
    val adId: String,
    val state: Int? = null,
    val adState: Int? = null,
    val adUrl: String? = null,
    val mediaUrl: String? = null,
    val adMediaFiles: List<SessionAdMediaFile> = emptyList(),
    val clickThroughUrl: String? = null,
    val adLengthInSeconds: Double? = null,
    val durationMs: Long? = null,
    val title: String? = null,
    val description: String? = null,
)

@Serializable
data class SessionOpportunityInfo(
    val state: String? = null,
    val queuePaused: Boolean? = null,
    val gracePeriodSeconds: Int? = null,
    val message: String? = null,
    val title: String? = null,
    val description: String? = null,
)

@Serializable
data class SessionAdState(
    val isAdsRequired: Boolean = false,
    val sessionAdsRequired: Boolean? = null,
    val isQueuePaused: Boolean? = null,
    val gracePeriodSeconds: Int? = null,
    val message: String? = null,
    val sessionAds: List<SessionAdInfo> = emptyList(),
    val ads: List<SessionAdInfo> = emptyList(),
    val opportunity: SessionOpportunityInfo? = null,
    val serverSentEmptyAds: Boolean = false,
)

@Serializable
data class StreamingFeatures(
    val reflex: Boolean? = null,
    val bitDepth: Int? = null,
    val cloudGsync: Boolean? = null,
    val chromaFormat: Int? = null,
    val enabledL4S: Boolean? = null,
    val trueHdr: Boolean? = null,
)

@Serializable
data class SessionInfo(
    val sessionId: String,
    val status: Int,
    val queuePosition: Int? = null,
    val seatSetupStep: Int? = null,
    val adState: SessionAdState? = null,
    val zone: String = "",
    val streamingBaseUrl: String? = null,
    val serverIp: String,
    val signalingServer: String,
    val signalingUrl: String,
    val gpuType: String? = null,
    val iceServers: List<IceServer> = emptyList(),
    val mediaConnectionInfo: MediaConnectionInfo? = null,
    val negotiatedStreamProfile: NegotiatedStreamProfile? = null,
    val requestedStreamingFeatures: StreamingFeatures? = null,
    val finalizedStreamingFeatures: StreamingFeatures? = null,
    val clientId: String? = null,
    val deviceId: String? = null,
)

@Serializable
data class ActiveSessionInfo(
    val sessionId: String,
    val appId: Int,
    val gpuType: String? = null,
    val status: Int,
    val queuePosition: Int? = null,
    val seatSetupStep: Int? = null,
    val streamingBaseUrl: String? = null,
    val serverIp: String? = null,
    val signalingUrl: String? = null,
    val resolution: String? = null,
    val fps: Int? = null,
    val settingsSignature: String? = null,
)

internal fun SessionInfo.isReadyForStream(): Boolean =
    status in setOf(2, 3) &&
        serverIp.isNotBlank() &&
        signalingServer.isNotBlank() &&
        signalingUrl.isNotBlank()

internal fun ActiveSessionInfo.isReadyForClaim(): Boolean =
    status in setOf(2, 3) && !serverIp.isNullOrBlank()

internal fun ActiveSessionInfo.matchesStreamGeometry(settings: StreamSettings): Boolean {
    val activeResolution = parseResolutionPixelsOrNull(resolution)
    val expectedResolution = streamResolutionPixels(settings)
    val activeFps = fps?.takeIf { it > 0 }
    return activeResolution == expectedResolution && activeFps == settings.fps
}

internal fun ActiveSessionInfo.matchesStreamSettings(settings: StreamSettings): Boolean =
    settingsSignature == streamSettingsSessionSignature(settings) && matchesStreamGeometry(settings)

internal fun activeSessionRecoveryCandidate(
    sessions: List<ActiveSessionInfo>,
    previousSessionId: String,
    launchAppId: Int?,
    settings: StreamSettings,
): ActiveSessionInfo? {
    val readySessions = sessions.filter { it.isReadyForClaim() }
    return readySessions.firstOrNull {
        it.sessionId == previousSessionId && it.matchesStreamGeometry(settings)
    } ?: launchAppId?.let { appId ->
        readySessions.firstOrNull {
            it.appId == appId && it.matchesStreamSettings(settings)
        }
    }
}

internal fun activeSessionLaunchConflict(
    sessions: List<ActiveSessionInfo>,
    launchAppId: Int?,
    settings: StreamSettings,
): ActiveSessionInfo? =
    sessions
        .filter { it.status in setOf(1, 2, 3) }
        .sortedWith(
            compareByDescending<ActiveSessionInfo> { launchAppId != null && it.appId == launchAppId }
                .thenByDescending { it.matchesStreamSettings(settings) }
                .thenByDescending { it.isReadyForClaim() }
                .thenBy { it.queuePosition ?: Int.MAX_VALUE },
        )
        .firstOrNull()

internal fun parseResolutionPixelsOrNull(value: String?): Pair<Int, Int>? {
    val parts = value?.split("x") ?: return null
    val width = parts.getOrNull(0)?.toIntOrNull()
    val height = parts.getOrNull(1)?.toIntOrNull()
    return if (width != null && height != null && width > 0 && height > 0) width to height else null
}

data class CodecCapability(
    val codec: VideoCodec,
    val decoderAvailable: Boolean,
    val encoderAvailable: Boolean,
    val hardwareDecoder: Boolean,
    val hardwareEncoder: Boolean,
    val decoderName: String? = null,
    val encoderName: String? = null,
    val realtimeSafe: Boolean = hardwareDecoder,
    val nativeDecoderAvailable: Boolean? = null,
    val webRtcDecoderAvailable: Boolean? = null,
    val webRtcHardwareDecoderAvailable: Boolean? = null,
    val webRtcDecoderName: String? = null,
    val webRtcCodecProfiles: List<String> = emptyList(),
    val maxSupportedWidth: Int? = null,
    val maxSupportedHeight: Int? = null,
    val maxFpsByResolution: Map<String, Int> = emptyMap(),
)

data class RuntimeCodecReport(
    val capabilities: List<CodecCapability>,
    val nativeRuntimeSummary: String,
    val androidTvProfile: Boolean,
    val lowPowerGpuProfile: Boolean,
)

data class StreamRuntimeStats(
    val bitrateKbps: Int? = null,
    val pingMs: Int? = null,
    val fps: Int? = null,
    val resolution: String? = null,
    val codec: String? = null,
    val decodeMs: Double? = null,
    val encodeMs: Double? = null,
    val packetLossPct: Double? = null,
)

internal fun CodecCapability.streamingDecoderAvailable(): Boolean =
    webRtcDecoderAvailable ?: decoderAvailable

internal fun CodecCapability.streamingHardwareDecoderAvailable(): Boolean =
    webRtcHardwareDecoderAvailable ?: (nativeDecoderAvailable?.let { it && hardwareDecoder } ?: hardwareDecoder)

internal fun CodecCapability.streamingDecoderName(): String? =
    webRtcDecoderName ?: decoderName

internal fun CodecCapability.streamingRealtimeSafe(): Boolean =
    streamingDecoderAvailable() &&
        (codec == VideoCodec.H264 || (nativeDecoderAvailable != false && (streamingHardwareDecoderAvailable() || webRtcDecoderAvailable == true)))

internal fun CodecCapability.streamingDecoderUsableForLaunch(): Boolean {
    if (webRtcDecoderAvailable == false && nativeDecoderAvailable != true) return false
    if (codec == VideoCodec.H264) return webRtcDecoderAvailable ?: decoderAvailable
    if (nativeDecoderAvailable == false) return false
    val nativeHardwareReady = nativeDecoderAvailable == true && hardwareDecoder
    val webRtcHardwareReady = webRtcDecoderAvailable == true && webRtcHardwareDecoderAvailable == true
    if (!nativeHardwareReady && !webRtcHardwareReady) return false
    return !decoderAvailable || (hardwareDecoder && realtimeSafe)
}

private fun RuntimeCodecReport.bestStreamingFallbackCodec(): VideoCodec =
    listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.AV1)
        .firstOrNull { codec -> capabilities.firstOrNull { it.codec == codec }?.streamingDecoderUsableForLaunch() == true }
        ?: VideoCodec.H264

internal fun StreamSettings.adjustedForDevice(report: RuntimeCodecReport?): StreamSettings {
    val availableSettings = withAndroidSettingsAvailability()
    if (availableSettings != this) return availableSettings.adjustedForDevice(report)

    if (report?.androidTvProfile == true && report.lowPowerGpuProfile) {
        val requestedCodecUsable = report.capabilities
            .firstOrNull { it.codec == codec }
            ?.streamingDecoderUsableForLaunch()
            ?: (codec == VideoCodec.H264)
        val effectiveCodec = if (requestedCodecUsable) codec else report.bestStreamingFallbackCodec()
        return copy(
            codec = effectiveCodec,
            colorQuality = ColorQuality.EightBit420,
            maxBitrateMbps = minOf(maxBitrateMbps, LOW_POWER_TV_BITRATE_CAP_MBPS),
            fps = minOf(fps, LOW_POWER_TV_FPS_CAP),
            hdrEnabled = false,
            enableCloudGsync = false,
        ).cappedResolution(LOW_POWER_TV_MAX_WIDTH, LOW_POWER_TV_MAX_HEIGHT, strict = false)
            .withStableAndroidCloudMatchProfile()
            .withoutAndroidTvSharpening(report)
    }

    val capability = report?.capabilities?.firstOrNull { it.codec == codec }
    val codecSupported = capability?.streamingDecoderUsableForLaunch() ?: true
    val effectiveCodec = if (codecSupported) codec else report.bestStreamingFallbackCodec()

    val profileBitrateCap = when {
        !codecSupported -> 35
        report?.lowPowerGpuProfile == true -> 25
        report?.androidTvProfile == true -> 35
        effectiveCodec == VideoCodec.H264 -> 75
        else -> 75
    }

    val adjusted = (if (effectiveCodec == codec) this else copy(codec = effectiveCodec)).withCodecColorCompatibility()
    val effectiveCapability = report?.capabilities?.firstOrNull { it.codec == effectiveCodec }
    val maxWidth = effectiveCapability?.maxSupportedWidth
    val maxHeight = effectiveCapability?.maxSupportedHeight

    val capped = when (effectiveCodec) {
        VideoCodec.H264 -> adjusted.copy(colorQuality = ColorQuality.EightBit420, maxBitrateMbps = minOf(adjusted.maxBitrateMbps, profileBitrateCap))
        VideoCodec.H265,
        VideoCodec.AV1 -> adjusted.copy(
            colorQuality = adjusted.androidWebRtcColorQuality(),
            maxBitrateMbps = minOf(adjusted.maxBitrateMbps, profileBitrateCap),
        )
    }.withStableAndroidCloudMatchProfile()
        .withoutAndroidTvSharpening(report)

    val capabilityCap = if (maxWidth != null && maxHeight != null) {
        capped.cappedResolution(maxWidth, maxHeight, strict = false)
    } else {
        capped
    }

    val maxSupportedFps = effectiveCapability?.maxFpsByResolution?.get(capabilityCap.resolution) ?: 360
    return capabilityCap.copy(fps = minOf(capabilityCap.fps, maxSupportedFps))
}

internal fun StreamSettings.androidSafeVideoFallback(): StreamSettings =
    copy(
        fps = minOf(fps, 60),
        maxBitrateMbps = minOf(maxBitrateMbps, 75),
        codec = VideoCodec.H264,
        colorQuality = ColorQuality.EightBit420,
        hdrEnabled = false,
        enableCloudGsync = false,
        streamSharpeningEnabled = false,
    ).cappedResolution(SAFE_VIDEO_FALLBACK_MAX_WIDTH, SAFE_VIDEO_FALLBACK_MAX_HEIGHT, strict = false)

private fun StreamSettings.androidWebRtcColorQuality(): ColorQuality {
    val compatible = withCodecColorCompatibility()
    if (compatible.hdrEnabled) return when (compatible.colorQuality) {
        ColorQuality.TenBit420,
        ColorQuality.TenBit444,
        -> compatible.colorQuality
        else -> ColorQuality.TenBit420
    }
    return when (compatible.colorQuality) {
        ColorQuality.EightBit420,
        ColorQuality.EightBit444,
        -> compatible.colorQuality
        else -> ColorQuality.EightBit420
    }
}

private fun StreamSettings.withStableAndroidCloudMatchProfile(): StreamSettings {
    val (width, height) = streamResolutionPixels(this)
    val pixels = width * height
    val maxFps = if (pixels > ANDROID_1440P_PIXEL_BUDGET) 120 else 360
    return copy(
        fps = minOf(fps, maxFps),
        hdrEnabled = hdrEnabled && codec != VideoCodec.H264,
        enableCloudGsync = enableCloudGsync && codec != VideoCodec.H264,
    )
}

private fun StreamSettings.withoutAndroidTvSharpening(report: RuntimeCodecReport?): StreamSettings =
    if (report?.androidTvProfile == true && streamSharpeningEnabled) {
        copy(streamSharpeningEnabled = false)
    } else {
        this
    }

private fun StreamSettings.cappedResolution(maxWidth: Int, maxHeight: Int, strict: Boolean = false): StreamSettings {
    val normalized = normalizeStreamResolutionForAspect(resolution, aspectRatio)
    val (width, height) = parseResolutionPixels(normalized)
    
    val fits = if (strict) {
        width <= maxWidth && height <= maxHeight
    } else {
        val maxPixelCount = (maxWidth * maxHeight * 1.4f).roundToInt()
        width <= maxWidth * 2 && height <= maxHeight * 2 && (width * height) <= maxPixelCount
    }
    
    if (fits) return copy(resolution = normalized)

    val sameAspect = STREAM_RESOLUTION_OPTIONS
        .filter { it.aspectRatio == aspectRatio && it.fitsWithin(maxWidth, maxHeight, strict) }
        .maxByOrNull { it.pixelCount() }
    val fallback = STREAM_RESOLUTION_OPTIONS
        .filter { it.fitsWithin(maxWidth, maxHeight, strict) }
        .maxWithOrNull(compareBy<StreamResolutionOption> { it.pixelCount() }.thenBy { if (it.aspectRatio == "16:9") 1 else 0 })
    val capped = sameAspect ?: fallback ?: StreamResolutionOption("1280x720", "16:9", "720")
    return copy(resolution = capped.value, aspectRatio = capped.aspectRatio)
}

private fun StreamResolutionOption.fitsWithin(maxWidth: Int, maxHeight: Int, strict: Boolean = false): Boolean {
    val (width, height) = parseResolutionPixels(value)
    return if (strict) {
        width <= maxWidth && height <= maxHeight
    } else {
        val maxPixelCount = (maxWidth * maxHeight * 1.4f).roundToInt()
        width <= maxWidth * 2 && height <= maxHeight * 2 && (width * height) <= maxPixelCount
    }
}

private fun StreamResolutionOption.pixelCount(): Int {
    val (width, height) = parseResolutionPixels(value)
    return width * height
}

private const val LOW_POWER_TV_MAX_WIDTH = 1920
private const val LOW_POWER_TV_MAX_HEIGHT = 1080
private const val LOW_POWER_TV_BITRATE_CAP_MBPS = 25
private const val LOW_POWER_TV_FPS_CAP = 60
private const val SAFE_VIDEO_FALLBACK_MAX_WIDTH = 1920
private const val SAFE_VIDEO_FALLBACK_MAX_HEIGHT = 1080
private const val ANDROID_1440P_PIXEL_BUDGET = 2560 * 1440
