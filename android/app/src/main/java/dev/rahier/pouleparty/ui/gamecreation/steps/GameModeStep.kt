package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.runtime.Composable
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.gamecreation.OptionCard
import dev.rahier.pouleparty.ui.gamecreation.StepContainer
import dev.rahier.pouleparty.ui.theme.GradientChicken
import dev.rahier.pouleparty.ui.theme.GradientHunter

@Composable
fun GameModeStep(
    selectedMode: GameMod,
    onSelect: (GameMod) -> Unit
) {
    StepContainer(
        title = "Quel mode de jeu ?",
        subtitle = "Choisis comment tu veux jouer"
    ) {
        OptionCard(
            text = "Follow the Chicken",
            emoji = "\uD83D\uDC14",
            isSelected = selectedMode == GameMod.FOLLOW_THE_CHICKEN,
            onClick = { onSelect(GameMod.FOLLOW_THE_CHICKEN) },
            gradient = GradientChicken,
            subtitle = "The zone shrinks toward the chicken"
        )
        OptionCard(
            text = "Stay in the Zone",
            emoji = "\uD83D\uDCCD",
            isSelected = selectedMode == GameMod.STAY_IN_THE_ZONE,
            onClick = { onSelect(GameMod.STAY_IN_THE_ZONE) },
            gradient = GradientHunter,
            subtitle = "Fixed zone that shrinks"
        )
    }
}
