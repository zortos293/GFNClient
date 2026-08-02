package com.opencloudgaming.opennow

/** Battery fill levels represented by the Material status-bar icons. */
internal enum class StreamBatteryLevel {
    Unknown,
    Empty,
    One,
    Two,
    Three,
    Four,
    Five,
    Six,
    Full,
}

/** Keeps the battery glyph in step with the percentage instead of always drawing a full battery. */
internal fun streamBatteryLevel(percent: Int?): StreamBatteryLevel {
    val normalized = percent?.coerceIn(0, 100) ?: return StreamBatteryLevel.Unknown
    return when (normalized) {
        in 0..5 -> StreamBatteryLevel.Empty
        in 6..20 -> StreamBatteryLevel.One
        in 21..35 -> StreamBatteryLevel.Two
        in 36..50 -> StreamBatteryLevel.Three
        in 51..65 -> StreamBatteryLevel.Four
        in 66..80 -> StreamBatteryLevel.Five
        in 81..95 -> StreamBatteryLevel.Six
        else -> StreamBatteryLevel.Full
    }
}
