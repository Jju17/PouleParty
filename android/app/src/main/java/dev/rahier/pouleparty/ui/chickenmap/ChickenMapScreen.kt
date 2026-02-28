package dev.rahier.pouleparty.ui.chickenmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import dev.rahier.pouleparty.ui.endgamecode.EndGameCodeContent

@Composable
fun ChickenMapScreen(
    onGoToMenu: () -> Unit,
    viewModel: ChickenMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show winner notification as snackbar
    LaunchedEffect(state.winnerNotification) {
        state.winnerNotification?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // Google Map
        val cameraPositionState = rememberCameraPositionState()

        // Center camera on circle when it updates
        LaunchedEffect(state.circleCenter) {
            state.circleCenter?.let { center ->
                cameraPositionState.position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(center, AppConstants.MAP_CAMERA_ZOOM)
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
            // Zone circle
            state.circleCenter?.let { center ->
                Circle(
                    center = center,
                    radius = state.radius.toDouble(),
                    fillColor = Color.Green.copy(alpha = 0.3f),
                    strokeColor = Color.Green.copy(alpha = 0.7f),
                    strokeWidth = 2f
                )
            }

            // Hunter annotations (mutualTracking mode) — only after hunt starts
            if (state.hasHuntStarted) state.hunterAnnotations.forEach { hunter ->
                Marker(
                    state = MarkerState(position = hunter.coordinate),
                    title = hunter.displayName,
                    snippet = "Hunter"
                )
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
                Text(stringResource(R.string.you_are_chicken), fontSize = 16.sp)
                Text(viewModel.chickenSubtitle, fontSize = 14.sp)
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
                    isChicken = true
                )
            }

            // FOUND button
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

        // Game start countdown overlay
        GameStartCountdownOverlay(
            countdownNumber = state.countdownNumber,
            countdownText = state.countdownText
        )
    }
    }

    // Cancel alert
    if (state.showCancelAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCancelAlert() },
            title = { Text(stringResource(R.string.cancel_game)) },
            text = { Text(stringResource(R.string.cancel_game_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCancelGame(onGoToMenu) }) {
                    Text(stringResource(R.string.cancel_game), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCancelAlert() }) {
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

    // Found code dialog
    if (state.showFoundCode) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFoundCode() },
            title = null,
            text = { EndGameCodeContent(foundCode = state.game.foundCode) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissFoundCode() }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // Game info dialog
    if (state.showGameInfo) {
        GameInfoDialog(
            game = state.game,
            codeCopied = state.codeCopied,
            onCodeCopied = { viewModel.onCodeCopied() },
            onDismiss = { viewModel.dismissGameInfo() },
            onCancelGame = { viewModel.onCancelGameTapped() }
        )
    }
}
