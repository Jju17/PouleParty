package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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
    StepContainer(
        title = stringResource(R.string.number_of_players),
        subtitle = stringResource(R.string.how_many_hunters_can_join)
    ) {
        Text(
            text = "$maxPlayers",
            style = bangerStyle(64),
            color = CROrange
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
