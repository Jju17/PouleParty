@file:Suppress("DEPRECATION")

package dev.rahier.pouleparty.ui.huntermap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.text.font.FontWeight
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.mapbox.maps.viewannotation.geometry
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
import dev.rahier.pouleparty.ui.components.HapticManager
import dev.rahier.pouleparty.ui.components.MapHapticsEffect
import dev.rahier.pouleparty.ui.components.KeepScreenOn
import dev.rahier.pouleparty.ui.components.GameOverAlertDialog
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.powerups.ui.ActivePowerUpBadge
import dev.rahier.pouleparty.ui.components.GameStartCountdownOverlay
import dev.rahier.pouleparty.ui.components.MapTopBar
import dev.rahier.pouleparty.powerups.ui.PowerUpDetailDialog
import dev.rahier.pouleparty.powerups.ui.PowerUpInventoryDialog
import dev.rahier.pouleparty.powerups.ui.PowerUpMapMarker
import dev.rahier.pouleparty.powerups.ui.PowerUpNotificationOverlay
import dev.rahier.pouleparty.ui.components.PreGameOverlay
import dev.rahier.pouleparty.ui.challenges.ChallengesSheet

import dev.rahier.pouleparty.ui.theme.*

@OptIn(MapboxExperimental::class)
@Composable
fun HunterMapScreen(
    onGoToMenu: () -> Unit,
    onVictory: (gameId: String, hunterName: String, hunterId: String) -> Unit = { _, _, _ -> },
    viewModel: HunterMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    KeepScreenOn()

    val view = LocalView.current

    // One-shot navigation effects from the ViewModel.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HunterMapEffect.NavigateToMenu -> onGoToMenu()
                HunterMapEffect.NavigateToVictory -> {
                    HapticManager.success(view)
                    viewModel.onIntent(HunterMapIntent.VictoryNavigated)
                    onVictory(viewModel.gameId, viewModel.hunterName, viewModel.hunterId)
                }
            }
        }
    }

    // Show winner notification as snackbar
    LaunchedEffect(state.winnerNotification) {
        state.winnerNotification?.let { message ->
            HapticManager.success(view)
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // Shared haptics (countdown / zone warning / power-up / winners)
    MapHapticsEffect(state, view)
    LaunchedEffect(state.showGameOverAlert) {
        if (state.showGameOverAlert) HapticManager.warning(view)
    }
    LaunchedEffect(state.showWrongCodeAlert) {
        if (state.showWrongCodeAlert) HapticManager.error(view)
    }

    // Force a hunter-location refresh whenever the player re-opens the
    // app. The periodic 5 s writer in the VM is the primary cadence,
    // but Android can suspend the writer coroutine while the app is
    // backgrounded — this bridges the gap so the chicken's map catches
    // up as soon as we're back in the foreground instead of waiting
    // on the next scheduled tick.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(HunterMapIntent.AppResumed)
    }

    var selectedPowerUpType by remember { mutableStateOf<PowerUpType?>(null) }
    var isChallengesSheetVisible by remember { mutableStateOf(false) }

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
                }

                // Power-up markers + collection-radius discs (hunter power-ups only)
                if (state.hasGameStarted) {
                    dev.rahier.pouleparty.powerups.ui.PowerUpsMapOverlay(
                        powerUps = state.availablePowerUps,
                        onMarkerClick = { selectedPowerUpType = it.typeEnum }
                    )
                }

                // Decoy: fake chicken marker when decoy is active
                state.decoyLocation?.let { decoy ->
                    PointAnnotation(point = decoy) {
                        textField = "🐔"
                        textSize = 28.0
                    }
                }

                // Zone preview circle (from Zone Preview power-up)
                state.previewCircle?.let { (center, radius) ->
                    val previewPoints = circlePolygonPoints(center, radius)
                    PolylineAnnotation(
                        points = previewPoints + listOf(previewPoints.first())
                    ) {
                        lineColor = PowerupFreeze.copy(alpha = 0.6f)
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
                titleRes = R.string.you_are_hunter,
                subtitle = viewModel.hunterSubtitle,
                gradientColors = listOf(HunterRed, CRPink),
                onInfoTapped = { viewModel.onIntent(HunterMapIntent.InfoTapped) }
            )

            // Challenges FAB — sits above the bottom bar on the right.
            if (state.hasChallenges) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp, end = 16.dp)
                ) {
                    IconButton(
                        onClick = { isChallengesSheetVisible = true },
                        modifier = Modifier
                            .neonGlow(CROrange, NeonGlowIntensity.SUBTLE, cornerRadius = 26.dp)
                            .background(CRDarkBackground.copy(alpha = 0.85f), CircleShape)
                            .size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = stringResource(R.string.challenges),
                            tint = Color.White
                        )
                    }
                }
            }

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
                        isChicken = false
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Power-up inventory button
                    if (state.collectedPowerUps.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.onIntent(HunterMapIntent.PowerUpInventoryTapped) },
                            colors = ButtonDefaults.buttonColors(containerColor = CROrange),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .size(width = 44.dp, height = 40.dp)
                                .neonGlow(CROrange, NeonGlowIntensity.SUBTLE, cornerRadius = 8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("\u26A1${state.collectedPowerUps.size}", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    // FOUND button (only visible after game starts)
                    if (state.hasGameStarted) {
                        Button(
                            onClick = { viewModel.onIntent(HunterMapIntent.FoundButtonTapped) },
                            colors = ButtonDefaults.buttonColors(containerColor = HunterRed),
                            shape = RoundedCornerShape(50.dp),
                            modifier = Modifier
                                .size(width = 50.dp, height = 40.dp)
                                .neonGlow(HunterRed, NeonGlowIntensity.SUBTLE, cornerRadius = 20.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(stringResource(R.string.found), fontSize = 11.sp, color = Color.White)
                        }
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
                        .neonGlow(ZoneDanger, NeonGlowIntensity.SUBTLE, cornerRadius = 12.dp)
                        .background(ZoneDanger.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }

    // Found code entry dialog
    if (state.isEnteringFoundCode) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(HunterMapIntent.DismissFoundCodeEntry) },
            title = { Text(stringResource(R.string.enter_found_code)) },
            text = {
                Column {
                    Text(stringResource(R.string.enter_code_message))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.enteredCode,
                        onValueChange = { viewModel.onIntent(HunterMapIntent.EnteredCodeChanged(it)) },
                        label = { Text(stringResource(R.string.four_digit_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(HunterMapIntent.SubmitFoundCode) }) { Text(stringResource(R.string.submit)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(HunterMapIntent.DismissFoundCodeEntry) }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Wrong code alert
    if (state.showWrongCodeAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(HunterMapIntent.DismissWrongCodeAlert) },
            title = { Text(stringResource(R.string.wrong_code)) },
            text = { Text(stringResource(R.string.wrong_code_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(HunterMapIntent.DismissWrongCodeAlert) }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // Winner registration failed, right code entered but Firestore write failed
    if (state.winnerRegistrationFailed) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(HunterMapIntent.DismissWinnerRegistrationError) },
            title = { Text(stringResource(R.string.winner_registration_failed_title)) },
            text = { Text(stringResource(R.string.winner_registration_failed_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(HunterMapIntent.RetryWinnerRegistration) }) {
                    Text(stringResource(R.string.retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(HunterMapIntent.DismissWinnerRegistrationError) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Quit game alert
    if (state.showLeaveAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(HunterMapIntent.DismissLeaveAlert) },
            title = { Text(stringResource(R.string.quit_game)) },
            text = { Text(stringResource(R.string.quit_game_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(HunterMapIntent.ConfirmLeaveGame) }) {
                    Text(stringResource(R.string.quit))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(HunterMapIntent.DismissLeaveAlert) }) {
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
                viewModel.onIntent(HunterMapIntent.ConfirmGameOver)
            }
        )
    }

    // Registration required alert — game requires registration but user isn't registered
    if (state.showRegistrationRequiredAlert) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.registration_required)) },
            text = { Text(stringResource(R.string.you_must_register_before_joining_this_game)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(HunterMapIntent.DismissRegistrationRequiredAlert)
                    onGoToMenu()
                }) { Text(stringResource(R.string.back_to_menu)) }
            }
        )
    }

    // Game info dialog
    if (state.showGameInfo) {
        GameInfoDialog(
            game = state.game,
            codeCopied = state.codeCopied,
            onCodeCopied = { viewModel.onIntent(HunterMapIntent.CodeCopied) },
            onDismiss = { viewModel.onIntent(HunterMapIntent.DismissGameInfo) },
            onCancelGame = { viewModel.onIntent(HunterMapIntent.LeaveGameTapped) },
            leaveGameLabel = stringResource(R.string.quit)
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
            activateButtonColor = CROrange,
            onActivate = { viewModel.onIntent(HunterMapIntent.ActivatePowerUp(it)) },
            onDismiss = { viewModel.onIntent(HunterMapIntent.DismissPowerUpInventory) }
        )
    }

    // Challenges / Leaderboard sheet
    if (isChallengesSheetVisible) {
        ChallengesSheet(onDismiss = { isChallengesSheetVisible = false })
    }
}
