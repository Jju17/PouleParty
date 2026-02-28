package dev.rahier.pouleparty.ui.chickenconfig

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.components.copyToClipboard
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
                        copyToClipboard(context, "Game Code", state.game.gameCode)
                        viewModel.onCodeCopied()
                    }) {
                        Icon(
                            imageVector = if (state.codeCopied) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = stringResource(R.string.copy_game_code),
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
                    Text(stringResource(R.string.start_at))
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
                    Text(stringResource(R.string.end_at))
                    Text(dateFormat.format(state.game.endDate))
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
                }
            }

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

            Spacer(Modifier.weight(1f))

            // Start game button
            Button(
                onClick = { viewModel.startGame(onStartGame) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CROrange)
            ) {
                Text(stringResource(R.string.start_game), color = Color.White, style = bangerStyle(20))
            }
        }
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
