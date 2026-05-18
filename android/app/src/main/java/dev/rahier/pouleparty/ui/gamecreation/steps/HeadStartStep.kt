package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamecreation.StepContainer
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle

@Composable
fun HeadStartStep(
    headStartMinutes: Double,
    onHeadStartChanged: (Double) -> Unit
) {
    StepContainer(
        title = stringResource(R.string.wizard_head_start_title),
        subtitle = stringResource(R.string.wizard_head_start_subtitle)
    ) {
        Text(
            text = "${headStartMinutes.toInt()} min",
            style = bangerStyle(64),
            color = CROrange
        )

        Slider(
            value = headStartMinutes.toFloat(),
            onValueChange = { onHeadStartChanged(it.toDouble()) },
            valueRange = 0f..15f,
            steps = 14,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(thumbColor = CROrange, activeTrackColor = CROrange)
        )

        Text(
            text = if (headStartMinutes.toInt() == 0)
                stringResource(R.string.wizard_head_start_none)
            else
                stringResource(R.string.wizard_head_start_minutes, headStartMinutes.toInt()),
            style = gameboyStyle(9),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
