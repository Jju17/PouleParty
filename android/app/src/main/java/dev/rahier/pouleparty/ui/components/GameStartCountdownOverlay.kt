package dev.rahier.pouleparty.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
                    color = CROrange
                )
            } else if (countdownText != null) {
                Text(
                    text = countdownText,
                    style = bangerStyle(48),
                    color = CROrange
                )
            }
        }
    }
}

@Composable
fun PreGameOverlay(
    isChicken: Boolean,
    gameModTitle: String,
    gameCode: String?,
    targetDate: Date,
    nowDate: Date,
    connectedHunters: Int = 0,
    onCancelGame: (() -> Unit)? = null
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

    AnimatedVisibility(
        visible = secondsRemaining > AppConstants.COUNTDOWN_THRESHOLD_SECONDS.toLong(),
        exit = fadeOut(animationSpec = tween(500))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = if (isChicken) stringResource(R.string.you_are_chicken) else stringResource(R.string.you_are_hunter),
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
                                tint = if (codeCopied) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.6f)
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
                        Text(text = "\uD83D\uDC14", fontSize = 14.sp)
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
                        Text(text = "\uD83D\uDD0D", fontSize = 14.sp)
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

                if (onCancelGame != null) {
                    TextButton(
                        onClick = onCancelGame,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cancel_game),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Red
                        )
                    }
                }
            }
        }
    }
}
