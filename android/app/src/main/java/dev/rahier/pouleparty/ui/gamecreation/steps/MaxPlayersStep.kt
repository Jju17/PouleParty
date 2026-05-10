package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamecreation.StepContainer
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle

@Composable
fun MaxPlayersStep(
    maxPlayers: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
    val keyboard = LocalSoftwareKeyboardController.current

    var inputText by remember { mutableStateOf(maxPlayers.toString()) }
    var isFocused by remember { mutableStateOf(false) }

    // Re-sync the displayed text whenever the source of truth changes (e.g.
    // user pressed the +/- buttons). Skip while editing so the user's typing
    // isn't overwritten mid-keystroke.
    LaunchedEffect(maxPlayers, isFocused) {
        if (!isFocused) inputText = maxPlayers.toString()
    }

    fun commit() {
        val parsed = inputText.trim().toIntOrNull() ?: return
        onValueChange(parsed)
    }

    StepContainer(
        title = stringResource(R.string.number_of_players),
        subtitle = stringResource(R.string.how_many_hunters_can_join)
    ) {
        BasicTextField(
            value = inputText,
            onValueChange = { newText ->
                // Numeric keyboard still permits paste; force digits only and
                // cap at 3 chars (max value is 500).
                inputText = newText.filter { it.isDigit() }.take(3)
            },
            singleLine = true,
            textStyle = bangerStyle(64).copy(color = CROrange, textAlign = TextAlign.Center),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(CROrange),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                commit()
                focusManager.clearFocus()
                keyboard?.hide()
            }),
            modifier = Modifier
                .width(220.dp)
                .onFocusChanged { focusState ->
                    val nowFocused = focusState.isFocused
                    if (isFocused && !nowFocused) commit()
                    isFocused = nowFocused
                }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { onValueChange(maxPlayers - 1) },
                enabled = maxPlayers > range.first,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.decrease_max_players),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(
                onClick = { onValueChange(maxPlayers + 1) },
                enabled = maxPlayers < range.last,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.increase_max_players),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Text(
            text = stringResource(R.string.between_x_and_y_hunters, range.first, range.last),
            style = gameboyStyle(9),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
