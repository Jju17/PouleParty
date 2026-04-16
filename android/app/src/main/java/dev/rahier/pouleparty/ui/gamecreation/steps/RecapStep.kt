package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.components.GameCodeCard
import dev.rahier.pouleparty.ui.gamecreation.GameCreationUiState
import dev.rahier.pouleparty.ui.gamecreation.RecapRow
import dev.rahier.pouleparty.ui.gamecreation.StepContainer
import dev.rahier.pouleparty.ui.gamecreation.formatDuration
import dev.rahier.pouleparty.ui.gamecreation.registrationDeadlineLabel
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.gameboyStyle
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun RecapStep(
    state: GameCreationUiState,
    dateFormat: SimpleDateFormat,
    onCodeCopied: () -> Unit
) {
    val game = state.game
    val endTime = Date(game.startDate.time + (state.gameDurationMinutes * 60 * 1000).toLong())

    StepContainer(
        title = "Recapitulatif",
        subtitle = "Verifie avant de lancer"
    ) {
        GameCodeCard(
            gameCode = game.gameCode,
            codeCopied = state.codeCopied,
            onCodeCopied = onCodeCopied,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RecapRow(
                    label = stringResource(R.string.role),
                    value = if (state.isParticipating) "Chicken \uD83D\uDC14" else "Organizer \uD83D\uDCCB"
                )
                HorizontalDivider()
                RecapRow(
                    label = stringResource(R.string.game_mode),
                    value = game.gameModEnum.title
                )
                HorizontalDivider()
                RecapRow(
                    label = stringResource(R.string.max_players),
                    value = "${game.maxPlayers}"
                )
                HorizontalDivider()
                RecapRow(
                    label = stringResource(R.string.start_at),
                    value = dateFormat.format(game.startDate)
                )
                HorizontalDivider()
                RecapRow(
                    label = stringResource(R.string.duration),
                    value = formatDuration(state.gameDurationMinutes)
                )
                HorizontalDivider()
                RecapRow(
                    label = stringResource(R.string.ends_at),
                    value = dateFormat.format(endTime)
                )
                HorizontalDivider()
                RecapRow(
                    label = stringResource(R.string.chicken_head_start),
                    value = "${game.timing.headStartMinutes.toInt()} min"
                )
                HorizontalDivider()
                RecapRow(
                    label = stringResource(R.string.zone_radius),
                    value = "${game.zone.radius.toInt()} m"
                )
                HorizontalDivider()
                RecapRow(
                    label = stringResource(R.string.power_ups),
                    value = if (game.powerUps.enabled) "ON" else "OFF"
                )
                if (game.powerUps.enabled) {
                    HorizontalDivider()
                    val enabledNames = PowerUpType.entries
                        .filter { it.firestoreValue in game.powerUps.enabledTypes }
                        .joinToString(", ") { it.title }
                    RecapRow(
                        label = stringResource(R.string.active_types),
                        value = enabledNames
                    )
                }
                if (game.gameModEnum == GameMod.FOLLOW_THE_CHICKEN) {
                    HorizontalDivider()
                    RecapRow(
                        label = stringResource(R.string.chicken_can_see_hunters),
                        value = if (game.chickenCanSeeHunters) "Yes" else "No"
                    )
                }
                if (game.isPaid) {
                    HorizontalDivider()
                    RecapRow(
                        label = stringResource(R.string.pricing),
                        value = game.pricingModelEnum.title
                    )
                    HorizontalDivider()
                    val totalCents = game.pricing.pricePerPlayer * game.maxPlayers
                    RecapRow(
                        label = stringResource(R.string.total_price),
                        value = String.format("%.2f€", totalCents / 100.0)
                    )
                    if (game.pricing.deposit > 0) {
                        HorizontalDivider()
                        RecapRow(
                            label = stringResource(R.string.deposit),
                            value = String.format("%.2f€", game.pricing.deposit / 100.0)
                        )
                    }
                }
                HorizontalDivider()
                RecapRow(
                    label = stringResource(R.string.registration),
                    value = if (game.registration.required) stringResource(R.string.registration_required) else stringResource(R.string.open_join)
                )
                if (game.registration.required && game.registration.closesMinutesBefore != null) {
                    HorizontalDivider()
                    RecapRow(
                        label = stringResource(R.string.registration_closes),
                        value = registrationDeadlineLabel(game.registration.closesMinutesBefore)
                    )
                }
                if (!state.isZoneConfigured) {
                    HorizontalDivider()
                    Text(
                        text = if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE)
                            stringResource(R.string.set_start_and_final_zone)
                        else
                            stringResource(R.string.set_start_zone),
                        style = gameboyStyle(8),
                        color = CROrange,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
