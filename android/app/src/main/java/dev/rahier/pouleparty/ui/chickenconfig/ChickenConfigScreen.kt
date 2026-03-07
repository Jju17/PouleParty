package dev.rahier.pouleparty.ui.chickenconfig

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.components.GameCodeCard
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.components.zoomForRadius
import dev.rahier.pouleparty.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChickenConfigScreen(
    onStartGame: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ChickenConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Power-up selection overlay
    if (state.showPowerUpSelection) {
        BackHandler { viewModel.dismissPowerUpSelection() }
        PowerUpSelectionScreen(
            enabledTypes = state.game.enabledPowerUpTypes,
            onToggle = { viewModel.togglePowerUpType(it) },
            onDismiss = { viewModel.dismissPowerUpSelection() }
        )
        return
    }

    // Map config full-screen overlay
    if (state.showMapConfig) {
        BackHandler { viewModel.dismissMapConfig() }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.map_setup)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.dismissMapConfig() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                ChickenMapConfigScreen(
                    initialRadius = state.game.initialRadius,
                    onLocationSelected = { point ->
                        viewModel.onLocationSelected(point)
                    }
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.game_settings)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(0.dp))

                // Game Code section
                GameCodeCard(
                    gameCode = state.game.gameCode,
                    codeCopied = state.codeCopied,
                    onCodeCopied = { viewModel.onCodeCopied() }
                )

                // Start time (tappable to open time picker)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.onStartTimeTapped() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.start_at))
                        Text(dateFormat.format(state.game.startDate))
                    }
                }

                // Duration picker (normal mode only)
                if (!state.isExpertMode) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.duration), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(8.dp))
                            val durationOptions = listOf(60.0 to "1h", 90.0 to "1h30", 120.0 to "2h", 150.0 to "2h30", 180.0 to "3h")
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                durationOptions.forEachIndexed { index, (minutes, label) ->
                                    SegmentedButton(
                                        selected = state.gameDurationMinutes == minutes,
                                        onClick = { viewModel.updateGameDuration(minutes) },
                                        shape = SegmentedButtonDefaults.itemShape(index, durationOptions.size)
                                    ) {
                                        Text(label)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val endTime = Date(state.game.startDate.time + (state.gameDurationMinutes * 60 * 1000).toLong())
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.ends_at))
                                Text(dateFormat.format(endTime), color = Color.Gray)
                            }
                        }
                    }
                }

                // Game Mode picker
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.game_mode), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        GameMod.entries.forEach { mod ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.game.gameModEnum == mod,
                                    onClick = { viewModel.updateGameMod(mod) }
                                )
                                Text(mod.title, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.chicken_can_see_hunters))
                            Switch(
                                checked = state.game.chickenCanSeeHunters,
                                onCheckedChange = { viewModel.toggleChickenCanSeeHunters(it) }
                            )
                        }
                    }
                }

                // Power-Ups card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.enable_power_ups))
                            Switch(
                                checked = state.game.powerUpsEnabled,
                                onCheckedChange = { viewModel.togglePowerUps(it) }
                            )
                        }
                        if (state.game.powerUpsEnabled) {
                            Spacer(Modifier.height(8.dp))
                            val enabledCount = state.game.enabledPowerUpTypes.size
                            val totalCount = dev.rahier.pouleparty.model.PowerUpType.entries.size
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onPowerUpSelectionTapped() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.choose_power_ups))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            stringResource(R.string.power_ups_count_format, enabledCount, totalCount),
                                            color = Color.Gray
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Zone card — inline map preview + radius slider
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.zone), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        // Inline map preview (tap to open full map config)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clickable { viewModel.onMapSetupTapped() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            MapPreviewContent(
                                center = state.game.initialLocation,
                                radius = state.game.initialRadius
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.zone_radius))
                            Text(stringResource(R.string.meters_format, state.game.initialRadius.toInt()))
                        }
                        Slider(
                            value = state.game.initialRadius.toFloat(),
                            onValueChange = { viewModel.updateInitialRadius(it.toDouble()) },
                            valueRange = 500f..2000f,
                            steps = 14
                        )
                    }
                }

                // Advanced settings (expert mode only)
                if (state.isExpertMode) {
                    // Radius interval update slider
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.radius_interval_update))
                                Text(stringResource(R.string.minutes_format, state.game.radiusIntervalUpdate.toInt()))
                            }
                            Slider(
                                value = state.game.radiusIntervalUpdate.toFloat(),
                                onValueChange = { viewModel.updateRadiusIntervalUpdate(it.toDouble()) },
                                valueRange = 1f..60f,
                                steps = 58
                            )
                        }
                    }

                    // Radius decline slider
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.radius_decline))
                                Text(stringResource(R.string.meters_format, state.game.radiusDeclinePerUpdate.toInt()))
                            }
                            Slider(
                                value = state.game.radiusDeclinePerUpdate.toFloat(),
                                onValueChange = { viewModel.updateRadiusDecline(it.toDouble()) },
                                valueRange = 50f..1000f,
                                steps = 94
                            )
                        }
                    }
                }

                // Chicken head start slider
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.chicken_head_start))
                            Text(stringResource(R.string.minutes_format, state.game.chickenHeadStartMinutes.toInt()))
                        }
                        Slider(
                            value = state.game.chickenHeadStartMinutes.toFloat(),
                            onValueChange = { viewModel.updateChickenHeadStart(it.toDouble()) },
                            valueRange = 0f..45f,
                            steps = 44
                        )
                    }
                }

                // Settings mode toggle (Normal / Expert)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings_mode), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !state.isExpertMode,
                                onClick = { viewModel.toggleExpertMode(false) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) {
                                Text(stringResource(R.string.normal))
                            }
                            SegmentedButton(
                                selected = state.isExpertMode,
                                onClick = { viewModel.toggleExpertMode(true) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) {
                                Text(stringResource(R.string.expert))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(0.dp))
            }

            // Start game button — prominent, outside the scrollable form
            Button(
                onClick = { viewModel.startGame(onStartGame) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CROrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.start_game), color = Color.White, style = bangerStyle(24))
            }
        }
    }

    // Time picker dialog
    if (state.showTimePicker) {
        val cal = remember { Calendar.getInstance().apply { time = state.game.startDate } }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { viewModel.dismissTimePicker() },
            title = { Text(stringResource(R.string.start_at)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateStartDate(timePickerState.hour, timePickerState.minute)
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTimePicker() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Error alert
    if (state.showAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAlert() },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(state.alertMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAlert() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

@OptIn(MapboxExperimental::class)
@Composable
private fun MapPreviewContent(center: Point, radius: Double) {
    // Extra -1 to account for the short height (180dp) of the inline preview
    val zoom = zoomForRadius(radius, center.latitude()) - 1.0
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(center)
            zoom(zoom)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState
        ) {
            val circlePoints = circlePolygonPoints(center, radius)
            PolylineAnnotation(
                points = circlePoints + listOf(circlePoints.first())
            ) {
                lineColor = CROrange.copy(alpha = 0.8f)
                lineWidth = 2.0
            }
        }
        // Overlay to absorb all touch events — makes the map preview non-interactive
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = false, onClick = {})
        )
    }
}
