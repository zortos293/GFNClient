package com.opencloudgaming.opennow

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.LocalContext

/**
 * App-chrome haptics.
 *
 * These deliberately do **not** go through `View.performHapticFeedback`. Detected gaming handhelds
 * use their device-tuned haptic effects under [VibrationAttributes.USAGE_TOUCH]. In particular,
 * Odin 2 Portal firmware reports successful medium-strength predefined effects that are not
 * perceptible on the physical unit, so known handhelds use longer full-amplitude touch pulses.
 * Other devices retain the shorter media/game-rumble route.
 *
 * The app's own vibration switch ([AppSettings.vibrationEnabled]) stays authoritative — see
 * [enabled] — so nothing here overrides a reader who asked for silence.
 */
internal enum class HapticCue {
    /** Focus landed on a new control. The lightest tick in the set; fires constantly. */
    FocusMove,

    /** A control was activated. */
    Activate,

    /** Backing out of a screen or dismissing a sheet. */
    Back,

    /** Focus tried to leave a container and could not, or an action was refused. */
    Boundary,
}

internal data class HapticPulse(val durationMs: Long, val amplitude: Int)

internal fun hapticPulseFor(cue: HapticCue): HapticPulse = when (cue) {
    HapticCue.FocusMove -> HapticPulse(durationMs = 9L, amplitude = 64)
    HapticCue.Activate -> HapticPulse(durationMs = 17L, amplitude = 150)
    HapticCue.Back -> HapticPulse(durationMs = 13L, amplitude = 104)
    HapticCue.Boundary -> HapticPulse(durationMs = 26L, amplitude = 196)
}

internal fun handheldHapticPulseFor(cue: HapticCue): HapticPulse = when (cue) {
    HapticCue.FocusMove -> HapticPulse(durationMs = 32L, amplitude = 255)
    HapticCue.Activate -> HapticPulse(durationMs = 55L, amplitude = 255)
    HapticCue.Back -> HapticPulse(durationMs = 45L, amplitude = 240)
    HapticCue.Boundary -> HapticPulse(durationMs = 80L, amplitude = 255)
}

/**
 * Focus can move faster than a motor can settle — holding a stick left runs the grid at the key
 * repeat rate — so ticks below this gap are dropped rather than queued into a buzz.
 */
internal const val FOCUS_HAPTIC_MIN_INTERVAL_MS = 45L

internal fun shouldEmitFocusHaptic(lastAtMs: Long, nowMs: Long): Boolean =
    lastAtMs == 0L || nowMs - lastAtMs >= FOCUS_HAPTIC_MIN_INTERVAL_MS

/**
 * Built-in-controller Android handhelds that should receive tactile D-pad focus ticks.
 *
 * These devices are not consistently exposed as Android TV and some firmware reports the
 * integrated controls in ways that make input-device-only detection unreliable. Build identity is
 * stable for this purpose and deliberately stays conservative so ordinary phones do not start
 * buzzing merely because a Bluetooth pad was paired once.
 */
