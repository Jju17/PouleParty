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
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.components.outerBoundsPoints
import dev.rahier.pouleparty.ui.components.zoomForRadius
import dev.rahier.pouleparty.ui.components.CountdownView
import dev.rahier.pouleparty.ui.components.GameInfoDialog
import dev.rahier.pouleparty.ui.components.GameOverAlertDialog
import dev.rahier.pouleparty.ui.components.GameStartCountdownOverlay
import dev.rahier.pouleparty.ui.endgamecode.EndGameCodeContent

@OptIn(MapboxExperimental::class)
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
    ) { _ ->
    Box(modifier = Modifier.fillMaxSize()) {
        // Mapbox Map
        val mapViewportState = rememberMapViewportState()

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

            // Hunter annotations (mutualTracking mode) -- only after hunt starts
            if (state.hasHuntStarted) state.hunterAnnotations.forEach { hunter ->
                PointAnnotation(
                    point = hunter.coordinate
                ) {
                    iconImage = IconImage("marker-15")
                    textField = hunter.displayName
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

        // Zone warning banner
        if (state.isOutsideZone) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 140.dp)
                    .background(Color.Red.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.return_to_zone),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.outsideZoneSeconds}s",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
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
