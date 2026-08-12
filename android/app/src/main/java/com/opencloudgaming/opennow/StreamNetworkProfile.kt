package com.opencloudgaming.opennow

/**
 * Keeps a 2.4 GHz stream below Android's current downstream link estimate.
 *
 * The estimate is intentionally used only on a confirmed 2.4 GHz Wi-Fi connection. Applying it
 * to Ethernet or 5/6 GHz Wi-Fi would turn a conservative platform estimate into an unnecessary
 * quality cap on otherwise suitable links. The 20% headroom matches the session-report warning
 * threshold and leaves room for Wi-Fi contention without changing the user's saved profile.
 */
internal fun StreamSettings.adjustedForCurrentNetwork(
    network: AndroidRuntimeDiagnosticsSnapshot,
): StreamSettings {
    if (
        network.networkKind != AndroidNetworkKind.Wifi ||
        network.wifiBand != AndroidWifiBand.TwoPointFourGhz
    ) {
        return this
    }

    val downstreamKbps = network.networkDownstreamKbps?.takeIf { it > 0 } ?: return this
    val safeBitrateMbps = (downstreamKbps / STREAM_NETWORK_HEADROOM_KBPS_PER_MBPS)
        .coerceAtLeast(MIN_STREAM_BITRATE_MBPS)
    if (maxBitrateMbps <= safeBitrateMbps) return this

    return copy(maxBitrateMbps = safeBitrateMbps)
}

internal const val STREAM_NETWORK_HEADROOM_KBPS_PER_MBPS = 1_200
private const val MIN_STREAM_BITRATE_MBPS = 1
