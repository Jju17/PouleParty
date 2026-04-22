package dev.rahier.pouleparty.ui.paymentconfirmation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun PaymentConfirmationScreen(
    onBackToHome: () -> Unit,
    viewModel: PaymentConfirmationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val dateFormat = remember {
        SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
    }

    val game = state.game
    val shareMessage = buildShareMessage(game, state.kind, dateFormat)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ConfettiOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            Text(text = "🎉", fontSize = 72.sp)

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(titleFor(state.kind)),
                style = bangerStyle(36),
                textAlign = TextAlign.Center,
                color = CROrange
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(subtitleFor(state.kind)),
                style = bangerStyle(16),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(24.dp))

            gameCard(game = game, nowMs = state.nowMs, dateFormat = dateFormat)

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GradientFire)
                    .clickable(enabled = game != null) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.payment_confirmed_share),
                        style = bangerStyle(20),
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.payment_confirmed_back_home),
                style = bangerStyle(18),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                modifier = Modifier
                    .clickable { onBackToHome() }
                    .padding(vertical = 14.dp, horizontal = 24.dp)
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun buildShareMessage(
    game: Game?,
    kind: ConfirmationKind,
    dateFormat: SimpleDateFormat
): String {
    if (game == null) return ""
    val code = game.gameCode
    val name = game.name.ifEmpty { "PouleParty" }
    val when_ = dateFormat.format(game.startDate)
    return when (kind) {
        ConfirmationKind.CREATOR_FORFAIT ->
            "Rejoins ma partie $name sur Poule Party 🐔 — code $code — on joue le $when_!"
        ConfirmationKind.HUNTER_CAUTION ->
            "Je joue à Poule Party 🐔 — $name, code $code, on lance le $when_!"
    }
}

@Composable
private fun gameCard(
    game: Game?,
    nowMs: Long,
    dateFormat: SimpleDateFormat
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (game == null) {
            Text(
                text = "…",
                style = bangerStyle(22),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
            return@Column
        }

        if (game.name.isNotEmpty()) {
            Text(
                text = game.name,
                style = bangerStyle(24),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.payment_confirmed_game_code),
                style = bangerStyle(13),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            val context = LocalContext.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = game.gameCode,
                    style = gameboyStyle(24),
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("game_code", game.gameCode))
                    }
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.copy_game_code),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }

        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.6f),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
        )

        val startMs = game.startDate.time
        val secondsRemaining = PaymentConfirmationLogic.secondsRemainingUntil(startMs, nowMs)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.payment_confirmed_starts_in),
                style = bangerStyle(13),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Text(
                text = PaymentConfirmationLogic.formatCountdown(secondsRemaining),
                style = gameboyStyle(22),
                color = CROrange
            )
            Text(
                text = dateFormat.format(game.startDate),
                style = bangerStyle(13),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(statusColor(game.gameStatusEnum))
            )
            Text(
                text = stringResource(statusLabel(game.gameStatusEnum)),
                style = bangerStyle(12),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

private fun statusLabel(status: GameStatus): Int = when (status) {
    GameStatus.WAITING -> R.string.payment_confirmed_status_waiting
    GameStatus.IN_PROGRESS -> R.string.payment_confirmed_status_in_progress
    GameStatus.DONE -> R.string.payment_confirmed_status_done
    GameStatus.PENDING_PAYMENT -> R.string.payment_confirmed_status_pending_payment
    GameStatus.PAYMENT_FAILED -> R.string.payment_confirmed_status_payment_failed
}

private fun statusColor(status: GameStatus): Color = when (status) {
    GameStatus.WAITING -> CROrange
    GameStatus.IN_PROGRESS -> Success
    GameStatus.DONE -> Color(0x33000000)
    GameStatus.PENDING_PAYMENT -> CROrange.copy(alpha = 0.6f)
    GameStatus.PAYMENT_FAILED -> Color.Red
}

private fun titleFor(kind: ConfirmationKind): Int = when (kind) {
    ConfirmationKind.CREATOR_FORFAIT -> R.string.payment_confirmed_title_creator
    ConfirmationKind.HUNTER_CAUTION -> R.string.payment_confirmed_title_hunter
}

private fun subtitleFor(kind: ConfirmationKind): Int = when (kind) {
    ConfirmationKind.CREATOR_FORFAIT -> R.string.payment_confirmed_subtitle_creator
    ConfirmationKind.HUNTER_CAUTION -> R.string.payment_confirmed_subtitle_hunter
}

// ── Confetti (forked from VictoryScreen) ────────────────────────────────────

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
    var shapeType: Int
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
                    drawCircle(color = p.color, radius = p.size / 2f, center = Offset(px, py))
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
