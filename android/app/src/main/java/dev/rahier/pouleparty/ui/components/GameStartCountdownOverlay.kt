package dev.rahier.pouleparty.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.rahier.pouleparty.ui.theme.*

@Composable
fun GameStartCountdownOverlay(
    countdownNumber: Int?,
    countdownText: String?
) {
    AnimatedVisibility(
        visible = countdownNumber != null || countdownText != null,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (countdownNumber != null) {
                Text(
                    text = "$countdownNumber",
                    style = gameboyStyle(80),
                    color = CROrange
                )
            } else if (countdownText != null) {
                Text(
                    text = countdownText,
                    style = bangerStyle(48),
                    color = CROrange
                )
            }
        }
    }
}
