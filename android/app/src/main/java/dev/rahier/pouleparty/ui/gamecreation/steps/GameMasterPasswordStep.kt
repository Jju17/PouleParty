package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamecreation.StepContainer
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.gameboyStyle

/**
 * PP-88 — chicken opts in to the GameMaster role and sets the 4-digit
 * password. Server side (PP-70) writes the password to the private
 * subcollection and flips `Game.hasGameMasterPassword`. Toggle ON by
 * default for D-Day so every Free game ships with a GameMaster slot.
 */
@Composable
fun GameMasterPasswordStep(
    isEnabled: Boolean,
    password: String,
    onEnabledChanged: (Boolean) -> Unit,
    onPasswordChanged: (String) -> Unit,
) {
    StepContainer(
        title = stringResource(R.string.wizard_gamemaster_title),
        subtitle = stringResource(R.string.wizard_gamemaster_subtitle),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wizard_gamemaster_enable),
                style = gameboyStyle(10),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChanged,
                colors = SwitchDefaults.colors(checkedThumbColor = CROrange, checkedTrackColor = CROrange.copy(alpha = 0.5f)),
            )
        }

        if (isEnabled) {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChanged,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("••••") },
                modifier = Modifier.fillMaxWidth(0.5f),
            )
            Text(
                text = stringResource(R.string.wizard_gamemaster_secret_hint),
                style = gameboyStyle(9),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
