package dev.rahier.pouleparty.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.rahier.pouleparty.ui.map.MapUiState

/**
 * Fires haptic feedback on key state transitions shared by both map screens.
 * Mirrors the iOS `MapHapticsModifier` view modifier.
 */
@Composable
fun MapHapticsEffect(state: MapUiState, view: View) {
    LaunchedEffect(state.countdownNumber) {
        if (state.countdownNumber != null) HapticManager.heavyImpact(view)
    }
    LaunchedEffect(state.countdownText) {
        if (state.countdownText != null) HapticManager.heavyImpact(view)
    }
    LaunchedEffect(state.isOutsideZone) {
        if (state.isOutsideZone) HapticManager.warning(view)
    }
    LaunchedEffect(state.powerUpNotification) {
        if (state.powerUpNotification != null) HapticManager.success(view)
    }
}
