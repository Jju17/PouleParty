package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamecreation.OptionCard
import dev.rahier.pouleparty.ui.gamecreation.StepContainer

@Composable
fun ChickenSelectionStep() {
    StepContainer(
        title = stringResource(R.string.wizard_chicken_selection_title),
        subtitle = stringResource(R.string.wizard_chicken_selection_subtitle)
    ) {
        OptionCard(
            text = stringResource(R.string.wizard_chicken_selection_random),
            emoji = "\uD83C\uDFB2",
            isSelected = true,
            onClick = { }
        )
        OptionCard(
            text = stringResource(R.string.wizard_chicken_selection_first),
            emoji = "\uD83C\uDFC3",
            isSelected = false,
            onClick = { }
        )
        OptionCard(
            text = stringResource(R.string.wizard_chicken_selection_pick),
            emoji = "\uD83D\uDC46",
            isSelected = false,
            onClick = { }
        )
    }
}
