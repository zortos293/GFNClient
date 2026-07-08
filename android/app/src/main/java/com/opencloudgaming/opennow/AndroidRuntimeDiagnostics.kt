package com.opencloudgaming.opennow

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlin.math.roundToInt

internal data class AndroidRuntimeDiagnosticsSnapshot(
    val batteryPercent: Int? = null,
    val batteryCharging: Boolean = false,
    val batteryTemperatureC: Float? = null,
    val thermalStatus: AndroidThermalStatus = AndroidThermalStatus.Unknown,
    val networkKind: AndroidNetworkKind = AndroidNetworkKind.Unknown,
    val networkSignalBars: Int? = null,
    val networkDownstreamKbps: Int? = null,
) {
    fun debugSummary(): String {
        val temperature = batteryTemperatureC?.let { "%.1f".format(java.util.Locale.US, it) } ?: "unknown"
        return "battery=${batteryPercent?.toString() ?: "unknown"} charging=$batteryCharging batteryTempC=$temperature thermal=${thermalStatus.logValue} network=${networkKind.logValue} bars=${networkSignalBars?.toString() ?: "unknown"} downKbps=${networkDownstreamKbps ?: 0}"
    }
}

internal enum class AndroidNetworkKind(val label: String, val logValue: String) {
    Wifi("WiFi", "wifi"),
    Cellular("Cell", "cellular"),
    Ethernet("LAN", "ethernet"),
    Other("Net", "other"),
    None("Off", "none"),
    Unknown("Net", "unknown"),
}

internal enum class AndroidThermalStatus(val logValue: String) {
    Unknown("unknown"),
    None("none"),
    Light("light"),
    Moderate("moderate"),
    Severe("severe"),
    Critical("critical"),
    Emergency("emergency"),
    Shutdown("shutdown"),
}

internal object AndroidRuntimeDiagnostics {
    fun snapshot(context: Context): AndroidRuntimeDiagnosticsSnapshot {
        val appContext = context.applicationContext
        val battery = readBattery(appContext)
        val network = readNetwork(appContext)
        return AndroidRuntimeDiagnosticsSnapshot(
            batteryPercent = battery.percent,
            batteryCharging = battery.charging,
            batteryTemperatureC = battery.temperatureC,
            thermalStatus = readThermalStatus(appContext),
            networkKind = network.kind,
            networkSignalBars = network.signalBars,
            networkDownstreamKbps = network.downstreamKbps,
        )
    }

    private fun readBattery(context: Context): BatteryDiagnostics {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
        val batteryStatus = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temperatureTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        return BatteryDiagnostics(
            percent = percent,
            charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0,
            temperatureC = temperatureTenths.takeIf { it != Int.MIN_VALUE }?.let { it / 10f },
        )
    }

    private fun readThermalStatus(context: Context): AndroidThermalStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return AndroidThermalStatus.Unknown
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return AndroidThermalStatus.Unknown
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> AndroidThermalStatus.None
            PowerManager.THERMAL_STATUS_LIGHT -> AndroidThermalStatus.Light
            PowerManager.THERMAL_STATUS_MODERATE -> AndroidThermalStatus.Moderate
            PowerManager.THERMAL_STATUS_SEVERE -> AndroidThermalStatus.Severe
            PowerManager.THERMAL_STATUS_CRITICAL -> AndroidThermalStatus.Critical
            PowerManager.THERMAL_STATUS_EMERGENCY -> AndroidThermalStatus.Emergency
            PowerManager.THERMAL_STATUS_SHUTDOWN -> AndroidThermalStatus.Shutdown
            else -> AndroidThermalStatus.Unknown
        }
    }

    private fun readNetwork(context: Context): NetworkDiagnostics {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        val kind = when {
            capabilities == null -> AndroidNetworkKind.None
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> AndroidNetworkKind.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> AndroidNetworkKind.Cellular
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> AndroidNetworkKind.Ethernet
            else -> AndroidNetworkKind.Other
        }
        return NetworkDiagnostics(
            kind = kind,
            signalBars = networkBars(capabilities),
            downstreamKbps = capabilities?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
        )
    }

    private fun networkBars(capabilities: NetworkCapabilities?): Int? {
        if (capabilities == null) return 0
        val signalBars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            networkBarsFromSignal(capabilities.signalStrength)
        } else {
            null
        }
        return signalBars ?: networkBarsFromBandwidth(capabilities.linkDownstreamBandwidthKbps)
    }

    private fun networkBarsFromSignal(signalStrength: Int): Int? {
        if (signalStrength == Int.MIN_VALUE) return null
        return when {
            signalStrength in 0..4 -> signalStrength
            signalStrength >= -55 -> 4
            signalStrength >= -67 -> 3
            signalStrength >= -80 -> 2
            else -> 1
        }
    }

    private fun networkBarsFromBandwidth(downstreamKbps: Int): Int? = when {
        downstreamKbps >= 25_000 -> 4
        downstreamKbps >= 10_000 -> 3
        downstreamKbps >= 3_000 -> 2
        downstreamKbps > 0 -> 1
        else -> null
    }

    private data class BatteryDiagnostics(
        val percent: Int?,
        val charging: Boolean,
        val temperatureC: Float?,
    )

    private data class NetworkDiagnostics(
        val kind: AndroidNetworkKind,
        val signalBars: Int?,
        val downstreamKbps: Int?,
    )
}
