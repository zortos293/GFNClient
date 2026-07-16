package com.opencloudgaming.opennow

private val CloudMatchStatus81Regex = Regex("""\bCloudMatch returned status\s+81\b""", RegexOption.IGNORE_CASE)
private val LaunchErrorWhitespaceRegex = Regex("""\s+""")

internal fun normalizeLaunchErrorMessage(error: Throwable, gameTitle: String? = null): String {
    val text = error.message ?: return "Launch failed"
    return when {
        isFreeTierEntitlementError(text) ->
            "Your GeForce NOW account is on the Free tier. This game requires a Priority or Ultimate membership."
        isLimitedModeStreamingError(text) -> limitedModeStreamingMessage(gameTitle)
        text.contains("patch", ignoreCase = true) || text.contains("maintenance", ignoreCase = true) ->
            "Game is patching or under maintenance. Try again when NVIDIA finishes updating it."
        else -> text
    }
}

private fun isFreeTierEntitlementError(text: String): Boolean =
    text.contains("ENTITLEMENT_FAILURE_STATUS", ignoreCase = true) ||
        text.contains("8A910006", ignoreCase = true)

private fun isLimitedModeStreamingError(text: String): Boolean =
    text.contains("STREAMING_NOT_ALLOWED_IN_LIMITED_MODE", ignoreCase = true) ||
        text.contains("8A91000D", ignoreCase = true) ||
        (CloudMatchStatus81Regex.containsMatchIn(text) && text.contains("limited", ignoreCase = true))

private fun limitedModeStreamingMessage(gameTitle: String?): String {
    val title = gameTitle
        ?.replace(LaunchErrorWhitespaceRegex, " ")
        ?.trim()
        .orEmpty()
    return if (title.isNotBlank()) {
        "$title is only available for Priority or Ultimate members"
    } else {
        "This game is only available for Priority or Ultimate members"
    }
}
