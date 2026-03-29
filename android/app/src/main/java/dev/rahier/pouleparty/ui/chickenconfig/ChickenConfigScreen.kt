package dev.rahier.pouleparty.ui.chickenconfig

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
            gameMod = state.game.gameModEnum,
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
                    finalMarker = state.game.finalLocation,
                    onLocationSelected = { point ->
                        viewModel.onLocationSelected(point)
                    },
                    onFinalLocationSelected = { point ->
                        viewModel.onFinalLocationSelected(point)
                    },
                    onRadiusChanged = { radius ->
                        viewModel.updateInitialRadius(radius)
                    }
                )
            }
        }
        return
    }

    val formBackground = MaterialTheme.colorScheme.background
    val formCardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val formCardElevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    val isZoneConfigured = state.isZoneConfigured

    Scaffold(
        containerColor = formBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.game_settings)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = formBackground)
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(Modifier.height(0.dp))

                // --- Game Code section ---
                SectionHeader(stringResource(R.string.game_code_label))
                GameCodeCard(
                    gameCode = state.game.gameCode,
                    codeCopied = state.codeCopied,
                    onCodeCopied = { viewModel.onCodeCopied() },
                    colors = formCardColors,
                    elevation = formCardElevation
                )

                // --- Schedule section ---
                SectionHeader(stringResource(R.string.schedule_label))
                Card(modifier = Modifier.fillMaxWidth(), colors = formCardColors, elevation = formCardElevation) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Start time row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onStartTimeTapped() },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.start_at))
                            Text(dateFormat.format(state.game.startDate))
                        }

                        if (!state.isExpertMode) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // Duration picker
                            Text(stringResource(R.string.duration))
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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // Ends at row
                            val endTime = Date(state.game.startDate.time + (state.gameDurationMinutes * 60 * 1000).toLong())
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.ends_at))
                                Text(dateFormat.format(endTime), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                // --- Game Mode section ---
                SectionHeader(stringResource(R.string.game_mode))
                Card(modifier = Modifier.fillMaxWidth(), colors = formCardColors, elevation = formCardElevation) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Game mode dropdown
                        var modeExpanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.game_mode))
                            Box {
                                Row(
                                    modifier = Modifier.clickable { modeExpanded = true },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        state.game.gameModEnum.title,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                DropdownMenu(
                                    expanded = modeExpanded,
                                    onDismissRequest = { modeExpanded = false }
                                ) {
                                    GameMod.entries.forEach { mod ->
                                        DropdownMenuItem(
                                            text = { Text(mod.title) },
                                            onClick = {
                                                viewModel.updateGameMod(mod)
                                                modeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Chicken can see hunters toggle
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

                // --- Power-Ups section ---
                SectionHeader(stringResource(R.string.power_ups_label))
                Card(modifier = Modifier.fillMaxWidth(), colors = formCardColors, elevation = formCardElevation) {
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
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            val unavailable = if (state.game.gameModEnum == dev.rahier.pouleparty.model.GameMod.STAY_IN_THE_ZONE) {
                                setOf(
                                    dev.rahier.pouleparty.model.PowerUpType.INVISIBILITY.firestoreValue,
                                    dev.rahier.pouleparty.model.PowerUpType.DECOY.firestoreValue,
                                    dev.rahier.pouleparty.model.PowerUpType.JAMMER.firestoreValue
                                )
                            } else emptySet()
                            val enabledCount = state.game.enabledPowerUpTypes.count { it !in unavailable }
                            val totalCount = dev.rahier.pouleparty.model.PowerUpType.entries.count { it.firestoreValue !in unavailable }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onPowerUpSelectionTapped() },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.choose_power_ups))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.power_ups_count_format, enabledCount, totalCount),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Zone section ---
                SectionHeader(stringResource(R.string.zone))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onMapSetupTapped() },
                    colors = formCardColors,
                    elevation = formCardElevation
                ) {
                    // Inline map preview (tap to open full map config)
                    Box(modifier = Modifier.padding(8.dp)) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            MapPreviewContent(
                                center = state.game.initialLocation,
                                radius = state.game.initialRadius,
                                finalCenter = state.game.finalLocation
                            )
                        }
                    }
                }

                // --- Advanced settings (expert mode only) ---
                if (state.isExpertMode) {
                    SectionHeader(stringResource(R.string.advanced_label))
                    Card(modifier = Modifier.fillMaxWidth(), colors = formCardColors, elevation = formCardElevation) {
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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

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

                // --- Head Start section ---
                SectionHeader(stringResource(R.string.head_start_label))
                Card(modifier = Modifier.fillMaxWidth(), colors = formCardColors, elevation = formCardElevation) {
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

                // --- Settings mode toggle (Normal / Expert) ---
                SectionHeader(stringResource(R.string.mode_label))
                Card(modifier = Modifier.fillMaxWidth(), colors = formCardColors, elevation = formCardElevation) {
                    Column(modifier = Modifier.padding(16.dp)) {
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

                Spacer(Modifier.height(8.dp))
            }

            // Start game button — prominent, outside the scrollable form
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isZoneConfigured) {
                    Text(
                        text = if (state.game.gameModEnum == GameMod.STAY_IN_THE_ZONE)
                            stringResource(R.string.set_start_and_final_zone)
                        else
                            stringResource(R.string.set_start_zone),
                        style = gameboyStyle(8),
                        color = CROrange
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .then(if (isZoneConfigured) Modifier.shadow(4.dp, RoundedCornerShape(50.dp)) else Modifier)
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (isZoneConfigured) GradientFire else Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f))))
                        .then(if (isZoneConfigured) Modifier.clickable { viewModel.startGame(onStartGame) } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.start_game),
                        color = Color.Black.copy(alpha = if (isZoneConfigured) 1f else 0.4f),
                        style = bangerStyle(24)
                    )
                }
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 8.dp)
    )
}

@OptIn(MapboxExperimental::class)
@Composable
private fun MapPreviewContent(center: Point, radius: Double, finalCenter: Point? = null) {
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
            // Neon glow start zone circle
            val circlePoints = circlePolygonPoints(center, radius)
            PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                lineColor = CROrange.copy(alpha = 0.1f)
                lineWidth = 12.0
            }
            PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                lineColor = CROrange.copy(alpha = 0.3f)
                lineWidth = 4.0
            }
            PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                lineColor = CROrange.copy(alpha = 0.9f)
                lineWidth = 2.0
            }

            // Final zone marker — neon green glow
            finalCenter?.let { fc ->
                val finalCirclePoints = circlePolygonPoints(fc, 50.0)
                PolylineAnnotation(points = finalCirclePoints + listOf(finalCirclePoints.first())) {
                    lineColor = ZoneGreen.copy(alpha = 0.4f)
                    lineWidth = 3.0
                }
                PolylineAnnotation(points = finalCirclePoints + listOf(finalCirclePoints.first())) {
                    lineColor = ZoneGreen.copy(alpha = 0.9f)
                    lineWidth = 1.5
                }
            }
        }
        // Transparent overlay to block map gestures while letting parent Card clicks through
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Consume all pointer events so the map doesn't pan/zoom,
                    // but don't use clickable so the Card's click handler still works
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
        )
    }
}
