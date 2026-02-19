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
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.*

@Composable
fun ChickenMapConfigScreen(
    initialRadius: Double,
    onLocationSelected: (com.google.android.gms.maps.model.LatLng, Double) -> Unit,
    viewModel: ChickenMapConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize(initialRadius)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = state.cameraPosition
    }

    // Update marker when camera moves
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val center = cameraPositionState.position.target
            viewModel.onCameraMove(center)
            onLocationSelected(center, state.radius)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = true,
                compassEnabled = true,
                zoomControlsEnabled = false
            )
        ) {
            Marker(
                state = MarkerState(position = state.markerPosition),
                title = "Start"
            )
            Circle(
                center = state.markerPosition,
                radius = state.radius,
                fillColor = Color.Green.copy(alpha = 0.3f),
                strokeColor = Color.Green.copy(alpha = 0.7f),
                strokeWidth = 2f
            )
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
