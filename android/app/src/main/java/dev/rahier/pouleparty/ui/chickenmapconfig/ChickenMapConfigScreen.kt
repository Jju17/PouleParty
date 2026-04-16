package dev.rahier.pouleparty.ui.chickenmapconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.ZoneGreen
import dev.rahier.pouleparty.ui.theme.gameboyStyle

@OptIn(MapboxExperimental::class, ExperimentalMaterial3Api::class)
@Composable
fun ChickenMapConfigScreen(
    initialRadius: Double,
    finalMarker: Point?,
    onLocationSelected: (Point) -> Unit,
    onFinalLocationSelected: (Point?) -> Unit,
    onRadiusChanged: (Double) -> Unit,
    isFollowMode: Boolean = false,
    viewModel: ChickenMapConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize(initialRadius, finalMarker)
    }

    LaunchedEffect(isFollowMode) {
        viewModel.setFollowMode(isFollowMode)
    }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(state.cameraCenter)
            zoom(state.cameraZoom.toDouble())
        }
    }

    // Fly camera when cameraCenter changes
    LaunchedEffect(state.cameraCenter) {
        mapViewportState.flyTo(
            cameraOptions = CameraOptions.Builder()
                .center(state.cameraCenter)
                .zoom(state.cameraZoom.toDouble())
                .build()
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = { MapStyle(style = Style.MAPBOX_STREETS) }
        ) {
            // Enable location puck + map tap listener
            MapEffect(Unit) { mapView ->
                mapView.location.updateSettings {
                    enabled = true
                    pulsingEnabled = true
                }
                mapView.gestures.addOnMapClickListener { point ->
                    // Read pin mode BEFORE onMapTapped auto-switches it
                    val previousMode = viewModel.uiState.value.pinMode
                    viewModel.onMapTapped(point)
                    val updatedState = viewModel.uiState.value
                    if (previousMode == MapConfigPinMode.START) {
                        onLocationSelected(point)
                    } else {
                        onFinalLocationSelected(updatedState.finalMarkerPosition)
                    }
                    true
                }
            }

            // Initial zone circle — neon glow (4 layers like iOS)
            val circlePoints = circlePolygonPoints(state.markerPosition, state.radius)
            PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                lineColor = CROrange.copy(alpha = 0.08f)
                lineWidth = 16.0
            }
            PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                lineColor = CROrange.copy(alpha = 0.15f)
                lineWidth = 8.0
            }
            PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                lineColor = CROrange.copy(alpha = 0.35f)
                lineWidth = 4.0
            }
            PolylineAnnotation(points = circlePoints + listOf(circlePoints.first())) {
                lineColor = CROrange.copy(alpha = 0.9f)
                lineWidth = 2.5
            }

            // Start center pin
            PointAnnotation(point = state.markerPosition) {
                textField = "📍 START"
                textSize = 12.0
                textOffset = listOf(0.0, -1.5)
                textColor = CROrange
            }

            // Final zone marker + circle (neon green glow)
            state.finalMarkerPosition?.let { finalPos ->
                // Final zone pin
                PointAnnotation(point = finalPos) {
                    textField = "🏁 FINAL"
                    textSize = 12.0
                    textOffset = listOf(0.0, -1.5)
                    textColor = ZoneGreen
                }

                // Neon glow circle at final position
                val finalCirclePoints = circlePolygonPoints(finalPos, 50.0)
                PolylineAnnotation(points = finalCirclePoints + listOf(finalCirclePoints.first())) {
                    lineColor = ZoneGreen.copy(alpha = 0.15f)
                    lineWidth = 8.0
                }
                PolylineAnnotation(points = finalCirclePoints + listOf(finalCirclePoints.first())) {
                    lineColor = ZoneGreen.copy(alpha = 0.5f)
                    lineWidth = 3.0
                }
                PolylineAnnotation(points = finalCirclePoints + listOf(finalCirclePoints.first())) {
                    lineColor = ZoneGreen.copy(alpha = 0.9f)
                    lineWidth = 1.5
                }
            }
        }

        // Search bar at top
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(8.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text(stringResource(R.string.search_address), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )

            if (state.searchResults.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    LazyColumn {
                        items(state.searchResults.take(5)) { result ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val previousMode = viewModel.uiState.value.pinMode
                                        viewModel.onSearchResultSelected(result)
                                        val updatedState = viewModel.uiState.value
                                        if (previousMode == MapConfigPinMode.START) {
                                            onLocationSelected(
                                                Point.fromLngLat(result.longitude, result.latitude)
                                            )
                                        } else {
                                            onFinalLocationSelected(updatedState.finalMarkerPosition)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(result.title, fontSize = 14.sp)
                                if (result.subtitle.isNotEmpty()) {
                                    Text(result.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Bottom bar: hint + radius slider + pin mode picker
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hint text
            if (state.pinMode == MapConfigPinMode.FINAL) {
                Text(
                    text = stringResource(R.string.final_zone_hint),
                    style = gameboyStyle(8),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // Radius slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.zone_radius), style = gameboyStyle(9))
                Text(
                    stringResource(R.string.meters_format, state.radius.toInt()),
                    style = gameboyStyle(9),
                    color = CROrange
                )
            }
            Slider(
                value = state.radius.toFloat(),
                onValueChange = {
                    viewModel.updateRadius(it.toDouble())
                    onRadiusChanged(it.toDouble())
                },
                valueRange = 500f..2000f,
                steps = 14,
                colors = SliderDefaults.colors(thumbColor = CROrange, activeTrackColor = CROrange)
            )

            // Pin mode segmented control — only relevant for Stay in the Zone mode.
            // In Follow the Chicken, the final zone is the chicken's live position.
            if (!isFollowMode) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.pinMode == MapConfigPinMode.START,
                        onClick = { viewModel.setPinMode(MapConfigPinMode.START) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) {
                        Text(stringResource(R.string.start_zone))
                    }
                    SegmentedButton(
                        selected = state.pinMode == MapConfigPinMode.FINAL,
                        onClick = { viewModel.setPinMode(MapConfigPinMode.FINAL) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) {
                        Text(stringResource(R.string.final_zone))
                    }
                }
            }
        }

    }
}
