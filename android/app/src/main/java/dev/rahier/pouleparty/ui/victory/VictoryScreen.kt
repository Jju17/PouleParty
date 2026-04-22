package dev.rahier.pouleparty.ui.victory

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.Registration
import dev.rahier.pouleparty.ui.components.LeaderboardContent
import dev.rahier.pouleparty.ui.components.SelectionButton
import dev.rahier.pouleparty.ui.theme.*
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun VictoryScreen(
    onGoToMenu: () -> Unit,
    viewModel: VictoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isCurrentUserAWinner = state.game.winners.any { it.hunterId == state.hunterId }
    val showConfetti = isCurrentUserAWinner && !state.isChicken

    val entries = remember(state.game, state.registrations, state.hunterId, state.isChicken) {
        buildLeaderboardEntries(
            game = state.game,
            registrations = state.registrations,
            currentUserId = if (state.isChicken) "" else state.hunterId
        )
    }

    val headerEmoji = if (state.isChicken) "\uD83D\uDC14" else "\uD83C\uDFC6"
    val headerText = when {
        state.isChicken -> stringResource(R.string.game_over)
        isCurrentUserAWinner -> stringResource(R.string.you_found_chicken)
        else -> stringResource(R.string.game_results)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showConfetti) {
            ConfettiOverlay(modifier = Modifier.fillMaxSize())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            Text(text = headerEmoji, fontSize = 60.sp)

            Spacer(Modifier.height(16.dp))

            Text(
                text = headerText,
                style = gameboyStyle(16),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "\uD83E\uDD37", fontSize = 60.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.no_participants),
                        style = bangerStyle(22),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.this_game_ended_without_any_hunters_joining),
                        style = gameboyStyle(9),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LeaderboardContent(
                    entries = entries,
                    hunterStartMs = state.game.hunterStartDate.time,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onReport = { entry -> viewModel.onReportInitiated(entry) }
                )
            }

            Spacer(Modifier.height(16.dp))

            SelectionButton(
                text = stringResource(R.string.back_to_menu),
                color = MaterialTheme.colorScheme.onBackground,
                onClick = onGoToMenu
            )

            Spacer(Modifier.height(32.dp))
        }

        val reportTarget = state.reportTarget
        if (reportTarget != null) {
            AlertDialog(
                onDismissRequest = { viewModel.onReportDismissed() },
                title = { Text(stringResource(R.string.report_player_title)) },
                text = {
                    Text(stringResource(R.string.report_player_message, reportTarget.displayName))
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.onReportConfirmed() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.report_player_submit)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onReportDismissed() }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        when (state.reportResult) {
            ReportResult.SUCCESS -> AlertDialog(
                onDismissRequest = { viewModel.onReportResultDismissed() },
                title = { Text(stringResource(R.string.report_player_success)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.onReportResultDismissed() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
            ReportResult.FAILURE -> AlertDialog(
                onDismissRequest = { viewModel.onReportResultDismissed() },
                title = { Text(stringResource(R.string.error)) },
                text = { Text(stringResource(R.string.report_player_error)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.onReportResultDismissed() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
            null -> Unit
        }
    }
}

// ── Confetti ────────────────────────────────────────────

private data class ConfettiParticle(
    var x: Float,
    var y: Float,
    var speed: Float,
    var wobblePhase: Float,
    var wobbleSpeed: Float,
    var color: Color,
    var size: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var shapeType: Int // 0 = circle, 1 = rect
)

private val confettiColors = listOf(CROrange, CRPink, ChickenYellow, ZoneGreen, PowerupVision, HunterRed)

@Composable
private fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val particles = remember {
        (0 until AppConstants.CONFETTI_PARTICLE_COUNT).map {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -1f,
                speed = Random.nextFloat() * 0.3f + 0.1f,
                wobblePhase = Random.nextFloat() * (Math.PI.toFloat() * 2f),
                wobbleSpeed = Random.nextFloat() * 2f + 1f,
                color = confettiColors.random(),
                size = Random.nextFloat() * 6f + 4f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 80f + 20f,
                shapeType = Random.nextInt(2)
            )
        }.toMutableList()
    }

    var lastFrameTime by remember { mutableLongStateOf(0L) }
    var startTime by remember { mutableLongStateOf(0L) }
    var isActive by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        var done = false
        while (!done) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                if (startTime == 0L) startTime = frameTimeMs
                if (lastFrameTime > 0L) {
                    val dt = (frameTimeMs - lastFrameTime) / 1000f

                    if (isActive && (frameTimeMs - startTime) >= AppConstants.CONFETTI_DURATION_MS) {
                        isActive = false
                    }

                    val iterator = particles.iterator()
                    while (iterator.hasNext()) {
                        val p = iterator.next()
                        p.y += p.speed * dt
                        p.x += sin(p.wobblePhase) * 0.002f
                        p.wobblePhase += p.wobbleSpeed * dt
                        p.rotation += p.rotationSpeed * dt

                        if (p.y > 1.1f) {
                            if (isActive) {
                                p.y = Random.nextFloat() * -0.15f - 0.05f
                                p.x = Random.nextFloat()
                            } else {
                                iterator.remove()
                            }
                        }
                    }
                    if (!isActive && particles.isEmpty()) done = true
                }
                lastFrameTime = frameTimeMs
            }
        }
    }

    Canvas(modifier = modifier) {
        for (p in particles) {
            val px = p.x * size.width
            val py = p.y * size.height

            rotate(degrees = p.rotation, pivot = Offset(px, py)) {
                if (p.shapeType == 0) {
                    drawCircle(
                        color = p.color,
                        radius = p.size / 2f,
                        center = Offset(px, py)
                    )
                } else {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(px - p.size / 2f, py - p.size * 0.75f),
                        size = Size(p.size, p.size * 1.5f)
                    )
                }
            }
        }
    }
}
