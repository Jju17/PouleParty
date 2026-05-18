package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rahier.pouleparty.R
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
        title = stringResource(R.string.wizard_game_mode_title),
        subtitle = stringResource(R.string.wizard_game_mode_subtitle)
    ) {
        OptionCard(
            text = stringResource(R.string.wizard_game_mode_follow_title),
            emoji = "\uD83D\uDC14",
            isSelected = selectedMode == GameMod.FOLLOW_THE_CHICKEN,
            onClick = { onSelect(GameMod.FOLLOW_THE_CHICKEN) },
            gradient = GradientChicken,
            subtitle = stringResource(R.string.wizard_game_mode_follow_subtitle)
        )
        OptionCard(
            text = stringResource(R.string.wizard_game_mode_stay_title),
            emoji = "\uD83D\uDCCD",
            isSelected = selectedMode == GameMod.STAY_IN_THE_ZONE,
            onClick = { onSelect(GameMod.STAY_IN_THE_ZONE) },
            gradient = GradientHunter,
            subtitle = stringResource(R.string.wizard_game_mode_stay_subtitle)
        )
    }
}
