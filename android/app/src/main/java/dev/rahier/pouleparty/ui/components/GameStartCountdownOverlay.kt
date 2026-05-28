package dev.rahier.pouleparty.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun GameStartCountdownOverlay(
    countdownNumber: Int?,
    countdownText: String?
) {
    AnimatedVisibility(
        visible = countdownNumber != null || countdownText != null,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (countdownNumber != null) {
                Text(
                    text = "$countdownNumber",
                    style = gameboyStyle(80),
                    color = CROrange,
                    modifier = Modifier.neonGlow(CROrange, NeonGlowIntensity.INTENSE)
                )
            } else if (countdownText != null) {
                Text(
                    text = countdownText,
                    style = bangerStyle(48),
                    color = CROrange,
                    modifier = Modifier.neonGlow(CROrange, NeonGlowIntensity.MEDIUM)
                )
            }
        }
    }
}

/** Role identifies which header copy + launch-button affordance the
 *  pre-game overlay should display. Mirrors iOS `GameRole`. */
enum class PreGameRole { CHICKEN, HUNTER, GAME_MASTER }

/**
 * Full-screen "lobby" overlay shown on every active map (chicken /
 * hunter / GameMaster) **before** the game starts. Three flavours:
 *
 *  - Auto-start (default): countdown to `targetDate`. The overlay
 *    self-hides once the 3-2-1-GO! `GameStartCountdownOverlay` takes
 *    over.
 *  - Manual-start launcher (`isManualStart == true` && role is
 *    chicken or game master): big LAUNCH button. Calls `onLaunchTapped`,
 *    shows spinner + "Launching…" while the callable is in flight,
 *    surfaces `launchErrorMessage` as an AlertDialog.
 *  - Manual-start waiter (`isManualStart == true` && role is hunter):
 *    passive "Waiting for the chicken to launch" message.
 *
 * Replaces the previous `ReadyToLaunchOverlay` (deleted with this
 * change) so all pre-game UI lives in one component.
 */
@Composable
fun PreGameOverlay(
    role: PreGameRole,
    gameModTitle: String,
    gameCode: String?,
    targetDate: Date,
    nowDate: Date,
    connectedHunters: Int = 0,
    onCancelGame: (() -> Unit)? = null,
    isManualStart: Boolean = false,
    isLaunching: Boolean = false,
    launchErrorMessage: String? = null,
    onLaunchTapped: (() -> Unit)? = null,
    onLaunchErrorDismissed: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val secondsRemaining = maxOf(0L, (targetDate.time - nowDate.time) / 1000)
    val days = secondsRemaining / 86400
    val hours = (secondsRemaining % 86400) / 3600
    val minutes = (secondsRemaining % 3600) / 60
    val seconds = secondsRemaining % 60
    val formattedTime = when {
        days > 0 -> String.format("%dj %02d:%02d:%02d", days, hours, minutes, seconds)
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
    val timerFontSize = when {
        days > 0 -> 30
        hours > 0 -> 38
        else -> 48
    }
    var codeCopied by remember { mutableStateOf(false) }

    LaunchedEffect(codeCopied) {
        if (codeCopied) {
            delay(1500)
            codeCopied = false
        }
    }

    // Manual-start stays visible until the launcher taps LAUNCH (the
    // parent unmounts the overlay by flipping out of READY_TO_LAUNCH).
    // Auto-start hides once the 3-2-1 takes over.
    val isVisible = isManualStart ||
        secondsRemaining > AppConstants.COUNTDOWN_THRESHOLD_SECONDS.toLong()

    val headerRes = when (role) {
        PreGameRole.CHICKEN -> R.string.you_are_chicken
        PreGameRole.HUNTER -> R.string.you_are_hunter
        PreGameRole.GAME_MASTER -> R.string.you_are_gamemaster
    }
    val isLauncherRole = role == PreGameRole.CHICKEN || role == PreGameRole.GAME_MASTER

    AnimatedVisibility(
        visible = isVisible,
        exit = fadeOut(animationSpec = tween(500))
    ) {
        // Manual-start uses Top alignment + a flexible Spacer between
        // info and launch so the launch button + cancel land in the
        // lower half (~3/4 height) — natural thumb reach instead of a
        // centered-card feel. Auto-start keeps the centered layout.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = if (isManualStart) Alignment.TopCenter else Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = if (isManualStart) {
                    Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 32.dp)
                } else {
                    Modifier.padding(32.dp)
                },
            ) {
                Text(
                    text = stringResource(headerRes),
                    style = bangerStyle(32),
                    color = Color.White
                )

                Text(
                    text = gameModTitle,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                if (gameCode != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable {
                                copyToClipboard(context, "Game Code", gameCode)
                                codeCopied = true
                            }
                    ) {
                        Text(
                            text = if (codeCopied) stringResource(R.string.copied) else stringResource(R.string.tap_to_copy),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = gameCode,
                                style = gameboyStyle(28),
                                color = Color.White
                            )
                            Icon(
                                imageVector = if (codeCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.copy_game_code),
                                tint = if (codeCopied) Success else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "🐔", fontSize = 14.sp)
                        Text(
                            text = "1",
                            style = gameboyStyle(16),
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.connected),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "🔍", fontSize = 14.sp)
                        Text(
                            text = "$connectedHunters",
                            style = gameboyStyle(16),
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.connected),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                if (isManualStart) {
                    // Flexible spacer pushes the launch section + cancel
                    // toward the lower half of the screen.
                    Spacer(Modifier.weight(1f).fillMaxWidth())
                    if (isLauncherRole && onLaunchTapped != null) {
                        Text(
                            text = "Ready when you are",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Button(
                            onClick = onLaunchTapped,
                            enabled = !isLaunching,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(
                                    brush = Brush.linearGradient(listOf(CROrange, CRPink)),
                                    shape = RoundedCornerShape(16.dp),
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (isLaunching) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Text(
                                    text = if (isLaunching) "Launching…" else "LAUNCH GAME",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Waiting for the chicken to launch",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.game_starting_in),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formattedTime,
                            style = gameboyStyle(timerFontSize),
                            color = CROrange
                        )
                    }
                }

                if (onCancelGame != null) {
                    TextButton(
                        onClick = onCancelGame,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cancel_game),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Danger
                        )
                    }
                }
                if (isManualStart) {
                    // Lower bottom margin so the launch chunk sits at
                    // roughly 70-75% screen height, not glued to the
                    // bottom safe area. Mirrors the iOS layout shift.
                    Spacer(Modifier.weight(0.4f).fillMaxWidth())
                }
            }
        }
    }

    if (launchErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { onLaunchErrorDismissed?.invoke() },
            title = { Text("Launch failed") },
            text = { Text(launchErrorMessage) },
            confirmButton = {
                TextButton(onClick = { onLaunchErrorDismissed?.invoke() }) { Text("OK") }
            },
        )
    }
}
