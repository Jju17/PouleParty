@file:Suppress("DEPRECATION")

package dev.rahier.pouleparty.ui.gamemastermap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.components.GMChickenMarker
import dev.rahier.pouleparty.ui.components.GameInfoDialog
import dev.rahier.pouleparty.ui.components.HunterMapMarker
import dev.rahier.pouleparty.ui.components.MapTopBar
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.components.outerBoundsPoints
import dev.rahier.pouleparty.ui.components.zoomForRadius
import dev.rahier.pouleparty.ui.theme.*

/**
 * GameMaster observer view (PP-24). Streams chicken + hunter positions
 * + power-ups in read-only mode. The GM never broadcasts their own GPS
 * and cannot collect power-ups.
 */
@OptIn(MapboxExperimental::class, ExperimentalMaterial3Api::class)
@Composable
fun GameMasterMapScreen(
    onGoToMenu: () -> Unit,
    onOpenValidationQueue: () -> Unit = {},
    onVictory: (gameId: String) -> Unit = {},
    viewModel: GameMasterMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                GameMasterMapEffect.ReturnedToMenu -> onGoToMenu()
                GameMasterMapEffect.OpenValidationQueue -> onOpenValidationQueue()
                GameMasterMapEffect.NavigateToVictory -> onVictory(state.game.id)
            }
        }
    }

    LaunchedEffect(state.winnerNotification) {
        state.winnerNotification?.let { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val mapViewportState = rememberMapViewportState()

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
                mapViewportState = mapViewportState,
            ) {
                state.circleCenter?.let { center ->
                    val circlePoints = circlePolygonPoints(center, state.radius.toDouble())
                    PolygonAnnotation(points = listOf(outerBoundsPoints(center), circlePoints)) {
                        fillColor = Color(0f, 0f, 0f, 0.3f)
                        fillOpacity = 1.0
                    }
                    PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                        lineColor = ZoneGreen.copy(alpha = 0.9f)
                        lineWidth = 2.5
                    }
                }

                state.game.finalLocation?.let { finalPos ->
                    val finalCirclePoints = circlePolygonPoints(finalPos, 50.0)
                    PolylineAnnotation(points = finalCirclePoints + listOf(finalCirclePoints.first())) {
                        lineColor = ZoneGreen.copy(alpha = 0.5f)
                        lineWidth = 3.0
                    }
                }

                state.chickenLocation?.let { chicken ->
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(chicken)
                            allowOverlap(true)
                            allowOverlapWithPuck(true)
                        }
                    ) {
                        GMChickenMarker(isInvisible = state.chickenIsInvisible)
                    }
                }

                state.hunterAnnotations.forEach { hunter ->
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(hunter.coordinate)
                            allowOverlap(true)
                            allowOverlapWithPuck(true)
                        }
                    ) {
                        HunterMapMarker(displayName = hunter.displayName)
                    }
                }

                if (state.hasGameStarted) {
                    dev.rahier.pouleparty.powerups.ui.PowerUpsMapOverlay(
                        powerUps = state.powerUpAnnotations,
                        onMarkerClick = { /* GM cannot collect */ },
                    )
                }
            }

            // Top bar (shared component with chicken/hunter — includes
            // the info button on the right).
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                MapTopBar(
                    titleRes = R.string.you_are_gamemaster,
                    subtitle = "Arbitre · ${state.hunterAnnotations.size} hunters",
                    gradientColors = listOf(CRPink, CROrange),
                    onInfoTapped = { viewModel.onIntent(GameMasterMapIntent.InfoTapped) },
                )
            }

            // Bottom action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(CRDarkBackground.copy(alpha = 0.85f))
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { viewModel.onIntent(GameMasterMapIntent.HuntersDrawerTapped) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CROrange),
                ) {
                    Icon(Icons.Default.Group, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Hunters (${state.hunterAnnotations.size})")
                }
                Box {
                    Button(
                        onClick = { viewModel.onIntent(GameMasterMapIntent.ValidationQueueTapped) },
                        colors = ButtonDefaults.buttonColors(containerColor = CRPink),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.challenge_validate))
                    }
                    if (state.pendingSubmissionsCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .background(HunterRed, RoundedCornerShape(50))
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

            // Hunters drawer
            if (state.showHuntersDrawer) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.onIntent(GameMasterMapIntent.DismissHuntersDrawer) },
                ) {
                    HuntersList(
                        connectedAnnotations = state.hunterAnnotations,
                        allHunterIds = state.game.hunterIds,
                        registrations = state.registrations,
                        currentChickenId = state.game.chickenId,
                        canDesignate = state.game.gameStatusEnum == dev.rahier.pouleparty.model.GameStatus.WAITING,
                        onDesignateTapped = { viewModel.onIntent(GameMasterMapIntent.DesignateHunterTapped(it)) },
                    )
                }
            }

            // PP-86 — confirmation alert
            val pending = state.pendingChickenDesignation
            if (pending != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.onIntent(GameMasterMapIntent.DesignateCancelTapped) },
                    title = { Text("Désigner la poule") },
                    text = { Text("${pending.teamName} deviendra la poule. La poule actuelle perdra ce rôle.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.onIntent(GameMasterMapIntent.DesignateConfirmTapped) }) {
                            Text("Désigner ${pending.teamName}")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onIntent(GameMasterMapIntent.DesignateCancelTapped) }) {
                            Text("Annuler")
                        }
                    },
                )
            }

            // PP-86 — error alert
            val designationError = state.designationError
            if (designationError != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.onIntent(GameMasterMapIntent.DesignationErrorDismissed) },
                    title = { Text("Erreur") },
                    text = { Text(designationError) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.onIntent(GameMasterMapIntent.DesignationErrorDismissed) }) {
                            Text("OK")
                        }
                    },
                )
            }

            // Unified pre-game overlay. READY_TO_LAUNCH flips it into
            // manual-launch mode (LAUNCH button — GM has the same
            // authority as the chicken to start the party); before the
            // planned `timing.start` it ticks down so the GM sees the
            // same lobby UI as everyone else.
            if (state.game.gameStatusEnum == dev.rahier.pouleparty.model.GameStatus.READY_TO_LAUNCH) {
                dev.rahier.pouleparty.ui.components.PreGameOverlay(
                    role = dev.rahier.pouleparty.ui.components.PreGameRole.GAME_MASTER,
                    gameModTitle = state.game.gameModEnum.title,
                    gameCode = state.game.gameCode,
                    targetDate = state.game.startDate,
                    nowDate = state.nowDate,
                    connectedHunters = state.game.hunterIds.size,
                    isManualStart = true,
                    isLaunching = state.isLaunching,
                    launchErrorMessage = state.launchError,
                    onLaunchTapped = { viewModel.onIntent(GameMasterMapIntent.LaunchTapped) },
                    onLaunchErrorDismissed = { viewModel.onIntent(GameMasterMapIntent.LaunchErrorDismissed) },
                )
            } else if (!state.hasGameStarted) {
                dev.rahier.pouleparty.ui.components.PreGameOverlay(
                    role = dev.rahier.pouleparty.ui.components.PreGameRole.GAME_MASTER,
                    gameModTitle = state.game.gameModEnum.title,
                    gameCode = state.game.gameCode,
                    targetDate = state.game.startDate,
                    nowDate = state.nowDate,
                    connectedHunters = state.game.hunterIds.size,
                )
            }

            // "Game ended → tap to see leaderboard" banner. Shown on
            // top of the map once `status == DONE`. Tapping navigates
            // to the Victory screen (which has the canonical "Back to
            // menu" CTA).
            if (state.isGameOver) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 92.dp, start = 16.dp, end = 16.dp)
                ) {
                    dev.rahier.pouleparty.ui.components.GameEndedBanner(
                        onTap = { viewModel.onIntent(GameMasterMapIntent.ViewLeaderboardTapped) }
                    )
                }
            }

            if (state.game.isDebugGame) {
                dev.rahier.pouleparty.ui.components.DebugQAPanel(
                    onSpawnPowerUps = { viewModel.onIntent(GameMasterMapIntent.DebugSpawnPowerUpsTapped) },
                    onEndNow = { viewModel.onIntent(GameMasterMapIntent.DebugEndNowTapped) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 130.dp)
                )
            }

            if (state.showGameInfo) {
                GameInfoDialog(
                    game = state.game,
                    codeCopied = state.codeCopied,
                    onCodeCopied = { viewModel.onIntent(GameMasterMapIntent.CodeCopied) },
                    onDismiss = { viewModel.onIntent(GameMasterMapIntent.DismissGameInfo) },
                    onCancelGame = { viewModel.onIntent(GameMasterMapIntent.LeaveGameTapped) },
                    leaveGameLabel = stringResource(R.string.quit),
                )
            }
        }
    }
}

