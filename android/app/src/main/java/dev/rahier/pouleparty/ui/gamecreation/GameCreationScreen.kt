package dev.rahier.pouleparty.ui.gamecreation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.chickenconfig.ChickenMapConfigScreen
import dev.rahier.pouleparty.ui.chickenconfig.PowerUpSelectionScreen
import dev.rahier.pouleparty.ui.components.GameCodeCard
import dev.rahier.pouleparty.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameCreationScreen(
    onStartGame: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: GameCreationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val isForward = state.goingForward

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

    // Handle back press
    BackHandler {
        if (state.canGoBack) {
            viewModel.back()
        } else {
            onDismiss()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = CROrange,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            // Step content
            AnimatedContent(
                targetState = state.currentStep,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (isForward) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    } else {
                        slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                    }
                },
                label = "step_transition"
            ) { step ->
                when (step) {
                    GameCreationStep.PARTICIPATION -> ParticipationStep(
                        isParticipating = state.isParticipating,
                        onSelect = { viewModel.setParticipating(it) }
                    )
                    GameCreationStep.CHICKEN_SELECTION -> ChickenSelectionStep()
                    GameCreationStep.GAME_MODE -> GameModeStep(
                        selectedMode = state.game.gameModEnum,
                        onSelect = { viewModel.updateGameMod(it) }
                    )
                    GameCreationStep.ZONE_SETUP -> ZoneSetupStep(
                        game = state.game,
                        isZoneConfigured = state.isZoneConfigured,
                        onLocationSelected = { viewModel.onLocationSelected(it) },
                        onFinalLocationSelected = { viewModel.onFinalLocationSelected(it) },
                        onRadiusChanged = { viewModel.updateInitialRadius(it) }
                    )
                    GameCreationStep.START_TIME -> StartTimeStep(
                        startDate = state.game.startDate,
                        showDatePicker = state.showDatePicker,
                        showTimePicker = state.showTimePicker,
                        onTapTime = { viewModel.onStartTimeTapped() },
                        onDismissDatePicker = { viewModel.dismissDatePicker() },
                        onDismissTimePicker = { viewModel.dismissTimePicker() },
                        onDateSelected = { y, m, d -> viewModel.updateStartDateOnly(y, m, d) },
                        onTimeSelected = { h, m -> viewModel.updateStartTime(h, m) }
                    )
                    GameCreationStep.DURATION -> DurationStep(
                        gameDurationMinutes = state.gameDurationMinutes,
                        startDate = state.game.startDate,
                        dateFormat = dateFormat,
                        onDurationChanged = { viewModel.updateDuration(it) }
                    )
                    GameCreationStep.HEAD_START -> HeadStartStep(
                        headStartMinutes = state.game.chickenHeadStartMinutes,
                        onHeadStartChanged = { viewModel.updateHeadStart(it) }
                    )
                    GameCreationStep.POWER_UPS -> PowerUpsStep(
                        powerUpsEnabled = state.game.powerUpsEnabled,
                        enabledPowerUpTypes = state.game.enabledPowerUpTypes,
                        gameMod = state.game.gameModEnum,
                        onTogglePowerUps = { viewModel.togglePowerUps(it) },
                        onPowerUpSelectionTapped = { viewModel.onPowerUpSelectionTapped() }
                    )
                    GameCreationStep.CHICKEN_SEES_HUNTERS -> ChickenSeesHuntersStep(
                        chickenCanSeeHunters = state.game.chickenCanSeeHunters,
                        onToggle = { viewModel.toggleChickenCanSeeHunters(it) }
                    )
                    GameCreationStep.REGISTRATION -> RegistrationStep(
                        requiresRegistration = state.game.requiresRegistration,
                        isDepositPlan = state.game.pricingModel == "deposit",
                        onToggle = { viewModel.toggleRequiresRegistration(it) }
                    )
                    GameCreationStep.RECAP -> RecapStep(
                        state = state,
                        dateFormat = dateFormat,
                        onCodeCopied = { viewModel.onCodeCopied() }
                    )
                }
            }

            // Bottom navigation bar
            BottomBar(
                state = state,
                onBack = {
                    if (state.canGoBack) viewModel.back() else onDismiss()
                },
                onNext = { viewModel.next() },
                onStartGame = { viewModel.startGame(onStartGame) }
            )
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

// ── Bottom Bar ──────────────────────────────────────────────────

@Composable
private fun BottomBar(
    state: GameCreationUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onStartGame: () -> Unit
) {
    val isRecap = state.currentStep == GameCreationStep.RECAP
    val isZoneStep = state.currentStep == GameCreationStep.ZONE_SETUP
    val canProceed = if (isZoneStep) state.isZoneConfigured else true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(50.dp),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.back), style = bangerStyle(18))
        }

        // Next / Start button
        if (isRecap) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .then(if (state.isZoneConfigured) Modifier.shadow(4.dp, RoundedCornerShape(50.dp)) else Modifier)
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        if (state.isZoneConfigured) GradientFire
                        else Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
                    )
                    .then(if (state.isZoneConfigured) Modifier.clickable { onStartGame() } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.start_game),
                    color = Color.Black.copy(alpha = if (state.isZoneConfigured) 1f else 0.4f),
                    style = bangerStyle(22)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .then(if (canProceed) Modifier.shadow(4.dp, RoundedCornerShape(50.dp)) else Modifier)
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        if (canProceed) GradientFire
                        else Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
                    )
                    .then(if (canProceed) Modifier.clickable { onNext() } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.next),
                        color = Color.Black.copy(alpha = if (canProceed) 1f else 0.4f),
                        style = bangerStyle(22)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.Black.copy(alpha = if (canProceed) 1f else 0.4f)
                    )
                }
            }
        }
    }
}

