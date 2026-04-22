package dev.rahier.pouleparty.ui.home

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import dev.rahier.pouleparty.ui.home.components.GameRulesOverlay
import dev.rahier.pouleparty.ui.home.components.JoinFlowBottomSheet
import dev.rahier.pouleparty.ui.home.components.PendingRegistrationBannerSlot
import dev.rahier.pouleparty.ui.theme.*

private enum class PendingPermissionAction { None, Start, CreateParty }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlanSelection: () -> Unit,
    onNavigateToGameCreation: (gameId: String, pricingModel: String, numberOfPlayers: Int, pricePerPlayerCents: Int, depositAmountCents: Int) -> Unit,
    onNavigateToChickenMap: (String) -> Unit,
    onNavigateToHunterMap: (String, String) -> Unit,
    onNavigateToVictory: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPaymentConfirmed: (gameId: String, kind: String) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // One-shot navigation effects from the ViewModel.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToChickenMap -> onNavigateToChickenMap(effect.gameId)
                is HomeEffect.NavigateToHunterMap -> onNavigateToHunterMap(effect.gameId, effect.hunterName)
                is HomeEffect.NavigateToGameDone -> onNavigateToVictory(effect.gameId)
                is HomeEffect.NavigateToPaymentConfirmed -> onNavigateToPaymentConfirmed(effect.gameId, effect.kind)
            }
        }
    }

    // Caution hunter payment overlay
    state.paymentContext?.let { ctx ->
        androidx.activity.compose.BackHandler { viewModel.onPaymentCancelled() }
        dev.rahier.pouleparty.ui.payment.PaymentScreen(
            context = ctx,
            onCreatorPaid = { /* not used from hunter flow */ },
            onHunterPaid = { viewModel.onHunterPaymentCompleted() },
            onCancelled = { viewModel.onPaymentCancelled() },
        )
        return
    }

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
    var isShowingPlanSelection by remember { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf<PendingPermissionAction>(PendingPermissionAction.None) }

    // Location permission launcher — triggered when Start or Create Party is tapped without
    // permission. If user grants in the system dialog, we re-attempt the original action.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = PendingPermissionAction.None
        if (granted) {
            when (action) {
                PendingPermissionAction.Start -> viewModel.onIntent(HomeIntent.StartButtonTapped)
                PendingPermissionAction.CreateParty -> {
                    isShowingPlanSelection = true
                }
                PendingPermissionAction.None -> {}
            }
        } else {
            viewModel.onIntent(HomeIntent.LocationPermissionDenied)
        }
    }

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
        viewModel.onIntent(HomeIntent.RefreshActiveGame)
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
                onClick = {
                    if (viewModel.hasLocationPermission()) {
                        viewModel.onIntent(HomeIntent.StartButtonTapped)
                    } else {
                        pendingPermissionAction = PendingPermissionAction.Start
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
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
                    viewModel.onIntent(HomeIntent.ToggleMusic)
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
            val activeGame = state.activeGame
            if (activeGame != null) {
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
                            activeGame.gameCode,
                            fontFamily = GameBoyFont,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        TextButton(
                            onClick = { viewModel.onIntent(HomeIntent.RejoinActiveGameTapped) },
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
                        onClick = { viewModel.onIntent(HomeIntent.ActiveGameDismissed) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            "✕",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                val pending = state.pendingRegistration
                if (pending != null) {
                PendingRegistrationBannerSlot(
                    pending = pending,
                    isCollapsed = isPendingBannerCollapsed,
                    onCollapse = { isPendingBannerCollapsed = true },
                    onExpand = { isPendingBannerCollapsed = false },
                    onJoin = { viewModel.onIntent(HomeIntent.PendingRegistrationJoinTapped) }
                )
                }
            }

            // Bottom row: Rules (left) + I am la poule (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { viewModel.onIntent(HomeIntent.RulesTapped) },
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
                        if (viewModel.hasLocationPermission()) {
                            isShowingPlanSelection = true
                        } else {
                            pendingPermissionAction = PendingPermissionAction.CreateParty
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
            onDismiss = { viewModel.onIntent(HomeIntent.JoinSheetDismissed) },
            onCodeChanged = { viewModel.onIntent(HomeIntent.GameCodeChanged(it)) },
            onTeamNameChanged = { viewModel.onIntent(HomeIntent.TeamNameChanged(it)) },
            onJoinTapped = { viewModel.onIntent(HomeIntent.JoinValidatedGameTapped) },
            onRegisterTapped = { viewModel.onIntent(HomeIntent.RegisterTapped) },
            onSubmitRegistrationTapped = { viewModel.onIntent(HomeIntent.SubmitRegistrationTapped) }
        )
    }

    // Game not found alert
    if (state.isShowingGameNotFound) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(HomeIntent.GameNotFoundDismissed) },
            title = { Text(stringResource(R.string.game_not_found)) },
            text = { Text(stringResource(R.string.game_not_found_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(HomeIntent.GameNotFoundDismissed) }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // Location required alert
    if (state.isShowingLocationRequired) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(HomeIntent.LocationRequiredDismissed) },
            title = { Text(stringResource(R.string.location_required)) },
            text = { Text(stringResource(R.string.location_required_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(HomeIntent.LocationRequiredDismissed)
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(HomeIntent.LocationRequiredDismissed) }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // Game Rules fullscreen overlay
    if (state.isShowingGameRules) {
        GameRulesOverlay(onDismiss = { viewModel.onIntent(HomeIntent.RulesDismissed) })
    }

    // Plan selection bottom sheet
    if (isShowingPlanSelection) {
        val planSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { isShowingPlanSelection = false },
            sheetState = planSheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxHeight(0.65f)) {
            dev.rahier.pouleparty.ui.planselection.PlanSelectionScreen(
                onPlanSelected = { params ->
                    isShowingPlanSelection = false
                    // Firestore-style auto-ID (20-char alphanumeric) so free-game
                    // IDs match the Cloud Function format used for Forfait games.
                    val gameId = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("games")
                        .document()
                        .id
                    onNavigateToGameCreation(
                        gameId,
                        params.pricingModel,
                        params.numberOfPlayers,
                        params.pricePerPlayerCents,
                        params.depositAmountCents
                    )
                },
                onBack = { isShowingPlanSelection = false }
            )
            }
        }
    }
}

