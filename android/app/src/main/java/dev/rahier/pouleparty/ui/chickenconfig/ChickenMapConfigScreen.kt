package dev.rahier.pouleparty.ui.chickenconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.mapbox.maps.plugin.locationcomponent.location
import dev.rahier.pouleparty.ui.components.circlePolygonPoints

@OptIn(MapboxExperimental::class)
@Composable
fun ChickenMapConfigScreen(
    initialRadius: Double,
    onLocationSelected: (Point, Double) -> Unit,
    viewModel: ChickenMapConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize(initialRadius)
    }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(state.cameraCenter)
            zoom(state.cameraZoom.toDouble())
        }
    }

    // Fly camera when cameraCenter changes (e.g. after getting user location)
    LaunchedEffect(state.cameraCenter) {
        mapViewportState.flyTo(
            cameraOptions = CameraOptions.Builder()
                .center(state.cameraCenter)
                .zoom(state.cameraZoom.toDouble())
                .build()
        )
    }

    // Track camera idle to update marker position
    var lastCameraCenter by remember { mutableStateOf(state.cameraCenter) }
    LaunchedEffect(Unit) {
        snapshotFlow { mapViewportState.cameraState }
            .collect { cameraState ->
                val center = cameraState?.center ?: return@collect
                if (center.longitude() != lastCameraCenter.longitude() || center.latitude() != lastCameraCenter.latitude()) {
                    lastCameraCenter = center
                    viewModel.onCameraMove(center)
                    onLocationSelected(center, state.radius)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

            // Circle showing radius
            val circlePoints = circlePolygonPoints(state.markerPosition, state.radius)
            PolylineAnnotation(
                points = circlePoints + listOf(circlePoints.first())
            ) {
                lineColor = Color(0f, 1f, 0f, 0.7f)
                lineWidth = 2.0
            }

            // Center marker
            PointAnnotation(
                point = state.markerPosition
            ) {
                iconImage = IconImage("marker-15")
                textField = "Start"
            }
        }

        // Bottom slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .background(Color.White.copy(alpha = 0.9f))
                .padding(16.dp)
        ) {
            Text("Radius: ${state.radius.toInt()}")
            Slider(
                value = state.radius.toFloat(),
                onValueChange = {
                    viewModel.onRadiusChanged(it.toDouble())
                    onLocationSelected(state.markerPosition, it.toDouble())
                },
                valueRange = 500f..2000f,
                steps = 14
            )
        }
    }
}
