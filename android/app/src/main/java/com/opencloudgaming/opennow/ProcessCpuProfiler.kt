package com.opencloudgaming.opennow

import android.os.Process
import android.os.SystemClock
import java.util.Locale

private const val PROCESS_CPU_PROFILE_MAX_SAMPLES = 180

internal data class ProcessCpuUsageSample(
    val capturedAtElapsedRealtimeMs: Long,
    val windowMs: Long,
    val processCpuPercent: Double,
    val deviceCpuCapacityPercent: Double,
    val logicalCoreCount: Int,
)

/**
 * Samples CPU time consumed by the whole app process, including native/WebRTC threads.
 * [processCpuPercent] is expressed in logical-core equivalents and may exceed 100%;
 * [deviceCpuCapacityPercent] normalizes the same value across the device's logical cores.
 */
internal class ProcessCpuSampler(
    private val processCpuTimeMs: () -> Long = Process::getElapsedCpuTime,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
    private val logicalCoreCount: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
) {
    private var previousProcessCpuTimeMs: Long? = null
    private var previousElapsedRealtimeMs: Long? = null

    @Synchronized
    fun reset() {
        previousProcessCpuTimeMs = null
        previousElapsedRealtimeMs = null
    }

    @Synchronized
    fun sample(): ProcessCpuUsageSample? {
        val currentProcessCpuTimeMs = processCpuTimeMs()
        val currentElapsedRealtimeMs = elapsedRealtimeMs()
        val previousCpu = previousProcessCpuTimeMs
        val previousElapsed = previousElapsedRealtimeMs
        previousProcessCpuTimeMs = currentProcessCpuTimeMs
        previousElapsedRealtimeMs = currentElapsedRealtimeMs

        if (previousCpu == null || previousElapsed == null) return null
        val cpuDeltaMs = currentProcessCpuTimeMs - previousCpu
        val elapsedDeltaMs = currentElapsedRealtimeMs - previousElapsed
        if (cpuDeltaMs < 0L || elapsedDeltaMs <= 0L) return null

        val maximumProcessPercent = logicalCoreCount * 100.0
        val processPercent = (cpuDeltaMs * 100.0 / elapsedDeltaMs)
            .coerceIn(0.0, maximumProcessPercent)
        return ProcessCpuUsageSample(
            capturedAtElapsedRealtimeMs = currentElapsedRealtimeMs,
            windowMs = elapsedDeltaMs,
            processCpuPercent = processPercent,
            deviceCpuCapacityPercent = (processPercent / logicalCoreCount).coerceIn(0.0, 100.0),
            logicalCoreCount = logicalCoreCount,
        )
    }
}

internal class ProcessCpuProfileBuffer(
    private val maxSamples: Int = PROCESS_CPU_PROFILE_MAX_SAMPLES,
) {
    init {
        require(maxSamples > 0)
    }

    private val samples = ArrayDeque<ProcessCpuUsageSample>()

    @Synchronized
    fun reset() {
        samples.clear()
    }

    @Synchronized
    fun record(sample: ProcessCpuUsageSample) {
        samples.addLast(sample)
        while (samples.size > maxSamples) {
            samples.removeFirst()
        }
    }

    @Synchronized
    fun snapshot(): String {
        if (samples.isEmpty()) return "cpu.profile=empty"
        val current = samples.toList()
        val averageProcessPercent = current.map { it.processCpuPercent }.average()
        val peakProcessPercent = current.maxOf { it.processCpuPercent }
        val averageDevicePercent = current.map { it.deviceCpuCapacityPercent }.average()
        val peakDevicePercent = current.maxOf { it.deviceCpuCapacityPercent }
        return buildString {
            appendLine(
                "cpu.profile samples=${current.size} cores=${current.last().logicalCoreCount} " +
                    "processAvgPct=${averageProcessPercent.cpuPercent()} processPeakPct=${peakProcessPercent.cpuPercent()} " +
                    "deviceCapacityAvgPct=${averageDevicePercent.cpuPercent()} deviceCapacityPeakPct=${peakDevicePercent.cpuPercent()}",
            )
            appendLine("cpu.profile.basis=process CPU time divided by wall time; processPct may exceed 100; deviceCapacityPct is normalized by logical cores")
            current.forEachIndexed { index, sample ->
                appendLine(
                    "cpu.${index + 1} uptimeMs=${sample.capturedAtElapsedRealtimeMs} windowMs=${sample.windowMs} " +
                        "processPct=${sample.processCpuPercent.cpuPercent()} " +
                        "deviceCapacityPct=${sample.deviceCpuCapacityPercent.cpuPercent()}",
                )
            }
        }.trimEnd()
    }
}

internal object ProcessCpuDiagnostics {
    private val profile = ProcessCpuProfileBuffer()

    fun beginStream() {
        profile.reset()
    }

    fun record(sample: ProcessCpuUsageSample) {
        profile.record(sample)
    }

    fun snapshot(): String = profile.snapshot()
}

private fun Double.cpuPercent(): String = "%.1f".format(Locale.US, this)
