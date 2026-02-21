package dev.rahier.pouleparty.ui.chickenconfig

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.model.GameMod
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
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Game Code section
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

            // Start time
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Start at")
                    Text(dateFormat.format(state.game.startDate))
                }
            }

            // End time
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("End at")
                    Text(dateFormat.format(state.game.endDate))
                }
            }

            // Game Mode picker
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Game Mode", style = MaterialTheme.typography.labelLarge)
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
                }
            }

            // Radius interval update slider
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Radius interval update")
                        Text("${state.game.radiusIntervalUpdate.toInt()} minutes")
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
                        Text("Radius decline")
                        Text("${state.game.radiusDeclinePerUpdate.toInt()} meters")
                    }
                    Slider(
                        value = state.game.radiusDeclinePerUpdate.toFloat(),
                        onValueChange = { viewModel.updateRadiusDecline(it.toDouble()) },
                        valueRange = 50f..1000f,
                        steps = 94
                    )
                }
            }

            // Chicken head start slider
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Chicken head start")
                        Text("${state.game.chickenHeadStartMinutes.toInt()} minutes")
                    }
                    Slider(
                        value = state.game.chickenHeadStartMinutes.toFloat(),
                        onValueChange = { viewModel.updateChickenHeadStart(it.toDouble()) },
                        valueRange = 0f..45f,
                        steps = 44
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Start game button
            Button(
                onClick = { viewModel.startGame(onStartGame) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CROrange)
            ) {
                Text("Start game", color = Color.White, style = bangerStyle(20))
            }
        }
    }

    // Error alert
    if (state.showAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAlert() },
            title = { Text("Error") },
            text = { Text(state.alertMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAlert() }) { Text("OK") }
            }
        )
    }
}
