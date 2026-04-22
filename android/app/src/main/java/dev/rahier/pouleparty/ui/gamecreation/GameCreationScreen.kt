package dev.rahier.pouleparty.ui.gamecreation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import dev.rahier.pouleparty.powerups.selection.PowerUpSelectionScreen
import dev.rahier.pouleparty.ui.gamecreation.steps.ChickenSeesHuntersStep
import dev.rahier.pouleparty.ui.gamecreation.steps.ChickenSelectionStep
import dev.rahier.pouleparty.ui.gamecreation.steps.DurationStep
import dev.rahier.pouleparty.ui.gamecreation.steps.GameModeStep
import dev.rahier.pouleparty.ui.gamecreation.steps.HeadStartStep
import dev.rahier.pouleparty.ui.gamecreation.steps.ParticipationStep
import dev.rahier.pouleparty.ui.gamecreation.steps.PowerUpsStep
import dev.rahier.pouleparty.ui.gamecreation.steps.RecapStep
import dev.rahier.pouleparty.ui.gamecreation.steps.RegistrationStep
import dev.rahier.pouleparty.ui.gamecreation.steps.StartTimeStep
import dev.rahier.pouleparty.ui.gamecreation.steps.ZoneSetupStep
import dev.rahier.pouleparty.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameCreationScreen(
    onStartGame: (String) -> Unit,
    onDismiss: () -> Unit,
    onPaidGameCreated: (gameId: String) -> Unit = { onDismiss() },
    viewModel: GameCreationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // One-shot navigation effects from the ViewModel.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GameCreationEffect.GameStarted -> onStartGame(effect.gameId)
                // Forfait: the server created the game in `pending_payment`; we
                // route to the payment-confirmation screen so the creator gets
                // immediate visual feedback + share affordance. The webhook
                // flips status to `waiting` in the background while the user
                // reads the confirmation.
                is GameCreationEffect.PaidGameCreated -> onPaidGameCreated(effect.gameId)
            }
        }
    }

    val isForward = state.goingForward

    // Forfait creator payment overlay
    state.paymentContext?.let { ctx ->
        BackHandler { viewModel.onPaymentCancelled() }
        dev.rahier.pouleparty.ui.payment.PaymentScreen(
            context = ctx,
            onCreatorPaid = { gameId -> viewModel.onCreatorPaymentCompleted(gameId) },
            onHunterPaid = { /* not used from creator flow */ },
            onCancelled = { viewModel.onPaymentCancelled() },
        )
        return
    }

    // Power-up selection overlay
    if (state.showPowerUpSelection) {
        BackHandler { viewModel.onIntent(GameCreationIntent.DismissPowerUpSelection) }
        PowerUpSelectionScreen(
            enabledTypes = state.game.powerUps.enabledTypes,
            gameMod = state.game.gameModEnum,
            onToggle = { viewModel.onIntent(GameCreationIntent.PowerUpTypeToggled(it)) },
            onDismiss = { viewModel.onIntent(GameCreationIntent.DismissPowerUpSelection) }
        )
        return
    }

    // Handle back press
    BackHandler {
        if (state.canGoBack) {
            viewModel.onIntent(GameCreationIntent.Back)
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
                drawStopIndicator = {},
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
                        onSelect = { viewModel.onIntent(GameCreationIntent.ParticipatingChanged(it)) }
                    )
                    GameCreationStep.CHICKEN_SELECTION -> ChickenSelectionStep()
                    GameCreationStep.GAME_MODE -> GameModeStep(
                        selectedMode = state.game.gameModEnum,
                        onSelect = { viewModel.onIntent(GameCreationIntent.GameModeChanged(it)) }
                    )
                    GameCreationStep.ZONE_SETUP -> ZoneSetupStep(
                        game = state.game,
                        isZoneConfigured = state.isZoneConfigured,
                        onLocationSelected = { viewModel.onIntent(GameCreationIntent.LocationSelected(it)) },
                        onFinalLocationSelected = { viewModel.onIntent(GameCreationIntent.FinalLocationSelected(it)) },
                        onRadiusChanged = { viewModel.onIntent(GameCreationIntent.InitialRadiusChanged(it)) }
                    )
                    GameCreationStep.START_TIME -> StartTimeStep(
                        startDate = state.game.startDate,
                        showDatePicker = state.showDatePicker,
                        showTimePicker = state.showTimePicker,
                        onTapTime = { viewModel.onIntent(GameCreationIntent.StartTimeTapped) },
                        onDismissDatePicker = { viewModel.onIntent(GameCreationIntent.DismissDatePicker) },
                        onDismissTimePicker = { viewModel.onIntent(GameCreationIntent.DismissTimePicker) },
                        onDateSelected = { y, m, d -> viewModel.onIntent(GameCreationIntent.StartDateChanged(y, m, d)) },
                        onTimeSelected = { h, m -> viewModel.onIntent(GameCreationIntent.StartTimeChanged(h, m)) }
                    )
                    GameCreationStep.DURATION -> DurationStep(
                        gameDurationMinutes = state.gameDurationMinutes,
                        startDate = state.game.startDate,
                        dateFormat = dateFormat,
                        onDurationChanged = { viewModel.onIntent(GameCreationIntent.DurationChanged(it)) }
                    )
                    GameCreationStep.HEAD_START -> HeadStartStep(
                        headStartMinutes = state.game.timing.headStartMinutes,
                        onHeadStartChanged = { viewModel.onIntent(GameCreationIntent.HeadStartChanged(it)) }
                    )
                    GameCreationStep.POWER_UPS -> PowerUpsStep(
                        powerUpsEnabled = state.game.powerUps.enabled,
                        enabledPowerUpTypes = state.game.powerUps.enabledTypes,
                        gameMod = state.game.gameModEnum,
                        onTogglePowerUps = { viewModel.onIntent(GameCreationIntent.PowerUpsToggled(it)) },
                        onPowerUpSelectionTapped = { viewModel.onIntent(GameCreationIntent.PowerUpSelectionTapped) }
                    )
                    GameCreationStep.CHICKEN_SEES_HUNTERS -> ChickenSeesHuntersStep(
                        chickenCanSeeHunters = state.game.chickenCanSeeHunters,
                        onToggle = { viewModel.onIntent(GameCreationIntent.ChickenCanSeeHuntersToggled(it)) }
                    )
                    GameCreationStep.REGISTRATION -> RegistrationStep(
                        requiresRegistration = state.game.registration.required,
                        isDepositPlan = state.game.pricing.model == "deposit",
                        registrationClosesBeforeStartMinutes = state.game.registration.closesMinutesBefore,
                        onToggle = { viewModel.onIntent(GameCreationIntent.RequiresRegistrationToggled(it)) },
                        onDeadlineChanged = { viewModel.onIntent(GameCreationIntent.RegistrationClosesBeforeStartChanged(it)) }
                    )
                    GameCreationStep.RECAP -> RecapStep(
                        state = state,
                        dateFormat = dateFormat,
                        onCodeCopied = { viewModel.onIntent(GameCreationIntent.CodeCopied) }
                    )
                }
            }

            // Bottom navigation bar
            BottomBar(
                state = state,
                onBack = {
                    if (state.canGoBack) viewModel.onIntent(GameCreationIntent.Back) else onDismiss()
                },
                onNext = { viewModel.onIntent(GameCreationIntent.Next) },
                onStartGame = { viewModel.onIntent(GameCreationIntent.StartGameTapped) }
            )
        }
    }

    // Error alert
    if (state.showAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(GameCreationIntent.DismissAlert) },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(state.alertMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(GameCreationIntent.DismissAlert) }) { Text(stringResource(R.string.ok)) }
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
