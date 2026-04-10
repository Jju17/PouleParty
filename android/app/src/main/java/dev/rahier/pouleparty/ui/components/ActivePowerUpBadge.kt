package dev.rahier.pouleparty.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.chickenconfig.powerUpColor
import dev.rahier.pouleparty.ui.chickenconfig.powerUpTextColor
import dev.rahier.pouleparty.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Shows active power-up effects as small badges below the compass.
 * Tapping a badge expands it to show details (name + remaining time).
 * Visible to all players (chicken and hunters).
 */
@Composable
fun ActivePowerUpBadge(game: Game, modifier: Modifier = Modifier) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expandedType by remember { mutableStateOf<PowerUpType?>(null) }

    // Tick every second to update countdown
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMs = System.currentTimeMillis()
        }
    }

    data class ActiveEffect(val type: PowerUpType, val until: Date)

    val now = Date(nowMs)
    val effects = game.powerUps.activeEffects
    val activeEffects = buildList<ActiveEffect> {
        effects.invisibility?.let {
            if (now.before(it.toDate())) add(ActiveEffect(PowerUpType.INVISIBILITY, it.toDate()))
        }
        effects.zoneFreeze?.let {
            if (now.before(it.toDate())) add(ActiveEffect(PowerUpType.ZONE_FREEZE, it.toDate()))
        }
        effects.radarPing?.let {
            if (now.before(it.toDate())) add(ActiveEffect(PowerUpType.RADAR_PING, it.toDate()))
        }
        effects.decoy?.let {
            if (now.before(it.toDate())) add(ActiveEffect(PowerUpType.DECOY, it.toDate()))
        }
        effects.jammer?.let {
            if (now.before(it.toDate())) add(ActiveEffect(PowerUpType.JAMMER, it.toDate()))
        }
    }

    if (activeEffects.isEmpty()) return

    Column(modifier = modifier.padding(end = 8.dp, top = 4.dp), horizontalAlignment = Alignment.End) {
        activeEffects.forEach { effect ->
            BadgeItem(
                type = effect.type,
                remainingSeconds = maxOf(0, (effect.until.time - nowMs) / 1000).toInt(),
                isExpanded = expandedType == effect.type,
                onClick = {
                    expandedType = if (expandedType == effect.type) null else effect.type
                }
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun BadgeItem(
    type: PowerUpType,
    remainingSeconds: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val bgColor = powerUpColor(type)
    val fgColor = powerUpTextColor(type)

    Row(
        modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = bgColor.copy(alpha = 0.6f), spotColor = bgColor.copy(alpha = 0.6f))
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = if (isExpanded) 10.dp else 0.dp)
            .then(if (!isExpanded) Modifier.size(36.dp) else Modifier.height(36.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (type) {
                PowerUpType.INVISIBILITY -> Icons.Default.VisibilityOff
                PowerUpType.ZONE_FREEZE -> Icons.Default.AcUnit
                PowerUpType.RADAR_PING -> Icons.Default.Sensors
                PowerUpType.ZONE_PREVIEW -> Icons.Default.RemoveRedEye
                PowerUpType.DECOY -> Icons.Default.PersonPin
                PowerUpType.JAMMER -> Icons.Default.SensorsOff
            },
            contentDescription = type.title,
            tint = fgColor,
            modifier = Modifier
                .then(if (!isExpanded) Modifier.padding(8.dp) else Modifier)
                .size(20.dp)
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        type.title,
                        color = fgColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${remainingSeconds}s remaining",
                        color = fgColor.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
