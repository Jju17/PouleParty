package dev.rahier.pouleparty.ui.chickenconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.theme.*

fun powerUpColor(type: PowerUpType): Color = when (type) {
    PowerUpType.INVISIBILITY -> PowerupStealth
    PowerUpType.ZONE_FREEZE -> PowerupFreeze
    PowerUpType.RADAR_PING -> PowerupRadar
    PowerUpType.ZONE_PREVIEW -> PowerupVision
    PowerUpType.DECOY -> PowerupSpeed
    PowerUpType.JAMMER -> PowerupShield
}

fun powerUpEmoji(type: PowerUpType): String = when (type) {
    PowerUpType.ZONE_PREVIEW -> "🔮"
    PowerUpType.RADAR_PING -> "📡"
    PowerUpType.INVISIBILITY -> "👻"
    PowerUpType.ZONE_FREEZE -> "❄️"
    PowerUpType.DECOY -> "🎭"
    PowerUpType.JAMMER -> "📶"
}

private val unavailableInZone = setOf(
    PowerUpType.INVISIBILITY, PowerUpType.DECOY, PowerUpType.JAMMER
)

@Composable
fun PowerUpSelectionScreen(
    enabledTypes: List<String>,
    gameMod: GameMod,
    onToggle: (PowerUpType) -> Unit,
    onDismiss: () -> Unit
) {
    val chickenPowerUps = PowerUpType.entries.filter { !it.isHunterPowerUp }
    val hunterPowerUps = PowerUpType.entries.filter { it.isHunterPowerUp }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Power-Ups", style = bangerStyle(28), color = MaterialTheme.colorScheme.onBackground)
            TextButton(onClick = onDismiss) {
                Text("Done", style = bangerStyle(18))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chicken section header
            item(span = { GridItemSpan(2) }) {
                SectionHeader(
                    title = "Chicken Power-Ups",
                    emoji = "🐔",
                    gradient = GradientChicken
                )
            }
            items(chickenPowerUps) { type ->
                val unavailable = gameMod == GameMod.STAY_IN_THE_ZONE && type in unavailableInZone
                val isEnabled = !unavailable && enabledTypes.contains(type.firestoreValue)
                PowerUpCard(type = type, isEnabled = isEnabled, unavailable = unavailable, onClick = { if (!unavailable) onToggle(type) })
            }

            // Hunter section header
            item(span = { GridItemSpan(2) }) {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    title = "Hunter Power-Ups",
                    emoji = "🎯",
                    gradient = GradientHunter
                )
            }
            items(hunterPowerUps) { type ->
                val unavailable = gameMod == GameMod.STAY_IN_THE_ZONE && type in unavailableInZone
                val isEnabled = !unavailable && enabledTypes.contains(type.firestoreValue)
                PowerUpCard(type = type, isEnabled = isEnabled, unavailable = unavailable, onClick = { if (!unavailable) onToggle(type) })
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, emoji: String, gradient: Brush) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(emoji, fontSize = 24.sp)
        Text(title, style = bangerStyle(20), color = Color.White)
    }
}

@Composable
private fun PowerUpCard(
    type: PowerUpType,
    isEnabled: Boolean,
    unavailable: Boolean = false,
    onClick: () -> Unit
) {
    val color = powerUpColor(type)
    val emoji = powerUpEmoji(type)

    Card(
        onClick = onClick,
        enabled = !unavailable,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isEnabled) Modifier.shadow(6.dp, RoundedCornerShape(12.dp), ambientColor = color.copy(alpha = 0.4f), spotColor = color.copy(alpha = 0.4f)) else Modifier)
            .then(if (unavailable) Modifier.alpha(0.5f) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) color else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(emoji, fontSize = 28.sp)
            Text(
                type.title,
                style = gameboyStyle(10),
                color = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (unavailable) {
                Text(
                    "Not available in this mode",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    type.description,
                    fontSize = 9.sp,
                    color = if (isEnabled) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 3
                )
            }
            type.durationSeconds?.let { duration ->
                Text(
                    "${duration}s",
                    style = gameboyStyle(8),
                    color = if (isEnabled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
