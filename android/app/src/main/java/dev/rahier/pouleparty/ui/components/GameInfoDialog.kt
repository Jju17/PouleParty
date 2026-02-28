package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.Game
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Shared game info dialog used by both ChickenMap and HunterMap screens.
 * Displays game code, mode, schedule, and optional cancel button.
 */
@Composable
fun GameInfoDialog(
    game: Game,
    codeCopied: Boolean,
    onCodeCopied: () -> Unit,
    onDismiss: () -> Unit,
    onCancelGame: (() -> Unit)? = null
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Game code (copiable)
                GameCodeCard(
                    gameCode = game.gameCode,
                    codeCopied = codeCopied,
                    onCodeCopied = onCodeCopied
                )

                // Game mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.mode))
                    Text(game.gameModEnum.title, color = Color.Gray)
                }

                // Start time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.start_label))
                    Text(dateFormat.format(game.startDate), color = Color.Gray)
                }

                // End time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.end_label))
                    Text(dateFormat.format(game.endDate), color = Color.Gray)
                }

                // Optional cancel game button (chicken only)
                if (onCancelGame != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            onDismiss()
                            onCancelGame()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cancel_game), color = Color.Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

/**
 * Shared game over alert dialog.
 */
@Composable
fun GameOverAlertDialog(
    message: String,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.game_over)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}
