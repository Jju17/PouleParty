package dev.rahier.pouleparty.ui.home.components

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

/** Modal sheet driving the multi-step join/register flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinFlowBottomSheet(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onTeamNameChanged: (String) -> Unit,
    onJoinTapped: () -> Unit,
    onRegisterTapped: () -> Unit,
    onSubmitRegistrationTapped: () -> Unit,
    onJoinAsGameMasterTapped: () -> Unit = {},
    onGameMasterPasswordChanged: (String) -> Unit = {},
    onSubmitGameMasterPasswordTapped: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        when (val step = state.joinStep) {
            is JoinFlowStep.Registering, is JoinFlowStep.SubmittingRegistration -> {
                val game = (step as? JoinFlowStep.Registering)?.game
                    ?: (step as JoinFlowStep.SubmittingRegistration).game
                val isSubmitting = step is JoinFlowStep.SubmittingRegistration
                RegisterFormContent(
                    game = game,
                    teamName = state.teamName,
                    isTeamNameValid = state.isTeamNameValid,
                    isSubmitting = isSubmitting,
                    onTeamNameChanged = onTeamNameChanged,
                    onSubmit = onSubmitRegistrationTapped
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
                    onJoinTapped = onJoinTapped,
                    onRegisterTapped = onRegisterTapped,
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
    onJoinTapped: () -> Unit,
    onRegisterTapped: () -> Unit,
    onJoinAsGameMasterTapped: () -> Unit,
) {
    val step = state.joinStep
    val needsRegister: Boolean = (step as? JoinFlowStep.CodeValidated)
        ?.let { it.game.registration.required && !it.alreadyRegistered } ?: false
    val isEnabled = step is JoinFlowStep.CodeValidated
    val buttonLabel = if (needsRegister) stringResource(R.string.register) else stringResource(R.string.join)
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
        OutlinedTextField(
            value = state.gameCode,
            onValueChange = onCodeChanged,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(0.7f),
            placeholder = { Text("ABC123") }
        )
        when (step) {
            is JoinFlowStep.Validating -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            is JoinFlowStep.CodeNotFound -> Text(
                stringResource(R.string.no_game_found_with_this_code),
                fontFamily = GameBoyFont,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.error
            )
            is JoinFlowStep.NetworkError -> Text(
                stringResource(R.string.network_error_please_try_again),
                fontFamily = GameBoyFont,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.error
            )
            is JoinFlowStep.RegistrationClosed -> Text(
                stringResource(R.string.registration_closed_for_this_game),
                fontFamily = GameBoyFont,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.error
            )
            else -> Spacer(Modifier.height(1.dp))
        }
        TextButton(
            onClick = { if (needsRegister) onRegisterTapped() else onJoinTapped() },
            enabled = isEnabled,
            modifier = Modifier
                .background(
                    if (isEnabled) GradientFire else SolidColor(Color.Gray.copy(alpha = 0.3f)),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            Text(
                if (gmAvailable) stringResource(R.string.join_as_hunter) else buttonLabel,
                fontFamily = GameBoyFont,
                fontSize = 18.sp,
                color = Color.Black.copy(alpha = if (isEnabled) 1f else 0.4f)
            )
        }
        if (gmAvailable) {
            TextButton(
                onClick = onJoinAsGameMasterTapped,
                modifier = Modifier
                    .background(
                        SolidColor(MaterialTheme.colorScheme.primary),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(R.string.join_as_game_master),
                    fontFamily = GameBoyFont,
                    fontSize = 18.sp,
                    color = Color.White,
                )
            }
        }
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
            enabled = !isSubmitting,
        )
        if (error != null) {
            Text(
                error,
                fontFamily = GameBoyFont,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        TextButton(
            onClick = onSubmit,
            enabled = password.length == 4 && !isSubmitting,
            modifier = Modifier
                .background(
                    if (password.length == 4 && !isSubmitting) GradientFire else SolidColor(Color.Gray.copy(alpha = 0.3f)),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    stringResource(R.string.submit),
                    fontFamily = GameBoyFont,
                    fontSize = 18.sp,
                    color = Color.Black,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RegisterFormContent(
    game: Game,
    teamName: String,
    isTeamNameValid: Boolean,
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
            stringResource(R.string.register),
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
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onSubmit,
            enabled = isTeamNameValid && !isSubmitting,
            modifier = Modifier
                .background(
                    if (isTeamNameValid && !isSubmitting) GradientFire else SolidColor(Color.Gray.copy(alpha = 0.3f)),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    stringResource(R.string.sign_up),
                    fontFamily = GameBoyFont,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
