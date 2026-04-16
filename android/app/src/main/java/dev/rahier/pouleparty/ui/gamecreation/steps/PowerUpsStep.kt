package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.gamecreation.StepContainer
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle

@Composable
fun PowerUpsStep(
    powerUpsEnabled: Boolean,
    enabledPowerUpTypes: List<String>,
    gameMod: GameMod,
    onTogglePowerUps: (Boolean) -> Unit,
    onPowerUpSelectionTapped: () -> Unit
) {
    StepContainer(
        title = "Power-Ups ?",
        subtitle = "Active les bonus en jeu"
    ) {
        val shape = RoundedCornerShape(16.dp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.enable_power_ups),
                    style = bangerStyle(20),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Switch(
                    checked = powerUpsEnabled,
                    onCheckedChange = onTogglePowerUps,
                    colors = SwitchDefaults.colors(checkedTrackColor = CROrange)
                )
            }
        }

        if (powerUpsEnabled) {
            val unavailable = if (gameMod == GameMod.STAY_IN_THE_ZONE) {
                setOf(
                    PowerUpType.INVISIBILITY.firestoreValue,
                    PowerUpType.DECOY.firestoreValue,
                    PowerUpType.JAMMER.firestoreValue
                )
            } else emptySet()
            val enabledCount = enabledPowerUpTypes.count { it !in unavailable }
            val totalCount = PowerUpType.entries.count { it.firestoreValue !in unavailable }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPowerUpSelectionTapped() },
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.choose_power_ups),
                        style = gameboyStyle(10),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.power_ups_count_format, enabledCount, totalCount),
                            style = gameboyStyle(10),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
