package dev.rahier.pouleparty.ui.victory

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.model.Winner
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
    val isSpectator = state.hunterId.isEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CRBeige)
    ) {
        // Confetti overlay (only for participants)
        if (!isSpectator) {
            ConfettiOverlay(
                modifier = Modifier.fillMaxSize()
            )
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            Text(
                text = "\uD83C\uDFC6",
                fontSize = 60.sp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (isSpectator) "Game Results" else "You found\nthe chicken!",
                style = gameboyStyle(16),
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // Leaderboard
            LeaderboardSection(
                game = state.game,
                hunterId = state.hunterId,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.height(16.dp))

            // Back to menu button
            SelectionButton(
                text = "BACK TO MENU",
                color = Color.Black,
                onClick = onGoToMenu
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LeaderboardSection(
    game: dev.rahier.pouleparty.model.Game,
    hunterId: String,
    modifier: Modifier = Modifier
) {
    val sortedWinners = remember(game.winners) {
        game.winners.sortedBy { it.timestamp.toDate().time }
    }
    val remaining = maxOf(0, game.hunterIds.size - game.winners.size)

    LazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(sortedWinners, key = { _, w -> w.hunterId }) { index, winner ->
            LeaderboardRow(
                rank = index + 1,
                winner = winner,
                isCurrentHunter = winner.hunterId == hunterId,
                gameStartDate = game.hunterStartDate
            )
        }

        if (remaining > 0) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                )
                Text(
                    text = "$remaining still in the party \uD83D\uDD0D",
                    style = bangerStyle(18),
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    winner: Winner,
    isCurrentHunter: Boolean,
    gameStartDate: java.util.Date
) {
    val medal = when (rank) {
        1 -> "\uD83E\uDD47"
        2 -> "\uD83E\uDD48"
        3 -> "\uD83E\uDD49"
        else -> "#$rank"
    }

    val timeDelta = winner.timestamp.toDate().time - gameStartDate.time
    val totalSeconds = (timeDelta / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timeString = "+${minutes}m %02ds".format(seconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                color = if (isCurrentHunter) CROrange.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = medal,
            fontSize = if (rank <= 3) 24.sp else 16.sp,
            color = Color.Black,
            modifier = Modifier.width(40.dp)
        )

        Text(
            text = winner.hunterName,
            style = bangerStyle(20),
            color = Color.Black,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = timeString,
            style = gameboyStyle(10),
            color = Color.Gray
        )
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

private val confettiColors = listOf(CROrange, CRPink, Color.Yellow, Color.Green, Color.Blue, Color.Red)

private const val CONFETTI_DURATION_MS = 10_000L

@Composable
private fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val particles = remember {
        (0 until 80).map {
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
        while (true) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                if (startTime == 0L) startTime = frameTimeMs
                if (lastFrameTime > 0L) {
                    val dt = (frameTimeMs - lastFrameTime) / 1000f

                    if (isActive && (frameTimeMs - startTime) >= CONFETTI_DURATION_MS) {
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
