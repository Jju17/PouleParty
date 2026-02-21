package dev.rahier.pouleparty.ui.selection

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.rules.GameRulesScreen
import dev.rahier.pouleparty.ui.theme.*

@Composable
fun SelectionScreen(
    onNavigateToChickenConfig: (String) -> Unit,
    onNavigateToChickenMap: (String) -> Unit,
    onNavigateToHunterMap: (String, String) -> Unit,
    viewModel: SelectionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Blinking animation for START text
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CRBeige)
    ) {
        // Center content
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(200.dp)
            )

            TextButton(
                onClick = { viewModel.onStartButtonTapped() },
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp)
                    .alpha(alpha)
                    .border(4.dp, Color.Black, RoundedCornerShape(10.dp))
            ) {
                Text(
                    "START",
                    fontFamily = GameBoyFont,
                    fontSize = 22.sp,
                    color = Color.Black
                )
            }

            Text(
                "Press start to play",
                fontFamily = GameBoyFont,
                fontSize = 12.sp,
                color = Color.Black
            )
        }

        // Top-right: Rules button
        TextButton(
            onClick = { viewModel.onRulesTapped() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 32.dp)
                .border(1.5.dp, Color.Black, RoundedCornerShape(8.dp))
        ) {
            Text(
                "Rules",
                fontFamily = GameBoyFont,
                fontSize = 8.sp,
                color = Color.Black
            )
        }

        // Bottom-right: I am la poule button
        TextButton(
            onClick = { viewModel.onIAmLaPouleTapped() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = 16.dp)
                .border(1.5.dp, Color.Black, RoundedCornerShape(8.dp))
        ) {
            Text(
                "I am la poule",
                fontFamily = GameBoyFont,
                fontSize = 8.sp,
                color = Color.Black
            )
        }
    }

    // ── Dialogs ──

    // Password dialog (chicken admin)
    if (state.isShowingPasswordDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onPasswordDialogDismissed() },
            title = { Text("Password") },
            text = {
                Column {
                    Text("Please enter admin password.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.onPasswordChanged(it) },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val gameId = viewModel.validatePassword()
                    if (gameId != null) {
                        onNavigateToChickenConfig(gameId)
                    }
                }) { Text("Ok") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onPasswordDialogDismissed() }) { Text("Cancel") }
            }
        )
    }

    // Join game dialog
    if (state.isShowingJoinDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onJoinDialogDismissed() },
            title = { Text("Join Game") },
            text = {
                Column {
                    Text("Enter the game code and your nickname.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.gameCode,
                        onValueChange = { viewModel.onGameCodeChanged(it) },
                        label = { Text("Game code") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.hunterName,
                        onValueChange = { viewModel.onHunterNameChanged(it) },
                        label = { Text("Your nickname") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.joinGame { gameId, hunterName ->
                        onNavigateToHunterMap(gameId, hunterName)
                    }
                }) { Text("Join") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onJoinDialogDismissed() }) { Text("Cancel") }
            }
        )
    }

    // Game not found alert
    if (state.isShowingGameNotFound) {
        AlertDialog(
            onDismissRequest = { viewModel.onGameNotFoundDismissed() },
            title = { Text("Game not found") },
            text = { Text("No active game found with this code. Check the code and try again.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onGameNotFoundDismissed() }) { Text("OK") }
            }
        )
    }

    // Game Rules bottom sheet
    if (state.isShowingGameRules) {
        GameRulesDialog(onDismiss = { viewModel.onRulesDismissed() })
    }
}

@Composable
private fun GameRulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Rules", style = bangerStyle(28), color = Color.Black)
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = "Close",
                        tint = Color.Black
                    )
                }
            }
        },
        text = { GameRulesScreen() },
        confirmButton = {},
        containerColor = CRBeige
    )
}
