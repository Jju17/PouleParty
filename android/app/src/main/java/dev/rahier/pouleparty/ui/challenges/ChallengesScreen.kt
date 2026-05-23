package dev.rahier.pouleparty.ui.challenges

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import dev.rahier.pouleparty.ui.theme.HunterRed
import dev.rahier.pouleparty.ui.theme.NeonGlowIntensity
import dev.rahier.pouleparty.ui.theme.ZoneGreen
import dev.rahier.pouleparty.ui.theme.neonGlow
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
    isClosedForSubmissions: Boolean = false,
    viewModel: ChallengesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(isClosedForSubmissions) {
        viewModel.setClosedForSubmissions(isClosedForSubmissions)
    }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChallengesEffect.ShowError -> {
                    val msg = context.getString(R.string.challenge_upload_failed) + ": " + effect.message
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val targetId = state.photoTargetChallengeId
        if (uri != null && targetId != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) {
                viewModel.onIntent(ChallengesIntent.PhotoPicked(targetId, bytes))
            } else {
                viewModel.onIntent(ChallengesIntent.PhotoSourceCancelled)
            }
        } else {
            viewModel.onIntent(ChallengesIntent.PhotoSourceCancelled)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val targetId = state.photoTargetChallengeId
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null && targetId != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) {
                viewModel.onIntent(ChallengesIntent.PhotoPicked(targetId, bytes))
            } else {
                viewModel.onIntent(ChallengesIntent.PhotoSourceCancelled)
            }
        } else {
            viewModel.onIntent(ChallengesIntent.PhotoSourceCancelled)
        }
    }

    if (state.photoTargetChallenge != null) {
        PhotoSourceDialog(
            onTakePhoto = {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "pouleparty_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )
                if (uri != null) {
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    viewModel.onIntent(ChallengesIntent.PhotoSourceCancelled)
                }
            },
            onPickFromLibrary = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onCancel = { viewModel.onIntent(ChallengesIntent.PhotoSourceCancelled) }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        // Fill the sheet entirely. Don't use fillMaxHeight(0.95f): the 5% of
        // empty space at the bottom belongs to the sheet and hijacks vertical
        // drags as swipe-to-dismiss. Don't wrap the LazyColumn in a Box either:
        // ModalBottomSheet auto-wires its NestedScrollConnection to the nearest
        // scrollable descendant, and a Box wrapper breaks that path on
        // Material3 (PP-38).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
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

            when (state.selectedTab) {
                ChallengesTab.CHALLENGES -> ChallengesTabContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = state,
                    onMarkAsDone = { viewModel.onIntent(ChallengesIntent.MarkAsDoneTapped(it)) },
                    onSubmit = { viewModel.onIntent(ChallengesIntent.SubmitForValidationTapped(it)) }
                )
                ChallengesTab.LEADERBOARD -> LeaderboardTabContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = state
                )
            }

            SegmentedTabBar(
                selected = state.selectedTab,
                onSelected = { viewModel.onIntent(ChallengesIntent.TabSelected(it)) }
            )
        }
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
    modifier: Modifier = Modifier,
    state: ChallengesUiState,
    onMarkAsDone: (Challenge) -> Unit,
    onSubmit: (Challenge) -> Unit
) {
    val groups = state.challengesByLevel
    if (groups.isEmpty()) {
        Box(
            modifier = modifier.padding(32.dp),
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
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.isClosedForSubmissions) {
            item("closed-banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HunterRed, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.challenges_closed_banner),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
        groups.forEach { (level, levelChallenges) ->
            val locked = state.isLevelLocked(level)
            val progress = state.progressForLevel(level)
            item(key = "header-$level") {
                LevelHeader(level = level, isLocked = locked, progress = progress)
            }
            items(levelChallenges, key = { it.id }) { challenge ->
                ChallengeRow(
                    challenge = challenge,
                    status = state.status(challenge),
                    isClosed = state.isClosedForSubmissions,
                    isLevelLocked = locked,
                    onMarkAsDone = { onMarkAsDone(challenge) },
                    onSubmit = { onSubmit(challenge) }
                )
            }
        }
    }
}

@Composable
private fun LevelHeader(
    level: Int,
    isLocked: Boolean,
    progress: dev.rahier.pouleparty.ui.gamelogic.LevelProgress,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.challenge_level_header, level),
            style = bangerStyle(18),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        if (isLocked) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(
                    R.string.challenge_level_locked_label,
                    progress.validated,
                    progress.total,
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ChallengeRow(
    challenge: Challenge,
    status: ChallengeStatus,
    isClosed: Boolean,
    isLevelLocked: Boolean,
    onMarkAsDone: () -> Unit,
    onSubmit: () -> Unit
) {
    val validated = status == ChallengeStatus.VALIDATED
    val completedBorder = if (validated) {
        Modifier
            .neonGlow(ZoneGreen, NeonGlowIntensity.MEDIUM, cornerRadius = 14.dp)
            .border(width = 2.dp, color = ZoneGreen, shape = RoundedCornerShape(14.dp))
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
        val langCode = LocalConfiguration.current.locales[0].language
        val displayTitle = challenge.localizedTitle(langCode)
        val displayBody = challenge.localizedBody(langCode)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (challenge.number > 0) {
                    Text(
                        text = "#${challenge.number}",
                        style = bangerStyle(18),
                        color = CROrange,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Text(
                    text = displayTitle,
                    style = bangerStyle(18),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (displayBody.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = displayBody,
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
        val labelRes = when (status) {
            ChallengeStatus.AVAILABLE -> R.string.challenge_mark_as_done
            ChallengeStatus.PENDING_LOCAL -> R.string.challenge_submit_for_validation
            ChallengeStatus.SUBMITTING -> R.string.challenge_uploading
            ChallengeStatus.AWAITING_VALIDATION -> R.string.challenge_awaiting_validation
            ChallengeStatus.VALIDATED -> R.string.challenge_done
            ChallengeStatus.REJECTED -> R.string.challenge_rejected
        }
        val containerColor = when (status) {
            ChallengeStatus.AVAILABLE -> CROrange
            ChallengeStatus.PENDING_LOCAL -> CRPink
            ChallengeStatus.SUBMITTING -> CROrange.copy(alpha = 0.6f)
            ChallengeStatus.AWAITING_VALIDATION -> CROrange.copy(alpha = 0.25f)
            ChallengeStatus.VALIDATED -> ZoneGreen
            ChallengeStatus.REJECTED -> Color.Gray
        }
        val onClick: () -> Unit = when (status) {
            ChallengeStatus.AVAILABLE -> onMarkAsDone
            ChallengeStatus.PENDING_LOCAL -> onSubmit
            else -> { -> }
        }
        val canSubmit = status == ChallengeStatus.PENDING_LOCAL && !isLevelLocked
        val canMark = status == ChallengeStatus.AVAILABLE
        Button(
            onClick = onClick,
            enabled = !isClosed && (canMark || canSubmit),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                disabledContainerColor = containerColor,
                disabledContentColor = if (validated) Color.Black else Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (status == ChallengeStatus.SUBMITTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(labelRes),
                    fontSize = 13.sp,
                    color = if (validated) Color.Black else Color.White
                )
            }
        }
    }
}

@Composable
private fun LeaderboardTabContent(
    modifier: Modifier = Modifier,
    state: ChallengesUiState
) {
    val entries = state.leaderboardEntries
    if (entries.isEmpty()) {
        Box(
            modifier = modifier.padding(32.dp),
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
        modifier = modifier,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceDialog(
    onTakePhoto: () -> Unit,
    onPickFromLibrary: () -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.challenge_add_photo),
                style = bangerStyle(20),
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CROrange, RoundedCornerShape(10.dp))
                    .clickable { onTakePhoto() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.challenge_take_photo),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CRPink, RoundedCornerShape(10.dp))
                    .clickable { onPickFromLibrary() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.challenge_choose_from_library),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCancel() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
