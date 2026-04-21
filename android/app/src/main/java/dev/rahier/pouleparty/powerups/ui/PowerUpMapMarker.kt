package dev.rahier.pouleparty.powerups.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.powerups.selection.powerUpColor
import dev.rahier.pouleparty.powerups.selection.powerUpIcon
import dev.rahier.pouleparty.ui.theme.NeonGlowIntensity
import dev.rahier.pouleparty.ui.theme.neonGlow

@Composable
fun PowerUpMapMarker(
    type: PowerUpType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = powerUpColor(type)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .neonGlow(color, NeonGlowIntensity.SUBTLE, cornerRadius = 12.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = powerUpIcon(type),
            contentDescription = type.title,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
    }
}
