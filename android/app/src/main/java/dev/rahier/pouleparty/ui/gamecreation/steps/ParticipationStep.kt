package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.runtime.Composable
import dev.rahier.pouleparty.ui.gamecreation.OptionCard
import dev.rahier.pouleparty.ui.gamecreation.StepContainer

@Composable
fun ParticipationStep(
    isParticipating: Boolean,
    onSelect: (Boolean) -> Unit
) {
    StepContainer(
        title = "Tu participes ?",
        subtitle = "Est-ce que tu joues aussi ?"
    ) {
        OptionCard(
            text = "Je suis la Poule",
            emoji = "\uD83D\uDC14",
            isSelected = isParticipating,
            onClick = { onSelect(true) }
        )
        OptionCard(
            text = "J'organise",
            emoji = "\uD83D\uDCCB",
            isSelected = !isParticipating,
            onClick = { onSelect(false) }
        )
    }
}
