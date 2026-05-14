package dev.rahier.pouleparty.powerups.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.R
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.ui.gamelogic.availablePowerUpTypes
import dev.rahier.pouleparty.ui.theme.*

fun powerUpColor(type: PowerUpType): Color = when (type) {
    PowerUpType.INVISIBILITY -> PowerupStealth
    PowerUpType.ZONE_FREEZE -> PowerupFreeze
    PowerUpType.RADAR_PING -> PowerupRadar
    PowerUpType.ZONE_PREVIEW -> PowerupVision
    PowerUpType.DECOY -> PowerupDecoy
    PowerUpType.JAMMER -> PowerupJammer
}

/** Darker variant for gradient bottom (matches DA). */
fun powerUpColorDark(type: PowerUpType): Color = when (type) {
    PowerUpType.INVISIBILITY -> PowerupStealthDark
    PowerUpType.ZONE_FREEZE -> PowerupFreezeDark
    PowerUpType.RADAR_PING -> PowerupRadarDark
    PowerUpType.ZONE_PREVIEW -> PowerupVisionDark
    PowerUpType.DECOY -> PowerupDecoyDark
    PowerUpType.JAMMER -> PowerupJammerDark
}

/** Text color that ensures readability on the power-up's background color. */
fun powerUpTextColor(type: PowerUpType): Color = when (type) {
    PowerUpType.ZONE_FREEZE, PowerUpType.DECOY, PowerUpType.JAMMER -> Color.Black
    PowerUpType.INVISIBILITY, PowerUpType.RADAR_PING, PowerUpType.ZONE_PREVIEW -> Color.White
}

fun powerUpIcon(type: PowerUpType): ImageVector = when (type) {
    PowerUpType.ZONE_PREVIEW -> Icons.Filled.Visibility
    PowerUpType.RADAR_PING -> Icons.Filled.CellTower
    PowerUpType.INVISIBILITY -> Icons.Filled.VisibilityOff
    PowerUpType.ZONE_FREEZE -> Icons.Filled.AcUnit
    PowerUpType.DECOY -> Icons.AutoMirrored.Filled.DirectionsWalk
    PowerUpType.JAMMER -> Icons.Filled.SignalCellularConnectedNoInternet0Bar
}

fun powerUpEmoji(type: PowerUpType): String = when (type) {
    PowerUpType.ZONE_PREVIEW -> "🔮"
    PowerUpType.RADAR_PING -> "📡"
    PowerUpType.INVISIBILITY -> "👻"
    PowerUpType.ZONE_FREEZE -> "❄️"
    PowerUpType.DECOY -> "🎭"
    PowerUpType.JAMMER -> "📶"
}

@Composable
fun PowerUpSelectionScreen(
    enabledTypes: List<String>,
    gameMod: GameMod,
    onToggle: (PowerUpType) -> Unit,
    onDismiss: () -> Unit
) {
    // PP-35: strict mode filter. Only power-ups that work in the current
    // game mode appear at all. No greyed-out "Not available in this mode"
    // cards.
    val available = availablePowerUpTypes(gameMod)
    val chickenPowerUps = available.filter { !it.isHunterPowerUp }
    val hunterPowerUps = available.filter { it.isHunterPowerUp }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.power_ups), style = bangerStyle(28), color = MaterialTheme.colorScheme.onBackground)
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done), style = bangerStyle(18))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chickenPowerUps.isNotEmpty()) {
                // Chicken section header
                item(span = { GridItemSpan(2) }) {
                    SectionHeader(
                        title = "Chicken Power-Ups",
                        emoji = "🐔",
                        gradient = GradientChicken,
                        textColor = Color.Black
                    )
                }
                items(chickenPowerUps) { type ->
                    val isEnabled = enabledTypes.contains(type.firestoreValue)
                    PowerUpCard(type = type, isEnabled = isEnabled, onClick = { onToggle(type) })
                }
            }

            if (hunterPowerUps.isNotEmpty()) {
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
                    val isEnabled = enabledTypes.contains(type.firestoreValue)
                    PowerUpCard(type = type, isEnabled = isEnabled, onClick = { onToggle(type) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, emoji: String, gradient: Brush, textColor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(emoji, fontSize = 24.sp)
        Text(title, style = bangerStyle(20), color = textColor)
    }
}

@Composable
private fun PowerUpCard(
    type: PowerUpType,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val color = powerUpColor(type)
    val colorDark = powerUpColorDark(type)
    val emoji = powerUpEmoji(type)
    val textColor = powerUpTextColor(type)
    val shape = RoundedCornerShape(18.dp)

    // Gradient background (DA: linear-gradient 135deg, color → darkerColor)
    val cardGradient = Brush.linearGradient(
        colors = listOf(color, colorDark),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    // Glossy radial overlay (DA: radial-gradient at 30% 30%, white 0.25 → transparent)
    val glossOverlay = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
        center = Offset(0.3f * 400f, 0.3f * 400f),
        radius = 300f
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            // Neon glow (DA: box-shadow 0 0 7px rgba(color,0.5), 0 4px 12px rgba(0,0,0,0.2))
            .then(
                if (isEnabled) Modifier.shadow(
                    12.dp, shape,
                    ambientColor = color.copy(alpha = 0.5f),
                    spotColor = color.copy(alpha = 0.5f)
                ) else Modifier.shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f))
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isEnabled) Modifier.background(cardGradient) else Modifier)
        ) {
            // Glossy overlay
            if (isEnabled) {
                Box(modifier = Modifier.fillMaxSize().background(glossOverlay))
            }

            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 10.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(emoji, fontSize = 36.sp)
                Text(
                    type.title,
                    style = bangerStyle(18),
                    color = if (isEnabled) textColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    type.description,
                    fontSize = 9.sp,
                    color = if (isEnabled) textColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 3
                )
                type.durationSeconds?.let { duration ->
                    Text(
                        "${duration}s",
                        style = gameboyStyle(8),
                        color = if (isEnabled) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
