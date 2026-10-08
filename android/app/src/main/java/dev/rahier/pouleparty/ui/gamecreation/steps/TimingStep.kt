package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamecreation.StepContainer
import java.util.Date

@Composable
fun TimingStep(
    gameDurationMinutes: Double,
    headStartMinutes: Double,
    startDate: Date,
    dateFormat: (Date) -> String,
    onDurationChanged: (Double) -> Unit,
    onHeadStartChanged: (Double) -> Unit,
) {
    StepContainer(title = stringResource(R.string.wizard_timing_title)) {
        DurationSection(gameDurationMinutes, startDate, dateFormat, onDurationChanged)
        HorizontalDivider()
        HeadStartSection(headStartMinutes, onHeadStartChanged)
    }
}
