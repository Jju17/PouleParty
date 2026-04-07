package dev.rahier.pouleparty.ui.home

import android.media.MediaPlayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.rules.GameRulesScreen
import dev.rahier.pouleparty.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToPlanSelection: () -> Unit,
    onNavigateToChickenMap: (String) -> Unit,
    onNavigateToHunterMap: (String, String) -> Unit,
    onNavigateToVictory: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Music playback
    val mediaPlayer = remember {
        MediaPlayer.create(context, R.raw.background_music).apply {
            isLooping = true
            setVolume(0.1f, 0.1f)
        }
    }

    DisposableEffect(Unit) {
        if (!state.isMusicMuted) mediaPlayer.start()
        onDispose { mediaPlayer.release() }
    }

    LaunchedEffect(state.isMusicMuted) {
        if (state.isMusicMuted) {
            if (mediaPlayer.isPlaying) mediaPlayer.pause()
        } else {
            if (!mediaPlayer.isPlaying) mediaPlayer.start()
        }
    }

    var isPendingBannerCollapsed by remember { mutableStateOf(false) }

    // Mute button bounce animation
    var musicButtonScale by remember { mutableFloatStateOf(1f) }
    val animatedScale by animateFloatAsState(
        targetValue = musicButtonScale,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "musicScale"
    )

    LaunchedEffect(musicButtonScale) {
        if (musicButtonScale > 1f) {
            kotlinx.coroutines.delay(150)
            musicButtonScale = 1f
        }
    }

    // Re-check for active game when screen is resumed (e.g. back navigation)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshActiveGame()
    }

    // Blinking animation for START text
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Center content (lifted up to clear the pending registration banner)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-60).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.chicken),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(150.dp)
            )
            Text(
                "POULE PARTY",
                fontFamily = GameBoyFont,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            TextButton(
                onClick = { viewModel.onStartButtonTapped() },
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp)
                    .alpha(alpha)
                    .border(4.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(12.dp))
            ) {
                Text(
                    stringResource(R.string.start),
                    fontFamily = GameBoyFont,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                stringResource(R.string.press_start),
                fontFamily = GameBoyFont,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Top bar: Mute (left) + Settings (right)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    viewModel.toggleMusicMuted()
                    musicButtonScale = 1.3f
                },
                modifier = Modifier.scale(animatedScale)
            ) {
                Icon(
                    imageVector = if (state.isMusicMuted) Icons.AutoMirrored.Filled.VolumeOff
                    else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (state.isMusicMuted) "Unmute music" else "Mute music",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_preferences),
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Bottom section: rejoin banner + I am la poule
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rejoin banner
            if (state.activeGame != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .background(GradientFire, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.rejoin_game_in_progress),
                            fontFamily = GameBoyFont,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            state.activeGame!!.gameCode,
                            fontFamily = GameBoyFont,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        TextButton(
                            onClick = {
                                viewModel.rejoinGame(
                                    onRejoinAsChicken = { gameId -> onNavigateToChickenMap(gameId) },
                                    onRejoinAsHunter = { gameId, hunterName -> onNavigateToHunterMap(gameId, hunterName) }
                                )
                            },
                            modifier = Modifier
                                .border(3.dp, Color.White, RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                stringResource(R.string.rejoin),
                                fontFamily = GameBoyFont,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Close button
                    IconButton(
                        onClick = { viewModel.dismissActiveGame() },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            "✕",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (state.pendingRegistration != null) {
                PendingRegistrationBannerSlot(
                    pending = state.pendingRegistration!!,
                    isCollapsed = isPendingBannerCollapsed,
                    onCollapse = { isPendingBannerCollapsed = true },
                    onExpand = { isPendingBannerCollapsed = false },
                    onJoin = {
                        viewModel.onPendingRegistrationJoinTapped(
                            onGameFound = { gameId, hunterName -> onNavigateToHunterMap(gameId, hunterName) },
                            onGameDone = { gameId -> onNavigateToVictory(gameId) }
                        )
                    }
                )
            }

            // Bottom row: Rules (left) + I am la poule (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { viewModel.onRulesTapped() },
                    modifier = Modifier
                        .padding(16.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        stringResource(R.string.rules),
                        fontFamily = GameBoyFont,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                TextButton(
                    onClick = {
                        if (viewModel.onCreatePartyTapped()) {
                            onNavigateToPlanSelection()
                        }
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        "Create Party",
                        fontFamily = GameBoyFont,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }

    // ── Dialogs ──

    // Join game bottom sheet
    if (state.isShowingJoinSheet) {
        JoinFlowBottomSheet(
            state = state,
            onDismiss = { viewModel.onJoinSheetDismissed() },
            onCodeChanged = { viewModel.onGameCodeChanged(it) },
            onTeamNameChanged = { viewModel.onTeamNameChanged(it) },
            onJoinTapped = {
                viewModel.onJoinTapped(
                    onGameFound = { gameId, hunterName -> onNavigateToHunterMap(gameId, hunterName) },
                    onGameDone = { gameId -> onNavigateToVictory(gameId) }
                )
            },
            onRegisterTapped = { viewModel.onRegisterTapped() },
            onSubmitRegistrationTapped = { viewModel.onSubmitRegistrationTapped() }
        )
    }

    // Game not found alert
    if (state.isShowingGameNotFound) {
        AlertDialog(
            onDismissRequest = { viewModel.onGameNotFoundDismissed() },
            title = { Text(stringResource(R.string.game_not_found)) },
            text = { Text(stringResource(R.string.game_not_found_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onGameNotFoundDismissed() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // Location required alert
    if (state.isShowingLocationRequired) {
        AlertDialog(
            onDismissRequest = { viewModel.onLocationRequiredDismissed() },
            title = { Text(stringResource(R.string.location_required)) },
            text = { Text(stringResource(R.string.location_required_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onLocationRequiredDismissed() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // Game Rules bottom sheet
    if (state.isShowingGameRules) {
        GameRulesDialog(onDismiss = { viewModel.onRulesDismissed() })
    }
}

@Composable
private fun PendingRegistrationBannerSlot(
    pending: PendingRegistration,
    isCollapsed: Boolean,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onJoin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 190.dp),
        contentAlignment = if (isCollapsed) Alignment.TopEnd else Alignment.Center
    ) {
        if (isCollapsed) {
            // Collapsed tab on right edge, top-aligned
            TextButton(
                onClick = onExpand,
                modifier = Modifier
                    .background(
                        GradientFire,
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.expand_registered_game_banner),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(GradientFire, RoundedCornerShape(16.dp))
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.registered_to_game),
                        fontFamily = GameBoyFont,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Text(
                        pending.gameCode,
                        fontFamily = GameBoyFont,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Text(
                        pending.teamName,
                        fontFamily = GameBoyFont,
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        relativeStartingIn(pending.startMs),
                        fontFamily = GameBoyFont,
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    TextButton(
                        onClick = onJoin,
                        modifier = Modifier
                            .border(3.dp, Color.White, RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            stringResource(R.string.join),
                            fontFamily = GameBoyFont,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = stringResource(R.string.collapse),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun relativeStartingIn(startMs: Long): String {
    val diffMs = startMs - System.currentTimeMillis()
    if (diffMs <= 0) return "Starting now"
    val totalMinutes = diffMs / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "Starting in ${hours}h ${minutes}min"
        minutes > 0 -> "Starting in ${minutes}min"
        else -> "Starting in <1min"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinFlowBottomSheet(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onTeamNameChanged: (String) -> Unit,
    onJoinTapped: () -> Unit,
    onRegisterTapped: () -> Unit,
    onSubmitRegistrationTapped: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        val step = state.joinStep
        when (step) {
            is JoinFlowStep.Registering, is JoinFlowStep.SubmittingRegistration -> {
                val game = (step as? JoinFlowStep.Registering)?.game
                    ?: (step as JoinFlowStep.SubmittingRegistration).game
                val isSubmitting = step is JoinFlowStep.SubmittingRegistration
                RegisterFormContent(
                    game = game,
                    teamName = state.teamName,
                    isTeamNameValid = state.isTeamNameValid,
                    isSubmitting = isSubmitting,
                    onTeamNameChanged = onTeamNameChanged,
                    onSubmit = onSubmitRegistrationTapped
                )
            }
            else -> {
                CodeEntryContent(
                    state = state,
                    onCodeChanged = onCodeChanged,
                    onJoinTapped = onJoinTapped,
                    onRegisterTapped = onRegisterTapped
                )
            }
        }
    }
}

@Composable
private fun CodeEntryContent(
    state: HomeUiState,
    onCodeChanged: (String) -> Unit,
    onJoinTapped: () -> Unit,
    onRegisterTapped: () -> Unit
) {
    val step = state.joinStep
    val needsRegister: Boolean = (step as? JoinFlowStep.CodeValidated)
        ?.let { it.game.requiresRegistration && !it.alreadyRegistered } ?: false
    val isEnabled = step is JoinFlowStep.CodeValidated
    val buttonLabel = if (needsRegister) stringResource(R.string.register) else stringResource(R.string.join)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.join_game),
            fontFamily = GameBoyFont,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(R.string.enter_the_game_code),
            fontFamily = GameBoyFont,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        OutlinedTextField(
            value = state.gameCode,
            onValueChange = onCodeChanged,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier
                .fillMaxWidth(0.7f),
            placeholder = { Text("ABC123") }
        )
        when (step) {
            is JoinFlowStep.Validating -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is JoinFlowStep.CodeNotFound -> {
                Text(
                    stringResource(R.string.no_game_found_with_this_code),
                    fontFamily = GameBoyFont,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            is JoinFlowStep.NetworkError -> {
                Text(
                    stringResource(R.string.network_error_please_try_again),
                    fontFamily = GameBoyFont,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> Spacer(Modifier.height(1.dp))
        }
        TextButton(
            onClick = {
                if (needsRegister) onRegisterTapped() else onJoinTapped()
            },
            enabled = isEnabled,
            modifier = Modifier
                .background(
                    if (isEnabled) GradientFire else androidx.compose.ui.graphics.SolidColor(Color.Gray.copy(alpha = 0.3f)),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            Text(
                buttonLabel,
                fontFamily = GameBoyFont,
                fontSize = 18.sp,
                color = Color.Black.copy(alpha = if (isEnabled) 1f else 0.4f)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RegisterFormContent(
    game: dev.rahier.pouleparty.model.Game,
    teamName: String,
    isTeamNameValid: Boolean,
    isSubmitting: Boolean,
    onTeamNameChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val isDeposit = game.pricingModel == "deposit"
    val buttonLabel = if (isDeposit) stringResource(R.string.pay) else stringResource(R.string.sign_up)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.register),
            fontFamily = GameBoyFont,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Game ${game.gameCode}",
            fontFamily = GameBoyFont,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = teamName,
            onValueChange = onTeamNameChanged,
            label = { Text(stringResource(R.string.team_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (isDeposit) {
            Text(
                stringResource(R.string.hunter_payment_will_be_available_soon),
                fontFamily = GameBoyFont,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onSubmit,
            enabled = isTeamNameValid && !isSubmitting,
            modifier = Modifier
                .background(
                    if (isTeamNameValid && !isSubmitting) GradientFire else androidx.compose.ui.graphics.SolidColor(Color.Gray.copy(alpha = 0.3f)),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    buttonLabel,
                    fontFamily = GameBoyFont,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GameRulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.rules), style = bangerStyle(28), color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        text = { GameRulesScreen() },
        confirmButton = {},
        containerColor = MaterialTheme.colorScheme.background
    )
}
