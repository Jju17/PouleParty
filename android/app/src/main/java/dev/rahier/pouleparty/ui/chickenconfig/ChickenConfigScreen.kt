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
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.components.GameCodeCard
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
                    onLocationSelected = { point, radius ->
                        viewModel.onLocationSelected(point, radius)
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

                // Map setup
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.onMapSetupTapped() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.map_setup))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.Gray
                        )
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