// ── Step Composables ────────────────────────────────────────────

@Composable
private fun StepContainer(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = title,
            style = bangerStyle(32),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = gameboyStyle(10),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
        content()
    }
    }
}

@Composable
private fun OptionCard(
    text: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    gradient: Brush? = null,
    subtitle: String? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isSelected) 8.dp else 2.dp, shape)
            .clip(shape)
            .then(
                if (isSelected && gradient != null) {
                    Modifier.background(gradient)
                } else if (isSelected) {
                    Modifier.background(GradientFire)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), shape)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(emoji, fontSize = 36.sp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text,
                    style = bangerStyle(22),
                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = gameboyStyle(7),
                        color = if (isSelected) Color.Black.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ── PARTICIPATION ───────────────────────────────────────────────

@Composable
private fun ParticipationStep(
    isParticipating: Boolean,
    onSelect: (Boolean) -> Unit
) {
    StepContainer(
        title = "Tu participes ?",
        subtitle = "Est-ce que tu joues aussi ?"
    ) {
        OptionCard(
            text = "Je suis la Poule",
            emoji = "\uD83D\uDC14",
            isSelected = isParticipating,
            onClick = { onSelect(true) }
        )
        OptionCard(
            text = "J'organise",
            emoji = "\uD83D\uDCCB",
            isSelected = !isParticipating,
            onClick = { onSelect(false) }
        )
    }
}

// ── CHICKEN SELECTION ───────────────────────────────────────────

@Composable
private fun ChickenSelectionStep() {
    StepContainer(
        title = "Qui sera la Poule ?",
        subtitle = "Comment choisir la poule ?"
    ) {
        OptionCard(
            text = "Au hasard",
            emoji = "\uD83C\uDFB2",
            isSelected = true,
            onClick = { }
        )
        OptionCard(
            text = "Premier arrive",
            emoji = "\uD83C\uDFC3",
            isSelected = false,
            onClick = { }
        )
        OptionCard(
            text = "Je designe",
            emoji = "\uD83D\uDC46",
            isSelected = false,
            onClick = { }
        )
    }
}

// ── GAME MODE ───────────────────────────────────────────────────

@Composable
private fun GameModeStep(
    selectedMode: GameMod,
    onSelect: (GameMod) -> Unit
) {
    StepContainer(
        title = "Quel mode de jeu ?",
        subtitle = "Choisis comment tu veux jouer"
    ) {
        OptionCard(
            text = "Follow the Chicken",
            emoji = "\uD83D\uDC14",
            isSelected = selectedMode == GameMod.FOLLOW_THE_CHICKEN,
            onClick = { onSelect(GameMod.FOLLOW_THE_CHICKEN) },
            gradient = GradientChicken,
            subtitle = "The zone shrinks toward the chicken"
        )

        OptionCard(
            text = "Stay in the Zone",
            emoji = "\uD83D\uDCCD",
            isSelected = selectedMode == GameMod.STAY_IN_THE_ZONE,
            onClick = { onSelect(GameMod.STAY_IN_THE_ZONE) },
            gradient = GradientHunter,
            subtitle = "Fixed zone that shrinks"
        )
    }
}

// ── ZONE SETUP ──────────────────────────────────────────────────

@Composable
private fun ZoneSetupStep(
    game: dev.rahier.pouleparty.model.Game,
    isZoneConfigured: Boolean,
    onLocationSelected: (com.mapbox.geojson.Point) -> Unit,
    onFinalLocationSelected: (com.mapbox.geojson.Point?) -> Unit,
    onRadiusChanged: (Double) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Title area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Configure la zone",
                style = bangerStyle(28),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE)
                    "Place la zone de depart et la zone finale"
                else
                    "Place la zone de depart",
                style = gameboyStyle(9),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            if (!isZoneConfigured) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE)
                        stringResource(R.string.set_start_and_final_zone)
                    else
                        stringResource(R.string.set_start_zone),
                    style = gameboyStyle(8),
                    color = CROrange,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Map fills remaining space
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            ChickenMapConfigScreen(
                initialRadius = game.initialRadius,
                finalMarker = game.finalLocation,
                onLocationSelected = onLocationSelected,
                onFinalLocationSelected = onFinalLocationSelected,
                onRadiusChanged = onRadiusChanged,
                isFollowMode = game.gameModEnum == GameMod.FOLLOW_THE_CHICKEN
            )
        }
    }
}

