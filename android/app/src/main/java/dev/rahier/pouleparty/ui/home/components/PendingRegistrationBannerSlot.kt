package dev.rahier.pouleparty.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.home.PendingRegistration
import dev.rahier.pouleparty.ui.theme.GameBoyFont
import dev.rahier.pouleparty.ui.theme.GradientFire

/**
 * Banner shown on Home when the user has a pending registration but is
 * not currently in an active game. Switches between expanded card and a
 * collapsed-tab variant on the trailing edge.
 */
@Composable
fun PendingRegistrationBannerSlot(
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
                        if (pending.isFinished) stringResource(R.string.game_ended)
                        else stringResource(R.string.registered_to_game),
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
                    if (!pending.isFinished) {
                        Text(
                            relativeStartingIn(pending.startMs),
                            fontFamily = GameBoyFont,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    TextButton(
                        onClick = onJoin,
                        modifier = Modifier
                            .border(3.dp, Color.White, RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            if (pending.isFinished) stringResource(R.string.view_results)
                            else stringResource(R.string.join),
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

internal fun relativeStartingIn(startMs: Long): String {
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
