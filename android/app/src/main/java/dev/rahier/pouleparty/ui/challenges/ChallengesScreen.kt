package dev.rahier.pouleparty.ui.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.CRPink
import dev.rahier.pouleparty.ui.theme.ZoneGreen
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle

/**
 * Near-full-height modal bottom sheet showing Challenges and Leaderboard tabs.
 *
 * Hoist visibility at the caller — render this composable only when the sheet
 * should be visible, and use [onDismiss] to hide it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengesSheet(
    onDismiss: () -> Unit,
    viewModel: ChallengesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .navigationBarsPadding()
        ) {
            // Title bar — title left, close button right. The ModalBottomSheet's
            // default drag handle renders above this row and signals swipe-to-dismiss.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.challenges),
                    style = bangerStyle(22),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            HorizontalDivider()

            // Scrollable content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (state.selectedTab) {
                    ChallengesTab.CHALLENGES -> ChallengesTabContent(
                        state = state,
                        onValidateTapped = { viewModel.onIntent(ChallengesIntent.ValidateTapped(it)) }
                    )
                    ChallengesTab.LEADERBOARD -> LeaderboardTabContent(state = state)
                }
            }

            // Fixed-bottom segmented picker
            SegmentedTabBar(
                selected = state.selectedTab,
                onSelected = { viewModel.onIntent(ChallengesIntent.TabSelected(it)) }
            )
        }
    }

    // Confirmation dialog for validating a challenge
    val pending = state.pendingChallenge
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(ChallengesIntent.DismissConfirmation) },
            title = { Text(pending.title) },
            text = { Text(stringResource(R.string.challenge_validate_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onIntent(ChallengesIntent.ConfirmValidation) },
                    enabled = !state.isSubmitting
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(R.string.submit))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onIntent(ChallengesIntent.DismissConfirmation) },
                    enabled = !state.isSubmitting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SegmentedTabBar(
    selected: ChallengesTab,
    onSelected: (ChallengesTab) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val tabs = ChallengesTab.entries
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size)
            ) {
                Text(
                    stringResource(
                        when (tab) {
                            ChallengesTab.CHALLENGES -> R.string.challenges
                            ChallengesTab.LEADERBOARD -> R.string.leaderboard
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun ChallengesTabContent(
    state: ChallengesUiState,
    onValidateTapped: (Challenge) -> Unit
) {
    if (state.challenges.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.challenges_empty),
                style = gameboyStyle(12),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        return
    }
    val completedIds = state.completedIdsForCurrentHunter
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.challenges, key = { it.id }) { challenge ->
            ChallengeRow(
                challenge = challenge,
                isCompleted = completedIds.contains(challenge.id),
                onValidateTapped = { onValidateTapped(challenge) }
            )
        }
    }
}

@Composable
private fun ChallengeRow(
    challenge: Challenge,
    isCompleted: Boolean,
    onValidateTapped: () -> Unit
) {
    val completedBorder = if (isCompleted) {
        Modifier
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(14.dp))
            .border(
                width = 2.dp,
                color = ZoneGreen,
                shape = RoundedCornerShape(14.dp)
            )
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(completedBorder)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = challenge.title,
                style = bangerStyle(18),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            if (challenge.body.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = challenge.body,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.challenge_points_format, challenge.points),
                style = gameboyStyle(10),
                color = CROrange
            )
        }
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = onValidateTapped,
            enabled = !isCompleted,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCompleted) ZoneGreen else CROrange,
                disabledContainerColor = ZoneGreen,
                disabledContentColor = Color.Black
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (isCompleted) {
                    stringResource(R.string.challenge_done)
                } else {
                    stringResource(R.string.challenge_validate)
                },
                fontSize = 13.sp,
                color = if (isCompleted) Color.Black else Color.White
            )
        }
    }
}

@Composable
private fun LeaderboardTabContent(state: ChallengesUiState) {
    val entries = state.leaderboardEntries
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.leaderboard_empty),
                style = gameboyStyle(12),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        return
    }
    val topThree = entries.take(3)
    val others = entries.drop(3)
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (topThree.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.podium),
                    style = bangerStyle(22),
                    color = CROrange
                )
            }
            items(topThree, key = { "top-${it.hunterId}" }) { entry ->
                val rank = entries.indexOf(entry) + 1
                TopPlayerRow(rank = rank, entry = entry)
            }
        }
        if (others.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.other_hunters),
                    style = bangerStyle(18),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            items(others, key = { "other-${it.hunterId}" }) { entry ->
                val rank = entries.indexOf(entry) + 1
                RegularPlayerRow(rank = rank, entry = entry)
            }
        }
    }
}

@Composable
private fun TopPlayerRow(rank: Int, entry: LeaderboardHunterEntry) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> Color.Gray
    }
    val rankEmoji = when (rank) {
        1 -> "\uD83E\uDD47"
        2 -> "\uD83E\uDD48"
        3 -> "\uD83E\uDD49"
        else -> "#$rank"
    }
    val background = if (entry.isCurrentHunter) {
        CROrange.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    }
    val borderMod = if (entry.isCurrentHunter) {
        Modifier.border(2.dp, CRPink, RoundedCornerShape(12.dp))
    } else {
        Modifier.border(2.dp, rankColor, RoundedCornerShape(12.dp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderMod)
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rankEmoji,
            fontSize = 28.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = entry.displayName,
            style = bangerStyle(22),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            text = stringResource(R.string.challenge_points_format, entry.totalPoints),
            style = gameboyStyle(12),
            color = rankColor
        )
    }
}

@Composable
private fun RegularPlayerRow(rank: Int, entry: LeaderboardHunterEntry) {
    val background = if (entry.isCurrentHunter) {
        CROrange.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
    }
    val borderMod = if (entry.isCurrentHunter) {
        Modifier.border(1.5.dp, CRPink, RoundedCornerShape(10.dp))
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderMod)
            .background(background, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = entry.displayName,
            style = bangerStyle(18),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            text = stringResource(R.string.challenge_points_format, entry.totalPoints),
            style = gameboyStyle(10),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
