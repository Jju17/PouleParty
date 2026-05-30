package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * QA-only floating panel, rendered only on debug games (`Game.isDebugGame`,
 * created via the `qa_debug_code` long-press). Mirrors the iOS `DebugQAPanel`:
 * spawn a power-up batch or end the game now, both routed through the
 * `debugAdvanceGame` callable (which itself refuses any game where
 * `isDebugGame != true`), so the panel can never affect a real game.
 */
@Composable
fun DebugQAPanel(
    onSpawnPowerUps: () -> Unit,
    onEndNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val purple = Color(0xFF8A2BE2)
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .border(1.5.dp, purple.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("QA DEBUG", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSpawnPowerUps,
                colors = ButtonDefaults.buttonColors(containerColor = purple),
            ) {
                Text("Spawn", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onEndNow,
                colors = ButtonDefaults.buttonColors(containerColor = purple),
            ) {
                Text("End", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
