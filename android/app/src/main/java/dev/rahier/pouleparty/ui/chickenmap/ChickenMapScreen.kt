@file:Suppress("DEPRECATION")

package dev.rahier.pouleparty.ui.chickenmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.font.FontWeight
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.locationcomponent.location
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.components.outerBoundsPoints
import dev.rahier.pouleparty.ui.components.zoomForRadius
import dev.rahier.pouleparty.ui.components.CountdownView
import dev.rahier.pouleparty.ui.components.GameInfoDialog
import dev.rahier.pouleparty.ui.components.GameOverAlertDialog
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.components.ActivePowerUpBadge
import dev.rahier.pouleparty.ui.components.MapTopBar
import dev.rahier.pouleparty.ui.components.PowerUpDetailDialog
import dev.rahier.pouleparty.ui.components.PowerUpInventoryDialog
import dev.rahier.pouleparty.ui.components.PowerUpNotificationOverlay
import dev.rahier.pouleparty.ui.components.GameStartCountdownOverlay
import dev.rahier.pouleparty.ui.components.PreGameOverlay
import dev.rahier.pouleparty.ui.chickenconfig.powerUpEmoji
import dev.rahier.pouleparty.ui.endgamecode.EndGameCodeContent
import dev.rahier.pouleparty.ui.theme.*

@OptIn(MapboxExperimental::class)
@Composable
fun ChickenMapScreen(
    onGoToMenu: () -> Unit,
    onVictory: (gameId: String) -> Unit = {},
    viewModel: ChickenMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate to victory when all hunters found
    LaunchedEffect(state.shouldNavigateToVictory) {
        if (state.shouldNavigateToVictory) {
            onVictory(state.game.id)
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
    Box(modifier = Modifier.fillMaxSize()) {
        // Mapbox Map
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

            // Inverted zone overlay
            state.circleCenter?.let { center ->
                val overlayColor = if (state.isOutsideZone) {
                    ZoneDanger.copy(alpha = 0.4f)
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

                // Zone border circle — neon glow effect (layered polylines)
                PolylineAnnotation(
                    points = circlePoints + listOf(circlePoints.first())
                ) {
                    lineColor = ZoneGreen.copy(alpha = 0.08f)
                    lineWidth = 16.0
                }
                PolylineAnnotation(
                    points = circlePoints + listOf(circlePoints.first())
                ) {
                    lineColor = ZoneGreen.copy(alpha = 0.15f)
                    lineWidth = 8.0
                }
                PolylineAnnotation(
                    points = circlePoints + listOf(circlePoints.first())
                ) {
                    lineColor = ZoneGreen.copy(alpha = 0.35f)
                    lineWidth = 4.0
                }
                PolylineAnnotation(
                    points = circlePoints + listOf(circlePoints.first())
                ) {
                    lineColor = ZoneGreen.copy(alpha = 0.9f)
                    lineWidth = 2.5
                }
            }

            // Power-up markers (chicken power-ups only)
            if (state.hasGameStarted) state.availablePowerUps.forEach { powerUp ->
                PointAnnotation(
                    point = powerUp.locationPoint,
                    onClick = {
                        selectedPowerUpType = powerUp.typeEnum
                        true
                    }
                ) {
                    textField = powerUpEmoji(powerUp.typeEnum)
                    textSize = 28.0
                }
            }

            // Hunter annotations (chickenCanSeeHunters) -- only after hunt starts
            if (state.hasHuntStarted) state.hunterAnnotations.forEach { hunter ->
                PointAnnotation(
                    point = hunter.coordinate
                ) {
                    iconImage = IconImage("marker-15")
                    textField = hunter.displayName
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
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = "North",
                    modifier = Modifier.rotate(-currentBearing)
                )
            }
            if (state.game.powerUps.enabled) {
                ActivePowerUpBadge(game = state.game)
            }
        }

        // Top bar
        MapTopBar(
            titleRes = R.string.you_are_chicken,
            subtitle = viewModel.chickenSubtitle,
            gradientColors = listOf(ChickenYellow, CROrange),
            onInfoTapped = { viewModel.onInfoTapped() }
        )

        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(CRDarkBackground.copy(alpha = 0.85f))
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.radius_format, state.radius), style = gameboyStyle(14), color = Color.White)
                CountdownView(
                    nowDate = state.nowDate,
                    nextUpdateDate = state.nextRadiusUpdate,
                    chickenStartDate = state.game.startDate,
                    hunterStartDate = state.game.hunterStartDate,
                    isChicken = true
                )
            }

            // Power-up inventory button
            if (state.collectedPowerUps.isNotEmpty()) {
                Button(
                    onClick = { viewModel.onPowerUpInventoryTapped() },
                    colors = ButtonDefaults.buttonColors(containerColor = CROrange),
                    shape = RoundedCornerShape(8.dp),
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
                    colors = ButtonDefaults.buttonColors(containerColor = HunterRed),
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier.size(width = 50.dp, height = 40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringResource(R.string.found), fontSize = 11.sp, color = Color.White)
                }
            }
        }

        // Game start countdown overlay
        GameStartCountdownOverlay(
            countdownNumber = state.countdownNumber,
            countdownText = state.countdownText
        )

        // Pre-game overlay
        if (!state.hasGameStarted) {
            PreGameOverlay(
                isChicken = true,
                gameModTitle = state.game.gameModEnum.title,
                gameCode = state.game.gameCode,
                targetDate = state.game.startDate,
                nowDate = state.nowDate,
                connectedHunters = state.game.hunterIds.size,
                onCancelGame = { viewModel.onCancelGameTapped() }
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
                    .background(ZoneDanger.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
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
                    Text(stringResource(R.string.cancel_game), color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCancelAlert() }) {
                    Text(stringResource(R.string.never_mind))
                }
            }
        )
    }

    // Game over alert — dismiss navigates to leaderboard (Victory)
    if (state.showGameOverAlert) {
        GameOverAlertDialog(
            message = state.gameOverMessage,
            onConfirm = {
                viewModel.confirmGameOver { onVictory(state.game.id) }
            }
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

    // Power-up notification
    PowerUpNotificationOverlay(
        notification = state.powerUpNotification,
        powerUpType = state.lastActivatedPowerUpType
    )

    // Power-up detail popup (tap on map icon)
    selectedPowerUpType?.let { type ->
        PowerUpDetailDialog(type = type, onDismiss = { selectedPowerUpType = null })
    }

    // Power-up inventory dialog
    if (state.showPowerUpInventory) {
        PowerUpInventoryDialog(
            collectedPowerUps = state.collectedPowerUps,
            activatingPowerUpId = state.activatingPowerUpId,
            activateButtonColor = PowerupStealth,
            onActivate = { viewModel.activatePowerUp(it) },
            onDismiss = { viewModel.dismissPowerUpInventory() }
        )
    }
}
