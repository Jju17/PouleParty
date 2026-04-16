package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.runtime.Composable
import dev.rahier.pouleparty.ui.gamecreation.OptionCard
import dev.rahier.pouleparty.ui.gamecreation.StepContainer

@Composable
fun ChickenSelectionStep() {
    StepContainer(
        title = "Qui sera la Poule ?",
        subtitle = "Comment choisir la poule ?"
    ) {
        OptionCard(
            text = "Au hasard",
            emoji = "\uD83C\uDFB2",
            isSelected = true,
            onClick = { }
        )
        OptionCard(
            text = "Premier arrive",
            emoji = "\uD83C\uDFC3",
            isSelected = false,
            onClick = { }
        )
        OptionCard(
            text = "Je designe",
            emoji = "\uD83D\uDC46",
            isSelected = false,
            onClick = { }
        )
    }
}
