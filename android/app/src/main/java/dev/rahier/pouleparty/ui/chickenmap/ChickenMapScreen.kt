package dev.rahier.pouleparty.ui.chickenmap

import dev.rahier.pouleparty.ui.common.ConnectionLostBanner
import dev.rahier.pouleparty.ui.common.rememberIsOnline
import dev.rahier.pouleparty.ui.theme.MinTouchTarget
import dev.rahier.pouleparty.ui.theme.MapOverlayOffsets
import androidx.compose.runtime.saveable.rememberSaveable
import dev.rahier.pouleparty.ui.components.FinalZoneOutline
import dev.rahier.pouleparty.ui.components.ZoneOverlay
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.powerups.ui.PowerUpsMapOverlay
import dev.rahier.pouleparty.ui.components.DebugQAPanel
import dev.rahier.pouleparty.ui.components.GameEndedBanner
import dev.rahier.pouleparty.ui.components.HunterMapMarker
import dev.rahier.pouleparty.ui.components.PreGameRole
import dev.rahier.pouleparty.ui.gamelogic.chickenSubtitleRes
import dev.rahier.pouleparty.ui.common.asString
import dev.rahier.pouleparty.ui.common.LoadState
import dev.rahier.pouleparty.ui.common.LoadStateScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.text.font.FontWeight
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.locationcomponent.location
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.components.zonePreviewColor
import dev.rahier.pouleparty.ui.components.outerBoundsPoints
import dev.rahier.pouleparty.ui.components.zoomForRadius
import dev.rahier.pouleparty.ui.components.CountdownView
import dev.rahier.pouleparty.ui.components.GameInfoDialog
import dev.rahier.pouleparty.ui.components.GameLeaderboardSheet
import dev.rahier.pouleparty.ui.components.HapticManager
import dev.rahier.pouleparty.ui.components.MapHapticsEffect
import dev.rahier.pouleparty.ui.components.KeepScreenOn
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.powerups.ui.ActivePowerUpBadge
import dev.rahier.pouleparty.ui.components.MapTopBar
import dev.rahier.pouleparty.powerups.ui.PowerUpDetailDialog
import dev.rahier.pouleparty.powerups.ui.PowerUpMapMarker
import dev.rahier.pouleparty.powerups.ui.PowerUpInventoryDialog
import dev.rahier.pouleparty.powerups.ui.PowerUpNotificationOverlay
import dev.rahier.pouleparty.ui.components.GameStartCountdownOverlay
import dev.rahier.pouleparty.ui.components.PreGameOverlay

import dev.rahier.pouleparty.ui.endgamecode.EndGameCodeContent
import dev.rahier.pouleparty.ui.theme.*

