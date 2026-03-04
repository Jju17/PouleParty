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
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
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
    onNavigateToVictory: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Center content
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(200.dp)
            )

            TextButton(
                onClick = { viewModel.onStartButtonTapped() },
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp)
                    .alpha(alpha)
                    .border(4.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(10.dp))
            ) {
                Text(
                    stringResource(R.string.start),
                    fontFamily = GameBoyFont,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                stringResource(R.string.press_start),
                fontFamily = GameBoyFont,
                fontSize = 12.sp,
                color = Color.Black
            )
        }

        // Top-right: Settings button
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 32.dp)
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_preferences),
                contentDescription = stringResource(R.string.settings),
                tint = Color.Black
            )
        }

        // Bottom section: rejoin banner + I am la poule
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rejoin banner
            if (state.activeGame != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .background(CROrange, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.rejoin_game_in_progress),
                            fontFamily = GameBoyFont,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            state.activeGame!!.gameCode,
                            fontFamily = GameBoyFont,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        TextButton(
                            onClick = {
                                viewModel.rejoinGame(
                                    onRejoinAsChicken = { gameId -> onNavigateToChickenMap(gameId) },
                                    onRejoinAsHunter = { gameId, hunterName -> onNavigateToHunterMap(gameId, hunterName) }
                                )
                            },
                            modifier = Modifier
                                .border(3.dp, Color.White, RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                stringResource(R.string.rejoin),
                                fontFamily = GameBoyFont,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Close button
                    IconButton(
                        onClick = { viewModel.dismissActiveGame() },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            "✕",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom row: Rules (left) + I am la poule (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { viewModel.onRulesTapped() },
                    modifier = Modifier
                        .padding(16.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        stringResource(R.string.rules),
                        fontFamily = GameBoyFont,
                        fontSize = 8.sp,
                        color = Color.Black
                    )
                }
                TextButton(
                    onClick = { viewModel.onIAmLaPouleTapped() },
                    modifier = Modifier
                        .padding(16.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        stringResource(R.string.i_am_la_poule),
                        fontFamily = GameBoyFont,
                        fontSize = 8.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }

    // ── Dialogs ──

    // Chicken confirmation dialog
    if (state.isShowingChickenConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onChickenConfirmDismissed() },
            title = { Text(stringResource(R.string.create_game_title)) },
            text = { Text(stringResource(R.string.create_game_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val gameId = viewModel.confirmChicken()
                    onNavigateToChickenConfig(gameId)
                }) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onChickenConfirmDismissed() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Join game dialog
    if (state.isShowingJoinDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onJoinDialogDismissed() },
            title = { Text(stringResource(R.string.join_game)) },
            text = {
                Column {
                    Text(stringResource(R.string.join_game_message))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.gameCode,
                        onValueChange = { viewModel.onGameCodeChanged(it) },
                        label = { Text(stringResource(R.string.game_code)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.joinGame(
                        onGameFound = { gameId, hunterName ->
                            onNavigateToHunterMap(gameId, hunterName)
                        },
                        onGameDone = { gameId ->
                            onNavigateToVictory(gameId)
                        }
                    )
                }) { Text(stringResource(R.string.join)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onJoinDialogDismissed() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Game not found alert
    if (state.isShowingGameNotFound) {
        AlertDialog(
            onDismissRequest = { viewModel.onGameNotFoundDismissed() },
            title = { Text(stringResource(R.string.game_not_found)) },
            text = { Text(stringResource(R.string.game_not_found_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onGameNotFoundDismissed() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // Location required alert
    if (state.isShowingLocationRequired) {
        AlertDialog(
            onDismissRequest = { viewModel.onLocationRequiredDismissed() },
            title = { Text(stringResource(R.string.location_required)) },
            text = { Text(stringResource(R.string.location_required_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onLocationRequiredDismissed() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // Game in progress alert
    if (state.isShowingGameInProgress) {
        AlertDialog(
            onDismissRequest = { viewModel.onGameInProgressDismissed() },
            title = { Text(stringResource(R.string.game_in_progress)) },
            text = { Text(stringResource(R.string.game_in_progress_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onGameInProgressDismissed() }) { Text(stringResource(R.string.ok)) }
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
                Text(stringResource(R.string.rules), style = bangerStyle(28), color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        text = { GameRulesScreen() },
        confirmButton = {},
        containerColor = MaterialTheme.colorScheme.background
    )
}
