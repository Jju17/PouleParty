package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamecreation.StepContainer
import dev.rahier.pouleparty.ui.theme.GradientFire
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun DurationStep(
    gameDurationMinutes: Double,
    startDate: Date,
    dateFormat: SimpleDateFormat,
    onDurationChanged: (Double) -> Unit
) {
    StepContainer(
        title = "Combien de temps ?",
        subtitle = "Duree de la partie"
    ) {
        val durationOptions = listOf(
            60.0 to "1h",
            90.0 to "1h30",
            120.0 to "2h",
            150.0 to "2h30",
            180.0 to "3h"
        )

        durationOptions.forEach { (minutes, label) ->
            val isSelected = gameDurationMinutes == minutes
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(if (isSelected) 6.dp else 2.dp, shape)
                    .clip(shape)
                    .then(
                        if (isSelected) Modifier.background(GradientFire)
                        else Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), shape)
                    )
                    .clickable { onDurationChanged(minutes) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = bangerStyle(24),
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        val endTime = Date(startDate.time + (gameDurationMinutes * 60 * 1000).toLong())
        Text(
            text = "${stringResource(R.string.ends_at)} ${dateFormat.format(endTime)}",
            style = gameboyStyle(10),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}
