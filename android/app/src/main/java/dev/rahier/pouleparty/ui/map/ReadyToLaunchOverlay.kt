package dev.rahier.pouleparty.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.CRPink

enum class LaunchOverlayRole { LAUNCHER, WAITER }

/**
 * Full-screen overlay shown when `Game.gameStatusEnum == READY_TO_LAUNCH`
 * (PP-71 manual-start mode). Mirrors iOS `ReadyToLaunchOverlay`.
 */
@Composable
fun ReadyToLaunchOverlay(
    role: LaunchOverlayRole,
    isLaunching: Boolean,
    errorMessage: String?,
    onLaunchTapped: () -> Unit,
    onErrorDismissed: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text("🐔", fontSize = 72.sp)
            when (role) {
                LaunchOverlayRole.LAUNCHER -> LauncherContent(
                    isLaunching = isLaunching,
                    onLaunchTapped = onLaunchTapped,
                )
                LaunchOverlayRole.WAITER -> WaiterContent()
            }
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = onErrorDismissed,
            title = { Text("Launch failed") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = onErrorDismissed) { Text("OK") }
            },
        )
    }
}

@Composable
private fun LauncherContent(
    isLaunching: Boolean,
    onLaunchTapped: () -> Unit,
) {
    Text(
        text = "Ready when you are",
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "Tap LAUNCH when everyone is gathered. The countdown starts as soon as you confirm.",
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onLaunchTapped,
        enabled = !isLaunching,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(
                brush = Brush.linearGradient(listOf(CROrange, CRPink)),
                shape = RoundedCornerShape(18.dp),
            ),
        shape = RoundedCornerShape(18.dp),
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
                    modifier = Modifier.size(22.dp),
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
}

@Composable
private fun WaiterContent() {
    Text(
        text = "Waiting for the chicken to launch",
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "The party will start as soon as the chicken or a GameMaster taps LAUNCH.",
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    Spacer(Modifier.height(4.dp))
    CircularProgressIndicator(
        color = Color.White,
        strokeWidth = 3.dp,
        modifier = Modifier.size(36.dp),
    )
}
