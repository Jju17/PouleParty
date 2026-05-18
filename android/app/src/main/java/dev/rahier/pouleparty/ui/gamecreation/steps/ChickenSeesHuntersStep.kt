package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamecreation.OptionCard
import dev.rahier.pouleparty.ui.gamecreation.StepContainer

@Composable
fun ChickenSeesHuntersStep(
    chickenCanSeeHunters: Boolean,
    onToggle: (Boolean) -> Unit
) {
    StepContainer(
        title = stringResource(R.string.wizard_chicken_sees_hunters_title),
        subtitle = stringResource(R.string.wizard_chicken_sees_hunters_subtitle)
    ) {
        OptionCard(
            text = stringResource(R.string.wizard_chicken_sees_hunters_yes),
            emoji = "\uD83D\uDC40",
            isSelected = chickenCanSeeHunters,
            onClick = { onToggle(true) }
        )
        OptionCard(
            text = stringResource(R.string.wizard_chicken_sees_hunters_no),
            emoji = "\uD83D\uDE48",
            isSelected = !chickenCanSeeHunters,
            onClick = { onToggle(false) }
        )
    }
}
