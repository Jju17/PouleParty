package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamecreation.OptionCard
import dev.rahier.pouleparty.ui.gamecreation.StepContainer

@Composable
fun ParticipationStep(
    isParticipating: Boolean,
    onSelect: (Boolean) -> Unit
) {
    StepContainer(
        title = stringResource(R.string.wizard_participation_title),
        subtitle = stringResource(R.string.wizard_participation_subtitle)
    ) {
        OptionCard(
            text = stringResource(R.string.wizard_participation_chicken),
            emoji = "\uD83D\uDC14",
            isSelected = isParticipating,
            onClick = { onSelect(true) }
        )
        OptionCard(
            text = stringResource(R.string.wizard_participation_organizer),
            emoji = "\uD83D\uDCCB",
            isSelected = !isParticipating,
            onClick = { onSelect(false) }
        )
    }
}
