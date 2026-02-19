package dev.rahier.pouleparty.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.util.Date

@Composable
fun CountdownView(nowDate: Date, nextUpdateDate: Date?) {
    val target = nextUpdateDate ?: Date()
    val diffMs = target.time - nowDate.time
    val totalSeconds = (diffMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    Text(
        text = "Map update in: %02d:%02d".format(minutes, seconds)
    )
}
