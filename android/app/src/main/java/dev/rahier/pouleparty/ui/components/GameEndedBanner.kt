package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.CRPink
import dev.rahier.pouleparty.ui.theme.NeonGlowIntensity
import dev.rahier.pouleparty.ui.theme.neonGlow

/**
 * Shared "Game ended → tap to see leaderboard" banner shown on top
 * of every active map (chicken / hunter / GameMaster) once the game
 * reaches `status == DONE`. The map stays on screen; tapping the
 * banner is the only path forward — it sends the screen's
 * `ViewLeaderboardTapped` intent which routes to the Victory /
 * leaderboard screen (which has the canonical "Back to menu" CTA).
 *
 * Mirrors iOS `GameEndedBanner`.
 */
@Composable
fun GameEndedBanner(
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neonGlow(CROrange, NeonGlowIntensity.SUBTLE, cornerRadius = 14.dp)
            .background(
                brush = Brush.horizontalGradient(listOf(CROrange, CRPink)),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onTap() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Game ended",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = "Tap to see the leaderboard",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp),
        )
    }
}
