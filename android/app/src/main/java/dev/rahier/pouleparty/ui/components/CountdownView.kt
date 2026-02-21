package dev.rahier.pouleparty.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        isPreChickenStart -> chickenStartDate!!
        isHeadStart -> hunterStartDate!!
        else -> nextUpdateDate ?: Date()
    }

    val label = when {
        isPreChickenStart -> if (isChicken) "You start in:" else "\uD83D\uDC14 starts in:"
        isHeadStart -> if (isChicken) "\uD83D\uDD0D Hunt starts in:" else "Hunt starts in:"
        else -> "Map update in:"
    }

    val diffMs = target.time - nowDate.time
    val totalSeconds = kotlin.math.ceil(diffMs / 1000.0).toLong().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    Text(text = "$label %02d:%02d".format(minutes, seconds))
}
