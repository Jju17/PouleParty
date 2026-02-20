package dev.rahier.pouleparty.ui.chickenmap

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.maps.android.compose.*
import dev.rahier.pouleparty.ui.components.CountdownView
import dev.rahier.pouleparty.ui.theme.GameBoyFont
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChickenMapScreen(
    onGoToMenu: () -> Unit,
    viewModel: ChickenMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Google Map
        val cameraPositionState = rememberCameraPositionState()

        // Center camera on circle when it updates
        LaunchedEffect(state.circleCenter) {
            state.circleCenter?.let { center ->
                cameraPositionState.position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(center, 14f)
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

            // Hunter annotations (mutualTracking mode)
            state.hunterAnnotations.forEach { hunter ->
                Marker(
                    state = MarkerState(position = hunter.coordinate),
                    title = hunter.displayName,
                    snippet = "Hunter"
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
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(40.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("You are the \uD83D\uDC14", fontSize = 16.sp)
                Text(viewModel.chickenSubtitle, fontSize = 14.sp)
            }
            IconButton(onClick = { viewModel.onInfoTapped() }) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Game Info",
                    tint = Color.Gray
                )
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // FOUND button
                Button(
                    onClick = { /* EndGameCode — stub */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier.size(width = 50.dp, height = 40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("FOUND", fontSize = 11.sp, color = Color.White)
                }

                // Stop button
                Button(
                    onClick = { viewModel.onCancelGameTapped() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier.size(width = 50.dp, height = 40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("⏹", fontSize = 20.sp, color = Color.White)
                }
            }
        }
    }

    // Cancel alert
    if (state.showCancelAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCancelAlert() },
            title = { Text("Cancel game") },
            text = { Text("Are you sure you want to cancel and finish the game now?") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCancelGame(onGoToMenu) }) {
                    Text("Cancel game", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCancelAlert() }) {
                    Text("Never mind")
                }
            }
        )
    }

    // Game over alert
    if (state.showGameOverAlert) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Game Over") },
            text = { Text(state.gameOverMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmGameOver(onGoToMenu) }) {
                    Text("OK")
                }
            }
        )
    }

    // Game info dialog
    if (state.showGameInfo) {
        val context = LocalContext.current
        val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissGameInfo() },
            title = { Text("Game Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Game code (copiable)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                state.game.gameCode,
                                fontFamily = GameBoyFont,
                                fontSize = 24.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Game Code", state.game.gameCode))
                                viewModel.onCodeCopied()
                            }) {
                                Icon(
                                    imageVector = if (state.codeCopied) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = "Copy",
                                    tint = if (state.codeCopied) Color(0xFF4CAF50) else Color.Gray
                                )
                            }
                        }
                    }

                    // Game mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mode")
                        Text(state.game.gameModEnum.title, color = Color.Gray)
                    }

                    // Start time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Start")
                        Text(dateFormat.format(state.game.startDate), color = Color.Gray)
                    }

                    // End time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("End")
                        Text(dateFormat.format(state.game.endDate), color = Color.Gray)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissGameInfo() }) {
                    Text("OK")
                }
            }
        )
    }
}