internal fun isGamingHandheldDevice(
    manufacturer: String?,
    brand: String?,
    model: String?,
    device: String?,
    product: String?,
): Boolean {
    val identity = listOf(manufacturer, brand, model, device, product)
        .joinToString(" ") { it.orEmpty() }
        .lowercase(java.util.Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    val tokens = identity.split(' ').filterTo(mutableSetOf()) { it.isNotBlank() }
    return tokens.contains("ayn") ||
        identity.contains("odin") ||
        identity.contains("retroid") ||
        identity.contains("anbernic") ||
        identity.contains("ayaneo") ||
        identity.contains("powkiddy") ||
        identity.contains("abxylute") ||
        tokens.contains("gpd") ||
        (tokens.contains("razer") && tokens.contains("edge")) ||
        (tokens.contains("logitech") && (identity.contains("g cloud") || identity.contains("gr0006")))
}

internal fun isGamingHandheldDevice(): Boolean = isGamingHandheldDevice(
    manufacturer = Build.MANUFACTURER,
    brand = Build.BRAND,
    model = Build.MODEL,
    device = Build.DEVICE,
    product = Build.PRODUCT,
)

internal class OpenNowHaptics(context: Context) {
    private val appContext = context.applicationContext

    /** Mirrors [AppSettings.vibrationEnabled]; kept in sync by [rememberOpenNowHaptics]. */
    var enabled: Boolean = true

    /** Focus movement is useful on controller-led handhelds and noisy on touch-first phones. */
    var navigationEnabled: Boolean = false

    /** Uses strong touch-channel pulses instead of short media rumble on known handhelds. */
    var handheldFeedback: Boolean = false

    private var lastFocusAtMs = 0L

    private val vibrator: Vibrator? by lazy {
        @Suppress("DEPRECATION")
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        found?.takeIf { it.hasVibrator() }
    }

    private val amplitudeControl: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasAmplitudeControl() == true
    }

    /** Throttled: safe to call from every `onFocusChanged` in a scrolling grid. */
    fun focusMoved() {
        if (!enabled || !navigationEnabled) return
        val now = SystemClock.uptimeMillis()
        if (!shouldEmitFocusHaptic(lastFocusAtMs, now)) return
        lastFocusAtMs = now
        play(HapticCue.FocusMove)
    }

    fun play(cue: HapticCue) {
        if (!enabled) return
        val target = vibrator ?: return
        val pulse = if (handheldFeedback) handheldHapticPulseFor(cue) else hapticPulseFor(cue)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = if (amplitudeControl || handheldFeedback) {
                    pulse.amplitude.coerceIn(1, 255)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                val effect = VibrationEffect.createOneShot(pulse.durationMs, amplitude)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    target.vibrate(
                        effect,
                        if (handheldFeedback) touchVibrationAttributes() else mediaVibrationAttributes(),
                    )
                } else {
                    target.vibrate(
                        effect,
                        if (handheldFeedback) touchAudioAttributes() else gameAudioAttributes(),
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                target.vibrate(
                    pulse.durationMs,
                    if (handheldFeedback) touchAudioAttributes() else gameAudioAttributes(),
                )
            }
        }
    }
}

/** Media/game-rumble routing retained for devices without the handheld firmware workaround. */
@RequiresApi(Build.VERSION_CODES.R)
internal fun mediaVibrationAttributes(): VibrationAttributes =
    VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build()

@RequiresApi(Build.VERSION_CODES.R)
internal fun touchVibrationAttributes(): VibrationAttributes =
    VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()

/**
 * The pre-API-33 spelling of the same intent: the platform maps `AudioAttributes.USAGE_GAME` onto
 * `VibrationAttributes.USAGE_MEDIA`, so both paths land in the same bucket.
 */
internal fun gameAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

internal fun touchAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

internal val LocalOpenNowHaptics = staticCompositionLocalOf<OpenNowHaptics?> { null }

@Composable
internal fun rememberOpenNowHaptics(
    enabled: Boolean,
    navigationEnabled: Boolean,
    handheldFeedback: Boolean,
): OpenNowHaptics {
    val context = LocalContext.current
    val haptics = remember(context) { OpenNowHaptics(context) }
    SideEffect {
        haptics.enabled = enabled
        haptics.navigationEnabled = navigationEnabled
        haptics.handheldFeedback = handheldFeedback
    }
    return haptics
}

/**
 * Ticks once whenever focus lands here.
 *
 * Place it next to the element's own `onFocusChanged` and before `focusable()`, otherwise the
 * focus modifier downstream never reports to it.
 */
@Composable
internal fun Modifier.focusMoveHaptics(): Modifier {
    val haptics = LocalOpenNowHaptics.current ?: return this
    return this.onFocusChanged { if (it.isFocused) haptics.focusMoved() }
}
