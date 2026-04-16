package dev.rahier.pouleparty.ui.components

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
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.powerupselection.powerUpColor
import dev.rahier.pouleparty.ui.powerupselection.powerUpTextColor
import dev.rahier.pouleparty.ui.theme.CROrange

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
            Text(
                text = it,
                color = powerUpType?.let { type -> powerUpTextColor(type) } ?: Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background((powerUpType?.let { type -> powerUpColor(type) } ?: CROrange).copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
