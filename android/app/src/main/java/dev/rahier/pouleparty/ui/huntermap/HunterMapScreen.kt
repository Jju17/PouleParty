package dev.rahier.pouleparty.ui.huntermap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.maps.android.compose.*
import dev.rahier.pouleparty.ui.components.CountdownView

@Composable
fun HunterMapScreen(
    viewModel: HunterMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        val cameraPositionState = rememberCameraPositionState()

        // Center camera on circle when it updates
        LaunchedEffect(state.circleCenter) {
            state.circleCenter?.let { center ->
                cameraPositionState.position =
                    com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(center, 14f)
            }
        }

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
            // Zone circle
            state.circleCenter?.let { center ->
                Circle(
                    center = center,
                    radius = state.radius.toDouble(),
                    fillColor = Color.Green.copy(alpha = 0.3f),
                    strokeColor = Color.Green.copy(alpha = 0.7f),
                    strokeWidth = 2f
                )
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.White.copy(alpha = 0.9f))
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You are the Hunter", fontSize = 16.sp)
                Text(viewModel.hunterSubtitle, fontSize = 12.sp)
            }
        }

        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.White.copy(alpha = 0.9f))
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Radius : ${state.radius}m")
                CountdownView(
                    nowDate = state.nowDate,
                    nextUpdateDate = state.nextRadiusUpdate
                )
            }
        }
    }
}