@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@OptIn(MapboxExperimental::class)
@Composable
fun ChickenMapScreen(
    onGoToMenu: () -> Unit,
    onVictory: (gameId: String) -> Unit = {},
    onOpenValidationQueue: () -> Unit = {},
    onBecameHunter: (gameId: String, teamName: String) -> Unit = { _, _ -> },
    viewModel: ChickenMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    KeepScreenOn()

    val view = LocalView.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ChickenMapEffect.NavigateToMenu -> onGoToMenu()
                ChickenMapEffect.NavigateToVictory -> {
                    HapticManager.success(view)
                    onVictory(state.game.id)
                }
                ChickenMapEffect.OpenValidationQueue -> onOpenValidationQueue()
                is ChickenMapEffect.NavigateToHunterMap ->
                    onBecameHunter(effect.gameId, effect.teamName)
            }
        }
    }

    if (state.loadState != LoadState.Ready) {
        LoadStateScreen(state.loadState, onRetry = { viewModel.onIntent(ChickenMapIntent.RetryLoad) }, onBack = onGoToMenu)
        return
    }

    // Show winner notification as snackbar
    val winnerMessage = state.winnerNotification?.asString()
    LaunchedEffect(winnerMessage) {
        winnerMessage?.let { message ->
            HapticManager.success(view)
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // Shared haptics (countdown / zone warning / power-up / winners)
    MapHapticsEffect(state, state.isGameOver, view)

    var selectedPowerUpType by rememberSaveable { mutableStateOf<PowerUpType?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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

            state.circleCenter?.let { center ->
                ZoneOverlay(center, state.radius.toDouble(), state.isOutsideZone)
            }
            state.game.finalLocation?.let { FinalZoneOutline(it) }

            // Power-up markers + collection-radius discs (chicken power-ups only)
            if (state.hasGameStarted) {
                PowerUpsMapOverlay(
                    powerUps = state.availablePowerUps,
                    onMarkerClick = { selectedPowerUpType = it.typeEnum }
                )
            }

            // Hunter annotations (chickenCanSeeHunters) -- only after hunt starts
            if (state.hasHuntStarted) state.hunterAnnotations.forEach { hunter ->
                key(hunter.id) {
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(hunter.coordinate)
                        allowOverlap(true)
                        allowOverlapWithPuck(true)
                    }
                ) {
                    HunterMapMarker(displayName = hunter.displayName.asString())
                }
                }
            }
        }

        if (!rememberIsOnline()) {
            ConnectionLostBanner(Modifier.align(Alignment.TopCenter).padding(top = MapOverlayOffsets.belowTopBar))
        }

        // Compass button + active power-up badges
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = MapOverlayOffsets.belowTopBar),
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
                    .size(MinTouchTarget)
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = stringResource(R.string.map_reset_north),
                    modifier = Modifier.rotate(-currentBearing)
                )
            }
            if (state.game.powerUps.enabled) {
                ActivePowerUpBadge(game = state.game)
            }
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.padding(end = 8.dp)) {
                IconButton(
                    onClick = { viewModel.onIntent(ChickenMapIntent.ValidationQueueTapped) },
                    modifier = Modifier
                        .shadow(4.dp, CircleShape)
                        .background(CRPink, CircleShape)
                        .size(MinTouchTarget),
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.validation_queue_title),
                        tint = Color.White,
                    )
                }
                if (state.pendingSubmissionsCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .background(HunterRed, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${state.pendingSubmissionsCount}",
                            color = Color.White,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }

        // Top bar
        MapTopBar(
            titleRes = R.string.you_are_chicken,
            subtitle = stringResource(chickenSubtitleRes(state.game)),
            gradientColors = listOf(ChickenYellow, CROrange),
            onInfoTapped = { viewModel.onIntent(ChickenMapIntent.InfoTapped) }
        )

        // "Game ended → tap to see leaderboard" banner. Shown on top
        // of the map once `status == DONE`. Tapping navigates to the
        // Victory screen (which has the canonical "Back to menu" CTA).
        if (state.isGameOver) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 92.dp, start = 16.dp, end = 16.dp)
            ) {
                GameEndedBanner(
                    onTap = { viewModel.onIntent(ChickenMapIntent.ViewLeaderboardTapped) }
                )
            }
        }

        if (state.game.isDebugGame) {
            DebugQAPanel(
                onNextStep = { viewModel.onIntent(ChickenMapIntent.DebugAdvanceStepTapped) },
                onEndNow = { viewModel.onIntent(ChickenMapIntent.DebugEndNowTapped) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 130.dp)
            )
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
                    endDate = state.game.endDate,
                    isChicken = true
                )
            }

            // Power-up inventory button
            if (state.collectedPowerUps.isNotEmpty()) {
                Button(
                    onClick = { viewModel.onIntent(ChickenMapIntent.PowerUpInventoryTapped) },
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

            if (state.hasGameStarted && !state.isGameOver) {
                Button(
                    onClick = { viewModel.onIntent(ChickenMapIntent.FoundButtonTapped) },
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

        // Game start countdown overlay
        GameStartCountdownOverlay(
            countdownNumber = state.countdownNumber,
            countdownText = state.countdownText?.asString()
        )

        if (state.game.gameStatusEnum == GameStatus.READY_TO_LAUNCH) {
            PreGameOverlay(
                role = PreGameRole.CHICKEN,
                gameModTitle = stringResource(state.game.gameModEnum.titleRes),
                gameCode = state.game.gameCode,
                targetDate = state.game.startDate,
                nowDate = state.nowDate,
                connectedHunters = state.game.hunterIds.size,
                onCancelGame = { viewModel.onIntent(ChickenMapIntent.CancelGameTapped) },
                isManualStart = true,
                isLaunching = state.isLaunching,
                launchErrorMessage = state.launchError?.asString(),
                onLaunchTapped = { viewModel.onIntent(ChickenMapIntent.LaunchTapped) },
                onLaunchErrorDismissed = { viewModel.onIntent(ChickenMapIntent.LaunchErrorDismissed) },
            )
        } else if (!state.hasGameStarted) {
            PreGameOverlay(
                role = PreGameRole.CHICKEN,
                gameModTitle = stringResource(state.game.gameModEnum.titleRes),
                gameCode = state.game.gameCode,
                targetDate = state.game.startDate,
                nowDate = state.nowDate,
                connectedHunters = state.game.hunterIds.size,
                onCancelGame = { viewModel.onIntent(ChickenMapIntent.CancelGameTapped) }
            )
        }

        // Zone warning banner (visual warning only, no elimination)
        if (state.isOutsideZone) {
            Text(
                text = stringResource(R.string.return_to_zone),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = MapOverlayOffsets.firstBanner)
                    .neonGlow(ZoneDanger, NeonGlowIntensity.SUBTLE, cornerRadius = 12.dp)
                    .background(ZoneDanger.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
    }

    // Cancel alert
    if (state.showCancelAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(ChickenMapIntent.DismissCancelAlert) },
            title = { Text(stringResource(R.string.cancel_game)) },
            text = { Text(stringResource(R.string.cancel_game_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(ChickenMapIntent.ConfirmCancelGame) }) {
                    Text(stringResource(R.string.cancel_game), color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(ChickenMapIntent.DismissCancelAlert) }) {
                    Text(stringResource(R.string.never_mind))
                }
            }
        )
    }

    if (state.showNewChickenAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(ChickenMapIntent.DismissNewChickenAlert) },
            title = { Text(stringResource(R.string.new_chicken_alert_title)) },
            text = { Text(stringResource(R.string.new_chicken_alert_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(ChickenMapIntent.DismissNewChickenAlert) }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Found code dialog
    if (state.showFoundCode) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(ChickenMapIntent.DismissFoundCode) },
            title = null,
            text = { EndGameCodeContent(foundCode = state.chickenFoundCode) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(ChickenMapIntent.DismissFoundCode) }) {
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
            onCodeCopied = { viewModel.onIntent(ChickenMapIntent.CodeCopied) },
            onDismiss = { viewModel.onIntent(ChickenMapIntent.DismissGameInfo) },
            onCancelGame = { viewModel.onIntent(ChickenMapIntent.CancelGameTapped) }
        )
    }

    // Power-up notification
    PowerUpNotificationOverlay(
        notification = state.powerUpNotification?.asString(),
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
            onActivate = { viewModel.onIntent(ChickenMapIntent.ActivatePowerUp(it)) },
            onDismiss = { viewModel.onIntent(ChickenMapIntent.DismissPowerUpInventory) }
        )
    }

}
