package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.PowerUp

@Composable
fun PowerUpInventoryDialog(
    collectedPowerUps: List<PowerUp>,
    activatingPowerUpId: String?,
    activateButtonColor: Color,
    onActivate: (PowerUp) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.power_ups)) },
        text = {
            Column {
                if (collectedPowerUps.isEmpty()) {
                    Text(stringResource(R.string.no_power_ups_collected))
                } else {
                    collectedPowerUps.forEach { powerUp ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(powerUp.typeEnum.title, fontWeight = FontWeight.Bold)
                                val durationText = powerUp.typeEnum.durationSeconds?.let { "${it}s" } ?: stringResource(R.string.instant)
                                Text(durationText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            }
                            Button(
                                onClick = { onActivate(powerUp) },
                                enabled = activatingPowerUpId == null,
                                colors = ButtonDefaults.buttonColors(containerColor = activateButtonColor)
                            ) {
                                Text(stringResource(R.string.activate))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
