package dev.rahier.pouleparty.ui.validation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.ChallengeSubmission
import dev.rahier.pouleparty.model.ChallengeType
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.CRPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidationQueueScreen(
    onDismiss: () -> Unit,
    viewModel: ValidationQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ValidationQueueEffect.Dismiss -> onDismiss()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.validation_queue_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onIntent(ValidationQueueIntent.CloseTapped) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.submissions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.validation_queue_empty),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            } else {
                val langCode = LocalConfiguration.current.locales[0].language
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.submissions, key = { it.id }) { submission ->
                        val ch = state.challenge(submission)
                        SubmissionRow(
                            submission = submission,
                            challengeTitle = ch?.localizedTitle(langCode) ?: submission.challengeId,
                            challengePoints = ch?.points ?: 0,
                            teamName = state.teamName(submission.hunterId),
                            onTap = { viewModel.onIntent(ValidationQueueIntent.SubmissionTapped(submission)) },
                        )
                    }
                }
            }

            val selected = state.selected
            if (selected != null) {
                val langCode = LocalConfiguration.current.locales[0].language
                val sel = state.challenge(selected)
                SubmissionDetailDialog(
                    submission = selected,
                    challengeTitle = sel?.localizedTitle(langCode) ?: selected.challengeId,
                    challengeBody = sel?.localizedBody(langCode) ?: "",
                    challengePoints = sel?.points ?: 0,
                    teamName = state.teamName(selected.hunterId),
                    isBusy = state.busyIds.contains(selected.id),
                    onValidate = { viewModel.onIntent(ValidationQueueIntent.ValidateTapped(selected)) },
                    onReject = { viewModel.onIntent(ValidationQueueIntent.RejectTapped(selected)) },
                    onClose = { viewModel.onIntent(ValidationQueueIntent.DetailDismissed) },
                )
            }

            val errorMsg = state.error
            if (errorMsg != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.onIntent(ValidationQueueIntent.ErrorDismissed) },
                    title = { Text(stringResource(R.string.validation_failed)) },
                    text = { Text(errorMsg) },
                    confirmButton = {
                        Button(onClick = { viewModel.onIntent(ValidationQueueIntent.ErrorDismissed) }) {
                            Text(stringResource(R.string.ok))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SubmissionRow(
    submission: ChallengeSubmission,
    challengeTitle: String,
    challengePoints: Int,
    teamName: String,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = submission.photoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (submission.typeEnum == ChallengeType.REPEATABLE) Icons.Filled.Refresh else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (submission.typeEnum == ChallengeType.REPEATABLE) CRPink else CROrange,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = challengeTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = teamName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CRPink,
            )
            if (challengePoints > 0) {
                Text(
                    text = stringResource(R.string.challenge_points_format, challengePoints),
                    fontSize = 12.sp,
                    color = CROrange,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SubmissionDetailDialog(
    submission: ChallengeSubmission,
    challengeTitle: String,
    challengeBody: String,
    challengePoints: Int,
    teamName: String,
    isBusy: Boolean,
    onValidate: () -> Unit,
    onReject: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(challengeTitle) },
        text = {
            Column {
                AsyncImage(
                    model = submission.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black, RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(12.dp))
                Text(text = teamName, fontWeight = FontWeight.Bold, color = CRPink, fontSize = 18.sp)
                if (challengeBody.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = challengeBody, fontSize = 14.sp)
                }
                if (challengePoints > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.challenge_points_format, challengePoints),
                        color = CROrange,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onValidate,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = CROrange),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.challenge_validate))
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onReject, enabled = !isBusy) {
                Text(stringResource(R.string.reject))
            }
        },
    )
}
