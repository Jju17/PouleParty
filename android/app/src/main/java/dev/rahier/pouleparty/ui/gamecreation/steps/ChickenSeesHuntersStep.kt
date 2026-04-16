package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.runtime.Composable
import dev.rahier.pouleparty.ui.gamecreation.OptionCard
import dev.rahier.pouleparty.ui.gamecreation.StepContainer

@Composable
fun ChickenSeesHuntersStep(
    chickenCanSeeHunters: Boolean,
    onToggle: (Boolean) -> Unit
) {
    StepContainer(
        title = "La poule voit les chasseurs ?",
        subtitle = "Can the chicken see where the hunters are?"
    ) {
        OptionCard(
            text = "Oui, elle les voit",
            emoji = "\uD83D\uDC40",
            isSelected = chickenCanSeeHunters,
            onClick = { onToggle(true) }
        )
        OptionCard(
            text = "Non, a l'aveugle",
            emoji = "\uD83D\uDE48",
            isSelected = !chickenCanSeeHunters,
            onClick = { onToggle(false) }
        )
    }
}
