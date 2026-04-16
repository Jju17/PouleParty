package dev.rahier.pouleparty.ui.gamecreation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.ui.theme.GradientFire
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle

/** Shared scaffolding for every game-creation step: scrollable column with title + subtitle. */
@Composable
internal fun StepContainer(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = title,
                style = bangerStyle(32),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = gameboyStyle(10),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
            content()
        }
    }
}

/** Selection card with emoji + optional subtitle, highlighted when selected. */
@Composable
internal fun OptionCard(
    text: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    gradient: Brush? = null,
    subtitle: String? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isSelected) 8.dp else 2.dp, shape)
            .clip(shape)
            .then(
                if (isSelected && gradient != null) {
                    Modifier.background(gradient)
                } else if (isSelected) {
                    Modifier.background(GradientFire)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), shape)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(emoji, fontSize = 36.sp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text,
                    style = bangerStyle(22),
                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = gameboyStyle(7),
                        color = if (isSelected) Color.Black.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Label + value row used in the recap step. */
@Composable
internal fun RecapRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = gameboyStyle(9),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = gameboyStyle(9),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// ── Formatters ──────────────────────────────────────────────────

internal fun formatDuration(minutes: Double): String {
    val hours = minutes.toInt() / 60
    val mins = minutes.toInt() % 60
    return if (mins == 0) "${hours}h" else "${hours}h${mins}"
}

internal fun registrationDeadlineLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min before"
    minutes == 60 -> "1 hour before"
    minutes == 1440 -> "1 day before"
    else -> "${minutes / 60} hours before"
}
