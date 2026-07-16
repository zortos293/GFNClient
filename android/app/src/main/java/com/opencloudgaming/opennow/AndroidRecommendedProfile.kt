package com.opencloudgaming.opennow

import android.app.ActivityManager
import android.content.Context
import kotlin.math.abs

internal data class AndroidDeviceRecommendation(
    val stream: StreamSettings,
    val displayWidth: Int,
    val displayHeight: Int,
    val processorCount: Int,
    val totalMemoryMiB: Long?,
    val androidTvProfile: Boolean,
    val lowPowerProfile: Boolean,
) {
    fun debugSummary(): String =
        "display=${displayWidth}x$displayHeight processors=$processorCount memoryMiB=${totalMemoryMiB ?: "unknown"} " +
            "tv=$androidTvProfile lowPower=$lowPowerProfile recommended=${stream.resolution}@${stream.fps} " +
            "codec=${stream.codec} bitrate=${stream.maxBitrateMbps}"
}

internal fun recommendedAndroidStreamProfile(
    context: Context,
    report: RuntimeCodecReport?,
): AndroidDeviceRecommendation {
    val metrics = context.resources.displayMetrics
    val displayWidth = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
    val displayHeight = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
    val processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val totalMemoryMiB = runCatching {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(info)
        info.totalMem.takeIf { it > 0L }?.div(1024L * 1024L)
    }.getOrNull()
    val tvProfile = report?.androidTvProfile ?: isAndroidTvProfile(context)
    val lowPower = report?.lowPowerGpuProfile == true ||
        totalMemoryMiB?.let { it < 3_000L } == true ||
        processorCount <= 4

    val maxHeight = when {
        lowPower -> 720
        tvProfile -> 1080
        totalMemoryMiB?.let { it >= 6_000L } == true && processorCount >= 8 -> 1440
        else -> 1080
    }
    val deviceAspect = displayWidth.toDouble() / displayHeight.toDouble()
    val aspectRatio = streamAspectRatioOptions().minByOrNull { option ->
        val parts = option.split(':')
        val optionAspect = parts.getOrNull(0)?.toDoubleOrNull()
            ?.div(parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0)
            ?: (16.0 / 9.0)
        abs(deviceAspect - optionAspect)
    } ?: "16:9"
    val choices = streamResolutionChoicesForAspect(aspectRatio)
    val selected = choices
        .filter { it.width <= displayWidth && it.height <= displayHeight && it.height <= maxHeight }
        .maxByOrNull { it.width * it.height }
        ?: choices
            .filter { it.height <= maxHeight }
            .maxByOrNull { it.width * it.height }
        ?: streamResolutionChoicesForAspect("16:9").first()

    val h265Ready = report?.capabilities
        ?.firstOrNull { it.codec == VideoCodec.H265 }
        ?.streamingDecoderUsableForLaunch() == true
    val preferH265 = !lowPower && !tvProfile && maxHeight >= 1440 && h265Ready
    val fps = if (lowPower && processorCount <= 4) 30 else 60
    val bitrate = when {
        lowPower -> if (fps <= 30) 12 else 18
        selected.height >= 1440 -> 45
        tvProfile -> 30
        else -> 35
    }
    val stream = StreamSettings(
        resolution = selected.value,
        aspectRatio = selected.aspectRatio,
        fps = fps,
        maxBitrateMbps = bitrate,
        codec = if (preferH265) VideoCodec.H265 else VideoCodec.H264,
        colorQuality = ColorQuality.EightBit420,
    ).adjustedForDevice(report)

    return AndroidDeviceRecommendation(
        stream = stream,
        displayWidth = displayWidth,
        displayHeight = displayHeight,
        processorCount = processorCount,
        totalMemoryMiB = totalMemoryMiB,
        androidTvProfile = tvProfile,
        lowPowerProfile = lowPower,
    )
}
