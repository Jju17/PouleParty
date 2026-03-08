package dev.rahier.pouleparty.ui.huntermap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.font.FontWeight
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.locationcomponent.location
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.components.outerBoundsPoints
import dev.rahier.pouleparty.ui.components.zoomForRadius
import dev.rahier.pouleparty.ui.components.CountdownView
import dev.rahier.pouleparty.ui.components.GameInfoDialog
import dev.rahier.pouleparty.ui.components.GameOverAlertDialog
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.components.ActivePowerUpBadge
import dev.rahier.pouleparty.ui.components.GameStartCountdownOverlay
import dev.rahier.pouleparty.ui.components.PowerUpDetailDialog
import dev.rahier.pouleparty.ui.components.PreGameOverlay

@OptIn(MapboxExperimental::class)
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

    var selectedPowerUpType by remember { mutableStateOf<PowerUpType?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val mapViewportState = rememberMapViewportState()
            var currentBearing by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) {
                snapshotFlow { mapViewportState.cameraState }
                    .collect { cameraState ->
                        cameraState?.bearing?.let { currentBearing = it.toFloat() }
                    }
            }

            // Center camera on circle and zoom to fit radius
            LaunchedEffect(state.circleCenter, state.radius) {
                state.circleCenter?.let { center ->
                    mapViewportState.flyTo(
                        cameraOptions = CameraOptions.Builder()
                            .center(center)
                            .zoom(zoomForRadius(state.radius.toDouble(), center.latitude()))
                            .build()
                    )
                }
            }

            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState
            ) {
                // Enable location puck
                MapEffect(Unit) { mapView ->
                    mapView.location.updateSettings {
                        enabled = true
                        pulsingEnabled = true
                    }
                }

                // Inverted zone overlay (only visible after game starts)
                if (state.hasGameStarted) {
                    state.circleCenter?.let { center ->
                        val overlayColor = if (state.isOutsideZone) {
                            Color(1f, 0f, 0f, 0.4f) // Red with ~0.4 alpha
                        } else {
                            Color(0f, 0f, 0f, 0.3f) // Black with ~0.3 alpha
                        }
                        val circlePoints = circlePolygonPoints(center, state.radius.toDouble())
                        PolygonAnnotation(
                            points = listOf(outerBoundsPoints(center), circlePoints)
                        ) {
                            fillColor = overlayColor
                            fillOpacity = 1.0
                        }

                        // Zone border circle
                        PolylineAnnotation(
                            points = circlePoints + listOf(circlePoints.first())
                        ) {
                            lineColor = Color(0f, 1f, 0f, 0.7f)
                            lineWidth = 2.0
                        }
                    }
                }

                // Power-up markers (hunter power-ups only)
                if (state.hasGameStarted) state.availablePowerUps.forEach { powerUp ->
                    PointAnnotation(
                        point = powerUp.locationPoint,
                        onClick = {
                            selectedPowerUpType = powerUp.typeEnum
                            true
                        }
                    ) {
                        iconImage = IconImage("marker-15")
                        textField = powerUp.typeEnum.title
                        iconAllowOverlap = true
                        textAllowOverlap = true
                    }
                }

                // Zone preview circle (from Zone Preview power-up)
                state.previewCircle?.let { (center, radius) ->
                    val previewPoints = circlePolygonPoints(center, radius)
                    PolylineAnnotation(
                        points = previewPoints + listOf(previewPoints.first())
                    ) {
                        lineColor = Color(0f, 1f, 1f, 0.6f)
                        lineWidth = 2.0
                    }
                }
            }

            // Compass button + active power-up badges
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 96.dp),
                horizontalAlignment = Alignment.End
            ) {
                IconButton(
                    onClick = {
                        state.circleCenter?.let { center ->
                            mapViewportState.flyTo(
                                cameraOptions = CameraOptions.Builder()
                                    .center(center)
                                    .zoom(zoomForRadius(state.radius.toDouble(), center.latitude()))
                                    .bearing(0.0)
                                    .build()
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Navigation,
                        contentDescription = "North",
                        modifier = Modifier.rotate(-currentBearing)
                    )
                }
                if (state.game.powerUpsEnabled) {
                    ActivePowerUpBadge(game = state.game)
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
                    // Power-up inventory button
                    if (state.collectedPowerUps.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.onPowerUpInventoryTapped() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier.size(width = 44.dp, height = 40.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("\u26A1${state.collectedPowerUps.size}", fontSize = 11.sp, color = Color.White)
                        }
                    }

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

            // Pre-game overlay
            if (!state.hasGameStarted) {
                PreGameOverlay(
                    isChicken = false,
                    gameModTitle = state.game.gameModEnum.title,
                    gameCode = null,
                    targetDate = state.game.hunterStartDate,
                    nowDate = state.nowDate,
                    connectedHunters = state.game.hunterIds.size
                )
            }

            // Zone warning banner (visual warning only — no elimination)
            if (state.isOutsideZone) {
                Text(
                    text = stringResource(R.string.return_to_zone),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 140.dp)
                        .background(Color.Red.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
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

    // Power-up notification
    state.powerUpNotification?.let { notification ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 120.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = notification,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(Color(0xFFFF9800).copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    // Power-up detail popup (tap on map icon)
    selectedPowerUpType?.let { type ->
        PowerUpDetailDialog(type = type, onDismiss = { selectedPowerUpType = null })
    }

    // Power-up inventory dialog
    if (state.showPowerUpInventory) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPowerUpInventory() },
            title = { Text("Power-Ups") },
            text = {
                Column {
                    if (state.collectedPowerUps.isEmpty()) {
                        Text("No power-ups collected yet")
                    } else {
                        state.collectedPowerUps.forEach { powerUp ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(powerUp.typeEnum.title, fontWeight = FontWeight.Bold)
                                    val durationText = powerUp.typeEnum.durationSeconds?.let { "${it}s" } ?: "Instant"
                                    Text(durationText, fontSize = 12.sp, color = Color.Gray)
                                }
                                Button(
                                    onClick = { viewModel.activatePowerUp(powerUp) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                                ) {
                                    Text("Activate")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPowerUpInventory() }) {
                    Text("Close")
                }
            }
        )
    }
}
