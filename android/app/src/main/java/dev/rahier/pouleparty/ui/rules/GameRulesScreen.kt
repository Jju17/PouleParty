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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.R
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
        Text(stringResource(R.string.how_to_play), style = bangerStyle(32), color = Color.Black)

        RuleRow(stringResource(R.string.rule_one_chicken))
        RuleRow(stringResource(R.string.rule_circular_zone))
        RuleRow(stringResource(R.string.rule_zone_shrinks))
        RuleRow(stringResource(R.string.rule_win_condition))
    }
}

@Composable
private fun GameModesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.game_modes), style = bangerStyle(32), color = Color.Black)

        GameModeCard(
            title = GameMod.FOLLOW_THE_CHICKEN.title,
            description = stringResource(R.string.mode_follow_desc),
            details = listOf(
                stringResource(R.string.mode_follow_detail1),
                stringResource(R.string.mode_follow_detail2),
                stringResource(R.string.mode_follow_detail3)
            )
        )

        GameModeCard(
            title = GameMod.STAY_IN_THE_ZONE.title,
            description = stringResource(R.string.mode_stay_desc),
            details = listOf(
                stringResource(R.string.mode_stay_detail1),
                stringResource(R.string.mode_stay_detail2),
                stringResource(R.string.mode_stay_detail3)
            )
        )

        GameModeCard(
            title = stringResource(R.string.chicken_can_see_hunters_title),
            description = stringResource(R.string.chicken_can_see_hunters_desc),
            details = listOf(
                stringResource(R.string.chicken_can_see_hunters_detail1),
                stringResource(R.string.chicken_can_see_hunters_detail2),
                stringResource(R.string.chicken_can_see_hunters_detail3)
            )
        )
    }
}

@Composable
private fun SettingsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.game_settings), style = bangerStyle(32), color = Color.Black)

        SettingRow(stringResource(R.string.setting_start_end_name), stringResource(R.string.setting_start_end_desc))
        SettingRow(stringResource(R.string.radius_interval_update), stringResource(R.string.setting_radius_interval_desc))
        SettingRow(stringResource(R.string.radius_decline), stringResource(R.string.setting_radius_decline_desc))
        SettingRow(stringResource(R.string.map_setup), stringResource(R.string.setting_map_desc))
    }
}

@Composable
private fun RuleRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("●", color = CROrange, fontSize = 10.sp)
        Text(text, fontSize = 14.sp, color = Color.Black)
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
        Text(description, fontSize = 14.sp, color = Color.Black.copy(alpha = 0.7f))

        details.forEach { detail ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Text(">", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CROrange)
                Text(detail, fontSize = 14.sp, color = Color.Black)
            }
        }
    }
}

@Composable
private fun SettingRow(name: String, explanation: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(name, style = gameboyStyle(10), color = CROrange)
        Text(explanation, fontSize = 14.sp, color = Color.Black.copy(alpha = 0.7f))
    }
}
