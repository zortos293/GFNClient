package com.opencloudgaming.opennow.ui.theme

import androidx.compose.ui.unit.dp

/** One spacing scale, so gutters and padding stop being decided independently at each call site. */
object OpenNowSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Distance from content to the edge of the screen. */
    val ScreenEdge = 16.dp

    /** Horizontal gap between cards in the catalog grid. */
    val GridGutter = 12.dp

    /** Vertical gap between rows in the catalog grid — larger than the gutter to separate captions. */
    val GridRowGap = 16.dp
}
