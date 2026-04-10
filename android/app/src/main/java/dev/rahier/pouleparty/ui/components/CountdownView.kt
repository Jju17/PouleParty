package dev.rahier.pouleparty.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rahier.pouleparty.R
import java.util.Date

@Composable
fun CountdownView(
    nowDate: Date,
    nextUpdateDate: Date?,
    chickenStartDate: Date? = null,
    hunterStartDate: Date? = null,
    isChicken: Boolean = false
) {
    val isPreChickenStart = chickenStartDate != null && nowDate.before(chickenStartDate)
    val isHeadStart = !isPreChickenStart && hunterStartDate != null && nowDate.before(hunterStartDate)

    val target = when {
        isPreChickenStart -> chickenStartDate ?: Date()
        isHeadStart -> hunterStartDate ?: Date()
        else -> nextUpdateDate ?: Date()
    }

    val label = when {
        isPreChickenStart -> if (isChicken) stringResource(R.string.you_start_in) else stringResource(R.string.chicken_starts_in)
        isHeadStart -> if (isChicken) stringResource(R.string.hunt_starts_in_chicken) else stringResource(R.string.hunt_starts_in_hunter)
        else -> stringResource(R.string.map_update_in)
    }

    val diffMs = target.time - nowDate.time
    val totalSeconds = kotlin.math.ceil(diffMs / 1000.0).toLong().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    Text(text = "$label %02d:%02d".format(minutes, seconds))
}