// ── START TIME ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeStep(
    startDate: Date,
    showDatePicker: Boolean,
    showTimePicker: Boolean,
    onTapTime: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDismissTimePicker: () -> Unit,
    onDateSelected: (year: Int, month: Int, day: Int) -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit
) {
    val displayFormat = remember { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()) }
    StepContainer(
        title = "Quand ?",
        subtitle = "Choisis la date et l'heure de depart"
    ) {
        // Combined date+time display card
        val shape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .shadow(4.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onTapTime() }
                .padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayFormat.format(startDate),
                style = bangerStyle(28),
                color = CROrange,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "Appuie pour changer",
            style = gameboyStyle(9),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }

    // Date picker dialog (shown first)
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.time,
            // Disallow picking dates in the past (from today onwards)
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val todayStart = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    return utcTimeMillis >= todayStart - 24L * 60 * 60 * 1000
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val cal = Calendar.getInstance().apply { timeInMillis = millis }
                        onDateSelected(
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDatePicker) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time picker dialog (shown after date is picked)
    if (showTimePicker) {
        val cal = remember(startDate) { Calendar.getInstance().apply { time = startDate } }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = onDismissTimePicker,
            title = { Text(stringResource(R.string.start_at)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissTimePicker) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ── DURATION ────────────────────────────────────────────────────

@Composable
private fun DurationStep(
    gameDurationMinutes: Double,
    startDate: Date,
    dateFormat: SimpleDateFormat,
    onDurationChanged: (Double) -> Unit
) {
    StepContainer(
        title = "Combien de temps ?",
        subtitle = "Duree de la partie"
    ) {
        val durationOptions = listOf(
            60.0 to "1h",
            90.0 to "1h30",
            120.0 to "2h",
            150.0 to "2h30",
            180.0 to "3h"
        )

        durationOptions.forEach { (minutes, label) ->
            val isSelected = gameDurationMinutes == minutes
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(if (isSelected) 6.dp else 2.dp, shape)
                    .clip(shape)
                    .then(
                        if (isSelected) Modifier.background(GradientFire)
                        else Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), shape)
                    )
                    .clickable { onDurationChanged(minutes) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = bangerStyle(24),
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // End time preview
        val endTime = Date(startDate.time + (gameDurationMinutes * 60 * 1000).toLong())
        Text(
            text = "${stringResource(R.string.ends_at)} ${dateFormat.format(endTime)}",
            style = gameboyStyle(10),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

// ── HEAD START ──────────────────────────────────────────────────

@Composable
private fun HeadStartStep(
    headStartMinutes: Double,
    onHeadStartChanged: (Double) -> Unit
) {
    StepContainer(
        title = "Avance de la poule ?",
        subtitle = "Temps avant que les chasseurs ne commencent"
    ) {
        Text(
            text = "${headStartMinutes.toInt()} min",
            style = bangerStyle(64),
            color = CROrange
        )

        Slider(
            value = headStartMinutes.toFloat(),
            onValueChange = { onHeadStartChanged(it.toDouble()) },
            valueRange = 0f..15f,
            steps = 14,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(thumbColor = CROrange, activeTrackColor = CROrange)
        )

        Text(
            text = if (headStartMinutes.toInt() == 0) "Pas d'avance" else "${headStartMinutes.toInt()} minutes d'avance pour la poule",
            style = gameboyStyle(9),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

// ── POWER UPS ───────────────────────────────────────────────────

@Composable
private fun PowerUpsStep(
    powerUpsEnabled: Boolean,
    enabledPowerUpTypes: List<String>,
    gameMod: GameMod,
    onTogglePowerUps: (Boolean) -> Unit,
    onPowerUpSelectionTapped: () -> Unit
) {
    StepContainer(
        title = "Power-Ups ?",
        subtitle = "Active les bonus en jeu"
    ) {
        // Toggle card
        val shape = RoundedCornerShape(16.dp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.enable_power_ups),
                    style = bangerStyle(20),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Switch(
                    checked = powerUpsEnabled,
                    onCheckedChange = onTogglePowerUps,
                    colors = SwitchDefaults.colors(checkedTrackColor = CROrange)
                )
            }
        }

        if (powerUpsEnabled) {
            val unavailable = if (gameMod == GameMod.STAY_IN_THE_ZONE) {
                setOf(
                    PowerUpType.INVISIBILITY.firestoreValue,
                    PowerUpType.DECOY.firestoreValue,
                    PowerUpType.JAMMER.firestoreValue
                )
            } else emptySet()
            val enabledCount = enabledPowerUpTypes.count { it !in unavailable }
            val totalCount = PowerUpType.entries.count { it.firestoreValue !in unavailable }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPowerUpSelectionTapped() },
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.choose_power_ups),
                        style = gameboyStyle(10),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.power_ups_count_format, enabledCount, totalCount),
                            style = gameboyStyle(10),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

// ── CHICKEN SEES HUNTERS ────────────────────────────────────────

@Composable
private fun ChickenSeesHuntersStep(
    chickenCanSeeHunters: Boolean,
    onToggle: (Boolean) -> Unit
) {
    StepContainer(
        title = "La poule voit les chasseurs ?",
        subtitle = "Can the chicken see where the hunters are?"
    ) {
        OptionCard(
            text = "Oui, elle les voit",
            emoji = "\uD83D\uDC40",
            isSelected = chickenCanSeeHunters,
            onClick = { onToggle(true) }
        )
        OptionCard(
            text = "Non, a l'aveugle",
            emoji = "\uD83D\uDE48",
            isSelected = !chickenCanSeeHunters,
            onClick = { onToggle(false) }
        )
    }
}

@Composable
private fun RegistrationStep(
    requiresRegistration: Boolean,
    isDepositPlan: Boolean,
    onToggle: (Boolean) -> Unit
) {
    StepContainer(
        title = stringResource(R.string.registration),
        subtitle = stringResource(R.string.do_hunters_need_to_register_before_joining)
    ) {
        OptionCard(
            text = stringResource(R.string.registration_required),
            emoji = "\uD83D\uDCDD",
            isSelected = requiresRegistration,
            onClick = { if (!isDepositPlan) onToggle(true) }
        )
        OptionCard(
            text = stringResource(R.string.open_join),
            emoji = "\uD83D\uDEAA",
            isSelected = !requiresRegistration,
            onClick = { if (!isDepositPlan) onToggle(false) }
        )
        if (isDepositPlan) {
            Text(
                text = stringResource(R.string.registration_required_for_paid_deposit_games),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

// ── RECAP ───────────────────────────────────────────────────────

@Composable
private fun RecapStep(
    state: GameCreationUiState,
    dateFormat: SimpleDateFormat,
    onCodeCopied: () -> Unit
) {
    val game = state.game
    val endTime = Date(game.startDate.time + (state.gameDurationMinutes * 60 * 1000).toLong())

    StepContainer(
        title = "Recapitulatif",
        subtitle = "Verifie avant de lancer"
    ) {
        // Game code
        GameCodeCard(
            gameCode = game.gameCode,
            codeCopied = state.codeCopied,
            onCodeCopied = onCodeCopied,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        )

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RecapRow(
                    label = stringResource(R.string.game_mode),
                    value = game.gameModEnum.title
                )
                HorizontalDivider()

                RecapRow(
                    label = stringResource(R.string.start_at),
                    value = dateFormat.format(game.startDate)
                )
                HorizontalDivider()

                RecapRow(
                    label = stringResource(R.string.duration),
                    value = formatDuration(state.gameDurationMinutes)
                )
                HorizontalDivider()

                RecapRow(
                    label = stringResource(R.string.ends_at),
                    value = dateFormat.format(endTime)
                )
                HorizontalDivider()

                RecapRow(
                    label = stringResource(R.string.chicken_head_start),
                    value = "${game.chickenHeadStartMinutes.toInt()} min"
                )
                HorizontalDivider()

                RecapRow(
                    label = stringResource(R.string.zone_radius),
                    value = "${game.initialRadius.toInt()} m"
                )
                HorizontalDivider()

                RecapRow(
                    label = stringResource(R.string.power_ups),
                    value = if (game.powerUpsEnabled) "ON" else "OFF"
                )

                if (game.gameModEnum == GameMod.FOLLOW_THE_CHICKEN) {
                    HorizontalDivider()
                    RecapRow(
                        label = stringResource(R.string.chicken_can_see_hunters),
                        value = if (game.chickenCanSeeHunters) "Oui" else "Non"
                    )
                }

                if (!state.isZoneConfigured) {
                    HorizontalDivider()
                    Text(
                        text = if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE)
                            stringResource(R.string.set_start_and_final_zone)
                        else
                            stringResource(R.string.set_start_zone),
                        style = gameboyStyle(8),
                        color = CROrange,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RecapRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = gameboyStyle(9),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = bangerStyle(18),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun formatDuration(minutes: Double): String {
    val hours = minutes.toInt() / 60
    val mins = minutes.toInt() % 60
    return if (mins == 0) "${hours}h" else "${hours}h${mins}"
}
