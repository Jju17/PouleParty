package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle
import dev.rahier.pouleparty.ui.victory.LeaderboardEntry

/**
 * Shared leaderboard rendering used by VictoryScreen (post-game) and
 * LeaderboardDialog (opened from Settings > My Games for finished games).
 *
 * Pure presentational — callers provide pre-built [LeaderboardEntry] values and the
 * effective hunter start time for relative time computation.
 */
@Composable
fun LeaderboardContent(
    entries: List<LeaderboardEntry>,
    hunterStartMs: Long,
    modifier: Modifier = Modifier,
    itemRenderer: @Composable (rank: Int?, entry: LeaderboardEntry) -> Unit = { rank, entry ->
        LeaderboardEntryRow(rank = rank, entry = entry, hunterStartMs = hunterStartMs)
    }
) {
    val sortedFinders = entries
        .filter { it.hasFound }
        .sortedBy { it.foundTimestampMs ?: Long.MAX_VALUE }
    val nonFinders = entries
        .filter { !it.hasFound }
        .sortedBy { it.displayName.lowercase() }
    val podium = sortedFinders.take(3)
    val others = sortedFinders.drop(3)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (podium.isNotEmpty()) {
            Text(stringResource(R.string.podium), style = bangerStyle(20), color = CROrange)
            podium.forEachIndexed { index, entry ->
                itemRenderer(index + 1, entry)
            }
        }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.other_hunters),
                style = bangerStyle(18),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            others.forEachIndexed { index, entry ->
                itemRenderer(index + 4, entry)
            }
        }
        if (nonFinders.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Did not find the chicken",
                style = bangerStyle(16),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            nonFinders.forEach { entry ->
                itemRenderer(null, entry)
            }
        }
    }
}

@Composable
fun LeaderboardEntryRow(
    rank: Int?,
    entry: LeaderboardEntry,
    hunterStartMs: Long
) {
    val rankLabel = when (rank) {
        null -> "—"
        1 -> "\uD83E\uDD47"
        2 -> "\uD83E\uDD48"
        3 -> "\uD83E\uDD49"
        else -> "#$rank"
    }

    val timeString: String? = entry.foundTimestampMs?.let { ms ->
        val totalSeconds = maxOf(0, ((ms - hunterStartMs) / 1000).toInt())
        "+${totalSeconds / 60}m %02ds".format(totalSeconds % 60)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                color = if (entry.isCurrentUser) CROrange.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rankLabel,
            fontSize = if ((rank ?: 99) <= 3) 24.sp else 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(44.dp)
        )

        Text(
            text = entry.displayName,
            style = bangerStyle(18),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        if (timeString != null) {
            Text(
                text = timeString,
                style = gameboyStyle(10),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}
