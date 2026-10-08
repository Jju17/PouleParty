package dev.rahier.pouleparty.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamelogic.formatOvertime
import dev.rahier.pouleparty.ui.theme.HunterRed
import java.util.Date

@Composable
fun CountdownView(
    nowDate: Date,
    nextUpdateDate: Date?,
    chickenStartDate: Date? = null,
    hunterStartDate: Date? = null,
    endDate: Date? = null,
    isChicken: Boolean = false
) {
    val isEnded = endDate != null && !nowDate.before(endDate)
    val isPreChickenStart = !isEnded && chickenStartDate != null && nowDate.before(chickenStartDate)
    val isHeadStart = !isEnded && !isPreChickenStart && hunterStartDate != null && nowDate.before(hunterStartDate)

    val target = when {
        isEnded -> endDate
        isPreChickenStart -> chickenStartDate
        isHeadStart -> hunterStartDate
        else -> nextUpdateDate ?: Date()
    }

    val label = when {
        isEnded -> stringResource(R.string.countdown_overtime)
        isPreChickenStart -> if (isChicken) stringResource(R.string.you_start_in) else stringResource(R.string.chicken_starts_in)
        isHeadStart -> if (isChicken) stringResource(R.string.hunt_starts_in_chicken) else stringResource(R.string.hunt_starts_in_hunter)
        else -> stringResource(R.string.map_update_in)
    }

    val text = if (isEnded) {
        "$label ${formatOvertime(nowDate, endDate)}"
    } else {
        val diffMs = target.time - nowDate.time
        val totalSeconds = kotlin.math.ceil(diffMs / 1000.0).toLong().coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        "$label %02d:%02d".format(minutes, seconds)
    }

    val animatedColor by animateColorAsState(
        targetValue = if (isEnded) HunterRed else LocalContentColor.current,
        animationSpec = tween(durationMillis = 250),
        label = "countdown-color",
    )
    Text(text = text, color = animatedColor)
}
