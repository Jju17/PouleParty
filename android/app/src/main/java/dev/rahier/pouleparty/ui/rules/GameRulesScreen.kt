package dev.rahier.pouleparty.ui.rules

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.theme.*

@Composable
fun GameRulesScreen() {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        HowToPlaySection()
        GameModesSection()
        SettingsSection()
    }
}

@Composable
private fun HowToPlaySection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How to play", style = bangerStyle(32), color = Color.Black)

        RuleRow("One player is the Chicken, the others are Hunters.")
        RuleRow("The game takes place in a circular zone on the map.")
        RuleRow("The zone shrinks over time based on game settings.")
        RuleRow("Hunters win by finding the Chicken. The Chicken wins by surviving until the end!")
    }
}

@Composable
private fun GameModesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Game Modes", style = bangerStyle(32), color = Color.Black)

        GameModeCard(
            title = GameMod.FOLLOW_THE_CHICKEN.title,
            description = "The Hunters see a circle that follows the Chicken's position in real time. The Chicken must run and hide!",
            details = listOf(
                "Chicken sends its position to all Hunters",
                "Hunters see the zone move with the Chicken",
                "Chicken does NOT see the Hunters"
            )
        )

        GameModeCard(
            title = GameMod.STAY_IN_THE_ZONE.title,
            description = "The zone stays fixed on the map and shrinks over time. Everyone must stay inside!",
            details = listOf(
                "No position sharing between players",
                "The zone is centered on the starting location",
                "Strategy: stay hidden inside the shrinking zone"
            )
        )

        GameModeCard(
            title = GameMod.MUTUAL_TRACKING.title,
            description = "Like Follow the Chicken, but the Chicken can also see all Hunters on her map!",
            details = listOf(
                "Chicken sends its position to Hunters",
                "Hunters send their position to the Chicken",
                "Both sides can track each other in real time"
            )
        )
    }
}

@Composable
private fun SettingsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Game Settings", style = bangerStyle(32), color = Color.Black)

        SettingRow("Start / End time", "When the game starts and ends. The Chicken wins if the time runs out!")
        SettingRow("Radius interval update", "How often the zone shrinks (in minutes).")
        SettingRow("Radius decline", "How many meters the zone shrinks each update.")
        SettingRow("Map setup", "Choose the starting location and initial radius of the zone.")
    }
}

@Composable
private fun RuleRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("●", color = CROrange, fontSize = 10.sp)
        Text(text, style = gameboyStyle(10), color = Color.Black)
    }
}

@Composable
private fun GameModeCard(title: String, description: String, details: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, CROrange, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = bangerStyle(22), color = Color.Black)
        Text(description, style = gameboyStyle(8), color = Color.Black.copy(alpha = 0.7f))

        details.forEach { detail ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Text(">", style = gameboyStyle(8), color = CROrange)
                Text(detail, style = gameboyStyle(8), color = Color.Black)
            }
        }
    }
}

@Composable
private fun SettingRow(name: String, explanation: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(name, style = gameboyStyle(10), color = CROrange)
        Text(explanation, style = gameboyStyle(8), color = Color.Black.copy(alpha = 0.7f))
    }
}
