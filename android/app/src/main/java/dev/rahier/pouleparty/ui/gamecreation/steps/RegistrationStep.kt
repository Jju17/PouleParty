package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamecreation.OptionCard
import dev.rahier.pouleparty.ui.gamecreation.StepContainer
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.gameboyStyle

@Composable
fun RegistrationStep(
    requiresRegistration: Boolean,
    isDepositPlan: Boolean,
    registrationClosesBeforeStartMinutes: Int?,
    onToggle: (Boolean) -> Unit,
    onDeadlineChanged: (Int?) -> Unit
) {
    StepContainer(
        title = stringResource(R.string.registration),
        subtitle = stringResource(R.string.do_hunters_need_to_register_before_joining)
    ) {
        OptionCard(
            text = stringResource(R.string.open_join),
            emoji = "\uD83D\uDEAA",
            isSelected = !requiresRegistration,
            onClick = { if (!isDepositPlan) onToggle(false) }
        )
        OptionCard(
            text = stringResource(R.string.registration_required),
            emoji = "\uD83D\uDCDD",
            isSelected = requiresRegistration,
            onClick = { if (!isDepositPlan) onToggle(true) }
        )
        if (isDepositPlan) {
            Text(
                text = stringResource(R.string.registration_required_for_paid_deposit_games),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        AnimatedVisibility(
            visible = requiresRegistration,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.registration_closes),
                    style = gameboyStyle(9),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                val options = listOf(
                    R.string.at_game_start to null,
                    R.string.fifteen_min_before to 15,
                    R.string.thirty_min_before to 30,
                    R.string.one_hour_before to 60,
                    R.string.two_hours_before to 120,
                    R.string.one_day_before to 1440,
                )
                options.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { (labelRes, minutes) ->
                            val isSelected = registrationClosesBeforeStartMinutes == minutes
                            val bgColor = if (isSelected) CROrange else MaterialTheme.colorScheme.surface
                            val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
                            Button(
                                onClick = { onDeadlineChanged(minutes) },
                                colors = ButtonDefaults.buttonColors(containerColor = bgColor),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = gameboyStyle(8),
                                    color = textColor,
                                    maxLines = 1
                                )
                            }
                        }
                        if (row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
