package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
        title = "Avance de la poule ?",
        subtitle = "Temps avant que les chasseurs ne commencent"
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
            text = if (headStartMinutes.toInt() == 0) "Pas d'avance" else "${headStartMinutes.toInt()} minutes d'avance pour la poule",
            style = gameboyStyle(9),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
