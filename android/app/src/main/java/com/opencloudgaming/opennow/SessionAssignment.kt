package com.opencloudgaming.opennow

import java.util.Locale

private val SESSION_ZONE_ID = Regex("^npa?-[a-z0-9]+(?:-[a-z0-9]+)+$")

/**
 * Returns the zone that owns an allocated session, when CloudMatch exposes it in the session
 * control hostname. This is deliberately separate from requestStatus.serverId: that field can
 * continue to identify the zone that handled the request even when Free Tier assigns the rig in
 * another zone.
 */
internal fun assignedSessionZoneFromControlHost(rawHost: String?): String? {
    val host = rawHost
        ?.trim()
        ?.trimEnd('.')
        ?.lowercase(Locale.US)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val isNvidiaSessionHost = host.endsWith(".cloudmatchbeta.nvidiagrid.net") ||
        host.endsWith(".cloudmatch.nvidiagrid.net") ||
        host.endsWith(".geforcenow.nvidiagrid.net")
    if (!isNvidiaSessionHost) return null

    return host.substringBefore('.')
        .takeIf(SESSION_ZONE_ID::matches)
        ?.uppercase(Locale.US)
}

/** The allocated zone when known, with the requested/routing zone retained as a safe fallback. */
internal fun SessionInfo.reportedServerZone(): String = assignedZone ?: zone
