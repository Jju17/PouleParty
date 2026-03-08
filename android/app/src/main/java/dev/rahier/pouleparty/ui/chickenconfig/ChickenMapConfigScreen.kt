package dev.rahier.pouleparty.ui.chickenconfig

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
import com.mapbox.maps.extension.compose.MapboxMap
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

@OptIn(MapboxExperimental::class, ExperimentalMaterial3Api::class)
@Composable
fun ChickenMapConfigScreen(
    initialRadius: Double,
    finalMarker: Point?,
    onLocationSelected: (Point) -> Unit,
    onFinalLocationSelected: (Point?) -> Unit,
    viewModel: ChickenMapConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize(initialRadius, finalMarker)
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
            mapViewportState = mapViewportState
        ) {
            // Enable location puck + map tap listener
            MapEffect(Unit) { mapView ->
                mapView.location.updateSettings {
                    enabled = true
                    pulsingEnabled = true
                }
                mapView.gestures.addOnMapClickListener { point ->
                    viewModel.onMapTapped(point)
                    val updatedState = viewModel.uiState.value
                    if (updatedState.pinMode == MapConfigPinMode.START) {
                        onLocationSelected(point)
                    } else {
                        onFinalLocationSelected(updatedState.finalMarkerPosition)
                    }
                    true
                }
            }

            // Initial zone circle — CROrange
            val circlePoints = circlePolygonPoints(state.markerPosition, state.radius)
            PolylineAnnotation(
                points = circlePoints + listOf(circlePoints.first())
            ) {
                lineColor = CROrange.copy(alpha = 0.8f)
                lineWidth = 2.0
            }

            // Start center marker
            PointAnnotation(
                point = state.markerPosition
            ) {
                iconImage = IconImage("marker-15")
                textField = "Start"
            }

            // Final zone marker + circle (smaller, green)
            state.finalMarkerPosition?.let { finalPos ->
                PointAnnotation(point = finalPos) {
                    iconImage = IconImage("marker-15")
                    textField = "Final"
                }

                // Small circle at final position to visualize it
                val finalCirclePoints = circlePolygonPoints(finalPos, 50.0)
                PolylineAnnotation(
                    points = finalCirclePoints + listOf(finalCirclePoints.first())
                ) {
                    lineColor = Color.Green.copy(alpha = 0.8f)
                    lineWidth = 2.0
                }
            }
        }

        // Pin mode toggle at top
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(8.dp)
        ) {
            // Pin mode segmented control
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
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

            Spacer(Modifier.height(4.dp))

            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text(stringResource(R.string.search_address), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.95f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.95f)
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
                                        viewModel.onSearchResultSelected(result)
                                        val updatedState = viewModel.uiState.value
                                        if (updatedState.pinMode == MapConfigPinMode.START) {
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
                                    Text(result.subtitle, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            // Hint text
            if (state.pinMode == MapConfigPinMode.FINAL) {
                Spacer(Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                ) {
                    Text(
                        text = stringResource(R.string.final_zone_hint),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

    }
}
