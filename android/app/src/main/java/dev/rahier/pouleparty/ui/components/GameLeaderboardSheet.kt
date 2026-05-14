package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle
import dev.rahier.pouleparty.ui.victory.LeaderboardEntry
import dev.rahier.pouleparty.ui.victory.buildLeaderboardEntries

/**
 * Reusable bottom sheet that shows the final leaderboard for a finished game.
 *
 * Used by:
 * - [dev.rahier.pouleparty.ui.settings.SettingsScreen] (My Games > tap finished game)
 * - PP-18: chicken & hunter map screens, after gameOver, behind the
 *   "View leaderboard" CTA in the bottom bar.
 *
 * Builds entries from winners only — no network fetch needed for a basic
 * leaderboard. Hunters without a registration doc fall back to the winner's
 * `hunterName` or "Hunter".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLeaderboardSheet(
    game: Game,
    currentUserId: String,
    onDismiss: () -> Unit,
    onReport: ((LeaderboardEntry) -> Unit)? = null
) {
    val entries = remember(game.winners, currentUserId) {
        buildLeaderboardEntries(
            game = game,
            registrations = emptyList(),
            currentUserId = currentUserId
        )
    }
    val hasWinners = game.winners.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🏆", fontSize = 56.sp)
            Text(
                game.name.ifEmpty { "Game ${game.gameCode}" },
                style = bangerStyle(22),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Final results",
                style = gameboyStyle(10),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            if (!hasWinners) {
                Spacer(Modifier.height(16.dp))
                Text("🐔", fontSize = 48.sp)
                Text(
                    "The Chicken survived!",
                    style = bangerStyle(22),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Text(
                    "No hunter found the chicken in this game",
                    style = gameboyStyle(9),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            } else {
                LeaderboardContent(
                    entries = entries,
                    hunterStartMs = game.hunterStartDate.time,
                    modifier = Modifier.fillMaxWidth(),
                    onReport = onReport
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
