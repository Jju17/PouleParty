package dev.rahier.pouleparty.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing scale shared by every screen. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Vertical slots under the map top bar, so overlays never overlap it or each other. */
object MapOverlayOffsets {
    val belowTopBar = 96.dp
    val firstBanner = 140.dp
    val secondBanner = 192.dp
}

/** Smallest touch target (Material and WCAG guidance). */
val MinTouchTarget = 48.dp
