package dev.rahier.pouleparty.powerups.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.powerups.selection.powerUpColor
import dev.rahier.pouleparty.powerups.selection.powerUpTextColor
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.NeonGlowIntensity
import dev.rahier.pouleparty.ui.theme.neonGlow

@Composable
fun PowerUpNotificationOverlay(
    notification: String?,
    powerUpType: PowerUpType?,
    modifier: Modifier = Modifier
) {
    notification?.let {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(top = 120.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val bgColor = powerUpType?.let { type -> powerUpColor(type) } ?: CROrange
            Text(
                text = it,
                color = powerUpType?.let { type -> powerUpTextColor(type) } ?: Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .neonGlow(bgColor, NeonGlowIntensity.SUBTLE, cornerRadius = 20.dp)
                    .background(bgColor.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
