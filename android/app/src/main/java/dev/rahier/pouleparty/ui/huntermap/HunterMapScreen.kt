package dev.rahier.pouleparty.ui.huntermap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.maps.android.compose.*
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.components.CountdownView
import dev.rahier.pouleparty.ui.components.GameInfoDialog
import dev.rahier.pouleparty.ui.components.GameOverAlertDialog
import dev.rahier.pouleparty.ui.components.GameStartCountdownOverlay

@Composable
fun HunterMapScreen(
    onGoToMenu: () -> Unit,
    onVictory: (gameId: String, hunterName: String, hunterId: String) -> Unit = { _, _, _ -> },
    viewModel: HunterMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate to victory screen when code is correct
    LaunchedEffect(state.shouldNavigateToVictory) {
        if (state.shouldNavigateToVictory) {
            onVictory(viewModel.gameId, viewModel.hunterName, viewModel.hunterId)
        }
    }

    // Show winner notification as snackbar
    LaunchedEffect(state.winnerNotification) {
        state.winnerNotification?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val cameraPositionState = rememberCameraPositionState()

            // Center camera on circle when it updates
            LaunchedEffect(state.circleCenter) {
                state.circleCenter?.let { center ->
                    cameraPositionState.position =
                        com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(center, AppConstants.MAP_CAMERA_ZOOM)
                }
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = true,
                    compassEnabled = true,
                    zoomControlsEnabled = false
                )
            ) {
                // Zone circle (only visible after game starts)
                if (state.hasGameStarted) {
                    state.circleCenter?.let { center ->
                        Circle(
                            center = center,
                            radius = state.radius.toDouble(),
                            fillColor = Color.Green.copy(alpha = 0.3f),
                            strokeColor = Color.Green.copy(alpha = 0.7f),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha = 0.9f))
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(40.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.you_are_hunter), fontSize = 16.sp)
                    Text(viewModel.hunterSubtitle, fontSize = 12.sp)
                }
                IconButton(onClick = { viewModel.onInfoTapped() }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.game_info),
                        tint = Color.Gray
                    )
                }
            }

            // Bottom bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.White.copy(alpha = 0.9f))
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.radius_format, state.radius))
                    CountdownView(
                        nowDate = state.nowDate,
                        nextUpdateDate = state.nextRadiusUpdate,
                        chickenStartDate = state.game.startDate,
                        hunterStartDate = state.game.hunterStartDate,
                        isChicken = false
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // FOUND button (only visible after game starts)
                    if (state.hasGameStarted) {
                        Button(
                            onClick = { viewModel.onFoundButtonTapped() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier.size(width = 50.dp, height = 40.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(stringResource(R.string.found), fontSize = 11.sp, color = Color.White)
                        }
                    }

                    // Quit button
                    TextButton(onClick = { viewModel.onLeaveGameTapped() }) {
                        Text(stringResource(R.string.quit), fontSize = 14.sp, color = Color.Gray)
                    }
                }
            }

            // Countdown overlay
            GameStartCountdownOverlay(
                countdownNumber = state.countdownNumber,
                countdownText = state.countdownText
            )
        }
    }

    // Found code entry dialog
    if (state.isEnteringFoundCode) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFoundCodeEntry() },
            title = { Text(stringResource(R.string.enter_found_code)) },
            text = {
                Column {
                    Text(stringResource(R.string.enter_code_message))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.enteredCode,
                        onValueChange = { viewModel.onEnteredCodeChanged(it) },
                        label = { Text(stringResource(R.string.four_digit_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.submitFoundCode() }) { Text(stringResource(R.string.submit)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissFoundCodeEntry() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Wrong code alert
    if (state.showWrongCodeAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWrongCodeAlert() },
            title = { Text(stringResource(R.string.wrong_code)) },
            text = { Text(stringResource(R.string.wrong_code_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissWrongCodeAlert() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // Quit game alert
    if (state.showLeaveAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLeaveAlert() },
            title = { Text(stringResource(R.string.quit_game)) },
            text = { Text(stringResource(R.string.quit_game_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmLeaveGame(onGoToMenu) }) {
                    Text(stringResource(R.string.quit))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLeaveAlert() }) {
                    Text(stringResource(R.string.never_mind))
                }
            }
        )
    }

    // Game over alert
    if (state.showGameOverAlert) {
        GameOverAlertDialog(
            message = state.gameOverMessage,
            onConfirm = { viewModel.confirmGameOver(onGoToMenu) }
        )
    }

    // Game info dialog (no cancel button for hunters)
    if (state.showGameInfo) {
        GameInfoDialog(
            game = state.game,
            codeCopied = state.codeCopied,
            onCodeCopied = { viewModel.onCodeCopied() },
            onDismiss = { viewModel.dismissGameInfo() }
        )
    }
}
