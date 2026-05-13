@file:Suppress("DEPRECATION")

package dev.rahier.pouleparty.ui.gamemastermap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import dev.rahier.pouleparty.R
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
    viewModel: GameMasterMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                GameMasterMapEffect.ReturnedToMenu -> onGoToMenu()
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
                    PointAnnotation(point = chicken) {
                        iconImage = IconImage("marker-15")
                        textField = if (state.chickenIsInvisible) "🐔 (hidden)" else "🐔"
                    }
                }

                state.hunterAnnotations.forEach { hunter ->
                    PointAnnotation(point = hunter.coordinate) {
                        iconImage = IconImage("marker-15")
                        textField = hunter.displayName
                    }
                }

                if (state.hasGameStarted) {
                    dev.rahier.pouleparty.powerups.ui.PowerUpsMapOverlay(
                        powerUps = state.powerUpAnnotations,
                        onMarkerClick = { /* GM cannot collect */ },
                    )
                }
            }

            // Top header
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = CROrange,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            ) {
                Column(
                    modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("GameMaster 🦅", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("Arbitre · ${state.hunterAnnotations.size} hunters", color = Color.White.copy(alpha = 0.85f))
                }
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
                IconButton(
                    onClick = { viewModel.onIntent(GameMasterMapIntent.LeaveGameTapped) },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Leave", tint = Color.White)
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
                    )
                }
            }
        }
    }
}

@Composable
private fun HuntersList(
    connectedAnnotations: List<dev.rahier.pouleparty.ui.chickenmap.HunterAnnotation>,
    allHunterIds: List<String>,
) {
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
                    Text("🏃 ${hunter.displayName}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            val missing = allHunterIds.filterNot { hid -> connectedAnnotations.any { it.id == hid } }
            if (missing.isNotEmpty()) {
                items(missing) { _ ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "👤 Hunter (no position yet)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
