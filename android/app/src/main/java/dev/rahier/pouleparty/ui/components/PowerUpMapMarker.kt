package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.powerupselection.powerUpColor
import dev.rahier.pouleparty.ui.powerupselection.powerUpIcon

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
            .size(40.dp)
            .shadow(6.dp, CircleShape, ambientColor = color, spotColor = color)
            .background(color, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = powerUpIcon(type),
            contentDescription = type.title,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}