@Composable
private fun HuntersList(
    connectedAnnotations: List<dev.rahier.pouleparty.ui.chickenmap.HunterAnnotation>,
    allHunterIds: List<String>,
    registrations: List<dev.rahier.pouleparty.model.Registration> = emptyList(),
    currentChickenId: String = "",
    canDesignate: Boolean = false,
    onDesignateTapped: (dev.rahier.pouleparty.model.Registration) -> Unit = {},
) {
    fun displayName(uid: String): String =
        registrations.firstOrNull { it.userId == uid }?.teamName ?: "Hunter"

    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Text("Hunters", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Connected: ${connectedAnnotations.size} / ${allHunterIds.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(connectedAnnotations) { hunter ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🏃 ${displayName(hunter.id)}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            val missing = allHunterIds.filterNot { hid -> connectedAnnotations.any { it.id == hid } }
            if (missing.isNotEmpty()) {
                items(missing) { hid ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "👤 ${displayName(hid)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val designatable = registrations.filter { it.userId != currentChickenId }
            if (canDesignate && designatable.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Désigner la poule",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Le hunter désigné devient la poule ; il quitte la liste des chasseurs. Possible uniquement avant le début de la partie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(designatable) { reg ->
                    TextButton(
                        onClick = { onDesignateTapped(reg) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("👑 ", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                reg.teamName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
