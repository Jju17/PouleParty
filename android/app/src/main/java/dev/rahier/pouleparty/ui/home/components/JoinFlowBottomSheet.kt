package dev.rahier.pouleparty.ui.home.components

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.rahier.pouleparty.ui.components.SecondaryButton
import dev.rahier.pouleparty.ui.components.PrimaryButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.ui.home.HomeUiState
import dev.rahier.pouleparty.ui.home.JoinFlowStep
import dev.rahier.pouleparty.ui.theme.GameBoyFont
import dev.rahier.pouleparty.ui.theme.GradientFire

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinFlowBottomSheet(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onTeamNameChanged: (String) -> Unit,
    onJoinAsHunterTapped: () -> Unit,
    onSubmitJoinTapped: () -> Unit,
    onJoinAsGameMasterTapped: () -> Unit = {},
    onGameMasterPasswordChanged: (String) -> Unit = {},
    onSubmitGameMasterPasswordTapped: () -> Unit = {},
    onValidationCodeChanged: (String) -> Unit = {},
    onSubmitValidationCodeTapped: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        when (val step = state.joinStep) {
            is JoinFlowStep.ValidationCodeEntry, is JoinFlowStep.SubmittingValidationCode -> {
                ValidationCodeContent(
                    code = state.validationCodeInput,
                    error = state.validationCodeError,
                    isSubmitting = step is JoinFlowStep.SubmittingValidationCode,
                    onCodeChanged = onValidationCodeChanged,
                    onSubmit = onSubmitValidationCodeTapped,
                )
            }
            is JoinFlowStep.JoiningWithTeamName, is JoinFlowStep.SubmittingJoin -> {
                val game = (step as? JoinFlowStep.JoiningWithTeamName)?.game
                    ?: (step as JoinFlowStep.SubmittingJoin).game
                val isSubmitting = step is JoinFlowStep.SubmittingJoin
                TeamNameFormContent(
                    game = game,
                    teamName = state.teamName,
                    isTeamNameValid = state.isTeamNameValid,
                    isTeamNameProfane = state.isTeamNameProfane,
                    isSubmitting = isSubmitting,
                    onTeamNameChanged = onTeamNameChanged,
                    onSubmit = onSubmitJoinTapped
                )
            }
            is JoinFlowStep.GameMasterPasswordEntry, is JoinFlowStep.SubmittingGameMasterPassword -> {
                val isSubmitting = step is JoinFlowStep.SubmittingGameMasterPassword
                GameMasterPasswordContent(
                    password = state.gameMasterPasswordInput,
                    error = state.gameMasterPasswordError,
                    isSubmitting = isSubmitting,
                    onPasswordChanged = onGameMasterPasswordChanged,
                    onSubmit = onSubmitGameMasterPasswordTapped,
                )
            }
            else -> {
                CodeEntryContent(
                    state = state,
                    onCodeChanged = onCodeChanged,
                    onJoinAsHunterTapped = onJoinAsHunterTapped,
                    onJoinAsGameMasterTapped = onJoinAsGameMasterTapped,
                )
            }
        }
    }
}

@Composable
private fun CodeEntryContent(
    state: HomeUiState,
    onCodeChanged: (String) -> Unit,
    onJoinAsHunterTapped: () -> Unit,
    onJoinAsGameMasterTapped: () -> Unit,
) {
    val step = state.joinStep
    val isEnabled = step is JoinFlowStep.CodeValidated
    val gmAvailable: Boolean = (step as? JoinFlowStep.CodeValidated)
        ?.let { it.game.hasGameMasterPassword } ?: false

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.join_game),
            fontFamily = GameBoyFont,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(R.string.enter_the_game_code),
            fontFamily = GameBoyFont,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        val codeError = when (step) {
            is JoinFlowStep.CodeNotFound -> stringResource(R.string.no_game_found_with_this_code)
            is JoinFlowStep.GameFull -> stringResource(R.string.party_full)
            is JoinFlowStep.NetworkError -> stringResource(R.string.network_error_please_try_again)
            else -> null
        }
        OutlinedTextField(
            value = state.gameCode,
            onValueChange = onCodeChanged,
            singleLine = true,
            isError = codeError != null,
            supportingText = codeError?.let { message -> { FieldError(message) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(0.7f),
            placeholder = { Text("ABC123") }
        )
        if (step is JoinFlowStep.Validating) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        if (step is JoinFlowStep.NetworkError) {
            SecondaryButton(text = stringResource(R.string.retry), onClick = { onCodeChanged(state.gameCode) })
        }
        PrimaryButton(
            text = if (gmAvailable) stringResource(R.string.join_as_hunter) else stringResource(R.string.join),
            onClick = onJoinAsHunterTapped,
            enabled = isEnabled,
        )
        if (gmAvailable) {
            SecondaryButton(text = stringResource(R.string.join_as_game_master), onClick = onJoinAsGameMasterTapped)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ValidationCodeContent(
    code: String,
    error: String?,
    isSubmitting: Boolean,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.validation_code_title),
            fontFamily = GameBoyFont,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(R.string.validation_code_hint),
            fontFamily = GameBoyFont,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChanged,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(0.7f),
            placeholder = { Text(stringResource(R.string.validation_code_label)) },
            isError = error != null,
            supportingText = error?.let { message -> { FieldError(message) } },
            enabled = !isSubmitting,
        )
        PrimaryButton(text = stringResource(R.string.validation_code_submit), onClick = onSubmit, enabled = code.trim().isNotEmpty(), isLoading = isSubmitting)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GameMasterPasswordContent(
    password: String,
    error: String?,
    isSubmitting: Boolean,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.gamemaster_join_title),
            fontFamily = GameBoyFont,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(R.string.gamemaster_password_hint),
            fontFamily = GameBoyFont,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChanged,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(0.4f),
            placeholder = { Text("••••") },
            isError = error != null,
            supportingText = error?.let { message -> { FieldError(message) } },
            enabled = !isSubmitting,
        )
        PrimaryButton(text = stringResource(R.string.submit), onClick = onSubmit, enabled = password.length == 4, isLoading = isSubmitting)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TeamNameFormContent(
    game: Game,
    teamName: String,
    isTeamNameValid: Boolean,
    isTeamNameProfane: Boolean,
    isSubmitting: Boolean,
    onTeamNameChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.team_name),
            fontFamily = GameBoyFont,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Game ${game.gameCode}",
            fontFamily = GameBoyFont,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = teamName,
            onValueChange = onTeamNameChanged,
            label = { Text(stringResource(R.string.team_name)) },
            singleLine = true,
            isError = isTeamNameProfane,
            supportingText = if (isTeamNameProfane) {
                { FieldError(stringResource(R.string.team_name_profane)) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(text = stringResource(R.string.join), onClick = onSubmit, enabled = isTeamNameValid, isLoading = isSubmitting)
        Spacer(Modifier.height(20.dp))
    }
}

/** Error under a field: red, and announced by TalkBack as soon as it appears. */
@Composable
private fun FieldError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
